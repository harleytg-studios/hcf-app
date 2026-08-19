package com.harleytg.forum.dev;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

/** Consolidated owner for Dev update orchestration and scheduling. */
final class UpdateManager {
    static final long FOREGROUND_MIN_INTERVAL_MS = 30L * 60L * 1000L;
    static final int JOB_ID = 41072;
    static final long PERIOD_MS = 6L * 60L * 60L * 1000L;

    static void applySchedule(Context context) {
        if (context == null) return;
        SharedPreferences prefs = AppSettings.prefs(context);
        if (!prefs.getBoolean(AppPrefs.UPDATE_AUTO_CHECK, true)) {
            cancelSchedule(context);
            return;
        }
        schedule(context);
    }

    static void schedule(Context context) {
        if (context == null) return;
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler == null) return;
            JobInfo info = new JobInfo.Builder(JOB_ID, new ComponentName(context, UpdateCheckJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(PERIOD_MS)
                    .setPersisted(true)
                    .build();
            int result = scheduler.schedule(info);
            AppLogger.info(context, "update_schedule", result == JobScheduler.RESULT_SUCCESS ? "scheduled_6h" : "failed");
        } catch (Throwable error) {
            AppLogger.error(context, "update_schedule", error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
        }
    }

    static void cancelSchedule(Context context) {
        if (context == null) return;
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler != null) scheduler.cancel(JOB_ID);
            AppLogger.info(context, "update_schedule", "cancelled");
        } catch (Throwable error) {
            AppLogger.error(context, "update_schedule_cancel", error.getClass().getSimpleName());
        }
    }

    static void maybeCheck(Context context, boolean force, UpdateAutomation.Listener listener) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        SharedPreferences prefs = AppSettings.prefs(app);
        if (!force && !prefs.getBoolean(AppPrefs.UPDATE_AUTO_CHECK, true)) {
            finish(listener, null, false, "Automatic update checks are off.");
            return;
        }
        long now = System.currentTimeMillis();
        long last = prefs.getLong(AppPrefs.UPDATE_LAST_CHECK, 0L);
        if (!force && last > 0L && now - last < FOREGROUND_MIN_INTERVAL_MS) {
            finish(listener, null, false, null);
            return;
        }

        // The Dev package is deliberately pinned to the Dev channel. Stable routing
        // cannot be selected unless BuildInfo explicitly permits it in a later release.
        String channel = BuildInfo.ALLOW_UPDATE_CHANNEL_SWITCH
                ? prefs.getString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL)
                : UpdateChecker.CHANNEL_DEV;
        UpdateChecker.check(app, channel, new UpdateChecker.Callback() {
            @Override public void onResult(UpdateChecker.Release release, boolean updateAvailable) {
                String priorTag = prefs.getString(AppPrefs.UPDATE_LAST_AVAILABLE_TAG, "");
                String releaseIdentity = release.assetKey();
                prefs.edit().putLong(AppPrefs.UPDATE_LAST_CHECK, System.currentTimeMillis()).apply();
                if (updateAvailable) {
                    prefs.edit().putString(AppPrefs.UPDATE_LAST_AVAILABLE_TAG, releaseIdentity).apply();
                    boolean autoDownload = prefs.getBoolean(AppPrefs.UPDATE_AUTO_DOWNLOAD, true);
                    if (autoDownload && release.apkUrl != null && !release.apkUrl.isEmpty()) {
                        AppUpdateDownloader.enqueue(app, release, false);
                    } else if (!releaseIdentity.equals(priorTag)) {
                        NotificationCenter.postUpdateAvailable(app, release);
                    }
                }
                boolean feedBehind = UpdateChecker.compareReleaseToInstalled(release) < 0;
                AppLogger.info(app, "update_auto_check", channel + " | " + release.tag
                        + " | newer=" + updateAvailable + " | feedBehind=" + feedBehind);
                finish(listener, release, updateAvailable, null);
            }

            @Override public void onError(String message) {
                prefs.edit().putLong(AppPrefs.UPDATE_LAST_CHECK, System.currentTimeMillis()).apply();
                AppLogger.warn(app, "update_auto_check", message);
                TelemetryService.sendDiagnosticEvent(app, "update_check_failed", message);
                finish(listener, null, false, message);
            }
        });
    }

    private static void finish(UpdateAutomation.Listener listener, UpdateChecker.Release release,
                               boolean updateAvailable, String error) {
        if (listener != null) listener.onFinished(release, updateAvailable, error);
    }

    private UpdateManager() {}
}

/** Compatibility facade retained for current Activity/JobService call sites. */
final class UpdateAutomation {
    interface Listener { void onFinished(UpdateChecker.Release release, boolean updateAvailable, String error); }
    static void maybeCheck(Context context, boolean force, Listener listener) {
        UpdateManager.maybeCheck(context, force, listener);
    }
    private UpdateAutomation() {}
}

/** Compatibility facade retained for current startup/settings call sites. */
final class UpdateScheduler {
    static void apply(Context context) { UpdateManager.applySchedule(context); }
    static void schedule(Context context) { UpdateManager.schedule(context); }
    static void cancel(Context context) { UpdateManager.cancelSchedule(context); }
    private UpdateScheduler() {}
}
