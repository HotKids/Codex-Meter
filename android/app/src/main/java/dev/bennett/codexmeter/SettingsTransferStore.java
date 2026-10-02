package dev.bennett.codexmeter;

import android.content.Context;
import android.net.Uri;
import dev.bennett.codexmeter.wear.PhoneWearSync;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

/** Reads and writes {@link SettingsTransfer} documents against on-device preferences. */
public final class SettingsTransferStore {
    private static final String KEY_LEAD_TIMES =
            SettingsTransfer.KEY_RESET_CREDIT_EXPIRY_LEAD_TIMES;
    private static final int MAX_TRANSFER_BYTES = 1024 * 1024;
    private static final int READ_BUFFER_BYTES = 4096;

    private SettingsTransferStore() {
    }

    // ---------------------------------------------------------------------------------------
    // Export
    // ---------------------------------------------------------------------------------------

    public static SettingsTransfer.Document collect(Context context, boolean includeAppSettings,
            boolean includeNotifications, boolean includeNowBar, boolean includeAuthentication)
            throws Exception {
        if (context == null) {
            throw new IllegalArgumentException("Context is required.");
        }
        if (!includeAppSettings && !includeNotifications && !includeNowBar
                && !includeAuthentication) {
            throw new IllegalArgumentException(
                    context.getString(R.string.settings_transfer_select_export));
        }
        Context app = context.getApplicationContext();
        JSONObject appSettings = includeAppSettings ? collectAppSettings(app) : null;
        JSONObject notifications = includeNotifications ? collectNotifications(app) : null;
        JSONObject nowBar = includeNowBar ? collectNowBar(app) : null;
        JSONObject authentication = null;
        if (includeAuthentication) {
            AuthTokens tokens = SecureTokenStore.load(app);
            if (tokens == null || !tokens.isUsable()) {
                throw new IllegalStateException(
                        context.getString(R.string.settings_transfer_error_no_auth_to_export));
            }
            authentication = tokens.toJson();
        }
        return SettingsTransfer.create(System.currentTimeMillis(), appSettings, notifications,
                nowBar, authentication);
    }

    public static void write(Context context, Uri uri, SettingsTransfer.Document document)
            throws Exception {
        if (context == null) {
            // No resources to localize with; unreachable from the settings page.
            throw new IllegalArgumentException("Export target is incomplete.");
        }
        if (uri == null || document == null) {
            throw new IllegalArgumentException(
                    context.getString(R.string.settings_transfer_error_export_target));
        }
        if (!document.hasAnySection()) {
            throw new IllegalArgumentException(
                    context.getString(R.string.settings_transfer_select_export));
        }
        byte[] bytes = document.toJsonString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) {
                throw new Exception(
                        context.getString(R.string.settings_transfer_error_open_export));
            }
            output.write(bytes);
            output.flush();
        }
    }

    private static JSONObject collectAppSettings(Context context) throws Exception {
        JSONObject json = new JSONObject();
        json.put("app_theme", AppPreferences.getAppTheme(context));
        json.put("material_you", AppPreferences.isMaterialYouEnabled(context));
        json.put("automatic_refresh", AppPreferences.getAutomaticRefresh(context));
        json.put("refresh_minutes", AppPreferences.getRefreshMinutes(context));
        json.put("refresh_on_launch", AppPreferences.getRefreshOnLaunch(context));
        json.put("dashboard_five_hour", AppPreferences.showDashboardFiveHour(context));
        json.put("dashboard_weekly", AppPreferences.showDashboardWeekly(context));
        json.put("dashboard_monthly", AppPreferences.showDashboardMonthly(context));
        json.put("dashboard_additional_limits",
                AppPreferences.showDashboardAdditionalLimits(context));
        json.put("dashboard_usage_credits", AppPreferences.showDashboardUsageCredits(context));
        json.put("dashboard_reset_credits", AppPreferences.showDashboardResetCredits(context));
        json.put("dashboard_usage_history", AppPreferences.showDashboardUsageHistory(context));
        json.put("dashboard_hidden_sections",
                AppPreferences.getDashboardHiddenSections(context));
        json.put("history_section_overrides",
                AppPreferences.getHistorySectionOverrides(context));
        json.put("usage_pace_enabled", UsagePacePreferences.isEnabled(context));
        json.put("usage_pace_sensitivity", UsagePacePreferences.getSensitivity(context));
        json.put("automatic_update_checks", UpdatePreferences.automaticChecks(context));
        json.put("update_channel", UpdatePreferences.channel(context));
        json.put("notify_updates", UpdatePreferences.notifyUpdatesEnabled(context));
        json.put("check_interval_hours", UpdatePreferences.checkIntervalHours(context));
        json.put("default_widget", SettingsTransfer.widgetOptionsToJson(
                AppPreferences.loadDefaultWidgetOptions(context)));
        return json;
    }

    private static JSONObject collectNotifications(Context context) throws Exception {
        JSONObject json = new JSONObject();
        json.put("style", ResetAlertPreferences.getStyle(context));
        json.put("metric", ResetAlertPreferences.getMetric(context));
        json.put("threshold", ResetAlertPreferences.getThreshold(context));
        json.put("unexpected_refills", ResetAlertPreferences.unexpectedRefillsEnabled(context));
        json.put("reset_credit_increases",
                ResetAlertPreferences.resetCreditIncreasesEnabled(context));
        json.put("reset_credit_expiry", ResetAlertPreferences.resetCreditExpiryEnabled(context));
        json.put(KEY_LEAD_TIMES, SettingsTransfer.leadTimesToJson(
                ResetAlertPreferences.getResetCreditExpiryLeadTimes(context)));
        return json;
    }

    private static JSONObject collectNowBar(Context context) throws Exception {
        JSONObject json = new JSONObject();
        json.put("display_mode", NowBarPreferences.getDisplayMode(context));
        json.put("percent_mode", NowBarPreferences.getPercentMode(context));
        json.put("auto_enabled", NowBarPreferences.isAutoStartEnabled(context));
        json.put("accelerated_enabled",
                NowBarPreferences.isAcceleratedStartEnabled(context));
        json.put("metric", NowBarPreferences.getMetric(context));
        json.put("threshold", NowBarPreferences.getThreshold(context));
        return json;
    }

    // ---------------------------------------------------------------------------------------
    // Import
    // ---------------------------------------------------------------------------------------

    public static SettingsTransfer.Document read(Context context, Uri uri) throws Exception {
        if (context == null) {
            // No resources to localize with; unreachable from the settings page.
            throw new IllegalArgumentException("Import source is incomplete.");
        }
        if (uri == null) {
            throw new IllegalArgumentException(
                    context.getString(R.string.settings_transfer_error_import_source));
        }
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new Exception(
                        context.getString(R.string.settings_transfer_error_open_import));
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[READ_BUFFER_BYTES];
            int read;
            while ((read = input.read(chunk)) >= 0) {
                buffer.write(chunk, 0, read);
                if (buffer.size() > MAX_TRANSFER_BYTES) {
                    throw new IllegalArgumentException(
                            context.getString(R.string.settings_transfer_error_too_large));
                }
            }
            return SettingsTransfer.parse(
                    new String(buffer.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    /**
     * Applies the selected sections of {@code document} in order, then reschedules everything
     * that depends on them.
     */
    public static ApplyResult apply(Context context, SettingsTransfer.Document document,
            boolean applyAppSettings, boolean applyNotifications, boolean applyNowBar,
            boolean applyAuthentication) throws Exception {
        if (context == null) {
            // No resources to localize with; unreachable from the settings page.
            throw new IllegalArgumentException("Import source is incomplete.");
        }
        if (document == null) {
            throw new IllegalArgumentException(
                    context.getString(R.string.settings_transfer_error_import_source));
        }
        validateImport(context, document, applyAppSettings, applyNotifications, applyNowBar,
                applyAuthentication);
        Context app = context.getApplicationContext();
        List<String> applied = new ArrayList<>();
        boolean themeChanged = false;

        if (applyAppSettings) {
            themeChanged = applyAppSettings(app, document.appSettings);
            applied.add(SettingsTransfer.SECTION_APP_SETTINGS);
        }
        if (applyNotifications) {
            applyNotifications(app, document.notifications);
            applied.add(SettingsTransfer.SECTION_NOTIFICATIONS);
        }
        if (applyNowBar) {
            applyNowBar(app, document.nowBar);
            applied.add(SettingsTransfer.SECTION_NOW_BAR);
        }
        if (applyAuthentication) {
            applyAuthentication(app, document.authentication);
            applied.add(SettingsTransfer.SECTION_AUTHENTICATION);
        }

        rescheduleAfterImport(app, applyAppSettings, applyNowBar);
        if (applyAuthentication) {
            refreshUsageInBackground(app);
        }
        return new ApplyResult(applied, themeChanged, applyAuthentication);
    }

    /**
     * Checks every selected section before anything is written, so a missing section, bad
     * reminder lead times or unusable tokens cannot leave a half-applied import behind.
     */
    private static void validateImport(Context context, SettingsTransfer.Document document,
            boolean applyAppSettings, boolean applyNotifications, boolean applyNowBar,
            boolean applyAuthentication) throws Exception {
        if (!applyAppSettings && !applyNotifications && !applyNowBar && !applyAuthentication) {
            throw new IllegalArgumentException(
                    context.getString(R.string.settings_transfer_select_import));
        }
        if (applyAppSettings) {
            requireSection(context, document.hasAppSettings(),
                    R.string.settings_transfer_error_no_app_settings);
        }
        if (applyNotifications) {
            requireSection(context, document.hasNotifications(),
                    R.string.settings_transfer_error_no_notifications);
            if (document.notifications.has(KEY_LEAD_TIMES)) {
                SettingsTransfer.requireLeadTimes(document.notifications, KEY_LEAD_TIMES);
            }
        }
        if (applyNowBar) {
            requireSection(context, document.hasNowBar(),
                    R.string.settings_transfer_error_no_now_bar);
        }
        if (applyAuthentication) {
            requireSection(context, document.hasAuthentication(),
                    R.string.settings_transfer_error_no_authentication);
            if (!AuthTokens.fromJson(document.authentication).isUsable()) {
                throw new IllegalArgumentException(
                        context.getString(R.string.settings_transfer_error_auth_invalid));
            }
        }
    }

    private static void requireSection(Context context, boolean present, int missingMessage) {
        if (!present) {
            throw new IllegalArgumentException(context.getString(missingMessage));
        }
    }

    /** Returns whether the app theme or Material You setting changed. */
    private static boolean applyAppSettings(Context context, JSONObject json) throws Exception {
        String previousTheme = AppPreferences.getAppTheme(context);
        boolean previousMaterialYou = AppPreferences.isMaterialYouEnabled(context);
        String theme = json.optString("app_theme", previousTheme);
        AppPreferences.setAppTheme(context, theme);
        AppPreferences.setMaterialYouEnabled(context,
                json.optBoolean("material_you", previousMaterialYou));
        AppPreferences.setAutomaticRefresh(context, json.optBoolean("automatic_refresh",
                AppPreferences.getAutomaticRefresh(context)));
        AppPreferences.setRefreshMinutes(context, json.optInt("refresh_minutes",
                AppPreferences.getRefreshMinutes(context)));
        AppPreferences.setRefreshOnLaunch(context, json.optBoolean("refresh_on_launch",
                AppPreferences.getRefreshOnLaunch(context)));
        AppPreferences.setDashboardVisibility(
                context,
                json.optBoolean("dashboard_five_hour",
                        AppPreferences.showDashboardFiveHour(context)),
                json.optBoolean("dashboard_weekly",
                        AppPreferences.showDashboardWeekly(context)),
                json.optBoolean("dashboard_monthly",
                        AppPreferences.showDashboardMonthly(context)),
                json.optBoolean("dashboard_additional_limits",
                        AppPreferences.showDashboardAdditionalLimits(context)),
                json.optBoolean("dashboard_usage_credits",
                        AppPreferences.showDashboardUsageCredits(context)),
                json.optBoolean("dashboard_reset_credits",
                        AppPreferences.showDashboardResetCredits(context)),
                json.optBoolean("dashboard_usage_history",
                        AppPreferences.showDashboardUsageHistory(context)));
        AppPreferences.setDashboardHiddenSections(context,
                json.optString("dashboard_hidden_sections",
                        AppPreferences.getDashboardHiddenSections(context)));
        AppPreferences.setHistorySectionOverrides(context,
                json.optString("history_section_overrides",
                        AppPreferences.getHistorySectionOverrides(context)));
        UsagePacePreferences.setEnabled(context, json.optBoolean("usage_pace_enabled",
                UsagePacePreferences.isEnabled(context)));
        UsagePacePreferences.setSensitivity(context, json.optString("usage_pace_sensitivity",
                UsagePacePreferences.getSensitivity(context)));
        UpdatePreferences.setAutomaticChecks(context, json.optBoolean("automatic_update_checks",
                UpdatePreferences.automaticChecks(context)));
        UpdatePreferences.setCheckIntervalHours(context, json.optInt("check_interval_hours",
                UpdatePreferences.checkIntervalHours(context)));
        UpdatePreferences.setNotifyUpdatesEnabled(context, json.optBoolean("notify_updates",
                UpdatePreferences.notifyUpdatesEnabled(context)));
        UpdatePreferences.setChannel(context, json.optString("update_channel",
                UpdatePreferences.channel(context)));
        JSONObject widget = json.optJSONObject("default_widget");
        if (widget != null) {
            AppPreferences.saveDefaultWidgetOptions(context,
                    SettingsTransfer.widgetOptionsFromJson(widget,
                            AppPreferences.loadDefaultWidgetOptions(context)));
        }
        return !previousTheme.equals(AppPreferences.getAppTheme(context))
                || previousMaterialYou != AppPreferences.isMaterialYouEnabled(context);
    }

    private static void applyNotifications(Context context, JSONObject json) throws Exception {
        // Fail closed before any preference writes: malformed lead times must leave
        // style/metric/threshold/enablement flags completely untouched.
        final List<Long> leadTimes = json.has(KEY_LEAD_TIMES)
                ? SettingsTransfer.requireLeadTimes(json, KEY_LEAD_TIMES)
                : null;
        ResetAlertPreferences.save(context,
                json.optString("style", ResetAlertPreferences.getStyle(context)),
                json.optString("metric", ResetAlertPreferences.getMetric(context)),
                json.optInt("threshold", ResetAlertPreferences.getThreshold(context)));
        ResetAlertPreferences.setUnexpectedRefillsEnabled(context,
                json.optBoolean("unexpected_refills",
                        ResetAlertPreferences.unexpectedRefillsEnabled(context)));
        ResetAlertPreferences.setResetCreditIncreasesEnabled(context,
                json.optBoolean("reset_credit_increases",
                        ResetAlertPreferences.resetCreditIncreasesEnabled(context)));
        ResetAlertPreferences.setResetCreditExpiryEnabled(context,
                json.optBoolean("reset_credit_expiry",
                        ResetAlertPreferences.resetCreditExpiryEnabled(context)));
        if (leadTimes != null) {
            ResetAlertPreferences.setResetCreditExpiryLeadTimes(context, leadTimes);
        }
        if (ResetAlertPreferences.enabled(context)) {
            ResetNotificationManager.ensureChannel(context);
            ResetNotificationManager.onUsageUpdated(context,
                    AppPreferences.loadSnapshot(context));
            ResetNotificationManager.onResetCreditsUpdated(context,
                    AppPreferences.loadResetCredits(context));
        } else {
            ResetNotificationManager.clearNotificationHistory(context);
        }
    }

    private static void applyNowBar(Context context, JSONObject json) {
        NowBarPreferences.setDisplayMode(context, json.optString("display_mode",
                NowBarPreferences.getDisplayMode(context)));
        NowBarPreferences.setPercentMode(context, json.optString("percent_mode",
                NowBarPreferences.getPercentMode(context)));
        NowBarPreferences.save(context,
                json.optBoolean("auto_enabled", NowBarPreferences.isAutoStartEnabled(context)),
                json.optString("metric", NowBarPreferences.getMetric(context)),
                json.optInt("threshold", NowBarPreferences.getThreshold(context)));
        NowBarPreferences.setAcceleratedStartEnabled(context,
                json.optBoolean("accelerated_enabled",
                        NowBarPreferences.isAcceleratedStartEnabled(context)));
        NowBarPreferences.clearSuppression(context);
        if (NowBarPreferences.isAutoStartEnabled(context)
                || NowBarPreferences.isAcceleratedStartEnabled(context)) {
            NowBarManager.maybeAutoStart(context, AppPreferences.loadSnapshot(context));
        }
    }

    private static void applyAuthentication(Context context, JSONObject json) throws Exception {
        AuthTokens tokens = AuthTokens.fromJson(json);
        if (!tokens.isUsable()) {
            throw new IllegalArgumentException(
                    context.getString(R.string.settings_transfer_error_auth_invalid));
        }
        SecureTokenStore.save(context, tokens);
        AppPreferences.clearSnapshot(context);
        AppPreferences.setOAuthPending(context, false, "");
        AppPreferences.completeOnboarding(context);
    }

    /** Re-arms schedules, alerts, the Now Bar, widgets, and the watch for the new settings. */
    private static void rescheduleAfterImport(Context app, boolean applyAppSettings,
            boolean applyNowBar) {
        RefreshScheduler.schedulePeriodic(app);
        ResetAlertScheduler.scheduleFromSnapshot(app, AppPreferences.loadSnapshot(app));
        ResetNotificationManager.onResetCreditExpirySettingsChanged(app,
                AppPreferences.loadResetCredits(app));
        if (UpdatePreferences.automaticChecks(app)) {
            ReleaseUpdateScheduler.ensureScheduled(app);
        } else {
            ReleaseUpdateScheduler.cancel(app);
        }
        // Only reconcile an active Now Bar when that section was imported. Unrelated
        // imports must not stop a live monitor if a transient repost fails.
        if (applyNowBar && NowBarManager.isActive(app) && !NowBarManager.repostActive(app)) {
            NowBarManager.stop(app, false);
        }
        if (applyAppSettings) {
            NowBarManager.onPaceSettingsChanged(app);
        }
        WidgetRenderer.updateAll(app);
        PhoneWearSync.pushSettings(app);
    }

    /** Fetches usage with newly imported credentials off the calling thread. */
    private static void refreshUsageInBackground(Context app) {
        new Thread(() -> {
            try {
                UsageSnapshot snapshot = UsageApi.refreshAndCache(app);
                RefreshScheduler.scheduleAtNextReset(app, snapshot);
                WidgetRenderer.updateAll(app);
            } catch (Exception exception) {
                String message = exception.getLocalizedMessage();
                if (message == null || message.trim().isEmpty()) {
                    message = app.getString(R.string.settings_transfer_error_refresh_failed);
                }
                AppPreferences.setLastError(app, message);
                WidgetRenderer.updateAll(app);
            }
        }, "codex-transfer-refresh").start();
    }

    public static final class ApplyResult {
        public final List<String> appliedSections;
        public final boolean themeChanged;
        public final boolean authenticationImported;

        ApplyResult(List<String> appliedSections, boolean themeChanged,
                boolean authenticationImported) {
            this.appliedSections = appliedSections == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(appliedSections));
            this.themeChanged = themeChanged;
            this.authenticationImported = authenticationImported;
        }
    }
}
