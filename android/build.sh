#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
VERSION_NAME="0.3.2"
DIST="$ROOT/dist"
SIGNING_DIR="$ROOT/.local-signing"
KEYSTORE="$SIGNING_DIR/codex-meter-local.p12"
PASS_FILE="$SIGNING_DIR/password"
CERT_SHA_FILE="$ROOT/ci/phone-signing-certificate.sha256"

if [[ -z "${JAVA_HOME:-}" && -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
if [[ -z "${ANDROID_SDK_ROOT:-}" && -d "$HOME/Library/Android/sdk" ]]; then
  export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
fi

if [[ ! -f "$KEYSTORE" || ! -s "$PASS_FILE" ]]; then
  echo "Fixed signing material is missing. Restore android/.local-signing from the signing backup." >&2
  exit 1
fi
mkdir -p "$DIST"

"$ROOT/gradlew" --project-dir "$ROOT" \
  :app:assembleRelease \
  --console=plain

SOURCE_APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
OUT="$DIST/CodexMeter-me.pipi.codexmeter-$VERSION_NAME.apk"


APKSIGNER="$(find "$ANDROID_SDK_ROOT/build-tools" -type f -name apksigner | sort | tail -1)"
PHONE_SIGNER_REPORT="$("$APKSIGNER" verify --verbose --print-certs "$SOURCE_APK")"
EXPECTED_CERT_SHA="$(tr -d '[:space:]' < "$CERT_SHA_FILE")"
SIGNER_COUNT="$(printf '%s\n' "$PHONE_SIGNER_REPORT" | awk '/^Number of signers:/ { print $NF }')"
ACTUAL_CERT_SHA="$(printf '%s\n' "$PHONE_SIGNER_REPORT" | awk '/certificate SHA-256 digest:/ { print $NF }')"
if [[ ! "$EXPECTED_CERT_SHA" =~ ^[0-9a-f]{64}$ || "$SIGNER_COUNT" != "1" || "$ACTUAL_CERT_SHA" != "$EXPECTED_CERT_SHA" ]]; then
  echo "Phone APK must have exactly one signer matching the fixed release certificate." >&2
  exit 1
fi

# A higher minSdk otherwise switches the default to uncompressed DEX files.
python3 - "$SOURCE_APK" <<'PY'
import sys
from zipfile import ZIP_DEFLATED, ZipFile

with ZipFile(sys.argv[1]) as apk:
    dex_files = [entry for entry in apk.infolist() if entry.filename.endswith(".dex")]
    if not dex_files or any(entry.compress_type != ZIP_DEFLATED for entry in dex_files):
        raise SystemExit("Phone APK must package all DEX files with compression.")
PY

cp "$SOURCE_APK" "$OUT"
printf '%s\n' "$PHONE_SIGNER_REPORT"
(cd "$DIST" && sha256sum "$(basename "$OUT")") | tee "$DIST/SHA256SUMS.txt"
echo "Built $OUT"
