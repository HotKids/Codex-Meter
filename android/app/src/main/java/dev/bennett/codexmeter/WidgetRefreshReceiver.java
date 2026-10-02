package dev.bennett.codexmeter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles a tap on a widget's refresh time: refreshes usage now and redraws the widgets. */
public final class WidgetRefreshReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && AppConstants.ACTION_REFRESH_WIDGET.equals(intent.getAction())) {
            RefreshScheduler.scheduleImmediate(context);
            WidgetRenderer.updateAll(context);
        }
    }
}
