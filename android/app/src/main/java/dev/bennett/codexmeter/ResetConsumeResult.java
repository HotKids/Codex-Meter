package dev.bennett.codexmeter;

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

    public String userMessage() {
        if (RESET.equals(outcome)) {
            String message = windowsReset > 0
                    ? "Reset applied to " + windowsReset + " usage window"
                            + (windowsReset == 1 ? "." : "s.")
                    : "Codex usage reset applied.";
            return refreshWarning.isEmpty() ? message : message + " " + refreshWarning;
        }
        if (NOTHING_TO_RESET.equals(outcome)) {
            return "There is no used Codex allowance to reset right now.";
        }
        if (NO_CREDIT.equals(outcome)) {
            return "No reset credit is currently available.";
        }
        if (ALREADY_REDEEMED.equals(outcome)) {
            return "That reset request was already redeemed.";
        }
        return "OpenAI returned an unrecognized reset result.";
    }
}
