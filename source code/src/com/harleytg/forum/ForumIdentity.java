package com.harleytg.forum.dev;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/* loaded from: classes.dex */
final class ForumIdentity {

    static final class Snapshot {
        final boolean admin;
        final String avatarUrl;
        final int commentCount;
        final String connections;
        final int discussionCount;
        final String displayName;
        final String email;
        final boolean emailConfirmed;
        final String groups;
        final String host;
        final String joinTime;
        final String lastSeenAt;
        final boolean loggedIn;
        final int newNotifications;
        final String slug;
        final long syncedAt;
        final int unreadNotifications;
        final String userId;
        final String username;

        Snapshot(boolean z, String str, String str2, String str3, String str4, String str5, boolean z2, String str6, String str7, String str8, boolean z3, String str9, String str10, int i, int i2, int i3, int i4, String str11, long j) {
            this.loggedIn = z;
            this.userId = ForumIdentity.safe(str);
            this.username = ForumIdentity.safe(str2);
            this.slug = ForumIdentity.safe(str3);
            this.displayName = ForumIdentity.safe(str4);
            this.email = ForumIdentity.safe(str5);
            this.emailConfirmed = z2;
            this.avatarUrl = ForumIdentity.safe(str6);
            this.groups = ForumIdentity.safe(str7);
            this.connections = ForumIdentity.safe(str8);
            this.admin = z3;
            this.joinTime = ForumIdentity.safe(str9);
            this.lastSeenAt = ForumIdentity.safe(str10);
            this.unreadNotifications = Math.max(0, i);
            this.newNotifications = Math.max(0, i2);
            this.discussionCount = Math.max(0, i3);
            this.commentCount = Math.max(0, i4);
            this.host = ForumIdentity.safe(str11);
            this.syncedAt = j;
        }

        String identityLabel() {
            if (!this.loggedIn) {
                return "Guest_Protocol";
            }
            String str = !this.displayName.isEmpty() ? this.displayName : this.username;
            if (!this.username.isEmpty()) {
                if (str.equalsIgnoreCase("@" + this.username)) {
                    str = this.username;
                }
            }
            return str.isEmpty() ? "Member" : str;
        }

        String usernameDisplay() {
            if (!this.loggedIn) {
                return "Guest_Protocol";
            }
            if (this.username.isEmpty()) {
                return "Signed-in forum member";
            }
            return "@" + this.username;
        }

        String identityMetaLabel() {
            return !this.loggedIn ? "Guest forum identity" : this.admin ? "Administrator" : !this.groups.isEmpty() ? this.groups : "Forum member";
        }

        String connectionLabel() {
            if (!this.loggedIn) {
                return "Guest_Protocol";
            }
            if (!this.connections.isEmpty()) {
                return this.connections;
            }
            if (this.email.isEmpty()) {
                return "Forum session";
            }
            return this.emailConfirmed ? "Email (verified)" : "Email";
        }
    }

    static Snapshot fromBridgeJson(String str, String str2) throws Exception {
        JSONObject jSONObject = new JSONObject(str == null ? "{}" : str);
        if (!jSONObject.optBoolean("loggedIn", false)) {
            return guest(str2);
        }
        return new Snapshot(true, jSONObject.optString("id", ""), jSONObject.optString("username", ""), jSONObject.optString("slug", ""), jSONObject.optString("displayName", ""), jSONObject.optString("email", ""), jSONObject.optBoolean("emailConfirmed", false), jSONObject.optString("avatarUrl", ""), joinUnique(jSONObject.optJSONArray("groups")), joinUnique(jSONObject.optJSONArray("connections")), jSONObject.optBoolean("isAdmin", false), jSONObject.optString("joinTime", ""), jSONObject.optString("lastSeenAt", ""), jSONObject.optInt("unreadNotificationCount", 0), jSONObject.optInt("newNotificationCount", 0), jSONObject.optInt("discussionCount", 0), jSONObject.optInt("commentCount", 0), str2, System.currentTimeMillis());
    }

    static Snapshot guest(String str) {
        return new Snapshot(false, "", "", "", "Guest_Protocol", "", false, "", "", "Guest_Protocol", false, "", "", 0, 0, 0, 0, str, System.currentTimeMillis());
    }

    static void save(Context context, Snapshot snapshot) {
        if (context == null || snapshot == null) {
            return;
        }
        SharedPreferences.Editor edit = context.getSharedPreferences("hcf_app", 0).edit();
        edit.putBoolean("identity_logged_in", snapshot.loggedIn).putString("identity_user_id", snapshot.userId).putString("identity_username", snapshot.username).putString("identity_slug", snapshot.slug).putString("identity_display_name", snapshot.displayName).putString("identity_email", snapshot.email).putBoolean("identity_email_confirmed", snapshot.emailConfirmed).putString("identity_avatar_url", snapshot.avatarUrl).putString("identity_groups", snapshot.groups).putString("identity_connections", snapshot.connections).putBoolean("identity_admin", snapshot.admin).putString("identity_join_time", snapshot.joinTime).putString("identity_last_seen_at", snapshot.lastSeenAt).putInt("identity_unread_notifications", snapshot.unreadNotifications).putInt("identity_new_notifications", snapshot.newNotifications).putInt("identity_discussion_count", snapshot.discussionCount).putInt("identity_comment_count", snapshot.commentCount).putString("identity_host", snapshot.host).putLong("identity_synced_at", snapshot.syncedAt);
        if (!snapshot.loggedIn || snapshot.userId.isEmpty()) {
            edit.remove("session_user_id").remove("last_notification_count");
        } else {
            edit.putString("session_user_id", snapshot.userId);
        }
        edit.apply();
    }

    static Snapshot load(Context context) {
        if (context == null) {
            return guest("forum.harleytg.com");
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("hcf_app", 0);
        return new Snapshot(sharedPreferences.getBoolean("identity_logged_in", false), sharedPreferences.getString("identity_user_id", ""), sharedPreferences.getString("identity_username", ""), sharedPreferences.getString("identity_slug", ""), sharedPreferences.getString("identity_display_name", "Guest_Protocol"), sharedPreferences.getString("identity_email", ""), sharedPreferences.getBoolean("identity_email_confirmed", false), sharedPreferences.getString("identity_avatar_url", ""), sharedPreferences.getString("identity_groups", ""), sharedPreferences.getString("identity_connections", ""), sharedPreferences.getBoolean("identity_admin", false), sharedPreferences.getString("identity_join_time", ""), sharedPreferences.getString("identity_last_seen_at", ""), sharedPreferences.getInt("identity_unread_notifications", 0), sharedPreferences.getInt("identity_new_notifications", 0), sharedPreferences.getInt("identity_discussion_count", 0), sharedPreferences.getInt("identity_comment_count", 0), sharedPreferences.getString("identity_host", "forum.harleytg.com"), sharedPreferences.getLong("identity_synced_at", 0L));
    }

    static void clear(Context context) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences("hcf_app", 0).edit().remove("identity_logged_in").remove("identity_user_id").remove("identity_username").remove("identity_slug").remove("identity_display_name").remove("identity_email").remove("identity_email_confirmed").remove("identity_avatar_url").remove("identity_groups").remove("identity_connections").remove("identity_admin").remove("identity_join_time").remove("identity_last_seen_at").remove("identity_unread_notifications").remove("identity_new_notifications").remove("identity_discussion_count").remove("identity_comment_count").remove("identity_host").remove("identity_synced_at").remove("session_user_id").remove("last_notification_count").apply();
        ForumSecurity.clear(context);
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

    private ForumIdentity() {
    }
}
