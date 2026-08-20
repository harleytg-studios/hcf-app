package com.harleytg.forum;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.PowerManager;

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
