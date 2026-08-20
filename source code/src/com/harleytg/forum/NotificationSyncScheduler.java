package com.harleytg.forum;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

/* loaded from: classes.dex */
final class NotificationSyncScheduler {
    private static final int JOB_ID = 41071;
    private static final long PERIOD_MS = 900000;

    static void apply(Context context) {
        if (context == null) {
            return;
        }
        try {
            SharedPreferences sharedPreferences = context.getSharedPreferences("hcf_app", 0);
            if (!sharedPreferences.getBoolean("background_notification_sync", true)) {
                InstantNotificationService.stop(context);
                cancel(context);
                return;
            }
            String string = sharedPreferences.getString("session_user_id", "");
            sharedPreferences.getBoolean("silence_background_service_notification", false);
            if (string != null && !string.trim().isEmpty()) {
                InstantNotificationService.start(context);
            } else {
                InstantNotificationService.stop(context);
            }
            schedule(context);
        } catch (Throwable th) {
            AppLogger.error(context, "notification_sync_apply", th.getClass().getSimpleName() + ": " + String.valueOf(th.getMessage()));
        }
    }

    static void schedule(Context context) {
        if (context == null) {
            return;
        }
        try {
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService("jobscheduler");
            if (jobScheduler == null) {
                return;
            }
            AppLogger.info(context, "notification_sync_schedule", jobScheduler.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, (Class<?>) NotificationSyncJobService.class)).setRequiredNetworkType(1).setPeriodic(PERIOD_MS).setPersisted(true).build()) == 1 ? "scheduled" : "failed");
        } catch (Throwable th) {
            AppLogger.error(context, "notification_sync_schedule", th.getClass().getSimpleName() + ": " + String.valueOf(th.getMessage()));
        }
    }

    static void cancel(Context context) {
        if (context == null) {
            return;
        }
        try {
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService("jobscheduler");
            if (jobScheduler != null) {
                jobScheduler.cancel(JOB_ID);
            }
            AppLogger.info(context, "notification_sync_schedule", "cancelled");
        } catch (Throwable th) {
            AppLogger.error(context, "notification_sync_cancel", th.getClass().getSimpleName());
        }
    }

    private NotificationSyncScheduler() {
    }
}
