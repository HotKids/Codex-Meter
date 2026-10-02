# Codex Meter for Android

Native Android client for viewing the Codex allowance attached to a signed-in
ChatGPT account, with a frozen Wear OS companion.

## Layout

| Path | Role |
|------|------|
| `app/` | Phone app, application ID `me.pipi.usage` (Java namespace `dev.bennett.codexmeter`) |
| `wear/` | Wear OS companion, `dev.bennett.codexmeter` (frozen; cannot pair with the renamed phone) |
| `shared/` | Shared phone↔watch contracts (frozen with Wear) |
| `tests/` | Pure-Java self-tests used by `./run-tests.sh` |
| `app/src/test/` | Robolectric unit tests, including widget preview renders |
| `tools/` | Generators for derived resources (`widget-card-shadow.sh`) |
| `vendor/m2/` | Cached One UI / SESL Maven artifacts |
| `ci/` | Encrypted release keystore material for GitHub Actions |

## Build and test

From this `android/` directory (or via the repo-root wrappers):

```bash
./run-tests.sh
./lint.sh
./build.sh
```

Requirements: JDK 17+, Android SDK Platform 36, Build Tools 36.x, and
`ANDROID_SDK_ROOT` / `ANDROID_HOME`. `vendor/m2` covers SESL deps offline;
optional `GH_USERNAME` / `GH_ACCESS_TOKEN` refresh GitHub Packages.

`./gradlew :app:testDebugUnitTest` runs the Robolectric tests. The widget
preview test renders the home widget cards with fixture data to
`app/build/reports/widget-previews/` (Robolectric renders, not device
screenshots).

Signed local APKs land in `android/dist/`. See the repository root
[`README.md`](../README.md) for product notes and release tagging.
