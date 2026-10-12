<div align="center">

<img src="docs/screenshots/icon.png" width="96" alt="Ngram">

# Ngram

**An unofficial Telegram client for Android: all of Telegram, plus adjustable liquid glass, tools for chats, an assistant that runs on your own AI key, and plugins.**

Built on the source code of Telegram for Android 12.10.6 · GPL-2.0

[Русский](README.md)

</div>

<p align="center">
  <img src="docs/screenshots/chats.png" width="24%" alt="Chat list">
  <img src="docs/screenshots/chat.png" width="24%" alt="Chat">
  <img src="docs/screenshots/look-glass.png" width="24%" alt="Glass settings">
  <img src="docs/screenshots/assistant.png" width="24%" alt="Assistant">
</p>

## What it is

Ngram is Telegram for Android with these things added:

- **Look.** Sliders for the liquid glass (density, blur, edge refraction, edge highlight), a colour tint, ready-made and saved sets, three kinds of button press (jelly, glass, plain), iOS-style switches, six app icons, and options for the main screen.
- **Assistant.** Conversations with an AI model and actions on messages: explain, translate, summarize, suggest a reply, read a picture, transcribe a voice message. It works with your own API key — OpenAI, Claude, Gemini, OpenRouter, a local Ollama, or any OpenAI-compatible service. Requests go from the phone straight to the provider; Ngram has no servers.
- **Chats.** Seconds in message times, exact numbers, your own double-tap action, sticker size, a question before a sticker, a GIF or a call, "listen first" for voice messages, templates, chat export and statistics, details of a message and of a profile.
- **Plugins.** Extensions in Python with exteraGram's SDK names: commands, menu entries, settings of their own. Replacing the app's methods (`hook_method`) is not supported.
- **Its own name and icons.** Ngram does not pass itself off as the official app.

There is no "ghost mode" and there will not be one: hiding read marks, typing or online status, and getting around copy protection in chats, are forbidden by Telegram's terms for third-party apps.

Everything else — chats, calls, stories, secret chats, folders, bots — is Telegram's own code, unchanged.

> [!IMPORTANT]
> Ngram is not affiliated with Telegram. Version 3.1 is early: its own features were checked in an emulator on a made-up account. Signing in with a real account, the arm64 build on a phone and the assistant with a real key were not checked by the author.

## How it is put together

Telegram's code lives in `TMessagesProj` and is barely touched: about thirty files, every change marked with a `// Ngram:` comment. Everything of Ngram's own lives in the separate `Ngram_App` module and talks to Telegram's code through three small classes in `org.ngram.core` (`NgramHooks`, `NgramConfig`, `NgramMotion`). A new Telegram release can therefore be taken with a plain `git merge`.

## Building

You need JDK 17, Android SDK 36, NDK 27.2.12479018, CMake 3.22.1 and your own `api_id` / `api_hash` from https://my.telegram.org, put into `local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
telegram.apiId=123456
telegram.apiHash=0123456789abcdef0123456789abcdef
```

```bash
git clone --recursive --shallow-submodules https://github.com/Neizvestnyi22/Ngram.git Ngram

# the native library, once per architecture (about 20 minutes)
./gradlew :TMessagesProj:externalNativeBuildRelease -PbuildNative -Pabi=arm64-v8a
# strip it into TMessagesProj/jniLibs/arm64-v8a/ — see docs/building.md

# the app
./gradlew :Ngram_App:assembleRelease -Pabi=arm64-v8a
```

## Documentation

The detailed documentation is in Russian:

- [Architecture](docs/architecture.md) — the two modules, the hook points, how to add a setting or a screen
- [Building](docs/building.md)
- [Look settings](docs/look.md) — what each slider changes and where in the code
- [Chats](docs/chats.md) — message options, the message menu, templates, export
- [Plugins](docs/plugins.md) — installing, writing one, what is and is not compatible with exteraGram
- [Assistant](docs/assistant.md) — what is sent where, providers, key storage
- [Debug demo mode](docs/demo.md) — looking at screens without an account
- [Changes to Telegram's code](docs/changes.md)

## Licence

GNU GPL version 2, the same as the code it is built on. See [LICENSE](LICENSE).

[Telegram for Android](https://github.com/DrKLO/Telegram) is © Telegram; nearly all the code in this repository was written by its authors. The assistant's answers are rendered with [Markwon](https://github.com/noties/Markwon) (Apache-2.0).
