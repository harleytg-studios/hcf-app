from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WIDGET = ROOT / "source code/src/com/harleytg/forum/HcfWidget.java"
UI = ROOT / "source code/src/com/harleytg/forum/HcfUI.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def patch_widget() -> None:
    text = WIDGET.read_text(encoding="utf-8")

    text = replace_once(
        text,
        '''    public static final String PREF_SHOW_LAST_NOTIFICATION_PREVIEW =
            "widget_show_last_notification_preview";

    private static final String PREF_HISTORY_JSON = "native_notification_history_json";''',
        '''    public static final String PREF_SHOW_LAST_NOTIFICATION_PREVIEW =
            "widget_show_last_notification_preview";
    public static final String PREF_HISTORY_MODE = "native_notification_history_mode";
    public static final String PREF_HISTORY_LIMIT = "native_notification_history_limit";
    public static final String HISTORY_MODE_OFF = "off";
    public static final String HISTORY_MODE_TITLE = "title";
    public static final String HISTORY_MODE_FULL = "full";

    private static final String PREF_HISTORY_JSON = "native_notification_history_json";''',
        "widget history constants",
    )

    old_receiver = '''            SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
            JSONArray history = parseHistory(prefs.getString(PREF_HISTORY_JSON, "[]"));
            JSONArray next = new JSONArray();
            try {
                JSONObject item = new JSONObject();
                item.put("title", title);
                item.put("body", body);
                item.put("url", url);
                item.put("count", count);
                item.put("time", now);
                next.put(item);
                for (int i = 0; i < history.length() && next.length() < HISTORY_LIMIT; i++) {
                    Object existing = history.opt(i);
                    if (existing != null) next.put(existing);
                }
            } catch (Throwable ignored) {
                next = history;
            }

            prefs.edit()
                    .putString(PREF_HISTORY_JSON, next.toString())
                    .putString(PREF_LAST_TITLE, title)
                    .putString(PREF_LAST_BODY, body)
                    .putString(PREF_LAST_URL, url)
                    .putLong(PREF_LAST_EVENT_MS, now)
                    .apply();
            refreshAll(context);'''
    new_receiver = '''            SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
            String historyMode = historyMode(prefs);
            int historyLimit = historyLimit(prefs);
            SharedPreferences.Editor editor = prefs.edit()
                    .putString(PREF_LAST_TITLE, title)
                    .putString(PREF_LAST_BODY, body)
                    .putString(PREF_LAST_URL, url)
                    .putLong(PREF_LAST_EVENT_MS, now);

            if (HISTORY_MODE_OFF.equals(historyMode)) {
                editor.remove(PREF_HISTORY_JSON);
            } else {
                JSONArray history = parseHistory(prefs.getString(PREF_HISTORY_JSON, "[]"));
                JSONArray next = new JSONArray();
                try {
                    JSONObject item = historyItem(title, body, url, count, now, historyMode);
                    next.put(item);
                    for (int i = 0; i < history.length() && next.length() < historyLimit; i++) {
                        JSONObject existing = history.optJSONObject(i);
                        if (existing == null) continue;
                        next.put(historyItem(
                                existing.optString("title", ""),
                                existing.optString("body", ""),
                                existing.optString("url", ""),
                                existing.optInt("count", -1),
                                existing.optLong("time", 0L),
                                historyMode));
                    }
                    editor.putString(PREF_HISTORY_JSON, next.toString());
                } catch (Throwable ignored) {}
            }

            editor.apply();
            refreshAll(context);'''
    text = replace_once(text, old_receiver, new_receiver, "notification receiver privacy")

    preview_anchor = '''            preview.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.edit().putBoolean(
                            PREF_SHOW_LAST_NOTIFICATION_PREVIEW, preview.isChecked()).apply();
                    refreshAll(SettingsActivity.this);
                }
            });

            root.addView(section("Automatic widget refresh"), spaced(16));'''
    preview_replacement = '''            preview.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.edit().putBoolean(
                            PREF_SHOW_LAST_NOTIFICATION_PREVIEW, preview.isChecked()).apply();
                    refreshAll(SettingsActivity.this);
                }
            });

            root.addView(section("Notification history privacy"), spaced(16));
            TextView historyHelp = label(
                    "Choose what HCF keeps in the local notification history. Widget preview storage is controlled separately above.",
                    12, false);
            historyHelp.setTextColor(Color.rgb(174, 187, 194));
            root.addView(historyHelp, spaced(2));

            RadioGroup historyModeGroup = new RadioGroup(this);
            historyModeGroup.setOrientation(RadioGroup.VERTICAL);
            final String[] historyModes = {HISTORY_MODE_OFF, HISTORY_MODE_TITLE, HISTORY_MODE_FULL};
            final String[] historyModeNames = {"Off", "Titles only", "Titles + message"};
            String selectedHistoryMode = historyMode(prefs);
            for (int i = 0; i < historyModes.length; i++) {
                RadioButton button = new RadioButton(this);
                button.setId(47200 + i);
                button.setText(historyModeNames[i]);
                button.setTextColor(Color.rgb(232, 248, 255));
                button.setTag(historyModes[i]);
                historyModeGroup.addView(button);
                if (historyModes[i].equals(selectedHistoryMode)) button.setChecked(true);
            }
            root.addView(historyModeGroup, matchWrap());
            historyModeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    View checked = group.findViewById(checkedId);
                    if (checked == null || !(checked.getTag() instanceof String)) return;
                    setHistoryPrivacy(prefs, (String) checked.getTag(), historyLimit(prefs));
                }
            });

            TextView retentionTitle = label("History retention", 14, true);
            retentionTitle.setTextColor(Color.rgb(0, 184, 240));
            root.addView(retentionTitle, spaced(12));
            RadioGroup retentionGroup = new RadioGroup(this);
            retentionGroup.setOrientation(RadioGroup.VERTICAL);
            final int[] retentionValues = {10, 30, 60};
            final String[] retentionNames = {"Keep 10 events", "Keep 30 events", "Keep 60 events"};
            int selectedRetention = historyLimit(prefs);
            for (int i = 0; i < retentionValues.length; i++) {
                RadioButton button = new RadioButton(this);
                button.setId(47300 + i);
                button.setText(retentionNames[i]);
                button.setTextColor(Color.rgb(232, 248, 255));
                button.setTag(Integer.valueOf(retentionValues[i]));
                retentionGroup.addView(button);
                if (retentionValues[i] == selectedRetention) button.setChecked(true);
            }
            root.addView(retentionGroup, matchWrap());
            retentionGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    View checked = group.findViewById(checkedId);
                    if (checked == null || !(checked.getTag() instanceof Integer)) return;
                    setHistoryPrivacy(prefs, historyMode(prefs), ((Integer) checked.getTag()).intValue());
                }
            });

            root.addView(section("Automatic widget refresh"), spaced(16));'''
    text = replace_once(text, preview_anchor, preview_replacement, "widget history settings UI")

    text = replace_once(
        text,
        '''                    getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE).edit()
                            .remove(PREF_HISTORY_JSON)
                            .remove(PREF_LAST_TITLE)
                            .remove(PREF_LAST_BODY)
                            .remove(PREF_LAST_URL)
                            .remove(PREF_LAST_EVENT_MS)
                            .apply();''',
        '''                    getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE).edit()
                            .remove(PREF_HISTORY_JSON)
                            .apply();''',
        "history clear should not erase widget preview",
    )

    empty_anchor = '''            SharedPreferences prefs = getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
            JSONArray history = parseHistory(prefs.getString(PREF_HISTORY_JSON, "[]"));
            if (history.length() == 0) {
                TextView empty = text("No notification history yet.", 15, false);'''
    empty_replacement = '''            SharedPreferences prefs = getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
            String mode = historyMode(prefs);
            JSONArray history = parseHistory(prefs.getString(PREF_HISTORY_JSON, "[]"));
            if (history.length() == 0) {
                TextView empty = text(HISTORY_MODE_OFF.equals(mode)
                        ? "Notification history is turned off."
                        : "No notification history yet.", 15, false);'''
    text = replace_once(text, empty_anchor, empty_replacement, "history off empty state")

    parse_anchor = '''    private static JSONArray parseHistory(String raw) {
        try {
            return new JSONArray(TextUtils.isEmpty(raw) ? "[]" : raw);
        } catch (Throwable ignored) {
            return new JSONArray();
        }
    }

    private static String safe(String value) {'''
    parse_replacement = '''    public static String historyMode(SharedPreferences prefs) {
        if (prefs == null) return HISTORY_MODE_FULL;
        return normalizeHistoryMode(prefs.getString(PREF_HISTORY_MODE, HISTORY_MODE_FULL));
    }

    public static int historyLimit(SharedPreferences prefs) {
        if (prefs == null) return HISTORY_LIMIT;
        return normalizeHistoryLimit(prefs.getInt(PREF_HISTORY_LIMIT, HISTORY_LIMIT));
    }

    public static String historyModeLabel(String mode) {
        String normalized = normalizeHistoryMode(mode);
        if (HISTORY_MODE_OFF.equals(normalized)) return "Off";
        if (HISTORY_MODE_TITLE.equals(normalized)) return "Titles only";
        return "Titles + message";
    }

    public static void setHistoryPrivacy(SharedPreferences prefs, String mode, int limit) {
        if (prefs == null) return;
        String normalizedMode = normalizeHistoryMode(mode);
        int normalizedLimit = normalizeHistoryLimit(limit);
        SharedPreferences.Editor editor = prefs.edit()
                .putString(PREF_HISTORY_MODE, normalizedMode)
                .putInt(PREF_HISTORY_LIMIT, normalizedLimit);
        if (HISTORY_MODE_OFF.equals(normalizedMode)) {
            editor.remove(PREF_HISTORY_JSON).apply();
            return;
        }

        JSONArray current = parseHistory(prefs.getString(PREF_HISTORY_JSON, "[]"));
        JSONArray sanitized = new JSONArray();
        try {
            for (int i = 0; i < current.length() && sanitized.length() < normalizedLimit; i++) {
                JSONObject item = current.optJSONObject(i);
                if (item == null) continue;
                sanitized.put(historyItem(
                        item.optString("title", ""),
                        item.optString("body", ""),
                        item.optString("url", ""),
                        item.optInt("count", -1),
                        item.optLong("time", 0L),
                        normalizedMode));
            }
            editor.putString(PREF_HISTORY_JSON, sanitized.toString());
        } catch (Throwable ignored) {}
        editor.apply();
    }

    private static JSONObject historyItem(String title, String body, String url,
                                          int count, long time, String mode) throws Exception {
        JSONObject item = new JSONObject();
        item.put("title", safe(title));
        if (HISTORY_MODE_FULL.equals(normalizeHistoryMode(mode))) {
            item.put("body", safe(body));
            item.put("url", safe(url));
        } else {
            item.put("body", "");
            item.put("url", "");
        }
        item.put("count", count);
        item.put("time", time);
        return item;
    }

    private static String normalizeHistoryMode(String mode) {
        if (HISTORY_MODE_OFF.equals(mode) || HISTORY_MODE_TITLE.equals(mode)) return mode;
        return HISTORY_MODE_FULL;
    }

    private static int normalizeHistoryLimit(int limit) {
        if (limit <= 10) return 10;
        if (limit <= 30) return 30;
        return 60;
    }

    private static JSONArray parseHistory(String raw) {
        try {
            return new JSONArray(TextUtils.isEmpty(raw) ? "[]" : raw);
        } catch (Throwable ignored) {
            return new JSONArray();
        }
    }

    private static String safe(String value) {'''
    text = replace_once(text, parse_anchor, parse_replacement, "history helper methods")

    WIDGET.write_text(text, encoding="utf-8")


def patch_ui() -> None:
    text = UI.read_text(encoding="utf-8")

    text = replace_once(
        text,
        '''        private TextView securityStatus;
        private TextView serverStatus;
        private LinearLayout settingsContent;''',
        '''        private TextView securityStatus;
        private TextView serverStatus;
        private TextView hostHealthStatus;
        private LinearLayout settingsContent;''',
        "host health field",
    )

    text = replace_once(
        text,
        '''                new SettingTarget("background_notification_sync", "Background notification sync", "notification sync background HCF Alerts real forum alerts outside app closed app", "notifications", "hcf_alerts"),
                new SettingTarget("silence_hcf_silent_alerts", "Silence HCF Silent Alerts", "silent service status background notification scheduled jobs", "notifications", "silent_alerts"),''',
        '''                new SettingTarget("background_notification_sync", "Background notification sync", "notification sync background HCF Alerts real forum alerts outside app closed app", "notifications", "hcf_alerts"),
                new SettingTarget("notification_history_privacy", "Notification history privacy", "notification history local privacy off titles message retention", "notifications", "notification_history"),
                new SettingTarget("notification_history_retention", "Notification history retention", "notification history keep 10 30 60 events local", "notifications", "notification_history"),
                new SettingTarget("open_notification_history", "Open Notification History", "notification history recent local events", "notifications", "notification_history"),
                new SettingTarget("silence_hcf_silent_alerts", "Silence HCF Silent Alerts", "silent service status background notification scheduled jobs", "notifications", "silent_alerts"),''',
        "notification history search entries",
    )

    text = replace_once(
        text,
        '''                new SettingTarget("auto_failover", "Automatically use backup if primary fails", "server backup failover routing", "forum_data", "connection_routing"),
                new SettingTarget("external_links", "Allow external links to open in browser/apps", "links browser external apps", "forum_data", "connection_routing"),''',
        '''                new SettingTarget("auto_failover", "Automatically use backup if primary fails", "server backup failover routing", "forum_data", "connection_routing"),
                new SettingTarget("host_health_status", "Host Health", "primary backup server online offline latency health active host", "forum_data", "host_health"),
                new SettingTarget("test_host_health", "Test Both Forum Hosts", "primary backup server test latency health", "forum_data", "host_health"),
                new SettingTarget("use_primary_host", "Use Primary Forum", "manual switch primary host forum.harleytg.com", "forum_data", "host_health"),
                new SettingTarget("use_backup_host", "Use Backup Forum", "manual switch backup host freeflarum", "forum_data", "host_health"),
                new SettingTarget("external_links", "Allow external links to open in browser/apps", "links browser external apps", "forum_data", "connection_routing"),''',
        "host health search entries",
    )

    text = replace_once(
        text,
        '''                case "notifications":
                    settingsContent.addView(connectedSettingsPanel("HCF Alerts", "Real forum notifications • background delivery", mainAlertsCard(), shouldExpand("hcf_alerts", true)));
                    settingsContent.addView(connectedSettingsPanel("HCF Silent Alerts", "Silent service-status channel only", silentAlertsCard(), shouldExpand("silent_alerts", false)));''',
        '''                case "notifications":
                    settingsContent.addView(connectedSettingsPanel("HCF Alerts", "Real forum notifications • background delivery", mainAlertsCard(), shouldExpand("hcf_alerts", true)));
                    settingsContent.addView(connectedSettingsPanel("Notification History", "Local history privacy and retention", notificationHistoryCard(), shouldExpand("notification_history", false)));
                    settingsContent.addView(connectedSettingsPanel("HCF Silent Alerts", "Silent service-status channel only", silentAlertsCard(), shouldExpand("silent_alerts", false)));''',
        "notification history panel",
    )

    text = replace_once(
        text,
        '''                case "forum_data":
                    settingsContent.addView(connectedSettingsPanel("Connection & Routing", "Primary/backup forum routing and link handling", connectionCard(), shouldExpand("connection_routing", true)));
                    settingsContent.addView(connectedSettingsPanel("Cookies & Site Data", "Forum data stored locally on this device", privacyCard(), shouldExpand("cookies_site_data", false)));''',
        '''                case "forum_data":
                    settingsContent.addView(connectedSettingsPanel("Connection & Routing", "Primary/backup forum routing and link handling", connectionCard(), shouldExpand("connection_routing", true)));
                    settingsContent.addView(connectedSettingsPanel("Host Health", "Primary/backup reachability, latency and manual host selection", hostHealthCard(), shouldExpand("host_health", false)));
                    settingsContent.addView(connectedSettingsPanel("Cookies & Site Data", "Forum data stored locally on this device", privacyCard(), shouldExpand("cookies_site_data", false)));''',
        "host health panel",
    )

    text = replace_once(
        text,
        '''            if ("hcf_alerts".equals(key)) return "HCF Alerts";
            if ("silent_alerts".equals(key)) return "HCF Silent Alerts";''',
        '''            if ("hcf_alerts".equals(key)) return "HCF Alerts";
            if ("notification_history".equals(key)) return "Notification History";
            if ("silent_alerts".equals(key)) return "HCF Silent Alerts";''',
        "notification history section name",
    )

    text = replace_once(
        text,
        '''            if ("widget_appearance".equals(key)) return "Widget Appearance";
            if ("connection_routing".equals(key)) return "Connection & Routing";
            if ("cookies_site_data".equals(key)) return "Cookies & Site Data";''',
        '''            if ("widget_appearance".equals(key)) return "Widget Appearance";
            if ("connection_routing".equals(key)) return "Connection & Routing";
            if ("host_health".equals(key)) return "Host Health";
            if ("cookies_site_data".equals(key)) return "Cookies & Site Data";''',
        "host health section name",
    )

    history_method_anchor = '''        private View testAlertsInfoCard() {'''
    history_method = '''        private View notificationHistoryCard() {
            LinearLayout card = card();
            String mode = HcfWidget.historyMode(prefs);
            int limit = HcfWidget.historyLimit(prefs);
            card.addView(target(settingsInfoCard(
                    "Stored notification history",
                    HcfWidget.historyModeLabel(mode) + " • keep up to " + limit + " events • stored only on this device",
                    R.drawable.fa_lock), "notification_history_privacy"));

            Button privacy = target(actionButton("History content: " + HcfWidget.historyModeLabel(mode), null),
                    "notification_history_privacy");
            privacy.setOnClickListener(v -> {
                final String[] labels = {"Off", "Titles only", "Titles + message"};
                final String[] values = {HcfWidget.HISTORY_MODE_OFF, HcfWidget.HISTORY_MODE_TITLE, HcfWidget.HISTORY_MODE_FULL};
                String saved = HcfWidget.historyMode(prefs);
                int selected = HcfWidget.HISTORY_MODE_OFF.equals(saved) ? 0
                        : HcfWidget.HISTORY_MODE_TITLE.equals(saved) ? 1 : 2;
                new AlertDialog.Builder(this)
                        .setTitle("Notification history privacy")
                        .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                            HcfWidget.setHistoryPrivacy(prefs, values[which], HcfWidget.historyLimit(prefs));
                            AppLogger.info(this, "notification_history_privacy", values[which]);
                            dialog.dismiss();
                            showSettingsSection("notifications");
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
            card.addView(privacy);

            Button retention = target(actionButton("History retention: " + limit + " events", null),
                    "notification_history_retention");
            retention.setOnClickListener(v -> {
                final String[] labels = {"10 events", "30 events", "60 events"};
                final int[] values = {10, 30, 60};
                int saved = HcfWidget.historyLimit(prefs);
                int selected = saved <= 10 ? 0 : saved <= 30 ? 1 : 2;
                new AlertDialog.Builder(this)
                        .setTitle("Notification history retention")
                        .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                            HcfWidget.setHistoryPrivacy(prefs, HcfWidget.historyMode(prefs), values[which]);
                            AppLogger.info(this, "notification_history_retention", Integer.toString(values[which]));
                            dialog.dismiss();
                            showSettingsSection("notifications");
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
            card.addView(retention);
            card.addView(target(actionButton("Open Notification History", v ->
                    startActivity(new Intent(this, HcfWidget.NotificationHistoryActivity.class))),
                    "open_notification_history"));
            card.addView(text(
                    "Off stores no notification-history list. Titles only removes message bodies and destination URLs from retained history. Titles + message keeps the current local history behavior. Clearing history does not erase the separate home-screen widget preview.",
                    10, getColor(R.color.hcf_muted)));
            return card;
        }

'''
    if history_method_anchor not in text:
        raise SystemExit("notification history method anchor missing")
    text = text.replace(history_method_anchor, history_method + history_method_anchor, 1)

    host_anchor = '''        private View connectionCard() {'''
    host_methods = '''        private View hostHealthCard() {
            LinearLayout card = card();
            hostHealthStatus = target(text(hostHealthSummary(), 11, getColor(R.color.hcf_meta)), "host_health_status");
            hostHealthStatus.setBackgroundResource(R.drawable.quick_action_background);
            hostHealthStatus.setPadding(dp(14), dp(11), dp(14), dp(11));
            card.addView(hostHealthStatus);
            card.addView(target(actionButton("Test Both Forum Hosts", v -> testHostHealth()), "test_host_health"));
            card.addView(target(actionButton("Use Primary Forum", v -> selectForumHost("forum.harleytg.com", true)), "use_primary_host"));
            card.addView(target(actionButton("Use Backup Forum", v -> selectForumHost("harleysclan.freeflarum.com", false)), "use_backup_host"));
            card.addView(text(
                    "Host tests use HTTPS only and record the last reachability result and round-trip latency locally. Manual selection changes the preferred host without disabling automatic failover.",
                    10, getColor(R.color.hcf_muted)));
            return card;
        }

        private String hostHealthSummary() {
            String active = prefs.getString("active_host", "forum.harleytg.com");
            if (!ForumUrlRouter.isForumHost(active)) active = "forum.harleytg.com";
            String primary = hostHealthLine("Primary", "host_health_primary");
            String backup = hostHealthLine("Backup", "host_health_backup");
            long lastSuccess = prefs.getLong("host_health_last_success_at", 0L);
            String lastSuccessHost = prefs.getString("host_health_last_success_host", "");
            String success = lastSuccess <= 0L
                    ? "Last successful probe: Not tested yet"
                    : "Last successful probe: " + ("harleysclan.freeflarum.com".equals(lastSuccessHost) ? "Backup" : "Primary")
                            + " • " + ageLabel(lastSuccess);
            return "Currently using: " + ("forum.harleytg.com".equals(active) ? "Primary" : "Backup") + " • " + active
                    + "\\n" + primary + "\\n" + backup + "\\n" + success;
        }

        private String hostHealthLine(String label, String prefix) {
            long checkedAt = prefs.getLong(prefix + "_checked_at", 0L);
            if (checkedAt <= 0L) return label + ": Not tested";
            boolean healthy = prefs.getBoolean(prefix + "_ok", false);
            long latency = prefs.getLong(prefix + "_latency_ms", -1L);
            int status = prefs.getInt(prefix + "_http_status", -1);
            return label + ": " + (healthy ? "Online" : "Offline")
                    + (latency >= 0 ? " • " + latency + " ms" : "")
                    + (status > 0 ? " • HTTP " + status : "")
                    + " • " + ageLabel(checkedAt);
        }

        private void testHostHealth() {
            if (hostHealthStatus != null) {
                hostHealthStatus.setText("Testing primary and backup hosts…");
                hostHealthStatus.setTextColor(getColor(R.color.hcf_cyan));
            }
            new Thread(() -> {
                HostHealthResult primary = probeHost("forum.harleytg.com");
                HostHealthResult backup = probeHost("harleysclan.freeflarum.com");
                long now = System.currentTimeMillis();
                SharedPreferences.Editor editor = prefs.edit()
                        .putBoolean("host_health_primary_ok", primary.healthy)
                        .putLong("host_health_primary_latency_ms", primary.latencyMs)
                        .putInt("host_health_primary_http_status", primary.httpStatus)
                        .putLong("host_health_primary_checked_at", now)
                        .putBoolean("host_health_backup_ok", backup.healthy)
                        .putLong("host_health_backup_latency_ms", backup.latencyMs)
                        .putInt("host_health_backup_http_status", backup.httpStatus)
                        .putLong("host_health_backup_checked_at", now);
                String active = prefs.getString("active_host", "forum.harleytg.com");
                if ("forum.harleytg.com".equals(active) && primary.healthy) {
                    editor.putLong("host_health_last_success_at", now)
                            .putString("host_health_last_success_host", primary.host);
                } else if ("harleysclan.freeflarum.com".equals(active) && backup.healthy) {
                    editor.putLong("host_health_last_success_at", now)
                            .putString("host_health_last_success_host", backup.host);
                } else if (primary.healthy) {
                    editor.putLong("host_health_last_success_at", now)
                            .putString("host_health_last_success_host", primary.host);
                } else if (backup.healthy) {
                    editor.putLong("host_health_last_success_at", now)
                            .putString("host_health_last_success_host", backup.host);
                }
                editor.apply();
                AppLogger.info(this, "host_health",
                        "primary=" + primary.summary() + " • backup=" + backup.summary());
                runOnUiThread(() -> {
                    if (hostHealthStatus != null) {
                        hostHealthStatus.setText(hostHealthSummary());
                        hostHealthStatus.setTextColor(getColor(
                                primary.healthy || backup.healthy ? R.color.hcf_meta : R.color.hcf_warning));
                    }
                    Toast.makeText(this,
                            primary.healthy || backup.healthy
                                    ? "Host health test complete."
                                    : "Neither forum host responded successfully.",
                            Toast.LENGTH_SHORT).show();
                });
            }, "hcf-host-health").start();
        }

        private HostHealthResult probeHost(String host) {
            long started = android.os.SystemClock.elapsedRealtime();
            HttpsURLConnection connection = null;
            try {
                connection = (HttpsURLConnection) new URL("https://" + host + "/").openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setUseCaches(false);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("HEAD");
                connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " HostHealth/1");
                int status = connection.getResponseCode();
                long latency = Math.max(0L, android.os.SystemClock.elapsedRealtime() - started);
                boolean healthy = status >= 200 && status < 500;
                return new HostHealthResult(host, healthy, latency, status, "");
            } catch (Throwable error) {
                long latency = Math.max(0L, android.os.SystemClock.elapsedRealtime() - started);
                return new HostHealthResult(host, false, latency, -1, error.getClass().getSimpleName());
            } finally {
                if (connection != null) connection.disconnect();
            }
        }

        private void selectForumHost(String host, boolean primary) {
            if (!ForumUrlRouter.isForumHost(host)) return;
            SharedPreferences.Editor editor = prefs.edit().putString("active_host", host);
            if (primary) editor.remove("fallback_until");
            editor.apply();
            refreshStatusLabels();
            if (hostHealthStatus != null) hostHealthStatus.setText(hostHealthSummary());
            AppLogger.info(this, "settings_host_select", host);
            Toast.makeText(this,
                    (primary ? "Primary" : "Backup") + " forum selected for the next forum navigation.",
                    Toast.LENGTH_SHORT).show();
        }

        private static final class HostHealthResult {
            final String host;
            final boolean healthy;
            final long latencyMs;
            final int httpStatus;
            final String error;

            HostHealthResult(String host, boolean healthy, long latencyMs, int httpStatus, String error) {
                this.host = host;
                this.healthy = healthy;
                this.latencyMs = latencyMs;
                this.httpStatus = httpStatus;
                this.error = error == null ? "" : error;
            }

            String summary() {
                return (healthy ? "online" : "offline") + "/" + latencyMs + "ms"
                        + (httpStatus > 0 ? "/http" + httpStatus : "")
                        + (error.isEmpty() ? "" : "/" + error);
            }
        }

'''
    if host_anchor not in text:
        raise SystemExit("host health method anchor missing")
    text = text.replace(host_anchor, host_methods + host_anchor, 1)

    text = replace_once(
        text,
        '''            if (serverStatus != null) {
                String host = prefs.getString("active_host", "forum.harleytg.com");
                boolean primary = "forum.harleytg.com".equalsIgnoreCase(host);
                serverStatus.setText("Current server: " + (primary ? "Primary • " : "Backup • ") + host);
                serverStatus.setTextColor(getColor(primary ? R.color.hcf_cyan : R.color.hcf_warning));
            }
        }''',
        '''            if (serverStatus != null) {
                String host = prefs.getString("active_host", "forum.harleytg.com");
                boolean primary = "forum.harleytg.com".equalsIgnoreCase(host);
                serverStatus.setText("Current server: " + (primary ? "Primary • " : "Backup • ") + host);
                serverStatus.setTextColor(getColor(primary ? R.color.hcf_cyan : R.color.hcf_warning));
            }
            if (hostHealthStatus != null) hostHealthStatus.setText(hostHealthSummary());
        }''',
        "host health status refresh",
    )

    text = replace_once(
        text,
        '''            if (lower.contains("connection") || lower.contains("routing") || lower.contains("endpoint")) return R.drawable.fa_globe;''',
        '''            if (lower.contains("connection") || lower.contains("routing") || lower.contains("endpoint") || lower.contains("host")) return R.drawable.fa_globe;''',
        "host health icon",
    )

    UI.write_text(text, encoding="utf-8")


patch_widget()
patch_ui()
print("Applied notification-history privacy and host-health patch.")
