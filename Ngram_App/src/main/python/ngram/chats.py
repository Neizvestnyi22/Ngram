"""Chats and messages.

A chat is known by its number: positive for a person, negative for a group or a channel —
the same number the app calls a dialog id.
"""

from typing import Any, Optional

from java import jclass

_Bridge = jclass("org.ngram.messenger.plugins.PluginBridge")


def _account(account: Optional[int]) -> int:
    return int(_Bridge.account()) if account is None else int(account)


def current() -> int:
    """The chat on screen, or 0 when the screen is not a chat."""
    return int(_Bridge.currentChatId())


def send(chat_id: int, text: str, reply_to: Any = None, markdown: bool = True, account: Optional[int] = None):
    """Sends a text message. ``reply_to`` is the app's MessageObject to answer.

    Messages a plugin sends do not pass through plugins' own ``message.sending`` handlers, so a
    plugin cannot loop on itself.
    """
    _Bridge.sendText(_account(account), int(chat_id), str(text), reply_to, "markdown" if markdown else "")


def send_file(chat_id: int, path: str, caption: str = "", as_photo: bool = False, account: Optional[int] = None):
    """Sends a file from the device."""
    _Bridge.sendFile(_account(account), "photo" if as_photo else "document", int(chat_id), str(path), str(caption or ""))


def edit(message: Any, text: str, account: Optional[int] = None):
    """Replaces the text of one of the user's own messages (``message`` is the app's MessageObject)."""
    _Bridge.editText(_account(account), message, str(text))


def open(chat_id: int):
    """Opens a chat on screen."""
    _Bridge.openChat(int(chat_id))


def user(user_id: int, account: Optional[int] = None):
    """The app's ``TLRPC.User`` for a person it knows, or None."""
    return jclass("org.telegram.messenger.MessagesController").getInstance(_account(account)).getUser(int(user_id))


def chat(chat_id: int, account: Optional[int] = None):
    """The app's ``TLRPC.Chat`` for a group or channel it knows, or None. Takes the number with or without the minus."""
    return jclass("org.telegram.messenger.MessagesController").getInstance(_account(account)).getChat(abs(int(chat_id)))


def me(account: Optional[int] = None):
    """The user's own ``TLRPC.User``."""
    return jclass("org.telegram.messenger.UserConfig").getInstance(_account(account)).getCurrentUser()


def title(chat_id: int, account: Optional[int] = None) -> str:
    """The name of a person, group or channel, as the chat list shows it."""
    chat_id = int(chat_id)
    if chat_id > 0:
        u = user(chat_id, account)
        if u is None:
            return ""
        return (" ".join(p for p in (u.first_name, u.last_name) if p)) or (u.username or "")
    c = chat(chat_id, account)
    return (c.title or "") if c is not None else ""
