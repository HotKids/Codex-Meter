package dev.bennett.codexmeter;

import android.content.Intent;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreferenceCompat;

/** Updates page: release channel, automatic GitHub release checks, and update alerts. */
public final class SettingsUpdatesFragment extends SettingsPageFragment {
    private static final int REQUEST_NOTIFICATION_PERMISSION = 8603;

    private ListPreference channelPreference;
    private SwitchPreferenceCompat automaticChecksPreference;
    private ListPreference intervalPreference;
    private SwitchPreferenceCompat notifyPreference;

    @Override
    void onCreatePage() {
        addPreferencesFromResource(R.xml.preferences_settings_updates);
        bindChannel();
        bindAutomaticChecks();
        findPreference("check_for_updates").setOnPreferenceClickListener(preference -> {
            openUpdateCheck();
            return true;
        });
        findPreference("release_history").setOnPreferenceClickListener(preference -> {
            Ui.startSecondaryActivity(requireActivity(), ReleaseHistoryActivity.class);
            return true;
        });
        updateSummary();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSummary();
    }

    private void bindChannel() {
        channelPreference = findPreference("update_channel_ui");
        channelPreference.setPersistent(false);
        channelPreference.setValue(UpdatePreferences.channel(requireContext()));
        channelPreference.setOnPreferenceChangeListener((preference, value) -> {
            String channel = UpdateChannel.normalize(String.valueOf(value));
            if (channel.equals(UpdatePreferences.channel(requireContext()))) {
                return true;
            }
            if (UpdateChannel.ALPHA.equals(channel)) {
                confirmAlphaChannel();
                // The confirmation dialog applies the channel if the user accepts.
                return false;
            }
            applyUpdateChannel(UpdateChannel.STABLE);
            return true;
        });
    }

    private void confirmAlphaChannel() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.updates_alpha_dialog_title)
                .setMessage(R.string.updates_alpha_dialog_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.updates_alpha_dialog_confirm,
                        (dialog, which) -> applyUpdateChannel(UpdateChannel.ALPHA))
                .show();
    }

    private void applyUpdateChannel(String channel) {
        UpdatePreferences.setChannel(requireContext(), channel);
        if (channelPreference != null) {
            channelPreference.setValue(channel);
        }
        updateSummary();
        openUpdateCheck();
    }

    private void openUpdateCheck() {
        startActivity(new Intent(requireContext(), UpdateActivity.class)
                .putExtra(UpdateActivity.EXTRA_FORCE_CHECK, true));
    }

    private void bindAutomaticChecks() {
        automaticChecksPreference = findPreference("automatic_update_checks_ui");
        automaticChecksPreference.setPersistent(false);
        automaticChecksPreference.setChecked(UpdatePreferences.automaticChecks(requireContext()));
        automaticChecksPreference.setOnPreferenceChangeListener((preference, value) -> {
            boolean enabled = (Boolean) value;
            UpdatePreferences.setAutomaticChecks(requireContext(), enabled);
            if (enabled) {
                ReleaseUpdateScheduler.ensureScheduled(requireContext());
            } else {
                ReleaseUpdateScheduler.cancel(requireContext());
                if (notifyPreference != null) {
                    notifyPreference.setChecked(false);
                }
            }
            updateAutomaticChecksEnabledState();
            updateAutomaticChecksSummary();
            return true;
        });

        intervalPreference = findPreference("update_check_interval_ui");
        intervalPreference.setPersistent(false);
        intervalPreference.setValue(
                String.valueOf(UpdatePreferences.checkIntervalHours(requireContext())));
        intervalPreference.setOnPreferenceChangeListener((preference, value) -> {
            int hours = UpdateCheckFrequency.normalize(Integer.parseInt(String.valueOf(value)));
            UpdatePreferences.setCheckIntervalHours(requireContext(), hours);
            intervalPreference.setValue(String.valueOf(hours));
            ReleaseUpdateScheduler.ensureScheduled(requireContext());
            updateAutomaticChecksSummary();
            return true;
        });

        notifyPreference = findPreference("notify_update_available_ui");
        notifyPreference.setPersistent(false);
        notifyPreference.setChecked(UpdatePreferences.notifyUpdatesEnabled(requireContext()));
        notifyPreference.setOnPreferenceChangeListener((preference, value) -> {
            boolean enabled = (Boolean) value;
            if (enabled && !ensureNotificationPermission()) {
                return false;
            }
            UpdatePreferences.setNotifyUpdatesEnabled(requireContext(), enabled);
            if (enabled) {
                UpdateNotificationManager.ensureChannel(requireContext());
                UpdateNotificationManager.onReleasesUpdated(requireContext());
            }
            return true;
        });
    }

    /** The interval and alerts only apply while automatic checks are on. */
    private void updateAutomaticChecksEnabledState() {
        boolean enabled = UpdatePreferences.automaticChecks(requireContext());
        if (intervalPreference != null) {
            intervalPreference.setEnabled(enabled);
        }
        if (notifyPreference != null) {
            notifyPreference.setEnabled(enabled);
            if (!enabled) {
                notifyPreference.setChecked(false);
            }
        }
    }

    private void updateAutomaticChecksSummary() {
        if (automaticChecksPreference == null || getContext() == null) {
            return;
        }
        if (!UpdatePreferences.automaticChecks(requireContext())) {
            automaticChecksPreference.setSummary(R.string.updates_automatic_checks_off);
            return;
        }
        automaticChecksPreference.setSummary(UpdatePreferences.checkIntervalSummary(
                requireContext(), UpdatePreferences.checkIntervalHours(requireContext())));
    }

    private boolean ensureNotificationPermission() {
        if (lacksNotificationPermission()) {
            requestNotificationPermission(REQUEST_NOTIFICATION_PERMISSION);
            showToast(getString(R.string.updates_notification_permission_needed),
                    Toast.LENGTH_LONG);
            return false;
        }
        if (areAppNotificationsEnabled()) {
            return true;
        }
        showToast(getString(R.string.updates_notifications_disabled), Toast.LENGTH_LONG);
        return false;
    }

    /** Re-reads every update setting into the (non-persistent) preferences on this page. */
    private void updateSummary() {
        if (getContext() == null) {
            return;
        }
        if (automaticChecksPreference != null) {
            automaticChecksPreference.setChecked(
                    UpdatePreferences.automaticChecks(requireContext()));
        }
        if (intervalPreference != null) {
            intervalPreference.setValue(
                    String.valueOf(UpdatePreferences.checkIntervalHours(requireContext())));
        }
        if (notifyPreference != null) {
            notifyPreference.setChecked(UpdatePreferences.notifyUpdatesEnabled(requireContext()));
        }
        if (channelPreference != null) {
            channelPreference.setValue(UpdatePreferences.channel(requireContext()));
        }
        updateAutomaticChecksEnabledState();
        updateAutomaticChecksSummary();
    }
}
