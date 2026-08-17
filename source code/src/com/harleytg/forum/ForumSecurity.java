package com.harleytg.forum;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Safe summary of the CURRENT signed-in user's /u/{username}/security page.
 *
 * The app intentionally stores only counts / capability flags / provider labels.
 * It never stores access-token strings, cookie values, session secrets, recovery
 * codes, provider account IDs, passwords, or the text of individual sessions.
 */
final class ForumSecurity {
    static final class Snapshot {
        final boolean seen;
        final int sessionCount;
        final int activeSessionCount;
        final String providers;
        final boolean passwordControls;
        final boolean emailControls;
        final boolean twoFactorControls;
        final String host;
        final String path;
        final long syncedAt;

        Snapshot(boolean seen, int sessionCount, int activeSessionCount, String providers,
                 boolean passwordControls, boolean emailControls, boolean twoFactorControls,
                 String host, String path, long syncedAt) {
            this.seen = seen;
            this.sessionCount = Math.max(0, sessionCount);
            this.activeSessionCount = Math.max(0, activeSessionCount);
            this.providers = safe(providers);
            this.passwordControls = passwordControls;
            this.emailControls = emailControls;
            this.twoFactorControls = twoFactorControls;
            this.host = safe(host);
            this.path = safe(path);
            this.syncedAt = syncedAt;
        }

        String sessionLabel() {
            if (!seen) return "Open Account Security to sync";
            if (sessionCount <= 0) return "No session list detected";
            return Integer.toString(sessionCount);
        }

        String currentSessionLabel() {
            if (!seen) return "Not synced yet";
            return activeSessionCount > 0 ? "Current app/browser session detected" : "Not identified";
        }
    }

    static Snapshot fromBridgeJson(String json, String host) throws Exception {
        JSONObject o = new JSONObject(json == null ? "{}" : json);
        return new Snapshot(
                o.optBoolean("seen", true),
                o.optInt("sessionCount", 0),
                o.optInt("activeSessionCount", 0),
                joinUnique(o.optJSONArray("providers")),
                o.optBoolean("passwordControls", false),
                o.optBoolean("emailControls", false),
                o.optBoolean("twoFactorControls", false),
                host,
                o.optString("path", ""),
                System.currentTimeMillis()
        );
    }

    static void save(Context context, Snapshot s) {
        if (context == null || s == null) return;
        context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE).edit()
                .putBoolean(AppPrefs.IDENTITY_SECURITY_SEEN, s.seen)
                .putInt(AppPrefs.IDENTITY_SECURITY_SESSION_COUNT, s.sessionCount)
                .putInt(AppPrefs.IDENTITY_SECURITY_ACTIVE_SESSION_COUNT, s.activeSessionCount)
                .putString(AppPrefs.IDENTITY_SECURITY_PROVIDERS, s.providers)
                .putBoolean(AppPrefs.IDENTITY_SECURITY_PASSWORD_CONTROLS, s.passwordControls)
                .putBoolean(AppPrefs.IDENTITY_SECURITY_EMAIL_CONTROLS, s.emailControls)
                .putBoolean(AppPrefs.IDENTITY_SECURITY_TWO_FACTOR_CONTROLS, s.twoFactorControls)
                .putString(AppPrefs.IDENTITY_SECURITY_HOST, s.host)
                .putString(AppPrefs.IDENTITY_SECURITY_PATH, s.path)
                .putLong(AppPrefs.IDENTITY_SECURITY_SYNCED_AT, s.syncedAt)
                .apply();
    }

    static Snapshot load(Context context) {
        if (context == null) return empty(ForumConfig.PRIMARY_HOST);
        SharedPreferences p = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        return new Snapshot(
                p.getBoolean(AppPrefs.IDENTITY_SECURITY_SEEN, false),
                p.getInt(AppPrefs.IDENTITY_SECURITY_SESSION_COUNT, 0),
                p.getInt(AppPrefs.IDENTITY_SECURITY_ACTIVE_SESSION_COUNT, 0),
                p.getString(AppPrefs.IDENTITY_SECURITY_PROVIDERS, ""),
                p.getBoolean(AppPrefs.IDENTITY_SECURITY_PASSWORD_CONTROLS, false),
                p.getBoolean(AppPrefs.IDENTITY_SECURITY_EMAIL_CONTROLS, false),
                p.getBoolean(AppPrefs.IDENTITY_SECURITY_TWO_FACTOR_CONTROLS, false),
                p.getString(AppPrefs.IDENTITY_SECURITY_HOST, ForumConfig.PRIMARY_HOST),
                p.getString(AppPrefs.IDENTITY_SECURITY_PATH, ""),
                p.getLong(AppPrefs.IDENTITY_SECURITY_SYNCED_AT, 0L)
        );
    }

    static Snapshot empty(String host) {
        return new Snapshot(false, 0, 0, "", false, false, false, host, "", 0L);
    }

    static void clear(Context context) {
        if (context == null) return;
        context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE).edit()
                .remove(AppPrefs.IDENTITY_SECURITY_SEEN)
                .remove(AppPrefs.IDENTITY_SECURITY_SESSION_COUNT)
                .remove(AppPrefs.IDENTITY_SECURITY_ACTIVE_SESSION_COUNT)
                .remove(AppPrefs.IDENTITY_SECURITY_PROVIDERS)
                .remove(AppPrefs.IDENTITY_SECURITY_PASSWORD_CONTROLS)
                .remove(AppPrefs.IDENTITY_SECURITY_EMAIL_CONTROLS)
                .remove(AppPrefs.IDENTITY_SECURITY_TWO_FACTOR_CONTROLS)
                .remove(AppPrefs.IDENTITY_SECURITY_HOST)
                .remove(AppPrefs.IDENTITY_SECURITY_PATH)
                .remove(AppPrefs.IDENTITY_SECURITY_SYNCED_AT)
                .apply();
    }

    static String mergeLabels(String first, String second) {
        List<String> names = new ArrayList<>();
        addCsv(names, first);
        addCsv(names, second);
        StringBuilder out = new StringBuilder();
        for (String name : names) {
            if (out.length() > 0) out.append(", ");
            out.append(name);
        }
        return out.toString();
    }

    private static void addCsv(List<String> out, String raw) {
        if (raw == null) return;
        for (String part : raw.split(",")) {
            String v = part == null ? "" : part.trim();
            if (!v.isEmpty() && !out.contains(v)) out.add(v);
        }
    }

    private static String joinUnique(JSONArray array) {
        List<String> names = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "").trim();
                if (!value.isEmpty() && !names.contains(value)) names.add(value);
            }
        }
        StringBuilder joined = new StringBuilder();
        for (String name : names) {
            if (joined.length() > 0) joined.append(", ");
            joined.append(name);
        }
        return joined.toString();
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private ForumSecurity() {}
}
