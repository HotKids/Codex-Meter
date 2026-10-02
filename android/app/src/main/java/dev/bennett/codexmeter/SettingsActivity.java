package dev.bennett.codexmeter;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import dev.oneuiproject.oneui.layout.ToolbarLayout;

/**
 * Settings built from the One UI Design Library preference components used by its sample app.
 * Each settings page opens as its own instance of this activity and hosts one
 * {@link SettingsPageFragment}; unknown or missing pages fall back to the root list.
 */
public final class SettingsActivity extends AppCompatActivity {
    private static final String EXTRA_PAGE = "settings_page";
    private static final String PAGE_ROOT = "root";
    static final String PAGE_APPEARANCE = "appearance";
    static final String PAGE_REFRESH_USAGE = "refresh_usage";
    static final String PAGE_NOTIFICATIONS = "notifications";
    static final String PAGE_NOW_BAR = "now_bar";
    static final String PAGE_UPDATES = "updates";
    static final String PAGE_TRANSFER = "transfer";
    static final String PAGE_PRIVACY = "privacy";
    private static final String PAGE_DIAGNOSTICS = "diagnostics";

    static Intent diagnosticsIntent(Context context) {
        return pageIntent(context, PAGE_DIAGNOSTICS);
    }

    static Intent pageIntent(Context context, String page) {
        return new Intent(context, SettingsActivity.class).putExtra(EXTRA_PAGE, page);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applySelectedTheme(this);
        super.onCreate(savedInstanceState);
        AppPreferences.setAppStyle(this, WidgetOptions.SURFACE_ONE_UI);
        setContentView(R.layout.activity_settings);
        String requestedPage = getIntent().getStringExtra(EXTRA_PAGE);
        String page = requestedPage == null ? PAGE_ROOT : requestedPage;
        ToolbarLayout toolbar = findViewById(R.id.settings_toolbar_layout);
        Ui.configureReachToolbar(toolbar, pageTitle(page), true);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings_fragment, createPageFragment(page))
                    .commit();
        }
    }

    private static String pageTitle(String page) {
        switch (page) {
            case PAGE_APPEARANCE:
                return "Appearance";
            case PAGE_REFRESH_USAGE:
                return "Refresh & usage";
            case PAGE_NOTIFICATIONS:
                return "Notifications";
            case PAGE_NOW_BAR:
                return "Now Bar";
            case PAGE_UPDATES:
                return "Updates";
            case PAGE_TRANSFER:
                return "Backup & transfer";
            case PAGE_PRIVACY:
                return "Privacy";
            case PAGE_DIAGNOSTICS:
                return "Diagnostics";
            case PAGE_ROOT:
            default:
                return "Settings";
        }
    }

    private static Fragment createPageFragment(String page) {
        switch (page) {
            case PAGE_APPEARANCE:
                return new SettingsAppearanceFragment();
            case PAGE_REFRESH_USAGE:
                return new SettingsRefreshUsageFragment();
            case PAGE_NOTIFICATIONS:
                return new SettingsNotificationsFragment();
            case PAGE_NOW_BAR:
                return new SettingsNowBarFragment();
            case PAGE_UPDATES:
                return new SettingsUpdatesFragment();
            case PAGE_TRANSFER:
                return new SettingsTransferFragment();
            case PAGE_PRIVACY:
                return new SettingsPrivacyFragment();
            case PAGE_DIAGNOSTICS:
                return new SettingsDiagnosticsFragment();
            case PAGE_ROOT:
            default:
                return new SettingsRootFragment();
        }
    }
}
