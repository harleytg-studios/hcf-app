package com.harleytg.forum.dev;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

final class NotificationSyncScheduler {
    private static final int JOB_ID = 41071;
    private static final long PERIOD_MS = 900000L;

    static void apply(Context context) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences("hcf_app", 0);
            if (!prefs.getBoolean("background_notification_sync", true)) {
                InstantNotificationService.stop(context);
                cancel(context);
                AppLogger.info(context, "notification_sync_mode", "disabled");
                return;
            }

            String userId = prefs.getString("session_user_id", "");
            boolean silenceForegroundStatus = prefs.getBoolean("silence_background_service_notification", false);

            if (userId != null && !userId.trim().isEmpty()) {
                if (silenceForegroundStatus) {
                    // Android requires a visible notification for a foreground service.
                    // Honor the user's silence switch by stopping that foreground service.
                    // Foreground WebSocket events can still request silent one-shot syncs,
                    // while JobScheduler remains the background fallback.
                    InstantNotificationService.stop(context);
                    AppLogger.info(context, "notification_sync_mode", "silent fallback • foreground service stopped");
                } else {
                    InstantNotificationService.start(context);
                    AppLogger.info(context, "notification_sync_mode", "foreground live sync");
                }
            } else {
                InstantNotificationService.stop(context);
                AppLogger.info(context, "notification_sync_mode", "waiting for signed-in session");
            }

            schedule(context);
        } catch (Throwable t) {
            AppLogger.error(context, "notification_sync_apply", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        }
    }

    static void schedule(Context context) {
        if (context == null) return;
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService("jobscheduler");
            if (scheduler == null) return;
            JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, NotificationSyncJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(PERIOD_MS)
                    .setPersisted(true)
                    .build();
            AppLogger.info(context, "notification_sync_schedule", scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS ? "scheduled" : "failed");
        } catch (Throwable t) {
            AppLogger.error(context, "notification_sync_schedule", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        }
    }

    static void cancel(Context context) {
        if (context == null) return;
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService("jobscheduler");
            if (scheduler != null) scheduler.cancel(JOB_ID);
            AppLogger.info(context, "notification_sync_schedule", "cancelled");
        } catch (Throwable t) {
            AppLogger.error(context, "notification_sync_cancel", t.getClass().getSimpleName());
        }
    }

    private NotificationSyncScheduler() {}
}
