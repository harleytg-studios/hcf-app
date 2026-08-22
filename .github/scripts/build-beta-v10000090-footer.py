from pathlib import Path
import os
import re
import shutil
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: build-beta-v10000090-footer.py <stable-source-code> <dev-source-code>")

root = Path(sys.argv[1])
meta = Path(sys.argv[2])

# Convert the known-good Stable runtime package to the Beta package.
for path in list((root / "src").rglob("*.java")) + [root / "AndroidManifest.xml"]:
    text = path.read_text(encoding="utf-8")
    path.write_text(text.replace("com.harleytg.forum", "com.harleytg.forum.dev"), encoding="utf-8")

# Build identity.
manifest = root / "AndroidManifest.xml"
text = manifest.read_text(encoding="utf-8")
text = text.replace('android:value="stable"', 'android:value="dev"')
text = re.sub(r'android:versionCode="\d+"', 'android:versionCode="10000090"', text, count=1)
text = re.sub(r'android:versionName="[^"]+"', 'android:versionName="1.0 (10000090)"', text, count=1)
text = re.sub(
    r'(<meta-data\s+android:name="com\.harleytg\.APP_VERSION"\s+android:value=")[^"]+("/>)',
    r'\g<1>1.0 (10000090)\2',
    text,
)
text = re.sub(
    r'(<meta-data\s+android:name="com\.harleytg\.APP_VERSION_CODE"\s+android:value=")[^"]+("/>)',
    r'\g<1>10000090\2',
    text,
)
manifest.write_text(text, encoding="utf-8")

# Fix the regression: the Stable runtime hard-codes its drawer/footer identity.
# Use BuildInfo instead so the Beta overlay controls the text.
main = root / "src/com/harleytg/forum/MainActivity.java"
text = main.read_text(encoding="utf-8")
old_footer = "textView.setText(\"Harley's Clan Forum v1.0 [Stable]\");"
new_footer = "textView.setText(BuildInfo.DEVELOPMENT_BUILD_LABEL);"
if old_footer not in text:
    raise SystemExit("Stable drawer footer pattern not found")
main.write_text(text.replace(old_footer, new_footer, 1), encoding="utf-8")

# Preserve the Silent Alerts behavior/status fix from v10000089.
helper = root / "src/com/harleytg/forum/NotificationHelper.java"
text = helper.read_text(encoding="utf-8")
old_status = '''        if (SILENT_CHANNEL_ID.equals(str)) {
            return "On • Silent";
        }'''
new_status = '''        if (SILENT_CHANNEL_ID.equals(str)) {
            return silencePassiveEnabled(context) ? "Disabled by app setting" : "Enabled • Silent";
        }'''
if old_status not in text:
    raise SystemExit("Stable runtime channelStatus pattern not found")
text = text.replace(old_status, new_status, 1)
old_can_post = '''    static boolean canPostOnChannel(Context context, String str) {
        return isEnabledByUser(context) && hasRuntimePermission(context) && areAppNotificationsEnabled(context) && channelImportance(context, str) != 0;
    }'''
new_can_post = '''    static boolean canPostOnChannel(Context context, String str) {
        if (SILENT_CHANNEL_ID.equals(str) && silencePassiveEnabled(context)) {
            return false;
        }
        return isEnabledByUser(context) && hasRuntimePermission(context) && areAppNotificationsEnabled(context) && channelImportance(context, str) != 0;
    }'''
if old_can_post not in text:
    raise SystemExit("Stable runtime canPostOnChannel pattern not found")
helper.write_text(text.replace(old_can_post, new_can_post, 1), encoding="utf-8")

settings = root / "src/com/harleytg/forum/SettingsActivity.java"
text = settings.read_text(encoding="utf-8")
text = text.replace('"Silence HCF Silent Alerts"', '"Disable HCF Silent Alerts"')
text = text.replace(
    '"Silent/background channel • hidden when Silence HCF Silent Alerts is enabled"',
    '"Silent/background channel • status below shows whether the app has disabled it"',
)
text = text.replace(
    'Toast.makeText(this, "Updated • Passive notification silence", Toast.LENGTH_LONG).show();',
    'Toast.makeText(this, checked ? "HCF Silent Alerts disabled." : "HCF Silent Alerts enabled • silent.", Toast.LENGTH_LONG).show();',
)
settings.write_text(text, encoding="utf-8")

# Overlay Beta resources and maintained Beta-specific runtime files.
shutil.copytree(meta / "res", root / "res", dirs_exist_ok=True)
public_xml = root / "res/values/public.xml"
if public_xml.exists():
    public_xml.unlink()

for name in [
    "BuildInfo.java",
    "LiveForumUpdater.java",
    "PerformanceProfile.java",
    "NotificationSyncScheduler.java",
    "UpdateChecker.java",
    "ForumConfig.java",
    "RemoteDomainConfig.java",
    "HcfApplication.java",
]:
    shutil.copy2(meta / "src/com/harleytg/forum" / name, root / "src/com/harleytg/forum" / name)

# Hard validation before Android compilation.
build_info = (root / "src/com/harleytg/forum/BuildInfo.java").read_text(encoding="utf-8")
main_text = main.read_text(encoding="utf-8")
helper_text = helper.read_text(encoding="utf-8")
settings_text = settings.read_text(encoding="utf-8")
scheduler_text = (root / "src/com/harleytg/forum/NotificationSyncScheduler.java").read_text(encoding="utf-8")
live_text = (root / "src/com/harleytg/forum/LiveForumUpdater.java").read_text(encoding="utf-8")

checks = [
    ("VERSION_CODE = 10000090" in build_info, "BuildInfo versionCode"),
    ("HCF-Beta-v10000090.apk" in build_info, "BuildInfo APK filename"),
    ("Harley's Clan Forum v1.0 [Development Build / Beta]" in build_info, "Beta development label"),
    ("textView.setText(BuildInfo.DEVELOPMENT_BUILD_LABEL);" in main_text, "drawer footer uses BuildInfo"),
    ("Harley's Clan Forum v1.0 [Stable]" not in main_text, "no Stable drawer footer"),
    ("Disabled by app setting" in helper_text, "Silent Alerts disabled status"),
    ("Disable HCF Silent Alerts" in settings_text, "Silent Alerts switch wording"),
    ("foreground service stopped" in scheduler_text, "Silent Alerts service behavior"),
    ("private-user" in live_text and "pusherKey" in live_text, "Pusher realtime code"),
]
for passed, label in checks:
    if not passed:
        raise SystemExit("validation failed: " + label)

print("v10000090 Beta runtime prepared successfully")
print("footer=Harley's Clan Forum v1.0 [Development Build / Beta]")
