"""Files and folders a plugin may use, with the names of the exteraGram SDK."""

import os
from typing import List, Optional

from java import jclass

_Bridge = jclass("org.ngram.messenger.plugins.PluginBridge")


def get_plugins_dir() -> str:
    return str(_Bridge.pluginsDir())


def get_cache_dir() -> str:
    return str(_Bridge.cacheDir())


def get_files_dir() -> str:
    return os.path.dirname(get_plugins_dir())


def ensure_dir_exists(path: str) -> str:
    os.makedirs(path, exist_ok=True)
    return path


def list_dir(path: str, recursive: bool = False, include_files: bool = True, include_dirs: bool = False,
             extensions: Optional[List[str]] = None) -> List[str]:
    found = []
    for root, dirs, files in os.walk(path):
        if include_dirs:
            found += [os.path.join(root, d) for d in dirs]
        if include_files:
            found += [os.path.join(root, f) for f in files if not extensions or os.path.splitext(f)[1].lower() in extensions]
        if not recursive:
            break
    return found


def read_file(path: str) -> Optional[str]:
    try:
        with open(path, encoding="utf-8") as f:
            return f.read()
    except OSError:
        return None


def write_file(path: str, content: str) -> bool:
    try:
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        return True
    except OSError:
        return False


def delete_file(path: str) -> bool:
    try:
        os.remove(path)
        return True
    except OSError:
        return False