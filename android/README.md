# Codex Meter for Android

Native Android + Wear OS client for viewing the Codex allowance attached to a
signed-in ChatGPT account. This directory is the **Android** Gradle project of
the Codex Meter repository. The Wear OS companion is retained with its code,
resources, dependencies, and version frozen.

## Layout

| Path | Role |
|------|------|
| `app/` | Phone app (`me.pipi.usage`; Java namespace remains `dev.bennett.codexmeter`) |
| `wear/` | Wear OS companion |
| `shared/` | Shared phone↔watch contracts |
| `tests/` | Pure-Java self-tests used by `./run-tests.sh` |
| `vendor/m2/` | Cached One UI / SESL Maven artifacts |
| `ci/` | Encrypted release keystore material for GitHub Actions |

## Build and test

From this `android/` directory (or via the repo-root wrappers):

```bash
./run-tests.sh
./gradlew :app:testDebugUnitTest
./lint.sh
./build.sh
```

Requirements: JDK 17+, Android SDK Platforms 36 and 37.0, Build Tools 36.x, and
`ANDROID_SDK_ROOT` / `ANDROID_HOME`. `vendor/m2` covers SESL deps offline;
optional `GH_USERNAME` / `GH_ACCESS_TOKEN` refresh GitHub Packages.

Signed local APKs land in `android/dist/`. See the repository root
[`README.md`](../README.md) for product notes and release tagging.

Robolectric tests exercise native widget inflation, Chinese/English resources,
selection persistence, missing usage windows, cached data and local history.
Their rendered fixtures are saved under `app/build/reports/widget-previews/`;
they are offline previews, not device screenshots or live account data.

The phone configuration flow has one persistence boundary:
`AppPreferences` delegates home widgets to `HomeWidgetPreferences`. Its schema
version handles existing-widget migration independently of the APK version.
`WidgetOptions.defaults()` supplies launcher and editor defaults; the editor
must not override them locally. `QuotaCardState` captures cached data once for
all responsive sizes. Lock-screen and Wear settings use their existing paths.

`HomeWidgetFlowTest` covers provider updates without configuration, migration,
restore, the actual editor's selection limit and save, and older Android hosts.
`SettingsImportTest` covers preflight validation before settings writes. CI runs
these tests on ordinary main/alpha pushes, pull requests, and manual builds,
and uploads test reports and offline widget fixtures. Only version tags publish
releases.

The retained Wear app still uses `dev.bennett.codexmeter`. It cannot pair with
the renamed phone through Google's Data Layer because application IDs differ.
Its code and the shared module remain frozen.
