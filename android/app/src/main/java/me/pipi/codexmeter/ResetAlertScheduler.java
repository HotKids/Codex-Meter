package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsageWindow;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Schedules an alarm at each usage window's reset for {@link ResetAlertReceiver}. */
public final class ResetAlertScheduler {
    static final String EXTRA_METRIC = "metric";
    static final String EXTRA_RESET_AT = "reset_at";
    /** Usage-window identifiers carried in {@link #EXTRA_METRIC}. */
    static final String WINDOW_FIVE_HOUR = "five_hour";
    static final String WINDOW_WEEKLY = "weekly";
    static final String WINDOW_MONTHLY = "monthly";

    private static final long DELIVERY_GRACE_MS = 3000;
    private static final int REQUEST_FIVE_HOUR = 74205;
    private static final int REQUEST_WEEKLY = 74207;
    private static final int REQUEST_MONTHLY = 74208;

    private ResetAlertScheduler() {
    }

    public static void scheduleFromSnapshot(Context context, UsageSnapshot snapshot) {
        Context app = appContext(context);
        if (app == null) {
            return;
        }
        cancelAll(app);
        if (snapshot == null || !SecureTokenStore.isSignedIn(app)
                || !ResetAlertPreferences.enabled(app)) {
            return;
        }
        String metric = ResetAlertPreferences.getMetric(app);
        if (!ResetAlertPreferences.METRIC_WEEKLY.equals(metric)) {
            scheduleWindow(app, snapshot.fiveHour, WINDOW_FIVE_HOUR, REQUEST_FIVE_HOUR);
        }
        if (!ResetAlertPreferences.METRIC_FIVE_HOUR.equals(metric)) {
            // The monthly free-tier window rides on the same long-cadence metric as weekly.
            scheduleWindow(app, snapshot.weekly, WINDOW_WEEKLY, REQUEST_WEEKLY);
            scheduleWindow(app, snapshot.monthly, WINDOW_MONTHLY, REQUEST_MONTHLY);
        }
    }

    public static void cancelAll(Context context) {
        Context app = appContext(context);
        if (app == null) {
            return;
        }
        AlarmManager alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        alarmManager.cancel(pending(app, WINDOW_FIVE_HOUR, 0L, REQUEST_FIVE_HOUR));
        alarmManager.cancel(pending(app, WINDOW_WEEKLY, 0L, REQUEST_WEEKLY));
        alarmManager.cancel(pending(app, WINDOW_MONTHLY, 0L, REQUEST_MONTHLY));
    }

    public static boolean canScheduleExact(Context context) {
        if (Build.VERSION.SDK_INT < 31) {
            return true;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    /**
     * Sets an RTC wake-up alarm that may fire while idle: exact when the app may schedule exact
     * alarms, otherwise inexact.
     */
    static void setWakeUpAlarm(AlarmManager alarmManager, long triggerAtMillis,
            PendingIntent operation) {
        try {
            if (Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis,
                        operation);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis,
                        operation);
            }
        } catch (SecurityException e) {
            // Exact-alarm access can be revoked between the check and the call.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
        }
    }

    private static void scheduleWindow(Context context, UsageWindow window, String windowId,
            int requestCode) {
        if (window == null || window.resetAtMillis() <= System.currentTimeMillis()) {
            return;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        long triggerAtMillis = window.resetAtMillis() + DELIVERY_GRACE_MS;
        setWakeUpAlarm(alarmManager, triggerAtMillis,
                pending(context, windowId, window.resetAtMillis(), requestCode));
    }

    private static PendingIntent pending(Context context, String windowId, long resetAtMillis,
            int requestCode) {
        Intent intent = new Intent(context, ResetAlertReceiver.class)
                .setAction(AppConstants.ACTION_RESET_ALERT)
                .putExtra(EXTRA_METRIC, windowId)
                .putExtra(EXTRA_RESET_AT, resetAtMillis);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Context appContext(Context context) {
        if (context == null) {
            return null;
        }
        Context applicationContext = context.getApplicationContext();
        return applicationContext != null ? applicationContext : context;
    }
}
