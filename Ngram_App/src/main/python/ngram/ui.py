"""What a plugin can show: notes at the bottom of the screen, questions, lists, sheets.

Everything here may be called from any thread; the app shows it on its own.
"""

from typing import Callable, Dict, List, Optional, Sequence, Tuple, Union

from java import jarray, jclass
from java.lang import String

_Bridge = jclass("org.ngram.messenger.plugins.PluginBridge")


def _callback(fn):
    import plugin_host
    return plugin_host.register_callback(fn) if fn is not None else 0


def toast(text: str, kind: str = "info", button: Optional[Tuple[str, Callable[[], None]]] = None):
    """A note at the bottom of the screen. ``kind`` is "info", "ok" or "error".

    ``button=("Отменить", fn)`` adds a button to it.
    """
    title, action = button if button else ("", None)
    _Bridge.notice(str(text), str(kind), str(title or ""), _callback(action))


def alert(title: str, text: str = "", ok: str = "", cancel: str = "", on_ok: Optional[Callable[[], None]] = None,
          on_cancel: Optional[Callable[[], None]] = None, danger: bool = False):
    """A dialog with one or two buttons. ``danger`` paints the confirming button red."""
    def answer(which):
        handler = on_ok if int(which) == -1 else on_cancel
        if handler is not None:
            handler()

    _Bridge.alert(str(title), str(text), str(ok or ""), str(cancel or ""), "", _callback(answer), bool(danger))


def ask(title: str, on_text: Callable[[str], None], hint: str = "", text: str = "", note: str = ""):
    """Asks for a line of text; ``on_text`` gets what was typed."""
    _Bridge.prompt(str(title), str(note or ""), str(hint or ""), str(text or ""), _callback(lambda value: on_text(str(value))))


def choose(title: str, options: Sequence[str], on_pick: Callable[[int], None]):
    """A list to pick from; ``on_pick`` gets the number of the option, from 0."""
    _Bridge.choose(str(title), jarray(String)([str(o) for o in options]), _callback(lambda index: on_pick(int(index))))


def sheet(title: str, rows: Union[Dict[str, object], Sequence[Tuple[str, object]]]):
    """A sheet of labelled values; tapping a value copies it. ``rows`` is a dict or (label, value) pairs."""
    pairs = list(rows.items()) if isinstance(rows, dict) else list(rows)
    _Bridge.sheet(str(title), jarray(String)([str(label) for label, _ in pairs]), jarray(String)([str(value) for _, value in pairs]))


def copy(text: str):
    """Puts text on the clipboard."""
    _Bridge.copyToClipboard(str(text))


def clipboard() -> str:
    return str(_Bridge.clipboardText())


def share(text: str):
    """Opens the system's Share sheet with the text."""
    _Bridge.shareText(str(text))


def open_url(url: str):
    _Bridge.openUrl(str(url))


def on_ui(fn: Callable[[], None], delay: float = 0.0):
    """Runs ``fn`` on the UI thread, after ``delay`` seconds. Needed for calling the app's views directly."""
    _Bridge.runOnUi(_callback(fn), int(delay * 1000))


def language() -> str:
    """The language the app is shown in: "ru", "en"..."""
    return str(_Bridge.language())
