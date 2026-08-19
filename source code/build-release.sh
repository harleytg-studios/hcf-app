#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
repo_root="$(git -C "$project_dir" rev-parse --show-toplevel 2>/dev/null || true)"
[[ -n "$repo_root" ]] || { echo "ERROR: build-release.sh must run inside the hcf-app Git repository." >&2; exit 10; }

sdk_root="${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
build_tools_version="${BUILD_TOOLS_VERSION:-35.0.0}"
platform_version="${ANDROID_PLATFORM_VERSION:-35}"
build_tools="$sdk_root/build-tools/$build_tools_version"
android_jar="$sdk_root/platforms/android-$platform_version/android.jar"
keystore_path="${HCF_KEYSTORE:?Set HCF_KEYSTORE}"
keystore_alias="${HCF_KEY_ALIAS:-hcf-beta-v2}"
keystore_password_file="${HCF_KEY_PASSWORD_FILE:?Set HCF_KEY_PASSWORD_FILE}"
export HCF_APKSIGNER_PASSWORD="$(sed -n '1p' "$keystore_password_file")"
output_dir="${HCF_OUTPUT_DIR:-$project_dir/out}"
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/hcf-build.XXXXXX")"
generated_dir="$project_dir/.build-generated"
trap 'rm -rf "$work_dir" "$generated_dir"' EXIT

expected_signer_sha256="93D49BF9A877C7CFB1B37F9064BD955CD67BD7DD8DB73A9E3F766B59C4BCCE63"
expected_package="com.harleytg.forum.dev"
expected_version_code="10000035"
expected_version_name="1.0 (10000035)"

fail() { echo "ERROR: $*" >&2; exit 20; }
normalize_fingerprint() { printf '%s' "$1" | tr '[:lower:]' '[:upper:]' | tr -d ':[:space:]'; }

for tool in aapt d8 zipalign apksigner; do
  [[ -x "$build_tools/$tool" ]] || fail "Missing Android build tool: $build_tools/$tool"
done
[[ -f "$android_jar" ]] || fail "Missing Android platform jar: $android_jar"
[[ -f "$project_dir/AndroidManifest.xml.in" ]] || fail "Missing AndroidManifest.xml.in"
[[ -f "$keystore_path" ]] || fail "Missing DEV keystore"
[[ -f "$keystore_password_file" ]] || fail "Missing DEV keystore password file"

git -C "$repo_root" remote get-url origin >/dev/null 2>&1 || fail "Git remote 'origin' is required to read MAIN configuration"
if [[ "${HCF_SKIP_FETCH_MAIN:-0}" != "1" ]]; then
  git -C "$repo_root" fetch --quiet origin main || fail "Could not fetch origin/main"
fi
git -C "$repo_root" rev-parse --verify origin/main >/dev/null 2>&1 || fail "origin/main does not exist"

rm -rf "$generated_dir"
mkdir -p "$generated_dir/assets" "$work_dir/gen" "$work_dir/classes" "$work_dir/dex" "$output_dir"
git -C "$repo_root" show origin/main:configs/domains.config > "$generated_dir/domains.config" \
  || fail "MAIN configs/domains.config is missing"
git -C "$repo_root" show origin/main:configs/firebase.config > "$generated_dir/firebase.config" \
  || fail "MAIN configs/firebase.config is missing"

validate_host() {
  local host="$1" lower
  [[ -n "$host" ]] || fail "Empty forum hostname"
  [[ "$host" != *"://"* ]] || fail "Hostname must not include a scheme: $host"
  [[ "$host" != *"/"* && "$host" != *"\\"* ]] || fail "Hostname must not include a path: $host"
  [[ ! "$host" =~ [[:space:]] ]] || fail "Hostname must not contain whitespace: $host"
  lower="${host,,}"
  [[ "$host" == "$lower" ]] || fail "Hostname must be lowercase: $host"
  [[ "$lower" != *".online"* ]] || fail "Retired .online forum domains are forbidden: $host"
  [[ "$lower" =~ ^[a-z0-9.-]+$ ]] || fail "Malformed forum hostname: $host"
  [[ "$lower" == *.* && "$lower" != .* && "$lower" != *. && "$lower" != *..* ]] || fail "Malformed forum hostname: $host"
}

declare -a domain_hosts=()
declare -A domain_seen=()
declare -A domain_config=()
primary_host=""
section=""
while IFS= read -r raw || [[ -n "$raw" ]]; do
  raw="${raw%$'\r'}"
  [[ -z "$raw" || "$raw" =~ ^[[:space:]]*# ]] && continue
  if [[ "$raw" =~ ^\[([a-z]+)\]$ ]]; then
    section="${BASH_REMATCH[1]}"
    case "$section" in config|primary|backups|routing) ;; *) fail "Unsupported domains.config section: [$section]" ;; esac
    continue
  fi
  [[ "$raw" =~ ^([a-z0-9_]+)=([^[:space:]].*|)$ ]] || fail "Unsupported domains.config syntax: $raw"
  key="${BASH_REMATCH[1]}"; value="${BASH_REMATCH[2]}"
  [[ -n "$section" ]] || fail "domains.config key outside a section: $key"
  [[ "$value" == "${value# }" && "$value" == "${value% }" ]] || fail "Whitespace around domains.config value: $key"
  case "$section:$key" in
    config:version|config:https_only|config:open_registered_domains_in_app|config:preserve_path|config:preserve_query|config:preserve_fragment)
      domain_config["$key"]="$value" ;;
    primary:domain)
      [[ -z "$primary_host" ]] || fail "Duplicate primary domain key"
      validate_host "$value"; primary_host="$value"; domain_hosts+=("$value") ;;
    backups:domain_*)
      [[ "$key" =~ ^domain_[0-9]+$ ]] || fail "Unsupported backup-domain key: $key"
      validate_host "$value"; domain_hosts+=("$value") ;;
    routing:external_domains|routing:unknown_hcf_domain)
      [[ "$value" == "browser" ]] || fail "Routing policy must be browser for $key" ;;
    *) fail "Unsupported domains.config key: $section.$key" ;;
  esac
done < "$generated_dir/domains.config"

[[ -n "$primary_host" ]] || fail "domains.config has no primary domain"
[[ "${domain_config[version]:-}" == "1" ]] || fail "domains.config version must be 1"
for key in https_only open_registered_domains_in_app preserve_path preserve_query preserve_fragment; do
  [[ "${domain_config[$key]:-}" == "true" ]] || fail "domains.config $key must be true"
done
for host in "${domain_hosts[@]}"; do
  [[ -z "${domain_seen[$host]:-}" ]] || fail "Duplicate forum domain: $host"
  domain_seen["$host"]=1
done

# A Dev source build must never regain canonical forum hosts in Java.
if grep -R -n -E 'forum\.harleytg\.com|harleysclan\.freeflarum\.com|forum\.harleytg\.online' "$project_dir/src" --include='*.java' >/dev/null 2>&1; then
  grep -R -n -E 'forum\.harleytg\.com|harleysclan\.freeflarum\.com|forum\.harleytg\.online' "$project_dir/src" --include='*.java' >&2 || true
  fail "Hardcoded forum-domain configuration found in Java source"
fi

section=""
declare -A firebase=()
while IFS= read -r raw || [[ -n "$raw" ]]; do
  raw="${raw%$'\r'}"
  [[ -z "$raw" || "$raw" =~ ^[[:space:]]*# ]] && continue
  if [[ "$raw" == "[firebase]" ]]; then section="firebase"; continue; fi
  [[ "$raw" =~ ^([a-z0-9_]+)=(.*)$ ]] || fail "Unsupported firebase.config syntax: $raw"
  [[ "$section" == "firebase" ]] || fail "firebase.config supports only [firebase]"
  key="${BASH_REMATCH[1]}"; value="${BASH_REMATCH[2]}"
  case "$key" in api_key|auth_domain|project_id|storage_bucket|messaging_sender_id|app_id|measurement_id) ;; *) fail "Unsupported firebase.config key: $key" ;; esac
  [[ -n "$value" ]] || fail "firebase.config field is empty: $key"
  [[ -z "${firebase[$key]:-}" ]] || fail "Duplicate firebase.config key: $key"
  firebase["$key"]="$value"
done < "$generated_dir/firebase.config"
for key in api_key auth_domain project_id storage_bucket messaging_sender_id app_id measurement_id; do
  [[ -n "${firebase[$key]:-}" ]] || fail "firebase.config missing required field: $key"
done

# Generated assets are build artifacts only. Existing non-config assets are copied if present.
if [[ -d "$project_dir/assets" ]]; then cp -a "$project_dir/assets/." "$generated_dir/assets/"; fi
rm -f "$generated_dir/assets/firebase-config.js" "$generated_dir/assets/domains.runtime" "$generated_dir/assets/firebase.runtime"
cp "$generated_dir/domains.config" "$generated_dir/assets/domains.runtime"
cp "$generated_dir/firebase.config" "$generated_dir/assets/firebase.runtime"

js_escape() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }
cat > "$generated_dir/assets/firebase-config.js" <<EOF
// GENERATED from MAIN configs/firebase.config. Do not edit or commit.
const firebaseConfig = {
  apiKey: "$(js_escape "${firebase[api_key]}")",
  authDomain: "$(js_escape "${firebase[auth_domain]}")",
  projectId: "$(js_escape "${firebase[project_id]}")",
  storageBucket: "$(js_escape "${firebase[storage_bucket]}")",
  messagingSenderId: "$(js_escape "${firebase[messaging_sender_id]}")",
  appId: "$(js_escape "${firebase[app_id]}")",
  measurementId: "$(js_escape "${firebase[measurement_id]}")"
};
EOF

# Build the manifest from the template and the same validated domain registry.
while IFS= read -r line || [[ -n "$line" ]]; do
  if [[ "$line" == *"@@HCF_APP_LINK_FILTERS@@"* ]]; then
    for host in "${domain_hosts[@]}"; do
      cat <<EOF
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="https" android:host="$host" />
            </intent-filter>
EOF
    done
  else
    printf '%s\n' "$line"
  fi
done < "$project_dir/AndroidManifest.xml.in" > "$generated_dir/AndroidManifest.xml"

grep -q "android:host=\"$primary_host\"" "$generated_dir/AndroidManifest.xml" || fail "Primary App Link was not generated"
if grep -q 'android:scheme="http" android:host=' "$generated_dir/AndroidManifest.xml"; then
  fail "HTTP App Link generated unexpectedly"
fi
if grep -q '@@HCF_APP_LINK_FILTERS@@' "$generated_dir/AndroidManifest.xml"; then
  fail "Manifest placeholder was not replaced"
fi

key_fingerprint="$(keytool -list -v -keystore "$keystore_path" -storepass "$HCF_APKSIGNER_PASSWORD" -alias "$keystore_alias" 2>/dev/null \
  | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n 1)"
key_fingerprint="$(normalize_fingerprint "$key_fingerprint")"
[[ -n "$key_fingerprint" && "$key_fingerprint" == "$expected_signer_sha256" ]] \
  || fail "Refusing to build DEV APK with the wrong signing certificate"

"$build_tools/aapt" package -f -m \
  -J "$work_dir/gen" \
  -M "$generated_dir/AndroidManifest.xml" \
  -S "$project_dir/res" \
  -A "$generated_dir/assets" \
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
[[ "$apk_fingerprint" == "$expected_signer_sha256" ]] || fail "Signed APK certificate changed unexpectedly"

badging="$("$build_tools/aapt" dump badging "$output_apk" | head -n 1)"
[[ "$badging" == *"name='$expected_package'"* ]] || fail "APK package changed"
[[ "$badging" == *"versionCode='$expected_version_code'"* ]] || fail "APK versionCode changed"
[[ "$badging" == *"versionName='$expected_version_name'"* ]] || fail "APK versionName changed"

apk_sha256="$(sha256sum "$output_apk" | awk '{print toupper($1)}')"
printf 'Configuration source: origin/main:configs/{domains.config,firebase.config}\n'
printf 'Registered domains: %s\n' "${domain_hosts[*]}"
printf 'Java file count: %s\n' "$(find "$project_dir/src" -name '*.java' | wc -l | tr -d ' ')"
printf 'DEV signing-line verification: PASS\n'
printf 'Package: %s\nVersionCode: %s\nVersionName: %s\n' "$expected_package" "$expected_version_code" "$expected_version_name"
printf 'Signer SHA-256: %s\nAPK SHA-256: %s\nAPK: %s\n' "$apk_fingerprint" "$apk_sha256" "$output_apk"
