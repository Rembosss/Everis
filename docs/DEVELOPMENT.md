# Development / Разработка

## Toolchain

The checked-in project pins Gradle 9.6.0 (wrapper checksum included), Android Gradle Plugin 9.4.0 and Kotlin 2.3.20. The Gradle daemon uses JDK 25, while application bytecode targets Java 17. Android Studio with the matching JDK or a local JDK 25 can build the project. Install Android SDK Platform 37 (the SDK manager package is `platforms;android-37.0`); this release was built with Build Tools 36.0.0.

Set the SDK location in Android Studio, through `ANDROID_HOME`, or in an untracked `local.properties` file (`sdk.dir=...`). No publisher-specific files are required for Debug builds.

```sh
git clone https://github.com/Rembosss/Everis.git
cd Everis
./gradlew :app:assembleDebug
```

Debug APK: `Releases/0.2-beta/Debug/Everis-0.2-beta-arm64-v8a-debug.apk`. It uses package `org.everis.app.debug` and your local Android debug signing key. The app requires Android 12/API 31 or newer and an ARM64 device. Both Debug and Release APKs target only `arm64-v8a`; x86 emulator images need native ARM translation to install them.

## Checks

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :core:domain:domainChecks :core:sources:sourceChecks :core:security:securityChecks
python3 tools/check_resources.py
```

The core modules use executable check tasks rather than discovered JUnit tests. The 0.2-beta application snapshot passed 136 app JUnit tests, 59 domain checks, 10 source checks and 14 security checks. Resource validation covers 244 keys in five languages. See [verification](VERIFICATION.md).

## Release signing

```sh
./gradlew :app:assembleRelease
```

Official private signing keys are not distributed. Without local signing configuration, this task produces an **unsigned** Release APK. Gradle can read your own external properties file through `-PeverisSigningProperties=/path/to/release.properties`; it expects `storeFile`, `storeType`, `storePassword`, `keyAlias` and `keyPassword`. Keep this file and your private keystore outside the repository. Official releases retain the public identity in [signing/release-identity.json](signing/release-identity.json); the matching public certificate is included.

## Architecture

- `app`: Compose/Material 3 Expressive UI, encrypted local snapshot, homework, WorkManager reminders and public update checks.
- `core/domain`: timetable, day status, period summaries, subgroup and reminder policies.
- `core/sources`: bounded HTTPS client and source adapter interfaces. University adapters cover RSUE/RINH and DSTU; teacher schedules and public catalogs are cached locally.
- `core/security`: authenticated envelopes and compatibility support for older encrypted backup imports. Current portable exports are plain JSON.

There is no application backend, sign-in, telemetry or cloud synchronization. Tests include public timetable fixtures, not user homework. Deprecated helper scripts, local QA recordings and development checkpoints are omitted from this repository snapshot. Existing cryptographic format identifiers remain stable for data compatibility.

## Кратко по-русски

Нужны JDK 25 и Android SDK API 37. Gradle Wrapper, AGP и Kotlin закреплены в проекте; компиляция приложения использует Java 17. Укажите SDK через Android Studio, `ANDROID_HOME` или локальный `local.properties`. Команды выше собирают Debug, запускают проверки и собирают Release.

Debug подписывается вашим локальным отладочным ключом. Официальный закрытый Release-ключ не публикуется; без собственной внешней конфигурации Release остаётся неподписанным. Готовые APK экспортируются в `Releases/<версия>/{Debug,Release}`. Поддерживаются Android 12+ и ARM64. Для проверки на эмуляторе используйте ARM64-образ либо образ с ARM-трансляцией; `tools/run_emulator.py` открывает отдельный Logcat. Данные приложения не очищаются без явного `--clear-data`.
