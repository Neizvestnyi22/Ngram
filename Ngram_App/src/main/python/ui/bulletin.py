"""Short on-screen notices: the bulletins that slide in at the bottom of the screen."""

from typing import Callable, Optional

from java import jclass

_Bridge = jclass("org.ngram.messenger.plugins.PluginBridge")


def _callback(fn):
    import plugin_host
    return plugin_host.register_callback(fn) if fn is not None else 0


class BulletinHelper:
    DURATION_SHORT = 1500
    DURATION_LONG = 2750
    DURATION_PROLONG = 5000

    @staticmethod
    def show_info(message: str, fragment=None):
        _Bridge.toast(str(message))

    @staticmethod
    def show_error(message: str, fragment=None):
        _Bridge.toast(str(message))

    @staticmethod
    def show_success(message: str, fragment=None):
        _Bridge.toast(str(message))

    @staticmethod
    def show_simple(text: str, icon_res_id: int = 0, fragment=None):
        _Bridge.toast(str(text))

    @staticmethod
    def show_two_line(title: str, subtitle: str, icon_res_id: int = 0, fragment=None):
        _Bridge.toast("%s\n%s" % (title, subtitle))

    @staticmethod
    def show_with_button(text: str, icon_res_id: int, button_text: str, on_click: Optional[Callable[[], None]],
                         fragment=None, duration: int = DURATION_PROLONG):
        # A toast cannot carry a button here; the choice is offered as a small alert instead.
        _Bridge.alert("", str(text), str(button_text), "", "", _callback((lambda which: on_click()) if on_click else None))

    @staticmethod
    def show_undo(text: str, on_undo: Callable[[], None], on_action: Optional[Callable[[], None]] = None,
                  subtitle: Optional[str] = None, fragment=None):
        def done(which):
            if which == -1:
                on_undo()
            elif on_action is not None:
                on_action()

        _Bridge.alert(str(text), str(subtitle or ""), "Undo", "OK", "", _callback(done))

    @staticmethod
    def show_copied_to_clipboard(message: Optional[str] = None, fragment=None):
        _Bridge.toast(str(message) if message else "Copied")

    @staticmethod
    def show_link_copied(is_private_link_info: bool = False, fragment=None):
        _Bridge.toast("Link copied")

    @staticmethod
    def show_file_saved_to_gallery(is_video: bool = False, amount: int = 1, fragment=None):
        _Bridge.toast("Saved to gallery")

    @staticmethod
    def show_file_saved_to_downloads(file_type_enum_name: str = "UNKNOWN", amount: int = 1, fragment=None):
        _Bridge.toast("Saved to downloads")
