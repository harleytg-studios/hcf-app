#!/usr/bin/env python3
"""Temporary v10000097 UI Playground screen-preview patch hook."""
from pathlib import Path
import subprocess
import sys


def fail(message: str) -> None:
    raise SystemExit(f"UI Playground patch failed: {message}")


source_root = Path(sys.argv[1]) if len(sys.argv) == 2 else Path("source code")
settings_path = source_root / "src/com/harleytg/forum/HcfSubActivities.java"
if not settings_path.is_file():
    fail(f"missing {settings_path}")

source = settings_path.read_text(encoding="utf-8")
start_marker = "        private void showUiPlayground() {"
end_marker = "        private void showNotificationTestConsole() {"
if source.count(start_marker) != 1 or source.count(end_marker) != 1:
    fail("could not isolate UI Playground")

start = source.index(start_marker)
end = source.index(end_marker, start)
replacement = '''        private void showUiPlayground() {
            AppLogger.info(this, "ui_playground_open", BuildInfo.VERSION);

            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(14), dp(8), dp(14), dp(16));
            scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

            content.addView(settingsInfoCard(
                    "Screen preview lab",
                    "Preview the newest HCF app screens without changing their normal layout.",
                    R.drawable.fa_bug
            ));

            content.addView(settingsSubsectionHeader(
                    "Screen previews",
                    "Open the newly designed app screens",
                    R.drawable.fa_circle_info
            ));
            content.addView(actionButton("Preview Welcome Screen", v ->
                    startActivity(new Intent(this, HcfMainActivities.WelcomeActivity.class))));
            content.addView(actionButton("Preview App Settings", v ->
                    startActivity(new Intent(this, HcfSubActivities.SettingsActivity.class))));

            new AlertDialog.Builder(this)
                    .setTitle("UI Playground")
                    .setView(scroll)
                    .setNegativeButton("Close", null)
                    .show();
        }

'''
updated = source[:start] + replacement + source[end:]
updated = updated.replace(
    "Preview and test HCF screens, visual states, controls and responsive layouts",
    "Preview newly designed HCF app screens"
)

for forbidden in (
    "Status & feedback",
    "Typography & hierarchy",
    "Controls & actions",
    "Loading & progress",
    "Notification preview",
    "Forum chrome",
    "Responsive layout",
    "Preview Account & Identity",
    "Preview Logs & Diagnostics",
    "Preview Contact Support",
    "Preview App Setup Center",
):
    if forbidden in updated[start:updated.index(end_marker, start)]:
        fail(f"obsolete Playground entry remains: {forbidden}")

for required in ("Preview Welcome Screen", "Preview App Settings"):
    if required not in updated[start:updated.index(end_marker, start)]:
        fail(f"missing {required}")

if updated != source:
    settings_path.write_text(updated, encoding="utf-8")
    subprocess.run(["git", "config", "user.name", "Harleys Studios Build Bot"], check=True)
    subprocess.run(["git", "config", "user.email", "actions@users.noreply.github.com"], check=True)
    subprocess.run(["git", "add", str(settings_path)], check=True)
    subprocess.run(["git", "commit", "-m", "Simplify UI Playground to screen previews"], check=True)
    subprocess.run(["git", "push", "origin", "HEAD:dev"], check=True)

print("UI Playground screen previews patch: PASS")
