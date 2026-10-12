"""Alerts, with the builder of the exteraGram SDK. They are drawn as the app's own alerts.

Buttons, a title, a message and a list of items are supported. Options that only make sense
for Telegram-Android dialogs (custom views, top images, theme keys) are accepted and ignored.
"""

from typing import Any, Callable, Dict, List, Optional

from java import jclass

_Bridge = jclass("org.ngram.messenger.plugins.PluginBridge")


def prompt(title: str, message: str = "", default: str = "", on_done: Optional[Callable[[str], None]] = None,
           placeholder: str = ""):
    """Ngram's own: an alert with a text field. ``on_done(text)`` is called when the user confirms."""
    import plugin_host
    _Bridge.prompt(str(title), str(message or ""), str(placeholder or ""), str(default or ""),
                   plugin_host.register_callback(on_done) if on_done is not None else 0)


class AlertDialogBuilder:
    ALERT_TYPE_MESSAGE = 0
    ALERT_TYPE_LOADING = 2
    ALERT_TYPE_SPINNER = 3
    BUTTON_POSITIVE = -1
    BUTTON_NEGATIVE = -2
    BUTTON_NEUTRAL = -3

    def __init__(self, context: Any = None, progress_style: int = ALERT_TYPE_MESSAGE, resources_provider: Any = None):
        self._title = ""
        self._message = ""
        self._buttons: Dict[int, tuple] = {}
        self._items: Optional[List[str]] = None
        self._items_listener = None
        self._on_dismiss = None
        self._shown = False
        self._red = set()
        self._loading = progress_style in (self.ALERT_TYPE_LOADING, self.ALERT_TYPE_SPINNER)

    # ---- content ---------------------------------------------------------------------

    def set_title(self, title: str):
        self._title = str(title)
        return self

    def set_message(self, message: str):
        self._message = str(message)
        return self

    def set_message_text_view_clickable(self, clickable: bool):
        return self

    def set_view(self, view: Any, height: int = -2):
        return self

    def set_items(self, items: List[str], listener: Optional[Callable[["AlertDialogBuilder", int], None]] = None,
                  icons: Optional[List[int]] = None):
        self._items = [str(i) for i in items]
        self._items_listener = listener
        return self

    # ---- buttons ---------------------------------------------------------------------

    def set_positive_button(self, text: str, listener: Optional[Callable[["AlertDialogBuilder", int], None]] = None):
        self._buttons[self.BUTTON_POSITIVE] = (str(text), listener)
        return self

    def set_negative_button(self, text: str, listener: Optional[Callable[["AlertDialogBuilder", int], None]] = None):
        self._buttons[self.BUTTON_NEGATIVE] = (str(text), listener)
        return self

    def set_neutral_button(self, text: str, listener: Optional[Callable[["AlertDialogBuilder", int], None]] = None):
        self._buttons[self.BUTTON_NEUTRAL] = (str(text), listener)
        return self

    def make_button_red(self, button_type: int):
        self._red.add(int(button_type))
        return self

    # ---- listeners -------------------------------------------------------------------

    def set_on_back_button_listener(self, listener=None):
        return self

    def set_on_dismiss_listener(self, listener=None):
        self._on_dismiss = listener
        return self

    def set_on_cancel_listener(self, listener=None):
        return self

    # ---- appearance (accepted for compatibility) -------------------------------------

    def set_top_image(self, res_id: int, background_color: int):
        return self

    def set_top_drawable(self, drawable: Any, background_color: int):
        return self

    def set_top_animation(self, res_id: int, size: int, auto_repeat: bool, background_color: int, layer_colors=None):
        return self

    def set_dim_enabled(self, enabled: bool):
        return self

    def set_dialog_button_color_key(self, theme_key: int):
        return self

    def set_blurred_background(self, blur: bool, blur_behind_if_possible: bool = True):
        return self

    def set_cancelable(self, cancelable: bool):
        return self

    def set_canceled_on_touch_outside(self, cancel: bool):
        return self

    def set_progress(self, progress: int):
        return self

    # ---- lifecycle -------------------------------------------------------------------

    def create(self):
        return self

    def show(self):
        import plugin_host

        if self._loading:
            # A spinner dialog has nothing to press: Ngram shows the text as a notice instead.
            _Bridge.toast(self._title or self._message or "…")
            self._shown = True
            return self
        if self._items is not None:
            def picked(index):
                if self._items_listener is not None and index >= 0:
                    self._items_listener(self, int(index))
                if self._on_dismiss is not None:
                    self._on_dismiss(self)

            _Bridge.choose(self._title, self._items, plugin_host.register_callback(picked))
        else:
            def pressed(which):
                entry = self._buttons.get(int(which))
                if entry is not None and entry[1] is not None:
                    entry[1](self, int(which))
                if self._on_dismiss is not None:
                    self._on_dismiss(self)

            def text(which):
                entry = self._buttons.get(which)
                return entry[0] if entry is not None else ""

            _Bridge.alert(self._title, self._message, text(self.BUTTON_POSITIVE), text(self.BUTTON_NEGATIVE),
                          text(self.BUTTON_NEUTRAL), plugin_host.register_callback(pressed), self.BUTTON_POSITIVE in self._red)
        self._shown = True
        return self

    def dismiss(self):
        # Ngram's alerts close themselves when a button is pressed.
        self._shown = False

    def get_dialog(self):
        return None

    def get_button(self, button_type: int):
        return None
