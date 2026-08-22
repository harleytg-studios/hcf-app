from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch-hcf-alerts-ui-v3.py <dev-source-code>")

root = Path(sys.argv[1]).resolve()
path = root / "src/com/harleytg/forum/SettingsActivity.java"
text = path.read_text(encoding="utf-8")

start = text.find("    // HCF_ALERTS_STATUS_TILES_V2")
end = text.find("    private View silentAlertsCard()", start)
if start < 0 or end < 0:
    raise SystemExit("HCF Alerts v2 block not found")

replacement = r'''    // HCF_ALERTS_RENDER_V3 — dedicated layout matching the selected second render.
    private View mainAlertsCard() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.TRANSPARENT);
        NotificationHelper.createChannel(this);

        final int green = android.graphics.Color.parseColor("#55E13B");
        final int yellow = android.graphics.Color.parseColor("#FFC21A");
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

        // Render row: Ready / Background / Synced.
        LinearLayout statusShell = new LinearLayout(this);
        statusShell.setOrientation(LinearLayout.HORIZONTAL);
        statusShell.setGravity(17);
        statusShell.setBackground(hcfAlertsPanelDrawable("#0C151B", "#29404B", 18, 1));
        statusShell.setPadding(dp(7), dp(8), dp(7), dp(8));
        LinearLayout.LayoutParams shellLp = new LinearLayout.LayoutParams(-1, -2);
        shellLp.bottomMargin = dp(14);
        root.addView(statusShell, shellLp);

        LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(0, dp(compact() ? 98 : 112), 1.0f);
        tileLp.setMargins(dp(4), 0, dp(4), 0);
        statusShell.addView(hcfAlertStatusTile(alertsReady ? "Ready" : "Blocked", "Alerts", "", alertsReady ? green : red), tileLp);
        statusShell.addView(hcfAlertStatusTile(backgroundState, "Background", "", backgroundColor), tileLp);
        statusShell.addView(hcfAlertStatusTile(syncState, "Last sync", syncDetail, syncColor), tileLp);

        root.addView(hcfAlertsSectionHeader("Background delivery", R.drawable.fa_bell));

        LinearLayout deliveryCard = new LinearLayout(this);
        deliveryCard.setOrientation(LinearLayout.VERTICAL);
        deliveryCard.setBackground(hcfAlertsPanelDrawable("#0E171E", "#29404B", 17, 1));
        deliveryCard.setPadding(dp(13), dp(11), dp(13), dp(11));
        LinearLayout.LayoutParams deliveryLp = new LinearLayout.LayoutParams(-1, -2);
        deliveryLp.bottomMargin = dp(14);
        root.addView(deliveryCard, deliveryLp);

        LinearLayout switchRow = new LinearLayout(this);
        switchRow.setOrientation(LinearLayout.HORIZONTAL);
        switchRow.setGravity(16);
        TextView syncLabel = text("Background notification sync", 14, getColor(R.color.hcf_text));
        switchRow.addView(syncLabel, new LinearLayout.LayoutParams(0, -2, 1.0f));
        Switch sync = target(toggle("", backgroundEnabled), "background_notification_sync");
        sync.setShowText(false);
        LinearLayout.LayoutParams switchLp = new LinearLayout.LayoutParams(dp(58), dp(40));
        switchRow.addView(sync, switchLp);
        deliveryCard.addView(switchRow);

        TextView deliveryText = text("Keep real HCF Alerts checking while the app is not open.", 10, getColor(R.color.hcf_muted));
        deliveryText.setPadding(0, dp(3), 0, dp(9));
        deliveryCard.addView(deliveryText);

        LinearLayout batteryTip = new LinearLayout(this);
        batteryTip.setOrientation(LinearLayout.HORIZONTAL);
        batteryTip.setGravity(16);
        batteryTip.setBackground(hcfAlertsPanelDrawable("#0A1319", "#203640", 14, 1));
        batteryTip.setPadding(dp(10), dp(8), dp(10), dp(8));
        TextView bolt = text("⚡", 17, getColor(R.color.hcf_text));
        bolt.setGravity(17);
        LinearLayout.LayoutParams boltLp = new LinearLayout.LayoutParams(dp(32), -2);
        boltLp.rightMargin = dp(7);
        batteryTip.addView(bolt, boltLp);
        TextView batteryText = text("For best reliability, set battery usage to Unrestricted\nin Android Settings > Apps > HCF Beta > Battery.", 10, getColor(R.color.hcf_muted));
        batteryText.setLineSpacing(0.0f, 1.05f);
        batteryTip.addView(batteryText, new LinearLayout.LayoutParams(0, -2, 1.0f));
        deliveryCard.addView(batteryTip);

        sync.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("background_notification_sync", checked).apply();
            NotificationSyncScheduler.apply(this);
            AppLogger.info(this, "setting_background_sync", Boolean.toString(checked));
            Toast.makeText(this,
                    checked ? "Background HCF Alerts enabled." : "Background checking paused. HCF Alerts channel stays available.",
                    Toast.LENGTH_SHORT).show();
            // Rebuild the Notifications page so all dashboard lights/statuses update immediately.
            showSettingsSection("notifications");
        });

        root.addView(hcfAlertsSectionHeader("Android access", R.drawable.fa_shield));

        LinearLayout accessCard = new LinearLayout(this);
        accessCard.setOrientation(LinearLayout.VERTICAL);
        accessCard.setBackground(hcfAlertsPanelDrawable("#0E171E", "#29404B", 17, 1));
        accessCard.setPadding(dp(11), dp(5), dp(11), dp(10));
        root.addView(accessCard, new LinearLayout.LayoutParams(-1, -2));

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

        TextView openSettings = target(hcfAlertsActionRow("Open Android settings", R.drawable.fa_gear), "open_hcf_alerts_android_settings");
        openSettings.setOnClickListener(v -> NotificationHelper.openChannelSettings(this));
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(-1, dp(compact() ? 48 : 52));
        openLp.topMargin = dp(9);
        accessCard.addView(openSettings, openLp);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setGravity(16);
        info.setBackground(hcfAlertsPanelDrawable("#0A1319", "#203640", 14, 1));
        info.setPadding(dp(12), dp(9), dp(12), dp(9));
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
        infoLp.topMargin = dp(11);
        root.addView(info, infoLp);
        ImageView infoIcon = settingsSectionIcon(R.drawable.fa_circle_info);
        LinearLayout.LayoutParams infoIconLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        infoIconLp.rightMargin = dp(10);
        info.addView(infoIcon, infoIconLp);
        TextView infoText = text("HCF Alerts are the real forum alerts.\nHCF Silent Alerts is only the silent service-status channel.", 10, getColor(R.color.hcf_muted));
        infoText.setLineSpacing(0.0f, 1.07f);
        info.addView(infoText, new LinearLayout.LayoutParams(0, -2, 1.0f));

        return root;
    }

    private LinearLayout hcfAlertStatusTile(String state, String label, String detail, int color) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(17);
        tile.setBackground(hcfAlertsPanelDrawable("#101A21", "#314A56", 15, 1));
        tile.setPadding(dp(5), dp(9), dp(5), dp(8));

        View light = hcfAlertStatusLight(color);
        LinearLayout.LayoutParams lightLp = new LinearLayout.LayoutParams(dp(11), dp(11));
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
            detailText.setSingleLine(true);
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
        if (Build.VERSION.SDK_INT >= 21) {
            light.setElevation(dp(5));
            light.setTranslationZ(dp(2));
        }
        return light;
    }

    private View hcfAlertsSectionHeader(String title, int iconRes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(16);
        row.setPadding(dp(5), dp(4), dp(5), dp(8));
        ImageView icon = settingsSectionIcon(iconRes);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        iconLp.rightMargin = dp(10);
        row.addView(icon, iconLp);
        TextView titleView = text(title, 14, getColor(R.color.hcf_accent_text));
        titleView.setTypeface(null, 1);
        row.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1.0f));
        return row;
    }

    private LinearLayout hcfAlertAccessRow(int iconRes, String label, String status, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(16);
        row.setPadding(dp(2), dp(10), dp(2), dp(10));

        ImageView icon = settingsSectionIcon(iconRes);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconLp.rightMargin = dp(10);
        row.addView(icon, iconLp);

        TextView labelText = text(label, 11, getColor(R.color.hcf_text));
        row.addView(labelText, new LinearLayout.LayoutParams(0, -2, 1.0f));

        View light = hcfAlertStatusLight(color);
        LinearLayout.LayoutParams lightLp = new LinearLayout.LayoutParams(dp(9), dp(9));
        lightLp.rightMargin = dp(8);
        row.addView(light, lightLp);

        TextView statusText = text(status, 10, getColor(R.color.hcf_muted));
        statusText.setGravity(17);
        row.addView(statusText);
        return row;
    }

    private View hcfAlertDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(android.graphics.Color.parseColor("#29404B"));
        divider.setAlpha(0.85f);
        divider.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        return divider;
    }

    private TextView hcfAlertsActionRow(String label, int iconRes) {
        TextView action = text(label, 13, getColor(R.color.hcf_accent_text));
        action.setGravity(17);
        action.setTypeface(null, 0);
        action.setClickable(true);
        action.setFocusable(true);
        action.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        action.setCompoundDrawablePadding(dp(10));
        action.setBackground(hcfAlertsPanelDrawable("#0D171E", "#00B8F0", 22, 1));
        action.setPadding(dp(14), 0, dp(14), 0);
        return action;
    }

    private android.graphics.drawable.GradientDrawable hcfAlertsPanelDrawable(String fill, String stroke, int radiusDp, int strokeDp) {
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        background.setColor(android.graphics.Color.parseColor(fill));
        background.setCornerRadius(dp(radiusDp));
        if (stroke != null && strokeDp > 0) background.setStroke(dp(strokeDp), android.graphics.Color.parseColor(stroke));
        return background;
    }

'''

text = text[:start] + replacement + text[end:]
# Remove duplicate old v2 marker if one survived before the replacement boundary.
text = text.replace("    // HCF_ALERTS_STATUS_TILES_V2 — organized dashboard-style HCF Alerts sub-settings.\n", "")
path.write_text(text, encoding="utf-8")

print("HCF Alerts render-v3 source patch prepared")
