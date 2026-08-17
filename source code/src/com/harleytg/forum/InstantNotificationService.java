package com.harleytg.forum.dev;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * User-visible foreground service for low-latency forum alerts. Healthy signed-in
 * sessions are checked about every 1.25 seconds. Failures back off automatically,
 * and Android's 15-minute JobScheduler remains a resilience fallback.
 */
public final class InstantNotificationService extends Service {
    static final String ACTION_SYNC_NOW = "com.harleytg.forum.dev.SYNC_NOTIFICATIONS_NOW";
    static final int SERVICE_NOTIFICATION_ID = 41070;
    static final long POLL_MS = 1250L;
    private static final long NO_SESSION_POLL_MS = 5000L;
    private static final long FAILURE_MIN_MS = 2500L;
    private static final long FAILURE_MAX_MS = 30000L;

    private final Object wakeSignal = new Object();
    private volatile boolean running;
    private Thread worker;

    static void apply(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true)
                && prefs.getBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, true);
        if (enabled) start(context); else stop(context);
    }

    static void start(Context context) {
        startWithAction(context, null);
    }

    static void requestImmediateSync(Context context) {
        startWithAction(context, ACTION_SYNC_NOW);
    }

    private static void startWithAction(Context context, String action) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, InstantNotificationService.class);
            if (action != null) intent.setAction(action);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
            AppLogger.info(context, "instant_notification_service",
                    action == null ? "start-requested" : "sync-requested");
        } catch (Throwable t) {
            AppLogger.warn(context, "instant_notification_service",
                    "start-blocked | " + t.getClass().getSimpleName());
        }
    }

    static void stop(Context context) {
        if (context == null) return;
        try {
            context.stopService(new Intent(context, InstantNotificationService.class));
            AppLogger.info(context, "instant_notification_service", "stop-requested");
        } catch (Throwable t) {
            AppLogger.warn(context, "instant_notification_service", "stop | " + t.getClass().getSimpleName());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannel(this);
        Notification serviceNotification = NotificationHelper.buildInstantServiceNotification(this);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(SERVICE_NOTIFICATION_ID, serviceNotification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(SERVICE_NOTIFICATION_ID, serviceNotification);
            }
        } catch (Throwable t) {
            AppLogger.error(this, "instant_notification_foreground",
                    t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
            stopSelf();
            return;
        }
        startWorker();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences prefs = getSharedPreferences(AppPrefs.FILE, MODE_PRIVATE);
        if (!prefs.getBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true)
                || !prefs.getBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, true)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startWorker();
        if (intent != null && ACTION_SYNC_NOW.equals(intent.getAction())) wakeWorker();
        return START_STICKY;
    }

    private synchronized void startWorker() {
        if (worker != null && worker.isAlive()) return;
        running = true;
        worker = new Thread(this::runLoop, "hcf-instant-notifications");
        worker.start();
    }

    private void wakeWorker() {
        synchronized (wakeSignal) { wakeSignal.notifyAll(); }
    }

    private void runLoop() {
        int failures = 0;
        AppLogger.info(this, "instant_notification_service", "running | poll=" + POLL_MS + "ms");
        while (running) {
            long delay = POLL_MS;
            try {
                SharedPreferences prefs = getSharedPreferences(AppPrefs.FILE, MODE_PRIVATE);
                if (!prefs.getBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true)
                        || !prefs.getBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, true)) {
                    stopSelf();
                    break;
                }

                String userId = prefs.getString(AppPrefs.SESSION_USER_ID, "");
                if (userId == null || userId.trim().isEmpty()) {
                    failures = 0;
                    delay = NO_SESSION_POLL_MS;
                } else {
                    String host = prefs.getString(AppPrefs.ACTIVE_HOST, ForumConfig.PRIMARY_HOST);
                    if (!ForumUrlRouter.isForumHost(host)) host = ForumConfig.PRIMARY_HOST;
                    ForumNotificationSync.perform(this, host, userId.trim(), "instant");
                    failures = 0;
                    delay = POLL_MS;
                }
            } catch (Throwable t) {
                failures = Math.min(failures + 1, 6);
                delay = Math.min(FAILURE_MAX_MS, FAILURE_MIN_MS << Math.min(failures - 1, 3));
                AppLogger.warn(this, "instant_notification_poll",
                        t.getClass().getSimpleName() + " | retry=" + delay + "ms");
            }

            synchronized (wakeSignal) {
                if (!running) break;
                try {
                    wakeSignal.wait(delay);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        AppLogger.info(this, "instant_notification_service", "stopped");
    }

    @Override
    public void onDestroy() {
        running = false;
        wakeWorker();
        if (worker != null) worker.interrupt();
        worker = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
