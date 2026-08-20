package com.harleytg.forum;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Periodic background job for the locked Stable update channel. */
public final class UpdateCheckJobService extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        UpdateAutomation.maybeCheck(this, true, new UpdateAutomation.Listener() {
            @Override
            public void onFinished(UpdateChecker.Release release, boolean updateAvailable, String error) {
                jobFinished(params, false);
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
