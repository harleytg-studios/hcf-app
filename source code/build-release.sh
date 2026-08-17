#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
sdk_root="${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
build_tools_version="${BUILD_TOOLS_VERSION:-35.0.0}"
platform_version="${ANDROID_PLATFORM_VERSION:-35}"
build_tools="$sdk_root/build-tools/$build_tools_version"
android_jar="$sdk_root/platforms/android-$platform_version/android.jar"
keystore_path="${HCF_KEYSTORE:?Set HCF_KEYSTORE}"
keystore_alias="${HCF_KEY_ALIAS:-hcf-release}"
keystore_password_file="${HCF_KEY_PASSWORD_FILE:?Set HCF_KEY_PASSWORD_FILE}"
export HCF_APKSIGNER_PASSWORD="$(sed -n '1p' "$keystore_password_file")"
output_dir="${HCF_OUTPUT_DIR:-$project_dir/out}"
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/hcf-build.XXXXXX")"

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
  --out "$output_dir/HarleysClanForum-1.0.apk" \
  "$work_dir/aligned.apk"

"$build_tools/apksigner" verify --verbose --print-certs "$output_dir/HarleysClanForum-1.0.apk"
