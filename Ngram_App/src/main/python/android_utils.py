"""Android helpers for plugins, with the names of the exteraGram SDK."""

from typing import Any, Callable

from java import dynamic_proxy, jclass
from java.lang import Runnable

_Bridge = jclass("org.ngram.messenger.plugins.PluginBridge")


def _host():
    import plugin_host
    return plugin_host


class R(dynamic_proxy(Runnable)):
    """Wraps a Python callable as a Java ``Runnable``."""

    def __init__(self, fn: Callable[[], None]):
        super().__init__()
        self._fn = fn

    def run(self):
        try:
            self._fn()
        except Exception:
            _host().report("Runnable")


def _click_listener():
    View = jclass("android.view.View")

    class _OnClick(dynamic_proxy(View.OnClickListener)):
        def __init__(self, fn):
            super().__init__()
            self._fn = fn

        def onClick(self, view):
            try:
                self._fn(view)
            except Exception:
                _host().report("OnClickListener")

    class _OnLongClick(dynamic_proxy(View.OnLongClickListener)):
        def __init__(self, fn):
            super().__init__()
            self._fn = fn

        def onLongClick(self, view):
            try:
                return bool(self._fn(view))
            except Exception:
                _host().report("OnLongClickListener")
                return False

    return _OnClick, _OnLongClick


def OnClickListener(fn: Callable[[Any], None]):
    return _click_listener()[0](fn)


def OnLongClickListener(fn: Callable[[Any], bool]):
    return _click_listener()[1](fn)


def run_on_ui_thread(func: Callable[[], None], delay: int = 0):
    """Runs ``func`` on the UI thread; ``delay`` is in milliseconds."""
    _Bridge.runOnUi(_host().register_callback(lambda: func()), int(delay or 0))


def log(data: Any):
    _Bridge.log("", "D", str(data))


def copy_to_clipboard(text: str):
    _Bridge.copyToClipboard(str(text))


def get_clipboard_text() -> str:
    """Ngram's own: the text on the clipboard, or "" (Android only hands it over while the app is on screen)."""
    return str(_Bridge.clipboardText() or "")


def open_url(url: str):
    """Ngram's own: opens a link the way a tap on it would: Telegram links inside the app, others in the browser."""
    _Bridge.openUrl(str(url))


def share_text(text: str):
    """Ngram's own: Android's share sheet with the given text."""
    _Bridge.shareText(str(text))


def is_ui_thread() -> bool:
    """Ngram's own: whether the caller is on the UI thread, where nothing slow may run."""
    return bool(_Bridge.isUiThread())
