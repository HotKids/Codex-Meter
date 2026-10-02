package dev.bennett.codexmeter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;
import java.util.List;

/**
 * One-row home widgets keep upstream Codex Meter's One UI dials: two arcs on a 2x1 cell and four
 * compact dials on a wide 4x1 cell, each showing the remaining (or used) percentage of one
 * selected window.
 */
final class DialWidgetRenderer {
    /** Width (dp) from which a one-row widget shows four dials instead of two. */
    static final float FOUR_DIAL_MIN_WIDTH_DP = 250f;

    private static final int[] FOUR_DIAL_GRAPHICS = {
            R.id.primary_four_graphic, R.id.secondary_four_graphic,
            R.id.reset_time_four_graphic, R.id.reset_count_four_graphic};
    private static final int[] FOUR_DIAL_SECTIONS = {
            R.id.primary_section, R.id.secondary_section, 0, 0};
    private static final int[] RING_PROGRESS = {
            R.id.primary_samsung_progress, R.id.secondary_samsung_progress};
    private static final int[] RING_VALUES = {
            R.id.primary_samsung_value, R.id.secondary_samsung_value};
    private static final int[] RING_ICONS = {
            R.id.primary_samsung_icon, R.id.secondary_samsung_icon};
    private static final int[] RING_SECTIONS = {R.id.primary_section, R.id.secondary_section};

    private DialWidgetRenderer() {
    }

    static RemoteViews build(Context context, int appWidgetId, WidgetOptions options,
            List<String> keys, UsageCardState state, float widthDp) {
        boolean four = widthDp >= FOUR_DIAL_MIN_WIDTH_DP;
        RemoteViews views = new RemoteViews(context.getPackageName(),
                four ? R.layout.widget_rings_four : R.layout.widget_rings);
        boolean dark = isDark(context, options);
        applyBackground(context, views, options, dark);
        int accent = WidgetGraphics.accentColor(context, options.accent, dark);
        int track = WidgetGraphics.trackColor(dark);
        int text = WidgetGraphics.mainTextColor(dark);
        if (four) {
            for (int index = 0; index < FOUR_DIAL_GRAPHICS.length; index++) {
                boolean shown = index < keys.size();
                views.setViewVisibility(FOUR_DIAL_GRAPHICS[index],
                        shown ? View.VISIBLE : View.GONE);
                if (FOUR_DIAL_SECTIONS[index] != 0) {
                    views.setViewVisibility(FOUR_DIAL_SECTIONS[index],
                            shown ? View.VISIBLE : View.GONE);
                }
                if (shown) {
                    String key = keys.get(index);
                    int value = value(key, state, options);
                    views.setImageViewBitmap(FOUR_DIAL_GRAPHICS[index],
                            WidgetGraphics.compactDial(context, value, icon(key), accent, track,
                                    text, valueText(value, options), 1.0f));
                }
            }
        } else {
            for (int index = 0; index < RING_PROGRESS.length; index++) {
                boolean shown = index < keys.size();
                views.setViewVisibility(RING_SECTIONS[index], shown ? View.VISIBLE : View.GONE);
                if (!shown) {
                    continue;
                }
                String key = keys.get(index);
                int value = value(key, state, options);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    views.setColorStateList(RING_PROGRESS[index], "setProgressTintList",
                            ColorStateList.valueOf(accent));
                    views.setColorStateList(RING_PROGRESS[index],
                            "setProgressBackgroundTintList", ColorStateList.valueOf(track));
                }
                views.setProgressBar(RING_PROGRESS[index], 100, Math.max(0, value), false);
                views.setTextViewText(RING_VALUES[index], valueText(value, options));
                views.setTextColor(RING_VALUES[index], text);
                views.setImageViewResource(RING_ICONS[index], icon(key));
                views.setInt(RING_ICONS[index], "setColorFilter", text);
            }
        }
        views.setOnClickPendingIntent(android.R.id.background,
                WidgetActions.openApp(context, appWidgetId));
        views.setOnClickPendingIntent(R.id.refresh_button,
                WidgetActions.refresh(context, appWidgetId));
        return views;
    }

    /** Remaining (or used) percentage of the window, or -1 when it is missing or signed out. */
    private static int value(String key, UsageCardState state, WidgetOptions options) {
        UsageWindow window = UsageCardWindows.window(key, state.snapshot);
        if (window == null) {
            return -1;
        }
        return WidgetOptions.DISPLAY_USED.equals(options.displayMode)
                ? window.usedPercent : window.remainingPercent();
    }

    private static String valueText(int value, WidgetOptions options) {
        if (value < 0) {
            return UsageCardFormat.MISSING;
        }
        return value + (options.showPercentSymbol ? "%" : "");
    }

    /** Clock for five-hour cadences, calendar for weekly and longer ones. */
    private static int icon(String key) {
        boolean fiveHour = WidgetMeters.FIVE_HOUR.equals(key)
                || (WidgetMeters.isLimitKey(key) && WidgetMeters.isLimitPrimary(key));
        return fiveHour ? R.drawable.ic_oui_time : R.drawable.ic_oui_calendar_week;
    }

    private static boolean isDark(Context context, WidgetOptions options) {
        if (WidgetOptions.THEME_DARK.equals(options.theme)) {
            return true;
        }
        return !WidgetOptions.THEME_LIGHT.equals(options.theme)
                && (context.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static void applyBackground(Context context, RemoteViews views,
            WidgetOptions options, boolean dark) {
        if (WidgetOptions.SURFACE_ONE_UI.equals(options.surfaceStyle) && isSamsung()) {
            int alpha = Math.round(Math.max(0, Math.min(100, options.opacity)) * 2.55f);
            views.setInt(android.R.id.background, "setBackgroundColor",
                    dark ? Color.argb(alpha, 0, 0, 0) : Color.argb(alpha, 255, 255, 255));
            return;
        }
        views.setInt(android.R.id.background, "setBackgroundResource",
                backgroundResource(dark, options.opacity, options.surfaceStyle, isSamsung()));
    }

    /** Upstream's background drawables per surface style, theme and opacity step. */
    private static int backgroundResource(boolean dark, int opacity, String surface,
            boolean samsung) {
        if (opacity == 0) {
            return R.drawable.widget_bg_transparent;
        }
        boolean oneUi = WidgetOptions.SURFACE_ONE_UI.equals(surface);
        if (oneUi && samsung) {
            return dark
                    ? pick(opacity, R.drawable.widget_bg_samsung_dark_56,
                            R.drawable.widget_bg_samsung_dark_72,
                            R.drawable.widget_bg_samsung_dark_88,
                            R.drawable.widget_bg_samsung_dark_100)
                    : pick(opacity, R.drawable.widget_bg_samsung_light_56,
                            R.drawable.widget_bg_samsung_light_72,
                            R.drawable.widget_bg_samsung_light_88,
                            R.drawable.widget_bg_samsung_light_100);
        }
        if (oneUi) {
            return dark
                    ? pick(opacity, R.drawable.widget_bg_oneui_dark_56,
                            R.drawable.widget_bg_oneui_dark_72,
                            R.drawable.widget_bg_oneui_dark_88,
                            R.drawable.widget_bg_oneui_dark_100)
                    : pick(opacity, R.drawable.widget_bg_oneui_light_56,
                            R.drawable.widget_bg_oneui_light_72,
                            R.drawable.widget_bg_oneui_light_88,
                            R.drawable.widget_bg_oneui_light_100);
        }
        return dark
                ? pick(opacity, R.drawable.widget_bg_dark_56, R.drawable.widget_bg_dark_72,
                        R.drawable.widget_bg_dark_88, R.drawable.widget_bg_dark_100)
                : pick(opacity, R.drawable.widget_bg_light_56, R.drawable.widget_bg_light_72,
                        R.drawable.widget_bg_light_88, R.drawable.widget_bg_light_100);
    }

    /** Opacity steps 56/72/100 map to their drawables; anything else uses the 88% default. */
    private static int pick(int opacity, int bg56, int bg72, int bg88, int bg100) {
        switch (opacity) {
            case 56:
                return bg56;
            case 72:
                return bg72;
            case 100:
                return bg100;
            default:
                return bg88;
        }
    }

    private static boolean isSamsung() {
        return "samsung".equalsIgnoreCase(Build.MANUFACTURER)
                || "samsung".equalsIgnoreCase(Build.BRAND);
    }
}
