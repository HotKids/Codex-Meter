package dev.bennett.codexmeter;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** User settings for low-usage, reset, refill, and reset-credit notifications. */
public final class ResetAlertPreferences {
    public static final String METRIC_BOTH = "both";
    public static final String METRIC_FIVE_HOUR = "five_hour";
    public static final String METRIC_WEEKLY = "weekly";
    public static final String STYLE_ALARM = "alarm";
    public static final String STYLE_NOTIFICATION = "notification";
    public static final String STYLE_OFF = "off";
    public static final String STYLE_SILENT = "silent";
    public static final long DEFAULT_RESET_CREDIT_EXPIRY_LEAD_TIME_MS = TimeUnit.DAYS.toMillis(1);

    private static final String PREFS = "codex_meter_reset_alerts_v1";
    private static final String KEY_METRIC = "metric";
    private static final String KEY_RESET_CREDIT_EXPIRY = "reset_credit_expiry";
    private static final String KEY_RESET_CREDIT_EXPIRY_LEAD_TIMES =
            "reset_credit_expiry_lead_times";
    private static final String KEY_RESET_CREDIT_INCREASES = "reset_credit_increases";
    private static final String KEY_STYLE = "style";
    private static final String KEY_THRESHOLD = "threshold";
    private static final String KEY_UNEXPECTED_REFILLS = "unexpected_refills";
    private static final int DEFAULT_THRESHOLD = 25;

    private ResetAlertPreferences() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getStyle(Context context) {
        return normalizeStyle(prefs(context).getString(KEY_STYLE, STYLE_OFF));
    }

    public static String getMetric(Context context) {
        return normalizeMetric(prefs(context).getString(KEY_METRIC, METRIC_BOTH));
    }

    public static int getThreshold(Context context) {
        return normalizeThreshold(prefs(context).getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD));
    }

    public static void save(Context context, String style, String metric, int threshold) {
        prefs(context).edit()
                .putString(KEY_STYLE, normalizeStyle(style))
                .putString(KEY_METRIC, normalizeMetric(metric))
                .putInt(KEY_THRESHOLD, normalizeThreshold(threshold))
                .apply();
    }

    public static boolean enabled(Context context) {
        return !STYLE_OFF.equals(getStyle(context));
    }

    public static boolean unexpectedRefillsEnabled(Context context) {
        return prefs(context).getBoolean(KEY_UNEXPECTED_REFILLS, true);
    }

    public static void setUnexpectedRefillsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_UNEXPECTED_REFILLS, enabled).apply();
    }

    public static boolean resetCreditIncreasesEnabled(Context context) {
        return prefs(context).getBoolean(KEY_RESET_CREDIT_INCREASES, true);
    }

    public static void setResetCreditIncreasesEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_RESET_CREDIT_INCREASES, enabled).apply();
    }

    public static boolean resetCreditExpiryEnabled(Context context) {
        return prefs(context).getBoolean(KEY_RESET_CREDIT_EXPIRY, true);
    }

    public static void setResetCreditExpiryEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_RESET_CREDIT_EXPIRY, enabled).apply();
    }

    /**
     * Sorted reminder lead times. Defaults to a single one-day reminder until the user saves a
     * selection; an explicitly saved empty selection stays empty.
     */
    public static List<Long> getResetCreditExpiryLeadTimes(Context context) {
        SharedPreferences preferences = prefs(context);
        if (!preferences.contains(KEY_RESET_CREDIT_EXPIRY_LEAD_TIMES)) {
            return Collections.singletonList(DEFAULT_RESET_CREDIT_EXPIRY_LEAD_TIME_MS);
        }
        Set<String> stored = preferences.getStringSet(KEY_RESET_CREDIT_EXPIRY_LEAD_TIMES,
                Collections.emptySet());
        List<Long> values = new ArrayList<>();
        if (stored != null) {
            for (String value : stored) {
                try {
                    long parsed = Long.parseLong(value);
                    if (ResetCreditExpiryReminder.isValidLeadTime(parsed)) {
                        values.add(parsed);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        Collections.sort(values);
        return values;
    }

    public static void setResetCreditExpiryLeadTimes(Context context, List<Long> leadTimes) {
        Set<String> stored = new HashSet<>();
        if (leadTimes != null) {
            for (Long leadTime : leadTimes) {
                if (leadTime != null && ResetCreditExpiryReminder.isValidLeadTime(leadTime)) {
                    stored.add(String.valueOf(leadTime));
                }
            }
        }
        prefs(context).edit().putStringSet(KEY_RESET_CREDIT_EXPIRY_LEAD_TIMES, stored).apply();
    }

    private static String normalizeStyle(String style) {
        return STYLE_SILENT.equals(style) || STYLE_NOTIFICATION.equals(style)
                || STYLE_ALARM.equals(style) ? style : STYLE_OFF;
    }

    private static String normalizeMetric(String metric) {
        return METRIC_FIVE_HOUR.equals(metric) || METRIC_WEEKLY.equals(metric)
                ? metric : METRIC_BOTH;
    }

    private static int normalizeThreshold(int threshold) {
        boolean valid = threshold == 10 || threshold == 25 || threshold == 50
                || threshold == 75 || threshold == 100;
        return valid ? threshold : DEFAULT_THRESHOLD;
    }
}
