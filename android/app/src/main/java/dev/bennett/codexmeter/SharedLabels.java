package dev.bennett.codexmeter;

import android.content.Context;
import java.util.List;

/**
 * Localized phone-side labels for values the frozen shared module (also used by the Wear OS
 * companion) only describes in English. Every lookup is keyed by the shared classes' stable
 * keys or fields, never by matching their English output.
 */
final class SharedLabels {
    /** Longest limit name kept whole on a short meter label (mirrors {@link WidgetMeters}). */
    private static final int SHORT_LIMIT_NAME_LENGTH = 10;

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

    /** Editor title for a {@link DashboardSections} key; limit keys use the limit's name. */
    static String dashboardSectionTitle(Context context, String key, List<UsageLimit> limits) {
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
        UsageLimit match = findDashboardLimit(key, limits);
        return match == null
                ? context.getString(R.string.dashboard_section_limit_title)
                : limitName(context, match);
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
        return context.getString(R.string.dashboard_section_limit_summary);
    }

    private static UsageLimit findDashboardLimit(String key, List<UsageLimit> limits) {
        if (limits == null) {
            return null;
        }
        for (UsageLimit limit : limits) {
            if (limit != null && DashboardSections.limitKey(limit).equals(key)) {
                return limit;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------
    // UsageLimit
    // ---------------------------------------------------------------------------------------

    /**
     * {@link UsageLimit#displayName()} with its English "Additional usage" fallback localized.
     * API-provided names are shown as reported.
     */
    static String limitName(Context context, UsageLimit limit) {
        if (limit == null || (limit.name.isEmpty() && limit.meteredFeature.isEmpty())) {
            return context.getString(R.string.dashboard_limit_additional_usage);
        }
        return limit.displayName();
    }

    // ---------------------------------------------------------------------------------------
    // PlanPricing
    // ---------------------------------------------------------------------------------------

    /**
     * Value-card title such as "Plus · $20/month". {@code planName} is the product name to
     * show; plan names are not translated, so {@link PlanPricing#planLabel} is used as is.
     */
    static String planPriceTitle(Context context, String planName, PlanPricing pricing) {
        String name = planName == null || planName.isEmpty() ? pricing.planLabel : planName;
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
        UsageLimit limit = WidgetMeters.findLimit(key, snapshot);
        if (limit != null) {
            String name = limitName(context, limit);
            return context.getString(WidgetMeters.isLimitPrimary(key)
                    ? R.string.dashboard_meter_limit_five_hour
                    : R.string.dashboard_meter_limit_weekly, name);
        }
        return WidgetMeters.configLabel(key, snapshot);
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
        UsageLimit limit = WidgetMeters.findLimit(key, snapshot);
        if (limit != null) {
            String base = shortLimitName(limitName(context, limit));
            return context.getString(WidgetMeters.isLimitPrimary(key)
                    ? R.string.dashboard_meter_short_limit_five_hour
                    : R.string.dashboard_meter_short_limit_weekly, base);
        }
        return context.getString(R.string.dashboard_meter_short_fallback);
    }

    /** Same shortening rule as the shared module: whole names up to 10 chars, else last word. */
    private static String shortLimitName(String displayName) {
        String trimmed = displayName.trim();
        if (trimmed.length() <= SHORT_LIMIT_NAME_LENGTH) {
            return trimmed;
        }
        String[] parts = trimmed.split("[\\s\\-_/]+");
        if (parts.length > 0 && !parts[parts.length - 1].isEmpty()) {
            String last = parts[parts.length - 1];
            return last.length() > SHORT_LIMIT_NAME_LENGTH
                    ? last.substring(0, SHORT_LIMIT_NAME_LENGTH) : last;
        }
        return trimmed.substring(0, SHORT_LIMIT_NAME_LENGTH);
    }
}
