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

GitHub Actions runs tests, lint and a Debug APK build without release signing
material. The legacy encrypted keystore and repository secret remain unchanged
and are not used by the current CI or release path. CI does not automatically
publish releases when a tag is pushed.

Build official phone releases locally with `./build.sh`. The build wrapper
verifies the fixed certificate and produces
`CodexMeter-me.pipi.codexmeter-<versionName>.apk` with `SHA256SUMS.txt`. Verify the
package, version and target SDK, then publish those two assets with GitHub CLI
after explicit maintainer authorization. Release notes come from the matching
phone version in `CHANGELOG.md`. The release contains only the Android phone APK
and its checksum file. Do not migrate remote signing credentials without a
separate request.
