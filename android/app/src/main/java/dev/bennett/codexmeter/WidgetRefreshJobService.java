package dev.bennett.codexmeter;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

/** Fetches complete widget windows without changing App/Wear usage or adaptive statistics. */
public final class WidgetRefreshJobService extends JobService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ConcurrentMap<Integer, Run> active = new ConcurrentHashMap<>();

    @Override
    public boolean onStartJob(JobParameters params) {
        Run run = new Run(params);
        run.task = new FutureTask<>(run, null);
        Run previous = active.put(params.getJobId(), run);
        if (previous != null) previous.stop();
        executor.execute(run.task);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Run run = active.remove(params.getJobId());
        if (run != null) run.stop();
        boolean force = params.getExtras().getBoolean(WidgetRefreshScheduler.EXTRA_FORCE);
        boolean initial = params.getExtras().getBoolean(WidgetRefreshScheduler.EXTRA_INITIAL);
        return (initial || WidgetRefreshScheduler.hasWidgets(this))
                && (initial || force || ReferenceWidgetPreferences.refreshMinutes(this) > 0);
    }

    @Override
    public void onDestroy() {
        for (Run run : active.values()) run.stop();
        active.clear();
        executor.shutdownNow();
        super.onDestroy();
    }

    private final class Run implements Runnable {
        private final JobParameters params;
        private volatile boolean stopped;
        private FutureTask<Void> task;

        Run(JobParameters params) { this.params = params; }

        void stop() {
            stopped = true;
            task.cancel(true);
        }

        @Override
        public void run() {
            boolean missingCredentials = false;
            boolean force = params.getExtras().getBoolean(WidgetRefreshScheduler.EXTRA_FORCE);
            boolean initial = params.getExtras().getBoolean(WidgetRefreshScheduler.EXTRA_INITIAL);
            String requestedAccount = null;
            try {
                synchronized (UsageApi.NETWORK_LOCK) {
                    if (stopped || Thread.currentThread().isInterrupted()
                            || (!initial && !WidgetRefreshScheduler.hasWidgets(WidgetRefreshJobService.this))) return;
                    missingCredentials = !SecureTokenStore.isSignedIn(WidgetRefreshJobService.this);
                    requestedAccount = WidgetUsageStore.key(WidgetRefreshJobService.this);
                    // Recheck complete widget data under the API lock to coalesce bootstrap and
                    // automatic requests. A refresh of the legacy App cache cannot satisfy this.
                    long now = System.currentTimeMillis();
                    boolean due = initial ? WidgetRefreshScheduler.shouldFetchInitial(WidgetRefreshJobService.this, now)
                            : WidgetRefreshScheduler.shouldFetch(WidgetRefreshJobService.this, force, now);
                    if (!missingCredentials && due) {
                        WidgetUsageApi.refreshAndCache(getApplicationContext());
                        WidgetRefreshScheduler.recordSuccess(WidgetRefreshJobService.this, requestedAccount);
                    }
                }
            } catch (Exception error) {
                if (!stopped && requestedAccount != null) {
                    WidgetRefreshScheduler.recordFailure(WidgetRefreshJobService.this,
                            System.currentTimeMillis(), safeMessage(error), requestedAccount);
                    DiagnosticLog.error(WidgetRefreshJobService.this, "widget",
                            "home_refresh_failed", error);
                }
            } finally {
                active.remove(params.getJobId(), this);
                if (!stopped) {
                    WidgetRefreshScheduler.updateHomeWidgets(WidgetRefreshJobService.this);
                    jobFinished(params, false);
                    WidgetRefreshScheduler.finished(WidgetRefreshJobService.this,
                            params.getJobId(), missingCredentials);
                }
            }
        }
    }

    private static String safeMessage(Exception error) {
        String message = DiagnosticSanitizer.redact(error.getMessage()).trim();
        if (message.isEmpty()) return AppText.get(R.string.phone_usage_refresh_failed_a0acd);
        return message.length() > 240 ? message.substring(0, 240) : message;
    }
}
