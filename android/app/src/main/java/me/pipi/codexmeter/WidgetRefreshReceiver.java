package me.pipi.codexmeter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles a tap on a widget's refresh time: refreshes usage now and redraws the widgets. */
public final class WidgetRefreshReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (AppConstants.ACTION_REFRESH_WIDGET.equals(action)) {
            if (!RefreshScheduler.scheduleManual(context)) WidgetRenderer.updateAll(context);
        } else if (WidgetRefreshStatus.ACTION_RECONCILE.equals(action)) {
            if (WidgetRefreshStatus.reconcile(context)) WidgetRenderer.updateAll(context);
        }
    }
}
