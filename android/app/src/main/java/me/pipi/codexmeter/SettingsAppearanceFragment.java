package me.pipi.codexmeter;

import androidx.preference.SwitchPreferenceCompat;
import dev.oneuiproject.oneui.preference.HorizontalRadioPreference;

/** Appearance page: light/dark/system theme. */
public final class SettingsAppearanceFragment extends SettingsPageFragment {
    @Override
    void onCreatePage() {
        addPreferencesFromResource(R.xml.preferences_settings_appearance);
        bindTheme();
    }

    private void bindTheme() {
        String selected = AppPreferences.getAppTheme(requireContext());
        boolean useSystem = WidgetOptions.THEME_SYSTEM.equals(selected);
        HorizontalRadioPreference theme = findPreference("app_theme");
        SwitchPreferenceCompat system = findPreference("theme_system_ui");
        system.setEnabled(true);
        // Keep "system" persisted while previewing the currently effective light/dark mode.
        theme.setPersistent(false);
        theme.setDividerEnabled(false);
        theme.setTouchEffectEnabled(false);
        theme.setValue(useSystem ? effectiveTheme() : selected);
        theme.setEnabled(!useSystem);
        system.setChecked(useSystem);
        theme.setOnPreferenceChangeListener((preference, value) -> {
            AppPreferences.setAppTheme(requireContext(), String.valueOf(value));
            requireActivity().recreate();
            return true;
        });
        system.setOnPreferenceChangeListener((preference, value) -> {
            boolean followSystem = (Boolean) value;
            AppPreferences.setAppTheme(requireContext(),
                    followSystem ? WidgetOptions.THEME_SYSTEM : effectiveTheme());
            requireActivity().recreate();
            return true;
        });
    }

    /** The light or dark theme currently in effect. */
    private String effectiveTheme() {
        return Ui.isDark(requireContext()) ? WidgetOptions.THEME_DARK : WidgetOptions.THEME_LIGHT;
    }
}
