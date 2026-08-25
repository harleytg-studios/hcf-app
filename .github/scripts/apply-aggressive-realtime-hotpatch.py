from pathlib import Path
import re


def replace_once(text, old, new, label):
    if old not in text:
        raise RuntimeError(f"Missing expected block: {label}")
    return text.replace(old, new, 1)


app_path = Path("source code/src/com/harleytg/forum/HcfApplication.java")
app = app_path.read_text(encoding="utf-8")

startup_old = '''            super.onCreate();

            // Theme must run before any activity attaches / draws windowBackground.'''
startup_new = '''            super.onCreate();

            // Dev/Beta realtime policy: latency wins over battery life.
            try {
                getSharedPreferences("hcf_app", 0).edit()
                        .putBoolean("aggressive_realtime", true)
                        .putString("performance_profile", PerformanceProfile.QUALITY)
                        .apply();
            } catch (Throwable ignored) {
            }

            // Theme must run before any activity attaches / draws windowBackground.'''
app = replace_once(app, startup_old, startup_new, "dev startup realtime policy")

notification_method = '''    static long notificationPollInterval(Context context, SharedPreferences sharedPreferences) {
        final boolean aggressiveRealtime = sharedPreferences == null
                || sharedPreferences.getBoolean("aggressive_realtime", true);
        final boolean foreground = RuntimeState.isForeground();
        final boolean interactive = context == null || RuntimeState.isInteractive(context);

        long interval;
        if (aggressiveRealtime) {
            interval = foreground ? 400L : 1500L;
        } else {
            String saved = saved(sharedPreferences);
            if (!AUTO.equals(saved)) {
                interval = QUALITY.equals(saved) ? 500L : BALANCED.equals(saved) ? 1000L : 1800L;
            } else {
                String runtime = context == null ? AUTO_BALANCED : autoRuntime(context, sharedPreferences);
                interval = AUTO_REALTIME.equals(runtime) ? 500L
                        : AUTO_BALANCED.equals(runtime) ? 1000L
                        : AUTO_EXTREME.equals(runtime) ? 4000L : 1800L;
            }

            if (!foreground) {
                long backgroundDurationMs = RuntimeState.backgroundDurationMs();
                interval = backgroundDurationMs <= 180000L
                        ? Math.min(interval, 1500L)
                        : Math.min(Math.max(interval, 1500L), 4000L);
            }
        }

        if (context != null) {
            int thermalStatus = thermalStatus(context);
            if (thermalStatus >= severeThermalStatus()) {
                interval = Math.max(interval, foreground ? 1500L : 3500L);
            } else if (thermalStatus >= moderateThermalStatus()) {
                interval = Math.max(interval, foreground ? 800L : 2200L);
            }

            if (isBatterySaver(context)) {
                interval = Math.max(interval, foreground ? 1200L : 3000L);
            }

            int batteryPercent = batteryPercent(context);
            if (!isCharging(context) && batteryPercent >= 0 && batteryPercent <= 10) {
                interval = Math.max(interval, foreground ? 1500L : 4000L);
            }
        }

        if (!interactive) {
            interval = Math.max(interval, 2500L);
            interval = Math.min(interval, 3000L);
        }

        return Math.max(300L, Math.min(interval, 8000L));
    }'''

app, count = re.subn(
    r'    static long notificationPollInterval\(Context context, SharedPreferences sharedPreferences\) \{.*?\n    \}\n\n(?=    static long livePollInterval)',
    notification_method + "\n\n",
    app,
    count=1,
    flags=re.S,
)
if count != 1:
    raise RuntimeError("Could not replace notificationPollInterval")

live_method = '''    static long livePollInterval(Context context, SharedPreferences sharedPreferences) {
        final boolean aggressiveRealtime = sharedPreferences == null
                || sharedPreferences.getBoolean("aggressive_realtime", true);
        final boolean foreground = RuntimeState.isForeground();

        long interval;
        if (aggressiveRealtime) {
            interval = foreground ? 450L : 1000L;
        } else {
            String saved = saved(sharedPreferences);
            if (!AUTO.equals(saved)) {
                interval = QUALITY.equals(saved) ? 500L : BALANCED.equals(saved) ? 750L : 1200L;
            } else {
                String runtime = context == null ? AUTO_BALANCED : autoRuntime(context, sharedPreferences);
                interval = AUTO_REALTIME.equals(runtime) ? 500L
                        : AUTO_BALANCED.equals(runtime) ? 800L
                        : AUTO_EXTREME.equals(runtime) ? 2000L : 1200L;
            }
        }

        if (context != null) {
            int thermalStatus = thermalStatus(context);
            if (thermalStatus >= severeThermalStatus() || isBatterySaver(context)) {
                interval = Math.max(interval, 2000L);
            } else if (thermalStatus >= moderateThermalStatus()) {
                interval = Math.max(interval, 1000L);
            }
            if (!RuntimeState.isInteractive(context)) {
                interval = Math.max(interval, 1500L);
            }
        }

        long sinceLastInteractionMs = RuntimeState.sinceLastInteractionMs();
        if (sinceLastInteractionMs >= 60000L) {
            interval = Math.max(interval, 1500L);
        } else if (sinceLastInteractionMs >= 12000L) {
            interval = Math.max(interval, 750L);
        }

        return Math.max(400L, Math.min(interval, 4000L));
    }'''

app, count = re.subn(
    r'    static long livePollInterval\(Context context, SharedPreferences sharedPreferences\) \{.*?\n    \}\n\n(?=    static long motionDuration)',
    live_method + "\n\n",
    app,
    count=1,
    flags=re.S,
)
if count != 1:
    raise RuntimeError("Could not replace livePollInterval")

app_path.write_text(app, encoding="utf-8")

forum_path = Path("source code/src/com/harleytg/forum/HcfForumEngine.java")
forum = forum_path.read_text(encoding="utf-8")
for old, new, label in [
    ("private static final long FIRST_POLL_MS = 250L;", "private static final long FIRST_POLL_MS = 100L;", "FIRST_POLL_MS"),
    ("private static final long IDLE_ROUTE_POLL_MS = 5000L;", "private static final long IDLE_ROUTE_POLL_MS = 1500L;", "IDLE_ROUTE_POLL_MS"),
    ("private static final long RECONNECT_MAX_MS = 30000L;", "private static final long RECONNECT_MAX_MS = 8000L;", "RECONNECT_MAX_MS"),
    ("private static final long RECONNECT_MIN_MS = 1000L;", "private static final long RECONNECT_MIN_MS = 300L;", "RECONNECT_MIN_MS"),
    ("schedulePushedRefresh(60L);", "schedulePushedRefresh(0L);", "push refresh delay"),
    ("scheduleFallback(Math.min(15000L, 2000L << Math.min(Math.max(0, this.failures - 1), 3)));", "scheduleFallback(Math.min(4000L, 500L << Math.min(Math.max(0, this.failures - 1), 3)));", "fallback failure backoff"),
]:
    forum = replace_once(forum, old, new, label)

socket_connected_old = '''        AppLogger.info(this.app, "live_update_socket", "connected • push primary");
        schedulePushedRefresh(0L);'''
socket_connected_new = '''        AppLogger.info(this.app, "live_update_socket", "connected • push primary");
        HcfNotificationEngine.InstantNotificationService.requestImmediateSync(this.app);
        schedulePushedRefresh(0L);'''
forum = replace_once(forum, socket_connected_old, socket_connected_new, "socket connected immediate sync")
forum_path.write_text(forum, encoding="utf-8")

notifications_path = Path("source code/src/com/harleytg/forum/HcfNotificationEngine.java")
notifications = notifications_path.read_text(encoding="utf-8")
for old, new, label in [
    ("private static final long FAILURE_MAX_MS = 60000L;", "private static final long FAILURE_MAX_MS = 8000L;", "notification failure max"),
    ("private static final long FAILURE_MIN_MS = 2500L;", "private static final long FAILURE_MIN_MS = 500L;", "notification failure min"),
    ("long nextDelay = 15000L;", "long nextDelay = 1500L;", "notification default delay"),
]:
    notifications = replace_once(notifications, old, new, label)
notifications_path.write_text(notifications, encoding="utf-8")

main_path = Path("source code/src/com/harleytg/forum/HcfMainActivities.java")
main = main_path.read_text(encoding="utf-8")
resume_old = '''        protected void onResume() {
            WebView webView;
            super.onResume();
            this.appliedThemeSignature = ThemeManager.signature(this);
            resumeUpdateInstallPermissionIfNeeded();'''
resume_new = '''        protected void onResume() {
            WebView webView;
            super.onResume();
            HcfNotificationEngine.InstantNotificationService.requestImmediateSync(this);
            if (this.liveUpdater != null) {
                this.liveUpdater.poke();
            }
            this.appliedThemeSignature = ThemeManager.signature(this);
            resumeUpdateInstallPermissionIfNeeded();'''
main = replace_once(main, resume_old, resume_new, "MainActivity onResume realtime kick")
main_path.write_text(main, encoding="utf-8")

print("Aggressive realtime hotpatch applied successfully.")
