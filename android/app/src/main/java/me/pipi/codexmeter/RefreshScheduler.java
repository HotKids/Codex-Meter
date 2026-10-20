package me.pipi.codexmeter;

import dev.bennett.codexmeter.AdaptiveRefreshPolicy;
import dev.bennett.codexmeter.UsageSnapshot;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import java.util.Calendar;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Schedules background usage refreshes through {@link UsageRefreshJobService}.
 *
 * <p>Fixed intervals of at least {@value #MIN_PERIODIC_MINUTES} minutes use a periodic job.
 * Shorter fixed intervals and automatic (adaptive) refresh use a chain of one-shot jobs that
 * alternate between two job IDs, each scheduling the next before it finishes.
 */
public final class RefreshScheduler {
    static final String REASON_ADAPTIVE = "adaptive";
    static final String REASON_SHORT_PERIODIC = "short_periodic";
    static final String REASON_MANUAL = "manual";
    private static final String REASON_IMMEDIATE = "immediate";
    private static final String REASON_PERIODIC = "periodic";
    private static final String REASON_RESET = "reset";
    private static final String EXTRA_REASON = "reason";
    private static final String EXTRA_GENERATION = "generation";

    private static final int PERIODIC_JOB_ID = 73100;
    private static final int IMMEDIATE_JOB_ID = 73101;
    private static final int RESET_JOB_ID = 73102;
    private static final int SHORT_JOB_ID_A = 73103;
    private static final int SHORT_JOB_ID_B = 73104;
    private static final int[] RECURRING_JOB_IDS = {
            PERIODIC_JOB_ID, SHORT_JOB_ID_A, SHORT_JOB_ID_B};
    // Scheduling must not acquire the widget feedback monitor while holding this lock.
    private static final Object SCHEDULE_LOCK = new Object();

    /** JobScheduler's minimum period; shorter fixed intervals are chained instead. */
    private static final int MIN_PERIODIC_MINUTES = 15;
    private static final int DEFAULT_REFRESH_MINUTES = 30;
    private static final long DEADLINE_SLACK_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long IMMEDIATE_DEADLINE_MS = 5000L;
    private static final long MIN_RESET_DELAY_MS = 1000L;
    /** Waits a little past the reset so the server reports the refreshed window. */
    private static final long RESET_GRACE_MS = 5000L;

    private RefreshScheduler() {
    }

    /** Keeps a healthy recurring job's deadline; rebuilds only missing or obsolete schedules. */
    public static boolean schedulePeriodic(Context context) {
        return ensurePeriodic(context, -1, "");
    }

    /** The finishing generation cannot count as its own successor. */
    static boolean scheduleNextShort(Context context, int finishedJobId, String finishedGeneration) {
        return ensurePeriodic(context, finishedJobId, finishedGeneration);
    }

    private static boolean ensurePeriodic(Context context, int finishedJobId,
            String finishedGeneration) {
        Context app = appContext(context);
        if (app == null) {
            return false;
        }
        if (!SecureTokenStore.isSignedIn(app)) {
            DiagnosticLog.info(app, "scheduler", "refresh_schedule_skipped_signed_out");
            cancelAll(app);
            return true;
        }
        try {
            synchronized (SCHEDULE_LOCK) {
                if (!SecureTokenStore.isSignedIn(app)) return false;
                JobScheduler scheduler = scheduler(app);
                if (scheduler == null) {
                    reportSchedulerUnavailable(app);
                    return false;
                }
                boolean automatic = AppPreferences.getAutomaticRefresh(app);
                int configuredMinutes = AppPreferences.getRefreshMinutes(app);
                String reason = automatic ? REASON_ADAPTIVE
                        : configuredMinutes < MIN_PERIODIC_MINUTES
                                ? REASON_SHORT_PERIODIC : REASON_PERIODIC;
                long intervalMillis = TimeUnit.MINUTES.toMillis(configuredMinutes);
                for (int jobId : RECURRING_JOB_IDS) {
                    JobInfo job = scheduler.getPendingJob(jobId);
                    boolean finishing = job != null && jobId == finishedJobId
                            && finishedGeneration.equals(generation(job.getExtras()));
                    if (!finishing && matches(job, reason, intervalMillis)) {
                        cancelObsolete(scheduler, reason, intervalMillis, finishedJobId);
                        return true;
                    }
                }
                int refreshMinutes = automatic ? effectiveRefreshMinutes(app) : configuredMinutes;
                DiagnosticLog.info(app, "scheduler", "refresh_schedule_requested",
                        "mode", automatic ? "adaptive" : "fixed",
                        "minutes", refreshMinutes);
                JobInfo.Builder next;
                if (REASON_PERIODIC.equals(reason)) {
                    next = baseJob(app, PERIODIC_JOB_ID, reason)
                            .setPeriodic(intervalMillis).setPersisted(true);
                } else {
                    int nextJobId = finishedJobId == SHORT_JOB_ID_A
                            ? SHORT_JOB_ID_B : SHORT_JOB_ID_A;
                    long delayMillis = TimeUnit.MINUTES.toMillis(refreshMinutes);
                    next = baseJob(app, nextJobId, reason)
                            .setMinimumLatency(delayMillis)
                            .setOverrideDeadline(delayMillis + DEADLINE_SLACK_MS);
                }
                if (!submit(app, next.build())) return false;
                cancelObsolete(scheduler, reason, intervalMillis, finishedJobId);
                return true;
            }
        } catch (RuntimeException e) {
            return failed(app, e);
        }
    }

    private static boolean matches(JobInfo job, String expectedReason, long intervalMillis) {
        if (job == null || !expectedReason.equals(reason(job.getExtras()))) return false;
        if (REASON_PERIODIC.equals(expectedReason)) {
            return job.getId() == PERIODIC_JOB_ID && job.isPeriodic()
                    && job.getIntervalMillis() == intervalMillis;
        }
        return (job.getId() == SHORT_JOB_ID_A || job.getId() == SHORT_JOB_ID_B)
                && !job.isPeriodic()
                && (REASON_ADAPTIVE.equals(expectedReason)
                        || job.getMinLatencyMillis() == intervalMillis);
    }

    private static void cancelObsolete(JobScheduler scheduler, String expectedReason,
            long intervalMillis, int finishedJobId) {
        for (int jobId : RECURRING_JOB_IDS) {
            JobInfo job = scheduler.getPendingJob(jobId);
            // Retain the running job until jobFinished so a rejected handoff can retry it.
            if (job != null && jobId != finishedJobId
                    && !matches(job, expectedReason, intervalMillis)) {
                scheduler.cancel(jobId);
            }
        }
    }

    /** The interval until the next background refresh, adapted to usage when automatic. */
    public static int effectiveRefreshMinutes(Context context) {
        Context app = appContext(context);
        if (app == null) {
            return DEFAULT_REFRESH_MINUTES;
        }
        if (!AppPreferences.getAutomaticRefresh(app)) {
            return AppPreferences.getRefreshMinutes(app);
        }
        long now = System.currentTimeMillis();
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return AdaptiveRefreshPolicy.chooseMinutes(
                AppPreferences.loadSnapshot(app),
                RefreshEngagement.score(app, now),
                hour,
                AppPreferences.getRefreshFailures(app),
                now);
    }

    public static boolean scheduleImmediate(Context context) {
        return scheduleImmediate(context, false);
    }

    static boolean scheduleManual(Context context) {
        return scheduleImmediate(context, true);
    }

    private static boolean scheduleImmediate(Context context, boolean manual) {
        Context app = appContext(context);
        if (app == null) {
            return false;
        }
        if (!SecureTokenStore.isSignedIn(app)) {
            WidgetRefreshStatus.clear(app);
            WidgetRenderer.updateAll(app);
            return true;
        }
        try {
            schedulePeriodic(app);
            boolean accepted;
            boolean refreshing;
            synchronized (WidgetRefreshStatus.class) {
                JobScheduler scheduler = scheduler(app);
                JobInfo pending = scheduler == null ? null : scheduler.getPendingJob(IMMEDIATE_JOB_ID);
                boolean manualFeedback = manual || (pending != null
                        && REASON_MANUAL.equals(reason(pending.getExtras())));
                refreshing = manual || WidgetRefreshStatus.isRefreshing(app);
                DiagnosticLog.info(app, "scheduler", "immediate_refresh_requested");
                JobInfo job = baseJob(app, IMMEDIATE_JOB_ID,
                        manualFeedback ? REASON_MANUAL : REASON_IMMEDIATE)
                        .setMinimumLatency(0L)
                        .setOverrideDeadline(IMMEDIATE_DEADLINE_MS)
                        .build();
                accepted = submit(app, job);
                if (accepted && refreshing) WidgetRefreshStatus.queued(app, job);
            }
            if (accepted && refreshing) WidgetRenderer.updateAll(app);
            return accepted;
        } catch (RuntimeException e) {
            return failed(app, e);
        }
    }

    /** Schedules a one-shot refresh shortly after the snapshot's next usage-window reset. */
    public static boolean scheduleAtNextReset(Context context, UsageSnapshot snapshot) {
        Context app = appContext(context);
        if (app == null || snapshot == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        long nextResetMillis = snapshot.nextResetMillis(now);
        if (nextResetMillis <= now) {
            return false;
        }
        try {
            long delayMillis = Math.max(MIN_RESET_DELAY_MS, nextResetMillis - now + RESET_GRACE_MS);
            DiagnosticLog.info(app, "scheduler", "reset_refresh_requested",
                    "delay_ms", delayMillis);
            return submit(app, baseJob(app, RESET_JOB_ID, REASON_RESET)
                    .setMinimumLatency(delayMillis)
                    .setOverrideDeadline(delayMillis + DEADLINE_SLACK_MS)
                    .build());
        } catch (RuntimeException e) {
            return failed(app, e);
        }
    }

    public static void cancelAll(Context context) {
        Context app = appContext(context);
        if (app == null) {
            return;
        }
        boolean feedbackEnded = false;
        try {
            synchronized (WidgetRefreshStatus.class) {
                feedbackEnded = WidgetRefreshStatus.clear(app);
                JobScheduler scheduler = scheduler(app);
                if (scheduler == null) {
                    return;
                }
                synchronized (SCHEDULE_LOCK) {
                    scheduler.cancel(PERIODIC_JOB_ID);
                    scheduler.cancel(IMMEDIATE_JOB_ID);
                    scheduler.cancel(RESET_JOB_ID);
                    scheduler.cancel(SHORT_JOB_ID_A);
                    scheduler.cancel(SHORT_JOB_ID_B);
                }
                AppPreferences.setSchedulerError(app, "");
                DiagnosticLog.info(app, "scheduler", "all_refresh_jobs_cancelled");
            }
        } catch (RuntimeException e) {
            failed(app, e);
        } finally {
            if (feedbackEnded) WidgetRenderer.updateAll(app);
        }
    }

    /** The scheduling reason stored in a refresh job's extras, or "" when absent. */
    static String reason(PersistableBundle extras) {
        return extras == null ? "" : extras.getString(EXTRA_REASON, "");
    }

    static String generation(PersistableBundle extras) {
        return extras == null ? "" : extras.getString(EXTRA_GENERATION, "");
    }

    private static boolean submit(Context context, JobInfo job) {
        JobScheduler scheduler = scheduler(context);
        if (scheduler == null) {
            reportSchedulerUnavailable(context);
            return false;
        }
        int result = scheduler.schedule(job);
        String reason = reason(job.getExtras());
        if (result == JobScheduler.RESULT_SUCCESS) {
            AppPreferences.setSchedulerError(context, "");
            DiagnosticLog.info(context, "scheduler", "job_scheduled",
                    "job_id", job.getId(),
                    "reason", reason);
            return true;
        }
        DiagnosticLog.warn(context, "scheduler", "job_rejected",
                "job_id", job.getId(),
                "reason", reason,
                "result", result);
        AppPreferences.setSchedulerError(context,
                context.getString(R.string.auth_error_scheduler_declined));
        return false;
    }

    private static JobInfo.Builder baseJob(Context context, int jobId, String reason) {
        PersistableBundle extras = new PersistableBundle();
        extras.putString(EXTRA_REASON, reason);
        extras.putString(EXTRA_GENERATION, UUID.randomUUID().toString());
        return new JobInfo.Builder(jobId,
                new ComponentName(context, UsageRefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setExtras(extras);
    }

    private static JobScheduler scheduler(Context context) {
        return (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }

    private static void reportSchedulerUnavailable(Context context) {
        AppPreferences.setSchedulerError(context,
                context.getString(R.string.auth_error_scheduler_unavailable));
    }

    private static Context appContext(Context context) {
        if (context == null) {
            return null;
        }
        Context applicationContext = context.getApplicationContext();
        return applicationContext != null ? applicationContext : context;
    }

    private static boolean failed(Context context, RuntimeException exception) {
        String message = exception.getLocalizedMessage();
        if (message == null || message.trim().isEmpty()) {
            message = exception.getClass().getSimpleName();
        }
        AppPreferences.setSchedulerError(context,
                context.getString(R.string.auth_error_scheduler_failed, message));
        DiagnosticLog.error(context, "scheduler", "scheduler_failed", exception);
        return false;
    }
}
