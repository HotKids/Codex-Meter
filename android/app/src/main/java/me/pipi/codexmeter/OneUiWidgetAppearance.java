package me.pipi.codexmeter;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.Color;
import android.os.Build;
import android.os.Process;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import java.util.List;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsageWindow;
import dev.bennett.codexmeter.WidgetMeters;

/** One UI backgrounds and dial geometry layered over the shared widget renderer. */
final class OneUiWidgetAppearance {
    // Calendar's saved 35% transparency becomes 34% after its whole-percent conversion.
    private static final int[] OPACITY_LEVELS = {30, 66, 100};
    private static final int[] PANELS = {R.id.md_panel_bg_0, R.id.md_panel_bg_1,
            R.id.md_panel_bg_2, R.id.md_panel_bg_3};
    private static final int[] BARS = {R.id.md_bar_0, R.id.md_bar_1,
            R.id.md_bar_2, R.id.md_bar_3};
    private static final int[][] ARCS = {
            {R.id.primary_samsung_track, R.id.primary_samsung_fill},
            {R.id.secondary_samsung_track, R.id.secondary_samsung_fill}};
    private static final int[] ICONS = {R.id.primary_samsung_icon, R.id.secondary_samsung_icon};
    private static final int[] VALUES = {R.id.primary_samsung_value, R.id.secondary_samsung_value};
    private static boolean previewAttempted;

    private OneUiWidgetAppearance() {
    }

    static boolean isStockLauncher(Context context) {
        if (!"samsung".equalsIgnoreCase(Build.MANUFACTURER)) {
            return false;
        }
        ResolveInfo home = context.getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY);
        return home != null && home.activityInfo != null
                && "com.sec.android.app.launcher".equals(home.activityInfo.packageName);
    }

    static void applySurface(Context context, RemoteViews views, boolean dial, int opacity) {
        applySurface(context, views, dial, opacity, WidgetOptions.COLOR_AUTO);
    }

    static boolean classicPalette(String colorStyle) {
        if (WidgetOptions.COLOR_CLASSIC.equals(colorStyle)) return true;
        if (WidgetOptions.COLOR_NATIVE.equals(colorStyle)) return false;
        return !("Google".equalsIgnoreCase(Build.MANUFACTURER)
                && Build.MODEL != null && Build.MODEL.startsWith("Pixel"));
    }

    static void applySurface(Context context, RemoteViews views, boolean dial, int opacity,
            String colorStyle) {
        boolean stock = isStockLauncher(context);
        boolean classic = classicPalette(colorStyle);
        boolean colorOs = ColorOsWidgetAppearance.isStockLauncher(context);
        int classicSurface = colorOs ? R.color.widget_coloros_classic_surface
                : R.color.widget_oneui_surface;
        int classicPanel = colorOs ? R.color.widget_coloros_classic_panel
                : R.color.widget_oneui_panel;
        int resolvedOpacity = resolveOpacity(context, opacity);
        int surface = dial ? R.id.dial_surface : R.id.md_surface;
        views.setImageViewResource(surface, dial && stock ? R.drawable.widget_oneui_dial_surface
                : dial ? R.drawable.widget_dial_surface : R.drawable.widget_card_surface);
        views.setColor(surface, "setColorFilter", classic ? classicSurface
                : R.color.widget_material_surface);
        views.setInt(surface, "setImageAlpha", Math.round(resolvedOpacity * 2.55f));
        if (!dial) {
            float progressAlpha = opacity > 0 ? resolvedOpacity / 100f : 1f;
            for (int index = 0; index < PANELS.length; index++) {
                int panel = PANELS[index];
                views.setImageViewResource(panel, R.drawable.widget_material_panel);
                // The launcher can provide its own dynamic palette when applying these resources.
                views.setColor(panel, "setColorFilter", classic ? classicPanel
                        : R.color.widget_material_panel);
                views.setInt(panel, "setImageAlpha", Math.round(resolvedOpacity * 2.55f));
                views.setFloat(BARS[index], "setAlpha", progressAlpha);
            }
        }
        int alpha = stock ? Math.round(resolvedOpacity * 2.55f) << 24 : 0;
        // One UI reads this ColorDrawable's alpha to enable its wallpaper blur.
        // ImageView alpha is invisible to the host and would paint the shell twice.
        if (stock && !classic && opacity > 0) {
            // The selector retains launcher-resolved colors and the host-visible shell alpha.
            int nativeSurface = resolvedOpacity == 30 ? R.color.widget_material_surface_low
                    : resolvedOpacity == 66 ? R.color.widget_material_surface_medium
                    : R.color.widget_material_surface;
            views.setColor(android.R.id.background, "setBackgroundColor", nativeSurface);
        } else {
            views.setColorInt(android.R.id.background, "setBackgroundColor",
                    stock ? alpha | (context.getColor(R.color.widget_oneui_surface_light)
                            & 0x00FFFFFF) : Color.TRANSPARENT,
                    stock ? alpha | (context.getColor(R.color.widget_oneui_surface_dark)
                            & 0x00FFFFFF) : Color.TRANSPARENT);
        }
        views.setViewVisibility(dial ? R.id.dial_surface : R.id.md_surface,
                stock || opacity <= 0 ? View.GONE : View.VISIBLE);
    }

    static int resolveOpacity(Context context, int opacity) {
        if (opacity <= 0) return 0;
        if (!isStockLauncher(context) && !ColorOsWidgetAppearance.isStockLauncher(context)) {
            return opacity;
        }
        // Stored ticks stay portable; only these hosts use the Calendar fill strengths.
        return OPACITY_LEVELS[WidgetOptions.opacityIndex(opacity)];
    }

    static void applyEditorSurface(Context context, RemoteViews views, boolean dial, int opacity) {
        if (!isStockLauncher(context)) return;
        // The editor has no launcher to paint or clip its shell.
        views.setInt(android.R.id.background, "setBackgroundColor", Color.TRANSPARENT);
        if (dial) {
            views.setImageViewResource(R.id.dial_surface, R.drawable.widget_oneui_dial_surface);
        }
        views.setInt(dial ? R.id.dial_surface : R.id.md_surface, "setImageAlpha",
                Math.round(resolveOpacity(context, opacity) * 2.55f));
        views.setViewVisibility(dial ? R.id.dial_surface : R.id.md_surface,
                opacity <= 0 ? View.GONE : View.VISIBLE);
    }

    static synchronized void publishPreview(Context context, AppWidgetManager manager) {
        if (Build.VERSION.SDK_INT < 35) return;
        try {
            int category = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN;
            if (!isStockLauncher(context)) {
                for (Class<?> type : new Class<?>[] {CodexDialWidget.class, CodexUsageWidget.class}) {
                    ComponentName provider = new ComponentName(context, type);
                    RemoteViews previous = manager.getWidgetPreview(provider,
                            Process.myUserHandle(), category);
                    int layout = type == CodexDialWidget.class ? R.layout.widget_oneui_rings
                            : R.layout.widget_material;
                    if (previous != null && (previous.getLayoutId() == layout
                            || (type == CodexUsageWidget.class
                                    && previous.getLayoutId() == R.layout.widget_material_refresh))) {
                        manager.removeWidgetPreview(provider, category);
                    }
                }
                previewAttempted = false;
                return;
            }
            if (previewAttempted) return;
            // Rebuild once per process so updated geometry replaces cached picker actions.
            boolean published = true;
            UsageSnapshot sample = new UsageSnapshot("plus", true, false,
                    new UsageWindow(24, 18000L, 0L, 0L),
                    new UsageWindow(25, 604800L, 0L, 0L), 0L);
            WidgetOptions options = new WidgetOptions("auto", "system", "app", 100,
                    "hidden", "remaining").withColorStyle(WidgetOptions.COLOR_CLASSIC);
            UsageCardState state = new UsageCardState(true, sample, null, "", 0L);
            UsageCardState dialState = new UsageCardState(true,
                    new UsageSnapshot("plus", true, false,
                            new UsageWindow(24, 18000L, 0L, 13500L), sample.weekly, 0L),
                    null, "", 0L);
            for (Class<?> type : new Class<?>[] {CodexDialWidget.class, CodexUsageWidget.class}) {
                boolean dial = type == CodexDialWidget.class;
                RemoteViews preview = dial ? DialWidgetRenderer.build(context,
                        AppWidgetManager.INVALID_APPWIDGET_ID, options,
                        List.of(WidgetMeters.FIVE_HOUR, WidgetMeters.NEXT_RESET),
                        dialState, 180f, 90f) : MaterialCardRenderer.build(context,
                        AppWidgetManager.INVALID_APPWIDGET_ID, options, WidgetMeters.defaultVisible(),
                        state, 170f, 170f);
                applySurface(context, preview, dial, options.opacity);
                if (!manager.setWidgetPreview(new ComponentName(context, type), category, preview)) {
                    published = false;
                    Log.w("CodexMeterWidget", "One UI preview publication rate limited");
                }
            }
            previewAttempted = published;
        } catch (RuntimeException exception) {
            Log.w("CodexMeterWidget", "One UI preview publication failed: "
                    + exception.getClass().getSimpleName());
        }
    }

    static void sizeDial(Context context, RemoteViews views, int index, int count, String value,
            float widthDp, float heightDp) {
        // ArcWidget Small uses Standard3/Sub2 padding and a height-based center column.
        float horizontal = heightDp * 0.12f;
        float vertical = heightDp * 0.06f;
        float density = context.getResources().getDisplayMetrics().density;
        views.setViewPadding(R.id.dial_content, Math.round(horizontal * density),
                Math.round(vertical * density), Math.round(horizontal * density),
                Math.round(vertical * density));
        float cellHeight = heightDp - vertical * 2f;
        float cellWidth = Math.max(1f, (widthDp - horizontal * 2f) / count);
        float arcSize = 0.8f * Math.min(cellWidth, cellHeight);
        for (int id : ARCS[index]) {
            size(views, id, arcSize, arcSize);
        }
        size(views, ICONS[index], cellHeight * 0.33f, cellHeight * 0.33f);
        views.setViewLayoutMargin(ICONS[index], RemoteViews.MARGIN_TOP, cellHeight * 0.33f,
                TypedValue.COMPLEX_UNIT_DIP);
        size(views, VALUES[index], cellWidth, cellHeight * 0.28f);
        views.setViewLayoutMargin(VALUES[index], RemoteViews.MARGIN_TOP, cellHeight * 0.66f,
                TypedValue.COMPLEX_UNIT_DIP);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTypeface(Typeface.create(Typeface.create("sec", Typeface.NORMAL), 500, false));
        text.setTextSize(heightDp * 0.21f * density);
        float measured = text.measureText(value);
        Paint.FontMetrics metrics = text.getFontMetrics();
        float lineHeight = (float) Math.ceil(metrics.descent) - (float) Math.floor(metrics.ascent);
        float fit = Math.min(1f, Math.min(cellWidth * density / Math.max(1f, measured),
                Math.max(1f, Math.round(cellHeight * 0.28f * density) - 1f) / lineHeight));
        views.setTextViewTextSize(VALUES[index], TypedValue.COMPLEX_UNIT_PX,
                text.getTextSize() * fit);
    }

    private static void size(RemoteViews views, int id, float width, float height) {
        views.setViewLayoutWidth(id, width, TypedValue.COMPLEX_UNIT_DIP);
        views.setViewLayoutHeight(id, height, TypedValue.COMPLEX_UNIT_DIP);
    }
}
