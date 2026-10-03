package me.pipi.codexmeter;

import android.content.Context;

/** Outcome of redeeming a reset credit, with the message shown to the user. */
public final class ResetConsumeResult {
    public static final String ALREADY_REDEEMED = "already_redeemed";
    public static final String NOTHING_TO_RESET = "nothing_to_reset";
    public static final String NO_CREDIT = "no_credit";
    public static final String RESET = "reset";

    public final String outcome;
    public final String refreshWarning;
    public final int windowsReset;

    public ResetConsumeResult(String outcome, int windowsReset, String refreshWarning) {
        this.outcome = outcome == null ? "" : outcome;
        this.windowsReset = Math.max(0, windowsReset);
        this.refreshWarning = refreshWarning == null ? "" : refreshWarning;
    }

    public boolean applied() {
        return RESET.equals(outcome);
    }

    /** The localized message for {@link #outcome}, followed by any refresh warning. */
    public String userMessage(Context context) {
        if (RESET.equals(outcome)) {
            String message = windowsReset > 0
                    ? context.getResources().getQuantityString(
                            R.plurals.alerts_reset_result_windows, windowsReset, windowsReset)
                    : context.getString(R.string.alerts_reset_result_applied);
            return refreshWarning.isEmpty()
                    ? message
                    : context.getString(R.string.alerts_reset_result_with_warning,
                            message, refreshWarning);
        }
        if (NOTHING_TO_RESET.equals(outcome)) {
            return context.getString(R.string.alerts_reset_result_nothing);
        }
        if (NO_CREDIT.equals(outcome)) {
            return context.getString(R.string.alerts_reset_result_no_credit);
        }
        if (ALREADY_REDEEMED.equals(outcome)) {
            return context.getString(R.string.alerts_reset_result_already_redeemed);
        }
        return context.getString(R.string.alerts_reset_result_unknown);
    }
}
