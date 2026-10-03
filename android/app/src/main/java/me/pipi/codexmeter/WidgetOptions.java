package me.pipi.codexmeter;

import dev.bennett.codexmeter.WidgetMeters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Immutable appearance and content settings for a home-screen widget. Constructors validate
 * every value, so unknown stored or imported values fall back to safe defaults.
 */
public final class WidgetOptions {
    public static final String USAGE_CREDITS = "usage_credits";
    public static final String ACCENT_APP = "app";
    public static final String ACCENT_AMBER = "amber";
    public static final String ACCENT_BLUE = "blue";
    public static final String ACCENT_CYAN = "cyan";
    public static final String ACCENT_LIME = "lime";
    public static final String ACCENT_MINT = "mint";
    public static final String ACCENT_MONO = "mono";
    public static final String ACCENT_ROSE = "rose";
    public static final String ACCENT_VIOLET = "violet";
    /** Material You card used for placements taller than one row. */
    public static final String CARD_CLEAR = "clear";
    public static final String DENSITY_AUTO = "auto";
    public static final String DENSITY_COMFORTABLE = "comfortable";
    public static final String DENSITY_COMPACT = "compact";
    public static final String DISPLAY_REMAINING = "remaining";
    public static final String DISPLAY_USED = "used";
    public static final String GRAPHIC_AUTO = "auto";
    public static final String GRAPHIC_LARGE = "large";
    public static final String GRAPHIC_MAX = "maximum";
    public static final String LAYOUT_AUTO = "auto";
    public static final String LAYOUT_COMPACT = "compact";
    public static final String LAYOUT_DETAILED = "detailed";
    public static final String METRIC_BOTH = "both";
    public static final String METRIC_FIVE_HOUR = "five_hour";
    public static final String METRIC_WEEKLY = "weekly";
    public static final String RESET_ABSOLUTE = "absolute";
    public static final String RESET_BOTH = "both";
    public static final String RESET_HIDDEN = "hidden";
    public static final String RESET_RELATIVE = "relative";
    public static final String STYLE_AUTO = "auto";
    public static final String STYLE_BARS = "bars";
    public static final String STYLE_DIALS = "dials";
    public static final String STYLE_MINIMAL = "minimal";
    public static final String STYLE_RINGS = "rings";
    public static final String SURFACE_MATERIAL = "material";
    public static final String SURFACE_ONE_UI = "one_ui";
    public static final String THEME_DARK = "dark";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_SYSTEM = "system";
    public static final String TAP_OPEN_APP = "open_app";
    public static final String TAP_REFRESH = "refresh";
    public static final String TAP_USE_RESET = "use_reset";
    /** One UI 7-style discrete fill strengths when the widget background is enabled. */
    public static final int[] OPACITY_LEVELS = {56, 88, 100};
    /** Opaque by default; 56 and 88 remain selectable. */
    public static final int DEFAULT_OPACITY = 100;
    /** Opacities accepted from storage: off, legacy four-step and drawable-aligned values. */
    private static final int[] KNOWN_OPACITIES = {0, 15, 40, 56, 70, 72, 88, 94, 100};

    public final String accent;
    /** Legacy style preferences and imports normalize to Clear. */
    public final String cardStyle;
    public final String density;
    public final String displayMode;
    public final String graphicScale;
    public final String layout;
    public final String metricMode;
    public final int opacity;
    public final String resetMode;
    public final boolean showPlan;
    public final boolean showRefresh;
    public final boolean showResetAction;
    public final boolean showResetCredits;
    public final boolean showTitle;
    public final boolean showUpdated;
    public final boolean showPercentSymbol;
    public final String surfaceStyle;
    public final String theme;
    /** Ordered CSV of {@link WidgetMeters} keys; empty means migrate from {@link #metricMode}. */
    public final String visibleMeters;

    public WidgetOptions(String layout, String theme, String accent, int opacity,
            String resetMode, String displayMode) {
        this(layout, DENSITY_AUTO, SURFACE_MATERIAL, GRAPHIC_AUTO, theme, accent, opacity,
                resetMode, displayMode, METRIC_BOTH, false, true, true, true, false, false);
    }

    public WidgetOptions(String layout, String density, String theme, String accent, int opacity,
            String resetMode, String displayMode, boolean showPlan, boolean showUpdated,
            boolean showRefresh) {
        this(layout, density, SURFACE_MATERIAL, GRAPHIC_AUTO, theme, accent, opacity, resetMode,
                displayMode, METRIC_BOTH, false, showPlan, showUpdated, showRefresh, false, false);
    }

    public WidgetOptions(String layout, String density, String surfaceStyle, String graphicScale,
            String theme, String accent, int opacity, String resetMode, String displayMode,
            boolean showPlan, boolean showUpdated, boolean showRefresh) {
        this(layout, density, surfaceStyle, graphicScale, theme, accent, opacity, resetMode,
                displayMode, METRIC_BOTH, false, showPlan, showUpdated, showRefresh, false, false);
    }

    public WidgetOptions(String layout, String density, String surfaceStyle, String graphicScale,
            String theme, String accent, int opacity, String resetMode, String displayMode,
            boolean showTitle, boolean showPlan, boolean showUpdated, boolean showRefresh) {
        this(layout, density, surfaceStyle, graphicScale, theme, accent, opacity, resetMode,
                displayMode, METRIC_BOTH, showTitle, showPlan, showUpdated, showRefresh, false,
                false);
    }

    public WidgetOptions(String layout, String density, String surfaceStyle, String graphicScale,
            String theme, String accent, int opacity, String resetMode, String displayMode,
            String metricMode, boolean showTitle, boolean showPlan, boolean showUpdated,
            boolean showRefresh, boolean showResetCredits, boolean showResetAction) {
        this(layout, density, surfaceStyle, graphicScale, theme, accent, opacity, resetMode,
                displayMode, metricMode, showTitle, showPlan, showUpdated, showRefresh,
                showResetCredits, showResetAction, true, "", CARD_CLEAR);
    }

    private WidgetOptions(String layout, String density, String surfaceStyle,
            String graphicScale, String theme, String accent, int opacity, String resetMode,
            String displayMode, String metricMode, boolean showTitle, boolean showPlan,
            boolean showUpdated, boolean showRefresh, boolean showResetCredits,
            boolean showResetAction, boolean showPercentSymbol, String visibleMeters,
            String cardStyle) {
        this.layout = normalizeStyle(layout);
        this.density = oneOf(density, DENSITY_AUTO, DENSITY_COMPACT, DENSITY_COMFORTABLE)
                ? density : DENSITY_AUTO;
        this.surfaceStyle = oneOf(surfaceStyle, SURFACE_MATERIAL, SURFACE_ONE_UI)
                ? surfaceStyle : SURFACE_MATERIAL;
        this.graphicScale = oneOf(graphicScale, GRAPHIC_AUTO, GRAPHIC_LARGE, GRAPHIC_MAX)
                ? graphicScale : GRAPHIC_AUTO;
        this.theme = oneOf(theme, THEME_SYSTEM, THEME_DARK, THEME_LIGHT) ? theme : THEME_SYSTEM;
        this.accent = validAccent(accent) ? accent : ACCENT_MINT;
        this.opacity = normalizeOpacity(opacity);
        this.resetMode = oneOf(resetMode, RESET_ABSOLUTE, RESET_RELATIVE, RESET_BOTH, RESET_HIDDEN)
                ? resetMode : RESET_ABSOLUTE;
        this.displayMode = DISPLAY_USED.equals(displayMode) ? DISPLAY_USED : DISPLAY_REMAINING;
        this.metricMode = oneOf(metricMode, METRIC_BOTH, METRIC_FIVE_HOUR, METRIC_WEEKLY)
                ? metricMode : METRIC_BOTH;
        this.showTitle = showTitle;
        this.showPlan = showPlan;
        this.showUpdated = showUpdated;
        this.showRefresh = showRefresh;
        this.showResetCredits = showResetCredits;
        this.showResetAction = showResetAction;
        this.showPercentSymbol = showPercentSymbol;
        this.visibleMeters = visibleMeters == null ? "" : visibleMeters.trim();
        this.cardStyle = CARD_CLEAR;
    }

    public WidgetOptions withPercentSymbol(boolean show) {
        return new WidgetOptions(layout, density, surfaceStyle, graphicScale, theme, accent,
                opacity, resetMode, displayMode, metricMode, showTitle, showPlan,
                showUpdated, showRefresh, showResetCredits, showResetAction,
                show, visibleMeters, cardStyle);
    }

    public WidgetOptions withVisibleMeters(String metersCsv) {
        return new WidgetOptions(layout, density, surfaceStyle, graphicScale, theme, accent,
                opacity, resetMode, displayMode, metricMode, showTitle, showPlan,
                showUpdated, showRefresh, showResetCredits, showResetAction,
                showPercentSymbol, metersCsv, cardStyle);
    }

    public WidgetOptions withCardStyle(String style) {
        return new WidgetOptions(layout, density, surfaceStyle, graphicScale, theme, accent,
                opacity, resetMode, displayMode, metricMode, showTitle, showPlan,
                showUpdated, showRefresh, showResetCredits, showResetAction,
                showPercentSymbol, visibleMeters, style);
    }

    public static WidgetOptions defaults() {
        return new WidgetOptions(STYLE_AUTO, DENSITY_AUTO, SURFACE_ONE_UI, GRAPHIC_AUTO,
                THEME_SYSTEM, ACCENT_BLUE, DEFAULT_OPACITY, RESET_HIDDEN, DISPLAY_REMAINING,
                METRIC_BOTH, false, false, false, false, false, false)
                .withVisibleMeters(WidgetMeters.serialize(WidgetMeters.defaultVisible()));
    }

    /** Nearest allowed opacity when background is on; {@code 0} stays fully off. */
    public static int snapOpacity(int opacity) {
        if (opacity <= 0) {
            return 0;
        }
        int best = DEFAULT_OPACITY;
        int distance = Integer.MAX_VALUE;
        for (int value : OPACITY_LEVELS) {
            int candidate = Math.abs(value - opacity);
            // Prefer the stronger fill when two levels are equidistant (e.g. 94 → 100).
            if (candidate < distance || (candidate == distance && value > best)) {
                best = value;
                distance = candidate;
            }
        }
        return best;
    }

    /** Slider index for a stored opacity; background-off restores the default tick. */
    public static int opacityIndex(int opacity) {
        int snapped = snapOpacity(opacity <= 0 ? DEFAULT_OPACITY : opacity);
        for (int i = 0; i < OPACITY_LEVELS.length; i++) {
            if (OPACITY_LEVELS[i] == snapped) {
                return i;
            }
        }
        return 1;
    }

    /** Effective auto / dials / bars preference used by the renderer. */
    public String layoutPreference() {
        return WidgetMeters.layoutPreference(layout);
    }

    /** Phone controls; reset credits are details of the next-reset meter. */
    public static List<String> availableMeterKeys() {
        return Arrays.asList(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY,
                WidgetMeters.NEXT_RESET, USAGE_CREDITS);
    }

    /** Migrates legacy reset-credit selections and removed model meters at the phone boundary. */
    public String effectiveVisibleMeters() {
        List<String> keys = new ArrayList<>();
        for (String key : WidgetMeters.parse(WidgetMeters.effectiveVisibleCsv(visibleMeters,
                metricMode))) {
            String migrated = WidgetMeters.RESET_CREDITS.equals(key) ? WidgetMeters.NEXT_RESET : key;
            if (!keys.contains(migrated)) {
                keys.add(migrated);
            }
        }
        return WidgetMeters.serialize(WidgetMeters.resolveVisibleForWidget(
                WidgetMeters.serialize(keys), availableMeterKeys(), metricMode));
    }

    public boolean showsFiveHour() {
        return WidgetMeters.contains(
                WidgetMeters.parse(effectiveVisibleMeters()), WidgetMeters.FIVE_HOUR);
    }

    public boolean showsWeekly() {
        return WidgetMeters.contains(
                WidgetMeters.parse(effectiveVisibleMeters()), WidgetMeters.WEEKLY);
    }

    public boolean singleMetric() {
        return WidgetMeters.resolvedSingleUsageMetric(effectiveVisibleMeters(),
                availableMeterKeys(), metricMode);
    }

    public static String normalizeStyle(String style) {
        if (LAYOUT_DETAILED.equals(style)) {
            return STYLE_BARS;
        }
        if (LAYOUT_COMPACT.equals(style)) {
            return STYLE_MINIMAL;
        }
        // Keep rings/minimal as stored values for transfer round-trips; layoutPreference()
        // maps them to adaptive auto at render time.
        return oneOf(style, STYLE_AUTO, STYLE_BARS, STYLE_RINGS, STYLE_DIALS, STYLE_MINIMAL)
                ? style : STYLE_AUTO;
    }

    public static String normalizeTapAction(String value) {
        if (TAP_REFRESH.equals(value) || TAP_USE_RESET.equals(value)) {
            return value;
        }
        return TAP_OPEN_APP;
    }

    /**
     * Accepts known stored opacities and snaps them to One UI's three fill strengths (or fully
     * off) so saved widgets migrate cleanly; anything else becomes the default.
     */
    private static int normalizeOpacity(int opacity) {
        for (int known : KNOWN_OPACITIES) {
            if (known == opacity) {
                return opacity > 0 ? snapOpacity(opacity) : opacity;
            }
        }
        return DEFAULT_OPACITY;
    }

    private static boolean validAccent(String accent) {
        return oneOf(accent, ACCENT_APP, ACCENT_MINT, ACCENT_BLUE, ACCENT_AMBER,
                ACCENT_VIOLET, ACCENT_ROSE, ACCENT_CYAN, ACCENT_LIME, ACCENT_MONO);
    }

    private static boolean oneOf(String value, String... options) {
        if (value == null) {
            return false;
        }
        for (String option : options) {
            if (option.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
