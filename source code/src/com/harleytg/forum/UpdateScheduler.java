package com.harleytg.forum.dev;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

final class UpdateScheduler {
    private static final int JOB_ID = 41072;
    private static final long PERIOD_MS = 6L * 60L * 60L * 1000L;

    static void apply(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(AppPrefs.UPDATE_AUTO_CHECK, true)) {
            cancel(context);
            return;
        }
        schedule(context);
    }

    static void schedule(Context context) {
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler == null) return;
            JobInfo info = new JobInfo.Builder(
                    JOB_ID,
                    new ComponentName(context, UpdateCheckJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(PERIOD_MS)
                    .setPersisted(true)
                    .build();
            int result = scheduler.schedule(info);
            AppLogger.info(context, "update_schedule", result == JobScheduler.RESULT_SUCCESS ? "scheduled_6h" : "failed");
        } catch (Throwable t) {
            AppLogger.error(context, "update_schedule", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        }
    }

    static void cancel(Context context) {
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler != null) scheduler.cancel(JOB_ID);
            AppLogger.info(context, "update_schedule", "cancelled");
        } catch (Throwable t) {
            AppLogger.error(context, "update_schedule_cancel", t.getClass().getSimpleName());
        }
    }

    private UpdateScheduler() {}
}
