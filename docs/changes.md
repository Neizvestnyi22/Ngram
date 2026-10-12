# Что изменено в коде Telegram

Ngram основан на Telegram для Android **12.10.6 (7112)**, коммит `f2908b1` репозитория [DrKLO/Telegram](https://github.com/DrKLO/Telegram). Ниже — все изменения, внесённые в его код в октябре 2026 года (версии Ngram 3.0 и 3.1). Каждое помечено в исходниках комментарием `// Ngram:`.

Посмотреть их целиком можно командой:

```bash
git diff f2908b1 -- TMessagesProj build.gradle settings.gradle gradle.properties
```

## Добавлено

| Путь | Что это |
| --- | --- |
| `Ngram_App/` | Модуль приложения: всё собственное Ngram. |
| `TMessagesProj/src/main/java/org/ngram/core/` | `NgramHooks`, `NgramConfig`, `NgramMotion` — посредник между кодом Telegram и Ngram. |
| `docs/`, `README.md`, `README.en.md`, `CHANGELOG.md` | Документация. Исходный `README.md` Telegram заменён. |
| `gradlew.bat` | Запуск сборки на Windows. |

## Изменено: сборка

| Файл | Изменение |
| --- | --- |
| `settings.gradle` | Подключены только `TMessagesProj` и `Ngram_App`. |
| `build.gradle` | Убраны плагины Huawei, Firebase Crashlytics и Google Services; исправлена проверка пути к модулям media на Windows; добавлен плагин Chaquopy (Python для плагинов Ngram). |
| `gradle.properties` | Пакет `org.ngram.messenger`, версия Ngram, меньше памяти для Gradle. |
| `TMessagesProj/build.gradle` | Ключи API из `local.properties` в `BuildConfig`; нативная сборка только с `-PbuildNative`, иначе готовая библиотека из `jniLibs`; фильтр архитектур `-Pabi`; убран плагин Google Services. |

## Изменено: код

| Файл | Изменение |
| --- | --- |
| `messenger/BuildVars.java` | `APP_ID` и `APP_HASH` берутся из `BuildConfig`. Отключены проверка обновлений, вход через Google, ключи доступа, покупки в Google Play, SafetyNet. |
| `messenger/LocaleController.java` | Перед поиском текста спрашивается `NgramHooks.string` — для названия приложения, первого экрана и названий значков. `formatShortNumber` при включённых «точных числах» возвращает число целиком. |
| `messenger/AndroidUtilities.java` | `formatWholeNumber`: то же для «точных чисел». |
| `messenger/SendMessagesHelper.java` | `sendMessage(SendMessageParams)` сначала передаёт параметры в `NgramHooks.onSendMessage` — плагин может изменить сообщение или отменить отправку. |
| `messenger/MessagesController.java` | `NgramHooks.onUpdates` в начале `processUpdates`. |
| `tgnet/ConnectionsManager.java` | `NgramHooks.onRequest` перед отправкой запроса и `onResponse` после ответа; в демо-режиме сетевому слою не сообщается идентификатор пользователя. |
| `ui/LaunchActivity.java` | `NgramHooks.onLaunch` и `onIntent`. |
| `ui/SettingsActivity.java` | Раздел Ngram в списке настроек и переход на его экраны. Собственный номер в заголовке показывается через `NgramConfig.ownPhone`. |
| `ui/ChatActivity.java` | Пункты Ngram в меню сообщения (два места) и в меню чата, их обработка. Двойное нажатие с выбранным пользователем действием (`hasDoubleTap`, `onDoubleTap`), метод `ngramRunOption`. Отключаемый переход к следующему каналу потягиванием. Скрытие клавиатуры при прокрутке. |
| `ui/DialogsActivity.java` | Кнопку камеры для историй и подсказку к ней можно отключить. Свой заголовок вместо надписи Ngram. Ряд историй можно скрыть. |
| `ui/ProfileActivity.java` | Пункт Ngram в меню профиля и его обработка. Собственный номер через `NgramConfig.ownPhone` (два места). |
| `ui/LauncherIconController.java` | Все шесть значков приложения доступны без ограничений: в Ngram это его собственные рисунки. |
| `ui/Cells/ChatMessageCell.java` | Время сообщения через `NgramConfig.time` (секунды). Размер стикеров умножается на `NgramConfig.stickerScale`. |
| `ui/Components/ChatActivityEnterView.java` | Вопрос перед отправкой стикера и GIF. «Сначала прослушать»: при отпускании кнопки записи голосовое ставится на паузу вместо отправки. |
| `ui/Components/voip/VoIPHelper.java` | Вопрос перед звонком человеку. |
| `ui/IntroActivity.java` | Круг на первом экране окрашен в цвета значка Ngram. |
| `ui/Components/ScaleStateListAnimator.java` | Отклик кнопок через `NgramMotion`. |
| `ui/Components/ButtonBounce.java` | Качание при отпускании в режиме «желе». |
| `ui/Components/Switch.java` | Переключатель в виде капсулы (`drawCapsule`). |
| `ui/Components/glass/GlassTabView.java` | Панель вкладок без подписей. |
| `ui/Components/blur3/drawable/BlurredBackgroundDrawable.java` | Плотность, цветной оттенок и блик стекла через `NgramConfig.tint` и `rim`. |
| `ui/Components/blur3/drawable/BlurredBackgroundDrawableRenderNode.java` | Сила преломления умножается на `NgramConfig.refraction`. |
| `ui/Components/blur3/DownscaleScrollableNoiseSuppressor.java` | Радиусы размытия умножаются на `NgramConfig.glassBlur` и `frostBlur`. |

Пути в таблице — относительно `TMessagesProj/src/main/java/org/telegram/`.

## Заменено ресурсами приложения

Одноимённые ресурсы в `Ngram_App/src/main/res` перекрывают ресурсы Telegram, сами файлы Telegram не тронуты:

- `mipmap-anydpi-v26/ic_launcher*.xml` — значок приложения;
- `mipmap-anydpi-v26/icon_2…icon_6_launcher*.xml`, `icon_*_foreground_sa.xml`, `icon_2_background_sa.xml`, `drawable/icon_*_background_sa.xml` — пять других значков Ngram на местах значков Telegram и их изображения в окне выбора;
- `drawable-anydpi-v24/telegram_logo.xml`, `telegram_logo_2.xml` — слово «Ngram» вместо логотипа Telegram (набрано шрифтом Roboto Medium из состава Telegram);
- `drawable-*/intro_tg_plane.png` — буква N на первом экране;
- `drawable-*/notification.png` — значок уведомлений;
- `values/strings.xml` — название приложения.

## Что не изменено

Нативный код (`TMessagesProj/jni`), протокол, шифрование, база данных, подмодули — как в Telegram.
