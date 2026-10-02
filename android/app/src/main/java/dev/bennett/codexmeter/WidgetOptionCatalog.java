package dev.bennett.codexmeter;

import android.widget.Spinner;

/* JADX INFO: loaded from: classes.dex */
final class WidgetOptionCatalog {
    static final int[] THEME_LABELS = {R.string.widget_option_theme_0, R.string.widget_option_theme_1, R.string.widget_option_theme_2};
    static final String[] THEME_VALUES = {WidgetOptions.THEME_SYSTEM, WidgetOptions.THEME_DARK, WidgetOptions.THEME_LIGHT};
    static final int[] SURFACE_LABELS = {R.string.widget_option_surface_0};
    static final String[] SURFACE_VALUES = {WidgetOptions.SURFACE_ONE_UI};
    static final int[] STYLE_LABELS = {R.string.ref_adaptive};
    static final String[] STYLE_VALUES = {WidgetOptions.STYLE_CARDS};
    static final int[] DENSITY_LABELS = {R.string.widget_option_density_0, R.string.widget_option_density_1, R.string.widget_option_density_2};
    static final String[] DENSITY_VALUES = {"auto", "compact", WidgetOptions.DENSITY_COMFORTABLE};
    static final int[] GRAPHIC_LABELS = {R.string.widget_option_graphic_0, R.string.widget_option_graphic_1, R.string.widget_option_graphic_2};
    static final String[] GRAPHIC_VALUES = {"auto", WidgetOptions.GRAPHIC_LARGE, WidgetOptions.GRAPHIC_MAX};
    static final int[] ACCENT_LABELS = {R.string.widget_option_accent_0, R.string.widget_option_accent_1, R.string.widget_option_accent_2, R.string.widget_option_accent_3, R.string.widget_option_accent_4, R.string.widget_option_accent_5, R.string.widget_option_accent_6, R.string.widget_option_accent_7, R.string.widget_option_accent_8};
    static final String[] ACCENT_VALUES = {WidgetOptions.ACCENT_MINT, WidgetOptions.ACCENT_BLUE, WidgetOptions.ACCENT_AMBER, WidgetOptions.ACCENT_VIOLET, WidgetOptions.ACCENT_ROSE, WidgetOptions.ACCENT_CYAN, WidgetOptions.ACCENT_LIME, WidgetOptions.ACCENT_MONO, WidgetOptions.ACCENT_APP};
    /** One UI 7-style discrete fill strengths when the widget background is enabled. */
    static final int[] OPACITY_LABELS = {R.string.widget_option_opacity_0, R.string.widget_option_opacity_1, R.string.widget_option_opacity_2};
    static final int[] OPACITY_VALUES = WidgetOptions.OPACITY_LEVELS;
    static final int[] RESET_LABELS = {R.string.widget_option_reset_0, R.string.widget_option_reset_1, R.string.widget_option_reset_2, R.string.widget_option_reset_3};
    static final String[] RESET_VALUES = {WidgetOptions.RESET_ABSOLUTE, WidgetOptions.RESET_RELATIVE, "both", WidgetOptions.RESET_HIDDEN};
    static final int[] METRIC_LABELS = {R.string.widget_option_metric_0, R.string.widget_option_metric_1, R.string.widget_option_metric_2};
    static final String[] METRIC_VALUES = {WidgetOptions.METRIC_BOTH,
            WidgetOptions.METRIC_FIVE_HOUR, WidgetOptions.METRIC_WEEKLY};
    static final int[] DISPLAY_LABELS = {R.string.widget_option_display_0, R.string.widget_option_display_1};
    static final String[] DISPLAY_VALUES = {WidgetOptions.DISPLAY_REMAINING, WidgetOptions.DISPLAY_USED};

    static String[] labels(android.content.Context context, int[] resources) {
        String[] labels = new String[resources.length];
        for (int i = 0; i < resources.length; i++) labels[i] = context.getString(resources[i]);
        return labels;
    }

    private WidgetOptionCatalog() {
    }

    static void selectString(Spinner spinner, String[] values, String selected) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(selected)) {
                spinner.setSelection(i);
                return;
            }
        }
        spinner.setSelection(0);
    }
}
