package dev.bennett.codexmeter;

/**
 * Stored values for the home widget editor's choices; style labels live in the
 * {@code widget_editor_style_labels} string array, in the same order.
 */
final class WidgetOptionCatalog {
    static final String[] STYLE_VALUES = {WidgetOptions.CARD_COLOR, WidgetOptions.CARD_CLEAR};
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
