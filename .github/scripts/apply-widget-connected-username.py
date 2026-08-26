#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')
UI = ROOT / 'source code/src/com/harleytg/forum/HcfUI.java'
WIDGET = ROOT / 'source code/src/com/harleytg/forum/HcfWidget.java'


def read(path):
    return path.read_text(encoding='utf-8')


def write(path, text):
    path.write_text(text, encoding='utf-8')


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, found {count}')
    return text.replace(old, new, 1)

# Widget runtime: preference, live identity refresh, and @username status prefix.
widget = read(WIDGET)
widget = replace_once(
    widget,
    '    public static final String TARGET_NOTIFICATIONS = "notifications";\n',
    '    public static final String TARGET_NOTIFICATIONS = "notifications";\n\n'
    '    /** User setting: show the connected forum identity in the widget status line. */\n'
    '    public static final String PREF_SHOW_CONNECTED_USERNAME = "widget_show_connected_username";\n',
    'widget preference constant'
)
widget = replace_once(
    widget,
    '                        || AppPrefs.SESSION_USER_ID.equals(key)\n'
    '                        || AppPrefs.WIDGET_FOLLOW_APP_THEME.equals(key)\n',
    '                        || AppPrefs.SESSION_USER_ID.equals(key)\n'
    '                        || AppPrefs.IDENTITY_USERNAME.equals(key)\n'
    '                        || PREF_SHOW_CONNECTED_USERNAME.equals(key)\n'
    '                        || AppPrefs.WIDGET_FOLLOW_APP_THEME.equals(key)\n',
    'widget preference refresh listener'
)
widget = replace_once(
    widget,
    '        int unreadCount = Math.max(0, prefs.getInt(AppPrefs.LAST_NOTIFICATION_COUNT, 0));\n\n'
    '        CharSequence status;\n'
    '        if (!signedIn) {\n'
    '            status = context.getString(R.string.widget_hcf_signed_out);\n'
    '        } else if (unreadCount == 0) {\n'
    '            status = context.getString(R.string.widget_hcf_no_notifications);\n'
    '        } else {\n'
    '            status = context.getString(R.string.widget_hcf_unread_count, unreadCount);\n'
    '        }\n',
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
    'widget connected username rendering'
)
write(WIDGET, widget)

# Root Settings UI: expose a user-controlled Show connected @username toggle.
ui = read(UI)
ui = replace_once(
    ui,
    '            addSettingsCategory(list, "Home-screen Widget", "Theme source and widget appearance", "widget");\n',
    '            addSettingsCategory(list, "Home-screen Widget", "Theme source, connected identity and widget appearance", "widget");\n',
    'widget category subtitle'
)
ui = replace_once(
    ui,
    '                new SettingTarget("widget_follow_app_theme", "Follow HCF app theme", "widget home screen theme app phone system light dark amoled", "widget", "widget_appearance"),\n'
    '                new SettingTarget("refresh_widget_now", "Refresh Home-screen Widget", "widget refresh reload home screen", "widget", "widget_appearance"),\n',
    '                new SettingTarget("widget_follow_app_theme", "Follow HCF app theme", "widget home screen theme app phone system light dark amoled", "widget", "widget_appearance"),\n'
    '                new SettingTarget("widget_show_connected_username", "Show connected @username", "widget account identity connected username handle profile", "widget", "widget_appearance"),\n'
    '                new SettingTarget("refresh_widget_now", "Refresh Home-screen Widget", "widget refresh reload home screen", "widget", "widget_appearance"),\n',
    'widget settings search target'
)
ui = replace_once(
    ui,
    '            card.addView(target(actionButton("Refresh Home-screen Widget", v -> {\n',
    '            final boolean showConnectedUsername = prefs.getBoolean(HcfWidget.PREF_SHOW_CONNECTED_USERNAME, true);\n'
    '            Switch connectedUsername = target(toggle("Show connected @username", showConnectedUsername), "widget_show_connected_username");\n'
    '            connectedUsername.setOnCheckedChangeListener((button, checked) -> {\n'
    '                prefs.edit().putBoolean(HcfWidget.PREF_SHOW_CONNECTED_USERNAME, checked).apply();\n'
    '                HcfWidget.refreshAll(this);\n'
    '                AppLogger.info(this, "setting_widget_connected_username", checked ? "shown" : "hidden");\n'
    '            });\n'
    '            card.addView(connectedUsername);\n'
    '            card.addView(text(\n'
    '                    "When on, signed-in widgets show the connected forum identity as @username next to the cached notification state. No username is shown while signed out.",\n'
    '                    10, getColor(R.color.hcf_muted)));\n\n'
    '            card.addView(target(actionButton("Refresh Home-screen Widget", v -> {\n',
    'widget username setting control'
)
ui = replace_once(
    ui,
    '                AppPrefs.WIDGET_FOLLOW_APP_THEME,\n',
    '                AppPrefs.WIDGET_FOLLOW_APP_THEME,\n'
    '                HcfWidget.PREF_SHOW_CONNECTED_USERNAME,\n',
    'settings backup widget username key'
)
write(UI, ui)

print('Connected @username widget patch applied.')
