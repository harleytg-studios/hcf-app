package com.harleytg.forum.dev;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

final class NotificationSyncScheduler {
    private static final int JOB_ID = 41071;
    private static final long PERIOD_MS = 15L * 60L * 1000L;

    static void apply(Context context) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
            if (!prefs.getBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true)
                    || !prefs.getBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, true)) {
                InstantNotificationService.stop(context);
                cancel(context);
                return;
            }
            String userId = prefs.getString(AppPrefs.SESSION_USER_ID, "");
            if (userId != null && !userId.trim().isEmpty()) {
                InstantNotificationService.start(context);
            } else {
                InstantNotificationService.stop(context);
            }
            // 15-minute JobScheduler remains only as a resilience fallback if an OEM
            // later stops the continuous instant-notification service.
            schedule(context);
        } catch (Throwable t) {
            AppLogger.error(context, "notification_sync_apply", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        }
    }

    static void schedule(Context context) {
        if (context == null) return;
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler == null) return;
            JobInfo info = new JobInfo.Builder(
                    JOB_ID,
                    new ComponentName(context, NotificationSyncJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(PERIOD_MS)
                    .setPersisted(true)
                    .build();
            int result = scheduler.schedule(info);
            AppLogger.info(context, "notification_sync_schedule", result == JobScheduler.RESULT_SUCCESS ? "scheduled" : "failed");
        } catch (Throwable t) {
            AppLogger.error(context, "notification_sync_schedule", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        }
    }

    static void cancel(Context context) {
        if (context == null) return;
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler != null) scheduler.cancel(JOB_ID);
            AppLogger.info(context, "notification_sync_schedule", "cancelled");
        } catch (Throwable t) {
            AppLogger.error(context, "notification_sync_cancel", t.getClass().getSimpleName());
        }
    }

    private NotificationSyncScheduler() {}
}
