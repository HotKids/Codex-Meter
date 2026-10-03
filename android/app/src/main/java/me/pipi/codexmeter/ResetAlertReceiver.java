package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsageWindow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.util.concurrent.TimeUnit;

/** Shows the reset notification scheduled by {@link ResetAlertScheduler} and refreshes usage. */
public final class ResetAlertReceiver extends BroadcastReceiver {
    /** How far the cached reset time may drift from the alarm's before the alarm is stale. */
    private static final long RESET_MATCH_TOLERANCE_MS = TimeUnit.MINUTES.toMillis(1);

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null
                || !AppConstants.ACTION_RESET_ALERT.equals(intent.getAction())
                || !SecureTokenStore.isSignedIn(context)
                || !ResetAlertPreferences.enabled(context)) {
            return;
        }
        String window = intent.getStringExtra(ResetAlertScheduler.EXTRA_METRIC);
        if (!ResetAlertScheduler.WINDOW_WEEKLY.equals(window)
                && !ResetAlertScheduler.WINDOW_MONTHLY.equals(window)) {
            window = ResetAlertScheduler.WINDOW_FIVE_HOUR;
        }
        long resetAtMillis = intent.getLongExtra(ResetAlertScheduler.EXTRA_RESET_AT, 0L);
        if (!stillRelevant(context, window, resetAtMillis)) {
            return;
        }
        ResetNotificationManager.showResetNotification(context, window);
        RefreshScheduler.scheduleImmediate(context);
        WidgetRenderer.updateAll(context);
    }

    /** False when newer usage data moved the window's reset away from the scheduled one. */
    private static boolean stillRelevant(Context context, String window, long resetAtMillis) {
        if (resetAtMillis <= 0) {
            return true;
        }
        UsageSnapshot snapshot = AppPreferences.loadSnapshot(context);
        if (snapshot == null) {
            return true;
        }
        UsageWindow usageWindow;
        if (ResetAlertScheduler.WINDOW_WEEKLY.equals(window)) {
            usageWindow = snapshot.weekly;
        } else if (ResetAlertScheduler.WINDOW_MONTHLY.equals(window)) {
            usageWindow = snapshot.monthly;
        } else {
            usageWindow = snapshot.fiveHour;
        }
        return usageWindow == null || usageWindow.resetAtMillis() <= 0
                || Math.abs(usageWindow.resetAtMillis() - resetAtMillis)
                        < RESET_MATCH_TOLERANCE_MS;
    }
}
