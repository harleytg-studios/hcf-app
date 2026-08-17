package com.harleytg.forum;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.SharedPreferences;

/** Conservative periodic fallback if an OEM stops the low-latency service. */
public final class NotificationSyncJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                syncNow();
            } catch (Throwable t) {
                AppLogger.error(this, "background_notification_sync",
                        t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
            } finally {
                jobFinished(params, false);
            }
        }, "hcf-notification-sync-fallback").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    private void syncNow() throws Exception {
        SharedPreferences prefs = getSharedPreferences(AppPrefs.FILE, MODE_PRIVATE);
        if (!prefs.getBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true)
                || !prefs.getBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, true)) return;

        String userId = prefs.getString(AppPrefs.SESSION_USER_ID, "");
        if (userId == null || userId.trim().isEmpty()) {
            AppLogger.info(this, "background_notification_sync", "no-session");
            return;
        }

        String host = prefs.getString(AppPrefs.ACTIVE_HOST, ForumConfig.PRIMARY_HOST);
        if (!ForumUrlRouter.isForumHost(host)) host = ForumConfig.PRIMARY_HOST;
        ForumNotificationSync.perform(this, host, userId.trim(), "fallback-job");
    }
}
