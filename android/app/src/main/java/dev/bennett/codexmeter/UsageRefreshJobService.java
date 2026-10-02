package dev.bennett.codexmeter;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.os.SystemClock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

/** Runs the background usage refreshes scheduled by {@link RefreshScheduler}. */
public final class UsageRefreshJobService extends JobService {
    private static final int MAX_MESSAGE_LENGTH = 240;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    /** The latest run for each job ID; a newer start of the same job cancels the older run. */
    private final ConcurrentMap<Integer, JobRun> active = new ConcurrentHashMap<>();

    @Override
    public boolean onStartJob(final JobParameters params) {
        if (!SecureTokenStore.isSignedIn(this)) {
            DiagnosticLog.info(this, "scheduler", "refresh_job_skipped_signed_out",
                    "job_id", params.getJobId());
            WidgetRenderer.updateAll(this);
            return false;
        }
        final JobRun run = new JobRun(params);
        run.task = new FutureTask<Void>(run, null) {
            @Override
            protected void done() {
                if (isCancelled()) {
                    active.remove(params.getJobId(), run);
                }
            }
        };
        JobRun previous = active.put(params.getJobId(), run);
        if (previous != null) {
            previous.cancel();
        }
        executor.execute(run.task);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        DiagnosticLog.warn(this, "scheduler", "refresh_job_stopped",
                "job_id", params.getJobId());
        JobRun run = active.remove(params.getJobId());
        if (run != null) {
            run.cancel();
        }
        return true;
    }

    @Override
    public void onDestroy() {
        for (JobRun run : active.values()) {
            run.cancel();
        }
        active.clear();
        executor.shutdownNow();
        super.onDestroy();
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Usage refresh failed.";
        }
        return message.length() > MAX_MESSAGE_LENGTH
                ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
    }

    private final class JobRun implements Runnable {
        private final JobParameters params;
        /** Whether this job is a link in the chain that schedules its own successor. */
        private final boolean chainedCycle;
        private final String reason;
        private volatile boolean stopped;
        private FutureTask<Void> task;

        JobRun(JobParameters params) {
            this.params = params;
            this.reason = RefreshScheduler.reason(params.getExtras());
            this.chainedCycle = RefreshScheduler.REASON_SHORT_PERIODIC.equals(reason)
                    || RefreshScheduler.REASON_ADAPTIVE.equals(reason);
        }

        void cancel() {
            stopped = true;
            task.cancel(true);
        }

        @Override
        public void run() {
            long startedAt = SystemClock.elapsedRealtime();
            DiagnosticLog.info(UsageRefreshJobService.this, "scheduler", "refresh_job_started",
                    "job_id", params.getJobId(),
                    "reason", reason);
            try {
                try {
                    refresh(startedAt);
                } catch (Exception e) {
                    onRefreshFailed(e, startedAt);
                }
            } catch (Throwable throwable) {
                WidgetRenderer.updateAll(getApplicationContext());
                finish(false);
                throw throwable;
            }
        }

        private void refresh(long startedAt) throws Exception {
            Context app = getApplicationContext();
            RefreshScheduler.scheduleAtNextReset(app, UsageApi.refreshAndCache(app));
            AppPreferences.recordRefreshSuccess(app);
            WidgetRenderer.updateAll(app);
            DiagnosticLog.info(UsageRefreshJobService.this, "scheduler",
                    "refresh_job_succeeded",
                    "job_id", params.getJobId(),
                    "reason", reason,
                    "duration_ms", SystemClock.elapsedRealtime() - startedAt);
            finish(false);
        }

        private void onRefreshFailed(Exception e, long startedAt) {
            Context app = getApplicationContext();
            DiagnosticLog.error(UsageRefreshJobService.this, "scheduler",
                    "refresh_job_failed", e,
                    "job_id", params.getJobId(),
                    "reason", reason,
                    "duration_ms", SystemClock.elapsedRealtime() - startedAt);
            AppPreferences.setLastError(app, safeMessage(e));
            AppPreferences.recordRefreshFailure(app);
            WidgetRenderer.updateAll(app);
            // A chained job schedules its own retry instead of using JobScheduler's backoff.
            finish(!chainedCycle);
        }

        /**
         * Releases this run and, unless the job was stopped or replaced, reports completion and
         * schedules the next link of a chained refresh.
         */
        private void finish(boolean needsReschedule) {
            active.remove(params.getJobId(), this);
            if (stopped) {
                return;
            }
            jobFinished(params, needsReschedule);
            Context app = getApplicationContext();
            if (chainedCycle && SecureTokenStore.isSignedIn(app)) {
                RefreshScheduler.scheduleNextShort(app, params.getJobId());
            }
        }
    }
}
