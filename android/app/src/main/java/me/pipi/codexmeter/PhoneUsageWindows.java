package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageWindow;

/** Filters expired usage windows while preserving cached observation timestamps. */
final class PhoneUsageWindows {
    private PhoneUsageWindows() {}

    static UsageWindow currentWindow(UsageWindow window, long now) {
        return currentWindow(window, 0L, now);
    }

    static UsageWindow currentWindow(UsageWindow window, long observedAtMillis, long now) {
        if (window == null) return null;
        long resetAt = window.effectiveResetAtMillis(observedAtMillis);
        return resetAt > 0L && resetAt <= now ? null : window;
    }
}
