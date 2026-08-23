#!/usr/bin/env python3
"""Fail a Dev build if the approved HCF Alerts dashboard is replaced.

The accepted UI is the second user-provided render (1000001940): three status
tiles, one background-delivery card, one compact Android-access status panel,
one Android-settings action, and the HCF Alerts/Silent Alerts explanation.
"""

from pathlib import Path
import sys


def fail(message: str) -> None:
    raise SystemExit(f"HCF Alerts UI verification failed: {message}")


if len(sys.argv) > 2:
    fail("usage: verify-hcf-alerts-ui.py [source-code-root]")

source_root = Path(sys.argv[1]) if len(sys.argv) == 2 else Path("source code")
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