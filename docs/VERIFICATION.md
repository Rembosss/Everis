# Everis 0.3-beta, build 16

The official Release APK uses package `org.everis.app`, targets Android API 37 and supports Android 12 (API 31) or later on ARM64. Packaging verified the v2 and v3 signatures against the [Everis release identity](signing/release-identity.json) and checked 16 KiB alignment of the APK and native libraries.

The previous 0.3-beta build (15) passed 176 app tests, 59 domain checks, 17 source checks and 14 security checks. Build 16 contains the final color adjustments for dropdowns and homework fields. It was assembled and signed without rerunning those tests.

---

# Verification — Everis 0.2-beta, build 10

The signed ARM64 Debug and Release artifacts were built with versionCode 10, minSdk 31 and targetSdk 37. Package IDs are org.everis.app.debug and org.everis.app. Release uses R8/resource shrinking, is not debuggable, and is signed with the existing dedicated Everis Release key (v2/v3). Debug retains its separate signing identity. ZIP and native ELF alignment support 16 KiB pages. Each release contains only the signed Release APK. GitHub exposes the asset SHA-256 digest through its Releases API; the public signing certificate and its fingerprint are included under docs/signing. Debug APKs and detailed verification reports remain local.

- 136 application JUnit tests.
- 59 domain checks, 10 public HTTP client checks and 14 security checks: 219 total.
- Debug/Release Lint: no errors. Warnings for available dependency updates, SDK attributes and intentional ARM64-only packaging remain.
- Five language resource sets, 244 keys each, positional formats and plural fallbacks validated.
- Headless API 37 UI checks with an isolated ARM64 QA installation: live DSTU group ВПР11, shared classes with source groupID 0, subgroups 1/2 and filtering; stable group search position with/without the keyboard; fixed Settings/About headings; 32 rapid navigation taps.
- First language change reproduced before the fix and recorded after the fix: the final control recording contained no fully black content frames. Locale/layoutDirection/screenLayout changes are handled by the current Compose activity.
- Previous validation also covered RSUE/DSTU teacher search, read-only daily timetables, public offline caches, section-root shortcuts and narrow-screen/large-font layouts.

No production user data was cleared. The emulator was stopped before final builds. No physical Pixel 8 frame-rate measurement was performed. University timetable completeness depends on the original public sources; missing or ambiguous identities are not guessed. Private GitHub Releases require repository access; no GitHub token is embedded in the application.
