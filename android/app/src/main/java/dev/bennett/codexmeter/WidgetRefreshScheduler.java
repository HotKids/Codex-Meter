package dev.bennett.codexmeter;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import java.util.concurrent.TimeUnit;

/** Home-widget refresh jobs, independent of the app's adaptive and reset schedules. */
public final class WidgetRefreshScheduler {
    static final int AUTOMATIC_JOB_A = 73600;
    static final int AUTOMATIC_JOB_B = 73601;
    static final int MANUAL_JOB = 73602;
    static final String EXTRA_FORCE = "force";
    static final String EXTRA_INITIAL = "initial_widget_usage";
    private static final String EXTRA_MINUTES = "widget_refresh_minutes";
    private static final String PREFS = "codex_meter_home_widget_refresh_v1";
    private static final String FAILURES = "failures";
    private static final String RETRY_AT = "retry_at";
    private static final String FAILED_AT = "failed_at";
    private static final String ERROR = "error";

    private WidgetRefreshScheduler() { }

    public static synchronized void schedule(Context context) {
        try {
            scheduleNext(context, -1, 0L);
        } catch (RuntimeException error) {
            failed(context, error);
        }
    }

    /** Explicit taps bypass freshness, while respecting the widget's active retry delay. */
    public static synchronized boolean scheduleImmediate(Context context) {
        try {
            JobScheduler scheduler = scheduler(context);
            if (scheduler == null || !hasWidgets(context)) return false;
            if (retryAt(context) > System.currentTimeMillis()) {
                return false;
            }
            // Coalesce repeated taps while the same explicit request is pending/running.
            JobInfo pending = scheduler.getPendingJob(MANUAL_JOB);
            if (pending != null && !pending.getExtras().getBoolean(EXTRA_INITIAL)) return true;
            return submit(context, scheduler, job(context, MANUAL_JOB, true, false, 0, 0L));
        } catch (RuntimeException error) {
            failed(context, error);
            return false;
        }
    }

    /** Fetch initial complete data even while an editor is configuring an unpinned widget. */
    public static synchronized boolean requestInitial(Context context) {
        try {
            if (!SecureTokenStore.isSignedIn(context) || WidgetUsageStore.load(context) != null) return false;
            JobScheduler scheduler = scheduler(context);
            if (scheduler == null || retryAt(context) > System.currentTimeMillis()) return false;
            if (scheduler.getPendingJob(MANUAL_JOB) != null) return true;
            return submit(context, scheduler, job(context, MANUAL_JOB, false, true, 0, 0L));
        } catch (RuntimeException error) {
            failed(context, error);
            return false;
        }
    }

    static synchronized void finished(Context context, int previousJobId,
            boolean missingCredentials) {
        long delay = missingCredentials
                ? TimeUnit.MINUTES.toMillis(ReferenceWidgetPreferences.refreshMinutes(context))
                : 0L;
        try {
            scheduleNext(context, previousJobId, delay);
        } catch (RuntimeException error) {
            failed(context, error);
        }
    }

    private static void scheduleNext(Context context, int previousJobId, long minimumDelay) {
        JobScheduler scheduler = scheduler(context);
        if (scheduler == null) return;
        int minutes = ReferenceWidgetPreferences.refreshMinutes(context);
        if (!hasWidgets(context)) {
            cancelAutomatic(scheduler);
            JobInfo manual = scheduler.getPendingJob(MANUAL_JOB);
            // The first editor can exist before its widget is pinned. Ordinary widget update
            // cleanup must not cancel the explicit data request that editor is waiting for.
            if (manual == null || !manual.getExtras().getBoolean(EXTRA_INITIAL)) scheduler.cancel(MANUAL_JOB);
            return;
        }
        // A fresh legacy App cache does not prove that it contains all API windows. Bootstrap
        // before checking interval/pending jobs, including widgets configured for manual refresh.
        requestInitial(context);
        if (minutes <= 0) {
            cancelAutomatic(scheduler);
            return;
        }
        boolean completedAutomatic = previousJobId == AUTOMATIC_JOB_A
                || previousJobId == AUTOMATIC_JOB_B;
        if (!completedAutomatic) {
            for (int id : new int[]{AUTOMATIC_JOB_A, AUTOMATIC_JOB_B}) {
                JobInfo pending = scheduler.getPendingJob(id);
                if (pending != null && pending.getExtras().getInt(EXTRA_MINUTES) == minutes) {
                    return;
                }
            }
            cancelAutomatic(scheduler);
        }
        long now = System.currentTimeMillis();
        long due = nextAutomaticAt(fetchedAt(context), retryAt(context),
                minutes, now);
        long delay = Math.max(minimumDelay, Math.max(0L, due - now));
        // A different ID avoids finishing the running job removing its successor.
        int id = previousJobId == AUTOMATIC_JOB_A ? AUTOMATIC_JOB_B : AUTOMATIC_JOB_A;
        scheduler.cancel(id);
        submit(context, scheduler, job(context, id, false, false, minutes, delay));
    }

    private static JobInfo job(Context context, int id, boolean force, boolean initial, int minutes, long delay) {
        PersistableBundle extras = new PersistableBundle();
        extras.putBoolean(EXTRA_FORCE, force);
        extras.putBoolean(EXTRA_INITIAL, initial);
        extras.putInt(EXTRA_MINUTES, minutes);
        // Android periodic jobs have a 15-minute minimum. One-shot jobs can request five
        // minutes, but Doze, scheduler quotas, and network constraints may defer execution.
        return new JobInfo.Builder(id, new ComponentName(context, WidgetRefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(delay)
                .setPersisted(!force && !initial)
                .setExtras(extras)
                .build();
    }

    static boolean shouldFetch(Context context, boolean force, long now) {
        long retryAt = retryAt(context);
        if (retryAt > now) return false;
        if (force) return true;
        int minutes = ReferenceWidgetPreferences.refreshMinutes(context);
        if (minutes <= 0 || !hasWidgets(context)) return false;
        return nextAutomaticAt(fetchedAt(context),
                retryAt, minutes, now) <= now;
    }

    static boolean shouldFetchInitial(Context context, long now) {
        return SecureTokenStore.isSignedIn(context) && WidgetUsageStore.load(context) == null
                && retryAt(context) <= now;
    }

    static long nextAutomaticAt(long fetchedAt, long retryAt, int minutes, long now) {
        if (minutes <= 0) return Long.MAX_VALUE;
        long cacheDue = fetchedAt <= 0L ? now
                : Math.min(fetchedAt, now) + TimeUnit.MINUTES.toMillis(minutes);
        return Math.max(now, Math.max(cacheDue, retryAt));
    }

    static synchronized void recordSuccess(Context context) {
        recordSuccess(context, WidgetUsageStore.key(context));
    }

    static synchronized void recordSuccess(Context context, String requestedAccount) {
        if (!requestedAccount.equals(WidgetUsageStore.key(context))) return;
        state(context, requestedAccount).edit().remove(FAILURES).remove(RETRY_AT)
                .remove(FAILED_AT).remove(ERROR).apply();
    }

    static synchronized void recordFailure(Context context, long now, String error) {
        recordFailure(context, now, error, WidgetUsageStore.key(context));
    }

    static synchronized void recordFailure(Context context, long now, String error, String requestedAccount) {
        if (!requestedAccount.equals(WidgetUsageStore.key(context))) return;
        SharedPreferences preferences = state(context, requestedAccount);
        int previous = hasCurrentFailure(preferences, fetchedAt(context))
                ? preferences.getInt(FAILURES, 0) : 0;
        int count = Math.min(4, previous + 1);
        String message = DiagnosticSanitizer.redact(error == null ? "" : error).trim();
        if (message.isEmpty()) message = AppText.get(R.string.phone_usage_refresh_failed_a0acd);
        if (message.length() > 240) message = message.substring(0, 240);
        preferences.edit().putInt(FAILURES, count)
                .putLong(RETRY_AT, now + failureDelayMillis(count))
                .putLong(FAILED_AT, now).putString(ERROR, message).apply();
    }

    /** Signed-in errors become obsolete only after a complete widget refresh for this account. */
    public static String lastError(Context context) {
        return hasCurrentFailure(state(context), fetchedAt(context)) ? state(context).getString(ERROR, "") : "";
    }

    private static long retryAt(Context context) {
        SharedPreferences preferences = state(context);
        return hasCurrentFailure(preferences, fetchedAt(context)) ? preferences.getLong(RETRY_AT, 0L) : 0L;
    }

    private static boolean hasCurrentFailure(SharedPreferences preferences, long fetchedAt) {
        return preferences.contains(FAILED_AT) && preferences.getLong(FAILED_AT, 0L) > fetchedAt;
    }

    private static long fetchedAt(Context context) {
        WidgetUsageSnapshot complete = WidgetUsageStore.load(context);
        if (complete != null) return complete.fetchedAtMillis;
        if (SecureTokenStore.isSignedIn(context)) return 0L;
        // Signed-out legacy state is useful only for migration diagnostics; jobs never perform
        // a credential-free fetch. Signed-in freshness and retries use complete widget data.
        UsageSnapshot legacy = AppPreferences.loadSnapshot(context);
        return legacy == null ? 0L : legacy.fetchedAtMillis;
    }

    static long failureDelayMillis(int count) {
        return TimeUnit.MINUTES.toMillis(count <= 1 ? 5 : count == 2 ? 15 : count == 3 ? 30 : 60);
    }

    static boolean hasWidgets(Context context) {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(
                new ComponentName(context, CodexUsageWidget.class)).length > 0;
    }

    static void updateHomeWidgets(Context context) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            for (int id : manager.getAppWidgetIds(new ComponentName(context, CodexUsageWidget.class))) {
                WidgetRenderer.update(context, manager, id);
            }
        } catch (RuntimeException error) {
            failed(context, error);
        }
    }

    static SharedPreferences state(Context context) {
        return state(context, WidgetUsageStore.key(context));
    }

    private static SharedPreferences state(Context context, String account) {
        return context.getSharedPreferences(account.isEmpty() ? PREFS : PREFS + "_" + account,
                Context.MODE_PRIVATE);
    }

    private static void cancelAutomatic(JobScheduler scheduler) {
        scheduler.cancel(AUTOMATIC_JOB_A);
        scheduler.cancel(AUTOMATIC_JOB_B);
    }

    private static JobScheduler scheduler(Context context) {
        return (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }

    private static boolean submit(Context context, JobScheduler scheduler, JobInfo job) {
        boolean accepted = scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS;
        DiagnosticLog.info(context, "widget", "home_refresh_scheduled",
                "job_id", job.getId(), "accepted", accepted,
                "minutes", job.getExtras().getInt(EXTRA_MINUTES));
        return accepted;
    }

    private static void failed(Context context, RuntimeException error) {
        DiagnosticLog.error(context, "widget", "home_refresh_schedule_failed", error);
    }
}
