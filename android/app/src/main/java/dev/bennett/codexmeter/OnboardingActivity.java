package dev.bennett.codexmeter;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import dev.oneuiproject.oneui.widget.CardItemView;
import dev.oneuiproject.oneui.widget.RoundedLinearLayout;

/** First-run setup built from the same One UI Design Library primitives as the app. */
public final class OnboardingActivity extends AppCompatActivity {
    public static final String EXTRA_AUTH_RETURN = "oauth_return";

    private LinearLayout content;
    private Ui.Page page;
    private boolean dark;
    private int step;
    private boolean receiverRegistered;
    private boolean oauthRequested;
    private String authMessage = "";
    private String lastLaunchedAuthUrl = "";

    private final BroadcastReceiver authReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent == null ? null : intent.getAction();
            if (AppConstants.ACTION_OAUTH_READY.equals(action)) {
                String url = intent.getStringExtra(AppConstants.EXTRA_AUTH_URL);
                if (url != null && !url.isEmpty()) {
                    authMessage = AppText.get(R.string.phone_your_secure_chatgpt_sign_in_is_open_in_the_brows_95ca5);
                    render();
                    openAuthUrl(url);
                }
                return;
            }
            if (AppConstants.ACTION_OAUTH_RESULT.equals(action)) {
                oauthRequested = false;
                boolean success = intent.getBooleanExtra(AppConstants.EXTRA_SUCCESS, false);
                String message = intent.getStringExtra(AppConstants.EXTRA_MESSAGE);
                if (success || SecureTokenStore.isSignedIn(OnboardingActivity.this)) {
                    showStep(OnboardingFlow.STEP_COMPLETE);
                } else {
                    authMessage = message == null || message.trim().isEmpty()
                            ? AppText.get(R.string.phone_sign_in_did_not_complete_please_try_again_fd59a)
                            : message;
                    showStep(OnboardingFlow.STEP_ACCOUNT);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle bundle) {
        Ui.applySelectedTheme(this);
        super.onCreate(bundle);
        if (AppPreferences.isOnboardingComplete(this)) {
            openMain();
            return;
        }
        this.dark = Ui.isDark(this);
        this.page = Ui.installPage(this, "Codex Meter", false);
        this.content = this.page.content;
        findViewById(R.id.dashboard_refresh).setEnabled(false);
        boolean oauthReturn = getIntent().getBooleanExtra(EXTRA_AUTH_RETURN, false);
        this.oauthRequested = AppPreferences.isOAuthPending(this);
        this.step = OnboardingFlow.initialStep(
                AppPreferences.getOnboardingStep(this),
                SecureTokenStore.isSignedIn(this),
                oauthReturn);
        if (oauthReturn && !SecureTokenStore.isSignedIn(this)) {
            this.authMessage = AppText.get(R.string.phone_sign_in_did_not_complete_you_can_safely_try_agai_0ad58);
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (step > OnboardingFlow.STEP_WELCOME) {
                    goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
        render();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        this.oauthRequested = AppPreferences.isOAuthPending(this);
        if (SecureTokenStore.isSignedIn(this)) {
            showStep(OnboardingFlow.STEP_COMPLETE);
        } else if (intent.getBooleanExtra(EXTRA_AUTH_RETURN, false)) {
            this.authMessage = AppText.get(R.string.phone_sign_in_did_not_complete_you_can_safely_try_agai_0ad58);
            showStep(OnboardingFlow.STEP_ACCOUNT);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (this.content != null && SecureTokenStore.isSignedIn(this)
                && this.step != OnboardingFlow.STEP_COMPLETE) {
            showStep(OnboardingFlow.STEP_COMPLETE);
        }
    }

    @Override
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(AppConstants.ACTION_OAUTH_READY);
        filter.addAction(AppConstants.ACTION_OAUTH_RESULT);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(this.authReceiver, filter, AppConstants.INTERNAL_PERMISSION, null,
                        Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(this.authReceiver, filter, AppConstants.INTERNAL_PERMISSION, null);
            }
            this.receiverRegistered = true;
        } catch (RuntimeException exception) {
            this.receiverRegistered = false;
            this.authMessage = "Sign-in updates are unavailable: " + safeMessage(exception);
            render();
        }
    }

    @Override
    protected void onStop() {
        if (this.receiverRegistered) {
            try {
                unregisterReceiver(this.authReceiver);
            } catch (RuntimeException ignored) {
            }
            this.receiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    public boolean onSupportNavigateUp() {
        goBack();
        return true;
    }

    private void render() {
        if (this.content == null) return;
        this.content.removeAllViews();
        this.page.toolbar.setTitle("Codex Meter");
        this.page.toolbar.setShowNavigationButtonAsBack(this.step > OnboardingFlow.STEP_WELCOME);

        addProgress();
        if (this.step == OnboardingFlow.STEP_WELCOME) {
            buildWelcome();
        } else if (this.step == OnboardingFlow.STEP_USAGE) {
            buildUsage();
        } else if (this.step == OnboardingFlow.STEP_ACCOUNT) {
            buildAccount();
        } else {
            buildComplete();
        }
        NestedScrollView scroll = findViewById(R.id.dashboard_scroll);
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void addProgress() {
        TextView label = Ui.text(this, getString(R.string.onboarding_step, this.step + 1, OnboardingFlow.STEP_COUNT), 12.0f, Ui.accent(this, this.dark));
        label.setTypeface(Ui.mediumTypeface(this));
        label.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.setMargins(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 10));
        this.content.addView(label, labelParams);

        ProgressBar progress = Ui.progress(this, this.dark);
        progress.setProgress((this.step + 1) * 100 / OnboardingFlow.STEP_COUNT);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 5));
        progressParams.setMargins(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 26));
        this.content.addView(progress, progressParams);
    }

    private void buildWelcome() {
        addIntro(AppText.get(R.string.phone_meet_codex_meter_02aa9),
                AppText.get(R.string.phone_your_chatgpt_codex_allowance_reset_timing_and_av_b83bb),
                R.drawable.ic_oui_battery);

        Ui.addSpacer(this.content, 20);
        RoundedLinearLayout card = Ui.seslCard(this, this.dark);
        TextView title = Ui.text(this, AppText.get(R.string.phone_built_to_feel_at_home_on_galaxy_58ecf), 18.0f,
                Ui.mainText(this.dark));
        title.setTypeface(Ui.mediumTypeface(this));
        card.addView(title);
        TextView body = Ui.text(this,
                AppText.get(R.string.phone_reachable_layouts_responsive_cards_system_themin_7af8a),
                15.0f, Ui.secondaryText(this.dark));
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.setMargins(0, Ui.dp(this, 10), 0, 0);
        card.addView(body, bodyParams);
        this.content.addView(card);
        addPrimaryAction(AppText.get(R.string.phone_continue_2e026), () -> showStep(OnboardingFlow.STEP_USAGE));
    }

    private void buildUsage() {
        addIntro(AppText.get(R.string.phone_everything_important_at_a_glance_279cd),
                AppText.get(R.string.phone_see_what_remains_without_digging_through_chatgpt_7d47f),
                R.drawable.ic_oui_time);

        this.content.addView(Ui.separator(this, AppText.get(R.string.phone_what_you_get_8ac7a)));
        RoundedLinearLayout features = Ui.seslRowCard(this, this.dark);
        CardItemView limits = Ui.actionRow(this, AppText.get(R.string.phone_live_codex_limits_46a57),
                AppText.get(R.string.phone_five_hour_and_weekly_allowance_with_reset_timing_a1b87),
                R.drawable.ic_oui_calendar_week, null);
        limits.setShowBottomDivider(true);
        features.addView(limits);
        CardItemView widgets = Ui.actionRow(this, AppText.get(R.string.phone_native_one_ui_widgets_f8f78),
                AppText.get(R.string.phone_at_a_glance_usage_on_your_home_and_lock_screens_83675),
                R.drawable.ic_oui_add_home, null);
        widgets.setShowBottomDivider(true);
        features.addView(widgets);
        features.addView(Ui.actionRow(this, AppText.get(R.string.phone_useful_alerts_0a909),
                AppText.get(R.string.phone_optional_updates_when_limits_reset_or_credits_ar_4fec9),
                R.drawable.ic_oui_notification, null));
        this.content.addView(features);
        addPrimaryAction(AppText.get(R.string.phone_continue_2e026), () -> showStep(OnboardingFlow.STEP_ACCOUNT));
    }

    private void buildAccount() {
        addIntro(AppText.get(R.string.phone_connect_your_chatgpt_account_b10e4),
                AppText.get(R.string.phone_use_openai_s_secure_browser_flow_to_sign_up_or_s_ef558),
                R.drawable.ic_oui_samsung_account);

        this.content.addView(Ui.separator(this, AppText.get(R.string.phone_private_by_design_3aaa9)));
        RoundedLinearLayout privacy = Ui.seslRowCard(this, this.dark);
        CardItemView encrypted = Ui.actionRow(this, AppText.get(R.string.phone_encrypted_on_this_device_7e60d),
                AppText.get(R.string.phone_session_tokens_are_protected_by_android_keystore_0b926),
                R.drawable.ic_oui_privacy, null);
        encrypted.setShowBottomDivider(true);
        privacy.addView(encrypted);
        privacy.addView(Ui.actionRow(this, AppText.get(R.string.phone_no_analytics_sdk_01ed2),
                AppText.get(R.string.phone_your_account_and_usage_are_not_sent_through_a_co_9d154),
                R.drawable.ic_oui_contact_outline, null));
        this.content.addView(privacy);

        if (!this.authMessage.isEmpty()) {
            Ui.addSpacer(this.content, 16);
            RoundedLinearLayout status = Ui.seslCard(this, this.dark);
            TextView message = Ui.text(this, this.authMessage, 14.0f,
                    Ui.secondaryText(this.dark));
            status.addView(message);
            this.content.addView(status);
        }

        String signInLabel = AppPreferences.isOAuthPending(this)
                ? AppText.get(R.string.phone_continue_sign_in_with_chatgpt_35eff)
                : AppText.get(R.string.phone_sign_up_or_sign_in_with_chatgpt_b7807);
        addPrimaryAction(signInLabel, this::startSignIn);
        Button later = Ui.button(this, AppText.get(R.string.phone_not_now_e4571), false, this.dark);
        later.setOnClickListener(view -> completeAndOpenMain());
        LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 54));
        laterParams.setMargins(0, Ui.dp(this, 10), 0, Ui.dp(this, 8));
        this.content.addView(later, laterParams);
    }

    private void buildComplete() {
        boolean signedIn = SecureTokenStore.isSignedIn(this);
        addIntro(signedIn ? AppText.get(R.string.phone_you_re_all_set_67c65) : AppText.get(R.string.phone_setup_complete_bffac),
                signedIn
                        ? AppText.get(R.string.phone_your_chatgpt_account_is_connected_codex_meter_wi_4487e)
                        : AppText.get(R.string.phone_you_can_connect_chatgpt_later_from_the_codex_met_5e561),
                signedIn ? R.drawable.ic_oui_samsung_account : R.drawable.ic_oui_info_outline);

        Ui.addSpacer(this.content, 20);
        RoundedLinearLayout account = Ui.seslRowCard(this, this.dark);
        AuthTokens tokens = SecureTokenStore.load(this);
        account.addView(Ui.actionRow(this,
                signedIn ? AppText.get(R.string.phone_chatgpt_connected_091f6) : AppText.get(R.string.phone_continue_without_an_account_931eb),
                signedIn && tokens != null && !tokens.email.isEmpty()
                        ? tokens.email
                        : (signedIn ? AppText.get(R.string.phone_secure_sign_in_complete_667ad) : AppText.get(R.string.phone_sign_in_whenever_you_re_ready_d9940)),
                signedIn ? R.drawable.ic_oui_contact_outline : R.drawable.ic_oui_privacy,
                null));
        this.content.addView(account);
        addPrimaryAction(AppText.get(R.string.phone_open_codex_meter_a55de), this::completeAndOpenMain);
    }

    private void addIntro(String titleText, String bodyText, int iconResource) {
        RoundedLinearLayout hero = Ui.seslCard(this, this.dark);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setColorFilter(Ui.accent(this, this.dark));
        icon.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setShape(GradientDrawable.OVAL);
        int accent = Ui.accent(this, this.dark);
        iconBackground.setColor(Color.argb(this.dark ? 45 : 24,
                Color.red(accent), Color.green(accent), Color.blue(accent)));
        icon.setBackground(iconBackground);
        hero.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 62), Ui.dp(this, 62)));

        TextView title = Ui.title(this, titleText, this.dark);
        title.setTextSize(34.0f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.setMargins(0, Ui.dp(this, 24), 0, 0);
        hero.addView(title, titleParams);

        TextView body = Ui.text(this, bodyText, 16.0f, Ui.secondaryText(this.dark));
        body.setLineSpacing(0.0f, 1.18f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.setMargins(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
        hero.addView(body, bodyParams);
        this.content.addView(hero);
    }

    private void addPrimaryAction(String label, Runnable action) {
        Button button = Ui.nativePrimaryButton(this, label);
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(this, 60));
        params.setMargins(0, Ui.dp(this, 22), 0, Ui.dp(this, 8));
        this.content.addView(button, params);
    }

    private void showStep(int requestedStep) {
        this.step = OnboardingFlow.normalizeStep(requestedStep);
        AppPreferences.setOnboardingStep(this, this.step);
        render();
    }

    private void goBack() {
        showStep(OnboardingFlow.previousStep(this.step));
    }

    private void startSignIn() {
        if (SecureTokenStore.isSignedIn(this)) {
            showStep(OnboardingFlow.STEP_COMPLETE);
            return;
        }
        boolean resuming = AppPreferences.isOAuthPending(this);
        this.oauthRequested = true;
        this.authMessage = resuming
                ? AppText.get(R.string.phone_resuming_secure_chatgpt_sign_in_4019e)
                : AppText.get(R.string.phone_preparing_secure_chatgpt_sign_in_ac39f);
        render();
        try {
            startForegroundService(new Intent(this, OAuthService.class)
                    .setAction(OAuthService.ACTION_START));
        } catch (RuntimeException exception) {
            this.oauthRequested = false;
            AppPreferences.setOAuthPending(this, false, "");
            this.authMessage = AppText.get(R.string.phone_could_not_start_sign_in_6a67c) + safeMessage(exception);
            render();
        }
    }

    private void openAuthUrl(String url) {
        if (!url.equals(this.lastLaunchedAuthUrl) || hasWindowFocus()) {
            this.lastLaunchedAuthUrl = url;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (RuntimeException exception) {
                this.authMessage = AppText.get(R.string.phone_no_browser_is_available_to_complete_sign_in_bb2d8);
                render();
            }
        }
    }

    private void completeAndOpenMain() {
        cancelPendingSignIn();
        AppPreferences.completeOnboarding(this);
        openMain();
    }

    private void cancelPendingSignIn() {
        if (!this.oauthRequested && !AppPreferences.isOAuthPending(this)) {
            return;
        }
        this.oauthRequested = false;
        try {
            startService(new Intent(this, OAuthService.class)
                    .setAction(OAuthService.ACTION_CANCEL_SILENT));
        } catch (RuntimeException ignored) {
            // The service may already have stopped after the browser returned.
        }
        AppPreferences.setOAuthPending(this, false, "");
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
