# Сборка

## Что нужно

| | Версия |
| --- | --- |
| JDK | 17 |
| Android SDK | платформа 36, build-tools 36.0.0 |
| Android NDK | 27.2.12479018 |
| CMake | 3.22.1 |
| Git | любой, с поддержкой подмодулей |

Места на диске — около 10 ГБ. Сборка проверена на Windows 11; на Linux и macOS должна идти так же (Telegram собирают именно там), но автором не проверялась.

## 1. Исходники

```bash
git clone --recursive --shallow-submodules https://github.com/Neizvestnyi22/Ngram.git Ngram
```

Если забыли `--recursive`:

```bash
git submodule update --init --recursive --depth=1
```

## 2. Ключи Telegram API

Свои `api_id` и `api_hash` получите на https://my.telegram.org → API development tools. Затем создайте в корне файл `local.properties`:

```properties
sdk.dir=/путь/к/Android/Sdk
telegram.apiId=123456
telegram.apiHash=0123456789abcdef0123456789abcdef
```

Файл не попадает в git. Необязательная строка `maps.apiKey=…` включает карты Google.

> [!WARNING]
> Не публикуйте свои `api_id` и `api_hash` и не вписывайте их в исходники.

## 3. Нативная библиотека — один раз на архитектуру

Это самая долгая часть: около 20 минут на ноутбуке для одной архитектуры.

```bash
./gradlew :TMessagesProj:externalNativeBuildRelease -PbuildNative -Pabi=arm64-v8a
```

Готовый файл — `TMessagesProj/build/intermediates/cxx/RelWithDebInfo/<хэш>/obj/arm64-v8a/libtmessages.49.so`. В нём отладочные символы, он весит больше 200 МБ. Уберите их и положите результат туда, откуда его берёт обычная сборка:

```bash
$ANDROID_NDK/toolchains/llvm/prebuilt/<платформа>/bin/llvm-strip --strip-unneeded \
    -o TMessagesProj/jniLibs/arm64-v8a/libtmessages.49.so \
    TMessagesProj/build/intermediates/cxx/RelWithDebInfo/*/obj/arm64-v8a/libtmessages.49.so
```

После этого библиотека весит около 19 МБ. Для эмулятора повторите то же с `x86_64`.

> [!NOTE]
> В библиотеке остаются полные пути к исходникам на вашем компьютере (сообщения об ошибках WebRTC), а с ними и имя пользователя системы. Если собираетесь раздавать APK, собирайте проект в папке с нейтральным путём или замените этот путь в готовом файле строкой той же длины.

Пересобирать её нужно только если изменились файлы в `TMessagesProj/jni`.

## 4. Приложение

```bash
./gradlew :Ngram_App:assembleDebug   -Pabi=x86_64       # для эмулятора
./gradlew :Ngram_App:assembleRelease -Pabi=arm64-v8a    # для телефона
```

`-Pabi` принимает и список через запятую. Готовый файл: `Ngram_App/build/outputs/apk/<тип>/ngram-<тип>.apk`.

Сколько это занимает:

| Что изменилось | Время |
| --- | --- |
| Только `Ngram_App` | секунды |
| Код в `TMessagesProj` | 7–12 минут: пересобирается весь Java-код Telegram |
| Первая оптимизированная сборка (release) | ещё около 7 минут |

## Профиль компиляции

`Ngram_App/src/main/baseline-prof.txt` — список классов и методов, которые Android компилирует заранее на свежеустановленном приложении (baseline profile). Без него первые дни после установки код исполняется медленнее: у приложения, поставленного из файла, нет магазина, который прислал бы такой профиль.

Список снят с эмулятора: приложение прошло по основным экранам, после чего профиль выгружен командами

```bash
adb shell pm dump-profiles --dump-classes-and-methods org.ngram.messenger
```

```bash
adb pull /data/misc/profman/org.ngram.messenger-primary.prof.txt
```

Из него убраны строки отладочного демо-режима и лямбды с именами, которые меняются от сборки к сборке. Сборка сама превращает файл в `assets/dexopt/baseline.prof`, а библиотека `androidx.profileinstaller` передаёт его системе при первом запуске. После заметных изменений в коде профиль стоит снять заново.

## Подпись

Без настройки сборки подписываются отладочным ключом компьютера (`~/.android/debug.keystore`): этого достаточно, чтобы попробовать, но не для раздачи — пароль такого ключа общеизвестен.

Свой ключ создаётся один раз (файл держите вне папки проекта):

```bash
keytool -genkeypair -v -keystore ngram-release.jks -alias ngram -keyalg RSA -keysize 4096 -validity 10000
```

и подключается в `local.properties`:

```properties
signing.storeFile=C:/путь/ngram-release.jks
signing.storePassword=…
signing.keyAlias=ngram
signing.keyPassword=…
```

После этого `assembleRelease` и `assembleDebug` подписывают APK им. Сборка с новым ключом не ставится поверх сборки со старым: прежнюю придётся удалить. Потеряете файл ключа или пароль — обновления поверх установленного приложения выпустить будет нельзя.

## Чем сборка отличается от сборки Telegram

- Собираются только `TMessagesProj` и `Ngram_App`; остальные модули приложений Telegram оставлены в репозитории ради слияний, но в `settings.gradle` не подключены.
- Плагин `google-services` убран: у Ngram нет проекта Firebase.
- Добавлен плагин [Chaquopy](https://chaquo.com/chaquopy/): он встраивает Python 3.11 для плагинов и сам скачивает его с Maven Central. Это добавляет около 18 МБ на архитектуру.
- В корневом `build.gradle` исправлена проверка пути, из-за которой сборка не шла на Windows.

## Обновление до новой версии Telegram

```bash
git remote add upstream https://github.com/DrKLO/Telegram.git
git fetch upstream
git merge upstream/master
git submodule update --init --recursive --depth=1
```

Конфликты возможны только в местах, помеченных `// Ngram:` (их список — в [changes.md](changes.md)). Если изменились файлы в `TMessagesProj/jni`, соберите нативную библиотеку заново.
