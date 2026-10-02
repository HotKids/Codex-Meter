package dev.bennett.codexmeter;

/** Localized phone presentation of NowBarCopy's shared exhausted-window rules. */
final class PhoneNowBarCopy {
    private PhoneNowBarCopy() { }

    static String focusCriticalText(String prefix, UsageWindow window, long observed, long now) {
        if (window == null) return prefix + "—";
        String duration = resetDuration(window, observed, now);
        return prefix + (window.remainingPercent() == 0 && duration != null
                ? duration : window.remainingPercent() + "%");
    }

    static String limitText(String label, UsageWindow window, long observed, long now) {
        if (window == null) return label + ": " + AppText.get(R.string.phone_unavailable_2c9c1);
        String duration = resetDuration(window, observed, now);
        return label + ": " + (window.remainingPercent() == 0 && duration != null
                ? AppText.get(R.string.time_reset_relative, duration)
                : AppText.get(R.string.percent_remaining, window.remainingPercent()));
    }

    static String chipExpandedText(String label, UsageWindow window, long observed, long now) {
        return "Codex · " + label + " " + (window == null
                ? AppText.get(R.string.phone_unavailable_2c9c1)
                : focusCriticalText("", window, observed, now));
    }

    private static String resetDuration(UsageWindow window, long observed, long now) {
        long reset = window.effectiveResetAtMillis(observed);
        return reset > now ? UsageFormat.compactDuration(reset - now) : null;
    }
}
