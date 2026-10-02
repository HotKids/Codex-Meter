package dev.bennett.codexmeter;

import android.content.Context;

/**
 * Appends usage samples to the per-window histories. UsageApi calls this while holding
 * NETWORK_LOCK, which serializes each history's read-modify-write.
 */
final class UsageHistoryRecorder {
    private UsageHistoryRecorder() {
    }

    static void record(Context context, UsageSnapshot snapshot) {
        if (context == null || snapshot == null) return;
        recordWindow(context, UsageHistory.FIVE_HOUR, snapshot.fiveHour,
                snapshot.fetchedAtMillis);
        recordWindow(context, UsageHistory.WEEKLY, snapshot.weekly,
                snapshot.fetchedAtMillis);
        recordWindow(context, UsageHistory.MONTHLY, snapshot.monthly,
                snapshot.fetchedAtMillis);
    }

    private static void recordWindow(Context context, String kind, UsageWindow window,
            long observedAtMillis) {
        if (window == null) return;
        UsageHistory current = AppPreferences.loadUsageHistory(context, kind);
        AppPreferences.saveUsageHistory(context, current.append(window, observedAtMillis));
    }
}
