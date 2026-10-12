"""Ngram's own: small HTTP helpers for plugins, built on the standard library.

``get``/``post``/``request`` block until the server answers, so they refuse to run on the UI
thread. Call them inside ``client_utils.run_on_queue`` or a timer, or use ``get_async`` /
``post_async``, which do the waiting in the background and call back on the UI thread.

    import http_utils

    def show(response, error):
        if error is None:
            BulletinHelper.show_info(response.json()["joke"])

    http_utils.get_async("https://icanhazdadjoke.com/", show, headers={"Accept": "application/json"})
"""

import json as _json
import threading
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Callable, Dict, Optional

from java import jclass

_Bridge = jclass("org.ngram.messenger.plugins.PluginBridge")

USER_AGENT = "Ngram-Plugin/1.0"


class Response:
    """What came back: ``status``, ``headers`` (a dict), ``content`` (bytes), ``text`` and ``json()``."""

    def __init__(self, status: int, headers: Dict[str, str], content: bytes, url: str):
        self.status = status
        self.status_code = status
        self.headers = headers
        self.content = content
        self.url = url

    @property
    def ok(self) -> bool:
        return 200 <= self.status < 300

    @property
    def text(self) -> str:
        charset = "utf-8"
        for part in self.headers.get("Content-Type", "").split(";"):
            part = part.strip().lower()
            if part.startswith("charset="):
                charset = part[8:] or "utf-8"
        try:
            return self.content.decode(charset, errors="replace")
        except LookupError:
            return self.content.decode("utf-8", errors="replace")

    def json(self) -> Any:
        return _json.loads(self.text)

    def raise_for_status(self):
        if not self.ok:
            raise RuntimeError("HTTP %d for %s" % (self.status, self.url))

    def __repr__(self):
        return "<Response %d, %d bytes>" % (self.status, len(self.content))


def request(url: str, method: str = "GET", params: Optional[Dict[str, Any]] = None, headers: Optional[Dict[str, str]] = None,
            data: Any = None, json: Any = None, timeout: float = 15.0, max_bytes: int = 8 * 1024 * 1024) -> Response:
    """Makes one HTTP request and returns the ``Response``, whatever its status.

    ``params`` are added to the URL, ``data`` is a dict (sent as a form), ``str`` or ``bytes``, and
    ``json`` is any object sent as JSON. Raises ``OSError`` when the server cannot be reached.
    """
    if _Bridge.isUiThread():
        raise RuntimeError("An HTTP request would freeze the interface here: use http_utils.get_async(), "
                           "or call it inside client_utils.run_on_queue()")
    if not url.lower().startswith(("http://", "https://")):
        raise ValueError("Only http:// and https:// addresses are supported")
    if params:
        url += ("&" if "?" in url else "?") + urllib.parse.urlencode(params, doseq=True)
    all_headers = {"User-Agent": USER_AGENT}
    body = None
    if json is not None:
        body = _json.dumps(json).encode("utf-8")
        all_headers["Content-Type"] = "application/json"
    elif isinstance(data, dict):
        body = urllib.parse.urlencode(data, doseq=True).encode("utf-8")
        all_headers["Content-Type"] = "application/x-www-form-urlencoded"
    elif isinstance(data, str):
        body = data.encode("utf-8")
    elif data is not None:
        body = bytes(data)
    all_headers.update(headers or {})
    req = urllib.request.Request(url, data=body, headers=all_headers, method=method.upper())
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return Response(r.status, dict(r.headers.items()), r.read(max_bytes), r.geturl())
    except urllib.error.HTTPError as e:
        # 4xx and 5xx still carry an answer worth reading.
        return Response(e.code, dict(e.headers.items()) if e.headers else {}, e.read(max_bytes), url)


def get(url: str, **kwargs) -> Response:
    return request(url, "GET", **kwargs)


def post(url: str, **kwargs) -> Response:
    return request(url, "POST", **kwargs)


def _async(method: str, url: str, callback: Optional[Callable[[Optional[Response], Optional[Exception]], None]], kwargs):
    import plugin_host

    def work():
        response, error = None, None
        try:
            response = request(url, method, **kwargs)
        except Exception as e:
            error = e
        if callback is not None:
            _Bridge.runOnUi(plugin_host.register_callback(lambda: callback(response, error)), 0)

    threading.Thread(target=work, daemon=True).start()


def get_async(url: str, callback: Optional[Callable[[Optional[Response], Optional[Exception]], None]] = None, **kwargs):
    """Like ``get``, in the background. ``callback(response, error)`` runs on the UI thread; one of the two is None."""
    _async("GET", url, callback, kwargs)


def post_async(url: str, callback: Optional[Callable[[Optional[Response], Optional[Exception]], None]] = None, **kwargs):
    _async("POST", url, callback, kwargs)


def download(url: str, path: str, timeout: float = 30.0, max_bytes: int = 64 * 1024 * 1024) -> str:
    """Saves what ``url`` returns to the file ``path`` and returns ``path``. Not for the UI thread."""
    response = request(url, timeout=timeout, max_bytes=max_bytes)
    response.raise_for_status()
    with open(path, "wb") as f:
        f.write(response.content)
    return path
