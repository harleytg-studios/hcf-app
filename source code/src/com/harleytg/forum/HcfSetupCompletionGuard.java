package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

/**
 * Keeps the App Setup hamburger entry aligned with setup completion state.
 *
 * While setup is incomplete, SetupCenter may expose its normal drawer entry.
 * Once Finish Setup marks SETUP_COMPLETED, the entry is removed whenever
 * MainActivity resumes so the completed wizard cannot be reopened from the
 * hamburger menu. If setup completion is reset later, the normal entry is
 * allowed to return.
 */
public final class HcfSetupCompletionGuard {
    private static final String DRAWER_TAG = "hcf_app_setup_drawer";
    private static boolean installed;

    private HcfSetupCompletionGuard() {}

    private static synchronized void install(Context context) {
        if (installed || context == null) return;
        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof Application)) return;
        installed = true;

        ((Application) appContext).registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override public void onActivityCreated(Activity activity, Bundle state) {}
                    @Override public void onActivityStarted(Activity activity) {}

                    @Override public void onActivityResumed(Activity activity) {
                        if (!(activity instanceof HcfMainActivities.MainActivity)) return;
                        syncDrawer((HcfMainActivities.MainActivity) activity);
                    }

                    @Override public void onActivityPaused(Activity activity) {}
                    @Override public void onActivityStopped(Activity activity) {}
                    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
                    @Override public void onActivityDestroyed(Activity activity) {}
                }
        );
    }

    private static void syncDrawer(HcfMainActivities.MainActivity activity) {
        if (activity == null || activity.isFinishing()) return;

        boolean completed = activity
                .getSharedPreferences(AppPrefs.FILE, 0)
                .getBoolean(AppPrefs.SETUP_COMPLETED, false);

        if (!completed) {
            try {
                SetupCenter.installDrawerEntry(activity);
            } catch (Throwable error) {
                AppLogger.warn(activity, "app_setup_drawer_guard",
                        "restore_failed_" + error.getClass().getSimpleName());
            }
            return;
        }

        try {
            View settings = activity.findViewById(R.id.drawerSettings);
            if (settings == null || !(settings.getParent() instanceof ViewGroup)) return;
            ViewGroup parent = (ViewGroup) settings.getParent();

            boolean removed = false;
            for (int i = parent.getChildCount() - 1; i >= 0; i--) {
                View child = parent.getChildAt(i);
                if (DRAWER_TAG.equals(child.getTag())) {
                    parent.removeViewAt(i);
                    removed = true;
                }
            }

            if (removed) {
                AppLogger.info(activity, "app_setup_drawer", "hidden_after_completion");
            }
        } catch (Throwable error) {
            AppLogger.warn(activity, "app_setup_drawer_guard",
                    "hide_failed_" + error.getClass().getSimpleName());
        }
    }

    /** Installed before activities so the guard can react as soon as MainActivity resumes. */
    public static final class BootstrapProvider extends ContentProvider {
        @Override public boolean onCreate() {
            install(getContext());
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection,
                                      String[] selectionArgs, String sortOrder) {
            return null;
        }

        @Override public String getType(Uri uri) {
            return null;
        }

        @Override public Uri insert(Uri uri, ContentValues values) {
            return null;
        }

        @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override public int update(Uri uri, ContentValues values, String selection,
                                    String[] selectionArgs) {
            return 0;
        }
    }
}
