# Android phone signing

All phone release APKs use the same signing identity. The public certificate
SHA-256 is pinned in `android/ci/phone-signing-certificate.sha256`:

```text
eae8cf67cb95488962ef339e5fd6ee65dc7a9c5c442e393bfdb984021b7751d0
```

The ignored `android/.local-signing/` directory contains
`codex-meter-local.p12` (PKCS12, alias `codexmeter`) and `password`. Gradle consumes
these files directly. Both the Gradle signing configuration and the build wrapper
check the fixed certificate. The wrapper also requires exactly one APK signer.
Missing material fails the build; it never creates a replacement key.

Release APKs explicitly compress DEX files with `packaging.dex.useLegacyPackaging`.
The build wrapper verifies compression to prevent a higher minimum SDK from
silently increasing the download size.

The owner's recovery backup is `/Users/joey/Desktop/Codex-Meter-Signing-Backup/`.
It contains the identical keystore and password files, the public certificate,
and recovery instructions. Keep this folder private and retain another protected
copy before deleting the checkout or the Desktop backup. Never commit signing
material, attach it to issues, or include its contents in logs.

To restore a checkout, recover the two files into `android/.local-signing/`, with
directory permissions `700` and file permissions `600`. The public fingerprint
must continue to match the checked-in pin. Keep this identity for every future
phone release. Only stable phone updates are currently enabled; the test channel
is a disabled placeholder.

The Pixel installation previously used a temporary certificate. Its transition
to this fixed identity required a one-time uninstall; subsequent APKs use normal
in-place updates with the fixed certificate.

## GitHub Actions releases

Branch and pull-request validation in `.github/workflows/build-apk.yml` runs
tests, lint and a Debug APK build without release signing material.

After explicit maintainer authorization, stable `v*` tags on `main` trigger
`.github/workflows/android-release.yml`. The first phone tag is `v0.1`, using
the existing version `0.1` / code 1; the alpha channel remains disabled.
The release workflow first runs tests and lint without signing material. It
then restores the same PKCS12 identity (alias `codexmeter`) from repository
Secrets `SIGNING_KEYSTORE_BASE64` and `SIGNING_STORE_PASSWORD` into the existing
ignored `android/.local-signing/` directory, and calls the existing `./build.sh`.
The repository Secrets contain the current keystore encoded as Base64 and its
existing password; they must never introduce a replacement signing identity.

Before uploading artifacts, the workflow verifies the certificate against the
public pin, the APK package `me.pipi.codexmeter`, the version and target SDK,
and the checksum. A separate publish job with `contents: write` creates the
stable GitHub release from the matching `CHANGELOG.md` section. The only release
assets are `CodexMeter-me.pipi.codexmeter-<versionName>.apk` and `SHA256SUMS.txt`.
Signing files are not published or included in build artifacts.

After the new stable release and both assets are confirmed, the publish job deletes
older stable phone releases and their tags, including older version tags without a
release. The current version and newer version tags remain intact.

The local signing source, recovery backup and normal `./build.sh` path remain
available. The retained legacy encrypted keystore is unused by this workflow;
do not read, decrypt or replace it as part of a phone release.
