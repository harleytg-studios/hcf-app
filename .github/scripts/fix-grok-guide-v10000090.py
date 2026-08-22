from pathlib import Path
import json
import re
import shutil
import subprocess
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: fix-grok-guide-v10000090.py <dev-source-code> <stable-source-code>")

dev = Path(sys.argv[1]).resolve()
stable = Path(sys.argv[2]).resolve()
repo = dev.parent
src = dev / "src/com/harleytg/forum"
stable_src = stable / "src/com/harleytg/forum"

STABLE_PACKAGE = "com.harleytg.forum"
DEV_PACKAGE = "com.harleytg.forum.dev"
VERSION_CODE = 10000090
INTERNAL_BUILD = 110
BETA_SIGNER = "93:D4:9B:F9:A8:77:C7:CF:B1:B3:7F:90:64:BD:95:5C:D6:7B:D7:DD:8D:B7:3A:9E:3F:76:6B:59:C4:BC:CE:63"
STABLE_SIGNER = "77:E0:E9:6C:11:77:84:2A:AA:31:1A:8F:C0:EB:EA:29:B9:2D:3C:D2:90:BB:81:5B:DB:86:AD:0E:0A:85:84:4F"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def copy_clean_stable(name: str) -> None:
    source = stable_src / name
    target = src / name
    if not source.is_file():
        raise SystemExit(f"missing Stable recovery source: {source}")
    text = read(source).replace(STABLE_PACKAGE, DEV_PACKAGE)
    write(target, text)
    print(f"recovered {name} from known-good Stable source")


def replace_java_string_number(text: str, old: str) -> str:
    # Convert hard-coded build numbers inside Java string literals into the
    # authoritative BuildInfo.VERSION_CODE expression.
    pattern = re.compile(r'"(?:\\.|[^"\\])*"', re.S)

    def repl(match):
        token = match.group(0)
        if old not in token:
            return token
        body = token[1:-1]
        parts = body.split(old)
        pieces = []
        for index, part in enumerate(parts):
            pieces.append('"' + part + '"')
            if index < len(parts) - 1:
                pieces.append("BuildInfo.VERSION_CODE")
        return " + ".join(pieces)

    return pattern.sub(repl, text)


def replace_method(text: str, signature: str, next_marker: str, replacement: str) -> str:
    start = text.find(signature)
    if start < 0:
        raise SystemExit(f"method signature not found: {signature}")
    end = text.find(next_marker, start)
    if end < 0:
        raise SystemExit(f"next method marker not found after: {signature}")
    return text[:start] + replacement.rstrip() + "\n\n" + text[end:]


# ---------------------------------------------------------------------------
# P0: recover every known decompiler-broken runtime class from the compileable
# Stable tree. MainActivity is recovered too; Beta-specific changes are then
# re-applied below. This makes the checked-in dev source the real build source.
# ---------------------------------------------------------------------------
for filename in [
    "AppUpdateDownloader.java",
    "NotificationHelper.java",
    "InstantNotificationService.java",
    "FirebaseConfigLoader.java",
    "LogsActivity.java",
    "MainActivity.java",
]:
    copy_clean_stable(filename)

# Reapply maintained Beta account-security sync and Notifications UI patch.
legacy_patch = repo / ".github/scripts/patch-v10000089-auto-security.py"
if not legacy_patch.is_file():
    raise SystemExit(f"missing Beta feature patch: {legacy_patch}")
subprocess.run([sys.executable, str(legacy_patch), str(dev)], check=True)

# ---------------------------------------------------------------------------
# Build identity: one authoritative BuildInfo value, manifest parity, README.
# ---------------------------------------------------------------------------
build_info = src / "BuildInfo.java"
text = read(build_info)
text = re.sub(r'APK_FILE_NAME = "[^"]+";', f'APK_FILE_NAME = "HCF-Beta-v{VERSION_CODE}.apk";', text)
text = re.sub(r'INTERNAL_BUILD = \d+;', f'INTERNAL_BUILD = {INTERNAL_BUILD};', text)
text = re.sub(r'USER_AGENT_MARKER = "[^"]+";', f'USER_AGENT_MARKER = "HarleysClanForumApp/1.0 Build/{VERSION_CODE}";', text)
text = re.sub(r'VERSION_CODE = \d+;', f'VERSION_CODE = {VERSION_CODE};', text)
if "static String installedVersionName()" not in text:
    marker = "    static String userAgent(String baseUserAgent) {"
    helper = (
        "    static String installedVersionName() {\n"
        "        return VERSION + \" (\" + VERSION_CODE + \")\";\n"
        "    }\n\n"
    )
    if marker not in text:
        raise SystemExit("BuildInfo userAgent marker missing")
    text = text.replace(marker, helper + marker, 1)
write(build_info, text)

manifest = dev / "AndroidManifest.xml"
text = read(manifest)
text = re.sub(r'android:versionCode="\d+"', f'android:versionCode="{VERSION_CODE}"', text, count=1)
text = re.sub(r'android:versionName="[^"]+"', f'android:versionName="1.0 ({VERSION_CODE})"', text, count=1)
text = re.sub(r'(<meta-data\s+android:name="com\.harleytg\.APP_VERSION"\s+android:value=")[^"]+("/>)', rf'\g<1>1.0 ({VERSION_CODE})\2', text)
text = re.sub(r'(<meta-data\s+android:name="com\.harleytg\.APP_VERSION_CODE"\s+android:value=")[^"]+("/>)', rf'\g<1>{VERSION_CODE}\2', text)
text = text.replace('android:value="stable"', 'android:value="dev"')
write(manifest, text)

readme = repo / "README.md"
if readme.is_file():
    text = read(readme)
    text = re.sub(r'Version name: `[^`]+`', f'Version name: `1.0 ({VERSION_CODE})`', text)
    text = re.sub(r'Version code: `\d+`', f'Version code: `{VERSION_CODE}`', text)
    text = re.sub(r'Internal build: `\d+`', f'Internal build: `{INTERNAL_BUILD}`', text)
    write(readme, text)

# ---------------------------------------------------------------------------
# AppUpdateDownloader: clean source is restored above. Make updater fallback
# build-driven and make cleanup target Beta updater APK names.
# ---------------------------------------------------------------------------
updater = src / "AppUpdateDownloader.java"
text = read(updater)
text = text.replace("return 10000071L;", "return BuildInfo.VERSION_CODE;")
text = text.replace('name.startsWith("hcf-stable-")', 'name.startsWith("hcf-beta-")')
text = text.replace('name.startsWith("harleysclanforum-stable-")', 'name.startsWith("harleysclanforum-beta-")')
text = text.replace('"stable installer artifacts removed="', '"beta installer artifacts removed="')
write(updater, text)

# ---------------------------------------------------------------------------
# NotificationHelper: HCF Alerts are never disabled by an in-app preference.
# Android permission/channel state is the final authority. Silent Alerts remain
# a separate service-status channel with explicit app-disabled state.
# ---------------------------------------------------------------------------
helper = src / "NotificationHelper.java"
text = read(helper)
text = text.replace('static final String CHANNEL_GROUP_NAME = "Harley\'s Clan Forum";', 'static final String CHANNEL_GROUP_NAME = "Harley\'s Clan Forum [Beta]";')
text = re.sub(
    r'static boolean isEnabledByUser\(Context context\) \{\s*return true;\s*\}',
    'static boolean isEnabledByUser(Context context) {\n        // Product rule: HCF Alerts are required and have no app-level OFF state.\n        // Android notification permission and per-channel settings remain authoritative.\n        return true;\n    }',
    text,
    count=1,
)
old_status = '''        if (SILENT_CHANNEL_ID.equals(str)) {
            return "On • Silent";
        }'''
new_status = '''        if (SILENT_CHANNEL_ID.equals(str)) {
            return silencePassiveEnabled(context) ? "Disabled by app setting" : "Enabled • Silent";
        }'''
if old_status in text:
    text = text.replace(old_status, new_status, 1)
old_can_post = '''    static boolean canPostOnChannel(Context context, String str) {
        return isEnabledByUser(context) && hasRuntimePermission(context) && areAppNotificationsEnabled(context) && channelImportance(context, str) != 0;
    }'''
new_can_post = '''    static boolean canPostOnChannel(Context context, String str) {
        if (SILENT_CHANNEL_ID.equals(str) && silencePassiveEnabled(context)) return false;
        return isEnabledByUser(context) && hasRuntimePermission(context) && areAppNotificationsEnabled(context) && channelImportance(context, str) != 0;
    }'''
if old_can_post in text:
    text = text.replace(old_can_post, new_can_post, 1)
write(helper, text)

# ---------------------------------------------------------------------------
# InstantNotificationService: Stable recovery already restores the main sync
# body. Add defensive silence gating so a race cannot re-create the foreground
# service after the user disables HCF Silent Alerts.
# ---------------------------------------------------------------------------
service = src / "InstantNotificationService.java"
text = read(service)
old_start = '''    static void start(Context context) {
        if (context == null) return;
        if (!hasSession(context)) {
            stop(context);
            return;
        }
        startWithAction(context, null);
    }'''
new_start = '''    static void start(Context context) {
        if (context == null) return;
        if (!hasSession(context) || NotificationHelper.silencePassiveEnabled(context)) {
            stop(context);
            return;
        }
        startWithAction(context, null);
    }'''
if old_start in text:
    text = text.replace(old_start, new_start, 1)
old_start_action = '''        SharedPreferences prefs = context.getSharedPreferences("hcf_app", 0);
        if (!prefs.getBoolean("background_notification_sync", true) || !hasSession(context)) {
            stop(context);
            return;
        }'''
new_start_action = '''        SharedPreferences prefs = context.getSharedPreferences("hcf_app", 0);
        if (!prefs.getBoolean("background_notification_sync", true) || !hasSession(context)) {
            stop(context);
            return;
        }
        if (NotificationHelper.silencePassiveEnabled(context)) {
            if (ACTION_SYNC_NOW.equals(action)) requestOneShotSync(context);
            else stop(context);
            return;
        }'''
if old_start_action in text:
    text = text.replace(old_start_action, new_start_action, 1)
write(service, text)

# ---------------------------------------------------------------------------
# MainActivity: Beta branding and all user-facing old build-code literals are
# converted to BuildInfo.VERSION_CODE. The recovered Stable implementation also
# restores file chooser + installer permission onActivityResult.
# ---------------------------------------------------------------------------
main = src / "MainActivity.java"
text = read(main)
replacements = {
    '"Stable Update Available"': '"Beta Update Available"',
    "Harley's Clan Forum Stable build": "Harley's Clan Forum development build",
    '"Checking Stable updates…"': '"Checking Development / Beta updates…"',
    '"Stable feed"': '"Development / Beta feed"',
    '"newest Stable build': '"newest Development / Beta build',
    '"A newer Stable build': '"A newer Development / Beta build',
    'Channel: Stable': 'Channel: Development / Beta',
    'Stable v': 'Beta/Dev v',
    '1.0 • Stable': '1.0 • Development / Beta',
}
for old, new in replacements.items():
    text = text.replace(old, new)
text = replace_java_string_number(text, "10000072")
text = text.replace(
    'AppLogger.info(this, "main_create", "1.0 | UA=HarleysClanForumApp/1.0");',
    'AppLogger.info(this, "main_create", BuildInfo.VERSION_BUILD_LINE + " | UA=" + BuildInfo.USER_AGENT_MARKER);',
)
text = text.replace(
    'setRequestProperty("User-Agent", "HarleysClanForumApp/1.0")',
    'setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER)',
)
write(main, text)

# ---------------------------------------------------------------------------
# Settings: surface actual last background sync age and battery guidance.
# ---------------------------------------------------------------------------
settings = src / "SettingsActivity.java"
text = read(settings)
old_refresh = '''            boolean ready = NotificationHelper.canPost(this) && NotificationHelper.channelImportance(this) != 0;
            boolean background = prefs.getBoolean("background_notification_sync", true);
            String delivery = background ? "Background delivery ON" : "Background delivery paused";
            notificationStatus.setText((ready ? "HCF Alerts ready" : NotificationHelper.status(this)) + " • " + delivery);
            notificationStatus.setTextColor(getColor(ready ? R.color.hcf_accent_text : R.color.hcf_warning));'''
new_refresh = '''            boolean ready = NotificationHelper.canPost(this) && NotificationHelper.channelImportance(this) != 0;
            boolean background = prefs.getBoolean("background_notification_sync", true);
            String delivery = background ? "Background delivery ON" : "Background delivery paused";
            long lastSyncAt = prefs.getLong(AppPrefs.NOTIFICATION_LAST_SYNC_AT, 0L);
            String lastSync;
            if (lastSyncAt <= 0L) {
                lastSync = "No background sync yet";
            } else {
                long ageSeconds = Math.max(0L, (System.currentTimeMillis() - lastSyncAt) / 1000L);
                if (ageSeconds < 60L) lastSync = "Last sync <1 min ago";
                else if (ageSeconds < 3600L) lastSync = "Last sync " + (ageSeconds / 60L) + " min ago";
                else lastSync = "Last sync " + (ageSeconds / 3600L) + " hr ago";
            }
            notificationStatus.setText((ready ? "HCF Alerts ready" : NotificationHelper.status(this)) + " • " + delivery + " • " + lastSync);
            notificationStatus.setTextColor(getColor(ready ? R.color.hcf_accent_text : R.color.hcf_warning));'''
if old_refresh in text:
    text = text.replace(old_refresh, new_refresh, 1)
needle = 'card.addView(text("Recommended: keep this ON so new forum alerts can be discovered when HCF is in the background.", 10, getColor(R.color.hcf_muted)));'
if needle in text and "Battery > Unrestricted" not in text:
    text = text.replace(
        needle,
        needle + '\n        card.addView(text("If Android delays background alerts, set HCF Beta battery usage to Unrestricted in Android Settings > Apps > HCF Beta > Battery.", 10, getColor(R.color.hcf_muted)));',
        1,
    )
write(settings, text)

# ---------------------------------------------------------------------------
# AppSecurity: support signing-certificate rotation safely. Multi-signer APKs
# require an exact current signer set; single-signer lineage may match any
# certificate in both histories.
# ---------------------------------------------------------------------------
security = src / "AppSecurity.java"
text = read(security)
if "java.util.LinkedHashSet" not in text:
    text = text.replace("import java.util.Locale;", "import java.util.LinkedHashSet;\nimport java.util.Locale;\nimport java.util.Set;")
old_verify = '''                        String signingDigest = signingDigest(packageInfo);
                        String signingDigest2 = signingDigest(packageArchiveInfo);
                        if (!signingDigest.isEmpty() && !signingDigest2.isEmpty() && signingDigest.equals(signingDigest2)) {
                            return new ApkVerification(true, "Verified package, version and signing certificate.");
                        }
                        return new ApkVerification(false, "Blocked update: signing certificate does not match the installed app.");'''
new_verify = '''                        if (signaturesCompatible(packageInfo, packageArchiveInfo)) {
                            return new ApkVerification(true, "Verified package, version and signing certificate lineage.");
                        }
                        return new ApkVerification(false, "Blocked update: signing certificate does not match the installed app.");'''
if old_verify not in text:
    raise SystemExit("AppSecurity signature verification block not found")
text = text.replace(old_verify, new_verify, 1)
sig_start = text.find("    private static String signingDigest(PackageInfo packageInfo) throws Exception {")
ctor = text.find("    private AppSecurity()", sig_start)
if sig_start < 0 or ctor < 0:
    raise SystemExit("AppSecurity signingDigest method not found")
new_signing = '''    private static boolean signaturesCompatible(PackageInfo installed, PackageInfo candidate) throws Exception {
        Set<String> installedCurrent = currentSigningDigests(installed);
        Set<String> candidateCurrent = currentSigningDigests(candidate);
        if (installedCurrent.isEmpty() || candidateCurrent.isEmpty()) return false;

        if (Build.VERSION.SDK_INT >= 28) {
            boolean installedMulti = installed.signingInfo != null && installed.signingInfo.hasMultipleSigners();
            boolean candidateMulti = candidate.signingInfo != null && candidate.signingInfo.hasMultipleSigners();
            if (installedMulti || candidateMulti) return installedCurrent.equals(candidateCurrent);
        } else {
            return installedCurrent.equals(candidateCurrent);
        }

        Set<String> installedHistory = signingHistoryDigests(installed);
        Set<String> candidateHistory = signingHistoryDigests(candidate);
        for (String digest : installedHistory) {
            if (candidateHistory.contains(digest)) return true;
        }
        return false;
    }

    private static Set<String> currentSigningDigests(PackageInfo info) throws Exception {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (info == null) return out;
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28) {
            if (info.signingInfo == null) return out;
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        addDigests(out, signatures);
        return out;
    }

    private static Set<String> signingHistoryDigests(PackageInfo info) throws Exception {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (info == null) return out;
        if (Build.VERSION.SDK_INT >= 28) {
            if (info.signingInfo == null) return out;
            Signature[] signatures = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
            addDigests(out, signatures);
        } else {
            addDigests(out, info.signatures);
        }
        return out;
    }

    private static void addDigests(Set<String> out, Signature[] signatures) throws Exception {
        if (signatures == null) return;
        for (Signature signature : signatures) {
            if (signature == null) continue;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte value : digest) sb.append(String.format(Locale.US, "%02x", Integer.valueOf(value & 255)));
            out.add(sb.toString());
        }
    }

'''
text = text[:sig_start] + new_signing + text[ctor:]
write(security, text)

# ---------------------------------------------------------------------------
# Live updater: build-driven UAs, state logging, and a small persistent route
# fingerprint baseline so process death does not always throw away the baseline.
# ---------------------------------------------------------------------------
live = src / "LiveForumUpdater.java"
text = read(live)
text = text.replace('"HarleysClanForumApp/1.0 Realtime"', 'BuildInfo.USER_AGENT_MARKER + " Realtime"')
text = text.replace('"HarleysClanForumApp/1.0 RealtimeBootstrap"', 'BuildInfo.USER_AGENT_MARKER + " RealtimeBootstrap"')
text = text.replace('"HarleysClanForumApp/1.0 LiveUpdate"', 'BuildInfo.USER_AGENT_MARKER + " LiveUpdate"')
old_ack = '''    void acknowledge(String str, String str2) {
        if (str == null || str2 == null) {
            return;
        }
        this.baselineKey = str;
        this.baselineFingerprint = str2;
    }'''
new_ack = '''    void acknowledge(String str, String str2) {
        if (str == null || str2 == null) return;
        this.baselineKey = str;
        this.baselineFingerprint = str2;
        saveBaseline(str, str2);
    }'''
if old_ack in text:
    text = text.replace(old_ack, new_ack, 1)
old_baseline = '''            if (!key.equals(this.baselineKey) || (previous = this.baselineFingerprint) == null) {
                this.baselineKey = key;
                this.baselineFingerprint = fingerprint;
            } else if (!fingerprint.equals(previous)) {
                this.listener.onChangeCandidate(key, fingerprint);
            }'''
new_baseline = '''            if (!key.equals(this.baselineKey) || (previous = this.baselineFingerprint) == null) {
                previous = loadBaseline(key);
                this.baselineKey = key;
                this.baselineFingerprint = previous == null ? fingerprint : previous;
                if (previous == null) saveBaseline(key, fingerprint);
            }
            previous = this.baselineFingerprint;
            if (previous != null && !fingerprint.equals(previous)) {
                this.listener.onChangeCandidate(key, fingerprint);
            }'''
if old_baseline in text:
    text = text.replace(old_baseline, new_baseline, 1)
old_state = '''    private void state(String state) {
        if (state.equals(this.lastState)) {
            return;
        }
        this.lastState = state;
        this.listener.onStateChanged(state);
    }'''
new_state = '''    private void state(String state) {
        if (state.equals(this.lastState)) return;
        String previous = this.lastState == null || this.lastState.isEmpty() ? "NONE" : this.lastState;
        this.lastState = state;
        AppLogger.info(this.app, "live_update_state", previous + " -> " + state);
        this.listener.onStateChanged(state);
    }

    private String baselinePrefKey(String key) {
        if (key == null) return "";
        return "live_fp_" + Integer.toHexString(key.hashCode());
    }

    private String loadBaseline(String key) {
        try {
            String value = this.prefs.getString(baselinePrefKey(key), null);
            return value == null || value.isEmpty() ? null : value;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void saveBaseline(String key, String fingerprint) {
        if (key == null || fingerprint == null || fingerprint.isEmpty()) return;
        try {
            this.prefs.edit().putString(baselinePrefKey(key), fingerprint).apply();
        } catch (Throwable ignored) {
        }
    }'''
if old_state in text:
    text = text.replace(old_state, new_state, 1)
write(live, text)

# Build-driven install telemetry.
boot = src / "BootReceiver.java"
if boot.is_file():
    text = read(boot).replace('TelemetryService.sendEvent(context, "update_installed", "1.0");', 'TelemetryService.sendEvent(context, "update_installed", BuildInfo.installedVersionName());')
    write(boot, text)

# ---------------------------------------------------------------------------
# build-release.sh: enforce source identity parity and zero decompiler stubs;
# pin the Beta signer by default and emit the Android v4 .idsig sidecar.
# ---------------------------------------------------------------------------
build_sh = dev / "build-release.sh"
text = read(build_sh)
if 'build_info="$project_dir/src/com/harleytg/forum/BuildInfo.java"' not in text:
    marker = 'manifest="$project_dir/AndroidManifest.xml"\n'
    insertion = marker + 'build_info="$project_dir/src/com/harleytg/forum/BuildInfo.java"\n'
    text = text.replace(marker, insertion, 1)
    text = text.replace(
        '[[ -f "$manifest" ]] || { echo "Missing AndroidManifest.xml" >&2; exit 2; }',
        '[[ -f "$manifest" ]] || { echo "Missing AndroidManifest.xml" >&2; exit 2; }\n[[ -f "$build_info" ]] || { echo "Missing BuildInfo.java" >&2; exit 2; }',
        1,
    )
identity_marker = 'version_name="$(sed -n \'s/.*android:versionName="\\([^\"]*\\)".*/\\1/p\' "$manifest" | head -1)"\n'
if identity_marker in text and "buildinfo_version_code=" not in text:
    guard = identity_marker + '''buildinfo_version_code="$(sed -n 's/.*VERSION_CODE = \\([0-9][0-9]*\\);.*/\\1/p' "$build_info" | head -1)"
buildinfo_apk_name="$(sed -n 's/.*APK_FILE_NAME = "\\([^"]*\\)";.*/\\1/p' "$build_info" | head -1)"
[[ -n "$buildinfo_version_code" && "$version_code" == "$buildinfo_version_code" ]] || { echo "Manifest/BuildInfo versionCode mismatch" >&2; exit 21; }
if grep -R -nE 'Method not decompiled:|throw new UnsupportedOperationException' "$project_dir/src"; then
  echo "Decompiler stubs remain in production source" >&2
  exit 22
fi
'''
    text = text.replace(identity_marker, guard, 1)
text = text.replace('expected_signer="${HCF_EXPECTED_SIGNER:-}"', 'expected_signer="${HCF_EXPECTED_SIGNER:-93D49BF9A877C7CFB1B37F9064BD955CD67BD7DD8DB73A9E3F766B59C4BCCE63}"')
text = text.replace('--v4-signing-enabled false', '--v4-signing-enabled true')
if '[[ "$output_name" == "$buildinfo_apk_name" ]]' not in text:
    marker = 'keystore_path="${HCF_KEYSTORE:?Set HCF_KEYSTORE to the channel signing JKS}"\n'
    text = text.replace(marker, '[[ "$output_name" == "$buildinfo_apk_name" ]] || { echo "BuildInfo APK filename mismatch" >&2; exit 23; }\n\n' + marker, 1)
write(build_sh, text)

# Remove stale decompiler-generated public resource IDs. aapt owns IDs.
public_xml = dev / "res/values/public.xml"
if public_xml.exists():
    public_xml.unlink()

# ---------------------------------------------------------------------------
# App Links deployment payload. The app side is already declared in manifest;
# these files are what must be served from BOTH forum hosts.
# ---------------------------------------------------------------------------
app_links = repo / "app-links"
app_links.mkdir(exist_ok=True)
assetlinks = [
    {
        "relation": ["delegate_permission/common.handle_all_urls"],
        "target": {
            "namespace": "android_app",
            "package_name": "com.harleytg.forum.dev",
            "sha256_cert_fingerprints": [BETA_SIGNER],
        },
    },
    {
        "relation": ["delegate_permission/common.handle_all_urls"],
        "target": {
            "namespace": "android_app",
            "package_name": "com.harleytg.forum",
            "sha256_cert_fingerprints": [STABLE_SIGNER],
        },
    },
]
write(app_links / "assetlinks.json", json.dumps(assetlinks, indent=2) + "\n")
write(
    app_links / "README.md",
    """# HCF Android App Links deployment\n\nServe `assetlinks.json` unchanged as HTTPS JSON with no redirect at both:\n\n- `https://forum.harleytg.com/.well-known/assetlinks.json`\n- `https://harleysclan.freeflarum.com/.well-known/assetlinks.json`\n\nThe file includes both the Beta/Dev package and Stable package signing fingerprints.\nAfter deployment verify on Android with `adb shell pm get-app-links com.harleytg.forum.dev`.\nThe Android manifest already declares both HTTPS hosts with `android:autoVerify=\"true\"`.\n""",
)

# FCM cannot be truthfully enabled without a Firebase Android app config and a
# server-side sender. Keep the build flag false and record the exact prerequisite.
docs = repo / "docs"
docs.mkdir(exist_ok=True)
write(
    docs / "background-push-requirements.md",
    """# Background push requirements\n\nHCF Beta currently uses the foreground notification service plus JobScheduler fallback.\n`BuildInfo.FCM_CONFIGURED` must stay `false` until all of the following exist:\n\n1. A Firebase Android app registered for `com.harleytg.forum.dev`.\n2. The matching Android Firebase configuration bundled into the native build.\n3. A server-side trusted sender that emits a data message when Flarum creates a notification.\n4. A native receiver/service that validates the payload and posts it only through `HCF Alerts`.\n5. Token registration/revocation tied to the signed-in forum account.\n\nDo not set the flag to true merely because the web Firebase config is present; web config alone is not native FCM delivery.\n""",
)

# ---------------------------------------------------------------------------
# Hard validation: the checked-in source must now be directly buildable and the
# specific Grok findings must no longer exist.
# ---------------------------------------------------------------------------
all_java = "\n".join(read(path) for path in (dev / "src").rglob("*.java"))
checks = [
    ("Method not decompiled:" not in all_java, "no Method not decompiled stubs"),
    ("throw new UnsupportedOperationException" not in all_java, "no UnsupportedOperationException stubs"),
    ("VERSION_CODE = 10000090" in read(build_info), "BuildInfo v10000090"),
    ('android:versionCode="10000090"' in read(manifest), "manifest v10000090"),
    ("10000072" not in read(main), "no old MainActivity build 10000072"),
    ("10000071L" not in read(updater), "no updater 10000071 fallback"),
    ("adaptive v10000047" not in read(service), "no service v10000047 log"),
    ("recordForumNotificationCount(Context context" in read(helper), "notification delta implementation restored"),
    ("performAdaptiveSync()" in read(service), "background sync implementation restored"),
    ("protected void onActivityResult(int requestCode" in read(main), "activity result implementation restored"),
    ("Background delivery" in read(settings), "background sync belongs to HCF Alerts UI"),
    ("private-user" in read(live) and "pusherKey" in read(live), "Pusher realtime preserved"),
    ("IDN.USE_STD3_ASCII_RULES" in read(src / "RemoteDomainConfig.java"), "remote host validation preserved"),
    ("signaturesCompatible" in read(security), "signer rotation policy implemented"),
    (not public_xml.exists(), "stale public.xml removed"),
]
for passed, label in checks:
    if not passed:
        raise SystemExit("validation failed: " + label)

print("Grok guide source repair prepared successfully")
print(f"versionCode={VERSION_CODE}")
print("source_mode=direct-dev-build")
