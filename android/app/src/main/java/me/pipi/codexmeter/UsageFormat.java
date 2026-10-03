package me.pipi.codexmeter;

import dev.bennett.codexmeter.NowBarCopy;
import dev.bennett.codexmeter.UsagePace;
import dev.bennett.codexmeter.UsageWindow;

import android.content.Context;
import android.text.format.DateFormat;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Phone/widget presentation of plans, percentages, reset times, and ages, localized through
 * the given {@link Context}. Plan names are product names and stay untranslated.
 */
public final class UsageFormat {
    private static final long MINUTES_PER_HOUR = TimeUnit.HOURS.toMinutes(1);
    private static final long MINUTES_PER_DAY = TimeUnit.DAYS.toMinutes(1);
    private static final long HOURS_PER_DAY = TimeUnit.DAYS.toHours(1);
    /** Separates an absolute time from its relative countdown, e.g. "Fri 10:00 · in 2d". */
    private static final String TIME_SEPARATOR = " · ";

    private UsageFormat() {
    }

    /**
     * Display name for a ChatGPT plan type. Pro tier aliases use the current 100, 200,
     * and 500 product names. Unknown plans return an empty string.
     */
    public static String planLabel(String plan) {
        if (plan == null || plan.trim().isEmpty()) {
            return "";
        }
        String normalized = plan.trim().toLowerCase(Locale.ROOT)
                .replace("_", "").replace("-", "").replace(" ", "").replace("×", "x");
        switch (normalized) {
            case "free":
                return "Free";
            case "go":
                return "GO";
            case "plus":
                return "Plus";
            case "prolite":
            case "pro5x":
            case "pro100":
                return "Pro 100";
            case "pro":
            case "pro10x":
            case "pro200":
                return "Pro 200";
            case "pro25x":
            case "pro500":
                return "Pro 500";
            case "team":
                return "Team";
            case "business":
                return "BUSINESS";
            case "enterprise":
                return "ENTERPRISE";
            case "premium":
                return "PREMIUM";
            default:
                return "";
        }
    }

    /** Numeric credit balance with grouping and at most two fraction digits. */
    public static String creditBalance(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.getDefault());
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }

    public static String percent(Context context, UsageWindow usageWindow, String mode,
            boolean compact) {
        if (usageWindow == null) {
            return compact ? "—" : context.getString(R.string.dashboard_percent_unavailable);
        }
        boolean showUsed = WidgetOptions.DISPLAY_USED.equals(mode);
        int value = showUsed ? usageWindow.usedPercent : usageWindow.remainingPercent();
        if (compact) {
            return value + "%";
        }
        return context.getString(showUsed
                ? R.string.dashboard_percent_used : R.string.dashboard_percent_left, value);
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
            return context.getString(R.string.dashboard_reset_unavailable);
        }
        if (WidgetOptions.RESET_RELATIVE.equals(mode)) {
            String countdown = countdown(context, resetAtMillis, nowMillis);
            return countdown.isEmpty()
                    ? context.getString(R.string.dashboard_resets_now)
                    : context.getString(R.string.dashboard_resets_relative, countdown);
        }
        String absolute = absolute(context, resetAtMillis, nowMillis);
        if (WidgetOptions.RESET_BOTH.equals(mode)) {
            return context.getString(R.string.dashboard_resets_both, absolute,
                    relative(context, resetAtMillis, nowMillis));
        }
        return context.getString(R.string.dashboard_resets_absolute, absolute);
    }

    /**
     * Time left until the window resets as a compact duration ("2h 50m"), or an empty string
     * when the window shows no countdown, its reset time is unknown, or under a minute remains.
     */
    public static String resetCountdown(Context context, UsageWindow usageWindow,
            long observedAtMillis, long nowMillis) {
        if (usageWindow == null || !usageWindow.showsResetCountdown()) {
            return "";
        }
        long resetAtMillis = usageWindow.effectiveResetAtMillis(observedAtMillis);
        return resetAtMillis <= 0 ? "" : countdown(context, resetAtMillis, nowMillis);
    }

    /** Compact duration until {@code targetMillis}, or "" once less than a minute remains. */
    private static String countdown(Context context, long targetMillis, long nowMillis) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(Math.max(0L, targetMillis - nowMillis));
        return minutes > 0 ? compactDuration(context, targetMillis - nowMillis) : "";
    }

    /** "Est. 1d 3h" / "Est. depleted", or "" when no estimate is available. */
    public static String estimatedRemaining(Context context, UsagePace.Assessment assessment) {
        return estimate(context, assessment, R.string.dashboard_estimate_remaining,
                R.string.dashboard_estimate_depleted);
    }

    /** Home-card exhaustion estimate using the compact units shown by the upstream dials. */
    static String estimatedExhaustion(Context context, UsagePace.Assessment assessment) {
        if (assessment == null || !assessment.available) {
            return "";
        }
        return assessment.estimatedRemainingMillis <= 0L
                ? context.getString(R.string.dashboard_estimate_depleted)
                : context.getString(R.string.dashboard_home_estimate_exhaustion,
                        NowBarCopy.compactDuration(assessment.estimatedRemainingMillis));
    }

    /** Soonest three available reset expiries for the home-card detail row. */
    static String resetCreditExpiryCountdowns(ResetCreditsSnapshot credits, long nowMillis) {
        if (credits == null || credits.availableCount <= 0) {
            return "";
        }
        List<String> countdowns = new ArrayList<>();
        for (RateLimitResetCredit credit : credits.availableCreditsByExpiry(nowMillis)) {
            if (credit.expiresAtMillis <= 0L) {
                continue;
            }
            countdowns.add(NowBarCopy.compactDuration(credit.expiresAtMillis - nowMillis));
            if (countdowns.size() == 3) {
                break;
            }
        }
        return String.join(" · ", countdowns);
    }

    /** Spoken home-card exhaustion estimate, with localized minute units. */
    public static String estimatedRemainingSpoken(Context context,
            UsagePace.Assessment assessment) {
        return estimate(context, assessment, R.string.dashboard_estimate_remaining_spoken,
                R.string.dashboard_estimate_depleted_spoken);
    }

    private static String estimate(Context context, UsagePace.Assessment assessment,
            int remainingRes, int depletedRes) {
        if (assessment == null || !assessment.available) {
            return "";
        }
        if (assessment.estimatedRemainingMillis <= 0L) {
            return context.getString(depletedRes);
        }
        return context.getString(remainingRes,
                compactDuration(context, assessment.estimatedRemainingMillis));
    }

    /** Localized "Xd Yh", "Xh Ym", or "Xm"; never below one minute. */
    static String compactDuration(Context context, long millis) {
        long minutes = durationMinutes(millis);
        long days = minutes / MINUTES_PER_DAY;
        long hours = minutes % MINUTES_PER_DAY / MINUTES_PER_HOUR;
        if (days > 0L) {
            return context.getString(R.string.dashboard_duration_days_hours, days, hours);
        }
        if (hours > 0L) {
            return context.getString(R.string.dashboard_duration_hours_minutes, hours,
                    minutes % MINUTES_PER_HOUR);
        }
        return context.getString(R.string.dashboard_duration_minutes, minutes);
    }

    private static long durationMinutes(long millis) {
        return Math.max(1L, TimeUnit.MILLISECONDS.toMinutes(Math.max(0L, millis)));
    }

    /** "today at …", "tomorrow at …", or a weekday-date-time, in the device's clock style. */
    public static String absolute(Context context, long millis, long nowMillis) {
        Calendar target = calendarAt(millis);
        Calendar today = calendarAt(nowMillis);
        Calendar tomorrow = (Calendar) today.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        Date date = new Date(millis);
        String time = clockTime(context, date);
        if (sameDay(target, today)) {
            return context.getString(R.string.dashboard_time_today, time);
        }
        if (sameDay(target, tomorrow)) {
            return context.getString(R.string.dashboard_time_tomorrow, time);
        }
        return context.getString(R.string.dashboard_time_date,
                localizedPattern("EEEMMMd", date), time);
    }

    /** Absolute time followed by the relative countdown, e.g. "Fri, May 2 at 10:00 · in 2d". */
    public static String absoluteAndRelative(Context context, long millis, long nowMillis) {
        return absolute(context, millis, nowMillis) + TIME_SEPARATOR
                + relative(context, millis, nowMillis);
    }

    /** Hours and minutes in the device's 12/24-hour clock style and the current locale. */
    static String clockTime(Context context, Date date) {
        return localizedPattern(DateFormat.is24HourFormat(context) ? "Hm" : "hma", date);
    }

    /** Formats {@code date} with the current locale's best pattern for {@code skeleton}. */
    static String localizedPattern(String skeleton, Date date) {
        Locale locale = Locale.getDefault();
        return new SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
                .format(date);
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
    public static String relative(Context context, long targetMillis, long nowMillis) {
        String countdown = countdown(context, targetMillis, nowMillis);
        return countdown.isEmpty()
                ? context.getString(R.string.dashboard_relative_now)
                : context.getString(R.string.dashboard_relative_in, countdown);
    }

    public static String updated(Context context, long observedMillis, long nowMillis) {
        if (observedMillis <= 0) {
            return context.getString(R.string.dashboard_updated_never);
        }
        long minutes = Math.max(0L, TimeUnit.MILLISECONDS.toMinutes(nowMillis - observedMillis));
        if (minutes < 1) {
            return context.getString(R.string.dashboard_updated_just_now);
        }
        if (minutes < MINUTES_PER_HOUR) {
            return context.getString(R.string.dashboard_updated_minutes, minutes);
        }
        long hours = minutes / MINUTES_PER_HOUR;
        if (hours < HOURS_PER_DAY) {
            return context.getString(R.string.dashboard_updated_hours, hours);
        }
        return context.getString(R.string.dashboard_updated_days, hours / HOURS_PER_DAY);
    }
}
