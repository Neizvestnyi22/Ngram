"""Rows of a plugin's settings page, as returned from ``BasePlugin.create_settings``.

Same constructors as in the exteraGram SDK. ``icon`` takes the drawable names used there
("msg_settings", "msg_delete"…): the common ones are drawn with Ngram's own icons, unknown names
get a generic one. ``Custom`` rows wrap Android views of Telegram for Android and are skipped.
"""

from typing import Any, Callable, List, Optional


class _Item:
    kind = ""

    def to_dict(self) -> dict:
        return {"type": self.kind}


class Header(_Item):
    kind = "header"

    def __init__(self, text: str):
        self.text = text

    def to_dict(self):
        return {"type": self.kind, "text": str(self.text)}


class Divider(_Item):
    kind = "divider"

    def __init__(self, text: Optional[str] = None):
        self.text = text

    def to_dict(self):
        return {"type": self.kind, "text": str(self.text or "")}


class Switch(_Item):
    kind = "switch"

    def __init__(self, key: str, text: str, default: bool, subtext: Optional[str] = None, icon: Optional[str] = None,
                 on_change: Optional[Callable[[bool], None]] = None, on_long_click: Optional[Callable] = None,
                 link_alias: Optional[str] = None):
        self.key = key
        self.text = text
        self.default = bool(default)
        self.subtext = subtext
        self.icon = icon
        self.on_change = on_change
        self.on_long_click = on_long_click
        self.link_alias = link_alias

    def to_dict(self):
        return {"type": self.kind, "key": self.key, "text": str(self.text), "subtext": str(self.subtext or ""), "default": self.default,
                "icon": str(self.icon or ""), "long": self.on_long_click is not None}


class Selector(_Item):
    kind = "selector"

    def __init__(self, key: str, text: str, default: int, items: Optional[List[str]] = None, icon: Optional[str] = None,
                 on_change: Optional[Callable[[int], None]] = None, on_long_click: Optional[Callable] = None,
                 link_alias: Optional[str] = None):
        self.key = key
        self.text = text
        self.default = int(default)
        self.items = list(items or [])
        self.icon = icon
        self.on_change = on_change
        self.on_long_click = on_long_click
        self.link_alias = link_alias

    def to_dict(self):
        return {"type": self.kind, "key": self.key, "text": str(self.text), "default": self.default, "items": [str(i) for i in self.items],
                "icon": str(self.icon or ""), "long": self.on_long_click is not None}


class Input(_Item):
    kind = "input"

    def __init__(self, key: str, text: str, default: Optional[str] = None, subtext: Optional[str] = None,
                 icon: Optional[str] = None, on_change: Optional[Callable[[str], None]] = None,
                 on_long_click: Optional[Callable] = None, link_alias: Optional[str] = None):
        self.key = key
        self.text = text
        self.default = default or ""
        self.subtext = subtext
        self.icon = icon
        self.on_change = on_change
        self.on_long_click = on_long_click
        self.link_alias = link_alias

    def to_dict(self):
        return {"type": self.kind, "key": self.key, "text": str(self.text), "subtext": str(self.subtext or ""), "default": str(self.default),
                "icon": str(self.icon or ""), "long": self.on_long_click is not None}


class Text(_Item):
    kind = "text"

    def __init__(self, text: str, subtext: Optional[str] = None, icon: Optional[str] = None, accent: bool = False,
                 red: bool = False, on_click: Optional[Callable] = None, on_long_click: Optional[Callable] = None,
                 create_sub_fragment: Optional[Callable[[], List[Any]]] = None, link_alias: Optional[str] = None):
        self.text = text
        self.subtext = subtext
        self.icon = icon
        self.accent = accent
        self.red = red
        self.on_click = on_click
        self.on_long_click = on_long_click
        self.create_sub_fragment = create_sub_fragment
        self.link_alias = link_alias

    def to_dict(self):
        return {
            "type": self.kind, "text": str(self.text), "subtext": str(self.subtext or ""), "accent": bool(self.accent),
            "red": bool(self.red), "clickable": self.on_click is not None, "sub": self.create_sub_fragment is not None,
            "icon": str(self.icon or ""), "long": self.on_long_click is not None,
        }


class EditText(_Item):
    kind = "edit"

    def __init__(self, key: str, hint: str, default: Optional[str] = None, multiline: bool = False, max_length: int = 0,
                 mask: Optional[str] = None, on_change: Optional[Callable[[str], None]] = None):
        self.key = key
        self.hint = hint
        self.default = default or ""
        self.multiline = multiline
        self.max_length = int(max_length or 0)
        self.mask = mask
        self.on_change = on_change

    def to_dict(self):
        return {"type": self.kind, "key": self.key, "text": str(self.hint), "default": str(self.default),
                "multiline": bool(self.multiline), "max": self.max_length}


class Custom(_Item):
    """A row built from Telegram-Android views. Ngram has no such views, so the row is left out."""

    kind = "custom"

    def __init__(self, item: Any = None, view: Any = None, factory: Any = None, factory_args: Any = None,
                 on_click: Optional[Callable] = None, on_long_click: Optional[Callable] = None,
                 create_sub_fragment: Optional[Callable[[], List[Any]]] = None, link_alias: Optional[str] = None):
        self.on_click = on_click
        self.create_sub_fragment = create_sub_fragment

    def to_dict(self):
        return {"type": self.kind}
