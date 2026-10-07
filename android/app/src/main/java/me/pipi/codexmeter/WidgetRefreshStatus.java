package me.pipi.codexmeter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.provider.Settings;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Transient, noncredential feedback for the latest accepted manual usage refresh. */
final class WidgetRefreshStatus {
    static final String PREFS = "codex_meter_widget_refresh_status_v1";
    static final String ACTION_RECONCILE = AppConstants.action("RECONCILE_WIDGET_REFRESH");
    private static final String KEY_GENERATION = "generation";
    private static final String KEY_JOB_ID = "job_id";
    private static final String KEY_RUNNING = "running";
    private static final String KEY_OWNER = "owner";
    private static final String KEY_QUEUED_UNTIL = "queued_until";
    private static final String KEY_BOOT_COUNT = "boot_count";
    private static final String PROCESS = UUID.randomUUID().toString();
    private static final long CHECK_DELAY_MS = 30_000L;
    // Pending feedback uses the scheduler's five-minute slack; its job may still run later.
    private static final long QUEUED_LIMIT_MS = TimeUnit.MINUTES.toMillis(5);
    private static final int CHECK_REQUEST = 75991;
    private static String runningGeneration = "";

    private WidgetRefreshStatus() {}

    static synchronized boolean isRefreshing(Context context) {
        SharedPreferences preferences = prefs(context);
        String generation = preferences.getString(KEY_GENERATION, "");
        if (generation.isEmpty()) return false;
        if (preferences.getBoolean(KEY_RUNNING, false)) {
            return generation.equals(runningGeneration)
                    && PROCESS.equals(preferences.getString(KEY_OWNER, ""));
        }
        long now = SystemClock.elapsedRealtime();
        long queuedUntil = preferences.getLong(KEY_QUEUED_UNTIL, 0L);
        int bootCount = preferences.getInt(KEY_BOOT_COUNT, -1);
        if (now < 0L || queuedUntil <= now || queuedUntil - now > QUEUED_LIMIT_MS
                || bootCount < 0 || bootCount != Settings.Global.getInt(
                        context.getContentResolver(), Settings.Global.BOOT_COUNT, -1)) return false;
        try {
            JobScheduler scheduler = context.getSystemService(JobScheduler.class);
            JobInfo job = scheduler == null ? null
                    : scheduler.getPendingJob(preferences.getInt(KEY_JOB_ID, -1));
            return job != null && generation.equals(RefreshScheduler.generation(job.getExtras()));
        } catch (RuntimeException exception) {
            DiagnosticLog.error(context, "widget", "refresh_status_lookup_failed", exception);
            return false;
        }
    }

    static synchronized void queued(Context context, JobInfo job) {
        runningGeneration = "";
        prefs(context).edit().clear()
                .putString(KEY_GENERATION, RefreshScheduler.generation(job.getExtras()))
                .putInt(KEY_JOB_ID, job.getId())
                .putLong(KEY_QUEUED_UNTIL, SystemClock.elapsedRealtime() + QUEUED_LIMIT_MS)
                .putInt(KEY_BOOT_COUNT, Settings.Global.getInt(
                        context.getContentResolver(), Settings.Global.BOOT_COUNT, -1)).commit();
        scheduleCheck(context);
    }

    static synchronized void started(Context context, PersistableBundle extras) {
        if (!matches(context, extras)) return;
        runningGeneration = RefreshScheduler.generation(extras);
        prefs(context).edit().putBoolean(KEY_RUNNING, true).putString(KEY_OWNER, PROCESS).commit();
        scheduleCheck(context);
    }

    static synchronized boolean finished(Context context, PersistableBundle extras) {
        return matches(context, extras) && clear(context);
    }

    /** Also used by boot/package replacement; immediate jobs and their UI do not survive reboot. */
    static synchronized boolean clear(Context context) {
        boolean changed = !prefs(context).getString(KEY_GENERATION, "").isEmpty();
        runningGeneration = "";
        prefs(context).edit().clear().commit();
        try {
            AlarmManager manager = context.getSystemService(AlarmManager.class);
            PendingIntent pending = checkIntent(context, PendingIntent.FLAG_NO_CREATE);
            if (pending != null) {
                if (manager != null) manager.cancel(pending);
                pending.cancel();
            }
        } catch (RuntimeException exception) {
            DiagnosticLog.error(context, "widget", "refresh_check_cancel_failed", exception);
        }
        return changed;
    }

    static synchronized boolean reconcile(Context context) {
        if (prefs(context).getString(KEY_GENERATION, "").isEmpty()) return false;
        if (!isRefreshing(context)) return clear(context);
        scheduleCheck(context);
        return false;
    }

    private static boolean matches(Context context, PersistableBundle extras) {
        String generation = RefreshScheduler.generation(extras);
        return !generation.isEmpty()
                && generation.equals(prefs(context).getString(KEY_GENERATION, ""));
    }

    private static void scheduleCheck(Context context) {
        try {
            AlarmManager manager = context.getSystemService(AlarmManager.class);
            if (manager != null) {
                // The launcher owns the animation: a process-local timer cannot repair it after death.
                manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME,
                        SystemClock.elapsedRealtime() + CHECK_DELAY_MS,
                        checkIntent(context, PendingIntent.FLAG_UPDATE_CURRENT));
            }
        } catch (RuntimeException exception) {
            DiagnosticLog.error(context, "widget", "refresh_check_schedule_failed", exception);
        }
    }

    private static PendingIntent checkIntent(Context context, int flags) {
        return PendingIntent.getBroadcast(context, CHECK_REQUEST,
                new Intent(context, WidgetRefreshReceiver.class).setAction(ACTION_RECONCILE),
                flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
