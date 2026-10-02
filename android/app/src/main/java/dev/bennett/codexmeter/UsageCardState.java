package dev.bennett.codexmeter;

import android.content.Context;
import java.util.concurrent.TimeUnit;

/**
 * Everything a home-widget card shows, captured once per update so every responsive size of
 * one widget renders the same data.
 */
final class UsageCardState {
    /** AI-Usage flags cached usage as stale after six hours without a successful refresh. */
    static final long STALE_AFTER_MS = TimeUnit.HOURS.toMillis(6);

    final boolean signedIn;
    final UsageSnapshot snapshot;
    final ResetCreditsSnapshot credits;
    final String refreshError;
    final long nowMillis;

    UsageCardState(boolean signedIn, UsageSnapshot snapshot, ResetCreditsSnapshot credits,
            String refreshError, long nowMillis) {
        this.signedIn = signedIn;
        this.snapshot = signedIn ? snapshot : null;
        this.credits = signedIn ? credits : null;
        this.refreshError = refreshError == null ? "" : refreshError;
        this.nowMillis = nowMillis;
    }

    static UsageCardState load(Context context) {
        boolean signedIn = SecureTokenStore.isSignedIn(context);
        return new UsageCardState(signedIn,
                signedIn ? AppPreferences.loadSnapshot(context) : null,
                signedIn ? AppPreferences.loadResetCredits(context) : null,
                signedIn ? AppPreferences.getVisibleRefreshError(context) : "",
                System.currentTimeMillis());
    }

    /** Available reset credits worth showing, or 0. */
    int availableCredits() {
        return credits == null ? 0 : credits.availableCount;
    }

    long nextCreditExpiryMillis() {
        return credits == null ? 0L : credits.nextExpiryMillis(nowMillis);
    }

    long fetchedAtMillis() {
        return snapshot == null ? 0L : snapshot.fetchedAtMillis;
    }

    boolean isStale() {
        return snapshot != null && nowMillis - snapshot.fetchedAtMillis >= STALE_AFTER_MS;
    }

    String planType() {
        return snapshot == null ? "" : snapshot.planType;
    }
}
