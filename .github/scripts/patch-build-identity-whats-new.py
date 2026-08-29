#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path("source code")
CORE = ROOT / "src/com/harleytg/forum/HcfCore.java"
FORUM = ROOT / "src/com/harleytg/forum/HcfForum.java"
UPDATES = ROOT / "src/com/harleytg/forum/HcfUpdates.java"
VERIFY = Path(".github/scripts/verify-release-readiness.py")
IDENTITY = ROOT / "ci/dev-build-identity.txt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


# BuildInfo: this single label is already consumed by the drawer and all Settings headers/footers.
core = CORE.read_text(encoding="utf-8")
core = replace_once(
    core,
    '    static final String BASE_VERSION = "1.1";\n    static final String CHANNEL = "Dev";',
    '    static final String BASE_VERSION = "1.1";\n    static final String BUILD_TAG = "Development Build / Beta";\n    static final String CHANNEL = "Dev";',
    "BuildInfo BUILD_TAG",
)
core = replace_once(
    core,
    '    static final String DEVELOPMENT_BUILD_LABEL = "Beta / Development Build";',
    '    static final String DEVELOPMENT_BUILD_LABEL = "v1.1-hf1-a3 (100000104) • " + BUILD_TAG;',
    "BuildInfo DEVELOPMENT_BUILD_LABEL",
)
core = replace_once(
    core,
    '    static final String PATCH_NAME = "Build-Label-About";',
    '    static final String PATCH_NAME = "Build-Identity-Whats-New";',
    "BuildInfo PATCH_NAME",
)
core = replace_once(
    core,
    '    static final String VERSION_BUILD_LINE = "v" + VERSION + " • Beta / Development Build • Build " + VERSION_CODE;',
    '    static final String VERSION_BUILD_LINE = "v" + VERSION + " (" + VERSION_CODE + ") • " + BUILD_TAG;',
    "BuildInfo VERSION_BUILD_LINE",
)
CORE.write_text(core, encoding="utf-8")

# Replace the stale v1.0 in-app What's New banner with the live build identity.
forum = FORUM.read_text(encoding="utf-8")
banner_pattern = re.compile(
    r"this\.welcomeBanner\.setText\(\"✨  What's New • v1\.0.*?Tap to view\"\);\s*"
    r"this\.welcomeBanner\.setContentDescription\(\"What's new in v1\.0\. Tap to view release notes\.\"\);",
    re.S,
)
banner_replacement = '''this.welcomeBanner.setText("✨  What's New • v" + BuildInfo.VERSION
                    + "\\n" + BuildInfo.VERSION_CODE + " • " + BuildInfo.BUILD_TAG + "  •  Tap to view");
            this.welcomeBanner.setContentDescription("What's new in v" + BuildInfo.VERSION
                    + " build " + BuildInfo.VERSION_CODE + ". Tap to view release notes.");'''
forum, count = banner_pattern.subn(banner_replacement, forum, count=1)
if count != 1:
    raise SystemExit(f"What's New banner: expected one stale v1.0 block, found {count}")
FORUM.write_text(forum, encoding="utf-8")

# Refresh release notes and use a revision marker so a corrected same-version APK can show them once.
updates = UPDATES.read_text(encoding="utf-8")
notes_pattern = re.compile(
    r"    static final String NOTES = .*?;\n    static final String SUMMARY = .*?;\n",
    re.S,
)
notes_replacement = '''    static final String NOTES = "Harley's Clan Forum • v" + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ") • " + BuildInfo.BUILD_TAG + "\\n"
            + "• Build identity now shows version, versionCode, and the Development Build / Beta tag in App Settings and the forum drawer.\\n"
            + "• What's New now reads the live BuildInfo version/build instead of the stale v1.0 label.\\n"
            + "• Android 14 foreground-service reliability fix is retained: network and screen callbacks sync the already-running notification service instead of self-restarting it.\\n"
            + "• Safe Mode, crash recovery, diagnostics, and sanitized crash reporting remain available for recovery builds.\\n"
            + "• Home-screen widget controls include theme following, compact mode, unread count, last-updated status, refresh behavior, and tap actions.\\n"
            + "• Theme selection includes Forum Auto, Phone Auto, Light, Dark, and AMOLED.\\n"
            + "• Developer notification/runtime tools and secure same-version APK hash updates remain enabled for the Dev/Beta channel.\\n\\n"
            + "Stable remains separate; this feature set is scoped to com.harleytg.forum.dev.";
    static final String SUMMARY = "v" + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ") • " + BuildInfo.BUILD_TAG;
    private static final String NOTES_REVISION = "build-identity-v2";
'''
updates, count = notes_pattern.subn(notes_replacement, updates, count=1)
if count != 1:
    raise SystemExit(f"ReleaseNotes header: expected one match, found {count}")
updates = replace_once(
    updates,
    '        return BuildInfo.VERSION + "-" + BuildInfo.VERSION_CODE;',
    '        return BuildInfo.VERSION + "-" + BuildInfo.VERSION_CODE + "-" + NOTES_REVISION;',
    "ReleaseNotes releaseId",
)
updates = replace_once(
    updates,
    '        TextView label2 = label(activity, "v1.0  •  Dev", 11, R.color.hcf_cyan_bright, true);',
    '        TextView label2 = label(activity, "v" + BuildInfo.VERSION + "  •  " + BuildInfo.VERSION_CODE + "  •  " + BuildInfo.BUILD_TAG, 11, R.color.hcf_cyan_bright, true);',
    "ReleaseNotes metadata label",
)
UPDATES.write_text(updates, encoding="utf-8")

# Update release gate so this identity cannot silently regress back to the old/stale strings.
verify = VERIFY.read_text(encoding="utf-8")
verify = replace_once(
    verify,
    'require("RC remains in public Dev build label", \'DEVELOPMENT_BUILD_LABEL = "Beta / Development Build"\' in build_info and \'Beta / Development Build • Build \' in build_info)',
    '''require("public Dev build tag mismatch", 'BUILD_TAG = "Development Build / Beta"' in build_info)
require(
    "public Dev build identity label mismatch",
    f'DEVELOPMENT_BUILD_LABEL = "v{EXPECTED_VERSION} ({version_code}) • " + BUILD_TAG' in build_info
    and 'VERSION_BUILD_LINE = "v" + VERSION + " (" + VERSION_CODE + ") • " + BUILD_TAG' in build_info,
)
require("What's New still has stale v1.0 copy", "What's New • v1.0" not in main_activity and '"v1.0  •  Dev"' not in updates_source)
require("What's New revision marker missing", 'NOTES_REVISION = "build-identity-v2"' in updates_source)''',
    "release readiness build-label gate",
)
VERIFY.write_text(verify, encoding="utf-8")

# Keep the human-readable CI identity marker aligned with the app.
identity = IDENTITY.read_text(encoding="utf-8")
identity = replace_once(
    identity,
    "Channel label: Beta / Development Build",
    "Channel label: v1.1-hf1-a3 (100000104) • Development Build / Beta",
    "identity channel label",
)
identity = replace_once(
    identity,
    "Patch: Build-Label-About",
    "Patch: Build-Identity-Whats-New",
    "identity patch",
)
identity = replace_once(
    identity,
    "The public-facing Dev build label intentionally does not include RC.",
    "The public-facing Dev build identity includes version, versionCode, and Development Build / Beta; it intentionally does not include RC.",
    "identity note",
)
IDENTITY.write_text(identity, encoding="utf-8")

# Patch sanity checks.
core_after = CORE.read_text(encoding="utf-8")
forum_after = FORUM.read_text(encoding="utf-8")
updates_after = UPDATES.read_text(encoding="utf-8")
assert 'BUILD_TAG = "Development Build / Beta"' in core_after
assert 'v1.1-hf1-a3 (100000104) • " + BUILD_TAG' in core_after
assert "What's New • v1.0" not in forum_after
assert '"v1.0  •  Dev"' not in updates_after
assert 'NOTES_REVISION = "build-identity-v2"' in updates_after
print("HCF Dev build identity and What's New patch applied.")
