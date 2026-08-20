package com.harleytg.forum;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.SharedPreferences;

public final class NotificationSyncJobService extends JobService {
    @Override public boolean onStopJob(JobParameters params) { return true; }

    @Override public boolean onStartJob(final JobParameters params) {
        SharedPreferences prefs = getSharedPreferences("hcf_app", 0);
        if (!prefs.getBoolean("background_notification_sync", true)) return false;
        String userId = prefs.getString("session_user_id", "");
        if (userId == null || userId.trim().isEmpty()) return false;

        AppExecutors.network().execute(new Runnable() {
            @Override public void run() {
                try { syncNow(); }
                catch (Throwable t) {
                    AppLogger.warn(NotificationSyncJobService.this, "background_notification_sync", "job-failed | " + t.getClass().getSimpleName());
                } finally {
                    try { jobFinished(params, false); } catch (Throwable ignored) {}
                }
            }
        });
        return true;
    }

    private void syncNow() throws Exception {
        SharedPreferences prefs = getSharedPreferences("hcf_app", 0);
        if (!prefs.getBoolean("background_notification_sync", true)) return;
        String userId = prefs.getString("session_user_id", "");
        if (userId == null || userId.trim().isEmpty()) return;
        String host = prefs.getString("active_host", "forum.harleytg.com");
        ForumNotificationSync.perform(this, ForumUrlRouter.isForumHost(host) ? host : "forum.harleytg.com", userId.trim(), "fallback-job");
    }
}
