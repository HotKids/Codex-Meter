package dev.bennett.codexmeter;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * Schedules background usage refreshes through {@link UsageRefreshJobService}.
 *
 * <p>Fixed intervals of at least {@value #MIN_PERIODIC_MINUTES} minutes use a periodic job.
 * Shorter fixed intervals and automatic (adaptive) refresh use a chain of one-shot jobs that
 * alternate between two job IDs, each scheduling the next when it finishes.
 */
public final class RefreshScheduler {
    static final String REASON_ADAPTIVE = "adaptive";
    static final String REASON_SHORT_PERIODIC = "short_periodic";
    private static final String REASON_IMMEDIATE = "immediate";
    private static final String REASON_PERIODIC = "periodic";
    private static final String REASON_RESET = "reset";
    private static final String EXTRA_REASON = "reason";

    private static final int PERIODIC_JOB_ID = 73100;
    private static final int IMMEDIATE_JOB_ID = 73101;
    private static final int RESET_JOB_ID = 73102;
    private static final int SHORT_JOB_ID_A = 73103;
    private static final int SHORT_JOB_ID_B = 73104;

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

    public static boolean schedulePeriodic(Context context) {
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
            JobScheduler scheduler = scheduler(app);
            if (scheduler == null) {
                reportSchedulerUnavailable(app);
                return false;
            }
            scheduler.cancel(PERIODIC_JOB_ID);
            scheduler.cancel(SHORT_JOB_ID_A);
            scheduler.cancel(SHORT_JOB_ID_B);
            if (AppPreferences.getAutomaticRefresh(app)) {
                DiagnosticLog.info(app, "scheduler", "refresh_schedule_requested",
                        "mode", "adaptive",
                        "minutes", effectiveRefreshMinutes(app));
                return scheduleNextChained(app, SHORT_JOB_ID_B, REASON_ADAPTIVE);
            }
            int refreshMinutes = AppPreferences.getRefreshMinutes(app);
            DiagnosticLog.info(app, "scheduler", "refresh_schedule_requested",
                    "mode", "fixed",
                    "minutes", refreshMinutes);
            if (refreshMinutes < MIN_PERIODIC_MINUTES) {
                return scheduleNextChained(app, SHORT_JOB_ID_B, REASON_SHORT_PERIODIC);
            }
            return submit(app, baseJob(app, PERIODIC_JOB_ID, REASON_PERIODIC)
                    .setPeriodic(TimeUnit.MINUTES.toMillis(refreshMinutes))
                    .setPersisted(true)
                    .build());
        } catch (RuntimeException e) {
            return failed(app, e);
        }
    }

    /** Called when a chained job finishes to schedule the next link in the chain. */
    static boolean scheduleNextShort(Context context, int finishedJobId) {
        Context app = appContext(context);
        if (app == null) {
            return false;
        }
        if (!SecureTokenStore.isSignedIn(app)) {
            return true;
        }
        boolean automatic = AppPreferences.getAutomaticRefresh(app);
        if (!automatic && AppPreferences.getRefreshMinutes(app) >= MIN_PERIODIC_MINUTES) {
            // The user switched to an interval a periodic job can handle.
            return schedulePeriodic(app);
        }
        return scheduleNextChained(app, finishedJobId,
                automatic ? REASON_ADAPTIVE : REASON_SHORT_PERIODIC);
    }

    private static boolean scheduleNextChained(Context context, int previousJobId,
            String reason) {
        int refreshMinutes = effectiveRefreshMinutes(context);
        try {
            JobScheduler scheduler = scheduler(context);
            if (scheduler == null) {
                reportSchedulerUnavailable(context);
                return false;
            }
            // Alternate IDs so scheduling the next link never cancels the job that is running.
            int nextJobId = previousJobId == SHORT_JOB_ID_A ? SHORT_JOB_ID_B : SHORT_JOB_ID_A;
            scheduler.cancel(nextJobId);
            long delayMillis = TimeUnit.MINUTES.toMillis(refreshMinutes);
            return submit(context, baseJob(context, nextJobId, reason)
                    .setMinimumLatency(delayMillis)
                    .setOverrideDeadline(delayMillis + DEADLINE_SLACK_MS)
                    .build());
        } catch (RuntimeException e) {
            return failed(context, e);
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
        Context app = appContext(context);
        if (app == null) {
            return false;
        }
        if (!SecureTokenStore.isSignedIn(app)) {
            WidgetRenderer.updateAll(app);
            return true;
        }
        try {
            DiagnosticLog.info(app, "scheduler", "immediate_refresh_requested");
            return submit(app, baseJob(app, IMMEDIATE_JOB_ID, REASON_IMMEDIATE)
                    .setMinimumLatency(0L)
                    .setOverrideDeadline(IMMEDIATE_DEADLINE_MS)
                    .build());
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
        try {
            JobScheduler scheduler = scheduler(app);
            if (scheduler == null) {
                return;
            }
            scheduler.cancel(PERIODIC_JOB_ID);
            scheduler.cancel(IMMEDIATE_JOB_ID);
            scheduler.cancel(RESET_JOB_ID);
            scheduler.cancel(SHORT_JOB_ID_A);
            scheduler.cancel(SHORT_JOB_ID_B);
            AppPreferences.setSchedulerError(app, "");
            DiagnosticLog.info(app, "scheduler", "all_refresh_jobs_cancelled");
        } catch (RuntimeException e) {
            failed(app, e);
        }
    }

    /** The scheduling reason stored in a refresh job's extras, or "" when absent. */
    static String reason(PersistableBundle extras) {
        return extras == null ? "" : extras.getString(EXTRA_REASON, "");
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
                "Android declined the background refresh request.");
        return false;
    }

    private static JobInfo.Builder baseJob(Context context, int jobId, String reason) {
        PersistableBundle extras = new PersistableBundle();
        extras.putString(EXTRA_REASON, reason);
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
                "Android's background scheduler is unavailable.");
    }

    private static Context appContext(Context context) {
        if (context == null) {
            return null;
        }
        Context applicationContext = context.getApplicationContext();
        return applicationContext != null ? applicationContext : context;
    }

    private static boolean failed(Context context, RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = exception.getClass().getSimpleName();
        }
        AppPreferences.setSchedulerError(context, "Background refresh: " + message);
        DiagnosticLog.error(context, "scheduler", "scheduler_failed", exception);
        return false;
    }
}
