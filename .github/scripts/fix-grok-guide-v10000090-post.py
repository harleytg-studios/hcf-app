from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: fix-grok-guide-v10000090-post.py <dev-source-code>")

root = Path(sys.argv[1]).resolve()
src = root / "src/com/harleytg/forum"

# ---------------------------------------------------------------------------
# MainActivity: all build-dependent gates and user-facing channel wording come
# from BuildInfo / Dev-Beta identity.
# ---------------------------------------------------------------------------
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
text = text.replace(
    'this.welcomeBanner.setText("✨  What\'s New • v1.0\\nBeta/Dev v" + BuildInfo.VERSION_CODE + " • Four-button theme selector  •  Tap to view");',
    'this.welcomeBanner.setText("✨  What\'s New • v1.0\\nBeta/Dev v" + BuildInfo.VERSION_CODE + " • Theme, notifications & stability update  •  Tap to view");',
)
main.write_text(text, encoding="utf-8")

# ---------------------------------------------------------------------------
# ForumNotificationClient: JADX left checked JSON construction in readableValue
# without handling. Keep the patch idempotent so release verification can be
# rerun safely after source has been committed.
# ---------------------------------------------------------------------------
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
if old in text:
    text = text.replace(old, new, 1)
elif 'return firstDeepObject(new JSONObject(trim), i + 1, strArr);' in text and 'catch (Throwable ignored)' not in text:
    raise SystemExit("ForumNotificationClient readableValue JSON guard missing")
client.write_text(text, encoding="utf-8")

# ---------------------------------------------------------------------------
# HcfIntentChooser: JADX left loadLabel potentially unassigned when
# ResolveInfo.loadLabel throws. Initialize it before the try so the checked-in
# Dev tree compiles with javac rather than depending on decompiler behavior.
# ---------------------------------------------------------------------------
chooser = src / "HcfIntentChooser.java"
text = chooser.read_text(encoding="utf-8")
text = text.replace(
    '    private static String resolveLabel(PackageManager packageManager, ResolveInfo resolveInfo) {\n        CharSequence loadLabel;\n        if (resolveInfo == null) {',
    '    private static String resolveLabel(PackageManager packageManager, ResolveInfo resolveInfo) {\n        CharSequence loadLabel = null;\n        if (resolveInfo == null) {',
)
chooser.write_text(text, encoding="utf-8")

# ---------------------------------------------------------------------------
# IdentityActivity: another JADX definite-assignment artifact. The avatar HTTPS
# loader catches openConnection failures and later disconnects conditionally,
# so initialize the connection reference to null exactly as the compileable
# Stable recovery source does.
# ---------------------------------------------------------------------------
identity = src / "IdentityActivity.java"
text = identity.read_text(encoding="utf-8")
text = text.replace(
    '    /* synthetic */ void m14x27753261(final String str) {\n        HttpsURLConnection httpsURLConnection;\n        HttpsURLConnection httpsURLConnection2 = null;',
    '    /* synthetic */ void m14x27753261(final String str) {\n        HttpsURLConnection httpsURLConnection = null;\n        HttpsURLConnection httpsURLConnection2 = null;',
)
identity.write_text(text, encoding="utf-8")

# ---------------------------------------------------------------------------
# Settings: make the background-delivery state truthful and user-friendly.
# HCF Alerts never becomes app-disabled; the Silent Alerts switch only changes
# whether Android's continuous foreground sync service may run.
# ---------------------------------------------------------------------------
settings = src / "SettingsActivity.java"
text = settings.read_text(encoding="utf-8")
text = text.replace(
    '            boolean background = prefs.getBoolean("background_notification_sync", true);\n            String delivery = background ? "Background delivery ON" : "Background delivery paused";',
    '            boolean background = prefs.getBoolean("background_notification_sync", true);\n            boolean silentStatusDisabled = prefs.getBoolean("silence_background_service_notification", false);\n            String sessionUserId = prefs.getString("session_user_id", "");\n            boolean signedIn = sessionUserId != null && !sessionUserId.trim().isEmpty();\n            String delivery = !background ? "Background delivery paused"\n                    : !signedIn ? "Background delivery waiting for sign-in"\n                    : silentStatusDisabled ? "Background delivery delayed"\n                    : "Background delivery live";',
)
text = text.replace(
    '            Toast.makeText(this, checked ? "HCF Silent Alerts disabled." : "HCF Silent Alerts enabled • silent.", Toast.LENGTH_LONG).show();',
    '            Toast.makeText(this, checked ? "HCF Silent Alerts disabled. Real HCF Alerts stay on; background delivery may be delayed." : "HCF Silent Alerts enabled • live background delivery available.", Toast.LENGTH_LONG).show();',
)
text = text.replace(
    '        card.addView(text("This affects the silent service-status channel only. Android may limit continuous background checking when the service status is disabled.", 10, getColor(R.color.hcf_muted)));',
    '        card.addView(text("This never disables HCF Alerts. When this switch is ON, the continuous foreground sync service stops because Android requires a visible service notification; fallback background checks remain scheduled.", 10, getColor(R.color.hcf_muted)));',
)
settings.write_text(text, encoding="utf-8")

# ---------------------------------------------------------------------------
# Diagnostics/support/release notes: remove historic hard-coded build identity.
# ---------------------------------------------------------------------------
logs = src / "LogsActivity.java"
text = logs.read_text(encoding="utf-8")
text = text.replace(
    'StringBuilder sb = new StringBuilder("Harley\'s Clan Forum • Sanitized Diagnostic Report\\n\\nApp: 1.0 (10000072)\\nPackage: ");',
    'StringBuilder sb = new StringBuilder("Harley\'s Clan Forum • Sanitized Diagnostic Report\\n\\nApp: " + BuildInfo.installedVersionName() + "\\nPackage: ");',
)
logs.write_text(text, encoding="utf-8")

support = src / "SupportContactActivity.java"
text = support.read_text(encoding="utf-8")
text = text.replace(
    'addLockedRow(bodyContainer, "App", "Harley\'s Clan Forum v1.0 • build 10000072");',
    'addLockedRow(bodyContainer, "App", "Harley\'s Clan Forum v" + BuildInfo.VERSION + " • build " + BuildInfo.VERSION_CODE);',
)
text = text.replace(
    'sb.append("\\n--- Sanitized Diagnostics ---\\nApp: Harley\'s Clan Forum v1.0\\nVersion code: 10000072\\nPackage: ");',
    'sb.append("\\n--- Sanitized Diagnostics ---\\nApp: Harley\'s Clan Forum v" + BuildInfo.VERSION + "\\nVersion code: " + BuildInfo.VERSION_CODE + "\\nPackage: ");',
)
support.write_text(text, encoding="utf-8")

notes = src / "ReleaseNotes.java"
text = notes.read_text(encoding="utf-8")
text = text.replace(
    'static final String NOTES = "Harley\'s Clan Forum (app) v1.0 • Beta/Dev v10000072\\n• Development/Beta versionCode 10000072.',
    'static final String NOTES = "Harley\'s Clan Forum (app) v" + BuildInfo.VERSION + " • Beta/Dev v" + BuildInfo.VERSION_CODE + "\\n• Development/Beta versionCode " + BuildInfo.VERSION_CODE + ".',
)
text = text.replace(
    '• Theme selection is now four buttons: Forum Auto, Phone Auto, Light, and Dark.',
    '• Theme selection now has five modes: Forum Auto, Phone Auto, Light, Dark, and AMOLED.',
)
text = text.replace(
    '• Forum Auto is the default and follows FoF Night Mode; Phone Auto follows Android directly.',
    '• Forum Auto follows Flarum Night Mode and remembers the last known forum theme for cold start; Phone Auto follows Android directly.',
)
text = text.replace(
    'static final String SUMMARY = "Beta/Dev v10000072 • Four-button theme selector";',
    'static final String SUMMARY = "Beta/Dev v" + BuildInfo.VERSION_CODE + " • Theme, notifications & stability update";',
)
text = text.replace(
    'return "1.0-10000072";',
    'return BuildInfo.VERSION + "-" + BuildInfo.VERSION_CODE;',
)
text = text.replace(
    'addSection(activity, linearLayout4, "Updated • Passive notification silence", "Silence passive notifications now keeps live background sync active while routing service status, generic summaries and test/status alerts through silent low-priority behavior. Messages, mentions and replies remain normal alerts.");',
    'addSection(activity, linearLayout4, "Updated • HCF Alerts background delivery", "Background notification sync now lives under HCF Alerts. HCF Alerts remains the real alert channel and is never disabled by the Silent Alerts setting. Disabling HCF Silent Alerts stops the continuous foreground sync service, so delivery while the app is closed may fall back to delayed Android background checks.");',
)
notes.write_text(text, encoding="utf-8")

updater = src / "AppUpdateDownloader.java"
text = updater.read_text(encoding="utf-8")
text = text.replace("return 10000072L;", "return BuildInfo.VERSION_CODE;")
text = text.replace("return 10000071L;", "return BuildInfo.VERSION_CODE;")
updater.write_text(text, encoding="utf-8")

# ---------------------------------------------------------------------------
# Final direct-source conditions. These are release gates, not warnings.
# ---------------------------------------------------------------------------
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
client_text = client.read_text(encoding="utf-8")
if 'return firstDeepObject(new JSONObject(trim), i + 1, strArr);' in client_text and 'catch (Throwable ignored)' not in client_text:
    raise SystemExit("ForumNotificationClient JSON parsing guard missing")
if 'CharSequence loadLabel = null;' not in chooser.read_text(encoding="utf-8"):
    raise SystemExit("HcfIntentChooser resolveLabel initialization missing")
if 'HttpsURLConnection httpsURLConnection = null;' not in identity.read_text(encoding="utf-8"):
    raise SystemExit("IdentityActivity avatar connection initialization missing")
settings_text = settings.read_text(encoding="utf-8")
if 'Background delivery live' not in settings_text or 'Background delivery delayed' not in settings_text:
    raise SystemExit("Settings background delivery status is not mode-aware")
if 'This never disables HCF Alerts.' not in settings_text:
    raise SystemExit("Silent Alerts helper does not preserve HCF Alerts semantics")
if "10000072" in support.read_text(encoding="utf-8"):
    raise SystemExit("SupportContactActivity still contains stale build identity")
notes_text = notes.read_text(encoding="utf-8")
if "AMOLED" not in notes_text:
    raise SystemExit("Release notes do not describe the AMOLED theme update")
if "HCF Alerts background delivery" not in notes_text:
    raise SystemExit("Release notes contain stale notification-silence behavior")

print("post-repair compile + identity cleanup passed")
