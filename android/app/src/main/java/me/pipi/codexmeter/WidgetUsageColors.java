package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageWindow;

/** Classic usage colors; fill length continues to represent the remaining allowance. */
final class WidgetUsageColors {
    private WidgetUsageColors() {
    }

    static int color(UsageWindow window, boolean usage) {
        // Reset progress measures waiting time, so exhaustion warnings do not apply to it.
        if (usage && window != null) {
            if (window.usedPercent >= 85) return R.color.widget_classic_progress_critical;
            if (window.usedPercent >= 60) return R.color.widget_classic_progress_warning;
        }
        return R.color.widget_classic_progress_normal;
    }
}
