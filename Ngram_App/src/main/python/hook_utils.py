"""Reflection helpers of the exteraGram SDK.

Reading and writing fields of the app's Java objects works here, because Ngram 3 is built on
Telegram for Android. Replacing methods (``hook_method`` and friends) needs a hooking framework
that Ngram does not ship; those raise a clear error.
"""

from typing import Any, Optional

from java import jclass


def find_class(name: str) -> Optional[Any]:
    """The Java class of that name, or ``None`` if the app has none."""
    try:
        return jclass(name)
    except Exception:
        return None


def _field(cls, name: str):
    while cls is not None:
        try:
            field = cls.getDeclaredField(name)
            field.setAccessible(True)
            return field
        except Exception:
            cls = cls.getSuperclass()
    raise AttributeError("no field named " + name)


def get_private_field(obj: Any, name: str) -> Any:
    return _field(obj.getClass(), name).get(obj)


def set_private_field(obj: Any, name: str, value: Any):
    _field(obj.getClass(), name).set(obj, value)


def _java_class(cls: Any):
    """``cls`` is a class name or what ``jclass``/``find_class`` returned."""
    if isinstance(cls, str):
        return jclass("java.lang.Class").forName(cls)
    return cls.getClass()


def get_static_private_field(cls: Any, name: str) -> Any:
    return _field(_java_class(cls), name).get(None)


def set_static_private_field(cls: Any, name: str, value: Any):
    _field(_java_class(cls), name).set(None, value)


def _unsupported(name):
    def missing(*args, **kwargs):
        raise NotImplementedError(name + "() replaces a method of the app, which needs a hooking framework Ngram does not include. "
                                  "Use BasePlugin.add_hook(), add_on_send_message_hook() or menu items instead.")

    missing.__name__ = name
    return missing


hook_method = _unsupported("hook_method")
hook_all_methods = _unsupported("hook_all_methods")
hook_all_constructors = _unsupported("hook_all_constructors")
unhook = _unsupported("unhook")
