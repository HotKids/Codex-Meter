package dev.bennett.codexmeter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/* JADX INFO: loaded from: classes.dex */
public final class WidgetRefreshReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        if (intent != null && AppConstants.ACTION_REFRESH_WIDGET.equals(intent.getAction())) {
            Uri source = intent.getData();
            if (isHomeSource(source)) {
                WidgetRefreshScheduler.scheduleImmediate(context);
                WidgetRefreshScheduler.updateHomeWidgets(context);
            } else {
                // Preserve any older/non-home callers of the original receiver.
                RefreshScheduler.scheduleImmediate(context);
                WidgetRenderer.updateAll(context);
            }
        }
    }

    static boolean isHomeSource(Uri source) {
        return source != null && "widget".equals(source.getHost())
                && (("codexmeter".equals(source.getScheme())
                && source.getPath() != null && source.getPath().startsWith("/home/"))
                || "pipi-usage".equals(source.getScheme()));
    }
}
