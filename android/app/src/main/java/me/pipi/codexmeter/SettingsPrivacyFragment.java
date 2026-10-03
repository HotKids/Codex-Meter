package me.pipi.codexmeter;

/** Privacy page: read-only notes on local token storage and direct network access. */
public final class SettingsPrivacyFragment extends SettingsPageFragment {
    @Override
    void onCreatePage() {
        addPreferencesFromResource(R.xml.preferences_settings_privacy);
    }
}
