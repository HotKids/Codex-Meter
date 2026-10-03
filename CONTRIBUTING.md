# Contributing

This repository holds the Codex Meter Android project:

- Shared docs and release notes live at the repository root (`README.md`,
  `CHANGELOG.md`, `LICENSE`, `AGENTS.md`).
- **Android** lives under [`android/`](android/) (Gradle, `app/`, `shared/`,
  `tests/`). It is an Android phone project; shared Java models and policies
  support the phone app.

Prefer focused commits and update tests with behavior changes. Do not commit
credentials, tokens, or generated build artifacts.

This fork (`HotKids/Codex-Meter`) takes ordinary commits and pushes; pull
requests are opened only when the maintainer asks for one. Keep the fork
relationship with `BenItBuhner/Codex-Meter` so upstream changes can be merged.

## Android local setup

Install JDK 21 and Android SDK Platform 37.0 with Build Tools 36.x. Set
`ANDROID_SDK_ROOT` or `ANDROID_HOME` to the SDK directory.

The OneUI-Design dependencies are hosted on GitHub Packages. Export `GH_USERNAME`
and a `GH_ACCESS_TOKEN` with `read:packages` access before running a full build or
Android lint when `android/vendor/m2` is incomplete.

From the repository root (wrappers) or from `android/`:

```bash
./run-tests.sh
./build.sh
./lint.sh
```

CI runs the core tests, phone lint (`:app:lintRelease`) and a Debug APK build without release
signing material. It additionally runs the Robolectric unit tests:

```bash
cd android && ./gradlew :app:testDebugUnitTest
```

User-visible phone text belongs in string resources with a Simplified Chinese
translation under `res/values-zh-rCN/`; lint treats a missing translation as an
error. Use whole-sentence format strings with positional arguments and
`<plurals>` for counts rather than concatenating fragments.

After editing `app/src/main/res/layout/widget_material.xml` or the renderer's
typography and spacing, regenerate the shadow and picker layouts with
`android/tools/widget-card-shadow.sh`. Picker previews must follow the current
widget presentation.

See [`android/README.md`](android/README.md) for module layout details.

## Phone releases

The phone application and namespace are `me.pipi.codexmeter`, starting at version `0.1`
(code 1), targeting SDK 37. Only stable updates are enabled. The test channel is a disabled
placeholder; it does not select prereleases. Version history is not exposed in the app.

Release assets must be named `CodexMeter-me.pipi.codexmeter-<versionName>.apk` and accompanied
by `SHA256SUMS.txt`. This identity prevents legacy-package APKs from becoming update candidates
when the phone version line restarts. Use the pinned fixed signer in
[android-signing.md](docs/android-signing.md). `build.sh` builds the phone only.

Build and verify official releases locally. Confirm the package, version, target
SDK, fixed certificate and checksum, then publish the matching tag and phone
assets with GitHub CLI after maintainer authorization. GitHub Actions validates
branches, pull requests and manual runs; it does not decrypt the retained legacy
keystore or automatically publish tag releases. Releases contain the Android
phone APK and its checksum file.

Version changes require an explicit release request. Update the phone Gradle version,
`AppConstants.java`, `android/build.sh`, the guards in `android/run-tests.sh`, and the changelog
together. Tags and public releases require explicit authorization; a local Pixel
test build does not authorize publishing.
