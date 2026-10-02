package dev.bennett.codexmeter;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

/**
 * Shared lifecycle for the Samsung lock-screen/AOD widget providers. Each manifest-registered
 * subclass only picks the shape, style and metric it renders.
 */
abstract class SamsungLockWidgetProvider extends AppWidgetProvider {
    protected abstract SamsungLockWidgetSupport.Shape shape();

    protected abstract SamsungLockWidgetSupport.Style style();

    protected SamsungLockWidgetSupport.Metric metric() {
        return SamsungLockWidgetSupport.Metric.BOTH;
    }

    @Override
    public final void onUpdate(Context context, AppWidgetManager appWidgetManager,
            int[] appWidgetIds) {
        SamsungLockWidgetSupport.updateIds(context, appWidgetManager, appWidgetIds, shape(),
                style(), metric());
    }

    @Override
    public final void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
            int appWidgetId, Bundle newOptions) {
        SamsungLockWidgetSupport.update(context, appWidgetManager, appWidgetId, shape(), style(),
                metric());
    }

    @Override
    public final void onRestored(Context context, int[] oldWidgetIds, int[] newWidgetIds) {
        if (oldWidgetIds != null && newWidgetIds != null) {
            int count = Math.min(oldWidgetIds.length, newWidgetIds.length);
            for (int i = 0; i < count; i++) {
                AppPreferences.saveLockWidgetOptions(context, newWidgetIds[i],
                        AppPreferences.loadLockWidgetOptions(context, oldWidgetIds[i]));
                AppPreferences.deleteLockWidgetOptions(context, oldWidgetIds[i]);
            }
        }
        SamsungLockWidgetSupport.updateIds(context, AppWidgetManager.getInstance(context),
                newWidgetIds, shape(), style(), metric());
    }

    @Override
    public final void onDeleted(Context context, int[] appWidgetIds) {
        if (appWidgetIds == null) {
            return;
        }
        for (int appWidgetId : appWidgetIds) {
            AppPreferences.deleteLockWidgetOptions(context, appWidgetId);
        }
    }

    @Override
    public final void onEnabled(Context context) {
        SamsungLockWidgetSupport.updateAll(context);
    }
}
