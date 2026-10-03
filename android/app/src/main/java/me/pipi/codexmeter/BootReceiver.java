package me.pipi.codexmeter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores background work after a reboot or an app update. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        boolean packageReplaced = Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        if (!packageReplaced && !Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            return;
        }
        DiagnosticLog.info(context, "process", "boot_receiver", "action", action);
        RefreshScheduler.schedulePeriodic(context);
        ReleaseUpdateScheduler.ensureScheduled(context);
        ResetAlertScheduler.scheduleFromSnapshot(context, AppPreferences.loadSnapshot(context));
        ResetCreditExpiryScheduler.scheduleFromSnapshot(context,
                AppPreferences.loadResetCredits(context));
        NowBarManager.restore(context);
        if (packageReplaced) {
            UpdateNotificationManager.dismiss(context);
            UpdatePreferences.clearNotifiedVersion(context);
            WidgetUpgradeRepair.afterPackageReplaced(context);
        } else {
            WidgetUpgradeRepair.runIfNeeded(context);
        }
    }
}
