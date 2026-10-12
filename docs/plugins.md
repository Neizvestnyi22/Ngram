# Плагины

`Настройки → Ngram → Плагины`

Плагин — это файл `.plugin` с программой на Python, которая добавляет в Ngram команды, пункты меню и собственные настройки. Имена в SDK повторяют [exteraGram](https://plugins.exteragram.app/docs), поэтому плагин, написанный по его документации, читается здесь так же.

> [!WARNING]
> Плагин работает внутри приложения с его правами: он может читать то, что видит приложение, и отправлять сообщения от вашего имени. Устанавливайте только те плагины, авторам которых доверяете. Ngram не проверяет чужие плагины и не отвечает за них.

## Как установить

- **Из файла.** Плагины → «Установить из файла».
- **Из чата.** Скачайте файл `.plugin` в сообщении, нажмите на сообщение → «Установить плагин».
- **Пример.** Плагины → «Примеры плагинов»: три плагина, которые идут вместе с приложением.

Перед установкой Ngram показывает название, автора, описание и число строк и спрашивает согласие. Установленный плагин выключен, пока вы сами не включите его на его странице.

Python запускается, только когда включён хотя бы один плагин, — через полторы секунды после старта приложения и в отдельном потоке.

## Страховка

- Плагин, чьи обработчики ошиблись 8 раз подряд, выключается сам; причина видна на его странице.
- Если приложение дважды подряд закрылось в первые секунды после запуска с плагинами, включается **безопасный режим**: ни один плагин не загружается, пока вы не разрешите.
- Обработчик, который работает дольше 300 мс, отмечается в журнале.

Журнал: Плагины → «Журнал» (и `adb logcat -s NgramPlugin`).

## Первый плагин

```python
from base_plugin import BasePlugin, MenuItemData, MenuItemType
from ui.bulletin import BulletinHelper
from ui.settings import Header, Input

__id__ = "hello_world"          # латиница, цифры, _ и -, от 2 до 64 знаков
__name__ = "Привет, мир"
__description__ = "Пункт в меню сообщения и команда .hello"
__author__ = "Вы"
__version__ = "1.0.0"


class HelloPlugin(BasePlugin):
    def on_plugin_load(self):
        self.add_menu_item(MenuItemData(
            menu_type=MenuItemType.MESSAGE_CONTEXT_MENU,
            text="Сказать привет",
            icon="msg_info",
            on_click=self.on_menu,
        ))
        self.add_command(".hello", self.on_command, "отправить приветствие")

    def on_menu(self, context):
        message = context.get("message")            # MessageObject приложения
        BulletinHelper.show_info("Знаков: %d" % len(message.messageOwner.message or ""))

    def on_command(self, args, params):
        return "%s, %s!" % (self.get_setting("greeting", "Привет"), args or "мир")

    def create_settings(self):
        return [Header(text="Приветствие"), Input(key="greeting", text="Слово", default="Привет")]
```

Сохраните как `hello_world.plugin` и установите.

## Что умеет плагин

### Жизненный цикл

| Метод | Когда вызывается |
| --- | --- |
| `on_plugin_load()` | Плагин включили или приложение запустилось с ним. |
| `on_plugin_unload()` | Плагин выключают или удаляют. Таймеры и пункты меню снимаются сами. |

### Команды — собственное дополнение Ngram

```python
self.add_command(".upper", lambda args, params: args.upper(), "заглавными")
```

Когда вы отправляете сообщение, начинающееся с команды, вызывается обработчик. Верните строку — она уйдёт вместо команды; не верните ничего — команда просто исчезнет.

### Перехват отправки

```python
self.add_on_send_message_hook()

def on_send_message_hook(self, account, params):
    params.message = params.message.replace("->", "→")
    return HookResult(strategy=HookStrategy.MODIFY, params=params)
```

`params` — настоящий `SendMessagesHelper.SendMessageParams` приложения: `message`, `peer`, `replyToMsg` и остальные поля. `HookStrategy.CANCEL` отменяет отправку.

### Запросы и обновления

```python
self.add_hook("TL_messages_sendMessage")      # имя класса запроса или обновления

def pre_request_hook(self, request_name, account, request): ...   # до отправки: можно изменить или отменить
def post_request_hook(self, request_name, account, response, error): ...   # ответ: только смотреть
def on_update_hook(self, update_name, account, update): ...       # одно обновление: только смотреть
def on_updates_hook(self, container_name, account, updates): ...  # пачка обновлений: только смотреть
```

`self.add_new_message_hook()` включает `on_new_message(message)` — вызывается с `TLRPC.Message` для каждого нового сообщения.

### Пункты меню

| `MenuItemType` | Где появляется |
| --- | --- |
| `MESSAGE_CONTEXT_MENU` | Меню сообщения. |
| `CHAT_ACTION_MENU` | Меню чата → Ngram. |
| `PROFILE_ACTION_MENU` | Меню профиля → Ngram. |
| `DRAWER_MENU`, `MAIN_MENU` | Экран «Плагины», раздел «Действия плагинов» (бокового меню в Ngram нет). |

Обработчик получает словарь: `account`, `context` (Activity), `fragment`, `dialog_id`, `message`, `user` / `chat`, `userId` / `chatId`. Параметр `condition` может быть функцией от того же словаря: вернула `False` — пункт не показывается.

### Настройки

`create_settings()` возвращает список строк: `Header`, `Divider`, `Switch`, `Selector`, `Input`, `EditText`, `Text` (в том числе с `on_click` и вложенной страницей `create_sub_fragment`). Значения читаются через `self.get_setting(key, default)` и хранятся в файле плагина.

### Интерфейс

```python
from ui.bulletin import BulletinHelper       # show_info, show_error, show_success, show_with_button…
from ui.alert import AlertDialogBuilder, prompt
```

### Telegram

```python
from client_utils import (send_text, send_message, send_request, send_request_sync, run_on_queue,
                          get_messages_controller, get_user_config, get_last_fragment,
                          get_current_chat_id, get_user, get_chat, open_chat)
from org.telegram.tgnet import TLRPC          # классы приложения доступны напрямую
```

`send_text(peer_id, text, parse_mode="markdown")` отправляет текст; сообщения, отправленные самим плагином, через обработчики отправки плагинов не проходят — зациклиться нельзя.

### Прочее

| Что | Как |
| --- | --- |
| Таймеры | `self.run_later(секунды, func)`, `self.run_every(секунды, func)`, `self.cancel_timer(id)` |
| Своя папка | `self.get_data_dir()` — удаляется вместе с плагином |
| Сеть | `http_utils.get_async(url, callback)`, `post_async`; блокирующие `get` / `post` — только вне потока интерфейса |
| Буфер, ссылки | `android_utils.copy_to_clipboard`, `open_url`, `share_text`, `run_on_ui_thread` |
| Журнал | `self.log("…")`, `self.logger.exception("…")` |

## Совместимость с exteraGram

Работает то, что описано выше: хуки запросов и обновлений по именам `TL_…`, перехват отправки, пункты меню, настройки, уведомления и диалоги, `client_utils`, прямой импорт `org.telegram.*` и чтение полей через `hook_utils.get_private_field`.

**Не работает** подмена методов приложения: `hook_method`, `hook_all_methods`, `MethodHook`, `MethodReplacement`. Для неё exteraGram использует встроенный фреймворк перехвата, которого в Ngram нет; такие вызовы завершаются понятной ошибкой, и плагин не загружается. Плагины, построенные только на этом, здесь не заработают.

Отличия:

- ответ на запрос и обновления плагин видит, но не может подменить;
- строки настроек `Custom` (собственные виджеты плагина) не показываются;
- условие пункта меню в виде строки MVEL игнорируется — используйте функцию.

## Отладка

Отладочная сборка принимает файл плагина при запуске — он устанавливается и включается:

```bash
adb push my.plugin /data/local/tmp/my.plugin
adb shell am start -n org.ngram.messenger/org.telegram.messenger.DefaultIcon --es plugin /data/local/tmp/my.plugin
adb logcat -s NgramPlugin
```

## Код

| Файл | Что в нём |
| --- | --- |
| `Ngram_App/src/main/python/` | SDK: `base_plugin`, `plugin_host`, `client_utils`, `android_utils`, `hook_utils`, `http_utils`, `file_utils`, `ui/` |
| `plugins/Plugins.java` | Движок: файлы, запуск Python, включение, хуки, страховка |
| `plugins/PluginBridge.java` | То, что вызывает Python: диалоги, отправка, экран |
| `plugins/PluginMenus.java` | Пункты меню плагинов |
| `ui/PluginsFragment.java`, `ui/PluginFragment.java` | Экраны |
| `Ngram_App/src/main/assets/plugin_examples/` | Примеры |

Python — 3.11, встраивается плагином сборки [Chaquopy](https://chaquo.com/chaquopy/) (лицензия MIT). Он добавляет к приложению около 18 МБ на архитектуру.
