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

# TEMP_UI_PLAYGROUND_PATCH_V10000097
# This block is removed immediately after the canonical Java change is pushed and compiled.
java_path = source / "src/com/harleytg/forum/HcfSubActivities.java"
java = text(java_path)
start_marker = "        private void showUiPlayground() {"
end_marker = "        private void showNotificationTestConsole() {"
require("UI Playground start marker must occur once", java.count(start_marker) == 1)
require("UI Playground end marker must occur once", java.count(end_marker) == 1)
start = java.index(start_marker)
end = java.index(end_marker)
require("UI Playground method order invalid", start < end)

replacement = r'''        private void showUiPlayground() {
            AppLogger.info(this, "ui_playground_open", BuildInfo.VERSION);

            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(14), dp(8), dp(14), dp(16));
            scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

            content.addView(settingsInfoCard(
                    "UI test workspace",
                    "Preview real HCF screens and visual states here. Playground controls are temporary and do not change normal app settings.",
                    R.drawable.fa_bug
            ));

            content.addView(settingsSubsectionHeader(
                    "Screen previews",
                    "Open real app screens to check theme, spacing and responsive layout",
                    R.drawable.fa_circle_info
            ));
            content.addView(actionButton("Preview App Setup Center", v ->
                    startActivity(new Intent(this, HcfMainActivities.SetupActivity.class))));
            content.addView(actionButton("Preview Account & Identity", v ->
                    startActivity(new Intent(this, HcfSubActivities.IdentityActivity.class))));
            content.addView(actionButton("Preview Logs & Diagnostics", v ->
                    startActivity(new Intent(this, HcfSubActivities.LogsActivity.class))));
            content.addView(actionButton("Preview Contact Support", v ->
                    startActivity(new Intent(this, HcfSubActivities.SupportContactActivity.class))));

            content.addView(settingsSubsectionHeader(
                    "Status & feedback",
                    "Compare ready, warning, error and offline visual states",
                    R.drawable.fa_triangle_exclamation
            ));
            content.addView(uiPlaygroundStatusGallery());

            content.addView(settingsSubsectionHeader(
                    "Typography & hierarchy",
                    "Check headings, body text, metadata and accent contrast",
                    R.drawable.fa_circle_info
            ));
            content.addView(uiPlaygroundTypographyGallery());

            content.addView(settingsSubsectionHeader(
                    "Controls & actions",
                    "Check enabled, disabled and local-only interactive controls",
                    R.drawable.fa_gear
            ));
            content.addView(uiPlaygroundControlsGallery());

            content.addView(settingsSubsectionHeader(
                    "Loading & progress",
                    "Preview indeterminate and staged loading UI",
                    R.drawable.fa_download
            ));
            content.addView(uiPlaygroundLoadingGallery());

            content.addView(settingsSubsectionHeader(
                    "Notification preview",
                    "Check an HCF Alerts card without posting a real notification",
                    R.drawable.fa_bell
            ));
            content.addView(uiPlaygroundNotificationPreview());

            content.addView(settingsSubsectionHeader(
                    "Forum chrome",
                    "Preview the native header and secure URL treatment",
                    R.drawable.fa_globe
            ));
            content.addView(uiPlaygroundChromePreview());

            content.addView(settingsSubsectionHeader(
                    "Responsive layout",
                    "Inspect the current viewport and wrapping behavior",
                    R.drawable.fa_gear
            ));
            content.addView(uiPlaygroundResponsivePreview());

            new AlertDialog.Builder(this)
                    .setTitle("UI Playground")
                    .setView(scroll)
                    .setNegativeButton("Close", null)
                    .show();
        }

        private LinearLayout uiPlaygroundPanel() {
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setBackgroundResource(R.drawable.quick_action_background);
            panel.setPadding(dp(12), dp(12), dp(12), dp(12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(7);
            panel.setLayoutParams(lp);
            return panel;
        }

        private View uiPlaygroundStatusGallery() {
            LinearLayout panel = uiPlaygroundPanel();
            LinearLayout first = new LinearLayout(this);
            first.setOrientation(LinearLayout.HORIZONTAL);
            first.addView(uiPlaygroundStatusTile("Ready", "Online", getColor(R.color.hcf_cyan)), uiPlaygroundHalfParams(false));
            first.addView(uiPlaygroundStatusTile("Warning", "Needs attention", getColor(R.color.hcf_warning)), uiPlaygroundHalfParams(true));
            panel.addView(first);

            LinearLayout second = new LinearLayout(this);
            second.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams secondLp = new LinearLayout.LayoutParams(-1, -2);
            secondLp.topMargin = dp(8);
            second.addView(uiPlaygroundStatusTile("Error", "Action failed", getColor(R.color.hcf_error)), uiPlaygroundHalfParams(false));
            second.addView(uiPlaygroundStatusTile("Offline", "No connection", getColor(R.color.hcf_muted)), uiPlaygroundHalfParams(true));
            panel.addView(second, secondLp);
            return panel;
        }

        private View uiPlaygroundStatusTile(String title, String detail, int color) {
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(17);
            tile.setPadding(dp(8), dp(10), dp(8), dp(10));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            bg.setColor(getColor(R.color.hcf_surface2));
            bg.setStroke(dp(1), color);
            bg.setCornerRadius(dp(14));
            tile.setBackground(bg);

            View dot = new View(this);
            android.graphics.drawable.GradientDrawable dotBg = new android.graphics.drawable.GradientDrawable();
            dotBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dotBg.setColor(color);
            dot.setBackground(dotBg);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(10), dp(10));
            dotLp.bottomMargin = dp(5);
            tile.addView(dot, dotLp);

            TextView titleView = text(title, 12, color);
            titleView.setTypeface(null, 1);
            titleView.setGravity(17);
            tile.addView(titleView);
            TextView detailView = text(detail, 9, getColor(R.color.hcf_muted));
            detailView.setGravity(17);
            tile.addView(detailView);
            return tile;
        }

        private LinearLayout.LayoutParams uiPlaygroundHalfParams(boolean right) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            if (right) lp.leftMargin = dp(8);
            return lp;
        }

        private View uiPlaygroundTypographyGallery() {
            LinearLayout panel = uiPlaygroundPanel();
            TextView hero = text("Harley's Clan Forum", 20, getColor(R.color.hcf_text));
            hero.setTypeface(null, 1);
            panel.addView(hero);
            TextView section = text("Section heading / accent", 15, getColor(R.color.hcf_accent_text));
            section.setTypeface(null, 1);
            panel.addView(section);
            panel.addView(text("Body text — primary readable content and longer descriptions.", 12, getColor(R.color.hcf_text)));
            panel.addView(text("Secondary text — supporting details, captions and explanations.", 10, getColor(R.color.hcf_muted)));
            TextView meta = text("DEVELOPMENT BUILD / BETA  •  UI SCALE CHECK", 9, getColor(R.color.hcf_cyan));
            meta.setTypeface(null, 1);
            panel.addView(meta);
            TextView longText = text("Long-line test: Harley's Clan Forum interface text should wrap cleanly without clipping when the phone uses a narrow display or larger system font size.", 11, getColor(R.color.hcf_text));
            longText.setPadding(0, dp(8), 0, 0);
            panel.addView(longText);
            return panel;
        }

        private View uiPlaygroundControlsGallery() {
            LinearLayout panel = uiPlaygroundPanel();
            final TextView state = text("Interaction state: idle", 10, getColor(R.color.hcf_muted));
            state.setPadding(dp(2), 0, dp(2), dp(4));
            panel.addView(state);

            Button primary = actionButton("Primary action", v -> state.setText("Interaction state: primary pressed"));
            panel.addView(primary);
            Button secondary = actionButton("Secondary action", v -> state.setText("Interaction state: secondary pressed"));
            panel.addView(secondary);
            Button disabled = actionButton("Disabled action", null);
            disabled.setEnabled(false);
            disabled.setAlpha(0.42f);
            panel.addView(disabled);

            Switch localSwitch = toggle("Local preview switch — On", true);
            localSwitch.setOnCheckedChangeListener((button, checked) -> {
                button.setText(checked ? "Local preview switch — On" : "Local preview switch — Off");
                state.setText(checked ? "Interaction state: switch on" : "Interaction state: switch off");
            });
            panel.addView(localSwitch);

            CheckBox check = new CheckBox(this);
            check.setText("Local checkbox preview");
            check.setTextColor(getColor(R.color.hcf_text));
            check.setTextSize(12.0f);
            check.setChecked(true);
            check.setPadding(0, dp(4), 0, 0);
            check.setOnCheckedChangeListener((button, checked) -> state.setText(checked
                    ? "Interaction state: checkbox selected"
                    : "Interaction state: checkbox cleared"));
            panel.addView(check);
            return panel;
        }

        private View uiPlaygroundLoadingGallery() {
            LinearLayout panel = uiPlaygroundPanel();
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(16);
            android.widget.ProgressBar spinner = new android.widget.ProgressBar(this);
            spinner.setIndeterminate(true);
            LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(dp(34), dp(34));
            spinnerLp.rightMargin = dp(12);
            row.addView(spinner, spinnerLp);
            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView title = text("Loading forum systems…", 13, getColor(R.color.hcf_text));
            title.setTypeface(null, 1);
            labels.addView(title);
            labels.addView(text("Checking native services and preparing the WebView", 10, getColor(R.color.hcf_muted)));
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1.0f));
            panel.addView(row);

            android.widget.ProgressBar progress = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setIndeterminate(false);
            progress.setMax(100);
            progress.setProgress(68);
            LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, dp(12));
            progressLp.topMargin = dp(10);
            panel.addView(progress, progressLp);
            TextView stage = text("Stage preview  •  68%  •  Starting forum engine", 9, getColor(R.color.hcf_cyan));
            stage.setGravity(17);
            panel.addView(stage);
            return panel;
        }

        private View uiPlaygroundNotificationPreview() {
            LinearLayout panel = uiPlaygroundPanel();
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(16);

            ImageView icon = settingsSectionIcon(R.drawable.fa_bell);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(26), dp(26));
            iconLp.rightMargin = dp(11);
            row.addView(icon, iconLp);

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView title = text("Harley's Clan Forum", 13, getColor(R.color.hcf_text));
            title.setTypeface(null, 1);
            labels.addView(title);
            labels.addView(text("New reply in a discussion", 11, getColor(R.color.hcf_text)));
            labels.addView(text("HCF Alerts  •  now", 9, getColor(R.color.hcf_muted)));
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1.0f));

            TextView badge = text("2", 10, getColor(R.color.hcf_accent_text));
            badge.setTypeface(null, 1);
            badge.setGravity(17);
            badge.setBackgroundResource(R.drawable.status_chip_background);
            badge.setPadding(dp(8), dp(4), dp(8), dp(4));
            row.addView(badge);
            panel.addView(row);
            return panel;
        }

        private View uiPlaygroundChromePreview() {
            LinearLayout panel = uiPlaygroundPanel();
            panel.setPadding(0, 0, 0, 0);

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(16);
            header.setPadding(dp(10), dp(8), dp(10), dp(8));
            header.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_app_bar));

            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.htg_app_logo);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(34), dp(34));
            logoLp.rightMargin = dp(10);
            header.addView(logo, logoLp);

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView title = text("Harley's Clan Forum", 13, getColor(R.color.hcf_text));
            title.setTypeface(null, 1);
            labels.addView(title);
            labels.addView(text("Development Build / Beta", 9, getColor(R.color.hcf_cyan)));
            header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1.0f));
            panel.addView(header);

            LinearLayout address = new LinearLayout(this);
            address.setOrientation(LinearLayout.HORIZONTAL);
            address.setGravity(16);
            address.setPadding(dp(11), dp(8), dp(11), dp(8));
            address.setBackgroundResource(R.drawable.quick_action_background);
            ImageView lock = settingsSectionIcon(R.drawable.fa_lock);
            LinearLayout.LayoutParams lockLp = new LinearLayout.LayoutParams(dp(18), dp(18));
            lockLp.rightMargin = dp(8);
            address.addView(lock, lockLp);
            TextView url = text("forum.harleytg.com", 11, getColor(R.color.hcf_text));
            url.setSingleLine(true);
            url.setEllipsize(TextUtils.TruncateAt.END);
            address.addView(url, new LinearLayout.LayoutParams(0, -2, 1.0f));
            TextView secure = text("SECURE", 8, getColor(R.color.hcf_cyan));
            secure.setTypeface(null, 1);
            address.addView(secure);
            panel.addView(address);
            return panel;
        }

        private View uiPlaygroundResponsivePreview() {
            LinearLayout panel = uiPlaygroundPanel();
            int width = getResources().getConfiguration().screenWidthDp;
            int height = getResources().getConfiguration().screenHeightDp;
            String orientation = getResources().getConfiguration().orientation == 2 ? "Landscape" : "Portrait";
            TextView viewport = text("Current viewport: " + width + " × " + height + " dp  •  " + orientation, 11, getColor(R.color.hcf_accent_text));
            viewport.setTypeface(null, 1);
            panel.addView(viewport);
            panel.addView(text("Resize/rotate the device or change Android font/display size, then reopen this Playground to verify wrapping and spacing.", 10, getColor(R.color.hcf_muted)));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.topMargin = dp(9);
            TextView left = text("Short label", 10, getColor(R.color.hcf_text));
            left.setGravity(17);
            left.setBackgroundResource(R.drawable.status_chip_background);
            left.setPadding(dp(8), dp(8), dp(8), dp(8));
            TextView right = text("Long responsive label that must wrap cleanly", 10, getColor(R.color.hcf_text));
            right.setGravity(17);
            right.setBackgroundResource(R.drawable.status_chip_background);
            right.setPadding(dp(8), dp(8), dp(8), dp(8));
            row.addView(left, uiPlaygroundHalfParams(false));
            row.addView(right, uiPlaygroundHalfParams(true));
            panel.addView(row, rowLp);
            return panel;
        }

'''

new_java = java[:start] + replacement + java[end:]
old_subtitle = '                    "Preview and test HCF screens, components, dialogs and visual states",'
new_subtitle = '                    "Preview and test HCF screens, visual states, controls and responsive layouts",'
require("UI Playground developer subtitle missing", old_subtitle in new_java)
new_java = new_java.replace(old_subtitle, new_subtitle, 1)
require("Component tests label remains", '"Component tests"' not in new_java)
require("Sample information card remains", '"Sample information card"' not in new_java)
require("legacy HCF dialog test remains", '"Test HCF Dialog"' not in new_java)
require("legacy toast test remains", '"Test Toast"' not in new_java)
require("new UI status gallery missing", "uiPlaygroundStatusGallery" in new_java)
require("new UI responsive preview missing", "uiPlaygroundResponsivePreview" in new_java)
require("Java braces unbalanced", new_java.count("{") == new_java.count("}"))
java_path.write_text(new_java, encoding="utf-8")

subprocess = __import__("subprocess")
subprocess.run(["git", "diff", "--check", "--", str(java_path.relative_to(root))], cwd=root, check=True)
subprocess.run(["git", "config", "user.name", "Harleys Studios Build Bot"], cwd=root, check=True)
subprocess.run(["git", "config", "user.email", "actions@users.noreply.github.com"], cwd=root, check=True)
subprocess.run(["git", "add", str(java_path.relative_to(root))], cwd=root, check=True)
subprocess.run(["git", "commit", "-m", "Expand UI Playground visual testing gallery"], cwd=root, check=True)
subprocess.run(["git", "push", "origin", "HEAD:dev"], cwd=root, check=True)
print("UI Playground canonical source patch pushed to dev")
