package com.harleytg.forum;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;

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
