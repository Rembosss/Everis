# Everis

**English** · [Русский](README.ru.md)

An independent Android university diary with offline timetables, homework and local reminders, built with Material 3 Expressive.

**Current version: 0.2-beta (build 10).** See [release notes](docs/CHANGELOG.md). This beta is intended for testing and feedback. The repository currently remains private.

## Features

- Download public university timetables and use saved schedules offline.
- Day, week and month views, subgroup filtering, class and break totals.
- Homework for the next class, a task list and reminders.
- Disciplines with teachers and study load statistics.
- Teacher timetable search within the selected university.
- Compact floating navigation, a classic navigation option, and a long press to return to a section’s main screen.
- Periodic and evening schedule change checks.
- Offline schedule sharing and local JSON backups.
- Light, dark and black themes, Monet dynamic colors and custom accents.
- Russian, Ukrainian, Belarusian, Kazakh and English interfaces.

Public timetable adapters support Rostov State University of Economics (RSUE/RINH) and Don State Technical University (DSTU). Other universities are planned. Everis is an independent project and is not an official university app or service.

## Privacy

Timetables, homework and preferences are stored on your device. Everis has no application server, user accounts, telemetry or cloud sync. It accesses public university timetable sources; update checks use GitHub Releases, and contributor images come from public Telegram profiles with a local cache. No GitHub or Telegram sign-in is required in the app.

## Compatibility

Android 12 or newer. **ARM64 (`arm64-v8a`) only**, for both debug and release builds. ARMv7 and x86_64 are not supported.

Download the signed [ARM64 Release APK](https://github.com/Rembosss/Everis/releases/download/0.2-beta/Everis-0.2-beta-arm64-v8a-release.apk) from [Releases](https://github.com/Rembosss/Everis/releases/tag/0.2-beta). Repository access is currently required. Package: `org.everis.app`. Android requires user confirmation to install updates.

## Build from source

See [Development](docs/DEVELOPMENT.md) for the pinned toolchain, build commands and checks. Official signing credentials, local configuration and generated artifacts are not included in the source tree.

## Feedback

- [Project Telegram channel](https://t.me/EverisProject)
- [Developer: Rembos1k](https://t.me/Rembos1k)
- [Report an issue](https://github.com/Rembosss/Everis/issues)

Include your Everis version, Android version and steps to reproduce the problem. Do not publish personal homework, backups or private logs.

## Developers

- [Rembos1k](https://github.com/Rembosss)
- [Codex](https://openai.com/codex/) · [GitHub app](https://github.com/apps/chatgpt-codex-connector)

Special thanks to [@Gods_Mask](https://t.me/Gods_Mask) for providing a Codex subscription.

This project was made possible by donors, plenty of caffeine, Codex, and good music.
