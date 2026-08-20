from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: stable-runtime-fixes-v10000077.py <promoted-source-code>')

ROOT = Path(sys.argv[1])
SRC = ROOT / 'src/com/harleytg/forum'

if not (ROOT / 'AndroidManifest.xml').exists():
    raise SystemExit('promoted AndroidManifest.xml not found')

# -----------------------------------------------------------------------------
# Stable build identity
# -----------------------------------------------------------------------------
manifest = ROOT / 'AndroidManifest.xml'
s = manifest.read_text(encoding='utf-8')
s = re.sub(r'android:versionCode="[0-9]+"', 'android:versionCode="10000077"', s, count=1)
s = re.sub(r'android:versionName="[^"]+"', 'android:versionName="1.0 (10000077)"', s, count=1)
s = s.replace('android:value="10000072"', 'android:value="10000077"')
s = s.replace('android:value="10000076"', 'android:value="10000077"')
s = s.replace('android:value="dev"', 'android:value="stable"')
manifest.write_text(s, encoding='utf-8')

build_info = SRC / 'BuildInfo.java'
s = build_info.read_text(encoding='utf-8')
s = re.sub(r'static final String APK_FILE_NAME = ".*?";', 'static final String APK_FILE_NAME = "HCF-Stable-v10000077.apk";', s)
s = re.sub(r'static final String CHANNEL = ".*?";', 'static final String CHANNEL = "Stable";', s)
s = re.sub(r'static final String DEFAULT_UPDATE_CHANNEL = ".*?";', 'static final String DEFAULT_UPDATE_CHANNEL = "stable";', s)
s = re.sub(r'static final String DEVELOPMENT_BUILD_LABEL = ".*?";', 'static final String DEVELOPMENT_BUILD_LABEL = "Harley\'s Clan Forum v1.0";', s)
s = re.sub(r'static final String META_LINE = ".*?";', 'static final String META_LINE = "1.0 • Stable";', s)
s = re.sub(r'static final String VERSION_BUILD_LINE = ".*?";', 'static final String VERSION_BUILD_LINE = "1.0 • Stable • Build 10000077";', s)
s = re.sub(r'static final int VERSION_CODE = [0-9]+;', 'static final int VERSION_CODE = 10000077;', s)
s = re.sub(r'static final boolean ENABLE_DEV_TEST_MENU = (?:true|false);', 'static final boolean ENABLE_DEV_TEST_MENU = false;', s)
s = re.sub(r'static final boolean ALLOW_UPDATE_CHANNEL_SWITCH = (?:true|false);', 'static final boolean ALLOW_UPDATE_CHANNEL_SWITCH = false;', s)
build_info.write_text(s, encoding='utf-8')

# Keep What's New aligned with the installed Stable build and remove public
# Beta/Dev labels left by the recovered source.
release_notes = SRC / 'ReleaseNotes.java'
if release_notes.exists():
    s = release_notes.read_text(encoding='utf-8')
    s = s.replace('10000072', '10000077').replace('10000076', '10000077')
    s = s.replace('Beta/Dev', 'Stable').replace('Development/Beta', 'Stable')
    s = s.replace('Development / Beta', 'Stable').replace('Development and Beta', 'Stable')
    s = s.replace('v1.0  •  Dev', 'v1.0  •  Stable')
    s = s.replace('Stable remains separate; this feature set is scoped to com.harleytg.forum.',
                  'This build uses the Stable package, Stable update channel and production defaults.')
    release_notes.write_text(s, encoding='utf-8')

# -----------------------------------------------------------------------------
# Foreground notification service
#
# Fixes:
# - never launch the foreground service for a guest/no-session state;
# - call startForeground before async/network work;
# - use the manifest-declared foreground service type through the safe two-arg
#   API instead of forcing the type constant again on OEM Android 14 builds;
# - stop instead of polling every 15 seconds after the session disappears;
# - restore the real adaptive sync body that JADX failed to reconstruct;
# - report the current BuildInfo.VERSION_CODE instead of stale v10000047.
# -----------------------------------------------------------------------------
(SRC / 'InstantNotificationService.java').write_text(r'''package com.harleytg.forum;

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
''', encoding='utf-8')

# Guest/no-session state should complete the fallback job immediately instead of
# scheduling network work and logging no-session repeatedly.
(SRC / 'NotificationSyncJobService.java').write_text(r'''package com.harleytg.forum;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.SharedPreferences;

public final class NotificationSyncJobService extends JobService {
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
        ForumNotificationSync.perform(this, ForumUrlRouter.isForumHost(host) ? host : "forum.harleytg.com", userId.trim(), "fallback-job");
    }
}
''', encoding='utf-8')

# -----------------------------------------------------------------------------
# Stable notification-channel cleanup
# -----------------------------------------------------------------------------
helper = SRC / 'NotificationHelper.java'
s = helper.read_text(encoding='utf-8')
s = s.replace('static final String CHANNEL_GROUP_NAME = "Harley\'s Clan Forum [Beta]";',
              'static final String CHANNEL_GROUP_NAME = "Harley\'s Clan Forum";')
s = s.replace('static final String CHANNEL_GROUP_NAME = "Harley\'s Clan Forum [Stable]";',
              'static final String CHANNEL_GROUP_NAME = "Harley\'s Clan Forum";')

# Create the Test channel only for actual development builds. Stable deletes a
# previously-created test channel during migration.
pattern = re.compile(
    r'\s*NotificationChannel notificationChannel3 = new NotificationChannel\(TEST_CHANNEL_ID, TEST_CHANNEL_NAME, 3\);'
    r'.*?notificationManager\.createNotificationChannel\(notificationChannel3\);',
    re.S)
replacement = r'''
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
                }'''
s, count = pattern.subn(replacement, s, count=1)
if count != 1:
    raise SystemExit('NotificationHelper test-channel block not found')

s = s.replace('AppLogger.info(context, "notification_channel", "channels=HCF Alerts|HCF Silent Alerts|HCF Test Alerts");',
              'AppLogger.info(context, "notification_channel", BuildInfo.ENABLE_DEV_TEST_MENU ? "channels=HCF Alerts|HCF Silent Alerts|HCF Test Alerts" : "channels=HCF Alerts|HCF Silent Alerts");')

# Stable's service notification builder should not rebuild all channels a second
# time before startForeground.
s = s.replace('static Notification buildInstantServiceNotification(Context context) {\n        createChannel(context);',
              'static Notification buildInstantServiceNotification(Context context) {')

# Keep dormant test APIs from recreating/using the removed Stable test channel.
s = s.replace('static boolean postNotificationServiceTest(Context context) {\n        if (context == null) {',
              'static boolean postNotificationServiceTest(Context context) {\n        if (!BuildInfo.ENABLE_DEV_TEST_MENU) return false;\n        if (context == null) {')
s = s.replace('static void postTest(Context context, String str, String str2, String str3) {\n        postInternal(',
              'static void postTest(Context context, String str, String str2, String str3) {\n        if (!BuildInfo.ENABLE_DEV_TEST_MENU) return;\n        postInternal(')
helper.write_text(s, encoding='utf-8')

# Final Stable-only channel/runtime string cleanup.
for p in ROOT.rglob('*'):
    if not p.is_file() or p.suffix.lower() not in {'.java', '.xml', '.txt', '.md', '.json', '.sh'}:
        continue
    try:
        text = p.read_text(encoding='utf-8')
    except Exception:
        continue
    text = text.replace('HCF-Stable-v10000076.apk', 'HCF-Stable-v10000077.apk')
    text = text.replace('1.0 (10000076)', '1.0 (10000077)')
    text = text.replace('Build 10000076', 'Build 10000077')
    p.write_text(text, encoding='utf-8')

# Safety assertions for the generated Stable source.
manifest_text = manifest.read_text(encoding='utf-8')
build_text = build_info.read_text(encoding='utf-8')
helper_text = helper.read_text(encoding='utf-8')
service_text = (SRC / 'InstantNotificationService.java').read_text(encoding='utf-8')
assert 'package="com.harleytg.forum"' in manifest_text
assert 'android:versionCode="10000077"' in manifest_text
assert 'DEFAULT_UPDATE_CHANNEL = "stable"' in build_text
assert 'ENABLE_DEV_TEST_MENU = false' in build_text
assert 'started • adaptive v10000047' not in service_text
assert 'channels=HCF Alerts|HCF Silent Alerts"' in helper_text
print('Stable v10000077 runtime fixes applied')
