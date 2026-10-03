package me.pipi.codexmeter;

import dev.bennett.codexmeter.DashboardSections;
import dev.bennett.codexmeter.HistorySections;
import dev.bennett.codexmeter.PlanPricing;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.WidgetMeters;

import android.content.Context;

/**
 * Localized phone-side labels for values the shared module describes in English.
 * Every lookup is keyed by the shared classes' stable
 * keys or fields, never by matching their English output.
 */
final class SharedLabels {
    private SharedLabels() {
    }

    // ---------------------------------------------------------------------------------------
    // HistorySections
    // ---------------------------------------------------------------------------------------

    /** Customize-sheet label for a {@link HistorySections} key. */
    static String historySection(Context context, String key) {
        if (HistorySections.GUIDE.equals(key)) {
            return context.getString(R.string.dashboard_highlight_guide);
        }
        if (HistorySections.WINDOW_LIST.equals(key)) {
            return context.getString(R.string.dashboard_highlight_window_list);
        }
        if (HistorySections.INSIGHT_PACE.equals(key)) {
            return context.getString(R.string.dashboard_highlight_pace);
        }
        if (HistorySections.INSIGHT_EXHAUSTION.equals(key)) {
            return context.getString(R.string.dashboard_history_projected_exhaustion);
        }
        if (HistorySections.INSIGHT_AVERAGE.equals(key)) {
            return context.getString(R.string.dashboard_highlight_average);
        }
        if (HistorySections.INSIGHT_PEAK.equals(key)) {
            return context.getString(R.string.dashboard_highlight_peak);
        }
        if (HistorySections.VALUE_ESTIMATES.equals(key)) {
            return context.getString(R.string.dashboard_highlight_value);
        }
        return HistorySections.label(key);
    }

    // ---------------------------------------------------------------------------------------
    // DashboardSections
    // ---------------------------------------------------------------------------------------

    /** Editor title for a standard {@link DashboardSections} key. */
    static String dashboardSectionTitle(Context context, String key) {
        if (DashboardSections.FIVE_HOUR.equals(key)) {
            return context.getString(R.string.dashboard_section_five_hour_title);
        }
        if (DashboardSections.WEEKLY.equals(key)) {
            return context.getString(R.string.dashboard_section_weekly_title);
        }
        if (DashboardSections.MONTHLY.equals(key)) {
            return context.getString(R.string.dashboard_section_monthly_title);
        }
        if (DashboardSections.USAGE_CREDITS.equals(key)) {
            return context.getString(R.string.dashboard_section_usage_credits_title);
        }
        if (DashboardSections.USAGE_HISTORY.equals(key)) {
            return context.getString(R.string.dashboard_usage_history);
        }
        if (DashboardSections.RESET_CREDITS.equals(key)) {
            return context.getString(R.string.dashboard_reset_credits);
        }
        return key;
    }

    /** Editor summary explaining when a {@link DashboardSections} key is shown. */
    static String dashboardSectionSummary(Context context, String key) {
        if (DashboardSections.FIVE_HOUR.equals(key)) {
            return context.getString(R.string.dashboard_section_five_hour_summary);
        }
        if (DashboardSections.WEEKLY.equals(key)) {
            return context.getString(R.string.dashboard_section_weekly_summary);
        }
        if (DashboardSections.MONTHLY.equals(key)) {
            return context.getString(R.string.dashboard_section_monthly_summary);
        }
        if (DashboardSections.USAGE_CREDITS.equals(key)) {
            return context.getString(R.string.dashboard_section_usage_credits_summary);
        }
        if (DashboardSections.USAGE_HISTORY.equals(key)) {
            return context.getString(R.string.dashboard_section_usage_history_summary);
        }
        if (DashboardSections.RESET_CREDITS.equals(key)) {
            return context.getString(R.string.dashboard_section_reset_credits_summary);
        }
        return "";
    }

    // ---------------------------------------------------------------------------------------
    // PlanPricing
    // ---------------------------------------------------------------------------------------

    /**
     * Value-card title such as "Plus · $20/month". Plan names use the phone's canonical
     * product-name mapping, including the fallback when the caller has no label.
     */
    static String planPriceTitle(Context context, String planName, PlanPricing pricing) {
        String name = planName == null || planName.isEmpty()
                ? UsageFormat.planLabel(pricing.planKey) : planName;
        return context.getString(R.string.dashboard_plan_price, name,
                PlanPricing.formatUsd(pricing.monthlyPriceUsd));
    }

    /** A dollar amount per month, e.g. "$700/month". */
    static String perMonth(Context context, double usd) {
        return context.getString(R.string.dashboard_value_per_month, PlanPricing.formatUsd(usd));
    }

    /** A dollar amount per hour, e.g. "$1.20/h". */
    static String perHour(Context context, double usd) {
        return context.getString(R.string.dashboard_value_per_hour, PlanPricing.formatUsd(usd));
    }

    // ---------------------------------------------------------------------------------------
    // WidgetMeters
    // ---------------------------------------------------------------------------------------

    /** Localized {@link WidgetMeters#configLabel}: the config-row title for a meter key. */
    static String widgetMeterConfigLabel(Context context, String key, UsageSnapshot snapshot) {
        if (WidgetMeters.FIVE_HOUR.equals(key)) {
            return context.getString(R.string.dashboard_meter_five_hour);
        }
        if (WidgetMeters.WEEKLY.equals(key)) {
            return context.getString(WidgetMeters.weeklyMeterIsMonthly(snapshot)
                    ? R.string.dashboard_meter_monthly : R.string.dashboard_meter_weekly);
        }
        if (WidgetMeters.NEXT_RESET.equals(key)) {
            return context.getString(R.string.dashboard_meter_next_reset);
        }
        if (WidgetMeters.RESET_CREDITS.equals(key)) {
            return context.getString(R.string.dashboard_reset_credits);
        }
        return key;
    }

    /** Localized {@link WidgetMeters#shortLabel}: the short label for dial and bar faces. */
    static String widgetMeterShortLabel(Context context, String key, UsageSnapshot snapshot) {
        if (WidgetMeters.FIVE_HOUR.equals(key)) {
            return context.getString(R.string.dashboard_meter_short_five_hour);
        }
        if (WidgetMeters.WEEKLY.equals(key)) {
            return context.getString(WidgetMeters.weeklyMeterIsMonthly(snapshot)
                    ? R.string.dashboard_meter_short_monthly
                    : R.string.dashboard_meter_short_weekly);
        }
        if (WidgetMeters.NEXT_RESET.equals(key)) {
            return context.getString(R.string.dashboard_meter_short_next_reset);
        }
        if (WidgetMeters.RESET_CREDITS.equals(key)) {
            return context.getString(R.string.dashboard_meter_short_reset_credits);
        }
        return context.getString(R.string.dashboard_meter_short_fallback);
    }

}
