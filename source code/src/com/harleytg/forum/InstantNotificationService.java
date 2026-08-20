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

/* loaded from: classes.dex */
public final class InstantNotificationService extends Service {
    static final String ACTION_SYNC_NOW = "com.harleytg.forum.SYNC_NOTIFICATIONS_NOW";
    private static final long FAILURE_MAX_MS = 60000;
    private static final long FAILURE_MIN_MS = 2500;
    private static final long NO_SESSION_POLL_MS = 15000;
    static final int SERVICE_NOTIFICATION_ID = 41070;
    private int failures;
    private volatile boolean immediateRequested;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean networkCallbackRegistered;
    private volatile boolean running;
    private ScheduledFuture<?> scheduled;
    private final Object scheduleLock = new Object();
    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    static void apply(Context context) {
        if (context == null) {
            return;
        }
        if (context.getSharedPreferences("hcf_app", 0).getBoolean("background_notification_sync", true)) {
            start(context);
        } else {
            stop(context);
        }
    }

    /* JADX WARN: Unreachable blocks removed: 2, instructions: 2 */
    static void start(Context context) {
        if (context == null) {
            return;
        }
        NotificationHelper.silencePassiveEnabled(context);
        startWithAction(context, null);
    }

    static void requestImmediateSync(Context context) {
        if (context == null) {
            return;
        }
        if (NotificationHelper.silencePassiveEnabled(context)) {
            requestOneShotSync(context);
        } else {
            startWithAction(context, ACTION_SYNC_NOW);
        }
    }

    private static void requestOneShotSync(Context context) {
        final Context applicationContext = context.getApplicationContext();
        AppExecutors.network().execute(new Runnable() { // from class: com.harleytg.forum.InstantNotificationService$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                InstantNotificationService.lambda$requestOneShotSync$0(applicationContext);
            }
        });
    }

    static /* synthetic */ void lambda$requestOneShotSync$0(Context context) {
        String string;
        String str = "forum.harleytg.com";
        try {
            SharedPreferences sharedPreferences = context.getSharedPreferences("hcf_app", 0);
            if (sharedPreferences.getBoolean("background_notification_sync", true) && (string = sharedPreferences.getString("session_user_id", "")) != null && !string.trim().isEmpty() && RuntimeState.networkAvailable(context)) {
                String string2 = sharedPreferences.getString("active_host", "forum.harleytg.com");
                if (ForumUrlRouter.isForumHost(string2)) {
                    str = string2;
                }
                ForumNotificationSync.perform(context, str, string.trim(), "silent-one-shot");
                AppLogger.info(context, "instant_notification_service", "one-shot sync • silent channel hidden");
            }
        } catch (Throwable th) {
            AppLogger.warn(context, "instant_notification_service", "one-shot | " + th.getClass().getSimpleName());
        }
    }

    /* JADX WARN: Unreachable blocks removed: 5, instructions: 6 */
    private static void startWithAction(Context context, String str) {
        if (context == null) {
            return;
        }
        NotificationHelper.silencePassiveEnabled(context);
        try {
            Intent intent = new Intent(context, (Class<?>) InstantNotificationService.class);
            if (str != null) {
                intent.setAction(str);
            }
            context.startForegroundService(intent);
        } catch (Throwable th) {
            AppLogger.warn(context, "instant_notification_service", "start-blocked | " + th.getClass().getSimpleName());
        }
    }

    static void stop(Context context) {
        if (context == null) {
            return;
        }
        try {
            context.stopService(new Intent(context, (Class<?>) InstantNotificationService.class));
        } catch (Throwable th) {
            AppLogger.warn(context, "instant_notification_service", "stop | " + th.getClass().getSimpleName());
        }
    }

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannel(this);
        Notification buildInstantServiceNotification = NotificationHelper.buildInstantServiceNotification(this);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(SERVICE_NOTIFICATION_ID, buildInstantServiceNotification, 1073741824);
            } else {
                startForeground(SERVICE_NOTIFICATION_ID, buildInstantServiceNotification);
            }
            this.running = true;
            this.failures = 0;
            registerNetworkCallback();
            AppLogger.info(this, "instant_notification_service", "started • adaptive v10000047");
            scheduleNext(0L);
        } catch (Throwable th) {
            AppLogger.error(this, "instant_notification_foreground", th.getClass().getSimpleName() + ": " + String.valueOf(th.getMessage()));
            stopSelf();
        }
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int i, int i2) {
        if (!getSharedPreferences("hcf_app", 0).getBoolean("background_notification_sync", true)) {
            stopSelf();
            return 2;
        }
        this.running = true;
        if (intent != null && ACTION_SYNC_NOW.equals(intent.getAction())) {
            this.immediateRequested = true;
            scheduleNext(0L);
        } else if (this.scheduled == null) {
            scheduleNext(0L);
        }
        return 1;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void scheduleNext(long j) {
        if (this.running) {
            synchronized (this.scheduleLock) {
                ScheduledFuture<?> scheduledFuture = this.scheduled;
                if (scheduledFuture != null) {
                    scheduledFuture.cancel(false);
                }
                long max = Math.max(0L, j);
                RuntimeDiagnostics.notificationPoll(max, "Adaptive polling fallback");
                this.scheduled = AppExecutors.scheduler().schedule(new Runnable() { // from class: com.harleytg.forum.InstantNotificationService$$ExternalSyntheticLambda1
                    @Override // java.lang.Runnable
                    public final void run() {
                        InstantNotificationService.this.triggerSync();
                    }
                }, max, TimeUnit.MILLISECONDS);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void triggerSync() {
        if (this.running) {
            if (!this.inFlight.compareAndSet(false, true)) {
                this.immediateRequested = true;
            } else {
                AppExecutors.network().execute(new Runnable() { // from class: com.harleytg.forum.InstantNotificationService$$ExternalSyntheticLambda2
                    @Override // java.lang.Runnable
                    public final void run() {
                        InstantNotificationService.this.m18x7f46ef73();
                    }
                });
            }
        }
    }

    /* JADX WARN: Removed duplicated region for block: B:26:0x007a A[RETURN] */
    /* JADX WARN: Removed duplicated region for block: B:27:0x007b  */
    /* renamed from: lambda$triggerSync$1$com-harleytg-forum-dev-InstantNotificationService, reason: not valid java name */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */

    /* synthetic */ void m18x7f46ef73() {
        long nextDelay = NO_SESSION_POLL_MS;
        try {
            SharedPreferences prefs = getSharedPreferences("hcf_app", 0);
            if (!prefs.getBoolean("background_notification_sync", true)) { this.running = false; stopSelf(); return; }
            String userId = prefs.getString("session_user_id", "");
            if (userId == null || userId.trim().isEmpty() || !RuntimeState.networkAvailable(this)) {
                this.failures = 0; nextDelay = NO_SESSION_POLL_MS;
            } else {
                String host = prefs.getString("active_host", "forum.harleytg.com");
                if (!ForumUrlRouter.isForumHost(host)) host = "forum.harleytg.com";
                ForumNotificationSync.perform(this, host, userId.trim(), "adaptive");
                this.failures = 0; nextDelay = PerformanceProfile.notificationPollInterval(this, prefs);
            }
        } catch (Throwable t) {
            this.failures = Math.min(this.failures + 1, 8);
            int shift = Math.min(Math.max(this.failures - 1, 0), 4);
            long retry = Math.min(FAILURE_MAX_MS, FAILURE_MIN_MS * (1L << shift));
            try { retry = Math.max(retry, PerformanceProfile.notificationPollInterval(this, getSharedPreferences("hcf_app", 0))); } catch (Throwable ignored) {}
            nextDelay = retry;
            if (this.failures == 1 || this.failures == 2 || this.failures == 4 || this.failures == 8)
                AppLogger.warn(this, "instant_notification_poll", t.getClass().getSimpleName() + " | failures=" + this.failures + " | retry=" + retry + "ms");
        } finally {
            this.inFlight.set(false);
            if (this.running) {
                if (this.immediateRequested) { this.immediateRequested = false; scheduleNext(0L); }
                else scheduleNext(nextDelay);
            }
        }
    }

    private void registerNetworkCallback() {
        if (this.networkCallbackRegistered) {
            return;
        }
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService("connectivity");
            if (connectivityManager == null) {
                return;
            }
            ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() { // from class: com.harleytg.forum.InstantNotificationService.1
                @Override // android.net.ConnectivityManager.NetworkCallback
                public void onAvailable(Network network) {
                    if (InstantNotificationService.this.running) {
                        InstantNotificationService.this.failures = 0;
                        InstantNotificationService.this.immediateRequested = true;
                        InstantNotificationService.this.scheduleNext(0L);
                    }
                }
            };
            this.networkCallback = networkCallback;
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
            this.networkCallbackRegistered = true;
        } catch (Throwable th) {
            AppLogger.warn(this, "notification_network_callback", th.getClass().getSimpleName());
        }
    }

    private void unregisterNetworkCallback() {
        ConnectivityManager.NetworkCallback networkCallback;
        if (this.networkCallbackRegistered) {
            try {
                ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService("connectivity");
                if (connectivityManager != null && (networkCallback = this.networkCallback) != null) {
                    connectivityManager.unregisterNetworkCallback(networkCallback);
                }
            } catch (Throwable unused) {
            }
            this.networkCallback = null;
            this.networkCallbackRegistered = false;
        }
    }

    @Override // android.app.Service
    public void onDestroy() {
        this.running = false;
        try {
            stopForeground(1);
        } catch (Throwable unused) {
        }
        synchronized (this.scheduleLock) {
            ScheduledFuture<?> scheduledFuture = this.scheduled;
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
            }
            this.scheduled = null;
        }
        unregisterNetworkCallback();
        AppLogger.info(this, "instant_notification_service", "stopped");
        super.onDestroy();
    }
}
