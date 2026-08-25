package com.harleytg.forum.dev;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONException;
import org.json.JSONObject;
import android.app.RemoteInput;
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

public final class HcfNotificationEngine {
    public static final class NotificationActionReceiver extends HcfNotificationActions.ActionReceiver {
        public NotificationActionReceiver() { super(); }
    }

    private HcfNotificationEngine() {}

    // ---- InstantNotificationService.java ----
    public static final class InstantNotificationService extends Service {
        static final String ACTION_SYNC_NOW = "com.harleytg.forum.dev.SYNC_NOTIFICATIONS_NOW";
        private static final long DEFAULT_NEXT_DELAY_MS = 2000L;
        private static final long FAILURE_MAX_MS = 8000L;
        private static final long FAILURE_MIN_MS = 1000L;
        private static final long THROTTLE_MIN_MS = 5000L;
        private static final long THROTTLE_MAX_MS = 120000L;
        static final int SERVICE_NOTIFICATION_ID = 41070;

        private int failures;
        private volatile boolean immediateRequested;
        private ConnectivityManager.NetworkCallback networkCallback;
        private boolean networkCallbackRegistered;
        private BroadcastReceiver screenOnReceiver;
        private boolean screenOnReceiverRegistered;
        private volatile boolean running;
        private ScheduledFuture<?> scheduled;
        private final Object scheduleLock = new Object();
        private final AtomicBoolean inFlight = new AtomicBoolean(false);

        @Override
        public IBinder onBind(Intent intent) { return null; }

        private static boolean hasSession(Context context) {
            if (context == null) return false;
            try {
                String userId = context.getSharedPreferences("hcf_app", 0).getString("session_user_id", "");
                return userId != null && !userId.trim().isEmpty();
            } catch (Throwable ignored) {
                return false;
            }
        }

        static void apply(Context context) {
            if (context == null) return;
            SharedPreferences prefs = context.getSharedPreferences("hcf_app", 0);
            if (prefs.getBoolean("background_notification_sync", true) && hasSession(context)) start(context);
            else stop(context);
        }

        static void start(Context context) {
            if (context == null) return;
            if (!hasSession(context)) {
                stop(context, "no-session");
                return;
            }
            startWithAction(context, null);
        }

        static void requestImmediateSync(Context context) {
            if (context == null || !hasSession(context)) return;
            startWithAction(context, ACTION_SYNC_NOW);
        }

        private static void requestOneShotSync(Context context) {
            final Context app = context.getApplicationContext();
            AppExecutors.network().execute(new Runnable() {
                @Override public void run() { runOneShot(app); }
            });
        }

        private static void runOneShot(Context context) {
            try {
                SharedPreferences prefs = context.getSharedPreferences("hcf_app", 0);
                if (!prefs.getBoolean("background_notification_sync", true)) return;
                String userId = prefs.getString("session_user_id", "");
                if (userId == null || userId.trim().isEmpty() || !RuntimeState.networkAvailable(context)) return;
                String host = prefs.getString("active_host", "forum.harleytg.com");
                if (!ForumUrlRouter.isForumHost(host)) host = "forum.harleytg.com";
                ForumNotificationSync.perform(context, host, userId.trim(), "silent-one-shot");
                AppLogger.info(context, "instant_notification_service", "one-shot sync • silent channel hidden");
            } catch (ForumNotificationClient.HttpStatusException e) {
                if (e.statusCode == 401) {
                    clearSessionForAuthFailure(context, "one-shot");
                } else {
                    AppLogger.warn(context, "instant_notification_service", "one-shot HTTP " + e.statusCode + " • retryAfter=" + e.retryAfterMs + "ms");
                }
            } catch (JSONException e) {
                // A transient 2xx response can occasionally contain HTML or an
                // unexpected payload instead of the Flarum JSON API object. Skip
                // this one-shot attempt and let the next sync retry normally.
                AppLogger.info(context, "instant_notification_service", "one-shot skipped • invalid notification API payload");
            } catch (Throwable t) {
                AppLogger.warn(context, "instant_notification_service", "one-shot | " + t.getClass().getSimpleName());
            }
        }

        private static void startWithAction(Context context, String action) {
            if (context == null) return;
            SharedPreferences prefs = context.getSharedPreferences("hcf_app", 0);
            if (!prefs.getBoolean("background_notification_sync", true) || !hasSession(context)) {
                stop(context);
                return;
            }
            try {
                Intent intent = new Intent(context, InstantNotificationService.class);
                if (action != null) intent.setAction(action);
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
                else context.startService(intent);
            } catch (Throwable t) {
                AppLogger.warn(context, "instant_notification_service", "start-blocked | " + t.getClass().getSimpleName());
            }
        }

        static void stop(Context context) {
            stop(context, "requested");
        }

        static void stop(Context context, String reason) {
            if (context == null) return;
            try {
                context.getSharedPreferences("hcf_app", 0).edit()
                        .putString("notification_service_stop_reason", reason == null ? "requested" : reason)
                        .apply();
                context.stopService(new Intent(context, InstantNotificationService.class));
            } catch (Throwable t) {
                AppLogger.warn(context, "instant_notification_service", "stop | " + t.getClass().getSimpleName());
            }
        }

        static void clearSessionForAuthFailure(Context context, String source) {
            if (context == null) return;
            try {
                SharedPreferences prefs = context.getSharedPreferences("hcf_app", 0);
                String host = prefs.getString("active_host", ForumConfig.PRIMARY_HOST);
                ForumIdentity.save(context, ForumIdentity.guest(host));
                ForumSecurity.clear(context);
                prefs.edit()
                        .remove("session_user_id")
                        .remove("last_notification_count")
                        .remove("delivered_notification_ids")
                        .putString("notification_last_sync_status", "Signed out • forum session expired")
                        .putString("notification_service_stop_reason", "auth-failure-" + (source == null ? "unknown" : source))
                        .apply();
                NotificationSyncScheduler.cancel(context);
                AppLogger.warn(context, "notification_auth", "HTTP 401 • cleared stale forum session | " + String.valueOf(source));
            } catch (Throwable error) {
                AppLogger.error(context, "notification_auth_clear", error.getClass().getSimpleName());
            }
        }

        @Override
        public void onCreate() {
            super.onCreate();
            try {
                NotificationHelper.createChannel(this);
                Notification notification = NotificationHelper.buildInstantServiceNotification(this);
                // The two-argument API uses the manifest-declared specialUse type and
                // avoids OEM-specific failures caused by redundantly forcing the type.
                startForeground(SERVICE_NOTIFICATION_ID, notification);
            } catch (Throwable t) {
                AppLogger.error(this, "instant_notification_foreground", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
                stopSelf();
                return;
            }

            running = true;
            failures = 0;
            registerNetworkCallback();
            registerScreenOnReceiver();
            AppLogger.info(this, "instant_notification_service", "started • adaptive v" + BuildInfo.VERSION_CODE);
            scheduleNext(0L);
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            SharedPreferences prefs = getSharedPreferences("hcf_app", 0);
            if (!prefs.getBoolean("background_notification_sync", true) || !hasSession(this)) {
                running = false;
                stopSelf();
                return START_NOT_STICKY;
            }
            running = true;
            if (intent != null && ACTION_SYNC_NOW.equals(intent.getAction())) {
                immediateRequested = true;
                scheduleNext(0L);
            } else if (scheduled == null) {
                scheduleNext(0L);
            }
            return START_STICKY;
        }

        public void scheduleNext(long delayMs) {
            if (!running) return;
            synchronized (scheduleLock) {
                if (scheduled != null) scheduled.cancel(false);
                long delay = Math.max(0L, delayMs);
                RuntimeDiagnostics.notificationPoll(delay, "Adaptive polling fallback");
                scheduled = AppExecutors.scheduler().schedule(new Runnable() {
                    @Override public void run() { triggerSync(); }
                }, delay, TimeUnit.MILLISECONDS);
            }
        }

        public void triggerSync() {
            if (!running) return;
            if (!inFlight.compareAndSet(false, true)) {
                immediateRequested = true;
                return;
            }
            AppExecutors.network().execute(new Runnable() {
                @Override public void run() { performAdaptiveSync(); }
            });
        }

        private void performAdaptiveSync() {
            long nextDelay = DEFAULT_NEXT_DELAY_MS;
            try {
                SharedPreferences prefs = getSharedPreferences("hcf_app", 0);
                if (!prefs.getBoolean("background_notification_sync", true)) {
                    running = false;
                    stop(this, "background-sync-disabled");
                    stopSelf();
                    return;
                }
                String userId = prefs.getString("session_user_id", "");
                if (userId == null || userId.trim().isEmpty()) {
                    running = false;
                    stop(this, "signed-out");
                    stopSelf();
                    return;
                }
                if (!RuntimeState.networkAvailable(this)) {
                    nextDelay = Math.max(DEFAULT_NEXT_DELAY_MS, PerformanceProfile.notificationPollInterval(this, prefs));
                } else {
                    String host = prefs.getString("active_host", "forum.harleytg.com");
                    if (!ForumUrlRouter.isForumHost(host)) host = "forum.harleytg.com";
                    ForumNotificationSync.perform(this, host, userId.trim(), "adaptive");
                    failures = 0;
                    nextDelay = PerformanceProfile.notificationPollInterval(this, prefs);
                }
            } catch (ForumNotificationClient.HttpStatusException http) {
                if (http.statusCode == 401) {
                    clearSessionForAuthFailure(this, "adaptive");
                    running = false;
                    stopSelf();
                    nextDelay = 0L;
                } else if (http.statusCode == 429 || http.statusCode == 503) {
                    failures = Math.min(failures + 1, 8);
                    int shift = Math.min(Math.max(failures - 1, 0), 4);
                    long exponential = Math.min(THROTTLE_MAX_MS, THROTTLE_MIN_MS * (1L << shift));
                    nextDelay = Math.max(exponential, Math.min(THROTTLE_MAX_MS, http.retryAfterMs));
                    AppLogger.warn(this, "instant_notification_throttle",
                            "HTTP " + http.statusCode + " | failures=" + failures + " | retry=" + nextDelay + "ms");
                } else {
                    failures = Math.min(failures + 1, 8);
                    int shift = Math.min(Math.max(failures - 1, 0), 3);
                    nextDelay = Math.min(FAILURE_MAX_MS, FAILURE_MIN_MS * (1L << shift));
                    AppLogger.warn(this, "instant_notification_http", "HTTP " + http.statusCode + " | retry=" + nextDelay + "ms");
                }
            } catch (Throwable t) {
                failures = Math.min(failures + 1, 8);
                int shift = Math.min(Math.max(failures - 1, 0), 3);
                long retry = Math.min(FAILURE_MAX_MS, FAILURE_MIN_MS * (1L << shift));
                try {
                    retry = Math.max(retry, PerformanceProfile.notificationPollInterval(this, getSharedPreferences("hcf_app", 0)));
                } catch (Throwable ignored) {}
                nextDelay = retry;
                if (failures == 1 || failures == 2 || failures == 4 || failures == 8) {
                    AppLogger.warn(this, "instant_notification_poll", t.getClass().getSimpleName() + " | failures=" + failures + " | retry=" + retry + "ms");
                }
            } finally {
                inFlight.set(false);
                if (running) {
                    if (immediateRequested) {
                        immediateRequested = false;
                        scheduleNext(0L);
                    } else {
                        scheduleNext(Math.max(0L, nextDelay));
                    }
                }
            }
        }

        private void registerNetworkCallback() {
            if (networkCallbackRegistered) return;
            try {
                ConnectivityManager cm = (ConnectivityManager) getSystemService("connectivity");
                if (cm == null) return;
                networkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override public void onAvailable(Network network) {
                        if (running) {
                            failures = 0;
                            requestImmediateSync(InstantNotificationService.this);
                        }
                    }
                };
                cm.registerDefaultNetworkCallback(networkCallback);
                networkCallbackRegistered = true;
            } catch (Throwable t) {
                AppLogger.warn(this, "notification_network_callback", t.getClass().getSimpleName());
            }
        }

        private void registerScreenOnReceiver() {
            if (screenOnReceiverRegistered) return;
            try {
                screenOnReceiver = new BroadcastReceiver() {
                    @Override public void onReceive(Context context, Intent intent) {
                        if (intent != null && Intent.ACTION_SCREEN_ON.equals(intent.getAction()) && running) {
                            requestImmediateSync(InstantNotificationService.this);
                        }
                    }
                };
                registerReceiver(screenOnReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
                screenOnReceiverRegistered = true;
            } catch (Throwable error) {
                AppLogger.warn(this, "notification_screen_receiver", error.getClass().getSimpleName());
            }
        }

        private void unregisterScreenOnReceiver() {
            if (!screenOnReceiverRegistered) return;
            try {
                if (screenOnReceiver != null) unregisterReceiver(screenOnReceiver);
            } catch (Throwable ignored) {
            }
            screenOnReceiver = null;
            screenOnReceiverRegistered = false;
        }

        private void unregisterNetworkCallback() {
            if (!networkCallbackRegistered) return;
            try {
                ConnectivityManager cm = (ConnectivityManager) getSystemService("connectivity");
                if (cm != null && networkCallback != null) cm.unregisterNetworkCallback(networkCallback);
            } catch (Throwable ignored) {}
            networkCallback = null;
            networkCallbackRegistered = false;
        }

        @Override
        public void onTaskRemoved(Intent rootIntent) {
            AppLogger.warn(this, "instant_notification_service", "task removed • START_STICKY remains armed");
            try { NotificationSyncScheduler.schedule(this); } catch (Throwable ignored) {}
            super.onTaskRemoved(rootIntent);
        }

        @Override
        public void onDestroy() {
            running = false;
            try { stopForeground(true); } catch (Throwable ignored) {}
            synchronized (scheduleLock) {
                if (scheduled != null) scheduled.cancel(false);
                scheduled = null;
            }
            unregisterNetworkCallback();
            unregisterScreenOnReceiver();
            SharedPreferences prefs = getSharedPreferences("hcf_app", 0);
            String reason = prefs.getString("notification_service_stop_reason", "system-or-process");
            prefs.edit().remove("notification_service_stop_reason").apply();
            AppLogger.info(this, "instant_notification_service", "stopped | reason=" + reason);
            super.onDestroy();
        }
    }

    // ---- NotificationSyncJobService.java ----
    public static final class NotificationSyncJobService extends JobService {
        @Override public boolean onStopJob(JobParameters params) { return true; }

        @Override public boolean onStartJob(final JobParameters params) {
            SharedPreferences prefs = getSharedPreferences("hcf_app", 0);
            if (!prefs.getBoolean("background_notification_sync", true)) return false;
            String userId = prefs.getString("session_user_id", "");
            if (userId == null || userId.trim().isEmpty()) return false;

            AppExecutors.network().execute(new Runnable() {
                @Override public void run() {
                    try { syncNow(); }
                    catch (Throwable t) {
                        AppLogger.warn(NotificationSyncJobService.this, "background_notification_sync", "job-failed | " + t.getClass().getSimpleName());
                    } finally {
                        try { jobFinished(params, false); } catch (Throwable ignored) {}
                    }
                }
            });
            return true;
        }

        private void syncNow() throws Exception {
            SharedPreferences prefs = getSharedPreferences("hcf_app", 0);
            if (!prefs.getBoolean("background_notification_sync", true)) return;
            String userId = prefs.getString("session_user_id", "");
            if (userId == null || userId.trim().isEmpty()) return;
            String host = prefs.getString("active_host", "forum.harleytg.com");
            try {
                ForumNotificationSync.perform(this, ForumUrlRouter.isForumHost(host) ? host : "forum.harleytg.com", userId.trim(), "fallback-job");
            } catch (ForumNotificationClient.HttpStatusException http) {
                if (http.statusCode == 401) {
                    InstantNotificationService.clearSessionForAuthFailure(this, "fallback-job");
                }
                throw http;
            }
        }
    }

    // ---- BootReceiver.java ----
    /* loaded from: classes.dex */
    public static final class BootReceiver extends BroadcastReceiver {
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if (intent != null && "android.intent.action.MY_PACKAGE_REPLACED".equals(intent.getAction())) {
                AppUpdateDownloader.cleanupAfterSuccessfulUpdate(context);
                AppUpdateDownloader.cleanupStaleUpdaterApks(context);
                TelemetryService.sendEvent(context, "update_installed", BuildInfo.installedVersionName());
            }
            NotificationSyncScheduler.apply(context);
            UpdateScheduler.apply(context);
            AppLogger.info(context, "boot_receiver", intent == null ? "boot" : String.valueOf(intent.getAction()));
        }
    }
}

// ---- NotificationHelper.java ----
/* loaded from: classes.dex */
final class NotificationHelper {
    static final String ACTION_NOTIFICATION_EVENT = "com.harleytg.forum.dev.NOTIFICATION_EVENT";
    static final String CHANNEL_GROUP_ID = "hcf_notifications_v1";
    static final String CHANNEL_GROUP_NAME = "Harley's Clan Forum [Beta]";
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
        // Product rule: HCF Alerts are required and have no app-level OFF state.
        // Android notification permission and per-channel settings remain authoritative.
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
            PendingIntent activity = PendingIntent.getActivity(context, 41079, new Intent(context, (Class<?>) HcfMainActivities.MainActivity.class).addFlags(603979776), 201326592);
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
            return silencePassiveEnabled(context) ? "Disabled by app setting" : "Enabled • Silent";
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
        if (SILENT_CHANNEL_ID.equals(str) && silencePassiveEnabled(context)) return false;
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

    static boolean postFromPushPayload(Context context, JSONObject data) {
        if (context == null || data == null || !canPost(context)) return false;
        try {
            JSONObject attributes = data.optJSONObject("attributes");
            if (attributes == null) attributes = data;

            String id = firstNonEmpty(
                    data.optString("id", ""),
                    attributes.optString("id", ""),
                    attributes.optString("notificationId", ""),
                    attributes.optString("notification_id", ""));
            String title = firstNonEmpty(
                    attributes.optString("title", ""),
                    data.optString("title", ""));
            String body = firstNonEmpty(
                    attributes.optString("body", ""),
                    attributes.optString("message", ""),
                    attributes.optString("text", ""),
                    attributes.optString("content", ""),
                    data.optString("body", ""),
                    data.optString("message", ""),
                    data.optString("content", ""));

            if (id.isEmpty() || title.isEmpty() || body.isEmpty()) {
                return false;
            }
            if (!claimDeliveredId(context, id)) {
                return false;
            }

            SharedPreferences prefs = context.getSharedPreferences("hcf_app", 0);
            String host = prefs.getString("active_host", ForumConfig.PRIMARY_HOST);
            if (!ForumUrlRouter.isForumHost(host)) host = ForumConfig.PRIMARY_HOST;
            String url = firstNonEmpty(attributes.optString("url", ""), data.optString("url", ""));
            if (url.isEmpty()) url = ForumUrlRouter.home(host) + "notifications";

            postInternal(context,
                    trim(title, 120, "Harley's Clan Forum"),
                    trim(body, 500, "You have a new forum notification."),
                    validatedForumUri(url),
                    603979776 | (id.hashCode() & 268435455),
                    true);
            AppLogger.info(context, "notification_push_payload", "posted native Flarum id=" + id);
            return true;
        } catch (Throwable error) {
            AppLogger.warn(context, "notification_push_payload", error.getClass().getSimpleName());
            return false;
        }
    }

    /** Future FCM receive hook: FirebaseMessagingService can call this unchanged. */
    static boolean onPushMessageReceived(Context context, Map<String, String> data) {
        JSONObject payload = new JSONObject();
        if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                try {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        payload.put(entry.getKey(), entry.getValue());
                    }
                } catch (Throwable ignored) {}
            }
        }
        boolean posted = postFromPushPayload(context, payload);
        HcfNotificationEngine.InstantNotificationService.requestImmediateSync(context);
        return posted;
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
            Intent intent = new Intent(context, (Class<?>) HcfMainActivities.MainActivity.class);
            intent.setAction("com.harleytg.forum.dev.OPEN_NOTIFICATION");
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

    private static void postForumAlert(final Context context, final ForumNotificationClient.Alert alert,
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

    static Notification buildInstantServiceNotification(Context context) {
        Intent intent = new Intent(context, (Class<?>) HcfMainActivities.MainActivity.class);
        intent.addFlags(603979776);
        return new Notification.Builder(context, SILENT_CHANNEL_ID).setSmallIcon(R.drawable.ic_notification_paw).setContentTitle("Harley's Clan Forum").setContentText("Live alerts active • checking in real time").setContentIntent(PendingIntent.getActivity(context, 41070, intent, 201326592)).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setCategory("service").setVisibility(0).setPriority(-2).build();
    }

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

    static synchronized int deliverDetailedAlerts(Context context, List<ForumNotificationClient.Alert> list, int expected, String host, String source) {
        synchronized (NotificationHelper.class) {
            if (context == null || expected <= 0) {
                return 0;
            }
            String safeHost = !ForumUrlRouter.isForumHost(host) ? "forum.harleytg.com" : host;
            SharedPreferences prefs = context.getSharedPreferences("hcf_app", 0);
            Set<String> delivered = deliveredIds(prefs.getString("delivered_notification_ids", ""));
            int max = Math.max(1, expected);
            int posted = 0;
            boolean sawAlreadyDelivered = false;

            if (list != null) {
                for (ForumNotificationClient.Alert alert : list) {
                    if (alert == null || alert.id.isEmpty()) continue;
                    if (delivered.contains(alert.id)) {
                        sawAlreadyDelivered = true;
                        continue;
                    }
                    postForumAlert(context, alert, safeHost,
                            603979776 | (alert.id.hashCode() & 268435455), null, false);
                    delivered.add(alert.id);
                    posted++;
                    if (posted >= max) break;
                }
            }

            trimDeliveredIds(delivered, 100);
            prefs.edit().putString("delivered_notification_ids", join(delivered)).apply();

            // If Pusher already posted this exact Flarum ID, do not emit a second
            // generic notification during the authoritative polling reconciliation.
            if (posted == 0 && !sawAlreadyDelivered) {
                postGenericDelta(context, expected, safeHost);
            }
            if (posted > 1) {
                postGroupSummary(context, posted, safeHost);
            }
            AppLogger.info(context, "notification_details",
                    (source == null ? "sync" : source) + " | delivered=" + posted
                            + " expected=" + expected + " reconciled=" + sawAlreadyDelivered);
            return posted;
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
        // This is a real forum alert fallback, not passive service status. It must
        // remain on the audible HCF Alerts channel even when Silent Alerts is off.
        postInternal(context, "Harley's Clan Forum", str3, Uri.parse(ForumUrlRouter.home(str) + "notifications"), FORUM_SUMMARY_ID, true, false);
    }

    static void postUpdateAvailable(Context context, UpdateChecker.Release release) {
        String str;
        createChannel(context);
        if (hasRuntimePermission(context) && areAppNotificationsEnabled(context)) {
            try {
                Intent intent = new Intent(context, (Class<?>) HcfSubActivities.SettingsActivity.class);
                intent.addFlags(335544320);
                PendingIntent activity = PendingIntent.getActivity(context, 51001, intent, 201326592);
                Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID);
                Notification.Builder contentTitle = builder.setSmallIcon(R.drawable.ic_notification_paw).setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.htg_app_logo)).setContentTitle("Beta update available");
                StringBuilder sb = new StringBuilder("v");
                sb.append(UpdateChecker.displayVersion(release));
                if (release == null || release.versionCode <= 0) {
                    str = "";
                } else {
                    str = " • build " + release.versionCode;
                }
                sb.append(str);
                sb.append(release != null && release.sameVersionHashUpdate
                        ? " is a revised Dev/Beta APK (new SHA-256)."
                        : " is ready for Dev/Beta.");
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
                Intent intent = new Intent(context, (Class<?>) HcfSubActivities.SettingsActivity.class);
                intent.setAction("com.harleytg.forum.dev.INSTALL_UPDATE");
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
            Intent intent = new Intent(context, (Class<?>) HcfMainActivities.MainActivity.class);
            intent.setAction("com.harleytg.forum.dev.OPEN_NOTIFICATION");
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

    private static boolean claimDeliveredId(Context context, String id) {
        if (context == null || id == null || id.trim().isEmpty()) return false;
        synchronized (NotificationHelper.class) {
            SharedPreferences prefs = context.getSharedPreferences("hcf_app", 0);
            Set<String> delivered = deliveredIds(prefs.getString("delivered_notification_ids", ""));
            String cleanId = id.trim();
            if (delivered.contains(cleanId)) return false;
            delivered.add(cleanId);
            trimDeliveredIds(delivered, 100);
            prefs.edit().putString("delivered_notification_ids", join(delivered)).apply();
            return true;
        }
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
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


// ---- NotificationSyncScheduler.java ----
final class NotificationSyncScheduler {
    private static final int JOB_ID = 41071;
    private static final long PERIOD_MS = 900000L;

    static void apply(Context context) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences("hcf_app", 0);
            if (!prefs.getBoolean("background_notification_sync", true)) {
                HcfNotificationEngine.InstantNotificationService.stop(context, "background-sync-disabled");
                cancel(context);
                AppLogger.info(context, "notification_sync_mode", "disabled");
                return;
            }

            String userId = prefs.getString("session_user_id", "");

            if (userId != null && !userId.trim().isEmpty()) {
                // Reliability rule: signed-in + background sync enabled always keeps
                // the legal foreground service running with its required ongoing notification.
                HcfNotificationEngine.InstantNotificationService.start(context);
                AppLogger.info(context, "notification_sync_mode", "foreground live sync • signed-in");
            } else {
                HcfNotificationEngine.InstantNotificationService.stop(context, "signed-out");
                AppLogger.info(context, "notification_sync_mode", "waiting for signed-in session");
            }

            schedule(context);
        } catch (Throwable t) {
            AppLogger.error(context, "notification_sync_apply", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        }
    }

    static void schedule(Context context) {
        if (context == null) return;
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService("jobscheduler");
            if (scheduler == null) return;
            JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, HcfNotificationEngine.NotificationSyncJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(PERIOD_MS)
                    .setPersisted(true)
                    .build();
            AppLogger.info(context, "notification_sync_schedule", scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS ? "scheduled" : "failed");
        } catch (Throwable t) {
            AppLogger.error(context, "notification_sync_schedule", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        }
    }

    static void cancel(Context context) {
        if (context == null) return;
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService("jobscheduler");
            if (scheduler != null) scheduler.cancel(JOB_ID);
            AppLogger.info(context, "notification_sync_schedule", "cancelled");
        } catch (Throwable t) {
            AppLogger.error(context, "notification_sync_cancel", t.getClass().getSimpleName());
        }
    }

    private NotificationSyncScheduler() {}
}

// ---- HcfNotificationActions.java ----
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

    public static class ActionReceiver extends BroadcastReceiver {
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
