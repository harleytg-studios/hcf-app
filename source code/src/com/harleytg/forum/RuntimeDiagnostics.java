package com.harleytg.forum;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;

/** Shared HCF v10000072 runtime diagnostics state. */
final class RuntimeDiagnostics {
    private static volatile int consecutiveApiFailures = 0;
    private static volatile long currentLivePollMs = 0;
    private static volatile long currentNotificationPollMs = 0;
    private static volatile long lastObservedLatencyMs = 0;
    private static volatile String lastProfile = "Auto • Balanced";
    private static volatile String lastProfileReason = "Starting";
    private static volatile String notificationMode = "Adaptive polling fallback";

    static void notificationPoll(long intervalMs, String mode) {
        currentNotificationPollMs = Math.max(0L, intervalMs);
        if (mode != null && !mode.trim().isEmpty()) notificationMode = mode.trim();
    }

    static void livePoll(long intervalMs) { currentLivePollMs = Math.max(0L, intervalMs); }
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
        return prefs == null ? 0 : prefs.getInt("renderer_recovery_count", 0);
    }

    static String fcmState() {
        return "Native FCM transport unavailable • adaptive polling active";
    }

    static String compact(Context context, SharedPreferences prefs) {
        StringBuilder out = new StringBuilder("Profile: ");
        out.append(PerformanceProfile.settingLabel(context, prefs));
        out.append("\nRuntime reason: ").append(profileReason());
        out.append("\nNotification sync: ").append(notificationMode());
        out.append("\nNotification interval: ").append(interval(currentNotificationPollMs));
        out.append("\nLive page interval: ").append(interval(currentLivePollMs));
        out.append("\nFCM: ").append(fcmState());
        out.append("\nNetwork: ").append(RuntimeState.networkType(context));
        out.append("\nBattery Saver: ").append(PerformanceProfile.isBatterySaver(context) ? "On" : "Off");
        out.append("\nRenderer recoveries: ").append(rendererRecoveries(prefs));
        out.append("\nConsecutive API failures: ").append(consecutiveApiFailures);
        return out.toString();
    }

    private static String interval(long value) {
        if (value <= 0) return "idle";
        if (value < 1000) return value + " ms";
        if (value % 1000 != 0) return String.format(Locale.US, "%.2f s", value / 1000.0d);
        return (value / 1000) + " s";
    }

    private RuntimeDiagnostics() {}
}
