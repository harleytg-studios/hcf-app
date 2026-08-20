package com.harleytg.forum;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import com.harleytg.forum.ForumNotificationClient;
import com.harleytg.forum.UpdateChecker;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/* loaded from: classes.dex */
final class NotificationHelper {
    static final String ACTION_NOTIFICATION_EVENT = "com.harleytg.forum.NOTIFICATION_EVENT";
    static final String CHANNEL_GROUP_ID = "hcf_notifications_v1";
    static final String CHANNEL_GROUP_NAME = "Harley's Clan Forum";
    static final String CHANNEL_ID = "hcf_alerts_v1";
    static final String CHANNEL_NAME = "HCF Alerts";
    static final String EXTRA_EVENT_BODY = "event_body";
    static final String EXTRA_EVENT_COUNT = "event_count";
    static final String EXTRA_EVENT_TITLE = "event_title";
    static final String EXTRA_EVENT_URL = "event_url";
    private static final String FORUM_GROUP_KEY = "hcf_forum_alerts";
    private static final int FORUM_SUMMARY_ID = 41072;
    private static final String[] LEGACY_CHANNEL_IDS = {"forum_messages_heads_up_v2", "forum_messages", "app_updates_v1", "hcf_background_v2", "instant_notification_service_v1", "hcf_passive_silent_v1", "hcf_messages", "hcf_forum_activity"};
    static final String SILENT_CHANNEL_ID = "hcf_silent_alerts_v1";
    static final String SILENT_CHANNEL_NAME = "HCF Silent Alerts";
    static final String TEST_CHANNEL_ID = "hcf_test_alerts_v1";
    static final String TEST_CHANNEL_NAME = "HCF Test Alerts";
    private static volatile Bitmap cachedLargeIcon;

    static boolean isEnabledByUser(Context context) {
        return true;
    }

    static void createChannel(Context context) {
        if (context != null) {
            try {
                NotificationManager notificationManager = (NotificationManager) context.getSystemService("notification");
                if (notificationManager == null) {
                    return;
                }
                try {
                    notificationManager.createNotificationChannelGroup(new NotificationChannelGroup(CHANNEL_GROUP_ID, CHANNEL_GROUP_NAME));
                } catch (Throwable unused) {
                }
                NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, 4);
                notificationChannel.setDescription("Audible HCF messages, mentions, replies, forum activity and important app alerts. App silence controls never affect this channel.");
                notificationChannel.setGroup(CHANNEL_GROUP_ID);
                notificationChannel.enableVibration(true);
                notificationChannel.enableLights(true);
                notificationChannel.setShowBadge(true);
                notificationChannel.setLockscreenVisibility(0);
                try {
                    notificationChannel.setSound(RingtoneManager.getDefaultUri(2), new AudioAttributes.Builder().setUsage(10).setContentType(4).build());
                } catch (Throwable unused2) {
                }
                notificationManager.createNotificationChannel(notificationChannel);
                NotificationChannel notificationChannel2 = new NotificationChannel(SILENT_CHANNEL_ID, SILENT_CHANNEL_NAME, 2);
                notificationChannel2.setDescription("Always-silent HCF background sync, service status, reconnect notices and passive alerts");
                notificationChannel2.setGroup(CHANNEL_GROUP_ID);
                notificationChannel2.setSound(null, null);
                notificationChannel2.enableVibration(false);
                notificationChannel2.enableLights(false);
                notificationChannel2.setShowBadge(false);
                notificationChannel2.setLockscreenVisibility(0);
                notificationManager.createNotificationChannel(notificationChannel2);
                if (BuildInfo.ENABLE_DEV_TEST_MENU) {
                    NotificationChannel notificationChannel3 = new NotificationChannel(TEST_CHANNEL_ID, TEST_CHANNEL_NAME, 3);
                    notificationChannel3.setDescription("Development and Beta notification tests only");
                    notificationChannel3.setGroup(CHANNEL_GROUP_ID);
                    notificationChannel3.enableVibration(true);
                    notificationChannel3.setShowBadge(false);
                    notificationChannel3.setLockscreenVisibility(0);
                    notificationManager.createNotificationChannel(notificationChannel3);
                } else {
                    deleteChannelIfPresent(notificationManager, TEST_CHANNEL_ID);
                }
                for (String str : LEGACY_CHANNEL_IDS) {
                    deleteChannelIfPresent(notificationManager, str);
                }
                AppLogger.info(context, "notification_channel", BuildInfo.ENABLE_DEV_TEST_MENU ? "channels=HCF Alerts|HCF Silent Alerts|HCF Test Alerts" : "channels=HCF Alerts|HCF Silent Alerts");
            } catch (Throwable th) {
                AppLogger.error(context, "notification_channel_create", th.getClass().getSimpleName() + ": " + String.valueOf(th.getMessage()));
            }
        }
    }

    private static void deleteChannelIfPresent(NotificationManager notificationManager, String str) {
        if (notificationManager == null || str == null || str.isEmpty()) {
            return;
        }
        try {
            if (notificationManager.getNotificationChannel(str) != null) {
                notificationManager.deleteNotificationChannel(str);
            }
        } catch (Throwable unused) {
        }
    }

    static void refreshChannels(Context context) {
        createChannel(context);
    }

    static boolean postNotificationServiceTest(Context context) {
        if (!BuildInfo.ENABLE_DEV_TEST_MENU) return false;
        if (context == null) {
            return false;
        }
        createChannel(context);
        if (!canPostOnChannel(context, TEST_CHANNEL_ID)) {
            return false;
        }
        try {
            PendingIntent activity = PendingIntent.getActivity(context, 41079, new Intent(context, (Class<?>) MainActivity.class).addFlags(603979776), 201326592);
            Notification.Builder builder = new Notification.Builder(context, TEST_CHANNEL_ID);
            builder.setSmallIcon(R.drawable.ic_notification_paw).setLargeIcon(largeIcon(context)).setContentTitle(TEST_CHANNEL_NAME).setContentText("Notification service test • channel ready").setContentIntent(activity).setAutoCancel(true).setOnlyAlertOnce(true).setCategory("service").setVisibility(0).setPriority(-1).setShowWhen(true);
            NotificationManager notificationManager = (NotificationManager) context.getSystemService("notification");
            if (notificationManager == null) {
                return false;
            }
            notificationManager.notify(41079, builder.build());
            AppLogger.info(context, "notification_service_test", "HCF Test Alerts test posted");
            return true;
        } catch (Throwable th) {
            AppLogger.warn(context, "notification_service_test", th.getClass().getSimpleName());
            return false;
        }
    }

    static boolean silencePassiveEnabled(Context context) {
        if (context == null) {
            return false;
        }
        return context.getSharedPreferences("hcf_app", 0).getBoolean("silence_background_service_notification", false);
    }

    static boolean hasRuntimePermission(Context context) {
        return Build.VERSION.SDK_INT < 33 || context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0;
    }

    static boolean areAppNotificationsEnabled(Context context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService("notification");
        return notificationManager != null && notificationManager.areNotificationsEnabled();
    }

    static int channelImportance(Context context) {
        return channelImportance(context, CHANNEL_ID);
    }

    static int channelImportance(Context context, String str) {
        NotificationChannel notificationChannel;
        NotificationManager notificationManager = (NotificationManager) context.getSystemService("notification");
        if (notificationManager == null || (notificationChannel = notificationManager.getNotificationChannel(str)) == null) {
            return 0;
        }
        return notificationChannel.getImportance();
    }

    static String channelStatus(Context context, String str) {
        int channelImportance = channelImportance(context, str);
        if (channelImportance == 0) {
            return "Off";
        }
        if (SILENT_CHANNEL_ID.equals(str)) {
            return "On • Silent";
        }
        if (channelImportance >= 4) {
            return "On • High priority";
        }
        if (channelImportance >= 3) {
            return "On • Normal";
        }
        return "On • Low priority";
    }

    static boolean headsUpChannelReady(Context context) {
        return channelImportance(context) >= 4;
    }

    static boolean canPost(Context context) {
        return canPostOnChannel(context, CHANNEL_ID);
    }

    static boolean canPostOnChannel(Context context, String str) {
        return isEnabledByUser(context) && hasRuntimePermission(context) && areAppNotificationsEnabled(context) && channelImportance(context, str) != 0;
    }

    static String status(Context context) {
        if (!hasRuntimePermission(context)) {
            return "Permission required";
        }
        if (!areAppNotificationsEnabled(context)) {
            return "Blocked in Android notification settings";
        }
        int channelImportance = channelImportance(context);
        if (channelImportance == 0) {
            return "HCF Alerts is OFF";
        }
        if (channelImportance < 4) {
            return "Notifications work, but floating/heads-up is not enabled";
        }
        return "Ready • floating/heads-up channel enabled";
    }

    static void openAppNotificationSettings(Context context) {
        try {
            Intent intent = new Intent("android.settings.APP_NOTIFICATION_SETTINGS");
            intent.putExtra("android.provider.extra.APP_PACKAGE", context.getPackageName());
            intent.addFlags(268435456);
            context.startActivity(intent);
        } catch (Throwable unused) {
            Intent intent2 = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
            intent2.setData(Uri.parse("package:" + context.getPackageName()));
            intent2.addFlags(268435456);
            context.startActivity(intent2);
        }
    }

    static void openChannelSettings(Context context) {
        openChannelSettings(context, CHANNEL_ID);
    }

    static void openChannelSettings(Context context, String str) {
        try {
            Intent intent = new Intent("android.settings.CHANNEL_NOTIFICATION_SETTINGS");
            intent.putExtra("android.provider.extra.APP_PACKAGE", context.getPackageName());
            intent.putExtra("android.provider.extra.CHANNEL_ID", str);
            intent.addFlags(268435456);
            context.startActivity(intent);
        } catch (Throwable unused) {
            Intent intent2 = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
            intent2.setData(Uri.parse("package:" + context.getPackageName()));
            intent2.addFlags(268435456);
            context.startActivity(intent2);
        }
    }

    static void post(Context context, String str, String str2, String str3) {
        postInternal(context, trim(str, 120, "Harley's Clan Forum"), trim(str2, 500, "You have a new forum message."), validatedForumUri(str3), (int) (System.currentTimeMillis() & 2147483647L), false, false);
    }

    static void postTest(Context context, String str, String str2, String str3) {
        if (!BuildInfo.ENABLE_DEV_TEST_MENU) return;
        postInternal(context, trim(str, 120, "HCF test alert"), trim(str2, 500, "HCF notification test"), validatedForumUri(str3), (int) (System.currentTimeMillis() & 2147483647L), false, false, TEST_CHANNEL_ID);
    }

    private static void postInternal(Context context, String str, String str2, Uri uri, int i, boolean z) {
        postInternal(context, str, str2, uri, i, z, false, null);
    }

    private static void postInternal(Context context, String str, String str2, Uri uri, int i, boolean z, boolean z2) {
        postInternal(context, str, str2, uri, i, z, z2, null);
    }

    private static void postInternal(Context context, String str, String str2, Uri uri, int i, boolean z, boolean z2, String str3) {
        createChannel(context);
        if (isEnabledByUser(context)) {
            broadcastEvent(context, str, str2, uri.toString(), -1);
            if (str3 == null) {
                str3 = z2 ? SILENT_CHANNEL_ID : CHANNEL_ID;
            }
            if (!canPostOnChannel(context, str3)) {
                AppLogger.warn(context, "notification_blocked", status(context) + " | channel=" + str3);
                return;
            }
            Intent intent = new Intent(context, (Class<?>) MainActivity.class);
            intent.setAction("com.harleytg.forum.OPEN_NOTIFICATION");
            intent.setData(uri);
            intent.addFlags(603979776);
            PendingIntent activity = PendingIntent.getActivity(context, uri.toString().hashCode(), intent, 201326592);
            Notification.Builder builder = new Notification.Builder(context, str3);
            builder.setSmallIcon(R.drawable.ic_notification_paw).setLargeIcon(largeIcon(context)).setContentTitle(str).setContentText(str2).setStyle(new Notification.BigTextStyle().bigText(str2)).setContentIntent(activity).setAutoCancel(true).setCategory(z2 ? "status" : "msg").setVisibility(0).setPriority(z2 ? -1 : 1).setOnlyAlertOnce(z2).setWhen(System.currentTimeMillis()).setShowWhen(true);
            if (z) {
                builder.setGroup(FORUM_GROUP_KEY);
            }
            NotificationManager notificationManager = (NotificationManager) context.getSystemService("notification");
            if (notificationManager != null) {
                notificationManager.notify(i, builder.build());
                AppLogger.info(context, "notification_posted", str + " | headsUp=" + headsUpChannelReady(context));
            }
        }
    }

    static Notification buildInstantServiceNotification(Context context) {
        Intent intent = new Intent(context, (Class<?>) MainActivity.class);
        intent.addFlags(603979776);
        return new Notification.Builder(context, SILENT_CHANNEL_ID).setSmallIcon(R.drawable.ic_notification_paw).setContentTitle("Harley's Clan Forum").setContentText("Live alerts active • checking in real time").setContentIntent(PendingIntent.getActivity(context, 41070, intent, 201326592)).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setCategory("service").setVisibility(0).setPriority(-2).build();
    }

    /* JADX WARN: Removed duplicated region for block: B:18:0x0039  */
    /* JADX WARN: Removed duplicated region for block: B:23:0x0064 A[Catch: all -> 0x009c, TryCatch #0 {, blocks: (B:9:0x0008, B:12:0x0010, B:19:0x003b, B:23:0x0064, B:26:0x007a, B:33:0x0044, B:35:0x004a, B:37:0x005a, B:38:0x005f), top: B:8:0x0008 }] */
    /* JADX WARN: Removed duplicated region for block: B:35:0x004a A[Catch: all -> 0x009c, TryCatch #0 {, blocks: (B:9:0x0008, B:12:0x0010, B:19:0x003b, B:23:0x0064, B:26:0x007a, B:33:0x0044, B:35:0x004a, B:37:0x005a, B:38:0x005f), top: B:8:0x0008 }] */
    /* JADX WARN: Removed duplicated region for block: B:37:0x005a A[Catch: all -> 0x009c, TryCatch #0 {, blocks: (B:9:0x0008, B:12:0x0010, B:19:0x003b, B:23:0x0064, B:26:0x007a, B:33:0x0044, B:35:0x004a, B:37:0x005a, B:38:0x005f), top: B:8:0x0008 }] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */

    static synchronized int recordForumNotificationCount(Context context, int newCount, String host, String source) {
        if (context == null) return 0;
        if (!ForumUrlRouter.isForumHost(host)) host = ForumConfig.PRIMARY_HOST;
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        int normalized = Math.max(0, newCount);
        boolean hadBaseline = prefs.contains(AppPrefs.LAST_NOTIFICATION_COUNT);
        int previous = prefs.getInt(AppPrefs.LAST_NOTIFICATION_COUNT, normalized);
        prefs.edit()
                .putInt(AppPrefs.LAST_NOTIFICATION_COUNT, normalized)
                .putString(AppPrefs.ACTIVE_HOST, host)
                .apply();

        if (!hadBaseline || normalized != previous) {
            broadcastEvent(context, "", "", ForumUrlRouter.home(host), normalized);
        }
        AppLogger.info(context, "notification_count",
                (source == null ? "sync" : source) + " | count=" + normalized + " previous=" + previous);
        return hadBaseline && normalized > previous ? normalized - previous : 0;
    }

    static synchronized int deliverDetailedAlerts(Context context, List<ForumNotificationClient.Alert> list, int i, String str, String str2) {
        synchronized (NotificationHelper.class) {
            int i2 = 0;
            if (context == null || i <= 0) {
                return 0;
            }
            String str3 = !ForumUrlRouter.isForumHost(str) ? "forum.harleytg.com" : str;
            SharedPreferences sharedPreferences = context.getSharedPreferences("hcf_app", 0);
            Set<String> deliveredIds = deliveredIds(sharedPreferences.getString("delivered_notification_ids", ""));
            int max = Math.max(1, i);
            if (list != null) {
                int i3 = 0;
                for (ForumNotificationClient.Alert alert : list) {
                    if (alert != null && !alert.id.isEmpty() && !deliveredIds.contains(alert.id)) {
                        postInternal(context, trim(alert.title, 120, "Harley's Clan Forum"), trim(alert.body, 500, "You have a new forum notification."), validatedForumUri(alert.url), 603979776 | (alert.id.hashCode() & 268435455), true);
                        deliveredIds.add(alert.id);
                        i3++;
                        if (i3 >= max) {
                            break;
                        }
                    }
                }
                i2 = i3;
            }
            trimDeliveredIds(deliveredIds, 100);
            sharedPreferences.edit().putString("delivered_notification_ids", join(deliveredIds)).apply();
            if (i2 == 0) {
                postGenericDelta(context, i, str3);
            }
            if (i2 > 1) {
                postGroupSummary(context, i2, str3);
            }
            StringBuilder sb = new StringBuilder();
            sb.append(str2 == null ? "sync" : str2);
            sb.append(" | delivered=");
            sb.append(i2);
            sb.append(" expected=");
            sb.append(i);
            AppLogger.info(context, "notification_details", sb.toString());
            return i2;
        }
    }

    static void postGenericDelta(Context context, int i, String str) {
        String str2;
        if (context == null || i <= 0) {
            return;
        }
        if (!ForumUrlRouter.isForumHost(str)) {
            str = "forum.harleytg.com";
        }
        if (i == 1) {
            str2 = "You have a new forum notification.";
        } else {
            str2 = "You have " + i + " new forum notifications.";
        }
        String str3 = str2;
        if (silencePassiveEnabled(context)) {
            AppLogger.info(context, "silent_alert_suppressed", "generic notification summary");
            return;
        }
        postInternal(context, "Harley's Clan Forum", str3, Uri.parse(ForumUrlRouter.home(str) + "notifications"), FORUM_SUMMARY_ID, true, true);
    }

    static void postUpdateAvailable(Context context, UpdateChecker.Release release) {
        String str;
        createChannel(context);
        if (hasRuntimePermission(context) && areAppNotificationsEnabled(context)) {
            try {
                Intent intent = new Intent(context, (Class<?>) SettingsActivity.class);
                intent.addFlags(335544320);
                PendingIntent activity = PendingIntent.getActivity(context, 51001, intent, 201326592);
                Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID);
                Notification.Builder contentTitle = builder.setSmallIcon(R.drawable.ic_notification_paw).setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.htg_app_logo)).setContentTitle("Stable update available");
                StringBuilder sb = new StringBuilder("v");
                sb.append(UpdateChecker.displayVersion(release));
                if (release == null || release.versionCode <= 0) {
                    str = "";
                } else {
                    str = " • build " + release.versionCode;
                }
                sb.append(str);
                sb.append(" is ready for Stable.");
                contentTitle.setContentText(sb.toString()).setContentIntent(activity).setAutoCancel(true).setCategory("sys").setVisibility(0).setPriority(0);
                NotificationManager notificationManager = (NotificationManager) context.getSystemService("notification");
                if (notificationManager != null) {
                    notificationManager.notify(51001, builder.build());
                }
            } catch (Throwable th) {
                AppLogger.error(context, "update_notification", th.getClass().getSimpleName());
            }
        }
    }

    static void postUpdateReady(Context context, String str, long j) {
        createChannel(context);
        if (hasRuntimePermission(context) && areAppNotificationsEnabled(context)) {
            try {
                Intent intent = new Intent(context, (Class<?>) SettingsActivity.class);
                intent.setAction("com.harleytg.forum.INSTALL_UPDATE");
                intent.putExtra("download_id", j);
                intent.addFlags(335544320);
                PendingIntent activity = PendingIntent.getActivity(context, 51002, intent, 201326592);
                Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID);
                builder.setSmallIcon(R.drawable.ic_notification_paw).setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.htg_app_logo)).setContentTitle("Update downloaded").setContentText(trim(str, 80, "Update") + " is ready to install.").setContentIntent(activity).setAutoCancel(true).setCategory("sys").setVisibility(0).setPriority(1);
                NotificationManager notificationManager = (NotificationManager) context.getSystemService("notification");
                if (notificationManager != null) {
                    notificationManager.notify(51002, builder.build());
                }
            } catch (Throwable th) {
                AppLogger.error(context, "update_ready_notification", th.getClass().getSimpleName());
            }
        }
    }

    private static void postGroupSummary(Context context, int i, String str) {
        if (i <= 1) {
            return;
        }
        try {
            Uri parse = Uri.parse(ForumUrlRouter.home(str) + "notifications");
            Intent intent = new Intent(context, (Class<?>) MainActivity.class);
            intent.setAction("com.harleytg.forum.OPEN_NOTIFICATION");
            intent.setData(parse);
            intent.addFlags(603979776);
            PendingIntent activity = PendingIntent.getActivity(context, FORUM_SUMMARY_ID, intent, 201326592);
            if (silencePassiveEnabled(context)) {
                AppLogger.info(context, "silent_alert_suppressed", "group notification summary");
                return;
            }
            if (canPostOnChannel(context, SILENT_CHANNEL_ID)) {
                Notification.Builder builder = new Notification.Builder(context, SILENT_CHANNEL_ID);
                builder.setSmallIcon(R.drawable.ic_notification_paw).setLargeIcon(largeIcon(context)).setContentTitle("Harley's Clan Forum").setContentText(i + " new forum alerts").setContentIntent(activity).setGroup(FORUM_GROUP_KEY).setGroupSummary(true).setNumber(i).setAutoCancel(true).setCategory("status").setVisibility(0).setPriority(-1);
                NotificationManager notificationManager = (NotificationManager) context.getSystemService("notification");
                if (notificationManager != null) {
                    notificationManager.notify(FORUM_SUMMARY_ID, builder.build());
                }
            }
        } catch (Throwable th) {
            AppLogger.warn(context, "notification_summary", th.getClass().getSimpleName());
        }
    }

    private static void broadcastEvent(Context context, String str, String str2, String str3, int i) {
        try {
            Intent intent = new Intent(ACTION_NOTIFICATION_EVENT);
            intent.setPackage(context.getPackageName());
            if (str == null) {
                str = "";
            }
            intent.putExtra(EXTRA_EVENT_TITLE, str);
            if (str2 == null) {
                str2 = "";
            }
            intent.putExtra(EXTRA_EVENT_BODY, str2);
            if (str3 == null) {
                str3 = "";
            }
            intent.putExtra(EXTRA_EVENT_URL, str3);
            intent.putExtra(EXTRA_EVENT_COUNT, i);
            context.sendBroadcast(intent);
        } catch (Throwable unused) {
        }
    }

    private static Bitmap largeIcon(Context context) {
        Bitmap bitmap;
        Bitmap bitmap2 = cachedLargeIcon;
        if (bitmap2 != null && !bitmap2.isRecycled()) {
            return bitmap2;
        }
        synchronized (NotificationHelper.class) {
            bitmap = cachedLargeIcon;
            if (bitmap == null || bitmap.isRecycled()) {
                bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.htg_app_logo);
                cachedLargeIcon = bitmap;
            }
        }
        return bitmap;
    }

    private static Set<String> deliveredIds(String str) {
        LinkedHashSet linkedHashSet = new LinkedHashSet();
        if (str != null && !str.isEmpty()) {
            String[] split = str.split("\\n");
            int length = split.length;
            for (int i = 0; i < length; i++) {
                String str2 = split[i];
                String trim = str2 == null ? "" : str2.trim();
                if (!trim.isEmpty()) {
                    linkedHashSet.add(trim);
                }
            }
        }
        return linkedHashSet;
    }

    private static void trimDeliveredIds(Set<String> set, int i) {
        while (set.size() > i) {
            Iterator<String> it = set.iterator();
            if (!it.hasNext()) {
                return;
            }
            it.next();
            it.remove();
        }
    }

    private static String join(Set<String> set) {
        StringBuilder sb = new StringBuilder();
        for (String str : set) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(str);
        }
        return sb.toString();
    }

    private static Uri validatedForumUri(String str) {
        if (str == null) {
            str = "";
        }
        try {
            Uri parse = Uri.parse(str);
            if (ForumUrlRouter.isForumUrl(parse)) {
                return parse;
            }
        } catch (Throwable unused) {
        }
        return Uri.parse(ForumUrlRouter.home("forum.harleytg.com"));
    }

    private static String trim(String str, int i, String str2) {
        String trim = str == null ? "" : str.trim();
        if (!trim.isEmpty()) {
            str2 = trim;
        }
        if (str2.length() <= i) {
            return str2;
        }
        return str2.substring(0, i) + "…";
    }

    private NotificationHelper() {
    }
}
