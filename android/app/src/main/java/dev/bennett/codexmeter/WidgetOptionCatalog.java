package dev.bennett.codexmeter;

/**
 * Stored values for the home widget editor's choices; their labels live in the matching
 * {@code widget_editor_*_labels} string arrays, in the same order.
 */
final class WidgetOptionCatalog {
    static final String[] STYLE_VALUES = {WidgetOptions.CARD_COLOR, WidgetOptions.CARD_CLEAR};
    static final String[] THEME_VALUES = {
            WidgetOptions.THEME_SYSTEM, WidgetOptions.THEME_DARK, WidgetOptions.THEME_LIGHT};
    static final String[] ACCENT_VALUES = {
            WidgetOptions.ACCENT_MINT, WidgetOptions.ACCENT_BLUE, WidgetOptions.ACCENT_AMBER,
            WidgetOptions.ACCENT_VIOLET, WidgetOptions.ACCENT_ROSE, WidgetOptions.ACCENT_CYAN,
            WidgetOptions.ACCENT_LIME, WidgetOptions.ACCENT_MONO};
    /** One UI 7-style discrete fill strengths when the widget background is enabled. */
    static final int[] OPACITY_VALUES = WidgetOptions.OPACITY_LEVELS;

    private WidgetOptionCatalog() {
    }

    /** Position of {@code value} in {@code values}, or 0 when it is not listed. */
    static int indexOf(String[] values, String value) {
        for (int index = 0; index < values.length; index++) {
            if (values[index].equals(value)) {
                return index;
            }
        }
        return 0;
    }
}
