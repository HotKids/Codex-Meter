package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsageWindow;
import dev.bennett.codexmeter.WidgetMeters;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/**
 * The usage meters a Samsung lock widget shows in its (at most two) slots. The primary slot is
 * always filled; the secondary one is {@code null} for single-meter widgets.
 */
final class LockMeterBinding {
    /** One meter as drawn on a lock widget face. */
    static final class Slot {
        final String label;
        /** Remaining percent clamped to 0-100, or -1 when the window is unknown. */
        final int remaining;
        final UsageWindow window;
        final int iconRes;

        Slot(String label, int remaining, UsageWindow window, int iconRes) {
            this.label = label;
            this.remaining = remaining;
            this.window = window;
            this.iconRes = iconRes;
        }
    }

    final Slot primary;
    final Slot secondary;
    final int primaryRemaining;
    final int secondaryRemaining;
    final boolean showPrimary;
    final boolean showSecondary;
    /** Face labels used when a slot is empty. */
    private final String defaultPrimaryLabel;
    private final String defaultSecondaryLabel;

    private LockMeterBinding(Context context, Slot primary, Slot secondary) {
        this.defaultPrimaryLabel = context.getString(R.string.dashboard_lock_face_five_hour);
        this.defaultSecondaryLabel = context.getString(R.string.dashboard_lock_face_weekly);
        this.primary = primary;
        this.secondary = secondary;
        this.showPrimary = primary != null;
        this.showSecondary = secondary != null;
        this.primaryRemaining = primary == null ? -1 : primary.remaining;
        this.secondaryRemaining = secondary == null ? -1 : secondary.remaining;
    }

    /** Resolves the widget's saved meter selection against what the snapshot offers. */
    static LockMeterBinding bind(Context context, UsageSnapshot snapshot,
            LockWidgetOptions options) {
        List<String> available = WidgetMeters.availableKeys(snapshot);
        String metricMode = options == null ? WidgetOptions.METRIC_BOTH : options.metricMode;
        String visibleCsv = options == null ? "" : options.effectiveVisibleMeters();
        List<String> usageKeys = new ArrayList<>();
        for (String key : WidgetMeters.resolveVisibleForWidget(visibleCsv, available, metricMode)) {
            if (WidgetMeters.FIVE_HOUR.equals(key) || WidgetMeters.WEEKLY.equals(key)) {
                usageKeys.add(key);
            }
        }
        usageKeys = new ArrayList<>(WidgetMeters.cap(usageKeys, WidgetMeters.lockSlotCapacity()));
        if (usageKeys.isEmpty()) {
            usageKeys.add(WidgetMeters.FIVE_HOUR);
        }
        Slot primary = slot(context, usageKeys.get(0), snapshot);
        Slot secondary = usageKeys.size() > 1 ? slot(context, usageKeys.get(1), snapshot) : null;
        return new LockMeterBinding(context, primary, secondary);
    }

    /** Remaining percent clamped to 0-100, or -1 when the window is unknown. */
    static int remaining(UsageWindow window) {
        if (window == null) {
            return -1;
        }
        return Math.max(0, Math.min(100, window.remainingPercent()));
    }

    boolean singleMetric() {
        return showPrimary && !showSecondary;
    }

    String primaryLabel() {
        return primary == null ? defaultPrimaryLabel : primary.label;
    }

    String secondaryLabel() {
        return secondary == null ? defaultSecondaryLabel : secondary.label;
    }

    UsageWindow primaryWindow() {
        return primary == null ? null : primary.window;
    }

    UsageWindow secondaryWindow() {
        return secondary == null ? null : secondary.window;
    }

    int primaryIconRes() {
        return primary == null ? R.drawable.ic_oui_time : primary.iconRes;
    }

    int secondaryIconRes() {
        return secondary == null ? R.drawable.ic_oui_calendar_week : secondary.iconRes;
    }

    private static Slot slot(Context context, String key, UsageSnapshot snapshot) {
        if (WidgetMeters.FIVE_HOUR.equals(key)) {
            UsageWindow window = snapshot == null ? null : snapshot.fiveHour;
            return new Slot(context.getString(R.string.dashboard_lock_face_five_hour),
                    remaining(window), window, R.drawable.ic_oui_time);
        }
        if (WidgetMeters.WEEKLY.equals(key)) {
            UsageWindow window = WidgetMeters.meterWindow(key, snapshot);
            String label = context.getString(WidgetMeters.weeklyMeterIsMonthly(snapshot)
                    ? R.string.dashboard_lock_face_monthly : R.string.dashboard_lock_face_weekly);
            return new Slot(label, remaining(window), window, R.drawable.ic_oui_calendar_week);
        }
        throw new IllegalArgumentException("Unsupported lock widget meter: " + key);
    }
}
