"""The app underneath: Telegram's own classes and its API, for what the kit has no word for.

    from ngram import tg

    req = tg.TL.TL_help_getNearestDc()
    tg.request(req, lambda response, error: plugin.log(response.country if response else error.text))

Everything of ``org.telegram.*`` can also be imported directly: ``from org.telegram.messenger import
MessageObject``. What the app's classes look like is up to Telegram and changes between its
versions; the rest of the kit does not.
"""

from typing import Any, Callable, Optional

from java import dynamic_proxy, jclass

import client_utils

_Bridge = jclass("org.ngram.messenger.plugins.PluginBridge")

#: The app's ``TLRPC`` class: every request, object and update of Telegram's API.
TL = jclass("org.telegram.tgnet.TLRPC")


def account() -> int:
    """The number of the account on screen (0 unless several accounts are signed in)."""
    return int(_Bridge.account())


def request(req: Any, then: Optional[Callable[[Any, Any], None]] = None, account: Optional[int] = None) -> int:
    """Sends a raw API request. ``then(response, error)`` is called on a network thread; one of the two is None."""
    return client_utils.send_request(req, then, account)


def call(req: Any, timeout: float = 15.0, account: Optional[int] = None) -> Any:
    """Sends a raw API request and waits for the answer. Raises on an error. Not for the UI thread."""
    return client_utils.send_request_sync(req, timeout, account)


def controller(account: Optional[int] = None):
    """The app's ``MessagesController``: what it knows about chats, users and dialogs."""
    return client_utils.get_messages_controller(account)


def java(name: str):
    """A class of the app or of Android by its full name: ``tg.java("org.telegram.messenger.FileLoader")``."""
    return jclass(name)


def listener(interface: str, **methods: Callable):
    """An object implementing a Java interface with Python functions, for the app's callbacks::

        tg.listener("java.lang.Runnable", run=lambda: plugin.log("ran"))
    """
    base = dynamic_proxy(jclass(interface))

    class _Listener(base):
        def __init__(self):
            super().__init__()

    for method, fn in methods.items():
        setattr(_Listener, method, (lambda f: lambda self, *args: f(*args))(fn))
    return _Listener()
