package dev.bennett.codexmeter;

import android.content.Context;
import android.text.format.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Localized phone presentation. Shared account data and Wear formatting stay unchanged. */
public final class UsageFormat {
    private UsageFormat() { }

    public static String planLabel(String value) {
        if (value == null) return "";
        switch (value.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "").replace("×", "x")) {
            case "free": return "Free";
            case "go": return "Go";
            case "plus": return "Plus";
            case "prolite":
            case "pro5x": return "Pro 5x";
            case "pro":
            case "pro20x": // Compatibility with cached / legacy API plan identifiers.
            case "pro10x": return "Pro 10x";
            case "team": return "Team";
            case "business": return "Business";
            case "enterprise": return "Enterprise";
            case "premium": return "Premium";
            default: return "";
        }
    }

    public static String percent(UsageWindow window, String mode, boolean compact) {
        if (window == null) return compact ? "—" : AppText.get(R.string.phone_unavailable_2c9c1);
        int value = WidgetOptions.DISPLAY_USED.equals(mode) ? window.usedPercent : window.remainingPercent();
        if (compact) return value + "%";
        return AppText.get(WidgetOptions.DISPLAY_USED.equals(mode)
                ? R.string.percent_used : R.string.percent_remaining, value);
    }

    public static String reset(Context context, UsageWindow window, String mode, long now) {
        return reset(context, window, mode, now, now);
    }

    public static String reset(Context context, UsageWindow window, String mode,
            long observedAtMillis, long nowMillis) {
        if (window == null || WidgetOptions.RESET_HIDDEN.equals(mode) || !window.showsResetCountdown()) return "";
        long reset = window.effectiveResetAtMillis(observedAtMillis);
        if (reset <= 0) return context.getString(R.string.card_missing_window);
        if (reset <= nowMillis) return context.getString(R.string.card_waiting_update);
        String absolute = absolute(context, reset, nowMillis);
        String duration = compactDuration(reset - nowMillis);
        if (WidgetOptions.RESET_RELATIVE.equals(mode)) return context.getString(R.string.time_reset_relative, duration);
        if (WidgetOptions.RESET_BOTH.equals(mode)) return context.getString(R.string.time_reset_both,
                absolute, context.getString(R.string.time_relative, duration));
        return context.getString(R.string.time_reset_absolute, absolute);
    }

    public static String estimatedRemaining(UsagePace.Assessment assessment) {
        if (assessment == null || !assessment.available) return "";
        if (assessment.estimatedRemainingMillis <= 0) return AppText.get(R.string.time_depleted);
        return AppText.get(R.string.time_estimated, compactDuration(assessment.estimatedRemainingMillis));
    }

    static String compactDuration(long millis) {
        long minutes = Math.max(1, TimeUnit.MILLISECONDS.toMinutes(Math.max(0, millis)));
        long days = minutes / 1440;
        long hours = minutes % 1440 / 60;
        if (days > 0) return AppText.get(R.string.time_days_hours, days, hours);
        if (hours > 0) return AppText.get(R.string.time_hours_minutes, hours, minutes % 60);
        return AppText.get(R.string.time_minutes, minutes);
    }

    public static String absolute(Context context, long millis, long now) {
        Calendar target = Calendar.getInstance(); target.setTimeInMillis(millis);
        Calendar today = Calendar.getInstance(); today.setTimeInMillis(now);
        Calendar tomorrow = (Calendar) today.clone(); tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        String time = DateFormat.getTimeFormat(context).format(new Date(millis));
        if (sameDay(target, today)) return context.getString(R.string.time_today, time);
        if (sameDay(target, tomorrow)) return context.getString(R.string.time_tomorrow, time);
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        String skeleton = DateFormat.is24HourFormat(context) ? "MMMEdHm" : "MMMEdhm";
        return new SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(new Date(millis));
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.ERA) == b.get(Calendar.ERA) && a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    public static String relative(long target, long now) {
        return target <= now ? AppText.get(R.string.time_now)
                : AppText.get(R.string.time_relative, compactDuration(target - now));
    }

    public static String updated(long observed, long now) {
        if (observed <= 0) return AppText.get(R.string.card_waiting_update);
        long minutes = Math.max(0, TimeUnit.MILLISECONDS.toMinutes(now - observed));
        if (minutes < 1) return AppText.get(R.string.widget_sample_updated);
        if (minutes < 60) return AppText.get(R.string.time_updated_minutes, minutes);
        if (minutes < 1440) return AppText.get(R.string.time_updated_hours, minutes / 60);
        return AppText.get(R.string.time_updated_days, minutes / 1440);
    }
}
