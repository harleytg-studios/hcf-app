package com.harleytg.forum;

import android.app.job.JobParameters;
import android.app.job.JobService;

public final class UpdateCheckJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        UpdateAutomation.maybeCheck(this, true, (release, updateAvailable, error) -> jobFinished(params, false));
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
