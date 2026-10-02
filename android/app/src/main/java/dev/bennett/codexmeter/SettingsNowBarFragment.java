package dev.bennett.codexmeter;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
import dev.bennett.codexmeter.wear.PhoneWearSync;

/**
 * Now Bar page: the live usage monitor, its display and percentage modes, automatic and
 * accelerated-usage starts, and shortcuts to the system Live Update settings.
 */
public final class SettingsNowBarFragment extends SettingsPageFragment {
    private static final int REQUEST_NOTIFICATION_PERMISSION = 8602;
    /** Delay before re-reading the monitor state after a manual start has posted it. */
    private static final long STARTED_SUMMARY_RECHECK_DELAY_MS = 1500L;
    private static final String SAMSUNG_HELP = "For Android Live Updates:\n"
            + "1. Open Settings > About phone/tablet > Software information.\n"
            + "2. Tap Build number seven times and confirm your screen lock.\n"
            + "3. Return to Settings > Developer options.\n"
            + "4. Turn on Live notifications for all apps.\n"
            + "5. Make sure Settings > Lock screen and AOD > Now bar is enabled.\n\n"
            + "If “Live notifications for all apps” is missing, select Samsung "
            + "compatibility above. Samsung changes third-party access by model, "
            + "region, and firmware build even when Android and One UI versions "
            + "match.\n\n"
            + "If both modes remain ordinary notifications, that firmware or "
            + "device does not expose a third-party Now Bar surface. Codex Meter "
            + "cannot override Samsung’s system allowlist.";

    private SwitchPreferenceCompat monitorPreference;
    private SwitchPreferenceCompat autoStartPreference;
    private SwitchPreferenceCompat acceleratedPreference;
    private ListPreference displayModePreference;
    private ListPreference percentModePreference;
    private ListPreference metricPreference;
    private ListPreference thresholdPreference;
    private Preference permissionPreference;

    @Override
    void onCreatePage() {
        addPreferencesFromResource(R.xml.preferences_settings_now_bar);
        bindDisplayModes();
        bindMonitor();
        bindAutoStart();
        bindPreview();
        bindSystemSettings();
        updateAutoStartEnabledState();
        updateSummary();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!NowBarManager.refreshActiveNotificationContract(requireContext())) {
            showToast("Could not refresh the live notification, so the monitor was stopped.",
                    Toast.LENGTH_LONG);
        }
        updateSummary();
    }

    private void bindDisplayModes() {
        displayModePreference = findPreference("now_bar_display_mode_ui");
        displayModePreference.setPersistent(false);
        displayModePreference.setValue(NowBarPreferences.getDisplayMode(requireContext()));
        displayModePreference.setOnPreferenceChangeListener((preference, value) -> {
            String mode = NowBarDisplayMode.normalize(String.valueOf(value));
            NowBarPreferences.setDisplayMode(requireContext(), mode);
            displayModePreference.setValue(mode);
            if (NowBarManager.isActive(requireContext())
                    && !NowBarManager.repostActive(requireContext())) {
                showToast("Could not refresh this display mode, so the monitor was stopped.",
                        Toast.LENGTH_LONG);
            }
            updateSummary();
            PhoneWearSync.pushSettings(requireContext());
            return true;
        });

        percentModePreference = findPreference("now_bar_percent_mode_ui");
        percentModePreference.setPersistent(false);
        percentModePreference.setValue(NowBarPreferences.getPercentMode(requireContext()));
        percentModePreference.setOnPreferenceChangeListener((preference, value) -> {
            String mode = NowBarPercentMode.normalize(String.valueOf(value));
            NowBarPreferences.setPercentMode(requireContext(), mode);
            percentModePreference.setValue(mode);
            if (NowBarManager.isActive(requireContext())
                    && !NowBarManager.applyPercentModeChange(requireContext())) {
                showToast("Could not refresh the percentage mode, so the monitor was stopped.",
                        Toast.LENGTH_LONG);
            }
            updateSummary();
            PhoneWearSync.pushSettings(requireContext());
            return true;
        });
    }

    private void bindMonitor() {
        monitorPreference = findPreference("now_bar_monitor_ui");
        monitorPreference.setPersistent(false);
        monitorPreference.setOnPreferenceChangeListener(
                (preference, value) -> setMonitorRunning((Boolean) value));
    }

    /** Starts or stops the monitor; returns whether the switch should accept the change. */
    private boolean setMonitorRunning(boolean running) {
        if (!running) {
            NowBarManager.stop(requireContext(), true);
            updateSummary();
            PhoneWearSync.pushSettings(requireContext());
            return true;
        }
        if (!ensureNotificationPermission()) {
            return false;
        }
        boolean started = NowBarManager.start(requireContext());
        if (!started) {
            showToast("Refresh your signed-in usage before starting the monitor.",
                    Toast.LENGTH_LONG);
        }
        updateSummary();
        View settingsView = getView();
        if (started && settingsView != null) {
            settingsView.postDelayed(this::updateSummary, STARTED_SUMMARY_RECHECK_DELAY_MS);
        }
        PhoneWearSync.pushSettings(requireContext());
        return started;
    }

    private void bindAutoStart() {
        autoStartPreference = findPreference("now_bar_auto_start_ui");
        autoStartPreference.setPersistent(false);
        autoStartPreference.setChecked(NowBarPreferences.isAutoStartEnabled(requireContext()));
        autoStartPreference.setOnPreferenceChangeListener((preference, value) -> {
            boolean enabled = (Boolean) value;
            if (enabled && !ensureNotificationPermission()) {
                return false;
            }
            saveAutoStart(enabled, NowBarPreferences.getMetric(requireContext()),
                    NowBarPreferences.getThreshold(requireContext()));
            return true;
        });

        acceleratedPreference = findPreference("now_bar_accelerated_ui");
        acceleratedPreference.setPersistent(false);
        acceleratedPreference.setChecked(
                NowBarPreferences.isAcceleratedStartEnabled(requireContext()));
        acceleratedPreference.setOnPreferenceChangeListener((preference, value) -> {
            boolean enabled = (Boolean) value;
            if (enabled && !ensureNotificationPermission()) {
                return false;
            }
            NowBarPreferences.setAcceleratedStartEnabled(requireContext(), enabled);
            if (enabled) {
                NowBarPreferences.clearSuppression(requireContext());
            }
            NowBarManager.onPaceSettingsChanged(requireContext());
            updateSummary();
            return true;
        });

        metricPreference = findPreference("now_bar_metric_ui");
        metricPreference.setPersistent(false);
        metricPreference.setValue(NowBarPreferences.getMetric(requireContext()));
        metricPreference.setOnPreferenceChangeListener((preference, value) -> {
            saveAutoStart(NowBarPreferences.isAutoStartEnabled(requireContext()),
                    String.valueOf(value),
                    NowBarPreferences.getThreshold(requireContext()));
            return true;
        });

        thresholdPreference = findPreference("now_bar_threshold_ui");
        thresholdPreference.setPersistent(false);
        thresholdPreference.setValue(
                String.valueOf(NowBarPreferences.getThreshold(requireContext())));
        thresholdPreference.setOnPreferenceChangeListener((preference, value) -> {
            saveAutoStart(NowBarPreferences.isAutoStartEnabled(requireContext()),
                    NowBarPreferences.getMetric(requireContext()),
                    Integer.parseInt(String.valueOf(value)));
            return true;
        });
    }

    /** The sample Live Update preview is only offered in debuggable builds. */
    private void bindPreview() {
        Preference preview = findPreference("now_bar_preview");
        preview.setVisible((requireContext().getApplicationInfo().flags
                & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
        preview.setOnPreferenceClickListener(preference -> {
            if (!ensureNotificationPermission()) {
                return true;
            }
            boolean started = NowBarManager.startPreview(requireContext());
            if (started) {
                showToast("Sample Live Update started for 20 minutes.", Toast.LENGTH_SHORT);
            } else {
                showToast("Allow notifications first.", Toast.LENGTH_LONG);
            }
            updateSummary();
            return true;
        });
    }

    private void bindSystemSettings() {
        permissionPreference = findPreference("now_bar_permission");
        permissionPreference.setOnPreferenceClickListener(preference -> {
            openLiveNotificationSettings();
            return true;
        });
        findPreference("now_bar_setup_help").setOnPreferenceClickListener(preference -> {
            showSamsungNowBarHelp();
            return true;
        });
    }

    private void openLiveNotificationSettings() {
        if (!NowBarManager.canPostNotifications(requireContext())) {
            openAppNotificationSettings();
            return;
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
                return;
            } catch (RuntimeException ignored) {
                // Fall back to the general notification settings below.
            }
        }
        openAppNotificationSettings();
    }

    private void showSamsungNowBarHelp() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Samsung Now Bar setup")
                .setMessage(SAMSUNG_HELP)
                .setNeutralButton("Developer options", (dialog, which) -> openDeveloperOptions())
                .setPositiveButton("Done", null)
                .show();
    }

    private void openDeveloperOptions() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (RuntimeException exception) {
            showToast("Developer options are not available on this firmware.",
                    Toast.LENGTH_LONG);
        }
    }

    private void saveAutoStart(boolean enabled, String metric, int threshold) {
        NowBarPreferences.save(requireContext(), enabled, metric, threshold);
        updateAutoStartEnabledState();
        if (enabled) {
            NowBarPreferences.clearSuppression(requireContext());
            boolean started = NowBarManager.maybeAutoStart(requireContext(),
                    AppPreferences.loadSnapshot(requireContext()));
            if (started) {
                showToast("Live monitor started from the current usage threshold.",
                        Toast.LENGTH_SHORT);
            }
        }
        updateSummary();
        PhoneWearSync.pushSettings(requireContext());
    }

    /** The automatic-start metric and threshold only matter while automatic start is on. */
    private void updateAutoStartEnabledState() {
        boolean enabled = NowBarPreferences.isAutoStartEnabled(requireContext());
        if (metricPreference != null) {
            metricPreference.setVisible(enabled);
        }
        if (thresholdPreference != null) {
            thresholdPreference.setVisible(enabled);
        }
    }

    private void updateAcceleratedEnabledState() {
        if (acceleratedPreference == null || getContext() == null) {
            return;
        }
        acceleratedPreference.setEnabled(
                UsagePacePreferences.areWarningsEnabled(requireContext()));
    }

    private boolean ensureNotificationPermission() {
        if (lacksNotificationPermission()) {
            requestNotificationPermission(REQUEST_NOTIFICATION_PERMISSION);
            showToast("Allow notifications, then start the monitor again.", Toast.LENGTH_LONG);
            return false;
        }
        if (NowBarManager.canPostNotifications(requireContext())) {
            return true;
        }
        showToast("Enable app notifications, then start the monitor again.", Toast.LENGTH_LONG);
        return false;
    }

    private void updateSummary() {
        if (monitorPreference == null || getContext() == null) {
            return;
        }
        Context context = requireContext();
        boolean active = NowBarManager.isActive(context);
        monitorPreference.setChecked(active);
        monitorPreference.setSummary(monitorSummary(context, active));
        if (autoStartPreference != null) {
            autoStartPreference.setChecked(NowBarPreferences.isAutoStartEnabled(context));
        }
        if (acceleratedPreference != null) {
            acceleratedPreference.setChecked(
                    NowBarPreferences.isAcceleratedStartEnabled(context));
            updateAcceleratedEnabledState();
        }
        if (displayModePreference != null) {
            displayModePreference.setValue(NowBarPreferences.getDisplayMode(context));
        }
        if (percentModePreference != null) {
            percentModePreference.setValue(NowBarPreferences.getPercentMode(context));
        }
        if (permissionPreference != null) {
            permissionPreference.setSummary(permissionSummary(context, active));
        }
    }

    private static String monitorSummary(Context context, boolean active) {
        if (active) {
            String kind = NowBarManager.isPreview(context) ? "Sample preview" : "Live monitor";
            return kind + " " + activeMonitorState(context) + " · ends "
                    + UsageFormat.absolute(context, NowBarManager.activeUntil(context),
                            System.currentTimeMillis());
        }
        if (NowBarPreferences.isAutoStartEnabled(context)
                || (UsagePacePreferences.areWarningsEnabled(context)
                && NowBarPreferences.isAcceleratedStartEnabled(context))) {
            return "Waiting for a low allowance or accelerated usage trigger";
        }
        return "Show remaining Codex allowance until the next available usage reset";
    }

    private static String activeMonitorState(Context context) {
        if (NowBarDisplayMode.SAMSUNG_COMPATIBILITY.equals(
                NowBarManager.postedDisplayMode(context))) {
            return "using Samsung compatibility";
        }
        if (Build.VERSION.SDK_INT < 36) {
            return "active";
        }
        return NowBarManager.isPromoted(context)
                ? "promoted as a Live Update"
                : "active, but not promoted by the system";
    }

    private static String permissionSummary(Context context, boolean active) {
        if (!NowBarManager.canPostNotifications(context)) {
            return "App or live-monitor notifications disabled · tap to enable";
        }
        if (NowBarDisplayMode.SAMSUNG_COMPATIBILITY.equals(
                NowBarManager.postedDisplayMode(context))) {
            return NowBarDisplayMode.AUTO.equals(NowBarPreferences.getDisplayMode(context))
                    ? "Automatic · using Samsung fallback until Android access is allowed"
                    : "Samsung compatibility selected · firmware support required";
        }
        if (Build.VERSION.SDK_INT < 36) {
            return "Notifications allowed · Live display depends on your device";
        }
        if (!NowBarManager.canPostPromotedNotifications(context)) {
            return "Live notifications not allowed · tap to enable";
        }
        if (active && NowBarManager.isPromoted(context)) {
            return "Live notification promoted by Android";
        }
        if (active) {
            return "Access allowed · active notification was not promoted";
        }
        return "Live notifications allowed";
    }
}
