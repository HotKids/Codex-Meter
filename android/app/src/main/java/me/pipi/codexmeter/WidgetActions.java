package me.pipi.codexmeter;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Tap targets shared by every home-widget layout. */
final class WidgetActions {
    static final String ACTION_OPEN = AppConstants.action("WIDGET_OPEN");
    static final String EXTRA_DASHBOARD_SECTION = "dashboard_section";

    private static final int OPEN_REQUEST_BASE = 74000;
    private static final int REFRESH_REQUEST_BASE = 75000;
    private static final int FLAGS = PendingIntent.FLAG_UPDATE_CURRENT
            | PendingIntent.FLAG_IMMUTABLE;

    private WidgetActions() {
    }

    /** Opens the dashboard. */
    static PendingIntent openApp(Context context, int appWidgetId) {
        Intent intent = new Intent(context, MainActivity.class)
                .setAction(ACTION_OPEN)
                .setData(widgetUri(appWidgetId, "root-open"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, OPEN_REQUEST_BASE + appWidgetId, intent, FLAGS);
    }

    /** Opens the dashboard at the module displayed by a widget meter. */
    static PendingIntent openSection(Context context, int appWidgetId, String section) {
        if (section == null || section.isEmpty()) {
            return openApp(context, appWidgetId);
        }
        Intent intent = new Intent(context, MainActivity.class)
                .setAction(ACTION_OPEN)
                .setData(widgetUri(appWidgetId, "section-open").buildUpon()
                        .appendPath(section).build())
                .putExtra(EXTRA_DASHBOARD_SECTION, section)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, OPEN_REQUEST_BASE + appWidgetId, intent, FLAGS);
    }

    /** Requests an immediate usage refresh; widgets update when it finishes. */
    static PendingIntent refresh(Context context, int appWidgetId) {
        Intent intent = new Intent(context, WidgetRefreshReceiver.class)
                .setAction(AppConstants.ACTION_REFRESH_WIDGET)
                .setData(widgetUri(appWidgetId, "refresh"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        return PendingIntent.getBroadcast(context, REFRESH_REQUEST_BASE + appWidgetId, intent,
                FLAGS);
    }

    /** Unique data URI so each widget keeps its own PendingIntent. */
    private static Uri widgetUri(int appWidgetId, String action) {
        return Uri.parse("codexmeter://widget/home/v" + AppConstants.VERSION_CODE + "/"
                + appWidgetId + "/" + action);
    }
}
