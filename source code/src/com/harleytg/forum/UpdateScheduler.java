package com.harleytg.forum.dev;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

/* loaded from: classes.dex */
final class UpdateScheduler {
    private static final int JOB_ID = 41072;
    private static final long PERIOD_MS = 21600000;

    static void apply(Context context) {
        if (context == null) {
            return;
        }
        if (!context.getSharedPreferences("hcf_app", 0).getBoolean("update_auto_check", true)) {
            cancel(context);
        } else {
            schedule(context);
        }
    }

    static void schedule(Context context) {
        try {
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService("jobscheduler");
            if (jobScheduler == null) {
                return;
            }
            AppLogger.info(context, "update_schedule", jobScheduler.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, (Class<?>) UpdateCheckJobService.class)).setRequiredNetworkType(1).setPeriodic(PERIOD_MS).setPersisted(true).build()) == 1 ? "scheduled_6h" : "failed");
        } catch (Throwable th) {
            AppLogger.error(context, "update_schedule", th.getClass().getSimpleName() + ": " + String.valueOf(th.getMessage()));
        }
    }

    static void cancel(Context context) {
        try {
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService("jobscheduler");
            if (jobScheduler != null) {
                jobScheduler.cancel(JOB_ID);
            }
            AppLogger.info(context, "update_schedule", "cancelled");
        } catch (Throwable th) {
            AppLogger.error(context, "update_schedule_cancel", th.getClass().getSimpleName());
        }
    }

    private UpdateScheduler() {
    }
}
