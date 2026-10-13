"""The network: plain HTTP for plugins.

    from ngram import net, ui

    @plugin.command(".joke")
    def joke(cmd):
        net.get("https://icanhazdadjoke.com/", headers={"Accept": "application/json"},
                then=lambda r, err: ui.toast(r.json()["joke"] if r else str(err)))

With ``then`` a request runs in the background and ``then(response, error)`` is called on the
UI thread. Without it the call waits for the answer and so must not be made on the UI thread
(a timer or ``plugin.after`` is a fine place).
"""

from typing import Any, Callable, Optional

import http_utils
from http_utils import Response  # noqa: F401  (part of this module's interface)


def get(url: str, then: Optional[Callable[[Optional[Response], Optional[Exception]], None]] = None, **options: Any):
    """GET. ``options``: params, headers, timeout."""
    if then is not None:
        http_utils.get_async(url, then, **options)
        return None
    return http_utils.get(url, **options)


def post(url: str, then: Optional[Callable[[Optional[Response], Optional[Exception]], None]] = None, **options: Any):
    """POST. ``options``: data (form dict, str or bytes), json, params, headers, timeout."""
    if then is not None:
        http_utils.post_async(url, then, **options)
        return None
    return http_utils.post(url, **options)


def download(url: str, path: str, timeout: float = 30.0) -> str:
    """Saves what ``url`` returns into the file ``path``. Waits; not for the UI thread."""
    return http_utils.download(url, path, timeout=timeout)
