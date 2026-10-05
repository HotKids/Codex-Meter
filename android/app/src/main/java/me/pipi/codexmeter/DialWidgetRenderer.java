package me.pipi.codexmeter;

import dev.bennett.codexmeter.NowBarCopy;
import dev.bennett.codexmeter.WidgetMeters;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;
import java.util.List;
import java.util.Locale;

/** Two-column, one-row dials using upstream arcs and the Clear system palette. */
final class DialWidgetRenderer {
    private static final int[] DIAL_COLORS = {
            R.color.widget_material_track, R.color.widget_material_fill};
    private static final int[][] RING_ARC_LAYERS = {
            {R.id.primary_samsung_track, R.id.primary_samsung_fill},
            {R.id.secondary_samsung_track, R.id.secondary_samsung_fill}};
    private static final int[] RING_VALUES = {
            R.id.primary_samsung_value, R.id.secondary_samsung_value};
    private static final int[] RING_ICONS = {
            R.id.primary_samsung_icon, R.id.secondary_samsung_icon};
    private static final int[] RING_SECTIONS = {R.id.primary_section, R.id.secondary_section};

    private DialWidgetRenderer() {
    }

    static RemoteViews build(Context context, int appWidgetId, WidgetOptions options,
            List<String> keys, UsageCardState state) {
        return build(context, appWidgetId, options, keys, state, 0f, 0f);
    }

    static RemoteViews build(Context context, int appWidgetId, WidgetOptions options,
            List<String> keys, UsageCardState state, float widthDp, float heightDp) {
        boolean oneUi = widthDp > 0f && heightDp > 0f
                && OneUiWidgetAppearance.isStockLauncher(context);
        RemoteViews views = new RemoteViews(context.getPackageName(),
                oneUi ? R.layout.widget_oneui_rings : R.layout.widget_rings);
        applyBackground(views, options);
        Context english = english(context);
        boolean classic = OneUiWidgetAppearance.classicPalette(options.colorStyle);
        for (int index = 0; index < RING_ARC_LAYERS.length; index++) {
            boolean shown = index < keys.size();
            views.setViewVisibility(RING_SECTIONS[index], shown ? View.VISIBLE : View.GONE);
            if (!shown) {
                continue;
            }
            String key = keys.get(index);
            WidgetMeter meter = new WidgetMeter(english, key, options, state);
            // VectorDrawable does not trim its path when ProgressBar changes the level.
            for (int layer = 0; layer < RING_ARC_LAYERS[index].length; layer++) {
                int viewId = RING_ARC_LAYERS[index][layer];
                int fillColor = WidgetUsageColors.color(meter.window,
                        WidgetMeters.FIVE_HOUR.equals(key) || WidgetMeters.WEEKLY.equals(key));
                boolean coloredFill = classic && layer != 0;
                int end = context.getColor(fillColor);
                int start = fillColor == R.color.widget_classic_progress_normal
                        ? context.getColor(R.color.widget_classic_progress_start) : end;
                views.setImageViewBitmap(viewId, coloredFill
                        ? WidgetGraphics.classicDialArc(meter.progress, oneUi, start, end)
                        : WidgetGraphics.twoDialArc(layer == 0 ? 100 : meter.progress, oneUi));
                views.setColorStateList(viewId, "setImageTintList", null);
                if (layer == 0) {
                    WidgetGraphics.tintTrack(views, viewId);
                } else if (coloredFill) {
                    views.setInt(viewId, "setColorFilter", Color.TRANSPARENT);
                } else {
                    views.setColor(viewId, "setColorFilter", DIAL_COLORS[layer]);
                }
            }
            String value = WidgetMeters.NEXT_RESET.equals(key) && meter.resetAtMillis > state.nowMillis
                    ? NowBarCopy.compactDuration(meter.resetAtMillis - state.nowMillis) : meter.value;
            views.setTextViewText(RING_VALUES[index], value);
            views.setImageViewResource(RING_ICONS[index],
                    WidgetMeters.FIVE_HOUR.equals(key)
                            ? (oneUi ? R.drawable.ic_oui_time : R.drawable.widget_dial_icon_session)
                            : WidgetMeters.WEEKLY.equals(key)
                                    ? (oneUi ? R.drawable.ic_oui_calendar_week : R.drawable.widget_dial_icon_weekly)
                                    : (oneUi ? R.drawable.ic_oui_alarm : R.drawable.widget_dial_icon_reset));
            if (oneUi) {
                OneUiWidgetAppearance.sizeDial(context, views, index,
                        Math.min(keys.size(), RING_SECTIONS.length), value, widthDp, heightDp);
            }
            views.setContentDescription(RING_ICONS[index],
                    description(english, meter));
            // Resolved by the launcher, so wallpaper colours and night mode stay live.
            views.setColor(RING_VALUES[index], "setTextColor",
                    R.color.widget_material_text);
            views.setColor(RING_ICONS[index], "setColorFilter",
                    R.color.widget_material_text);
        }
        views.setOnClickPendingIntent(android.R.id.background,
                WidgetActions.openApp(context, appWidgetId));
        views.setOnClickPendingIntent(R.id.refresh_button,
                WidgetActions.refresh(context, appWidgetId));
        return views;
    }

    /** The dials are deliberately English-only; this context resolves their strings. */
    private static Context english(Context context) {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(Locale.ENGLISH);
        return context.createConfigurationContext(config);
    }

    /** Spoken label of one dial: the window title and its value, in English. */
    private static String description(Context context, WidgetMeter meter) {
        return context.getString(R.string.widget_dial_description,
                meter.title, meter.value);
    }

    private static void applyBackground(RemoteViews views, WidgetOptions options) {
        views.setViewVisibility(R.id.dial_surface, options.opacity <= 0 ? View.GONE : View.VISIBLE);
        if (options.opacity <= 0) {
            return;
        }
        views.setColor(R.id.dial_surface, "setColorFilter", R.color.widget_material_surface);
        views.setInt(R.id.dial_surface, "setImageAlpha", Math.round(options.opacity * 2.55f));
    }
}
