"""The plugin base class and the types hooks exchange with the app.

The names and shapes follow the exteraGram plugin SDK, so a plugin written against its
documented API (hooks, menu items, settings, client utilities) reads the same here. Ngram 3
is built on Telegram for Android: requests and updates are its ``TLRPC`` objects
(``TL_messages_sendMessage``, ``TL_updateNewMessage``), and ``org.telegram.*`` classes can be
imported. What Ngram does not have is method hooking (``hook_method``): see ``hook_utils``.
"""

import json
import os
import threading
import traceback
from enum import Enum
from typing import Any, Callable, Dict, List, Optional

from java import jclass

_Bridge = jclass("org.ngram.messenger.plugins.PluginBridge")


class HookStrategy(Enum):
    DEFAULT = "DEFAULT"
    CANCEL = "CANCEL"
    MODIFY = "MODIFY"
    MODIFY_FINAL = "MODIFY_FINAL"


class HookResult:
    """What a hook wants done: nothing, cancel, or carry on with a changed object."""

    def __init__(self, strategy: HookStrategy = HookStrategy.DEFAULT, request: Any = None, response: Any = None,
                 update: Any = None, updates: Any = None, params: Any = None):
        self.strategy = strategy
        self.request = request
        self.response = response
        self.update = update
        self.updates = updates
        self.params = params


class AppEvent(Enum):
    START = "START"
    STOP = "STOP"
    PAUSE = "PAUSE"
    RESUME = "RESUME"


class MenuItemType(Enum):
    MESSAGE_CONTEXT_MENU = "MESSAGE_CONTEXT_MENU"
    DRAWER_MENU = "DRAWER_MENU"
    MAIN_MENU = "MAIN_MENU"
    CHAT_ACTION_MENU = "CHAT_ACTION_MENU"
    PROFILE_ACTION_MENU = "PROFILE_ACTION_MENU"


class MenuItemData:
    def __init__(self, menu_type: MenuItemType, text: str, on_click: Callable[[Dict[str, Any]], None],
                 item_id: Optional[str] = None, icon: Optional[str] = None, subtext: Optional[str] = None,
                 condition: Optional[str] = None, priority: int = 0):
        self.menu_type = menu_type
        self.text = text
        self.on_click = on_click
        self.item_id = item_id
        self.icon = icon
        self.subtext = subtext
        # exteraGram evaluates a string here as an MVEL expression, which Ngram does not have: a string
        # is ignored and the item is always shown. Ngram's own form is a callable taking the same
        # context dict as on_click and returning whether to show the item.
        self.condition = condition
        self.priority = priority


class _Logger:
    """Writes to logcat under ``plugin.<id>`` and to the log shown on the Plugins screen."""

    def __init__(self, plugin_id: str):
        self._id = plugin_id

    def _write(self, level: str, message: Any):
        try:
            _Bridge.log(self._id, level, str(message))
        except Exception:
            pass

    def debug(self, message: Any, *args):
        self._write("D", (str(message) % args) if args else message)

    def info(self, message: Any, *args):
        self._write("I", (str(message) % args) if args else message)

    def warning(self, message: Any, *args):
        self._write("W", (str(message) % args) if args else message)

    warn = warning

    def error(self, message: Any, *args):
        self._write("E", (str(message) % args) if args else message)

    def exception(self, message: Any = "", *args):
        text = (str(message) % args) if args else str(message)
        self._write("E", (text + "\n" if text else "") + traceback.format_exc())

    def dump(self, obj: Any, label: str = ""):
        self._write("D", (label + ": " if label else "") + repr(obj))


class BasePlugin:
    """Subclass this once per plugin file. The engine creates the instance and calls the hooks."""

    def __init__(self):
        self.id = ""
        self.name = ""
        self.description = ""
        self.author = ""
        self.version = ""
        self.icon = ""
        self.enabled = False
        self.logger = _Logger("")
        self._settings: Dict[str, Any] = {}
        self._settings_path = ""
        self._hooks: Dict[str, Dict[str, Any]] = {}
        self._send_hook_priority: Optional[int] = None
        self._menu_items: Dict[str, MenuItemData] = {}
        self._menu_counter = 0
        self._commands: Dict[str, Any] = {}
        self._message_hook = False
        self._timers: Dict[int, Any] = {}
        self._timer_seq = 0
        self._data_dir = ""
        # Kept by the engine: failures in a row, and how often a hook was slow.
        self._strikes = 0
        self._slow = 0
        self._slow_reported = set()

    # ---- wiring done by the engine -------------------------------------------------

    def _bind(self, meta: Dict[str, Any], settings_path: str, data_dir: str = ""):
        self._data_dir = data_dir
        self.id = str(meta.get("id", ""))
        self.name = str(meta.get("name", self.id))
        self.description = str(meta.get("description", ""))
        self.author = str(meta.get("author", ""))
        self.version = str(meta.get("version", ""))
        self.icon = str(meta.get("icon", ""))
        self.logger = _Logger(self.id)
        self._settings_path = settings_path
        try:
            if os.path.exists(settings_path):
                with open(settings_path, encoding="utf-8") as f:
                    self._settings = json.load(f)
        except Exception:
            self._settings = {}

    def _save_settings(self):
        # Written to a side file first, so a crash mid-write cannot leave half a file behind.
        try:
            tmp = self._settings_path + ".tmp"
            with open(tmp, "w", encoding="utf-8") as f:
                json.dump(self._settings, f, ensure_ascii=False)
            os.replace(tmp, self._settings_path)
        except Exception:
            self.logger.exception("Couldn't save settings")

    def _cancel_timers(self):
        for timer in list(self._timers.values()):
            try:
                timer.cancel()
            except Exception:
                pass
        self._timers.clear()

    # ---- lifecycle -------------------------------------------------------------------

    def on_plugin_load(self):
        pass

    def on_plugin_unload(self):
        pass

    def on_app_event(self, event_type: AppEvent):
        pass

    # ---- settings --------------------------------------------------------------------

    def create_settings(self) -> List[Any]:
        return []

    def get_setting(self, key: str, default: Any = None) -> Any:
        return self._settings.get(key, default)

    def set_setting(self, key: str, value: Any, reload_settings: bool = False):
        self._settings[key] = value
        self._save_settings()
        if reload_settings:
            _Bridge.reloadSettings(self.id)

    def export_settings(self) -> Dict[str, Any]:
        return dict(self._settings)

    def import_settings(self, settings: Dict[str, Any], reload_settings: bool = True):
        self._settings = dict(settings or {})
        self._save_settings()
        if reload_settings:
            _Bridge.reloadSettings(self.id)

    # ---- logging ---------------------------------------------------------------------

    def log(self, message: Any):
        self.logger.info(message)

    # ---- hooks -----------------------------------------------------------------------

    def add_hook(self, name: str, match_substring: bool = False, priority: int = 0) -> str:
        """Watches a request or an update by class name, e.g. ``"TL_messages_sendMessage"`` or ``"TL_updateNewMessage"``."""
        self._hooks[name] = {"substring": bool(match_substring), "priority": int(priority)}
        _host()._hooks_changed()
        return name

    def remove_hook(self, name: str):
        if self._hooks.pop(name, None) is not None:
            _host()._hooks_changed()

    def add_on_send_message_hook(self, priority: int = 0):
        self._send_hook_priority = int(priority)
        _host()._hooks_changed()

    def pre_request_hook(self, request_name: str, account: int, request: Any) -> HookResult:
        return HookResult()

    def post_request_hook(self, request_name: str, account: int, response: Any, error: Any) -> HookResult:
        return HookResult()

    def on_update_hook(self, update_name: str, account: int, update: Any) -> HookResult:
        return HookResult()

    def on_updates_hook(self, container_name: str, account: int, updates: Any) -> HookResult:
        return HookResult()

    def on_send_message_hook(self, account: int, params: Any) -> HookResult:
        return HookResult()

    # ---- Ngram's own shortcuts -------------------------------------------------------

    def add_command(self, name: str, handler: Callable, description: str = ""):
        """Registers a text command, e.g. ``self.add_command(".hi", self.cmd_hi, "says hello")``.

        When the user sends a message that starts with the command word, ``handler(args, params)`` is
        called with the rest of the text. Return a string to send that text instead, a ``HookResult``
        for full control, or nothing to do the work quietly: the command itself is then not sent.
        """
        self._commands[str(name).lower()] = (handler, str(description))
        _host()._hooks_changed()

    def remove_command(self, name: str):
        if self._commands.pop(str(name).lower(), None) is not None:
            _host()._hooks_changed()

    def add_new_message_hook(self):
        """Asks for ``on_new_message`` to be called for every message that arrives or is sent."""
        self._message_hook = True
        _host()._hooks_changed()

    def on_new_message(self, message: Any):
        """Called with a ``TLRPC.Message`` after ``add_new_message_hook()``. For watching, not changing."""
        pass

    def run_later(self, seconds: float, func: Callable[[], None]) -> int:
        """Runs ``func`` once, off the UI thread, after ``seconds``. Cancelled when the plugin unloads."""
        return self._schedule(seconds, func, repeat=False)

    def run_every(self, seconds: float, func: Callable[[], None]) -> int:
        """Runs ``func`` every ``seconds`` (at least 1), off the UI thread, until cancelled or unloaded."""
        return self._schedule(max(1.0, float(seconds)), func, repeat=True)

    def cancel_timer(self, timer_id: int):
        timer = self._timers.pop(timer_id, None)
        if timer is not None:
            timer.cancel()

    def _schedule(self, seconds: float, func: Callable[[], None], repeat: bool) -> int:
        self._timer_seq += 1
        timer_id = self._timer_seq

        def fire():
            if not self.enabled or timer_id not in self._timers:
                return
            try:
                func()
            except Exception:
                self.logger.exception("Timer failed")
            if repeat and self.enabled and timer_id in self._timers:
                arm()
            else:
                self._timers.pop(timer_id, None)

        def arm():
            timer = threading.Timer(float(seconds), fire)
            timer.daemon = True
            self._timers[timer_id] = timer
            timer.start()

        arm()
        return timer_id

    def get_data_dir(self) -> str:
        """A folder of the plugin's own for files; created on first use, removed with the plugin."""
        os.makedirs(self._data_dir, exist_ok=True)
        return self._data_dir

    # ---- menu items ------------------------------------------------------------------

    def add_menu_item(self, data: MenuItemData) -> str:
        self._menu_counter += 1
        item_id = data.item_id or "%s:%d" % (self.id, self._menu_counter)
        if not item_id.startswith(self.id + ":"):
            item_id = self.id + ":" + item_id
        self._menu_items[item_id] = data
        menu_type = data.menu_type.value if isinstance(data.menu_type, MenuItemType) else str(data.menu_type)
        _Bridge.registerMenuItem(self.id, item_id, menu_type, str(data.text), data.subtext or "", data.icon or "", int(data.priority),
                                 callable(data.condition))
        return item_id

    def remove_menu_item(self, item_id: str):
        if self._menu_items.pop(item_id, None) is not None:
            _Bridge.removeMenuItem(item_id)

    # ---- helpers ---------------------------------------------------------------------

    def hook_method(self, *args, **kwargs):
        raise NotImplementedError("hook_method() replaces a method of the app, which needs a hooking framework Ngram does not "
                                  "include. Use add_hook(), add_on_send_message_hook() or menu items instead.")

    hook_all_methods = hook_method
    hook_all_constructors = hook_method

    def unhook_method(self, *args, **kwargs):
        pass

    def client(self, account: int = 0):
        """The client utilities, for code written as ``self.client(account).send_text(...)``."""
        import client_utils
        return client_utils


def _host():
    import plugin_host
    return plugin_host
