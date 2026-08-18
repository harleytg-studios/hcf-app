package com.harleytg.forum.dev;

import android.content.Context;
import android.content.SharedPreferences;

/** Lightweight, mostly in-memory diagnostics for the adaptive runtime. */
final class RuntimeDiagnostics {
    private static volatile String notificationMode = "Adaptive polling fallback";
    private static volatile long currentNotificationPollMs;
    private static volatile long currentLivePollMs;
    private static volatile int consecutiveApiFailures;
    private static volatile long lastObservedLatencyMs;
    private static volatile String lastProfile = "Auto • Balanced";
    private static volatile String lastProfileReason = "Starting";

    static void notificationPoll(long ms, String mode) {
        currentNotificationPollMs = Math.max(0L, ms);
        if (mode != null && !mode.trim().isEmpty()) notificationMode = mode.trim();
    }

    static void livePoll(long ms) { currentLivePollMs = Math.max(0L, ms); }

    static void syncSucceeded(long latencyMs) {
        lastObservedLatencyMs = Math.max(0L, latencyMs);
        consecutiveApiFailures = 0;
    }

    static void syncFailed() { consecutiveApiFailures = Math.min(99, consecutiveApiFailures + 1); }

    static int failures() { return consecutiveApiFailures; }
    static long lastLatencyMs() { return lastObservedLatencyMs; }
    static long notificationPollMs() { return currentNotificationPollMs; }
    static long livePollMs() { return currentLivePollMs; }
    static String notificationMode() { return notificationMode; }

    static void profile(String label, String reason) {
        if (label != null && !label.isEmpty()) lastProfile = label;
        if (reason != null && !reason.isEmpty()) lastProfileReason = reason;
    }

    static String profileLabel() { return lastProfile; }
    static String profileReason() { return lastProfileReason; }

    static int rendererRecoveries(SharedPreferences prefs) {
        return prefs == null ? 0 : prefs.getInt(AppPrefs.RENDERER_RECOVERY_COUNT, 0);
    }

    static String fcmState() {
        return BuildInfo.FCM_CONFIGURED
                ? "Configured • push preferred"
                : "Native FCM transport unavailable • adaptive polling active";
    }

    static String compact(Context context, SharedPreferences prefs) {
        return "Profile: " + PerformanceProfile.settingLabel(context, prefs)
                + "\nRuntime reason: " + profileReason()
                + "\nNotification sync: " + notificationMode()
                + "\nNotification interval: " + interval(currentNotificationPollMs)
                + "\nLive page interval: " + interval(currentLivePollMs)
                + "\nFCM: " + fcmState()
                + "\nNetwork: " + RuntimeState.networkType(context)
                + "\nBattery Saver: " + (PerformanceProfile.isBatterySaver(context) ? "On" : "Off")
                + "\nRenderer recoveries: " + rendererRecoveries(prefs)
                + "\nConsecutive API failures: " + consecutiveApiFailures;
    }

    private static String interval(long ms) {
        if (ms <= 0L) return "idle";
        if (ms < 1000L) return ms + " ms";
        if (ms % 1000L == 0L) return (ms / 1000L) + " s";
        return String.format(java.util.Locale.US, "%.2f s", ms / 1000.0d);
    }

    private RuntimeDiagnostics() {}
}
