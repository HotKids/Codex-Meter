package dev.bennett.codexmeter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

/** Bridges PackageInstaller status callbacks to the user-confirmation activity. */
public final class UpdateInstallReceiver extends BroadcastReceiver {
    public static final String EXTRA_VERSION = "update_version";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !AppConstants.ACTION_INSTALL_STATUS.equals(intent.getAction())) {
            return;
        }
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation;
            if (Build.VERSION.SDK_INT >= 33) {
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            } else {
                @SuppressWarnings("deprecation")
                Intent legacy = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                confirmation = legacy;
            }
            if (confirmation == null) {
                abandon(context, intent);
                UpdatePreferences.setInstallError(context,
                        AppText.get(R.string.phone_android_did_not_provide_an_update_confirmation_s_335af));
                return;
            }
            try {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(confirmation);
            } catch (RuntimeException exception) {
                abandon(context, intent);
                UpdatePreferences.setInstallError(context,
                        AppText.get(R.string.phone_could_not_open_android_s_update_confirmation_scr_cb6a2));
            }
            return;
        }
        if (status == PackageInstaller.STATUS_SUCCESS) {
            UpdatePreferences.setInstallError(context, "");
            return;
        }
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if (message == null || message.trim().isEmpty()) {
            message = AppText.get(R.string.phone_android_rejected_the_update_status_a73f0) + status + ").";
        }
        UpdatePreferences.setInstallError(context, message);
    }

    private static void abandon(Context context, Intent intent) {
        int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
        if (sessionId >= 0) {
            try {
                context.getPackageManager().getPackageInstaller().abandonSession(sessionId);
            } catch (RuntimeException ignored) {
            }
        }
    }
}
