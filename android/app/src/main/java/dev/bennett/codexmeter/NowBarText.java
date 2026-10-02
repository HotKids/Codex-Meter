package dev.bennett.codexmeter;

import java.util.concurrent.TimeUnit;

/**
 * Localizable phone twin of the shared {@link NowBarCopy}: the same Now Bar / Live Update rules
 * (a fully exhausted window swaps its percentage for the time until that window's natural
 * reset), with every word and number pattern supplied by {@link Strings}. The phone binds
 * {@link Strings} to string resources; ParserSelfTest binds the English resource patterns and
 * checks the output against {@link NowBarCopy}. Pure Java, so the self-tests compile it without
 * the Android SDK.
 */
final class NowBarText {
    /** Copy for one locale; the examples are the English renderings. */
    interface Strings {
        /** Name of the five-hour window, used when a chip has no window label: "5-hour". */
        String fiveHourLabel();

        /** "60%". */
        String percent(int percent);

        /** "2d 4h". */
        String daysHours(long days, long hours);

        /** "2d". */
        String days(long days);

        /** "4h 20m". */
        String hoursMinutes(long hours, long minutes);

        /** "4h". */
        String hours(long hours);

        /** "12m". */
        String minutes(long minutes);

        /** Placeholder for a missing focused window: "—". */
        String focusUnavailable();

        /** A focus value behind a window marker: "W 60%". */
        String focusMarked(String marker, String value);

        /** Expanded chip: "Codex · 5-hour 60%". */
        String chip(String windowLabel, String value);

        /** Expanded chip without a window: "Codex · 5-hour unavailable". */
        String chipUnavailable(String windowLabel);

        /** "5-hour: 60% left". */
        String limitLeft(String label, int percent);

        /** "Weekly: resets in 2d 4h". */
        String limitResetsIn(String label, String duration);

        /** "5-hour: unavailable". */
        String limitUnavailable(String label);
    }

    private NowBarText() {
    }

    /**
     * Compact critical / chip text for the focused window, as {@link NowBarCopy#focusCriticalText}.
     * {@code marker} names a non-five-hour window ("W", "M"); null or empty adds no marker.
     */
    static String focusCriticalText(Strings strings, String marker, UsageWindow window,
            long observedAtMillis, long nowMillis) {
        String value;
        if (window == null) {
            value = strings.focusUnavailable();
        } else {
            int remaining = window.remainingPercent();
            if (remaining > 0) {
                value = strings.percent(remaining);
            } else {
                String duration = resetDurationText(strings, window, observedAtMillis,
                        nowMillis);
                value = duration == null ? strings.percent(0) : duration;
            }
        }
        return marker == null || marker.isEmpty() ? value : strings.focusMarked(marker, value);
    }

    /** Samsung expanded-chip label, as {@link NowBarCopy#chipExpandedText}. */
    static String chipExpandedText(Strings strings, String windowLabel, UsageWindow window,
            long observedAtMillis, long nowMillis) {
        String focusLabel = windowLabel == null || windowLabel.isEmpty()
                ? strings.fiveHourLabel() : windowLabel;
        if (window == null) {
            return strings.chipUnavailable(focusLabel);
        }
        int remaining = window.remainingPercent();
        if (remaining > 0) {
            return strings.chip(focusLabel, strings.percent(remaining));
        }
        String duration = resetDurationText(strings, window, observedAtMillis, nowMillis);
        return strings.chip(focusLabel, duration == null ? strings.percent(0) : duration);
    }

    /** Notification body line for one window, as {@link NowBarCopy#limitText}. */
    static String limitText(Strings strings, String label, UsageWindow window,
            long observedAtMillis, long nowMillis) {
        if (window == null) {
            return strings.limitUnavailable(label);
        }
        int remaining = window.remainingPercent();
        if (remaining > 0) {
            return strings.limitLeft(label, remaining);
        }
        String duration = resetDurationText(strings, window, observedAtMillis, nowMillis);
        return duration == null
                ? strings.limitLeft(label, 0)
                : strings.limitResetsIn(label, duration);
    }

    /**
     * Time until {@code window}'s natural reset, or null when the reset is unknown or not in the
     * future, as {@link NowBarCopy#resetDurationText}.
     */
    static String resetDurationText(Strings strings, UsageWindow window, long observedAtMillis,
            long nowMillis) {
        if (window == null) {
            return null;
        }
        long resetAt = window.effectiveResetAtMillis(observedAtMillis);
        if (resetAt <= nowMillis) {
            return null;
        }
        return compactDuration(strings, resetAt - nowMillis);
    }

    /** Days and/or hours, or minutes under an hour, as {@link NowBarCopy#compactDuration}. */
    static String compactDuration(Strings strings, long durationMillis) {
        long minutes = Math.max(1L,
                TimeUnit.MILLISECONDS.toMinutes(Math.max(0L, durationMillis)));
        long days = minutes / TimeUnit.DAYS.toMinutes(1);
        long hours = (minutes % TimeUnit.DAYS.toMinutes(1)) / TimeUnit.HOURS.toMinutes(1);
        long remainingMinutes = minutes % TimeUnit.HOURS.toMinutes(1);
        if (days > 0L) {
            return hours > 0L ? strings.daysHours(days, hours) : strings.days(days);
        }
        if (hours > 0L) {
            return remainingMinutes > 0L
                    ? strings.hoursMinutes(hours, remainingMinutes)
                    : strings.hours(hours);
        }
        return strings.minutes(minutes);
    }
}
