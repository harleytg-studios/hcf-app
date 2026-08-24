#!/usr/bin/env python3
"""Release-readiness gate for Stable after promotion from the Dev runtime."""
from pathlib import Path
import hashlib
import re
import subprocess
import sys
import tempfile

EXPECTED_VERSION_CODE = 10000098
EXPECTED_INTERNAL_BUILD = 112
EXPECTED_PACKAGE = "com.harleytg.forum"
EXPECTED_STABLE_SIGNER = "77:E0:E9:6C:11:77:84:2A:AA:31:1A:8F:C0:EB:EA:29:B9:2D:3C:D2:90:BB:81:5B:DB:86:AD:0E:0A:85:84:4F"
EXPECTED_DEV_PACKAGE = "com.harleytg.forum.dev"
EXPECTED_DEV_SIGNER = "93:D4:9B:F9:A8:77:C7:CF:B1:B3:7F:90:64:BD:95:5C:D6:7B:D7:DD:8D:B7:3A:9E:3F:76:6B:59:C4:BC:CE:63"
EXPECTED_STABLE_LOGO_SHA256 = "f1f3404176dd3810f560d4ee1fdc6bde47d77fdf68a88493dfeb5081ac441144"


def fail(message: str) -> None:
    raise SystemExit(f"Stable release readiness verification failed: {message}")


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
readme = text(root / "README.md")
build_script = text(source / "build-release.sh")
overlay = source / "stable-build-overlay.py"
assetlinks = text(root / "configs/app-links/assetlinks.json")
app_links_readme = text(root / "configs/app-links/README.md")
logo = source / "res/drawable-nodpi-v4/htg_app_logo.png"

version_match = re.search(r'android:versionCode="(\d+)"', manifest)
name_match = re.search(r'android:versionName="([^"]+)"', manifest)
require("manifest versionCode missing", version_match is not None)
require("manifest versionName missing", name_match is not None)
require("wrong Stable versionCode", int(version_match.group(1)) == EXPECTED_VERSION_CODE)
require("wrong Stable versionName", name_match.group(1) == f"1.0 ({EXPECTED_VERSION_CODE})")
require("wrong Stable package", f'package="{EXPECTED_PACKAGE}"' in manifest)
require("Dev package leaked into Stable manifest", EXPECTED_DEV_PACKAGE not in manifest)
require("wrong Stable channel metadata", 'android:name="com.harleytg.BUILD_CHANNEL" android:value="stable"' in manifest)
require("wrong minimum SDK", 'android:minSdkVersion="26"' in manifest)
require("wrong target SDK", 'android:targetSdkVersion="34"' in manifest)
require("wrong compile SDK", 'android:compileSdkVersion="35"' in manifest)
require("primary App Link missing", 'android:host="forum.harleytg.com"' in manifest)
require("backup App Link missing", 'android:host="harleysclan.freeflarum.com"' in manifest)
for component in (
    "HcfApplication$App",
    "HcfBanSystem$GateActivity",
    "HcfDiscordObservation$BootstrapProvider",
    "HcfSessionPersistence$BootstrapProvider",
    "HcfNativeRoutes$BootstrapProvider",
    "HcfUpdateEngine$UpdateFileProvider",
    "HcfMainActivities$SetupActivity",
    "HcfUITheme$StartupActivity",
):
    require(f"new Dev runtime component missing from Stable manifest: {component}", f'{EXPECTED_PACKAGE}.{component}' in manifest)

expected_java_files = {
    "HcfAppLinksConfig.java",
    "HcfApplication.java",
    "HcfBanSystem.java",
    "HcfDiscordObservation.java",
    "HcfForumEngine.java",
    "HcfMainActivities.java",
    "HcfNativeRoutes.java",
    "HcfNotificationEngine.java",
    "HcfSecurityAndPrefs.java",
    "HcfSessionPersistence.java",
    "HcfSubActivities.java",
    "HcfUITheme.java",
    "HcfUpdateEngine.java",
}
java_dir = source / "src/com/harleytg/forum"
actual_java_files = {p.name for p in java_dir.glob("*.java")}
require("Stable is not using the complete current Dev Hcf* source set", actual_java_files == expected_java_files)

runtime = {p.name: text(p) for p in java_dir.glob("*.java")}
all_runtime = "\n".join(runtime.values())
for marker in ("Method not decompiled", "Code decompiled incorrectly", "UnsupportedOperationException", "JADX ERROR"):
    require(f"decompiler marker remains: {marker}", marker not in all_runtime)
require("obsolete .online domain remains", ".online" not in all_runtime.lower())
require("KaiOS source must not be bundled", "kaios" not in all_runtime.lower())
require("support email missing", "harleytg.hq@gmail.com" in all_runtime)
require("primary forum host missing", "forum.harleytg.com" in all_runtime)
require("backup forum host missing", "harleysclan.freeflarum.com" in all_runtime)

# Functional parity checks against the current Dev architecture.
require("Setup Center lifecycle hook missing", "SetupCenter.maybeLaunchForMainActivity" in runtime["HcfApplication.java"])
require("automatic runtime domain config missing", "RemoteDomainConfig.initialize" in runtime["HcfApplication.java"])
require("ban runtime config missing", "configs/ban-system.config" in runtime["HcfBanSystem.java"] and '"ip_sha256"' in runtime["HcfBanSystem.java"])
require("Discord user/guest observation missing", "buildUserRecord" in runtime["HcfDiscordObservation.java"] and "buildGuestRecord" in runtime["HcfDiscordObservation.java"])
require("Discord encrypted build binding missing", "HcfDiscordSecret" in runtime["HcfDiscordObservation.java"])
require("session cookie persistence missing", "setAcceptCookie(true)" in runtime["HcfSessionPersistence.java"] and "manager.flush()" in runtime["HcfSessionPersistence.java"])
require("native /app/settings route missing", 'SETTINGS_PATH = "/app/settings"' in runtime["HcfNativeRoutes.java"] and "HCFNative.openSettings" in runtime["HcfNativeRoutes.java"])
require("same-version hash updater missing", "sameVersionHashUpdate" in runtime["HcfUpdateEngine.java"])
require("installed APK hash comparison missing", "installedApkSha256" in runtime["HcfUpdateEngine.java"] or "installedApkSha256" in runtime["HcfSecurityAndPrefs.java"])
require("downloaded APK SHA-256 verification missing", "APK SHA-256 does not match" in runtime["HcfSecurityAndPrefs.java"])
require("signer rollback protection missing", "candidateHistory.containsAll(installedCurrent)" in runtime["HcfSecurityAndPrefs.java"])
require("notification engine missing HCF Alerts", "HCF Alerts" in runtime["HcfNotificationEngine.java"])
require("approved HCF Alerts panel missing", "HCF_ALERTS_RENDER_FINAL_V10000090" in runtime["HcfSubActivities.java"])
require("What's New focus gate missing", "hasWindowFocus() && ReleaseNotes.shouldNotify" in runtime["HcfMainActivities.java"])
require("URL-bar back button missing", 'android:id="@+id/urlBackButton"' in text(source / "res/layout/activity_main.xml"))

# Stable identity is applied to a temporary compile tree, never by overwriting the
# promoted functional source. Exercise the overlay here so drift fails before javac.
require("Stable overlay missing", overlay.is_file())
with tempfile.TemporaryDirectory(prefix="hcf-stable-verify-") as temp:
    temp_path = Path(temp)
    out_src = temp_path / "src"
    out_res = temp_path / "res"
    result = subprocess.run([sys.executable, str(overlay), str(source), str(out_src), str(out_res)], text=True, capture_output=True)
    if result.returncode != 0:
        fail("Stable overlay failed: " + (result.stderr.strip() or result.stdout.strip()))
    stable_app = text(out_src / "com/harleytg/forum/HcfApplication.java")
    stable_update = text(out_src / "com/harleytg/forum/HcfUpdateEngine.java")
    stable_main = text(out_src / "com/harleytg/forum/HcfMainActivities.java")
    stable_sub = text(out_src / "com/harleytg/forum/HcfSubActivities.java")
    stable_strings = text(out_res / "values/strings.xml")
    stable_layout = text(out_res / "layout/activity_main.xml")
    require("Stable package overlay missing", "package com.harleytg.forum;" in stable_app and "package com.harleytg.forum.dev;" not in stable_app)
    require("Stable BuildInfo version missing", f"VERSION_CODE = {EXPECTED_VERSION_CODE};" in stable_app)
    require("Stable internal build missing", f"INTERNAL_BUILD = {EXPECTED_INTERNAL_BUILD};" in stable_app)
    require("Stable APK filename missing", f'HCF-Stable-v{EXPECTED_VERSION_CODE}.apk' in stable_app)
    require("Stable channel missing", 'CHANNEL = "Stable"' in stable_app and 'DEFAULT_UPDATE_CHANNEL = "stable"' in stable_app)
    require("Stable build badge missing", "Harley's Clan Forum v1.0 [Stable]" in stable_app)
    require("Stable release fetch missing", "fetchStable()" in stable_update and "CHANNEL_STABLE" in stable_update)
    require("Stable updater does not reject prereleases", '!object.optBoolean("prerelease", false)' in stable_update)
    for blocked in ("beta", "dev", "preview", "debug", "unsigned"):
        require(f"Stable updater does not reject {blocked} APK assets", f'"{blocked}"' in stable_update)
    require("Stable update dialog copy missing", "Stable Update Available" in stable_main)
    require("Stable settings battery copy missing", "HCF Beta > Battery" not in stable_sub)
    require("Stable app display name wrong", "Harley\\'s Clan Forum [Beta]" not in stable_strings and "Harley\\'s Clan Forum" in stable_strings)
    require("Stable header branding wrong", "Harley&apos;s Clan Forum [Beta]" not in stable_layout)
    transformed = "\n".join(text(p) for p in out_src.rglob("*.java"))
    require("Dev package leaked into compiled Stable source", EXPECTED_DEV_PACKAGE not in transformed)
    require("Beta update copy leaked into compiled Stable source", "Beta Update Available" not in transformed)

require("Stable logo missing", logo.is_file())
require("Stable logo was replaced", hashlib.sha256(logo.read_bytes()).hexdigest() == EXPECTED_STABLE_LOGO_SHA256)
require("README Stable package mismatch", f"`{EXPECTED_PACKAGE}`" in readme)
require("README Stable version mismatch", f"Version code: `{EXPECTED_VERSION_CODE}`" in readme)
require("README Stable internal build mismatch", f"Internal build: `{EXPECTED_INTERNAL_BUILD}`" in readme)
require("README Stable channel boundary missing", "official non-prerelease" in readme.lower())

require("Stable package missing from shared Digital Asset Links", f'"package_name": "{EXPECTED_PACKAGE}"' in assetlinks)
require("Stable signer missing from shared Digital Asset Links", EXPECTED_STABLE_SIGNER in assetlinks)
require("Dev package missing from shared Digital Asset Links", f'"package_name": "{EXPECTED_DEV_PACKAGE}"' in assetlinks)
require("Dev signer missing from shared Digital Asset Links", EXPECTED_DEV_SIGNER in assetlinks)
require("canonical main-branch App Links source missing", "blob/main/configs/app-links%2Fassetlinks.json" in app_links_readme)

require("build script does not use Stable overlay", "stable-build-overlay.py" in build_script)
require("Stable package lock missing from build", 'com.harleytg.forum' in build_script)
require("Stable signer alias missing from build", "hcf-stable-v2" in build_script)
require("Stable signer fingerprint missing from build", EXPECTED_STABLE_SIGNER.replace(":", "") in build_script)
require("encrypted Discord build binding missing", "HcfDiscordSecret" in build_script and "DISCORD_WEBHOOK_URL" in build_script)
require("v1 signing compatibility missing", "--v1-signing-enabled true" in build_script and "--min-sdk-version 23" in build_script)
require("v2 signing missing", "--v2-signing-enabled true" in build_script)
require("v3 signing missing", "--v3-signing-enabled true" in build_script)
require("v4 signing missing", "--v4-signing-enabled true" in build_script)
require("v4 sidecar verification missing", "Missing APK Signature Scheme v4 sidecar" in build_script)

workflows = {p.name: text(p) for p in (root / ".github/workflows").glob("*.yml")}
require("Stable v10000098 build workflow missing", "build-stable-v10000098.yml" in workflows)
require("Dev release workflow leaked into Stable", "build-dev-v10000098.yml" not in workflows)
for name, body in workflows.items():
    require(f"workflow may write repository contents: {name}", "contents: write" not in body)

print(
    "Stable release readiness verification: PASS "
    f"({EXPECTED_PACKAGE} v{EXPECTED_VERSION_CODE}, internal {EXPECTED_INTERNAL_BUILD}; current Dev Hcf runtime + Stable identity overlay)"
)
