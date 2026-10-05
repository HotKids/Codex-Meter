package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.WidgetMeters;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import dev.oneuiproject.oneui.preference.LayoutPreference;
import dev.oneuiproject.oneui.widget.CardItemView;
import dev.oneuiproject.oneui.widget.RoundedLinearLayout;

/** Root settings list: the ChatGPT account card plus a summarized link to every sub-page. */
public final class SettingsRootFragment extends SettingsPageFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int SIGN_OUT_COLOR_DARK = 0xFFFF6B6B;
    private static final int SIGN_OUT_COLOR_LIGHT = 0xFFFF3B30;
    private static final int AVATAR_PADDING_DP = 10;
    private static final int MINUTES_PER_HOUR = 60;

    @Override
    void onCreatePage() {
        addPreferencesFromResource(R.xml.preferences_settings);
        bindAccount();
        findPreference("settings_home_display").setOnPreferenceClickListener(preference -> {
            Ui.startSecondaryActivity(requireActivity(), DashboardReorderActivity.class);
            return true;
        });
        bindPageLink("settings_appearance", SettingsActivity.PAGE_APPEARANCE);
        bindPageLink("settings_refresh_usage", SettingsActivity.PAGE_REFRESH_USAGE);
        bindPageLink("settings_notifications", SettingsActivity.PAGE_NOTIFICATIONS);
        bindPageLink("settings_now_bar", SettingsActivity.PAGE_NOW_BAR);
        bindPageLink("settings_updates", SettingsActivity.PAGE_UPDATES);
        bindPageLink("settings_transfer", SettingsActivity.PAGE_TRANSFER);
        bindPageLink("settings_privacy", SettingsActivity.PAGE_PRIVACY);
        findPreference("about_codex_meter").setOnPreferenceClickListener(preference -> {
            Ui.startSecondaryActivity(requireActivity(), AboutActivity.class);
            return true;
        });
        updateSummaries();
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
        bindAccount();
        updateSummaries();
    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        // Unrelated refresh bookkeeping must not reread credentials, and queued callbacks may
        // arrive after this page pauses. A cleared store changes both account-related fields.
        if (isResumed() && (key == null || "reauthentication_required".equals(key)
                || "last_snapshot".equals(key))) {
            bindAccount();
        }
    }

    private void bindPageLink(String key, String page) {
        findPreference(key).setOnPreferenceClickListener(preference -> {
            DiagnosticLog.info(requireContext(), "user", "settings_page_opened", "page", page);
            startActivity(SettingsActivity.pageIntent(requireContext(), page));
            return true;
        });
    }

    private void bindAccount() {
        Context context = requireContext();
        boolean dark = Ui.isDark(context);
        LayoutPreference preference = findPreference("account_card");
        RoundedLinearLayout card = preference.findViewById(R.id.settings_account_card);
        card.setBackground(Ui.card(context, dark).getBackground());
        styleAvatar(preference.findViewById(R.id.settings_account_avatar), dark);
        TextView title = preference.findViewById(R.id.settings_account_title);
        TextView summary = preference.findViewById(R.id.settings_account_summary);
        TextView plan = preference.findViewById(R.id.settings_account_plan);
        CardItemView action = preference.findViewById(R.id.settings_account_action);
        title.setTextColor(Ui.mainText(context, dark));
        summary.setTextColor(Ui.secondaryText(dark));
        plan.setTextColor(Ui.mainText(context, dark));
        plan.setBackground(Ui.pillBackground(context, dark));

        AuthTokens tokens = SecureTokenStore.load(context);
        UsageSnapshot snapshot = AppPreferences.loadSnapshot(context);
        boolean signedIn = tokens != null;
        boolean reauthenticate = !signedIn || AppPreferences.isReauthenticationRequired(context);
        title.setText(signedIn
                ? R.string.settings_account_title_signed_in
                : R.string.settings_account_title_signed_out);
        if (!signedIn) {
            summary.setText(R.string.settings_account_summary_signed_out);
        } else if (tokens.email.isEmpty()) {
            summary.setText(R.string.settings_account_summary_connected);
        } else {
            summary.setText(tokens.email);
        }
        if (!reauthenticate) {
            String label = snapshot == null ? "" : UsageFormat.planLabel(snapshot.planType);
            if (label.isEmpty()) {
                plan.setText(R.string.settings_account_plan_fallback);
            } else {
                plan.setText(label);
            }
            plan.setOnClickListener(null);
            plan.setClickable(false);
            plan.setFocusable(false);
        } else {
            plan.setText(R.string.settings_account_sign_in_again);
            plan.setOnClickListener(view -> openSignIn(true));
            plan.setFocusable(true);
        }
        plan.setVisibility(View.VISIBLE);
        action.getTitleView().setText(signedIn
                ? R.string.settings_account_sign_out
                : R.string.settings_account_sign_in);
        action.getTitleView().setTextColor(signedIn
                ? (dark ? SIGN_OUT_COLOR_DARK : SIGN_OUT_COLOR_LIGHT)
                : Ui.accent(context, dark));
        action.setOnClickListener(view -> {
            if (SecureTokenStore.isSignedIn(requireContext())) {
                confirmSignOut();
            } else {
                openSignIn(false);
            }
        });
    }

    private void openSignIn(boolean reauthenticate) {
        startActivity(new Intent(requireContext(), MainActivity.class)
                .putExtra("start_sign_in", true)
                .putExtra(OAuthService.EXTRA_REAUTHENTICATE, reauthenticate));
        requireActivity().finish();
    }

    private void styleAvatar(ImageView avatar, boolean dark) {
        Context context = requireContext();
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Ui.controlSurface(context, dark));
        avatar.setBackground(background);
        int padding = Ui.dp(context, AVATAR_PADDING_DP);
        avatar.setPadding(padding, padding, padding, padding);
        avatar.setImageResource(R.drawable.ic_ms_account_circle);
        avatar.setColorFilter(Ui.mainText(context, dark));
    }

    private void confirmSignOut() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_sign_out_dialog_title)
                .setMessage(R.string.settings_sign_out_dialog_message)
                .setNegativeButton(R.string.settings_dialog_cancel, null)
                .setPositiveButton(R.string.settings_account_sign_out,
                        (dialog, which) -> signOut())
                .show();
    }

    private void signOut() {
        Context context = requireContext();
        AuthTokens tokens = SecureTokenStore.load(context);
        SecureTokenStore.clear(context);
        AppPreferences.clearSnapshot(context);
        AppPreferences.setOAuthPending(context, false, "");
        RefreshScheduler.cancelAll(context);
        ResetAlertScheduler.cancelAll(context);
        WidgetRenderer.updateAll(context);
        showToast(getString(R.string.settings_signed_out), Toast.LENGTH_SHORT);
        requireActivity().recreate();
        if (tokens != null) {
            // Server-side revocation is best effort and must not block the UI thread.
            Context app = context.getApplicationContext();
            new Thread(() -> OAuthClient.revokeBestEffort(app, tokens), "codex-sign-out").start();
        }
    }

    private void updateSummaries() {
        if (getContext() == null) {
            return;
        }
        Context context = requireContext();
        findPreference("settings_appearance").setSummary(appearanceSummary(context));
        findPreference("settings_refresh_usage").setSummary(refreshUsageSummary(context));
        findPreference("settings_notifications").setSummary(notificationsSummary(context));
        findPreference("settings_now_bar").setSummary(nowBarSummary(context));
        findPreference("settings_updates").setSummary(updatesSummary(context));
    }

    private static String appearanceSummary(Context context) {
        String theme = AppPreferences.getAppTheme(context);
        String themeLabel = WidgetOptions.THEME_SYSTEM.equals(theme)
                ? context.getString(R.string.settings_theme_system_default)
                : arrayLabel(context, R.array.preferences_darkmode_entries,
                        R.array.preferences_darkmode_values,
                        WidgetOptions.THEME_DARK.equals(theme)
                                ? WidgetOptions.THEME_DARK : WidgetOptions.THEME_LIGHT);
        return context.getString(AppPreferences.isMaterialYouEnabled(context)
                ? R.string.settings_summary_appearance_material_you_on
                : R.string.settings_summary_appearance_material_you_off, themeLabel);
    }

    private static String refreshUsageSummary(Context context) {
        boolean automatic = AppPreferences.getAutomaticRefresh(context);
        int refreshMinutes = automatic
                ? RefreshScheduler.effectiveRefreshMinutes(context)
                : AppPreferences.getRefreshMinutes(context);
        return context.getString(automatic
                        ? R.string.settings_summary_refresh_adaptive
                        : R.string.settings_summary_refresh_fixed,
                refreshIntervalLabel(context, refreshMinutes));
    }

    private static String refreshIntervalLabel(Context context, int minutes) {
        if (minutes < MINUTES_PER_HOUR) {
            return context.getResources().getQuantityString(
                    R.plurals.settings_refresh_interval_minutes, minutes, minutes);
        }
        if (minutes == MINUTES_PER_HOUR) {
            return context.getString(R.string.settings_refresh_interval_hourly);
        }
        int hours = minutes / MINUTES_PER_HOUR;
        return context.getResources().getQuantityString(
                R.plurals.settings_refresh_interval_every_hours, hours, hours);
    }

    private static String notificationsSummary(Context context) {
        if (!ResetAlertPreferences.enabled(context)) {
            return context.getString(R.string.settings_summary_notifications_off);
        }
        return context.getString(R.string.settings_summary_notifications_on,
                metricLabel(context, ResetAlertPreferences.getMetric(context)),
                ResetAlertPreferences.getThreshold(context));
    }

    private static String metricLabel(Context context, String metric) {
        if ("five_hour".equals(metric)) {
            return context.getString(R.string.settings_summary_metric_five_hour);
        }
        if ("weekly".equals(metric)) {
            return context.getString(WidgetMeters.weeklyMeterIsMonthly(
                    AppPreferences.loadSnapshot(context))
                    ? R.string.dashboard_window_monthly : R.string.settings_summary_metric_weekly);
        }
        return context.getString(R.string.settings_summary_metric_both);
    }

    private static String nowBarSummary(Context context) {
        if (NowBarManager.isActive(context)) {
            return context.getString(R.string.settings_summary_now_bar_active);
        }
        if (NowBarPreferences.isAutoStartEnabled(context)) {
            return context.getString(R.string.settings_summary_now_bar_automatic,
                    NowBarPreferences.getThreshold(context));
        }
        if (UsagePacePreferences.areWarningsEnabled(context)
                && NowBarPreferences.isAcceleratedStartEnabled(context)) {
            return context.getString(R.string.alerts_now_bar_summary_waiting);
        }
        return context.getString(R.string.settings_summary_now_bar_manual);
    }

    private static String updatesSummary(Context context) {
        GitHubRelease availableUpdate = UpdatePreferences.availableUpdate(context);
        String status;
        if (availableUpdate != null) {
            status = context.getString(R.string.settings_summary_updates_available,
                    availableUpdate.version);
        } else if (UpdatePreferences.automaticChecks(context)) {
            int hours = UpdateCheckFrequency.normalize(
                    UpdatePreferences.checkIntervalHours(context));
            status = context.getString(R.string.settings_summary_updates_automatic,
                    arrayLabel(context, R.array.settings_update_interval_entries,
                            R.array.settings_update_interval_values, String.valueOf(hours)));
        } else {
            status = context.getString(R.string.settings_summary_updates_checks_off);
        }
        return UpdateChannel.isAlpha(UpdatePreferences.channel(context))
                ? context.getString(R.string.settings_summary_updates_alpha, status)
                : status;
    }

    /** The localized list entry whose stored value is {@code value}; the value if none match. */
    private static String arrayLabel(Context context, int entriesRes, int valuesRes,
            String value) {
        String[] entries = context.getResources().getStringArray(entriesRes);
        String[] values = context.getResources().getStringArray(valuesRes);
        for (int i = 0; i < values.length && i < entries.length; i++) {
            if (values[i].equals(value)) {
                return entries[i];
            }
        }
        return value;
    }
}
