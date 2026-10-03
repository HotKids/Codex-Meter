package me.pipi.codexmeter;

import android.content.Intent;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.view.View;
import android.view.ViewGroup;
import androidx.preference.Preference;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreferenceCompat;

/** Updates page: release channel, automatic GitHub release checks, and update alerts. */
public final class SettingsUpdatesFragment extends SettingsPageFragment {
    private static final int REQUEST_NOTIFICATION_PERMISSION = 8603;

    private Preference channelPreference;
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
        updateSummary();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSummary();
    }

    private void bindChannel() {
        channelPreference = findPreference("update_channel_ui");
        channelPreference.setSummary(R.string.updates_channel_stable);
        channelPreference.setOnPreferenceClickListener(preference -> {
            String[] entries = {getString(R.string.updates_channel_stable),
                    getString(R.string.updates_channel_test_placeholder)};
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(),
                    android.R.layout.simple_list_item_single_choice, entries) {
                @Override public boolean areAllItemsEnabled() { return false; }
                @Override public boolean isEnabled(int position) { return position == 0; }
                @Override public View getView(int position, View convertView, ViewGroup parent) {
                    View row = super.getView(position, convertView, parent);
                    row.setEnabled(position == 0);
                    row.setAlpha(position == 0 ? 1f : 0.38f);
                    return row;
                }
            };
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.updates_pref_channel_title)
                    .setSingleChoiceItems(adapter, 0, (dialog, which) -> {
                        if (which == 0) {
                            UpdatePreferences.setChannel(requireContext(), UpdateChannel.STABLE);
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        });
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
            channelPreference.setSummary(R.string.updates_channel_stable);
        }
        updateAutomaticChecksEnabledState();
        updateAutomaticChecksSummary();
    }
}
