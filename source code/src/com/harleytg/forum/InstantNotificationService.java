package com.harleytg.forum.dev;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.IBinder;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Foreground notification service backed by the v10000055 adaptive engine.
 * Foreground-capable devices can sync around 1 second; background/off-screen
 * polling progressively backs off, with immediate sync on explicit wake events.
 */
public final class InstantNotificationService extends Service {
    static final String ACTION_SYNC_NOW = "com.harleytg.forum.dev.SYNC_NOTIFICATIONS_NOW";
    static final int SERVICE_NOTIFICATION_ID = 41070;

    private static final long NO_SESSION_POLL_MS = 15_000L;
    private static final long FAILURE_MIN_MS = 2_500L;
    private static final long FAILURE_MAX_MS = 60_000L;

    private final Object scheduleLock = new Object();
    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    private volatile boolean running;
    private volatile boolean immediateRequested;
    private ScheduledFuture<?> scheduled;
    private int failures;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean networkCallbackRegistered;

    static void apply(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true)
                && prefs.getBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, true);
        if (enabled) start(context); else stop(context);
    }

    static void start(Context context) { startWithAction(context, null); }
    static void requestImmediateSync(Context context) { startWithAction(context, ACTION_SYNC_NOW); }

    private static void startWithAction(Context context, String action) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, InstantNotificationService.class);
            if (action != null) intent.setAction(action);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (Throwable t) {
            AppLogger.warn(context, "instant_notification_service",
                    "start-blocked | " + t.getClass().getSimpleName());
        }
    }

    static void stop(Context context) {
        if (context == null) return;
        try { context.stopService(new Intent(context, InstantNotificationService.class)); }
        catch (Throwable t) {
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
        running = true;
        failures = 0;
        registerNetworkCallback();
        AppLogger.info(this, "instant_notification_service", "started • adaptive v10000055");
        scheduleNext(0L);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences prefs = getSharedPreferences(AppPrefs.FILE, MODE_PRIVATE);
        if (!prefs.getBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true)
                || !prefs.getBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, true)) {
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

    private void scheduleNext(long delayMs) {
        if (!running) return;
        synchronized (scheduleLock) {
            if (scheduled != null) scheduled.cancel(false);
            long safe = Math.max(0L, delayMs);
            RuntimeDiagnostics.notificationPoll(safe,
                    BuildInfo.FCM_CONFIGURED ? "FCM preferred • adaptive fallback" : "Adaptive polling fallback");
            scheduled = AppExecutors.scheduler().schedule(this::triggerSync, safe, TimeUnit.MILLISECONDS);
        }
    }

    private void triggerSync() {
        if (!running) return;
        if (!inFlight.compareAndSet(false, true)) {
            immediateRequested = true;
            return;
        }

        AppExecutors.network().execute(() -> {
            long delay = 5_000L;
            try {
                SharedPreferences prefs = getSharedPreferences(AppPrefs.FILE, MODE_PRIVATE);
                if (!prefs.getBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true)
                        || !prefs.getBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, true)) {
                    running = false;
                    stopSelf();
                    return;
                }

                String userId = prefs.getString(AppPrefs.SESSION_USER_ID, "");
                if (userId == null || userId.trim().isEmpty()) {
                    failures = 0;
                    delay = NO_SESSION_POLL_MS;
                } else if (!RuntimeState.networkAvailable(this)) {
                    failures = 0;
                    delay = 15_000L;
                } else {
                    String host = prefs.getString(AppPrefs.ACTIVE_HOST, ForumConfig.PRIMARY_HOST);
                    if (!ForumUrlRouter.isForumHost(host)) host = ForumConfig.PRIMARY_HOST;
                    ForumNotificationSync.perform(this, host, userId.trim(), "adaptive");
                    failures = 0;
                    delay = PerformanceProfile.notificationPollInterval(this, prefs);
                }
            } catch (Throwable t) {
                failures = Math.min(failures + 1, 8);
                long backoff = Math.min(FAILURE_MAX_MS,
                        FAILURE_MIN_MS << Math.min(Math.max(0, failures - 1), 4));
                SharedPreferences prefs = getSharedPreferences(AppPrefs.FILE, MODE_PRIVATE);
                delay = Math.max(backoff, PerformanceProfile.notificationPollInterval(this, prefs));
                if (failures == 1 || failures == 2 || failures == 4 || failures == 8) {
                    AppLogger.warn(this, "instant_notification_poll",
                            t.getClass().getSimpleName() + " | failures=" + failures + " | retry=" + delay + "ms");
                }
            } finally {
                inFlight.set(false);
                if (!running) return;
                if (immediateRequested) {
                    immediateRequested = false;
                    delay = 0L;
                }
                scheduleNext(delay);
            }
        });
    }


    private void registerNetworkCallback() {
        if (networkCallbackRegistered) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    if (!running) return;
                    failures = 0;
                    immediateRequested = true;
                    scheduleNext(0L);
                }
            };
            cm.registerDefaultNetworkCallback(networkCallback);
            networkCallbackRegistered = true;
        } catch (Throwable t) {
            AppLogger.warn(this, "notification_network_callback", t.getClass().getSimpleName());
        }
    }

    private void unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && networkCallback != null) cm.unregisterNetworkCallback(networkCallback);
        } catch (Throwable ignored) {}
        networkCallback = null;
        networkCallbackRegistered = false;
    }

    @Override
    public void onDestroy() {
        running = false;
        synchronized (scheduleLock) {
            if (scheduled != null) scheduled.cancel(false);
            scheduled = null;
        }
        unregisterNetworkCallback();
        AppLogger.info(this, "instant_notification_service", "stopped");
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
