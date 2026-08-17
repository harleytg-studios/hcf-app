package com.harleytg.forum;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Privacy-scoped mirror of the CURRENT signed-in Flarum user's identity.
 *
 * This intentionally does not collect passwords, access/session tokens,
 * preferences blobs, linked-provider identifiers, or private data belonging
 * to other forum users. Connection names are labels only (for example Email
 * or Discord) when the current session exposes enough information to infer
 * the provider without storing provider account IDs.
 */
final class ForumIdentity {
    static final class Snapshot {
        final boolean loggedIn;
        final String userId;
        final String username;
        final String slug;
        final String displayName;
        final String email;
        final boolean emailConfirmed;
        final String avatarUrl;
        final String groups;
        final String connections;
        final boolean admin;
        final String joinTime;
        final String lastSeenAt;
        final int unreadNotifications;
        final int newNotifications;
        final int discussionCount;
        final int commentCount;
        final String host;
        final long syncedAt;

        Snapshot(boolean loggedIn, String userId, String username, String slug,
                 String displayName, String email, boolean emailConfirmed,
                 String avatarUrl, String groups, String connections,
                 boolean admin, String joinTime, String lastSeenAt,
                 int unreadNotifications, int newNotifications,
                 int discussionCount, int commentCount, String host, long syncedAt) {
            this.loggedIn = loggedIn;
            this.userId = safe(userId);
            this.username = safe(username);
            this.slug = safe(slug);
            this.displayName = safe(displayName);
            this.email = safe(email);
            this.emailConfirmed = emailConfirmed;
            this.avatarUrl = safe(avatarUrl);
            this.groups = safe(groups);
            this.connections = safe(connections);
            this.admin = admin;
            this.joinTime = safe(joinTime);
            this.lastSeenAt = safe(lastSeenAt);
            this.unreadNotifications = Math.max(0, unreadNotifications);
            this.newNotifications = Math.max(0, newNotifications);
            this.discussionCount = Math.max(0, discussionCount);
            this.commentCount = Math.max(0, commentCount);
            this.host = safe(host);
            this.syncedAt = syncedAt;
        }

        String identityLabel() {
            if (!loggedIn) return "Guest_Protocol";
            String value = !displayName.isEmpty() ? displayName : username;
            if (!username.isEmpty() && value.equalsIgnoreCase("@" + username)) value = username;
            return value.isEmpty() ? "Member" : value;
        }

        String usernameDisplay() {
            if (!loggedIn) return "Guest_Protocol";
            if (!username.isEmpty()) return "@" + username;
            return "Signed-in forum member";
        }

        String identityMetaLabel() {
            if (!loggedIn) return "Guest forum identity";
            if (admin) return "Administrator";
            if (!groups.isEmpty()) return groups;
            return "Forum member";
        }

        String connectionLabel() {
            if (!loggedIn) return "Guest_Protocol";
            if (!connections.isEmpty()) return connections;
            if (!email.isEmpty()) return emailConfirmed ? "Email (verified)" : "Email";
            return "Forum session";
        }
    }

    static Snapshot fromBridgeJson(String json, String host) throws Exception {
        JSONObject o = new JSONObject(json == null ? "{}" : json);
        boolean loggedIn = o.optBoolean("loggedIn", false);
        if (!loggedIn) return guest(host);

        String groups = joinUnique(o.optJSONArray("groups"));
        String connections = joinUnique(o.optJSONArray("connections"));

        return new Snapshot(
                true,
                o.optString("id", ""),
                o.optString("username", ""),
                o.optString("slug", ""),
                o.optString("displayName", ""),
                o.optString("email", ""),
                o.optBoolean("emailConfirmed", false),
                o.optString("avatarUrl", ""),
                groups,
                connections,
                o.optBoolean("isAdmin", false),
                o.optString("joinTime", ""),
                o.optString("lastSeenAt", ""),
                o.optInt("unreadNotificationCount", 0),
                o.optInt("newNotificationCount", 0),
                o.optInt("discussionCount", 0),
                o.optInt("commentCount", 0),
                host,
                System.currentTimeMillis()
        );
    }

    static Snapshot guest(String host) {
        return new Snapshot(false, "", "", "", "Guest_Protocol", "", false,
                "", "", "Guest_Protocol", false, "", "", 0, 0, 0, 0, host,
                System.currentTimeMillis());
    }

    static void save(Context context, Snapshot s) {
        if (context == null || s == null) return;
        SharedPreferences.Editor e = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE).edit();
        e.putBoolean(AppPrefs.IDENTITY_LOGGED_IN, s.loggedIn)
                .putString(AppPrefs.IDENTITY_USER_ID, s.userId)
                .putString(AppPrefs.IDENTITY_USERNAME, s.username)
                .putString(AppPrefs.IDENTITY_SLUG, s.slug)
                .putString(AppPrefs.IDENTITY_DISPLAY_NAME, s.displayName)
                .putString(AppPrefs.IDENTITY_EMAIL, s.email)
                .putBoolean(AppPrefs.IDENTITY_EMAIL_CONFIRMED, s.emailConfirmed)
                .putString(AppPrefs.IDENTITY_AVATAR_URL, s.avatarUrl)
                .putString(AppPrefs.IDENTITY_GROUPS, s.groups)
                .putString(AppPrefs.IDENTITY_CONNECTIONS, s.connections)
                .putBoolean(AppPrefs.IDENTITY_ADMIN, s.admin)
                .putString(AppPrefs.IDENTITY_JOIN_TIME, s.joinTime)
                .putString(AppPrefs.IDENTITY_LAST_SEEN_AT, s.lastSeenAt)
                .putInt(AppPrefs.IDENTITY_UNREAD_NOTIFICATIONS, s.unreadNotifications)
                .putInt(AppPrefs.IDENTITY_NEW_NOTIFICATIONS, s.newNotifications)
                .putInt(AppPrefs.IDENTITY_DISCUSSION_COUNT, s.discussionCount)
                .putInt(AppPrefs.IDENTITY_COMMENT_COUNT, s.commentCount)
                .putString(AppPrefs.IDENTITY_HOST, s.host)
                .putLong(AppPrefs.IDENTITY_SYNCED_AT, s.syncedAt);
        if (s.loggedIn && !s.userId.isEmpty()) e.putString(AppPrefs.SESSION_USER_ID, s.userId);
        else e.remove(AppPrefs.SESSION_USER_ID).remove(AppPrefs.LAST_NOTIFICATION_COUNT);
        e.apply();
    }

    static Snapshot load(Context context) {
        if (context == null) return guest(ForumConfig.PRIMARY_HOST);
        SharedPreferences p = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        return new Snapshot(
                p.getBoolean(AppPrefs.IDENTITY_LOGGED_IN, false),
                p.getString(AppPrefs.IDENTITY_USER_ID, ""),
                p.getString(AppPrefs.IDENTITY_USERNAME, ""),
                p.getString(AppPrefs.IDENTITY_SLUG, ""),
                p.getString(AppPrefs.IDENTITY_DISPLAY_NAME, "Guest_Protocol"),
                p.getString(AppPrefs.IDENTITY_EMAIL, ""),
                p.getBoolean(AppPrefs.IDENTITY_EMAIL_CONFIRMED, false),
                p.getString(AppPrefs.IDENTITY_AVATAR_URL, ""),
                p.getString(AppPrefs.IDENTITY_GROUPS, ""),
                p.getString(AppPrefs.IDENTITY_CONNECTIONS, ""),
                p.getBoolean(AppPrefs.IDENTITY_ADMIN, false),
                p.getString(AppPrefs.IDENTITY_JOIN_TIME, ""),
                p.getString(AppPrefs.IDENTITY_LAST_SEEN_AT, ""),
                p.getInt(AppPrefs.IDENTITY_UNREAD_NOTIFICATIONS, 0),
                p.getInt(AppPrefs.IDENTITY_NEW_NOTIFICATIONS, 0),
                p.getInt(AppPrefs.IDENTITY_DISCUSSION_COUNT, 0),
                p.getInt(AppPrefs.IDENTITY_COMMENT_COUNT, 0),
                p.getString(AppPrefs.IDENTITY_HOST, ForumConfig.PRIMARY_HOST),
                p.getLong(AppPrefs.IDENTITY_SYNCED_AT, 0L)
        );
    }

    static void clear(Context context) {
        if (context == null) return;
        context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE).edit()
                .remove(AppPrefs.IDENTITY_LOGGED_IN)
                .remove(AppPrefs.IDENTITY_USER_ID)
                .remove(AppPrefs.IDENTITY_USERNAME)
                .remove(AppPrefs.IDENTITY_SLUG)
                .remove(AppPrefs.IDENTITY_DISPLAY_NAME)
                .remove(AppPrefs.IDENTITY_EMAIL)
                .remove(AppPrefs.IDENTITY_EMAIL_CONFIRMED)
                .remove(AppPrefs.IDENTITY_AVATAR_URL)
                .remove(AppPrefs.IDENTITY_GROUPS)
                .remove(AppPrefs.IDENTITY_CONNECTIONS)
                .remove(AppPrefs.IDENTITY_ADMIN)
                .remove(AppPrefs.IDENTITY_JOIN_TIME)
                .remove(AppPrefs.IDENTITY_LAST_SEEN_AT)
                .remove(AppPrefs.IDENTITY_UNREAD_NOTIFICATIONS)
                .remove(AppPrefs.IDENTITY_NEW_NOTIFICATIONS)
                .remove(AppPrefs.IDENTITY_DISCUSSION_COUNT)
                .remove(AppPrefs.IDENTITY_COMMENT_COUNT)
                .remove(AppPrefs.IDENTITY_HOST)
                .remove(AppPrefs.IDENTITY_SYNCED_AT)
                .remove(AppPrefs.SESSION_USER_ID)
                .remove(AppPrefs.LAST_NOTIFICATION_COUNT)
                .apply();
        ForumSecurity.clear(context);
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
    private ForumIdentity() {}
}
