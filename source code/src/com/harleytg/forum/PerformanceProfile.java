package com.harleytg.forum;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.PowerManager;

/** Runtime performance profiles for native motion and WebView visual overhead. */
final class PerformanceProfile {
    static final String AUTO = "auto";
    static final String PERFORMANCE = "performance";
    static final String BALANCED = "balanced";
    static final String QUALITY = "quality";

    private static final long THREE_GB = 3L * 1024L * 1024L * 1024L;

    static String saved(SharedPreferences prefs) {
        if (prefs == null) return AUTO;
        String raw = prefs.getString(AppPrefs.PERFORMANCE_PROFILE, "");
        if (PERFORMANCE.equals(raw) || BALANCED.equals(raw) || QUALITY.equals(raw) || AUTO.equals(raw)) return raw;
        // Auto is the default whenever no explicit four-profile choice exists.
        // Do not silently inherit the legacy binary performance-mode switch.
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

    static String resolve(Context context, SharedPreferences prefs) {
        String saved = saved(prefs);
        if (!AUTO.equals(saved)) return saved;
        return isLowEndOrConstrained(context) ? PERFORMANCE : BALANCED;
    }

    static boolean isLowEndOrConstrained(Context context) {
        if (context == null) return false;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isPowerSaveMode()) return true;
        } catch (Throwable ignored) {}
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                if (am.isLowRamDevice()) return true;
                ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(info);
                if (info.totalMem > 0L && info.totalMem <= THREE_GB) return true;
            }
        } catch (Throwable ignored) {}
        try {
            if (Runtime.getRuntime().availableProcessors() <= 4) return true;
        } catch (Throwable ignored) {}
        return false;
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
        if (QUALITY.equals(profile)) return "Quality";
        return "Auto";
    }

    static String settingLabel(Context context, SharedPreferences prefs) {
        String saved = saved(prefs);
        if (AUTO.equals(saved)) return "Auto • currently " + label(resolve(context, prefs));
        return label(saved);
    }

    static String detail(Context context, SharedPreferences prefs) {
        String resolved = resolve(context, prefs);
        if (PERFORMANCE.equals(resolved)) return "Minimal motion • blur disabled • lowest visual overhead";
        if (BALANCED.equals(resolved)) return "Shorter motion • reduced visual overhead";
        return "Full motion and visual effects";
    }

    private static String normalize(String profile) {
        if (PERFORMANCE.equals(profile) || BALANCED.equals(profile) || QUALITY.equals(profile)) return profile;
        return AUTO;
    }

    private PerformanceProfile() {}
}
