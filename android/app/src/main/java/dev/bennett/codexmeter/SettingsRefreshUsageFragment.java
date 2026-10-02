package dev.bennett.codexmeter;

import androidx.preference.ListPreference;
import androidx.preference.SwitchPreferenceCompat;
import dev.bennett.codexmeter.wear.PhoneWearSync;

/** Refresh & usage page: refresh schedule, dashboard layout, and usage-pace estimates. */
public final class SettingsRefreshUsageFragment extends SettingsPageFragment {
    private static final String REFRESH_MODE_AUTOMATIC = "automatic";
    private static final String REFRESH_MODE_MANUAL = "manual";

    @Override
    void onCreatePage() {
        addPreferencesFromResource(R.xml.preferences_settings_refresh_usage);
        bindRefresh();
        bindUsagePace();
    }

    private void bindRefresh() {
        findPreference("dashboard_reorder_ui").setOnPreferenceClickListener(preference -> {
            Ui.startSecondaryActivity(requireActivity(), DashboardReorderActivity.class);
            return true;
        });

        SwitchPreferenceCompat onLaunch = findPreference("refresh_on_launch");
        onLaunch.setEnabled(true);
        onLaunch.setChecked(AppPreferences.getRefreshOnLaunch(requireContext()));
        onLaunch.setOnPreferenceChangeListener((preference, value) -> {
            AppPreferences.setRefreshOnLaunch(requireContext(), (Boolean) value);
            return true;
        });

        boolean automatic = AppPreferences.getAutomaticRefresh(requireContext());
        ListPreference interval = findPreference("refresh_interval_ui");
        interval.setPersistent(false);
        interval.setValue(String.valueOf(AppPreferences.getRefreshMinutes(requireContext())));
        interval.setEnabled(!automatic);
        interval.setOnPreferenceChangeListener((preference, value) -> {
            AppPreferences.setRefreshMinutes(requireContext(),
                    Integer.parseInt(String.valueOf(value)));
            onRefreshScheduleChanged();
            return true;
        });

        ListPreference mode = findPreference("refresh_mode_ui");
        mode.setPersistent(false);
        mode.setValue(automatic ? REFRESH_MODE_AUTOMATIC : REFRESH_MODE_MANUAL);
        mode.setOnPreferenceChangeListener((preference, value) -> {
            boolean automaticMode = REFRESH_MODE_AUTOMATIC.equals(String.valueOf(value));
            AppPreferences.setAutomaticRefresh(requireContext(), automaticMode);
            interval.setEnabled(!automaticMode);
            onRefreshScheduleChanged();
            return true;
        });
    }

    private void onRefreshScheduleChanged() {
        RefreshScheduler.schedulePeriodic(requireContext());
        PhoneWearSync.pushSettings(requireContext());
    }

    private void bindUsagePace() {
        SwitchPreferenceCompat enabled = findPreference("usage_pace_enabled_ui");
        enabled.setPersistent(false);
        enabled.setChecked(UsagePacePreferences.isEnabled(requireContext()));

        ListPreference sensitivity = findPreference("usage_pace_sensitivity_ui");
        sensitivity.setPersistent(false);
        sensitivity.setValue(UsagePacePreferences.getSensitivity(requireContext()));
        sensitivity.setEnabled(UsagePacePreferences.isEnabled(requireContext()));

        enabled.setOnPreferenceChangeListener((preference, value) -> {
            boolean isEnabled = (Boolean) value;
            UsagePacePreferences.setEnabled(requireContext(), isEnabled);
            sensitivity.setEnabled(isEnabled);
            NowBarManager.onPaceSettingsChanged(requireContext());
            return true;
        });
        sensitivity.setOnPreferenceChangeListener((preference, value) -> {
            String normalized = UsagePace.normalizeSensitivity(String.valueOf(value));
            UsagePacePreferences.setSensitivity(requireContext(), normalized);
            sensitivity.setValue(normalized);
            NowBarManager.onPaceSettingsChanged(requireContext());
            return true;
        });
    }
}
