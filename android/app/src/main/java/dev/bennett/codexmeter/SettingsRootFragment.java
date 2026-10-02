package dev.bennett.codexmeter;

import android.content.Context;
import android.content.Intent;
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
public final class SettingsRootFragment extends SettingsPageFragment {
    private static final int SIGN_OUT_COLOR_DARK = 0xFFFF6B6B;
    private static final int SIGN_OUT_COLOR_LIGHT = 0xFFFF3B30;
    private static final int AVATAR_PADDING_DP = 10;
    private static final int MINUTES_PER_HOUR = 60;

    @Override
    void onCreatePage() {
        addPreferencesFromResource(R.xml.preferences_settings);
        bindAccount();
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
        updateSummaries();
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
        title.setTextColor(Ui.mainText(dark));
        summary.setTextColor(Ui.secondaryText(dark));
        plan.setTextColor(Ui.mainText(dark));
        plan.setBackground(Ui.pillBackground(context, dark));

        AuthTokens tokens = SecureTokenStore.load(context);
        UsageSnapshot snapshot = AppPreferences.loadSnapshot(context);
        boolean signedIn = tokens != null;
        title.setText(signedIn ? "ChatGPT account" : "Not connected");
        if (!signedIn) {
            summary.setText("Sign in from the dashboard");
        } else {
            summary.setText(tokens.email.isEmpty() ? "Connected" : tokens.email);
        }
        if (signedIn && snapshot != null) {
            String label = UsageFormat.planLabel(snapshot.planType);
            plan.setText(label.isEmpty() ? "Codex" : label);
            plan.setVisibility(View.VISIBLE);
        } else {
            plan.setVisibility(View.GONE);
        }
        action.getTitleView().setText(signedIn ? "Sign out" : "Sign in with ChatGPT");
        action.getTitleView().setTextColor(signedIn
                ? (dark ? SIGN_OUT_COLOR_DARK : SIGN_OUT_COLOR_LIGHT)
                : Ui.accent(context, dark));
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

    private void styleAvatar(ImageView avatar, boolean dark) {
        Context context = requireContext();
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Ui.controlSurface(context, dark));
        avatar.setBackground(background);
        int padding = Ui.dp(context, AVATAR_PADDING_DP);
        avatar.setPadding(padding, padding, padding, padding);
        avatar.setImageResource(R.drawable.ic_oui_contact_outline);
        avatar.setColorFilter(Ui.mainText(dark));
    }

    private void confirmSignOut() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Sign out?")
                .setMessage("This removes encrypted ChatGPT tokens and cached usage from this "
                        + "device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Sign out", (dialog, which) -> signOut())
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
        showToast("Signed out.", Toast.LENGTH_SHORT);
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
        String themeLabel;
        if (WidgetOptions.THEME_SYSTEM.equals(theme)) {
            themeLabel = "System default";
        } else if (WidgetOptions.THEME_DARK.equals(theme)) {
            themeLabel = "Dark";
        } else {
            themeLabel = "Light";
        }
        return themeLabel + " · Material You "
                + (AppPreferences.isMaterialYouEnabled(context) ? "on" : "off");
    }

    private static String refreshUsageSummary(Context context) {
        boolean automatic = AppPreferences.getAutomaticRefresh(context);
        int refreshMinutes = automatic
                ? RefreshScheduler.effectiveRefreshMinutes(context)
                : AppPreferences.getRefreshMinutes(context);
        String refreshLabel = refreshIntervalLabel(refreshMinutes);
        if (automatic) {
            refreshLabel = "Automatic · currently " + refreshLabel;
        }
        return refreshLabel + " · " + estimatesSummary(context);
    }

    private static String refreshIntervalLabel(int minutes) {
        if (minutes < MINUTES_PER_HOUR) {
            return minutes + " minutes";
        }
        if (minutes == MINUTES_PER_HOUR) {
            return "Hourly";
        }
        return "Every " + (minutes / MINUTES_PER_HOUR) + " hours";
    }

    private static String estimatesSummary(Context context) {
        if (!UsagePacePreferences.isEnabled(context)) {
            return "Estimates off";
        }
        if (!UsagePacePreferences.areWarningsEnabled(context)) {
            return "Estimates on · Warnings off";
        }
        return "Estimates on";
    }

    private static String notificationsSummary(Context context) {
        if (!ResetAlertPreferences.enabled(context)) {
            return "Off";
        }
        return "On · " + metricLabel(ResetAlertPreferences.getMetric(context))
                + " at " + ResetAlertPreferences.getThreshold(context) + "%";
    }

    private static String metricLabel(String metric) {
        if ("five_hour".equals(metric)) {
            return "5-hour";
        }
        if ("weekly".equals(metric)) {
            return "Weekly";
        }
        return "Both limits";
    }

    private static String nowBarSummary(Context context) {
        if (NowBarManager.isActive(context)) {
            return "Live monitor active";
        }
        if (NowBarPreferences.isAutoStartEnabled(context)) {
            return "Automatic · starts at " + NowBarPreferences.getThreshold(context) + "%";
        }
        return "Manual start";
    }

    private static String updatesSummary(Context context) {
        GitHubRelease availableUpdate = UpdatePreferences.availableUpdate(context);
        String channelSuffix = UpdateChannel.isAlpha(UpdatePreferences.channel(context))
                ? " · Alpha channel" : "";
        String status;
        if (availableUpdate != null) {
            status = "v" + availableUpdate.version + " available";
        } else if (UpdatePreferences.automaticChecks(context)) {
            status = "Automatic · " + UpdateCheckFrequency.label(
                    UpdatePreferences.checkIntervalHours(context));
        } else {
            status = "Automatic checks off";
        }
        return status + channelSuffix;
    }
}
