package com.harleytg.forum.dev;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

/**
 * Single motion policy for every native HCF animation path.
 *
 * The CI motion-normalizer routes explicit ViewPropertyAnimator, ObjectAnimator,
 * ValueAnimator and AnimatorSet durations/interpolators through this class. The
 * runtime provider also applies the shared window transition style to every native
 * Activity. The forum WebView remains a separate web surface and is intentionally
 * not modified here.
 */
public final class HcfMotionSystem {
    public static final String MARKER = "HCF_MOTION_SYSTEM_V4_ALL_NATIVE";

    private static final TimeInterpolator STANDARD =
            new PathInterpolator(0.20f, 0.0f, 0.20f, 1.0f);
    private static final TimeInterpolator EMPHASIZED =
            new PathInterpolator(0.20f, 0.0f, 0.0f, 1.0f);
    private static final TimeInterpolator ACCELERATE =
            new PathInterpolator(0.30f, 0.0f, 0.80f, 0.15f);
    private static final TimeInterpolator LINEAR = new LinearInterpolator();

    private static volatile Context appContext;
    private static boolean registered;

    private HcfMotionSystem() {}

    /** Base duration after HCF performance, battery and low-RAM policy. */
    public static long duration(long baseMs) {
        if (baseMs <= 0L) return 0L;
        if (!systemAnimatorsEnabled()) return 1L;

        Context context = appContext;
        if (context == null) return clampDuration(baseMs);

        SharedPreferences prefs = context.getSharedPreferences("hcf_app", 0);
        long resolved = PerformanceProfile.motionDuration(context, prefs, baseMs);
        if (resolved <= 0L) return 1L;

        if (powerSaver(context)) {
            resolved = Math.round(resolved * 0.78d);
        }
        if (lowRam(context)) {
            resolved = Math.round(resolved * 0.86d);
        }
        return clampDuration(resolved);
    }

    /** Start delays are removed when motion is disabled and scaled otherwise. */
    public static long delay(long baseMs) {
        if (baseMs <= 0L || !systemAnimatorsEnabled() || performanceMotionDisabled()) return 0L;
        Context context = appContext;
        long result = baseMs;
        if (context != null) {
            if (powerSaver(context)) result = Math.round(result * 0.70d);
            if (lowRam(context)) result = Math.round(result * 0.80d);
        }
        return Math.max(0L, Math.min(result, 240L));
    }

    /** Prevent infinite pulse/repeat loops when motion should effectively be off. */
    public static int repeatCount(int requested) {
        if (requested >= 0) return requested;
        if (!systemAnimatorsEnabled() || performanceMotionDisabled()) return 0;
        Context context = appContext;
        if (context != null && (powerSaver(context) || lowRam(context))) return 0;
        return requested;
    }

    public static TimeInterpolator standard() { return STANDARD; }
    public static TimeInterpolator decelerate() { return EMPHASIZED; }
    public static TimeInterpolator emphasized() { return EMPHASIZED; }
    public static TimeInterpolator accelerate() { return ACCELERATE; }
    public static TimeInterpolator linear() { return LINEAR; }

    public static boolean systemAnimatorsEnabled() {
        try {
            return ValueAnimator.areAnimatorsEnabled();
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static boolean fullMotionEnabled() {
        return systemAnimatorsEnabled() && !performanceMotionDisabled();
    }

    /** Apply the same Activity open/close motion family across the native app. */
    public static void configureWindow(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        try {
            Window window = activity.getWindow();
            WindowManager.LayoutParams params = window.getAttributes();
            params.windowAnimations = fullMotionEnabled() ? R.style.HcfWindowAnimation : 0;
            window.setAttributes(params);
        } catch (Throwable ignored) {
        }
    }

    private static boolean performanceMotionDisabled() {
        Context context = appContext;
        if (context == null) return false;
        try {
            SharedPreferences prefs = context.getSharedPreferences("hcf_app", 0);
            return PerformanceProfile.PERFORMANCE.equals(PerformanceProfile.resolve(context, prefs));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean powerSaver(Context context) {
        try {
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return power != null && power.isPowerSaveMode();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean lowRam(Context context) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            return manager != null && manager.isLowRamDevice();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static long clampDuration(long value) {
        return Math.max(1L, Math.min(value, 2600L));
    }

    public static final class BootstrapProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            Context context = getContext();
            if (context == null) return true;
            appContext = context.getApplicationContext();
            if (registered || !(appContext instanceof Application)) return true;
            registered = true;

            ((Application) appContext).registerActivityLifecycleCallbacks(
                    new Application.ActivityLifecycleCallbacks() {
                        @Override public void onActivityCreated(Activity activity, Bundle state) {
                            configureWindow(activity);
                        }
                        @Override public void onActivityStarted(Activity activity) {}
                        @Override public void onActivityResumed(Activity activity) {
                            configureWindow(activity);
                        }
                        @Override public void onActivityPaused(Activity activity) {}
                        @Override public void onActivityStopped(Activity activity) {}
                        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
                        @Override public void onActivityDestroyed(Activity activity) {}
                    });
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection,
                                      String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection,
                                    String[] selectionArgs) { return 0; }
    }
}
