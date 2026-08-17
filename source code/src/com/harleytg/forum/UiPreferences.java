package com.harleytg.forum.dev;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

/** One-time defaults/migrations and preference repair for the v0.4 app shell. */
final class UiPreferences {
    private static final int CURRENT_REVAMP = 3;

    static void migrate(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);

        // Repair stale/malformed values before any typed SharedPreferences getter is used.
        // Android throws ClassCastException when a key exists with the wrong stored type,
        // which previously could make Diagnostics, Logs, or app startup crash after an update.
        sanitizePreferenceTypes(prefs);

        int rev = 0;
        try { rev = prefs.getInt(AppPrefs.UI_REVAMP_VERSION, 0); }
        catch (Throwable ignored) {
            prefs.edit().remove(AppPrefs.UI_REVAMP_VERSION).apply();
        }
        if (rev >= CURRENT_REVAMP) return;

        // Preserve the compact native header and no-bottom-nav layout while advancing
        // the migration marker for the diagnostics/title-case stability pass.
        SharedPreferences.Editor edit = prefs.edit()
                .putBoolean(AppPrefs.COMPACT_HEADER, true)
                .putBoolean(AppPrefs.SHOW_BOTTOM_NAV, false)
                .putInt(AppPrefs.UI_REVAMP_VERSION, CURRENT_REVAMP);
        if (!prefs.contains(AppPrefs.SHOW_URL_BAR)) edit.putBoolean(AppPrefs.SHOW_URL_BAR, false);
        edit.apply();
    }

    private static void sanitizePreferenceTypes(SharedPreferences prefs) {
        final Map<String, ?> all;
        try { all = prefs.getAll(); }
        catch (Throwable ignored) { return; }

        SharedPreferences.Editor edit = prefs.edit();
        boolean changed = false;

        String[] boolKeys = new String[]{
                AppPrefs.NOTIFICATIONS_ENABLED,
                AppPrefs.BACKGROUND_NOTIFICATION_SYNC,
                AppPrefs.AUTO_FAILOVER,
                AppPrefs.EXTERNAL_LINKS,
                AppPrefs.SHOW_URL_BAR,
                AppPrefs.COMPACT_HEADER,
                AppPrefs.SHOW_BOTTOM_NAV,
                AppPrefs.SHOW_STARTUP_SCREEN,
                AppPrefs.LIVE_FORUM_UPDATES,
                AppPrefs.PERFORMANCE_MODE,
                AppPrefs.NOTIFICATION_PERMISSION_ASKED,
                AppPrefs.PERMISSION_ONBOARDING_DONE,
                AppPrefs.INSTALL_PERMISSION_PROMPTED,
                AppPrefs.APP_HAS_LAUNCHED,
                AppPrefs.UPDATE_AUTO_CHECK,
                AppPrefs.UPDATE_AUTO_DOWNLOAD,
                AppPrefs.UPDATE_INSTALL_PENDING,
                AppPrefs.UPDATE_RESUME_AFTER_PERMISSION,
                AppPrefs.TELEMETRY_ENABLED,
                AppPrefs.TELEMETRY_AUTO_CRASH_REPORTS,
                AppPrefs.TELEMETRY_ASK_BEFORE_CRASH_REPORT,
                AppPrefs.TELEMETRY_AUTO_ERROR_REPORTS,
                AppPrefs.TELEMETRY_INCLUDE_IDENTITY,
                AppPrefs.TELEMETRY_INCLUDE_EMAIL,
                AppPrefs.TELEMETRY_INCLUDE_DEVICE_MODEL,
                AppPrefs.TELEMETRY_INCLUDE_ROUTE,
                AppPrefs.IDENTITY_LOGGED_IN,
                AppPrefs.IDENTITY_EMAIL_CONFIRMED,
                AppPrefs.IDENTITY_ADMIN,
                AppPrefs.IDENTITY_SECURITY_SEEN,
                AppPrefs.IDENTITY_SECURITY_PASSWORD_CONTROLS,
                AppPrefs.IDENTITY_SECURITY_EMAIL_CONTROLS,
                AppPrefs.IDENTITY_SECURITY_TWO_FACTOR_CONTROLS
        };
        for (String key : boolKeys) changed |= removeIfWrongType(all, edit, key, Boolean.class);

        String[] stringKeys = new String[]{
                AppPrefs.SAFE_LINKS_SEEN_DOMAINS,
                AppPrefs.APP_THEME,
                AppPrefs.PERFORMANCE_PROFILE,
                AppPrefs.NATIVE_ACCENT,
                AppPrefs.LAST_RECOVERABLE_URL,
                AppPrefs.LAST_SEEN_WHATS_NEW_VERSION,
                AppPrefs.SESSION_USER_ID,
                AppPrefs.ACTIVE_HOST,
                AppPrefs.DELIVERED_NOTIFICATION_IDS,
                AppPrefs.NOTIFICATION_LAST_SYNC_STATUS,
                AppPrefs.UPDATE_CHANNEL,
                AppPrefs.UPDATE_LAST_AVAILABLE_TAG,
                AppPrefs.UPDATE_DOWNLOAD_TAG,
                AppPrefs.UPDATE_DOWNLOAD_NAME,
                AppPrefs.TELEMETRY_LEVEL,
                AppPrefs.TELEMETRY_LAST_ROUTE,
                AppPrefs.TELEMETRY_BREADCRUMBS,
                AppPrefs.TELEMETRY_REPORT_HISTORY,
                AppPrefs.TELEMETRY_PENDING_CRASH_ID,
                AppPrefs.TELEMETRY_LAST_RESULT,
                AppPrefs.IDENTITY_USER_ID,
                AppPrefs.IDENTITY_USERNAME,
                AppPrefs.IDENTITY_SLUG,
                AppPrefs.IDENTITY_DISPLAY_NAME,
                AppPrefs.IDENTITY_EMAIL,
                AppPrefs.IDENTITY_AVATAR_URL,
                AppPrefs.IDENTITY_GROUPS,
                AppPrefs.IDENTITY_CONNECTIONS,
                AppPrefs.IDENTITY_JOIN_TIME,
                AppPrefs.IDENTITY_LAST_SEEN_AT,
                AppPrefs.IDENTITY_HOST,
                AppPrefs.IDENTITY_SECURITY_PROVIDERS,
                AppPrefs.IDENTITY_SECURITY_HOST,
                AppPrefs.IDENTITY_SECURITY_PATH
        };
        for (String key : stringKeys) changed |= removeIfWrongType(all, edit, key, String.class);

        String[] longKeys = new String[]{
                AppPrefs.FALLBACK_UNTIL,
                AppPrefs.LAST_MAIN_PAUSED_AT,
                AppPrefs.NOTIFICATION_LAST_SYNC_AT,
                AppPrefs.NOTIFICATION_LAST_SYNC_LATENCY_MS,
                AppPrefs.UPDATE_LAST_CHECK,
                AppPrefs.UPDATE_DOWNLOAD_ID,
                AppPrefs.TELEMETRY_LAST_HEARTBEAT,
                AppPrefs.IDENTITY_SYNCED_AT,
                AppPrefs.IDENTITY_SECURITY_SYNCED_AT
        };
        for (String key : longKeys) changed |= removeIfWrongType(all, edit, key, Long.class);

        String[] intKeys = new String[]{
                AppPrefs.UI_REVAMP_VERSION,
                AppPrefs.NOTIFICATION_PERMISSION_PROMPT_VERSION,
                AppPrefs.LAST_NOTIFICATION_COUNT,
                AppPrefs.IDENTITY_UNREAD_NOTIFICATIONS,
                AppPrefs.IDENTITY_NEW_NOTIFICATIONS,
                AppPrefs.IDENTITY_DISCUSSION_COUNT,
                AppPrefs.IDENTITY_COMMENT_COUNT,
                AppPrefs.IDENTITY_SECURITY_SESSION_COUNT,
                AppPrefs.IDENTITY_SECURITY_ACTIVE_SESSION_COUNT
        };
        for (String key : intKeys) changed |= removeIfWrongType(all, edit, key, Integer.class);

        if (changed) edit.apply();
    }

    private static boolean removeIfWrongType(Map<String, ?> all, SharedPreferences.Editor edit,
                                             String key, Class<?> expected) {
        if (!all.containsKey(key)) return false;
        Object value = all.get(key);
        if (value == null || expected.isInstance(value)) return false;
        edit.remove(key);
        return true;
    }

    private UiPreferences() {}
}
