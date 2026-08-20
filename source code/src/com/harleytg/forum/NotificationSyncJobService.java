package com.harleytg.forum;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.SharedPreferences;

/* loaded from: classes.dex */
public final class NotificationSyncJobService extends JobService {
    @Override // android.app.job.JobService
    public boolean onStopJob(JobParameters jobParameters) {
        return true;
    }

    @Override // android.app.job.JobService
    public boolean onStartJob(final JobParameters jobParameters) {
        AppExecutors.network().execute(new Runnable() { // from class: com.harleytg.forum.NotificationSyncJobService$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                NotificationSyncJobService.this.m128x38509368(jobParameters);
            }
        });
        return true;
    }

    /* renamed from: lambda$onStartJob$0$com-harleytg-forum-dev-NotificationSyncJobService, reason: not valid java name */
    /* synthetic */ void m128x38509368(JobParameters jobParameters) {
        try {
            syncNow();
        } finally {
            try {
            } finally {
            }
        }
    }

    private void syncNow() throws Exception {
        SharedPreferences sharedPreferences = getSharedPreferences("hcf_app", 0);
        if (sharedPreferences.getBoolean("background_notification_sync", true)) {
            String string = sharedPreferences.getString("session_user_id", "");
            if (string == null || string.trim().isEmpty()) {
                AppLogger.info(this, "background_notification_sync", "no-session");
            } else {
                String string2 = sharedPreferences.getString("active_host", "forum.harleytg.com");
                ForumNotificationSync.perform(this, ForumUrlRouter.isForumHost(string2) ? string2 : "forum.harleytg.com", string.trim(), "fallback-job");
            }
        }
    }
}
