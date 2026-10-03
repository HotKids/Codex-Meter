# AGENTS.md

## Repository layout

Codex Meter contains an Android phone app with no backend:

| Path | Stack | Package / product |
|------|--------|-------------------|
| Repository root | Shared docs, license, changelog, CI entrypoints | — |
| `android/` | Android (Gradle `:app`, `:shared`) | Phone `me.pipi.codexmeter` |

The Android app talks directly to OpenAI/ChatGPT remote endpoints. Tokens stay on-device in Android Keystore.

## Fork workflow

- Keep the GitHub fork relationship. The maintainer prefers ordinary commits and pushes to `HotKids/Codex-Meter`, without creating pull requests unless explicitly requested.
- The phone application ID is `me.pipi.codexmeter`. Its Java namespace and component classes are also `me.pipi.codexmeter`; shared model classes retain their existing namespace. Derive broadcast actions and permissions from `BuildConfig.APPLICATION_ID` (`${applicationId}` in the manifest), never from a hard-coded package string.
- In-app updates target this fork so an upstream APK with the old package ID is not offered as an update.
- Phone release builds must use the fixed certificate pinned in `android/ci/phone-signing-certificate.sha256`. Follow `docs/android-signing.md` for its ignored local source and recovery backup. Never generate a replacement signing identity to resolve a missing-key build failure.

## Platform scope

- Maintain the Android phone app and its shared usage models only.
- The iOS and Wear OS modules, phone-to-watch synchronization and watch-only dependencies have been removed at the maintainer's request.
- Do not reintroduce other platforms or watch synchronization without an explicit request.

## Phone conventions

- User-visible text lives in string resources with a Simplified Chinese copy in `res/values-zh-rCN/`. Use whole-sentence format strings with positional arguments and `<plurals>`; translate English produced by the shared module on the phone side by stable ids.
- Home widgets use upstream One UI dials for fixed-width one-row placements (2×1), and the Clear card (`MaterialCardRenderer`, `res/layout/widget_material.xml`) for 2×2 and larger placements. The one-row editor offers five-hour, weekly/monthly, and reset meters. Larger cards also offer numeric remaining credits without a progress bar. Clear places the available reset count below the next-reset progress bar; legacy standalone credit selections migrate to next reset. Regenerate the shadow and picker preview layouts with `android/tools/widget-card-shadow.sh` after editing the card layout, typography, or spacing; keep the picker content in sync with widget presentation changes. `UsageCardPreviewTest` renders preview PNGs to `android/app/build/reports/widget-previews/`.

## Release channels & etiquette (Android)

The phone starts a separate version line at `0.1` (code 1), targeting SDK 37. Only stable updates are enabled; the test channel is a disabled placeholder. The branch policy below is retained for future release work:

- `main` — **stable** channel. Phone tags start at `v0.1` and publish as regular GitHub releases marked latest.
- `alpha` — reserved for future prerelease work; the phone test channel is not enabled.

Rules for agents:

- Target **`alpha`** with feature and experimental PRs unless the user explicitly says the work is for `main`. Docs, CI, and user-requested hotfixes to the shipped stable go to `main`.
- **Never push `v*` tags or create/edit GitHub releases unless the user explicitly asks.** CI validates branch builds and produces debug APKs; authorized releases use the local fixed-signature phone APK and `SHA256SUMS.txt`. Tags do not trigger signing or publication.
- **Never bump `versionCode`/`versionName` on your own.** Version bumps are release preparation and happen only when the user asks to cut a release.
- Preparing a future **alpha release** (`X.Y.Z-alpha.N`) requires explicit authorization to enable the channel. Keep `versionCode` equal to the newest stable release for the same phone package so returning to stable remains an in-place install. `X.Y.Z` must be the next stable version, not the shipped one. Do not compare the new package's version line against inherited upstream tags.
- Preparing a **stable release** (promotion): merge `alpha` into `main`, drop the suffix, bump `versionCode` by exactly one, and consolidate the alpha changelog sections under the stable version.
- Any phone version change must update every synced touchpoint together: `android/app/build.gradle.kts`, `AppConstants.java` (`VERSION_NAME`, `VERSION_CODE`, and the literal user-agent string), `android/build.sh`, the version guards in `android/run-tests.sh`, and a matching `## <version>` section in root `CHANGELOG.md`.
- Merging `main` into `alpha` to keep it fresh is fine; never force-push either branch, and never delete or recreate `alpha` on your own.

Convenience wrappers at the repo root forward into the Android project:

- `./run-tests.sh` → `android/run-tests.sh`
- `./build.sh` → `android/build.sh`
- `./lint.sh` → `android/lint.sh`

## Cursor Cloud specific instructions (Android)

### Toolchain (pre-installed in the VM snapshot)
- JDK 21 at `/usr/lib/jvm/java-21-openjdk-amd64` (project targets Java 17; JDK 21 builds fine with Gradle 9.6.1).
- Android SDK at `~/android-sdk` with `platforms;android-36` + `platforms;android-37.0` + `build-tools;36.0.0` + `platform-tools`.
- Gradle 9.6.1 via the committed wrapper (`android/gradlew`); no system Gradle needed.
- `JAVA_HOME`, `ANDROID_SDK_ROOT`, `ANDROID_HOME`, and `PATH` are exported from `~/.bashrc`. `android/build.sh` / `android/lint.sh` only auto-detect these on macOS paths, so on this Linux VM they rely on those env vars being present. In a non-login/non-interactive shell that did not source `~/.bashrc`, export them first:
  `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_SDK_ROOT=$HOME/android-sdk ANDROID_HOME=$HOME/android-sdk`.

### One UI / SESL dependency resolution
`android/build.sh` (`:app:assembleRelease`) and `android/lint.sh` (`:app:lintRelease`) resolve `io.github.tribalfs:oneui-design` and its transitive **SESL** dependencies. GitHub Packages Maven **always requires authentication, even for public packages**, so without credentials live SESL downloads return `401 Unauthorized`. `android/vendor/m2` caches the top-level `oneui-design` AAR **and** the SESL transitive artifacts, so phone release builds work offline without `GH_USERNAME` / `GH_ACCESS_TOKEN`. Those env vars remain optional for refreshing deps from GitHub Packages; `android/settings.gradle.kts` still reads them when present.

### Running / testing (Android)
- `./run-tests.sh` (or `android/run-tests.sh`) compiles and runs the pure-Java core self-tests (usage-response parsing, PKCE/OAuth, JWT claims, widget options) — no Android SDK or GitHub creds required. Use this as the fast correctness check.
- `cd android && ./gradlew :app:testDebugUnitTest` runs the Robolectric unit tests (CI runs them too).
- There is no Android emulator/GUI in this VM, and an APK cannot be installed/launched headlessly here. Validate changes with `run-tests.sh` and a successful `build.sh`/`lint.sh`. Signed phone APKs land in `android/dist/` and are the product artifacts.
