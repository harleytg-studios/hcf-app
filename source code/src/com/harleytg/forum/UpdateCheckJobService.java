package com.harleytg.forum.dev;

import android.app.job.JobParameters;
import android.app.job.JobService;
import com.harleytg.forum.dev.UpdateAutomation;
import com.harleytg.forum.dev.UpdateChecker;

/* loaded from: classes.dex */
public final class UpdateCheckJobService extends JobService {
    @Override // android.app.job.JobService
    public boolean onStopJob(JobParameters jobParameters) {
        return true;
    }

    /* renamed from: lambda$onStartJob$0$com-harleytg-forum-dev-UpdateCheckJobService, reason: not valid java name */
    /* synthetic */ void m211lambda$onStartJob$0$comharleytgforumdevUpdateCheckJobService(JobParameters jobParameters, UpdateChecker.Release release, boolean z, String str) {
        jobFinished(jobParameters, false);
    }

    @Override // android.app.job.JobService
    public boolean onStartJob(final JobParameters jobParameters) {
        UpdateAutomation.maybeCheck(this, true, new UpdateAutomation.Listener() { // from class: com.harleytg.forum.dev.UpdateCheckJobService$$ExternalSyntheticLambda0
            @Override // com.harleytg.forum.dev.UpdateAutomation.Listener
            public final void onFinished(UpdateChecker.Release release, boolean z, String str) {
                UpdateCheckJobService.this.m211lambda$onStartJob$0$comharleytgforumdevUpdateCheckJobService(jobParameters, release, z, str);
            }
        });
        return true;
    }
}
