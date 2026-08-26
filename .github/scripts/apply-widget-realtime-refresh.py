#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')
NOTIFICATIONS = ROOT / 'source code/src/com/harleytg/forum/HcfNotifications.java'
WIDGET = ROOT / 'source code/src/com/harleytg/forum/HcfWidget.java'
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

# Widget: track the actual completed notification-sync time and refresh on that pref.
widget = read(WIDGET)
widget = replace_once(
    widget,
    '    public static final String PREF_DEFAULT_TAP_ACTION = AppPrefs.WIDGET_DEFAULT_TAP_ACTION;\n',
    '    public static final String PREF_DEFAULT_TAP_ACTION = AppPrefs.WIDGET_DEFAULT_TAP_ACTION;\n'
    '    public static final String PREF_LAST_REALTIME_SYNC_MS = "widget_last_realtime_sync_ms";\n',
    'widget realtime timestamp preference'
)
widget = replace_once(
    widget,
    '                        || PREF_DEFAULT_TAP_ACTION.equals(key)\n'
    '                        || AppPrefs.WIDGET_FOLLOW_APP_THEME.equals(key)\n',
    '                        || PREF_DEFAULT_TAP_ACTION.equals(key)\n'
    '                        || PREF_LAST_REALTIME_SYNC_MS.equals(key)\n'
    '                        || AppPrefs.WIDGET_FOLLOW_APP_THEME.equals(key)\n',
    'widget realtime preference listener'
)
widget = replace_once(
    widget,
    '        boolean showLastUpdated = prefs.getBoolean(PREF_SHOW_LAST_UPDATED, false);\n'
    '        String username = prefs.getString(AppPrefs.IDENTITY_USERNAME, "");\n',
    '        boolean showLastUpdated = prefs.getBoolean(PREF_SHOW_LAST_UPDATED, false);\n'
    '        long lastRealtimeSyncMs = Math.max(0L, prefs.getLong(PREF_LAST_REALTIME_SYNC_MS, 0L));\n'
    '        String username = prefs.getString(AppPrefs.IDENTITY_USERNAME, "");\n',
    'widget realtime timestamp read'
)
widget = replace_once(
    widget,
    '        String updatedText = "Updated "\n'
    '                + android.text.format.DateFormat.getTimeFormat(context)\n'
    '                .format(new java.util.Date());\n',
    '        String updatedText = lastRealtimeSyncMs > 0L\n'
    '                ? "Synced " + android.text.format.DateFormat.getTimeFormat(context)\n'
    '                .format(new java.util.Date(lastRealtimeSyncMs))\n'
    '                : "Waiting for live sync";\n',
    'widget actual sync timestamp label'
)
write(WIDGET, widget)

# Notification engine: after every successful forum notification sync, stamp + redraw widget.
notifications = read(NOTIFICATIONS)
notifications = replace_once(
    notifications,
    '    private HcfNotifications() {}\n',
    '    private HcfNotifications() {}\n\n'
    '    /** Redraw placed widgets immediately after a successful forum notification sync. */\n'
    '    private static void refreshWidgetAfterNotificationSync(Context context, String source) {\n'
    '        if (context == null) return;\n'
    '        try {\n'
    '            context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE).edit()\n'
    '                    .putLong(HcfWidget.PREF_LAST_REALTIME_SYNC_MS, System.currentTimeMillis())\n'
    '                    .apply();\n'
    '            HcfWidget.refreshAll(context);\n'
    '            AppLogger.info(context, "widget_live_refresh", source == null ? "sync" : source);\n'
    '        } catch (Throwable error) {\n'
    '            try { AppLogger.warn(context, "widget_live_refresh", error.getClass().getSimpleName()); }\n'
    '            catch (Throwable ignored) {}\n'
    '        }\n'
    '    }\n',
    'widget realtime refresh helper'
)
notifications = replace_once(
    notifications,
    '                ForumNotificationSync.perform(context, host, userId.trim(), "silent-one-shot");\n',
    '                ForumNotificationSync.perform(context, host, userId.trim(), "silent-one-shot");\n'
    '                refreshWidgetAfterNotificationSync(context, "silent-one-shot");\n',
    'one-shot widget refresh'
)
notifications = replace_once(
    notifications,
    '                    ForumNotificationSync.perform(this, host, userId.trim(), "adaptive");\n'
    '                    failures = 0;\n',
    '                    ForumNotificationSync.perform(this, host, userId.trim(), "adaptive");\n'
    '                    refreshWidgetAfterNotificationSync(this, "adaptive");\n'
    '                    failures = 0;\n',
    'adaptive widget refresh'
)
notifications = replace_once(
    notifications,
    '                ForumNotificationSync.perform(this, ForumUrlRouter.isForumHost(host) ? host : "forum.harleytg.com", userId.trim(), "fallback-job");\n',
    '                ForumNotificationSync.perform(this, ForumUrlRouter.isForumHost(host) ? host : "forum.harleytg.com", userId.trim(), "fallback-job");\n'
    '                refreshWidgetAfterNotificationSync(this, "fallback-job");\n',
    'fallback job widget refresh'
)
write(NOTIFICATIONS, notifications)

# Release verifier: lock the real-time sync wiring into the release gate.
verify = read(VERIFY)
verify = replace_once(
    verify,
    'require("widget default tap routing missing", \'bodyPendingIntent\' in widget_source and \'TAP_NOTIFICATIONS\' in widget_source and \'TAP_SETTINGS\' in widget_source)\n',
    'require("widget default tap routing missing", \'bodyPendingIntent\' in widget_source and \'TAP_NOTIFICATIONS\' in widget_source and \'TAP_SETTINGS\' in widget_source)\n'
    'require("widget real-time sync timestamp missing", \'PREF_LAST_REALTIME_SYNC_MS\' in widget_source and \'Synced \' in widget_source)\n'
    'require("widget real-time notification refresh helper missing", \'refreshWidgetAfterNotificationSync\' in notifications_source and \'HcfWidget.refreshAll(context)\' in notifications_source)\n'
    'require("adaptive sync does not redraw widget", \'refreshWidgetAfterNotificationSync(this, "adaptive")\' in notifications_source)\n'
    'require("one-shot sync does not redraw widget", \'refreshWidgetAfterNotificationSync(context, "silent-one-shot")\' in notifications_source)\n'
    'require("fallback job sync does not redraw widget", \'refreshWidgetAfterNotificationSync(this, "fallback-job")\' in notifications_source)\n',
    'realtime release checks'
)
write(VERIFY, verify)

readme = read(README)
readme = replace_once(
    readme,
    'Identity, theme, content, layout, and action preference changes refresh existing widget instances automatically, and the category includes a manual **Refresh Home-screen Widget** action. The user-selected widget controls are included in HCF settings transfer/backup data.',
    'Identity, theme, content, layout, and action preference changes refresh existing widget instances automatically, and the category includes a manual **Refresh Home-screen Widget** action. While HCF live/background notification sync is active, every successful forum notification sync now immediately redraws placed widgets and records the actual sync time, so widget state tracks the existing adaptive notification loop instead of waiting for the launcher periodic update. The launcher schedule remains only a fallback. The user-selected widget controls are included in HCF settings transfer/backup data.',
    'README realtime widget description'
)
write(README, readme)

print('Near-real-time HCF widget refresh patch applied.')
