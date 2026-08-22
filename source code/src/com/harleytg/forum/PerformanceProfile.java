package com.harleytg.forum;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;

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
        if (context == null || !RuntimeState.networkAvailable(context)) {
            return 15000L;
        }
        String saved = saved(sharedPreferences);
        long interval;
        if (!AUTO.equals(saved)) {
            interval = QUALITY.equals(saved) ? 700L : BALANCED.equals(saved) ? 1800L : 5000L;
        } else {
            String runtime = autoRuntime(context, sharedPreferences);
            interval = AUTO_REALTIME.equals(runtime) ? 700L : AUTO_BALANCED.equals(runtime) ? 1800L : AUTO_EXTREME.equals(runtime) ? 10000L : 5000L;
        }
        int thermalStatus = thermalStatus(context);
        if (thermalStatus >= severeThermalStatus() || isBatterySaver(context)) {
            interval = Math.max(interval, 10000L);
        } else if (thermalStatus >= moderateThermalStatus()) {
            interval = Math.max(interval, 5000L);
        }
        if (RuntimeState.networkMetered(context)) {
            interval = Math.max(interval, 3000L);
        }
        int batteryPercent = batteryPercent(context);
        if (!isCharging(context) && batteryPercent >= 0) {
            if (batteryPercent <= 10) {
                interval = Math.max(interval, 10000L);
            } else if (batteryPercent <= 20) {
                interval = Math.max(interval, 5000L);
            }
        }
        if (!RuntimeState.isForeground()) {
            long backgroundDurationMs = RuntimeState.backgroundDurationMs();
            interval = Math.max(interval, backgroundDurationMs >= 60000L ? 15000L : 5000L);
        }
        if (!RuntimeState.isInteractive(context)) {
            interval = Math.max(interval, 10000L);
        }
        long sinceLastInteractionMs = RuntimeState.sinceLastInteractionMs();
        if (sinceLastInteractionMs >= 60000L) {
            interval = Math.max(interval, 10000L);
        } else if (sinceLastInteractionMs >= 12000L) {
            interval = Math.max(interval, 5000L);
        }
        return interval;
    }

    static long livePollInterval(Context context, SharedPreferences sharedPreferences) {
        if (context == null || !RuntimeState.networkAvailable(context)) {
            return 5000L;
        }
        String saved = saved(sharedPreferences);
        long j = 1000;
        if (!AUTO.equals(saved)) {
            if (!QUALITY.equals(saved)) {
                j = BALANCED.equals(saved) ? 3000L : 6000L;
            }
        } else {
            String autoRuntime = autoRuntime(context, sharedPreferences);
            if (!AUTO_REALTIME.equals(autoRuntime)) {
                if (AUTO_BALANCED.equals(autoRuntime)) {
                    j = 2500;
                } else {
                    j = AUTO_EXTREME.equals(autoRuntime) ? 10000L : 5000L;
                }
            }
        }
        long sinceLastInteractionMs = RuntimeState.sinceLastInteractionMs();
        if (sinceLastInteractionMs >= 60000) {
            return Math.max(j, 10000L);
        }
        return sinceLastInteractionMs >= 12000 ? Math.max(j, 5000L) : j;
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
