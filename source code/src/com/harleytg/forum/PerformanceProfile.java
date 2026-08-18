package com.harleytg.forum.dev;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

/** Runtime performance profiles and the v10000033 hardware-aware Auto engine. */
final class PerformanceProfile {
    static final String AUTO = "auto";
    static final String PERFORMANCE = "performance";
    static final String BALANCED = "balanced";
    static final String QUALITY = "quality";

    private static final String AUTO_REALTIME = "auto_realtime";
    private static final String AUTO_BALANCED = "auto_balanced";
    private static final String AUTO_PERFORMANCE = "auto_performance";
    private static final String AUTO_EXTREME = "auto_extreme";

    private static final long THREE_GB = 3L * 1024L * 1024L * 1024L;
    private static final long FOUR_GB = 4L * 1024L * 1024L * 1024L;
    private static final long LOW_AVAILABLE_BYTES = 384L * 1024L * 1024L;
    private static final long HEALTHY_AVAILABLE_BYTES = 768L * 1024L * 1024L;

    static String saved(SharedPreferences prefs) {
        if (prefs == null) return AUTO;
        String raw = prefs.getString(AppPrefs.PERFORMANCE_PROFILE, "");
        if (PERFORMANCE.equals(raw) || BALANCED.equals(raw) || QUALITY.equals(raw) || AUTO.equals(raw)) return raw;
        return AUTO;
    }

    static void save(SharedPreferences prefs, String profile) {
        if (prefs == null) return;
        String safe = normalize(profile);
        prefs.edit()
                .putString(AppPrefs.PERFORMANCE_PROFILE, safe)
                .putBoolean(AppPrefs.PERFORMANCE_MODE, PERFORMANCE.equals(safe))
                .apply();
    }

    /** Resolves to the visual profile used by the existing UI/WebView code. */
    static String resolve(Context context, SharedPreferences prefs) {
        String saved = saved(prefs);
        if (!AUTO.equals(saved)) return saved;
        String auto = autoRuntime(context, prefs);
        if (AUTO_REALTIME.equals(auto)) return QUALITY;
        if (AUTO_BALANCED.equals(auto)) return BALANCED;
        return PERFORMANCE;
    }

    static String autoRuntime(Context context, SharedPreferences prefs) {
        if (context == null) return AUTO_BALANCED;

        MemorySnapshot memory = memory(context);
        int cores = safeCpuCount();
        int thermal = thermalStatus(context);
        boolean saver = isBatterySaver(context);
        int battery = batteryPercent(context);
        boolean charging = isCharging(context);
        boolean interactive = RuntimeState.isInteractive(context);
        boolean metered = RuntimeState.networkMetered(context);
        String network = RuntimeState.networkType(context);
        long latency = RuntimeDiagnostics.lastLatencyMs();
        int trim = RuntimeState.memoryTrimLevel();
        int rendererRecoveries = prefs == null ? 0 : prefs.getInt(AppPrefs.RENDERER_RECOVERY_COUNT, 0);

        if (saver || thermal >= severeThermalStatus()
                || trim >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
                || (memory.available > 0L && memory.available < 192L * 1024L * 1024L)) {
            RuntimeDiagnostics.profile("Auto • Extreme Saver",
                    saver ? "Battery Saver active" : (thermal >= severeThermalStatus() ? "Thermal pressure" : "Critical memory pressure"));
            return AUTO_EXTREME;
        }

        if ((!charging && battery >= 0 && battery <= 10)
                || memory.lowRam || memory.total > 0L && memory.total <= THREE_GB
                || memory.available > 0L && memory.available <= LOW_AVAILABLE_BYTES
                || cores <= 4 || rendererRecoveries >= 3) {
            RuntimeDiagnostics.profile("Auto • Performance",
                    (!charging && battery >= 0 && battery <= 10) ? "Very low battery"
                            : (memory.lowRam ? "Android low-RAM device" : (cores <= 4 ? "Lower CPU class" : "Memory/WebView pressure")));
            return AUTO_PERFORMANCE;
        }

        if (thermal >= moderateThermalStatus() || metered || latency >= 2500L
                || (!charging && battery >= 0 && battery <= 20)
                || "Offline".equals(network) || !interactive) {
            RuntimeDiagnostics.profile("Auto • Balanced",
                    thermal >= moderateThermalStatus() ? "Moderate thermal pressure"
                            : ((!charging && battery >= 0 && battery <= 20) ? "Low battery"
                            : (metered ? "Metered network" : (latency >= 2500L ? "Higher request latency" : "Conservative current state"))));
            return AUTO_BALANCED;
        }

        boolean strongMemory = memory.total > THREE_GB && memory.available >= (640L * 1024L * 1024L);
        boolean strongCpu = cores >= 6;
        boolean healthyLatency = latency <= 0L || latency < 1800L;
        if (strongMemory && strongCpu && healthyLatency && RuntimeState.networkAvailable(context)) {
            RuntimeDiagnostics.profile("Auto • Real-Time", "Healthy CPU, memory, battery, thermal and network state");
            return AUTO_REALTIME;
        }

        RuntimeDiagnostics.profile("Auto • Balanced", "Average hardware or current load");
        return AUTO_BALANCED;
    }

    static boolean isLowEndOrConstrained(Context context) {
        String mode = autoRuntime(context, null);
        return AUTO_PERFORMANCE.equals(mode) || AUTO_EXTREME.equals(mode);
    }

    static long notificationPollInterval(Context context, SharedPreferences prefs) {
        if (context == null) return 3000L;

        if (!RuntimeState.networkAvailable(context)) return 15000L;

        // Android-background efficiency is deliberately much more conservative than
        // foreground behavior. Push is preferred when a native FCM transport exists.
        if (!RuntimeState.isForeground()) {
            if (isBatterySaver(context)) return 5L * 60L * 1000L;
            if (!RuntimeState.isInteractive(context)) return 3L * 60L * 1000L;
            long background = RuntimeState.backgroundDurationMs();
            if (background < 60_000L) return 10_000L;
            if (background < 10L * 60L * 1000L) return RuntimeState.networkMetered(context) ? 30_000L : 20_000L;
            return RuntimeState.networkMetered(context) ? 3L * 60L * 1000L : 90_000L;
        }

        String saved = saved(prefs);
        if (!AUTO.equals(saved)) {
            if (QUALITY.equals(saved)) return 1000L;
            if (BALANCED.equals(saved)) return 3000L;
            return 6000L;
        }

        String runtime = autoRuntime(context, prefs);
        if (AUTO_REALTIME.equals(runtime)) return 1000L;
        if (AUTO_BALANCED.equals(runtime)) return 2500L;
        if (AUTO_EXTREME.equals(runtime)) return 10_000L;
        return 5000L;
    }

    static long livePollInterval(Context context, SharedPreferences prefs) {
        if (context == null) return 5000L;
        if (!RuntimeState.networkAvailable(context)) return 5000L;
        long base;
        String saved = saved(prefs);
        if (!AUTO.equals(saved)) {
            if (QUALITY.equals(saved)) base = 1000L;
            else if (BALANCED.equals(saved)) base = 3000L;
            else base = 6000L;
        } else {
            String runtime = autoRuntime(context, prefs);
            if (AUTO_REALTIME.equals(runtime)) base = 1000L;
            else if (AUTO_BALANCED.equals(runtime)) base = 2500L;
            else if (AUTO_EXTREME.equals(runtime)) base = 10_000L;
            else base = 5000L;
        }

        long idle = RuntimeState.sinceLastInteractionMs();
        if (idle >= 60_000L) return Math.max(base, 10_000L);
        if (idle >= 12_000L) return Math.max(base, 5000L);
        return base;
    }

    static long motionDuration(Context context, SharedPreferences prefs, long qualityMs) {
        String mode = resolve(context, prefs);
        if (PERFORMANCE.equals(mode)) return 0L;
        if (BALANCED.equals(mode)) return Math.max(70L, Math.round(qualityMs * 0.62d));
        return qualityMs;
    }

    static String label(String profile) {
        if (PERFORMANCE.equals(profile)) return "Performance";
        if (BALANCED.equals(profile)) return "Balanced";
        if (QUALITY.equals(profile)) return "High Performance";
        return "Auto";
    }

    static String settingLabel(Context context, SharedPreferences prefs) {
        String chosen = saved(prefs);
        if (!AUTO.equals(chosen)) return label(chosen);
        String runtime = autoRuntime(context, prefs);
        if (AUTO_REALTIME.equals(runtime)) return "Auto • Real-Time";
        if (AUTO_PERFORMANCE.equals(runtime)) return "Auto • Performance";
        if (AUTO_EXTREME.equals(runtime)) return "Auto • Extreme Saver";
        return "Auto • Balanced";
    }

    static String detail(Context context, SharedPreferences prefs) {
        String chosen = saved(prefs);
        if (AUTO.equals(chosen)) {
            String runtime = autoRuntime(context, prefs);
            if (AUTO_REALTIME.equals(runtime)) return "Fastest safe foreground refresh • full UI effects";
            if (AUTO_EXTREME.equals(runtime)) return "Minimal polling and visual effects while the device is constrained";
            if (AUTO_PERFORMANCE.equals(runtime)) return "Reduced effects • slower idle refresh • lower background overhead";
            return "Adaptive refresh • normal UI effects";
        }
        String resolved = resolve(context, prefs);
        if (PERFORMANCE.equals(resolved)) return "Minimal motion • blur disabled • lowest visual overhead";
        if (BALANCED.equals(resolved)) return "Shorter motion • reduced visual overhead";
        return "Full motion and visual effects";
    }

    static boolean isBatterySaver(Context context) {
        if (context == null) return false;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isPowerSaveMode();
        } catch (Throwable ignored) { return false; }
    }

    static int batteryPercent(Context context) {
        if (context == null) return -1;
        try {
            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return -1;
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level < 0 || scale <= 0) return -1;
            return Math.max(0, Math.min(100, Math.round(level * 100f / scale)));
        } catch (Throwable ignored) { return -1; }
    }

    static boolean isCharging(Context context) {
        if (context == null) return false;
        try {
            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return false;
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        } catch (Throwable ignored) { return false; }
    }

    private static int thermalStatus(Context context) {
        if (Build.VERSION.SDK_INT < 29 || context == null) return 0;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm == null ? 0 : pm.getCurrentThermalStatus();
        } catch (Throwable ignored) { return 0; }
    }

    private static int moderateThermalStatus() {
        return Build.VERSION.SDK_INT >= 29 ? PowerManager.THERMAL_STATUS_MODERATE : 2;
    }

    private static int severeThermalStatus() {
        return Build.VERSION.SDK_INT >= 29 ? PowerManager.THERMAL_STATUS_SEVERE : 3;
    }

    private static int safeCpuCount() {
        try { return Math.max(1, Runtime.getRuntime().availableProcessors()); }
        catch (Throwable ignored) { return 4; }
    }

    private static MemorySnapshot memory(Context context) {
        MemorySnapshot out = new MemorySnapshot();
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return out;
            out.lowRam = am.isLowRamDevice();
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            out.total = info.totalMem;
            out.available = info.availMem;
        } catch (Throwable ignored) {}
        return out;
    }

    private static String normalize(String profile) {
        if (PERFORMANCE.equals(profile) || BALANCED.equals(profile) || QUALITY.equals(profile)) return profile;
        return AUTO;
    }

    private static final class MemorySnapshot {
        long total;
        long available;
        boolean lowRam;
    }

    private PerformanceProfile() {}
}
