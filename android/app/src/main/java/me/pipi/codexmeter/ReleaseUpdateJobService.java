package me.pipi.codexmeter;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Executes persisted release checks without keeping an Activity alive. */
public final class ReleaseUpdateJobService extends JobService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile Future<?> activeTask;
    /** Parameters of the running job; cleared once the job is finished or stopped. */
    private volatile JobParameters activeParameters;

    @Override
    public boolean onStartJob(JobParameters params) {
        if (!UpdatePreferences.automaticChecks(this)) {
            DiagnosticLog.info(this, "scheduler", "release_job_skipped_disabled");
            return false;
        }
        DiagnosticLog.info(this, "scheduler", "release_job_started",
                "job_id", params.getJobId());
        activeParameters = params;
        activeTask = executor.submit(() -> runCheck(params));
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        DiagnosticLog.warn(this, "scheduler", "release_job_stopped",
                "job_id", params.getJobId());
        Future<?> task = activeTask;
        if (activeParameters == params) {
            activeParameters = null;
            activeTask = null;
        }
        if (task != null) {
            task.cancel(true);
        }
        return UpdatePreferences.automaticChecks(this);
    }

    @Override
    public void onDestroy() {
        Future<?> task = activeTask;
        if (task != null) {
            task.cancel(true);
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    /** Runs on the executor; reports completion unless the job was stopped meanwhile. */
    private void runCheck(JobParameters params) {
        boolean retry = false;
        try {
            ReleaseUpdateClient.check(getApplicationContext());
        } catch (Exception exception) {
            DiagnosticLog.error(getApplicationContext(), "scheduler",
                    "release_job_failed", exception,
                    "job_id", params.getJobId());
            retry = true;
        } finally {
            if (activeParameters == params) {
                activeParameters = null;
                activeTask = null;
                jobFinished(params, retry);
            }
        }
    }
}
