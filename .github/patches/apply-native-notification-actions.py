#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel, text):
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 exact match, found {count}")
    return text.replace(old, new, 1)


def sub_once(text, pattern, replacement, label, flags=0):
    out, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 regex match, found {count}")
    return out


# ---------------------------------------------------------------------------
# New shared sanitizer. It is intentionally dependency-free and is used both
# when writing new logs and when presenting/copying older on-disk logs.
# ---------------------------------------------------------------------------
write("source code/src/com/harleytg/forum/HcfSupportSanitizer.java", r'''package com.harleytg.forum.dev;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small support-data redactor shared by AppLogger and diagnostics UI. */
final class HcfSupportSanitizer {
    private static final String REDACTED = "[REDACTED]";
    private static final Pattern HEADER = Pattern.compile(
            "(?i)\\b(authorization|proxy-authorization|cookie|set-cookie)\\s*[:=]\\s*[^\\r\\n|]+"
    );
    private static final Pattern BEARER = Pattern.compile(
            "(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]+"
    );
    private static final Pattern DISCORD_WEBHOOK = Pattern.compile(
            "(?i)https://(?:www\\.)?discord(?:app)?\\.com/api/webhooks/[^\\s\\\"']+"
    );
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)\\b(password|passwd|session[_-]?(?:id|token)|access[_-]?token|refresh[_-]?token|auth(?:entication)?[_-]?token|csrf[_-]?token|reply[_-]?(?:text|body|content)|message[_-]?(?:body|content|text)|notification[_-]?(?:body|message|content)|private[_-]?(?:message|body|content))\\b\\s*[:=]\\s*(?:\\\"[^\\\"]*\\\"|'[^']*'|[^,\\s|;}]+)"
    );

    static String sanitize(String value) {
        if (value == null || value.isEmpty()) return value == null ? "" : value;
        String out = value;
        out = replaceHeader(out);
        out = BEARER.matcher(out).replaceAll("Bearer " + REDACTED);
        out = DISCORD_WEBHOOK.matcher(out).replaceAll("https://discord.com/api/webhooks/" + REDACTED);
        Matcher matcher = KEY_VALUE.matcher(out);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + "=" + REDACTED));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String replaceHeader(String value) {
        Matcher matcher = HEADER.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + "=" + REDACTED));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private HcfSupportSanitizer() {}
}
''')


# ---------------------------------------------------------------------------
# Notification action receiver + avatar loader/cache.
# ---------------------------------------------------------------------------
write("source code/src/com/harleytg/forum/HcfNotificationActions.java", r'''package com.harleytg.forum.dev;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.webkit.CookieManager;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Native Android actions for a single, server-backed Flarum notification. */
final class HcfNotificationActions {
    static final String ACTION_OPEN = "com.harleytg.forum.dev.NOTIFICATION_OPEN_AND_READ";
    static final String ACTION_REPLY = "com.harleytg.forum.dev.NOTIFICATION_INLINE_REPLY";
    static final String ACTION_MARK_READ = "com.harleytg.forum.dev.NOTIFICATION_MARK_READ";
    static final String REMOTE_INPUT_REPLY = "hcf_inline_reply";

    private static final String EXTRA_HOST = "hcf_forum_host";
    private static final String EXTRA_NOTIFICATION_ID = "hcf_notification_id";
    private static final String EXTRA_CONVERSATION_ID = "hcf_conversation_id";
    private static final String EXTRA_DISCUSSION_ID = "hcf_discussion_id";
    private static final String EXTRA_NOTIFICATION_URL = "hcf_notification_url";
    private static final String EXTRA_LOCAL_ID = "hcf_local_notification_id";
    private static final String PREFS = "hcf_app";
    private static final long IN_FLIGHT_TTL_MS = 120000L;
    private static final long COMPLETE_TTL_MS = 604800000L;
    private static final int MAX_AVATAR_BYTES = 1048576;
    private static final int MAX_AVATAR_DIMENSION = 8192;
    private static final int AVATAR_CACHE_SIZE = 16;
    private static final Object CLAIM_LOCK = new Object();
    private static final Object AVATAR_LOCK = new Object();
    private static final Map<String, Bitmap> AVATAR_CACHE = new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            return size() > AVATAR_CACHE_SIZE;
        }
    };
    private static final Map<String, Boolean> AVATAR_IN_FLIGHT = new LinkedHashMap<>();

    interface AvatarCallback {
        void onLoaded(Bitmap bitmap);
    }

    static PendingIntent openPendingIntent(Context context, ForumNotificationClient.Alert alert, String host, int localId) {
        Intent intent = baseIntent(context, ACTION_OPEN, alert, host, localId, "open");
        return PendingIntent.getBroadcast(context, requestCode("open", host, alert, localId), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void addActions(Context context, Notification.Builder builder, ForumNotificationClient.Alert alert, String host, int localId) {
        if (context == null || builder == null || alert == null) return;
        if (alert.replyCapable && numeric(alert.conversationId) && numeric(alert.id)) {
            Intent replyIntent = baseIntent(context, ACTION_REPLY, alert, host, localId, "reply");
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent replyPendingIntent = PendingIntent.getBroadcast(
                    context, requestCode("reply", host, alert, localId), replyIntent, flags);
            RemoteInput remoteInput = new RemoteInput.Builder(REMOTE_INPUT_REPLY)
                    .setLabel("Reply")
                    .build();
            Notification.Action replyAction = new Notification.Action.Builder(
                    android.R.drawable.ic_menu_send, "Reply", replyPendingIntent)
                    .addRemoteInput(remoteInput)
                    .build();
            builder.addAction(replyAction);
        }
        if (numeric(alert.id)) {
            Intent readIntent = baseIntent(context, ACTION_MARK_READ, alert, host, localId, "read");
            PendingIntent readPendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode("read", host, alert, localId),
                    readIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new Notification.Action.Builder(
                    android.R.drawable.checkbox_on_background, "Mark as read", readPendingIntent).build());
        }
    }

    private static Intent baseIntent(Context context, String action, ForumNotificationClient.Alert alert,
                                     String host, int localId, String kind) {
        Intent intent = new Intent(context, ActionReceiver.class);
        intent.setAction(action);
        String notificationId = alert == null ? "" : clean(alert.id);
        String conversationId = alert == null ? "" : clean(alert.conversationId);
        String discussionId = alert == null ? "" : clean(alert.discussionId);
        String safeHost = ForumUrlRouter.isForumHost(host) ? host : ForumConfig.PRIMARY_HOST;
        String identity = "hcf-action://" + kind + "/" + Uri.encode(safeHost) + "/"
                + Uri.encode(notificationId) + "/" + Uri.encode(conversationId) + "/"
                + Uri.encode(discussionId) + "/" + localId;
        intent.setData(Uri.parse(identity));
        intent.putExtra(EXTRA_HOST, safeHost);
        intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        intent.putExtra(EXTRA_CONVERSATION_ID, conversationId);
        intent.putExtra(EXTRA_DISCUSSION_ID, discussionId);
        intent.putExtra(EXTRA_NOTIFICATION_URL, alert == null ? "" : clean(alert.url));
        intent.putExtra(EXTRA_LOCAL_ID, localId);
        return intent;
    }

    private static int requestCode(String kind, String host, ForumNotificationClient.Alert alert, int localId) {
        String value = kind + "|" + clean(host) + "|" + (alert == null ? "" : clean(alert.id)) + "|"
                + (alert == null ? "" : clean(alert.conversationId)) + "|"
                + (alert == null ? "" : clean(alert.discussionId)) + "|" + localId;
        return 0x20000000 | (value.hashCode() & 0x1fffffff);
    }

    static Bitmap cachedAvatar(String url) {
        String key = trustedAvatarUrl(url);
        if (key.isEmpty()) return null;
        synchronized (AVATAR_LOCK) {
            Bitmap bitmap = AVATAR_CACHE.get(key);
            return bitmap != null && !bitmap.isRecycled() ? bitmap : null;
        }
    }

    static void requestAvatar(final Context context, final String avatarUrl, final String forumHost, final AvatarCallback callback) {
        if (context == null || callback == null) return;
        final String trusted = trustedAvatarUrl(avatarUrl);
        if (trusted.isEmpty()) return;
        Bitmap cached = cachedAvatar(trusted);
        if (cached != null) {
            callback.onLoaded(cached);
            return;
        }
        synchronized (AVATAR_LOCK) {
            if (Boolean.TRUE.equals(AVATAR_IN_FLIGHT.get(trusted))) return;
            AVATAR_IN_FLIGHT.put(trusted, Boolean.TRUE);
        }
        final Context app = context.getApplicationContext();
        AppExecutors.network().execute(new Runnable() {
            @Override public void run() {
                Bitmap loaded = null;
                try {
                    loaded = downloadAvatar(app, trusted, forumHost);
                    if (loaded != null) {
                        synchronized (AVATAR_LOCK) { AVATAR_CACHE.put(trusted, loaded); }
                    }
                } catch (Throwable error) {
                    AppLogger.warn(app, "notification_avatar", "fetch failed | " + category(error));
                } finally {
                    synchronized (AVATAR_LOCK) { AVATAR_IN_FLIGHT.remove(trusted); }
                }
                if (loaded != null) {
                    try { callback.onLoaded(loaded); } catch (Throwable ignored) {}
                }
            }
        });
    }

    private static Bitmap downloadAvatar(Context context, String avatarUrl, String forumHost) throws Exception {
        URL url = new URL(avatarUrl);
        if (!"https".equalsIgnoreCase(url.getProtocol())) return null;
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(3500);
            connection.setUseCaches(true);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "image/*");
            connection.setRequestProperty("User-Agent", "HarleysClanForumApp/1.0 NotificationAvatar");
            if (ForumUrlRouter.isForumHost(forumHost) && forumHost.equalsIgnoreCase(url.getHost())) {
                String cookie = CookieManager.getInstance().getCookie("https://" + forumHost + "/");
                if (cookie != null && !cookie.trim().isEmpty()) connection.setRequestProperty("Cookie", cookie);
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return null;
            int declared = connection.getContentLength();
            if (declared > MAX_AVATAR_BYTES) return null;
            InputStream input = connection.getInputStream();
            ByteArrayOutputStream output = new ByteArrayOutputStream(declared > 0 ? declared : 32768);
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_AVATAR_BYTES) return null;
                output.write(buffer, 0, read);
            }
            input.close();
            byte[] bytes = output.toByteArray();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                    || bounds.outWidth > MAX_AVATAR_DIMENSION || bounds.outHeight > MAX_AVATAR_DIMENSION) return null;
            int target = Math.max(96, (int) (96f * context.getResources().getDisplayMetrics().density));
            int sample = 1;
            while (bounds.outWidth / sample > target * 2 || bounds.outHeight / sample > target * 2) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (bitmap == null) return null;
            int max = Math.max(bitmap.getWidth(), bitmap.getHeight());
            if (max > target * 2) {
                float scale = (target * 2f) / max;
                int width = Math.max(1, Math.round(bitmap.getWidth() * scale));
                int height = Math.max(1, Math.round(bitmap.getHeight() * scale));
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
                if (scaled != bitmap) bitmap.recycle();
                bitmap = scaled;
            }
            return bitmap;
        } finally {
            connection.disconnect();
        }
    }

    private static String trustedAvatarUrl(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        try {
            Uri uri = Uri.parse(raw.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().trim().isEmpty()) return "";
            return uri.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    static String diagnosticSummary(Context context) {
        if (context == null) return "Notification reply: Ready • Never used\nMark as read: Ready • Never used";
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String reply = prefs.getString("notification_reply_action_state", "Never used");
            String read = prefs.getString("notification_read_action_state", "Never used");
            long at = prefs.getLong("notification_action_last_time", 0L);
            int http = prefs.getInt("notification_action_last_http", 0);
            String failure = prefs.getString("notification_action_last_failure", "");
            StringBuilder out = new StringBuilder();
            out.append("Notification reply: ").append("Never used".equals(reply) ? "Ready • Never used" : reply).append('\n');
            out.append("Mark as read: ").append("Never used".equals(read) ? "Ready • Never used" : read).append('\n');
            out.append("Last notification action: ");
            if (at <= 0L) {
                out.append("Never used");
            } else {
                out.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(at)));
                if (http > 0) out.append(" • HTTP ").append(http);
                if (failure != null && !failure.trim().isEmpty()) out.append(" • ").append(clean(failure));
            }
            return HcfSupportSanitizer.sanitize(out.toString());
        } catch (Throwable error) {
            return "Notification reply: Ready\nMark as read: Ready\nLast notification action: unavailable";
        }
    }

    private static void recordSuccess(Context context, String kind, int http) {
        if (context == null) return;
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("notification_action_last_time", System.currentTimeMillis())
                .putInt("notification_action_last_http", Math.max(0, http))
                .remove("notification_action_last_failure");
        if ("reply".equals(kind)) editor.putString("notification_reply_action_state", "Last success");
        if ("read".equals(kind)) editor.putString("notification_read_action_state", "Last success");
        editor.apply();
    }

    private static void recordFailure(Context context, String kind, int http, String category) {
        if (context == null) return;
        String safeCategory = clean(category);
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("notification_action_last_time", System.currentTimeMillis())
                .putInt("notification_action_last_http", Math.max(0, http))
                .putString("notification_action_last_failure", safeCategory);
        if ("reply".equals(kind)) editor.putString("notification_reply_action_state", "Failed • " + safeCategory);
        if ("read".equals(kind)) editor.putString("notification_read_action_state", "Failed • " + safeCategory);
        editor.apply();
    }

    private static boolean numeric(String value) {
        return value != null && value.matches("[0-9]+");
    }

    private static String clean(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= 240 ? trimmed : trimmed.substring(0, 240);
    }

    private static String actionKey(String kind, String host, String notificationId, String target) {
        return Integer.toHexString((kind + "|" + host + "|" + notificationId + "|" + target).hashCode());
    }

    private static boolean claim(Context context, String key) {
        synchronized (CLAIM_LOCK) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long now = System.currentTimeMillis();
            long done = prefs.getLong("notification_action_done_" + key, 0L);
            if (done > 0L && now - done < COMPLETE_TTL_MS) return false;
            long inFlight = prefs.getLong("notification_action_inflight_" + key, 0L);
            if (inFlight > 0L && now - inFlight < IN_FLIGHT_TTL_MS) return false;
            return prefs.edit().putLong("notification_action_inflight_" + key, now).commit();
        }
    }

    private static void complete(Context context, String key) {
        synchronized (CLAIM_LOCK) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove("notification_action_inflight_" + key)
                    .putLong("notification_action_done_" + key, System.currentTimeMillis())
                    .commit();
        }
    }

    private static void release(Context context, String key) {
        synchronized (CLAIM_LOCK) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove("notification_action_inflight_" + key).commit();
        }
    }

    private static String category(Throwable error) {
        if (error instanceof ForumNotificationClient.HttpStatusException) {
            int code = ((ForumNotificationClient.HttpStatusException) error).statusCode;
            if (code == 401) return "auth-expired";
            if (code == 403) return "auth-or-permission";
            if (code == 429) return "rate-limited";
            return "http";
        }
        if (error instanceof java.io.IOException) return "network";
        if (error instanceof org.json.JSONException) return "protocol";
        return error == null ? "unknown" : error.getClass().getSimpleName();
    }

    private static int status(Throwable error) {
        return error instanceof ForumNotificationClient.HttpStatusException
                ? ((ForumNotificationClient.HttpStatusException) error).statusCode : 0;
    }

    private static void cancelLocal(Context context, int localId) {
        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.cancel(localId);
        } catch (Throwable ignored) {}
    }

    private static boolean hasRecoverableSession(Context context) {
        try {
            String userId = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("session_user_id", "");
            return userId != null && numeric(userId.trim());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void openTarget(Context context, Intent source, String host) {
        String raw = source == null ? "" : source.getStringExtra(EXTRA_NOTIFICATION_URL);
        Uri uri;
        try { uri = Uri.parse(raw == null ? "" : raw); } catch (Throwable ignored) { uri = null; }
        if (uri == null || !ForumUrlRouter.isForumUrl(uri)) uri = Uri.parse(ForumUrlRouter.home(host) + "notifications");
        try {
            Intent open = new Intent(context, HcfMainActivities.MainActivity.class);
            open.setAction("com.harleytg.forum.dev.OPEN_NOTIFICATION");
            open.setData(uri);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(open);
        } catch (Throwable error) {
            AppLogger.warn(context, "notification_open_action", error.getClass().getSimpleName());
        }
    }

    public static final class ActionReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, final Intent intent) {
            if (context == null || intent == null) return;
            final Context app = context.getApplicationContext();
            final String action = intent.getAction();
            String rawHost = intent.getStringExtra(EXTRA_HOST);
            final String host = ForumUrlRouter.isForumHost(rawHost) ? rawHost : "";
            if (host.isEmpty()) {
                recordFailure(app, ACTION_REPLY.equals(action) ? "reply" : "read", 0, "malformed-target");
                return;
            }
            if (ACTION_OPEN.equals(action)) openTarget(app, intent, host);

            final PendingResult pendingResult = goAsync();
            AppExecutors.network().execute(new Runnable() {
                @Override public void run() {
                    try {
                        handle(app, intent, action, host);
                    } catch (Throwable error) {
                        String kind = ACTION_REPLY.equals(action) ? "reply" : "read";
                        int http = status(error);
                        String failure = category(error);
                        recordFailure(app, kind, http, failure);
                        AppLogger.warn(app, "notification_" + kind + "_action",
                                "failed | category=" + failure + (http > 0 ? " | http=" + http : ""));
                        if (http == 401) HcfNotificationEngine.InstantNotificationService.clearSessionForAuthFailure(app, "notification-action");
                    } finally {
                        pendingResult.finish();
                    }
                }
            });
        }

        private static void handle(Context context, Intent intent, String action, String host) throws Exception {
            if (!ACTION_REPLY.equals(action) && !ACTION_MARK_READ.equals(action) && !ACTION_OPEN.equals(action)) return;
            String notificationId = clean(intent.getStringExtra(EXTRA_NOTIFICATION_ID));
            String conversationId = clean(intent.getStringExtra(EXTRA_CONVERSATION_ID));
            int localId = intent.getIntExtra(EXTRA_LOCAL_ID, 0);
            if (!numeric(notificationId)) {
                if (!ACTION_OPEN.equals(action)) recordFailure(context, ACTION_REPLY.equals(action) ? "reply" : "read", 0, "malformed-target");
                return;
            }
            if (!hasRecoverableSession(context)) {
                recordFailure(context, ACTION_REPLY.equals(action) ? "reply" : "read", 0, "auth-expired");
                return;
            }
            if (!RuntimeState.networkAvailable(context)) {
                recordFailure(context, ACTION_REPLY.equals(action) ? "reply" : "read", 0, "offline");
                return;
            }

            if (ACTION_REPLY.equals(action)) {
                if (!numeric(conversationId)) {
                    recordFailure(context, "reply", 0, "malformed-target");
                    return;
                }
                Bundle results = RemoteInput.getResultsFromIntent(intent);
                CharSequence rawReply = results == null ? null : results.getCharSequence(REMOTE_INPUT_REPLY);
                String reply = rawReply == null ? "" : rawReply.toString().trim();
                if (reply.isEmpty() || reply.length() > 10000) {
                    recordFailure(context, "reply", 0, reply.isEmpty() ? "empty-reply" : "reply-too-long");
                    return;
                }
                String key = actionKey("reply", host, notificationId, conversationId);
                if (!claim(context, key)) return;
                try {
                    int replyHttp = ForumNotificationClient.sendConversationReply(context, host, conversationId, reply);
                    recordSuccess(context, "reply", replyHttp);
                    complete(context, key);
                    try {
                        int readHttp = ForumNotificationClient.markNotificationRead(context, host, notificationId);
                        recordSuccess(context, "read", readHttp);
                        NotificationHelper.markNotificationHandled(context, notificationId);
                    } catch (Throwable readFailure) {
                        int readStatus = status(readFailure);
                        String readCategory = category(readFailure);
                        recordFailure(context, "read", readStatus, readCategory);
                        AppLogger.warn(context, "notification_read_after_reply",
                                "failed | category=" + readCategory + (readStatus > 0 ? " | http=" + readStatus : ""));
                        if (readStatus == 401) HcfNotificationEngine.InstantNotificationService.clearSessionForAuthFailure(context, "reply-read");
                    }
                    cancelLocal(context, localId);
                    HcfNotificationEngine.InstantNotificationService.requestImmediateSync(context);
                    AppLogger.info(context, "notification_reply_action", "success | http=" + replyHttp);
                } catch (Throwable error) {
                    release(context, key);
                    throw error;
                }
                return;
            }

            String key = actionKey("read", host, notificationId, "");
            if (!claim(context, key)) return;
            try {
                int readHttp = ForumNotificationClient.markNotificationRead(context, host, notificationId);
                recordSuccess(context, "read", readHttp);
                complete(context, key);
                NotificationHelper.markNotificationHandled(context, notificationId);
                cancelLocal(context, localId);
                HcfNotificationEngine.InstantNotificationService.requestImmediateSync(context);
                AppLogger.info(context, ACTION_OPEN.equals(action) ? "notification_open_read" : "notification_read_action",
                        "success | http=" + readHttp);
            } catch (Throwable error) {
                release(context, key);
                throw error;
            }
        }
    }

    private HcfNotificationActions() {}
}
''')


# ---------------------------------------------------------------------------
# Identity avatar view: keeps the old footprint, but the border belongs to an
# outer frame and profile images are center-cropped instead of stretched.
# ---------------------------------------------------------------------------
write("source code/src/com/harleytg/forum/HcfIdentityAvatarView.java", r'''package com.harleytg.forum.dev;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Profile-avatar ImageView that preserves aspect ratio and never owns the cyan frame. */
public final class HcfIdentityAvatarView extends ImageView {
    public HcfIdentityAvatarView(Context context) { super(context); init(); }
    public HcfIdentityAvatarView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public HcfIdentityAvatarView(Context context, AttributeSet attrs, int style) { super(context, attrs, style); init(); }

    private void init() {
        setAdjustViewBounds(false);
        setCropToPadding(false);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                float radius = 9f * getResources().getDisplayMetrics().density;
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);
    }

    @Override public void setScaleType(ScaleType scaleType) {
        super.setScaleType(scaleType == ScaleType.FIT_XY ? ScaleType.CENTER_CROP : scaleType);
    }

    static FrameLayout frame(Context context, ImageView avatar) {
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundResource(R.drawable.identity_avatar_background);
        FrameLayout.LayoutParams inner = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        int inset = Math.max(1, Math.round(2f * context.getResources().getDisplayMetrics().density));
        inner.setMargins(inset, inset, inset, inset);
        frame.addView(avatar, inner);
        return frame;
    }
}
''')


# ---------------------------------------------------------------------------
# Forum client: carry trusted server identifiers + sender avatar through the
# parsed Alert, and add cookie+CSRF mutations mirroring Flarum's own frontend.
# ---------------------------------------------------------------------------
rel = "source code/src/com/harleytg/forum/HcfForumEngine.java"
text = read(rel)
old_alert = '''    static final class Alert {\n        final String body;\n        final String id;\n        final String title;\n        final String url;\n\n        Alert(String str, String str2, String str3, String str4) {\n            this.id = ForumNotificationClient.clean(str, 120);\n            this.title = ForumNotificationClient.clean(str2, 120);\n            this.body = ForumNotificationClient.clean(str3, 500);\n            this.url = str4 == null ? "" : str4;\n        }\n    }\n'''
new_alert = '''    static final class Alert {\n        final String body;\n        final String id;\n        final String title;\n        final String url;\n        final String conversationId;\n        final String discussionId;\n        final String senderAvatarUrl;\n        final boolean replyCapable;\n\n        Alert(String id, String title, String body, String url, String conversationId,\n              String discussionId, String senderAvatarUrl, boolean replyCapable) {\n            this.id = ForumNotificationClient.clean(id, 120);\n            this.title = ForumNotificationClient.clean(title, 120);\n            this.body = ForumNotificationClient.clean(body, 500);\n            this.url = url == null ? "" : url;\n            this.conversationId = ForumNotificationClient.clean(conversationId, 120);\n            this.discussionId = ForumNotificationClient.clean(discussionId, 120);\n            this.senderAvatarUrl = senderAvatarUrl == null ? "" : senderAvatarUrl;\n            this.replyCapable = replyCapable;\n        }\n    }\n'''
text = replace_once(text, old_alert, new_alert, "ForumNotificationClient.Alert")

old_return = '''        return new Alert(optString, str3, cleanNotificationBody, str5);\n    }\n\n    private static JSONObject relationData'''
new_return = '''        String senderAvatarUrl = absoluteHttpsUrl(str, attribute(jSONObject2, "avatarUrl"));\n        boolean privateMessageType = lowerCase.contains("privatediscussion")\n                || lowerCase.contains("private_message")\n                || lowerCase.contains("privatemessage")\n                || lowerCase.contains("conversationmessage")\n                || lowerCase.contains("conversation_message")\n                || lowerCase.contains("messenger");\n        boolean replyCapable = firstDeep.matches("[0-9]+")\n                && (privateMessageType || "messages".equals(optString3));\n        return new Alert(optString, str3, cleanNotificationBody, str5, firstDeep, firstDeep2, senderAvatarUrl, replyCapable);\n    }\n\n    private static String absoluteHttpsUrl(String base, String raw) {\n        if (raw == null || raw.trim().isEmpty()) return "";\n        try {\n            Uri uri = Uri.parse(raw.trim());\n            if (uri.isAbsolute()) return "https".equalsIgnoreCase(uri.getScheme()) ? uri.toString() : "";\n            Uri resolved = Uri.parse(base).buildUpon().encodedPath(raw.startsWith("/") ? raw : "/" + raw).build();\n            return "https".equalsIgnoreCase(resolved.getScheme()) ? resolved.toString() : "";\n        } catch (Throwable ignored) {\n            return "";\n        }\n    }\n\n    private static JSONObject relationData'''
text = replace_once(text, old_return, new_return, "alert return metadata")

mutation_anchor = '''    private static long retryAfterMillis(HttpURLConnection connection) {'''
mutation_code = r'''    static int markNotificationRead(Context context, String host, String notificationId) throws Exception {
        if (context == null || notificationId == null || !notificationId.matches("[0-9]+")) {
            throw new IllegalArgumentException("Invalid notification id");
        }
        String base = trustedBase(host);
        JSONObject attributes = new JSONObject().put("isRead", true);
        JSONObject data = new JSONObject()
                .put("type", "notifications")
                .put("id", notificationId)
                .put("attributes", attributes);
        JSONObject body = new JSONObject().put("data", data);
        return mutate(context, base, "api/notifications/" + notificationId, body, true);
    }

    static int sendConversationReply(Context context, String host, String conversationId, String replyText) throws Exception {
        if (context == null || conversationId == null || !conversationId.matches("[0-9]+")) {
            throw new IllegalArgumentException("Invalid conversation id");
        }
        String reply = replyText == null ? "" : replyText.trim();
        if (reply.isEmpty() || reply.length() > 10000) throw new IllegalArgumentException("Invalid reply");
        String base = trustedBase(host);
        JSONObject attributes = new JSONObject()
                .put("messageContents", reply)
                .put("conversationId", conversationId);
        JSONObject data = new JSONObject()
                .put("type", "messages")
                .put("attributes", attributes);
        return mutate(context, base, "api/neoncube-private-messages/messages", new JSONObject().put("data", data), false);
    }

    private static int mutate(Context context, String base, String path, JSONObject body, boolean patch) throws Exception {
        String cookie = CookieManager.getInstance().getCookie(base);
        if (cookie == null || cookie.trim().isEmpty()) throw new HttpStatusException(401, 0L);
        String csrf = currentCsrfToken(context, base, cookie);
        if (csrf.isEmpty()) throw new HttpStatusException(401, 0L);
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            if (patch) connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            connection.setRequestProperty("Accept", "application/vnd.api+json, application/json");
            connection.setRequestProperty("Content-Type", "application/vnd.api+json");
            connection.setRequestProperty("X-CSRF-Token", csrf);
            connection.setRequestProperty("Cookie", cookie);
            connection.setRequestProperty("User-Agent", "HarleysClanForumApp/1.0 NotificationAction");
            connection.setFixedLengthStreamingMode(bytes.length);
            OutputStream output = connection.getOutputStream();
            output.write(bytes);
            output.flush();
            output.close();
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new HttpStatusException(status, retryAfterMillis(connection));
            return status;
        } finally {
            connection.disconnect();
        }
    }

    private static String currentCsrfToken(Context context, String base, String cookie) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(base).openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            connection.setRequestProperty("Cache-Control", "no-cache, max-age=0");
            connection.setRequestProperty("Cookie", cookie);
            connection.setRequestProperty("User-Agent", "HarleysClanForumApp/1.0 NotificationSession");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new HttpStatusException(status, retryAfterMillis(connection));
            String html = readLimited(connection.getInputStream(), 1500000);
            int marker = html.indexOf("id=\\\"flarum-json-payload\\\"");
            if (marker < 0) marker = html.indexOf("id='flarum-json-payload'");
            if (marker < 0) return "";
            int start = html.indexOf('>', marker);
            int end = start < 0 ? -1 : html.indexOf("</script>", start + 1);
            if (start < 0 || end <= start) return "";
            JSONObject payload = new JSONObject(html.substring(start + 1, end).trim());
            JSONObject session = payload.optJSONObject("session");
            if (session == null || session.optInt("userId", 0) <= 0) return "";
            return clean(session.optString("csrfToken", ""), 1000);
        } finally {
            connection.disconnect();
        }
    }

    private static String readLimited(InputStream inputStream, int maxChars) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(Math.min(maxChars, 131072));
        char[] buffer = new char[8192];
        int count;
        while ((count = reader.read(buffer)) != -1 && out.length() < maxChars) {
            out.append(buffer, 0, Math.min(count, maxChars - out.length()));
        }
        reader.close();
        return out.toString();
    }

'''
text = replace_once(text, mutation_anchor, mutation_code + mutation_anchor, "Flarum write methods")
write(rel, text)


# ---------------------------------------------------------------------------
# Notification builder: detailed Flarum alerts get immutable open/read actions,
# mutable only for RemoteInput, and sender avatars refresh silently when loaded.
# ---------------------------------------------------------------------------
rel = "source code/src/com/harleytg/forum/HcfNotificationEngine.java"
text = read(rel)
old_delivery = '''                    postInternal(context,\n                            trim(alert.title, 120, "Harley's Clan Forum"),\n                            trim(alert.body, 500, "You have a new forum notification."),\n                            validatedForumUri(alert.url),\n                            603979776 | (alert.id.hashCode() & 268435455),\n                            true);'''
new_delivery = '''                    postForumAlert(context, alert, safeHost,\n                            603979776 | (alert.id.hashCode() & 268435455), null, false);'''
text = replace_once(text, old_delivery, new_delivery, "detailed alert builder")

insert_before_service = '''    static Notification buildInstantServiceNotification(Context context) {'''
specialized = r'''    private static void postForumAlert(final Context context, final ForumNotificationClient.Alert alert,
                                       final String host, final int localId, Bitmap suppliedAvatar,
                                       final boolean avatarRefresh) {
        if (context == null || alert == null) return;
        createChannel(context);
        if (!isEnabledByUser(context)) return;
        if (!canPostOnChannel(context, CHANNEL_ID)) {
            AppLogger.warn(context, "notification_blocked", status(context) + " | channel=" + CHANNEL_ID);
            return;
        }
        final Uri uri = validatedForumUri(alert.url);
        if (!avatarRefresh) broadcastEvent(context, alert.title, alert.body, uri.toString(), -1);
        PendingIntent contentIntent = HcfNotificationActions.openPendingIntent(context, alert, host, localId);
        Bitmap avatar = suppliedAvatar != null ? suppliedAvatar : HcfNotificationActions.cachedAvatar(alert.senderAvatarUrl);
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID);
        builder.setSmallIcon(R.drawable.ic_notification_paw)
                .setLargeIcon(avatar != null ? avatar : largeIcon(context))
                .setContentTitle(trim(alert.title, 120, "Harley's Clan Forum"))
                .setContentText(trim(alert.body, 500, "You have a new forum notification."))
                .setStyle(new Notification.BigTextStyle().bigText(trim(alert.body, 500, "You have a new forum notification.")))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory("msg")
                .setVisibility(0)
                .setPriority(1)
                .setOnlyAlertOnce(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setGroup(FORUM_GROUP_KEY);
        HcfNotificationActions.addActions(context, builder, alert, host, localId);
        NotificationManager manager = (NotificationManager) context.getSystemService("notification");
        if (manager != null) manager.notify(localId, builder.build());
        if (!avatarRefresh && avatar == null && alert.senderAvatarUrl != null && !alert.senderAvatarUrl.isEmpty()) {
            HcfNotificationActions.requestAvatar(context, alert.senderAvatarUrl, host,
                    new HcfNotificationActions.AvatarCallback() {
                        @Override public void onLoaded(Bitmap bitmap) {
                            postForumAlert(context, alert, host, localId, bitmap, true);
                        }
                    });
        }
        if (!avatarRefresh) {
            AppLogger.info(context, "notification_posted", "native Flarum alert | headsUp=" + headsUpChannelReady(context));
        }
    }

    static void markNotificationHandled(Context context, String id) {
        if (context == null || id == null || id.trim().isEmpty()) return;
        synchronized (NotificationHelper.class) {
            SharedPreferences prefs = context.getSharedPreferences("hcf_app", 0);
            Set<String> delivered = deliveredIds(prefs.getString("delivered_notification_ids", ""));
            delivered.add(id.trim());
            trimDeliveredIds(delivered, 100);
            Set<String> handled = deliveredIds(prefs.getString("handled_notification_ids", ""));
            handled.add(id.trim());
            trimDeliveredIds(handled, 100);
            prefs.edit()
                    .putString("delivered_notification_ids", join(delivered))
                    .putString("handled_notification_ids", join(handled))
                    .apply();
        }
    }

'''
text = replace_once(text, insert_before_service, specialized + insert_before_service, "specialized forum notification builder")
write(rel, text)


# ---------------------------------------------------------------------------
# AppLogger: redact before write and again when older files are read.
# ---------------------------------------------------------------------------
rel = "source code/src/com/harleytg/forum/HcfSecurityAndPrefs.java"
text = read(rel)
text = replace_once(text,
'''            return sb.toString();\n        }\n    }\n\n    static String readRecent''',
'''            return HcfSupportSanitizer.sanitize(sb.toString());\n        }\n    }\n\n    static String readRecent''',
"AppLogger.readAll sanitization")
text = replace_once(text,
'''                    return sb.substring(sb.length() - i2);\n                }\n                return sb.toString();''',
'''                    return HcfSupportSanitizer.sanitize(sb.substring(sb.length() - i2));\n                }\n                return HcfSupportSanitizer.sanitize(sb.toString());''',
"AppLogger.readRecent sanitization")
text = replace_once(text,
'''        String replace = str.replace((char) 0, ' ').replace("\\r", "");''',
'''        String replace = HcfSupportSanitizer.sanitize(str).replace((char) 0, ' ').replace("\\r", "");''',
"AppLogger.clean sanitization")
write(rel, text)


# ---------------------------------------------------------------------------
# Logs/Diagnostics: final display/copy/export redaction + guarded diagnostics.
# IdentityActivity gets the same separate frame used by the drawer avatar.
# ---------------------------------------------------------------------------
rel = "source code/src/com/harleytg/forum/HcfSubActivities.java"
text = read(rel)
text = replace_once(text,
'''            ImageView imageView = new ImageView(this);\n            this.identityAvatar = imageView;''',
'''            ImageView imageView = new HcfIdentityAvatarView(this);\n            this.identityAvatar = imageView;''',
"IdentityActivity avatar type")
text = text.replace('            this.identityAvatar.setBackgroundResource(R.drawable.identity_avatar_background);\n', '', 1)
text = replace_once(text,
'''            linearLayout.addView(this.identityAvatar, new LinearLayout.LayoutParams(dp(72), dp(72)));''',
'''            FrameLayout identityAvatarFrame = HcfIdentityAvatarView.frame(this, this.identityAvatar);\n            linearLayout.addView(identityAvatarFrame, new LinearLayout.LayoutParams(dp(72), dp(72)));''',
"IdentityActivity avatar frame")
text = replace_once(text,
'''            visiblePlainText = plain.toString();''',
'''            visiblePlainText = HcfSupportSanitizer.sanitize(plain.toString());''',
"Logs visible text sanitization")
text = replace_once(text,
'''            String buildDiagnosticReport = buildDiagnosticReport();\n            this.visiblePlainText = buildDiagnosticReport;''',
'''            String buildDiagnosticReport;\n            try {\n                buildDiagnosticReport = HcfSupportSanitizer.sanitize(\n                        buildDiagnosticReport() + "\\n\\nNotification actions\\n" + HcfNotificationActions.diagnosticSummary(this));\n            } catch (Throwable error) {\n                AppLogger.warn(this, "diagnostics_render", error.getClass().getSimpleName());\n                buildDiagnosticReport = "Diagnostics temporarily unavailable.\\nFailure category: "\n                        + error.getClass().getSimpleName() + "\\n\\nNotification actions\\n"\n                        + HcfNotificationActions.diagnosticSummary(this);\n                buildDiagnosticReport = HcfSupportSanitizer.sanitize(buildDiagnosticReport);\n            }\n            this.visiblePlainText = buildDiagnosticReport;''',
"Diagnostics guarded render")
text = replace_once(text,
'''            String str = this.visiblePlainText;\n            String trim = str == null ? "" : str.trim();''',
'''            String str = this.visiblePlainText;\n            String trim = HcfSupportSanitizer.sanitize(str == null ? "" : str).trim();''',
"Logs copy sanitization")
text = replace_once(text,
'''            String text = visiblePlainText == null ? "" : visiblePlainText;''',
'''            String text = HcfSupportSanitizer.sanitize(visiblePlainText == null ? "" : visiblePlainText);''',
"Logs export sanitization")
write(rel, text)


# ---------------------------------------------------------------------------
# Drawer identity avatar: outer FrameLayout owns the cyan border, inner custom
# ImageView owns only the bitmap. Same total size / alignment as before.
# ---------------------------------------------------------------------------
rel = "source code/res/layout/activity_main.xml"
text = read(rel)
pattern = r'''            <ImageView\n                android:layout_gravity="center_vertical"\n                android:id="@\+id/drawerIdentityAvatar"\n.*?\n                android:contentDescription="[^"]*"/>'''
replacement = '''            <FrameLayout\n                android:layout_gravity="center_vertical"\n                android:background="@drawable/identity_avatar_background"\n                android:layout_width="@dimen/identity_avatar_size"\n                android:layout_height="@dimen/identity_avatar_size">\n                <com.harleytg.forum.dev.HcfIdentityAvatarView\n                    android:id="@+id/drawerIdentityAvatar"\n                    android:layout_gravity="center"\n                    android:layout_width="match_parent"\n                    android:layout_height="match_parent"\n                    android:layout_margin="2dp"\n                    android:src="@drawable/htg_app_logo"\n                    android:scaleType="fitCenter"\n                    android:contentDescription="Current forum identity avatar"/>\n            </FrameLayout>'''
text = sub_once(text, pattern, replacement, "drawer identity avatar XML", flags=re.S)
write(rel, text)


# ---------------------------------------------------------------------------
# Manifest receiver is explicit/exported=false; no broad implicit filter.
# ---------------------------------------------------------------------------
rel = "source code/AndroidManifest.xml"
text = read(rel)
anchor = '        <receiver android:name="com.harleytg.forum.dev.HcfNotificationEngine$BootReceiver" android:enabled="true" android:exported="false"><intent-filter><action android:name="android.intent.action.BOOT_COMPLETED"/><action android:name="android.intent.action.MY_PACKAGE_REPLACED"/></intent-filter></receiver>\n'
receiver = anchor + '        <receiver android:name="com.harleytg.forum.dev.HcfNotificationActions$ActionReceiver" android:enabled="true" android:exported="false"/>\n'
text = replace_once(text, anchor, receiver, "manifest notification action receiver")
write(rel, text)


# ---------------------------------------------------------------------------
# Minimal README note only; no changelog expansion.
# ---------------------------------------------------------------------------
rel = "README.md"
text = read(rel)
anchor = '''The shared Stable + Dev Digital Asset Links source is [`configs/app-links/assetlinks.json`](./configs/app-links/assetlinks.json). Its canonical deployment source is the `main`-branch path `configs/app-links/assetlinks.json`; this Dev release does not modify or rebuild the Stable app.\n'''
note = anchor + '''\nIndividual authenticated forum message notifications can expose Android inline **Reply** and **Mark as read** when the Flarum payload contains a resolvable server notification/conversation target. Logs & Diagnostics records only sanitized action state/status metadata and never stores notification message or inline-reply content.\n'''
text = replace_once(text, anchor, note, "README notification action note")
write(rel, text)

print("Native notification actions, avatar polish, and diagnostics hardening patched.")
