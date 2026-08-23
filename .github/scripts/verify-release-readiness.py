#!/usr/bin/env python3
"""Static release gate for the HCF Beta/Dev Android source tree."""

from pathlib import Path
import re
import sys


EXPECTED_VERSION_CODE = 10000097
EXPECTED_INTERNAL_BUILD = 117
EXPECTED_PACKAGE = "com.harleytg.forum.dev"
EXPECTED_SIGNER = "93:D4:9B:F9:A8:77:C7:CF:B1:B3:7F:90:64:BD:95:5C:D6:7B:D7:DD:8D:B7:3A:9E:3F:76:6B:59:C4:BC:CE:63"


def fail(message: str) -> None:
    raise SystemExit(f"Release readiness verification failed: {message}")


def require(label: str, condition: bool) -> None:
    if not condition:
        fail(label)


def text(path: Path) -> str:
    if not path.is_file():
        fail(f"missing {path}")
    return path.read_text(encoding="utf-8")


if len(sys.argv) > 2:
    fail("usage: verify-release-readiness.py [repository-root]")

root = Path(sys.argv[1]).resolve() if len(sys.argv) == 2 else Path.cwd().resolve()
source = root / "source code"
manifest = text(source / "AndroidManifest.xml")
build_info = text(source / "src/com/harleytg/forum/HcfApplication.java")
readme = text(root / "README.md")
build_script = text(source / "build-release.sh")
app_prefs = text(source / "src/com/harleytg/forum/HcfSecurityAndPrefs.java")
app_security = text(source / "src/com/harleytg/forum/HcfSecurityAndPrefs.java")
downloader = text(source / "src/com/harleytg/forum/HcfUpdateEngine.java")
update_checker = text(source / "src/com/harleytg/forum/HcfUpdateEngine.java")
notification_helper = text(source / "src/com/harleytg/forum/HcfNotificationEngine.java")
main_activity = text(source / "src/com/harleytg/forum/HcfMainActivities.java")
hcf_application = text(source / "src/com/harleytg/forum/HcfApplication.java")
setup_center = text(source / "src/com/harleytg/forum/HcfMainActivities.java")
assetlinks = text(root / "configs/app-links/assetlinks.json")
app_links_readme = text(root / "configs/app-links/README.md")
workflows = list((root / ".github/workflows").glob("*.yml"))

version_match = re.search(r'android:versionCode="(\d+)"', manifest)
name_match = re.search(r'android:versionName="([^"]+)"', manifest)
build_version_match = re.search(r"VERSION_CODE = (\d+);", build_info)
internal_match = re.search(r"INTERNAL_BUILD = (\d+);", build_info)
require("manifest versionCode missing", version_match is not None)
require("manifest versionName missing", name_match is not None)
require("BuildInfo versionCode missing", build_version_match is not None)
require("BuildInfo internal build missing", internal_match is not None)

version_code = int(version_match.group(1))
require(f"expected versionCode {EXPECTED_VERSION_CODE}", version_code == EXPECTED_VERSION_CODE)
require("manifest/BuildInfo versionCode mismatch", int(build_version_match.group(1)) == version_code)
require("wrong internal build", int(internal_match.group(1)) == EXPECTED_INTERNAL_BUILD)
require("wrong versionName", name_match.group(1) == f"1.0 ({version_code})")
require("wrong Dev package", f'package="{EXPECTED_PACKAGE}"' in manifest)
require("wrong minimum SDK", 'android:minSdkVersion="26"' in manifest)
require("wrong target SDK", 'android:targetSdkVersion="34"' in manifest)
require("wrong compile SDK", 'android:compileSdkVersion="35"' in manifest)
require("BuildInfo APK name mismatch", f'HCF-Beta-v{version_code}.apk' in build_info)
require("BuildInfo user agent mismatch", f"Build/{version_code}" in build_info)
require("README versionCode mismatch", f"Version code: `{version_code}`" in readme)
require("README internal build mismatch", f"Internal build: `{EXPECTED_INTERNAL_BUILD}`" in readme)
require("brand spelling regression", "Harley's Studios" in build_info and "Harley&apos;s Studios" in manifest)
require("obsolete brand spelling remains", "Harley's Studio's" not in build_info and "Studio&apos;s" not in manifest)

expected_java_files = {"HcfApplication.java","HcfSecurityAndPrefs.java","HcfUpdateEngine.java","HcfNotificationEngine.java","HcfForumEngine.java","HcfMainActivities.java","HcfSubActivities.java","HcfUITheme.java"}
actual_java_files = {p.name for p in (source / "src/com/harleytg/forum").glob("*.java")}
require("Java runtime must contain exactly 8 consolidated source files", actual_java_files == expected_java_files)
require("URL-bar back button missing", 'android:id="@+id/urlBackButton"' in text(source / "res/layout/activity_main.xml"))

production_files = list((source / "src").rglob("*.java"))
for path in production_files:
    body = text(path)
    for marker in ("Method not decompiled", "Code decompiled incorrectly", "UnsupportedOperationException", "JADX ERROR"):
        require(f"decompiler marker {marker!r} remains in {path.relative_to(root)}", marker not in body)

all_runtime_text = "\n".join(text(path) for path in production_files)
require("obsolete .online forum domain remains", ".online" not in all_runtime_text.lower())
require("support email missing", "harleytg.hq@gmail.com" in all_runtime_text)
require("primary forum host missing", "forum.harleytg.com" in all_runtime_text)
require("backup forum host missing", "harleysclan.freeflarum.com" in all_runtime_text)
require("KaiOS source must not be bundled", "kaios" not in all_runtime_text.lower())

require("SetupActivity missing from manifest", f'{EXPECTED_PACKAGE}.HcfMainActivities$SetupActivity' in manifest)
require("Setup Center lifecycle launch missing", "SetupCenter.maybeLaunchForMainActivity" in hcf_application)
require("Setup Center drawer entry missing", 'setup.setText("App Setup")' in setup_center)
require("legacy permission onboarding guard missing", "PERMISSION_ONBOARDING_DONE" in hcf_application)

require("Beta asset-links package missing", EXPECTED_PACKAGE in assetlinks)
require("Beta signer missing from asset-links", EXPECTED_SIGNER in assetlinks)
require("Stable package missing from shared asset-links", '"package_name": "com.harleytg.forum"' in assetlinks)
require("canonical main-branch App Links source missing", "blob/main/configs/app-links%2Fassetlinks.json" in app_links_readme)
require("primary App Link missing", 'android:host="forum.harleytg.com"' in manifest)
require("backup App Link missing", 'android:host="harleysclan.freeflarum.com"' in manifest)

for key in ("UPDATE_DOWNLOAD_SHA256", "UPDATE_DOWNLOAD_VERSION_CODE", "UPDATE_DOWNLOAD_LABEL"):
    require(f"missing update preference {key}", key in app_prefs)
require("same-version hash comparison missing", "sameVersionHashUpdate" in update_checker)
require("installed APK hash comparison missing", "installedApkSha256" in update_checker)
require("release APK hash computation missing", "fileSha256(apk)" in update_checker)
require("downloaded APK hash verification missing", "APK SHA-256 does not match" in app_security)
require("exact installed APK duplicate guard missing", "this exact APK is already installed" in app_security)
require("download SHA-256 persistence missing", "UPDATE_DOWNLOAD_SHA256" in downloader)
require("download retry cleanup missing", "Failed, missing, or superseded downloads" in downloader)
require("same-version installer cleanup is not hash-aware", "expectedSha256.equalsIgnoreCase(installedSha256)" in downloader)
require("signer rollback protection missing", "candidateHistory.containsAll(installedCurrent)" in app_security)

require("real alert fallback is still tied to Silent Alerts", "generic notification summary" not in notification_helper)
require("real alert fallback is not on HCF Alerts", "FORUM_SUMMARY_ID, true, false" in notification_helper)
require("What's New does not wait for window focus", "hasWindowFocus() && ReleaseNotes.shouldNotify" in main_activity)
require("What's New is not rescheduled after Setup", "scheduleWhatsNew(true);" in main_activity)

require("v1 signing compatibility floor missing", "--min-sdk-version 23" in build_script)
require("v4 signature sidecar check missing", "Missing APK Signature Scheme v4 sidecar" in build_script)
require("release gate is not called by build", "verify-release-readiness.py" in build_script)
require("canonical v10000097 workflow missing", any(path.name == "build-dev-v10000097.yml" for path in workflows))
for path in workflows:
    require(f"release workflow must not write repository contents: {path.name}", "contents: write" not in text(path))

print(
    "Release readiness verification: PASS "
    f"({EXPECTED_PACKAGE} v{version_code}, internal {EXPECTED_INTERNAL_BUILD}, SHA-256 updater enabled)"
)
