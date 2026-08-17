package com.harleytg.forum;

import android.content.Context;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Small authenticated JSON:API client used only for native alert delivery. */
final class ForumNotificationClient {
    private static final int CONNECT_TIMEOUT_MS = 4500;
    private static final int READ_TIMEOUT_MS = 4500;
    private static final int MAX_BODY_CHARS = 700000;

    static final class Alert {
        final String id;
        final String title;
        final String body;
        final String url;

        Alert(String id, String title, String body, String url) {
            this.id = clean(id, 120);
            this.title = clean(title, 120);
            this.body = clean(body, 500);
            this.url = url == null ? "" : url;
        }
    }

    static int fetchNewCount(Context context, String host, String userId) throws Exception {
        String base = trustedBase(host);
        String body = get(context, base, "api/users/" + userId, "Count");
        JSONObject attrs = new JSONObject(body).getJSONObject("data").getJSONObject("attributes");
        // The native badge is an unread-notification badge. Flarum may report
        // unreadNotificationCount and newNotificationCount differently after
        // the notification list has been viewed, so keep the larger value.
        int unread = Math.max(0, attrs.optInt("unreadNotificationCount", 0));
        int fresh = Math.max(0, attrs.optInt("newNotificationCount", 0));
        return Math.max(unread, fresh);
    }

    static List<Alert> fetchLatest(Context context, String host, int limit) throws Exception {
        String base = trustedBase(host);
        int safeLimit = Math.max(1, Math.min(20, limit));
        String path = "api/notifications?include=fromUser,subject&page%5Blimit%5D=" + safeLimit;
        String body;
        try {
            body = get(context, base, path, "Details");
        } catch (Throwable first) {
            // Some Flarum extensions reject an include they do not expose. The
            // core notification collection still provides a useful fallback.
            body = get(context, base,
                    "api/notifications?page%5Blimit%5D=" + safeLimit,
                    "DetailsFallback");
        }
        return parseAlerts(new JSONObject(body), base, safeLimit);
    }

    private static List<Alert> parseAlerts(JSONObject root, String base, int limit) {
        JSONArray data = root.optJSONArray("data");
        if (data == null || data.length() == 0) return Collections.emptyList();

        Map<String, JSONObject> included = new HashMap<>();
        JSONArray sideLoaded = root.optJSONArray("included");
        if (sideLoaded != null) {
            for (int i = 0; i < sideLoaded.length(); i++) {
                JSONObject item = sideLoaded.optJSONObject(i);
                if (item == null) continue;
                included.put(key(item.optString("type"), item.optString("id")), item);
            }
        }

        List<Alert> alerts = new ArrayList<>();
        for (int i = 0; i < data.length() && alerts.size() < limit; i++) {
            JSONObject notification = data.optJSONObject(i);
            if (notification == null) continue;
            Alert alert = parseAlert(notification, included, base);
            if (alert != null && !alert.id.isEmpty()) alerts.add(alert);
        }
        return alerts;
    }

    private static Alert parseAlert(JSONObject notification, Map<String, JSONObject> included, String base) {
        String id = notification.optString("id", "");
        JSONObject attrs = notification.optJSONObject("attributes");
        if (attrs == null) attrs = new JSONObject();
        String type = attrs.optString("type", "notification");

        JSONObject relationships = notification.optJSONObject("relationships");
        JSONObject fromRef = relationData(relationships, "fromUser");
        JSONObject subjectRef = relationData(relationships, "subject");
        JSONObject fromUser = fromRef == null ? null
                : included.get(key(fromRef.optString("type"), fromRef.optString("id")));
        JSONObject subject = subjectRef == null ? null
                : included.get(key(subjectRef.optString("type"), subjectRef.optString("id")));

        JSONObject content = contentObject(attrs.opt("content"));
        String sender = userLabel(fromUser);
        if (sender.isEmpty()) sender = firstDeep(content, "displayName", "display_name", "username", "user_name", "senderName", "sender_name");
        if (sender.isEmpty()) sender = "Someone";

        String conversationId = firstDeep(content, "conversationId", "conversation_id");
        String discussionId = firstDeep(content, "discussionId", "discussion_id");
        String discussionTitle = firstDeep(content, "discussionTitle", "discussion_title", "title");
        String postNumber = firstDeep(content, "postNumber", "post_number", "number");
        String profileSlug = firstDeep(content, "userSlug", "user_slug", "slug", "username");
        String senderSlug = prefer(attribute(fromUser, "slug"), attribute(fromUser, "username"));
        String subjectType = subjectRef == null ? "" : subjectRef.optString("type", "");
        if ("discussions".equals(subjectType)) {
            discussionId = subjectRef.optString("id", discussionId);
            discussionTitle = prefer(discussionTitle, attribute(subject, "title"));
        } else if ("posts".equals(subjectType)) {
            JSONObject postRelationships = subject == null ? null : subject.optJSONObject("relationships");
            JSONObject discussionRef = relationData(postRelationships, "discussion");
            if (discussionRef != null) discussionId = discussionRef.optString("id", discussionId);
            postNumber = prefer(postNumber, attribute(subject, "number"));
        } else if ("users".equals(subjectType)) {
            profileSlug = prefer(attribute(subject, "slug"), attribute(subject, "username"));
        }

        String normalized = type.toLowerCase(Locale.US);
        boolean conversationAlert = !conversationId.isEmpty()
                || normalized.contains("privatediscussion")
                || normalized.contains("private_message")
                || normalized.contains("privatemessage")
                || normalized.contains("conversationmessage")
                || normalized.contains("conversation_message")
                || normalized.contains("messenger");

        String title;
        if (conversationAlert) {
            title = "New message from " + sender;
        } else if (normalized.contains("postmentioned") || normalized.contains("usermentioned")) {
            title = sender + " mentioned you";
        } else if (normalized.contains("liked") || normalized.contains("postliked")) {
            title = sender + " liked your post";
        } else if (normalized.contains("newpost") || normalized.contains("reply")) {
            title = "New reply from " + sender;
        } else if (normalized.contains("follow")) {
            title = sender + " followed you";
        } else {
            title = "New forum alert from " + sender;
        }

        String excerpt = firstDeep(content, "message", "body", "text", "excerpt", "preview", "content");
        String body = conversationAlert ? excerpt : (!discussionTitle.isEmpty() ? discussionTitle : excerpt);
        body = cleanNotificationBody(body, conversationAlert);
        if (body.isEmpty()) body = conversationAlert
                ? "You have a new private message."
                : readableType(type);

        String destination = base + "notifications";
        if (conversationAlert && !conversationId.isEmpty() && conversationId.matches("[0-9]+")) {
            destination = base + "conversations/" + conversationId;
        } else if (normalized.contains("follow") && !senderSlug.isEmpty()) {
            destination = base + "u/" + android.net.Uri.encode(senderSlug);
        } else if (!profileSlug.isEmpty() && "users".equals(subjectType)) {
            destination = base + "u/" + android.net.Uri.encode(profileSlug);
        } else if (!discussionId.isEmpty() && discussionId.matches("[0-9]+")) {
            destination = base + "d/" + discussionId;
            if (!postNumber.isEmpty() && postNumber.matches("[0-9]+")) destination += "/" + postNumber;
        }
        return new Alert(id, title, body, destination);
    }

    private static JSONObject relationData(JSONObject relationships, String name) {
        if (relationships == null) return null;
        JSONObject relationship = relationships.optJSONObject(name);
        if (relationship == null) return null;
        Object data = relationship.opt("data");
        return data instanceof JSONObject ? (JSONObject) data : null;
    }

    private static JSONObject contentObject(Object value) {
        if (value instanceof JSONObject) return (JSONObject) value;
        if (value instanceof String) {
            String raw = ((String) value).trim();
            if (raw.startsWith("{") && raw.endsWith("}")) {
                try { return new JSONObject(raw); } catch (Throwable ignored) {}
            }
        }
        return new JSONObject();
    }

    private static String userLabel(JSONObject user) {
        if (user == null) return "";
        JSONObject attrs = user.optJSONObject("attributes");
        if (attrs == null) return "";
        String display = clean(attrs.optString("displayName", ""), 80);
        if (!display.isEmpty()) return display;
        return clean(attrs.optString("username", ""), 80);
    }

    private static String attribute(JSONObject resource, String name) {
        if (resource == null) return "";
        JSONObject attrs = resource.optJSONObject("attributes");
        return attrs == null ? "" : clean(attrs.optString(name, ""), 300);
    }

    /**
     * Finds a user-visible scalar without ever converting a JSONObject/JSONArray
     * directly to text. Messenger/private-message extensions frequently nest the
     * real message inside content -> body -> message (or similar), and calling
     * String.valueOf() on that container is what caused raw API JSON to appear in
     * Android notifications.
     */
    private static String firstDeep(JSONObject object, String... names) {
        return firstDeepObject(object, 0, names);
    }

    private static String firstDeepObject(JSONObject object, int depth, String... names) {
        if (object == null || depth > 5) return "";

        // Prefer explicitly requested keys at the current level first.
        for (String name : names) {
            if (!object.has(name)) continue;
            String text = readableValue(object.opt(name), depth + 1, names);
            if (!text.isEmpty()) return text;
        }

        // Then inspect nested containers. This supports extension-specific wrappers
        // such as {"body":{"message":"Hello", "conversation_id":23}}.
        JSONArray keys = object.names();
        if (keys != null) {
            for (int i = 0; i < keys.length(); i++) {
                String key = keys.optString(i, "");
                Object value = object.opt(key);
                if (!(value instanceof JSONObject) && !(value instanceof JSONArray)) continue;
                String text = readableValue(value, depth + 1, names);
                if (!text.isEmpty()) return text;
            }
        }
        return "";
    }

    private static String readableValue(Object value, int depth, String... names) {
        if (value == null || value == JSONObject.NULL || depth > 6) return "";
        if (value instanceof JSONObject) return firstDeepObject((JSONObject) value, depth, names);
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                String text = readableValue(array.opt(i), depth + 1, names);
                if (!text.isEmpty()) return text;
            }
            return "";
        }

        String raw = clean(String.valueOf(value), 2000);
        if (raw.isEmpty()) return "";
        String trimmed = raw.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                if (trimmed.startsWith("{")) return firstDeepObject(new JSONObject(trimmed), depth + 1, names);
                JSONArray array = new JSONArray(trimmed);
                for (int i = 0; i < array.length(); i++) {
                    String text = readableValue(array.opt(i), depth + 1, names);
                    if (!text.isEmpty()) return text;
                }
                return "";
            } catch (Throwable ignored) {
                // If it merely looks like JSON but cannot be parsed, do not surface
                // the API blob to the user.
                return "";
            }
        }
        return clean(trimmed, 500);
    }

    private static String cleanNotificationBody(String value, boolean conversationAlert) {
        String text = stripMarkup(value);
        if (text.isEmpty()) return "";
        String trimmed = text.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                String extracted;
                if (trimmed.startsWith("{")) {
                    extracted = firstDeepObject(new JSONObject(trimmed), 0,
                            "message", "body", "text", "excerpt", "preview", "content");
                } else {
                    extracted = readableValue(new JSONArray(trimmed), 0,
                            "message", "body", "text", "excerpt", "preview", "content");
                }
                text = stripMarkup(extracted);
            } catch (Throwable ignored) {
                return "";
            }
        }
        if (text.isEmpty() && conversationAlert) return "You have a new private message.";
        return clean(text, 500);
    }

    private static String prefer(String first, String second) {
        return first == null || first.trim().isEmpty() ? clean(second, 500) : first;
    }

    private static String readableType(String type) {
        String value = type == null ? "Notification" : type;
        value = value.replace('_', ' ').replace('-', ' ')
                .replaceAll("([a-z])([A-Z])", "$1 $2").trim();
        return value.isEmpty() ? "You have a new forum notification." : value;
    }

    private static String stripMarkup(String value) {
        if (value == null) return "";
        return clean(value.replaceAll("<[^>]+>", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " "), 500);
    }

    private static String trustedBase(String host) {
        String safeHost = ForumUrlRouter.isForumHost(host) ? host : ForumConfig.PRIMARY_HOST;
        return ForumUrlRouter.home(safeHost);
    }

    private static String get(Context context, String base, String path, String client) throws Exception {
        URL url = new URL(base + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.api+json, application/json");
            connection.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " Notification" + client);
            String cookies = CookieManager.getInstance().getCookie(base);
            if (cookies != null && !cookies.trim().isEmpty()) connection.setRequestProperty("Cookie", cookies);

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            return read(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream stream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[8192];
        int read;
        while ((read = reader.read(buffer)) != -1 && out.length() < MAX_BODY_CHARS) {
            out.append(buffer, 0, Math.min(read, MAX_BODY_CHARS - out.length()));
        }
        reader.close();
        return out.toString();
    }

    private static String clean(String value, int max) {
        String text = value == null ? "" : value.trim();
        if (text.length() > max) text = text.substring(0, max) + "…";
        return text;
    }

    private static String key(String type, String id) {
        return (type == null ? "" : type) + ":" + (id == null ? "" : id);
    }

    private ForumNotificationClient() {}
}
