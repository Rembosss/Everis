# 0.1-beta verification

VersionName `0.1-beta`, Android versionCode `8`, minSdk `31`, targetSdk `37`, ARM64 only.

- 192 application/core checks passed: 109 app JUnit, 59 domain, 10 source and 14 security checks.
- Android Lint: no errors. Warnings concern pinned dependency updates, optional newer Android attributes and intentional ARM64-only support.
- All five languages have 226 matching resource keys with positional format and plural fallback checks.
- Runtime QA used an isolated ARM64 Debug package on API 37 with native ARM translation. It covered 48 rapid tab changes plus 8 with performance mode, tab/date/calendar/settings state, Back exiting to launcher, switch/button animations, language switching, notification/time settings, repository URL and 200% font at a narrow width. No app crash was observed.
- The official Release APK passed package/version, certificate, v2/v3 signature, ARM64 and ZIP/ELF 16 KiB alignment checks. It is not debuggable. SHA-256 checksums are attached to the GitHub release.

Physical Pixel 8/GrapheneOS performance and tactile feedback, API 31 runtime, prolonged Doze/reboot and full TalkBack checks were not part of this verification pass. Debug ARM translation observations are not physical-device benchmarks.
