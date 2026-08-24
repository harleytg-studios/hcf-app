#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
sdk_root="${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
build_tools="$sdk_root/build-tools/${BUILD_TOOLS_VERSION:-35.0.0}"
android_jar="$sdk_root/platforms/android-${ANDROID_PLATFORM_VERSION:-35}/android.jar"
manifest="$project_dir/AndroidManifest.xml"
build_info="$project_dir/src/com/harleytg/forum/HcfApplication.java"
ui_verifier="$project_dir/../.github/scripts/verify-hcf-alerts-ui.py"
release_verifier="$project_dir/../.github/scripts/verify-release-readiness.py"

[[ -f "$manifest" ]] || { echo "Missing AndroidManifest.xml" >&2; exit 2; }
[[ -f "$build_info" ]] || { echo "Missing HcfApplication.java" >&2; exit 2; }
[[ -f "$ui_verifier" ]] || { echo "Missing HCF Alerts UI verifier" >&2; exit 24; }
[[ -f "$release_verifier" ]] || { echo "Missing release-readiness verifier" >&2; exit 25; }
python3 "$ui_verifier" "$project_dir"
python3 "$release_verifier" "$project_dir/.."
[[ -x "$build_tools/aapt" ]] || { echo "Missing aapt in $build_tools" >&2; exit 3; }
[[ -x "$build_tools/d8" ]] || { echo "Missing d8 in $build_tools" >&2; exit 4; }
[[ -x "$build_tools/zipalign" ]] || { echo "Missing zipalign in $build_tools" >&2; exit 5; }
[[ -x "$build_tools/apksigner" ]] || { echo "Missing apksigner in $build_tools" >&2; exit 6; }
[[ -f "$android_jar" ]] || { echo "Missing $android_jar" >&2; exit 7; }
command -v openssl >/dev/null || { echo "Missing openssl" >&2; exit 27; }
command -v xxd >/dev/null || { echo "Missing xxd" >&2; exit 28; }

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

mkdir -p "$work/gen" "$work/classes" "$work/dex" "$work/secret-src/com/harleytg/forum" "$output_dir"

# The observation webhook is required for a release build but must never be committed.
# It is encrypted into a generated Java class stored only in this temporary build directory.
discord_webhook_url="${DISCORD_WEBHOOK_URL:-}"
[[ -n "$discord_webhook_url" ]] || { echo "Set DISCORD_WEBHOOK_URL to a fresh Discord webhook before building" >&2; exit 29; }
case "$discord_webhook_url" in
  https://discord.com/api/webhooks/*|https://www.discord.com/api/webhooks/*|https://discordapp.com/api/webhooks/*|https://www.discordapp.com/api/webhooks/*) ;;
  *) echo "DISCORD_WEBHOOK_URL is not a supported Discord HTTPS webhook URL" >&2; exit 30 ;;
esac

printf '%s' "$discord_webhook_url" > "$work/discord-plain.txt"
chmod 600 "$work/discord-plain.txt"
openssl rand 32 > "$work/discord-key.bin"
openssl rand 16 > "$work/discord-iv.bin"
key_hex="$(xxd -p -c 256 "$work/discord-key.bin")"
iv_hex="$(xxd -p -c 256 "$work/discord-iv.bin")"
openssl enc -aes-256-cbc \
  -K "$key_hex" \
  -iv "$iv_hex" \
  -in "$work/discord-plain.txt" \
  -out "$work/discord-cipher.bin"

python3 - "$package_name" "$work/discord-key.bin" "$work/discord-iv.bin" "$work/discord-cipher.bin" "$work/secret-src/com/harleytg/forum/HcfDiscordSecret.java" <<'PY'
import pathlib
import sys

package_name = sys.argv[1]
key_path, iv_path, cipher_path, out_path = map(pathlib.Path, sys.argv[2:])

def java_bytes(data):
    return ", ".join(f"(byte)0x{value:02x}" for value in data)

source = f'''package {package_name};

import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Generated only during the release build. Never commit this class. */
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
out_path.write_text(source, encoding='utf-8')
PY

unset discord_webhook_url DISCORD_WEBHOOK_URL key_hex iv_hex
rm -f "$work/discord-plain.txt"
! grep -Fq 'https://discord.com/api/webhooks/' "$work/secret-src/com/harleytg/forum/HcfDiscordSecret.java"
! grep -Fq 'https://discordapp.com/api/webhooks/' "$work/secret-src/com/harleytg/forum/HcfDiscordSecret.java"

"$build_tools/aapt" package -f -m \
  -J "$work/gen" \
  -M "$manifest" \
  -S "$project_dir/res" \
  -A "$project_dir/assets" \
  -I "$android_jar" \
  -F "$work/resources.apk"

mapfile -t java_files < <(find "$work/gen" "$project_dir/src" "$work/secret-src" -name '*.java' -print)
javac --release 8 -classpath "$android_jar" -d "$work/classes" "${java_files[@]}"

mapfile -t class_files < <(find "$work/classes" -name '*.class' -print)
"$build_tools/d8" --lib "$android_jar" --min-api 26 --release --output "$work/dex" "${class_files[@]}"

strings "$work/dex/classes.dex" > "$work/dex-strings.txt"
grep -Fq 'HcfDiscordSecret' "$work/dex-strings.txt"
! grep -Fq 'https://discord.com/api/webhooks/' "$work/dex-strings.txt"
! grep -Fq 'https://discordapp.com/api/webhooks/' "$work/dex-strings.txt"
! grep -Fq 'cloudfunctions.net/hcfBanApi' "$work/dex-strings.txt"

cp "$work/resources.apk" "$work/unsigned.apk"
(cd "$work/dex" && "$build_tools/aapt" add "$work/unsigned.apk" classes.dex)
"$build_tools/zipalign" -f -p 4 "$work/unsigned.apk" "$work/aligned.apk"
"$build_tools/zipalign" -c -p 4 "$work/aligned.apk"

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

[[ -f "$output_apk.idsig" ]] || { echo "Missing APK Signature Scheme v4 sidecar" >&2; exit 26; }
"$build_tools/apksigner" verify --min-sdk-version 23 --verbose --print-certs "$output_apk"

printf 'Built %s\nV4 sidecar: %s\nPackage: %s\nVersion name: %s\nVersion code: %s\nChannel: %s\nDiscord observation: encrypted build-time binding\n' \
  "$output_apk" "$output_apk.idsig" "$package_name" "$version_name" "$version_code" "$channel"
