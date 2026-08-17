#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
sdk_root="${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
build_tools_version="${BUILD_TOOLS_VERSION:-35.0.0}"
platform_version="${ANDROID_PLATFORM_VERSION:-35}"
build_tools="$sdk_root/build-tools/$build_tools_version"
android_jar="$sdk_root/platforms/android-$platform_version/android.jar"
keystore_path="${HCF_KEYSTORE:?Set HCF_KEYSTORE}"
keystore_alias="${HCF_KEY_ALIAS:-hcf-dev}"
keystore_password_file="${HCF_KEY_PASSWORD_FILE:?Set HCF_KEY_PASSWORD_FILE}"
export HCF_APKSIGNER_PASSWORD="$(sed -n '1p' "$keystore_password_file")"
output_dir="${HCF_OUTPUT_DIR:-$project_dir/out}"
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/hcf-build.XXXXXX")"
trap 'rm -rf "$work_dir"' EXIT

# Permanent DEV signing line. Every com.harleytg.forum.dev release must use this
# exact certificate or Android will reject an in-place update as a package conflict.
expected_signer_sha256="AC6B913EE0809483371F66A73CC5D0BBDA1E45D491E143574D404674B023ABCE"

normalize_fingerprint() {
  printf '%s' "$1" | tr '[:lower:]' '[:upper:]' | tr -d ':[:space:]'
}

# Fail before compiling if somebody points this build at the wrong DEV key.
key_fingerprint="$(keytool -list -v \
  -keystore "$keystore_path" \
  -storepass "$HCF_APKSIGNER_PASSWORD" \
  -alias "$keystore_alias" 2>/dev/null \
  | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n 1)"
key_fingerprint="$(normalize_fingerprint "$key_fingerprint")"
if [[ -z "$key_fingerprint" || "$key_fingerprint" != "$expected_signer_sha256" ]]; then
  echo "ERROR: Refusing to build DEV APK with the wrong signing certificate." >&2
  echo "Expected DEV signer SHA-256: $expected_signer_sha256" >&2
  echo "Actual DEV signer SHA-256:   ${key_fingerprint:-UNAVAILABLE}" >&2
  exit 22
fi

mkdir -p "$work_dir/gen" "$work_dir/classes" "$work_dir/dex" "$output_dir"

"$build_tools/aapt" package -f -m \
  -J "$work_dir/gen" \
  -M "$project_dir/AndroidManifest.xml" \
  -S "$project_dir/res" \
  -I "$android_jar" \
  -F "$work_dir/resources.apk"

java -m jdk.compiler/com.sun.tools.javac.Main \
  --release 8 \
  -classpath "$android_jar" \
  -d "$work_dir/classes" \
  $(find "$work_dir/gen" "$project_dir/src" -name '*.java' -print)

"$build_tools/d8" \
  --lib "$android_jar" \
  --min-api 26 \
  --release \
  --output "$work_dir/dex" \
  $(find "$work_dir/classes" -name '*.class' -print)

cp "$work_dir/resources.apk" "$work_dir/unsigned.apk"
(
  cd "$work_dir/dex"
  "$build_tools/aapt" add "$work_dir/unsigned.apk" classes.dex
)
"$build_tools/zipalign" -f -p 4 "$work_dir/unsigned.apk" "$work_dir/aligned.apk"

output_apk="$output_dir/Harley's Clan Forum [Beta].apk"
"$build_tools/apksigner" sign \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --v4-signing-enabled false \
  --ks "$keystore_path" \
  --ks-key-alias "$keystore_alias" \
  --ks-pass env:HCF_APKSIGNER_PASSWORD \
  --key-pass env:HCF_APKSIGNER_PASSWORD \
  --out "$output_apk" \
  "$work_dir/aligned.apk"

"$build_tools/zipalign" -c -p 4 "$output_apk"
"$build_tools/apksigner" verify --verbose --print-certs "$output_apk"

apk_fingerprint="$("$build_tools/apksigner" verify --print-certs "$output_apk" \
  | sed -n 's/^Signer #1 certificate SHA-256 digest:[[:space:]]*//p' | head -n 1)"
apk_fingerprint="$(normalize_fingerprint "$apk_fingerprint")"
if [[ "$apk_fingerprint" != "$expected_signer_sha256" ]]; then
  echo "ERROR: Signed APK certificate changed unexpectedly." >&2
  exit 23
fi

echo "DEV signing-line verification: PASS"
echo "DEV versionCode: 10000033"
