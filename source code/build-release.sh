#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
sdk_root="${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
build_tools="$sdk_root/build-tools/${BUILD_TOOLS_VERSION:-35.0.0}"
android_jar="$sdk_root/platforms/android-${ANDROID_PLATFORM_VERSION:-35}/android.jar"
manifest="$project_dir/AndroidManifest.xml"

[[ -f "$manifest" ]] || { echo "Missing AndroidManifest.xml" >&2; exit 2; }
[[ -x "$build_tools/aapt" ]] || { echo "Missing aapt in $build_tools" >&2; exit 3; }
[[ -x "$build_tools/d8" ]] || { echo "Missing d8 in $build_tools" >&2; exit 4; }
[[ -x "$build_tools/zipalign" ]] || { echo "Missing zipalign in $build_tools" >&2; exit 5; }
[[ -x "$build_tools/apksigner" ]] || { echo "Missing apksigner in $build_tools" >&2; exit 6; }
[[ -f "$android_jar" ]] || { echo "Missing $android_jar" >&2; exit 7; }

package_name="$(sed -n 's/.*package="\([^"]*\)".*/\1/p' "$manifest" | head -1)"
version_code="$(sed -n 's/.*android:versionCode="\([^"]*\)".*/\1/p' "$manifest" | head -1)"
version_name="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' "$manifest" | head -1)"

[[ "$package_name" == "com.harleytg.forum" ]] || { echo "Stable build requires package com.harleytg.forum" >&2; exit 8; }
[[ -n "$version_code" ]] || { echo "Missing Android versionCode" >&2; exit 9; }

output_name="HCF-Stable-v${version_code}.apk"
keystore_path="${HCF_KEYSTORE:?Set HCF_KEYSTORE to the private Stable V2 JKS}"
keystore_alias="${HCF_KEY_ALIAS:-hcf-stable-v2}"
password_file="${HCF_KEY_PASSWORD_FILE:?Set HCF_KEY_PASSWORD_FILE}"
export HCF_APKSIGNER_PASSWORD="$(sed -n '1p' "$password_file")"
output_dir="${HCF_OUTPUT_DIR:-$project_dir/out}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

expected_signer="77E0E96C1177842AAA311A8FC0EBEA29B92D3CD290BB815BDB86AD0E0A85844F"
keyfp="$(keytool -list -v -keystore "$keystore_path" -storepass "$HCF_APKSIGNER_PASSWORD" -alias "$keystore_alias" 2>/dev/null | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -1 | tr '[:lower:]' '[:upper:]' | tr -d ':[:space:]')"
[[ "$keyfp" == "$expected_signer" ]] || { echo 'Wrong Stable signer' >&2; exit 20; }

mkdir -p "$work/gen" "$work/classes" "$work/dex" "$output_dir"
"$build_tools/aapt" package -f -m -J "$work/gen" -M "$manifest" -S "$project_dir/res" -A "$project_dir/assets" -I "$android_jar" -F "$work/resources.apk"
mapfile -t java_files < <(find "$work/gen" "$project_dir/src" -name '*.java' -print)
javac --release 8 -classpath "$android_jar" -d "$work/classes" "${java_files[@]}"
mapfile -t class_files < <(find "$work/classes" -name '*.class' -print)
"$build_tools/d8" --lib "$android_jar" --min-api 26 --release --output "$work/dex" "${class_files[@]}"
cp "$work/resources.apk" "$work/unsigned.apk"
(cd "$work/dex" && "$build_tools/aapt" add "$work/unsigned.apk" classes.dex)
"$build_tools/zipalign" -f -p 4 "$work/unsigned.apk" "$work/aligned.apk"
"$build_tools/zipalign" -c -p 4 "$work/aligned.apk"
output_apk="$output_dir/$output_name"
"$build_tools/apksigner" sign --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --ks "$keystore_path" --ks-key-alias "$keystore_alias" --ks-pass env:HCF_APKSIGNER_PASSWORD --key-pass env:HCF_APKSIGNER_PASSWORD --out "$output_apk" "$work/aligned.apk"
"$build_tools/apksigner" verify --verbose --print-certs "$output_apk"
printf 'Built %s\nPackage: %s\nVersion: %s (%s)\nChannel: stable\n' "$output_apk" "$package_name" "$version_name" "$version_code"
