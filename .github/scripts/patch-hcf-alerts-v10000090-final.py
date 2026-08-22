from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch-hcf-alerts-v10000090-final.py <dev-source-code>")

root = Path(sys.argv[1]).resolve()
path = root / "src/com/harleytg/forum/SettingsActivity.java"
if not path.is_file():
    raise SystemExit(f"missing SettingsActivity: {path}")

text = path.read_text(encoding="utf-8")
start = text.find("    private View mainAlertsCard() {")
end = text.find("    private View silentAlertsCard() {", start)
if start < 0 or end < 0:
    raise SystemExit("could not locate expanded HCF Alerts block")

replacement = r'''    // HCF_ALERTS_RENDER_FINAL_V10000090 — selected newer expanded HCF Alerts design.
    private View mainAlertsCard() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.TRANSPARENT);
        NotificationHelper.createChannel(this);

        final int green = android.graphics.Color.parseColor("#55E13B");
        final int yellow = android.graphics.Color.parseColor("#FFC21A");
        final int red = android.graphics.Color.parseColor("#FF4D57");

        final boolean runtimePermission = NotificationHelper.hasRuntimePermission(this);
        final boolean appNotificationsEnabled = NotificationHelper.areAppNotificationsEnabled(this);
        final int channelImportance = NotificationHelper.channelImportance(this, NotificationHelper.CHANNEL_ID);
        final boolean channelEnabled = channelImportance > 0;
        final boolean canPost = NotificationHelper.canPost(this);

        String alertState;
        int alertColor;
        if (!canPost) {
            alertState = "Blocked";
            alertColor = red;
        } else if (channelImportance < 4) {
            alertState = "Limited";
            alertColor = yellow;
        } else {
            alertState = "Ready";
            alertColor = green;
        }

        final boolean backgroundEnabled = prefs.getBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, true);
        final boolean silentStatusDisabled = prefs.getBoolean(AppPrefs.SILENCE_BACKGROUND_SERVICE_NOTIFICATION, false);
        String sessionUserId = prefs.getString(AppPrefs.SESSION_USER_ID, "");
        final boolean signedIn = sessionUserId != null && !sessionUserId.trim().isEmpty();
        final String lastSyncStatus = prefs.getString(AppPrefs.NOTIFICATION_LAST_SYNC_STATUS, "");
        final String normalizedSyncStatus = lastSyncStatus == null ? "" : lastSyncStatus.toLowerCase(Locale.US);
        final boolean syncStatusSucceeded = normalizedSyncStatus.contains("synced");
        final boolean syncStatusWaiting = normalizedSyncStatus.contains("waiting") || normalizedSyncStatus.contains("unavailable");
        final boolean syncStatusFailed = normalizedSyncStatus.contains("failed") || normalizedSyncStatus.contains("error");

        String backgroundState;
        int backgroundColor;
        if (!backgroundEnabled) {
            backgroundState = "Off";
            backgroundColor = red;
        } else if (!signedIn) {
            backgroundState = "Waiting";
            backgroundColor = yellow;
        } else if (silentStatusDisabled || syncStatusWaiting || syncStatusFailed) {
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
        if (!backgroundEnabled) {
            syncState = "Paused";
            syncDetail = "Sync off";
            syncColor = red;
        } else if (lastSyncAt <= 0L) {
            syncState = "Waiting";
            syncDetail = "No sync yet";
            syncColor = yellow;
        } else if (syncStatusFailed) {
            syncState = "Failed";
            syncDetail = hcfAlertSyncAge(syncAgeSeconds);
            syncColor = red;
        } else if (syncStatusWaiting) {
            syncState = "Waiting";
            syncDetail = hcfAlertSyncAge(syncAgeSeconds);
            syncColor = yellow;
        } else if (syncStatusSucceeded && syncAgeSeconds < 120L) {
            syncState = "Synced";
            syncDetail = hcfAlertSyncAge(syncAgeSeconds);
            syncColor = green;
        } else if (syncStatusSucceeded && syncAgeSeconds < 900L) {
            syncState = "Recent";
            syncDetail = hcfAlertSyncAge(syncAgeSeconds);
            syncColor = yellow;
        } else if (syncAgeSeconds >= 900L) {
            syncState = "Stale";
            syncDetail = hcfAlertSyncAge(syncAgeSeconds);
            syncColor = red;
        } else {
            syncState = "Recent";
            syncDetail = hcfAlertSyncAge(syncAgeSeconds);
            syncColor = yellow;
        }

        // Newer reference: one grouped container with three equal status tiles.
        LinearLayout statusShell = new LinearLayout(this);
        statusShell.setOrientation(LinearLayout.HORIZONTAL);
        statusShell.setGravity(17);
        statusShell.setBackground(hcfAlertsPanelDrawable("#0C151B", "#29404B", 18, 1));
        statusShell.setPadding(dp(6), dp(8), dp(6), dp(8));
        LinearLayout.LayoutParams shellLp = new LinearLayout.LayoutParams(-1, -2);
        shellLp.bottomMargin = dp(14);
        root.addView(statusShell, shellLp);

        LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(0, dp(compact() ? 96 : 110), 1.0f);
        tileLp.setMargins(dp(3), 0, dp(3), 0);
        statusShell.addView(hcfAlertStatusTile(alertState, "Alerts", "", alertColor), tileLp);
        statusShell.addView(hcfAlertStatusTile(backgroundState, "Background", "", backgroundColor), tileLp);
        statusShell.addView(hcfAlertStatusTile(syncState, "Last sync", syncDetail, syncColor), tileLp);

        // Background delivery.
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
        syncLabel.setTypeface(null, 1);
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

        final String batteryMessage = "For best reliability, set battery usage to Unrestricted in Android Settings > Apps > HCF Beta > Battery.";
        android.text.SpannableString batteryStyled = new android.text.SpannableString(batteryMessage);
        int unrestrictedStart = batteryMessage.indexOf("Unrestricted");
        if (unrestrictedStart >= 0) {
            int unrestrictedEnd = unrestrictedStart + "Unrestricted".length();
            batteryStyled.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), unrestrictedStart, unrestrictedEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            batteryStyled.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.hcf_accent_text)), unrestrictedStart, unrestrictedEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView batteryText = text("", 10, getColor(R.color.hcf_muted));
        batteryText.setText(batteryStyled);
        batteryText.setLineSpacing(0.0f, 1.05f);
        batteryTip.addView(batteryText, new LinearLayout.LayoutParams(0, -2, 1.0f));
        deliveryCard.addView(batteryTip);

        sync.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, checked).apply();
            NotificationSyncScheduler.apply(this);
            AppLogger.info(this, "setting_background_sync", Boolean.toString(checked));
            Toast.makeText(this,
                    checked ? "Background HCF Alerts enabled." : "Background checking paused. HCF Alerts channel stays available.",
                    Toast.LENGTH_SHORT).show();
            showSettingsSection("notifications");
        });

        // Android access compact status panel.
        root.addView(hcfAlertsSectionHeader("Android access", R.drawable.fa_shield));

        LinearLayout accessCard = new LinearLayout(this);
        accessCard.setOrientation(LinearLayout.VERTICAL);
        accessCard.setBackground(hcfAlertsPanelDrawable("#0E171E", "#29404B", 17, 1));
        accessCard.setPadding(dp(11), dp(5), dp(11), dp(10));
        root.addView(accessCard, new LinearLayout.LayoutParams(-1, -2));

        String permissionStatus;
        int permissionColor;
        if (!runtimePermission) {
            permissionStatus = "Permission denied";
            permissionColor = red;
        } else if (!appNotificationsEnabled) {
            permissionStatus = "Blocked by Android";
            permissionColor = red;
        } else {
            permissionStatus = "Permission allowed";
            permissionColor = green;
        }
        accessCard.addView(hcfAlertAccessRow(R.drawable.fa_shield, "Permission", permissionStatus, permissionColor));
        accessCard.addView(hcfAlertDivider());

        String channelStatus;
        int channelColor;
        if (!channelEnabled) {
            channelStatus = "Blocked / off";
            channelColor = red;
        } else if (channelImportance >= 4) {
            channelStatus = "High priority";
            channelColor = green;
        } else if (channelImportance >= 3) {
            channelStatus = "Normal priority";
            channelColor = yellow;
        } else {
            channelStatus = "Low priority";
            channelColor = yellow;
        }
        accessCard.addView(hcfAlertAccessRow(R.drawable.fa_bell, "Notification channel", channelStatus, channelColor));

        TextView openSettings = target(hcfAlertsActionRow("Open Android settings", R.drawable.fa_gear), "open_hcf_alerts_android_settings");
        openSettings.setOnClickListener(v -> NotificationHelper.openChannelSettings(this));
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(-1, dp(compact() ? 48 : 52));
        openLp.topMargin = dp(9);
        accessCard.addView(openSettings, openLp);

        // Informational footer above HCF Silent Alerts.
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

    private String hcfAlertSyncAge(long syncAgeSeconds) {
        if (syncAgeSeconds == Long.MAX_VALUE) return "No sync yet";
        if (syncAgeSeconds < 60L) return "<1 min ago";
        long minutes = syncAgeSeconds / 60L;
        if (minutes < 60L) return minutes + (minutes == 1L ? " min ago" : " min ago");
        long hours = minutes / 60L;
        return hours + (hours == 1L ? " hr ago" : " hr ago");
    }

    private LinearLayout hcfAlertStatusTile(String state, String label, String detail, int color) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(17);
        tile.setBackground(hcfAlertsPanelDrawable("#101A21", "#314A56", 15, 1));
        tile.setPadding(dp(4), dp(8), dp(4), dp(7));

        View light = hcfAlertStatusLight(color);
        LinearLayout.LayoutParams lightLp = new LinearLayout.LayoutParams(dp(11), dp(11));
        lightLp.bottomMargin = dp(6);
        tile.addView(light, lightLp);

        TextView stateText = text(state, compact() ? 12 : 14, color);
        stateText.setTypeface(null, 1);
        stateText.setGravity(17);
        stateText.setSingleLine(true);
        tile.addView(stateText);

        TextView labelText = text(label, compact() ? 9 : 10, getColor(R.color.hcf_muted));
        labelText.setGravity(17);
        labelText.setPadding(0, dp(3), 0, 0);
        labelText.setSingleLine(true);
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
        labelText.setMaxLines(2);
        row.addView(labelText, new LinearLayout.LayoutParams(0, -2, 1.0f));

        LinearLayout statusWrap = new LinearLayout(this);
        statusWrap.setOrientation(LinearLayout.HORIZONTAL);
        statusWrap.setGravity(16);
        View light = hcfAlertStatusLight(color);
        LinearLayout.LayoutParams lightLp = new LinearLayout.LayoutParams(dp(9), dp(9));
        lightLp.rightMargin = dp(7);
        statusWrap.addView(light, lightLp);
        TextView statusText = text(status, 10, getColor(R.color.hcf_muted));
        statusText.setGravity(17);
        statusText.setMaxLines(2);
        statusWrap.addView(statusText, new LinearLayout.LayoutParams(-2, -2));
        row.addView(statusWrap, new LinearLayout.LayoutParams(-2, -2));
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

# Ensure any legacy combined status TextView (if retained elsewhere) no longer renders the old sentence.
refresh_start = text.find("        if (notificationStatus != null) {")
refresh_end = text.find("        if (cookieStatus != null)", refresh_start)
if refresh_start >= 0 and refresh_end > refresh_start:
    replacement_refresh = '''        if (notificationStatus != null) {\n            NotificationHelper.createChannel(this);\n            boolean ready = NotificationHelper.canPost(this) && NotificationHelper.channelImportance(this) >= 4;\n            notificationStatus.setText(NotificationHelper.status(this));\n            notificationStatus.setTextColor(getColor(ready ? R.color.hcf_accent_text : R.color.hcf_warning));\n        }\n'''
    text = text[:refresh_start] + replacement_refresh + text[refresh_end:]

path.write_text(text, encoding="utf-8")

check = path.read_text(encoding="utf-8")
block_start = check.find("    // HCF_ALERTS_RENDER_FINAL_V10000090")
block_end = check.find("    private View silentAlertsCard()", block_start)
block = check[block_start:block_end]
required = [
    "HCF_ALERTS_RENDER_FINAL_V10000090",
    'hcfAlertStatusTile(alertState, "Alerts"',
    'hcfAlertStatusTile(backgroundState, "Background"',
    'hcfAlertStatusTile(syncState, "Last sync"',
    '"Background notification sync"',
    '"Unrestricted"',
    '"Permission allowed"',
    '"High priority"',
    '"Open Android settings"',
    '"HCF Alerts are the real forum alerts.\\nHCF Silent Alerts is only the silent service-status channel."',
    "NotificationHelper.hasRuntimePermission(this)",
    "NotificationHelper.areAppNotificationsEnabled(this)",
    "NotificationHelper.channelImportance(this, NotificationHelper.CHANNEL_ID)",
    "AppPrefs.NOTIFICATION_LAST_SYNC_STATUS",
]
for marker in required:
    if marker not in block:
        raise SystemExit("missing final HCF Alerts UI marker: " + marker)

obsolete = [
    'sectionTitle("Real forum alerts"',
    'settingsInfoCard("Real forum alerts"',
    '"READY"',
    '"Open HCF Alerts Android Settings"',
    'notificationChannelStatusRow(',
]
for marker in obsolete:
    if marker in block:
        raise SystemExit("obsolete expanded HCF Alerts rendering remains: " + marker)

print("V10000090 HCF Alerts final UI migration applied and validated")
