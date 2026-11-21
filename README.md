# Everis

[English](#english) · [Русский](#русский)

## English

A free Android university diary with offline timetables, homework and local reminders, built with Material 3 Expressive.

**Everis 0.3-beta (build 16)** is available for Android 12 and later. [Release notes](docs/CHANGELOG_0.3_BETA.md)

### Features

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

Student timetables are available for RSUE (RINH), DSTU, SFedU ICTIS, RGUPS and Southern University (IMBL). Teacher search covers RSUE, DSTU and SFedU ICTIS. Everis uses public university sources. [Coverage by university](docs/PUBLIC_SOURCES_0.3.md)

### Privacy

Timetables, homework and preferences are stored on your device. Everis has no application server, user accounts, telemetry or cloud sync. It accesses public university timetable sources; update checks use GitHub Releases, and contributor images come from public Telegram profiles with a local cache. No GitHub or Telegram sign-in is required in the app.

### Compatibility

Android 12 or newer. **ARM64 (`arm64-v8a`) only**, for both debug and release builds. ARMv7 and x86_64 are not supported.

Download the signed [ARM64 Release APK](https://github.com/Rembosss/Everis/releases/download/0.3-beta/Everis-0.3-beta-arm64-v8a-release.apk) from [Releases](https://github.com/Rembosss/Everis/releases/tag/0.3-beta). Package: `org.everis.app`. Android requires user confirmation to install updates.

### Build from source

See [Development](docs/DEVELOPMENT.md) for the pinned toolchain, build commands and checks. Official signing credentials, local configuration and generated artifacts are not included in the source tree.

### Feedback

- [Project Telegram channel](https://t.me/EverisProject)
- [Developer: Rembos1k](https://t.me/Rembos1k)
- [Report an issue](https://github.com/Rembosss/Everis/issues)

Include your Everis version, Android version and steps to reproduce the problem. Do not publish personal homework, backups or private logs.

### Donors

No donors are listed yet, and a donation link is not available. If you would like to support the project, [contact me on Telegram](https://t.me/Rembos1k).

#### Special thanks

- [@Gods_Mask](https://t.me/Gods_Mask) (for providing a Codex subscription).

### Developers

- [Rembos1k](https://github.com/Rembosss)
- [Codex](https://openai.com/codex/) — implementation and testing · [GitHub app](https://github.com/apps/chatgpt-codex-connector)

This project was made possible by donors, plenty of caffeine, Codex, and good music.

---

## Русский

Свободный электронный дневник для Android: расписание вуза, домашние задания и локальные напоминания в интерфейсе Material 3 Expressive.

**Everis 0.3-beta (сборка 16)** доступна для Android 12 и новее. [Что нового](docs/CHANGELOG_0.3_BETA.md#русский)

### Возможности

- Загрузка публичного расписания и работа с сохранёнными данными без интернета.
- Расписание на день, неделю и месяц, подгруппы, время занятий и перерывов.
- Домашние задания к следующему занятию, общий список и напоминания.
- Список дисциплин с преподавателями и статистикой нагрузки.
- Поиск расписания преподавателя в выбранном вузе.
- Компактный плавающий док, возможность вернуть классическую панель и долгое нажатие для возврата на главный экран раздела.
- Периодическая и вечерняя проверка изменений расписания.
- Обмен расписанием и резервные копии через локальные JSON-файлы.
- Светлая, тёмная и чёрная темы, динамический акцент Monet и собственный цвет.
- Русский, украинский, белорусский, казахский и английский языки.

Расписания групп доступны для РГЭУ (РИНХ), ДГТУ, ИКТИБ ЮФУ, РГУПС и Южного университета (ИУБиП). Поиск преподавателей работает для РИНХ, ДГТУ и ИКТИБ ЮФУ. Everis использует открытые источники вузов. [Подробнее об источниках](docs/PUBLIC_SOURCES_0.3.md)

### Приватность

Расписание, задания и настройки хранятся на устройстве. У Everis нет собственного сервера, аккаунтов, телеметрии и облачной синхронизации. Приложение обращается к публичному источнику расписания; проверка обновлений использует GitHub Releases, изображения участников — публичные Telegram-профили с локальным кешем. Для этого не нужна авторизация в GitHub или Telegram.

### Совместимость

Android 12 и новее. Единственная поддерживаемая архитектура — ARM64 (`arm64-v8a`), включая отладочные и релизные сборки. ARMv7 и x86_64 не поддерживаются.

Подписанный [Release APK для ARM64](https://github.com/Rembosss/Everis/releases/download/0.3-beta/Everis-0.3-beta-arm64-v8a-release.apk) доступен в [Releases](https://github.com/Rembosss/Everis/releases/tag/0.3-beta). Пакет: `org.everis.app`. Установка обновлений подтверждается пользователем средствами Android.

### Сборка из исходников

Закреплённые версии инструментов, команды сборки и проверок — в [Development](docs/DEVELOPMENT.md). Закрытые ключи официальной подписи, локальные настройки и результаты сборок не включены в исходники.

### Обратная связь

- [Telegram-канал проекта](https://t.me/EverisProject)
- [Разработчик: Rembos1k](https://t.me/Rembos1k)
- [Сообщить об ошибке](https://github.com/Rembosss/Everis/issues)

При описании ошибки укажите версию Everis, Android и шаги воспроизведения. Не публикуйте личные задания, резервные копии и приватные журналы.

### Донатеры

Пока здесь пусто, а ссылки для пожертвований ещё нет. Если хотите поддержать проект, напишите мне в [Telegram](https://t.me/Rembos1k).

#### Особая благодарность

- [@Gods_Mask](https://t.me/Gods_Mask) (За предоставленную подписку Codex).

### Разработчики

- [Rembos1k](https://github.com/Rembosss)
- [Codex](https://openai.com/codex/) — разработка и проверка · [GitHub app](https://github.com/apps/chatgpt-codex-connector)

Проект стал возможен благодаря донатерам, обилию кофеина, Codex и хорошей музыке.
