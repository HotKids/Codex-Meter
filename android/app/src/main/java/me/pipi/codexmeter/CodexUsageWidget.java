package me.pipi.codexmeter;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

/** The resizable Clear home widget and shared refresh lifecycle for home-widget providers. */
public class CodexUsageWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        if (appWidgetIds != null) {
            for (int appWidgetId : appWidgetIds) {
                WidgetRenderer.update(context, manager, appWidgetId);
            }
        }
        RefreshScheduler.schedulePeriodic(context);
        RefreshScheduler.scheduleImmediate(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
            int appWidgetId, Bundle newOptions) {
        WidgetRenderer.update(context, manager, appWidgetId);
    }

    @Override
    public void onEnabled(Context context) {
        RefreshScheduler.schedulePeriodic(context);
        RefreshScheduler.scheduleImmediate(context);
    }

    @Override
    public void onDisabled(Context context) {
        RefreshScheduler.schedulePeriodic(context);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        if (appWidgetIds != null) {
            for (int appWidgetId : appWidgetIds) {
                AppPreferences.deleteWidgetOptions(context, appWidgetId);
            }
        }
    }

    @Override
    public void onRestored(Context context, int[] oldWidgetIds, int[] newWidgetIds) {
        if (oldWidgetIds != null && newWidgetIds != null) {
            AppPreferences.restoreWidgetOptions(context, oldWidgetIds, newWidgetIds);
        }
        if (newWidgetIds != null) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            for (int appWidgetId : newWidgetIds) {
                WidgetRenderer.update(context, manager, appWidgetId);
            }
        }
    }
}
