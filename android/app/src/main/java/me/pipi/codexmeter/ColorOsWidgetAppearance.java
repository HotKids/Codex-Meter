package me.pipi.codexmeter;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.RemoteViews;

/** Adds ColorOS corners to the One UI background layer and shared widget content. */
final class ColorOsWidgetAppearance {
    private static final String TAG = "CodexMeterWidget";
    private static final int[] PANELS = {R.id.md_panel_bg_0, R.id.md_panel_bg_1,
            R.id.md_panel_bg_2, R.id.md_panel_bg_3};
    private static boolean previewAttempted;

    private ColorOsWidgetAppearance() {
    }

    static void apply(Context context, RemoteViews views, boolean dial, int opacity) {
        // Generated-preview APIs do not supply the picker's eventual content bounds.
        apply(context, views, dial, opacity, 0f, 0f);
    }

    static void apply(Context context, RemoteViews views, boolean dial, int opacity,
            float widthDp, float heightDp) {
        apply(context, views, dial, opacity, widthDp, heightDp, WidgetOptions.COLOR_AUTO);
    }

    static void apply(Context context, RemoteViews views, boolean dial, int opacity,
            float widthDp, float heightDp, String colorStyle) {
        OneUiWidgetAppearance.applySurface(context, views, dial, opacity, colorStyle);
        boolean colorOs = isStockLauncher(context);
        int surface = dial ? R.id.dial_surface : R.id.md_surface;
        geometry(context, views, surface, colorOs, dial, widthDp, heightDp);
        if (!colorOs) return;
        views.setImageViewResource(surface, dial ? R.drawable.widget_coloros_dial_surface
                : R.drawable.widget_coloros_card_surface);
        if (!dial) {
            for (int panel : PANELS) {
                views.setImageViewResource(panel, R.drawable.widget_coloros_panel);
            }
        }
    }

    private static void geometry(Context context, RemoteViews views, int surface, boolean colorOs,
            boolean dial, float widthDp, float heightDp) {
        // Reapply may reuse views after a host, orientation, or icon-size change.
        views.setViewLayoutHeight(android.R.id.background, ViewGroup.LayoutParams.MATCH_PARENT,
                TypedValue.COMPLEX_UNIT_PX);
        views.setViewLayoutWidth(surface, ViewGroup.LayoutParams.MATCH_PARENT,
                TypedValue.COMPLEX_UNIT_PX);
        views.setViewLayoutHeight(surface, ViewGroup.LayoutParams.MATCH_PARENT,
                TypedValue.COMPLEX_UNIT_PX);
        views.setFloat(surface, "setPivotX", 0f);
        views.setFloat(surface, "setPivotY", 0f);
        views.setFloat(surface, "setScaleX", 1f);
        views.setFloat(surface, "setScaleY", 1f);
        if (!dial) {
            views.setViewLayoutWidth(R.id.md_refresh_corner_anchor, cardCornerRadiusPx(context),
                    TypedValue.COMPLEX_UNIT_PX);
        }
        if (!colorOs || !positive(widthDp) || !positive(heightDp)) {
            return;
        }
        float iconDp = iconSizeDp(context);
        float density = context.getResources().getDisplayMetrics().density;
        if (!positive(iconDp) || Math.round(iconDp * density) < 1) {
            return;
        }
        if (dial) {
            // Native portrait cards bound their visible height by the final launcher icon size.
            if (context.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_PORTRAIT
                    && Math.round(Math.min(heightDp, iconDp) * density) >= 1) {
                views.setViewLayoutHeight(android.R.id.background, Math.min(heightDp, iconDp),
                        TypedValue.COMPLEX_UNIT_DIP);
            }
            return;
        }
        float baseRadius = context.getResources().getDimension(R.dimen.widget_coloros_card_radius);
        float radiusPx = cardCornerRadiusPx(context);
        float scale = radiusPx / baseRadius;
        if (!positive(scale) || widthDp / scale * density < 1f
                || heightDp / scale * density < 1f) {
            return;
        }
        // Inverse layout scaling keeps the visible bounds while changing only the outer curve.
        views.setViewLayoutWidth(surface, widthDp / scale, TypedValue.COMPLEX_UNIT_DIP);
        views.setViewLayoutHeight(surface, heightDp / scale, TypedValue.COMPLEX_UNIT_DIP);
        views.setFloat(surface, "setScaleX", scale);
        views.setFloat(surface, "setScaleY", scale);
    }

    private static float iconSizeDp(Context context) {
        try {
            // ColorOS publishes the final post-theme and post-layout value in dp.
            String value = Settings.Secure.getString(context.getContentResolver(), "layout_icon_size");
            return value == null ? 0f : Float.parseFloat(value);
        } catch (SecurityException | NumberFormatException exception) {
            return 0f;
        }
    }

    static float cardCornerRadiusPx(Context context) {
        if (!isStockLauncher(context)) {
            return context.getResources().getDimension(R.dimen.widget_card_corner_radius);
        }
        float radius = context.getResources().getDimension(R.dimen.widget_coloros_card_radius);
        float iconDp = iconSizeDp(context);
        float density = context.getResources().getDisplayMetrics().density;
        return positive(iconDp) && Math.round(iconDp * density) >= 1
                ? (int) (Math.round(iconDp * density) / (density * 56f) * Math.round(radius))
                : radius;
    }

    private static boolean positive(float value) {
        return Float.isFinite(value) && value > 0f;
    }

    static boolean isStockLauncher(Context context) {
        if (!"oppo".equalsIgnoreCase(Build.MANUFACTURER)) {
            return false;
        }
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo launcher = context.getPackageManager().resolveActivity(home,
                PackageManager.MATCH_DEFAULT_ONLY);
        return launcher != null && launcher.activityInfo != null
                && "com.android.launcher".equals(launcher.activityInfo.packageName);
    }

    static synchronized void publishPreviews(Context context, AppWidgetManager manager) {
        boolean colorOs = isStockLauncher(context);
        reconcileDialAvailability(context, manager, colorOs);
        if (Build.VERSION.SDK_INT < 35) {
            return;
        }
        boolean refresh = colorOs && !previewAttempted;
        if (!colorOs) previewAttempted = false;
        boolean published = true;
        for (Class<?> provider : new Class<?>[] {CodexUsageWidget.class, CodexDialWidget.class}) {
            try {
                boolean dial = provider == CodexDialWidget.class;
                int layout = dial ? R.layout.widget_coloros_dial_preview
                        : R.layout.widget_coloros_card_preview;
                ComponentName component = new ComponentName(context, provider);
                RemoteViews current = manager.getWidgetPreview(component, Process.myUserHandle(),
                        AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
                if (!colorOs) {
                    if (current != null && (current.getLayoutId() == layout
                            || !dial && current.getLayoutId() == R.layout.widget_material_preview)) {
                        manager.removeWidgetPreview(component,
                                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
                    }
                    continue;
                }
                if (!refresh && current != null && current.getLayoutId() == layout) {
                    continue;
                }
                RemoteViews preview = new RemoteViews(context.getPackageName(), layout);
                apply(context, preview, dial, 100);
                if (!manager.setWidgetPreview(component,
                        AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, preview)) {
                    published = false;
                    Log.w(TAG, "Widget preview publication rate limited");
                }
            } catch (RuntimeException exception) {
                published = false;
                Log.w(TAG, "Widget preview publication failed: "
                        + exception.getClass().getSimpleName());
            }
        }
        if (colorOs) previewAttempted = published;
    }

    private static void reconcileDialAvailability(Context context, AppWidgetManager manager,
            boolean colorOs) {
        try {
            ComponentName component = new ComponentName(context, CodexDialWidget.class);
            java.util.List<AppWidgetProviderInfo> providers = manager.getInstalledProvidersForPackage(
                    context.getPackageName(), Process.myUserHandle());
            if (providers == null) {
                return;
            }
            for (AppWidgetProviderInfo provider : providers) {
                if (!component.equals(provider.provider)) {
                    continue;
                }
                boolean hidden = (provider.widgetFeatures
                        & AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER) != 0;
                if (hidden != colorOs) {
                    // Keep HOME eligibility: removing it can make ColorOS delete existing widgets.
                    manager.updateAppWidgetProviderInfo(component, colorOs
                            ? BuildConfig.APPLICATION_ID + ".COLOROS_HIDDEN_DIAL_PROVIDER_INFO"
                            : null);
                }
                return;
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Widget picker availability update failed: "
                    + exception.getClass().getSimpleName());
        }
    }
}
