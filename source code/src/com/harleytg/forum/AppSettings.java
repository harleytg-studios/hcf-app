package com.harleytg.forum.dev;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/** Central preference owner for the native app. */
final class AppSettings {
    static final String DND_OFF = "off";
    static final String DND_ON = "on";
    static final String DND_SCHEDULED = "scheduled";
    static final int DEFAULT_DND_START_MINUTE = 22 * 60;
    static final int DEFAULT_DND_END_MINUTE = 7 * 60;
    static final int HISTORY_LIMIT = 200;

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
    }

    static String dndMode(Context context) {
        String value = prefs(context).getString(AppPrefs.NOTIFICATION_DND_MODE, DND_OFF);
        if (DND_ON.equals(value) || DND_SCHEDULED.equals(value)) return value;
        return DND_OFF;
    }

    static void setDndMode(Context context, String mode) {
        String safe = DND_ON.equals(mode) || DND_SCHEDULED.equals(mode) ? mode : DND_OFF;
        prefs(context).edit().putString(AppPrefs.NOTIFICATION_DND_MODE, safe).apply();
    }

    static int dndStartMinute(Context context) {
        return clampMinute(prefs(context).getInt(AppPrefs.NOTIFICATION_DND_START_MINUTE, DEFAULT_DND_START_MINUTE));
    }

    static int dndEndMinute(Context context) {
        return clampMinute(prefs(context).getInt(AppPrefs.NOTIFICATION_DND_END_MINUTE, DEFAULT_DND_END_MINUTE));
    }

    static void setDndSchedule(Context context, int startMinute, int endMinute) {
        prefs(context).edit()
                .putInt(AppPrefs.NOTIFICATION_DND_START_MINUTE, clampMinute(startMinute))
                .putInt(AppPrefs.NOTIFICATION_DND_END_MINUTE, clampMinute(endMinute))
                .apply();
    }

    static boolean isDndActive(Context context) {
        String mode = dndMode(context);
        if (DND_ON.equals(mode)) return true;
        if (!DND_SCHEDULED.equals(mode)) return false;
        Calendar calendar = Calendar.getInstance();
        int now = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        int start = dndStartMinute(context);
        int end = dndEndMinute(context);
        if (start == end) return true;
        if (start < end) return now >= start && now < end;
        return now >= start || now < end;
    }

    static String dndLabel(Context context) {
        String mode = dndMode(context);
        if (DND_ON.equals(mode)) return "On";
        if (DND_SCHEDULED.equals(mode)) {
            return "Scheduled • " + timeLabel(dndStartMinute(context)) + "–" + timeLabel(dndEndMinute(context))
                    + (isDndActive(context) ? " • active" : "");
        }
        return "Off";
    }

    static void saveReplyDraft(Context context, String conversationId, String text, String url) {
        if (conversationId == null || !conversationId.matches("[0-9]+")) return;
        prefs(context).edit()
                .putString(AppPrefs.NOTIFICATION_REPLY_DRAFT_PREFIX + conversationId, text == null ? "" : text)
                .putString(AppPrefs.NOTIFICATION_REPLY_URL_PREFIX + conversationId, url == null ? "" : url)
                .putLong(AppPrefs.NOTIFICATION_REPLY_TIME_PREFIX + conversationId, System.currentTimeMillis())
                .apply();
    }

    static String replyDraft(Context context, String conversationId) {
        return prefs(context).getString(AppPrefs.NOTIFICATION_REPLY_DRAFT_PREFIX + conversationId, "");
    }

    static void clearReplyDraft(Context context, String conversationId) {
        prefs(context).edit()
                .remove(AppPrefs.NOTIFICATION_REPLY_DRAFT_PREFIX + conversationId)
                .remove(AppPrefs.NOTIFICATION_REPLY_URL_PREFIX + conversationId)
                .remove(AppPrefs.NOTIFICATION_REPLY_TIME_PREFIX + conversationId)
                .apply();
    }

    static String timeLabel(int minute) {
        int safe = clampMinute(minute);
        int hour = safe / 60;
        int min = safe % 60;
        return String.format(java.util.Locale.US, "%02d:%02d", hour, min);
    }

    private static int clampMinute(int value) {
        if (value < 0) return 0;
        if (value > 1439) return 1439;
        return value;
    }

    private AppSettings() {}
}

/** Compatibility key facade. Preference keys are owned here, not scattered across features. */
final class AppPrefs {
    static final String FILE = "hcf_app";
    static final String FALLBACK_UNTIL = "fallback_until";
    static final String NOTIFICATIONS_ENABLED = "notifications_enabled";
    static final String BACKGROUND_NOTIFICATION_SYNC = "background_notification_sync";
    static final String AUTO_FAILOVER = "auto_failover";
    static final String EXTERNAL_LINKS = "external_links";
    static final String SAFE_LINKS_SEEN_DOMAINS = "safe_links_seen_domains";
    static final String SHOW_URL_BAR = "show_url_bar";
    static final String COMPACT_HEADER = "compact_header";
    static final String SHOW_BOTTOM_NAV = "show_bottom_nav";
    static final String UI_REVAMP_VERSION = "ui_revamp_version";
    static final String SHOW_STARTUP_SCREEN = "show_startup_screen";
    static final String APP_THEME = "app_theme";
    static final String LIVE_FORUM_UPDATES = "live_forum_updates";
    static final String PERFORMANCE_MODE = "performance_mode";
    static final String PERFORMANCE_PROFILE = "performance_profile";
    static final String NATIVE_ACCENT = "native_accent";
    static final String NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked";
    static final String NOTIFICATION_PERMISSION_PROMPT_VERSION = "notification_permission_prompt_version";
    static final String PERMISSION_ONBOARDING_DONE = "permission_onboarding_done";
    static final String INSTALL_PERMISSION_PROMPTED = "install_permission_prompted";
    static final String APP_HAS_LAUNCHED = "app_has_launched";
    static final String LAST_SEEN_WHATS_NEW_VERSION = "last_seen_whats_new_version";
    static final String LAST_MAIN_PAUSED_AT = "last_main_paused_at";
    static final String LAST_RECOVERABLE_URL = "last_recoverable_url";
    static final String IDENTITY_LOGGED_IN = "identity_logged_in";
    static final String IDENTITY_USER_ID = "identity_user_id";
    static final String IDENTITY_USERNAME = "identity_username";
    static final String IDENTITY_SLUG = "identity_slug";
    static final String IDENTITY_DISPLAY_NAME = "identity_display_name";
    static final String IDENTITY_EMAIL = "identity_email";
    static final String IDENTITY_EMAIL_CONFIRMED = "identity_email_confirmed";
    static final String IDENTITY_AVATAR_URL = "identity_avatar_url";
    static final String IDENTITY_GROUPS = "identity_groups";
    static final String IDENTITY_CONNECTIONS = "identity_connections";
    static final String IDENTITY_ADMIN = "identity_admin";
    static final String IDENTITY_JOIN_TIME = "identity_join_time";
    static final String IDENTITY_LAST_SEEN_AT = "identity_last_seen_at";
    static final String IDENTITY_UNREAD_NOTIFICATIONS = "identity_unread_notifications";
    static final String IDENTITY_NEW_NOTIFICATIONS = "identity_new_notifications";
    static final String IDENTITY_DISCUSSION_COUNT = "identity_discussion_count";
    static final String IDENTITY_COMMENT_COUNT = "identity_comment_count";
    static final String IDENTITY_HOST = "identity_host";
    static final String IDENTITY_SYNCED_AT = "identity_synced_at";
    static final String IDENTITY_SECURITY_SEEN = "identity_security_seen";
    static final String IDENTITY_SECURITY_SESSION_COUNT = "identity_security_session_count";
    static final String IDENTITY_SECURITY_ACTIVE_SESSION_COUNT = "identity_security_active_session_count";
    static final String IDENTITY_SECURITY_PROVIDERS = "identity_security_providers";
    static final String IDENTITY_SECURITY_PASSWORD_CONTROLS = "identity_security_password_controls";
    static final String IDENTITY_SECURITY_EMAIL_CONTROLS = "identity_security_email_controls";
    static final String IDENTITY_SECURITY_TWO_FACTOR_CONTROLS = "identity_security_two_factor_controls";
    static final String IDENTITY_SECURITY_HOST = "identity_security_host";
    static final String IDENTITY_SECURITY_PATH = "identity_security_path";
    static final String IDENTITY_SECURITY_SYNCED_AT = "identity_security_synced_at";
    static final String SESSION_USER_ID = "session_user_id";
    static final String ACTIVE_HOST = "active_host";
    static final String LAST_NOTIFICATION_COUNT = "last_notification_count";
    static final String DELIVERED_NOTIFICATION_IDS = "delivered_notification_ids";
    static final String NOTIFICATION_LAST_SYNC_AT = "notification_last_sync_at";
    static final String NOTIFICATION_LAST_SYNC_STATUS = "notification_last_sync_status";
    static final String NOTIFICATION_LAST_SYNC_LATENCY_MS = "notification_last_sync_latency_ms";
    static final String NOTIFICATION_LAST_COUNT_CHANGE_AT = "notification_last_count_change_at";
    static final String NOTIFICATION_LAST_RECEIVED_AT = "notification_last_received_at";
    static final String NOTIFICATION_LAST_ERROR = "notification_last_error";
    static final String NOTIFICATION_HISTORY_JSON = "notification_history_json";
    static final String NOTIFICATION_RECENT_IDS = "notification_recent_ids";
    static final String NOTIFICATION_DND_MODE = "notification_dnd_mode";
    static final String NOTIFICATION_DND_START_MINUTE = "notification_dnd_start_minute";
    static final String NOTIFICATION_DND_END_MINUTE = "notification_dnd_end_minute";
    static final String NOTIFICATION_REPLY_DRAFT_PREFIX = "notification_reply_draft_";
    static final String NOTIFICATION_REPLY_URL_PREFIX = "notification_reply_url_";
    static final String NOTIFICATION_REPLY_TIME_PREFIX = "notification_reply_time_";
    static final String RENDERER_RECOVERY_COUNT = "renderer_recovery_count";
    static final String FIREBASE_CONFIG_URL = "firebase_config_url";
    static final String FIREBASE_CONFIG_CACHE = "firebase_config_cache";
    static final String FIREBASE_CONFIG_SOURCE = "firebase_config_source";
    static final String UPDATE_CHANNEL = "update_channel";
    static final String UPDATE_AUTO_CHECK = "update_auto_check";
    static final String UPDATE_LAST_CHECK = "update_last_check";
    static final String UPDATE_AUTO_DOWNLOAD = "update_auto_download";
    static final String UPDATE_AUTO_INSTALL = "update_auto_install";
    static final String UPDATE_LAST_AVAILABLE_TAG = "update_last_available_tag";
    static final String UPDATE_DOWNLOAD_ID = "update_download_id";
    static final String UPDATE_DOWNLOAD_TAG = "update_download_tag";
    static final String UPDATE_DOWNLOAD_NAME = "update_download_name";
    static final String UPDATE_INSTALL_PENDING = "update_install_pending";
    static final String UPDATE_RESUME_AFTER_PERMISSION = "update_resume_after_permission";
    static final String TELEMETRY_ENABLED = "telemetry_enabled";
    static final String TELEMETRY_LEVEL = "telemetry_level";
    static final String TELEMETRY_AUTO_CRASH_REPORTS = "telemetry_auto_crash_reports";
    static final String TELEMETRY_ASK_BEFORE_CRASH_REPORT = "telemetry_ask_before_crash_report";
    static final String TELEMETRY_AUTO_ERROR_REPORTS = "telemetry_auto_error_reports";
    static final String TELEMETRY_INCLUDE_IDENTITY = "telemetry_include_identity";
    static final String TELEMETRY_INCLUDE_EMAIL = "telemetry_include_email";
    static final String TELEMETRY_INCLUDE_DEVICE_MODEL = "telemetry_include_device_model";
    static final String TELEMETRY_INCLUDE_ROUTE = "telemetry_include_route";
    static final String TELEMETRY_LAST_ROUTE = "telemetry_last_route";
    static final String TELEMETRY_BREADCRUMBS = "telemetry_breadcrumbs";
    static final String TELEMETRY_REPORT_HISTORY = "telemetry_report_history";
    static final String TELEMETRY_PENDING_CRASH_ID = "telemetry_pending_crash_id";
    static final String TELEMETRY_LAST_HEARTBEAT = "telemetry_last_heartbeat";
    static final String TELEMETRY_LAST_RESULT = "telemetry_last_result";

    private AppPrefs() {}
}
