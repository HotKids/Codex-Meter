package dev.bennett.codexmeter;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

/** The home-screen usage widget: cards above one row, upstream dials on one-row cells. */
public final class CodexUsageWidget extends AppWidgetProvider {
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
            int count = Math.min(oldWidgetIds.length, newWidgetIds.length);
            for (int index = 0; index < count; index++) {
                int oldId = oldWidgetIds[index];
                int newId = newWidgetIds[index];
                AppPreferences.saveWidgetOptions(context, newId,
                        AppPreferences.loadWidgetOptions(context, oldId));
                AppPreferences.saveWidgetTapAction(context, newId,
                        AppPreferences.getWidgetTapAction(context, oldId));
                AppPreferences.deleteWidgetOptions(context, oldId);
            }
        }
        if (newWidgetIds != null) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            for (int appWidgetId : newWidgetIds) {
                WidgetRenderer.update(context, manager, appWidgetId);
            }
        }
    }
}
