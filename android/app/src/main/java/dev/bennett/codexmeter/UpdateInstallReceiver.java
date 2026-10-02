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
            showConfirmation(context, intent);
            return;
        }
        if (status == PackageInstaller.STATUS_SUCCESS) {
            UpdatePreferences.setInstallError(context, "");
            return;
        }
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if (message == null || message.trim().isEmpty()) {
            message = context.getString(R.string.updates_error_install_rejected, status);
        }
        UpdatePreferences.setInstallError(context, message);
    }

    /** Launches Android's install confirmation, abandoning the session if that is impossible. */
    private static void showConfirmation(Context context, Intent intent) {
        Intent confirmation = confirmationIntent(intent);
        if (confirmation == null) {
            abandon(context, intent);
            UpdatePreferences.setInstallError(context,
                    context.getString(R.string.updates_error_no_confirmation_screen));
            return;
        }
        try {
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(confirmation);
        } catch (RuntimeException exception) {
            abandon(context, intent);
            UpdatePreferences.setInstallError(context,
                    context.getString(R.string.updates_error_confirmation_screen_failed));
        }
    }

    private static Intent confirmationIntent(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
        }
        @SuppressWarnings("deprecation")
        Intent legacy = intent.getParcelableExtra(Intent.EXTRA_INTENT);
        return legacy;
    }

    private static void abandon(Context context, Intent intent) {
        int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
        if (sessionId < 0) {
            return;
        }
        try {
            context.getPackageManager().getPackageInstaller().abandonSession(sessionId);
        } catch (RuntimeException ignored) {
            // The session is already gone or no longer ours.
        }
    }
}
