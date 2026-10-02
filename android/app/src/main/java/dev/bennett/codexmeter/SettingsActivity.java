package dev.bennett.codexmeter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import dev.bennett.codexmeter.wear.PhoneWearSync;
import dev.oneuiproject.oneui.layout.ToolbarLayout;
import dev.oneuiproject.oneui.preference.HorizontalRadioPreference;
import dev.oneuiproject.oneui.preference.LayoutPreference;
import dev.oneuiproject.oneui.widget.RoundedLinearLayout;
import dev.oneuiproject.oneui.widget.CardItemView;
import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Settings built from the One UI Design Library preference components used by its sample app. */
public final class SettingsActivity extends AppCompatActivity {
    private static final String EXTRA_PAGE = "settings_page";
    private static final String PAGE_ROOT = "root";
    private static final String PAGE_APPEARANCE = "appearance";
    private static final String PAGE_REFRESH_USAGE = "refresh_usage";
    private static final String PAGE_NOTIFICATIONS = "notifications";
    private static final String PAGE_NOW_BAR = "now_bar";
    private static final String PAGE_UPDATES = "updates";
    private static final String PAGE_TRANSFER = "transfer";
    private static final String PAGE_PRIVACY = "privacy";
    private static final String PAGE_DIAGNOSTICS = "diagnostics";

    static Intent diagnosticsIntent(Context context) {
        return new Intent(context, SettingsActivity.class)
                .putExtra(EXTRA_PAGE, PAGE_DIAGNOSTICS);
    }

    @Override
    protected void onCreate(Bundle bundle) {
        Ui.applySelectedTheme(this);
        super.onCreate(bundle);
        AppPreferences.setAppStyle(this, WidgetOptions.SURFACE_ONE_UI);
        setContentView(R.layout.activity_settings);
        String page = normalizePage(getIntent().getStringExtra(EXTRA_PAGE));
        ToolbarLayout toolbar = findViewById(R.id.settings_toolbar_layout);
        Ui.configureReachToolbar(toolbar, pageTitle(page), true);
        if (bundle == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings_fragment, SettingsFragment.newInstance(page))
                    .commit();
        }
    }

    private static String normalizePage(String page) {
        if (PAGE_APPEARANCE.equals(page)
                || PAGE_REFRESH_USAGE.equals(page)
                || PAGE_NOTIFICATIONS.equals(page)
                || PAGE_NOW_BAR.equals(page)
                || PAGE_UPDATES.equals(page)
                || PAGE_TRANSFER.equals(page)
                || PAGE_PRIVACY.equals(page)
                || PAGE_DIAGNOSTICS.equals(page)) {
            return page;
        }
        return PAGE_ROOT;
    }

    private static String pageTitle(String page) {
        switch (page) {
            case PAGE_APPEARANCE:
                return AppText.get(R.string.phone_appearance_41def);
            case PAGE_REFRESH_USAGE:
                return AppText.get(R.string.phone_refresh_usage_90304);
            case PAGE_NOTIFICATIONS:
                return AppText.get(R.string.phone_notifications_753a2);
            case PAGE_NOW_BAR:
                return "Now Bar";
            case PAGE_UPDATES:
                return AppText.get(R.string.phone_updates_c76d1);
            case PAGE_TRANSFER:
                return AppText.get(R.string.phone_backup_transfer_84bb2);
            case PAGE_PRIVACY:
                return AppText.get(R.string.phone_privacy_cf014);
            case PAGE_DIAGNOSTICS:
                return AppText.get(R.string.phone_diagnostics_3af22);
            case PAGE_ROOT:
            default:
                return AppText.get(R.string.phone_settings_c7f73);
        }
    }

    // One UI's lint detector only associates this fragment with preferences_settings.xml.
    // This fragment intentionally loads one of several page-specific preference resources.
    @SuppressLint("FindPreferenceKeyNotFound")
    public static final class SettingsFragment extends PreferenceFragmentCompat {
        private static final String ARG_PAGE = "page";
        private static final int REQUEST_EXPORT_TRANSFER = 9201;
        private static final int REQUEST_IMPORT_TRANSFER = 9202;
        private static final int REQUEST_EXPORT_DIAGNOSTICS = 9203;

        private String page = PAGE_ROOT;
        private Preference expiryTimesPreference;
        private Preference permissionPreference;
        private Preference testNotificationPreference;
        private PreferenceCategory notificationLowUsageCategory;
        private PreferenceCategory notificationResetCreditCategory;
        private PreferenceCategory notificationTroubleshootingCategory;
        private ListPreference notificationStylePreference;
        private SwitchPreferenceCompat nowBarMonitorPreference;
        private SwitchPreferenceCompat nowBarAutoStartPreference;
        private SwitchPreferenceCompat nowBarAcceleratedPreference;
        private ListPreference nowBarDisplayModePreference;
        private ListPreference nowBarPercentModePreference;
        private ListPreference nowBarMetricPreference;
        private ListPreference nowBarThresholdPreference;
        private ListPreference usagePaceSensitivityPreference;
        private Preference nowBarPermissionPreference;
        private SwitchPreferenceCompat automaticUpdatePreference;
        private ListPreference updateChannelPreference;
        private ListPreference updateIntervalPreference;
        private SwitchPreferenceCompat notifyUpdatePreference;
        private boolean pendingExportAppSettings;
        private boolean pendingExportNotifications;
        private boolean pendingExportNowBar;
        private boolean pendingExportAuthentication;
        private SettingsTransfer.Document pendingImportDocument;

        static SettingsFragment newInstance(String page) {
            SettingsFragment fragment = new SettingsFragment();
            Bundle arguments = new Bundle();
            arguments.putString(ARG_PAGE, normalizePage(page));
            fragment.setArguments(arguments);
            return fragment;
        }

        @Override
        public void onCreatePreferences(Bundle bundle, String rootKey) {
            getPreferenceManager().setSharedPreferencesName("codex_meter_settings_v1");
            page = normalizePage(getArguments() == null
                    ? null : getArguments().getString(ARG_PAGE));
            switch (page) {
                case PAGE_APPEARANCE:
                    addPreferencesFromResource(R.xml.preferences_settings_appearance);
                    bindAppearance();
                    break;
                case PAGE_REFRESH_USAGE:
                    addPreferencesFromResource(R.xml.preferences_settings_refresh_usage);
                    bindRefresh();
                    bindUsagePace();
                    break;
                case PAGE_NOTIFICATIONS:
                    addPreferencesFromResource(R.xml.preferences_settings_notifications);
                    bindNotifications();
                    break;
                case PAGE_NOW_BAR:
                    addPreferencesFromResource(R.xml.preferences_settings_now_bar);
                    bindNowBar();
                    break;
                case PAGE_UPDATES:
                    addPreferencesFromResource(R.xml.preferences_settings_updates);
                    bindUpdates();
                    break;
                case PAGE_TRANSFER:
                    addPreferencesFromResource(R.xml.preferences_settings_transfer);
                    bindTransfer();
                    break;
                case PAGE_PRIVACY:
                    addPreferencesFromResource(R.xml.preferences_settings_privacy);
                    break;
                case PAGE_DIAGNOSTICS:
                    addPreferencesFromResource(R.xml.preferences_settings_diagnostics);
                    bindDiagnostics();
                    break;
                case PAGE_ROOT:
                default:
                    addPreferencesFromResource(R.xml.preferences_settings);
                    bindRoot();
                    break;
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
                return;
            }
            Uri uri = data.getData();
            if (requestCode == REQUEST_EXPORT_TRANSFER) {
                finishExport(uri);
            } else if (requestCode == REQUEST_IMPORT_TRANSFER) {
                beginImport(uri);
            } else if (requestCode == REQUEST_EXPORT_DIAGNOSTICS) {
                finishDiagnosticExport(uri);
            }
        }

        @Override
        public void onViewCreated(View view, Bundle bundle) {
            super.onViewCreated(view, bundle);
            view.setBackgroundColor(Ui.background(requireContext(), Ui.isDark(requireContext())));
        }

        @Override
        public void onResume() {
            super.onResume();
            if (PAGE_ROOT.equals(page)) {
                updateRootSummaries();
            } else if (PAGE_NOTIFICATIONS.equals(page)) {
                updatePermissionSummary();
            } else if (PAGE_NOW_BAR.equals(page)) {
                if (!NowBarManager.refreshActiveNotificationContract(requireContext())) {
                    Toast.makeText(requireContext(),
                            AppText.get(R.string.phone_could_not_refresh_the_live_notification_so_the_m_f6874),
                            Toast.LENGTH_LONG).show();
                }
                updateNowBarSummary();
            } else if (PAGE_UPDATES.equals(page)) {
                updateUpdateSummary();
            } else if (PAGE_DIAGNOSTICS.equals(page)) {
                updateDiagnosticSummary();
            }
        }

        private void bindRoot() {
            bindAccount();
            bindPageLink("settings_appearance", PAGE_APPEARANCE);
            bindPageLink("settings_refresh_usage", PAGE_REFRESH_USAGE);
            bindPageLink("settings_notifications", PAGE_NOTIFICATIONS);
            bindPageLink("settings_now_bar", PAGE_NOW_BAR);
            bindPageLink("settings_updates", PAGE_UPDATES);
            bindPageLink("settings_transfer", PAGE_TRANSFER);
            bindPageLink("settings_privacy", PAGE_PRIVACY);
            findPreference("about_codex_meter").setOnPreferenceClickListener(preference -> {
                Ui.startSecondaryActivity(requireActivity(), AboutActivity.class);
                return true;
            });
            updateRootSummaries();
        }

        private void bindPageLink(String key, String targetPage) {
            findPreference(key).setOnPreferenceClickListener(preference -> {
                DiagnosticLog.info(requireContext(), "user", "settings_page_opened",
                        "page", targetPage);
                startActivity(new Intent(requireContext(), SettingsActivity.class)
                        .putExtra(EXTRA_PAGE, targetPage));
                return true;
            });
        }

        private void bindDiagnostics() {
            SwitchPreferenceCompat enabled = findPreference("diagnostic_logging_enabled");
            enabled.setPersistent(false);
            enabled.setChecked(DiagnosticLog.isEnabled(requireContext()));
            enabled.setOnPreferenceChangeListener((preference, value) -> {
                boolean loggingEnabled = (Boolean) value;
                DiagnosticLog.setEnabled(requireContext(), loggingEnabled);
                enabled.setChecked(loggingEnabled);
                updateDiagnosticSummary();
                Toast.makeText(requireContext(), loggingEnabled
                                ? AppText.get(R.string.phone_diagnostic_tracing_enabled_cf885)
                                : AppText.get(R.string.phone_diagnostic_tracing_disabled_saved_logs_were_kept_19836),
                        Toast.LENGTH_LONG).show();
                return true;
            });
            findPreference("export_diagnostic_logs").setOnPreferenceClickListener(preference -> {
                launchDiagnosticExport();
                return true;
            });
            findPreference("clear_diagnostic_logs").setOnPreferenceClickListener(preference -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle(AppText.get(R.string.phone_clear_diagnostic_logs_94f18))
                        .setMessage(AppText.get(R.string.phone_this_permanently_deletes_all_saved_diagnostic_ev_f81cd))
                        .setNegativeButton(AppText.get(R.string.widget_config_cancel), null)
                        .setPositiveButton(AppText.get(R.string.phone_clear_719ea), (dialog, which) -> {
                            DiagnosticLog.clear(requireContext());
                            updateDiagnosticSummary();
                            Toast.makeText(requireContext(), AppText.get(R.string.phone_diagnostic_logs_cleared_c0d69),
                                    Toast.LENGTH_SHORT).show();
                        })
                        .show();
                return true;
            });
            updateDiagnosticSummary();
        }

        private void updateDiagnosticSummary() {
            if (!PAGE_DIAGNOSTICS.equals(page) || getContext() == null) {
                return;
            }
            boolean enabled = DiagnosticLog.isEnabled(requireContext());
            SwitchPreferenceCompat toggle = findPreference("diagnostic_logging_enabled");
            if (toggle != null) {
                toggle.setChecked(enabled);
            }
            DiagnosticLog.Stats stats = DiagnosticLog.stats(requireContext());
            Preference status = findPreference("diagnostic_log_status");
            status.setSummary((enabled ? AppText.get(R.string.phone_tracing_on_c020c) : AppText.get(R.string.phone_tracing_off_307f8))
                    + " · " + DiagnosticLog.formatBytes(stats.bytes)
                    + (stats.files == 1 ? AppText.get(R.string.phone_in_1_file_4657d) : AppText.get(R.string.phone_across_49521) + stats.files + AppText.get(R.string.phone_files_05211)));
            findPreference("export_diagnostic_logs").setEnabled(stats.hasLogs());
            findPreference("clear_diagnostic_logs").setEnabled(stats.hasLogs());
        }

        private void launchDiagnosticExport() {
            DiagnosticLog.Stats stats = DiagnosticLog.stats(requireContext());
            if (!stats.hasLogs()) {
                Toast.makeText(requireContext(), AppText.get(R.string.phone_there_are_no_diagnostic_logs_to_export_e2c87),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/x-ndjson")
                    .putExtra(Intent.EXTRA_TITLE, "codex-meter-diagnostics-" + stamp + ".jsonl");
            try {
                startActivityForResult(create, REQUEST_EXPORT_DIAGNOSTICS);
            } catch (RuntimeException exception) {
                DiagnosticLog.error(requireContext(), "diagnostics", "export_picker_failed",
                        exception);
                Toast.makeText(requireContext(),
                        AppText.get(R.string.phone_no_file_picker_is_available_to_export_diagnostic_0e364),
                        Toast.LENGTH_LONG).show();
            }
        }

        private void finishDiagnosticExport(Uri uri) {
            try {
                DiagnosticLog.export(requireContext(), uri);
                updateDiagnosticSummary();
                Toast.makeText(requireContext(),
                        AppText.get(R.string.phone_diagnostic_logs_exported_review_the_file_before_ef6af),
                        Toast.LENGTH_LONG).show();
            } catch (Exception exception) {
                DiagnosticLog.error(requireContext(), "diagnostics", "export_failed", exception);
                Toast.makeText(requireContext(),
                        AppText.get(R.string.phone_could_not_export_diagnostic_logs_1dd99) + MainActivity.safeMessage(exception),
                        Toast.LENGTH_LONG).show();
            }
        }

        private void updateRootSummaries() {
            if (!PAGE_ROOT.equals(page) || getContext() == null) return;
            String theme = AppPreferences.getAppTheme(requireContext());
            String themeLabel = WidgetOptions.THEME_SYSTEM.equals(theme)
                    ? AppText.get(R.string.phone_system_default_9d8d3)
                    : WidgetOptions.THEME_DARK.equals(theme) ? AppText.get(R.string.phone_dark_ae1ef) : AppText.get(R.string.phone_light_a36ef);
            findPreference("settings_appearance").setSummary(themeLabel + AppText.get(R.string.phone_material_you_58dac)
                    + (AppPreferences.isMaterialYouEnabled(requireContext()) ? "on" : "off"));

            int refreshMinutes = AppPreferences.getAutomaticRefresh(requireContext())
                    ? RefreshScheduler.effectiveRefreshMinutes(requireContext())
                    : AppPreferences.getRefreshMinutes(requireContext());
            String refreshLabel = refreshMinutes < 60
                    ? refreshMinutes + AppText.get(R.string.phone_minutes_ae098)
                    : refreshMinutes == 60 ? AppText.get(R.string.phone_hourly_d9362) : AppText.get(R.string.phone_every_9dd38) + (refreshMinutes / 60) + AppText.get(R.string.phone_hours_81e3f);
            if (AppPreferences.getAutomaticRefresh(requireContext())) {
                refreshLabel = AppText.get(R.string.phone_automatic_currently_cf569) + refreshLabel;
            }
            String estimatesSummary;
            if (!UsagePacePreferences.isEnabled(requireContext())) {
                estimatesSummary = AppText.get(R.string.phone_estimates_off_e1741);
            } else if (!UsagePacePreferences.areWarningsEnabled(requireContext())) {
                estimatesSummary = AppText.get(R.string.phone_estimates_on_warnings_off_5588f);
            } else {
                estimatesSummary = AppText.get(R.string.phone_estimates_on_90bb3);
            }
            findPreference("settings_refresh_usage").setSummary(
                    refreshLabel + " · " + estimatesSummary);

            findPreference("settings_notifications").setSummary(
                    ResetAlertPreferences.enabled(requireContext())
                            ? AppText.get(R.string.phone_on_86e1b)
                            + metricLabel(ResetAlertPreferences.getMetric(requireContext()))
                            + AppText.get(R.string.phone_at_7df70) + ResetAlertPreferences.getThreshold(requireContext()) + "%"
                            : AppText.get(R.string.phone_off_e3de5));

            String nowBarSummary;
            if (NowBarManager.isActive(requireContext())) {
                nowBarSummary = AppText.get(R.string.phone_live_monitor_active_d68de);
            } else if (NowBarPreferences.isAutoStartEnabled(requireContext())) {
                nowBarSummary = AppText.get(R.string.phone_automatic_starts_at_258d3)
                        + NowBarPreferences.getThreshold(requireContext()) + "%";
            } else {
                nowBarSummary = AppText.get(R.string.phone_manual_start_daa73);
            }
            findPreference("settings_now_bar").setSummary(nowBarSummary);

            GitHubRelease availableUpdate = UpdatePreferences.availableUpdate(requireContext());
            String channelSuffix = UpdateChannel.isAlpha(
                    UpdatePreferences.channel(requireContext())) ? AppText.get(R.string.phone_alpha_channel_0a216) : "";
            findPreference("settings_updates").setSummary((availableUpdate != null
                    ? "v" + availableUpdate.version + AppText.get(R.string.phone_available_3e36f)
                    : UpdatePreferences.automaticChecks(requireContext())
                    ? AppText.get(R.string.phone_automatic_f21e5) + PhoneLabels.updateLabel(
                    UpdatePreferences.checkIntervalHours(requireContext()))
                    : AppText.get(R.string.phone_automatic_checks_off_15e8a)) + channelSuffix);
        }

        private String metricLabel(String metric) {
            if ("five_hour".equals(metric)) return AppText.get(R.string.five_hour);
            if ("weekly".equals(metric)) return AppText.get(R.string.weekly);
            return AppText.get(R.string.phone_both_limits_b8c26);
        }

        private void bindAccount() {
            boolean dark = Ui.isDark(requireContext());
            LayoutPreference preference = findPreference("account_card");
            RoundedLinearLayout card = preference.findViewById(R.id.settings_account_card);
            card.setBackground(Ui.card(requireContext(), dark).getBackground());
            ImageView avatar = preference.findViewById(R.id.settings_account_avatar);
            GradientDrawable avatarBackground = new GradientDrawable();
            avatarBackground.setShape(GradientDrawable.OVAL);
            avatarBackground.setColor(Ui.controlSurface(requireContext(), dark));
            avatar.setBackground(avatarBackground);
            avatar.setPadding(Ui.dp(requireContext(), 10), Ui.dp(requireContext(), 10),
                    Ui.dp(requireContext(), 10), Ui.dp(requireContext(), 10));
            avatar.setImageResource(R.drawable.ic_oui_contact_outline);
            avatar.setColorFilter(Ui.mainText(dark));
            TextView title = preference.findViewById(R.id.settings_account_title);
            TextView summary = preference.findViewById(R.id.settings_account_summary);
            TextView plan = preference.findViewById(R.id.settings_account_plan);
            CardItemView action = preference.findViewById(R.id.settings_account_action);
            title.setTextColor(Ui.mainText(dark));
            summary.setTextColor(Ui.secondaryText(dark));
            plan.setTextColor(Ui.mainText(dark));
            plan.setBackground(Ui.pillBackground(requireContext(), dark));

            AuthTokens tokens = SecureTokenStore.load(requireContext());
            UsageSnapshot snapshot = AppPreferences.loadSnapshot(requireContext());
            title.setText(tokens == null ? AppText.get(R.string.phone_not_connected_8b02f) : AppText.get(R.string.phone_chatgpt_account_b7b4f));
            summary.setText(tokens == null ? AppText.get(R.string.phone_sign_in_from_the_dashboard_6a097)
                    : (tokens.email.isEmpty() ? AppText.get(R.string.phone_connected_c2f9b) : tokens.email));
            if (tokens != null && snapshot != null) {
                String label = UsageFormat.planLabel(snapshot.planType);
                plan.setText(label.isEmpty() ? "Codex" : label);
                plan.setVisibility(View.VISIBLE);
            } else {
                plan.setVisibility(View.GONE);
            }
            action.getTitleView().setText(tokens == null ? AppText.get(R.string.phone_sign_in_with_chatgpt_fe0b3) : AppText.get(R.string.phone_sign_out_dc164));
            action.getTitleView().setTextColor(tokens == null
                    ? Ui.accent(requireContext(), dark)
                    : (dark ? 0xFFFF6B6B : 0xFFFF3B30));
            action.setOnClickListener(view -> {
                if (SecureTokenStore.isSignedIn(requireContext())) {
                    confirmSignOut();
                } else {
                    startActivity(new Intent(requireContext(), MainActivity.class)
                            .putExtra("start_sign_in", true));
                    requireActivity().finish();
                }
            });
        }

        private void confirmSignOut() {
            androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(AppText.get(R.string.phone_sign_out_b1155))
                    .setMessage(AppText.get(R.string.phone_this_removes_encrypted_chatgpt_tokens_and_cached_2d2b6))
                    .setNegativeButton(AppText.get(R.string.widget_config_cancel), null)
                    .setPositiveButton(AppText.get(R.string.phone_sign_out_dc164), (dialogInterface, which) -> {
                        AuthTokens tokens = SecureTokenStore.load(requireContext());
                        SecureTokenStore.clear(requireContext());
                        AppPreferences.clearSnapshot(requireContext());
                        AppPreferences.setOAuthPending(requireContext(), false, "");
                        RefreshScheduler.cancelAll(requireContext());
                        ResetAlertScheduler.cancelAll(requireContext());
                        WidgetRenderer.updateAll(requireContext());
                        Toast.makeText(requireContext(), AppText.get(R.string.phone_signed_out_05d2a), Toast.LENGTH_SHORT).show();
                        requireActivity().recreate();
                        if (tokens != null) {
                            Context app = requireContext().getApplicationContext();
                            new Thread(() -> OAuthClient.revokeBestEffort(app, tokens),
                                    "codex-sign-out").start();
                        }
                    })
                    .create();
            dialog.show();
        }

        private void bindAppearance() {
            String selected = AppPreferences.getAppTheme(requireContext());
            boolean useSystem = WidgetOptions.THEME_SYSTEM.equals(selected);
            HorizontalRadioPreference theme = findPreference("app_theme");
            SwitchPreferenceCompat system = findPreference("theme_system_ui");
            SwitchPreferenceCompat materialYou = findPreference("material_you");
            system.setEnabled(true);
            // Keep "system" persisted while previewing the currently effective light/dark mode.
            theme.setPersistent(false);
            theme.setDividerEnabled(false);
            theme.setTouchEffectEnabled(false);
            theme.setValue(useSystem
                    ? (Ui.isDark(requireContext()) ? WidgetOptions.THEME_DARK : WidgetOptions.THEME_LIGHT)
                    : selected);
            theme.setEnabled(!useSystem);
            system.setChecked(useSystem);
            theme.setOnPreferenceChangeListener((preference, value) -> {
                AppPreferences.setAppTheme(requireContext(), String.valueOf(value));
                requireActivity().recreate();
                return true;
            });
            system.setOnPreferenceChangeListener((preference, value) -> {
                boolean enabled = (Boolean) value;
                AppPreferences.setAppTheme(requireContext(), enabled
                        ? WidgetOptions.THEME_SYSTEM
                        : (Ui.isDark(requireContext())
                                ? WidgetOptions.THEME_DARK : WidgetOptions.THEME_LIGHT));
                requireActivity().recreate();
                return true;
            });
            materialYou.setPersistent(false);
            materialYou.setChecked(AppPreferences.isMaterialYouEnabled(requireContext()));
            materialYou.setOnPreferenceChangeListener((preference, value) -> {
                AppPreferences.setMaterialYouEnabled(requireContext(), (Boolean) value);
                WidgetRenderer.updateAll(requireContext());
                requireActivity().recreate();
                return true;
            });
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

            ListPreference interval = findPreference("refresh_interval_ui");
            interval.setPersistent(false);
            interval.setValue(String.valueOf(AppPreferences.getRefreshMinutes(requireContext())));
            interval.setEnabled(!AppPreferences.getAutomaticRefresh(requireContext()));
            interval.setOnPreferenceChangeListener((preference, value) -> {
                AppPreferences.setRefreshMinutes(requireContext(), Integer.parseInt(String.valueOf(value)));
                RefreshScheduler.schedulePeriodic(requireContext());
                PhoneWearSync.pushSettings(requireContext());
                return true;
            });

            ListPreference mode = findPreference("refresh_mode_ui");
            mode.setPersistent(false);
            mode.setValue(AppPreferences.getAutomaticRefresh(requireContext())
                    ? "automatic" : "manual");
            mode.setOnPreferenceChangeListener((preference, value) -> {
                boolean automatic = "automatic".equals(String.valueOf(value));
                AppPreferences.setAutomaticRefresh(requireContext(), automatic);
                interval.setEnabled(!automatic);
                RefreshScheduler.schedulePeriodic(requireContext());
                PhoneWearSync.pushSettings(requireContext());
                return true;
            });
        }

        private void bindUsagePace() {
            SwitchPreferenceCompat enabled = findPreference("usage_pace_enabled_ui");
            enabled.setPersistent(false);
            enabled.setChecked(UsagePacePreferences.isEnabled(requireContext()));

            usagePaceSensitivityPreference = findPreference("usage_pace_sensitivity_ui");
            usagePaceSensitivityPreference.setPersistent(false);
            usagePaceSensitivityPreference.setValue(
                    UsagePacePreferences.getSensitivity(requireContext()));
            usagePaceSensitivityPreference.setEnabled(
                    UsagePacePreferences.isEnabled(requireContext()));

            enabled.setOnPreferenceChangeListener((preference, value) -> {
                boolean isEnabled = (Boolean) value;
                UsagePacePreferences.setEnabled(requireContext(), isEnabled);
                usagePaceSensitivityPreference.setEnabled(isEnabled);
                updateNowBarAcceleratedEnabledState();
                NowBarManager.onPaceSettingsChanged(requireContext());
                return true;
            });
            usagePaceSensitivityPreference.setOnPreferenceChangeListener((preference, value) -> {
                String sensitivity = UsagePace.normalizeSensitivity(String.valueOf(value));
                UsagePacePreferences.setSensitivity(requireContext(), sensitivity);
                usagePaceSensitivityPreference.setValue(sensitivity);
                updateNowBarAcceleratedEnabledState();
                NowBarManager.onPaceSettingsChanged(requireContext());
                return true;
            });
        }

        private void bindUpdates() {
            updateChannelPreference = findPreference("update_channel_ui");
            updateChannelPreference.setPersistent(false);
            updateChannelPreference.setValue(UpdatePreferences.channel(requireContext()));
            updateChannelPreference.setOnPreferenceChangeListener((preference, value) -> {
                String channel = UpdateChannel.normalize(String.valueOf(value));
                if (channel.equals(UpdatePreferences.channel(requireContext()))) {
                    return true;
                }
                if (UpdateChannel.ALPHA.equals(channel)) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle(AppText.get(R.string.phone_switch_to_the_alpha_channel_f49de))
                            .setMessage(AppText.get(R.string.phone_alpha_builds_ship_faster_with_less_testing_and_m_ac77a))
                            .setNegativeButton(AppText.get(R.string.widget_config_cancel), null)
                            .setPositiveButton(AppText.get(R.string.phone_use_alpha_c590d), (dialog, which) ->
                                    applyUpdateChannel(UpdateChannel.ALPHA))
                            .show();
                    return false;
                }
                applyUpdateChannel(UpdateChannel.STABLE);
                return true;
            });

            automaticUpdatePreference = findPreference("automatic_update_checks_ui");
            automaticUpdatePreference.setPersistent(false);
            automaticUpdatePreference.setChecked(UpdatePreferences.automaticChecks(requireContext()));
            automaticUpdatePreference.setOnPreferenceChangeListener((preference, value) -> {
                boolean enabled = (Boolean) value;
                UpdatePreferences.setAutomaticChecks(requireContext(), enabled);
                if (enabled) {
                    ReleaseUpdateScheduler.ensureScheduled(requireContext());
                } else {
                    ReleaseUpdateScheduler.cancel(requireContext());
                    if (notifyUpdatePreference != null) {
                        notifyUpdatePreference.setChecked(false);
                    }
                }
                updateAutomaticUpdateEnabledState();
                updateAutomaticUpdateSummary();
                return true;
            });

            updateIntervalPreference = findPreference("update_check_interval_ui");
            updateIntervalPreference.setPersistent(false);
            updateIntervalPreference.setValue(
                    String.valueOf(UpdatePreferences.checkIntervalHours(requireContext())));
            updateIntervalPreference.setOnPreferenceChangeListener((preference, value) -> {
                int hours = UpdateCheckFrequency.normalize(Integer.parseInt(String.valueOf(value)));
                UpdatePreferences.setCheckIntervalHours(requireContext(), hours);
                updateIntervalPreference.setValue(String.valueOf(hours));
                ReleaseUpdateScheduler.ensureScheduled(requireContext());
                updateAutomaticUpdateSummary();
                return true;
            });

            notifyUpdatePreference = findPreference("notify_update_available_ui");
            notifyUpdatePreference.setPersistent(false);
            notifyUpdatePreference.setChecked(
                    UpdatePreferences.notifyUpdatesEnabled(requireContext()));
            notifyUpdatePreference.setOnPreferenceChangeListener((preference, value) -> {
                boolean enabled = (Boolean) value;
                if (enabled && !ensureUpdateNotificationPermission()) {
                    return false;
                }
                UpdatePreferences.setNotifyUpdatesEnabled(requireContext(), enabled);
                if (enabled) {
                    UpdateNotificationManager.ensureChannel(requireContext());
                    UpdateNotificationManager.onReleasesUpdated(requireContext());
                }
                return true;
            });

            findPreference("check_for_updates").setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), UpdateActivity.class)
                        .putExtra(UpdateActivity.EXTRA_FORCE_CHECK, true));
                return true;
            });
            findPreference("release_history").setOnPreferenceClickListener(preference -> {
                Ui.startSecondaryActivity(requireActivity(), ReleaseHistoryActivity.class);
                return true;
            });
            updateAutomaticUpdateEnabledState();
            updateAutomaticUpdateSummary();
            updateUpdateSummary();
        }

        private void applyUpdateChannel(String channel) {
            UpdatePreferences.setChannel(requireContext(), channel);
            if (updateChannelPreference != null) {
                updateChannelPreference.setValue(channel);
            }
            updateUpdateSummary();
            startActivity(new Intent(requireContext(), UpdateActivity.class)
                    .putExtra(UpdateActivity.EXTRA_FORCE_CHECK, true));
        }

        private void updateAutomaticUpdateEnabledState() {
            boolean enabled = UpdatePreferences.automaticChecks(requireContext());
            if (updateIntervalPreference != null) {
                updateIntervalPreference.setEnabled(enabled);
            }
            if (notifyUpdatePreference != null) {
                notifyUpdatePreference.setEnabled(enabled);
                if (!enabled) {
                    notifyUpdatePreference.setChecked(false);
                }
            }
        }

        private void updateAutomaticUpdateSummary() {
            if (automaticUpdatePreference == null || getContext() == null) {
                return;
            }
            if (!UpdatePreferences.automaticChecks(requireContext())) {
                automaticUpdatePreference.setSummary(AppText.get(R.string.phone_automatic_github_release_checks_are_off_992de));
                return;
            }
            automaticUpdatePreference.setSummary(PhoneLabels.updateSummary(
                    UpdatePreferences.checkIntervalHours(requireContext())));
        }

        private boolean ensureUpdateNotificationPermission() {
            if (Build.VERSION.SDK_INT >= 33
                    && requireContext().checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 8603);
                Toast.makeText(requireContext(),
                        AppText.get(R.string.phone_allow_notifications_then_enable_update_alerts_ag_c52f5),
                        Toast.LENGTH_LONG).show();
                return false;
            }
            NotificationManager manager = (NotificationManager) requireContext()
                    .getSystemService(NOTIFICATION_SERVICE);
            if (manager != null && manager.areNotificationsEnabled()) {
                return true;
            }
            Toast.makeText(requireContext(),
                    AppText.get(R.string.phone_enable_app_notifications_then_turn_on_update_ale_b7e9e),
                    Toast.LENGTH_LONG).show();
            return false;
        }

        private void updateUpdateSummary() {
            if (getContext() == null) {
                return;
            }
            if (automaticUpdatePreference != null) {
                automaticUpdatePreference.setChecked(
                        UpdatePreferences.automaticChecks(requireContext()));
            }
            if (updateIntervalPreference != null) {
                updateIntervalPreference.setValue(
                        String.valueOf(UpdatePreferences.checkIntervalHours(requireContext())));
            }
            if (notifyUpdatePreference != null) {
                notifyUpdatePreference.setChecked(
                        UpdatePreferences.notifyUpdatesEnabled(requireContext()));
            }
            if (updateChannelPreference != null) {
                updateChannelPreference.setValue(UpdatePreferences.channel(requireContext()));
            }
            updateAutomaticUpdateEnabledState();
            updateAutomaticUpdateSummary();
        }

        private void bindNotifications() {
            notificationLowUsageCategory = findPreference("notification_low_usage_category");
            notificationResetCreditCategory =
                    findPreference("notification_reset_credit_category");
            notificationTroubleshootingCategory =
                    findPreference("notification_troubleshooting_category");
            SwitchPreferenceCompat allow = findPreference("notifications_allowed_ui");
            allow.setEnabled(true);
            allow.setChecked(ResetAlertPreferences.enabled(requireContext()));
            allow.setOnPreferenceChangeListener((preference, value) -> {
                setNotificationsEnabled((Boolean) value);
                return true;
            });
            notificationStylePreference = findPreference("notification_style_ui");
            String currentStyle = ResetAlertPreferences.getStyle(requireContext());
            notificationStylePreference.setValue(ResetAlertPreferences.STYLE_OFF.equals(currentStyle)
                    ? ResetAlertPreferences.STYLE_NOTIFICATION : currentStyle);
            notificationStylePreference.setEnabled(ResetAlertPreferences.enabled(requireContext()));
            notificationStylePreference.setOnPreferenceChangeListener((preference, value) -> {
                String style = String.valueOf(value);
                saveAlert(style, ResetAlertPreferences.getMetric(requireContext()),
                        ResetAlertPreferences.getThreshold(requireContext()));
                return true;
            });

            ListPreference metric = findPreference("notification_metric_ui");
            metric.setValue(ResetAlertPreferences.getMetric(requireContext()));
            metric.setOnPreferenceChangeListener((preference, value) -> {
                saveAlert(ResetAlertPreferences.getStyle(requireContext()), String.valueOf(value),
                        ResetAlertPreferences.getThreshold(requireContext()));
                return true;
            });

            ListPreference threshold = findPreference("notification_threshold_ui");
            threshold.setValue(String.valueOf(ResetAlertPreferences.getThreshold(requireContext())));
            threshold.setOnPreferenceChangeListener((preference, value) -> {
                saveAlert(ResetAlertPreferences.getStyle(requireContext()),
                        ResetAlertPreferences.getMetric(requireContext()), Integer.parseInt(String.valueOf(value)));
                return true;
            });

            SwitchPreferenceCompat unexpectedRefills = findPreference("unexpected_refills_ui");
            unexpectedRefills.setPersistent(false);
            unexpectedRefills.setChecked(ResetAlertPreferences.unexpectedRefillsEnabled(requireContext()));
            unexpectedRefills.setOnPreferenceChangeListener((preference, value) -> {
                ResetAlertPreferences.setUnexpectedRefillsEnabled(requireContext(), (Boolean) value);
                return true;
            });

            SwitchPreferenceCompat resetCreditIncreases = findPreference("reset_credit_increases_ui");
            resetCreditIncreases.setPersistent(false);
            resetCreditIncreases.setChecked(ResetAlertPreferences.resetCreditIncreasesEnabled(requireContext()));
            resetCreditIncreases.setOnPreferenceChangeListener((preference, value) -> {
                ResetAlertPreferences.setResetCreditIncreasesEnabled(requireContext(), (Boolean) value);
                return true;
            });

            SwitchPreferenceCompat resetCreditExpiry =
                    findPreference("reset_credit_expiry_ui");
            resetCreditExpiry.setPersistent(false);
            resetCreditExpiry.setChecked(
                    ResetAlertPreferences.resetCreditExpiryEnabled(requireContext()));
            resetCreditExpiry.setOnPreferenceChangeListener((preference, value) -> {
                boolean enabled = (Boolean) value;
                ResetAlertPreferences.setResetCreditExpiryEnabled(requireContext(), enabled);
                expiryTimesPreference.setEnabled(enabled);
                scheduleResetCreditExpiryReminders();
                return true;
            });

            expiryTimesPreference = findPreference("reset_credit_expiry_times_ui");
            expiryTimesPreference.setEnabled(
                    ResetAlertPreferences.resetCreditExpiryEnabled(requireContext()));
            expiryTimesPreference.setOnPreferenceClickListener(preference -> {
                showExpiryReminderTimesDialog();
                return true;
            });
            updateExpiryTimesSummary();

            permissionPreference = findPreference("notification_permission");
            permissionPreference.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName()));
                return true;
            });
            testNotificationPreference = findPreference("notification_test");
            testNotificationPreference.setOnPreferenceClickListener(preference -> {
                boolean sent = ResetNotificationManager.sendTestNotification(requireContext());
                Toast.makeText(requireContext(), sent
                        ? AppText.get(R.string.phone_test_notification_sent_93829)
                        : AppText.get(R.string.phone_enable_notifications_and_allow_permission_first_d59d3),
                        sent ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                return true;
            });
            updatePermissionSummary();
            updateNotificationEnabledState();
        }

        private void updateNotificationEnabledState() {
            boolean enabled = ResetAlertPreferences.enabled(requireContext());
            if (notificationStylePreference != null) {
                notificationStylePreference.setEnabled(enabled);
            }
            if (notificationLowUsageCategory != null) {
                notificationLowUsageCategory.setVisible(enabled);
            }
            if (notificationResetCreditCategory != null) {
                notificationResetCreditCategory.setVisible(enabled);
            }
            if (notificationTroubleshootingCategory != null) {
                notificationTroubleshootingCategory.setVisible(enabled);
            }
        }

        private void showExpiryReminderTimesDialog() {
            List<Long> leadTimes = ResetAlertPreferences.getResetCreditExpiryLeadTimes(
                    requireContext());
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                    .setTitle(AppText.get(R.string.phone_reminder_times_ff2ab))
                    .setNeutralButton(AppText.get(R.string.phone_add_61cc5), (dialog, which) ->
                            showAddExpiryReminderDialog())
                    .setNegativeButton(AppText.get(R.string.phone_done_e9b45), null);
            if (leadTimes.isEmpty()) {
                builder.setMessage(AppText.get(R.string.phone_no_reminder_times_are_configured_add_one_to_choo_fb5d2));
            } else {
                String[] labels = new String[leadTimes.size()];
                for (int i = 0; i < leadTimes.size(); i++) {
                    labels[i] = formatLeadTime(leadTimes.get(i))
                            + AppText.get(R.string.phone_before_expiry_tap_to_remove_fc503);
                }
                builder.setItems(labels, (dialog, which) -> {
                    List<Long> updated = new ArrayList<>(leadTimes);
                    long removed = updated.remove(which);
                    saveExpiryLeadTimes(updated);
                    Toast.makeText(requireContext(),
                            formatLeadTime(removed) + AppText.get(R.string.phone_reminder_removed_1458b),
                            Toast.LENGTH_SHORT).show();
                });
            }
            builder.show();
        }

        private void showAddExpiryReminderDialog() {
            boolean dark = Ui.isDark(requireContext());
            LinearLayout container = new LinearLayout(requireContext());
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(Ui.dp(requireContext(), 24), Ui.dp(requireContext(), 8),
                    Ui.dp(requireContext(), 24), 0);
            TextView explanation = Ui.text(requireContext(),
                    AppText.get(R.string.phone_notify_me_this_long_before_each_available_reset_8e6cf),
                    14.0f, Ui.secondaryText(dark));
            container.addView(explanation, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout inputRow = Ui.horizontal(requireContext(), 12);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.setMargins(0, Ui.dp(requireContext(), 16), 0, 0);
            EditText amount = new EditText(requireContext());
            amount.setHint(AppText.get(R.string.phone_amount_43dc8));
            amount.setSingleLine(true);
            amount.setTextColor(Ui.mainText(dark));
            amount.setHintTextColor(Ui.secondaryText(dark));
            amount.setInputType(InputType.TYPE_CLASS_NUMBER
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            inputRow.addView(amount, new LinearLayout.LayoutParams(0,
                    Ui.dp(requireContext(), 54), 1.0f));
            String[] units = {AppText.get(R.string.phone_minutes_092f9), AppText.get(R.string.phone_hours_9e25a), AppText.get(R.string.phone_days_f6bb0), AppText.get(R.string.phone_weeks_7d752)};
            Spinner unit = Ui.spinner(requireContext(), units, dark);
            unit.setSelection(1);
            LinearLayout.LayoutParams unitParams = new LinearLayout.LayoutParams(
                    Ui.dp(requireContext(), 142), Ui.dp(requireContext(), 54));
            unitParams.setMargins(Ui.dp(requireContext(), 8), 0, 0, 0);
            inputRow.addView(unit, unitParams);
            container.addView(inputRow, rowParams);

            AlertDialog dialog = new AlertDialog.Builder(requireContext())
                    .setTitle(AppText.get(R.string.phone_add_reminder_time_e5489))
                    .setView(container)
                    .setNegativeButton(AppText.get(R.string.widget_config_cancel), null)
                    .setPositiveButton(AppText.get(R.string.phone_add_61cc5), null)
                    .create();
            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        Long leadTime = parseLeadTime(amount.getText().toString(),
                                unit.getSelectedItemPosition());
                        if (leadTime == null) {
                            amount.setError(AppText.get(R.string.phone_enter_a_time_from_1_minute_to_1_year_in_whole_mi_5ccac));
                            return;
                        }
                        List<Long> updated = new ArrayList<>(
                                ResetAlertPreferences.getResetCreditExpiryLeadTimes(
                                        requireContext()));
                        if (!updated.contains(leadTime)) updated.add(leadTime);
                        saveExpiryLeadTimes(updated);
                        dialog.dismiss();
                    }));
            dialog.show();
        }

        private Long parseLeadTime(String amount, int unitPosition) {
            long[] unitMillis = {
                    TimeUnit.MINUTES.toMillis(1),
                    TimeUnit.HOURS.toMillis(1),
                    TimeUnit.DAYS.toMillis(1),
                    TimeUnit.DAYS.toMillis(7)
            };
            if (amount == null || amount.trim().isEmpty()
                    || unitPosition < 0 || unitPosition >= unitMillis.length) {
                return null;
            }
            try {
                char decimalSeparator = DecimalFormatSymbols.getInstance()
                        .getDecimalSeparator();
                String normalized = decimalSeparator == '.'
                        ? amount.trim() : amount.trim().replace(decimalSeparator, '.');
                long value = new BigDecimal(normalized)
                        .multiply(BigDecimal.valueOf(unitMillis[unitPosition]))
                        .longValueExact();
                return value >= ResetCreditExpiryReminder.MIN_LEAD_TIME_MS
                        && value <= ResetCreditExpiryReminder.MAX_LEAD_TIME_MS
                        && value % ResetCreditExpiryReminder.MIN_LEAD_TIME_MS == 0L
                        ? value : null;
            } catch (ArithmeticException | NumberFormatException exception) {
                return null;
            }
        }

        private void saveExpiryLeadTimes(List<Long> leadTimes) {
            ResetAlertPreferences.setResetCreditExpiryLeadTimes(requireContext(), leadTimes);
            updateExpiryTimesSummary();
            scheduleResetCreditExpiryReminders();
        }

        private void updateExpiryTimesSummary() {
            if (expiryTimesPreference == null) return;
            List<Long> leadTimes = ResetAlertPreferences.getResetCreditExpiryLeadTimes(
                    requireContext());
            if (leadTimes.isEmpty()) {
                expiryTimesPreference.setSummary(AppText.get(R.string.phone_no_reminder_times_configured_b8da5));
                return;
            }
            List<String> labels = new ArrayList<>();
            for (Long leadTime : leadTimes) labels.add(formatLeadTime(leadTime));
            expiryTimesPreference.setSummary(String.join(", ", labels) + AppText.get(R.string.phone_before_expiry_6f388));
        }

        private String formatLeadTime(long millis) {
            if (millis % TimeUnit.DAYS.toMillis(7) == 0L) {
                long weeks = millis / TimeUnit.DAYS.toMillis(7);
                return weeks + AppText.get(R.string.phone_week_a4779) + AppText.nounSuffix(weeks);
            }
            if (millis % TimeUnit.DAYS.toMillis(1) == 0L) {
                long days = millis / TimeUnit.DAYS.toMillis(1);
                return days + AppText.get(R.string.phone_day_80ee8) + AppText.nounSuffix(days);
            }
            if (millis % TimeUnit.HOURS.toMillis(1) == 0L) {
                long hours = millis / TimeUnit.HOURS.toMillis(1);
                return hours + AppText.get(R.string.phone_hour_84c59) + AppText.nounSuffix(hours);
            }
            long minutes = millis / TimeUnit.MINUTES.toMillis(1);
            return minutes + AppText.get(R.string.phone_minute_82d61) + AppText.nounSuffix(minutes);
        }

        private void scheduleResetCreditExpiryReminders() {
            ResetNotificationManager.onResetCreditExpirySettingsChanged(requireContext(),
                    AppPreferences.loadResetCredits(requireContext()));
        }

        private void bindNowBar() {
            nowBarDisplayModePreference = findPreference("now_bar_display_mode_ui");
            nowBarDisplayModePreference.setPersistent(false);
            nowBarDisplayModePreference.setValue(
                    NowBarPreferences.getDisplayMode(requireContext()));
            nowBarDisplayModePreference.setOnPreferenceChangeListener((preference, value) -> {
                String mode = NowBarDisplayMode.normalize(String.valueOf(value));
                NowBarPreferences.setDisplayMode(requireContext(), mode);
                nowBarDisplayModePreference.setValue(mode);
                if (NowBarManager.isActive(requireContext())
                        && !NowBarManager.repostActive(requireContext())) {
                    Toast.makeText(requireContext(),
                            AppText.get(R.string.phone_could_not_refresh_this_display_mode_so_the_monit_12e82),
                            Toast.LENGTH_LONG).show();
                }
                updateNowBarSummary();
                PhoneWearSync.pushSettings(requireContext());
                return true;
            });

            nowBarPercentModePreference = findPreference("now_bar_percent_mode_ui");
            nowBarPercentModePreference.setPersistent(false);
            nowBarPercentModePreference.setValue(
                    NowBarPreferences.getPercentMode(requireContext()));
            nowBarPercentModePreference.setOnPreferenceChangeListener((preference, value) -> {
                String mode = NowBarPercentMode.normalize(String.valueOf(value));
                NowBarPreferences.setPercentMode(requireContext(), mode);
                nowBarPercentModePreference.setValue(mode);
                if (NowBarManager.isActive(requireContext())
                        && !NowBarManager.applyPercentModeChange(requireContext())) {
                    Toast.makeText(requireContext(),
                            AppText.get(R.string.phone_could_not_refresh_the_percentage_mode_so_the_mon_dba50),
                            Toast.LENGTH_LONG).show();
                }
                updateNowBarSummary();
                PhoneWearSync.pushSettings(requireContext());
                return true;
            });

            nowBarMonitorPreference = findPreference("now_bar_monitor_ui");
            nowBarMonitorPreference.setPersistent(false);
            nowBarMonitorPreference.setOnPreferenceChangeListener((preference, value) -> {
                boolean enabled = (Boolean) value;
                if (!enabled) {
                    NowBarManager.stop(requireContext(), true);
                    updateNowBarSummary();
                    PhoneWearSync.pushSettings(requireContext());
                    return true;
                }
                if (!ensureNotificationPermission()) return false;
                boolean started = NowBarManager.start(requireContext());
                if (!started) {
                    Toast.makeText(requireContext(),
                            AppText.get(R.string.phone_refresh_your_signed_in_usage_before_starting_the_0d1c6),
                            Toast.LENGTH_LONG).show();
                }
                updateNowBarSummary();
                View settingsView = getView();
                if (started && settingsView != null) {
                    settingsView.postDelayed(this::updateNowBarSummary, 1500L);
                }
                PhoneWearSync.pushSettings(requireContext());
                return started;
            });

            nowBarAutoStartPreference = findPreference("now_bar_auto_start_ui");
            nowBarAutoStartPreference.setPersistent(false);
            nowBarAutoStartPreference.setChecked(
                    NowBarPreferences.isAutoStartEnabled(requireContext()));
            nowBarAutoStartPreference.setOnPreferenceChangeListener((preference, value) -> {
                boolean enabled = (Boolean) value;
                if (enabled && !ensureNotificationPermission()) return false;
                saveNowBarAutoStart(enabled, NowBarPreferences.getMetric(requireContext()),
                        NowBarPreferences.getThreshold(requireContext()));
                return true;
            });

            nowBarAcceleratedPreference = findPreference("now_bar_accelerated_ui");
            nowBarAcceleratedPreference.setPersistent(false);
            nowBarAcceleratedPreference.setChecked(
                    NowBarPreferences.isAcceleratedStartEnabled(requireContext()));
            nowBarAcceleratedPreference.setOnPreferenceChangeListener((preference, value) -> {
                boolean enabled = (Boolean) value;
                if (enabled && !ensureNotificationPermission()) return false;
                NowBarPreferences.setAcceleratedStartEnabled(requireContext(), enabled);
                if (enabled) NowBarPreferences.clearSuppression(requireContext());
                NowBarManager.onPaceSettingsChanged(requireContext());
                updateNowBarSummary();
                return true;
            });

            nowBarMetricPreference = findPreference("now_bar_metric_ui");
            nowBarMetricPreference.setPersistent(false);
            nowBarMetricPreference.setValue(NowBarPreferences.getMetric(requireContext()));
            nowBarMetricPreference.setOnPreferenceChangeListener((preference, value) -> {
                saveNowBarAutoStart(NowBarPreferences.isAutoStartEnabled(requireContext()),
                        String.valueOf(value),
                        NowBarPreferences.getThreshold(requireContext()));
                return true;
            });

            nowBarThresholdPreference = findPreference("now_bar_threshold_ui");
            nowBarThresholdPreference.setPersistent(false);
            nowBarThresholdPreference.setValue(
                    String.valueOf(NowBarPreferences.getThreshold(requireContext())));
            nowBarThresholdPreference.setOnPreferenceChangeListener((preference, value) -> {
                saveNowBarAutoStart(NowBarPreferences.isAutoStartEnabled(requireContext()),
                        NowBarPreferences.getMetric(requireContext()),
                        Integer.parseInt(String.valueOf(value)));
                return true;
            });

            Preference preview = findPreference("now_bar_preview");
            preview.setVisible((requireContext().getApplicationInfo().flags
                    & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
            preview.setOnPreferenceClickListener(preference -> {
                if (!ensureNotificationPermission()) return true;
                boolean started = NowBarManager.startPreview(requireContext());
                Toast.makeText(requireContext(), started
                        ? AppText.get(R.string.phone_sample_live_update_started_for_20_minutes_654f5)
                        : AppText.get(R.string.phone_allow_notifications_first_98c03),
                        started ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                updateNowBarSummary();
                return true;
            });

            nowBarPermissionPreference = findPreference("now_bar_permission");
            nowBarPermissionPreference.setOnPreferenceClickListener(preference -> {
                if (!NowBarManager.canPostNotifications(requireContext())) {
                    startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE,
                                    requireContext().getPackageName()));
                    return true;
                }
                // AUTO deliberately keeps this screen reachable: a false promotion result can
                // mean either user-disabled access or an OEM policy denial. The API cannot
                // distinguish them, and routing by the Samsung fallback would prevent users
                // from enabling Android Live Updates here.
                if (Build.VERSION.SDK_INT >= 36
                        && !NowBarDisplayMode.SAMSUNG_COMPATIBILITY.equals(
                        NowBarPreferences.getDisplayMode(requireContext()))) {
                    Intent promotion = new Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
                    try {
                        startActivity(promotion);
                        return true;
                    } catch (RuntimeException ignored) {
                    }
                }
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName()));
                return true;
            });
            findPreference("now_bar_setup_help").setOnPreferenceClickListener(preference -> {
                showSamsungNowBarHelp();
                return true;
            });
            updateNowBarAutoStartEnabledState();
            updateNowBarSummary();
        }

        private void showSamsungNowBarHelp() {
            new AlertDialog.Builder(requireContext())
                    .setTitle(AppText.get(R.string.phone_samsung_now_bar_setup_8ae6f))
                    .setMessage(AppText.get(R.string.phone_samsung_help))
                    .setNeutralButton(AppText.get(R.string.phone_developer_options_7ea27), (dialog, which) -> {
                        try {
                            startActivity(new Intent(
                                    Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                        } catch (RuntimeException exception) {
                            Toast.makeText(requireContext(),
                                    AppText.get(R.string.phone_developer_options_are_not_available_on_this_firm_df312),
                                    Toast.LENGTH_LONG).show();
                        }
                    })
                    .setPositiveButton(AppText.get(R.string.phone_done_e9b45), null)
                    .show();
        }

        private void saveNowBarAutoStart(boolean enabled, String metric, int threshold) {
            NowBarPreferences.save(requireContext(), enabled, metric, threshold);
            updateNowBarAutoStartEnabledState();
            if (enabled) {
                NowBarPreferences.clearSuppression(requireContext());
                boolean started = NowBarManager.maybeAutoStart(requireContext(),
                        AppPreferences.loadSnapshot(requireContext()));
                if (started) {
                    Toast.makeText(requireContext(),
                            AppText.get(R.string.phone_live_monitor_started_from_the_current_usage_thre_b2548),
                            Toast.LENGTH_SHORT).show();
                }
            }
            updateNowBarSummary();
            PhoneWearSync.pushSettings(requireContext());
        }

        private void updateNowBarAutoStartEnabledState() {
            boolean enabled = NowBarPreferences.isAutoStartEnabled(requireContext());
            if (nowBarMetricPreference != null) nowBarMetricPreference.setVisible(enabled);
            if (nowBarThresholdPreference != null) nowBarThresholdPreference.setVisible(enabled);
        }

        private void updateNowBarAcceleratedEnabledState() {
            if (nowBarAcceleratedPreference == null || getContext() == null) return;
            nowBarAcceleratedPreference.setEnabled(
                    UsagePacePreferences.areWarningsEnabled(requireContext()));
        }

        private boolean ensureNotificationPermission() {
            if (Build.VERSION.SDK_INT >= 33
                    && requireContext().checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 8602);
                Toast.makeText(requireContext(),
                        AppText.get(R.string.phone_allow_notifications_then_start_the_monitor_again_9a35a), Toast.LENGTH_LONG).show();
                return false;
            }
            if (NowBarManager.canPostNotifications(requireContext())) return true;
            Toast.makeText(requireContext(),
                    AppText.get(R.string.phone_enable_app_notifications_then_start_the_monitor_a9588),
                    Toast.LENGTH_LONG).show();
            return false;
        }

        private void updateNowBarSummary() {
            if (nowBarMonitorPreference == null || getContext() == null) return;
            boolean active = NowBarManager.isActive(requireContext());
            nowBarMonitorPreference.setChecked(active);
            if (active) {
                String kind = NowBarManager.isPreview(requireContext()) ? AppText.get(R.string.phone_sample_preview_58492) : AppText.get(R.string.phone_live_monitor_2a883);
                boolean samsungCompatibility = NowBarDisplayMode.SAMSUNG_COMPATIBILITY.equals(
                        NowBarManager.postedDisplayMode(requireContext()));
                String state = samsungCompatibility
                        ? AppText.get(R.string.phone_using_samsung_compatibility_8a9d6)
                        : Build.VERSION.SDK_INT >= 36
                        ? (NowBarManager.isPromoted(requireContext())
                        ? AppText.get(R.string.phone_promoted_as_a_live_update_3029e)
                        : AppText.get(R.string.phone_active_but_not_promoted_by_the_system_2bb4c))
                        : "active";
                nowBarMonitorPreference.setSummary(kind + " " + state + AppText.get(R.string.phone_ends_324fe)
                        + UsageFormat.absolute(requireContext(), NowBarManager.activeUntil(requireContext()),
                        System.currentTimeMillis()));
            } else if (NowBarPreferences.isAutoStartEnabled(requireContext())
                    || (UsagePacePreferences.areWarningsEnabled(requireContext())
                    && NowBarPreferences.isAcceleratedStartEnabled(requireContext()))) {
                nowBarMonitorPreference.setSummary(
                        AppText.get(R.string.phone_waiting_for_a_low_allowance_or_accelerated_usage_3b3ec));
            } else {
                nowBarMonitorPreference.setSummary(
                        AppText.get(R.string.phone_show_remaining_codex_allowance_until_the_next_av_9775e));
            }
            if (nowBarAutoStartPreference != null) {
                nowBarAutoStartPreference.setChecked(
                        NowBarPreferences.isAutoStartEnabled(requireContext()));
            }
            if (nowBarAcceleratedPreference != null) {
                nowBarAcceleratedPreference.setChecked(
                        NowBarPreferences.isAcceleratedStartEnabled(requireContext()));
                updateNowBarAcceleratedEnabledState();
            }
            if (nowBarDisplayModePreference != null) {
                nowBarDisplayModePreference.setValue(
                        NowBarPreferences.getDisplayMode(requireContext()));
            }
            if (nowBarPercentModePreference != null) {
                nowBarPercentModePreference.setValue(
                        NowBarPreferences.getPercentMode(requireContext()));
            }
            if (nowBarPermissionPreference != null) {
                String summary;
                if (!NowBarManager.canPostNotifications(requireContext())) {
                    summary = AppText.get(R.string.phone_app_or_live_monitor_notifications_disabled_tap_t_c9aa9);
                } else if (NowBarDisplayMode.SAMSUNG_COMPATIBILITY.equals(
                        NowBarManager.postedDisplayMode(requireContext()))) {
                    summary = NowBarDisplayMode.AUTO.equals(
                            NowBarPreferences.getDisplayMode(requireContext()))
                            ? AppText.get(R.string.phone_automatic_using_samsung_fallback_until_android_a_611de)
                            : AppText.get(R.string.phone_samsung_compatibility_selected_firmware_support_9daaf);
                } else if (Build.VERSION.SDK_INT < 36) {
                    summary = AppText.get(R.string.phone_notifications_allowed_live_display_depends_on_yo_d3abc);
                } else if (!NowBarManager.canPostPromotedNotifications(requireContext())) {
                    summary = AppText.get(R.string.phone_live_notifications_not_allowed_tap_to_enable_57223);
                } else if (active && NowBarManager.isPromoted(requireContext())) {
                    summary = AppText.get(R.string.phone_live_notification_promoted_by_android_acbc6);
                } else if (active) {
                    summary = AppText.get(R.string.phone_access_allowed_active_notification_was_not_promo_d4c91);
                } else {
                    summary = AppText.get(R.string.phone_live_notifications_allowed_60d4a);
                }
                nowBarPermissionPreference.setSummary(summary);
            }
        }

        private void setNotificationsEnabled(boolean enabled) {
            String selectedStyle = notificationStylePreference == null
                    ? ResetAlertPreferences.STYLE_NOTIFICATION
                    : notificationStylePreference.getValue();
            saveAlert(enabled ? selectedStyle : ResetAlertPreferences.STYLE_OFF,
                    ResetAlertPreferences.getMetric(requireContext()),
                    ResetAlertPreferences.getThreshold(requireContext()));
            if (enabled && Build.VERSION.SDK_INT >= 33
                    && requireContext().checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 8601);
            }
            if (enabled) {
                ResetNotificationManager.ensureChannel(requireContext());
            } else {
                ResetNotificationManager.clearNotificationHistory(requireContext());
            }
            updateNotificationEnabledState();
        }

        private void saveAlert(String style, String metric, int threshold) {
            ResetAlertPreferences.save(requireContext(), style, metric, threshold);
            if (!ResetAlertPreferences.STYLE_OFF.equals(style)) {
                ResetNotificationManager.ensureChannel(requireContext());
                ResetNotificationManager.onUsageUpdated(requireContext(), AppPreferences.loadSnapshot(requireContext()));
                ResetNotificationManager.onResetCreditsUpdated(requireContext(), AppPreferences.loadResetCredits(requireContext()));
            }
            ResetAlertScheduler.scheduleFromSnapshot(requireContext(), AppPreferences.loadSnapshot(requireContext()));
            scheduleResetCreditExpiryReminders();
        }

        private void updatePermissionSummary() {
            if (permissionPreference == null || getContext() == null) return;
            NotificationManager manager = (NotificationManager) requireContext().getSystemService(NOTIFICATION_SERVICE);
            boolean allowed = manager != null && manager.areNotificationsEnabled()
                    && (Build.VERSION.SDK_INT < 33
                    || requireContext().checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    == PackageManager.PERMISSION_GRANTED);
            permissionPreference.setSummary(allowed ? AppText.get(R.string.phone_allowed_77c7b) : AppText.get(R.string.phone_not_allowed_e0315));
            if (testNotificationPreference != null) {
                testNotificationPreference.setEnabled(allowed && ResetAlertPreferences.enabled(requireContext()));
            }
        }

        private void bindTransfer() {
            findPreference("export_settings_transfer").setOnPreferenceClickListener(preference -> {
                showExportSectionDialog();
                return true;
            });
            findPreference("import_settings_transfer").setOnPreferenceClickListener(preference -> {
                Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/json");
                open.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                        "application/json",
                        "text/plain",
                        "text/json",
                        "*/*"
                });
                try {
                    startActivityForResult(open, REQUEST_IMPORT_TRANSFER);
                } catch (RuntimeException exception) {
                    Toast.makeText(requireContext(),
                            AppText.get(R.string.phone_no_file_picker_is_available_to_import_a_transfer_d3672),
                            Toast.LENGTH_LONG).show();
                }
                return true;
            });
        }

        private void showExportSectionDialog() {
            boolean signedIn = SecureTokenStore.isSignedIn(requireContext());
            String[] labels = {
                    PhoneLabels.translate(SettingsTransfer.sectionTitle(SettingsTransfer.SECTION_APP_SETTINGS))
                            + "\n" + PhoneLabels.translate(SettingsTransfer.sectionSummary(
                            SettingsTransfer.SECTION_APP_SETTINGS)),
                    PhoneLabels.translate(SettingsTransfer.sectionTitle(SettingsTransfer.SECTION_NOTIFICATIONS))
                            + "\n" + PhoneLabels.translate(SettingsTransfer.sectionSummary(
                            SettingsTransfer.SECTION_NOTIFICATIONS)),
                    PhoneLabels.translate(SettingsTransfer.sectionTitle(SettingsTransfer.SECTION_NOW_BAR))
                            + "\n" + PhoneLabels.translate(SettingsTransfer.sectionSummary(
                            SettingsTransfer.SECTION_NOW_BAR)),
                    PhoneLabels.translate(SettingsTransfer.sectionTitle(SettingsTransfer.SECTION_AUTHENTICATION))
                            + "\n" + (signedIn
                            ? PhoneLabels.translate(SettingsTransfer.sectionSummary(
                            SettingsTransfer.SECTION_AUTHENTICATION))
                            : AppText.get(R.string.phone_sign_in_first_to_export_chatgpt_authentication_9925b))
            };
            boolean[] checked = {true, true, true, false};
            new AlertDialog.Builder(requireContext())
                    .setTitle(AppText.get(R.string.phone_export_sections_0c67f))
                    .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                        if (which == 3 && isChecked && !signedIn) {
                            checked[3] = false;
                            ((AlertDialog) dialog).getListView().setItemChecked(3, false);
                            Toast.makeText(requireContext(),
                                    AppText.get(R.string.phone_sign_in_before_exporting_authentication_391f9),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        checked[which] = isChecked;
                    })
                    .setNegativeButton(AppText.get(R.string.widget_config_cancel), null)
                    .setPositiveButton(AppText.get(R.string.phone_continue_2e026), (dialog, which) -> {
                        boolean any = checked[0] || checked[1] || checked[2] || checked[3];
                        if (!any) {
                            Toast.makeText(requireContext(),
                                    AppText.get(R.string.phone_select_at_least_one_section_to_export_2dcfc),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (checked[3]) {
                            confirmSensitiveExport(checked[0], checked[1], checked[2], true);
                        } else {
                            launchExportPicker(checked[0], checked[1], checked[2], false);
                        }
                    })
                    .show();
        }

        private void confirmSensitiveExport(boolean appSettings, boolean notifications,
                boolean nowBar, boolean authentication) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(AppText.get(R.string.phone_authentication_will_be_included_7305e))
                    .setMessage(AppText.get(R.string.phone_transfer_security)
                            + AppText.get(R.string.phone_transfer_own_device))
                    .setNegativeButton(AppText.get(R.string.widget_config_cancel), null)
                    .setPositiveButton(AppText.get(R.string.phone_export_anyway_6dd10), (dialog, which) ->
                            launchExportPicker(appSettings, notifications, nowBar, authentication))
                    .show();
        }

        private void launchExportPicker(boolean appSettings, boolean notifications,
                boolean nowBar, boolean authentication) {
            pendingExportAppSettings = appSettings;
            pendingExportNotifications = notifications;
            pendingExportNowBar = nowBar;
            pendingExportAuthentication = authentication;
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
            String name = pendingExportAuthentication
                    ? "codex-meter-transfer-AUTH-" + stamp + ".json"
                    : "codex-meter-transfer-" + stamp + ".json";
            Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, name);
            try {
                startActivityForResult(create, REQUEST_EXPORT_TRANSFER);
            } catch (RuntimeException exception) {
                Toast.makeText(requireContext(),
                        AppText.get(R.string.phone_no_file_picker_is_available_to_export_a_transfer_982d5),
                        Toast.LENGTH_LONG).show();
            }
        }

        private void finishExport(Uri uri) {
            try {
                SettingsTransfer.Document document = SettingsTransferStore.collect(requireContext(),
                        pendingExportAppSettings, pendingExportNotifications,
                        pendingExportNowBar, pendingExportAuthentication);
                SettingsTransferStore.write(requireContext(), uri, document);
                String message = document.hasAuthentication()
                        ? AppText.get(R.string.phone_exported_keep_this_file_private_it_includes_chat_d3eda)
                        : AppText.get(R.string.phone_settings_exported_22612);
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            } catch (Exception exception) {
                Toast.makeText(requireContext(),
                        exception.getMessage() == null || exception.getMessage().isEmpty()
                                ? AppText.get(R.string.phone_could_not_export_transfer_file_b27ef)
                                : exception.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }

        private void beginImport(Uri uri) {
            try {
                pendingImportDocument = SettingsTransferStore.read(requireContext(), uri);
                showImportSectionDialog(pendingImportDocument);
            } catch (Exception exception) {
                pendingImportDocument = null;
                Toast.makeText(requireContext(),
                        exception.getMessage() == null || exception.getMessage().isEmpty()
                                ? AppText.get(R.string.phone_could_not_read_transfer_file_e43b9)
                                : exception.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }

        private void showImportSectionDialog(SettingsTransfer.Document document) {
            List<String> present = document.presentSections();
            if (present.isEmpty()) {
                Toast.makeText(requireContext(), AppText.get(R.string.phone_this_transfer_file_has_no_sections_e4b48),
                        Toast.LENGTH_LONG).show();
                return;
            }
            String[] labels = new String[present.size()];
            boolean[] checked = new boolean[present.size()];
            for (int i = 0; i < present.size(); i++) {
                String section = present.get(i);
                String warning = SettingsTransfer.isAuthenticationSection(section)
                        ? AppText.get(R.string.phone_transfer_replace_warning)
                        : "";
                labels[i] = PhoneLabels.translate(SettingsTransfer.sectionTitle(section))
                        + "\n" + PhoneLabels.translate(SettingsTransfer.sectionSummary(section)) + warning;
                checked[i] = !SettingsTransfer.isAuthenticationSection(section);
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle(AppText.get(R.string.phone_import_sections_f7c9f))
                    .setMultiChoiceItems(labels, checked,
                            (dialog, which, isChecked) -> checked[which] = isChecked)
                    .setNegativeButton(AppText.get(R.string.widget_config_cancel), null)
                    .setPositiveButton(AppText.get(R.string.phone_continue_2e026), (dialog, which) -> {
                        boolean appSettings = false;
                        boolean notifications = false;
                        boolean nowBar = false;
                        boolean authentication = false;
                        for (int i = 0; i < present.size(); i++) {
                            if (!checked[i]) continue;
                            String section = present.get(i);
                            if (SettingsTransfer.SECTION_APP_SETTINGS.equals(section)) {
                                appSettings = true;
                            } else if (SettingsTransfer.SECTION_NOTIFICATIONS.equals(section)) {
                                notifications = true;
                            } else if (SettingsTransfer.SECTION_NOW_BAR.equals(section)) {
                                nowBar = true;
                            } else if (SettingsTransfer.SECTION_AUTHENTICATION.equals(section)) {
                                authentication = true;
                            }
                        }
                        if (!(appSettings || notifications || nowBar || authentication)) {
                            Toast.makeText(requireContext(),
                                    AppText.get(R.string.phone_select_at_least_one_section_to_import_8df3b),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (authentication) {
                            confirmSensitiveImport(document, appSettings, notifications, nowBar,
                                    true);
                        } else {
                            finishImport(document, appSettings, notifications, nowBar, false);
                        }
                    })
                    .show();
        }

        private void confirmSensitiveImport(SettingsTransfer.Document document,
                boolean appSettings, boolean notifications, boolean nowBar,
                boolean authentication) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(AppText.get(R.string.phone_import_authentication_fa362))
                    .setMessage(AppText.get(R.string.phone_transfer_security)
                            + AppText.get(R.string.phone_transfer_replace))
                    .setNegativeButton(AppText.get(R.string.widget_config_cancel), null)
                    .setPositiveButton(AppText.get(R.string.phone_import_anyway_3ee21), (dialog, which) ->
                            finishImport(document, appSettings, notifications, nowBar,
                                    authentication))
                    .show();
        }

        private void finishImport(SettingsTransfer.Document document, boolean appSettings,
                boolean notifications, boolean nowBar, boolean authentication) {
            try {
                SettingsTransferStore.ApplyResult result = SettingsTransferStore.apply(
                        requireContext(), document, appSettings, notifications, nowBar,
                        authentication);
                pendingImportDocument = null;
                StringBuilder message = new StringBuilder(AppText.get(R.string.phone_imported_eec56));
                for (int i = 0; i < result.appliedSections.size(); i++) {
                    if (i > 0) message.append(", ");
                    message.append(PhoneLabels.translate(SettingsTransfer.sectionTitle(result.appliedSections.get(i))));
                }
                message.append('.');
                if (result.authenticationImported) {
                    message.append(AppText.get(R.string.phone_authentication_replaced_keep_the_file_private_dc559));
                }
                Toast.makeText(requireContext(), message.toString(), Toast.LENGTH_LONG).show();
                requireActivity().recreate();
            } catch (Exception exception) {
                Toast.makeText(requireContext(),
                        exception.getMessage() == null || exception.getMessage().isEmpty()
                                ? AppText.get(R.string.phone_could_not_import_transfer_file_033ff)
                                : exception.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
