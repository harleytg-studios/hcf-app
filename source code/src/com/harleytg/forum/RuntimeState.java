package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.PowerManager;

/** In-memory app/process state used by the adaptive v10000033 engine. */
final class RuntimeState implements Application.ActivityLifecycleCallbacks {
    private static final RuntimeState INSTANCE = new RuntimeState();

    private static volatile int startedActivities;
    private static volatile long backgroundSinceMs = System.currentTimeMillis();
    private static volatile long lastForegroundAtMs;
    private static volatile long lastInteractionAtMs = System.currentTimeMillis();
    private static volatile int memoryTrimLevel;

    static void install(Application app) {
        if (app == null) return;
        try { app.registerActivityLifecycleCallbacks(INSTANCE); }
        catch (Throwable ignored) {}
    }

    static boolean isForeground() { return startedActivities > 0; }

    static long backgroundDurationMs() {
        if (isForeground()) return 0L;
        return Math.max(0L, System.currentTimeMillis() - backgroundSinceMs);
    }

    static long sinceLastInteractionMs() {
        return Math.max(0L, System.currentTimeMillis() - lastInteractionAtMs);
    }

    static void noteUserInteraction() {
        lastInteractionAtMs = System.currentTimeMillis();
    }

    static void noteTrimMemory(int level) {
        memoryTrimLevel = Math.max(memoryTrimLevel, level);
    }

    static void clearMemoryPressure() {
        memoryTrimLevel = 0;
    }

    static int memoryTrimLevel() { return memoryTrimLevel; }

    static boolean isInteractive(Context context) {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm == null || pm.isInteractive();
        } catch (Throwable ignored) {
            return true;
        }
    }

    static boolean networkAvailable(Context context) {
        return !"Offline".equals(networkType(context));
    }

    static String networkType(Context context) {
        if (context == null) return "Unknown";
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "Unknown";
            Network network = cm.getActiveNetwork();
            if (network == null) return "Offline";
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return "Offline";
            boolean metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return metered ? "Wi-Fi • metered" : "Wi-Fi";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return metered ? "Cellular • metered" : "Cellular";
            return metered ? "Connected • metered" : "Connected";
        } catch (Throwable ignored) {
            return "Unknown";
        }
    }

    static boolean networkMetered(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            return cm != null && cm.isActiveNetworkMetered();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {}

    @Override public void onActivityStarted(Activity activity) {
        startedActivities++;
        lastForegroundAtMs = System.currentTimeMillis();
        clearMemoryPressure();
    }

    @Override public void onActivityResumed(Activity activity) {
        lastForegroundAtMs = System.currentTimeMillis();
        noteUserInteraction();
    }

    @Override public void onActivityPaused(Activity activity) {}

    @Override public void onActivityStopped(Activity activity) {
        startedActivities = Math.max(0, startedActivities - 1);
        if (startedActivities == 0) backgroundSinceMs = System.currentTimeMillis();
    }

    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}

    private RuntimeState() {}
}
