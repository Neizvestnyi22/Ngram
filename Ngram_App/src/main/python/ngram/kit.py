"""Rows of a plugin's settings page.

    from ngram import plugin, kit

    plugin.settings.define(
        kit.Section("Приветствие"),
        kit.Text("greeting", "Слово", default="Привет"),
        kit.Switch("loud", "Громко", default=False, note="Добавлять восклицательный знак"),
        kit.Choice("style", "Стиль", ["Обычный", "Деловой"], default=0),
        kit.Note("Слово подставляется в команду .hello"),
        kit.Button("Проверить", on_tap=lambda: ui.toast("Работает")),
        kit.Page("Ещё", kit.Switch("debug", "Подробный журнал", default=False)),
    )

Values are read with ``plugin.settings["greeting"]``.
"""

from typing import Any, Callable, List, Optional


class Section:
    """A heading that opens a group of rows."""

    def __init__(self, title: str):
        self.title = title

    def to_dict(self):
        return {"type": "header", "text": str(self.title)}


class Note:
    """Grey text under a group of rows; it also closes the group."""

    def __init__(self, text: str = ""):
        self.text = text

    def to_dict(self):
        return {"type": "divider", "text": str(self.text or "")}


class Switch:
    def __init__(self, key: str, title: str, default: bool = False, note: str = "", on_change: Optional[Callable[[bool], None]] = None):
        self.key = key
        self.title = title
        self.default = bool(default)
        self.note = note
        self.on_change = on_change

    def to_dict(self):
        return {"type": "switch", "key": self.key, "text": str(self.title), "subtext": str(self.note or ""), "default": self.default}


class Choice:
    """One of several options; the stored value is the number of the option, from 0."""

    def __init__(self, key: str, title: str, options: List[str], default: int = 0, on_change: Optional[Callable[[int], None]] = None):
        self.key = key
        self.title = title
        self.options = list(options)
        self.default = int(default)
        self.on_change = on_change

    def to_dict(self):
        return {"type": "selector", "key": self.key, "text": str(self.title), "default": self.default, "items": [str(o) for o in self.options]}


class Text:
    """A line of text the user types in."""

    def __init__(self, key: str, title: str, default: str = "", note: str = "", multiline: bool = False, limit: int = 0,
                 on_change: Optional[Callable[[str], None]] = None):
        self.key = key
        self.title = title
        self.default = default or ""
        self.note = note
        self.multiline = multiline
        self.limit = int(limit or 0)
        self.on_change = on_change

    def to_dict(self):
        return {"type": "input", "key": self.key, "text": str(self.title), "subtext": str(self.note or ""), "default": str(self.default),
                "multiline": bool(self.multiline), "max": self.limit}


class Number:
    """A whole number between ``low`` and ``high``, picked from a list of values with the given ``step``."""

    def __init__(self, key: str, title: str, low: int, high: int, default: Optional[int] = None, step: int = 1, unit: str = "",
                 on_change: Optional[Callable[[int], None]] = None):
        self.key = key
        self.title = title
        self.values = list(range(int(low), int(high) + 1, max(1, int(step))))
        self.default = self.values[0] if default is None else int(default)
        self.unit = unit
        self.on_change = on_change

    # Stored as the number itself; shown to the app as a choice among ``values``.

    def to_dict(self):
        return {"type": "selector", "key": self.key, "text": str(self.title), "number": True,
                "default": self.values.index(self.default) if self.default in self.values else 0,
                "items": ["%d%s" % (v, (" " + self.unit) if self.unit else "") for v in self.values], "values": self.values}


class Button:
    """A row that does something when tapped."""

    def __init__(self, title: str, on_tap: Callable[[], None], note: str = "", accent: bool = True, danger: bool = False):
        self.title = title
        self.note = note
        self.accent = accent and not danger
        self.danger = danger
        self._on_tap = on_tap

    def on_click(self, _view=None):
        self._on_tap()

    def to_dict(self):
        return {"type": "text", "text": str(self.title), "subtext": str(self.note or ""), "accent": bool(self.accent),
                "red": bool(self.danger), "clickable": True, "sub": False}


class Page:
    """A row that opens another page of rows."""

    def __init__(self, title: str, *rows: Any, note: str = ""):
        self.title = title
        self.rows = list(rows)
        self.note = note

    def create_sub_fragment(self):
        return self.rows

    def to_dict(self):
        return {"type": "text", "text": str(self.title), "subtext": str(self.note or ""), "accent": False, "red": False,
                "clickable": False, "sub": True}
