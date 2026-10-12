"""Talking to Telegram from a plugin.

Function names follow the exteraGram SDK. Ngram 3 is built on Telegram for Android, so the
objects are the real ones: requests are ``TLRPC`` classes (``from org.telegram.tgnet import
TLRPC``), and ``get_messages_controller()`` and its neighbours return the app's own controllers.
"""

import threading
from typing import Any, Callable, Optional

from java import dynamic_proxy, jclass

_Bridge = jclass("org.ngram.messenger.plugins.PluginBridge")
_Utilities = jclass("org.telegram.messenger.Utilities")
_UserConfig = jclass("org.telegram.messenger.UserConfig")
_AccountInstance = jclass("org.telegram.messenger.AccountInstance")
_RequestDelegate = jclass("org.telegram.tgnet.RequestDelegate")

STAGE_QUEUE = "stageQueue"
GLOBAL_QUEUE = "globalQueue"
CACHE_CLEAR_QUEUE = "cacheClearQueue"
SEARCH_QUEUE = "searchQueue"
PHONE_BOOK_QUEUE = "phoneBookQueue"
THEME_QUEUE = "themeQueue"
EXTERNAL_NETWORK_QUEUE = "externalNetworkQueue"
PLUGINS_QUEUE = "pluginsQueue"


def _host():
    import plugin_host
    return plugin_host


def _account(account: Optional[int] = None) -> int:
    return int(_UserConfig.selectedAccount if account is None or account < 0 else account)


# ---- queues ------------------------------------------------------------------------------


def get_queue_by_name(name: str):
    """One of the app's dispatch queues (``Utilities.stageQueue`` and so on), or ``None`` for the plugins' own."""
    if name == PLUGINS_QUEUE:
        return None
    return getattr(_Utilities, name, None)


def run_on_queue(func: Callable[[], None], queue: str = PLUGINS_QUEUE, delay_ms: int = 0):
    """Runs ``func`` off the UI thread, after ``delay_ms`` milliseconds if given."""

    def guarded():
        try:
            func()
        except Exception:
            _host().report("run_on_queue")

    if delay_ms and delay_ms > 0:
        timer = threading.Timer(delay_ms / 1000.0, guarded)
        timer.daemon = True
        timer.start()
    else:
        threading.Thread(target=guarded, daemon=True).start()


# ---- requests ----------------------------------------------------------------------------


class RequestCallback(dynamic_proxy(_RequestDelegate)):
    """Wraps ``fn(response, error)`` as the delegate Telegram's network code calls back."""

    def __init__(self, fn: Callable[[Any, Any], None]):
        super().__init__()
        self._fn = fn

    def run(self, response, error):
        try:
            self._fn(response, error)
        except Exception:
            _host().report("request callback")

    def __call__(self, response, error):
        self._fn(response, error)


def send_request(request: Any, fn: Optional[Callable[[Any, Any], None]] = None, account: Optional[int] = None) -> int:
    """Sends a ``TLRPC`` request. ``fn(response, error)`` is called on a network thread; one of the two is ``None``."""
    connections = get_connections_manager(account)
    callback = fn if isinstance(fn, RequestCallback) else RequestCallback(fn or (lambda response, error: None))
    return int(connections.sendRequest(request, callback))


def send_request_sync(request: Any, timeout: float = 15.0, account: Optional[int] = None) -> Any:
    """Ngram's own: sends a request and waits for the answer.

    Returns the response; raises ``RuntimeError`` with Telegram's error text and ``TimeoutError`` when
    no answer came in ``timeout`` seconds. It blocks, so it must not be called on the UI thread (hooks,
    menu clicks, settings callbacks): wrap the work in ``run_on_queue``.
    """
    if _Bridge.isUiThread():
        raise RuntimeError("send_request_sync() would freeze the interface here: call it inside run_on_queue(), "
                           "or use send_request() with a callback")
    done = threading.Event()
    box = {}

    def finish(response, error):
        box["response"], box["error"] = response, error
        done.set()

    send_request(request, finish, account)
    if not done.wait(timeout):
        raise TimeoutError("Telegram did not answer in %s seconds" % timeout)
    if box["error"] is not None:
        raise RuntimeError("%s (code %s)" % (box["error"].text, box["error"].code))
    return box["response"]


# ---- sending -----------------------------------------------------------------------------


def _reply_object(reply):
    """A ``MessageObject`` to reply to, or ``None``: plugins pass the object they got in a menu context."""
    return reply if reply is not None and hasattr(reply, "messageOwner") else None


def send_text(peer_id: int, text: str, replyToMsg: Any = None, parse_mode: Optional[str] = None, account: Optional[int] = None):
    """Sends a text message. ``parse_mode`` may be "markdown" or "html"; the text is then parsed the way the app does."""
    _Bridge.sendText(_account(account), int(peer_id), str(text), _reply_object(replyToMsg), (parse_mode or "").lower())


def send_photo(peer_id: int, photo_path: str, caption: Optional[str] = None, high_quality: bool = False,
               parse_mode: Optional[str] = None, account: Optional[int] = None):
    _Bridge.sendFile(_account(account), "photo", int(peer_id), str(photo_path), caption or "")


def send_document(peer_id: int, file_path: str, caption: Optional[str] = None, account: Optional[int] = None):
    _Bridge.sendFile(_account(account), "document", int(peer_id), str(file_path), caption or "")


def send_video(peer_id: int, video_path: str, caption: Optional[str] = None, account: Optional[int] = None):
    _Bridge.sendFile(_account(account), "video", int(peer_id), str(video_path), caption or "")


def send_audio(peer_id: int, audio_path: str, caption: Optional[str] = None, account: Optional[int] = None):
    _Bridge.sendFile(_account(account), "audio", int(peer_id), str(audio_path), caption or "")


def send_message(params: dict, parse_mode: Optional[str] = None, account: Optional[int] = None):
    """Low-level send. Understood keys: ``peer``, ``message``, ``caption``, ``photo``, ``document``, ``path``, ``replyToMsg``."""
    peer = int(params.get("peer", 0))
    path = params.get("path") or params.get("photo") or params.get("document")
    if isinstance(path, str) and path:
        kind = "photo" if params.get("photo") else "document"
        _Bridge.sendFile(_account(account), kind, peer, path, str(params.get("caption") or params.get("message") or ""))
    else:
        _Bridge.sendText(_account(account), peer, str(params.get("message") or ""), _reply_object(params.get("replyToMsg")),
                         (parse_mode or "").lower())


def edit_message(message_obj: Any, text: Optional[str] = None, file_path: Optional[str] = None,
                 with_spoiler: bool = False, parse_mode: Optional[str] = None, account: Optional[int] = None):
    """Replaces the text of a message of yours. ``message_obj`` is the app's ``MessageObject``."""
    if text is None:
        raise NotImplementedError("Replacing media is not supported; pass text=")
    _Bridge.editText(_account(account), message_obj, str(text))


# ---- the app and the account -------------------------------------------------------------


def get_account_instance(account: Optional[int] = None):
    return _AccountInstance.getInstance(_account(account))


def get_messages_controller(account: Optional[int] = None):
    return get_account_instance(account).getMessagesController()


def get_contacts_controller(account: Optional[int] = None):
    return get_account_instance(account).getContactsController()


def get_media_data_controller(account: Optional[int] = None):
    return get_account_instance(account).getMediaDataController()


def get_connections_manager(account: Optional[int] = None):
    return get_account_instance(account).getConnectionsManager()


def get_location_controller(account: Optional[int] = None):
    return get_account_instance(account).getLocationController()


def get_notifications_controller(account: Optional[int] = None):
    return get_account_instance(account).getNotificationsController()


def get_messages_storage(account: Optional[int] = None):
    return get_account_instance(account).getMessagesStorage()


def get_send_messages_helper(account: Optional[int] = None):
    return get_account_instance(account).getSendMessagesHelper()


def get_file_loader(account: Optional[int] = None):
    return get_account_instance(account).getFileLoader()


def get_secret_chat_helper(account: Optional[int] = None):
    return get_account_instance(account).getSecretChatHelper()


def get_download_controller(account: Optional[int] = None):
    return get_account_instance(account).getDownloadController()


def get_notifications_settings(account: Optional[int] = None):
    return get_account_instance(account).getNotificationsSettings()


def get_notification_center(account: Optional[int] = None):
    return get_account_instance(account).getNotificationCenter()


def get_media_controller():
    return jclass("org.telegram.messenger.MediaController").getInstance()


def get_user_config(account: Optional[int] = None):
    return get_account_instance(account).getUserConfig()


def get_last_fragment():
    """The screen on top, or ``None`` before the app has drawn one."""
    return _Bridge.lastFragment()


def get_current_chat_id() -> int:
    """Ngram's own: the chat on screen (negative for groups and channels), or 0."""
    return int(_Bridge.currentChatId())


def get_chat(chat_id: int, account: Optional[int] = None):
    """Ngram's own: the cached ``TLRPC.Chat`` of a group or channel (its id without the minus), or ``None``."""
    return get_messages_controller(account).getChat(abs(int(chat_id)))


def get_user(user_id: int, account: Optional[int] = None):
    """Ngram's own: the cached ``TLRPC.User``, or ``None``."""
    return get_messages_controller(account).getUser(int(user_id))


def get_me(account: Optional[int] = None):
    """Ngram's own: the signed-in account as a ``TLRPC.User``, or ``None`` before sign-in."""
    return get_user_config(account).getCurrentUser()


def open_chat(chat_id: int):
    """Ngram's own: opens a chat on screen; groups and channels have a negative id."""
    _Bridge.openChat(int(chat_id))


def get_app_version() -> str:
    """Ngram's own: the app's version name, e.g. "3.1"."""
    return str(_Bridge.appVersion())


def is_demo() -> bool:
    """Ngram's own: True in the debug demo mode, where nothing is really sent."""
    return bool(_Bridge.isDemo())


class NotificationCenterDelegate(dynamic_proxy(jclass("org.telegram.messenger.NotificationCenter").NotificationCenterDelegate)):
    """Subclass and override ``didReceivedNotification`` to listen to the app's event bus."""

    def __init__(self):
        super().__init__()

    def didReceivedNotification(self, id, account, args):
        pass
