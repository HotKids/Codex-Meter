package dev.bennett.codexmeter;

import android.content.Context;

/** Capture cached phone data once so every size of a responsive widget shows the same refresh. */
final class QuotaCardState {
    final UsageSnapshot snapshot;
    final WidgetUsageSnapshot widgetSnapshot;
    final ResetCreditsSnapshot credits;
    final UsageHistory history;
    final boolean signedIn;
    final String error;
    final long now;

    private QuotaCardState(UsageSnapshot snapshot, WidgetUsageSnapshot widgetSnapshot, ResetCreditsSnapshot credits,
            UsageHistory history, boolean signedIn, String error, long now) {
        this.snapshot = snapshot;
        this.widgetSnapshot = widgetSnapshot;
        this.credits = credits;
        this.history = history;
        this.signedIn = signedIn;
        this.error = error;
        this.now = now;
    }

    static QuotaCardState load(Context context) {
        boolean signedIn = SecureTokenStore.isSignedIn(context);
        UsageSnapshot snapshot = signedIn ? AppPreferences.loadSnapshot(context) : null;
        WidgetUsageSnapshot widgetSnapshot = signedIn ? WidgetUsageStore.load(context) : null;
        boolean hasCompleteWindows = widgetSnapshot != null;
        if (widgetSnapshot == null) widgetSnapshot = WidgetUsageSnapshot.fromLegacy(snapshot);
        ResetCreditsSnapshot credits = signedIn ? AppPreferences.loadResetCredits(context) : null;
        String kind = snapshot != null && snapshot.longWindowIsMonthly()
                ? UsageHistory.MONTHLY : UsageHistory.WEEKLY;
        UsageHistory history = signedIn ? AppPreferences.loadUsageHistory(context, kind)
                : UsageHistory.empty(kind);
        String widgetError = signedIn ? WidgetRefreshScheduler.lastError(context) : "";
        return new QuotaCardState(snapshot, widgetSnapshot, credits, history, signedIn,
                !signedIn ? "" : widgetError.isEmpty() && !hasCompleteWindows
                        ? AppPreferences.getLastError(context) : widgetError,
                System.currentTimeMillis());
    }
}
