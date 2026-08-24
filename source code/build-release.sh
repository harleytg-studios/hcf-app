#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
repo_root="$(cd "$project_dir/.." && pwd)"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[[ -n "$sdk_root" ]] || { echo "Set ANDROID_SDK_ROOT or ANDROID_HOME" >&2; exit 2; }
build_tools="$sdk_root/build-tools/${BUILD_TOOLS_VERSION:-35.0.0}"
android_jar="$sdk_root/platforms/android-${ANDROID_PLATFORM_VERSION:-35}/android.jar"
manifest="$project_dir/AndroidManifest.xml"
overlay="$project_dir/stable-build-overlay.py"
ui_verifier="$repo_root/.github/scripts/verify-hcf-alerts-ui.py"
release_verifier="$repo_root/.github/scripts/verify-release-readiness.py"

[[ -f "$manifest" ]] || { echo "Missing AndroidManifest.xml" >&2; exit 3; }
[[ -f "$overlay" ]] || { echo "Missing stable-build-overlay.py" >&2; exit 4; }
[[ -f "$ui_verifier" ]] || { echo "Missing HCF Alerts UI verifier" >&2; exit 5; }
[[ -f "$release_verifier" ]] || { echo "Missing release-readiness verifier" >&2; exit 6; }
python3 "$ui_verifier" "$project_dir"
python3 "$release_verifier" "$repo_root"
[[ -x "$build_tools/aapt" ]] || { echo "Missing aapt in $build_tools" >&2; exit 7; }
[[ -x "$build_tools/d8" ]] || { echo "Missing d8 in $build_tools" >&2; exit 8; }
[[ -x "$build_tools/zipalign" ]] || { echo "Missing zipalign in $build_tools" >&2; exit 9; }
[[ -x "$build_tools/apksigner" ]] || { echo "Missing apksigner in $build_tools" >&2; exit 10; }
[[ -f "$android_jar" ]] || { echo "Missing $android_jar" >&2; exit 11; }
command -v openssl >/dev/null || { echo "Missing openssl" >&2; exit 12; }
command -v xxd >/dev/null || { echo "Missing xxd" >&2; exit 13; }

package_name="$(sed -n 's/.*package="\([^"]*\)".*/\1/p' "$manifest" | head -1)"
version_code="$(sed -n 's/.*android:versionCode="\([^"]*\)".*/\1/p' "$manifest" | head -1)"
version_name="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' "$manifest" | head -1)"
[[ "$package_name" == "com.harleytg.forum" ]] || { echo "Stable build requires package com.harleytg.forum" >&2; exit 14; }
[[ "$version_code" == "10000092" ]] || { echo "Stable versionCode must remain 10000092" >&2; exit 15; }
[[ "$version_name" == "1.0 (10000092)" ]] || { echo "Stable versionName mismatch" >&2; exit 16; }

channel="stable"
output_name="HCF-Stable-v${version_code}.apk"
default_alias="hcf-stable-v2"
expected_signer="77E0E96C1177842AAA311A8FC0EBEA29B92D3CD290BB815BDB86AD0E0A85844F"
output_dir="${HCF_OUTPUT_DIR:-$project_dir/out}"
unsigned_only="${HCF_UNSIGNED_ONLY:-0}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/gen" "$work/classes" "$work/dex" "$work/secret-src/com/harleytg/forum" "$output_dir"

python3 "$overlay" "$project_dir" "$work/src" "$work/res"
build_info="$work/src/com/harleytg/forum/HcfApplication.java"
buildinfo_version_code="$(sed -n 's/.*VERSION_CODE = \([0-9][0-9]*\);.*/\1/p' "$build_info" | head -1)"
buildinfo_apk_name="$(sed -n 's/.*APK_FILE_NAME = "\([^"]*\)";.*/\1/p' "$build_info" | head -1)"
[[ "$buildinfo_version_code" == "$version_code" ]] || { echo "Manifest/BuildInfo versionCode mismatch" >&2; exit 17; }
[[ "$buildinfo_apk_name" == "$output_name" ]] || { echo "BuildInfo APK filename mismatch" >&2; exit 18; }
if grep -R -nE 'Method not decompiled:|Code decompiled incorrectly|throw new UnsupportedOperationException|JADX ERROR' "$work/src"; then
  echo "Decompiler stubs remain in production source" >&2
  exit 19
fi

# Observation credential is release-build input only. It is encrypted into a
# generated temporary class and never stored in the repository tree.
discord_webhook_url="${DISCORD_WEBHOOK_URL:-}"
[[ -n "$discord_webhook_url" ]] || { echo "Set DISCORD_WEBHOOK_URL for the release build" >&2; exit 20; }
case "$discord_webhook_url" in
  https://discord.com/api/webhooks/*|https://www.discord.com/api/webhooks/*|https://discordapp.com/api/webhooks/*|https://www.discordapp.com/api/webhooks/*) ;;
  *) echo "DISCORD_WEBHOOK_URL is not a supported Discord HTTPS webhook URL" >&2; exit 21 ;;
esac
printf '%s' "$discord_webhook_url" > "$work/discord-plain.txt"
chmod 600 "$work/discord-plain.txt"
openssl rand 32 > "$work/discord-key.bin"
openssl rand 16 > "$work/discord-iv.bin"
key_hex="$(xxd -p -c 256 "$work/discord-key.bin")"
iv_hex="$(xxd -p -c 256 "$work/discord-iv.bin")"
openssl enc -aes-256-cbc -K "$key_hex" -iv "$iv_hex" -in "$work/discord-plain.txt" -out "$work/discord-cipher.bin"

python3 - "$work/discord-key.bin" "$work/discord-iv.bin" "$work/discord-cipher.bin" "$work/secret-src/com/harleytg/forum/HcfDiscordSecret.java" <<'PY'
import pathlib
import sys
key_path, iv_path, cipher_path, out_path = map(pathlib.Path, sys.argv[1:])
def java_bytes(data):
    return ", ".join(f"(byte)0x{value:02x}" for value in data)
source = f'''package com.harleytg.forum;

import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Generated during the release build only. Never commit this class. */
public final class HcfDiscordSecret {{
    private static final byte[] KEY = new byte[]{{{java_bytes(key_path.read_bytes())}}};
    private static final byte[] IV = new byte[]{{{java_bytes(iv_path.read_bytes())}}};
    private static final byte[] DATA = new byte[]{{{java_bytes(cipher_path.read_bytes())}}};
    private HcfDiscordSecret() {{}}
    public static String decrypt() throws Exception {{
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"), new IvParameterSpec(IV));
        return new String(cipher.doFinal(DATA), StandardCharsets.UTF_8);
    }}
}}
'''
out_path.write_text(source, encoding="utf-8")
PY
unset discord_webhook_url DISCORD_WEBHOOK_URL key_hex iv_hex
rm -f "$work/discord-plain.txt"
if grep -Eaq 'https://(www\.)?discord(app)?\.com/api/webhooks/[0-9]{5,}/[A-Za-z0-9._-]{20,}' "$work/secret-src/com/harleytg/forum/HcfDiscordSecret.java"; then
  echo "Plaintext Discord webhook credential found in generated source" >&2
  exit 22
fi

"$build_tools/aapt" package -f -m \
  -J "$work/gen" \
  -M "$manifest" \
  -S "$work/res" \
  -A "$project_dir/assets" \
  -I "$android_jar" \
  -F "$work/resources.apk"

mapfile -t java_files < <(find "$work/gen" "$work/src" "$work/secret-src" -name '*.java' -print)
javac --release 8 -classpath "$android_jar" -d "$work/classes" "${java_files[@]}"
mapfile -t class_files < <(find "$work/classes" -name '*.class' -print)
"$build_tools/d8" --lib "$android_jar" --min-api 26 --release --output "$work/dex" "${class_files[@]}"

strings "$work/dex/classes.dex" > "$work/dex-strings.txt"
grep -Fq 'same build code, revised APK hash' "$work/dex-strings.txt" || { echo "Same-version hash updater missing" >&2; exit 23; }
grep -Fq 'APK SHA-256 does not match' "$work/dex-strings.txt" || { echo "APK SHA-256 verification missing" >&2; exit 24; }
grep -Fq 'App Setup' "$work/dex-strings.txt" || { echo "Setup Center missing" >&2; exit 25; }
grep -Fq 'HCF Alerts are the real forum alerts.' "$work/dex-strings.txt" || { echo "Approved HCF Alerts UI missing" >&2; exit 26; }
grep -Fq 'HcfDiscordSecret' "$work/dex-strings.txt" || { echo "Discord observation binding missing" >&2; exit 27; }
grep -Fq 'configs/ban-list.json' "$work/dex-strings.txt" || { echo "Ban-system configuration missing" >&2; exit 28; }
grep -Fq 'Stable Update Available' "$work/dex-strings.txt" || { echo "Stable updater UI missing" >&2; exit 29; }
if grep -Fq 'Beta Update Available' "$work/dex-strings.txt"; then echo "Beta updater UI leaked into Stable" >&2; exit 30; fi
if grep -Fq 'com.harleytg.forum.dev' "$work/dex-strings.txt"; then echo "Dev package leaked into Stable DEX" >&2; exit 31; fi
if grep -Eaq 'https://(www\.)?discord(app)?\.com/api/webhooks/[0-9]{5,}/[A-Za-z0-9._-]{20,}' "$work/dex-strings.txt"; then echo "Plaintext Discord webhook found in DEX" >&2; exit 32; fi
if grep -Fq 'cloudfunctions.net/hcfBanApi' "$work/dex-strings.txt"; then echo "Obsolete Firebase ban endpoint found in DEX" >&2; exit 33; fi

cp "$work/resources.apk" "$work/unsigned.apk"
(cd "$work/dex" && "$build_tools/aapt" add "$work/unsigned.apk" classes.dex)
"$build_tools/zipalign" -f -p 4 "$work/unsigned.apk" "$work/aligned.apk"
"$build_tools/zipalign" -c -p 4 "$work/aligned.apk"

if [[ "$unsigned_only" == "1" ]]; then
  output_apk="$output_dir/HCF-Stable-v${version_code}-aligned-unsigned.apk"
  cp "$work/aligned.apk" "$output_apk"
  "$build_tools/aapt" dump badging "$output_apk" | head -1
  printf 'Built verified unsigned Stable APK: %s\nPackage: %s\nVersion: %s\n' "$output_apk" "$package_name" "$version_name"
  exit 0
fi

keystore_path="${HCF_KEYSTORE:?Set HCF_KEYSTORE to the Stable signing JKS}"
keystore_alias="${HCF_KEY_ALIAS:-$default_alias}"
password_file="${HCF_KEY_PASSWORD_FILE:?Set HCF_KEY_PASSWORD_FILE}"
export HCF_APKSIGNER_PASSWORD="$(sed -n '1p' "$password_file")"
keyfp="$(keytool -list -v -keystore "$keystore_path" -storepass "$HCF_APKSIGNER_PASSWORD" -alias "$keystore_alias" 2>/dev/null | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -1 | tr '[:lower:]' '[:upper:]' | tr -d ':[:space:]')"
normalized_expected="$(printf '%s' "$expected_signer" | tr '[:lower:]' '[:upper:]' | tr -d ':[:space:]')"
[[ "$keyfp" == "$normalized_expected" ]] || { echo "Wrong Stable signer" >&2; exit 34; }

output_apk="$output_dir/$output_name"
"$build_tools/apksigner" sign \
  --min-sdk-version 23 \
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
[[ -f "$output_apk.idsig" ]] || { echo "Missing APK Signature Scheme v4 sidecar" >&2; exit 35; }
"$build_tools/apksigner" verify --min-sdk-version 23 --verbose --print-certs "$output_apk"
printf 'Built %s\nV4 sidecar: %s\nPackage: %s\nVersion name: %s\nVersion code: %s\nChannel: %s\nDiscord observation: encrypted build-time binding\n' \
  "$output_apk" "$output_apk.idsig" "$package_name" "$version_name" "$version_code" "$channel"
