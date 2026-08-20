#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
sdk_root="${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
build_tools_version="${BUILD_TOOLS_VERSION:-35.0.0}"
platform_version="${ANDROID_PLATFORM_VERSION:-35}"
build_tools="$sdk_root/build-tools/$build_tools_version"
android_jar="$sdk_root/platforms/android-$platform_version/android.jar"
keystore_path="${HCF_KEYSTORE:?Set HCF_KEYSTORE to the private Stable V2 JKS}"
keystore_alias="${HCF_KEY_ALIAS:-hcf-stable-v2}"
keystore_password_file="${HCF_KEY_PASSWORD_FILE:?Set HCF_KEY_PASSWORD_FILE}"
export HCF_APKSIGNER_PASSWORD="$(sed -n '1p' "$keystore_password_file")"
output_dir="${HCF_OUTPUT_DIR:-$project_dir/out}"
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/hcf-stable-build.XXXXXX")"
trap 'rm -rf "$work_dir"' EXIT

expected_package="com.harleytg.forum"
expected_version_code="10000072"
expected_version_name="1.0 (10000072)"
expected_signer_sha256="77E0E96C1177842AAA311A8FC0EBEA29B92D3CD290BB815BDB86AD0E0A85844F"
output_apk="$output_dir/HCF-Stable-v10000072.apk"

fail() { echo "ERROR: $*" >&2; exit 20; }
normalize_fingerprint() { printf '%s' "$1" | tr '[:lower:]' '[:upper:]' | tr -d ':[:space:]'; }

for tool in aapt d8 zipalign apksigner; do
  [[ -x "$build_tools/$tool" ]] || fail "Missing Android build tool: $build_tools/$tool"
done
[[ -f "$android_jar" ]] || fail "Missing Android platform jar: $android_jar"
[[ -f "$project_dir/AndroidManifest.xml" ]] || fail "Missing AndroidManifest.xml"
[[ -f "$keystore_path" ]] || fail "Missing Stable V2 private signing key"
[[ -f "$keystore_password_file" ]] || fail "Missing signing-key password file"

key_fingerprint="$(keytool -list -v -keystore "$keystore_path" -storepass "$HCF_APKSIGNER_PASSWORD" -alias "$keystore_alias" 2>/dev/null \
  | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n 1)"
key_fingerprint="$(normalize_fingerprint "$key_fingerprint")"
[[ "$key_fingerprint" == "$expected_signer_sha256" ]] \
  || fail "Refusing to build Stable with a signing certificate other than Stable V2"

mkdir -p "$work_dir/gen" "$work_dir/classes" "$work_dir/dex" "$output_dir"

"$build_tools/aapt" package -f -m \
  -J "$work_dir/gen" \
  -M "$project_dir/AndroidManifest.xml" \
  -S "$project_dir/res" \
  -A "$project_dir/assets" \
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
[[ "$apk_fingerprint" == "$expected_signer_sha256" ]] || fail "Signed APK certificate changed unexpectedly"

badging="$("$build_tools/aapt" dump badging "$output_apk" | head -n 1)"
[[ "$badging" == *"name='$expected_package'"* ]] || fail "APK package changed"
[[ "$badging" == *"versionCode='$expected_version_code'"* ]] || fail "APK versionCode changed"
[[ "$badging" == *"versionName='$expected_version_name'"* ]] || fail "APK versionName changed"

apk_sha256="$(sha256sum "$output_apk" | awk '{print tolower($1)}')"
printf 'Stable V2 signing verification: PASS\n'
printf 'Package: %s\nVersionCode: %s\nVersionName: %s\n' "$expected_package" "$expected_version_code" "$expected_version_name"
printf 'Signer SHA-256: %s\nAPK SHA-256: %s\nAPK: %s\n' "$apk_fingerprint" "$apk_sha256" "$output_apk"
