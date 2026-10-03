package me.pipi.codexmeter;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import java.util.concurrent.TimeUnit;

/** Schedules low-frequency GitHub checks under Android's battery and network policy. */
public final class ReleaseUpdateScheduler {
    private static final int PERIODIC_JOB_ID = 73400;
    private static final int INITIAL_JOB_ID = 73401;
    private static final long INITIAL_CHECK_DEADLINE_MILLIS = TimeUnit.MINUTES.toMillis(5);

    private ReleaseUpdateScheduler() {
    }

    /**
     * Keeps the periodic check in line with the current preferences and, before the first
     * successful check, queues a one-off check soon. Returns false when scheduling failed.
     */
    public static boolean ensureScheduled(Context context) {
        Context app = application(context);
        if (!UpdatePreferences.automaticChecks(app)) {
            cancel(app);
            return true;
        }
        try {
            JobScheduler scheduler = jobScheduler(app);
            if (scheduler == null) {
                return false;
            }
            int hours = UpdatePreferences.checkIntervalHours(app);
            long period = UpdateCheckFrequency.periodMillis(hours);
            long flex = UpdateCheckFrequency.flexMillis(hours);
            boolean scheduled = true;
            JobInfo existing = scheduler.getPendingJob(PERIODIC_JOB_ID);
            if (!matchesPeriodicJob(existing, period, flex)) {
                JobInfo periodic = base(app, PERIODIC_JOB_ID)
                        .setPeriodic(period, flex)
                        .setPersisted(true)
                        .build();
                scheduled = scheduler.schedule(periodic) == JobScheduler.RESULT_SUCCESS;
            }
            if (UpdatePreferences.lastCheckMillis(app) == 0L
                    && scheduler.getPendingJob(INITIAL_JOB_ID) == null) {
                JobInfo initial = base(app, INITIAL_JOB_ID)
                        .setMinimumLatency(0L)
                        .setOverrideDeadline(INITIAL_CHECK_DEADLINE_MILLIS)
                        .build();
                // Always submit the initial job, even when the periodic one failed.
                boolean initialScheduled =
                        scheduler.schedule(initial) == JobScheduler.RESULT_SUCCESS;
                scheduled = initialScheduled && scheduled;
            }
            return scheduled;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static void cancel(Context context) {
        Context app = application(context);
        try {
            JobScheduler scheduler = jobScheduler(app);
            if (scheduler != null) {
                scheduler.cancel(PERIODIC_JOB_ID);
                scheduler.cancel(INITIAL_JOB_ID);
            }
        } catch (RuntimeException ignored) {
            // Nothing to cancel when the scheduler is unavailable.
        }
    }

    private static boolean matchesPeriodicJob(JobInfo job, long period, long flex) {
        return job != null
                && job.getIntervalMillis() == period
                && job.getFlexMillis() == flex
                && job.getNetworkType() == JobInfo.NETWORK_TYPE_ANY
                && job.isPersisted();
    }

    private static JobInfo.Builder base(Context context, int jobId) {
        return new JobInfo.Builder(jobId,
                new ComponentName(context, ReleaseUpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
    }

    private static JobScheduler jobScheduler(Context context) {
        return (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }

    private static Context application(Context context) {
        Context app = context.getApplicationContext();
        return app == null ? context : app;
    }
}
