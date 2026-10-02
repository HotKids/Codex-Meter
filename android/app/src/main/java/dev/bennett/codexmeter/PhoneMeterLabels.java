package dev.bennett.codexmeter;

import android.content.Context;

/** Localizes shared meter keys on the phone without changing Wear's labels or protocol. */
final class PhoneMeterLabels {
    private PhoneMeterLabels() { }
    static String config(Context context, String key, UsageSnapshot snapshot) {
        if (WidgetMeters.FIVE_HOUR.equals(key)) return context.getString(R.string.widget_meter_five_hour);
        if (WidgetMeters.WEEKLY.equals(key)) return context.getString(WidgetMeters.weeklyMeterIsMonthly(snapshot)
                ? R.string.widget_meter_monthly : R.string.widget_meter_weekly);
        if (WidgetMeters.NEXT_RESET.equals(key)) return context.getString(R.string.widget_meter_next_reset);
        if (WidgetMeters.RESET_CREDITS.equals(key)) return context.getString(R.string.widget_meter_credits);
        return WidgetMeters.configLabel(key, snapshot);
    }
    static String shortLabel(String key, UsageSnapshot snapshot) {
        if (WidgetMeters.FIVE_HOUR.equals(key)) return AppText.get(R.string.card_short_five);
        if (WidgetMeters.WEEKLY.equals(key)) return AppText.get(WidgetMeters.weeklyMeterIsMonthly(snapshot)
                ? R.string.card_short_month : R.string.card_short_week);
        if (WidgetMeters.NEXT_RESET.equals(key)) return AppText.get(R.string.card_short_reset);
        if (WidgetMeters.RESET_CREDITS.equals(key)) return AppText.get(R.string.card_short_credit);
        return WidgetMeters.shortLabel(key, snapshot);
    }
}
