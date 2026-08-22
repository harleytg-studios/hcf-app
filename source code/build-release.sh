#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
sdk_root="${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
build_tools="$sdk_root/build-tools/${BUILD_TOOLS_VERSION:-35.0.0}"
android_jar="$sdk_root/platforms/android-${ANDROID_PLATFORM_VERSION:-35}/android.jar"
manifest="$project_dir/AndroidManifest.xml"
build_info="$project_dir/src/com/harleytg/forum/BuildInfo.java"

[[ -f "$manifest" ]] || { echo "Missing AndroidManifest.xml" >&2; exit 2; }
[[ -f "$build_info" ]] || { echo "Missing BuildInfo.java" >&2; exit 2; }
[[ -x "$build_tools/aapt" ]] || { echo "Missing aapt in $build_tools" >&2; exit 3; }
[[ -x "$build_tools/d8" ]] || { echo "Missing d8 in $build_tools" >&2; exit 4; }
[[ -x "$build_tools/zipalign" ]] || { echo "Missing zipalign in $build_tools" >&2; exit 5; }
[[ -x "$build_tools/apksigner" ]] || { echo "Missing apksigner in $build_tools" >&2; exit 6; }
[[ -f "$android_jar" ]] || { echo "Missing $android_jar" >&2; exit 7; }

package_name="$(sed -n 's/.*package="\([^"]*\)".*/\1/p' "$manifest" | head -1)"
version_code="$(sed -n 's/.*android:versionCode="\([^"]*\)".*/\1/p' "$manifest" | head -1)"
version_name="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' "$manifest" | head -1)"
buildinfo_version_code="$(sed -n 's/.*VERSION_CODE = \([0-9][0-9]*\);.*/\1/p' "$build_info" | head -1)"
buildinfo_apk_name="$(sed -n 's/.*APK_FILE_NAME = "\([^"]*\)";.*/\1/p' "$build_info" | head -1)"
[[ -n "$buildinfo_version_code" && "$version_code" == "$buildinfo_version_code" ]] || { echo "Manifest/BuildInfo versionCode mismatch" >&2; exit 21; }
if grep -R -nE 'Method not decompiled:|throw new UnsupportedOperationException' "$project_dir/src"; then
  echo "Decompiler stubs remain in production source" >&2
  exit 22
fi

case "$package_name" in
  com.harleytg.forum)
    channel="stable"
    output_name="HCF-Stable-v${version_code}.apk"
    default_alias="hcf-stable-v2"
    expected_signer="77E0E96C1177842AAA311A8FC0EBEA29B92D3CD290BB815BDB86AD0E0A85844F"
    ;;
  com.harleytg.forum.dev)
    channel="dev"
    output_name="HCF-Beta-v${version_code}.apk"
    default_alias="hcf-beta-v2"
    expected_signer="${HCF_EXPECTED_SIGNER:-93D49BF9A877C7CFB1B37F9064BD955CD67BD7DD8DB73A9E3F766B59C4BCCE63}"
    ;;
  *)
    echo "Unsupported package: $package_name" >&2
    exit 8
    ;;
esac

[[ "$output_name" == "$buildinfo_apk_name" ]] || { echo "BuildInfo APK filename mismatch" >&2; exit 23; }

keystore_path="${HCF_KEYSTORE:?Set HCF_KEYSTORE to the channel signing JKS}"
keystore_alias="${HCF_KEY_ALIAS:-$default_alias}"
password_file="${HCF_KEY_PASSWORD_FILE:?Set HCF_KEY_PASSWORD_FILE}"
export HCF_APKSIGNER_PASSWORD="$(sed -n '1p' "$password_file")"
output_dir="${HCF_OUTPUT_DIR:-$project_dir/out}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

if [[ -n "$expected_signer" ]]; then
  keyfp="$(keytool -list -v -keystore "$keystore_path" -storepass "$HCF_APKSIGNER_PASSWORD" -alias "$keystore_alias" 2>/dev/null | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -1 | tr '[:lower:]' '[:upper:]' | tr -d ':[:space:]')"
  normalized_expected="$(printf '%s' "$expected_signer" | tr '[:lower:]' '[:upper:]' | tr -d ':[:space:]')"
  [[ "$keyfp" == "$normalized_expected" ]] || { echo "Wrong $channel signer" >&2; exit 20; }
fi

mkdir -p "$work/gen" "$work/classes" "$work/dex" "$output_dir"

"$build_tools/aapt" package -f -m \
  -J "$work/gen" \
  -M "$manifest" \
  -S "$project_dir/res" \
  -A "$project_dir/assets" \
  -I "$android_jar" \
  -F "$work/resources.apk"

mapfile -t java_files < <(find "$work/gen" "$project_dir/src" -name '*.java' -print)
javac --release 8 -classpath "$android_jar" -d "$work/classes" "${java_files[@]}"

mapfile -t class_files < <(find "$work/classes" -name '*.class' -print)
"$build_tools/d8" --lib "$android_jar" --min-api 26 --release --output "$work/dex" "${class_files[@]}"

cp "$work/resources.apk" "$work/unsigned.apk"
(cd "$work/dex" && "$build_tools/aapt" add "$work/unsigned.apk" classes.dex)
"$build_tools/zipalign" -f -p 4 "$work/unsigned.apk" "$work/aligned.apk"
"$build_tools/zipalign" -c -p 4 "$work/aligned.apk"

output_apk="$output_dir/$output_name"
"$build_tools/apksigner" sign \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --v4-signing-enabled true \
  --ks "$keystore_path" \
  --ks-key-alias "$keystore_alias" \
  --ks-pass env:HCF_APKSIGNER_PASSWORD \
  --key-pass env:HCF_APKSIGNER_PASSWORD \
  --out "$output_apk" \
  "$work/aligned.apk"

"$build_tools/apksigner" verify --verbose --print-certs "$output_apk"

printf 'Built %s\nPackage: %s\nVersion: %s (%s)\nChannel: %s\n' \
  "$output_apk" "$package_name" "$version_name" "$version_code" "$channel"
