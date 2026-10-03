package me.pipi.codexmeter;

import android.content.Context;
import android.content.res.Resources;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Localized reset labels and refresh times for the Clear card. */
final class UsageCardFormat {
    static final String MISSING = "—";

    private static final long MINUTES_PER_DAY = TimeUnit.DAYS.toMinutes(1);
    private static final long MINUTES_PER_HOUR = TimeUnit.HOURS.toMinutes(1);
    private static final long RESETTING_SOON_MS = TimeUnit.MINUTES.toMillis(1);

    private UsageCardFormat() {
    }

    /** Upstream compact units: "3d 8h", "2h 25m" or "5m" in every widget locale. */
    static String duration(Resources resources, long millis) {
        long totalMinutes = Math.max(0L, millis) / TimeUnit.MINUTES.toMillis(1);
        long days = totalMinutes / MINUTES_PER_DAY;
        long hours = (totalMinutes % MINUTES_PER_DAY) / MINUTES_PER_HOUR;
        long minutes = totalMinutes % MINUTES_PER_HOUR;
        if (days > 0) {
            return resources.getString(R.string.widget_card_days_hours, days, hours);
        }
        if (hours > 0) {
            return resources.getString(R.string.widget_card_hours_minutes, hours, minutes);
        }
        return resources.getString(R.string.widget_card_minutes, Math.max(1L, minutes));
    }

    /** Reset label with a compact countdown, imminent/expired state or missing value. */
    static String relativeReset(Resources resources, long resetAtMillis, long nowMillis) {
        String bare = bareReset(resources, resetAtMillis, nowMillis);
        if (resetAtMillis <= 0L || resetAtMillis - nowMillis < RESETTING_SOON_MS) {
            return bare;
        }
        return resources.getString(R.string.widget_card_resets_in, bare);
    }

    /** Reset meter value without a label prefix. */
    static String bareReset(Resources resources, long resetAtMillis, long nowMillis) {
        if (resetAtMillis <= 0L) {
            return MISSING;
        }
        long remaining = resetAtMillis - nowMillis;
        if (remaining < 0L) {
            return resources.getString(R.string.widget_card_reset_done);
        }
        if (remaining < RESETTING_SOON_MS) {
            return resources.getString(R.string.widget_card_resetting_soon);
        }
        return duration(resources, remaining);
    }

    /** Exact reset date and time for widget usage details. */
    static String absoluteReset(long millis) {
        return millis <= 0L ? MISSING
                : new SimpleDateFormat("MM/dd HH:mm", Locale.ROOT).format(new Date(millis));
    }

    /** Successful-refresh time in the requested 24-hour format. */
    static String time(Context context, long millis) {
        if (millis <= 0L) {
            return MISSING;
        }
        return new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(millis));
    }
}
