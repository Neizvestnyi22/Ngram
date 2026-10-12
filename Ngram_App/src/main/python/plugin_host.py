"""The plugin engine's Python half: loads plugin files and routes the app's events to them.

Everything here is called from Java (``org.ngram.messenger.plugins.Plugins``). A plugin
that raises is logged and skipped; it never takes the event, or the app, down with it. One
that keeps raising is switched off, so a broken plugin cannot slow every message forever.
"""

import ast
import importlib.machinery
import importlib.util
import json
import os
import re
import sys
import time
import traceback
from collections import OrderedDict

from java import jclass

from base_plugin import AppEvent, BasePlugin, HookResult, HookStrategy

# The exteraGram SDK level the names follow, and Ngram's own revision of the additions on top.
SDK_VERSION = "1.4.4.3"
NGRAM_SDK = 3

# Failures in a row after which a plugin is switched off.
MAX_STRIKES = 8
# A hook that takes longer than this holds up the interface; it is reported in the log.
SLOW_HOOK_MS = 300
# One-shot callbacks that were never called (an alert closed by the back button) are dropped beyond this.
MAX_CALLBACKS = 512

_Bridge = jclass("org.ngram.messenger.plugins.PluginBridge")

_plugins = OrderedDict()  # id -> BasePlugin instance, in load order
_callbacks = OrderedDict()
_callback_seq = 0
_dir = ""

_META = ("__id__", "__name__", "__description__", "__author__", "__version__", "__icon__",
         "__app_version__", "__sdk_version__", "__min_version__")


def init(plugins_dir):
    global _dir
    _dir = str(plugins_dir)
    return "python " + sys.version.split()[0]


def report(where, plugin_id=""):
    """Logs the exception being handled, attributed to a plugin when known."""
    try:
        _Bridge.log(plugin_id, "E", "%s\n%s" % (where, traceback.format_exc()))
    except Exception:
        pass


def _strike(plugin, where):
    """Counts a failure of ``plugin``; too many in a row and the app switches it off."""
    report(where, plugin.id)
    plugin._strikes += 1
    if plugin._strikes >= MAX_STRIKES and plugin.enabled:
        plugin.enabled = False
        try:
            _Bridge.pluginFailed(plugin.id, "%d errors in a row, the last one in %s" % (plugin._strikes, where))
        except Exception:
            pass


def _timed(plugin, where, call):
    """Runs one hook of ``plugin``, noting failures and slowness. Returns its result, or None if it raised."""
    started = time.monotonic()
    try:
        result = call()
        plugin._strikes = 0
        return result
    except Exception:
        _strike(plugin, where)
        return None
    finally:
        took = (time.monotonic() - started) * 1000.0
        if took > SLOW_HOOK_MS:
            plugin._slow += 1
            if where not in plugin._slow_reported:
                plugin._slow_reported.add(where)
                _Bridge.log(plugin.id, "W", "%s took %d ms. Hooks run on the UI thread: move slow work to "
                                          "client_utils.run_on_queue()." % (where, int(took)))


# ---- callbacks handed to Java ------------------------------------------------------------


def register_callback(fn, keep=False):
    """Gives ``fn`` a number Java can call it by. One-shot unless ``keep`` is set."""
    global _callback_seq
    _callback_seq += 1
    _callbacks[_callback_seq] = (fn, bool(keep))
    while len(_callbacks) > MAX_CALLBACKS:
        _callbacks.popitem(last=False)
    return _callback_seq


def drop_callback(callback_id):
    _callbacks.pop(int(callback_id), None)


def run_callback(callback_id, args):
    """Runs a callback. ``args`` is a Java array, or None."""
    callback_id = int(callback_id)
    entry = _callbacks.get(callback_id)
    if entry is None:
        return
    fn, keep = entry
    if not keep:
        _callbacks.pop(callback_id, None)
    try:
        fn(*list(args or []))
    except Exception:
        report("callback")


# ---- installing and loading --------------------------------------------------------------


def read_metadata(path):
    """The top-level ``__id__ = "..."`` constants of a plugin file, as JSON. Read without running it."""
    with open(str(path), encoding="utf-8") as f:
        source = f.read()
    try:
        tree = ast.parse(source)
    except SyntaxError as e:
        raise ValueError("Syntax error on line %s: %s" % (e.lineno, e.msg))
    meta = {}
    for node in tree.body:
        if isinstance(node, ast.Assign) and len(node.targets) == 1 and isinstance(node.targets[0], ast.Name):
            name = node.targets[0].id
            if name in _META and isinstance(node.value, ast.Constant):
                meta[name.strip("_")] = node.value.value
    plugin_id = str(meta.get("id", ""))
    if not re.fullmatch(r"[A-Za-z0-9_\-]{2,64}", plugin_id):
        raise ValueError("The plugin has no valid __id__ (letters, digits, _ and -, 2 to 64 characters)")
    has_class = any(
        isinstance(node, ast.ClassDef) and any(getattr(b, "id", getattr(b, "attr", "")) == "BasePlugin" for b in node.bases)
        for node in tree.body
    )
    if not has_class:
        raise ValueError("The file defines no class derived from BasePlugin")
    meta["lines"] = source.count("\n") + 1
    return json.dumps(meta, ensure_ascii=False)


def load(path, meta_json):
    """Runs a plugin file and calls ``on_plugin_load``. Returns "" or the error text."""
    meta = json.loads(str(meta_json))
    plugin_id = str(meta["id"])
    try:
        unload(plugin_id)
        # Plugin files end in ".plugin", which importlib does not take for Python: name the loader.
        module_name = "ngram_plugin_" + re.sub(r"\W", "_", plugin_id)
        spec = importlib.util.spec_from_file_location(module_name, str(path), loader=importlib.machinery.SourceFileLoader(module_name, str(path)))
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        cls = None
        for value in vars(module).values():
            if isinstance(value, type) and issubclass(value, BasePlugin) and value is not BasePlugin:
                cls = value
                break
        if cls is None:
            return "The file defines no class derived from BasePlugin"
        plugin = cls()
        if not hasattr(plugin, "_hooks"):
            # The plugin defined __init__ without calling super().__init__(): give it what the engine needs.
            BasePlugin.__init__(plugin)
        plugin._bind(meta, os.path.join(_dir, plugin_id + ".settings.json"), os.path.join(_dir, plugin_id + ".data"))
        plugin.enabled = True
        _plugins[plugin_id] = plugin
        try:
            plugin.on_plugin_load()
        except Exception:
            text = traceback.format_exc()
            unload(plugin_id)
            return text
        _hooks_changed()
        return ""
    except Exception:
        _plugins.pop(plugin_id, None)
        return traceback.format_exc()


def unload(plugin_id):
    plugin = _plugins.pop(str(plugin_id), None)
    if plugin is None:
        return
    was_enabled = plugin.enabled
    plugin.enabled = True
    try:
        plugin.on_plugin_unload()
    except Exception:
        report("on_plugin_unload", plugin.id)
    plugin.enabled = False
    plugin._cancel_timers()
    for item_id in list(plugin._menu_items):
        _Bridge.removeMenuItem(item_id)
    plugin._menu_items.clear()
    _hooks_changed()
    return was_enabled


def _hooks_changed():
    """Tells the app which names anyone watches, so unwatched traffic costs nothing."""
    exact, partial, send = set(), set(), False
    for plugin in _plugins.values():
        send = send or plugin._send_hook_priority is not None or bool(plugin._commands)
        if plugin._message_hook:
            exact.add("TL_updateNewMessage")
            exact.add("TL_updateNewChannelMessage")
        for name, spec in plugin._hooks.items():
            (partial if spec["substring"] else exact).add(name)
    _Bridge.setHooks(sorted(exact), sorted(partial), send)


def app_event(name):
    event = AppEvent[str(name)]
    for plugin in list(_plugins.values()):
        try:
            plugin.on_app_event(event)
        except Exception:
            report("on_app_event", plugin.id)


def describe(plugin_id):
    """What a loaded plugin has registered, as JSON, for its page in the app."""
    plugin = _plugins.get(str(plugin_id))
    if plugin is None:
        return "{}"
    return json.dumps({
        "commands": [[name, spec[1]] for name, spec in plugin._commands.items()],
        "hooks": sorted(plugin._hooks) + (["on_send_message"] if plugin._send_hook_priority is not None else [])
                 + (["on_new_message"] if plugin._message_hook else []),
        "menu": len(plugin._menu_items),
        "timers": len(plugin._timers),
        "strikes": plugin._strikes,
        "slow": plugin._slow,
        "settings": len(plugin._settings),
    }, ensure_ascii=False)


# ---- hooks -------------------------------------------------------------------------------


def _watchers(name):
    found = []
    for plugin in _plugins.values():
        best = None
        for hooked, spec in plugin._hooks.items():
            if hooked == name or (spec["substring"] and hooked in name):
                best = spec["priority"] if best is None else max(best, spec["priority"])
        if best is not None:
            found.append((best, plugin))
    found.sort(key=lambda pair: -pair[0])
    return [plugin for _, plugin in found]


def _run(plugins, where, call, pick, current):
    """Runs one hook over ``plugins``. Returns ``[code, object]`` where code is OK or CANCEL."""
    for plugin in plugins:
        if not plugin.enabled:
            continue
        result = _timed(plugin, where, lambda: call(plugin, current))
        if not isinstance(result, HookResult):
            continue
        if result.strategy == HookStrategy.CANCEL:
            return ["CANCEL", current]
        if result.strategy in (HookStrategy.MODIFY, HookStrategy.MODIFY_FINAL):
            changed = pick(result)
            if changed is not None:
                current = changed
            if result.strategy == HookStrategy.MODIFY_FINAL:
                break
    return ["OK", current]


def _run_commands(params):
    """Looks for a plugin command in the outgoing text. Returns CANCEL, OK (text may be replaced) or None."""
    text = params.message
    if not isinstance(text, str) or not text:
        return None
    word, _, rest = text.partition(" ")
    for plugin in list(_plugins.values()):
        spec = plugin._commands.get(word.lower()) if plugin.enabled else None
        if spec is None:
            continue
        handler = spec[0]

        def call():
            try:
                return handler(rest.strip(), params)
            except TypeError:
                # A handler written as ``def cmd(self, args)``.
                return handler(rest.strip())

        result = _timed(plugin, "command " + word, call)
        if isinstance(result, HookResult):
            if result.strategy == HookStrategy.CANCEL:
                return "CANCEL"
            changed = result.params
            if changed is not None and changed is not params:
                new_text = changed.get("message") if isinstance(changed, dict) else getattr(changed, "message", None)
                if isinstance(new_text, str):
                    params.message = new_text
            return "OK"
        if isinstance(result, str):
            # The command answers with the text to send instead of itself.
            params.message = result
            return "OK"
        # Nothing returned: the command did its work, and the command text itself is not sent.
        return "CANCEL"
    return None


def dispatch_send(account, params):
    """Outgoing message. ``params`` is the app's ``SendMessagesHelper.SendMessageParams``; hooks edit it in place."""
    account = int(account)
    if not isinstance(params.message, str) or not params.message:
        # Media without a caption: nothing for text commands, and hooks see it as it is.
        handled = None
    else:
        handled = _run_commands(params)
    if handled == "CANCEL":
        return "CANCEL"
    senders = [p for p in _plugins.values() if p._send_hook_priority is not None]
    senders.sort(key=lambda p: -p._send_hook_priority)

    def pick(result):
        changed = result.params
        if changed is not None and changed is not params:
            # A plugin may hand back its own object or a dict; only the text is carried over.
            text = changed.get("message") if isinstance(changed, dict) else getattr(changed, "message", None)
            if isinstance(text, str):
                params.message = text
        return params

    return _run(senders, "on_send_message_hook", lambda p, cur: p.on_send_message_hook(account, cur), pick, params)[0]


def dispatch_request(name, account, request):
    name, account = str(name), int(account)
    return _run(_watchers(name), "pre_request_hook " + name, lambda p, cur: p.pre_request_hook(name, account, cur), lambda r: r.request, request)


def dispatch_response(name, account, response, error):
    """The answer to a watched request. Hooks see it; the app goes on with the answer as it came."""
    name, account = str(name), int(account)
    _run(_watchers(name), "post_request_hook " + name, lambda p, cur: p.post_request_hook(name, account, cur, error), lambda r: r.response, response)


def dispatch_updates(name, account, updates):
    """A whole container of updates (``TL_updates``, ``TL_updateShort``...), before the app applies it."""
    name, account = str(name), int(account)
    _run(_watchers(name), "on_updates_hook " + name, lambda p, cur: p.on_updates_hook(name, account, cur), lambda r: r.updates, updates)


def dispatch_update(name, account, update):
    """One update out of a container. Hooks see it; the app applies it as it came."""
    name, account = str(name), int(account)
    if name in ("TL_updateNewMessage", "TL_updateNewChannelMessage"):
        for plugin in list(_plugins.values()):
            if plugin._message_hook and plugin.enabled:
                _timed(plugin, "on_new_message", lambda: plugin.on_new_message(update.message))
    _run(_watchers(name), "on_update_hook " + name, lambda p, cur: p.on_update_hook(name, account, cur), lambda r: r.update, update)


# ---- menu items --------------------------------------------------------------------------


def _context(keys, values):
    return {str(k): v for k, v in zip(list(keys), list(values))}


def menu_visible(item_id, keys, values):
    """Asks an item's ``condition`` callable whether to show it. Items without one are always shown."""
    item_id = str(item_id)
    for plugin in _plugins.values():
        data = plugin._menu_items.get(item_id)
        if data is None:
            continue
        if not callable(data.condition):
            return True
        try:
            return bool(data.condition(_context(keys, values)))
        except Exception:
            report("menu condition", plugin.id)
            return False
    return False


def click_menu(item_id, keys, values):
    item_id = str(item_id)
    for plugin in _plugins.values():
        data = plugin._menu_items.get(item_id)
        if data is None:
            continue
        _timed(plugin, "menu item", lambda: data.on_click(_context(keys, values)))
        return


# ---- settings ----------------------------------------------------------------------------


def _page(plugin, path):
    """The rows of a settings page; ``path`` is the trail of sub-page rows that leads to it, e.g. "3/0"."""
    items = list(plugin.create_settings() or [])
    for index in [int(part) for part in str(path).split("/") if part != ""]:
        items = list(items[index].create_sub_fragment() or [])
    return items


def settings_json(plugin_id, path):
    plugin = _plugins.get(str(plugin_id))
    if plugin is None:
        return "[]"
    try:
        rows = []
        for item in _page(plugin, path):
            row = item.to_dict() if hasattr(item, "to_dict") else {"type": "custom"}
            key = row.get("key")
            if key is not None:
                row["value"] = plugin.get_setting(key, row.get("default"))
            rows.append(row)
        return json.dumps(rows, ensure_ascii=False)
    except Exception:
        report("create_settings", plugin.id)
        return "[]"


def settings_set(plugin_id, path, index, value_json):
    plugin = _plugins.get(str(plugin_id))
    if plugin is None:
        return
    try:
        item = _page(plugin, path)[int(index)]
        value = json.loads(str(value_json))
        plugin.set_setting(item.key, value)
        handler = getattr(item, "on_change", None)
        if handler is not None:
            handler(value)
    except Exception:
        report("setting changed", plugin.id)


def _call_flexible(handler):
    """Click handlers are written both as ``fn(view)`` and as ``fn()``."""
    try:
        handler(None)
    except TypeError:
        handler()


def settings_click(plugin_id, path, index, long=False):
    plugin = _plugins.get(str(plugin_id))
    if plugin is None:
        return
    try:
        handler = getattr(_page(plugin, path)[int(index)], "on_long_click" if long else "on_click", None)
        if handler is not None:
            _call_flexible(handler)
    except Exception:
        report("setting clicked", plugin.id)


def settings_reset(plugin_id):
    """Forgets every stored setting of a plugin, so the defaults from its code apply again."""
    plugin = _plugins.get(str(plugin_id))
    if plugin is not None:
        plugin.import_settings({}, True)
