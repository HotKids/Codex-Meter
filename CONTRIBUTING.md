# Contributing

This repository contains the Android phone app and its frozen Wear OS companion:

- Shared docs and release notes live at the repository root (`README.md`,
  `CHANGELOG.md`, `LICENSE`, `AGENTS.md`).
- **Android** lives under [`android/`](android/) (Gradle, `app/`, `shared/`,
  `wear/`, `tests/`).

Keep Android changes under `android/`. Prefer focused commits and
update tests with behavior changes. Do not commit credentials, tokens, or
generated build artifacts.

Wear OS is frozen: leave `android/wear/` code, resources, dependencies, and version
unchanged, and preserve the shared phone-to-watch behavior. Building and validating
the existing companion is allowed. Release steps that change Wear OS require an
explicit maintainer request to resume that work.

## Android local setup

Install JDK 17 or newer and Android SDK Platform 36 with Build Tools 36.x. Set
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

See [`android/README.md`](android/README.md) for module layout details.

## Release channels (Android)

Two long-lived branches feed two update channels in the app (Settings → Updates →
Update channel):

- `main` is the **stable** channel. Tags look like `v2.7.0`.
- `alpha` is the **rapid-iteration** channel. Tags look like `v2.7.0-alpha.1` and
  publish as GitHub prereleases. The branch was bootstrapped from `main` at the
  2.7.0 rollout; if it is ever deleted, recreate it from `main` (`git push origin
  main:refs/heads/alpha`).

Both channels are built by the same tag-triggered CI job and signed with the same
release keystore, so the in-app updater's SHA-256 and signing-certificate checks
pass when switching channels in either direction — no uninstall/reinstall.

Versioning rules (enforced by CI on tags):

- **Alpha releases** bump only `versionName` and must keep `versionCode` **equal
  to** the newest stable release's `versionCode`. Android permits
  equal-`versionCode` installs, which is what makes the one-tap "Return to
  stable" flow an ordinary in-place install. The `versionName` must be the
  **next** stable version plus `-alpha.N` (after stable `2.7.0`, the first alpha
  is `2.8.0-alpha.1`, then `2.8.0-alpha.2`, ...). Never suffix the shipped
  stable itself (`2.7.0-alpha.1` after `2.7.0`): SemVer orders `X.Y.Z-alpha.N`
  *below* `X.Y.Z`, so the in-app updater would never offer it.
- **Stable releases** drop the suffix and bump `versionCode` by one, so a stable
  promotion is a normal upgrade for both channels.

Cutting an alpha: branch work off `alpha`, set `versionName` to the next stable
version plus the alpha suffix (for example, `2.8.0-alpha.1` while stable is
`2.7.0`) in `android/app/build.gradle.kts`, `android/wear/build.gradle.kts`,
`AppConstants.java`, `android/build.sh`, and the guards in `android/run-tests.sh`,
add a `## 2.8.0-alpha.1` section to `CHANGELOG.md`, then tag `v2.8.0-alpha.1`.

Promoting to stable: merge `alpha` into `main`, drop the suffix, bump
`versionCode`, consolidate the alpha changelog sections under the stable version,
then tag as usual.
