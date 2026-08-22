package com.harleytg.forum.dev;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/* loaded from: classes.dex */
final class ForumSecurity {

    static final class Snapshot {
        final int activeSessionCount;
        final boolean emailControls;
        final String host;
        final boolean passwordControls;
        final String path;
        final String providers;
        final boolean seen;
        final int sessionCount;
        final long syncedAt;
        final boolean twoFactorControls;

        Snapshot(boolean z, int i, int i2, String str, boolean z2, boolean z3, boolean z4, String str2, String str3, long j) {
            this.seen = z;
            this.sessionCount = Math.max(0, i);
            this.activeSessionCount = Math.max(0, i2);
            this.providers = ForumSecurity.safe(str);
            this.passwordControls = z2;
            this.emailControls = z3;
            this.twoFactorControls = z4;
            this.host = ForumSecurity.safe(str2);
            this.path = ForumSecurity.safe(str3);
            this.syncedAt = j;
        }

        String sessionLabel() {
            if (!this.seen) {
                return "Syncing automatically";
            }
            int i = this.sessionCount;
            return i <= 0 ? "No session list detected" : Integer.toString(i);
        }

        String currentSessionLabel() {
            return !this.seen ? "Waiting for automatic sync" : this.activeSessionCount > 0 ? "Current app/browser session detected" : "Not identified";
        }
    }

    static Snapshot fromBridgeJson(String str, String str2) throws Exception {
        if (str == null) {
            str = "{}";
        }
        JSONObject jSONObject = new JSONObject(str);
        return new Snapshot(jSONObject.optBoolean("seen", true), jSONObject.optInt("sessionCount", 0), jSONObject.optInt("activeSessionCount", 0), joinUnique(jSONObject.optJSONArray("providers")), jSONObject.optBoolean("passwordControls", false), jSONObject.optBoolean("emailControls", false), jSONObject.optBoolean("twoFactorControls", false), str2, jSONObject.optString("path", ""), System.currentTimeMillis());
    }

    static void save(Context context, Snapshot snapshot) {
        if (context == null || snapshot == null) {
            return;
        }
        context.getSharedPreferences("hcf_app", 0).edit().putBoolean("identity_security_seen", snapshot.seen).putInt("identity_security_session_count", snapshot.sessionCount).putInt("identity_security_active_session_count", snapshot.activeSessionCount).putString("identity_security_providers", snapshot.providers).putBoolean("identity_security_password_controls", snapshot.passwordControls).putBoolean("identity_security_email_controls", snapshot.emailControls).putBoolean("identity_security_two_factor_controls", snapshot.twoFactorControls).putString("identity_security_host", snapshot.host).putString("identity_security_path", snapshot.path).putLong("identity_security_synced_at", snapshot.syncedAt).apply();
    }

    static Snapshot load(Context context) {
        if (context == null) {
            return empty("forum.harleytg.com");
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("hcf_app", 0);
        return new Snapshot(sharedPreferences.getBoolean("identity_security_seen", false), sharedPreferences.getInt("identity_security_session_count", 0), sharedPreferences.getInt("identity_security_active_session_count", 0), sharedPreferences.getString("identity_security_providers", ""), sharedPreferences.getBoolean("identity_security_password_controls", false), sharedPreferences.getBoolean("identity_security_email_controls", false), sharedPreferences.getBoolean("identity_security_two_factor_controls", false), sharedPreferences.getString("identity_security_host", "forum.harleytg.com"), sharedPreferences.getString("identity_security_path", ""), sharedPreferences.getLong("identity_security_synced_at", 0L));
    }

    static Snapshot empty(String str) {
        return new Snapshot(false, 0, 0, "", false, false, false, str, "", 0L);
    }

    static void clear(Context context) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences("hcf_app", 0).edit().remove("identity_security_seen").remove("identity_security_session_count").remove("identity_security_active_session_count").remove("identity_security_providers").remove("identity_security_password_controls").remove("identity_security_email_controls").remove("identity_security_two_factor_controls").remove("identity_security_host").remove("identity_security_path").remove("identity_security_synced_at").apply();
    }

    static String mergeLabels(String str, String str2) {
        ArrayList<String> arrayList = new ArrayList();
        addCsv(arrayList, str);
        addCsv(arrayList, str2);
        StringBuilder sb = new StringBuilder();
        for (String str3 : arrayList) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(str3);
        }
        return sb.toString();
    }

    private static void addCsv(List<String> list, String str) {
        if (str == null) {
            return;
        }
        String[] split = str.split(",");
        int length = split.length;
        for (int i = 0; i < length; i++) {
            String str2 = split[i];
            String trim = str2 == null ? "" : str2.trim();
            if (!trim.isEmpty() && !list.contains(trim)) {
                list.add(trim);
            }
        }
    }

    private static String joinUnique(JSONArray jSONArray) {
        ArrayList<String> arrayList = new ArrayList();
        if (jSONArray != null) {
            for (int i = 0; i < jSONArray.length(); i++) {
                String trim = jSONArray.optString(i, "").trim();
                if (!trim.isEmpty() && !arrayList.contains(trim)) {
                    arrayList.add(trim);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String str : arrayList) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(str);
        }
        return sb.toString();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String safe(String str) {
        return str == null ? "" : str.trim();
    }

    private ForumSecurity() {
    }
}
