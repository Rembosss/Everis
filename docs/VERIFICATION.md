# Verification — Everis 0.2-beta, build 10

The signed ARM64 Debug and Release artifacts were built with versionCode 10, minSdk 31 and targetSdk 37. Package IDs are org.everis.app.debug and org.everis.app. Release uses R8/resource shrinking, is not debuggable, and is signed with the existing dedicated Everis Release key (v2/v3). Debug retains its separate signing identity. ZIP and native ELF alignment support 16 KiB pages. APK hashes and certificate fingerprints are published with the release as verification.json and SHA256SUMS.

- 136 application JUnit tests.
- 59 domain checks, 10 public HTTP client checks and 14 security checks: 219 total.
- Debug/Release Lint: no errors. Warnings for available dependency updates, SDK attributes and intentional ARM64-only packaging remain.
- Five language resource sets, 244 keys each, positional formats and plural fallbacks validated.
- Headless API 37 UI checks with an isolated ARM64 QA installation: live DSTU group ВПР11, shared classes with source groupID 0, subgroups 1/2 and filtering; stable group search position with/without the keyboard; fixed Settings/About headings; 32 rapid navigation taps.
- First language change reproduced before the fix and recorded after the fix: the final control recording contained no fully black content frames. Locale/layoutDirection/screenLayout changes are handled by the current Compose activity.
- Previous validation also covered RSUE/DSTU teacher search, read-only daily timetables, public offline caches, section-root shortcuts and narrow-screen/large-font layouts.

No production user data was cleared. The emulator was stopped before final builds. No physical Pixel 8 frame-rate measurement was performed. University timetable completeness depends on the original public sources; missing or ambiguous identities are not guessed. Private GitHub Releases require repository access; no GitHub token is embedded in the application.
