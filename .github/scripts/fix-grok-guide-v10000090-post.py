from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: fix-grok-guide-v10000090-post.py <dev-source-code>")

root = Path(sys.argv[1]).resolve()
src = root / "src/com/harleytg/forum"

main = src / "MainActivity.java"
text = main.read_text(encoding="utf-8")
text = text.replace(
    'this.prefs.getInt("notification_permission_prompt_version", 0) < 10000072',
    'this.prefs.getInt("notification_permission_prompt_version", 0) < BuildInfo.VERSION_CODE',
)
text = text.replace(
    'putInt("notification_permission_prompt_version", 10000072)',
    'putInt("notification_permission_prompt_version", BuildInfo.VERSION_CODE)',
)
text = text.replace("is newer than the Stable feed", "is newer than the Development / Beta feed")
text = text.replace("You're on the newest Stable build.", "You're on the newest Development / Beta build.")
text = text.replace("Checking Stable updates…", "Checking Development / Beta updates…")
text = text.replace("A newer Stable build", "A newer Development / Beta build")
main.write_text(text, encoding="utf-8")

# JADX left checked JSON construction in a readable helper without a catch.
client = src / "ForumNotificationClient.java"
text = client.read_text(encoding="utf-8")
old = '''        if ((trim.startsWith("{") && trim.endsWith("}")) || (trim.startsWith("[") && trim.endsWith("]"))) {
            if (trim.startsWith("{")) {
                return firstDeepObject(new JSONObject(trim), i + 1, strArr);
            }
            JSONArray jSONArray2 = new JSONArray(trim);
            while (i2 < jSONArray2.length()) {
                String readableValue2 = readableValue(jSONArray2.opt(i2), i + 1, strArr);
                if (!readableValue2.isEmpty()) {
                    return readableValue2;
                }
                i2++;
            }
            return "";
        }
        return clean(trim, 500);'''
new = '''        if ((trim.startsWith("{") && trim.endsWith("}")) || (trim.startsWith("[") && trim.endsWith("]"))) {
            try {
                if (trim.startsWith("{")) {
                    return firstDeepObject(new JSONObject(trim), i + 1, strArr);
                }
                JSONArray jSONArray2 = new JSONArray(trim);
                while (i2 < jSONArray2.length()) {
                    String readableValue2 = readableValue(jSONArray2.opt(i2), i + 1, strArr);
                    if (!readableValue2.isEmpty()) {
                        return readableValue2;
                    }
                    i2++;
                }
                return "";
            } catch (Throwable ignored) {
                return clean(trim, 500);
            }
        }
        return clean(trim, 500);'''
if old not in text:
    raise SystemExit("ForumNotificationClient readableValue JSON block not found")
text = text.replace(old, new, 1)
client.write_text(text, encoding="utf-8")

# Remaining version identity drift.
logs = src / "LogsActivity.java"
text = logs.read_text(encoding="utf-8")
text = text.replace(
    'StringBuilder sb = new StringBuilder("Harley\'s Clan Forum • Sanitized Diagnostic Report\\n\\nApp: 1.0 (10000072)\\nPackage: ");',
    'StringBuilder sb = new StringBuilder("Harley\'s Clan Forum • Sanitized Diagnostic Report\\n\\nApp: " + BuildInfo.installedVersionName() + "\\nPackage: ");',
)
logs.write_text(text, encoding="utf-8")

support = src / "SupportContactActivity.java"
text = support.read_text(encoding="utf-8")
old_support = 'addLockedRow(bodyContainer, "App", "Harley\'s Clan Forum v1.0 • build 10000072");'
new_support = 'addLockedRow(bodyContainer, "App", "Harley\'s Clan Forum v" + BuildInfo.VERSION + " • build " + BuildInfo.VERSION_CODE);'
if old_support not in text:
    raise SystemExit("SupportContactActivity exact stale App build row not found")
text = text.replace(old_support, new_support, 1)
support.write_text(text, encoding="utf-8")

notes = src / "ReleaseNotes.java"
text = notes.read_text(encoding="utf-8")
text = text.replace(
    'static final String NOTES = "Harley\'s Clan Forum (app) v1.0 • Beta/Dev v10000072\\n• Development/Beta versionCode 10000072.',
    'static final String NOTES = "Harley\'s Clan Forum (app) v" + BuildInfo.VERSION + " • Beta/Dev v" + BuildInfo.VERSION_CODE + "\\n• Development/Beta versionCode " + BuildInfo.VERSION_CODE + ".',
)
text = text.replace(
    'static final String SUMMARY = "Beta/Dev v10000072 • Four-button theme selector";',
    'static final String SUMMARY = "Beta/Dev v" + BuildInfo.VERSION_CODE + " • Four-button theme selector";',
)
text = text.replace(
    'return "1.0-10000072";',
    'return BuildInfo.VERSION + "-" + BuildInfo.VERSION_CODE;',
)
notes.write_text(text, encoding="utf-8")

updater = src / "AppUpdateDownloader.java"
text = updater.read_text(encoding="utf-8")
text = text.replace("return 10000072L;", "return BuildInfo.VERSION_CODE;")
text = text.replace("return 10000071L;", "return BuildInfo.VERSION_CODE;")
updater.write_text(text, encoding="utf-8")

# Final direct-source conditions.
problems = []
stale_markers = ("10000072", "10000071", "v10000047")
for path in src.rglob("*.java"):
    value = path.read_text(encoding="utf-8")
    for number, line in enumerate(value.splitlines(), 1):
        if "Method not decompiled:" in line:
            problems.append(f"{path}:{number}: Method not decompiled")
        if "throw new UnsupportedOperationException" in line:
            problems.append(f"{path}:{number}: UnsupportedOperationException")
    if path.name != "BuildInfo.java":
        for stale in stale_markers:
            if stale in value:
                problems.append(f"{path}: stale build marker {stale}")

if problems:
    print("\n".join(problems))
    raise SystemExit("direct-source audit failed")

main_text = main.read_text(encoding="utf-8")
if "notification_permission_prompt_version\", BuildInfo.VERSION_CODE" not in main_text:
    raise SystemExit("notification permission prompt is not BuildInfo-driven")
if "Development / Beta feed" not in main_text or "newest Development / Beta build" not in main_text:
    raise SystemExit("MainActivity update channel wording is not Beta-safe")
if "catch (Throwable ignored)" not in client.read_text(encoding="utf-8"):
    raise SystemExit("ForumNotificationClient JSON parsing guard missing")

print("post-repair compile + identity cleanup passed")
