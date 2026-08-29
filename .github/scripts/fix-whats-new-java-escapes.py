#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path("source code/src/com/harleytg/forum")
FORUM = ROOT / "HcfForum.java"
UPDATES = ROOT / "HcfUpdates.java"
VERIFY = Path(".github/scripts/verify-release-readiness.py")


def sub_once(pattern: str, replacement: str, text: str, label: str, flags=0) -> str:
    compiled = re.compile(pattern, flags)
    out, count = compiled.subn(lambda _match: replacement, text, count=1)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return out


forum = FORUM.read_text(encoding="utf-8")
forum = sub_once(
    r'this\.welcomeBanner\.setText\("✨  What\'s New • v" \+ BuildInfo\.VERSION.*?'
    r'this\.welcomeBanner\.setContentDescription\("What\'s new in v" \+ BuildInfo\.VERSION.*?release notes\."\);',
    '''this.welcomeBanner.setText("✨  What's New • v" + BuildInfo.VERSION
                    + "\\n" + BuildInfo.VERSION_CODE + " • " + BuildInfo.BUILD_TAG + "  •  Tap to view");
            this.welcomeBanner.setContentDescription("What's new in v" + BuildInfo.VERSION
                    + " build " + BuildInfo.VERSION_CODE + ". Tap to view release notes.");''',
    forum,
    "What's New banner normalization",
    re.S,
)
FORUM.write_text(forum, encoding="utf-8")

updates = UPDATES.read_text(encoding="utf-8")
notes_replacement = '''    static final String NOTES = "Harley's Clan Forum • v" + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ") • " + BuildInfo.BUILD_TAG + "\\n"
            + "• Build identity now shows version, versionCode, and the Development Build / Beta tag in App Settings and the forum drawer.\\n"
            + "• What's New now uses the live BuildInfo version/build and build tag instead of stale fixed version text.\\n"
            + "• Android 14 foreground-service crash fix is retained: network and screen callbacks sync the already-running notification service instead of self-restarting it.\\n"
            + "• Safe Mode, crash recovery, diagnostics, and sanitized crash reporting remain available.\\n"
            + "• Home-screen widget controls include theme following, compact mode, unread count, last-updated status, refresh behavior, and tap actions.\\n"
            + "• Theme selection includes Forum Auto, Phone Auto, Light, Dark, and AMOLED.\\n"
            + "• Developer notification/runtime tools and secure same-version APK hash updates remain enabled for the Dev/Beta channel.\\n\\n"
            + "Stable remains separate; this feature set is scoped to com.harleytg.forum.dev.";
    static final String SUMMARY = "v" + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ") • " + BuildInfo.BUILD_TAG;
    private static final String NOTES_REVISION = "build-identity-v2";
'''
updates = sub_once(
    r'    static final String NOTES = .*?\n    private static final String NOTES_REVISION = "build-identity-v2";\n',
    notes_replacement,
    updates,
    "ReleaseNotes string normalization",
    re.S,
)

sections_replacement = '''        addSection(activity, linearLayout4, "Current Dev build", "Harley's Clan Forum v" + BuildInfo.VERSION + " (versionCode " + BuildInfo.VERSION_CODE + ") • " + BuildInfo.BUILD_TAG + ".");
        addSection(activity, linearLayout4, "Updated • Build identity", "The forum drawer and App Settings now show the full version, Android versionCode, and Development Build / Beta tag instead of only the channel label.");
        addSection(activity, linearLayout4, "Updated • What's New", "The banner, release-notes header, summary and accessibility text now read the live BuildInfo version/build so old fixed v1.0 text cannot drift out of date.");
        addSection(activity, linearLayout4, "Fixed • Android 14 notification service crash", "Network-available and screen-on callbacks no longer restart the foreground notification service with startForegroundService(). They request immediate sync on the service that is already running.");
        addSection(activity, linearLayout4, "Recovery • Safe Mode and crash tools", "Safe Mode, crash recovery, diagnostics and sanitized crash reporting remain available to recover from startup or runtime failures.");
        addSection(activity, linearLayout4, "Updated • Home-screen Widget", "Widget settings cover app-theme following, compact mode, unread count, last-updated status, refresh behavior and configurable tap actions.");
        addSection(activity, linearLayout4, "Updated • Secure Dev/Beta updates", "Update checks compare Android versionCode and APK SHA-256, allowing a revised same-version APK only when its hash differs while preserving package and signer verification.");
        addSection(activity, linearLayout4, "Updated • Appearance and performance", "Forum Auto, Phone Auto, Light, Dark and AMOLED themes remain available together with the app's performance profiles and runtime tools.");
'''
updates = sub_once(
    r'        addSection\(activity, linearLayout4, "Harley\'s Clan Forum \(app\) v1\.0".*?\n        scrollView\.addView',
    sections_replacement + '        scrollView.addView',
    updates,
    "ReleaseNotes detail sections",
    re.S,
)
UPDATES.write_text(updates, encoding="utf-8")

verify = VERIFY.read_text(encoding="utf-8")
anchor = 'require("What\'s New revision marker missing", \'NOTES_REVISION = "build-identity-v2"\' in updates_source)'
if anchor not in verify:
    raise SystemExit("release readiness What's New anchor missing")
if "stale detailed release title remains" not in verify:
    verify = verify.replace(
        anchor,
        anchor + '\nrequire("stale detailed release title remains", "Harley\'s Clan Forum (app) v1.0" not in updates_source)\nrequire("dynamic What\'s New build tag missing", "BuildInfo.BUILD_TAG" in updates_source and "Current Dev build" in updates_source)',
        1,
    )
VERIFY.write_text(verify, encoding="utf-8")

forum_after = FORUM.read_text(encoding="utf-8")
updates_after = UPDATES.read_text(encoding="utf-8")
assert '+ "\\n" + BuildInfo.VERSION_CODE' in forum_after
assert '+ BuildInfo.BUILD_TAG + "\\n"' in updates_after
assert 'Harley\'s Clan Forum (app) v1.0' not in updates_after
assert 'Current Dev build' in updates_after
print("What's New Java strings and detailed release notes normalized.")
