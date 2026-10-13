"""The handle a plugin file works through, and the objects its handlers receive."""

import json
import os
from typing import Any, Callable, Dict, List, Optional

from java import jclass

from base_plugin import BasePlugin, HookResult, HookStrategy, MenuItemData

_Bridge = jclass("org.ngram.messenger.plugins.PluginBridge")

# Where a menu entry may appear, in the kit's words and in the engine's.
_PLACES = {
    "message": "MESSAGE_CONTEXT_MENU",
    "chat": "CHAT_ACTION_MENU",
    "profile": "PROFILE_ACTION_MENU",
    "main": "MAIN_MENU",
}

# Events a handler may stop by returning False or calling event.cancel().
CANCELLABLE = {"message.sending", "link.open", "request"}

EVENTS = (
    "message.sending",   # a message is about to be sent: text may be changed, sending may be stopped
    "message.new",       # a message arrived or was sent from another device
    "message.edited",    # a message was edited
    "message.deleted",   # messages were deleted
    "chat.open",         # a chat screen opened
    "chat.close",        # a chat screen closed
    "link.open",         # a link is about to be opened: may be taken over
    "app.resume",        # the app came to the front
    "app.pause",         # the app went to the background
    "request",           # a raw API request is about to leave (needs name=...): may be changed or dropped
    "response",          # the answer to a raw API request (needs name=...)
    "update",            # a raw update from Telegram (needs name=...)
)


class Event:
    """What a handler of ``@plugin.on(...)`` receives. Fields depend on the event."""

    def __init__(self, name: str, **fields):
        self.name = name
        self.cancelled = False
        self.__dict__.update(fields)

    def cancel(self):
        """Stops what was about to happen, where the event allows it."""
        self.cancelled = True

    def __repr__(self):
        return "<Event %s %s>" % (self.name, {k: v for k, v in self.__dict__.items() if k not in ("name", "cancelled")})


class Sending(Event):
    """``message.sending``: the outgoing message. Assign to ``text`` to change it."""

    def __init__(self, account: int, params: Any):
        super().__init__("message.sending", account=account, params=params)

    @property
    def text(self) -> str:
        return self.params.message or ""

    @text.setter
    def text(self, value: str):
        self.params.message = str(value)

    @property
    def chat_id(self) -> int:
        return int(self.params.peer)

    @property
    def reply_to(self):
        return self.params.replyToMsg


class Command:
    """What a command handler receives: the words after the command and the message being sent."""

    def __init__(self, name: str, args: str, params: Any):
        self.name = name
        self.args = args
        self.params = params
        self.text = (params.message or "") if params is not None else ""
        self.chat_id = int(params.peer) if params is not None else 0

    def words(self) -> List[str]:
        return self.args.split()


class Context:
    """What a menu handler receives: where the entry was tapped and on what."""

    def __init__(self, raw: Dict[str, Any]):
        self.raw = raw
        self.account = int(raw.get("account", 0))
        self.chat_id = int(raw.get("dialog_id", 0))
        self.fragment = raw.get("fragment")
        self.activity = raw.get("context")
        self.message = raw.get("message")       # the app's MessageObject, when tapped on a message
        self.user = raw.get("user")             # TLRPC.User of a private chat or profile
        self.chat = raw.get("chat")             # TLRPC.Chat of a group or channel

    @property
    def text(self) -> str:
        """The text or caption of the message the entry was tapped on."""
        if self.message is None or self.message.messageOwner is None:
            return ""
        return self.message.messageOwner.message or ""

    @property
    def message_id(self) -> int:
        return int(self.message.getId()) if self.message is not None else 0

    @property
    def is_mine(self) -> bool:
        return bool(self.message.isOut()) if self.message is not None else False

    def reply(self, text: str):
        """Sends ``text`` to the chat as a reply to the message."""
        _Bridge.sendText(self.account, self.chat_id, str(text), self.message, "markdown")


class _Store:
    """A small dictionary that survives restarts: the plugin's own notes, apart from its settings."""

    def __init__(self, path: str):
        self._path = path
        self._data: Dict[str, Any] = {}
        try:
            if os.path.exists(path):
                with open(path, encoding="utf-8") as f:
                    self._data = json.load(f)
        except Exception:
            self._data = {}

    def _save(self):
        tmp = self._path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(self._data, f, ensure_ascii=False)
        os.replace(tmp, self._path)

    def __getitem__(self, key):
        return self._data[key]

    def __setitem__(self, key, value):
        self._data[key] = value
        self._save()

    def __delitem__(self, key):
        del self._data[key]
        self._save()

    def __contains__(self, key):
        return key in self._data

    def get(self, key, default=None):
        return self._data.get(key, default)

    def keys(self):
        return list(self._data.keys())

    def clear(self):
        self._data = {}
        self._save()


class _Settings:
    """``plugin.settings``: the values the user sets on the plugin's page, and the rows of that page."""

    def __init__(self, adapter):
        self._adapter = adapter
        self._rows: List[Any] = []
        self._defaults: Dict[str, Any] = {}

    def define(self, *rows):
        """Declares the rows of the settings page; see ``ngram.kit`` for what a row can be."""
        self._rows = list(rows)
        self._defaults = {}
        self._collect(self._rows)
        return self

    def _collect(self, rows):
        for row in rows:
            key = getattr(row, "key", None)
            if key is not None:
                self._defaults[key] = getattr(row, "default", None)
            children = getattr(row, "rows", None)
            if children:
                self._collect(children)

    def __getitem__(self, key):
        return self._adapter.get_setting(key, self._defaults.get(key))

    def __setitem__(self, key, value):
        self._adapter.set_setting(key, value, True)

    def get(self, key, default=None):
        return self._adapter.get_setting(key, self._defaults.get(key, default))

    def __contains__(self, key):
        return key in self._adapter._settings or key in self._defaults


class _Adapter(BasePlugin):
    """Lets the engine treat a kit plugin like any other: it routes the engine's calls to handlers."""

    def __init__(self, owner):
        super().__init__()
        self._owner = owner
        self._events: Dict[str, List[Any]] = {}      # event -> [(priority, fn, name filter)]

    def on_plugin_load(self):
        for fn in self._owner._started:
            fn()

    def on_plugin_unload(self):
        for fn in self._owner._stopped:
            fn()

    def create_settings(self):
        return self._owner.settings._rows

    # The engine's four raw hooks, turned into events.

    def _raw(self, event: str, name: str, **fields):
        result = None
        for _priority, fn, wanted in self._events.get(event, ()):
            if wanted and wanted != name and not (wanted.endswith("*") and name.startswith(wanted[:-1])):
                continue
            ev = Event(event, tl_name=name, **fields)
            answer = fn(ev)
            if ev.cancelled or answer is False:
                return ev, True
            result = ev
        return result, False

    def pre_request_hook(self, request_name, account, request):
        ev, cancelled = self._raw("request", request_name, account=account, request=request)
        if cancelled:
            return HookResult(strategy=HookStrategy.CANCEL)
        if ev is not None and ev.request is not request:
            return HookResult(strategy=HookStrategy.MODIFY, request=ev.request)
        return HookResult()

    def post_request_hook(self, request_name, account, response, error):
        self._raw("response", request_name, account=account, response=response, error=error)
        return HookResult()

    def on_update_hook(self, update_name, account, update):
        self._raw("update", update_name, account=account, update=update)
        if update_name in ("TL_updateEditMessage", "TL_updateEditChannelMessage"):
            self._emit("message.edited", Event("message.edited", account=account, message=update.message))
        elif update_name in ("TL_updateDeleteMessages", "TL_updateDeleteChannelMessages"):
            self._emit("message.deleted", Event("message.deleted", account=account, ids=list(update.messages),
                                                 channel_id=int(getattr(update, "channel_id", 0) or 0)))
        return HookResult()

    def on_new_message(self, message):
        text = message.message or ""
        chat_id = int(jclass("org.telegram.messenger.MessageObject").getDialogId(message))
        self._emit("message.new", Event("message.new", message=message, text=text, out=bool(message.out), chat_id=chat_id))

    def on_event(self, name, fields):
        """One of the app's own moments (a chat opened, a link tapped...). True when a handler stopped it."""
        if name not in self._events:
            return False
        return self._emit(name, Event(name, **fields))

    def on_send_message_hook(self, account, params):
        ev = Sending(account, params)
        if self._emit("message.sending", ev):
            return HookResult(strategy=HookStrategy.CANCEL)
        return HookResult(strategy=HookStrategy.MODIFY, params=params)

    def _emit(self, event: str, ev: Event) -> bool:
        """Runs the handlers of ``event``; True when one of them stopped it."""
        for _priority, fn, _name in self._events.get(event, ()):
            answer = fn(ev)
            if event in CANCELLABLE and (ev.cancelled or answer is False):
                return True
        return False


class Plugin:
    """One plugin file's handle on the app. Never created by plugins: import ``plugin`` from ``ngram``."""

    def __init__(self):
        self._adapter = _Adapter(self)
        self._started: List[Callable] = []
        self._stopped: List[Callable] = []
        self._strings: Dict[str, Dict[str, str]] = {}
        self._exports: Dict[str, Callable] = {}
        self._store: Optional[_Store] = None
        self._used = False
        self.settings = _Settings(self._adapter)

    # ---- who it is ---------------------------------------------------------------------

    @property
    def id(self) -> str:
        return self._adapter.id

    @property
    def name(self) -> str:
        return self._adapter.name

    @property
    def version(self) -> str:
        return self._adapter.version

    # ---- life ---------------------------------------------------------------------------

    def started(self, fn: Callable[[], None]):
        """Decorator: runs once the plugin is switched on (and at every start of the app with it)."""
        self._used = True
        self._started.append(fn)
        return fn

    def stopped(self, fn: Callable[[], None]):
        """Decorator: runs when the plugin is switched off or removed. Timers and menu entries go by themselves."""
        self._used = True
        self._stopped.append(fn)
        return fn

    # ---- events -------------------------------------------------------------------------

    def on(self, event: str, name: str = "", priority: int = 0):
        """Decorator: calls the function when ``event`` happens.

        ``event`` is one of ``ngram.EVENTS`` or ``"app:<name>"`` for any of the app's own
        notifications, by the name of its field in ``NotificationCenter``
        (``"app:didReceiveNewMessages"``, ``"app:messagesDeleted"``...). The raw events ``request``,
        ``response`` and ``update`` need ``name``: the class name they watch, e.g.
        ``"TL_messages_sendMessage"``; a trailing ``*`` matches by prefix.
        """
        def register(fn):
            self._used = True
            a = self._adapter
            if event in ("request", "response", "update"):
                if not name:
                    raise ValueError('@plugin.on("%s") needs name="TL_..."' % event)
                a.add_hook(name[:-1] if name.endswith("*") else name, match_substring=name.endswith("*"), priority=priority)
            elif event in ("message.new",):
                a.add_new_message_hook()
            elif event in ("message.edited", "message.deleted"):
                for tl in (("TL_updateEditMessage", "TL_updateEditChannelMessage") if event == "message.edited"
                           else ("TL_updateDeleteMessages", "TL_updateDeleteChannelMessages")):
                    a.add_hook(tl, priority=priority)
            elif event == "message.sending":
                a.add_on_send_message_hook(priority)
            elif not event.startswith("app:") and event not in EVENTS:
                raise ValueError("Unknown event %r. Known: %s, or app:<NotificationCenter name>" % (event, ", ".join(EVENTS)))
            handlers = a._events.setdefault(event, [])
            handlers.append((int(priority), fn, name))
            handlers.sort(key=lambda h: -h[0])
            _host()._hooks_changed()
            return fn
        return register

    def command(self, name: str, help: str = ""):
        """Decorator: a text command. Sending a message that starts with ``name`` calls the function
        with a ``Command``. Return a string to send it instead of the command, nothing to send nothing."""
        def register(fn):
            self._used = True

            def run(args, params):
                return fn(Command(name, args, params))

            self._adapter.add_command(name, run, help)
            return fn
        return register

    def menu(self, place: str, title: str, icon: Optional[str] = None, when: Optional[Callable] = None, order: int = 0):
        """Decorator: an entry in a menu. ``place`` is "message", "chat", "profile" or "main".

        The function gets a ``Context``. ``when`` may be a function of the same ``Context`` that
        says whether the entry should be shown this time.
        """
        if place not in _PLACES:
            raise ValueError('A menu place is one of: %s' % ", ".join(_PLACES))

        def register(fn):
            self._used = True
            condition = (lambda raw: bool(when(Context(raw)))) if callable(when) else None
            self._adapter.add_menu_item(MenuItemData(menu_type=_PLACES[place], text=title, icon=icon,
                                                     on_click=lambda raw: fn(Context(raw)), condition=condition, priority=order))
            return fn
        return register

    # ---- time ---------------------------------------------------------------------------

    def every(self, seconds: float):
        """Decorator: calls the function every ``seconds``, off the UI thread, while the plugin is on."""
        def register(fn):
            self._used = True
            self._started.append(lambda: self._adapter.run_every(seconds, fn))
            return fn
        return register

    def after(self, seconds: float, fn: Callable[[], None]) -> int:
        """Calls ``fn`` once after ``seconds``, off the UI thread. Returns a number for ``cancel``."""
        return self._adapter.run_later(seconds, fn)

    def cancel(self, timer: int):
        self._adapter.cancel_timer(timer)

    # ---- keeping things -----------------------------------------------------------------

    @property
    def store(self) -> _Store:
        """A dictionary that survives restarts (JSON values)."""
        if self._store is None:
            self._store = _Store(os.path.join(self._adapter.get_data_dir(), "store.json"))
        return self._store

    @property
    def folder(self) -> str:
        """A folder of the plugin's own; it is removed together with the plugin."""
        return self._adapter.get_data_dir()

    # ---- words --------------------------------------------------------------------------

    def strings(self, **languages: Dict[str, str]):
        """Texts in several languages: ``plugin.strings(ru={"hi": "Привет"}, en={"hi": "Hello"})``."""
        self._used = True
        for lang, table in languages.items():
            self._strings.setdefault(lang, {}).update(table)

    def t(self, key: str, **values) -> str:
        """The text for ``key`` in the app's language (English, then any language, as a fallback)."""
        lang = str(_Bridge.language())
        for table in (self._strings.get(lang), self._strings.get("en"), *self._strings.values()):
            if table and key in table:
                text = table[key]
                return text.format(**values) if values else text
        return key

    # ---- talking to other plugins -------------------------------------------------------

    def export(self, fn: Callable):
        """Decorator: lets other plugins call this function through ``ngram.use("<id>").<name>(...)``."""
        self._used = True
        self._exports[fn.__name__] = fn
        return fn

    # ---- the log ------------------------------------------------------------------------

    def log(self, *parts):
        self._adapter.logger.info(" ".join(str(p) for p in parts))

    def warn(self, *parts):
        self._adapter.logger.warning(" ".join(str(p) for p in parts))

    def error(self, *parts):
        """Writes to the log; inside an ``except`` block the traceback is added."""
        import sys
        text = " ".join(str(p) for p in parts)
        if sys.exc_info()[0] is not None:
            self._adapter.logger.exception(text)
        else:
            self._adapter.logger.error(text)


class _Exports:
    def __init__(self, plugin_id: str, table: Dict[str, Callable]):
        self._id = plugin_id
        self._table = table

    def __getattr__(self, name):
        try:
            return self._table[name]
        except KeyError:
            raise AttributeError("Plugin %r exports no function %r" % (self._id, name))

    def __contains__(self, name):
        return name in self._table


def use(plugin_id: str):
    """What another plugin has exported, or None when it is not switched on."""
    loaded = _host()._plugins.get(str(plugin_id))
    owner = getattr(loaded, "_owner", None)
    if owner is None or not loaded.enabled:
        return None
    return _Exports(plugin_id, owner._exports)


def _host():
    import plugin_host
    return plugin_host
