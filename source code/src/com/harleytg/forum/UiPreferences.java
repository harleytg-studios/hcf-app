package com.harleytg.forum;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;

/* loaded from: classes.dex */
final class UiPreferences {
    private static final int CURRENT_REVAMP = 4;

    static void migrate(Context context) {
        int i;
        if (context == null) {
            return;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("hcf_app", 0);
        sanitizePreferenceTypes(sharedPreferences);
        try {
            i = sharedPreferences.getInt("ui_revamp_version", 0);
        } catch (Throwable unused) {
            sharedPreferences.edit().remove("ui_revamp_version").apply();
            i = 0;
        }
        if (i >= CURRENT_REVAMP) {
            return;
        }
        sharedPreferences.edit().putBoolean("update_auto_download", false).putBoolean("show_url_bar", true).putInt("ui_revamp_version", CURRENT_REVAMP).apply();
    }

    private static void sanitizePreferenceTypes(SharedPreferences sharedPreferences) {
        try {
            Map<String, ?> all = sharedPreferences.getAll();
            SharedPreferences.Editor edit = sharedPreferences.edit();
            String[] strArr = {"notifications_enabled", "background_notification_sync", "auto_failover", "external_links", "show_url_bar", "compact_header", "show_bottom_nav", "show_startup_screen", "live_forum_updates", "performance_mode", "notification_permission_asked", "permission_onboarding_done", "install_permission_prompted", "app_has_launched", "update_auto_check", "update_auto_download", "update_install_pending", "update_resume_after_permission", "telemetry_enabled", "telemetry_auto_crash_reports", "telemetry_ask_before_crash_report", "telemetry_auto_error_reports", "telemetry_include_identity", "telemetry_include_email", "telemetry_include_device_model", "telemetry_include_route", "identity_logged_in", "identity_email_confirmed", "identity_admin", "identity_security_seen", "identity_security_password_controls", "identity_security_email_controls", "identity_security_two_factor_controls"};
            boolean z = false;
            for (int i = 0; i < 33; i++) {
                z |= removeIfWrongType(all, edit, strArr[i], Boolean.class);
            }
            String[] strArr2 = {"safe_links_seen_domains", "app_theme", "performance_profile", "native_accent", "last_recoverable_url", "last_seen_whats_new_version", "session_user_id", "active_host", "delivered_notification_ids", "notification_last_sync_status", "firebase_config_url", "firebase_config_cache", "firebase_config_source", "update_channel", "update_last_available_tag", "update_download_tag", "update_download_name", "telemetry_level", "telemetry_last_route", "telemetry_breadcrumbs", "telemetry_report_history", "telemetry_pending_crash_id", "telemetry_last_result", "identity_user_id", "identity_username", "identity_slug", "identity_display_name", "identity_email", "identity_avatar_url", "identity_groups", "identity_connections", "identity_join_time", "identity_last_seen_at", "identity_host", "identity_security_providers", "identity_security_host", "identity_security_path"};
            for (int i2 = 0; i2 < 37; i2++) {
                z |= removeIfWrongType(all, edit, strArr2[i2], String.class);
            }
            String[] strArr3 = {"fallback_until", "last_main_paused_at", "notification_last_sync_at", "notification_last_sync_latency_ms", "update_last_check", "update_download_id", "telemetry_last_heartbeat", "identity_synced_at", "identity_security_synced_at"};
            for (int i3 = 0; i3 < 9; i3++) {
                z |= removeIfWrongType(all, edit, strArr3[i3], Long.class);
            }
            String[] strArr4 = {"ui_revamp_version", "notification_permission_prompt_version", "last_notification_count", "identity_unread_notifications", "identity_new_notifications", "identity_discussion_count", "identity_comment_count", "identity_security_session_count", "identity_security_active_session_count"};
            for (int i4 = 0; i4 < 9; i4++) {
                z |= removeIfWrongType(all, edit, strArr4[i4], Integer.class);
            }
            if (z) {
                edit.apply();
            }
        } catch (Throwable unused) {
        }
    }

    private static boolean removeIfWrongType(Map<String, ?> map, SharedPreferences.Editor editor, String str, Class<?> cls) {
        Object obj;
        if (!map.containsKey(str) || (obj = map.get(str)) == null || cls.isInstance(obj)) {
            return false;
        }
        editor.remove(str);
        return true;
    }

    private UiPreferences() {
    }
}
