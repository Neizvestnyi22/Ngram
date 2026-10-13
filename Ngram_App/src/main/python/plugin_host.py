"""The plugin engine's Python half: loads plugin files and routes the app's events to them.

Everything here is called from Java (``org.ngram.messenger.plugins.Plugins``). A plugin
that raises is logged and skipped; it never takes the event, or the app, down with it. One
that keeps raising is switched off, so a broken plugin cannot slow every message forever.

Two kinds of plugin files are understood. Ngram's own kind starts with a passport of
``# @id ...`` comment lines and works through the ``ngram`` package. The exteraGram kind
declares ``__id__ = ...`` constants and a class derived from ``BasePlugin``. Inside the engine
both are the same thing: a ``BasePlugin`` object (the kit hides one behind its handle).
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

# The exteraGram SDK level the compatible names follow, and the revision of Ngram's own kit.
SDK_VERSION = "1.4.4.3"
NGRAM_KIT = 1

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
# The lines of a kit plugin's passport: "# @id hello".
_PASSPORT = re.compile(r"^#\s*@([a-z_]+)\s+(.*?)\s*$")
_PASSPORT_KEYS = ("id", "name", "version", "author", "about", "icon", "needs", "requires", "update")


def init(plugins_dir):
    global _dir
    _dir = str(plugins_dir)
    site = _site_dir()
    if site not in sys.path:
        sys.path.append(site)
    return "python " + sys.version.split()[0]


def _site_dir():
    """Where packages that plugins asked for are unpacked."""
    path = os.path.join(_dir, "_site")
    os.makedirs(path, exist_ok=True)
    return path


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
        plugin._calls += 1
        plugin._ms += took
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


def _passport(source):
    """The ``# @key value`` lines at the top of a kit plugin, as a dict; empty when there are none."""
    found = {}
    for line in source.split("\n"):
        stripped = line.strip()
        if not stripped or stripped.startswith("#!"):
            continue
        if not stripped.startswith("#"):
            break
        match = _PASSPORT.match(stripped)
        if match and match.group(1) in _PASSPORT_KEYS and match.group(1) not in found:
            found[match.group(1)] = match.group(2)
    return found if "id" in found else {}


def _dotted(node):
    parts = []
    while isinstance(node, ast.Attribute):
        parts.append(node.attr)
        node = node.value
    if isinstance(node, ast.Name):
        parts.append(node.id)
    return ".".join(reversed(parts))


def _inspect(tree):
    """What the code is able to do, judged from what it imports and calls. A hint for the user, not a guard."""
    uses = set()
    for node in ast.walk(tree):
        names = []
        if isinstance(node, ast.Import):
            names = [alias.name for alias in node.names]
        elif isinstance(node, ast.ImportFrom):
            module = node.module or ""
            names = [module] + [module + "." + alias.name for alias in node.names]
        for name in names:
            root = name.split(".")[0]
            if root in ("urllib", "http", "socket", "requests", "httpx", "aiohttp", "http_utils", "ssl", "ftplib", "smtplib") or name == "ngram.net":
                uses.add("network")
            if root in ("java", "org", "android", "androidx", "com", "hook_utils", "client_utils") or name == "ngram.tg":
                uses.add("app")
            if root in ("subprocess", "ctypes", "multiprocessing"):
                uses.add("shell")
            if root in ("shutil", "pathlib", "file_utils", "tempfile", "zipfile", "sqlite3"):
                uses.add("files")
        if isinstance(node, ast.Call):
            called = _dotted(node.func)
            last = called.split(".")[-1]
            if called in ("open", "os.remove", "os.rename", "os.unlink") or called.startswith("os.path"):
                uses.add("files")
            if called in ("os.system", "os.popen", "eval", "exec", "__import__"):
                uses.add("shell")
            if last in ("send", "send_text", "send_message", "send_file", "send_photo", "send_document", "reply", "edit", "edit_message"):
                uses.add("send")
            if last in ("add_hook", "add_new_message_hook", "add_on_send_message_hook", "on"):
                uses.add("watch")
    return sorted(uses)


def read_metadata(path):
    """What a plugin file says about itself and what its code can do, as JSON. Read without running it."""
    with open(str(path), encoding="utf-8") as f:
        source = f.read()
    try:
        tree = ast.parse(source)
    except SyntaxError as e:
        raise ValueError("Syntax error on line %s: %s" % (e.lineno, e.msg))
    meta = _passport(source)
    if meta:
        meta["kind"] = "kit"
        meta["description"] = meta.pop("about", "")
        uses_kit = any(
            (isinstance(node, ast.ImportFrom) and (node.module or "").split(".")[0] == "ngram")
            or (isinstance(node, ast.Import) and any(alias.name.split(".")[0] == "ngram" for alias in node.names))
            for node in tree.body
        )
        if not uses_kit:
            raise ValueError("The file has a passport but never imports the ngram kit")
    else:
        meta = {"kind": "class"}
        for node in tree.body:
            if isinstance(node, ast.Assign) and len(node.targets) == 1 and isinstance(node.targets[0], ast.Name):
                name = node.targets[0].id
                if name in _META and isinstance(node.value, ast.Constant):
                    meta[name.strip("_")] = node.value.value
        has_class = any(
            isinstance(node, ast.ClassDef) and any(getattr(b, "id", getattr(b, "attr", "")) == "BasePlugin" for b in node.bases)
            for node in tree.body
        )
        if not has_class:
            raise ValueError("Not a plugin: the file has neither a passport (# @id ...) nor a class derived from BasePlugin")
    plugin_id = str(meta.get("id", ""))
    if not re.fullmatch(r"[A-Za-z0-9_\-]{2,64}", plugin_id):
        raise ValueError("The plugin has no valid id (letters, digits, _ and -, 2 to 64 characters)")
    meta["lines"] = source.count("\n") + 1
    meta["uses"] = _inspect(tree)
    meta["missing"] = missing(meta.get("requires", ""))
    return json.dumps(meta, ensure_ascii=False)


# ---- packages plugins ask for ------------------------------------------------------------


def _wanted(requires):
    return [name.strip() for name in re.split(r"[,\s]+", str(requires or "")) if re.fullmatch(r"[A-Za-z0-9_.\-]{1,64}", name.strip())]


def missing(requires):
    """Which of the packages named in a passport's ``@requires`` cannot be imported yet."""
    absent = []
    for name in _wanted(requires):
        try:
            found = importlib.util.find_spec(name.replace("-", "_")) is not None
        except Exception:
            found = False
        if not found:
            absent.append(name)
    return absent


def pip_install(requires):
    """Fetches pure-Python packages from pypi.org into the plugins' own folder. Returns "" or the error.

    Only wheels that run anywhere (``py3-none-any``) are taken: there is no compiler on a phone.
    Called off the UI thread, and only after the user agreed to it.
    """
    import urllib.request
    import zipfile
    try:
        for name in missing(requires):
            with urllib.request.urlopen("https://pypi.org/pypi/%s/json" % name, timeout=20) as r:
                info = json.loads(r.read().decode("utf-8"))
            wheel = None
            for candidate in info.get("urls", []):
                if candidate.get("packagetype") == "bdist_wheel" and candidate.get("filename", "").endswith("-none-any.whl"):
                    wheel = candidate
                    break
            if wheel is None:
                return "The package %s has no pure-Python build, so it cannot be installed on a phone" % name
            if int(wheel.get("size", 0)) > 12 * 1024 * 1024:
                return "The package %s is too large (%d MB)" % (name, int(wheel["size"]) // (1024 * 1024))
            target = os.path.join(_dir, "_site.download")
            with urllib.request.urlopen(wheel["url"], timeout=60) as r, open(target, "wb") as out:
                out.write(r.read())
            with zipfile.ZipFile(target) as archive:
                site = os.path.realpath(_site_dir())
                for member in archive.namelist():
                    # Nothing may be written outside the packages folder.
                    if not os.path.realpath(os.path.join(site, member)).startswith(site + os.sep):
                        return "The package %s has an unsafe file name inside" % name
                archive.extractall(site)
            os.remove(target)
            importlib.invalidate_caches()
            _Bridge.log("", "I", "Installed package %s %s" % (name, info.get("info", {}).get("version", "")))
        return ""
    except Exception:
        return traceback.format_exc()


# ---- loading -----------------------------------------------------------------------------


def _forget(plugin):
    """Takes back what a plugin has registered in the app."""
    plugin._cancel_timers()
    for item_id in list(plugin._menu_items):
        _Bridge.removeMenuItem(item_id)
    plugin._menu_items.clear()


def load(path, meta_json):
    """Runs a plugin file and tells it that it is on. Returns "" or the error text."""
    import ngram
    from ngram._plugin import Plugin

    meta = json.loads(str(meta_json))
    plugin_id = str(meta["id"])
    settings_path = os.path.join(_dir, plugin_id + ".settings.json")
    data_dir = os.path.join(_dir, plugin_id + ".data")
    handle = None
    plugin = None
    try:
        unload(plugin_id)
        absent = missing(meta.get("requires", ""))
        if absent:
            return "Packages are missing: %s. Install the plugin again to fetch them." % ", ".join(absent)
        # Plugin files end in ".plugin", which importlib does not take for Python: name the loader.
        module_name = "ngram_plugin_" + re.sub(r"\W", "_", plugin_id)
        spec = importlib.util.spec_from_file_location(module_name, str(path), loader=importlib.machinery.SourceFileLoader(module_name, str(path)))
        module = importlib.util.module_from_spec(spec)
        # A kit plugin talks to its handle while the file runs, so the handle exists before that.
        handle = Plugin()
        handle._adapter._bind(meta, settings_path, data_dir)
        ngram.plugin = handle
        try:
            spec.loader.exec_module(module)
        finally:
            ngram.plugin = None
        cls = None
        for value in vars(module).values():
            if isinstance(value, type) and issubclass(value, BasePlugin) and value is not BasePlugin and value.__module__ == module_name:
                cls = value
                break
        if cls is not None:
            _forget(handle._adapter)
            plugin = cls()
            if not hasattr(plugin, "_hooks"):
                # The plugin defined __init__ without calling super().__init__(): give it what the engine needs.
                BasePlugin.__init__(plugin)
            plugin._bind(meta, settings_path, data_dir)
        elif handle._used:
            plugin = handle._adapter
        else:
            return "The file registers nothing: no command, event, menu entry or class derived from BasePlugin"
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
        for candidate in (plugin, handle._adapter if handle is not None else None):
            if candidate is not None:
                try:
                    _forget(candidate)
                except Exception:
                    pass
        _hooks_changed()
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
    _forget(plugin)
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
    # The app's own moments that kit plugins listen for: untold ones never cross into Python.
    events = set()
    for plugin in _plugins.values():
        events.update(getattr(plugin, "_events", ()))
    _Bridge.setEvents(sorted(events))


def app_event(name):
    """The app started, stopped, went to the background or came back."""
    name = str(name)
    event = AppEvent[name]
    for plugin in list(_plugins.values()):
        if plugin.enabled:
            _timed(plugin, "on_app_event", lambda: plugin.on_app_event(event))
    if name in ("PAUSE", "RESUME"):
        dispatch_event("app." + name.lower(), [], [])


def dispatch_event(name, keys, values):
    """One of the app's own moments, for kit plugins. Returns True when a handler stopped it."""
    name = str(name)
    fields = _context(keys, values)
    for plugin in list(_plugins.values()):
        handler = getattr(plugin, "on_event", None)
        if handler is None or not plugin.enabled:
            continue
        if _timed(plugin, "event " + name, lambda: handler(name, fields)):
            return True
    return False


def describe(plugin_id):
    """What a loaded plugin has registered, as JSON, for its page in the app."""
    plugin = _plugins.get(str(plugin_id))
    if plugin is None:
        return "{}"
    owner = getattr(plugin, "_owner", None)
    events = sorted(getattr(plugin, "_events", ()))
    return json.dumps({
        "commands": [[name, spec[1]] for name, spec in plugin._commands.items()],
        "hooks": events if owner is not None else (
            sorted(plugin._hooks) + (["on_send_message"] if plugin._send_hook_priority is not None else [])
            + (["on_new_message"] if plugin._message_hook else [])),
        "menu": len(plugin._menu_items),
        "timers": len(plugin._timers),
        "strikes": plugin._strikes,
        "slow": plugin._slow,
        "calls": plugin._calls,
        "ms": round(plugin._ms, 1),
        "exports": sorted(owner._exports) if owner is not None else [],
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
                values = getattr(item, "values", None)
                if values is not None:
                    # A number row keeps the number itself; the app is shown its place among the options.
                    stored = plugin.get_setting(key, getattr(item, "default", values[0]))
                    row["value"] = values.index(stored) if stored in values else row.get("default", 0)
                else:
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
        values = getattr(item, "values", None)
        if values is not None:
            value = values[int(value)]
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
