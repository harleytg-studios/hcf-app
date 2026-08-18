package com.harleytg.forum.dev;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class NotificationHelper {
    static final String ACTION_NOTIFICATION_EVENT = "com.harleytg.forum.dev.NOTIFICATION_EVENT";
    static final String EXTRA_EVENT_TITLE = "event_title";
    static final String EXTRA_EVENT_BODY = "event_body";
    static final String EXTRA_EVENT_URL = "event_url";
    static final String EXTRA_EVENT_COUNT = "event_count";
    private static final String FORUM_GROUP_KEY = "hcf_forum_alerts";
    private static final int FORUM_SUMMARY_ID = 41072;
    private static volatile Bitmap cachedLargeIcon;
    // New channel ID is intentional. Android won't let an app raise the importance
    // of an existing channel after it has been created, so this migrates the dev app
    // from the old DEFAULT channel to a true HIGH / heads-up channel.
    static final String CHANNEL_ID = "forum_messages_heads_up_v2";
    static final String LEGACY_CHANNEL_ID = "forum_messages";
    static final String CHANNEL_NAME = "Messages & alerts";
    static final String UPDATE_CHANNEL_ID = "app_updates_v1";
    static final String UPDATE_CHANNEL_NAME = "App updates";
    static final String INSTANT_SERVICE_CHANNEL_ID = "instant_notification_service_v1";
    static final String INSTANT_SERVICE_CHANNEL_NAME = "Instant notification service";

    static void createChannel(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;

            Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Forum messages, mentions, replies and important alerts from Harley's Clan Forum");
            channel.enableVibration(true);
            channel.enableLights(true);
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

            // Some OEM Android builds have thrown from custom channel sound/audio
            // configuration. The default system channel sound is sufficient for
            // heads-up notifications, so keep channel creation minimal and robust.
            try {
                Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                AudioAttributes audio = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                channel.setSound(soundUri, audio);
            } catch (Throwable ignored) {}

            manager.createNotificationChannel(channel);

            NotificationChannel instantServiceChannel = new NotificationChannel(
                    INSTANT_SERVICE_CHANNEL_ID,
                    INSTANT_SERVICE_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
            );
            instantServiceChannel.setDescription("Keeps Harley's Clan Forum notifications checking in the foreground and background");
            instantServiceChannel.setSound(null, null);
            instantServiceChannel.enableVibration(false);
            instantServiceChannel.setShowBadge(false);
            instantServiceChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            manager.createNotificationChannel(instantServiceChannel);

            NotificationChannel updateChannel = new NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    UPDATE_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            updateChannel.setDescription("Harley's Clan Forum app update availability and install-ready alerts");
            updateChannel.enableVibration(true);
            updateChannel.setShowBadge(false);
            updateChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            manager.createNotificationChannel(updateChannel);

            AppLogger.info(context, "notification_channel", "id=" + CHANNEL_ID + " importance=" + channelImportance(context));
        } catch (Throwable t) {
            AppLogger.error(context, "notification_channel_create", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        }
    }

    static boolean isEnabledByUser(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        return prefs.getBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true);
    }

    static boolean hasRuntimePermission(Context context) {
        return Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean areAppNotificationsEnabled(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager != null && manager.areNotificationsEnabled();
    }

    static int channelImportance(Context context) {
        if (Build.VERSION.SDK_INT < 26) return NotificationManager.IMPORTANCE_HIGH;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return NotificationManager.IMPORTANCE_NONE;
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        return channel == null ? NotificationManager.IMPORTANCE_NONE : channel.getImportance();
    }

    static boolean headsUpChannelReady(Context context) {
        return channelImportance(context) >= NotificationManager.IMPORTANCE_HIGH;
    }

    static boolean canPost(Context context) {
        if (!isEnabledByUser(context)) return false;
        if (!hasRuntimePermission(context)) return false;
        if (!areAppNotificationsEnabled(context)) return false;
        if (Build.VERSION.SDK_INT >= 26 && channelImportance(context) == NotificationManager.IMPORTANCE_NONE) return false;
        return true;
    }

    static String status(Context context) {
        if (!isEnabledByUser(context)) return "OFF in App Settings";
        if (!hasRuntimePermission(context)) return "Permission required";
        if (!areAppNotificationsEnabled(context)) return "Blocked in Android notification settings";
        if (Build.VERSION.SDK_INT >= 26) {
            int importance = channelImportance(context);
            if (importance == NotificationManager.IMPORTANCE_NONE) return "Messages & alerts channel is OFF";
            if (importance < NotificationManager.IMPORTANCE_HIGH) return "Notifications work, but floating/heads-up is not enabled";
        }
        return "Ready • floating/heads-up channel enabled";
    }

    static void openChannelSettings(Context context) {
        try {
            Intent intent;
            if (Build.VERSION.SDK_INT >= 26) {
                intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
                intent.putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID);
            } else {
                intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            fallback.setData(Uri.parse("package:" + context.getPackageName()));
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(fallback);
        }
    }

    static void post(Context context, String title, String body, String url) {
        String safeTitle = trim(title, 120, "Harley's Clan Forum");
        String safeBody = trim(body, 500, "You have a new forum message.");
        Uri destination = validatedForumUri(url);
        int id = (int) (System.currentTimeMillis() & 0x7fffffff);
        postInternal(context, safeTitle, safeBody, destination, id, false);
    }

    private static void postInternal(
            Context context,
            String safeTitle,
            String safeBody,
            Uri destination,
            int notificationId,
            boolean grouped
    ) {
        createChannel(context);
        if (!isEnabledByUser(context)) return;

        // Foreground UI receives the same event immediately even if Android's
        // runtime notification permission has not been granted yet.
        broadcastEvent(context, safeTitle, safeBody, destination.toString(), -1);
        if (!canPost(context)) {
            AppLogger.warn(context, "notification_blocked", status(context));
            return;
        }

        Intent open = new Intent(context, MainActivity.class);
        open.setAction("com.harleytg.forum.dev.OPEN_NOTIFICATION");
        open.setData(destination);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pending = PendingIntent.getActivity(
                context,
                destination.toString().hashCode(),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);

        builder.setSmallIcon(R.drawable.ic_notification_paw)
                .setLargeIcon(largeIcon(context))
                .setContentTitle(safeTitle)
                .setContentText(safeBody)
                .setStyle(new Notification.BigTextStyle().bigText(safeBody))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPriority(Notification.PRIORITY_HIGH)
                .setOnlyAlertOnce(false)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true);
        if (grouped) builder.setGroup(FORUM_GROUP_KEY);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(notificationId, builder.build());
            AppLogger.info(context, "notification_posted", safeTitle + " | headsUp=" + headsUpChannelReady(context));
        }
    }

    static Notification buildInstantServiceNotification(Context context) {
        createChannel(context);
        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                context,
                41070,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, INSTANT_SERVICE_CHANNEL_ID)
                : new Notification.Builder(context);
        return builder
                .setSmallIcon(R.drawable.ic_notification_paw)
                .setContentTitle("Harley's Clan Forum")
                .setContentText("Live alerts active • checking in real time")
                .setContentIntent(pending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPriority(Notification.PRIORITY_MIN)
                .build();
    }

    static synchronized int recordForumNotificationCount(Context context, int newCount, String host, String source) {
        if (context == null) return 0;
        if (!ForumUrlRouter.isForumHost(host)) host = ForumConfig.PRIMARY_HOST;
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        int normalized = Math.max(0, newCount);
        boolean hadBaseline = prefs.contains(AppPrefs.LAST_NOTIFICATION_COUNT);
        int previous = prefs.getInt(AppPrefs.LAST_NOTIFICATION_COUNT, normalized);
        String previousHost = prefs.getString(AppPrefs.ACTIVE_HOST, "");
        boolean countChanged = !hadBaseline || normalized != previous;
        boolean hostChanged = !host.equalsIgnoreCase(previousHost == null ? "" : previousHost);

        if (countChanged || hostChanged) {
            SharedPreferences.Editor edit = prefs.edit();
            if (countChanged) {
                edit.putInt(AppPrefs.LAST_NOTIFICATION_COUNT, normalized);
                edit.putLong(AppPrefs.NOTIFICATION_LAST_COUNT_CHANGE_AT, System.currentTimeMillis());
            }
            if (hostChanged) edit.putString(AppPrefs.ACTIVE_HOST, host);
            edit.apply();
        }

        if (countChanged) {
            broadcastEvent(context, "", "", ForumUrlRouter.home(host), normalized);
            AppLogger.info(context, "notification_count_changed",
                    (source == null ? "sync" : source) + " | count=" + normalized + " previous=" + previous);
        }
        return hadBaseline && normalized > previous ? normalized - previous : 0;
    }

    static synchronized int deliverDetailedAlerts(
            Context context,
            List<ForumNotificationClient.Alert> alerts,
            int expectedDelta,
            String host,
            String source
    ) {
        if (context == null || expectedDelta <= 0) return 0;
        if (!ForumUrlRouter.isForumHost(host)) host = ForumConfig.PRIMARY_HOST;
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        Set<String> delivered = deliveredIds(prefs.getString(AppPrefs.DELIVERED_NOTIFICATION_IDS, ""));
        int sent = 0;
        int max = Math.max(1, expectedDelta);

        if (alerts != null) {
            for (ForumNotificationClient.Alert alert : alerts) {
                if (alert == null || alert.id.isEmpty() || delivered.contains(alert.id)) continue;
                String title = trim(alert.title, 120, "Harley's Clan Forum");
                String body = trim(alert.body, 500, "You have a new forum notification.");
                Uri destination = validatedForumUri(alert.url);
                int notificationId = 0x24000000 | (alert.id.hashCode() & 0x0fffffff);
                postInternal(context, title, body, destination, notificationId, true);
                delivered.add(alert.id);
                sent++;
                if (sent >= max) break;
            }
        }

        trimDeliveredIds(delivered, 100);
        prefs.edit().putString(AppPrefs.DELIVERED_NOTIFICATION_IDS, join(delivered)).apply();
        if (sent == 0) postGenericDelta(context, expectedDelta, host);
        if (sent > 1) postGroupSummary(context, sent, host);
        AppLogger.info(context, "notification_details",
                (source == null ? "sync" : source) + " | delivered=" + sent + " expected=" + expectedDelta);
        return sent;
    }

    static void postGenericDelta(Context context, int delta, String host) {
        if (context == null || delta <= 0) return;
        if (!ForumUrlRouter.isForumHost(host)) host = ForumConfig.PRIMARY_HOST;
        String body = delta == 1
                ? "You have a new forum notification."
                : "You have " + delta + " new forum notifications.";
        postInternal(context,
                "Harley's Clan Forum",
                body,
                Uri.parse(ForumUrlRouter.home(host) + "notifications"),
                FORUM_SUMMARY_ID,
                true);
    }

    static void postUpdateAvailable(Context context, UpdateChecker.Release release) {
        createChannel(context);
        if (!hasRuntimePermission(context) || !areAppNotificationsEnabled(context)) return;
        try {
            Intent open = new Intent(context, SettingsActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pending = PendingIntent.getActivity(
                    context,
                    51001,
                    open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, UPDATE_CHANNEL_ID)
                    : new Notification.Builder(context);
            builder.setSmallIcon(R.drawable.ic_notification_paw)
                    .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.htg_app_logo))
                    .setContentTitle("Beta update available")
                    .setContentText("v" + UpdateChecker.displayVersion(release) + (release != null && release.versionCode > 0 ? " • build " + release.versionCode : "") + " is ready for Development / Beta.")
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_SYSTEM)
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setPriority(Notification.PRIORITY_DEFAULT);
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(51001, builder.build());
        } catch (Throwable t) {
            AppLogger.error(context, "update_notification", t.getClass().getSimpleName());
        }
    }

    static void postUpdateReady(Context context, String tag, long downloadId) {
        createChannel(context);
        if (!hasRuntimePermission(context) || !areAppNotificationsEnabled(context)) return;
        try {
            Intent install = new Intent(context, SettingsActivity.class);
            install.setAction("com.harleytg.forum.dev.INSTALL_UPDATE");
            install.putExtra("download_id", downloadId);
            install.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pending = PendingIntent.getActivity(
                    context,
                    51002,
                    install,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, UPDATE_CHANNEL_ID)
                    : new Notification.Builder(context);
            builder.setSmallIcon(R.drawable.ic_notification_paw)
                    .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.htg_app_logo))
                    .setContentTitle("Update downloaded")
                    .setContentText(trim(tag, 80, "Update") + " is ready to install.")
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_SYSTEM)
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setPriority(Notification.PRIORITY_HIGH);
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(51002, builder.build());
        } catch (Throwable t) {
            AppLogger.error(context, "update_ready_notification", t.getClass().getSimpleName());
        }
    }

    private static void postGroupSummary(Context context, int count, String host) {
        if (!canPost(context) || count <= 1) return;
        try {
            Uri destination = Uri.parse(ForumUrlRouter.home(host) + "notifications");
            Intent open = new Intent(context, MainActivity.class);
            open.setAction("com.harleytg.forum.dev.OPEN_NOTIFICATION");
            open.setData(destination);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pending = PendingIntent.getActivity(
                    context,
                    FORUM_SUMMARY_ID,
                    open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, CHANNEL_ID)
                    : new Notification.Builder(context);
            builder.setSmallIcon(R.drawable.ic_notification_paw)
                    .setLargeIcon(largeIcon(context))
                    .setContentTitle("Harley's Clan Forum")
                    .setContentText(count + " new forum alerts")
                    .setContentIntent(pending)
                    .setGroup(FORUM_GROUP_KEY)
                    .setGroupSummary(true)
                    .setNumber(count)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setPriority(Notification.PRIORITY_HIGH);
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(FORUM_SUMMARY_ID, builder.build());
        } catch (Throwable t) {
            AppLogger.warn(context, "notification_summary", t.getClass().getSimpleName());
        }
    }

    private static void broadcastEvent(Context context, String title, String body, String url, int count) {
        try {
            Intent event = new Intent(ACTION_NOTIFICATION_EVENT);
            event.setPackage(context.getPackageName());
            event.putExtra(EXTRA_EVENT_TITLE, title == null ? "" : title);
            event.putExtra(EXTRA_EVENT_BODY, body == null ? "" : body);
            event.putExtra(EXTRA_EVENT_URL, url == null ? "" : url);
            event.putExtra(EXTRA_EVENT_COUNT, count);
            context.sendBroadcast(event);
        } catch (Throwable ignored) {}
    }

    private static Bitmap largeIcon(Context context) {
        Bitmap value = cachedLargeIcon;
        if (value != null && !value.isRecycled()) return value;
        synchronized (NotificationHelper.class) {
            value = cachedLargeIcon;
            if (value == null || value.isRecycled()) {
                value = BitmapFactory.decodeResource(context.getResources(), R.drawable.htg_app_logo);
                cachedLargeIcon = value;
            }
            return value;
        }
    }

    private static Set<String> deliveredIds(String raw) {
        Set<String> values = new LinkedHashSet<>();
        if (raw == null || raw.isEmpty()) return values;
        String[] parts = raw.split("\\n");
        for (String part : parts) {
            String id = part == null ? "" : part.trim();
            if (!id.isEmpty()) values.add(id);
        }
        return values;
    }

    private static void trimDeliveredIds(Set<String> values, int max) {
        while (values.size() > max) {
            Iterator<String> iterator = values.iterator();
            if (!iterator.hasNext()) return;
            iterator.next();
            iterator.remove();
        }
    }

    private static String join(Set<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append('\n');
            out.append(value);
        }
        return out.toString();
    }

    private static Uri validatedForumUri(String url) {
        try {
            Uri uri = Uri.parse(url == null ? "" : url);
            if (ForumUrlRouter.isForumUrl(uri)) return uri;
        } catch (Throwable ignored) {}
        return Uri.parse(ForumUrlRouter.home(ForumConfig.PRIMARY_HOST));
    }

    private static String trim(String value, int max, String fallback) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) v = fallback;
        if (v.length() > max) v = v.substring(0, max) + "…";
        return v;
    }

    private NotificationHelper() {}
}
