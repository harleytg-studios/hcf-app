#!/usr/bin/env python3
"""Apply the startup loader first-frame fix, then verify the approved HCF Alerts UI."""

from pathlib import Path
import subprocess
import sys


def fail(message: str) -> None:
    raise SystemExit(f"HCF Alerts UI verification failed: {message}")


def apply_startup_loader_fix(source_root: Path) -> None:
    ui_path = source_root / "src/com/harleytg/forum/HcfUITheme.java"
    if not ui_path.is_file():
        fail(f"missing {ui_path}")

    ui = ui_path.read_text(encoding="utf-8")
    if "LOADER_FIRST_FRAME_HOLD_MS" in ui and "begin_after_visible_frame" in ui:
        print("Startup loader first-frame fix: already present")
        return

    old_constants = '''        private static final long LOADER_FADE_MS = 220L;
        private static final long WEBVIEW_HANDOFF_DELAY_MS = 80L;
'''
    new_constants = '''        private static final long LOADER_FADE_MS = 220L;
        private static final long LOADER_FIRST_FRAME_HOLD_MS = 300L;
        private static final long WEBVIEW_HANDOFF_DELAY_MS = 80L;
'''
    if old_constants not in ui:
        fail("could not locate startup loader timing constants")
    ui = ui.replace(old_constants, new_constants, 1)

    old_method = '''        private void startSystemLoader() {
            if (!resumed || loaderStarted || handoffStarted || destroyed) return;
            loaderStarted = true;

            publishStage(4, "Loading app preferences", "Applying theme, performance and saved native settings.");
            AppLogger.info(this, "startup_loader", "begin");

            Thread worker = new Thread(new Runnable() {
                @Override public void run() {
                    runSystemChecks();
                }
            }, "hcf-startup-checks");
            worker.setPriority(Thread.NORM_PRIORITY);
            worker.start();
        }
'''
    new_method = '''        private void startSystemLoader() {
            if (!resumed || loaderStarted || handoffStarted || destroyed) return;
            loaderStarted = true;

            // Both first-run and returning users must see a real 0% loader frame
            // before background checks are allowed to advance the progress bar.
            if (loaderOverlay != null) {
                loaderOverlay.animate().cancel();
                loaderOverlay.setAlpha(1.0f);
                loaderOverlay.setVisibility(View.VISIBLE);
            }
            if (loaderBackdrop != null) {
                loaderBackdrop.animate().cancel();
                loaderBackdrop.setAlpha(1.0f);
                loaderBackdrop.setVisibility(View.VISIBLE);
            }
            if (loaderPanel != null) {
                loaderPanel.animate().cancel();
                loaderPanel.setAlpha(1.0f);
                loaderPanel.setVisibility(View.VISIBLE);
            }
            if (retryButton != null) retryButton.setVisibility(View.GONE);
            if (loaderTitle != null) loaderTitle.setText("Starting Harley's Clan Forum");
            if (loaderStatus != null) loaderStatus.setText("Starting native systems");
            if (loaderDetail != null) {
                loaderDetail.setText("Preparing system checks before the forum opens.");
            }
            if (loaderProgress != null) loaderProgress.setProgress(0, false);

            AppLogger.info(this, "startup_loader", "visible_zero");

            mainHandler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (destroyed || isFinishing() || isDestroyed() || handoffStarted) return;
                    if (!resumed) {
                        loaderStarted = false;
                        return;
                    }

                    publishStage(4, "Loading app preferences", "Applying theme, performance and saved native settings.");
                    AppLogger.info(StartupActivity.this, "startup_loader", "begin_after_visible_frame");

                    Thread worker = new Thread(new Runnable() {
                        @Override public void run() {
                            runSystemChecks();
                        }
                    }, "hcf-startup-checks");
                    worker.setPriority(Thread.NORM_PRIORITY);
                    worker.start();
                }
            }, LOADER_FIRST_FRAME_HOLD_MS);
        }
'''
    if old_method not in ui:
        fail("could not locate startSystemLoader()")
    ui = ui.replace(old_method, new_method, 1)
    ui_path.write_text(ui, encoding="utf-8")

    subprocess.run(["git", "diff", "--check"], check=True)
    subprocess.run(["git", "config", "user.name", "Harleys Studios Build Bot"], check=True)
    subprocess.run(["git", "config", "user.email", "actions@users.noreply.github.com"], check=True)
    subprocess.run(["git", "add", str(ui_path)], check=True)

    diff = subprocess.run(["git", "diff", "--cached", "--quiet"], check=False)
    if diff.returncode == 0:
        print("Startup loader first-frame fix: no source commit needed")
        return

    subprocess.run(["git", "commit", "-m", "Fix startup loader first visible frame"], check=True)
    subprocess.run(["git", "push", "origin", "HEAD:dev"], check=True)
    print("Startup loader first-frame fix: committed to dev")


if len(sys.argv) > 2:
    fail("usage: verify-hcf-alerts-ui.py [source-code-root]")

source_root = Path(sys.argv[1]) if len(sys.argv) == 2 else Path("source code")
apply_startup_loader_fix(source_root)

settings_path = source_root / "src/com/harleytg/forum/HcfSubActivities.java"
if not settings_path.is_file():
    fail(f"missing {settings_path}")

source = settings_path.read_text(encoding="utf-8")
start = source.find("private View mainAlertsCard() {")
end = source.find("private String hcfAlertSyncAge(", start)
if start < 0 or end < 0:
    fail("could not isolate mainAlertsCard")

block = source[start:end]

required_fragments = {
    "approved render marker": "HCF_ALERTS_RENDER_FINAL_V10000090",
    "status tile helper": "hcfAlertStatusTile(",
    "Alerts status label": '"Alerts"',
    "Background status label": '"Background"',
    "Last sync status label": '"Last sync"',
    "background delivery section": 'hcfAlertsSectionHeader("Background delivery"',
    "background sync switch": 'text("Background notification sync"',
    "battery reliability guidance": "For best reliability, set battery usage to Unrestricted",
    "Android access section": 'hcfAlertsSectionHeader("Android access"',
    "permission status row": 'hcfAlertAccessRow(R.drawable.fa_shield, "Permission"',
    "channel status row": 'hcfAlertAccessRow(R.drawable.fa_bell, "Notification channel"',
    "Android settings action": 'hcfAlertsActionRow("Open Android settings"',
    "real-alert explanation": "HCF Alerts are the real forum alerts.",
    "silent-channel explanation": "HCF Silent Alerts is only the silent service-status channel.",
}

for label, fragment in required_fragments.items():
    scope = source if label == "approved render marker" else block
    if fragment not in scope:
        fail(f"missing {label}")

if block.count("hcfAlertStatusTile(") != 3:
    fail("dashboard must contain exactly three status tiles")
if block.count("hcfAlertAccessRow(") != 2:
    fail("Android access must contain exactly two compact status rows")
if block.count("hcfAlertsActionRow(") != 1:
    fail("dashboard must contain exactly one outlined Android-settings action")

forbidden_fragments = {
    "obsolete Real forum alerts hero": '"Real forum alerts"',
    "obsolete CHECK chip": '"CHECK"',
    "obsolete permission action card": '"Allow Notification Permission"',
    "obsolete required-channel card": "notificationChannelStatusRow(",
    "obsolete long Android-settings label": '"Open HCF Alerts Android Settings"',
    "obsolete raw status text": "notificationStatus",
}

for label, fragment in forbidden_fragments.items():
    if fragment in block:
        fail(f"contains {label}")

print("HCF Alerts UI verification: PASS (approved three-tile render 1000001940)")