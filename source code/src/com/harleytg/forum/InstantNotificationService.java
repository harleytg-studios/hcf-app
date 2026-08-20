package com.harleytg.forum;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.IBinder;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class InstantNotificationService extends Service {
    static final String ACTION_SYNC_NOW = "com.harleytg.forum.SYNC_NOTIFICATIONS_NOW";
    private static final long FAILURE_MAX_MS = 60000L;
    private static final long FAILURE_MIN_MS = 2500L;
    static final int SERVICE_NOTIFICATION_ID = 41070;

    private int failures;
    private volatile boolean immediateRequested;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean networkCallbackRegistered;
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
            stop(context);
            return;
        }
        startWithAction(context, null);
    }

    static void requestImmediateSync(Context context) {
        if (context == null || !hasSession(context)) return;
        if (NotificationHelper.silencePassiveEnabled(context)) requestOneShotSync(context);
        else startWithAction(context, ACTION_SYNC_NOW);
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
        if (context == null) return;
        try { context.stopService(new Intent(context, InstantNotificationService.class)); }
        catch (Throwable t) { AppLogger.warn(context, "instant_notification_service", "stop | " + t.getClass().getSimpleName()); }
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
        long nextDelay = 15000L;
        try {
            SharedPreferences prefs = getSharedPreferences("hcf_app", 0);
            if (!prefs.getBoolean("background_notification_sync", true)) {
                running = false;
                stopSelf();
                return;
            }
            String userId = prefs.getString("session_user_id", "");
            if (userId == null || userId.trim().isEmpty()) {
                running = false;
                stopSelf();
                return;
            }
            if (!RuntimeState.networkAvailable(this)) {
                failures = 0;
                nextDelay = PerformanceProfile.notificationPollInterval(this, prefs);
            } else {
                String host = prefs.getString("active_host", "forum.harleytg.com");
                if (!ForumUrlRouter.isForumHost(host)) host = "forum.harleytg.com";
                ForumNotificationSync.perform(this, host, userId.trim(), "adaptive");
                failures = 0;
                nextDelay = PerformanceProfile.notificationPollInterval(this, prefs);
            }
        } catch (Throwable t) {
            failures = Math.min(failures + 1, 8);
            int shift = Math.min(Math.max(failures - 1, 0), 4);
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
                    scheduleNext(nextDelay);
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
                        immediateRequested = true;
                        scheduleNext(0L);
                    }
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
            ConnectivityManager cm = (ConnectivityManager) getSystemService("connectivity");
            if (cm != null && networkCallback != null) cm.unregisterNetworkCallback(networkCallback);
        } catch (Throwable ignored) {}
        networkCallback = null;
        networkCallbackRegistered = false;
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
        AppLogger.info(this, "instant_notification_service", "stopped");
        super.onDestroy();
    }
}
