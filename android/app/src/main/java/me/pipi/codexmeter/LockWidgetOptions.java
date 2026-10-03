package me.pipi.codexmeter;

import dev.bennett.codexmeter.WidgetMeters;

/** Per-widget settings for a Samsung lock-screen/AOD widget. */
public final class LockWidgetOptions {
    /** Legacy single-metric mode: one of the {@code WidgetOptions.METRIC_*} values. */
    public final String metricMode;
    public final boolean showCountdown;
    public final boolean showResetAction;
    public final boolean showResetCredits;
    /** Ordered CSV of {@link WidgetMeters} keys; empty means migrate from {@link #metricMode}. */
    public final String visibleMeters;

    public LockWidgetOptions(String metricMode, boolean showResetCredits, boolean showResetAction,
            boolean showCountdown, String visibleMeters) {
        boolean singleMetric = WidgetOptions.METRIC_FIVE_HOUR.equals(metricMode)
                || WidgetOptions.METRIC_WEEKLY.equals(metricMode);
        this.metricMode = singleMetric ? metricMode : WidgetOptions.METRIC_BOTH;
        this.showResetCredits = showResetCredits;
        this.showResetAction = showResetAction;
        this.showCountdown = showCountdown;
        this.visibleMeters = visibleMeters == null ? "" : visibleMeters.trim();
    }

    public static LockWidgetOptions defaults() {
        return new LockWidgetOptions(WidgetOptions.METRIC_BOTH, false, false, true,
                WidgetMeters.serialize(WidgetMeters.defaultVisible()));
    }

    /** Resolved visible-meters CSV, migrating from {@link #metricMode} when unset. */
    public String effectiveVisibleMeters() {
        return WidgetMeters.effectiveVisibleCsv(this.visibleMeters, this.metricMode);
    }
}
