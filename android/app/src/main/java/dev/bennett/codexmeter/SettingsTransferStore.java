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
    private SettingsTransferStore() {
    }

    public static SettingsTransfer.Document collect(Context context, boolean includeAppSettings,
            boolean includeNotifications, boolean includeNowBar, boolean includeAuthentication)
            throws Exception {
        if (context == null) {
            throw new IllegalArgumentException("Context is required.");
        }
        if (!includeAppSettings && !includeNotifications && !includeNowBar
                && !includeAuthentication) {
            throw new IllegalArgumentException(AppText.get(R.string.phone_select_at_least_one_section_to_export_2dcfc));
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
                        AppText.get(R.string.phone_no_chatgpt_authentication_is_saved_on_this_devic_7be85));
            }
            authentication = tokens.toJson();
        }
        return SettingsTransfer.create(System.currentTimeMillis(), appSettings, notifications,
                nowBar, authentication);
    }

    public static void write(Context context, Uri uri, SettingsTransfer.Document document)
            throws Exception {
        if (context == null || uri == null || document == null) {
            throw new IllegalArgumentException(AppText.get(R.string.phone_export_target_is_incomplete_71bc1));
        }
        if (!document.hasAnySection()) {
            throw new IllegalArgumentException(AppText.get(R.string.phone_select_at_least_one_section_to_export_2dcfc));
        }
        byte[] bytes = document.toJsonString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) {
                throw new Exception(AppText.get(R.string.phone_could_not_open_the_export_file_for_writing_3dd7e));
            }
            output.write(bytes);
            output.flush();
        }
    }

    public static SettingsTransfer.Document read(Context context, Uri uri) throws Exception {
        if (context == null || uri == null) {
            throw new IllegalArgumentException(AppText.get(R.string.phone_import_source_is_incomplete_90fa3));
        }
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new Exception(AppText.get(R.string.phone_could_not_open_the_import_file_for_reading_a8cc0));
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = input.read(chunk)) >= 0) {
                buffer.write(chunk, 0, read);
                if (buffer.size() > 1024 * 1024) {
                    throw new IllegalArgumentException(AppText.get(R.string.phone_transfer_file_is_too_large_81b1f));
                }
            }
            return SettingsTransfer.parse(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    public static ApplyResult apply(Context context, SettingsTransfer.Document document,
            boolean applyAppSettings, boolean applyNotifications, boolean applyNowBar,
            boolean applyAuthentication) throws Exception {
        if (context == null || document == null) {
            throw new IllegalArgumentException(AppText.get(R.string.phone_import_source_is_incomplete_90fa3));
        }
        validateImport(document, applyAppSettings, applyNotifications, applyNowBar,
                applyAuthentication);
        Context app = context.getApplicationContext();
        List<String> applied = new ArrayList<>();
        boolean themeChanged = false;
        boolean authImported = false;

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
            authImported = true;
        }

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

        if (authImported) {
            new Thread(() -> {
                try {
                    UsageSnapshot snapshot = UsageApi.refreshAndCache(app);
                    RefreshScheduler.scheduleAtNextReset(app, snapshot);
                    WidgetRenderer.updateAll(app);
                } catch (Exception exception) {
                    String message = exception.getMessage();
                    if (message == null || message.trim().isEmpty()) {
                        message = AppText.get(R.string.phone_imported_authentication_could_not_refresh_usage_d9f3e);
                    }
                    AppPreferences.setLastError(app, message);
                    WidgetRenderer.updateAll(app);
                }
            }, "codex-transfer-refresh").start();
        }

        return new ApplyResult(applied, themeChanged, authImported);
    }

    /** Validate every selected section before the first preference or credential write. */
    private static void validateImport(SettingsTransfer.Document document, boolean appSettings,
            boolean notifications, boolean nowBar, boolean authentication) throws Exception {
        if (!appSettings && !notifications && !nowBar && !authentication) {
            throw new IllegalArgumentException(AppText.get(R.string.phone_select_at_least_one_section_to_import_8df3b));
        }
        if (appSettings && !document.hasAppSettings()) {
            throw new IllegalArgumentException(AppText.get(R.string.phone_this_file_has_no_app_settings_to_import_602e0));
        }
        if (notifications && !document.hasNotifications()) {
            throw new IllegalArgumentException(AppText.get(R.string.phone_this_file_has_no_notification_settings_to_import_e8434));
        }
        if (nowBar && !document.hasNowBar()) {
            throw new IllegalArgumentException(AppText.get(R.string.phone_this_file_has_no_now_bar_settings_to_import_dbcd6));
        }
        if (authentication && !document.hasAuthentication()) {
            throw new IllegalArgumentException(AppText.get(R.string.phone_this_file_has_no_authentication_to_import_18627));
        }
        if (notifications && document.notifications.has("reset_credit_expiry_lead_times")) {
            SettingsTransfer.requireLeadTimes(document.notifications, "reset_credit_expiry_lead_times");
        }
        if (authentication && !AuthTokens.fromJson(document.authentication).isUsable()) {
            throw new IllegalArgumentException(AppText.get(R.string.phone_imported_authentication_is_incomplete_or_invalid_2f91b));
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
        json.put("reset_credit_expiry_lead_times", SettingsTransfer.leadTimesToJson(
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
        boolean automatic = json.optBoolean("automatic_update_checks",
                UpdatePreferences.automaticChecks(context));
        UpdatePreferences.setAutomaticChecks(context, automatic);
        UpdatePreferences.setCheckIntervalHours(context, json.optInt("check_interval_hours",
                UpdatePreferences.checkIntervalHours(context)));
        UpdatePreferences.setNotifyUpdatesEnabled(context,
                json.optBoolean("notify_updates", UpdatePreferences.notifyUpdatesEnabled(context)));
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
        final List<Long> leadTimes = json.has("reset_credit_expiry_lead_times")
                ? SettingsTransfer.requireLeadTimes(json, "reset_credit_expiry_lead_times")
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
            ResetNotificationManager.onUsageUpdated(context, AppPreferences.loadSnapshot(context));
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
                    AppText.get(R.string.phone_imported_authentication_is_incomplete_or_invalid_2f91b));
        }
        SecureTokenStore.save(context, tokens);
        AppPreferences.clearSnapshot(context);
        AppPreferences.setOAuthPending(context, false, "");
        AppPreferences.completeOnboarding(context);
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
