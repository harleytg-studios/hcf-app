package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Application;
import android.app.DownloadManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
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
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.lang.Thread;
import java.net.IDN;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;

import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;


// ---- Consolidated from HcfApplication.java ----
public final class HcfCore {
    private HcfCore() {}

    // ---- HcfCore.java ----
    /**
     * Application entry. Applies resolved night mode as early as possible so the
     * first activity frame does not flash the opposite theme (light splash on a
     * dark preference, etc.).
     */
    public static final class App extends Application {
        @Override
        public void onCreate() {
            super.onCreate();

            // Dev/Beta realtime policy: latency wins over battery life.
            try {
                getSharedPreferences("hcf_app", 0).edit()
                        .putBoolean("aggressive_realtime", true)
                        .putString("performance_profile", PerformanceProfile.QUALITY)
                        .apply();
            } catch (Throwable ignored) {
            }

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
                    if (activity instanceof HcfForum.MainActivity) {
                        try {
                            SetupCenter.maybeLaunchForMainActivity((HcfForum.MainActivity) activity, state);
                        } catch (Throwable error) {
                            AppLogger.error(App.this, "app_setup_lifecycle", error.getClass().getSimpleName());
                        }
                    }
                }

                @Override public void onActivityResumed(Activity activity) {
                    if (activity instanceof HcfForum.MainActivity) {
                        try {
                            BatteryOptimizationHelper.maybeRequest(activity);
                        } catch (Throwable error) {
                            AppLogger.warn(App.this, "battery_optimization_request", error.getClass().getSimpleName());
                        }
                        try {
                            SetupCenter.installDrawerEntry((HcfForum.MainActivity) activity);
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
        NETWORK = Executors.newFixedThreadPool(max2, new ThreadFactory() { // from class: com.harleytg.forum.dev.AppExecutors$$ExternalSyntheticLambda0
            @Override // java.util.concurrent.ThreadFactory
            public final Thread newThread(Runnable runnable) {
                return AppExecutors.lambda$static$0(runnable);
            }
        });
        DISK = Executors.newSingleThreadExecutor(new ThreadFactory() { // from class: com.harleytg.forum.dev.AppExecutors$$ExternalSyntheticLambda1
            @Override // java.util.concurrent.ThreadFactory
            public final Thread newThread(Runnable runnable) {
                return AppExecutors.lambda$static$1(runnable);
            }
        });
        SERIAL = Executors.newSingleThreadExecutor(new ThreadFactory() { // from class: com.harleytg.forum.dev.AppExecutors$$ExternalSyntheticLambda2
            @Override // java.util.concurrent.ThreadFactory
            public final Thread newThread(Runnable runnable) {
                return AppExecutors.lambda$static$2(runnable);
            }
        });
        SCHEDULER = Executors.newScheduledThreadPool(2, new ThreadFactory() { // from class: com.harleytg.forum.dev.AppExecutors$$ExternalSyntheticLambda3
            @Override // java.util.concurrent.ThreadFactory
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

    private AppExecutors() {
    }
}


// ---- BuildInfo.java ----
/** Build identity for the Dev/Beta Harley's Clan Forum Android app. */
final class BuildInfo {
    static final boolean ALLOW_UPDATE_CHANNEL_SWITCH = false;
    static final String APK_FILE_NAME = "HCF-Beta-v1.2.apk";
    static final String BRAND = "Harley's Studios";
    static final String BASE_VERSION = "1.2";
    static final String BUILD_TAG = "Beta / Development Build";
    static final String CHANNEL = "Dev";
    static final String DEFAULT_UPDATE_CHANNEL = "dev";
    static final String DEVELOPMENT_BUILD_LABEL = "v1.2 (100000106) • " + BUILD_TAG;
    static final boolean ENABLE_DEV_TEST_MENU = true;
    static final boolean FCM_CONFIGURED = false;
    static final boolean FIREBASE_WEB_CONFIG_BUNDLED = true;
    static final int INTERNAL_BUILD = 124;
    static final String META_LINE = "v1.2";
    static final String PATCH_NAME = "v1.2";
    static final String RELEASE_STAGE = "Development";
    static final String SESSION_CLIENT = "Harley's Clan Forum App";
    static final String UPDATE_DEV_BRANCH = "dev";
    static final String UPDATE_REPOSITORY = "harleytg-studios/hcf-app";
    static final String UPDATE_STABLE_BRANCH = "stable";
    static final String USER_AGENT_MARKER = "HarleysClanForumApp/1.2 Build/100000106";
    static final String VERSION = "1.2";
    static final int VERSION_CODE = 100000106;
    static final String VERSION_BUILD_LINE = "v" + VERSION + " (" + VERSION_CODE + ") • " + BUILD_TAG;
    static final String VERSION_CODE_SCHEME = "dev-version-v1";
    static final String VERSION_TAG = "v1.2";
    static final String REMOTE_DOMAIN_CONFIG = "https://raw.githubusercontent.com/harleytg-studios/hcf-app/main/configs/domains.config";

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

    private static int moderateThermalStatus() {
        return 2;
    }

    private static int severeThermalStatus() {
        return 3;
    }

    static String saved(SharedPreferences sharedPreferences) {
        if (sharedPreferences == null) {
            return AUTO;
        }
        String string = sharedPreferences.getString("performance_profile", "");
        return (PERFORMANCE.equals(string) || BALANCED.equals(string) || QUALITY.equals(string) || AUTO.equals(string)) ? string : AUTO;
    }

    static void save(SharedPreferences sharedPreferences, String str) {
        if (sharedPreferences == null) {
            return;
        }
        String normalize = normalize(str);
        sharedPreferences.edit().putString("performance_profile", normalize).putBoolean("performance_mode", PERFORMANCE.equals(normalize)).apply();
    }

    static String resolve(Context context, SharedPreferences sharedPreferences) {
        String saved = saved(sharedPreferences);
        if (!AUTO.equals(saved)) {
            return saved;
        }
        String autoRuntime = autoRuntime(context, sharedPreferences);
        return AUTO_REALTIME.equals(autoRuntime) ? QUALITY : AUTO_BALANCED.equals(autoRuntime) ? BALANCED : PERFORMANCE;
    }

    static String autoRuntime(Context context, SharedPreferences sharedPreferences) {
        String str;
        String str2;
        String str3;
        if (context == null) {
            return AUTO_BALANCED;
        }
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
            if (isBatterySaver) {
                str = "Battery Saver active";
            } else {
                str = thermalStatus >= severeThermalStatus() ? "Thermal pressure" : "Critical memory pressure";
            }
            RuntimeDiagnostics.profile("Auto • Extreme Saver", str);
            return AUTO_EXTREME;
        }
        if ((!isCharging && batteryPercent >= 0 && batteryPercent <= 10) || memory.lowRam || ((memory.total > 0 && memory.total <= THREE_GB) || ((memory.available > 0 && memory.available <= LOW_AVAILABLE_BYTES) || safeCpuCount <= 4 || i >= 3))) {
            if (!isCharging && batteryPercent >= 0 && batteryPercent <= 10) {
                str3 = "Very low battery";
            } else {
                str3 = memory.lowRam ? "Android low-RAM device" : safeCpuCount <= 4 ? "Lower CPU class" : "Memory/WebView pressure";
            }
            RuntimeDiagnostics.profile("Auto • Performance", str3);
            return AUTO_PERFORMANCE;
        }
        if (thermalStatus >= moderateThermalStatus() || networkMetered || lastLatencyMs >= 2500 || ((!isCharging && batteryPercent >= 0 && batteryPercent <= 20) || "Offline".equals(networkType) || !isInteractive)) {
            if (thermalStatus >= moderateThermalStatus()) {
                str2 = "Moderate thermal pressure";
            } else if (!isCharging && batteryPercent >= 0 && batteryPercent <= 20) {
                str2 = "Low battery";
            } else if (networkMetered) {
                str2 = "Metered network";
            } else {
                str2 = lastLatencyMs >= 2500 ? "Higher request latency" : "Conservative current state";
            }
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
    }

    static long livePollInterval(Context context, SharedPreferences sharedPreferences) {
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
    }

    static long motionDuration(Context context, SharedPreferences sharedPreferences, long j) {
        String resolve = resolve(context, sharedPreferences);
        if (PERFORMANCE.equals(resolve)) {
            return 0L;
        }
        return BALANCED.equals(resolve) ? Math.max(70L, Math.round(j * 0.62d)) : j;
    }

    static String label(String str) {
        return PERFORMANCE.equals(str) ? "Performance" : BALANCED.equals(str) ? "Balanced" : QUALITY.equals(str) ? "High Performance" : "Auto";
    }

    static String settingLabel(Context context, SharedPreferences sharedPreferences) {
        String saved = saved(sharedPreferences);
        if (!AUTO.equals(saved)) {
            return label(saved);
        }
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
        if (context == null) {
            return false;
        }
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService("power");
            if (powerManager != null) {
                return powerManager.isPowerSaveMode();
            }
            return false;
        } catch (Throwable unused) {
            return false;
        }
    }

    static int batteryPercent(Context context) {
        Intent registerReceiver = null;
        if (context == null) {
            return -1;
        }
        try {
            registerReceiver = context.registerReceiver(null, new IntentFilter("android.intent.action.BATTERY_CHANGED"));
        } catch (Throwable unused) {
        }
        if (registerReceiver == null) {
            return -1;
        }
        int intExtra = registerReceiver.getIntExtra("level", -1);
        int intExtra2 = registerReceiver.getIntExtra("scale", -1);
        if (intExtra >= 0 && intExtra2 > 0) {
            return Math.max(0, Math.min(100, Math.round((intExtra * 100.0f) / intExtra2)));
        }
        return -1;
    }

    static boolean isCharging(Context context) {
        if (context == null) {
            return false;
        }
        try {
            Intent registerReceiver = context.registerReceiver(null, new IntentFilter("android.intent.action.BATTERY_CHANGED"));
            if (registerReceiver == null) {
                return false;
            }
            int intExtra = registerReceiver.getIntExtra("status", -1);
            return intExtra == 2 || intExtra == 5;
        } catch (Throwable unused) {
            return false;
        }
    }

    private static int thermalStatus(Context context) {
        if (Build.VERSION.SDK_INT < 29 || context == null) {
            return 0;
        }
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService("power");
            if (powerManager == null) {
                return 0;
            }
            return powerManager.getCurrentThermalStatus();
        } catch (Throwable unused) {
            return 0;
        }
    }

    private static int safeCpuCount() {
        try {
            return Math.max(1, Runtime.getRuntime().availableProcessors());
        } catch (Throwable unused) {
            return 4;
        }
    }

    private static MemorySnapshot memory(Context context) {
        ActivityManager activityManager = null;
        MemorySnapshot memorySnapshot = new MemorySnapshot();
        try {
            activityManager = (ActivityManager) context.getSystemService("activity");
        } catch (Throwable unused) {
        }
        if (activityManager == null) {
            return memorySnapshot;
        }
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

        private MemorySnapshot() {
        }
    }

    private PerformanceProfile() {
    }
}


// ---- BatteryOptimizationHelper.java ----
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
        if (str == null || str.trim().isEmpty()) {
            return;
        }
        notificationMode = str.trim();
    }

    static void livePoll(long j) {
        currentLivePollMs = Math.max(0L, j);
    }

    static void syncSucceeded(long j) {
        lastObservedLatencyMs = Math.max(0L, j);
        consecutiveApiFailures = 0;
    }

    static void syncFailed() {
        consecutiveApiFailures = Math.min(99, consecutiveApiFailures + 1);
    }

    static int failures() {
        return consecutiveApiFailures;
    }

    static long lastLatencyMs() {
        return lastObservedLatencyMs;
    }

    static long notificationPollMs() {
        return currentNotificationPollMs;
    }

    static long livePollMs() {
        return currentLivePollMs;
    }

    static String notificationMode() {
        return notificationMode;
    }

    static void profile(String str, String str2) {
        if (str != null && !str.isEmpty()) {
            lastProfile = str;
        }
        if (str2 == null || str2.isEmpty()) {
            return;
        }
        lastProfileReason = str2;
    }

    static String profileLabel() {
        return lastProfile;
    }

    static String profileReason() {
        return lastProfileReason;
    }

    static int rendererRecoveries(SharedPreferences sharedPreferences) {
        if (sharedPreferences == null) {
            return 0;
        }
        return sharedPreferences.getInt("renderer_recovery_count", 0);
    }

    static String fcmState() {
        return "Native FCM transport unavailable • adaptive polling active";
    }

    static String compact(Context context, SharedPreferences sharedPreferences) {
        StringBuilder sb = new StringBuilder("Profile: ");
        sb.append(PerformanceProfile.settingLabel(context, sharedPreferences));
        sb.append("\nRuntime reason: ");
        sb.append(profileReason());
        sb.append("\nNotification sync: ");
        sb.append(notificationMode());
        sb.append("\nNotification interval: ");
        sb.append(interval(currentNotificationPollMs));
        sb.append("\nLive page interval: ");
        sb.append(interval(currentLivePollMs));
        sb.append("\nFCM: ");
        sb.append(fcmState());
        sb.append("\nNetwork: ");
        sb.append(RuntimeState.networkType(context));
        sb.append("\nBattery Saver: ");
        sb.append(PerformanceProfile.isBatterySaver(context) ? "On" : "Off");
        sb.append("\nRenderer recoveries: ");
        sb.append(rendererRecoveries(sharedPreferences));
        sb.append("\nConsecutive API failures: ");
        sb.append(consecutiveApiFailures);
        return sb.toString();
    }

    private static String interval(long j) {
        if (j <= 0) {
            return "idle";
        }
        if (j < 1000) {
            return j + " ms";
        }
        if (j % 1000 != 0) {
            return String.format(Locale.US, "%.2f s", Double.valueOf(j / 1000.0d));
        }
        return (j / 1000) + " s";
    }

    private RuntimeDiagnostics() {
    }
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

    @Override // android.app.Application.ActivityLifecycleCallbacks
    public void onActivityCreated(Activity activity, Bundle bundle) {
    }

    @Override // android.app.Application.ActivityLifecycleCallbacks
    public void onActivityDestroyed(Activity activity) {
    }

    @Override // android.app.Application.ActivityLifecycleCallbacks
    public void onActivityPaused(Activity activity) {
    }

    @Override // android.app.Application.ActivityLifecycleCallbacks
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
    }

    static void install(Application application) {
        if (application == null) {
            return;
        }
        try {
            application.registerActivityLifecycleCallbacks(INSTANCE);
        } catch (Throwable unused) {
        }
    }

    static boolean isForeground() {
        return startedActivities > 0;
    }

    static long backgroundDurationMs() {
        if (isForeground()) {
            return 0L;
        }
        return Math.max(0L, System.currentTimeMillis() - backgroundSinceMs);
    }

    static long sinceLastInteractionMs() {
        return Math.max(0L, System.currentTimeMillis() - lastInteractionAtMs);
    }

    static void noteUserInteraction() {
        lastInteractionAtMs = System.currentTimeMillis();
    }

    static void noteTrimMemory(int i) {
        memoryTrimLevel = Math.max(memoryTrimLevel, i);
    }

    static void clearMemoryPressure() {
        memoryTrimLevel = 0;
    }

    static int memoryTrimLevel() {
        return memoryTrimLevel;
    }

    static boolean isInteractive(Context context) {
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService("power");
            if (powerManager != null) {
                return powerManager.isInteractive();
            }
            return true;
        } catch (Throwable unused) {
            return true;
        }
    }

    static boolean networkAvailable(Context context) {
        return !"Offline".equals(networkType(context));
    }

    static String networkType(Context context) {
        NetworkCapabilities networkCapabilities;
        if (context == null) {
            return "Unknown";
        }
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService("connectivity");
            if (connectivityManager == null) {
                return "Unknown";
            }
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork != null && (networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)) != null && networkCapabilities.hasCapability(12)) {
                boolean z = !networkCapabilities.hasCapability(11);
                return networkCapabilities.hasTransport(1) ? z ? "Wi-Fi • metered" : "Wi-Fi" : networkCapabilities.hasTransport(3) ? "Ethernet" : networkCapabilities.hasTransport(0) ? z ? "Cellular • metered" : "Cellular" : z ? "Connected • metered" : "Connected";
            }
            return "Offline";
        } catch (Throwable unused) {
            return "Unknown";
        }
    }

    static boolean networkMetered(Context context) {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService("connectivity");
            if (connectivityManager != null) {
                return connectivityManager.isActiveNetworkMetered();
            }
            return false;
        } catch (Throwable unused) {
            return false;
        }
    }

    @Override // android.app.Application.ActivityLifecycleCallbacks
    public void onActivityStarted(Activity activity) {
        startedActivities++;
        lastForegroundAtMs = System.currentTimeMillis();
        clearMemoryPressure();
    }

    @Override // android.app.Application.ActivityLifecycleCallbacks
    public void onActivityResumed(Activity activity) {
        lastForegroundAtMs = System.currentTimeMillis();
        noteUserInteraction();
    }

    @Override // android.app.Application.ActivityLifecycleCallbacks
    public void onActivityStopped(Activity activity) {
        startedActivities = Math.max(0, startedActivities - 1);
        if (startedActivities == 0) {
            backgroundSinceMs = System.currentTimeMillis();
        }
    }

    private RuntimeState() {
    }
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

    static boolean isEnabled(Context context) {
        return context != null && prefs(context).getBoolean("telemetry_enabled", false);
    }

    static String level(Context context) {
        if (context == null) return LEVEL_BASIC;
        return LEVEL_DIAGNOSTICS.equals(prefs(context).getString("telemetry_level", LEVEL_BASIC)) ? LEVEL_DIAGNOSTICS : LEVEL_BASIC;
    }

    static boolean isDiagnostics(Context context) {
        return isEnabled(context) && LEVEL_DIAGNOSTICS.equals(level(context));
    }

    static String levelLabel(Context context) {
        return LEVEL_DIAGNOSTICS.equals(level(context)) ? "Diagnostics" : "Basic";
    }

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

    static void noteRoute(Context context, String route) {
        if (context == null) return;
        prefs(context).edit().putString("telemetry_last_route", sanitizedRoute(route, false)).apply();
    }

    static void recordBreadcrumb(Context context, String event, String detail) {
        if (!isDiagnostics(context)) return;
        try {
            SharedPreferences p = prefs(context);
            JSONArray old;
            try { old = new JSONArray(p.getString("telemetry_breadcrumbs", "[]")); }
            catch (Throwable ignored) { old = new JSONArray(); }
            JSONArray next = new JSONArray();
            for (int i = Math.max(0, old.length() - (MAX_BREADCRUMBS - 1)); i < old.length(); i++) next.put(old.opt(i));
            JSONObject item = new JSONObject();
            item.put("time", isoNow());
            item.put("event", safeToken(event, "event"));
            item.put("detail", safeDetail(detail, 180));
            next.put(item);
            p.edit().putString("telemetry_breadcrumbs", next.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    static void sendEvent(Context context, String event, String detail) {
        if (!isEnabled(context)) return;
        final Context app = context.getApplicationContext();
        final String safeEvent = safeToken(event, "app_event");
        final String safeText = safeDetail(detail, 240);
        AppExecutors.network().execute(() -> {
            try { postReport(app, buildBaseReport(app, "event", safeEvent, safeText, null)); }
            catch (Throwable error) { saveResult(app, "Last send failed"); }
        });
    }

    static void sendDiagnosticEvent(Context context, String event, String detail) {
        if (!isDiagnostics(context) || !prefs(context).getBoolean("telemetry_auto_error_reports", false)) return;
        final Context app = context.getApplicationContext();
        AppExecutors.network().execute(() -> {
            try {
                JSONObject report = buildBaseReport(app, "diagnostic_error", safeToken(event, "error"), safeDetail(detail, 420), null);
                report.put("breadcrumbs", breadcrumbs(app));
                postReport(app, report);
            } catch (Throwable ignored) {
            }
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
                } catch (Throwable ignored) {
                }
            });
        } else {
            sendEvent(context, "telemetry_test", "manual basic telemetry test from App Settings");
        }
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
        } catch (Throwable ignored) {
        }
    }

    static boolean hasPendingCrash(Context context) {
        return context != null && pendingCrashFile(context).exists();
    }

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
        notes.setMinLines(2);
        notes.setMaxLines(5);
        LinearLayout.LayoutParams notesLp = new LinearLayout.LayoutParams(-1, -2);
        notesLp.topMargin = dp(activity, 10);
        content.addView(notes, notesLp);
        final Switch identity = new Switch(activity);
        identity.setText("Include my forum identity with this report");
        identity.setChecked(p.getBoolean("telemetry_include_identity", false));
        content.addView(identity);
        TextView privacy = new TextView(activity);
        privacy.setText("Passwords, cookies, access tokens, recovery codes, posts and messages are never included.");
        privacy.setTextSize(11.0f);
        content.addView(privacy);
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Send crash report?")
                .setView(content)
                .setPositiveButton("Send Report", null)
                .setNeutralButton("Preview", null)
                .setNegativeButton("Don't Send", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                sendPendingCrash(activity, notes.getText().toString(), identity.isChecked());
                Toast.makeText(activity, "Crash report queued: " + reportId, Toast.LENGTH_LONG).show();
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> showTextDialog(activity, "Crash report preview", previewPendingReport(activity, notes.getText().toString(), identity.isChecked())));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                deletePendingCrash(activity);
                addHistory(activity, reportId, "crash", "discarded");
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    static void showManualFeedbackDialog(final Activity activity) {
        if (activity == null) return;
        if (!isEnabled(activity)) {
            Toast.makeText(activity, "Enable Telemetry Services first.", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        content.setPadding(pad, pad / 2, pad, 0);
        final EditText details = new EditText(activity);
        details.setHint("Describe the problem or feedback");
        details.setMinLines(3);
        details.setMaxLines(7);
        content.addView(details);
        final Switch identity = new Switch(activity);
        identity.setText("Include my forum identity with this report");
        identity.setChecked(prefs(activity).getBoolean("telemetry_include_identity", false));
        content.addView(identity);
        new AlertDialog.Builder(activity).setTitle("Send diagnostic feedback").setView(content)
                .setPositiveButton("Send", (dialog, which) -> sendManualFeedback(activity, safeDetail(details.getText().toString(), 900), identity.isChecked()))
                .setNegativeButton("Cancel", null).show();
    }

    static void showPreview(Activity activity) {
        if (activity != null) showTextDialog(activity, "Telemetry report preview", previewReport(activity));
    }

    static void showHistory(Activity activity) {
        if (activity != null) showTextDialog(activity, "Telemetry report history", historyText(activity));
    }

    static void clearLocalReports(Context context) {
        if (context == null) return;
        try { pendingCrashFile(context).delete(); } catch (Throwable ignored) {}
        prefs(context).edit().remove("telemetry_report_history").remove("telemetry_pending_crash_id").remove("telemetry_breadcrumbs").apply();
    }

    static String previewReport(Context context) {
        try {
            JSONObject report = buildBaseReport(context, "preview", "settings_preview", "Example of the data this app would send with the current telemetry settings.", null);
            if (LEVEL_DIAGNOSTICS.equals(level(context))) report.put("breadcrumbs", breadcrumbs(context));
            return report.toString(2);
        } catch (Throwable error) {
            return "Preview unavailable: " + error.getClass().getSimpleName();
        }
    }

    private static void sendPendingCrash(Context context, final String notes, final boolean includeIdentity) {
        final Context app = context.getApplicationContext();
        final JSONObject pending = readPendingCrash(app);
        if (pending == null) return;
        AppExecutors.network().execute(() -> {
            try {
                if (postReport(app, prepareReportForSend(app, pending, notes, includeIdentity))) deletePendingCrash(app);
            } catch (Throwable error) {
                saveResult(app, "Crash report send failed");
            }
        });
    }

    private static String previewPendingReport(Context context, String notes, boolean includeIdentity) {
        try {
            JSONObject pending = readPendingCrash(context);
            return pending == null ? "No pending crash report." : prepareReportForSend(context, pending, notes, includeIdentity).toString(2);
        } catch (Throwable error) {
            return "Preview unavailable: " + error.getClass().getSimpleName();
        }
    }

    private static void sendManualFeedback(Context context, final String details, final boolean includeIdentity) {
        final Context app = context.getApplicationContext();
        AppExecutors.network().execute(() -> {
            try {
                JSONObject report = buildBaseReport(app, "feedback", "manual_feedback", details, includeIdentity);
                if (LEVEL_DIAGNOSTICS.equals(level(app))) report.put("breadcrumbs", breadcrumbs(app));
                postReport(app, report);
            } catch (Throwable ignored) {
            }
        });
        Toast.makeText(context, "Diagnostic feedback queued.", Toast.LENGTH_SHORT).show();
    }

    private static JSONObject prepareReportForSend(Context context, JSONObject original, String notes, boolean includeIdentity) throws Exception {
        JSONObject report = new JSONObject(original.toString());
        if (notes != null && !notes.trim().isEmpty()) report.put("userFeedback", safeDetail(notes, 900));
        applyOptionalPrivacyFields(context, report, includeIdentity);
        return report;
    }

    private static JSONObject buildBaseReport(Context context, String type, String event, String detail, Boolean identityOverride) throws Exception {
        JSONObject report = new JSONObject();
        report.put("reportId", newReportId());
        report.put("type", safeToken(type, "event"));
        report.put("event", safeToken(event, "app_event"));
        report.put("timestampUtc", isoNow());
        report.put("appVersion", BuildInfo.VERSION);
        report.put("versionCode", BuildInfo.VERSION_CODE);
        report.put("internalBuild", BuildInfo.INTERNAL_BUILD);
        report.put("channel", BuildInfo.CHANNEL);
        report.put("package", context == null ? "" : context.getPackageName());
        report.put("androidApi", Build.VERSION.SDK_INT);
        report.put("orientation", orientation(context));
        if (detail != null && !detail.trim().isEmpty()) report.put("detail", safeDetail(detail, 900));
        ForumIdentity.Snapshot identity = ForumIdentity.load(context);
        report.put("identityMode", identity.loggedIn ? "SIGNED_IN" : "Guest_Protocol");
        String host = identity.host == null || identity.host.trim().isEmpty()
                ? prefs(context).getString("active_host", "forum.harleytg.com") : identity.host;
        report.put("forumHost", ForumUrlRouter.isForumHost(host) ? host : "forum.harleytg.com");
        applyOptionalPrivacyFields(context, report, identityOverride);
        return report;
    }

    private static void applyOptionalPrivacyFields(Context context, JSONObject report, Boolean identityOverride) throws Exception {
        SharedPreferences p = prefs(context);
        boolean includeDevice = p.getBoolean("telemetry_include_device_model", false);
        boolean includeRoute = p.getBoolean("telemetry_include_route", false);
        boolean includeIdentity = identityOverride != null ? identityOverride.booleanValue() : p.getBoolean("telemetry_include_identity", false);
        boolean includeEmail = includeIdentity && p.getBoolean("telemetry_include_email", false);
        if (includeDevice) report.put("device", safeDetail(Build.MANUFACTURER + " " + Build.MODEL, 120)); else report.remove("device");
        if (includeRoute) {
            String route = p.getString("telemetry_last_route", "");
            if (route != null && !route.isEmpty()) report.put("route", sanitizedRoute(route, includeIdentity));
        } else report.remove("route");
        if (includeIdentity) {
            ForumIdentity.Snapshot identity = ForumIdentity.load(context);
            if (identity.loggedIn) {
                JSONObject forumIdentity = new JSONObject();
                forumIdentity.put("displayName", safeDetail(identity.displayName, 120));
                forumIdentity.put("username", safeDetail(identity.username, 120));
                if (!identity.groups.isEmpty()) forumIdentity.put("groups", safeDetail(identity.groups, 240));
                if (includeEmail && !identity.email.isEmpty()) forumIdentity.put("email", safeDetail(identity.email, 180));
                report.put("forumIdentity", forumIdentity);
            }
        } else report.remove("forumIdentity");
    }

    private static boolean postReport(Context context, JSONObject report) {
        HttpsURLConnection connection = null;
        String reportId = report == null ? "HCF-REPORT" : report.optString("reportId", "HCF-REPORT");
        String type = report == null ? "event" : report.optString("type", "event");
        try {
            String endpoint = decryptWebhook(context);
            if (endpoint == null || !endpoint.startsWith("https://discord.com/api/webhooks/")) {
                saveResult(context, "Blocked: endpoint verification failed");
                addHistory(context, reportId, type, "blocked");
                return false;
            }
            byte[] body = discordPayload(report).toString().getBytes(StandardCharsets.UTF_8);
            connection = (HttpsURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("User-Agent", "HarleysClanForumTelemetry/" + BuildInfo.VERSION);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
                output.flush();
            }
            int code = connection.getResponseCode();
            if (code >= 200 && code < 300) {
                saveResult(context, "Last send succeeded • " + reportId);
                addHistory(context, reportId, type, "sent");
                AppLogger.info(context, "telemetry_sent", reportId + " | " + type);
                return true;
            }
            saveResult(context, "Last send failed (HTTP " + code + ")");
            addHistory(context, reportId, type, "HTTP " + code);
            AppLogger.warn(context, "telemetry_http", reportId + " | " + code);
        } catch (Throwable error) {
            saveResult(context, "Last send failed");
            addHistory(context, reportId, type, "failed");
            AppLogger.warn(context, "telemetry_failed", reportId + " | " + error.getClass().getSimpleName());
        } finally {
            if (connection != null) connection.disconnect();
        }
        return false;
    }

    private static JSONObject discordPayload(JSONObject report) throws Exception {
        JSONObject root = new JSONObject();
        root.put("username", "HCF Diagnostics");
        root.put("allowed_mentions", new JSONObject().put("parse", new JSONArray()));
        String type = report.optString("type", "event");
        int color = "crash".equals(type) ? 15022389 : (type.contains("error") ? 15769600 : 47344);
        JSONObject embed = new JSONObject();
        embed.put("title", ("crash".equals(type) ? "🔴 Crash" : "HCF Diagnostics") + " • " + report.optString("event", type));
        embed.put("color", color);
        String detail = report.optString("detail", "");
        if (!detail.isEmpty()) embed.put("description", safeDetail(detail, 900));
        JSONArray fields = new JSONArray();
        fields.put(field("Report ID", report.optString("reportId", "—"), true));
        fields.put(field("App", report.optString("appVersion", BuildInfo.VERSION) + " • " + report.optString("channel", BuildInfo.CHANNEL), true));
        fields.put(field("Build", report.optInt("internalBuild", BuildInfo.INTERNAL_BUILD) + " / " + report.optInt("versionCode", BuildInfo.VERSION_CODE), true));
        fields.put(field("Android", "API " + report.optInt("androidApi", Build.VERSION.SDK_INT), true));
        fields.put(field("Orientation", report.optString("orientation", "unknown"), true));
        fields.put(field("Identity mode", report.optString("identityMode", "Guest_Protocol"), true));
        fields.put(field("Forum host", report.optString("forumHost", "—"), false));
        if (report.has("device")) fields.put(field("Device", report.optString("device", "—"), false));
        if (report.has("route")) fields.put(field("Route", report.optString("route", "—"), false));
        JSONObject forumIdentity = report.optJSONObject("forumIdentity");
        if (forumIdentity != null) {
            StringBuilder identity = new StringBuilder();
            if (!forumIdentity.optString("displayName", "").isEmpty()) identity.append(forumIdentity.optString("displayName")).append('\n');
            if (!forumIdentity.optString("username", "").isEmpty()) identity.append('@').append(forumIdentity.optString("username")).append('\n');
            if (!forumIdentity.optString("groups", "").isEmpty()) identity.append("Groups: ").append(forumIdentity.optString("groups")).append('\n');
            if (!forumIdentity.optString("email", "").isEmpty()) identity.append("Email: ").append(forumIdentity.optString("email"));
            fields.put(field("Forum identity (user opted in)", safeDetail(identity.toString(), 900), false));
        }
        if (report.has("exception")) fields.put(field("Exception", report.optString("exception", "—") + (report.optString("message", "").isEmpty() ? "" : "\n" + report.optString("message")), false));
        if (report.has("userFeedback")) fields.put(field("User feedback", report.optString("userFeedback", "—"), false));
        if (report.has("stackTrace")) fields.put(field("Stack trace", "```\n" + safeDetail(report.optString("stackTrace", ""), 900) + "\n```", false));
        JSONArray crumbs = report.optJSONArray("breadcrumbs");
        if (crumbs != null && crumbs.length() > 0) fields.put(field("Recent app events", breadcrumbText(crumbs), false));
        embed.put("fields", fields);
        embed.put("footer", new JSONObject().put("text", "HCF opt-in diagnostics • sensitive credentials and forum content excluded"));
        embed.put("timestamp", report.optString("timestampUtc", isoNow()));
        root.put("embeds", new JSONArray().put(embed));
        return root;
    }

    private static JSONObject field(String name, String value, boolean inline) throws Exception {
        String safe = value == null || value.trim().isEmpty() ? "—" : value.trim();
        if (safe.length() > 1000) safe = safe.substring(0, 1000) + "…";
        return new JSONObject().put("name", name).put("value", safe).put("inline", inline);
    }

    private static JSONArray breadcrumbs(Context context) {
        try { return new JSONArray(prefs(context).getString("telemetry_breadcrumbs", "[]")); }
        catch (Throwable ignored) { return new JSONArray(); }
    }

    private static String breadcrumbText(JSONArray items) {
        StringBuilder out = new StringBuilder();
        for (int i = Math.max(0, items.length() - 12); i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            if (out.length() > 0) out.append('\n');
            out.append(item.optString("event", "event"));
            String detail = item.optString("detail", "");
            if (!detail.isEmpty()) out.append(" • ").append(detail);
            if (out.length() > 880) break;
        }
        return safeDetail(out.toString(), 900);
    }

    private static String sanitizeStack(Throwable error) {
        try {
            StringWriter writer = new StringWriter();
            error.printStackTrace(new PrintWriter(writer));
            String safe = writer.toString().replace((char) 0, ' ').replace("\r", "")
                    .replaceAll("https?://[^\\s?]+\\?[^\\s]+", "[URL_QUERY_REDACTED]")
                    .replaceAll("(?i)(token|authorization|cookie|password)=?[^\\s,;]+", "$1=[REDACTED]");
            return safe.length() <= 6000 ? safe : safe.substring(0, 6000) + "…";
        } catch (Throwable ignored) {
            return error.getClass().getName();
        }
    }

    private static String sanitizedRoute(String value, boolean identityAllowed) {
        if (value == null || value.trim().isEmpty()) return "";
        try {
            String path = Uri.parse(value).getPath();
            if (path == null || path.isEmpty()) path = value.startsWith("/") ? value : "/";
            if (!identityAllowed) path = path.replaceAll("(?i)^/u/[^/]+", "/u/[user]");
            path = path.replaceAll("(?i)^/d/(\\d+)[^/]*", "/d/$1");
            return path.length() > 240 ? path.substring(0, 240) : path;
        } catch (Throwable ignored) {
            return "[route-unavailable]";
        }
    }

    private static String orientation(Context context) {
        try {
            int orientation = context.getResources().getConfiguration().orientation;
            return orientation == 2 ? "landscape" : (orientation == 1 ? "portrait" : "unknown");
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static String newReportId() {
        String date;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            date = format.format(new Date());
        } catch (Throwable ignored) {
            date = "00000000";
        }
        byte[] random = new byte[3];
        new SecureRandom().nextBytes(random);
        StringBuilder suffix = new StringBuilder();
        for (byte b : random) suffix.append(String.format(Locale.US, "%02X", b & 255));
        return "HCF-" + date + "-" + suffix;
    }

    private static void addHistory(Context context, String reportId, String type, String result) {
        try {
            SharedPreferences p = prefs(context);
            JSONArray old;
            try { old = new JSONArray(p.getString("telemetry_report_history", "[]")); }
            catch (Throwable ignored) { old = new JSONArray(); }
            JSONArray next = new JSONArray();
            for (int i = Math.max(0, old.length() - (MAX_HISTORY - 1)); i < old.length(); i++) next.put(old.opt(i));
            next.put(new JSONObject().put("time", isoNow()).put("reportId", reportId).put("type", type).put("result", result));
            p.edit().putString("telemetry_report_history", next.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    private static String historyText(Context context) {
        try {
            JSONArray history = new JSONArray(prefs(context).getString("telemetry_report_history", "[]"));
            if (history.length() == 0) return "No telemetry reports have been sent or discarded on this device.";
            StringBuilder out = new StringBuilder();
            for (int i = history.length() - 1; i >= 0; i--) {
                JSONObject item = history.optJSONObject(i);
                if (item == null) continue;
                out.append(item.optString("time", "")).append('\n')
                        .append(item.optString("reportId", "HCF-REPORT")).append(" • ")
                        .append(item.optString("type", "event")).append(" • ")
                        .append(item.optString("result", "unknown")).append("\n\n");
            }
            return out.toString().trim();
        } catch (Throwable ignored) {
            return "History unavailable.";
        }
    }

    private static JSONObject readPendingCrash(Context context) {
        try {
            File file = pendingCrashFile(context);
            return file.exists() ? new JSONObject(readText(file)) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void deletePendingCrash(Context context) {
        try { pendingCrashFile(context).delete(); } catch (Throwable ignored) {}
        try { prefs(context).edit().remove("telemetry_pending_crash_id").apply(); } catch (Throwable ignored) {}
    }

    private static File pendingCrashFile(Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(), "telemetry");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, PENDING_CRASH_FILE);
    }

    private static void writeText(File file, String value) throws Exception {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
            writer.write(value == null ? "" : value);
            writer.flush();
        }
    }

    private static String readText(File file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
            return out.toString();
        }
    }

    private static String decryptWebhook(Context context) throws Exception {
        String fingerprint = signingCertificateSha256(context);
        if (fingerprint.isEmpty()) throw new IllegalStateException("signing certificate unavailable");
        byte[] key = MessageDigest.getInstance("SHA-256").digest(("HCF_TELEMETRY_V1|" + context.getPackageName() + "|" + fingerprint + "|HCF-Telemetry-Discord-Relay-v1").getBytes(StandardCharsets.UTF_8));
        SecretKeySpec secret = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secret, new GCMParameterSpec(128, Base64.decode(WEBHOOK_IV_B64, Base64.NO_WRAP)));
        cipher.updateAAD(AAD.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(Base64.decode(WEBHOOK_CIPHERTEXT_B64, Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }

    private static String signingCertificateSha256(Context context) throws Exception {
        PackageManager manager = context.getPackageManager();
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28) {
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            signatures = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        } else {
            signatures = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES).signatures;
        }
        if (signatures == null || signatures.length == 0) return "";
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) out.append(String.format(Locale.US, "%02x", b & 255));
        return out.toString();
    }

    private static String isoNow() {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            return format.format(new Date());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String safeToken(String value, String fallback) {
        String safe = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isEmpty()) safe = fallback;
        return safe.length() > 64 ? safe.substring(0, 64) : safe;
    }

    private static String safeDetail(String value, int max) {
        if (value == null) return "";
        String safe = value.replace((char) 0, ' ').replace("\r", "").trim()
                .replaceAll("(?i)(authorization|cookie|password|access[_-]?token|refresh[_-]?token)\\s*[:=]\\s*[^\\s,;]+", "$1=[REDACTED]");
        return safe.length() <= max ? safe : safe.substring(0, max) + "…";
    }

    private static void saveResult(Context context, String value) {
        try { prefs(context).edit().putString("telemetry_last_result", value).apply(); } catch (Throwable ignored) {}
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("hcf_app", 0);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void showTextDialog(Activity activity, String title, String body) {
        TextView text = new TextView(activity);
        text.setText(body == null ? "" : body);
        text.setTextSize(12.0f);
        text.setTextIsSelectable(true);
        text.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(text);
        new AlertDialog.Builder(activity).setTitle(title).setView(scroll).setPositiveButton("Close", null).show();
    }

    private TelemetryService() {}
}

// ---- Consolidated from HcfSecurityAndPrefs.java ----
final class HcfSecurityAndPrefs {
    private HcfSecurityAndPrefs() {}
}

// ---- AppSecurity.java ----
/* loaded from: classes.dex */
final class AppSecurity {

    static final class ApkVerification {
        final String message;
        final boolean ok;

        ApkVerification(boolean z, String str) {
            this.ok = z;
            this.message = str;
        }
    }

    static boolean canInstallUpdates(Context context) {
        if (context == null) {
            return true;
        }
        try {
            return context.getPackageManager().canRequestPackageInstalls();
        } catch (Throwable unused) {
            return false;
        }
    }

    static boolean isTrustedReleaseDownload(String str) {
        String trim;
        String path;
        if (str == null) {
            trim = "";
        } else {
            try {
                trim = str.trim();
            } catch (Throwable unused) {
                return false;
            }
        }
        Uri parse = Uri.parse(trim);
        if ("https".equalsIgnoreCase(parse.getScheme()) && "github.com".equalsIgnoreCase(parse.getHost()) && (path = parse.getPath()) != null && path.toLowerCase(Locale.US).startsWith("/harleytg-studios/hcf-app/releases/download/".toLowerCase(Locale.US))) {
            return path.toLowerCase(Locale.US).endsWith(".apk");
        }
        return false;
    }

    static ApkVerification verifyDownloadedUpdate(Context context, long j) {
        if (context == null || j <= 0) {
            return new ApkVerification(false, "Update file is unavailable.");
        }
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, 0);
            String string = prefs.getString(AppPrefs.UPDATE_DOWNLOAD_NAME, "");
            if (string != null && !string.trim().isEmpty()) {
                File externalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                File file = externalFilesDir == null ? null : new File(externalFilesDir, string);
                if (file != null && file.isFile()) {
                    if (((DownloadManager) context.getSystemService("download")) != null && AppUpdateDownloader.status(context, j) != 8) {
                        return new ApkVerification(false, "Update download is not complete.");
                    }
                    PackageManager packageManager = context.getPackageManager();
                    int i = Build.VERSION.SDK_INT >= 28 ? 134217728 : 64;
                    PackageInfo packageArchiveInfo = packageManager.getPackageArchiveInfo(file.getAbsolutePath(), i);
                    PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), i);
                    if (packageArchiveInfo != null && packageInfo != null) {
                        if (!context.getPackageName().equals(packageArchiveInfo.packageName)) {
                            return new ApkVerification(false, "Blocked update: APK package name does not match this app.");
                        }
                        long candidateVersion = Build.VERSION.SDK_INT >= 28 ? packageArchiveInfo.getLongVersionCode() : packageArchiveInfo.versionCode;
                        long installedVersion = Build.VERSION.SDK_INT >= 28 ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
                        long expectedVersion = prefs.getLong(AppPrefs.UPDATE_DOWNLOAD_VERSION_CODE, -1L);
                        String expectedSha256 = prefs.getString(AppPrefs.UPDATE_DOWNLOAD_SHA256, "");
                        if (candidateVersion != expectedVersion) {
                            return new ApkVerification(false, "Blocked update: APK versionCode changed after the release check.");
                        }
                        if (!isSha256(expectedSha256)) {
                            return new ApkVerification(false, "Blocked update: expected APK SHA-256 is missing.");
                        }
                        String downloadedSha256 = fileSha256(file);
                        if (!expectedSha256.equalsIgnoreCase(downloadedSha256)) {
                            return new ApkVerification(false, "Blocked update: APK SHA-256 does not match the checked release.");
                        }
                        if (candidateVersion < installedVersion) {
                            return new ApkVerification(false, "Blocked update: APK versionCode is older than the installed version.");
                        }
                        if (candidateVersion == installedVersion) {
                            String installedSha256 = installedApkSha256(context);
                            if (!isSha256(installedSha256) || installedSha256.equalsIgnoreCase(downloadedSha256)) {
                                return new ApkVerification(false, "Blocked update: this exact APK is already installed.");
                            }
                        }
                        if (signaturesCompatible(packageInfo, packageArchiveInfo)) {
                            String mode = candidateVersion == installedVersion ? "same-version hash revision" : "newer versionCode";
                            return new ApkVerification(true, "Verified package, SHA-256, " + mode + " and signing certificate lineage.");
                        }
                        return new ApkVerification(false, "Blocked update: signing certificate does not match the installed app.");
                    }
                    return new ApkVerification(false, "APK package metadata could not be verified.");
                }
                return new ApkVerification(false, "Downloaded APK could not be found.");
            }
            return new ApkVerification(false, "Update filename is missing.");
        } catch (Throwable th) {
            return new ApkVerification(false, "Update security check failed: " + th.getClass().getSimpleName());
        }
    }

    static String securitySummary(Context context) {
        return "HTTPS only • SSL errors blocked • mixed HTTP blocked\nThird-party cookies blocked • file URL access blocked\nWebView debugging off • app backup disabled\nUpdate APK signature verification on • installer permission: ".concat(canInstallUpdates(context) ? "Allowed" : "Needs approval");
    }

    private static boolean signaturesCompatible(PackageInfo installed, PackageInfo candidate) throws Exception {
        Set<String> installedCurrent = currentSigningDigests(installed);
        Set<String> candidateCurrent = currentSigningDigests(candidate);
        if (installedCurrent.isEmpty() || candidateCurrent.isEmpty()) return false;
        if (installedCurrent.equals(candidateCurrent)) return true;

        if (Build.VERSION.SDK_INT >= 28) {
            boolean installedMulti = installed.signingInfo != null && installed.signingInfo.hasMultipleSigners();
            boolean candidateMulti = candidate.signingInfo != null && candidate.signingInfo.hasMultipleSigners();
            if (installedMulti || candidateMulti) return false;
        } else {
            return false;
        }

        Set<String> candidateHistory = signingHistoryDigests(candidate);
        return candidateHistory.containsAll(installedCurrent);
    }

    static String installedApkSha256(Context context) throws Exception {
        if (context == null || context.getApplicationInfo() == null) return "";
        String sourceDir = context.getApplicationInfo().sourceDir;
        return sourceDir == null || sourceDir.trim().isEmpty() ? "" : fileSha256(new File(sourceDir));
    }

    static String fileSha256(File file) throws Exception {
        if (file == null || !file.isFile()) return "";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        StringBuilder out = new StringBuilder(64);
        for (byte value : digest.digest()) out.append(String.format(Locale.US, "%02x", Integer.valueOf(value & 255)));
        return out.toString();
    }

    static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }

    private static Set<String> currentSigningDigests(PackageInfo info) throws Exception {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (info == null) return out;
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28) {
            if (info.signingInfo == null) return out;
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        addDigests(out, signatures);
        return out;
    }

    private static Set<String> signingHistoryDigests(PackageInfo info) throws Exception {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (info == null) return out;
        if (Build.VERSION.SDK_INT >= 28) {
            if (info.signingInfo == null) return out;
            Signature[] signatures = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
            addDigests(out, signatures);
        } else {
            addDigests(out, info.signatures);
        }
        return out;
    }

    private static void addDigests(Set<String> out, Signature[] signatures) throws Exception {
        if (signatures == null) return;
        for (Signature signature : signatures) {
            if (signature == null) continue;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte value : digest) sb.append(String.format(Locale.US, "%02x", Integer.valueOf(value & 255)));
            out.add(sb.toString());
        }
    }

    private AppSecurity() {
    }
}


// ---- LinkSafety.java ----
/* loaded from: classes.dex */
final class LinkSafety {
    private static final Pattern IPV4 = Pattern.compile("^(?:\\d{1,3}\\.){3}\\d{1,3}$");
    private static final Pattern IPV6ISH = Pattern.compile("^[0-9a-fA-F:]+$");

    enum Status {
        OFFICIAL("🛡️", "Official"),
        EXTERNAL("🔗", "External"),
        SUSPICIOUS("⚠️", "Suspicious"),
        BLOCKED("⛔", "Blocked");

        final String icon;
        final String label;

        Status(String str, String str2) {
            this.icon = str;
            this.label = str2;
        }

        String display() {
            return this.icon + " " + this.label;
        }
    }

    static final class Result {
        final String host;
        final String reason;
        final Status status;

        Result(Status status, String str, String str2) {
            this.status = status;
            this.host = str == null ? "" : str;
            this.reason = str2 == null ? "" : str2;
        }
    }

    private LinkSafety() {
    }

    static Result classify(Uri uri) {
        if (uri == null) {
            return blocked("", "Invalid link");
        }
        if (ForumUrlRouter.isForumUrl(uri)) {
            return new Result(Status.OFFICIAL, safeHost(uri), "Harley's Clan Forum trusted domain");
        }
        String lower = lower(uri.getScheme());
        if (!"http".equals(lower) && !"https".equals(lower)) {
            return blocked(safeHost(uri), "Unsupported link type");
        }
        String canonicalHost = canonicalHost(uri.getHost());
        if (canonicalHost.isEmpty()) {
            return blocked("", "Missing website domain");
        }
        if (!"https".equals(lower)) {
            return suspicious(canonicalHost, "This website is not using HTTPS");
        }
        if (uri.getUserInfo() != null && !uri.getUserInfo().isEmpty()) {
            return suspicious(canonicalHost, "The address contains embedded sign-in information");
        }
        if (canonicalHost.startsWith("xn--") || canonicalHost.contains(".xn--")) {
            return suspicious(canonicalHost, "The domain uses an encoded international name");
        }
        if (isIpAddress(canonicalHost)) {
            return suspicious(canonicalHost, "The link uses a direct IP address instead of a normal domain");
        }
        if (looksLikeForumImpersonation(canonicalHost)) {
            return suspicious(canonicalHost, "This domain resembles Harley's Clan Forum but is not an official domain");
        }
        return new Result(Status.EXTERNAL, canonicalHost, "External website");
    }

    static String canonicalHost(String str) {
        if (str == null) {
            return "";
        }
        String lowerCase = str.trim().toLowerCase(Locale.US);
        if (lowerCase.endsWith(".")) {
            lowerCase = lowerCase.substring(0, lowerCase.length() - 1);
        }
        try {
            return IDN.toASCII(lowerCase, 1).toLowerCase(Locale.US);
        } catch (Throwable unused) {
            return lowerCase;
        }
    }

    private static boolean looksLikeForumImpersonation(String str) {
        if (ForumUrlRouter.isForumHost(str)) {
            return false;
        }
        String replace = str.replace("-", "").replace("_", "");
        return replace.contains("harleytg") || replace.contains("harleysclan") || replace.contains("harleyclan") || (replace.contains("harley") && replace.contains("forum"));
    }

    private static boolean isIpAddress(String str) {
        if (IPV4.matcher(str).matches()) {
            return true;
        }
        return str.indexOf(58) >= 0 && IPV6ISH.matcher(str).matches();
    }

    private static Result suspicious(String str, String str2) {
        return new Result(Status.SUSPICIOUS, str, str2);
    }

    private static Result blocked(String str, String str2) {
        return new Result(Status.BLOCKED, str, str2);
    }

    private static String safeHost(Uri uri) {
        return canonicalHost(uri == null ? null : uri.getHost());
    }

    private static String lower(String str) {
        return str == null ? "" : str.trim().toLowerCase(Locale.US);
    }
}


// ---- AppPrefs.java ----
/* loaded from: classes.dex */
final class AppPrefs {
    static final String ACTIVE_HOST = "active_host";
    static final String APP_HAS_LAUNCHED = "app_has_launched";
    static final String APP_THEME = "app_theme";
    static final String AUTO_FAILOVER = "auto_failover";
    static final String BACKGROUND_NOTIFICATION_SYNC = "background_notification_sync";
    static final String COMPACT_HEADER = "compact_header";
    static final String DELIVERED_NOTIFICATION_IDS = "delivered_notification_ids";
    static final String EXTERNAL_LINKS = "external_links";
    static final String FALLBACK_UNTIL = "fallback_until";
    static final String FILE = "hcf_app";
    static final String FIREBASE_CONFIG_CACHE = "firebase_config_cache";
    static final String FIREBASE_CONFIG_SOURCE = "firebase_config_source";
    static final String FIREBASE_CONFIG_URL = "firebase_config_url";
    static final String FORUM_AUTO_THEME = "forum_auto_theme";
    static final String FORUM_AUTO_THEME_UPDATED_AT = "forum_auto_theme_updated_at";
    static final String IDENTITY_ADMIN = "identity_admin";
    static final String IDENTITY_AVATAR_URL = "identity_avatar_url";
    static final String IDENTITY_COMMENT_COUNT = "identity_comment_count";
    static final String IDENTITY_CONNECTIONS = "identity_connections";
    static final String IDENTITY_DISCUSSION_COUNT = "identity_discussion_count";
    static final String IDENTITY_DISPLAY_NAME = "identity_display_name";
    static final String IDENTITY_EMAIL = "identity_email";
    static final String IDENTITY_EMAIL_CONFIRMED = "identity_email_confirmed";
    static final String IDENTITY_GROUPS = "identity_groups";
    static final String IDENTITY_HOST = "identity_host";
    static final String IDENTITY_JOIN_TIME = "identity_join_time";
    static final String IDENTITY_LAST_SEEN_AT = "identity_last_seen_at";
    static final String IDENTITY_LOGGED_IN = "identity_logged_in";
    static final String IDENTITY_NEW_NOTIFICATIONS = "identity_new_notifications";
    static final String IDENTITY_SECURITY_ACTIVE_SESSION_COUNT = "identity_security_active_session_count";
    static final String IDENTITY_SECURITY_EMAIL_CONTROLS = "identity_security_email_controls";
    static final String IDENTITY_SECURITY_HOST = "identity_security_host";
    static final String IDENTITY_SECURITY_PASSWORD_CONTROLS = "identity_security_password_controls";
    static final String IDENTITY_SECURITY_PATH = "identity_security_path";
    static final String IDENTITY_SECURITY_PROVIDERS = "identity_security_providers";
    static final String IDENTITY_SECURITY_SEEN = "identity_security_seen";
    static final String IDENTITY_SECURITY_SESSION_COUNT = "identity_security_session_count";
    static final String IDENTITY_SECURITY_SYNCED_AT = "identity_security_synced_at";
    static final String IDENTITY_SECURITY_TWO_FACTOR_CONTROLS = "identity_security_two_factor_controls";
    static final String IDENTITY_SLUG = "identity_slug";
    static final String IDENTITY_SYNCED_AT = "identity_synced_at";
    static final String IDENTITY_UNREAD_NOTIFICATIONS = "identity_unread_notifications";
    static final String IDENTITY_USERNAME = "identity_username";
    static final String IDENTITY_USER_ID = "identity_user_id";
    static final String INSTALL_PERMISSION_PROMPTED = "install_permission_prompted";
    static final String LAST_MAIN_PAUSED_AT = "last_main_paused_at";
    static final String LAST_NOTIFICATION_COUNT = "last_notification_count";
    static final String LAST_RECOVERABLE_URL = "last_recoverable_url";
    static final String LAST_SEEN_WHATS_NEW_VERSION = "last_seen_whats_new_version";
    static final String LIVE_FORUM_UPDATES = "live_forum_updates";
    static final String NATIVE_ACCENT = "native_accent";
    static final String NOTIFICATIONS_ENABLED = "notifications_enabled";
    static final String NOTIFICATION_LAST_COUNT_CHANGE_AT = "notification_last_count_change_at";
    static final String NOTIFICATION_LAST_SYNC_AT = "notification_last_sync_at";
    static final String NOTIFICATION_LAST_SYNC_LATENCY_MS = "notification_last_sync_latency_ms";
    static final String NOTIFICATION_LAST_SYNC_STATUS = "notification_last_sync_status";
    static final String NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked";
    static final String NOTIFICATION_PERMISSION_PROMPT_VERSION = "notification_permission_prompt_version";
    static final String PERFORMANCE_MODE = "performance_mode";
    static final String PERFORMANCE_PROFILE = "performance_profile";
    static final String PERMISSION_ONBOARDING_DONE = "permission_onboarding_done";
    static final String RENDERER_RECOVERY_COUNT = "renderer_recovery_count";
    static final String SAFE_LINKS_SEEN_DOMAINS = "safe_links_seen_domains";
    static final String SESSION_USER_ID = "session_user_id";
    static final String SETUP_COMPLETED = "setup_completed";
    static final String SETUP_SEEN = "setup_seen";
    static final String SETUP_VERSION = "setup_version";
    static final String WELCOME_SEEN = "welcome_seen";
    static final String WELCOME_VERSION = "welcome_version";
    static final String SHOW_BOTTOM_NAV = "show_bottom_nav";
    static final String SHOW_STARTUP_SCREEN = "show_startup_screen";
    static final String SHOW_URL_BAR = "show_url_bar";
    static final String SILENCE_BACKGROUND_SERVICE_NOTIFICATION = "silence_background_service_notification";
    static final String TELEMETRY_ASK_BEFORE_CRASH_REPORT = "telemetry_ask_before_crash_report";
    static final String TELEMETRY_AUTO_CRASH_REPORTS = "telemetry_auto_crash_reports";
    static final String TELEMETRY_AUTO_ERROR_REPORTS = "telemetry_auto_error_reports";
    static final String TELEMETRY_BREADCRUMBS = "telemetry_breadcrumbs";
    static final String TELEMETRY_ENABLED = "telemetry_enabled";
    static final String TELEMETRY_INCLUDE_DEVICE_MODEL = "telemetry_include_device_model";
    static final String TELEMETRY_INCLUDE_EMAIL = "telemetry_include_email";
    static final String TELEMETRY_INCLUDE_IDENTITY = "telemetry_include_identity";
    static final String TELEMETRY_INCLUDE_ROUTE = "telemetry_include_route";
    static final String TELEMETRY_LAST_HEARTBEAT = "telemetry_last_heartbeat";
    static final String TELEMETRY_LAST_RESULT = "telemetry_last_result";
    static final String TELEMETRY_LAST_ROUTE = "telemetry_last_route";
    static final String TELEMETRY_LEVEL = "telemetry_level";
    static final String TELEMETRY_PENDING_CRASH_ID = "telemetry_pending_crash_id";
    static final String TELEMETRY_REPORT_HISTORY = "telemetry_report_history";
    static final String UI_REVAMP_VERSION = "ui_revamp_version";
    static final String WIDGET_FOLLOW_APP_THEME = "widget_follow_app_theme";
    static final String WIDGET_SHOW_CONNECTED_USERNAME = "widget_show_connected_username";
    static final String WIDGET_SHOW_UNREAD_COUNT = "widget_show_unread_count";
    static final String WIDGET_COMPACT_MODE = "widget_compact_mode";
    static final String WIDGET_SHOW_LAST_UPDATED = "widget_show_last_updated";
    static final String WIDGET_DEFAULT_TAP_ACTION = "widget_default_tap_action";
    static final String UPDATE_AUTO_CHECK = "update_auto_check";
    static final String UPDATE_AUTO_DOWNLOAD = "update_auto_download";
    static final String UPDATE_AUTO_INSTALL = "update_auto_install";
    static final String UPDATE_CHANNEL = "update_channel";
    static final String UPDATE_DOWNLOAD_ID = "update_download_id";
    static final String UPDATE_DOWNLOAD_LABEL = "update_download_label";
    static final String UPDATE_DOWNLOAD_NAME = "update_download_name";
    static final String UPDATE_DOWNLOAD_SHA256 = "update_download_sha256";
    static final String UPDATE_DOWNLOAD_TAG = "update_download_tag";
    static final String UPDATE_DOWNLOAD_VERSION_CODE = "update_download_version_code";
    static final String UPDATE_INSTALL_PENDING = "update_install_pending";
    static final String UPDATE_LAST_AVAILABLE_TAG = "update_last_available_tag";
    static final String UPDATE_LAST_CHECK = "update_last_check";
    static final String UPDATE_RESUME_AFTER_PERMISSION = "update_resume_after_permission";

    private AppPrefs() {
    }
}


// ---- UiPreferences.java ----
/* loaded from: classes.dex */
final class UiPreferences {
    private static final int CURRENT_REVAMP = 4;

    static void migrate(Context context) {
        int i;
        if (context == null) {
            return;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("hcf_app", 0);
        sanitizePreferenceTypes(sharedPreferences);
        try {
            i = sharedPreferences.getInt("ui_revamp_version", 0);
        } catch (Throwable unused) {
            sharedPreferences.edit().remove("ui_revamp_version").apply();
            i = 0;
        }
        if (i >= CURRENT_REVAMP) {
            return;
        }
        sharedPreferences.edit().putBoolean("update_auto_download", false).putBoolean("show_url_bar", true).putInt("ui_revamp_version", CURRENT_REVAMP).apply();
    }

    private static void sanitizePreferenceTypes(SharedPreferences sharedPreferences) {
        try {
            Map<String, ?> all = sharedPreferences.getAll();
            SharedPreferences.Editor edit = sharedPreferences.edit();
            String[] strArr = {"notifications_enabled", "background_notification_sync", "auto_failover", "external_links", "show_url_bar", "compact_header", "show_bottom_nav", "show_startup_screen", "widget_follow_app_theme", "live_forum_updates", "performance_mode", "notification_permission_asked", "permission_onboarding_done", "install_permission_prompted", "app_has_launched", "update_auto_check", "update_auto_download", "update_install_pending", "update_resume_after_permission", "telemetry_enabled", "telemetry_auto_crash_reports", "telemetry_ask_before_crash_report", "telemetry_auto_error_reports", "telemetry_include_identity", "telemetry_include_email", "telemetry_include_device_model", "telemetry_include_route", "identity_logged_in", "identity_email_confirmed", "identity_admin", "identity_security_seen", "identity_security_password_controls", "identity_security_email_controls", "identity_security_two_factor_controls"};
            boolean z = false;
            for (int i = 0; i < strArr.length; i++) {
                z |= removeIfWrongType(all, edit, strArr[i], Boolean.class);
            }
            String[] strArr2 = {"safe_links_seen_domains", "app_theme", "performance_profile", "native_accent", "last_recoverable_url", "last_seen_whats_new_version", "session_user_id", "active_host", "delivered_notification_ids", "notification_last_sync_status", "firebase_config_url", "firebase_config_cache", "firebase_config_source", "update_channel", "update_last_available_tag", "update_download_tag", "update_download_label", "update_download_name", "update_download_sha256", "telemetry_level", "telemetry_last_route", "telemetry_breadcrumbs", "telemetry_report_history", "telemetry_pending_crash_id", "telemetry_last_result", "identity_user_id", "identity_username", "identity_slug", "identity_display_name", "identity_email", "identity_avatar_url", "identity_groups", "identity_connections", "identity_join_time", "identity_last_seen_at", "identity_host", "identity_security_providers", "identity_security_host", "identity_security_path"};
            for (int i2 = 0; i2 < strArr2.length; i2++) {
                z |= removeIfWrongType(all, edit, strArr2[i2], String.class);
            }
            String[] strArr3 = {"fallback_until", "last_main_paused_at", "notification_last_sync_at", "notification_last_sync_latency_ms", "update_last_check", "update_download_id", "update_download_version_code", "telemetry_last_heartbeat", "identity_synced_at", "identity_security_synced_at"};
            for (int i3 = 0; i3 < strArr3.length; i3++) {
                z |= removeIfWrongType(all, edit, strArr3[i3], Long.class);
            }
            String[] strArr4 = {"ui_revamp_version", "notification_permission_prompt_version", "last_notification_count", "identity_unread_notifications", "identity_new_notifications", "identity_discussion_count", "identity_comment_count", "identity_security_session_count", "identity_security_active_session_count"};
            for (int i4 = 0; i4 < strArr4.length; i4++) {
                z |= removeIfWrongType(all, edit, strArr4[i4], Integer.class);
            }
            if (z) {
                edit.apply();
            }
        } catch (Throwable unused) {
        }
    }

    private static boolean removeIfWrongType(Map<String, ?> map, SharedPreferences.Editor editor, String str, Class<?> cls) {
        Object obj;
        if (!map.containsKey(str) || (obj = map.get(str)) == null || cls.isInstance(obj)) {
            return false;
        }
        editor.remove(str);
        return true;
    }

    private UiPreferences() {
    }
}


// ---- AppLogger.java ----
/* loaded from: classes.dex */
final class AppLogger {
    private static final Object LOCK = new Object();
    private static final String LOG_DIR = "app-logs";
    private static final String LOG_FILE = "hcf-app.log";
    private static final long MAX_LOG_BYTES = 524288;
    private static final String OLD_LOG_FILE = "hcf-app.previous.log";

    static void info(Context context, String str, String str2) {
        write(context, "INFO", str, str2);
    }

    static void warn(Context context, String str, String str2) {
        write(context, "WARN", str, str2);
    }

    static void error(Context context, String str, String str2) {
        write(context, "ERROR", str, str2);
    }

    static void crash(Context context, Throwable th) {
        StringWriter stringWriter = new StringWriter();
        th.printStackTrace(new PrintWriter(stringWriter));
        write(context, "CRASH", "uncaught_exception", stringWriter.toString());
    }

    static String safeUrl(String str) {
        String str2 = "";
        if (str == null || str.isEmpty()) {
            return "";
        }
        try {
            Uri parse = Uri.parse(str);
            String scheme = parse.getScheme() == null ? "" : parse.getScheme();
            if (parse.getHost() != null) {
                str2 = parse.getHost();
            }
            return scheme + "://" + str2 + (parse.getPath() == null ? "/" : parse.getPath());
        } catch (Throwable unused) {
            return "[unparseable-url]";
        }
    }

    static String readAll(Context context) {
        synchronized (LOCK) {
            StringBuilder sb = new StringBuilder();
            appendFile(sb, oldFile(context));
            appendFile(sb, logFile(context));
            if (sb.length() == 0) {
                return "No app logs yet.";
            }
            return HcfSupportSanitizer.sanitize(sb.toString());
        }
    }

    static String readRecent(Context context, int i) {
        int max = Math.max(4096, i);
        synchronized (LOCK) {
            try {
                File oldFile = oldFile(context);
                File logFile = logFile(context);
                long length = (oldFile.exists() ? oldFile.length() : 0L) + (logFile.exists() ? logFile.length() : 0L);
                StringBuilder sb = new StringBuilder(Math.min(max + 256, 200000));
                String readTail = readTail(logFile, Math.min(max, Math.max(4096, (max * 3) / 4)));
                int max2 = Math.max(0, max - readTail.length());
                String readTail2 = max2 > 0 ? readTail(oldFile, max2) : "";
                if (length > max) {
                    sb.append("Older log entries omitted from the on-screen viewer. Export logs to save the complete local history.\n\n");
                }
                if (!readTail2.isEmpty()) {
                    sb.append(readTail2);
                }
                if (!readTail.isEmpty()) {
                    sb.append(readTail);
                }
                if (sb.length() == 0) {
                    return "No app logs yet.";
                }
                int i2 = max + 220;
                if (sb.length() > i2) {
                    return HcfSupportSanitizer.sanitize(sb.substring(sb.length() - i2));
                }
                return HcfSupportSanitizer.sanitize(sb.toString());
            } catch (Throwable unused) {
                return "App logs are temporarily unavailable.";
            }
        }
    }

    static void clear(Context context) {
        synchronized (LOCK) {
            try {
                oldFile(context).delete();
            } catch (Throwable unused) {
            }
            try {
                logFile(context).delete();
            } catch (Throwable unused2) {
            }
        }
    }

    private static void write(Context context, String str, String str2, String str3) {
        if (context == null) {
            return;
        }
        synchronized (LOCK) {
            try {
                File logFile = logFile(context);
                if (logFile.length() >= MAX_LOG_BYTES) {
                    rotate(context);
                }
                String format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date());
                String clean = clean(str2, 120);
                String clean2 = clean(str3, 12000);
                TelemetryService.recordBreadcrumb(context, clean, clean2);
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8);
                outputStreamWriter.write(format + " [" + str + "] " + clean);
                if (!clean2.isEmpty()) {
                    outputStreamWriter.write(" | " + clean2);
                }
                outputStreamWriter.write("\n");
                outputStreamWriter.flush();
                outputStreamWriter.close();
            } catch (Throwable unused) {
            }
        }
    }

    private static void rotate(Context context) {
        File logFile = logFile(context);
        File oldFile = oldFile(context);
        try {
            oldFile.delete();
        } catch (Throwable unused) {
        }
        if (logFile.exists()) {
            logFile.renameTo(oldFile);
        }
    }

    private static File logFile(Context context) {
        File file = new File(context.getApplicationContext().getFilesDir(), LOG_DIR);
        if (!file.exists()) {
            file.mkdirs();
        }
        return new File(file, LOG_FILE);
    }

    private static File oldFile(Context context) {
        File file = new File(context.getApplicationContext().getFilesDir(), LOG_DIR);
        if (!file.exists()) {
            file.mkdirs();
        }
        return new File(file, OLD_LOG_FILE);
    }

    private static void appendFile(StringBuilder sb, File file) {
        if (!file.exists()) {
            return;
        }
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            while (true) {
                String readLine = bufferedReader.readLine();
                if (readLine == null) {
                    bufferedReader.close();
                    return;
                } else {
                    sb.append(readLine);
                    sb.append('\n');
                }
            }
        } catch (Throwable unused) {
        }
    }

    private static String readTail(File file, int i) {
        int indexOf;
        int i2;
        if (file != null && file.exists() && i > 0) {
            RandomAccessFile randomAccessFile = null;
            try {
                RandomAccessFile randomAccessFile2 = new RandomAccessFile(file, "r");
                try {
                    long length = randomAccessFile2.length();
                    long min = Math.min(512000, Math.max(8192, i * 2));
                    long max = Math.max(0L, length - min);
                    randomAccessFile2.seek(max);
                    byte[] bArr = new byte[(int) Math.min(min, length - max)];
                    randomAccessFile2.readFully(bArr);
                    String str = new String(bArr, StandardCharsets.UTF_8);
                    if (max > 0 && (indexOf = str.indexOf(10)) >= 0 && (i2 = indexOf + 1) < str.length()) {
                        str = str.substring(i2);
                    }
                    if (str.length() > i) {
                        str = str.substring(str.length() - i);
                    }
                    try {
                        randomAccessFile2.close();
                    } catch (Throwable unused) {
                    }
                    return str;
                } catch (Throwable unused2) {
                    randomAccessFile = randomAccessFile2;
                    if (randomAccessFile != null) {
                        try {
                            randomAccessFile.close();
                        } catch (Throwable unused3) {
                        }
                    }
                    return "";
                }
            } catch (Throwable unused4) {
            }
        }
        return "";
    }

    private static String clean(String str, int i) {
        if (str == null) {
            return "";
        }
        String replace = HcfSupportSanitizer.sanitize(str).replace((char) 0, ' ').replace("\r", "");
        if (replace.length() <= i) {
            return replace;
        }
        return replace.substring(0, i) + "…";
    }

    private AppLogger() {
    }
}


// ---- ErrorSystem.java ----
/* loaded from: classes.dex */
final class ErrorSystem {

    static final class AppError {
        final String code;
        final String message;
        final String technical;
        final String title;

        AppError(String str, String str2, String str3, String str4) {
            this.code = str;
            this.title = str2;
            this.message = str3;
            this.technical = str4 == null ? "" : str4;
        }
    }

    static AppError offline() {
        return new AppError("HCF-NET-001", "You're offline", "Waiting for an internet connection. The forum will retry automatically.", "No validated network connection is currently available.");
    }

    static AppError fromWebView(int i, String str, boolean z) {
        String str2;
        if (z) {
            return offline();
        }
        StringBuilder sb = new StringBuilder("WebView error ");
        sb.append(i);
        if (str == null || str.trim().isEmpty()) {
            str2 = "";
        } else {
            str2 = " • " + str.trim();
        }
        sb.append(str2);
        String sb2 = sb.toString();
        if (i == -16) {
            return new AppError("HCF-SEC-002", "Unsafe resource blocked", "Android Safe Browsing blocked this page or resource for your protection.", sb2);
        }
        if (i == -15) {
            return new AppError("HCF-WEB-429", "Too many requests", "The forum is temporarily limiting requests. Wait a moment and try again.", sb2);
        }
        if (i == -12) {
            return new AppError("HCF-WEB-001", "Invalid forum address", "The app could not load this forum address safely.", sb2);
        }
        if (i == -11) {
            return ssl(sb2);
        }
        if (i == -9) {
            return new AppError("HCF-WEB-002", "Redirect loop detected", "The page keeps redirecting and cannot be opened safely.", sb2);
        }
        if (i == -8) {
            return new AppError("HCF-NET-004", "Forum connection timed out", "The server took too long to respond. Try again or use the backup server.", sb2);
        }
        if (i == -7) {
            return new AppError("HCF-NET-005", "Connection interrupted", "The connection ended before the page could finish loading.", sb2);
        }
        if (i == -6) {
            return new AppError("HCF-NET-003", "Can't reach the forum server", "The server refused or could not accept the connection. Automatic recovery will keep trying.", sb2);
        }
        if (i == -2) {
            return new AppError("HCF-NET-002", "Forum server couldn't be found", "The forum address could not be resolved. Harley's Clan Forum will try the backup server when available.", sb2);
        }
        return new AppError("HCF-NET-099", "Can't load the forum", "Harley's Clan Forum could not load this page. Try again or switch servers.", sb2);
    }

    static AppError fromHttp(int i, String str) {
        String str2;
        StringBuilder sb = new StringBuilder("HTTP ");
        sb.append(i);
        if (str == null || str.isEmpty()) {
            str2 = "";
        } else {
            str2 = " • " + str;
        }
        sb.append(str2);
        String sb2 = sb.toString();
        if (i == 403) {
            return new AppError("HCF-WEB-403", "Access Forbidden", "Access to this forum area is restricted. Your account may not have permission to view this resource.", sb2);
        }
        if (i == 404) {
            return new AppError("HCF-WEB-404", "Page Not Found", "The forum page could not be found. It may have been moved, renamed, or removed.", sb2);
        }
        if (i == 429) {
            return new AppError("HCF-WEB-429", "Too Many Requests", "The forum is temporarily limiting requests. Wait a moment and try again.", sb2);
        }
        if (i == 500) {
            return new AppError("HCF-WEB-500", "Internal Server Error", "The forum hit an unexpected server error while processing your request. Please try again in a moment.", sb2);
        }
        if (i == 502) {
            return new AppError("HCF-WEB-502", "Bad Gateway", "A gateway between the app and forum returned an invalid response.", sb2);
        }
        if (i == 503) {
            return new AppError("HCF-WEB-503", "Service Unavailable", "The forum is temporarily unavailable, usually because of maintenance or a short service interruption.", sb2);
        }
        if (i == 504) {
            return new AppError("HCF-WEB-504", "Gateway Timeout", "The forum gateway did not receive a response in time.", sb2);
        }
        return new AppError("HCF-WEB-5XX", "Forum Server Problem", "The forum server returned HTTP " + i + ". Try again or use the backup server.", sb2);
    }

    static AppError externalBlocked(String str) {
        String trim = (str == null || str.trim().isEmpty()) ? "unknown destination" : str.trim();
        if (trim.length() > 180) {
            trim = trim.substring(0, 180) + "…";
        }
        return new AppError("HCF-SEC-003", "External Site Blocked", "This app only opens registered Harley's Clan Forum websites inside the forum viewer.", "Blocked non-forum navigation: " + trim);
    }

    static AppError connectionTimeout(String str) {
        return new AppError("HCF-NET-004", "Forum connection timed out", "The forum is taking longer than expected to respond. Try again or use the backup server.", "Startup connection timeout while loading " + ((str == null || str.trim().isEmpty()) ? "the forum server" : str.trim()) + ".");
    }

    static AppError ssl(String str) {
        if (str == null || str.isEmpty()) {
            str = "TLS/SSL validation failed.";
        }
        return new AppError("HCF-SSL-001", "Secure connection blocked", "Harley's Clan Forum could not verify the secure connection. For your safety, the app did not continue.", str);
    }

    static AppError renderer(boolean z) {
        return new AppError("HCF-WV-001", "Forum viewer restarted", "The Android web viewer stopped unexpectedly. The app is rebuilding it and restoring your forum page.", z ? "WebView renderer process crashed." : "WebView renderer process was terminated by Android.");
    }

    static AppError updateVerification(String str) {
        return new AppError("HCF-UPD-002", "Update verification failed", "The downloaded APK did not pass Harley's Clan Forum security checks, so it will not be installed.", str);
    }

    static AppError updateDownloadFailure(int i) {
        String str;
        switch (i) {
            case 1001:
                str = "Android could not write the update APK to storage.";
                break;
            case 1002:
                str = "The update server returned an unexpected HTTP response.";
                break;
            case 1003:
            default:
                str = "Android could not finish downloading the update. Check your connection and try again.";
                break;
            case 1004:
                str = "The update connection ended while Android was receiving the APK.";
                break;
            case 1005:
                str = "The update download was stopped because the server redirected too many times.";
                break;
            case 1006:
                str = "There is not enough free storage to download the update.";
                break;
            case 1007:
                str = "Android could not access the selected download storage.";
                break;
            case 1008:
                str = "Android could not resume the interrupted update download. Start the download again.";
                break;
        }
        return new AppError("HCF-UPD-001", "Update download failed", str, "DownloadManager reason " + i);
    }

    static AppError installerOpenFailure(String str) {
        return new AppError("HCF-UPD-003", "Couldn't open Android installer", "The APK is downloaded, but Android could not open the package installer. Check the install-apps permission and try again.", str);
    }

    static AppError generic(String str) {
        return new AppError("HCF-APP-099", "Something went wrong", (str == null || str.trim().isEmpty()) ? "The app hit a recoverable problem." : str.trim(), str);
    }

    private ErrorSystem() {
    }
}

// ---- HcfSupportSanitizer.java ----
final class HcfSupportSanitizer {
    private static final String REDACTED = "[REDACTED]";
    private static final Pattern HEADER = Pattern.compile(
            "(?i)\\b(authorization|proxy-authorization|cookie|set-cookie)\\s*[:=]\\s*[^\\r\\n|]+"
    );
    private static final Pattern BEARER = Pattern.compile(
            "(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]+"
    );
    private static final Pattern DISCORD_WEBHOOK = Pattern.compile(
            "(?i)https://(?:www\\.)?discord(?:app)?\\.com/api/webhooks/[^\\s\\\"']+"
    );
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)\\b(password|passwd|session[_-]?(?:id|token)|access[_-]?token|refresh[_-]?token|auth(?:entication)?[_-]?token|csrf[_-]?token|reply[_-]?(?:text|body|content)|message[_-]?(?:body|content|text)|notification[_-]?(?:body|message|content)|private[_-]?(?:message|body|content))\\b\\s*[:=]\\s*(?:\\\"[^\\\"]*\\\"|'[^']*'|[^,\\s|;}]+)"
    );

    static String sanitize(String value) {
        if (value == null || value.isEmpty()) return value == null ? "" : value;
        String out = value;
        out = replaceHeader(out);
        out = BEARER.matcher(out).replaceAll("Bearer " + REDACTED);
        out = DISCORD_WEBHOOK.matcher(out).replaceAll("https://discord.com/api/webhooks/" + REDACTED);
        Matcher matcher = KEY_VALUE.matcher(out);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + "=" + REDACTED));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String replaceHeader(String value) {
        Matcher matcher = HEADER.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + "=" + REDACTED));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private HcfSupportSanitizer() {}
}

// ---- Consolidated from HcfSetupCompletionGuard.java ----
/**
 * Keeps the App Setup hamburger entry aligned with setup completion state.
 *
 * While setup is incomplete, SetupCenter may expose its normal drawer entry.
 * Once Finish Setup marks SETUP_COMPLETED, the entry is removed and suppressed
 * whenever MainActivity resumes so the completed wizard cannot be reopened from
 * the hamburger menu. If setup completion is reset later, the normal entry is
 * allowed to return.
 */
final class HcfSetupCompletionGuard {
    private static final String DRAWER_TAG = "hcf_app_setup_drawer";
    private static final String DRAWER_LABEL = "App Setup";
    private static boolean installed;

    private HcfSetupCompletionGuard() {}

    private static synchronized void install(Context context) {
        if (installed || context == null) return;
        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof Application)) return;
        installed = true;

        ((Application) appContext).registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override public void onActivityCreated(Activity activity, Bundle state) {}
                    @Override public void onActivityStarted(Activity activity) {}

                    @Override public void onActivityResumed(Activity activity) {
                        if (!(activity instanceof HcfForum.MainActivity)) return;
                        scheduleSync((HcfForum.MainActivity) activity);
                    }

                    @Override public void onActivityPaused(Activity activity) {}
                    @Override public void onActivityStopped(Activity activity) {}
                    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
                    @Override public void onActivityDestroyed(Activity activity) {}
                }
        );
    }

    /**
     * Run once immediately and again after the activity's other lifecycle callbacks have
     * had a chance to rebuild the drawer. This prevents SetupCenter from re-adding the
     * button after the completion guard has already run.
     */
    private static void scheduleSync(final HcfForum.MainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        syncDrawer(activity);

        View root = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (root == null) return;

        root.post(new Runnable() {
            @Override public void run() {
                syncDrawer(activity);
            }
        });
        root.postDelayed(new Runnable() {
            @Override public void run() {
                syncDrawer(activity);
            }
        }, 150L);
        root.postDelayed(new Runnable() {
            @Override public void run() {
                syncDrawer(activity);
            }
        }, 600L);
        root.postDelayed(new Runnable() {
            @Override public void run() {
                syncDrawer(activity);
            }
        }, 1500L);
    }

    private static void syncDrawer(HcfForum.MainActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        boolean completed = activity
                .getSharedPreferences(AppPrefs.FILE, 0)
                .getBoolean(AppPrefs.SETUP_COMPLETED, false);

        if (!completed) {
            try {
                SetupCenter.installDrawerEntry(activity);
            } catch (Throwable error) {
                AppLogger.warn(activity, "app_setup_drawer_guard",
                        "restore_failed_" + error.getClass().getSimpleName());
            }
            return;
        }

        try {
            View drawerRoot = activity.findViewById(R.id.drawerPanel);
            if (!(drawerRoot instanceof ViewGroup)) {
                View settings = activity.findViewById(R.id.drawerSettings);
                if (settings != null && settings.getParent() instanceof ViewGroup) {
                    drawerRoot = (ViewGroup) settings.getParent();
                }
            }

            int removed = drawerRoot instanceof ViewGroup
                    ? removeSetupEntries((ViewGroup) drawerRoot)
                    : 0;

            if (removed > 0) {
                AppLogger.info(activity, "app_setup_drawer",
                        "hidden_after_completion count=" + removed);
            }
        } catch (Throwable error) {
            AppLogger.warn(activity, "app_setup_drawer_guard",
                    "hide_failed_" + error.getClass().getSimpleName());
        }
    }

    /** Remove both the tagged dynamic entry and any matching built-in App Setup row. */
    private static int removeSetupEntries(ViewGroup parent) {
        if (parent == null) return 0;
        int removed = 0;

        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            if (isSetupEntry(child)) {
                parent.removeViewAt(i);
                removed++;
                continue;
            }
            if (child instanceof ViewGroup) {
                removed += removeSetupEntries((ViewGroup) child);
            }
        }
        return removed;
    }

    private static boolean isSetupEntry(View view) {
        if (view == null) return false;
        if (DRAWER_TAG.equals(view.getTag())) return true;
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            return text != null && DRAWER_LABEL.equals(text.toString().trim());
        }
        return false;
    }

    /** Installed before activities so the guard can react as soon as MainActivity resumes. */
    public static final class BootstrapProvider extends ContentProvider {
        @Override public boolean onCreate() {
            install(getContext());
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection,
                                      String[] selectionArgs, String sortOrder) {
            return null;
        }

        @Override public String getType(Uri uri) {
            return null;
        }

        @Override public Uri insert(Uri uri, ContentValues values) {
            return null;
        }

        @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override public int update(Uri uri, ContentValues values, String selection,
                                    String[] selectionArgs) {
            return 0;
        }
    }
}

// ---- Consolidated from HcfSettingsTransfer.java ----
/**
 * Portable HCF user-settings backup/import helper.
 *
 * Only settings that the user can actually configure in App Settings are transferred.
 * Internal runtime state, account/session data, package/channel identity, Android permission
 * state, notification history, telemetry history and downloaded-update state are excluded.
 */
final class HcfSettingsTransfer {
    static final String FORMAT = "hcf-settings";
    static final int SCHEMA_VERSION = 2;
    private static final int MAX_IMPORT_CHARS = 1024 * 1024;

    /** On/off controls that are directly user-configurable in HCF App Settings. */
    private static final Set<String> BOOLEAN_KEYS = new LinkedHashSet<>(Arrays.asList(
            AppPrefs.AUTO_FAILOVER,
            AppPrefs.BACKGROUND_NOTIFICATION_SYNC,
            AppPrefs.COMPACT_HEADER,
            AppPrefs.EXTERNAL_LINKS,
            AppPrefs.LIVE_FORUM_UPDATES,
            AppPrefs.NOTIFICATIONS_ENABLED,
            AppPrefs.PERFORMANCE_MODE,
            AppPrefs.SHOW_BOTTOM_NAV,
            AppPrefs.SHOW_STARTUP_SCREEN,
            AppPrefs.WIDGET_FOLLOW_APP_THEME,
            AppPrefs.WIDGET_SHOW_CONNECTED_USERNAME,
            AppPrefs.WIDGET_SHOW_UNREAD_COUNT,
            AppPrefs.WIDGET_COMPACT_MODE,
            AppPrefs.WIDGET_SHOW_LAST_UPDATED,
            AppPrefs.SHOW_URL_BAR,
            AppPrefs.SILENCE_BACKGROUND_SERVICE_NOTIFICATION,
            AppPrefs.TELEMETRY_ASK_BEFORE_CRASH_REPORT,
            AppPrefs.TELEMETRY_AUTO_CRASH_REPORTS,
            AppPrefs.TELEMETRY_AUTO_ERROR_REPORTS,
            AppPrefs.TELEMETRY_ENABLED,
            AppPrefs.TELEMETRY_INCLUDE_DEVICE_MODEL,
            AppPrefs.TELEMETRY_INCLUDE_EMAIL,
            AppPrefs.TELEMETRY_INCLUDE_IDENTITY,
            AppPrefs.TELEMETRY_INCLUDE_ROUTE,
            AppPrefs.UPDATE_AUTO_CHECK,
            AppPrefs.UPDATE_AUTO_DOWNLOAD,
            AppPrefs.UPDATE_AUTO_INSTALL
    ));

    /** User-selected option/value controls from App Settings. */
    private static final Set<String> STRING_KEYS = new LinkedHashSet<>(Arrays.asList(
            AppPrefs.APP_THEME,
            AppPrefs.NATIVE_ACCENT,
            AppPrefs.PERFORMANCE_PROFILE,
            AppPrefs.WIDGET_DEFAULT_TAP_ACTION,
            AppPrefs.TELEMETRY_LEVEL,
            AppPrefs.FIREBASE_CONFIG_URL
    ));

    static final class Result {
        final boolean ok;
        final int imported;
        final int skipped;
        final String message;

        Result(boolean ok, int imported, int skipped, String message) {
            this.ok = ok;
            this.imported = imported;
            this.skipped = skipped;
            this.message = message == null ? "" : message;
        }

        String summary() {
            if (!ok) return message.isEmpty() ? "Settings import failed." : message;
            String base = "Imported " + imported + (imported == 1 ? " user setting" : " user settings");
            if (skipped > 0) base += " • skipped " + skipped;
            return base;
        }
    }

    private HcfSettingsTransfer() {}

    static Result importFromUri(Context context, Uri uri) {
        if (context == null || uri == null) {
            return new Result(false, 0, 0, "No settings backup was selected.");
        }
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                return new Result(false, 0, 0, "The selected settings backup could not be opened.");
            }
            return importJson(context, readUtf8(input));
        } catch (Throwable error) {
            AppLogger.warn(context, "settings_import", error.getClass().getSimpleName());
            return new Result(false, 0, 0, "This HCF settings backup could not be imported.");
        }
    }

    static Result importJson(Context context, String json) {
        if (context == null || json == null || json.trim().isEmpty()) {
            return new Result(false, 0, 0, "The selected settings backup is empty.");
        }
        try {
            JSONObject root = new JSONObject(json);
            String format = root.optString("format", "").trim();
            if (!format.isEmpty() && !FORMAT.equalsIgnoreCase(format)) {
                return new Result(false, 0, 0, "This file is not an HCF settings backup.");
            }
            int schema = root.optInt("schemaVersion", root.optInt("schema", 1));
            if (schema < 1 || schema > SCHEMA_VERSION) {
                return new Result(false, 0, 0, "This settings backup uses a newer unsupported format.");
            }

            JSONObject settings = root.optJSONObject("settings");
            boolean simpleRootBackup = settings == null;
            if (settings == null) {
                // Keep compatibility with early/manual schema-v1 HCF backups.
                settings = root;
            }

            SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, 0);
            SharedPreferences.Editor edit = prefs.edit();
            int imported = 0;
            int skipped = 0;

            java.util.Iterator<String> keys = settings.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (simpleRootBackup && isMetadataKey(key)) continue;

                Object value = settings.opt(key);
                if (BOOLEAN_KEYS.contains(key)) {
                    Boolean parsed = asBoolean(value);
                    if (parsed == null) {
                        skipped++;
                    } else {
                        edit.putBoolean(key, parsed.booleanValue());
                        imported++;
                    }
                } else if (STRING_KEYS.contains(key)) {
                    if (value == null || value == JSONObject.NULL) {
                        skipped++;
                    } else {
                        edit.putString(key, String.valueOf(value));
                        imported++;
                    }
                } else {
                    // Ignore internal/runtime fields even if an older backup contains them.
                    skipped++;
                }
            }

            if (imported <= 0) {
                return new Result(false, 0, skipped, "No user-configurable HCF app settings were found in this backup.");
            }
            if (!edit.commit()) {
                return new Result(false, 0, skipped, "HCF could not save the imported settings.");
            }
            UiPreferences.migrate(context);
            AppLogger.info(context, "settings_import", "user_settings=" + imported + " skipped=" + skipped);
            return new Result(true, imported, skipped, "");
        } catch (Throwable error) {
            AppLogger.warn(context, "settings_import", error.getClass().getSimpleName());
            return new Result(false, 0, 0, "This HCF settings backup is invalid or damaged.");
        }
    }

    static String exportJson(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, 0);
        Map<String, ?> all = prefs.getAll();
        JSONObject settings = new JSONObject();
        JSONObject types = new JSONObject();

        // Export only the values owned by real user-facing App Settings controls.
        for (String key : BOOLEAN_KEYS) {
            Object value = all.get(key);
            if (value instanceof Boolean) {
                settings.put(key, value);
                types.put(key, "boolean");
            }
        }
        for (String key : STRING_KEYS) {
            Object value = all.get(key);
            if (value instanceof String) {
                settings.put(key, value);
                types.put(key, "string");
            }
        }

        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("backupType", "user-settings");
        root.put("sourcePackage", context.getPackageName());
        root.put("sourceChannel", BuildInfo.DEFAULT_UPDATE_CHANNEL);
        root.put("sourceVersionCode", BuildInfo.VERSION_CODE);

        String username = safeStringPref(prefs, AppPrefs.IDENTITY_USERNAME);
        if (!username.isEmpty()) root.put("connectedUsername", username);

        root.put("settings", settings);
        root.put("settingTypes", types);
        return root.toString(2);
    }

    static void exportToUri(Context context, Uri uri) throws Exception {
        if (context == null || uri == null) throw new IllegalArgumentException("Missing export destination");
        try (OutputStream output = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) throw new IllegalStateException("Unable to open export destination");
            try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                writer.write(exportJson(context));
                writer.flush();
            }
        }
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        if (value instanceof String) {
            String text = ((String) value).trim();
            if ("true".equalsIgnoreCase(text) || "1".equals(text)) return Boolean.TRUE;
            if ("false".equalsIgnoreCase(text) || "0".equals(text)) return Boolean.FALSE;
        }
        return null;
    }

    private static String safeStringPref(SharedPreferences prefs, String key) {
        try {
            String value = prefs.getString(key, "");
            return value == null ? "" : value.trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean isMetadataKey(String key) {
        return "format".equals(key)
                || "schemaVersion".equals(key)
                || "schema".equals(key)
                || "backupType".equals(key)
                || "sourcePackage".equals(key)
                || "sourceChannel".equals(key)
                || "sourceVersionCode".equals(key)
                || "connectedUsername".equals(key)
                || "settingTypes".equals(key);
    }

    private static String readUtf8(InputStream input) throws Exception {
        InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            if (read == 0) continue;
            if (out.length() + read > MAX_IMPORT_CHARS) {
                throw new IllegalArgumentException("Settings backup is too large");
            }
            out.append(buffer, 0, read);
        }
        return out.toString();
    }
}
