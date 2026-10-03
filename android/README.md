# Codex Meter for Android

Native Android phone client for viewing the Codex allowance attached to a
signed-in ChatGPT account. The project contains the phone app and its shared
Java models and policies.

## Layout

| Path | Role |
|------|------|
| `app/` | Phone app, application ID and Java namespace `me.pipi.codexmeter` |
| `shared/` | Java usage models and policies used by the phone app |
| `tests/` | Pure-Java self-tests used by `./run-tests.sh` |
| `app/src/test/` | Robolectric unit tests, including widget preview renders |
| `tools/` | Generators for derived resources (`widget-card-shadow.sh`) |
| `vendor/m2/` | Cached One UI / SESL Maven artifacts |
| `ci/` | Fixed phone certificate pin and retained legacy encrypted signing files |

## Build and test

From this `android/` directory (or via the repo-root wrappers):

```bash
./run-tests.sh
./lint.sh
./build.sh
```

Requirements: JDK 21, Android SDK Platform 37.0, Build Tools 36.x, and
`ANDROID_SDK_ROOT` / `ANDROID_HOME`. `vendor/m2` covers SESL deps offline;
optional `GH_USERNAME` / `GH_ACCESS_TOKEN` refresh GitHub Packages.

`./gradlew :app:testDebugUnitTest` runs the Robolectric tests. The widget
preview test renders the home widget cards with fixture data to
`app/build/reports/widget-previews/` (Robolectric renders, not device
screenshots).

CI runs the core and Robolectric tests, phone lint and a Debug APK build.
Its Debug APKs are test artifacts; it does not consume release signing material
or publish GitHub releases.

The phone version line starts at `0.1` (code 1), with compile and target SDK 37.
`build.sh` builds only the phone. Signed local APKs land in `android/dist/` as
`CodexMeter-me.pipi.codexmeter-<versionName>.apk`, alongside `SHA256SUMS.txt`.
See the repository root
[`README.md`](../README.md) for product notes and release tagging.
Phone release builds require the fixed signing identity described in
[`docs/android-signing.md`](../docs/android-signing.md). Restore its backup if
`android/.local-signing/` is missing; builds never generate replacement keys.

Regenerate shadow and picker layouts with `tools/widget-card-shadow.sh` after
changing the card layout, typography or spacing. See
[`docs/ai-usage-widgets.md`](../docs/ai-usage-widgets.md) for the current widget
contract.
