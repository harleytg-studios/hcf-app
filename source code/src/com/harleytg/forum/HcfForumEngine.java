package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.IDN;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.json.JSONArray;
import org.json.JSONObject;

public final class HcfForumEngine {
    private HcfForumEngine() {}
}

// ---- ForumConfig.java ----
/** Runtime forum-domain registry with safe built-in fallbacks. */
final class ForumConfig {
    static final String HTTPS = "https";
    static final long PRIMARY_RETRY_COOLDOWN_MS = 21600000L;

    static final String BUILTIN_PRIMARY_HOST = "forum.harleytg.com";
    static final String BUILTIN_BACKUP_HOST = "harleysclan.freeflarum.com";

    static volatile String PRIMARY_HOST = BUILTIN_PRIMARY_HOST;
    static volatile String BACKUP_HOST = BUILTIN_BACKUP_HOST;
    static volatile Set<String> FORUM_HOSTS = builtInHosts();

    static synchronized void applyRemote(String primary, Collection<String> backups) {
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        if (primary != null && !primary.isEmpty()) hosts.add(primary);
        if (backups != null) {
            for (String host : backups) {
                if (host != null && !host.isEmpty()) hosts.add(host);
            }
        }
        if (hosts.isEmpty()) {
            resetBuiltIn();
            return;
        }

        PRIMARY_HOST = hosts.iterator().next();
        List<String> ordered = new ArrayList<>(hosts);
        BACKUP_HOST = ordered.size() > 1 ? ordered.get(1) : BUILTIN_BACKUP_HOST;
        if (ordered.size() == 1 && !BACKUP_HOST.equals(PRIMARY_HOST)) hosts.add(BACKUP_HOST);
        FORUM_HOSTS = Collections.unmodifiableSet(new LinkedHashSet<>(hosts));
    }

    static synchronized void resetBuiltIn() {
        PRIMARY_HOST = BUILTIN_PRIMARY_HOST;
        BACKUP_HOST = BUILTIN_BACKUP_HOST;
        FORUM_HOSTS = builtInHosts();
    }

    private static Set<String> builtInHosts() {
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        hosts.add(BUILTIN_PRIMARY_HOST);
        hosts.add(BUILTIN_BACKUP_HOST);
        return Collections.unmodifiableSet(hosts);
    }

    private ForumConfig() {}
}


// ---- FirebaseConfigLoader.java ----
/* loaded from: classes.dex */
final class FirebaseConfigLoader {

    interface Callback {
        void onResult(Config config, String str);
    }

    static final class Config {
        final String apiKey;
        final String appId;
        final String authDomain;
        final String measurementId;
        final String messagingSenderId;
        final String projectId;
        final String source;
        final String storageBucket;

        Config(String str, String str2, String str3, String str4, String str5, String str6, String str7, String str8) {
            this.apiKey = str;
            this.authDomain = str2;
            this.projectId = str3;
            this.storageBucket = str4;
            this.messagingSenderId = str5;
            this.appId = str6;
            this.measurementId = str7;
            this.source = str8;
        }

        boolean isValid() {
            return FirebaseConfigLoader.notBlank(this.apiKey) && FirebaseConfigLoader.notBlank(this.projectId) && FirebaseConfigLoader.notBlank(this.messagingSenderId) && FirebaseConfigLoader.notBlank(this.appId);
        }

        String safeSummary() {
            if (!isValid()) {
                return "Configuration unavailable";
            }
            return "Loaded • project " + this.projectId + " • " + this.source;
        }
    }

    static Config load(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("hcf_app", 0);
        Config parse = parse(sharedPreferences.getString("firebase_config_cache", ""), sharedPreferences.getString("firebase_config_source", "Cached HTTPS config"));
        if (parse != null && parse.isValid()) {
            return parse;
        }
        try {
            Config parse2 = parse(readAll(context.getAssets().open("firebase-config.js")), "Bundled firebase-config.js");
            if (parse2 == null) {
                return null;
            }
            if (parse2.isValid()) {
                return parse2;
            }
            return null;
        } catch (Throwable unused) {
            return null;
        }
    }

    static void refresh(Context context, final Callback callback) {
        final Context applicationContext = context.getApplicationContext();
        final SharedPreferences sharedPreferences = applicationContext.getSharedPreferences("hcf_app", 0);
        final String trim = sharedPreferences.getString("firebase_config_url", "").trim();
        if (trim.isEmpty()) {
            Config load = load(applicationContext);
            callback.onResult(load, load == null ? "Bundled Firebase config could not be read." : "Using bundled Firebase config.");
        } else if (!trim.startsWith("https://")) {
            callback.onResult(load(applicationContext), "Firebase config URL must use HTTPS.");
        } else {
            AppExecutors.network().execute(new Runnable() { // from class: com.harleytg.forum.dev.FirebaseConfigLoader$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    FirebaseConfigLoader.lambda$refresh$1(trim, sharedPreferences, applicationContext, callback);
                }
            });
        }
    }

    static /* synthetic */ void lambda$refresh$1(String urlText, SharedPreferences sharedPreferences, Context context, final Callback callback) {
        Config result;
        String message;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlText).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "HarleysClanForumApp/1.0 FirebaseConfig");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            String raw = readAll(connection.getInputStream());
            Config parsed = parse(raw, "HTTPS config");
            if (parsed == null || !parsed.isValid()) throw new IllegalStateException("Invalid Firebase config");
            sharedPreferences.edit().putString("firebase_config_cache", raw).putString("firebase_config_source", "HTTPS config").apply();
            result = parsed;
            message = "Firebase config refreshed from HTTPS.";
        } catch (Throwable t) {
            result = load(context);
            message = "Remote refresh failed; kept " + (result == null ? "no config" : result.source) + ".";
            AppLogger.error(context, "firebase_config_refresh", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        } finally {
            if (connection != null) connection.disconnect();
        }
        final Config callbackConfig = result;
        final String callbackMessage = message;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() { callback.onResult(callbackConfig, callbackMessage); }
        });
    }

    static void clearRemoteCache(Context context) {
        context.getSharedPreferences("hcf_app", 0).edit().remove("firebase_config_cache").remove("firebase_config_source").apply();
    }

    private static Config parse(String str, String str2) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }
        Config config = new Config(value(str, "apiKey"), value(str, "authDomain"), value(str, "projectId"), value(str, "storageBucket"), value(str, "messagingSenderId"), value(str, "appId"), value(str, "measurementId"), str2);
        if (config.isValid()) {
            return config;
        }
        return null;
    }

    private static String value(String str, String str2) {
        Matcher matcher = Pattern.compile("(?:\\\"?" + Pattern.quote(str2) + "\\\"?)\\s*:\\s*[\\\"']([^\\\"']*)[\\\"']").matcher(str);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean notBlank(String str) {
        return (str == null || str.trim().isEmpty()) ? false : true;
    }

    private static String readAll(InputStream inputStream) throws Exception {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        while (true) {
            String readLine = bufferedReader.readLine();
            if (readLine == null) {
                bufferedReader.close();
                return sb.toString();
            }
            sb.append(readLine);
            sb.append('\n');
        }
    }

    private FirebaseConfigLoader() {
    }
}


// ---- ForumIdentity.java ----
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


// ---- ForumNotificationClient.java ----
/* loaded from: classes.dex */
final class ForumNotificationClient {
    private static final int CONNECT_TIMEOUT_MS = 4500;
    private static final int MAX_BODY_CHARS = 700000;
    private static final int READ_TIMEOUT_MS = 4500;

    static final class HttpStatusException extends IOException {
        final int statusCode;
        final long retryAfterMs;

        HttpStatusException(int statusCode, long retryAfterMs) {
            super("HTTP " + statusCode);
            this.statusCode = statusCode;
            this.retryAfterMs = Math.max(0L, retryAfterMs);
        }
    }

    static final class Alert {
        final String body;
        final String id;
        final String title;
        final String url;

        Alert(String str, String str2, String str3, String str4) {
            this.id = ForumNotificationClient.clean(str, 120);
            this.title = ForumNotificationClient.clean(str2, 120);
            this.body = ForumNotificationClient.clean(str3, 500);
            this.url = str4 == null ? "" : str4;
        }
    }

    static int fetchNewCount(Context context, String str, String str2) throws Exception {
        JSONObject jSONObject = new JSONObject(get(context, trustedBase(str), "api/users/" + str2 + "?fields%5Busers%5D=unreadNotificationCount%2CnewNotificationCount", "Count")).getJSONObject("data").getJSONObject("attributes");
        return Math.max(Math.max(0, jSONObject.optInt("unreadNotificationCount", 0)), Math.max(0, jSONObject.optInt("newNotificationCount", 0)));
    }

    static List<Alert> fetchLatest(Context context, String str, int i) throws Exception {
        String str2;
        String trustedBase = trustedBase(str);
        int max = Math.max(1, Math.min(20, i));
        try {
            str2 = get(context, trustedBase, "api/notifications?include=fromUser,subject&page%5Blimit%5D=" + max, "Details");
        } catch (Throwable error) {
            if (error instanceof HttpStatusException) {
                throw (HttpStatusException) error;
            }
            str2 = get(context, trustedBase, "api/notifications?page%5Blimit%5D=" + max, "DetailsFallback");
        }
        return parseAlerts(new JSONObject(str2), trustedBase, max);
    }

    private static List<Alert> parseAlerts(JSONObject jSONObject, String str, int i) {
        Alert parseAlert;
        JSONArray optJSONArray = jSONObject.optJSONArray("data");
        if (optJSONArray == null || optJSONArray.length() == 0) {
            return Collections.emptyList();
        }
        HashMap hashMap = new HashMap();
        JSONArray optJSONArray2 = jSONObject.optJSONArray("included");
        if (optJSONArray2 != null) {
            for (int i2 = 0; i2 < optJSONArray2.length(); i2++) {
                JSONObject optJSONObject = optJSONArray2.optJSONObject(i2);
                if (optJSONObject != null) {
                    hashMap.put(key(optJSONObject.optString("type"), optJSONObject.optString("id")), optJSONObject);
                }
            }
        }
        ArrayList arrayList = new ArrayList();
        for (int i3 = 0; i3 < optJSONArray.length() && arrayList.size() < i; i3++) {
            JSONObject optJSONObject2 = optJSONArray.optJSONObject(i3);
            if (optJSONObject2 != null && (parseAlert = parseAlert(optJSONObject2, hashMap, str)) != null && !parseAlert.id.isEmpty()) {
                arrayList.add(parseAlert);
            }
        }
        return arrayList;
    }

    private static Alert parseAlert(JSONObject jSONObject, Map<String, JSONObject> map, String str) {
        String str2;
        String str3;
        String readableType;
        String optString = jSONObject.optString("id", "");
        JSONObject optJSONObject = jSONObject.optJSONObject("attributes");
        if (optJSONObject == null) {
            optJSONObject = new JSONObject();
        }
        String optString2 = optJSONObject.optString("type", "notification");
        JSONObject optJSONObject2 = jSONObject.optJSONObject("relationships");
        JSONObject relationData = relationData(optJSONObject2, "fromUser");
        JSONObject relationData2 = relationData(optJSONObject2, "subject");
        JSONObject jSONObject2 = relationData == null ? null : map.get(key(relationData.optString("type"), relationData.optString("id")));
        JSONObject jSONObject3 = relationData2 == null ? null : map.get(key(relationData2.optString("type"), relationData2.optString("id")));
        JSONObject contentObject = contentObject(optJSONObject.opt("content"));
        String userLabel = userLabel(jSONObject2);
        if (userLabel.isEmpty()) {
            userLabel = firstDeep(contentObject, "displayName", "display_name", "username", "user_name", "senderName", "sender_name");
        }
        if (userLabel.isEmpty()) {
            userLabel = "Someone";
        }
        String firstDeep = firstDeep(contentObject, "conversationId", "conversation_id");
        String firstDeep2 = firstDeep(contentObject, "discussionId", "discussion_id");
        String firstDeep3 = firstDeep(contentObject, "discussionTitle", "discussion_title", "title");
        String str4 = userLabel;
        String firstDeep4 = firstDeep(contentObject, "postNumber", "post_number", "number");
        String firstDeep5 = firstDeep(contentObject, "userSlug", "user_slug", "slug", "username");
        String prefer = prefer(attribute(jSONObject2, "slug"), attribute(jSONObject2, "username"));
        String optString3 = relationData2 != null ? relationData2.optString("type", "") : "";
        if ("discussions".equals(optString3)) {
            firstDeep2 = relationData2.optString("id", firstDeep2);
            firstDeep3 = prefer(firstDeep3, attribute(jSONObject3, "title"));
            str2 = firstDeep4;
        } else if ("posts".equals(optString3)) {
            JSONObject relationData3 = relationData(jSONObject3 == null ? null : jSONObject3.optJSONObject("relationships"), "discussion");
            if (relationData3 != null) {
                firstDeep2 = relationData3.optString("id", firstDeep2);
            }
            str2 = prefer(firstDeep4, attribute(jSONObject3, "number"));
        } else {
            if ("users".equals(optString3)) {
                firstDeep5 = prefer(attribute(jSONObject3, "slug"), attribute(jSONObject3, "username"));
            }
            str2 = firstDeep4;
        }
        String lowerCase = optString2.toLowerCase(Locale.US);
        boolean z = !firstDeep.isEmpty() || lowerCase.contains("privatediscussion") || lowerCase.contains("private_message") || lowerCase.contains("privatemessage") || lowerCase.contains("conversationmessage") || lowerCase.contains("conversation_message") || lowerCase.contains("messenger");
        if (z) {
            str3 = "New message from " + str4;
        } else if (lowerCase.contains("postmentioned") || lowerCase.contains("usermentioned")) {
            str3 = str4 + " mentioned you";
        } else if (lowerCase.contains("liked") || lowerCase.contains("postliked")) {
            str3 = str4 + " liked your post";
        } else if (lowerCase.contains("newpost") || lowerCase.contains("reply")) {
            str3 = "New reply from " + str4;
        } else if (lowerCase.contains("follow")) {
            str3 = str4 + " followed you";
        } else {
            str3 = "New forum alert from " + str4;
        }
        String firstDeep6 = firstDeep(contentObject, "message", "body", "text", "excerpt", "preview", "content");
        if (z || firstDeep3.isEmpty()) {
            firstDeep3 = firstDeep6;
        }
        String cleanNotificationBody = cleanNotificationBody(firstDeep3, z);
        if (cleanNotificationBody.isEmpty()) {
            if (z) {
                readableType = "You have a new private message.";
            } else {
                readableType = readableType(optString2);
            }
            cleanNotificationBody = readableType;
        }
        String str5 = str + "notifications";
        if (z && !firstDeep.isEmpty() && firstDeep.matches("[0-9]+")) {
            str5 = str + "conversations/" + firstDeep;
        } else if (lowerCase.contains("follow") && !prefer.isEmpty()) {
            str5 = str + "u/" + Uri.encode(prefer);
        } else if (!firstDeep5.isEmpty() && "users".equals(optString3)) {
            str5 = str + "u/" + Uri.encode(firstDeep5);
        } else if (!firstDeep2.isEmpty() && firstDeep2.matches("[0-9]+")) {
            str5 = str + "d/" + firstDeep2;
            if (!str2.isEmpty() && str2.matches("[0-9]+")) {
                str5 = str5 + "/" + str2;
            }
        }
        return new Alert(optString, str3, cleanNotificationBody, str5);
    }

    private static JSONObject relationData(JSONObject jSONObject, String str) {
        JSONObject optJSONObject;
        if (jSONObject == null || (optJSONObject = jSONObject.optJSONObject(str)) == null) {
            return null;
        }
        Object opt = optJSONObject.opt("data");
        if (opt instanceof JSONObject) {
            return (JSONObject) opt;
        }
        return null;
    }

    private static JSONObject contentObject(Object obj) {
        if (obj instanceof JSONObject) {
            return (JSONObject) obj;
        }
        if (obj instanceof String) {
            String trim = ((String) obj).trim();
            if (trim.startsWith("{") && trim.endsWith("}")) {
                try {
                    return new JSONObject(trim);
                } catch (Throwable unused) {
                }
            }
        }
        return new JSONObject();
    }

    private static String userLabel(JSONObject jSONObject) {
        JSONObject optJSONObject;
        if (jSONObject != null && (optJSONObject = jSONObject.optJSONObject("attributes")) != null) {
            String clean = clean(optJSONObject.optString("displayName", ""), 80);
            return !clean.isEmpty() ? clean : clean(optJSONObject.optString("username", ""), 80);
        }
        return "";
    }

    private static String attribute(JSONObject jSONObject, String str) {
        JSONObject optJSONObject;
        if (jSONObject == null || (optJSONObject = jSONObject.optJSONObject("attributes")) == null) {
            return "";
        }
        return clean(optJSONObject.optString(str, ""), 300);
    }

    private static String firstDeep(JSONObject jSONObject, String... strArr) {
        return firstDeepObject(jSONObject, 0, strArr);
    }

    private static String firstDeepObject(JSONObject jSONObject, int i, String... strArr) {
        if (jSONObject != null && i <= 5) {
            for (String str : strArr) {
                if (jSONObject.has(str)) {
                    String readableValue = readableValue(jSONObject.opt(str), i + 1, strArr);
                    if (!readableValue.isEmpty()) {
                        return readableValue;
                    }
                }
            }
            JSONArray names = jSONObject.names();
            if (names != null) {
                for (int i2 = 0; i2 < names.length(); i2++) {
                    Object opt = jSONObject.opt(names.optString(i2, ""));
                    if ((opt instanceof JSONObject) || (opt instanceof JSONArray)) {
                        String readableValue2 = readableValue(opt, i + 1, strArr);
                        if (!readableValue2.isEmpty()) {
                            return readableValue2;
                        }
                    }
                }
            }
        }
        return "";
    }

    private static String readableValue(Object obj, int i, String... strArr) {
        if (obj == null || obj == JSONObject.NULL || i > 6) {
            return "";
        }
        if (obj instanceof JSONObject) {
            return firstDeepObject((JSONObject) obj, i, strArr);
        }
        int i2 = 0;
        if (obj instanceof JSONArray) {
            JSONArray jSONArray = (JSONArray) obj;
            while (i2 < jSONArray.length()) {
                String readableValue = readableValue(jSONArray.opt(i2), i + 1, strArr);
                if (!readableValue.isEmpty()) {
                    return readableValue;
                }
                i2++;
            }
            return "";
        }
        String clean = clean(String.valueOf(obj), 2000);
        if (clean.isEmpty()) {
            return "";
        }
        String trim = clean.trim();
        if ((trim.startsWith("{") && trim.endsWith("}")) || (trim.startsWith("[") && trim.endsWith("]"))) {
            try {
                if (trim.startsWith("{")) {
                    return firstDeepObject(new JSONObject(trim), i + 1, strArr);
                }
                JSONArray jSONArray2 = new JSONArray(trim);
                while (i2 < jSONArray2.length()) {
                    String readableValue2 = readableValue(jSONArray2.opt(i2), i + 1, strArr);
                    if (!readableValue2.isEmpty()) {
                        return readableValue2;
                    }
                    i2++;
                }
                return "";
            } catch (Throwable ignored) {
                return clean(trim, 500);
            }
        }
        return clean(trim, 500);
    }

    private static String cleanNotificationBody(String str, boolean z) {
        String readableValue;
        String stripMarkup = stripMarkup(str);
        if (stripMarkup.isEmpty()) {
            return "";
        }
        String trim = stripMarkup.trim();
        if ((trim.startsWith("{") && trim.endsWith("}")) || (trim.startsWith("[") && trim.endsWith("]"))) {
            try {
                if (trim.startsWith("{")) {
                    readableValue = firstDeepObject(new JSONObject(trim), 0, "message", "body", "text", "excerpt", "preview", "content");
                } else {
                    readableValue = readableValue(new JSONArray(trim), 0, "message", "body", "text", "excerpt", "preview", "content");
                }
                stripMarkup = stripMarkup(readableValue);
            } catch (Throwable unused) {
                return "";
            }
        }
        return (stripMarkup.isEmpty() && z) ? "You have a new private message." : clean(stripMarkup, 500);
    }

    private static String prefer(String str, String str2) {
        return (str == null || str.trim().isEmpty()) ? clean(str2, 500) : str;
    }

    private static String readableType(String str) {
        if (str == null) {
            str = "Notification";
        }
        String trim = str.replace('_', ' ').replace('-', ' ').replaceAll("([a-z])([A-Z])", "$1 $2").trim();
        return trim.isEmpty() ? "You have a new forum notification." : trim;
    }

    private static String stripMarkup(String str) {
        if (str == null) {
            return "";
        }
        return clean(str.replaceAll("<[^>]+>", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replaceAll("\\s+", " "), 500);
    }

    private static String trustedBase(String str) {
        if (!ForumUrlRouter.isForumHost(str)) {
            str = "forum.harleytg.com";
        }
        return ForumUrlRouter.home(str);
    }

    private static String get(Context context, String str, String str2, String str3) throws Exception {
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str + str2).openConnection();
        try {
            httpURLConnection.setConnectTimeout(4500);
            httpURLConnection.setReadTimeout(4500);
            httpURLConnection.setUseCaches(true);
            httpURLConnection.setInstanceFollowRedirects(false);
            httpURLConnection.setRequestMethod("GET");
            httpURLConnection.setRequestProperty("Accept", "application/vnd.api+json, application/json");
            httpURLConnection.setRequestProperty("Cache-Control", "no-cache, max-age=0");
            httpURLConnection.setRequestProperty("User-Agent", "HarleysClanForumApp/1.0 Notification" + str3);
            String cookie = CookieManager.getInstance().getCookie(str);
            if (cookie != null && !cookie.trim().isEmpty()) {
                httpURLConnection.setRequestProperty("Cookie", cookie);
            }
            int responseCode = httpURLConnection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new HttpStatusException(responseCode, retryAfterMillis(httpURLConnection));
            }
            return read(httpURLConnection.getInputStream());
        } finally {
            httpURLConnection.disconnect();
        }
    }

    private static long retryAfterMillis(HttpURLConnection connection) {
        if (connection == null) return 0L;
        String value = connection.getHeaderField("Retry-After");
        if (value == null || value.trim().isEmpty()) return 0L;
        try {
            long seconds = Long.parseLong(value.trim());
            return Math.min(300000L, Math.max(0L, seconds * 1000L));
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static String read(InputStream inputStream) throws Exception {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        char[] cArr = new char[8192];
        while (true) {
            int read = bufferedReader.read(cArr);
            if (read == -1 || sb.length() >= MAX_BODY_CHARS) {
                break;
            }
            sb.append(cArr, 0, Math.min(read, MAX_BODY_CHARS - sb.length()));
        }
        bufferedReader.close();
        return sb.toString();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String clean(String str, int i) {
        String trim = str == null ? "" : str.trim();
        if (trim.length() <= i) {
            return trim;
        }
        return trim.substring(0, i) + "…";
    }

    private static String key(String str, String str2) {
        StringBuilder sb = new StringBuilder();
        if (str == null) {
            str = "";
        }
        sb.append(str);
        sb.append(":");
        if (str2 == null) {
            str2 = "";
        }
        sb.append(str2);
        return sb.toString();
    }

    private ForumNotificationClient() {
    }
}


// ---- ForumNotificationSync.java ----
/* loaded from: classes.dex */
final class ForumNotificationSync {
    private static final long STATUS_WRITE_INTERVAL_MS = 15000;

    static final class Outcome {
        final int count;
        final int delivered;
        final long latencyMs;

        Outcome(int i, int i2, long j) {
            this.count = i;
            this.delivered = i2;
            this.latencyMs = j;
        }
    }

    static Outcome perform(Context context, String str, String str2, String str3) throws Exception {
        int i = 0;
        long currentTimeMillis = System.currentTimeMillis();
        try {
            int fetchNewCount = ForumNotificationClient.fetchNewCount(context, str, str2);
            int recordForumNotificationCount = NotificationHelper.recordForumNotificationCount(context, fetchNewCount, str, str3);
            if (recordForumNotificationCount > 0) {
                try {
                    i = NotificationHelper.deliverDetailedAlerts(context, ForumNotificationClient.fetchLatest(context, str, Math.max(recordForumNotificationCount + 4, 8)), recordForumNotificationCount, str, str3);
                } catch (Throwable th) {
                    if (th instanceof ForumNotificationClient.HttpStatusException) {
                        throw (ForumNotificationClient.HttpStatusException) th;
                    }
                    NotificationHelper.postGenericDelta(context, recordForumNotificationCount, str);
                    AppLogger.warn(context, "notification_detail", th.getClass().getSimpleName() + " | generic-fallback");
                }
                long max = Math.max(0L, System.currentTimeMillis() - currentTimeMillis);
                RuntimeDiagnostics.syncSucceeded(max);
                recordStatus(context, "Live • synced", max, false);
                return new Outcome(fetchNewCount, i, max);
            }
            i = 0;
            long max2 = Math.max(0L, System.currentTimeMillis() - currentTimeMillis);
            RuntimeDiagnostics.syncSucceeded(max2);
            recordStatus(context, "Live • synced", max2, false);
            return new Outcome(fetchNewCount, i, max2);
        } catch (Exception e) {
            long max3 = Math.max(0L, System.currentTimeMillis() - currentTimeMillis);
            RuntimeDiagnostics.syncFailed();
            recordStatus(context, "Waiting for connection", max3, true);
            throw e;
        }
    }

    static void deliverObservedCountAsync(Context context, final String str, final int i, final String str2) {
        if (context == null || i <= 0) {
            return;
        }
        final Context applicationContext = context.getApplicationContext();
        AppExecutors.network().execute(new Runnable() { // from class: com.harleytg.forum.dev.ForumNotificationSync$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                ForumNotificationSync.lambda$deliverObservedCountAsync$0(applicationContext, str, i, str2);
            }
        });
    }

    static /* synthetic */ void lambda$deliverObservedCountAsync$0(Context context, String str, int i, String str2) {
        long currentTimeMillis = System.currentTimeMillis();
        try {
            NotificationHelper.deliverDetailedAlerts(context, ForumNotificationClient.fetchLatest(context, str, Math.max(i + 4, 8)), i, str, str2);
            long currentTimeMillis2 = System.currentTimeMillis() - currentTimeMillis;
            RuntimeDiagnostics.syncSucceeded(currentTimeMillis2);
            recordStatus(context, "Live • synced", currentTimeMillis2, false);
        } catch (Throwable th) {
            NotificationHelper.postGenericDelta(context, i, str);
            long currentTimeMillis3 = System.currentTimeMillis() - currentTimeMillis;
            RuntimeDiagnostics.syncFailed();
            recordStatus(context, "Live • detail unavailable", currentTimeMillis3, true);
            AppLogger.warn(context, "notification_detail", th.getClass().getSimpleName());
        }
    }

    private static void recordStatus(Context context, String str, long j, boolean z) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("hcf_app", 0);
        long currentTimeMillis = System.currentTimeMillis();
        String string = sharedPreferences.getString("notification_last_sync_status", "");
        long j2 = sharedPreferences.getLong("notification_last_sync_at", 0L);
        boolean z2 = !str.equals(string);
        if (z || z2 || currentTimeMillis - j2 >= STATUS_WRITE_INTERVAL_MS) {
            sharedPreferences.edit().putLong("notification_last_sync_at", currentTimeMillis).putString("notification_last_sync_status", str).putLong("notification_last_sync_latency_ms", Math.max(0L, j)).apply();
        }
    }

    private ForumNotificationSync() {
    }
}


// ---- ForumSecurity.java ----
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


// ---- ForumUrlRouter.java ----
/* loaded from: classes.dex */
final class ForumUrlRouter {
    private ForumUrlRouter() {
    }

    static boolean isForumHost(String str) {
        return str != null && ForumConfig.FORUM_HOSTS.contains(str.toLowerCase(Locale.US));
    }

    static boolean isForumUrl(Uri uri) {
        if (uri == null || !isForumHost(uri.getHost())) {
            return false;
        }
        String scheme = uri.getScheme();
        return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
    }

    static String equivalentOnHost(Uri uri, String str) {
        Uri.Builder encodedPath = new Uri.Builder().scheme("https").authority(str).encodedPath(emptyToSlash(uri.getEncodedPath()));
        if (uri.getEncodedQuery() != null) {
            encodedPath.encodedQuery(uri.getEncodedQuery());
        }
        if (uri.getEncodedFragment() != null) {
            encodedPath.encodedFragment(uri.getEncodedFragment());
        }
        return encodedPath.build().toString();
    }

    static String home(String str) {
        return "https://" + str + "/";
    }

    private static String emptyToSlash(String str) {
        return (str == null || str.isEmpty()) ? "/" : str;
    }
}


// ---- LiveForumUpdater.java ----
/* loaded from: classes.dex */
final class LiveForumUpdater {
    private static final long AUTH_CHECK_MS = 15000L;
    private static final int CONNECT_TIMEOUT_MS = 4500;
    private static final long FIRST_POLL_MS = 100L;
    private static final long IDLE_ROUTE_POLL_MS = 1500L;
    private static final int MAX_BODY_CHARS = 180000;
    private static final int MAX_WS_PAYLOAD_BYTES = 524288;
    private static final int PUSH_READ_TIMEOUT_MS = 60000;
    private static final long RECONNECT_MAX_MS = 8000L;
    private static final long RECONNECT_MIN_MS = 300L;
    private static final int READ_TIMEOUT_MS = 4500;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Context app;
    private String baselineFingerprint;
    private String baselineKey;
    private final Map<String, CacheEntry> cache = new HashMap();
    private int failures;
    private boolean forceQueuedRefresh;
    private boolean inFlight;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private boolean queuedRefresh;
    private int reconnectAttempts;
    private volatile boolean running;
    private volatile PushConnection socket;
    private volatile boolean socketConnected;
    private boolean socketConnecting;
    private volatile int socketGeneration;
    private String socketIdentity = "";
    private String lastState = "";
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            LiveForumUpdater.this.poll();
        }
    };
    private final Runnable reconnect = new Runnable() {
        @Override
        public void run() {
            LiveForumUpdater.this.connectSocket();
        }
    };
    private final Runnable authCheck = new Runnable() {
        @Override
        public void run() {
            LiveForumUpdater.this.checkRealtimeIdentity();
        }
    };
    private final Runnable pushedRefresh = new Runnable() {
        @Override
        public void run() {
            LiveForumUpdater.this.refreshCurrentRoute(false, true);
        }
    };

    interface Listener {
        String currentUrl();

        void onChangeCandidate(String str, String str2);

        void onStateChanged(String str);
    }

    LiveForumUpdater(Context context, Listener listener) {
        Context applicationContext = context.getApplicationContext();
        this.app = applicationContext;
        this.listener = listener;
        this.prefs = applicationContext.getSharedPreferences("hcf_app", 0);
    }

    void start() {
        if (this.running) {
            return;
        }
        this.running = true;
        this.failures = 0;
        this.reconnectAttempts = 0;
        this.socketIdentity = realtimeIdentity();
        state("SYNCING");
        scheduleFallback(FIRST_POLL_MS);
        scheduleReconnect(0L);
        scheduleAuthCheck();
    }

    void stop() {
        this.running = false;
        this.socketGeneration++;
        this.main.removeCallbacks(this.tick);
        this.main.removeCallbacks(this.reconnect);
        this.main.removeCallbacks(this.authCheck);
        this.main.removeCallbacks(this.pushedRefresh);
        this.socketConnecting = false;
        this.socketConnected = false;
        closeSocket();
        state("PAUSED");
    }

    void destroy() {
        stop();
    }

    void reset() {
        this.baselineKey = null;
        this.baselineFingerprint = null;
        if (this.running) {
            poke();
        }
    }

    void poke() {
        if (!this.running) {
            return;
        }
        if (!realtimeIdentity().equals(this.socketIdentity)) {
            restartSocket("auth-or-host-changed");
        } else if (!this.socketConnected) {
            scheduleReconnect(0L);
        }
        if (this.socketConnected) {
            schedulePushedRefresh(0L);
        } else {
            scheduleFallback(0L);
        }
    }

    void noteUserInteraction() {
        RuntimeState.noteUserInteraction();
        if (!this.running || this.socketConnected) {
            return;
        }
        scheduleFallback(Math.min(FIRST_POLL_MS, PerformanceProfile.livePollInterval(this.app, this.prefs)));
    }

    void acknowledge(String str, String str2) {
        if (str == null || str2 == null) return;
        this.baselineKey = str;
        this.baselineFingerprint = str2;
        saveBaseline(str, str2);
    }

    private void scheduleAuthCheck() {
        if (!this.running) {
            return;
        }
        this.main.removeCallbacks(this.authCheck);
        this.main.postDelayed(this.authCheck, AUTH_CHECK_MS);
    }

    private void checkRealtimeIdentity() {
        if (!this.running) {
            return;
        }
        String realtimeIdentity = realtimeIdentity();
        if (!realtimeIdentity.equals(this.socketIdentity)) {
            restartSocket("auth-or-cookie-changed");
        } else if (!this.socketConnected && !this.socketConnecting && RuntimeState.networkAvailable(this.app)) {
            scheduleReconnect(0L);
        }
        scheduleAuthCheck();
    }

    private String realtimeIdentity() {
        String str = "";
        try {
            Uri parse = Uri.parse(this.listener.currentUrl());
            if (ForumUrlRouter.isForumUrl(parse) && parse.getHost() != null) {
                str = parse.getHost().toLowerCase(Locale.US);
            }
        } catch (Throwable unused) {
        }
        String string = this.prefs.getString("session_user_id", "");
        if (string == null) {
            string = "";
        }
        String cookie = cookieForHost(str);
        return str + "|" + string.trim() + "|" + Integer.toHexString(cookie.hashCode());
    }

    private String cookieForHost(String str) {
        if (str == null || str.trim().isEmpty()) {
            return "";
        }
        try {
            String cookie = CookieManager.getInstance().getCookie("https://" + str + "/");
            return cookie == null ? "" : cookie;
        } catch (Throwable unused) {
            return "";
        }
    }

    private void restartSocket(String str) {
        this.socketGeneration++;
        this.socketConnected = false;
        this.socketConnecting = false;
        this.reconnectAttempts = 0;
        this.socketIdentity = realtimeIdentity();
        closeSocket();
        AppLogger.info(this.app, "live_update_socket", "restart | " + str);
        state(RuntimeState.networkAvailable(this.app) ? "SYNCING" : "OFFLINE");
        scheduleFallback(FIRST_POLL_MS);
        scheduleReconnect(0L);
    }

    private void closeSocket() {
        PushConnection pushConnection = this.socket;
        this.socket = null;
        if (pushConnection != null) {
            pushConnection.closeQuietly();
        }
    }

    private void scheduleReconnect(long j) {
        if (!this.running || this.socketConnected || this.socketConnecting) {
            return;
        }
        long max = Math.max(0L, j);
        this.main.removeCallbacks(this.reconnect);
        this.main.postDelayed(this.reconnect, max);
    }

    private long reconnectDelay() {
        int min = Math.min(this.reconnectAttempts, 5);
        long min2 = Math.min(RECONNECT_MAX_MS, RECONNECT_MIN_MS << min);
        return Math.min(RECONNECT_MAX_MS, min2 + RANDOM.nextInt(350));
    }

    private void connectSocket() {
        if (!this.running || this.socketConnected || this.socketConnecting) {
            return;
        }
        if (!RuntimeState.networkAvailable(this.app)) {
            state("OFFLINE");
            scheduleFallback(IDLE_ROUTE_POLL_MS);
            return;
        }
        final Uri parse;
        try {
            parse = Uri.parse(this.listener.currentUrl());
        } catch (Throwable unused) {
            scheduleFallback(IDLE_ROUTE_POLL_MS);
            return;
        }
        if (!ForumUrlRouter.isForumUrl(parse) || parse.getHost() == null) {
            state("PAUSED");
            scheduleFallback(IDLE_ROUTE_POLL_MS);
            return;
        }
        final String lowerCase = parse.getHost().toLowerCase(Locale.US);
        final int i = this.socketGeneration;
        this.socketIdentity = realtimeIdentity();
        this.socketConnecting = true;
        state("SYNCING");
        AppExecutors.network().execute(new Runnable() {
            @Override
            public void run() {
                LiveForumUpdater.this.runSocketSession(i, lowerCase);
            }
        });
    }

    private void runSocketSession(final int i, String str) {
        PushConnection pushConnection = null;
        String str2 = "closed";
        try {
            RealtimeConfig realtimeConfig = fetchRealtimeConfig(str);
            if (!this.running || i != this.socketGeneration) {
                return;
            }
            if (realtimeConfig.key.isEmpty()) {
                throw new IOException("Flarum Pusher extension is not configured");
            }
            pushConnection = new PushConnection(realtimeConfig, str);
            pushConnection.open();
            if (!this.running || i != this.socketGeneration) {
                pushConnection.closeQuietly();
                return;
            }
            this.socket = pushConnection;
            String socketId = awaitConnectionEstablished(pushConnection);
            subscribe(pushConnection, "public", "");
            if (!realtimeConfig.userId.isEmpty() && !realtimeConfig.csrfToken.isEmpty()) {
                try {
                    String auth = authorizePrivateChannel(realtimeConfig, socketId);
                    if (!auth.isEmpty()) {
                        subscribe(pushConnection, "private-user" + realtimeConfig.userId, auth);
                    }
                } catch (Throwable th) {
                    AppLogger.warn(this.app, "live_update_socket_auth", th.getClass().getSimpleName());
                }
            }
            final PushConnection connected = pushConnection;
            this.main.post(new Runnable() {
                @Override
                public void run() {
                    LiveForumUpdater.this.handleSocketConnected(i, connected);
                }
            });
            int heartbeatTimeouts = 0;
            while (this.running && i == this.socketGeneration) {
                try {
                    String text = pushConnection.readText();
                    heartbeatTimeouts = 0;
                    handlePusherMessage(pushConnection, text);
                } catch (SocketTimeoutException unused) {
                    heartbeatTimeouts++;
                    if (heartbeatTimeouts > 1) {
                        throw new IOException("Pusher heartbeat timeout");
                    }
                    pushConnection.sendPusherEvent("pusher:ping", new JSONObject());
                }
            }
        } catch (Throwable th2) {
            str2 = th2.getClass().getSimpleName() + ": " + String.valueOf(th2.getMessage());
        } finally {
            if (pushConnection != null) {
                pushConnection.closeQuietly();
            }
            final String reason = str2;
            this.main.post(new Runnable() {
                @Override
                public void run() {
                    LiveForumUpdater.this.handleSocketDisconnected(i, reason);
                }
            });
        }
    }

    private String awaitConnectionEstablished(PushConnection pushConnection) throws Exception {
        int i = 0;
        while (i < 8) {
            i++;
            String readText = pushConnection.readText();
            JSONObject message = new JSONObject(readText);
            String event = message.optString("event", "");
            if ("pusher:connection_established".equals(event)) {
                JSONObject data = jsonData(message);
                String socketId = data.optString("socket_id", "");
                int activityTimeout = data.optInt("activity_timeout", 60);
                pushConnection.setReadTimeout(Math.max(20000, Math.min(PUSH_READ_TIMEOUT_MS, activityTimeout * 1000)));
                if (socketId.isEmpty()) {
                    throw new IOException("Pusher socket id missing");
                }
                return socketId;
            }
            if ("pusher:error".equals(event)) {
                throw new IOException("Pusher connection error");
            }
        }
        throw new IOException("Pusher connection handshake timed out");
    }

    private void subscribe(PushConnection pushConnection, String channel, String auth) throws Exception {
        JSONObject data = new JSONObject();
        data.put("channel", channel);
        if (auth != null && !auth.isEmpty()) {
            data.put("auth", auth);
        }
        pushConnection.sendPusherEvent("pusher:subscribe", data);
    }

    private String authorizePrivateChannel(RealtimeConfig realtimeConfig, String socketId) throws Exception {
        String channel = "private-user" + realtimeConfig.userId;
        String body = "socket_id=" + URLEncoder.encode(socketId, "UTF-8") + "&channel_name=" + URLEncoder.encode(channel, "UTF-8");
        byte[] bytes = body.getBytes("UTF-8");
        HttpURLConnection connection = (HttpURLConnection) new URL("https://" + realtimeConfig.forumHost + "/api/pusher/auth").openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("X-CSRF-Token", realtimeConfig.csrfToken);
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " Realtime");
            String cookie = cookieForHost(realtimeConfig.forumHost);
            if (!cookie.isEmpty()) {
                connection.setRequestProperty("Cookie", cookie);
            }
            connection.setFixedLengthStreamingMode(bytes.length);
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(bytes);
            outputStream.flush();
            outputStream.close();
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Pusher auth HTTP " + code);
            }
            return new JSONObject(readLimited(connection.getInputStream())).optString("auth", "");
        } finally {
            connection.disconnect();
        }
    }

    private void handleSocketConnected(int generation, PushConnection pushConnection) {
        if (!this.running || generation != this.socketGeneration || this.socket != pushConnection) {
            pushConnection.closeQuietly();
            return;
        }
        this.socketConnecting = false;
        this.socketConnected = true;
        this.reconnectAttempts = 0;
        this.main.removeCallbacks(this.reconnect);
        this.main.removeCallbacks(this.tick);
        state("LIVE");
        AppLogger.info(this.app, "live_update_socket", "connected • push primary");
        HcfNotificationEngine.InstantNotificationService.requestImmediateSync(this.app);
        schedulePushedRefresh(0L);
    }

    private void handleSocketDisconnected(int generation, String reason) {
        if (generation != this.socketGeneration) {
            return;
        }
        this.socket = null;
        this.socketConnecting = false;
        this.socketConnected = false;
        if (!this.running) {
            return;
        }
        this.reconnectAttempts = Math.min(this.reconnectAttempts + 1, 6);
        state(RuntimeState.networkAvailable(this.app) ? "WAITING" : "OFFLINE");
        if (this.reconnectAttempts == 1 || this.reconnectAttempts == 3) {
            AppLogger.warn(this.app, "live_update_socket", "fallback polling | " + reason);
        }
        scheduleFallback(0L);
        scheduleReconnect(reconnectDelay());
    }

    private void handlePusherMessage(PushConnection pushConnection, String text) throws Exception {
        JSONObject message = new JSONObject(text);
        String event = message.optString("event", "");
        if ("pusher:ping".equals(event)) {
            pushConnection.sendPusherEvent("pusher:pong", new JSONObject());
            return;
        }
        if ("pusher:error".equals(event)) {
            throw new IOException("Pusher protocol error");
        }
        if (!"newPost".equals(event) && !"notification".equals(event)) {
            return;
        }
        final String pushEvent = event;
        final JSONObject data = jsonData(message);
        this.main.post(new Runnable() {
            @Override
            public void run() {
                LiveForumUpdater.this.handlePushEvent(pushEvent, data);
            }
        });
    }

    private JSONObject jsonData(JSONObject message) {
        Object data = message.opt("data");
        if (data instanceof JSONObject) {
            return (JSONObject) data;
        }
        if (data instanceof String) {
            try {
                return new JSONObject((String) data);
            } catch (Throwable unused) {
            }
        }
        return new JSONObject();
    }

    private void handlePushEvent(String event, JSONObject data) {
        if (!this.running || !this.socketConnected) {
            return;
        }
        if ("notification".equals(event)) {
            NotificationHelper.postFromPushPayload(this.app, data);
            HcfNotificationEngine.InstantNotificationService.requestImmediateSync(this.app);
        }
        if (eventAffectsCurrentRoute(event, data)) {
            schedulePushedRefresh(0L);
        }
    }

    private boolean eventAffectsCurrentRoute(String event, JSONObject data) {
        try {
            Uri uri = Uri.parse(this.listener.currentUrl());
            if (!ForumUrlRouter.isForumUrl(uri)) {
                return false;
            }
            String path = uri.getPath() == null ? "/" : uri.getPath();
            String lower = path.toLowerCase(Locale.US);
            if (lower.startsWith("/notifications")) {
                return "notification".equals(event);
            }
            if (!"newPost".equals(event)) {
                return false;
            }
            if (lower.startsWith("/d/")) {
                String discussionId = discussionIdFromPath(path);
                String eventDiscussionId = data.optString("discussionId", "");
                return discussionId.isEmpty() || eventDiscussionId.isEmpty() || discussionId.equals(eventDiscussionId);
            }
            return "/".equals(lower) || lower.startsWith("/all") || lower.startsWith("/following") || lower.startsWith("/tags") || lower.startsWith("/t/");
        } catch (Throwable unused) {
            return false;
        }
    }

    private String discussionIdFromPath(String path) {
        if (path == null || path.length() < 4) {
            return "";
        }
        String value = path.substring(3);
        int slash = value.indexOf(47);
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        int dash = value.indexOf(45);
        if (dash >= 0) {
            value = value.substring(0, dash);
        }
        return value.matches("[0-9]+") ? value : "";
    }

    private void schedulePushedRefresh(long delayMs) {
        if (!this.running || !this.socketConnected) {
            return;
        }
        this.main.removeCallbacks(this.pushedRefresh);
        this.main.postDelayed(this.pushedRefresh, Math.max(0L, delayMs));
    }

    private RealtimeConfig fetchRealtimeConfig(String host) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://" + host + "/api").openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/vnd.api+json, application/json");
            connection.setRequestProperty("Cache-Control", "no-cache, max-age=0");
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " RealtimeBootstrap");
            String cookie = cookieForHost(host);
            if (!cookie.isEmpty()) {
                connection.setRequestProperty("Cookie", cookie);
            }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Flarum realtime bootstrap HTTP " + code);
            }
            JSONObject root = new JSONObject(readLimited(connection.getInputStream()));
            JSONObject data = root.optJSONObject("data");
            JSONObject attributes = data == null ? null : data.optJSONObject("attributes");
            RealtimeConfig config = new RealtimeConfig();
            config.forumHost = host;
            config.key = attributes == null ? "" : attributes.optString("pusherKey", "").trim();
            config.cluster = attributes == null ? "" : attributes.optString("pusherCluster", "").trim().toLowerCase(Locale.US);
            if (config.cluster.isEmpty()) {
                config.cluster = "mt1";
            }
            if (!config.cluster.matches("[a-z0-9-]+")) {
                throw new IOException("Invalid Pusher cluster");
            }
            config.csrfToken = header(connection, "X-CSRF-Token");
            String userId = this.prefs.getString("session_user_id", "");
            config.userId = userId == null ? "" : userId.trim();
            return config;
        } finally {
            connection.disconnect();
        }
    }

    private String header(HttpURLConnection connection, String name) {
        String value = connection.getHeaderField(name);
        return value == null ? "" : value.trim();
    }

    private void scheduleFallback(long delayMs) {
        if (!this.running || this.socketConnected) {
            return;
        }
        long delay = Math.max(0L, delayMs);
        RuntimeDiagnostics.livePoll(delay);
        this.main.removeCallbacks(this.tick);
        this.main.postDelayed(this.tick, delay);
    }

    public void poll() {
        refreshCurrentRoute(true, false);
    }

    private void refreshCurrentRoute(final boolean fallbackPoll, final boolean forceRefresh) {
        if (!this.running || (fallbackPoll && this.socketConnected)) {
            return;
        }
        if (this.inFlight) {
            this.queuedRefresh = true;
            this.forceQueuedRefresh |= forceRefresh;
            return;
        }
        if (!RuntimeState.networkAvailable(this.app)) {
            state("OFFLINE");
            if (fallbackPoll) {
                scheduleFallback(IDLE_ROUTE_POLL_MS);
            }
            return;
        }
        try {
            Uri uri = Uri.parse(this.listener.currentUrl());
            if (!ForumUrlRouter.isForumUrl(uri)) {
                state("PAUSED");
                if (fallbackPoll) {
                    scheduleFallback(IDLE_ROUTE_POLL_MS);
                }
                return;
            }
            final String endpoint = endpointFor(uri);
            final String key = pageKey(uri, endpoint);
            if (key == null || endpoint == null) {
                if (this.socketConnected) {
                    state("LIVE");
                }
                if (fallbackPoll) {
                    scheduleFallback(IDLE_ROUTE_POLL_MS);
                }
                return;
            }
            this.inFlight = true;
            AppExecutors.network().execute(new Runnable() {
                @Override
                public void run() {
                    LiveForumUpdater.this.fetchRouteFingerprint(endpoint, key, fallbackPoll, forceRefresh);
                }
            });
        } catch (Throwable unused) {
            if (fallbackPoll) {
                scheduleFallback(IDLE_ROUTE_POLL_MS);
            }
        }
    }

    private void fetchRouteFingerprint(String endpoint, final String key, final boolean fallbackPoll, boolean forceRefresh) {
        final String fingerprint;
        try {
            fingerprint = fetchFingerprint(endpoint, forceRefresh);
        } catch (Throwable th) {
            if (this.failures == 0 || this.failures == 2) {
                AppLogger.warn(this.app, fallbackPoll ? "live_update_poll" : "live_update_push_refresh", th.getClass().getSimpleName());
            }
            this.main.post(new Runnable() {
                @Override
                public void run() {
                    LiveForumUpdater.this.handleFingerprintResult(key, null, fallbackPoll);
                }
            });
            return;
        }
        this.main.post(new Runnable() {
            @Override
            public void run() {
                LiveForumUpdater.this.handleFingerprintResult(key, fingerprint, fallbackPoll);
            }
        });
    }

    private void handleFingerprintResult(String key, String fingerprint, boolean fallbackPoll) {
        String previous;
        this.inFlight = false;
        if (!this.running) {
            return;
        }
        if (fingerprint == null || fingerprint.isEmpty()) {
            this.failures = Math.min(this.failures + 1, 5);
            if (!this.socketConnected) {
                state(RuntimeState.networkAvailable(this.app) ? "WAITING" : "OFFLINE");
                if (fallbackPoll) {
                    scheduleFallback(Math.min(4000L, 500L << Math.min(Math.max(0, this.failures - 1), 3)));
                }
            }
        } else {
            this.failures = 0;
            state("LIVE");
            if (!key.equals(this.baselineKey) || (previous = this.baselineFingerprint) == null) {
                previous = loadBaseline(key);
                this.baselineKey = key;
                this.baselineFingerprint = previous == null ? fingerprint : previous;
                if (previous == null) saveBaseline(key, fingerprint);
            }
            previous = this.baselineFingerprint;
            if (previous != null && !fingerprint.equals(previous)) {
                this.listener.onChangeCandidate(key, fingerprint);
            }
            if (fallbackPoll && !this.socketConnected) {
                scheduleFallback(PerformanceProfile.livePollInterval(this.app, this.prefs));
            }
        }
        if (this.queuedRefresh) {
            boolean force = this.forceQueuedRefresh;
            this.queuedRefresh = false;
            this.forceQueuedRefresh = false;
            refreshCurrentRoute(!this.socketConnected, force);
        }
    }

    private void state(String state) {
        if (state.equals(this.lastState)) return;
        String previous = this.lastState == null || this.lastState.isEmpty() ? "NONE" : this.lastState;
        this.lastState = state;
        AppLogger.info(this.app, "live_update_state", previous + " -> " + state);
        this.listener.onStateChanged(state);
    }

    private String baselinePrefKey(String key) {
        if (key == null) return "";
        return "live_fp_" + Integer.toHexString(key.hashCode());
    }

    private String loadBaseline(String key) {
        try {
            String value = this.prefs.getString(baselinePrefKey(key), null);
            return value == null || value.isEmpty() ? null : value;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void saveBaseline(String key, String fingerprint) {
        if (key == null || fingerprint == null || fingerprint.isEmpty()) return;
        try {
            this.prefs.edit().putString(baselinePrefKey(key), fingerprint).apply();
        } catch (Throwable ignored) {
        }
    }

    private String endpointFor(Uri uri) {
        String host = uri.getHost();
        if (host == null) {
            return null;
        }
        String path = uri.getPath() == null ? "/" : uri.getPath();
        String lower = path.toLowerCase(Locale.US);
        String root = "https://" + host;
        if (lower.startsWith("/d/")) {
            String discussionId = discussionIdFromPath(path);
            if (!discussionId.isEmpty()) {
                return root + "/api/discussions/" + discussionId + "?fields%5Bdiscussions%5D=lastPostedAt%2ClastPostNumber%2CcommentCount";
            }
        }
        if ("/".equals(lower) || lower.startsWith("/all") || lower.startsWith("/following") || lower.startsWith("/tags") || lower.startsWith("/t/")) {
            return root + "/api/discussions?sort=-lastPostedAt&page%5Blimit%5D=1&fields%5Bdiscussions%5D=lastPostedAt%2ClastPostNumber%2CcommentCount";
        }
        if (!lower.startsWith("/notifications")) {
            return null;
        }
        return root + "/api/notifications?page%5Blimit%5D=1";
    }

    private String pageKey(Uri uri, String endpoint) {
        if (endpoint == null) {
            return null;
        }
        String path = uri.getPath() == null ? "/" : uri.getPath();
        StringBuilder sb = new StringBuilder();
        sb.append(uri.getHost() == null ? "" : uri.getHost());
        sb.append("|");
        sb.append(path);
        sb.append("|");
        sb.append(endpoint);
        return sb.toString();
    }

    private String fetchFingerprint(String endpoint, boolean forceRefresh) throws Exception {
        CacheEntry cacheEntry = this.cache.get(endpoint);
        URL url = new URL(endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/vnd.api+json, application/json");
            connection.setRequestProperty("Cache-Control", "no-cache, max-age=0");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " LiveUpdate");
            if (!forceRefresh && cacheEntry != null) {
                if (!cacheEntry.etag.isEmpty()) {
                    connection.setRequestProperty("If-None-Match", cacheEntry.etag);
                }
                if (cacheEntry.lastModified > 0) {
                    connection.setIfModifiedSince(cacheEntry.lastModified);
                }
            }
            String cookie = CookieManager.getInstance().getCookie(url.getProtocol() + "://" + url.getHost() + "/");
            if (cookie != null && !cookie.trim().isEmpty()) {
                connection.setRequestProperty("Cookie", cookie);
            }
            int code = connection.getResponseCode();
            if (code == 304 && cacheEntry != null) {
                return cacheEntry.signature;
            }
            if (code < 200 || code >= 300) {
                return null;
            }
            String signature = compactSignature(readLimited(connection.getInputStream()));
            if (signature != null && !signature.isEmpty()) {
                CacheEntry next = new CacheEntry();
                String etag = connection.getHeaderField("ETag");
                next.etag = etag == null ? "" : etag;
                next.lastModified = connection.getLastModified();
                next.signature = signature;
                this.cache.put(endpoint, next);
                return signature;
            }
            return null;
        } finally {
            connection.disconnect();
        }
    }

    private String compactSignature(String body) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try {
            Object data = new JSONObject(body).opt("data");
            StringBuilder sb = new StringBuilder(160);
            if (data instanceof JSONObject) {
                appendResourceSignature(sb, (JSONObject) data);
            } else if (data instanceof JSONArray) {
                JSONArray array = (JSONArray) data;
                for (int i = 0; i < array.length() && i < 2; i++) {
                    JSONObject resource = array.optJSONObject(i);
                    if (resource != null) {
                        appendResourceSignature(sb, resource);
                    }
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        } catch (Throwable unused) {
        }
        return "fallback|" + body.length() + "|" + Integer.toHexString(body.substring(0, Math.min(body.length(), 32768)).hashCode());
    }

    private void appendResourceSignature(StringBuilder sb, JSONObject resource) {
        sb.append(resource.optString("type", ""));
        sb.append(':');
        sb.append(resource.optString("id", ""));
        JSONObject attributes = resource.optJSONObject("attributes");
        if (attributes != null) {
            appendAttr(sb, attributes, "lastPostNumber");
            appendAttr(sb, attributes, "lastPostedAt");
            appendAttr(sb, attributes, "commentCount");
            appendAttr(sb, attributes, "newNotificationCount");
            appendAttr(sb, attributes, "unreadNotificationCount");
            appendAttr(sb, attributes, "time");
            appendAttr(sb, attributes, "createdAt");
            appendAttr(sb, attributes, "isRead");
            appendAttr(sb, attributes, "type");
        }
        sb.append('|');
    }

    private void appendAttr(StringBuilder sb, JSONObject attributes, String name) {
        Object value;
        if (!attributes.has(name) || (value = attributes.opt(name)) == null || value == JSONObject.NULL) {
            return;
        }
        sb.append(';');
        sb.append(name);
        sb.append('=');
        sb.append(String.valueOf(value));
    }

    private String readLimited(InputStream inputStream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[4096];
        while (true) {
            int read = reader.read(buffer);
            if (read == -1 || sb.length() >= MAX_BODY_CHARS) {
                break;
            }
            sb.append(buffer, 0, Math.min(read, MAX_BODY_CHARS - sb.length()));
        }
        reader.close();
        return sb.toString();
    }

    private static final class RealtimeConfig {
        String cluster = "mt1";
        String csrfToken = "";
        String forumHost = "";
        String key = "";
        String userId = "";
    }

    private static final class PushConnection {
        private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        private final RealtimeConfig config;
        private final String originHost;
        private InputStream input;
        private OutputStream output;
        private SSLSocket socket;

        PushConnection(RealtimeConfig realtimeConfig, String originHost) {
            this.config = realtimeConfig;
            this.originHost = originHost;
        }

        void open() throws Exception {
            String wsHost = "ws-" + this.config.cluster + ".pusher.com";
            String path = "/app/" + this.config.key + "?protocol=7&client=android-hcf&version=1.0&flash=false";
            Socket rawSocket = new Socket();
            rawSocket.connect(new InetSocketAddress(wsHost, 443), CONNECT_TIMEOUT_MS);
            SSLSocket sslSocket = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(rawSocket, wsHost, 443, true);
            this.socket = sslSocket;
            sslSocket.setSoTimeout(PUSH_READ_TIMEOUT_MS);
            this.socket.startHandshake();
            this.input = this.socket.getInputStream();
            this.output = this.socket.getOutputStream();
            byte[] nonce = new byte[16];
            RANDOM.nextBytes(nonce);
            String webSocketKey = Base64.encodeToString(nonce, Base64.NO_WRAP);
            StringBuilder request = new StringBuilder();
            request.append("GET ").append(path).append(" HTTP/1.1\r\n");
            request.append("Host: ").append(wsHost).append("\r\n");
            request.append("Upgrade: websocket\r\n");
            request.append("Connection: Upgrade\r\n");
            request.append("Sec-WebSocket-Key: ").append(webSocketKey).append("\r\n");
            request.append("Sec-WebSocket-Version: 13\r\n");
            request.append("Origin: https://").append(this.originHost).append("\r\n");
            request.append("User-Agent: HarleysClanForumApp/1.0 Realtime\r\n\r\n");
            this.output.write(request.toString().getBytes("UTF-8"));
            this.output.flush();
            String status = readHttpLine(this.input);
            if (status == null || status.indexOf(" 101 ") < 0) {
                throw new IOException("WebSocket upgrade failed: " + status);
            }
            String accept = "";
            while (true) {
                String header = readHttpLine(this.input);
                if (header == null || header.isEmpty()) {
                    break;
                }
                int colon = header.indexOf(58);
                if (colon > 0 && "sec-websocket-accept".equals(header.substring(0, colon).trim().toLowerCase(Locale.US))) {
                    accept = header.substring(colon + 1).trim();
                }
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            String expected = Base64.encodeToString(digest.digest((webSocketKey + WS_GUID).getBytes("UTF-8")), Base64.NO_WRAP);
            if (!expected.equals(accept)) {
                throw new IOException("WebSocket accept mismatch");
            }
        }

        void setReadTimeout(int timeoutMs) throws Exception {
            SSLSocket sslSocket = this.socket;
            if (sslSocket != null) {
                sslSocket.setSoTimeout(timeoutMs);
            }
        }

        synchronized void sendPusherEvent(String event, JSONObject data) throws Exception {
            JSONObject message = new JSONObject();
            message.put("event", event);
            message.put("data", data);
            sendText(message.toString());
        }

        synchronized void sendText(String text) throws Exception {
            sendFrame(1, text.getBytes("UTF-8"));
        }

        private synchronized void sendControl(int opcode, byte[] payload) throws Exception {
            sendFrame(opcode, payload);
        }

        private void sendFrame(int opcode, byte[] payload) throws Exception {
            OutputStream outputStream = this.output;
            if (outputStream == null) {
                throw new EOFException("WebSocket closed");
            }
            if (payload == null) {
                payload = new byte[0];
            }
            if (payload.length > MAX_WS_PAYLOAD_BYTES) {
                throw new IOException("WebSocket payload too large");
            }
            outputStream.write(128 | (opcode & 15));
            int length = payload.length;
            if (length < 126) {
                outputStream.write(128 | length);
            } else if (length <= 65535) {
                outputStream.write(254);
                outputStream.write((length >>> 8) & 255);
                outputStream.write(length & 255);
            } else {
                outputStream.write(255);
                long frameLength = length;
                for (int i = 7; i >= 0; i--) {
                    outputStream.write((int) ((frameLength >>> (i * 8)) & 255));
                }
            }
            byte[] mask = new byte[4];
            RANDOM.nextBytes(mask);
            outputStream.write(mask);
            for (int i = 0; i < payload.length; i++) {
                outputStream.write(payload[i] ^ mask[i & 3]);
            }
            outputStream.flush();
        }

        String readText() throws Exception {
            ByteArrayOutputStream fragments = null;
            int fragmentedOpcode = 0;
            while (true) {
                int first = readRequired(this.input);
                int second = readRequired(this.input);
                boolean fin = (first & 128) != 0;
                int opcode = first & 15;
                boolean masked = (second & 128) != 0;
                long length = second & 127;
                if (length == 126) {
                    length = (readRequired(this.input) << 8) | readRequired(this.input);
                } else if (length == 127) {
                    length = 0;
                    for (int i = 0; i < 8; i++) {
                        length = (length << 8) | readRequired(this.input);
                    }
                }
                if (length < 0 || length > MAX_WS_PAYLOAD_BYTES) {
                    throw new IOException("WebSocket payload exceeds limit");
                }
                byte[] mask = null;
                if (masked) {
                    mask = new byte[4];
                    readFully(this.input, mask);
                }
                byte[] payload = new byte[(int) length];
                readFully(this.input, payload);
                if (mask != null) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] = (byte) (payload[i] ^ mask[i & 3]);
                    }
                }
                if (opcode == 8) {
                    throw new EOFException("WebSocket closed by peer");
                }
                if (opcode == 9) {
                    sendControl(10, payload);
                    continue;
                }
                if (opcode == 10) {
                    continue;
                }
                if (opcode == 1) {
                    fragments = new ByteArrayOutputStream(Math.max(64, payload.length));
                    fragments.write(payload);
                    fragmentedOpcode = 1;
                } else if (opcode == 0 && fragments != null && fragmentedOpcode == 1) {
                    fragments.write(payload);
                } else {
                    continue;
                }
                if (fragments.size() > MAX_WS_PAYLOAD_BYTES) {
                    throw new IOException("WebSocket fragmented payload exceeds limit");
                }
                if (fin) {
                    return new String(fragments.toByteArray(), "UTF-8");
                }
            }
        }

        void closeQuietly() {
            try {
                SSLSocket sslSocket = this.socket;
                if (sslSocket != null) {
                    sslSocket.close();
                }
            } catch (Throwable unused) {
            }
            this.socket = null;
            this.input = null;
            this.output = null;
        }

        private static int readRequired(InputStream inputStream) throws Exception {
            if (inputStream == null) {
                throw new EOFException("WebSocket input closed");
            }
            int value = inputStream.read();
            if (value < 0) {
                throw new EOFException("WebSocket EOF");
            }
            return value;
        }

        private static void readFully(InputStream inputStream, byte[] bytes) throws Exception {
            int offset = 0;
            while (offset < bytes.length) {
                int read = inputStream.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    throw new EOFException("WebSocket EOF");
                }
                offset += read;
            }
        }

        private static String readHttpLine(InputStream inputStream) throws Exception {
            StringBuilder sb = new StringBuilder();
            int previous = -1;
            while (sb.length() < 8192) {
                int value = inputStream.read();
                if (value < 0) {
                    return sb.length() == 0 ? null : sb.toString();
                }
                if (previous == 13 && value == 10) {
                    sb.setLength(Math.max(0, sb.length() - 1));
                    return sb.toString();
                }
                sb.append((char) value);
                previous = value;
            }
            throw new IOException("WebSocket HTTP header line too long");
        }
    }

    private static final class CacheEntry {
        String etag = "";
        long lastModified;
        String signature = "";
    }
}


// ---- RemoteDomainConfig.java ----
/** Pulls the trusted forum-domain registry from the hcf-app main branch. */
final class RemoteDomainConfig {
    static final String CONFIG_URL = BuildInfo.REMOTE_DOMAIN_CONFIG;

    private static final String PREFS = "hcf_remote_domains";
    private static final String KEY_RAW = "raw";
    private static final String KEY_ETAG = "etag";
    private static final String KEY_FETCHED_AT = "fetched_at";
    private static final long REFRESH_MS = 60L * 60L * 1000L;
    private static final int MAX_BYTES = 16 * 1024;

    static void initialize(Context context) {
        final Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String cached = prefs.getString(KEY_RAW, null);
        if (cached != null) {
            Parsed parsed = parse(cached);
            if (parsed != null) ForumConfig.applyRemote(parsed.primary, parsed.backups);
        }

        long age = System.currentTimeMillis() - prefs.getLong(KEY_FETCHED_AT, 0L);
        if (age >= 0L && age < REFRESH_MS) return;

        AppExecutors.network().execute(new Runnable() {
            @Override public void run() {
                refresh(app);
            }
        });
    }

    static void refresh(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(CONFIG_URL).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "text/plain, text/*;q=0.9, */*;q=0.1");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " DomainConfig/" + BuildInfo.VERSION_CODE);
            String etag = prefs.getString(KEY_ETAG, null);
            if (etag != null && !etag.isEmpty()) connection.setRequestProperty("If-None-Match", etag);

            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                prefs.edit().putLong(KEY_FETCHED_AT, System.currentTimeMillis()).apply();
                return;
            }
            if (status != HttpURLConnection.HTTP_OK) throw new IllegalStateException("HTTP " + status);

            String raw = readLimited(connection.getInputStream());
            Parsed parsed = parse(raw);
            if (parsed == null) throw new IllegalArgumentException("Invalid domains.config");

            ForumConfig.applyRemote(parsed.primary, parsed.backups);
            SharedPreferences.Editor edit = prefs.edit()
                    .putString(KEY_RAW, raw)
                    .putLong(KEY_FETCHED_AT, System.currentTimeMillis());
            String newEtag = connection.getHeaderField("ETag");
            if (newEtag != null && !newEtag.isEmpty()) edit.putString(KEY_ETAG, newEtag);
            edit.apply();
            AppLogger.info(app, "domain_config", "github-main | primary=" + parsed.primary + " | hosts=" + ForumConfig.FORUM_HOSTS.size());
        } catch (Throwable error) {
            AppLogger.warn(app, "domain_config", "fallback | " + error.getClass().getSimpleName());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static Parsed parse(String raw) {
        if (raw == null || raw.length() > MAX_BYTES) return null;
        String section = "";
        String primary = null;
        Set<String> backups = new LinkedHashSet<>();
        Set<String> all = new LinkedHashSet<>();

        try {
            BufferedReader reader = new BufferedReader(new java.io.StringReader(raw));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1).trim().toLowerCase(Locale.US);
                    continue;
                }
                int equals = line.indexOf('=');
                if (equals <= 0) continue;
                String key = line.substring(0, equals).trim().toLowerCase(Locale.US);
                String value = line.substring(equals + 1).trim();
                if ("primary".equals(section) && "domain".equals(key)) {
                    primary = normalizeHost(value);
                    if (primary == null) return null;
                    all.add(primary);
                } else if ("backups".equals(section) && key.startsWith("domain")) {
                    String host = normalizeHost(value);
                    if (host == null) return null;
                    if (all.add(host)) backups.add(host);
                }
            }
        } catch (Throwable ignored) {
            return null;
        }

        if (primary == null || primary.isEmpty()) return null;
        return new Parsed(primary, new ArrayList<>(backups));
    }

    private static String normalizeHost(String value) {
        if (value == null) return null;
        String host = value.trim().toLowerCase(Locale.US);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.isEmpty() || host.length() > 253) return null;
        if (host.contains("://") || host.contains("/") || host.contains("\\") || host.contains(":") || host.contains("@") || host.contains("?" ) || host.contains("#")) return null;
        try {
            host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.US);
        } catch (Throwable ignored) {
            return null;
        }
        if (!(host.equals("harleytg.com") || host.endsWith(".harleytg.com") || host.equals("freeflarum.com") || host.endsWith(".freeflarum.com"))) return null;
        return host;
    }

    private static String readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) throw new IllegalArgumentException("domains.config too large");
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    static final class Parsed {
        final String primary;
        final List<String> backups;
        Parsed(String primary, List<String> backups) {
            this.primary = primary;
            this.backups = backups;
        }
    }

    private RemoteDomainConfig() {}
}


// ---- AppDomainRouter.java ----
/**
 * Local in-app URL namespace for Harley's Clan Forum.
 *
 * Format:
 *   app.forum.harleytg.com/<item>
 *
 * These addresses are intercepted by the Android app. They are not treated as
 * real forum hosts and are never sent to DNS/the network.
 */
final class AppDomainRouter {
    public static final String APP_HOST = "app.forum.harleytg.com";

    private AppDomainRouter() {
    }

    /**
     * Installs a very small inflater hook before MainActivity inflates its UI.
     * Only R.id.currentUrlText is replaced; every other view is left to the
     * normal Android inflater.
     */
    static void installAddressBarInflater(final Activity activity) {
        if (activity == null) {
            return;
        }

        try {
            final LayoutInflater inflater = activity.getLayoutInflater();
            if (inflater == null || inflater.getFactory2() != null || inflater.getFactory() != null) {
                return;
            }

            inflater.setFactory2(new LayoutInflater.Factory2() {
                @Override
                public View onCreateView(View parent, String name, android.content.Context context, AttributeSet attrs) {
                    return createAddressBarIfNeeded(name, context, attrs);
                }

                @Override
                public View onCreateView(String name, android.content.Context context, AttributeSet attrs) {
                    return createAddressBarIfNeeded(name, context, attrs);
                }
            });
        } catch (Throwable error) {
            try {
                AppLogger.warn(activity, "app_domain_inflater", error.getClass().getSimpleName());
            } catch (Throwable ignored) {
            }
        }
    }

    private static View createAddressBarIfNeeded(
            String name,
            android.content.Context context,
            AttributeSet attrs
    ) {
        if (context == null || attrs == null || name == null) {
            return null;
        }

        int id = attrs.getAttributeResourceValue(
                "http://schemas.android.com/apk/res/android", "id", 0);
        if (id == R.id.currentUrlText && ("EditText".equals(name) || "android.widget.EditText".equals(name)))
            return new AppDomainEditText(context, attrs);
        if (id == R.id.urlBackButton && ("ImageButton".equals(name) || "android.widget.ImageButton".equals(name)))
            return new UrlBackButton(context, attrs);
        return null;
    }

    public static boolean isAppUrl(String rawInput) {
        Uri uri = parse(rawInput);
        return uri != null
                && uri.getHost() != null
                && APP_HOST.equalsIgnoreCase(uri.getHost());
    }

    /**
     * Handles app.forum.harleytg.com/<item>.
     *
     * @return true when this was an app-domain URL and normal URL handling
     * should stop.
     */
    public static boolean handle(Activity activity, String rawInput) {
        if (activity == null || !isAppUrl(rawInput)) {
            return false;
        }

        Uri uri = parse(rawInput);
        String item = firstPathItem(uri);

        try {
            switch (item) {
                case "":
                case "home":
                case "menu":
                    showMenu(activity);
                    break;

                case "settings":
                case "preferences":
                case "appearance":
                case "updates":
                case "advanced":
                case "about":
                    open(activity, HcfSubActivities.SettingsActivity.class, item.isEmpty() ? "settings" : item);
                    break;

                case "identity":
                case "account":
                    open(activity, HcfSubActivities.IdentityActivity.class, "identity");
                    break;

                case "setup":
                case "app-setup":
                    open(activity, HcfMainActivities.SetupActivity.class, "setup");
                    break;

                case "welcome":
                    open(activity, HcfMainActivities.WelcomeActivity.class, "welcome");
                    break;

                case "logs":
                case "diagnostics":
                case "crash-logs":
                    open(activity, HcfSubActivities.LogsActivity.class, "logs");
                    break;

                case "support":
                case "contact":
                case "contact-support":
                    open(activity, HcfSubActivities.SupportContactActivity.class, "support");
                    break;

                case "cookies":
                case "cookie-manager":
                    open(activity, HcfMainActivities.CookieManagerActivity.class, "cookies");
                    break;

                case "notification-settings":
                case "alert-settings":
                    openNotificationSettings(activity);
                    break;

                case "permissions":
                case "app-info":
                case "android-settings":
                    openAndroidAppInfo(activity);
                    break;

                case "forum":
                    loadForumPath(activity, "/", "forum_home");
                    break;

                case "browse":
                case "all":
                    loadForumPath(activity, "/all", "browse");
                    break;

                case "notifications":
                case "alerts":
                    loadForumPath(activity, "/notifications", "notifications");
                    try {
                        HcfNotificationEngine.InstantNotificationService.requestImmediateSync(activity);
                    } catch (Throwable ignored) {
                    }
                    break;

                case "profile":
                case "my-profile":
                    openForumProfile(activity);
                    break;

                case "compose":
                case "new":
                case "new-discussion":
                    loadForumPath(activity, "/compose", "compose");
                    break;

                case "downloads":
                    loadForumPath(activity, "/p/14-downloads", "downloads");
                    break;

                case "forum-support":
                case "support-page":
                    loadForumPath(activity, "/p/17-support", "forum_support");
                    break;

                default:
                    showUnknownItem(activity, item);
                    break;
            }

            return true;
        } catch (Throwable error) {
            try {
                AppLogger.error(activity, "app_domain", item + " | " + error.getClass().getSimpleName());
            } catch (Throwable ignored) {
            }

            Toast.makeText(
                    activity,
                    "Unable to open app item: " + item,
                    Toast.LENGTH_SHORT
            ).show();
            return true;
        }
    }

    private static void showMenu(final Activity activity) {
        final String[] labels = {
                "App Settings",
                "Account & Identity",
                "App Setup",
                "Welcome Screen",
                "Logs & Diagnostics",
                "Contact Support",
                "Cookie Manager",
                "Notification Settings",
                "Android App Info",
                "Forum Home",
                "Browse Discussions",
                "Notifications",
                "My Profile",
                "New Discussion",
                "Downloads"
        };

        final String[] items = {
                "settings",
                "identity",
                "setup",
                "welcome",
                "logs",
                "support",
                "cookies",
                "notification-settings",
                "app-info",
                "forum",
                "browse",
                "notifications",
                "profile",
                "compose",
                "downloads"
        };

        new AlertDialog.Builder(activity)
                .setTitle("Harley's Clan Forum App")
                .setMessage(APP_HOST)
                .setItems(labels, (dialog, which) -> {
                    if (which >= 0 && which < items.length) {
                        handle(activity, APP_HOST + "/" + items[which]);
                    }
                })
                .setNegativeButton("Close", null)
                .show();

        AppLogger.info(activity, "app_domain", "menu");
    }

    private static void open(Activity activity, Class<?> target, String destination) {
        activity.startActivity(new Intent(activity, target));
        AppLogger.info(activity, "app_domain", destination);
    }

    private static void openNotificationSettings(Activity activity) {
        try {
            NotificationHelper.createChannel(activity);
            NotificationHelper.openChannelSettings(activity);
            AppLogger.info(activity, "app_domain", "notification_settings");
        } catch (Throwable error) {
            AppLogger.warn(activity, "app_domain_notification_settings", error.getClass().getSimpleName());
            openAndroidAppInfo(activity);
        }
    }

    private static void openAndroidAppInfo(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
        AppLogger.info(activity, "app_domain", "android_app_info");
    }

    private static void openForumProfile(Activity activity) {
        ForumIdentity.Snapshot snapshot = ForumIdentity.load(activity);

        if (snapshot == null || !snapshot.loggedIn) {
            loadForumPath(activity, "/login", "profile_login");
            return;
        }

        String slug = snapshot.slug;
        if (slug == null || slug.trim().isEmpty()) {
            slug = snapshot.username;
        }

        if (slug == null || slug.trim().isEmpty()) {
            Toast.makeText(activity, "Your profile is still syncing.", Toast.LENGTH_SHORT).show();
            try {
                HcfNotificationEngine.InstantNotificationService.requestImmediateSync(activity);
            } catch (Throwable ignored) {
            }
            return;
        }

        loadForumPath(activity, "/u/" + Uri.encode(slug.trim()), "profile");
    }

    private static void loadForumPath(Activity activity, String path, String destination) {
        WebView webView = activity.findViewById(R.id.webView);
        if (webView == null) {
            Toast.makeText(activity, "Forum view is unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }

        String host = activeForumHost(activity);
        String safePath = path == null || path.trim().isEmpty() ? "/" : path.trim();
        if (!safePath.startsWith("/") || safePath.startsWith("//")) {
            safePath = "/";
        }

        String url = "https://" + host + safePath;
        webView.loadUrl(url);
        AppLogger.info(activity, "app_domain", destination + " | " + AppLogger.safeUrl(url));
    }

    private static String activeForumHost(Activity activity) {
        String host = "";
        try {
            SharedPreferences prefs = activity.getSharedPreferences("hcf_app", 0);
            host = prefs.getString("active_host", "");
        } catch (Throwable ignored) {
        }

        if (host != null) {
            host = host.trim().toLowerCase(Locale.US);
            if (ForumConfig.FORUM_HOSTS.contains(host)) {
                return host;
            }
        }

        String primary = ForumConfig.PRIMARY_HOST;
        if (primary == null || primary.trim().isEmpty()) {
            primary = ForumConfig.BUILTIN_PRIMARY_HOST;
        }
        return primary.toLowerCase(Locale.US);
    }

    private static Uri parse(String rawInput) {
        if (rawInput == null) {
            return null;
        }

        String value = rawInput.trim();
        if (value.isEmpty()) {
            return null;
        }

        try {
            if (!value.contains("://")) {
                value = "https://" + value;
            }
            return Uri.parse(value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Only the first item after app.forum.harleytg.com/ is the command. */
    private static String firstPathItem(Uri uri) {
        if (uri == null) {
            return "";
        }

        String path = uri.getPath();
        if (path == null || path.trim().isEmpty() || "/".equals(path)) {
            return "";
        }

        String value = path.trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }

        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }

        return value.trim().toLowerCase(Locale.US).replace('_', '-');
    }

    private static void showUnknownItem(Activity activity, String item) {
        new AlertDialog.Builder(activity)
                .setTitle("Unknown app item")
                .setMessage(
                        APP_HOST + "/" + item
                                + "\n\nUse " + APP_HOST
                                + " to see available in-app items."
                )
                .setPositiveButton("OK", null)
                .show();

        AppLogger.warn(activity, "app_domain", "unknown | " + item);
    }
}
