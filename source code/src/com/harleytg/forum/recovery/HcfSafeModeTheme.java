package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

/**
 * Applies the normal HCF appearance resolver to the crash-recovery activity.
 *
 * Recovery historically used its own Activity/colors, which could leave it on the
 * phone's light resources while the rest of HCF was using Night/AMOLED/Auto Forum.
 * This bridge resolves the same app_theme + forum_auto_theme policy before the
 * recovery window draws, including Auto • Phone following Android's system theme.
 */
public final class HcfSafeModeTheme {
    private static final int UI_MODE_NIGHT_MASK = 0x30;
    private static final int UI_MODE_NIGHT_NO = 0x10;
    private static final int UI_MODE_NIGHT_YES = 0x20;
    private static final String EXTRA_RECREATED = "hcf_recovery_theme_recreated";

    private HcfSafeModeTheme() {}

    public static final class BootstrapProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            Context context = getContext();
            if (context == null) return true;
            Context appContext = context.getApplicationContext();
            if (!(appContext instanceof Application)) return true;

            ((Application) appContext).registerActivityLifecycleCallbacks(
                    new Application.ActivityLifecycleCallbacks() {
                        // API 29+ calls this before Activity.onCreate(), preventing the
                        // wrong light/dark recovery frame from being drawn first.
                        @Override
                        public void onActivityPreCreated(Activity activity, Bundle state) {
                            if (activity instanceof HcfSafeMode.SafeModeActivity) {
                                applyBeforeDraw(activity);
                            }
                        }

                        @Override
                        public void onActivityCreated(Activity activity, Bundle state) {
                            if (!(activity instanceof HcfSafeMode.SafeModeActivity)) return;
                            if (ensureResolvedTheme(activity)) return;
                            postSystemBarRefresh(activity);
                        }

                        @Override
                        public void onActivityResumed(Activity activity) {
                            if (!(activity instanceof HcfSafeMode.SafeModeActivity)) return;
                            if (ensureResolvedTheme(activity)) return;
                            postSystemBarRefresh(activity);
                        }

                        @Override public void onActivityStarted(Activity activity) {}
                        @Override public void onActivityPaused(Activity activity) {}
                        @Override public void onActivityStopped(Activity activity) {}
                        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
                        @Override public void onActivityDestroyed(Activity activity) {}
                    });
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    }

    private static void applyBeforeDraw(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        applyNightConfiguration(activity, desiredNightMode(activity));
        try {
            ThemeManager.apply(activity);
        } catch (Throwable ignored) {}
    }

    /**
     * Returns true when a recreate was requested. This is mainly the Android 8/9
     * fallback where ActivityLifecycleCallbacks has no pre-create callback.
     */
    private static boolean ensureResolvedTheme(Activity activity) {
        if (activity == null || activity.isFinishing()) return false;

        int desired = desiredNightMode(activity);
        int current = activity.getResources().getConfiguration().uiMode & UI_MODE_NIGHT_MASK;
        if (current == desired) {
            try { ThemeManager.apply(activity); } catch (Throwable ignored) {}
            return false;
        }

        applyNightConfiguration(activity, desired);
        try { ThemeManager.apply(activity); } catch (Throwable ignored) {}

        Intent intent = activity.getIntent();
        boolean alreadyRecreated = intent != null && intent.getBooleanExtra(EXTRA_RECREATED, false);
        if (!alreadyRecreated) {
            if (intent != null) intent.putExtra(EXTRA_RECREATED, true);
            View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
            if (decor != null) {
                decor.post(activity::recreate);
            } else {
                activity.recreate();
            }
            return true;
        }
        return false;
    }

    private static void applyNightConfiguration(Activity activity, int desiredNight) {
        try {
            Resources resources = activity.getResources();
            Configuration configuration = new Configuration(resources.getConfiguration());
            configuration.uiMode = (configuration.uiMode & ~UI_MODE_NIGHT_MASK) | desiredNight;
            // Recovery is intentionally a small compatibility surface. updateConfiguration
            // keeps Android 8/9 working while API 29+ receives this before onCreate().
            resources.updateConfiguration(configuration, resources.getDisplayMetrics());
        } catch (Throwable ignored) {}
    }

    private static int desiredNightMode(Context context) {
        String mode;
        try {
            mode = ThemeManager.mode(context);
        } catch (Throwable ignored) {
            mode = ThemeManager.DARK;
        }

        if (ThemeManager.DARK.equals(mode) || ThemeManager.AMOLED.equals(mode)) {
            return UI_MODE_NIGHT_YES;
        }
        if (ThemeManager.LIGHT.equals(mode)) {
            return UI_MODE_NIGHT_NO;
        }
        if (ThemeManager.AUTO_PHONE.equals(mode)) {
            return phoneSystemNightMode();
        }

        // Auto • Forum: obey the forum-resolved theme when available; otherwise
        // fall back to the actual Android system appearance.
        try {
            String forum = ThemeManager.forumAutoTheme(context);
            if ("dark".equals(forum)) return UI_MODE_NIGHT_YES;
            if ("light".equals(forum)) return UI_MODE_NIGHT_NO;
        } catch (Throwable ignored) {}
        return phoneSystemNightMode();
    }

    private static int phoneSystemNightMode() {
        try {
            int system = Resources.getSystem().getConfiguration().uiMode & UI_MODE_NIGHT_MASK;
            return system == UI_MODE_NIGHT_YES ? UI_MODE_NIGHT_YES : UI_MODE_NIGHT_NO;
        } catch (Throwable ignored) {
            return UI_MODE_NIGHT_NO;
        }
    }

    private static void postSystemBarRefresh(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        View decor = activity.getWindow().getDecorView();
        if (decor == null) return;
        decor.post(() -> {
            try {
                ThemeManager.applySystemBars(activity);
                AppLogger.info(activity, "recovery_theme",
                        ThemeManager.label(activity) + " | " + ThemeManager.webColorScheme(activity));
            } catch (Throwable ignored) {}
        });
    }
}
