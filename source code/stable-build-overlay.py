#!/usr/bin/env python3
"""Create a temporary Stable compile tree from the current Dev functional source.

The checked-in Hcf* implementation is promoted from dev unchanged so functional
parity is reviewable. This script applies only Stable identity/channel values to
a temporary build tree. It never rewrites the checked-in Dev-derived sources.
"""
from pathlib import Path
import shutil
import sys

DEV_VERSION_CODE = "10000098"
STABLE_VERSION_CODE = "10000092"
STABLE_INTERNAL_BUILD = "112"


def fail(message: str) -> None:
    raise SystemExit(f"Stable build overlay failed: {message}")


def replace_required(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        fail(f"expected {label!r} marker was not found")
    return text.replace(old, new)


if len(sys.argv) != 4:
    fail("usage: stable-build-overlay.py <source-code-root> <output-src> <output-res>")

root = Path(sys.argv[1]).resolve()
out_src = Path(sys.argv[2]).resolve()
out_res = Path(sys.argv[3]).resolve()
source_src = root / "src"
source_res = root / "res"
if not source_src.is_dir() or not source_res.is_dir():
    fail("source tree is incomplete")

shutil.rmtree(out_src, ignore_errors=True)
shutil.rmtree(out_res, ignore_errors=True)
shutil.copytree(source_src, out_src)
shutil.copytree(source_res, out_res)

# Stable package identity is intentionally different from Dev/Beta.
for path in out_src.rglob("*.java"):
    body = path.read_text(encoding="utf-8")
    body = body.replace("com.harleytg.forum.dev", "com.harleytg.forum")
    body = body.replace(DEV_VERSION_CODE, STABLE_VERSION_CODE)
    path.write_text(body, encoding="utf-8")

application = out_src / "com/harleytg/forum/HcfApplication.java"
if not application.is_file():
    fail("HcfApplication.java is missing")
body = application.read_text(encoding="utf-8")
body = replace_required(body, 'static final String APK_FILE_NAME = "HCF-Beta-v10000092.apk";', 'static final String APK_FILE_NAME = "HCF-Stable-v10000092.apk";', "Stable APK filename")
body = replace_required(body, 'static final String CHANNEL = "Dev";', 'static final String CHANNEL = "Stable";', "Stable channel")
body = replace_required(body, 'static final String DEFAULT_UPDATE_CHANNEL = "dev";', 'static final String DEFAULT_UPDATE_CHANNEL = "stable";', "Stable default update channel")
body = replace_required(body, 'static final String DEVELOPMENT_BUILD_LABEL = "Harley\'s Clan Forum v1.0 [Development Build / Beta]";', 'static final String DEVELOPMENT_BUILD_LABEL = "Harley\'s Clan Forum v1.0 [Stable]";', "Stable build label")
body = replace_required(body, 'static final int INTERNAL_BUILD = 118;', f'static final int INTERNAL_BUILD = {STABLE_INTERNAL_BUILD};', "Stable internal build")
body = replace_required(body, 'static final String META_LINE = "1.0 • Development / Beta";', 'static final String META_LINE = "1.0 • Stable";', "Stable meta line")
body = replace_required(body, 'static final String VERSION_BUILD_LINE = VERSION + " • Development / Beta • Build " + VERSION_CODE;', 'static final String VERSION_BUILD_LINE = VERSION + " • Stable • Build " + VERSION_CODE;', "Stable version line")
application.write_text(body, encoding="utf-8")

# The Stable app consumes only official non-prerelease releases. Keep the new
# hash/version/signer verification logic from dev, but swap only the feed policy.
updater = out_src / "com/harleytg/forum/HcfUpdateEngine.java"
if not updater.is_file():
    fail("HcfUpdateEngine.java is missing")
body = updater.read_text(encoding="utf-8")
body = replace_required(body, "Release release = fetchDev();", "Release release = fetchStable();", "Stable release fetch")
body = replace_required(body, "resolveApkMetadata(app, release, CHANNEL_DEV);", "resolveApkMetadata(app, release, CHANNEL_STABLE);", "Stable release metadata channel")
body = replace_required(body, "private static Release fetchDev() throws Exception {", "private static Release fetchStable() throws Exception {", "Stable release method")
body = replace_required(
    body,
    'if (object != null && !object.optBoolean("draft", false) && object.optBoolean("prerelease", false)) {',
    'if (object != null && !object.optBoolean("draft", false) && !object.optBoolean("prerelease", false)) {',
    "non-prerelease release filter",
)
body = replace_required(body, 'throw new IllegalStateException("No Dev/Beta release with a trusted APK is published yet.");', 'throw new IllegalStateException("No Stable release with a trusted APK is published yet.");', "Stable empty-feed message")
body = replace_required(
    body,
    'if (candidateName.toLowerCase(Locale.US).endsWith(".apk") && AppSecurity.isTrustedReleaseDownload(candidateUrl)) {',
    'if (isStableApkName(candidateName) && AppSecurity.isTrustedReleaseDownload(candidateUrl)) {',
    "Stable APK asset filter",
)
insert_at = "    private static Release parseRelease(JSONObject object) {\n"
helper = '''    private static boolean isStableApkName(String name) {\n        String value = name == null ? "" : name.trim().toLowerCase(Locale.US);\n        if (!value.endsWith(".apk")) return false;\n        for (String blocked : new String[]{"beta", "dev", "preview", "debug", "unsigned"}) {\n            if (value.contains(blocked)) return false;\n        }\n        return true;\n    }\n\n'''
if insert_at not in body:
    fail("parseRelease insertion point was not found")
body = body.replace(insert_at, helper + insert_at, 1)
updater.write_text(body, encoding="utf-8")

# Stable-visible copy. Internal method names/comments may still mention Beta where
# inherited from source; user-visible labels, assets, and messages may not.
visible_replacements = {
    "Harley's Clan Forum [Beta]": "Harley's Clan Forum",
    "Harley&apos;s Clan Forum [Beta]": "Harley&apos;s Clan Forum",
    "Harley\\'s Clan Forum [Beta]": "Harley\\'s Clan Forum",
    "1.0 • Development / Beta": "1.0 • Stable",
    "Development / Beta": "Stable",
    "Beta/Dev v": "Stable v",
    "Beta Update Available": "Stable Update Available",
    "An updated Harley's Clan Forum development APK is ready.": "A newer Harley's Clan Forum stable release is ready.",
    "Channel: Development / Beta": "Channel: Stable • Official Releases",
    "Update when you're ready to test the latest build.": "Update when you're ready to install the latest stable release.",
    "Checking Development / Beta updates…": "Checking Stable updates…",
    "Development / Beta feed": "Stable release feed",
    "newest Development / Beta build": "newest Stable build",
    "newer Development / Beta build": "newer Stable build",
    "Revised Dev/Beta APK available": "Revised Stable APK available",
    "Dev/Beta diagnostics only": "Notification diagnostics only",
    "Dev/Beta notification tests": "Notification tests",
    "Apps > HCF Beta > Battery.": "Apps > Harley's Clan Forum > Battery.",
    "R.drawable.dev_badge_background": "R.drawable.stable_badge_background",
    "R.drawable.welcome_dev_badge_background": "R.drawable.welcome_stable_badge_background",
    "@drawable/dev_badge_background": "@drawable/stable_badge_background",
    "@drawable/welcome_dev_badge_background": "@drawable/welcome_stable_badge_background",
}
for root_dir, suffix in ((out_src, "*.java"), (out_res, "*.xml")):
    for path in root_dir.rglob(suffix):
        body = path.read_text(encoding="utf-8")
        for old, new in visible_replacements.items():
            body = body.replace(old, new)
        path.write_text(body, encoding="utf-8")

# Hard build-time invariants: Stable identity may never silently fall back to Dev.
all_java = "\n".join(path.read_text(encoding="utf-8") for path in out_src.rglob("*.java"))
all_xml = "\n".join(path.read_text(encoding="utf-8") for path in out_res.rglob("*.xml"))
for forbidden in (
    "package com.harleytg.forum.dev;",
    'DEFAULT_UPDATE_CHANNEL = "dev"',
    'CHANNEL = "Dev"',
    "HCF-Beta-v10000092.apk",
    "Harley's Clan Forum [Beta]",
    "Harley\\'s Clan Forum [Beta]",
    "Harley&apos;s Clan Forum [Beta]",
    "Development / Beta",
    "Beta Update Available",
    "R.drawable.dev_badge_background",
    "R.drawable.welcome_dev_badge_background",
):
    if forbidden in all_java or forbidden in all_xml:
        fail(f"Dev identity leaked into Stable build output: {forbidden}")
for required in (
    'DEFAULT_UPDATE_CHANNEL = "stable"',
    'CHANNEL = "Stable"',
    'HCF-Stable-v10000092.apk',
    'VERSION_CODE = 10000092',
    'INTERNAL_BUILD = 112',
):
    if required not in all_java:
        fail(f"Stable identity marker missing after overlay: {required}")
if "fetchStable()" not in updater.read_text(encoding="utf-8") or '!object.optBoolean("prerelease", false)' not in updater.read_text(encoding="utf-8"):
    fail("Stable updater policy was not applied")
if not (out_res / "drawable/stable_badge_background.xml").is_file() or not (out_res / "drawable/welcome_stable_badge_background.xml").is_file():
    fail("Stable badge resources are missing")

print("Stable build overlay: PASS (Dev functionality + Stable package/channel/version/update/assets identity)")
