package dev.bennett.codexmeter;

import android.content.Context;
import android.text.format.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** English phone/widget presentation of plans, percentages, reset times, and ages. */
public final class UsageFormat {
    private static final long MINUTES_PER_HOUR = TimeUnit.HOURS.toMinutes(1);
    private static final long MINUTES_PER_DAY = TimeUnit.DAYS.toMinutes(1);
    private static final long HOURS_PER_DAY = TimeUnit.DAYS.toHours(1);

    private UsageFormat() {
    }

    /**
     * Display name for a ChatGPT plan type. OpenAI's top Pro tier reports {@code pro} (older
     * caches say {@code pro20x}); it is labelled Pro 10x. Unknown plans return an empty string.
     */
    public static String planLabel(String plan) {
        if (plan == null || plan.trim().isEmpty()) {
            return "";
        }
        String normalized = plan.trim().toLowerCase(Locale.ROOT)
                .replace("_", "").replace("-", "").replace(" ", "").replace("\u00d7", "x");
        switch (normalized) {
            case "free":
                return "Free";
            case "go":
                return "Go";
            case "plus":
                return "Plus";
            case "prolite":
            case "pro5x":
                return "Pro 5x";
            case "pro":
            case "pro10x":
            case "pro20x":
                return "Pro 10x";
            case "team":
                return "Team";
            case "business":
                return "Business";
            case "enterprise":
                return "Enterprise";
            case "premium":
                return "Premium";
            default:
                return "";
        }
    }

    public static String percent(UsageWindow usageWindow, String mode, boolean compact) {
        if (usageWindow == null) {
            return compact ? "—" : "Unavailable";
        }
        boolean showUsed = WidgetOptions.DISPLAY_USED.equals(mode);
        int value = showUsed ? usageWindow.usedPercent : usageWindow.remainingPercent();
        if (compact) {
            return value + "%";
        }
        return value + "% " + (showUsed ? "used" : "left");
    }

    public static String reset(Context context, UsageWindow usageWindow, String mode, long now) {
        return reset(context, usageWindow, mode, now, now);
    }

    public static String reset(Context context, UsageWindow usageWindow, String mode,
            long observedAtMillis, long nowMillis) {
        if (usageWindow == null || WidgetOptions.RESET_HIDDEN.equals(mode)
                || !usageWindow.showsResetCountdown()) {
            return "";
        }
        long resetAtMillis = usageWindow.effectiveResetAtMillis(observedAtMillis);
        if (resetAtMillis <= 0) {
            return "Reset time unavailable";
        }
        String absolute = absolute(context, resetAtMillis, nowMillis);
        String relative = relative(resetAtMillis, nowMillis);
        if (WidgetOptions.RESET_RELATIVE.equals(mode)) {
            return "Resets " + relative;
        }
        if (WidgetOptions.RESET_BOTH.equals(mode)) {
            return "Resets " + absolute + " (" + relative + ")";
        }
        return "Resets " + absolute;
    }

    public static String estimatedRemaining(UsagePace.Assessment assessment) {
        if (assessment == null || !assessment.available) {
            return "";
        }
        if (assessment.estimatedRemainingMillis <= 0L) {
            return "Est. depleted";
        }
        return "Est. " + compactDuration(assessment.estimatedRemainingMillis);
    }

    /** Formats a duration as "Xd Yh", "Xh Ym", or "Xm"; never below one minute. */
    static String compactDuration(long millis) {
        long minutes = Math.max(1L, TimeUnit.MILLISECONDS.toMinutes(Math.max(0L, millis)));
        long days = minutes / MINUTES_PER_DAY;
        long hours = minutes % MINUTES_PER_DAY / MINUTES_PER_HOUR;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes % MINUTES_PER_HOUR + "m";
        }
        return minutes + "m";
    }

    /** "today at …", "tomorrow at …", or a weekday-date-time, in the device's clock style. */
    public static String absolute(Context context, long millis, long nowMillis) {
        boolean use24Hour = DateFormat.is24HourFormat(context);
        Calendar target = calendarAt(millis);
        Calendar today = calendarAt(nowMillis);
        Calendar tomorrow = (Calendar) today.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        String pattern;
        if (sameDay(target, today)) {
            pattern = use24Hour ? "'today at' HH:mm" : "'today at' h:mm a";
        } else if (sameDay(target, tomorrow)) {
            pattern = use24Hour ? "'tomorrow at' HH:mm" : "'tomorrow at' h:mm a";
        } else {
            pattern = use24Hour ? "EEE, MMM d 'at' HH:mm" : "EEE, MMM d 'at' h:mm a";
        }
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(millis));
    }

    private static Calendar calendarAt(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        return calendar;
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.ERA) == b.get(Calendar.ERA)
                && a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    /** "in Xd Yh", "in Xh Ym", "in Xm", or "now" once less than a minute remains. */
    public static String relative(long targetMillis, long nowMillis) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(Math.max(0L, targetMillis - nowMillis));
        return minutes > 0 ? "in " + compactDuration(targetMillis - nowMillis) : "now";
    }

    public static String updated(long observedMillis, long nowMillis) {
        if (observedMillis <= 0) {
            return "Not updated yet";
        }
        long minutes = Math.max(0L, TimeUnit.MILLISECONDS.toMinutes(nowMillis - observedMillis));
        if (minutes < 1) {
            return "Updated just now";
        }
        if (minutes < MINUTES_PER_HOUR) {
            return "Updated " + minutes + "m ago";
        }
        long hours = minutes / MINUTES_PER_HOUR;
        if (hours < HOURS_PER_DAY) {
            return "Updated " + hours + "h ago";
        }
        return "Updated " + hours / HOURS_PER_DAY + "d ago";
    }
}
