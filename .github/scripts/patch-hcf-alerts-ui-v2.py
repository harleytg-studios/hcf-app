from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch-hcf-alerts-ui-v2.py <dev-source-code>")

root = Path(sys.argv[1]).resolve()
settings = root / "src/com/harleytg/forum/SettingsActivity.java"
if not settings.is_file():
    raise SystemExit(f"missing SettingsActivity: {settings}")

text = settings.read_text(encoding="utf-8")
start_marker = "    private View mainAlertsCard() {"
end_marker = "    private View silentAlertsCard() {"
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit("could not locate HCF Alerts settings method boundaries")

replacement = r'''    // HCF_ALERTS_STATUS_TILES_V2 — organized dashboard-style HCF Alerts sub-settings.
    private View mainAlertsCard() {
        LinearLayout card = card();
        NotificationHelper.createChannel(this);

        final int green = android.graphics.Color.parseColor("#54E33B");
        final int yellow = android.graphics.Color.parseColor("#FFC21C");
        final int red = android.graphics.Color.parseColor("#FF4D57");

        boolean permissionAllowed = Build.VERSION.SDK_INT < 33 || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0;
        boolean channelAvailable = NotificationHelper.channelImportance(this, "hcf_alerts_v1") != 0;
        boolean alertsReady = permissionAllowed && channelAvailable;

        boolean backgroundEnabled = prefs.getBoolean("background_notification_sync", true);
        boolean silentStatusDisabled = prefs.getBoolean("silence_background_service_notification", false);
        String sessionUserId = prefs.getString("session_user_id", "");
        boolean signedIn = sessionUserId != null && !sessionUserId.trim().isEmpty();

        String backgroundState;
        int backgroundColor;
        if (!backgroundEnabled) {
            backgroundState = "Paused";
            backgroundColor = red;
        } else if (!signedIn) {
            backgroundState = "Waiting";
            backgroundColor = yellow;
        } else if (silentStatusDisabled) {
            backgroundState = "Delayed";
            backgroundColor = yellow;
        } else {
            backgroundState = "Live";
            backgroundColor = green;
        }

        long lastSyncAt = prefs.getLong(AppPrefs.NOTIFICATION_LAST_SYNC_AT, 0L);
        long syncAgeSeconds = lastSyncAt <= 0L ? Long.MAX_VALUE : Math.max(0L, (System.currentTimeMillis() - lastSyncAt) / 1000L);
        String syncState;
        String syncDetail;
        int syncColor;
        if (lastSyncAt <= 0L) {
            syncState = "Waiting";
            syncDetail = "No sync yet";
            syncColor = yellow;
        } else if (syncAgeSeconds < 120L) {
            syncState = "Synced";
            syncDetail = syncAgeSeconds < 60L ? "<1 min ago" : "1 min ago";
            syncColor = green;
        } else if (syncAgeSeconds < 900L) {
            syncState = "Recent";
            syncDetail = (syncAgeSeconds / 60L) + " min ago";
            syncColor = yellow;
        } else {
            syncState = "Stale";
            syncDetail = (syncAgeSeconds / 60L) + " min ago";
            syncColor = red;
        }

        // Compact three-tile overview, matching the selected HCF render.
        LinearLayout statusShell = new LinearLayout(this);
        statusShell.setOrientation(LinearLayout.HORIZONTAL);
        statusShell.setGravity(17);
        statusShell.setBackgroundResource(R.drawable.quick_action_background);
        statusShell.setPadding(dp(8), dp(9), dp(8), dp(9));

        LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        tileLp.setMargins(dp(4), 0, dp(4), 0);
        statusShell.addView(hcfAlertStatusTile(alertsReady ? "Ready" : "Blocked", "Alerts", "", alertsReady ? green : red), tileLp);
        statusShell.addView(hcfAlertStatusTile(backgroundState, "Background", "", backgroundColor), tileLp);
        statusShell.addView(hcfAlertStatusTile(syncState, "Last sync", syncDetail, syncColor), tileLp);
        card.addView(statusShell);

        // Background delivery section.
        View deliveryHeader = settingsSubsectionHeader(
                "Background delivery",
                "Keep real HCF Alerts checking while the app is not open",
                R.drawable.fa_bell);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(-1, -2);
        headerLp.topMargin = dp(12);
        card.addView(deliveryHeader, headerLp);

        LinearLayout deliveryCard = new LinearLayout(this);
        deliveryCard.setOrientation(LinearLayout.VERTICAL);
        deliveryCard.setBackgroundResource(R.drawable.identity_card_background);
        deliveryCard.setPadding(dp(12), dp(10), dp(12), dp(10));

        Switch sync = target(toggle("Background notification sync", backgroundEnabled), "background_notification_sync");
        sync.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("background_notification_sync", checked).apply();
            NotificationSyncScheduler.apply(this);
            AppLogger.info(this, "setting_background_sync", Boolean.toString(checked));
            Toast.makeText(this,
                    checked ? "Background HCF Alerts enabled." : "Background checking paused. HCF Alerts channel stays available.",
                    Toast.LENGTH_SHORT).show();
            refreshStatusLabels();
        });
        deliveryCard.addView(sync);

        TextView deliveryText = text(
                "Keep real HCF Alerts checking while the app is not open.",
                10,
                getColor(R.color.hcf_muted));
        deliveryText.setPadding(dp(1), dp(4), dp(1), dp(7));
        deliveryCard.addView(deliveryText);

        LinearLayout batteryTip = new LinearLayout(this);
        batteryTip.setOrientation(LinearLayout.HORIZONTAL);
        batteryTip.setGravity(16);
        batteryTip.setBackgroundResource(R.drawable.quick_action_background);
        batteryTip.setPadding(dp(10), dp(8), dp(10), dp(8));
        TextView bolt = text("⚡", 17, getColor(R.color.hcf_text));
        bolt.setGravity(17);
        LinearLayout.LayoutParams boltLp = new LinearLayout.LayoutParams(dp(34), -2);
        boltLp.rightMargin = dp(6);
        batteryTip.addView(bolt, boltLp);
        TextView batteryText = text(
                "For best reliability, set battery usage to Unrestricted in Android Settings > Apps > HCF Beta > Battery.",
                10,
                getColor(R.color.hcf_muted));
        batteryText.setLineSpacing(0.0f, 1.06f);
        batteryTip.addView(batteryText, new LinearLayout.LayoutParams(0, -2, 1.0f));
        deliveryCard.addView(batteryTip);

        card.addView(deliveryCard);

        // Android access section.
        View accessHeader = settingsSubsectionHeader(
                "Android access",
                "Permission and channel status",
                R.drawable.fa_shield);
        LinearLayout.LayoutParams accessHeaderLp = new LinearLayout.LayoutParams(-1, -2);
        accessHeaderLp.topMargin = dp(12);
        card.addView(accessHeader, accessHeaderLp);

        LinearLayout accessCard = new LinearLayout(this);
        accessCard.setOrientation(LinearLayout.VERTICAL);
        accessCard.setBackgroundResource(R.drawable.identity_card_background);
        accessCard.setPadding(dp(10), dp(4), dp(10), dp(9));
        accessCard.addView(hcfAlertAccessRow(
                R.drawable.fa_shield,
                "Permission",
                permissionAllowed ? "Permission allowed" : "Permission blocked",
                permissionAllowed ? green : red));
        accessCard.addView(hcfAlertDivider());
        accessCard.addView(hcfAlertAccessRow(
                R.drawable.fa_bell,
                "Notification channel",
                channelAvailable ? "High priority" : "Blocked by Android",
                channelAvailable ? green : red));

        Button openSettings = target(
                actionButton("Open Android settings", v -> NotificationHelper.openChannelSettings(this)),
                "open_hcf_alerts_android_settings");
        openSettings.setCompoundDrawablesWithIntrinsicBounds(R.drawable.fa_gear, 0, 0, 0);
        openSettings.setCompoundDrawablePadding(dp(10));
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(-1, dp(50));
        openLp.topMargin = dp(9);
        accessCard.addView(openSettings, openLp);
        card.addView(accessCard);

        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
        infoLp.topMargin = dp(10);
        card.addView(settingsInfoCard(
                "HCF Alerts are the real forum alerts.",
                "HCF Silent Alerts is only the silent service-status channel. It never turns off messages, mentions, replies or important HCF activity.",
                R.drawable.fa_circle_info), infoLp);

        return card;
    }

    private LinearLayout hcfAlertStatusTile(String state, String label, String detail, int color) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(17);
        tile.setBackgroundResource(R.drawable.identity_card_background);
        tile.setPadding(dp(6), dp(10), dp(6), dp(10));
        tile.setMinimumHeight(dp(104));

        View light = hcfAlertStatusLight(color);
        LinearLayout.LayoutParams lightLp = new LinearLayout.LayoutParams(dp(10), dp(10));
        lightLp.bottomMargin = dp(7);
        tile.addView(light, lightLp);

        TextView stateText = text(state, 14, color);
        stateText.setTypeface(null, 1);
        stateText.setGravity(17);
        tile.addView(stateText);

        TextView labelText = text(label, 10, getColor(R.color.hcf_muted));
        labelText.setGravity(17);
        labelText.setPadding(0, dp(4), 0, 0);
        tile.addView(labelText);

        if (detail != null && !detail.isEmpty()) {
            TextView detailText = text(detail, 9, getColor(R.color.hcf_muted));
            detailText.setGravity(17);
            tile.addView(detailText);
        }
        return tile;
    }

    private View hcfAlertStatusLight(int color) {
        View light = new View(this);
        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setColor(color);
        light.setBackground(dot);
        if (Build.VERSION.SDK_INT >= 21) light.setElevation(dp(3));
        return light;
    }

    private LinearLayout hcfAlertAccessRow(int iconRes, String label, String status, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(16);
        row.setPadding(dp(2), dp(9), dp(2), dp(9));

        ImageView icon = settingsSectionIcon(iconRes);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconLp.rightMargin = dp(9);
        row.addView(icon, iconLp);

        TextView labelText = text(label, 11, getColor(R.color.hcf_text));
        row.addView(labelText, new LinearLayout.LayoutParams(0, -2, 1.0f));

        View light = hcfAlertStatusLight(color);
        LinearLayout.LayoutParams lightLp = new LinearLayout.LayoutParams(dp(9), dp(9));
        lightLp.rightMargin = dp(7);
        row.addView(light, lightLp);

        TextView statusText = text(status, 10, color);
        statusText.setGravity(17);
        row.addView(statusText);
        return row;
    }

    private View hcfAlertDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.hcf_divider));
        divider.setAlpha(0.85f);
        divider.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        return divider;
    }
'''

text = text[:start] + replacement + "\n\n" + text[end:]
settings.write_text(text, encoding="utf-8")

check = settings.read_text(encoding="utf-8")
required = [
    "HCF_ALERTS_STATUS_TILES_V2",
    '"Ready" : "Blocked"',
    'backgroundState = "Delayed"',
    'syncState = "Synced"',
    '"Open Android settings"',
    '"HCF Alerts are the real forum alerts."',
]
for marker in required:
    if marker not in check:
        raise SystemExit("HCF Alerts UI validation failed: " + marker)

print("HCF Alerts organized status-dashboard UI patch applied")
