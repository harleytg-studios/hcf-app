from pathlib import Path
import shutil, re, os, sys

if len(sys.argv) != 4:
    raise SystemExit('usage: promote.py <dev-source-code> <stable-source-code> <stable-backup-source-code>')
DEVROOT=Path(sys.argv[1]); OUT=Path(sys.argv[2]); STROOT=Path(sys.argv[3])
if OUT.exists(): shutil.rmtree(OUT)
shutil.copytree(DEVROOT, OUT)

def span(text, pattern):
    m=re.search(pattern,text,re.M)
    if not m: raise RuntimeError('method not found: '+pattern)
    start=m.start(); brace=text.find('{',m.end()-1)
    depth=0; i=brace; instr=False; quote=''; esc=False; line=False; block=False
    while i < len(text):
        c=text[i]; n=text[i+1] if i+1<len(text) else ''
        if line:
            if c=='\n': line=False
        elif block:
            if c=='*' and n=='/': block=False; i+=1
        elif instr:
            if esc: esc=False
            elif c=='\\': esc=True
            elif c==quote: instr=False
        else:
            if c=='/' and n=='/': line=True; i+=1
            elif c=='/' and n=='*': block=True; i+=1
            elif c in '\"\'': instr=True; quote=c
            elif c=='{': depth+=1
            elif c=='}':
                depth-=1
                if depth==0: return start,i+1
        i+=1
    raise RuntimeError('unclosed method: '+pattern)

def extract(path, pattern):
    t=path.read_text(encoding='utf-8'); a,b=span(t,pattern); return t[a:b]
def replace_method(path, pattern, replacement):
    t=path.read_text(encoding='utf-8'); a,b=span(t,pattern); path.write_text(t[:a]+replacement+t[b:],encoding='utf-8')

# Promote every Dev source/resource to Stable package identity.
for p in OUT.rglob('*'):
    if p.is_file() and p.suffix.lower() in {'.java','.xml','.js','.md','.txt','.json','.sh'}:
        try: s=p.read_text(encoding='utf-8')
        except Exception: continue
        p.write_text(s.replace('com.harleytg.forum.dev','com.harleytg.forum'),encoding='utf-8')

# Stable identity constants, keeping the full Dev feature surface.
(OUT/'src/com/harleytg/forum/BuildInfo.java').write_text(r'''package com.harleytg.forum;

/** Stable identity for the full v10000072 Dev-to-Stable promotion. */
final class BuildInfo {
    static final String APK_FILE_NAME = "HCF-Stable-v10000072.apk";
    static final String BRAND = "Harley's Studio's";
    static final String CHANNEL = "Stable";
    static final String DEFAULT_UPDATE_CHANNEL = "stable";
    static final String DEVELOPMENT_BUILD_LABEL = "Harley's Clan Forum v1.0 [Stable]";
    static final boolean ENABLE_DEV_TEST_MENU = false;
    static final boolean FIREBASE_WEB_CONFIG_BUNDLED = true;
    static final boolean FCM_CONFIGURED = false;
    static final int INTERNAL_BUILD = 100;
    static final String META_LINE = "1.0 • Stable";
    static final String SESSION_CLIENT = "Harley's Clan Forum App";
    static final String UPDATE_DEV_BRANCH = "dev";
    static final String UPDATE_REPOSITORY = "markhitchk/hcf-app";
    static final String UPDATE_STABLE_BRANCH = "stable";
    static final String USER_AGENT_MARKER = "HarleysClanForumApp/1.0";
    static final String VERSION = "1.0";
    static final String VERSION_BUILD_LINE = "1.0 • Stable • Build 10000072";
    static final int VERSION_CODE = 10000072;
    static final String VERSION_CODE_SCHEME = "major-release-v1";
    static final String VERSION_TAG = "v1.0";
    static final boolean ALLOW_UPDATE_CHANNEL_SWITCH = false;
    static String userAgent(String baseUserAgent) {
        String base = baseUserAgent == null ? "" : baseUserAgent.trim();
        if (base.contains(USER_AGENT_MARKER)) return base;
        return base.isEmpty() ? USER_AGENT_MARKER : base + " " + USER_AGENT_MARKER + " NativeApp";
    }
    private BuildInfo() {}
}
''',encoding='utf-8')

# Remove Dev/Beta user-facing identity while retaining the underlying features.
repls=[
("Harley's Clan Forum v1.0 [Development Build / Beta]","Harley's Clan Forum v1.0 [Stable]"),
("Harley&apos;s Clan Forum v1.0 [Development Build / Beta]","Harley&apos;s Clan Forum v1.0 [Stable]"),
("Harley's Clan Forum [Beta]","Harley's Clan Forum"),("Harley&apos;s Clan Forum [Beta]","Harley&apos;s Clan Forum"),
("Beta/Dev v10000072","Stable v10000072"),("Beta/Dev","Stable"),("Dev/Beta-only","Stable"),
("Development/Beta-only","Stable"),("Development/Beta","Stable"),("Development / Beta","Stable"),
("Development and Beta","Stable"),("Beta Update Available","Stable Update Available"),
("Beta update available","Stable update available"),("Beta build","Stable build"),("Beta APK","Stable APK"),
("development build","Stable build"),("Development environment active","Stable environment"),
("channel Dev • update feed dev","channel Stable • update feed stable"),
("Channel: Dev • Update feed: dev","Channel: Stable • Update feed: stable"),
("Channel: Dev / dev","Channel: Stable / stable")]
for p in OUT.rglob('*'):
    if p.is_file() and p.suffix.lower() in {'.java','.xml','.md','.txt'}:
        try: s=p.read_text(encoding='utf-8')
        except Exception: continue
        for a,b in repls: s=s.replace(a,b)
        p.write_text(s,encoding='utf-8')

# Stable versionCode is 10000072; Dev's recovered internal 10000071 was an APK quirk.
for name in ['MainActivity.java','UpdateChecker.java','AppUpdateDownloader.java','TelemetryService.java']:
    p=OUT/'src/com/harleytg/forum'/name
    s=p.read_text(encoding='utf-8').replace('10000071L','10000072L').replace('10000071','10000072')
    p.write_text(s,encoding='utf-8')

# Stable never consumes the preview update feed.
p=OUT/'src/com/harleytg/forum/SettingsActivity.java'; s=p.read_text(encoding='utf-8')
s=s.replace('private String effectiveUpdateChannel() {\n        return "dev";\n    }','private String effectiveUpdateChannel() {\n        return "stable";\n    }')
s=s.replace('UpdateChecker.check(this, "dev", new UpdateChecker.Callback()','UpdateChecker.check(this, "stable", new UpdateChecker.Callback()')
p.write_text(s,encoding='utf-8')
p=OUT/'src/com/harleytg/forum/MainActivity.java'; s=p.read_text(encoding='utf-8')
s=s.replace('UpdateChecker.check(this, "dev", new UpdateChecker.Callback()','UpdateChecker.check(this, "stable", new UpdateChecker.Callback()')
p.write_text(s,encoding='utf-8')

# Repair only the individual JADX-failed method bodies using maintained Stable implementations.
repairs=[
('AppUpdateDownloader.java',r'^\s*static com\.harleytg\.forum\.AppUpdateDownloader\.ProgressSnapshot progress\(android\.content\.Context r18, long r19\)','AppUpdateDownloader.java',r'^\s*static ProgressSnapshot progress\(Context context, long id\)'),
('AppUpdateDownloader.java',r'^\s*static int status\(android\.content\.Context r4, long r5\)','AppUpdateDownloader.java',r'^\s*static int status\(Context context, long id\)'),
('AppUpdateDownloader.java',r'^\s*static boolean cleanupAfterSuccessfulUpdate\(android\.content\.Context r10\)','AppUpdateDownloader.java',r'^\s*static boolean cleanupAfterSuccessfulUpdate\(Context context\)'),
('AppUpdateDownloader.java',r'^\s*static boolean cleanupStaleUpdaterApks\(android\.content\.Context r18\)','AppUpdateDownloader.java',r'^\s*static boolean cleanupStaleUpdaterApks\(Context context\)'),
('MainActivity.java',r'^\s*protected void onActivityResult\(int r4, int r5, android\.content\.Intent r6\)','MainActivity.java',r'^\s*protected void onActivityResult\(int requestCode, int resultCode, Intent data\)'),
('NotificationHelper.java',r'^\s*static synchronized int recordForumNotificationCount\(android\.content\.Context r10, int r11, java\.lang\.String r12, java\.lang\.String r13\)','NotificationHelper.java',r'^\s*static synchronized int recordForumNotificationCount\(Context context, int newCount, String host, String source\)')]
for tf,tp,sf,sp in repairs:
    replace_method(OUT/'src/com/harleytg/forum'/tf,tp,extract(STROOT/'src/com/harleytg/forum'/sf,sp))
logs=extract(STROOT/'src/com/harleytg/forum/LogsActivity.java',r'^\s*private void renderLogs\(\)').replace('private void renderLogs()','public void renderLogs()',1)
replace_method(OUT/'src/com/harleytg/forum/LogsActivity.java',r'^\s*public void renderLogs\(\)',logs)

# Imports required by the repaired bodies.
p=OUT/'src/com/harleytg/forum/AppUpdateDownloader.java'; s=p.read_text(encoding='utf-8')
if 'import android.database.Cursor;' not in s: s=s.replace('import android.content.pm.ResolveInfo;\n','import android.content.pm.ResolveInfo;\nimport android.content.pm.PackageManager;\nimport android.database.Cursor;\n')
p.write_text(s,encoding='utf-8')
p=OUT/'src/com/harleytg/forum/LogsActivity.java'; s=p.read_text(encoding='utf-8')
if 'import java.util.Collections;' not in s: s=s.replace('import java.util.ArrayList;\n','import java.util.ArrayList;\nimport java.util.Collections;\n')
p.write_text(s,encoding='utf-8')

# Dev UpdateChecker retained, but its failed async body is restored for Stable feed semantics.
replace_method(OUT/'src/com/harleytg/forum/UpdateChecker.java',r'^\s*static /\* synthetic \*/ void lambda\$check\$2\(android\.content\.Context r7, final com\.harleytg\.forum\.UpdateChecker\.Callback r8\)',r'''
    static /* synthetic */ void lambda$check$2(Context context, final Callback callback) {
        try {
            Release release = fetchStable();
            long remoteCode = resolveApkVersionCode(context, release);
            release.versionCode = remoteCode;
            final Release resultRelease = release;
            final boolean updateAvailable = remoteCode > 0L ? remoteCode > BuildInfo.VERSION_CODE : compareVersions(release.tag, BuildInfo.VERSION) > 0;
            post(new Runnable() { @Override public void run() { callback.onResult(resultRelease, updateAvailable); } });
        } catch (Throwable t) {
            String message = t.getMessage();
            if (message == null || message.trim().isEmpty()) message = t.getClass().getSimpleName();
            final String out = message;
            post(new Runnable() { @Override public void run() { callback.onError(out); } });
        }
    }''')
p=OUT/'src/com/harleytg/forum/UpdateChecker.java'; s=p.read_text(encoding='utf-8')
s=s.replace('if (release.versionCode == 10000072) {','if (release.versionCode == BuildInfo.VERSION_CODE) {')
s=s.replace('return release.versionCode < 10000072 ? -1 : 1;','return release.versionCode < BuildInfo.VERSION_CODE ? -1 : 1;')
s=s.replace('return compareVersions(release.tag, "1.0");','return compareVersions(release.tag, BuildInfo.VERSION);')
p.write_text(s,encoding='utf-8')

# Reconstruct Dev adaptive notification worker from the exact v10000072 DEX control flow.
replace_method(OUT/'src/com/harleytg/forum/InstantNotificationService.java',r'^\s*/\* synthetic \*/ void m18x7f46ef73\(\)',r'''
    /* synthetic */ void m18x7f46ef73() {
        long nextDelay = NO_SESSION_POLL_MS;
        try {
            SharedPreferences prefs = getSharedPreferences("hcf_app", 0);
            if (!prefs.getBoolean("background_notification_sync", true)) { this.running = false; stopSelf(); return; }
            String userId = prefs.getString("session_user_id", "");
            if (userId == null || userId.trim().isEmpty() || !RuntimeState.networkAvailable(this)) {
                this.failures = 0; nextDelay = NO_SESSION_POLL_MS;
            } else {
                String host = prefs.getString("active_host", "forum.harleytg.com");
                if (!ForumUrlRouter.isForumHost(host)) host = "forum.harleytg.com";
                ForumNotificationSync.perform(this, host, userId.trim(), "adaptive");
                this.failures = 0; nextDelay = PerformanceProfile.notificationPollInterval(this, prefs);
            }
        } catch (Throwable t) {
            this.failures = Math.min(this.failures + 1, 8);
            int shift = Math.min(Math.max(this.failures - 1, 0), 4);
            long retry = Math.min(FAILURE_MAX_MS, FAILURE_MIN_MS * (1L << shift));
            try { retry = Math.max(retry, PerformanceProfile.notificationPollInterval(this, getSharedPreferences("hcf_app", 0))); } catch (Throwable ignored) {}
            nextDelay = retry;
            if (this.failures == 1 || this.failures == 2 || this.failures == 4 || this.failures == 8)
                AppLogger.warn(this, "instant_notification_poll", t.getClass().getSimpleName() + " | failures=" + this.failures + " | retry=" + retry + "ms");
        } finally {
            this.inFlight.set(false);
            if (this.running) {
                if (this.immediateRequested) { this.immediateRequested = false; scheduleNext(0L); }
                else scheduleNext(nextDelay);
            }
        }
    }''')

# Stable release notes identity, preserving Dev v10000072 feature notes.
rp=OUT/'src/com/harleytg/forum/ReleaseNotes.java'; s=rp.read_text(encoding='utf-8')
s=s.replace('Stable remains separate; this feature set is scoped to com.harleytg.forum.','This Stable release contains the full v10000072 feature set promoted from Dev.')
s=s.replace('Stable versionCode 10000072.','Stable versionCode 10000072.')
rp.write_text(s,encoding='utf-8')

# Manifest/strings Stable identity.
p=OUT/'AndroidManifest.xml'; s=p.read_text(encoding='utf-8').replace('android:value="dev"','android:value="stable"'); p.write_text(s,encoding='utf-8')
p=OUT/'res/values/strings.xml'; s=p.read_text(encoding='utf-8').replace("Harley\\'s Clan Forum [Beta]","Harley\\'s Clan Forum"); p.write_text(s,encoding='utf-8')

# Overlay Stable logo/icon set without rolling back Dev UI/resources.
stable_res=STROOT/'res'; logo=stable_res/'drawable-nodpi/htg_app_logo.png'
# Dev exact uses drawable-nodpi-v4. Keep the Stable art in that existing
# resource slot only; aapt treats nodpi and nodpi-v4 as duplicate configs.
(OUT/'res/drawable-nodpi-v4').mkdir(parents=True,exist_ok=True)
shutil.copy2(logo,OUT/'res/drawable-nodpi-v4/htg_app_logo.png')
dup=OUT/'res/drawable-nodpi/htg_app_logo.png'
if dup.exists(): dup.unlink()
for pat in ['fa_*.xml','ic_*.xml','htg_icon_foreground.xml']:
    for src in (stable_res/'drawable').glob(pat): shutil.copy2(src,OUT/'res/drawable'/src.name)
for d in stable_res.glob('mipmap-*'):
    if d.is_dir():
        dest=OUT/'res'/d.name
        if dest.exists(): shutil.rmtree(dest)
        shutil.copytree(d,dest)
for name in ['.gitignore','APP-LINKS-SETUP.md','BUILD-NOTES.md','README.md','RELEASE-SIGNING.md','STABLE-FEATURE-PARITY-v10000032.md','STABLE-FEATURE-PARITY-v10000072.md']:
    src=STROOT/name
    if src.exists(): shutil.copy2(src,OUT/name)
for dname in ['app-links','branding','signing']:
    src=STROOT/dname
    if src.exists():
        dest=OUT/dname
        if dest.exists(): shutil.rmtree(dest)
        shutil.copytree(src,dest)

# Direct build script for the promoted source; Stable V2 signer is required and remains external.
(OUT/'build-release.sh').write_text(r'''#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")" && pwd)"; sdk_root="${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
build_tools="$sdk_root/build-tools/${BUILD_TOOLS_VERSION:-35.0.0}"; android_jar="$sdk_root/platforms/android-${ANDROID_PLATFORM_VERSION:-35}/android.jar"
keystore_path="${HCF_KEYSTORE:?Set HCF_KEYSTORE to the private Stable V2 JKS}"; keystore_alias="${HCF_KEY_ALIAS:-hcf-stable-v2}"; password_file="${HCF_KEY_PASSWORD_FILE:?Set HCF_KEY_PASSWORD_FILE}"
export HCF_APKSIGNER_PASSWORD="$(sed -n '1p' "$password_file")"; output_dir="${HCF_OUTPUT_DIR:-$project_dir/out}"; work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
expected_signer="77E0E96C1177842AAA311A8FC0EBEA29B92D3CD290BB815BDB86AD0E0A85844F"
keyfp="$(keytool -list -v -keystore "$keystore_path" -storepass "$HCF_APKSIGNER_PASSWORD" -alias "$keystore_alias" 2>/dev/null | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -1 | tr '[:lower:]' '[:upper:]' | tr -d ':[:space:]')"
[[ "$keyfp" == "$expected_signer" ]] || { echo 'Wrong Stable signer' >&2; exit 20; }
mkdir -p "$work/gen" "$work/classes" "$work/dex" "$output_dir"
"$build_tools/aapt" package -f -m -J "$work/gen" -M "$project_dir/AndroidManifest.xml" -S "$project_dir/res" -A "$project_dir/assets" -I "$android_jar" -F "$work/resources.apk"
mapfile -t java_files < <(find "$work/gen" "$project_dir/src" -name '*.java' -print); javac --release 8 -classpath "$android_jar" -d "$work/classes" "${java_files[@]}"
mapfile -t class_files < <(find "$work/classes" -name '*.class' -print); "$build_tools/d8" --lib "$android_jar" --min-api 26 --release --output "$work/dex" "${class_files[@]}"
cp "$work/resources.apk" "$work/unsigned.apk"; (cd "$work/dex" && "$build_tools/aapt" add "$work/unsigned.apk" classes.dex)
"$build_tools/zipalign" -f -p 4 "$work/unsigned.apk" "$work/aligned.apk"
"$build_tools/apksigner" sign --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --ks "$keystore_path" --ks-key-alias "$keystore_alias" --ks-pass env:HCF_APKSIGNER_PASSWORD --key-pass env:HCF_APKSIGNER_PASSWORD --out "$output_dir/HCF-Stable-v10000072.apk" "$work/aligned.apk"
"$build_tools/apksigner" verify --verbose --print-certs "$output_dir/HCF-Stable-v10000072.apk"
''',encoding='utf-8')
os.chmod(OUT/'build-release.sh',0o755)
if (OUT/'patches').exists(): shutil.rmtree(OUT/'patches')

# Hard guards before compile/commit.
alltext='\n'.join(p.read_text(encoding='utf-8',errors='ignore') for p in OUT.rglob('*') if p.is_file() and p.suffix.lower() in {'.java','.xml'})
if 'com.harleytg.forum.dev' in alltext: raise RuntimeError('Dev package identity remains')
if 'Method not decompiled' in alltext: raise RuntimeError('JADX failure remains')
if '[Beta]' in alltext or 'Development / Beta' in alltext: raise RuntimeError('Beta UI branding remains')
manifest=(OUT/'AndroidManifest.xml').read_text(encoding='utf-8')
for required in ['package="com.harleytg.forum"','android:versionCode="10000072"','android:versionName="1.0 (10000072)"','android:value="stable"']:
    if required not in manifest: raise RuntimeError('manifest guard failed: '+required)
print('Full Dev v10000072 -> Stable source promotion prepared:',sum(1 for p in OUT.rglob('*') if p.is_file()),'files')
