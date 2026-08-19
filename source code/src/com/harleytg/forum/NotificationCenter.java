package com.harleytg.forum.dev;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Native notification feature owner: channels, readable text, history, DND,
 * stable identities, conversation reply actions and local diagnostics.
 */
final class NotificationCenter {
    static final String ACTION_NOTIFICATION_EVENT = "com.harleytg.forum.dev.NOTIFICATION_EVENT";
    static final String EXTRA_EVENT_TITLE = "event_title";
    static final String EXTRA_EVENT_BODY = "event_body";
    static final String EXTRA_EVENT_URL = "event_url";
    static final String EXTRA_EVENT_COUNT = "event_count";
    static final String ACTION_REPLY = "com.harleytg.forum.dev.NOTIFICATION_REPLY";
    static final String EXTRA_CONVERSATION_ID = "conversation_id";
    static final String EXTRA_NOTIFICATION_ID = "notification_id";
    static final String EXTRA_DESTINATION = "notification_destination";
    static final String REMOTE_INPUT_KEY = "hcf_inline_reply";

    static final String MESSAGE_CHANNEL_ID = "hcf_messages";
    static final String ACTIVITY_CHANNEL_ID = "hcf_forum_activity";
    static final String UPDATE_CHANNEL_ID = "app_updates_v1";
    static final String SERVICE_CHANNEL_ID = "instant_notification_service_v1";
    private static final String FORUM_GROUP_KEY = "hcf_forum_alerts";
    private static final int FORUM_SUMMARY_ID = 41072;
    private static volatile Bitmap cachedLargeIcon;

    static final class Entry {
        final String id;
        final String type;
        final String title;
        final String message;
        final long timestamp;
        final String url;
        final String conversationId;
        final boolean opened;

        Entry(String id, String type, String title, String message, long timestamp,
              String url, String conversationId, boolean opened) {
            this.id = id == null ? "" : id;
            this.type = type == null ? "activity" : type;
            this.title = title == null ? "" : title;
            this.message = message == null ? "" : message;
            this.timestamp = timestamp;
            this.url = url == null ? "" : url;
            this.conversationId = conversationId == null ? "" : conversationId;
            this.opened = opened;
        }

        JSONObject json() {
            JSONObject out = new JSONObject();
            try {
                out.put("id", id);
                out.put("type", type);
                out.put("title", title);
                out.put("message", message);
                out.put("timestamp", timestamp);
                out.put("url", url);
                out.put("conversationId", conversationId);
                out.put("opened", opened);
            } catch (Throwable ignored) {}
            return out;
        }

        static Entry from(JSONObject object) {
            return new Entry(object.optString("id", ""), object.optString("type", "activity"),
                    object.optString("title", ""), object.optString("message", ""),
                    object.optLong("timestamp", 0L), object.optString("url", ""),
                    object.optString("conversationId", ""), object.optBoolean("opened", false));
        }
    }

    static void createChannels(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationManager manager = manager(context);
            if (manager == null) return;

            NotificationChannel messages = new NotificationChannel(
                    MESSAGE_CHANNEL_ID, "HCF Messages", NotificationManager.IMPORTANCE_HIGH);
            messages.setDescription("Private conversations and direct messages from Harley's Clan Forum");
            messages.enableVibration(true);
            messages.enableLights(true);
            messages.setShowBadge(true);
            messages.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            manager.createNotificationChannel(messages);

            NotificationChannel activity = new NotificationChannel(
                    ACTIVITY_CHANNEL_ID, "HCF Forum Activity", NotificationManager.IMPORTANCE_DEFAULT);
            activity.setDescription("Mentions, replies, follows and other forum activity");
            activity.enableVibration(true);
            activity.setShowBadge(true);
            activity.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            manager.createNotificationChannel(activity);

            NotificationChannel updates = new NotificationChannel(
                    UPDATE_CHANNEL_ID, "HCF Updates", NotificationManager.IMPORTANCE_DEFAULT);
            updates.setDescription("Harley's Clan Forum app update and install-ready alerts");
            updates.setShowBadge(false);
            updates.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            manager.createNotificationChannel(updates);

            NotificationChannel service = new NotificationChannel(
                    SERVICE_CHANNEL_ID, "HCF Background Service", NotificationManager.IMPORTANCE_LOW);
            service.setDescription("Background notification synchronization status");
            service.setSound(null, null);
            service.enableVibration(false);
            service.setShowBadge(false);
            service.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            manager.createNotificationChannel(service);
        } catch (Throwable error) {
            AppSettings.prefs(context).edit().putString(AppPrefs.NOTIFICATION_LAST_ERROR,
                    error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage())).apply();
            AppLogger.error(context, "notification_channels", error.getClass().getSimpleName());
        }
    }

    static boolean enabledByUser(Context context) {
        return AppSettings.prefs(context).getBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true);
    }

    static boolean hasRuntimePermission(Context context) {
        return Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean appNotificationsEnabled(Context context) {
        NotificationManager manager = manager(context);
        return manager != null && manager.areNotificationsEnabled();
    }

    static int messageChannelImportance(Context context) {
        if (Build.VERSION.SDK_INT < 26) return NotificationManager.IMPORTANCE_HIGH;
        NotificationManager manager = manager(context);
        if (manager == null) return NotificationManager.IMPORTANCE_NONE;
        NotificationChannel channel = manager.getNotificationChannel(MESSAGE_CHANNEL_ID);
        return channel == null ? NotificationManager.IMPORTANCE_NONE : channel.getImportance();
    }

    static boolean canAlert(Context context) {
        if (!enabledByUser(context) || !hasRuntimePermission(context) || !appNotificationsEnabled(context)) return false;
        if (AppSettings.isDndActive(context)) return false;
        return Build.VERSION.SDK_INT < 26 || messageChannelImportance(context) != NotificationManager.IMPORTANCE_NONE;
    }

    static String status(Context context) {
        if (!enabledByUser(context)) return "Disabled in HCF App Settings";
        if (!hasRuntimePermission(context)) return "Android notification permission denied";
        if (!appNotificationsEnabled(context)) return "Blocked in Android notification settings";
        if (AppSettings.isDndActive(context)) return "DND active • history continues";
        if (Build.VERSION.SDK_INT >= 26 && messageChannelImportance(context) == NotificationManager.IMPORTANCE_NONE) {
            return "HCF Messages channel is off";
        }
        return "Ready • native notifications enabled";
    }

    static void openAndroidSettings(Context context) {
        try {
            Intent intent;
            if (Build.VERSION.SDK_INT >= 26) {
                intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
                intent.putExtra(Settings.EXTRA_CHANNEL_ID, MESSAGE_CHANNEL_ID);
            } else {
                intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable error) {
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + context.getPackageName()));
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(fallback);
        }
    }

    static void postFromBridge(Context context, String title, String body, String url) {
        Uri destination = safeDestination(url);
        String safeTitle = readableText(title, "Harley's Clan Forum", 120);
        String safeBody = readableText(body, "New activity on Harley's Clan Forum", 500);
        String conversationId = LinkRouter.conversationId(destination);
        String eventId = "bridge:" + Integer.toHexString((safeTitle + "\n" + safeBody + "\n" + destination).hashCode());
        if (!markHandled(context, eventId)) return;
        postEvent(context, eventId, conversationId.isEmpty() ? "activity" : "message",
                safeTitle, safeBody, destination, conversationId, stableId(eventId), false);
    }

    static synchronized int recordForumNotificationCount(Context context, int newCount, String host, String source) {
        if (context == null) return 0;
        if (!LinkRouter.isTrustedHost(host)) host = LinkRouter.primaryHost();
        SharedPreferences prefs = AppSettings.prefs(context);
        int normalized = Math.max(0, newCount);
        boolean hadBaseline = prefs.contains(AppPrefs.LAST_NOTIFICATION_COUNT);
        int previous = prefs.getInt(AppPrefs.LAST_NOTIFICATION_COUNT, normalized);
        String previousHost = prefs.getString(AppPrefs.ACTIVE_HOST, "");
        boolean countChanged = !hadBaseline || normalized != previous;
        boolean hostChanged = previousHost == null || !host.equalsIgnoreCase(previousHost);
        SharedPreferences.Editor edit = prefs.edit();
        if (countChanged) {
            edit.putInt(AppPrefs.LAST_NOTIFICATION_COUNT, normalized)
                    .putLong(AppPrefs.NOTIFICATION_LAST_COUNT_CHANGE_AT, System.currentTimeMillis());
        }
        if (hostChanged) edit.putString(AppPrefs.ACTIVE_HOST, host);
        if (countChanged || hostChanged) edit.apply();
        if (countChanged) broadcastEvent(context, "", "", LinkRouter.home(host), normalized);
        return hadBaseline && normalized > previous ? normalized - previous : 0;
    }

    static synchronized int deliverDetailedAlerts(Context context, List<ForumNotificationClient.Alert> alerts,
                                                   int expectedDelta, String host, String source) {
        if (context == null || expectedDelta <= 0) return 0;
        if (!LinkRouter.isTrustedHost(host)) host = LinkRouter.primaryHost();
        int posted = 0;
        int processed = 0;
        int max = Math.max(1, expectedDelta);
        if (alerts != null) {
            for (ForumNotificationClient.Alert alert : alerts) {
                if (alert == null || alert.id == null || alert.id.trim().isEmpty()) continue;
                String eventId = "server:" + alert.id.trim();
                if (!markHandled(context, eventId)) continue;
                Uri destination = safeDestination(alert.url);
                String conversationId = LinkRouter.conversationId(destination);
                String title = readableText(alert.title, conversationId.isEmpty()
                        ? "Harley's Clan Forum" : "New message", 120);
                String body = readableText(alert.body, conversationId.isEmpty()
                        ? "New activity on Harley's Clan Forum" : "You have a new private message.", 500);
                processed++;
                boolean nativePosted = postEvent(context, eventId,
                        conversationId.isEmpty() ? "activity" : "message",
                        title, body, destination, conversationId, stableId(eventId), true);
                if (nativePosted) posted++;
                if (processed >= max) break;
            }
        }
        if (posted > 1) postGroupSummary(context, posted, host);
        AppLogger.info(context, "notification_details", (source == null ? "sync" : source)
                + " | processed=" + processed + " | posted=" + posted + " | dnd=" + AppSettings.isDndActive(context));
        return posted;
    }

    static void postGenericDelta(Context context, int delta, String host) {
        if (context == null || delta <= 0) return;
        if (!LinkRouter.isTrustedHost(host)) host = LinkRouter.primaryHost();
        String body = delta == 1 ? "You have a new forum notification."
                : "You have " + delta + " new forum notifications.";
        String eventId = "generic:" + host + ":" + AppSettings.prefs(context).getInt(AppPrefs.LAST_NOTIFICATION_COUNT, delta);
        if (!markHandled(context, eventId)) return;
        postEvent(context, eventId, "activity", "Harley's Clan Forum", body,
                LinkRouter.forumPath(host, "notifications"), "", FORUM_SUMMARY_ID, true);
    }

    private static boolean postEvent(Context context, String eventId, String type, String title, String body,
                                     Uri destination, String conversationId, int notificationId, boolean grouped) {
        createChannels(context);
        String safeTitle = readableText(title, "Harley's Clan Forum", 120);
        String safeBody = readableText(body, "New activity on Harley's Clan Forum", 500);
        Uri safeDestination = destination != null && LinkRouter.isInternal(destination)
                ? destination : Uri.parse(LinkRouter.home(LinkRouter.primaryHost()));
        long now = System.currentTimeMillis();
        appendHistory(context, new Entry(eventId, type, safeTitle, safeBody, now,
                safeDestination.toString(), conversationId, false));
        AppSettings.prefs(context).edit().putLong(AppPrefs.NOTIFICATION_LAST_RECEIVED_AT, now).apply();
        broadcastEvent(context, safeTitle, safeBody, safeDestination.toString(), -1);

        if (!canAlert(context)) {
            AppLogger.info(context, "notification_suppressed", status(context) + " | stored-in-history");
            return false;
        }

        PendingIntent open = openPendingIntent(context, safeDestination, notificationId);
        String channel = conversationId == null || conversationId.isEmpty() ? ACTIVITY_CHANNEL_ID : MESSAGE_CHANNEL_ID;
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, channel) : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_notification_paw)
                .setLargeIcon(largeIcon(context))
                .setContentTitle(safeTitle)
                .setContentText(safeBody)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setOnlyAlertOnce(true)
                .setWhen(now)
                .setShowWhen(true);
        if (grouped) builder.setGroup(FORUM_GROUP_KEY);

        if (conversationId != null && !conversationId.isEmpty()) {
            Notification.MessagingStyle style = new Notification.MessagingStyle("You")
                    .setConversationTitle("Messenger")
                    .addMessage(safeBody, now, senderFromTitle(safeTitle));
            builder.setStyle(style).setCategory(Notification.CATEGORY_MESSAGE)
                    .setPriority(Notification.PRIORITY_HIGH);
            builder.addAction(replyAction(context, conversationId, safeDestination, notificationId));
            builder.addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_view, "Open", open).build());
        } else {
            builder.setStyle(new Notification.BigTextStyle().bigText(safeBody))
                    .setCategory(Notification.CATEGORY_SOCIAL)
                    .setPriority(Notification.PRIORITY_DEFAULT);
        }
        NotificationManager manager = manager(context);
        if (manager != null) {
            manager.notify(notificationId, builder.build());
            return true;
        }
        return false;
    }

    private static Notification.Action replyAction(Context context, String conversationId, Uri destination, int notificationId) {
        RemoteInput input = new RemoteInput.Builder(REMOTE_INPUT_KEY).setLabel("Reply").build();
        Intent intent = new Intent(context, NotificationReplyReceiver.class)
                .setAction(ACTION_REPLY)
                .putExtra(EXTRA_CONVERSATION_ID, conversationId)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(EXTRA_DESTINATION, destination.toString());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent pending = PendingIntent.getBroadcast(context, notificationId ^ 0x5a5a,
                intent, flags);
        return new Notification.Action.Builder(android.R.drawable.ic_menu_send, "Reply", pending)
                .addRemoteInput(input).build();
    }

    static Notification buildServiceNotification(Context context) {
        createChannels(context);
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 41070, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent centerIntent = new Intent(context, NotificationHistoryActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent center = PendingIntent.getActivity(context, 41073, centerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, SERVICE_CHANNEL_ID) : new Notification.Builder(context);
        return builder.setSmallIcon(R.drawable.ic_notification_paw)
                .setContentTitle("Harley's Clan Forum")
                .setContentText("Native alert service active")
                .setContentIntent(pending)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_recent_history,
                        "Notification Center", center).build())
                .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPriority(Notification.PRIORITY_MIN).build();
    }

    static void postUpdateAvailable(Context context, UpdateChecker.Release release) {
        if (context == null || release == null) return;
        createChannels(context);
        if (!hasRuntimePermission(context) || !appNotificationsEnabled(context)) return;
        Intent open = new Intent(context, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 51001, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, UPDATE_CHANNEL_ID) : new Notification.Builder(context);
        String body = "v" + UpdateChecker.displayVersion(release)
                + (release.versionCode > 0 ? " • build " + release.versionCode : "") + " is ready for Development / Beta.";
        builder.setSmallIcon(R.drawable.ic_notification_paw).setLargeIcon(largeIcon(context))
                .setContentTitle("Beta update available").setContentText(body).setContentIntent(pending)
                .setAutoCancel(true).setCategory(Notification.CATEGORY_SYSTEM)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setPriority(Notification.PRIORITY_DEFAULT);
        NotificationManager manager = manager(context);
        if (manager != null) manager.notify(51001, builder.build());
    }

    static void postUpdateReady(Context context, String tag, long downloadId) {
        if (context == null) return;
        createChannels(context);
        if (!hasRuntimePermission(context) || !appNotificationsEnabled(context)) return;
        Intent install = new Intent(context, SettingsActivity.class)
                .setAction("com.harleytg.forum.dev.INSTALL_UPDATE")
                .putExtra("download_id", downloadId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 51002, install,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, UPDATE_CHANNEL_ID) : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_notification_paw).setLargeIcon(largeIcon(context))
                .setContentTitle("Beta update ready to install")
                .setContentText((tag == null || tag.trim().isEmpty() ? "Downloaded update" : tag) + " is ready.")
                .setContentIntent(pending).setAutoCancel(true).setCategory(Notification.CATEGORY_SYSTEM)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setPriority(Notification.PRIORITY_DEFAULT);
        NotificationManager manager = manager(context);
        if (manager != null) manager.notify(51002, builder.build());
    }

    static void showReplyFallback(Context context, String conversationId, String text, Uri destination, int notificationId) {
        if (context == null || conversationId == null || !conversationId.matches("[0-9]+")) return;
        Uri safeDestination = destination != null && LinkRouter.isInternal(destination)
                ? destination : LinkRouter.forumPath(LinkRouter.primaryHost(), "conversations/" + conversationId);
        AppSettings.saveReplyDraft(context, conversationId, text, safeDestination.toString());
        String id = "reply-draft:" + conversationId + ":" + System.currentTimeMillis();
        appendHistory(context, new Entry(id, "reply_draft", "Reply saved locally",
                readableText(text, "Reply draft", 500), System.currentTimeMillis(),
                safeDestination.toString(), conversationId, false));
        createChannels(context);
        if (!hasRuntimePermission(context) || !appNotificationsEnabled(context)) return;
        PendingIntent open = openPendingIntent(context, safeDestination, notificationId);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, MESSAGE_CHANNEL_ID) : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_notification_paw).setLargeIcon(largeIcon(context))
                .setContentTitle("Reply saved — finish in Messenger")
                .setContentText("The reply was not sent automatically. Tap to open the conversation; your draft is saved locally.")
                .setStyle(new Notification.BigTextStyle().bigText(
                        "The reply was not sent automatically. Tap to open the conversation; your draft is saved locally."))
                .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_MESSAGE).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPriority(Notification.PRIORITY_HIGH);
        NotificationManager manager = manager(context);
        if (manager != null) manager.notify(notificationId, builder.build());
    }

    static synchronized List<Entry> history(Context context) {
        ArrayList<Entry> entries = new ArrayList<>();
        String raw = AppSettings.prefs(context).getString(AppPrefs.NOTIFICATION_HISTORY_JSON, "[]");
        try {
            JSONArray array = new JSONArray(raw == null || raw.trim().isEmpty() ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) entries.add(Entry.from(object));
            }
        } catch (Throwable error) {
            AppSettings.prefs(context).edit().putString(AppPrefs.NOTIFICATION_HISTORY_JSON, "[]")
                    .putString(AppPrefs.NOTIFICATION_LAST_ERROR, "History reset: " + error.getClass().getSimpleName()).apply();
        }
        return entries;
    }

    static synchronized void clearHistory(Context context) {
        AppSettings.prefs(context).edit().putString(AppPrefs.NOTIFICATION_HISTORY_JSON, "[]").apply();
    }

    static synchronized void clearReadHistory(Context context) {
        List<Entry> current = history(context);
        JSONArray out = new JSONArray();
        for (Entry entry : current) if (!entry.opened) out.put(entry.json());
        AppSettings.prefs(context).edit().putString(AppPrefs.NOTIFICATION_HISTORY_JSON, out.toString()).apply();
    }

    static synchronized void markHistoryOpened(Context context, String id) {
        List<Entry> current = history(context);
        JSONArray out = new JSONArray();
        for (Entry entry : current) {
            out.put(new Entry(entry.id, entry.type, entry.title, entry.message, entry.timestamp,
                    entry.url, entry.conversationId, entry.opened || entry.id.equals(id)).json());
        }
        AppSettings.prefs(context).edit().putString(AppPrefs.NOTIFICATION_HISTORY_JSON, out.toString()).apply();
    }

    static int historyCount(Context context) { return history(context).size(); }

    static String diagnostics(Context context) {
        SharedPreferences prefs = AppSettings.prefs(context);
        long sync = prefs.getLong(AppPrefs.NOTIFICATION_LAST_SYNC_AT, 0L);
        long received = prefs.getLong(AppPrefs.NOTIFICATION_LAST_RECEIVED_AT, 0L);
        String error = prefs.getString(AppPrefs.NOTIFICATION_LAST_ERROR, "");
        boolean firebaseLoaded;
        try { firebaseLoaded = AppConfig.firebase().isValid(); } catch (Throwable ignored) { firebaseLoaded = false; }
        boolean serviceEnabled = prefs.getBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true)
                && prefs.getBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, true);
        return "Notification permission: " + (hasRuntimePermission(context) ? "Granted" : "Denied")
                + "\nForum notifications: " + (enabledByUser(context) ? "Enabled" : "Disabled")
                + "\nNative notification service: " + (serviceEnabled ? "Enabled" : "Inactive")
                + "\nFirebase config: " + (firebaseLoaded ? "Loaded" : "Missing")
                + "\nFCM: " + (BuildInfo.FCM_CONFIGURED ? "Configured" : "Not configured")
                + "\nLast notification sync: " + timeOrNever(sync)
                + "\nLast notification received: " + timeOrNever(received)
                + "\nLast error: " + (error == null || error.trim().isEmpty() ? "None" : error)
                + "\nDND: " + AppSettings.dndLabel(context)
                + "\nNotification history count: " + historyCount(context);
    }

    private static synchronized void appendHistory(Context context, Entry entry) {
        List<Entry> current = history(context);
        JSONArray out = new JSONArray();
        out.put(entry.json());
        int keep = Math.min(current.size(), AppSettings.HISTORY_LIMIT - 1);
        for (int i = 0; i < keep; i++) out.put(current.get(i).json());
        AppSettings.prefs(context).edit().putString(AppPrefs.NOTIFICATION_HISTORY_JSON, out.toString()).apply();
    }

    private static synchronized boolean markHandled(Context context, String id) {
        SharedPreferences prefs = AppSettings.prefs(context);
        LinkedHashSet<String> values = splitIds(prefs.getString(AppPrefs.NOTIFICATION_RECENT_IDS, ""));
        if (values.contains(id)) return false;
        values.add(id);
        trimIds(values, 250);
        prefs.edit().putString(AppPrefs.NOTIFICATION_RECENT_IDS, joinIds(values)).apply();
        return true;
    }

    private static String readableText(String value, String fallback, int max) {
        String raw = value == null ? "" : value.trim();
        String text = raw;
        if (looksJson(raw)) text = extractJson(raw);
        text = stripMarkup(text);
        if (text.isEmpty()) text = fallback;
        if (looksJson(text)) text = fallback;
        if (text.length() > max) text = text.substring(0, max) + "…";
        return text;
    }

    private static String extractJson(String raw) {
        try {
            Object root = raw.startsWith("[") ? new JSONArray(raw) : new JSONObject(raw);
            String found = deepReadable(root, 0);
            return found == null ? "" : found;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String deepReadable(Object value, int depth) {
        if (value == null || value == JSONObject.NULL || depth > 7) return "";
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String[] preferred = {"title", "subject", "message", "content", "body", "text", "preview",
                    "excerpt", "displayName", "display_name", "username", "discussionTitle", "conversationTitle"};
            for (String key : preferred) {
                if (!object.has(key)) continue;
                String found = deepReadable(object.opt(key), depth + 1);
                if (!found.isEmpty()) return found;
            }
            JSONArray names = object.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String found = deepReadable(object.opt(names.optString(i)), depth + 1);
                    if (!found.isEmpty()) return found;
                }
            }
            return "";
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                String found = deepReadable(array.opt(i), depth + 1);
                if (!found.isEmpty()) return found;
            }
            return "";
        }
        String scalar = String.valueOf(value).trim();
        if (looksJson(scalar)) return extractJson(scalar);
        return scalar;
    }

    private static boolean looksJson(String value) {
        if (value == null) return false;
        String v = value.trim();
        return (v.startsWith("{") && v.endsWith("}")) || (v.startsWith("[") && v.endsWith("]"));
    }

    private static String stripMarkup(String value) {
        if (value == null) return "";
        return value.replaceAll("<[^>]+>", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replaceAll("\\s+", " ").trim();
    }

    private static Uri safeDestination(String url) {
        try {
            Uri value = Uri.parse(url == null ? "" : url);
            if (LinkRouter.isInternal(value)) return value;
        } catch (Throwable ignored) {}
        return Uri.parse(LinkRouter.home(LinkRouter.primaryHost()));
    }

    private static PendingIntent openPendingIntent(Context context, Uri destination, int requestCode) {
        Intent open = new Intent(context, MainActivity.class)
                .setAction("com.harleytg.forum.dev.OPEN_NOTIFICATION")
                .setData(destination)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, requestCode, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void postGroupSummary(Context context, int count, String host) {
        if (!canAlert(context) || count <= 1) return;
        Uri destination = LinkRouter.forumPath(host, "notifications");
        PendingIntent open = openPendingIntent(context, destination, FORUM_SUMMARY_ID);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, ACTIVITY_CHANNEL_ID) : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_notification_paw).setLargeIcon(largeIcon(context))
                .setContentTitle("Harley's Clan Forum").setContentText(count + " new notifications")
                .setContentIntent(open).setGroup(FORUM_GROUP_KEY).setGroupSummary(true)
                .setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = manager(context);
        if (manager != null) manager.notify(FORUM_SUMMARY_ID, builder.build());
    }

    private static void broadcastEvent(Context context, String title, String body, String url, int count) {
        try {
            Intent event = new Intent(ACTION_NOTIFICATION_EVENT).setPackage(context.getPackageName());
            event.putExtra(EXTRA_EVENT_TITLE, title == null ? "" : title);
            event.putExtra(EXTRA_EVENT_BODY, body == null ? "" : body);
            event.putExtra(EXTRA_EVENT_URL, url == null ? "" : url);
            event.putExtra(EXTRA_EVENT_COUNT, count);
            context.sendBroadcast(event);
        } catch (Throwable ignored) {}
    }

    private static String senderFromTitle(String title) {
        String value = title == null ? "" : title.trim();
        String prefix = "New message from ";
        return value.startsWith(prefix) && value.length() > prefix.length() ? value.substring(prefix.length()) : value;
    }

    private static NotificationManager manager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static Bitmap largeIcon(Context context) {
        Bitmap value = cachedLargeIcon;
        if (value != null && !value.isRecycled()) return value;
        synchronized (NotificationCenter.class) {
            value = cachedLargeIcon;
            if (value == null || value.isRecycled()) {
                value = BitmapFactory.decodeResource(context.getResources(), R.drawable.htg_app_logo);
                cachedLargeIcon = value;
            }
            return value;
        }
    }

    private static int stableId(String id) { return 0x24000000 | (id.hashCode() & 0x0fffffff); }

    private static LinkedHashSet<String> splitIds(String raw) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (raw == null || raw.isEmpty()) return values;
        for (String value : raw.split("\\n")) if (!value.trim().isEmpty()) values.add(value.trim());
        return values;
    }

    private static void trimIds(Set<String> values, int max) {
        while (values.size() > max) {
            Iterator<String> iterator = values.iterator();
            if (!iterator.hasNext()) break;
            iterator.next(); iterator.remove();
        }
    }

    private static String joinIds(Set<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) { if (out.length() > 0) out.append('\n'); out.append(value); }
        return out.toString();
    }

    private static String timeOrNever(long value) {
        if (value <= 0L) return "Never";
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new java.util.Date(value));
    }

    private NotificationCenter() {}
}

/** Compatibility facade retained for existing Activities/Services. */
final class NotificationHelper {
    static final String ACTION_NOTIFICATION_EVENT = NotificationCenter.ACTION_NOTIFICATION_EVENT;
    static final String EXTRA_EVENT_TITLE = NotificationCenter.EXTRA_EVENT_TITLE;
    static final String EXTRA_EVENT_BODY = NotificationCenter.EXTRA_EVENT_BODY;
    static final String EXTRA_EVENT_URL = NotificationCenter.EXTRA_EVENT_URL;
    static final String EXTRA_EVENT_COUNT = NotificationCenter.EXTRA_EVENT_COUNT;
    static final String CHANNEL_ID = NotificationCenter.MESSAGE_CHANNEL_ID;
    static final String LEGACY_CHANNEL_ID = "forum_messages_heads_up_v2";
    static final String CHANNEL_NAME = "HCF Messages";
    static final String UPDATE_CHANNEL_ID = NotificationCenter.UPDATE_CHANNEL_ID;
    static final String UPDATE_CHANNEL_NAME = "HCF Updates";
    static final String INSTANT_SERVICE_CHANNEL_ID = NotificationCenter.SERVICE_CHANNEL_ID;
    static final String INSTANT_SERVICE_CHANNEL_NAME = "HCF Background Service";

    static void createChannel(Context context) { NotificationCenter.createChannels(context); }
    static boolean isEnabledByUser(Context context) { return NotificationCenter.enabledByUser(context); }
    static boolean hasRuntimePermission(Context context) { return NotificationCenter.hasRuntimePermission(context); }
    static boolean areAppNotificationsEnabled(Context context) { return NotificationCenter.appNotificationsEnabled(context); }
    static int channelImportance(Context context) { return NotificationCenter.messageChannelImportance(context); }
    static boolean headsUpChannelReady(Context context) { return channelImportance(context) >= NotificationManager.IMPORTANCE_HIGH; }
    static boolean canPost(Context context) { return NotificationCenter.canAlert(context); }
    static String status(Context context) { return NotificationCenter.status(context); }
    static void openChannelSettings(Context context) { NotificationCenter.openAndroidSettings(context); }
    static void post(Context context, String title, String body, String url) { NotificationCenter.postFromBridge(context, title, body, url); }
    static Notification buildInstantServiceNotification(Context context) { return NotificationCenter.buildServiceNotification(context); }
    static synchronized int recordForumNotificationCount(Context context, int count, String host, String source) {
        return NotificationCenter.recordForumNotificationCount(context, count, host, source);
    }
    static synchronized int deliverDetailedAlerts(Context context, List<ForumNotificationClient.Alert> alerts,
                                                   int expectedDelta, String host, String source) {
        return NotificationCenter.deliverDetailedAlerts(context, alerts, expectedDelta, host, source);
    }
    static void postGenericDelta(Context context, int delta, String host) { NotificationCenter.postGenericDelta(context, delta, host); }
    static void postUpdateAvailable(Context context, UpdateChecker.Release release) { NotificationCenter.postUpdateAvailable(context, release); }
    static void postUpdateReady(Context context, String tag, long downloadId) { NotificationCenter.postUpdateReady(context, tag, downloadId); }
    private NotificationHelper() {}
}

/** Coordinates count checks, hydration and status while delegating delivery to NotificationCenter. */
final class ForumNotificationSync {
    private static final long STATUS_WRITE_INTERVAL_MS = 15_000L;
    static final class Outcome {
        final int count; final int delivered; final long latencyMs;
        Outcome(int count, int delivered, long latencyMs) { this.count = count; this.delivered = delivered; this.latencyMs = latencyMs; }
    }

    static Outcome perform(Context context, String host, String userId, String source) throws Exception {
        long started = System.currentTimeMillis();
        try {
            int count = ForumNotificationClient.fetchNewCount(context, host, userId);
            int delta = NotificationCenter.recordForumNotificationCount(context, count, host, source);
            int delivered = 0;
            if (delta > 0) {
                try {
                    List<ForumNotificationClient.Alert> alerts = ForumNotificationClient.fetchLatest(context, host, Math.max(delta + 4, 8));
                    delivered = NotificationCenter.deliverDetailedAlerts(context, alerts, delta, host, source);
                } catch (Throwable detailError) {
                    NotificationCenter.postGenericDelta(context, delta, host);
                    AppLogger.warn(context, "notification_detail", detailError.getClass().getSimpleName() + " | generic-fallback");
                }
            }
            long latency = Math.max(0L, System.currentTimeMillis() - started);
            RuntimeDiagnostics.syncSucceeded(latency);
            recordStatus(context, "Live • synced", latency, false, null);
            return new Outcome(count, delivered, latency);
        } catch (Exception error) {
            long latency = Math.max(0L, System.currentTimeMillis() - started);
            RuntimeDiagnostics.syncFailed();
            recordStatus(context, "Waiting for connection", latency, true, error);
            throw error;
        }
    }

    static void deliverObservedCountAsync(Context context, String host, int delta, String source) {
        if (context == null || delta <= 0) return;
        Context app = context.getApplicationContext();
        AppExecutors.network().execute(() -> {
            long started = System.currentTimeMillis();
            try {
                List<ForumNotificationClient.Alert> alerts = ForumNotificationClient.fetchLatest(app, host, Math.max(delta + 4, 8));
                NotificationCenter.deliverDetailedAlerts(app, alerts, delta, host, source);
                long latency = System.currentTimeMillis() - started;
                RuntimeDiagnostics.syncSucceeded(latency);
                recordStatus(app, "Live • synced", latency, false, null);
            } catch (Throwable error) {
                NotificationCenter.postGenericDelta(app, delta, host);
                long latency = System.currentTimeMillis() - started;
                RuntimeDiagnostics.syncFailed();
                recordStatus(app, "Live • detail unavailable", latency, true, error);
                AppLogger.warn(app, "notification_detail", error.getClass().getSimpleName());
            }
        });
    }

    private static void recordStatus(Context context, String status, long latency, boolean force, Throwable error) {
        SharedPreferences prefs = AppSettings.prefs(context);
        long now = System.currentTimeMillis();
        String oldStatus = prefs.getString(AppPrefs.NOTIFICATION_LAST_SYNC_STATUS, "");
        long lastWrite = prefs.getLong(AppPrefs.NOTIFICATION_LAST_SYNC_AT, 0L);
        if (!force && status.equals(oldStatus) && now - lastWrite < STATUS_WRITE_INTERVAL_MS) return;
        SharedPreferences.Editor edit = prefs.edit().putLong(AppPrefs.NOTIFICATION_LAST_SYNC_AT, now)
                .putString(AppPrefs.NOTIFICATION_LAST_SYNC_STATUS, status)
                .putLong(AppPrefs.NOTIFICATION_LAST_SYNC_LATENCY_MS, Math.max(0L, latency));
        if (error == null) edit.remove(AppPrefs.NOTIFICATION_LAST_ERROR);
        else edit.putString(AppPrefs.NOTIFICATION_LAST_ERROR,
                error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
        edit.apply();
    }

    private ForumNotificationSync() {}
}

/** Background JobScheduler resilience layer; the foreground service remains the fast path. */
final class NotificationSyncScheduler {
    private static final int JOB_ID = 41071;
    private static final long PERIOD_MS = 15L * 60L * 1000L;

    static void apply(Context context) {
        if (context == null) return;
        try {
            SharedPreferences prefs = AppSettings.prefs(context);
            if (!prefs.getBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true)
                    || !prefs.getBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, true)) {
                InstantNotificationService.stop(context); cancel(context); return;
            }
            String userId = prefs.getString(AppPrefs.SESSION_USER_ID, "");
            if (userId != null && !userId.trim().isEmpty()) InstantNotificationService.start(context);
            else InstantNotificationService.stop(context);
            schedule(context);
        } catch (Throwable error) {
            AppLogger.error(context, "notification_sync_apply", error.getClass().getSimpleName());
        }
    }

    static void schedule(Context context) {
        if (context == null) return;
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler == null) return;
            JobInfo info = new JobInfo.Builder(JOB_ID, new ComponentName(context, NotificationSyncJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(PERIOD_MS).setPersisted(true).build();
            int result = scheduler.schedule(info);
            AppLogger.info(context, "notification_sync_schedule", result == JobScheduler.RESULT_SUCCESS ? "scheduled" : "failed");
        } catch (Throwable error) {
            AppLogger.error(context, "notification_sync_schedule", error.getClass().getSimpleName());
        }
    }

    static void cancel(Context context) {
        if (context == null) return;
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler != null) scheduler.cancel(JOB_ID);
        } catch (Throwable error) {
            AppLogger.error(context, "notification_sync_cancel", error.getClass().getSimpleName());
        }
    }
    private NotificationSyncScheduler() {}
}
