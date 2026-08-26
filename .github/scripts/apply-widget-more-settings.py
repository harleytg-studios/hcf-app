#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')
CORE = ROOT / 'source code/src/com/harleytg/forum/HcfCore.java'
UI = ROOT / 'source code/src/com/harleytg/forum/HcfUI.java'
WIDGET = ROOT / 'source code/src/com/harleytg/forum/HcfWidget.java'
LAYOUT = ROOT / 'source code/res/layout/widget_hcf_notifications.xml'
README = ROOT / 'README.md'
VERIFY = ROOT / '.github/scripts/verify-release-readiness.py'


def read(path):
    return path.read_text(encoding='utf-8')


def write(path, text):
    path.write_text(text, encoding='utf-8')


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, found {count}')
    return text.replace(old, new, 1)

# AppPrefs + settings transfer support.
core = read(CORE)
core = replace_once(
    core,
    '    static final String WIDGET_FOLLOW_APP_THEME = "widget_follow_app_theme";\n',
    '    static final String WIDGET_FOLLOW_APP_THEME = "widget_follow_app_theme";\n'
    '    static final String WIDGET_SHOW_CONNECTED_USERNAME = "widget_show_connected_username";\n'
    '    static final String WIDGET_SHOW_UNREAD_COUNT = "widget_show_unread_count";\n'
    '    static final String WIDGET_COMPACT_MODE = "widget_compact_mode";\n'
    '    static final String WIDGET_SHOW_LAST_UPDATED = "widget_show_last_updated";\n'
    '    static final String WIDGET_DEFAULT_TAP_ACTION = "widget_default_tap_action";\n',
    'AppPrefs widget settings'
)
core = replace_once(
    core,
    '            AppPrefs.WIDGET_FOLLOW_APP_THEME,\n            AppPrefs.SHOW_URL_BAR,\n',
    '            AppPrefs.WIDGET_FOLLOW_APP_THEME,\n'
    '            AppPrefs.WIDGET_SHOW_CONNECTED_USERNAME,\n'
    '            AppPrefs.WIDGET_SHOW_UNREAD_COUNT,\n'
    '            AppPrefs.WIDGET_COMPACT_MODE,\n'
    '            AppPrefs.WIDGET_SHOW_LAST_UPDATED,\n'
    '            AppPrefs.SHOW_URL_BAR,\n',
    'settings transfer widget booleans'
)
core = replace_once(
    core,
    '            AppPrefs.PERFORMANCE_PROFILE,\n            AppPrefs.TELEMETRY_LEVEL,\n',
    '            AppPrefs.PERFORMANCE_PROFILE,\n'
    '            AppPrefs.WIDGET_DEFAULT_TAP_ACTION,\n'
    '            AppPrefs.TELEMETRY_LEVEL,\n',
    'settings transfer widget string'
)
write(CORE, core)

# Widget runtime behavior.
widget = read(WIDGET)
widget = replace_once(
    widget,
    '    /** User setting: show the connected forum identity in the widget status line. */\n'
    '    public static final String PREF_SHOW_CONNECTED_USERNAME = "widget_show_connected_username";\n',
    '    /** User settings exposed under App Settings -> Home-screen Widget. */\n'
    '    public static final String PREF_SHOW_CONNECTED_USERNAME = AppPrefs.WIDGET_SHOW_CONNECTED_USERNAME;\n'
    '    public static final String PREF_SHOW_UNREAD_COUNT = AppPrefs.WIDGET_SHOW_UNREAD_COUNT;\n'
    '    public static final String PREF_COMPACT_MODE = AppPrefs.WIDGET_COMPACT_MODE;\n'
    '    public static final String PREF_SHOW_LAST_UPDATED = AppPrefs.WIDGET_SHOW_LAST_UPDATED;\n'
    '    public static final String PREF_DEFAULT_TAP_ACTION = AppPrefs.WIDGET_DEFAULT_TAP_ACTION;\n\n'
    '    public static final String TAP_FORUM = "forum";\n'
    '    public static final String TAP_NOTIFICATIONS = "notifications";\n'
    '    public static final String TAP_SETTINGS = "settings";\n',
    'widget preference constants'
)
widget = replace_once(
    widget,
    '                        || PREF_SHOW_CONNECTED_USERNAME.equals(key)\n'
    '                        || AppPrefs.WIDGET_FOLLOW_APP_THEME.equals(key)\n',
    '                        || PREF_SHOW_CONNECTED_USERNAME.equals(key)\n'
    '                        || PREF_SHOW_UNREAD_COUNT.equals(key)\n'
    '                        || PREF_COMPACT_MODE.equals(key)\n'
    '                        || PREF_SHOW_LAST_UPDATED.equals(key)\n'
    '                        || PREF_DEFAULT_TAP_ACTION.equals(key)\n'
    '                        || AppPrefs.WIDGET_FOLLOW_APP_THEME.equals(key)\n',
    'widget preference listener'
)
widget = replace_once(
    widget,
    '        int unreadCount = Math.max(0, prefs.getInt(AppPrefs.LAST_NOTIFICATION_COUNT, 0));\n'
    '        boolean showConnectedUsername = prefs.getBoolean(PREF_SHOW_CONNECTED_USERNAME, true);\n'
    '        String username = prefs.getString(AppPrefs.IDENTITY_USERNAME, "");\n'
    '        String connectedHandle = username == null ? "" : username.trim();\n'
    '        if (!connectedHandle.isEmpty() && !connectedHandle.startsWith("@")) {\n'
    '            connectedHandle = "@" + connectedHandle;\n'
    '        }\n\n'
    '        CharSequence status;\n'
    '        if (!signedIn) {\n'
    '            status = context.getString(R.string.widget_hcf_signed_out);\n'
    '        } else {\n'
    '            String notificationState = unreadCount == 0\n'
    '                    ? context.getString(R.string.widget_hcf_no_notifications)\n'
    '                    : context.getString(R.string.widget_hcf_unread_count, unreadCount);\n'
    '            status = showConnectedUsername && !connectedHandle.isEmpty()\n'
    '                    ? connectedHandle + " • " + notificationState\n'
    '                    : notificationState;\n'
    '        }\n',
    '        int unreadCount = Math.max(0, prefs.getInt(AppPrefs.LAST_NOTIFICATION_COUNT, 0));\n'
    '        boolean showConnectedUsername = prefs.getBoolean(PREF_SHOW_CONNECTED_USERNAME, true);\n'
    '        boolean showUnreadCount = prefs.getBoolean(PREF_SHOW_UNREAD_COUNT, true);\n'
    '        boolean compactMode = prefs.getBoolean(PREF_COMPACT_MODE, false);\n'
    '        boolean showLastUpdated = prefs.getBoolean(PREF_SHOW_LAST_UPDATED, false);\n'
    '        String username = prefs.getString(AppPrefs.IDENTITY_USERNAME, "");\n'
    '        String connectedHandle = username == null ? "" : username.trim();\n'
    '        if (!connectedHandle.isEmpty() && !connectedHandle.startsWith("@")) {\n'
    '            connectedHandle = "@" + connectedHandle;\n'
    '        }\n\n'
    '        CharSequence status;\n'
    '        if (!signedIn) {\n'
    '            status = context.getString(R.string.widget_hcf_signed_out);\n'
    '        } else {\n'
    '            String identityState = showConnectedUsername && !connectedHandle.isEmpty()\n'
    '                    ? connectedHandle : "";\n'
    '            String notificationState = "";\n'
    '            if (showUnreadCount) {\n'
    '                notificationState = unreadCount == 0\n'
    '                        ? context.getString(R.string.widget_hcf_no_notifications)\n'
    '                        : context.getString(R.string.widget_hcf_unread_count, unreadCount);\n'
    '            }\n'
    '            if (!identityState.isEmpty() && !notificationState.isEmpty()) {\n'
    '                status = identityState + " • " + notificationState;\n'
    '            } else if (!identityState.isEmpty()) {\n'
    '                status = identityState;\n'
    '            } else if (!notificationState.isEmpty()) {\n'
    '                status = notificationState;\n'
    '            } else {\n'
    '                status = "Connected to forum";\n'
    '            }\n'
    '        }\n\n'
    '        String updatedText = "Updated "\n'
    '                + android.text.format.DateFormat.getTimeFormat(context)\n'
    '                .format(new java.util.Date());\n',
    'widget status rendering'
)
widget = replace_once(
    widget,
    '        views.setTextViewText(R.id.widget_hcf_title, context.getString(R.string.widget_hcf_title));\n'
    '        views.setTextViewText(R.id.widget_hcf_status, status);\n'
    '        applyWidgetTheme(context, prefs, views);\n\n'
    '        views.setOnClickPendingIntent(\n'
    '                R.id.widget_hcf_body,\n'
    '                startupPendingIntent(context, REQUEST_OPEN_BODY, TARGET_FORUM)\n'
    '        );\n',
    '        views.setTextViewText(R.id.widget_hcf_title, context.getString(R.string.widget_hcf_title));\n'
    '        views.setTextViewText(R.id.widget_hcf_status, status);\n'
    '        views.setTextViewText(R.id.widget_hcf_updated, updatedText);\n'
    '        views.setViewVisibility(R.id.widget_hcf_logo, compactMode ? android.view.View.GONE : android.view.View.VISIBLE);\n'
    '        views.setViewVisibility(R.id.widget_hcf_title, compactMode ? android.view.View.GONE : android.view.View.VISIBLE);\n'
    '        views.setViewVisibility(R.id.widget_hcf_updated,\n'
    '                showLastUpdated && !compactMode ? android.view.View.VISIBLE : android.view.View.GONE);\n'
    '        applyWidgetTheme(context, prefs, views);\n\n'
    '        views.setOnClickPendingIntent(\n'
    '                R.id.widget_hcf_body,\n'
    '                bodyPendingIntent(context, prefs)\n'
    '        );\n',
    'widget views and body tap'
)
widget = replace_once(
    widget,
    '        views.setTextColor(R.id.widget_hcf_title, titleColor);\n'
    '        views.setTextColor(R.id.widget_hcf_status, mutedColor);\n',
    '        views.setTextColor(R.id.widget_hcf_title, titleColor);\n'
    '        views.setTextColor(R.id.widget_hcf_status, mutedColor);\n'
    '        views.setTextColor(R.id.widget_hcf_updated, mutedColor);\n',
    'widget updated text color'
)
widget = replace_once(
    widget,
    '    private static PendingIntent startupPendingIntent(Context context, int requestCode, String target) {\n',
    '    private static PendingIntent bodyPendingIntent(Context context, SharedPreferences prefs) {\n'
    '        String action = prefs == null ? TAP_FORUM\n'
    '                : prefs.getString(PREF_DEFAULT_TAP_ACTION, TAP_FORUM);\n'
    '        if (TAP_SETTINGS.equals(action)) {\n'
    '            return settingsPendingIntent(context);\n'
    '        }\n'
    '        if (TAP_NOTIFICATIONS.equals(action)) {\n'
    '            return startupPendingIntent(context, REQUEST_OPEN_BODY, TARGET_NOTIFICATIONS);\n'
    '        }\n'
    '        return startupPendingIntent(context, REQUEST_OPEN_BODY, TARGET_FORUM);\n'
    '    }\n\n'
    '    private static PendingIntent startupPendingIntent(Context context, int requestCode, String target) {\n',
    'widget body pending intent helper'
)
write(WIDGET, widget)

# Layout adds a dedicated optional last-updated row.
layout = read(LAYOUT)
layout = replace_once(
    layout,
    '                <TextView\n'
    '                    android:id="@+id/widget_hcf_status"\n'
    '                    android:layout_width="match_parent"\n'
    '                    android:layout_height="wrap_content"\n'
    '                    android:layout_marginTop="2dp"\n'
    '                    android:ellipsize="end"\n'
    '                    android:maxLines="1"\n'
    '                    android:text="@string/widget_hcf_status_initial"\n'
    '                    android:textColor="@color/hcf_muted"\n'
    '                    android:textSize="12sp" />\n',
    '                <TextView\n'
    '                    android:id="@+id/widget_hcf_status"\n'
    '                    android:layout_width="match_parent"\n'
    '                    android:layout_height="wrap_content"\n'
    '                    android:layout_marginTop="2dp"\n'
    '                    android:ellipsize="end"\n'
    '                    android:maxLines="1"\n'
    '                    android:text="@string/widget_hcf_status_initial"\n'
    '                    android:textColor="@color/hcf_muted"\n'
    '                    android:textSize="12sp" />\n\n'
    '                <TextView\n'
    '                    android:id="@+id/widget_hcf_updated"\n'
    '                    android:layout_width="match_parent"\n'
    '                    android:layout_height="wrap_content"\n'
    '                    android:layout_marginTop="1dp"\n'
    '                    android:ellipsize="end"\n'
    '                    android:maxLines="1"\n'
    '                    android:textColor="@color/hcf_muted"\n'
    '                    android:textSize="10sp"\n'
    '                    android:visibility="gone" />\n',
    'widget last updated view'
)
write(LAYOUT, layout)

# Settings UI.
ui = read(UI)
ui = replace_once(
    ui,
    '            addSettingsCategory(list, "Home-screen Widget", "Theme source, connected identity and widget appearance", "widget");\n',
    '            addSettingsCategory(list, "Home-screen Widget", "Theme, identity, content, layout and actions", "widget");\n',
    'widget category subtitle'
)
ui = replace_once(
    ui,
    '                new SettingTarget("widget_show_connected_username", "Show connected @username", "widget account identity connected username handle profile", "widget", "widget_appearance"),\n'
    '                new SettingTarget("refresh_widget_now", "Refresh Home-screen Widget", "widget refresh reload home screen", "widget", "widget_appearance"),\n',
    '                new SettingTarget("widget_show_connected_username", "Show connected @username", "widget account identity connected username handle profile", "widget", "widget_appearance"),\n'
    '                new SettingTarget("widget_show_unread_count", "Show unread count", "widget unread notifications count status", "widget", "widget_appearance"),\n'
    '                new SettingTarget("widget_compact_mode", "Compact widget mode", "widget compact small logo title layout", "widget", "widget_appearance"),\n'
    '                new SettingTarget("widget_show_last_updated", "Show last updated time", "widget refresh updated timestamp time", "widget", "widget_appearance"),\n'
    '                new SettingTarget("widget_default_tap_action", "Default widget tap", "widget tap open forum notifications settings action", "widget", "widget_appearance"),\n'
    '                new SettingTarget("refresh_widget_now", "Refresh Home-screen Widget", "widget refresh reload home screen", "widget", "widget_appearance"),\n',
    'widget search targets'
)
ui = replace_once(
    ui,
    '            card.addView(text(\n'
    '                    "When on, signed-in widgets show the connected forum identity as @username next to the cached notification state. No username is shown while signed out.",\n'
    '                    10, getColor(R.color.hcf_muted)));\n\n'
    '            card.addView(target(actionButton("Refresh Home-screen Widget", v -> {\n',
    '            card.addView(text(\n'
    '                    "When on, signed-in widgets show the connected forum identity as @username next to the cached notification state. No username is shown while signed out.",\n'
    '                    10, getColor(R.color.hcf_muted)));\n\n'
    '            Switch showUnread = target(toggle("Show unread count",\n'
    '                    prefs.getBoolean(HcfWidget.PREF_SHOW_UNREAD_COUNT, true)), "widget_show_unread_count");\n'
    '            showUnread.setOnCheckedChangeListener((button, checked) -> {\n'
    '                prefs.edit().putBoolean(HcfWidget.PREF_SHOW_UNREAD_COUNT, checked).apply();\n'
    '                HcfWidget.refreshAll(this);\n'
    '                AppLogger.info(this, "setting_widget_unread_count", checked ? "shown" : "hidden");\n'
    '            });\n'
    '            card.addView(showUnread);\n\n'
    '            Switch compactWidget = target(toggle("Compact widget mode",\n'
    '                    prefs.getBoolean(HcfWidget.PREF_COMPACT_MODE, false)), "widget_compact_mode");\n'
    '            compactWidget.setOnCheckedChangeListener((button, checked) -> {\n'
    '                prefs.edit().putBoolean(HcfWidget.PREF_COMPACT_MODE, checked).apply();\n'
    '                HcfWidget.refreshAll(this);\n'
    '                AppLogger.info(this, "setting_widget_compact_mode", checked ? "on" : "off");\n'
    '            });\n'
    '            card.addView(compactWidget);\n'
    '            card.addView(text(\n'
    '                    "Compact mode hides the large widget logo and title so the connected identity/status gets more room. Quick actions stay available.",\n'
    '                    10, getColor(R.color.hcf_muted)));\n\n'
    '            Switch showUpdated = target(toggle("Show last updated time",\n'
    '                    prefs.getBoolean(HcfWidget.PREF_SHOW_LAST_UPDATED, false)), "widget_show_last_updated");\n'
    '            showUpdated.setOnCheckedChangeListener((button, checked) -> {\n'
    '                prefs.edit().putBoolean(HcfWidget.PREF_SHOW_LAST_UPDATED, checked).apply();\n'
    '                HcfWidget.refreshAll(this);\n'
    '                AppLogger.info(this, "setting_widget_last_updated", checked ? "shown" : "hidden");\n'
    '            });\n'
    '            card.addView(showUpdated);\n\n'
    '            final String currentTap = prefs.getString(HcfWidget.PREF_DEFAULT_TAP_ACTION, HcfWidget.TAP_FORUM);\n'
    '            final String currentTapLabel = HcfWidget.TAP_NOTIFICATIONS.equals(currentTap)\n'
    '                    ? "Notifications" : HcfWidget.TAP_SETTINGS.equals(currentTap) ? "App Settings" : "Forum";\n'
    '            Button defaultTap = target(actionButton("Default widget tap: " + currentTapLabel, null),\n'
    '                    "widget_default_tap_action");\n'
    '            defaultTap.setOnClickListener(v -> {\n'
    '                final String[] labels = {"Forum", "Notifications", "App Settings"};\n'
    '                String savedTap = prefs.getString(HcfWidget.PREF_DEFAULT_TAP_ACTION, HcfWidget.TAP_FORUM);\n'
    '                int selected = HcfWidget.TAP_NOTIFICATIONS.equals(savedTap) ? 1\n'
    '                        : HcfWidget.TAP_SETTINGS.equals(savedTap) ? 2 : 0;\n'
    '                new AlertDialog.Builder(this)\n'
    '                        .setTitle("Default widget tap")\n'
    '                        .setSingleChoiceItems(labels, selected, (dialog, which) -> {\n'
    '                            String value = which == 1 ? HcfWidget.TAP_NOTIFICATIONS\n'
    '                                    : which == 2 ? HcfWidget.TAP_SETTINGS : HcfWidget.TAP_FORUM;\n'
    '                            prefs.edit().putString(HcfWidget.PREF_DEFAULT_TAP_ACTION, value).apply();\n'
    '                            HcfWidget.refreshAll(this);\n'
    '                            defaultTap.setText("Default widget tap: " + labels[which]);\n'
    '                            AppLogger.info(this, "setting_widget_default_tap", value);\n'
    '                            dialog.dismiss();\n'
    '                        })\n'
    '                        .setNegativeButton("Cancel", null)\n'
    '                        .show();\n'
    '            });\n'
    '            card.addView(defaultTap);\n\n'
    '            card.addView(target(actionButton("Refresh Home-screen Widget", v -> {\n',
    'widget expanded settings UI'
)
write(UI, ui)

# Documentation.
readme = read(README)
readme = replace_once(
    readme,
    'App Settings includes a root-level **Home-screen Widget** category. **Follow HCF app theme** is enabled by default, so the widget explicitly uses HCF\'s resolved Light, Dark, or AMOLED palette instead of allowing the launcher/phone theme to override its colors. Turning the option off makes the widget follow Android\'s phone light/dark mode instead. **Show connected @username** is also enabled by default; when a signed-in forum identity is available, the widget shows `@username` beside the cached unread state. Identity, theme, and notification preference changes refresh existing widget instances automatically, and the category includes a manual **Refresh Home-screen Widget** action.',
    'App Settings includes a root-level **Home-screen Widget** category. **Follow HCF app theme** is enabled by default, so the widget explicitly uses HCF\'s resolved Light, Dark, or AMOLED palette instead of allowing the launcher/phone theme to override its colors. **Show connected @username** and **Show unread count** are enabled by default. Additional controls include **Compact widget mode**, **Show last updated time**, and a **Default widget tap** chooser for Forum, Notifications, or App Settings. Identity, theme, content, layout, and action preference changes refresh existing widget instances automatically, and the category includes a manual **Refresh Home-screen Widget** action.',
    'README expanded widget settings'
)
write(README, readme)

# Extend, never weaken, release checks for the new widget controls.
verify = read(VERIFY)
verify = replace_once(
    verify,
    'require("widget theme changes do not refresh widget", \'AppPrefs.APP_THEME.equals(key)\' in widget_source and \'AppPrefs.WIDGET_FOLLOW_APP_THEME.equals(key)\' in widget_source)\n',
    'require("widget theme changes do not refresh widget", \'AppPrefs.APP_THEME.equals(key)\' in widget_source and \'AppPrefs.WIDGET_FOLLOW_APP_THEME.equals(key)\' in widget_source)\n'
    'for widget_key in ("WIDGET_SHOW_CONNECTED_USERNAME", "WIDGET_SHOW_UNREAD_COUNT", "WIDGET_COMPACT_MODE", "WIDGET_SHOW_LAST_UPDATED", "WIDGET_DEFAULT_TAP_ACTION"):\n'
    '    require(f"widget setting preference missing: {widget_key}", widget_key in app_prefs)\n'
    'require("widget expanded settings UI missing", all(label in ui_source for label in ("Show unread count", "Compact widget mode", "Show last updated time", "Default widget tap")))\n'
    'require("widget last-updated view missing", \'widget_hcf_updated\' in widget_source and \'widget_hcf_updated\' in text(source / "res/layout/widget_hcf_notifications.xml"))\n'
    'require("widget default tap routing missing", \'bodyPendingIntent\' in widget_source and \'TAP_NOTIFICATIONS\' in widget_source and \'TAP_SETTINGS\' in widget_source)\n',
    'release verifier widget additions'
)
write(VERIFY, verify)

print('Expanded HCF widget settings patch applied.')
