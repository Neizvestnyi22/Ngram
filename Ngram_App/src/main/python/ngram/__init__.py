"""Ngram's own plugin kit.

A plugin is one Python file that starts with a passport — comment lines the app reads without
running the file — and then says what it does with decorators::

    # @id          hello
    # @name        Привет, мир
    # @version     1.0
    # @author      Вы
    # @about       Команда .hello и пункт в меню сообщения
    # @needs       send

    from ngram import plugin, ui

    @plugin.command(".hello", help="отправить приветствие")
    def hello(cmd):
        return "Привет, %s!" % (cmd.args or "мир")

    @plugin.menu("message", "Сколько знаков", icon="msg_info")
    def count(ctx):
        ui.toast("Знаков: %d" % len(ctx.text))

``plugin`` is this file's own handle: every plugin file gets a different one. ``ui``, ``chats``,
``net``, ``tg`` and ``kit`` are shared modules of helpers.

Plugins written for exteraGram (a class derived from ``BasePlugin``) are loaded as well: their
SDK lives next to this package under its original module names.
"""

from . import chats, kit, net, tg, ui
from ._plugin import EVENTS, Context, Event, Plugin, use

# Replaced by the engine with the handle of the file being loaded, just before the file runs.
plugin = None

__all__ = ["plugin", "ui", "chats", "net", "tg", "kit", "use", "Plugin", "Event", "Context", "EVENTS"]
