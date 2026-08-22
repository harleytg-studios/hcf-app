package com.harleytg.forum.dev;

import android.content.Context;
import android.net.Uri;
import android.webkit.CookieManager;
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
import org.json.JSONArray;
import org.json.JSONObject;

/* loaded from: classes.dex */
final class ForumNotificationClient {
    private static final int CONNECT_TIMEOUT_MS = 4500;
    private static final int MAX_BODY_CHARS = 700000;
    private static final int READ_TIMEOUT_MS = 4500;

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
        } catch (Throwable unused) {
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
                throw new IllegalStateException("HTTP " + responseCode);
            }
            return read(httpURLConnection.getInputStream());
        } finally {
            httpURLConnection.disconnect();
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
