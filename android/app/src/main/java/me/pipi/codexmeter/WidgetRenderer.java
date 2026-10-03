package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.WidgetMeters;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.SizeF;
import android.widget.RemoteViews;
import androidx.annotation.RequiresApi;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders home-screen widgets. One-row cells keep upstream's One UI dials; every larger cell
 * shows the Clear card. On Android 12+ each size the launcher reports gets its own
 * layout, so resizing never waits for an app refresh.
 */
public final class WidgetRenderer {
    private static final String TAG = "CodexMeterWidget";
    /** Hosts shorter than this (dp) are one row tall and show dials. */
    static final float ONE_ROW_MAX_HEIGHT_DP = 130f;
    private static final String SAMSUNG_ROW_SPAN = "semAppWidgetRowSpan";
    private static final String SAMSUNG_COLUMN_SPAN = "semAppWidgetColumnSpan";
    private static final int MAX_RESPONSIVE_SIZES = 16;
    /** Size buckets for launchers that do not report their exact sizes (dp). */
    private static final SizeF[] FALLBACK_SIZES = {
            new SizeF(110f, 60f), new SizeF(250f, 60f), new SizeF(140f, 140f),
            new SizeF(170f, 190f), new SizeF(280f, 140f), new SizeF(330f, 190f)};
    private static final float DEFAULT_WIDTH_DP = 170f;
    private static final float DEFAULT_HEIGHT_DP = 170f;
    private static final int MAX_ERROR_LENGTH = 180;

    private WidgetRenderer() {
    }

    public static void updateAll(Context context) {
        if (context == null) {
            return;
        }
        Context app = context.getApplicationContext() == null
                ? context : context.getApplicationContext();
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(app);
            int[] ids = manager.getAppWidgetIds(new ComponentName(app, CodexUsageWidget.class));
            DiagnosticLog.info(app, "widget", "update_all_started",
                    "home_widget_count", ids.length,
                    "lock_widget_count", SamsungLockWidgetSupport.countAll(app));
            int[] dialIds = manager.getAppWidgetIds(new ComponentName(app, CodexDialWidget.class));
            int[] allIds = new int[ids.length + dialIds.length];
            System.arraycopy(ids, 0, allIds, 0, ids.length);
            System.arraycopy(dialIds, 0, allIds, ids.length, dialIds.length);
            for (int id : allIds) {
                update(app, manager, id);
            }
            SamsungLockWidgetSupport.updateAll(app);
            DiagnosticLog.info(app, "widget", "update_all_finished");
        } catch (RuntimeException exception) {
            DiagnosticLog.error(app, "widget", "update_all_failed", exception);
            Log.w(TAG, "Widget update failed: " + safeMessage(exception));
        }
    }

    public static void update(Context context, AppWidgetManager manager, int appWidgetId) {
        if (context == null || manager == null
                || appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return;
        }
        try {
            WidgetOptions options = AppPreferences.loadWidgetOptions(context, appWidgetId);
            Bundle host = manager.getAppWidgetOptions(appWidgetId);
            UsageCardState state = UsageCardState.load(context);
            RemoteViews views = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? buildResponsive(context, appWidgetId, options, state, host)
                    : build(context, appWidgetId, options, state, currentWidth(context, host),
                            currentHeight(context, host), host);
            manager.updateAppWidget(appWidgetId, views);
        } catch (RuntimeException exception) {
            DiagnosticLog.error(context, "widget", "render_failed", exception,
                    "widget_id", appWidgetId);
            Log.w(TAG, "Widget render failed: " + safeMessage(exception));
            try {
                manager.updateAppWidget(appWidgetId, buildFallback(context, appWidgetId));
            } catch (RuntimeException fallbackException) {
                DiagnosticLog.error(context, "widget", "fallback_render_failed",
                        fallbackException, "widget_id", appWidgetId);
            }
        }
    }

    /** Renders the widget for one size, as the editor preview and pre-12 hosts need. */
    static RemoteViews buildPreview(Context context, int appWidgetId, WidgetOptions options,
            float widthDp, float heightDp) {
        Bundle host = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId);
        return build(context, appWidgetId, options, UsageCardState.load(context), widthDp,
                heightDp, host);
    }

    static RemoteViews build(Context context, int appWidgetId, WidgetOptions options,
            UsageCardState state, float widthDp, float heightDp, Bundle host) {
        List<String> keys = selectedKeys(options, state.snapshot);
        if (oneRow(host, heightDp)) {
            keys.remove(WidgetOptions.USAGE_CREDITS);
            if (keys.isEmpty()) {
                keys.add(WidgetMeters.WEEKLY);
            }
            return DialWidgetRenderer.build(context, appWidgetId, options, keys, state);
        }
        return MaterialCardRenderer.build(context, appWidgetId, options, keys, state, widthDp,
                heightDp);
    }

    /** Disabled and unavailable phone meters remain configurable. */
    static List<String> selectedKeys(WidgetOptions options, UsageSnapshot snapshot) {
        return WidgetMeters.resolveVisibleForWidget(options.effectiveVisibleMeters(),
                WidgetOptions.availableMeterKeys(), options.metricMode);
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private static RemoteViews buildResponsive(Context context, int appWidgetId,
            WidgetOptions options, UsageCardState state, Bundle host) {
        Map<SizeF, RemoteViews> layouts = new LinkedHashMap<>();
        for (SizeF size : responsiveSizes(host)) {
            layouts.put(size, build(context, appWidgetId, options, state, size.getWidth(),
                    size.getHeight(), host));
        }
        return new RemoteViews(layouts);
    }

    @SuppressWarnings("deprecation") // The typed overload needs API 33; sizes exist from 31.
    private static List<SizeF> responsiveSizes(Bundle host) {
        List<SizeF> sizes = new ArrayList<>();
        ArrayList<SizeF> reported = host == null ? null
                : host.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
        if (reported != null) {
            for (SizeF size : reported) {
                if (size != null && size.getWidth() >= 1f && size.getHeight() >= 1f
                        && !sizes.contains(size) && sizes.size() < MAX_RESPONSIVE_SIZES) {
                    sizes.add(size);
                }
            }
        }
        if (sizes.isEmpty()) {
            for (SizeF size : FALLBACK_SIZES) {
                sizes.add(size);
            }
        }
        return sizes;
    }

    /**
     * One UI reports cell spans. A one-row placement keeps the dials even where its cells are
     * taller than {@link #ONE_ROW_MAX_HEIGHT_DP}.
     */
    static boolean oneRow(Bundle host, float heightDp) {
        int rows = host == null ? 0 : host.getInt(SAMSUNG_ROW_SPAN, 0);
        return rows > 0 ? rows == 1 : heightDp < ONE_ROW_MAX_HEIGHT_DP;
    }

    private static RemoteViews buildFallback(Context context, int appWidgetId) {
        UsageCardState signedOut = new UsageCardState(false, null, null, "",
                System.currentTimeMillis());
        return MaterialCardRenderer.build(context, appWidgetId,
                WidgetOptions.defaults(), WidgetMeters.defaultVisible(), signedOut, DEFAULT_WIDTH_DP,
                DEFAULT_HEIGHT_DP);
    }

    /** Portrait hosts are min width x max height; landscape hosts the reverse. */
    private static float currentWidth(Context context, Bundle host) {
        int min = option(host, AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int max = option(host, AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH);
        int width = landscape(context) ? firstPositive(max, min) : firstPositive(min, max);
        return width > 0 ? width : DEFAULT_WIDTH_DP;
    }

    private static float currentHeight(Context context, Bundle host) {
        int min = option(host, AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        int max = option(host, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        int height = landscape(context) ? firstPositive(min, max) : firstPositive(max, min);
        return height > 0 ? height : DEFAULT_HEIGHT_DP;
    }

    private static boolean landscape(Context context) {
        return context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    private static int option(Bundle host, String key) {
        return host == null ? 0 : host.getInt(key, 0);
    }

    private static int firstPositive(int preferred, int fallback) {
        return preferred > 0 ? preferred : fallback;
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > MAX_ERROR_LENGTH
                ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
