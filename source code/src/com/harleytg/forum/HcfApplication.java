package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Base64;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

public final class HcfApplication {
    private HcfApplication() {}

    // ---- HcfApplication.java ----
    /**
     * Application entry. Applies resolved night mode as early as possible so the
     * first activity frame does not flash the opposite theme (light splash on a
     * dark preference, etc.).
     */
    public static final class App extends Application {
        @Override
        public void onCreate() {
            super.onCreate();

            // Theme must run before any activity attaches / draws windowBackground.
            try {
                ThemeManager.applyToApplication(this);
            } catch (Throwable ignored) {
            }

            RuntimeState.install(this);
            RemoteDomainConfig.initialize(this);

            // The old MainActivity permission dialog is now legacy. Set its guard
            // before MainActivity is created so the delayed 700 ms AlertDialog can
            // never appear. The versioned Setup Center owns onboarding from here on.
            try {
                getSharedPreferences(AppPrefs.FILE, 0).edit()
                        .putBoolean(AppPrefs.PERMISSION_ONBOARDING_DONE, true)
                        .apply();
            } catch (Throwable ignored) {
            }

            registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity activity, Bundle state) {
                    if (activity instanceof HcfMainActivities.MainActivity) {
                        try {
                            SetupCenter.maybeLaunchForMainActivity((HcfMainActivities.MainActivity) activity, state);
                        } catch (Throwable error) {
                            AppLogger.error(App.this, "app_setup_lifecycle", error.getClass().getSimpleName());
                        }
                    }
                }

                @Override public void onActivityResumed(Activity activity) {
                    if (activity instanceof HcfMainActivities.MainActivity) {
                        try {
                            SetupCenter.installDrawerEntry((HcfMainActivities.MainActivity) activity);
                        } catch (Throwable error) {
                            AppLogger.warn(App.this, "app_setup_drawer", error.getClass().getSimpleName());
                        }
                    }
                }

                @Override public void onActivityStarted(Activity activity) {}
                @Override public void onActivityPaused(Activity activity) {}
                @Override public void onActivityStopped(Activity activity) {}
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
                @Override public void onActivityDestroyed(Activity activity) {}
            });

            final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override public void uncaughtException(Thread thread, Throwable error) {
                    App.this.handleUncaught(previous, thread, error);
                }
            });

            try {
                UiPreferences.migrate(this);
            } catch (Throwable ignored) {
            }
            try {
                AppLogger.info(this, "app_start", BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE
                        + ") | SDK " + Build.VERSION.SDK_INT + " | " + Build.MANUFACTURER + " " + Build.MODEL
                        + " | theme=" + ThemeManager.mode(this) + "/" + ThemeManager.webColorScheme(this));
            } catch (Throwable ignored) {
            }

            AppExecutors.main().postDelayed(new Runnable() {
                @Override public void run() {
                    startDeferredWork();
                }
            }, 3500L);
        }

        private void handleUncaught(Thread.UncaughtExceptionHandler previous, Thread thread, Throwable error) {
            try {
                TelemetryService.captureCrash(this, thread, error);
            } catch (Throwable ignored) {
            }
            try {
                AppLogger.crash(this, error);
            } catch (Throwable ignored) {
            }
            if (previous != null) previous.uncaughtException(thread, error);
        }

        private void startDeferredWork() {
            AppExecutors.disk().execute(new Runnable() {
                @Override public void run() {
                    try {
                        AppUpdateDownloader.cleanupIfCurrentVersionWasDownloaded(App.this);
                    } catch (Throwable ignored) {
                    }
                }
            });
            AppExecutors.network().execute(new Runnable() {
                @Override public void run() {
                    try {
                        TelemetryService.heartbeat(App.this);
                    } catch (Throwable ignored) {
                    }
                }
            });
        }

        @Override
        public void onTrimMemory(int level) {
            super.onTrimMemory(level);
            RuntimeState.noteTrimMemory(level);
        }

        @Override
        public void onLowMemory() {
            super.onLowMemory();
            RuntimeState.noteTrimMemory(80);
        }
    }
}

// ---- AppExecutors.java ----
/* loaded from: classes.dex */
final class AppExecutors {
    private static final int CPU_COUNT;
    private static final ExecutorService DISK;
    private static final Handler MAIN;
    private static final ExecutorService NETWORK;
    private static final int NETWORK_THREADS;
    private static final ScheduledExecutorService SCHEDULER;
    private static final ExecutorService SERIAL;

    static {
        int max = Math.max(2, Runtime.getRuntime().availableProcessors());
        CPU_COUNT = max;
        int max2 = Math.max(2, Math.min(4, max));
        NETWORK_THREADS = max2;
        NETWORK = Executors.newFixedThreadPool(max2, new ThreadFactory() {
            @Override
            public final Thread newThread(Runnable runnable) {
                return AppExecutors.lambda$static$0(runnable);
            }
        });
        DISK = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public final Thread newThread(Runnable runnable) {
                return AppExecutors.lambda$static$1(runnable);
            }
        });
        SERIAL = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public final Thread newThread(Runnable runnable) {
                return AppExecutors.lambda$static$2(runnable);
            }
        });
        SCHEDULER = Executors.newScheduledThreadPool(2, new ThreadFactory() {
            @Override
            public final Thread newThread(Runnable runnable) {
                return AppExecutors.lambda$static$3(runnable);
            }
        });
        MAIN = new Handler(Looper.getMainLooper());
    }

    static /* synthetic */ Thread lambda$static$0(Runnable runnable) {
        Thread thread = new Thread(runnable, "hcf-network");
        thread.setPriority(5);
        return thread;
    }

    static /* synthetic */ Thread lambda$static$1(Runnable runnable) {
        Thread thread = new Thread(runnable, "hcf-disk");
        thread.setPriority(4);
        return thread;
    }

    static /* synthetic */ Thread lambda$static$2(Runnable runnable) {
        Thread thread = new Thread(runnable, "hcf-serial");
        thread.setPriority(5);
        return thread;
    }

    static /* synthetic */ Thread lambda$static$3(Runnable runnable) {
        Thread thread = new Thread(runnable, "hcf-scheduler");
        thread.setPriority(5);
        return thread;
    }

    static ExecutorService network() {
        return NETWORK;
    }

    static ExecutorService disk() {
        return DISK;
    }

    static ExecutorService serial() {
        return SERIAL;
    }

    static ScheduledExecutorService scheduler() {
        return SCHEDULER;
    }

    static Handler main() {
        return MAIN;
    }

    private AppExecutors() {}
}

// ---- BuildInfo.java ----
/** Build identity for the Dev/Beta Harley's Clan Forum Android app. */
final class BuildInfo {
    static final boolean ALLOW_UPDATE_CHANNEL_SWITCH = false;
    static final String APK_FILE_NAME = "HCF-Beta-v10000098.apk";
    static final String BRAND = "Harley's Studios";
    static final String CHANNEL = "Dev";
    static final String DEFAULT_UPDATE_CHANNEL = "dev";
    static final String DEVELOPMENT_BUILD_LABEL = "Harley's Clan Forum v1.0 [Development Build / Beta]";
    static final boolean ENABLE_DEV_TEST_MENU = true;
    static final boolean FCM_CONFIGURED = false;
    static final boolean FIREBASE_WEB_CONFIG_BUNDLED = true;
    static final int INTERNAL_BUILD = 118;
    static final String META_LINE = "1.0 • Development / Beta";
    static final String SESSION_CLIENT = "Harley's Clan Forum App";
    static final String UPDATE_DEV_BRANCH = "dev";
    static final String UPDATE_REPOSITORY = "markhitchk/hcf-app";
    static final String UPDATE_STABLE_BRANCH = "stable";
    static final String USER_AGENT_MARKER = "HarleysClanForumApp/1.0 Build/10000098";
    static final String VERSION = "1.0";
    static final int VERSION_CODE = 10000098;
    static final String VERSION_BUILD_LINE = VERSION + " • Development / Beta • Build " + VERSION_CODE;
    static final String VERSION_CODE_SCHEME = "major-release-v1";
    static final String VERSION_TAG = "v1.0";
    static final String REMOTE_DOMAIN_CONFIG = "https://raw.githubusercontent.com/markhitchk/hcf-app/main/configs/domains.config";

    static String installedVersionName() {
        return VERSION + " (" + VERSION_CODE + ")";
    }

    static String userAgent(String baseUserAgent) {
        String base = baseUserAgent == null ? "" : baseUserAgent.trim();
        if (base.contains(USER_AGENT_MARKER)) return base;
        return base.isEmpty() ? USER_AGENT_MARKER : base + " " + USER_AGENT_MARKER + " NativeApp";
    }

    private BuildInfo() {}
}

// ---- PerformanceProfile.java ----
/* loaded from: classes.dex */
final class PerformanceProfile {
    static final String AUTO = "auto";
    private static final String AUTO_BALANCED = "auto_balanced";
    private static final String AUTO_EXTREME = "auto_extreme";
    private static final String AUTO_PERFORMANCE = "auto_performance";
    private static final String AUTO_REALTIME = "auto_realtime";
    static final String BALANCED = "balanced";
    private static final long FOUR_GB = 4294967296L;
    private static final long HEALTHY_AVAILABLE_BYTES = 805306368;
    private static final long LOW_AVAILABLE_BYTES = 402653184;
    static final String PERFORMANCE = "performance";
    static final String QUALITY = "quality";
    private static final long THREE_GB = 3221225472L;

    private static int moderateThermalStatus() { return 2; }
    private static int severeThermalStatus() { return 3; }

    static String saved(SharedPreferences sharedPreferences) {
        if (sharedPreferences == null) return AUTO;
        String string = sharedPreferences.getString("performance_profile", "");
        return (PERFORMANCE.equals(string) || BALANCED.equals(string) || QUALITY.equals(string) || AUTO.equals(string)) ? string : AUTO;
    }

    static void save(SharedPreferences sharedPreferences, String str) {
        if (sharedPreferences == null) return;
        String normalize = normalize(str);
        sharedPreferences.edit().putString("performance_profile", normalize).putBoolean("performance_mode", PERFORMANCE.equals(normalize)).apply();
    }

    static String resolve(Context context, SharedPreferences sharedPreferences) {
        String saved = saved(sharedPreferences);
        if (!AUTO.equals(saved)) return saved;
        String autoRuntime = autoRuntime(context, sharedPreferences);
        return AUTO_REALTIME.equals(autoRuntime) ? QUALITY : AUTO_BALANCED.equals(autoRuntime) ? BALANCED : PERFORMANCE;
    }

    static String autoRuntime(Context context, SharedPreferences sharedPreferences) {
        String str;
        String str2;
        String str3;
        if (context == null) return AUTO_BALANCED;
        MemorySnapshot memory = memory(context);
        int safeCpuCount = safeCpuCount();
        int thermalStatus = thermalStatus(context);
        boolean isBatterySaver = isBatterySaver(context);
        int batteryPercent = batteryPercent(context);
        boolean isCharging = isCharging(context);
        boolean isInteractive = RuntimeState.isInteractive(context);
        boolean networkMetered = RuntimeState.networkMetered(context);
        String networkType = RuntimeState.networkType(context);
        long lastLatencyMs = RuntimeDiagnostics.lastLatencyMs();
        int memoryTrimLevel = RuntimeState.memoryTrimLevel();
        int i = sharedPreferences == null ? 0 : sharedPreferences.getInt("renderer_recovery_count", 0);
        if (isBatterySaver || thermalStatus >= severeThermalStatus() || memoryTrimLevel >= 15 || (memory.available > 0 && memory.available < 201326592)) {
            if (isBatterySaver) str = "Battery Saver active";
            else str = thermalStatus >= severeThermalStatus() ? "Thermal pressure" : "Critical memory pressure";
            RuntimeDiagnostics.profile("Auto • Extreme Saver", str);
            return AUTO_EXTREME;
        }
        if ((!isCharging && batteryPercent >= 0 && batteryPercent <= 10) || memory.lowRam || ((memory.total > 0 && memory.total <= THREE_GB) || ((memory.available > 0 && memory.available <= LOW_AVAILABLE_BYTES) || safeCpuCount <= 4 || i >= 3))) {
            if (!isCharging && batteryPercent >= 0 && batteryPercent <= 10) str3 = "Very low battery";
            else str3 = memory.lowRam ? "Android low-RAM device" : safeCpuCount <= 4 ? "Lower CPU class" : "Memory/WebView pressure";
            RuntimeDiagnostics.profile("Auto • Performance", str3);
            return AUTO_PERFORMANCE;
        }
        if (thermalStatus >= moderateThermalStatus() || networkMetered || lastLatencyMs >= 2500 || ((!isCharging && batteryPercent >= 0 && batteryPercent <= 20) || "Offline".equals(networkType) || !isInteractive)) {
            if (thermalStatus >= moderateThermalStatus()) str2 = "Moderate thermal pressure";
            else if (!isCharging && batteryPercent >= 0 && batteryPercent <= 20) str2 = "Low battery";
            else if (networkMetered) str2 = "Metered network";
            else str2 = lastLatencyMs >= 2500 ? "Higher request latency" : "Conservative current state";
            RuntimeDiagnostics.profile("Auto • Balanced", str2);
            return AUTO_BALANCED;
        }
        boolean z = memory.total > THREE_GB && memory.available >= 671088640;
        boolean z2 = safeCpuCount >= 6;
        boolean z3 = lastLatencyMs <= 0 || lastLatencyMs < 1800;
        if (z && z2 && z3 && RuntimeState.networkAvailable(context)) {
            RuntimeDiagnostics.profile("Auto • Real-Time", "Healthy CPU, memory, battery, thermal and network state");
            return AUTO_REALTIME;
        }
        RuntimeDiagnostics.profile("Auto • Balanced", "Average hardware or current load");
        return AUTO_BALANCED;
    }

    static boolean isLowEndOrConstrained(Context context) {
        String autoRuntime = autoRuntime(context, null);
        return AUTO_PERFORMANCE.equals(autoRuntime) || AUTO_EXTREME.equals(autoRuntime);
    }

    static long notificationPollInterval(Context context, SharedPreferences sharedPreferences) {
        if (context == null || !RuntimeState.networkAvailable(context)) return 15000L;
        String saved = saved(sharedPreferences);
        long interval;
        if (!AUTO.equals(saved)) interval = QUALITY.equals(saved) ? 700L : BALANCED.equals(saved) ? 1800L : 5000L;
        else {
            String runtime = autoRuntime(context, sharedPreferences);
            interval = AUTO_REALTIME.equals(runtime) ? 700L : AUTO_BALANCED.equals(runtime) ? 1800L : AUTO_EXTREME.equals(runtime) ? 10000L : 5000L;
        }
        int thermalStatus = thermalStatus(context);
        if (thermalStatus >= severeThermalStatus() || isBatterySaver(context)) interval = Math.max(interval, 10000L);
        else if (thermalStatus >= moderateThermalStatus()) interval = Math.max(interval, 5000L);
        if (RuntimeState.networkMetered(context)) interval = Math.max(interval, 3000L);
        int batteryPercent = batteryPercent(context);
        if (!isCharging(context) && batteryPercent >= 0) {
            if (batteryPercent <= 10) interval = Math.max(interval, 10000L);
            else if (batteryPercent <= 20) interval = Math.max(interval, 5000L);
        }
        if (!RuntimeState.isForeground()) {
            long backgroundDurationMs = RuntimeState.backgroundDurationMs();
            interval = Math.max(interval, backgroundDurationMs >= 60000L ? 15000L : 5000L);
        }
        if (!RuntimeState.isInteractive(context)) interval = Math.max(interval, 10000L);
        long sinceLastInteractionMs = RuntimeState.sinceLastInteractionMs();
        if (sinceLastInteractionMs >= 60000L) interval = Math.max(interval, 10000L);
        else if (sinceLastInteractionMs >= 12000L) interval = Math.max(interval, 5000L);
        return interval;
    }

    static long livePollInterval(Context context, SharedPreferences sharedPreferences) {
        if (context == null || !RuntimeState.networkAvailable(context)) return 5000L;
        String saved = saved(sharedPreferences);
        long j = 1000;
        if (!AUTO.equals(saved)) {
            if (!QUALITY.equals(saved)) j = BALANCED.equals(saved) ? 3000L : 6000L;
        } else {
            String autoRuntime = autoRuntime(context, sharedPreferences);
            if (!AUTO_REALTIME.equals(autoRuntime)) {
                if (AUTO_BALANCED.equals(autoRuntime)) j = 2500;
                else j = AUTO_EXTREME.equals(autoRuntime) ? 10000L : 5000L;
            }
        }
        long sinceLastInteractionMs = RuntimeState.sinceLastInteractionMs();
        if (sinceLastInteractionMs >= 60000) return Math.max(j, 10000L);
        return sinceLastInteractionMs >= 12000 ? Math.max(j, 5000L) : j;
    }

    static long motionDuration(Context context, SharedPreferences sharedPreferences, long j) {
        String resolve = resolve(context, sharedPreferences);
        if (PERFORMANCE.equals(resolve)) return 0L;
        return BALANCED.equals(resolve) ? Math.max(70L, Math.round(j * 0.62d)) : j;
    }

    static String label(String str) {
        return PERFORMANCE.equals(str) ? "Performance" : BALANCED.equals(str) ? "Balanced" : QUALITY.equals(str) ? "High Performance" : "Auto";
    }

    static String settingLabel(Context context, SharedPreferences sharedPreferences) {
        String saved = saved(sharedPreferences);
        if (!AUTO.equals(saved)) return label(saved);
        String autoRuntime = autoRuntime(context, sharedPreferences);
        return AUTO_REALTIME.equals(autoRuntime) ? "Auto • Real-Time" : AUTO_PERFORMANCE.equals(autoRuntime) ? "Auto • Performance" : AUTO_EXTREME.equals(autoRuntime) ? "Auto • Extreme Saver" : "Auto • Balanced";
    }

    static String detail(Context context, SharedPreferences sharedPreferences) {
        if (AUTO.equals(saved(sharedPreferences))) {
            String autoRuntime = autoRuntime(context, sharedPreferences);
            return AUTO_REALTIME.equals(autoRuntime) ? "Fastest safe foreground refresh • full UI effects" : AUTO_EXTREME.equals(autoRuntime) ? "Minimal polling and visual effects while the device is constrained" : AUTO_PERFORMANCE.equals(autoRuntime) ? "Reduced effects • slower idle refresh • lower background overhead" : "Adaptive refresh • normal UI effects";
        }
        String resolve = resolve(context, sharedPreferences);
        return PERFORMANCE.equals(resolve) ? "Minimal motion • blur disabled • lowest visual overhead" : BALANCED.equals(resolve) ? "Shorter motion • reduced visual overhead" : "Full motion and visual effects";
    }

    static boolean isBatterySaver(Context context) {
        if (context == null) return false;
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService("power");
            return powerManager != null && powerManager.isPowerSaveMode();
        } catch (Throwable unused) { return false; }
    }

    static int batteryPercent(Context context) {
        Intent registerReceiver = null;
        if (context == null) return -1;
        try { registerReceiver = context.registerReceiver(null, new IntentFilter("android.intent.action.BATTERY_CHANGED")); } catch (Throwable unused) {}
        if (registerReceiver == null) return -1;
        int intExtra = registerReceiver.getIntExtra("level", -1);
        int intExtra2 = registerReceiver.getIntExtra("scale", -1);
        if (intExtra >= 0 && intExtra2 > 0) return Math.max(0, Math.min(100, Math.round((intExtra * 100.0f) / intExtra2)));
        return -1;
    }

    static boolean isCharging(Context context) {
        if (context == null) return false;
        try {
            Intent registerReceiver = context.registerReceiver(null, new IntentFilter("android.intent.action.BATTERY_CHANGED"));
            if (registerReceiver == null) return false;
            int intExtra = registerReceiver.getIntExtra("status", -1);
            return intExtra == 2 || intExtra == 5;
        } catch (Throwable unused) { return false; }
    }

    private static int thermalStatus(Context context) {
        if (Build.VERSION.SDK_INT < 29 || context == null) return 0;
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService("power");
            return powerManager == null ? 0 : powerManager.getCurrentThermalStatus();
        } catch (Throwable unused) { return 0; }
    }

    private static int safeCpuCount() {
        try { return Math.max(1, Runtime.getRuntime().availableProcessors()); } catch (Throwable unused) { return 4; }
    }

    private static MemorySnapshot memory(Context context) {
        ActivityManager activityManager = null;
        MemorySnapshot memorySnapshot = new MemorySnapshot();
        try { activityManager = (ActivityManager) context.getSystemService("activity"); } catch (Throwable unused) {}
        if (activityManager == null) return memorySnapshot;
        memorySnapshot.lowRam = activityManager.isLowRamDevice();
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        memorySnapshot.total = memoryInfo.totalMem;
        memorySnapshot.available = memoryInfo.availMem;
        return memorySnapshot;
    }

    private static String normalize(String str) {
        return (PERFORMANCE.equals(str) || BALANCED.equals(str) || QUALITY.equals(str)) ? str : AUTO;
    }

    private static final class MemorySnapshot {
        long available;
        boolean lowRam;
        long total;
        private MemorySnapshot() {}
    }

    private PerformanceProfile() {}
}

// ---- RuntimeDiagnostics.java ----
/* loaded from: classes.dex */
final class RuntimeDiagnostics {
    private static volatile int consecutiveApiFailures = 0;
    private static volatile long currentLivePollMs = 0;
    private static volatile long currentNotificationPollMs = 0;
    private static volatile long lastObservedLatencyMs = 0;
    private static volatile String lastProfile = "Auto • Balanced";
    private static volatile String lastProfileReason = "Starting";
    private static volatile String notificationMode = "Adaptive polling fallback";

    static void notificationPoll(long j, String str) {
        currentNotificationPollMs = Math.max(0L, j);
        if (str == null || str.trim().isEmpty()) return;
        notificationMode = str.trim();
    }
    static void livePoll(long j) { currentLivePollMs = Math.max(0L, j); }
    static void syncSucceeded(long j) { lastObservedLatencyMs = Math.max(0L, j); consecutiveApiFailures = 0; }
    static void syncFailed() { consecutiveApiFailures = Math.min(99, consecutiveApiFailures + 1); }
    static int failures() { return consecutiveApiFailures; }
    static long lastLatencyMs() { return lastObservedLatencyMs; }
    static long notificationPollMs() { return currentNotificationPollMs; }
    static long livePollMs() { return currentLivePollMs; }
    static String notificationMode() { return notificationMode; }
    static void profile(String str, String str2) { if (str != null && !str.isEmpty()) lastProfile = str; if (str2 != null && !str2.isEmpty()) lastProfileReason = str2; }
    static String profileLabel() { return lastProfile; }
    static String profileReason() { return lastProfileReason; }
    static int rendererRecoveries(SharedPreferences sharedPreferences) { return sharedPreferences == null ? 0 : sharedPreferences.getInt("renderer_recovery_count", 0); }
    static String fcmState() { return "Native FCM transport unavailable • adaptive polling active"; }

    static String compact(Context context, SharedPreferences sharedPreferences) {
        StringBuilder sb = new StringBuilder("Profile: ");
        sb.append(PerformanceProfile.settingLabel(context, sharedPreferences));
        sb.append("\nRuntime reason: ").append(profileReason());
        sb.append("\nNotification sync: ").append(notificationMode());
        sb.append("\nNotification interval: ").append(interval(currentNotificationPollMs));
        sb.append("\nLive page interval: ").append(interval(currentLivePollMs));
        sb.append("\nFCM: ").append(fcmState());
        sb.append("\nNetwork: ").append(RuntimeState.networkType(context));
        sb.append("\nBattery Saver: ").append(PerformanceProfile.isBatterySaver(context) ? "On" : "Off");
        sb.append("\nRenderer recoveries: ").append(rendererRecoveries(sharedPreferences));
        sb.append("\nConsecutive API failures: ").append(consecutiveApiFailures);
        return sb.toString();
    }

    private static String interval(long j) {
        if (j <= 0) return "idle";
        if (j < 1000) return j + " ms";
        if (j % 1000 != 0) return String.format(Locale.US, "%.2f s", Double.valueOf(j / 1000.0d));
        return (j / 1000) + " s";
    }
    private RuntimeDiagnostics() {}
}

// ---- RuntimeState.java ----
/* loaded from: classes.dex */
final class RuntimeState implements Application.ActivityLifecycleCallbacks {
    private static volatile long lastForegroundAtMs;
    private static volatile int memoryTrimLevel;
    private static volatile int startedActivities;
    private static final RuntimeState INSTANCE = new RuntimeState();
    private static volatile long backgroundSinceMs = System.currentTimeMillis();
    private static volatile long lastInteractionAtMs = System.currentTimeMillis();

    @Override public void onActivityCreated(Activity activity, Bundle bundle) {}
    @Override public void onActivityDestroyed(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {}

    static void install(Application application) { if (application != null) try { application.registerActivityLifecycleCallbacks(INSTANCE); } catch (Throwable unused) {} }
    static boolean isForeground() { return startedActivities > 0; }
    static long backgroundDurationMs() { return isForeground() ? 0L : Math.max(0L, System.currentTimeMillis() - backgroundSinceMs); }
    static long sinceLastInteractionMs() { return Math.max(0L, System.currentTimeMillis() - lastInteractionAtMs); }
    static void noteUserInteraction() { lastInteractionAtMs = System.currentTimeMillis(); }
    static void noteTrimMemory(int i) { memoryTrimLevel = Math.max(memoryTrimLevel, i); }
    static void clearMemoryPressure() { memoryTrimLevel = 0; }
    static int memoryTrimLevel() { return memoryTrimLevel; }

    static boolean isInteractive(Context context) {
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService("power");
            return powerManager == null || powerManager.isInteractive();
        } catch (Throwable unused) { return true; }
    }
    static boolean networkAvailable(Context context) { return !"Offline".equals(networkType(context)); }
    static String networkType(Context context) {
        if (context == null) return "Unknown";
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService("connectivity");
            if (connectivityManager == null) return "Unknown";
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
                if (networkCapabilities != null && networkCapabilities.hasCapability(12)) {
                    boolean metered = !networkCapabilities.hasCapability(11);
                    if (networkCapabilities.hasTransport(1)) return metered ? "Wi-Fi • metered" : "Wi-Fi";
                    if (networkCapabilities.hasTransport(3)) return "Ethernet";
                    if (networkCapabilities.hasTransport(0)) return metered ? "Cellular • metered" : "Cellular";
                    return metered ? "Connected • metered" : "Connected";
                }
            }
            return "Offline";
        } catch (Throwable unused) { return "Unknown"; }
    }
    static boolean networkMetered(Context context) {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService("connectivity");
            return connectivityManager != null && connectivityManager.isActiveNetworkMetered();
        } catch (Throwable unused) { return false; }
    }

    @Override public void onActivityStarted(Activity activity) { startedActivities++; lastForegroundAtMs = System.currentTimeMillis(); clearMemoryPressure(); }
    @Override public void onActivityResumed(Activity activity) { lastForegroundAtMs = System.currentTimeMillis(); noteUserInteraction(); }
    @Override public void onActivityStopped(Activity activity) { startedActivities = Math.max(0, startedActivities - 1); if (startedActivities == 0) backgroundSinceMs = System.currentTimeMillis(); }
    private RuntimeState() {}
}

// ---- TelemetryService.java ----
/** Optional, opt-in HCF telemetry and diagnostic reporting. */
final class TelemetryService {
    private static final String AAD = "HCF_TELEMETRY";
    private static final long HEARTBEAT_INTERVAL_MS = 21600000L;
    private static final int MAX_BREADCRUMBS = 25;
    private static final int MAX_HISTORY = 20;
    private static final String PENDING_CRASH_FILE = "pending-crash.json";
    private static final String WEBHOOK_CIPHERTEXT_B64 = "VwSJKWKfeSuD5qbdEnHBEyUmADOfG8Fz61tvPWfIaqjA3c20QXuN1hgob3q5Oe26CKskpawsGgAHQm0uWQCveFBGHJbo/Ux6GT400Emw1NDCMp7dGDrMhEqDMUElQpRD7mE6WyOPeEJRQzWYavxeceU6bo3aWvdfNy9UZuAy1UuNMU/cUIrDqfA=";
    private static final String WEBHOOK_IV_B64 = "uoO2HMaYrR1Qjq+6";
    static final String LEVEL_BASIC = "basic";
    static final String LEVEL_DIAGNOSTICS = "diagnostics";

    static boolean isEnabled(Context context) { return context != null && prefs(context).getBoolean("telemetry_enabled", false); }
    static String level(Context context) { if (context == null) return LEVEL_BASIC; return LEVEL_DIAGNOSTICS.equals(prefs(context).getString("telemetry_level", LEVEL_BASIC)) ? LEVEL_DIAGNOSTICS : LEVEL_BASIC; }
    static boolean isDiagnostics(Context context) { return isEnabled(context) && LEVEL_DIAGNOSTICS.equals(level(context)); }
    static String levelLabel(Context context) { return LEVEL_DIAGNOSTICS.equals(level(context)) ? "Diagnostics" : "Basic"; }
    static String status(Context context) {
        if (!isEnabled(context)) return "Telemetry: Off • no telemetry is sent";
        String result = prefs(context).getString("telemetry_last_result", "Ready");
        if (result == null || result.trim().isEmpty()) result = "Ready";
        return "Telemetry: On • " + levelLabel(context) + " • " + result.trim();
    }

    static void heartbeat(Context context) {
        if (!isEnabled(context)) return;
        Context app = context.getApplicationContext();
        SharedPreferences p = prefs(app);
        long now = System.currentTimeMillis();
        long last = p.getLong("telemetry_last_heartbeat", 0L);
        if (last <= 0L || now - last >= HEARTBEAT_INTERVAL_MS) {
            p.edit().putLong("telemetry_last_heartbeat", now).apply();
            sendEvent(app, "app_heartbeat", "foreground");
        }
    }

    static void noteRoute(Context context, String route) { if (context != null) prefs(context).edit().putString("telemetry_last_route", sanitizedRoute(route, false)).apply(); }

    static void recordBreadcrumb(Context context, String event, String detail) {
        if (!isDiagnostics(context)) return;
        try {
            SharedPreferences p = prefs(context);
            JSONArray old;
            try { old = new JSONArray(p.getString("telemetry_breadcrumbs", "[]")); } catch (Throwable ignored) { old = new JSONArray(); }
            JSONArray next = new JSONArray();
            for (int i = Math.max(0, old.length() - (MAX_BREADCRUMBS - 1)); i < old.length(); i++) next.put(old.opt(i));
            JSONObject item = new JSONObject();
            item.put("time", isoNow()); item.put("event", safeToken(event, "event")); item.put("detail", safeDetail(detail, 180)); next.put(item);
            p.edit().putString("telemetry_breadcrumbs", next.toString()).apply();
        } catch (Throwable ignored) {}
    }

    static void sendEvent(Context context, String event, String detail) {
        if (!isEnabled(context)) return;
        final Context app = context.getApplicationContext();
        final String safeEvent = safeToken(event, "app_event");
        final String safeText = safeDetail(detail, 240);
        AppExecutors.network().execute(() -> { try { postReport(app, buildBaseReport(app, "event", safeEvent, safeText, null)); } catch (Throwable error) { saveResult(app, "Last send failed"); } });
    }

    static void sendDiagnosticEvent(Context context, String event, String detail) {
        if (!isDiagnostics(context) || !prefs(context).getBoolean("telemetry_auto_error_reports", false)) return;
        final Context app = context.getApplicationContext();
        AppExecutors.network().execute(() -> {
            try {
                JSONObject report = buildBaseReport(app, "diagnostic_error", safeToken(event, "error"), safeDetail(detail, 420), null);
                report.put("breadcrumbs", breadcrumbs(app));
                postReport(app, report);
            } catch (Throwable ignored) {}
        });
    }

    static void sendTest(Context context) {
        if (!isEnabled(context)) return;
        if (LEVEL_DIAGNOSTICS.equals(level(context))) {
            final Context app = context.getApplicationContext();
            AppExecutors.network().execute(() -> {
                try {
                    JSONObject report = buildBaseReport(app, "diagnostic_test", "telemetry_test", "manual diagnostics test from App Settings", null);
                    report.put("breadcrumbs", breadcrumbs(app));
                    postReport(app, report);
                } catch (Throwable ignored) {}
            });
        } else sendEvent(context, "telemetry_test", "manual basic telemetry test from App Settings");
    }

    static void captureCrash(Context context, Thread thread, Throwable error) {
        if (context == null || error == null || !isDiagnostics(context)) return;
        try {
            JSONObject report = buildBaseReport(context, "crash", "uncaught_exception", "", false);
            report.put("thread", safeDetail(thread == null ? "unknown" : thread.getName(), 80));
            report.put("exception", error.getClass().getName());
            report.put("message", safeDetail(String.valueOf(error.getMessage()), 800));
            report.put("stackTrace", sanitizeStack(error));
            report.put("breadcrumbs", breadcrumbs(context));
            writeText(pendingCrashFile(context), report.toString());
            prefs(context).edit().putString("telemetry_pending_crash_id", report.optString("reportId", "")).apply();
        } catch (Throwable ignored) {}
    }

    static boolean hasPendingCrash(Context context) { return context != null && pendingCrashFile(context).exists(); }

    static void handlePendingCrash(final Activity activity) {
        if (activity == null || activity.isFinishing() || !hasPendingCrash(activity) || !isDiagnostics(activity)) return;
        SharedPreferences p = prefs(activity);
        if (p.getBoolean("telemetry_auto_crash_reports", false) && !p.getBoolean("telemetry_ask_before_crash_report", true)) {
            sendPendingCrash(activity, "", p.getBoolean("telemetry_include_identity", false));
            return;
        }
        JSONObject pending = readPendingCrash(activity);
        if (pending == null) return;
        final String reportId = pending.optString("reportId", "HCF-REPORT");
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        content.setPadding(pad, pad / 2, pad, 0);
        TextView intro = new TextView(activity);
        intro.setText("Harley's Clan Forum recovered from a problem.\nReport ID: " + reportId);
        intro.setTextSize(14.0f);
        content.addView(intro);
        final EditText notes = new EditText(activity);
        notes.setHint("What were you doing when this happened? (optional)");
        notes.setMinLines(2); notes.setMaxLines(5);
        LinearLayout.LayoutParams notesLp = new LinearLayout.LayoutParams(-1, -2); notesLp.topMargin = dp(activity, 10); content.addView(notes, notesLp);
        final Switch identity = new Switch(activity);
        identity.setText("Include my forum identity with this report"); identity.setChecked(p.getBoolean("telemetry_include_identity", false)); content.addView(identity);
        TextView privacy = new TextView(activity); privacy.setText("Passwords, cookies, access tokens, recovery codes, posts and messages are never included."); privacy.setTextSize(11.0f); content.addView(privacy);
        final AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Send crash report?").setView(content).setPositiveButton("Send Report", null).setNeutralButton("Preview", null).setNegativeButton("Don't Send", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> { sendPendingCrash(activity, notes.getText().toString(), identity.isChecked()); Toast.makeText(activity, "Crash report queued: " + reportId, Toast.LENGTH_LONG).show(); dialog.dismiss(); });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> showTextDialog(activity, "Crash report preview", previewPendingReport(activity, notes.getText().toString(), identity.isChecked())));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> { deletePendingCrash(activity); addHistory(activity, reportId, "crash", "discarded"); dialog.dismiss(); });
        });
        dialog.show();
    }

    static void showManualFeedbackDialog(final Activity activity) {
        if (activity == null) return;
        if (!isEnabled(activity)) { Toast.makeText(activity, "Enable Telemetry Services first.", Toast.LENGTH_SHORT).show(); return; }
        LinearLayout content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL); int pad = dp(activity, 16); content.setPadding(pad, pad / 2, pad, 0);
        final EditText details = new EditText(activity); details.setHint("Describe the problem or feedback"); details.setMinLines(3); details.setMaxLines(7); content.addView(details);
        final Switch identity = new Switch(activity); identity.setText("Include my forum identity with this report"); identity.setChecked(prefs(activity).getBoolean("telemetry_include_identity", false)); content.addView(identity);
        new AlertDialog.Builder(activity).setTitle("Send diagnostic feedback").setView(content).setPositiveButton("Send", (dialog, which) -> sendManualFeedback(activity, safeDetail(details.getText().toString(), 900), identity.isChecked())).setNegativeButton("Cancel", null).show();
    }

    static void showPreview(Activity activity) { if (activity != null) showTextDialog(activity, "Telemetry report preview", previewReport(activity)); }
    static void showHistory(Activity activity) { if (activity != null) showTextDialog(activity, "Telemetry report history", historyText(activity)); }
    static void clearLocalReports(Context context) { if (context == null) return; try { pendingCrashFile(context).delete(); } catch (Throwable ignored) {} prefs(context).edit().remove("telemetry_report_history").remove("telemetry_pending_crash_id").remove("telemetry_breadcrumbs").apply(); }

    static String previewReport(Context context) {
        try {
            JSONObject report = buildBaseReport(context, "preview", "settings_preview", "Example of the data this app would send with the current telemetry settings.", null);
            if (LEVEL_DIAGNOSTICS.equals(level(context))) report.put("breadcrumbs", breadcrumbs(context));
            return report.toString(2);
        } catch (Throwable error) { return "Preview unavailable: " + error.getClass().getSimpleName(); }
    }

    private static void sendPendingCrash(Context context, final String notes, final boolean includeIdentity) {
        final Context app = context.getApplicationContext(); final JSONObject pending = readPendingCrash(app); if (pending == null) return;
        AppExecutors.network().execute(() -> { try { if (postReport(app, prepareReportForSend(app, pending, notes, includeIdentity))) deletePendingCrash(app); } catch (Throwable error) { saveResult(app, "Crash report send failed"); } });
    }

    private static String previewPendingReport(Context context, String notes, boolean includeIdentity) {
        try { JSONObject pending = readPendingCrash(context); return pending == null ? "No pending crash report." : prepareReportForSend(context, pending, notes, includeIdentity).toString(2); }
        catch (Throwable error) { return "Preview unavailable: " + error.getClass().getSimpleName(); }
    }

    private static void sendManualFeedback(Context context, final String details, final boolean includeIdentity) {
        final Context app = context.getApplicationContext();
        AppExecutors.network().execute(() -> { try { JSONObject report = buildBaseReport(app, "feedback", "manual_feedback", details, includeIdentity); if (LEVEL_DIAGNOSTICS.equals(level(app))) report.put("breadcrumbs", breadcrumbs(app)); postReport(app, report); } catch (Throwable ignored) {} });
        Toast.makeText(context, "Diagnostic feedback queued.", Toast.LENGTH_SHORT).show();
    }

    private static JSONObject prepareReportForSend(Context context, JSONObject original, String notes, boolean includeIdentity) throws Exception {
        JSONObject report = new JSONObject(original.toString()); if (notes != null && !notes.trim().isEmpty()) report.put("userFeedback", safeDetail(notes, 900)); applyOptionalPrivacyFields(context, report, includeIdentity); return report;
    }

    private static JSONObject buildBaseReport(Context context, String type, String event, String detail, Boolean identityOverride) throws Exception {
        JSONObject report = new JSONObject(); report.put("reportId", newReportId()); report.put("type", safeToken(type, "event")); report.put("event", safeToken(event, "app_event")); report.put("timestampUtc", isoNow()); report.put("appVersion", BuildInfo.VERSION); report.put("versionCode", BuildInfo.VERSION_CODE); report.put("internalBuild", BuildInfo.INTERNAL_BUILD); report.put("channel", BuildInfo.CHANNEL); report.put("package", context == null ? "" : context.getPackageName()); report.put("androidApi", Build.VERSION.SDK_INT); report.put("orientation", orientation(context)); if (detail != null && !detail.trim().isEmpty()) report.put("detail", safeDetail(detail, 900)); ForumIdentity.Snapshot identity = ForumIdentity.load(context); report.put("identityMode", identity.loggedIn ? "SIGNED_IN" : "Guest_Protocol"); String host = identity.host == null || identity.host.trim().isEmpty() ? prefs(context).getString("active_host", "forum.harleytg.com") : identity.host; report.put("forumHost", ForumUrlRouter.isForumHost(host) ? host : "forum.harleytg.com"); applyOptionalPrivacyFields(context, report, identityOverride); return report;
    }

    private static void applyOptionalPrivacyFields(Context context, JSONObject report, Boolean identityOverride) throws Exception {
        SharedPreferences p = prefs(context); boolean includeDevice = p.getBoolean("telemetry_include_device_model", false); boolean includeRoute = p.getBoolean("telemetry_include_route", false); boolean includeIdentity = identityOverride != null ? identityOverride.booleanValue() : p.getBoolean("telemetry_include_identity", false); boolean includeEmail = includeIdentity && p.getBoolean("telemetry_include_email", false);
        if (includeDevice) report.put("device", safeDetail(Build.MANUFACTURER + " " + Build.MODEL, 120)); else report.remove("device");
        if (includeRoute) { String route = p.getString("telemetry_last_route", ""); if (route != null && !route.isEmpty()) report.put("route", sanitizedRoute(route, includeIdentity)); } else report.remove("route");
        if (includeIdentity) {
            ForumIdentity.Snapshot identity = ForumIdentity.load(context); if (identity.loggedIn) { JSONObject forumIdentity = new JSONObject(); forumIdentity.put("displayName", safeDetail(identity.displayName, 120)); forumIdentity.put("username", safeDetail(identity.username, 120)); if (!identity.groups.isEmpty()) forumIdentity.put("groups", safeDetail(identity.groups, 240)); if (includeEmail && !identity.email.isEmpty()) forumIdentity.put("email", safeDetail(identity.email, 180)); report.put("forumIdentity", forumIdentity); }
        } else report.remove("forumIdentity");
    }

    private static boolean postReport(Context context, JSONObject report) {
        HttpsURLConnection connection = null; String reportId = report == null ? "HCF-REPORT" : report.optString("reportId", "HCF-REPORT"); String type = report == null ? "event" : report.optString("type", "event");
        try {
            String endpoint = decryptWebhook(context); if (endpoint == null || !endpoint.startsWith("https://discord.com/api/webhooks/")) { saveResult(context, "Blocked: endpoint verification failed"); addHistory(context, reportId, type, "blocked"); return false; }
            byte[] body = discordPayload(report).toString().getBytes(StandardCharsets.UTF_8); connection = (HttpsURLConnection) new URL(endpoint).openConnection(); connection.setConnectTimeout(8000); connection.setReadTimeout(8000); connection.setRequestMethod("POST"); connection.setDoOutput(true); connection.setInstanceFollowRedirects(false); connection.setRequestProperty("Content-Type", "application/json; charset=utf-8"); connection.setRequestProperty("User-Agent", "HarleysClanForumTelemetry/" + BuildInfo.VERSION); connection.setFixedLengthStreamingMode(body.length); try (OutputStream output = connection.getOutputStream()) { output.write(body); output.flush(); }
            int code = connection.getResponseCode(); if (code >= 200 && code < 300) { saveResult(context, "Last send succeeded • " + reportId); addHistory(context, reportId, type, "sent"); AppLogger.info(context, "telemetry_sent", reportId + " | " + type); return true; }
            saveResult(context, "Last send failed (HTTP " + code + ")"); addHistory(context, reportId, type, "HTTP " + code); AppLogger.warn(context, "telemetry_http", reportId + " | " + code);
        } catch (Throwable error) { saveResult(context, "Last send failed"); addHistory(context, reportId, type, "failed"); AppLogger.warn(context, "telemetry_failed", reportId + " | " + error.getClass().getSimpleName()); }
        finally { if (connection != null) connection.disconnect(); }
        return false;
    }

    private static JSONObject discordPayload(JSONObject report) throws Exception {
        JSONObject root = new JSONObject(); root.put("username", "HCF Diagnostics"); root.put("allowed_mentions", new JSONObject().put("parse", new JSONArray())); String type = report.optString("type", "event"); int color = "crash".equals(type) ? 15022389 : (type.contains("error") ? 15769600 : 47344); JSONObject embed = new JSONObject(); embed.put("title", ("crash".equals(type) ? "🔴 Crash" : "HCF Diagnostics") + " • " + report.optString("event", type)); embed.put("color", color); String detail = report.optString("detail", ""); if (!detail.isEmpty()) embed.put("description", safeDetail(detail, 900)); JSONArray fields = new JSONArray(); fields.put(field("Report ID", report.optString("reportId", "—"), true)); fields.put(field("App", report.optString("appVersion", BuildInfo.VERSION) + " • " + report.optString("channel", BuildInfo.CHANNEL), true)); fields.put(field("Build", report.optInt("internalBuild", BuildInfo.INTERNAL_BUILD) + " / " + report.optInt("versionCode", BuildInfo.VERSION_CODE), true)); fields.put(field("Android", "API " + report.optInt("androidApi", Build.VERSION.SDK_INT), true)); fields.put(field("Orientation", report.optString("orientation", "unknown"), true)); fields.put(field("Identity mode", report.optString("identityMode", "Guest_Protocol"), true)); fields.put(field("Forum host", report.optString("forumHost", "—"), false)); if (report.has("device")) fields.put(field("Device", report.optString("device", "—"), false)); if (report.has("route")) fields.put(field("Route", report.optString("route", "—"), false)); JSONObject forumIdentity = report.optJSONObject("forumIdentity"); if (forumIdentity != null) { StringBuilder identity = new StringBuilder(); if (!forumIdentity.optString("displayName", "").isEmpty()) identity.append(forumIdentity.optString("displayName")).append('\n'); if (!forumIdentity.optString("username", "").isEmpty()) identity.append('@').append(forumIdentity.optString("username")).append('\n'); if (!forumIdentity.optString("groups", "").isEmpty()) identity.append("Groups: ").append(forumIdentity.optString("groups")).append('\n'); if (!forumIdentity.optString("email", "").isEmpty()) identity.append("Email: ").append(forumIdentity.optString("email")); fields.put(field("Forum identity (user opted in)", safeDetail(identity.toString(), 900), false)); } if (report.has("exception")) fields.put(field("Exception", report.optString("exception", "—") + (report.optString("message", "").isEmpty() ? "" : "\n" + report.optString("message")), false)); if (report.has("userFeedback")) fields.put(field("User feedback", report.optString("userFeedback", "—"), false)); if (report.has("stackTrace")) fields.put(field("Stack trace", "```\n" + safeDetail(report.optString("stackTrace", ""), 900) + "\n```", false)); JSONArray crumbs = report.optJSONArray("breadcrumbs"); if (crumbs != null && crumbs.length() > 0) fields.put(field("Recent app events", breadcrumbText(crumbs), false)); embed.put("fields", fields); embed.put("footer", new JSONObject().put("text", "HCF opt-in diagnostics • sensitive credentials and forum content excluded")); embed.put("timestamp", report.optString("timestampUtc", isoNow())); root.put("embeds", new JSONArray().put(embed)); return root;
    }

    private static JSONObject field(String name, String value, boolean inline) throws Exception { String safe = value == null || value.trim().isEmpty() ? "—" : value.trim(); if (safe.length() > 1000) safe = safe.substring(0, 1000) + "…"; return new JSONObject().put("name", name).put("value", safe).put("inline", inline); }
    private static JSONArray breadcrumbs(Context context) { try { return new JSONArray(prefs(context).getString("telemetry_breadcrumbs", "[]")); } catch (Throwable ignored) { return new JSONArray(); } }
    private static String breadcrumbText(JSONArray items) { StringBuilder out = new StringBuilder(); for (int i = Math.max(0, items.length() - 12); i < items.length(); i++) { JSONObject item = items.optJSONObject(i); if (item == null) continue; if (out.length() > 0) out.append('\n'); out.append(item.optString("event", "event")); String detail = item.optString("detail", ""); if (!detail.isEmpty()) out.append(" • ").append(detail); if (out.length() > 880) break; } return safeDetail(out.toString(), 900); }

    private static String sanitizeStack(Throwable error) {
        try {
            StringWriter writer = new StringWriter(); error.printStackTrace(new PrintWriter(writer)); String safe = writer.toString().replace((char) 0, ' ').replace("\r", "").replaceAll("https?://[^\\s?]+\\?[^\\s]+", "[URL_QUERY_REDACTED]").replaceAll("(?i)(token|authorization|cookie|password)=?[^\\s,;]+", "$1=[REDACTED]"); return safe.length() <= 6000 ? safe : safe.substring(0, 6000) + "…";
        } catch (Throwable ignored) { return error.getClass().getName(); }
    }

    private static String sanitizedRoute(String value, boolean identityAllowed) {
        if (value == null || value.trim().isEmpty()) return "";
        try { String path = Uri.parse(value).getPath(); if (path == null || path.isEmpty()) path = value.startsWith("/") ? value : "/"; if (!identityAllowed) path = path.replaceAll("(?i)^/u/[^/]+", "/u/[user]"); path = path.replaceAll("(?i)^/d/(\\d+)[^/]*", "/d/$1"); return path.length() > 240 ? path.substring(0, 240) : path; }
        catch (Throwable ignored) { return "[route-unavailable]"; }
    }

    private static String orientation(Context context) { try { int orientation = context.getResources().getConfiguration().orientation; return orientation == 2 ? "landscape" : (orientation == 1 ? "portrait" : "unknown"); } catch (Throwable ignored) { return "unknown"; } }

    private static String newReportId() {
        String date; try { SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd", Locale.US); format.setTimeZone(TimeZone.getTimeZone("UTC")); date = format.format(new Date()); } catch (Throwable ignored) { date = "00000000"; }
        byte[] random = new byte[3]; new SecureRandom().nextBytes(random); StringBuilder suffix = new StringBuilder(); for (byte b : random) suffix.append(String.format(Locale.US, "%02X", b & 255)); return "HCF-" + date + "-" + suffix;
    }

    private static void addHistory(Context context, String reportId, String type, String result) {
        try { SharedPreferences p = prefs(context); JSONArray old; try { old = new JSONArray(p.getString("telemetry_report_history", "[]")); } catch (Throwable ignored) { old = new JSONArray(); } JSONArray next = new JSONArray(); for (int i = Math.max(0, old.length() - (MAX_HISTORY - 1)); i < old.length(); i++) next.put(old.opt(i)); next.put(new JSONObject().put("time", isoNow()).put("reportId", reportId).put("type", type).put("result", result)); p.edit().putString("telemetry_report_history", next.toString()).apply(); } catch (Throwable ignored) {}
    }

    private static String historyText(Context context) {
        try { JSONArray history = new JSONArray(prefs(context).getString("telemetry_report_history", "[]")); if (history.length() == 0) return "No telemetry reports have been sent or discarded on this device."; StringBuilder out = new StringBuilder(); for (int i = history.length() - 1; i >= 0; i--) { JSONObject item = history.optJSONObject(i); if (item == null) continue; out.append(item.optString("time", "")).append('\n').append(item.optString("reportId", "HCF-REPORT")).append(" • ").append(item.optString("type", "event")).append(" • ").append(item.optString("result", "unknown")).append("\n\n"); } return out.toString().trim(); } catch (Throwable ignored) { return "History unavailable."; }
    }

    private static JSONObject readPendingCrash(Context context) { try { File file = pendingCrashFile(context); return file.exists() ? new JSONObject(readText(file)) : null; } catch (Throwable ignored) { return null; } }
    private static void deletePendingCrash(Context context) { try { pendingCrashFile(context).delete(); } catch (Throwable ignored) {} try { prefs(context).edit().remove("telemetry_pending_crash_id").apply(); } catch (Throwable ignored) {} }
    private static File pendingCrashFile(Context context) { File dir = new File(context.getApplicationContext().getFilesDir(), "telemetry"); if (!dir.exists()) dir.mkdirs(); return new File(dir, PENDING_CRASH_FILE); }
    private static void writeText(File file, String value) throws Exception { try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8)) { writer.write(value == null ? "" : value); writer.flush(); } }
    private static String readText(File file) throws Exception { try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) { StringBuilder out = new StringBuilder(); String line; while ((line = reader.readLine()) != null) out.append(line); return out.toString(); } }

    private static String decryptWebhook(Context context) throws Exception {
        String fingerprint = signingCertificateSha256(context); if (fingerprint.isEmpty()) throw new IllegalStateException("signing certificate unavailable"); byte[] key = MessageDigest.getInstance("SHA-256").digest(("HCF_TELEMETRY_V1|" + context.getPackageName() + "|" + fingerprint + "|HCF-Telemetry-Discord-Relay-v1").getBytes(StandardCharsets.UTF_8)); SecretKeySpec secret = new SecretKeySpec(key, "AES"); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, secret, new GCMParameterSpec(128, Base64.decode(WEBHOOK_IV_B64, Base64.NO_WRAP))); cipher.updateAAD(AAD.getBytes(StandardCharsets.UTF_8)); return new String(cipher.doFinal(Base64.decode(WEBHOOK_CIPHERTEXT_B64, Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }

    private static String signingCertificateSha256(Context context) throws Exception {
        PackageManager manager = context.getPackageManager(); Signature[] signatures; if (Build.VERSION.SDK_INT >= 28) { PackageInfo info = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES); signatures = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners(); } else signatures = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES).signatures; if (signatures == null || signatures.length == 0) return ""; byte[] digest = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()); StringBuilder out = new StringBuilder(digest.length * 2); for (byte b : digest) out.append(String.format(Locale.US, "%02x", b & 255)); return out.toString();
    }

    private static String isoNow() { try { SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US); format.setTimeZone(TimeZone.getTimeZone("UTC")); return format.format(new Date()); } catch (Throwable ignored) { return ""; } }
    private static String safeToken(String value, String fallback) { String safe = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9._-]", "_"); if (safe.isEmpty()) safe = fallback; return safe.length() > 64 ? safe.substring(0, 64) : safe; }
    private static String safeDetail(String value, int max) { if (value == null) return ""; String safe = value.replace((char) 0, ' ').replace("\r", "").trim().replaceAll("(?i)(authorization|cookie|password|access[_-]?token|refresh[_-]?token)\\s*[:=]\\s*[^\\s,;]+", "$1=[REDACTED]"); return safe.length() <= max ? safe : safe.substring(0, max) + "…"; }
    private static void saveResult(Context context, String value) { try { prefs(context).edit().putString("telemetry_last_result", value).apply(); } catch (Throwable ignored) {} }
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences("hcf_app", 0); }
    private static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private static void showTextDialog(Activity activity, String title, String body) { TextView text = new TextView(activity); text.setText(body == null ? "" : body); text.setTextSize(12.0f); text.setTextIsSelectable(true); text.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10)); ScrollView scroll = new ScrollView(activity); scroll.addView(text); new AlertDialog.Builder(activity).setTitle(title).setView(scroll).setPositiveButton("Close", null).show(); }
    private TelemetryService() {}
}
