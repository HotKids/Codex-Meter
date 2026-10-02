package dev.bennett.codexmeter;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Card copy rules ported from AI-Usage's usage-format.ts, localized through resources. */
final class UsageCardFormat {
    static final String MISSING = "—";

    private static final long MINUTES_PER_DAY = TimeUnit.DAYS.toMinutes(1);
    private static final long MINUTES_PER_HOUR = TimeUnit.HOURS.toMinutes(1);
    private static final long RESETTING_SOON_MS = TimeUnit.MINUTES.toMillis(1);

    private UsageCardFormat() {
    }

    /** Remaining percentage such as "72%", or "—" when the window is unknown. */
    static String percent(UsageWindow window) {
        return window == null ? MISSING : window.remainingPercent() + "%";
    }

    /** "3天8小时", "2小时25分" or "5分钟" (at least one minute) for a positive duration. */
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

    /** Row subtitle: "2小时50分后重置", "即将重置", "已重置" or "—". */
    static String relativeReset(Resources resources, long resetAtMillis, long nowMillis) {
        String bare = bareReset(resources, resetAtMillis, nowMillis);
        if (resetAtMillis <= 0L || resetAtMillis - nowMillis < RESETTING_SOON_MS) {
            return bare;
        }
        return resources.getString(R.string.widget_card_resets_in, bare);
    }

    /** Footer value next to "重置时间": the same rules without the "后重置" suffix. */
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

    /** Reset-credit expiry such as "09月13日 02:49" (zh) or "Sep 13, 02:49" (en). */
    static String expiryDate(Context context, long millis) {
        Resources resources = context.getResources();
        Locale locale = resources.getConfiguration().getLocales().get(0);
        String pattern = resources.getString(R.string.widget_card_date_pattern) + " "
                + (DateFormat.is24HourFormat(context) ? "HH:mm" : "h:mm a");
        return new SimpleDateFormat(pattern, locale).format(new Date(millis));
    }

    /** Wall-clock time of the last refresh, honouring the 12/24-hour setting. */
    static String time(Context context, long millis) {
        if (millis <= 0L) {
            return MISSING;
        }
        return DateFormat.getTimeFormat(context).format(new Date(millis));
    }
}
