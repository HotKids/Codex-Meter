package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageWindow;

/** Shared severity thresholds; fill length continues to represent the remaining allowance. */
final class WidgetUsageColors {
    private WidgetUsageColors() {
    }

    static int color(UsageWindow window, boolean usage) {
        // Reset progress measures waiting time, so exhaustion warnings do not apply to it.
        return color(usage && window != null ? window.usedPercent : 0);
    }

    static int color(int usedPercent) {
        if (usedPercent >= 85) return R.color.widget_classic_progress_critical;
        if (usedPercent >= 60) return R.color.widget_classic_progress_warning;
        return R.color.widget_classic_progress_normal;
    }

    static int materialColor(UsageWindow window, boolean usage) {
        int classic = color(window, usage);
        if (classic == R.color.widget_classic_progress_critical) {
            return R.color.widget_material_fill_critical;
        }
        if (classic == R.color.widget_classic_progress_warning) {
            return R.color.widget_material_fill_warning;
        }
        return R.color.widget_material_fill;
    }
}
