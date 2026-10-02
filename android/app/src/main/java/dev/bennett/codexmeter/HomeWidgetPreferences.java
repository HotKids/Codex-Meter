package dev.bennett.codexmeter;

import android.content.SharedPreferences;
import java.util.List;

/** One persistence and migration path for editor, launcher, restore, and imported defaults. */
final class HomeWidgetPreferences {
    static final int SCHEMA_VERSION = 4;
    private final SharedPreferences preferences;

    HomeWidgetPreferences(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    WidgetOptions load(int widgetId) {
        WidgetOptions fallback = widgetId == 0 ? WidgetOptions.defaults() : load(0);
        String prefix = prefix(widgetId);
        if (!hasOptions(widgetId)) return fallback;
        boolean migrate = preferences.getInt(prefix + "schema_version", 0) < SCHEMA_VERSION;
        WidgetOptions options = new WidgetOptions(
                preferences.getString(prefix + "style",
                        preferences.getString(prefix + "layout",
                                migrate ? WidgetOptions.STYLE_AUTO : fallback.layout)),
                WidgetOptions.DENSITY_AUTO, WidgetOptions.SURFACE_ONE_UI, "auto",
                preferences.getString(prefix + "theme", fallback.theme),
                preferences.getString(prefix + "accent", fallback.accent),
                preferences.getInt(prefix + "opacity", fallback.opacity),
                WidgetOptions.RESET_HIDDEN,
                preferences.getString(prefix + "display_mode", fallback.displayMode),
                preferences.getString(prefix + "metric_mode", fallback.metricMode),
                false, false, false, false, false, false)
                .withPercentSymbol(preferences.getBoolean(prefix + "show_percent_symbol",
                        fallback.showPercentSymbol))
                .withReferenceStyle(preferences.getString(prefix + "reference_style",
                        fallback.referenceStyle))
                .withVisibleMeters(preferences.getString(prefix + "visible_meters", ""));
        if (migrate) {
            // Existing classic widgets must also reach the new renderer after an APK update.
            options = normalize(options.withLayout(WidgetOptions.STYLE_CARDS)
                    .withVisibleMeters(options.effectiveVisibleMeters()));
        }
        String pending = preferences.getString(prefix + "legacy_window_keys", "");
        if (migrate && !preferences.contains(prefix + "legacy_window_keys")) {
            java.util.ArrayList<String> legacy = new java.util.ArrayList<>();
            for (String key : WidgetMeters.parse(options.visibleMeters)) {
                if (WidgetMeters.isLimitKey(key)) legacy.add(key);
            }
            pending = WidgetMeters.serialize(legacy);
        }
        if (migrate || !pending.isEmpty()) {
            QuotaCardOptions.LegacySelectionMigration result = QuotaCardOptions.migrateLegacySelection(
                    options.visibleMeters, pending, cachedSnapshot());
            options = options.withVisibleMeters(result.selection);
            save(widgetId, options);
            preferences.edit().putString(prefix + "legacy_window_keys", result.remainingPending).apply();
        }
        return normalize(options);
    }

    boolean hasOptions(int widgetId) {
        String prefix = prefix(widgetId);
        return preferences.contains(prefix + "style") || preferences.contains(prefix + "layout")
                || preferences.contains(prefix + "visible_meters")
                || preferences.contains(prefix + "metric_mode");
    }

    void save(int widgetId, WidgetOptions options) {
        options = normalize(options);
        String prefix = prefix(widgetId);
        preferences.edit()
                .putInt(prefix + "schema_version", SCHEMA_VERSION)
                .putString(prefix + "style", options.layout)
                .putString(prefix + "layout", options.layout)
                .putString(prefix + "density", options.density)
                .putString(prefix + "surface_style", options.surfaceStyle)
                .putString(prefix + "graphic_scale", options.graphicScale)
                .putString(prefix + "theme", options.theme)
                .putString(prefix + "accent", options.accent)
                .putInt(prefix + "opacity", options.opacity)
                .putString(prefix + "reset_mode", options.resetMode)
                .putString(prefix + "display_mode", options.displayMode)
                .putString(prefix + "metric_mode", metricMode(options.effectiveVisibleMeters()))
                .putString(prefix + "visible_meters", options.visibleMeters)
                .putBoolean(prefix + "show_percent_symbol", options.showPercentSymbol)
                .putString(prefix + "reference_style", options.referenceStyle)
                .putString(prefix + "legacy_window_keys", retainedLegacyKeys(
                        preferences.getString(prefix + "legacy_window_keys", ""), options.visibleMeters))
                .apply();
    }

    void delete(int widgetId) {
        String prefix = prefix(widgetId);
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(prefix)) editor.remove(key);
        }
        editor.apply();
    }

    private static String prefix(int widgetId) {
        return widgetId == 0 ? "default_" : "widget_" + widgetId + "_";
    }

    private UsageSnapshot cachedSnapshot() {
        try {
            String json = preferences.getString("last_snapshot", null);
            return json == null ? null : UsageSnapshot.fromJson(new org.json.JSONObject(json));
        } catch (Exception ignored) {
            return null;
        }
    }

    static String retainedLegacyKeys(String pending, String selection) {
        java.util.ArrayList<String> retained = new java.util.ArrayList<>();
        List<String> selected = WidgetMeters.parse(selection);
        for (String key : WidgetMeters.parse(pending)) if (selected.contains(key)) retained.add(key);
        return WidgetMeters.serialize(retained);
    }

    private static WidgetOptions normalize(WidgetOptions options) {
        WidgetOptions normalized = new WidgetOptions(WidgetOptions.STYLE_CARDS, WidgetOptions.DENSITY_AUTO,
                WidgetOptions.SURFACE_ONE_UI, "auto", options.theme, options.accent,
                options.opacity, WidgetOptions.RESET_HIDDEN, options.displayMode, options.metricMode,
                false, false, false, false, false, false)
                .withPercentSymbol(options.showPercentSymbol)
                .withReferenceStyle(options.referenceStyle);
        return normalized.withVisibleMeters(WidgetMeters.serialize(
                QuotaCardOptions.resolve(options.effectiveVisibleMeters())));
    }

    private static String metricMode(String visible) {
        List<String> keys = WidgetMeters.parse(visible);
        boolean five = keys.contains(WidgetMeters.FIVE_HOUR);
        boolean weekly = keys.contains(WidgetMeters.WEEKLY);
        return five == weekly ? WidgetOptions.METRIC_BOTH
                : five ? WidgetOptions.METRIC_FIVE_HOUR : WidgetOptions.METRIC_WEEKLY;
    }
}
