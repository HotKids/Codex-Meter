package me.pipi.codexmeter;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;
import androidx.preference.PreferenceFragmentCompat;

/**
 * Base for the One UI preference pages hosted by {@link SettingsActivity}. Every page stores its
 * persistent preferences in the app's main settings file and shares a few notification helpers.
 */
abstract class SettingsPageFragment extends PreferenceFragmentCompat {
    private static final String SETTINGS_PREFERENCES = "codex_meter_settings_v1";
    private static final String POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";

    @Override
    public final void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(SETTINGS_PREFERENCES);
        onCreatePage();
    }

    /** Adds this page's preference resource and binds its preferences. */
    abstract void onCreatePage();

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(Ui.background(requireContext(), Ui.isDark(requireContext())));
    }

    /** True on Android 13+ while the runtime notification permission is still missing. */
    final boolean lacksNotificationPermission() {
        return Build.VERSION.SDK_INT >= 33
                && requireContext().checkSelfPermission(POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
    }

    final void requestNotificationPermission(int requestCode) {
        requestPermissions(new String[]{POST_NOTIFICATIONS}, requestCode);
    }

    /** Whether the user has left notifications enabled for the whole app. */
    final boolean areAppNotificationsEnabled() {
        NotificationManager manager = (NotificationManager) requireContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);
        return manager != null && manager.areNotificationsEnabled();
    }

    final void openAppNotificationSettings() {
        startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName()));
    }

    final void showToast(CharSequence text, int duration) {
        Toast.makeText(requireContext(), text, duration).show();
    }
}
