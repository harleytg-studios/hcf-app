from pathlib import Path
import re

ROOT = Path("source code")
SRC = ROOT / "src/com/harleytg/forum"


def replace_once(text, old, new, label):
    if old not in text:
        raise RuntimeError(f"Missing expected block: {label}")
    return text.replace(old, new, 1)


def sub_once(text, pattern, replacement, label, flags=re.S):
    out, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f"Could not replace {label}; matches={count}")
    return out

# -----------------------------------------------------------------------------
# HcfApplication.java: polling policy + battery optimization UX
# -----------------------------------------------------------------------------
app_path = SRC / "HcfApplication.java"
app = app_path.read_text(encoding="utf-8")

if "import android.provider.Settings;" not in app:
    app = replace_once(app, "import android.os.PowerManager;\n", "import android.os.PowerManager;\nimport android.provider.Settings;\n", "Settings import")

notification_method = r'''    static long notificationPollInterval(Context context, SharedPreferences sharedPreferences) {
        if (context == null) {
            return 2000L;
        }

        final boolean aggressiveRealtime = sharedPreferences == null
                || sharedPreferences.getBoolean("aggressive_realtime", true);
        final boolean foreground = RuntimeState.isForeground();
        final boolean interactive = RuntimeState.isInteractive(context);
        final boolean batteryExempt = BatteryOptimizationHelper.isIgnoring(context);
        final long backgroundDurationMs = RuntimeState.backgroundDurationMs();

        long interval;
        if (aggressiveRealtime) {
            if (foreground) {
                interval = batteryExempt ? 500L : 650L;
            } else if (backgroundDurationMs <= 180000L) {
                interval = batteryExempt ? 1500L : 2000L;
            } else {
                interval = batteryExempt ? 4000L : 5000L;
            }
        } else {
            String saved = saved(sharedPreferences);
            if (!AUTO.equals(saved)) {
                interval = QUALITY.equals(saved) ? 600L : BALANCED.equals(saved) ? 1200L : 2000L;
            } else {
                String runtime = autoRuntime(context, sharedPreferences);
                interval = AUTO_REALTIME.equals(runtime) ? 600L
                        : AUTO_BALANCED.equals(runtime) ? 1200L
                        : AUTO_EXTREME.equals(runtime) ? 5000L : 2000L;
            }

            if (!foreground) {
                interval = backgroundDurationMs <= 180000L
                        ? Math.min(Math.max(interval, 1200L), 2000L)
                        : Math.min(Math.max(interval, 2500L), 5000L);
            }
        }

        int thermalStatus = thermalStatus(context);
        boolean powerSave = isBatterySaver(context);

        // Severe thermal pressure and Battery Saver are the only states allowed
        // to relax the realtime target all the way to the 8-second ceiling.
        if (powerSave || thermalStatus >= severeThermalStatus()) {
            interval = 8000L;
        } else {
            if (thermalStatus >= moderateThermalStatus()) {
                interval = Math.max(interval, 3500L);
            }

            int batteryPercent = batteryPercent(context);
            if (!isCharging(context) && batteryPercent >= 0 && batteryPercent <= 10) {
                interval = Math.max(interval, 5000L);
            }

            // Screen-off polling stays at four seconds or faster in normal power state.
            if (!interactive) {
                interval = Math.min(Math.max(interval, 2500L), 4000L);
            }
        }

        if (!RuntimeState.networkAvailable(context)) {
            interval = Math.max(interval, 5000L);
        }

        return Math.max(400L, Math.min(interval, 8000L));
    }'''
app = sub_once(
    app,
    r'    static long notificationPollInterval\(Context context, SharedPreferences sharedPreferences\) \{.*?\n    \}\n\n(?=    static long livePollInterval)',
    notification_method + "\n\n",
    "notificationPollInterval",
)

live_method = r'''    static long livePollInterval(Context context, SharedPreferences sharedPreferences) {
        if (context == null) {
            return 1000L;
        }

        final boolean aggressiveRealtime = sharedPreferences == null
                || sharedPreferences.getBoolean("aggressive_realtime", true);
        final boolean foreground = RuntimeState.isForeground();

        long interval;
        if (aggressiveRealtime) {
            interval = foreground ? 500L : 1200L;
        } else {
            String saved = saved(sharedPreferences);
            if (!AUTO.equals(saved)) {
                interval = QUALITY.equals(saved) ? 600L : BALANCED.equals(saved) ? 900L : 1500L;
            } else {
                String runtime = autoRuntime(context, sharedPreferences);
                interval = AUTO_REALTIME.equals(runtime) ? 600L
                        : AUTO_BALANCED.equals(runtime) ? 900L
                        : AUTO_EXTREME.equals(runtime) ? 2500L : 1500L;
            }
        }

        int thermalStatus = thermalStatus(context);
        if (isBatterySaver(context) || thermalStatus >= severeThermalStatus()) {
            interval = Math.max(interval, 3000L);
        } else if (thermalStatus >= moderateThermalStatus()) {
            interval = Math.max(interval, 1200L);
        }

        if (!RuntimeState.isInteractive(context)) {
            interval = Math.max(interval, 1500L);
        }

        return Math.max(400L, Math.min(interval, 4000L));
    }'''
app = sub_once(
    app,
    r'    static long livePollInterval\(Context context, SharedPreferences sharedPreferences\) \{.*?\n    \}\n\n(?=    static long motionDuration)',
    live_method + "\n\n",
    "livePollInterval",
)

resume_old = '''                @Override public void onActivityResumed(Activity activity) {
                    if (activity instanceof HcfMainActivities.MainActivity) {
                        try {
                            SetupCenter.installDrawerEntry((HcfMainActivities.MainActivity) activity);'''
resume_new = '''                @Override public void onActivityResumed(Activity activity) {
                    if (activity instanceof HcfMainActivities.MainActivity) {
                        try {
                            BatteryOptimizationHelper.maybeRequest(activity);
                        } catch (Throwable error) {
                            AppLogger.warn(App.this, "battery_optimization_request", error.getClass().getSimpleName());
                        }
                        try {
                            SetupCenter.installDrawerEntry((HcfMainActivities.MainActivity) activity);'''
app = replace_once(app, resume_old, resume_new, "MainActivity battery optimization hook")

battery_helper = r'''// ---- BatteryOptimizationHelper.java ----
final class BatteryOptimizationHelper {
    private static final String PREF_REQUEST_SHOWN = "battery_optimization_request_shown";
    private static final String PREF_WARNING_LOGGED = "battery_optimization_warning_logged";
    private static final String PREF_DENIED = "battery_optimization_denied";

    static boolean isIgnoring(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 23) {
            return true;
        }
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return powerManager != null && powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void maybeRequest(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < 23) {
            return;
        }
        SharedPreferences prefs = activity.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("aggressive_realtime", true)) {
            return;
        }

        if (isIgnoring(activity)) {
            prefs.edit()
                    .putBoolean(PREF_DENIED, false)
                    .putBoolean(PREF_WARNING_LOGGED, false)
                    .apply();
            return;
        }

        if (!prefs.getBoolean(PREF_REQUEST_SHOWN, false)) {
            prefs.edit().putBoolean(PREF_REQUEST_SHOWN, true).apply();
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
                AppLogger.info(activity, "battery_optimization_request", "requested • aggressive realtime");
            } catch (Throwable error) {
                prefs.edit().putBoolean(PREF_DENIED, true).apply();
                AppLogger.warn(activity, "battery_optimization_request", "unavailable | " + error.getClass().getSimpleName());
            }
            return;
        }

        // A prior request was shown and the app is still optimized: treat it as
        // denied/revoked and use the slightly less aggressive (still <=5s) path.
        prefs.edit().putBoolean(PREF_DENIED, true).apply();
        if (!prefs.getBoolean(PREF_WARNING_LOGGED, false)) {
            prefs.edit().putBoolean(PREF_WARNING_LOGGED, true).apply();
            AppLogger.warn(activity, "battery_optimization", "not exempt • using conservative realtime background intervals");
        }
    }

    private BatteryOptimizationHelper() {}
}


'''
marker = "// ---- RuntimeDiagnostics.java ----"
if "final class BatteryOptimizationHelper" not in app:
    app = replace_once(app, marker, battery_helper + marker, "BatteryOptimizationHelper insertion")

app_path.write_text(app, encoding="utf-8")

# -----------------------------------------------------------------------------
# HcfForumEngine.java: typed HTTP status, Pusher direct path, throttle metadata
# -----------------------------------------------------------------------------
forum_path = SRC / "HcfForumEngine.java"
forum = forum_path.read_text(encoding="utf-8")

client_header_old = '''final class ForumNotificationClient {
    private static final int CONNECT_TIMEOUT_MS = 4500;
    private static final int MAX_BODY_CHARS = 700000;
    private static final int READ_TIMEOUT_MS = 4500;

    static final class Alert {'''
client_header_new = '''final class ForumNotificationClient {
    private static final int CONNECT_TIMEOUT_MS = 4500;
    private static final int MAX_BODY_CHARS = 700000;
    private static final int READ_TIMEOUT_MS = 4500;

    static final class HttpStatusException extends IOException {
        final int statusCode;
        final long retryAfterMs;

        HttpStatusException(int statusCode, long retryAfterMs) {
            super("HTTP " + statusCode);
            this.statusCode = statusCode;
            this.retryAfterMs = Math.max(0L, retryAfterMs);
        }
    }

    static final class Alert {'''
forum = replace_once(forum, client_header_old, client_header_new, "ForumNotificationClient HttpStatusException")

fetch_latest_old = '''        try {
            str2 = get(context, trustedBase, "api/notifications?include=fromUser,subject&page%5Blimit%5D=" + max, "Details");
        } catch (Throwable unused) {
            str2 = get(context, trustedBase, "api/notifications?page%5Blimit%5D=" + max, "DetailsFallback");
        }'''
fetch_latest_new = '''        try {
            str2 = get(context, trustedBase, "api/notifications?include=fromUser,subject&page%5Blimit%5D=" + max, "Details");
        } catch (Throwable error) {
            if (error instanceof HttpStatusException) {
                throw (HttpStatusException) error;
            }
            str2 = get(context, trustedBase, "api/notifications?page%5Blimit%5D=" + max, "DetailsFallback");
        }'''
forum = replace_once(forum, fetch_latest_old, fetch_latest_new, "fetchLatest status propagation")

get_old = '''            int responseCode = httpURLConnection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IllegalStateException("HTTP " + responseCode);
            }
            return read(httpURLConnection.getInputStream());'''
get_new = '''            int responseCode = httpURLConnection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new HttpStatusException(responseCode, retryAfterMillis(httpURLConnection));
            }
            return read(httpURLConnection.getInputStream());'''
forum = replace_once(forum, get_old, get_new, "typed notification HTTP errors")

read_marker = '''    private static String read(InputStream inputStream) throws Exception {'''
retry_helper = '''    private static long retryAfterMillis(HttpURLConnection connection) {
        if (connection == null) return 0L;
        String value = connection.getHeaderField("Retry-After");
        if (value == null || value.trim().isEmpty()) return 0L;
        try {
            long seconds = Long.parseLong(value.trim());
            return Math.min(300000L, Math.max(0L, seconds * 1000L));
        } catch (Throwable ignored) {
            return 0L;
        }
    }

'''
forum = replace_once(forum, read_marker, retry_helper + read_marker, "Retry-After parser")

inner_detail_old = '''                } catch (Throwable th) {
                    NotificationHelper.postGenericDelta(context, recordForumNotificationCount, str);
                    AppLogger.warn(context, "notification_detail", th.getClass().getSimpleName() + " | generic-fallback");
                }'''
inner_detail_new = '''                } catch (Throwable th) {
                    if (th instanceof ForumNotificationClient.HttpStatusException) {
                        throw (ForumNotificationClient.HttpStatusException) th;
                    }
                    NotificationHelper.postGenericDelta(context, recordForumNotificationCount, str);
                    AppLogger.warn(context, "notification_detail", th.getClass().getSimpleName() + " | generic-fallback");
                }'''
forum = replace_once(forum, inner_detail_old, inner_detail_new, "detail HTTP status propagation")

push_old = '''        if ("notification".equals(event)) {
            HcfNotificationEngine.InstantNotificationService.requestImmediateSync(this.app);
        }
        if (eventAffectsCurrentRoute(event, data)) {'''
push_new = '''        if ("notification".equals(event)) {
            NotificationHelper.postFromPushPayload(this.app, data);
            HcfNotificationEngine.InstantNotificationService.requestImmediateSync(this.app);
        }
        if (eventAffectsCurrentRoute(event, data)) {'''
forum = replace_once(forum, push_old, push_new, "Pusher native notification path")

forum_path.write_text(forum, encoding="utf-8")

# -----------------------------------------------------------------------------
# HcfNotificationEngine.java: FGS reliability, dedupe, screen/network hooks, stub
# -----------------------------------------------------------------------------
notification_path = SRC / "HcfNotificationEngine.java"
notifications = notification_path.read_text(encoding="utf-8")

if "import android.content.IntentFilter;" not in notifications:
    notifications = replace_once(notifications, "import android.content.Intent;\n", "import android.content.Intent;\nimport android.content.IntentFilter;\n", "IntentFilter import")
if "import java.util.Map;" not in notifications:
    notifications = replace_once(notifications, "import java.util.List;\n", "import java.util.List;\nimport java.util.Map;\n", "Map import")
if "import org.json.JSONObject;" not in notifications:
    notifications = replace_once(notifications, "import org.json.JSONException;\n", "import org.json.JSONException;\nimport org.json.JSONObject;\n", "JSONObject import")

constants_old = '''        static final String ACTION_SYNC_NOW = "com.harleytg.forum.dev.SYNC_NOTIFICATIONS_NOW";
        private static final long FAILURE_MAX_MS = 8000L;
        private static final long FAILURE_MIN_MS = 500L;
        static final int SERVICE_NOTIFICATION_ID = 41070;'''
constants_new = '''        static final String ACTION_SYNC_NOW = "com.harleytg.forum.dev.SYNC_NOTIFICATIONS_NOW";
        private static final long DEFAULT_NEXT_DELAY_MS = 2000L;
        private static final long FAILURE_MAX_MS = 8000L;
        private static final long FAILURE_MIN_MS = 1000L;
        private static final long THROTTLE_MIN_MS = 5000L;
        private static final long THROTTLE_MAX_MS = 120000L;
        static final int SERVICE_NOTIFICATION_ID = 41070;'''
notifications = replace_once(notifications, constants_old, constants_new, "service constants")

fields_old = '''        private ConnectivityManager.NetworkCallback networkCallback;
        private boolean networkCallbackRegistered;
        private volatile boolean running;'''
fields_new = '''        private ConnectivityManager.NetworkCallback networkCallback;
        private boolean networkCallbackRegistered;
        private BroadcastReceiver screenOnReceiver;
        private boolean screenOnReceiverRegistered;
        private volatile boolean running;'''
notifications = replace_once(notifications, fields_old, fields_new, "screen receiver fields")

start_old = '''        static void start(Context context) {
            if (context == null) return;
            if (!hasSession(context) || NotificationHelper.silencePassiveEnabled(context)) {
                stop(context);
                return;
            }
            startWithAction(context, null);
        }

        static void requestImmediateSync(Context context) {
            if (context == null || !hasSession(context)) return;
            if (NotificationHelper.silencePassiveEnabled(context)) requestOneShotSync(context);
            else startWithAction(context, ACTION_SYNC_NOW);
        }'''
start_new = '''        static void start(Context context) {
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
        }'''
notifications = replace_once(notifications, start_old, start_new, "service start/immediate policy")

start_with_action_old = '''            if (NotificationHelper.silencePassiveEnabled(context)) {
                if (ACTION_SYNC_NOW.equals(action)) requestOneShotSync(context);
                else stop(context);
                return;
            }
            try {'''
notifications = replace_once(notifications, start_with_action_old, "            try {", "remove silent-mode FGS shutdown")

stop_old = '''        static void stop(Context context) {
            if (context == null) return;
            try { context.stopService(new Intent(context, InstantNotificationService.class)); }
            catch (Throwable t) { AppLogger.warn(context, "instant_notification_service", "stop | " + t.getClass().getSimpleName()); }
        }'''
stop_new = '''        static void stop(Context context) {
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
        }'''
notifications = replace_once(notifications, stop_old, stop_new, "service stop + auth clear")

run_one_shot_old = '''            } catch (JSONException e) {
                // A transient 2xx response can occasionally contain HTML or an
                // unexpected payload instead of the Flarum JSON API object. Skip
                // this one-shot attempt and let the next sync retry normally.
                AppLogger.info(context, "instant_notification_service", "one-shot skipped • invalid notification API payload");
            } catch (Throwable t) {'''
run_one_shot_new = '''            } catch (ForumNotificationClient.HttpStatusException e) {
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
            } catch (Throwable t) {'''
notifications = replace_once(notifications, run_one_shot_old, run_one_shot_new, "one-shot status handling")

notifications = replace_once(notifications, "            registerNetworkCallback();\n", "            registerNetworkCallback();\n            registerScreenOnReceiver();\n", "screen receiver registration")

adaptive_method = r'''        private void performAdaptiveSync() {
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
        }'''
notifications = sub_once(
    notifications,
    r'        private void performAdaptiveSync\(\) \{.*?\n        \}\n\n(?=        private void registerNetworkCallback)',
    adaptive_method + "\n\n",
    "performAdaptiveSync",
)

network_old = '''                    @Override public void onAvailable(Network network) {
                        if (running) {
                            failures = 0;
                            immediateRequested = true;
                            scheduleNext(0L);
                        }
                    }'''
network_new = '''                    @Override public void onAvailable(Network network) {
                        if (running) {
                            failures = 0;
                            requestImmediateSync(InstantNotificationService.this);
                        }
                    }'''
notifications = replace_once(notifications, network_old, network_new, "network-regain immediate sync")

unregister_marker = '''        private void unregisterNetworkCallback() {'''
screen_methods = '''        private void registerScreenOnReceiver() {
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

'''
notifications = replace_once(notifications, unregister_marker, screen_methods + unregister_marker, "screen receiver methods")

on_destroy_old = '''        @Override
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
        }'''
on_destroy_new = '''        @Override
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
        }'''
notifications = replace_once(notifications, on_destroy_old, on_destroy_new, "service lifecycle logging")

job_sync_old = '''            String host = prefs.getString("active_host", "forum.harleytg.com");
            ForumNotificationSync.perform(this, ForumUrlRouter.isForumHost(host) ? host : "forum.harleytg.com", userId.trim(), "fallback-job");'''
job_sync_new = '''            String host = prefs.getString("active_host", "forum.harleytg.com");
            try {
                ForumNotificationSync.perform(this, ForumUrlRouter.isForumHost(host) ? host : "forum.harleytg.com", userId.trim(), "fallback-job");
            } catch (ForumNotificationClient.HttpStatusException http) {
                if (http.statusCode == 401) {
                    InstantNotificationService.clearSessionForAuthFailure(this, "fallback-job");
                }
                throw http;
            }'''
notifications = replace_once(notifications, job_sync_old, job_sync_new, "job HTTP status handling")

scheduler_old = '''            String userId = prefs.getString("session_user_id", "");
            boolean silenceForegroundStatus = prefs.getBoolean("silence_background_service_notification", false);

            if (userId != null && !userId.trim().isEmpty()) {
                if (silenceForegroundStatus) {
                    // Android requires a visible notification for a foreground service.
                    // Honor the user's silence switch by stopping that foreground service.
                    // Foreground WebSocket events can still request silent one-shot syncs,
                    // while JobScheduler remains the background fallback.
                    HcfNotificationEngine.InstantNotificationService.stop(context);
                    AppLogger.info(context, "notification_sync_mode", "silent fallback • foreground service stopped");
                } else {
                    HcfNotificationEngine.InstantNotificationService.start(context);
                    AppLogger.info(context, "notification_sync_mode", "foreground live sync");
                }
            } else {
                HcfNotificationEngine.InstantNotificationService.stop(context);
                AppLogger.info(context, "notification_sync_mode", "waiting for signed-in session");
            }'''
scheduler_new = '''            String userId = prefs.getString("session_user_id", "");

            if (userId != null && !userId.trim().isEmpty()) {
                // Reliability rule: signed-in + background sync enabled always keeps
                // the legal foreground service running with its required ongoing notification.
                HcfNotificationEngine.InstantNotificationService.start(context);
                AppLogger.info(context, "notification_sync_mode", "foreground live sync • signed-in");
            } else {
                HcfNotificationEngine.InstantNotificationService.stop(context, "signed-out");
                AppLogger.info(context, "notification_sync_mode", "waiting for signed-in session");
            }'''
notifications = replace_once(notifications, scheduler_old, scheduler_new, "scheduler service policy")
notifications = replace_once(
    notifications,
    '''                HcfNotificationEngine.InstantNotificationService.stop(context);\n                cancel(context);''',
    '''                HcfNotificationEngine.InstantNotificationService.stop(context, "background-sync-disabled");\n                cancel(context);''',
    "scheduler disabled stop reason",
)

# Replace detailed delivery with a push-aware dedupe reconciliation.
detailed_method = r'''    static synchronized int deliverDetailedAlerts(Context context, List<ForumNotificationClient.Alert> list, int expected, String host, String source) {
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
                    postInternal(context,
                            trim(alert.title, 120, "Harley's Clan Forum"),
                            trim(alert.body, 500, "You have a new forum notification."),
                            validatedForumUri(alert.url),
                            603979776 | (alert.id.hashCode() & 268435455),
                            true);
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
    }'''
notifications = sub_once(
    notifications,
    r'    static synchronized int deliverDetailedAlerts\(Context context, List<ForumNotificationClient\.Alert> list, int i, String str, String str2\) \{.*?\n    \}\n\n(?=    static void postGenericDelta)',
    detailed_method + "\n\n",
    "deliverDetailedAlerts",
)

post_marker = '''    static void post(Context context, String str, String str2, String str3) {'''
push_helpers = r'''    static boolean postFromPushPayload(Context context, JSONObject data) {
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

'''
notifications = replace_once(notifications, post_marker, push_helpers + post_marker, "push/FCM notification helpers")

claim_marker = '''    private static Set<String> deliveredIds(String str) {'''
claim_helper = r'''    private static boolean claimDeliveredId(Context context, String id) {
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

'''
notifications = replace_once(notifications, claim_marker, claim_helper + claim_marker, "notification ID claim helper")

notification_path.write_text(notifications, encoding="utf-8")

# -----------------------------------------------------------------------------
# Settings copy: the foreground-service notification is mandatory for reliability.
# -----------------------------------------------------------------------------
sub_path = SRC / "HcfSubActivities.java"
sub = sub_path.read_text(encoding="utf-8")
sub = replace_once(
    sub,
    'Switch silence = target(toggle("Disable HCF Silent Alerts", prefs.getBoolean("silence_background_service_notification", false)), "silence_hcf_silent_alerts");',
    'Switch silence = target(toggle("Silence optional HCF status alerts", prefs.getBoolean("silence_background_service_notification", false)), "silence_hcf_silent_alerts");',
    "silent alerts toggle label",
)
sub = replace_once(
    sub,
    'Toast.makeText(this, checked ? "HCF Silent Alerts disabled. Real HCF Alerts stay on; background delivery may be delayed." : "HCF Silent Alerts enabled • live background delivery available.", Toast.LENGTH_LONG).show();',
    'Toast.makeText(this, checked ? "Optional silent status alerts hidden. The required live-service notification stays on." : "Optional HCF status alerts enabled.", Toast.LENGTH_LONG).show();',
    "silent alerts toast",
)
sub = replace_once(
    sub,
    'card.addView(text("This never disables HCF Alerts. When this switch is ON, the continuous foreground sync service stops because Android requires a visible service notification; fallback background checks remain scheduled.", 10, getColor(R.color.hcf_muted)));',
    'card.addView(text("This never disables real HCF Alerts. Android requires an ongoing notification for reliable foreground-service delivery, so the live sync service stays running while signed in and background sync is enabled.", 10, getColor(R.color.hcf_muted)));',
    "silent alerts explanatory copy",
)
sub_path.write_text(sub, encoding="utf-8")

# -----------------------------------------------------------------------------
# Manifest: battery-optimization request permission. Existing services/receivers stay private.
# -----------------------------------------------------------------------------
manifest_path = ROOT / "AndroidManifest.xml"
manifest = manifest_path.read_text(encoding="utf-8")
if 'android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS' not in manifest:
    manifest = replace_once(
        manifest,
        '    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>\n',
        '    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>\n    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>\n',
        "battery optimization manifest permission",
    )
manifest_path.write_text(manifest, encoding="utf-8")

# Sanity assertions for requested constants and security posture.
assert 'private static final long FIRST_POLL_MS = 100L;' in forum
assert 'private static final long IDLE_ROUTE_POLL_MS = 1500L;' in forum
assert 'private static final long RECONNECT_MIN_MS = 300L;' in forum
assert 'private static final long RECONNECT_MAX_MS = 8000L;' in forum
assert 'android:exported="false" android:stopWithTask="false" android:foregroundServiceType="specialUse"' in manifest
assert 'android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS' in manifest

print("Off-app notification reliability patch applied.")
