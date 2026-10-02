package dev.bennett.codexmeter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import androidx.appcompat.app.AppCompatActivity;
import dev.oneuiproject.oneui.widget.CardItemView;
import dev.oneuiproject.oneui.widget.RoundedLinearLayout;

/* JADX INFO: loaded from: classes.dex */
public final class ResetCreditActivity extends AppCompatActivity {
    private LinearLayout content;
    private boolean dark;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private int expiryNotificationId = -1;
    private Button useButton;

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        Ui.applySelectedTheme(this);
        super.onCreate(bundle);
        this.dark = Ui.isDark(this);
        this.content = Ui.installPage(this, AppText.get(R.string.phone_codex_reset_77be3), true).content;
        rebuild();
        refreshDetailsIfNeeded();
        if (bundle == null) {
            maybePromptUseReset(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        rebuild();
        maybePromptUseReset(intent);
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        this.executor.shutdownNow();
        super.onDestroy();
    }

    public void rebuild() {
        this.content.removeAllViews();
        ResetCreditsSnapshot snapshot = AppPreferences.loadResetCredits(this);
        int available = snapshot == null ? 0 : snapshot.availableCount;
        long now = System.currentTimeMillis();
        List<RateLimitResetCredit> availableCredits = snapshot == null
                ? Collections.emptyList()
                : snapshot.availableCreditsByExpiry(now);
        long nextExpiry = snapshot == null ? 0L : snapshot.nextExpiryMillis(now);

        this.content.addView(Ui.separator(this, AppText.get(R.string.phone_available_credits_18001)));
        RoundedLinearLayout summaryCard = Ui.seslRowCard(this, this.dark);
        summaryCard.addView(Ui.actionRow(
                this,
                available <= 0
                        ? AppText.get(R.string.phone_no_resets_available_d6246)
                        : (available == 1 ? AppText.get(R.string.phone_1_reset_available_6972a) : available + AppText.get(R.string.phone_resets_available_0480d)),
                summaryText(available, nextExpiry, now),
                R.drawable.ic_oui_battery,
                null));
        this.content.addView(summaryCard);

        this.content.addView(Ui.separator(this, AppText.get(R.string.phone_credit_expirations_33fb9)));
        RoundedLinearLayout expirations = Ui.seslRowCard(this, this.dark);
        addCreditExpirations(expirations, availableCredits, available, now);
        this.content.addView(expirations);

        String visibleResetCreditsError = AppPreferences.getVisibleResetCreditsError(this);
        if (!visibleResetCreditsError.isEmpty()) {
            Ui.addSpacer(this.content, 12);
            RoundedLinearLayout errorCard = Ui.seslCard(this, this.dark);
            errorCard.addView(Ui.text(this, visibleResetCreditsError, 13.0f,
                    Ui.danger(this.dark)));
            this.content.addView(errorCard);
        }

        this.useButton = Ui.nativePrimaryButton(
                this, available > 0 ? AppText.get(R.string.phone_use_1_reset_9f6ed) : AppText.get(R.string.phone_no_resets_available_d6246));
        this.useButton.setEnabled(available > 0 && SecureTokenStore.isSignedIn(this));
        LinearLayout.LayoutParams useButtonParams =
                new LinearLayout.LayoutParams(-1, Ui.dp(this, 60.0f));
        useButtonParams.setMargins(0, Ui.dp(this, 22.0f), 0, Ui.dp(this, 8.0f));
        this.useButton.setOnClickListener(view -> confirmUse());
        this.content.addView(this.useButton, useButtonParams);
    }

    private String summaryText(int available, long nextExpiry, long now) {
        if (nextExpiry > 0L) {
            return AppText.get(R.string.phone_next_expires_fe980) + UsageFormat.absolute(this, nextExpiry, now)
                    + " · " + UsageFormat.relative(nextExpiry, now);
        }
        if (available > 0) {
            return AppText.get(R.string.phone_openai_will_choose_an_eligible_credit_8a9cf);
        }
        return AppText.get(R.string.phone_no_reset_credit_is_currently_available_4e348);
    }

    private void addCreditExpirations(RoundedLinearLayout card,
            List<RateLimitResetCredit> credits, int availableCount, long nowMillis) {
        for (int index = 0; index < credits.size(); index++) {
            RateLimitResetCredit credit = credits.get(index);
            String titleText = credit.title.trim().isEmpty()
                    ? AppText.get(R.string.phone_reset_credit_4ddbc) + (index + 1) : credit.title.trim();
            if (index == 0 && credit.expiresAtMillis > 0L) {
                titleText = titleText + AppText.get(R.string.phone_next_7271e);
            }
            String expiryText = credit.expiresAtMillis > 0L
                    ? UsageFormat.absolute(this, credit.expiresAtMillis, nowMillis)
                            + " · " + UsageFormat.relative(credit.expiresAtMillis, nowMillis)
                    : AppText.get(R.string.phone_expiration_unavailable_7fca0);
            CardItemView row = Ui.actionRow(this, titleText, expiryText, 0, null);
            row.setShowTopDivider(index > 0);
            card.addView(row);
        }

        int missingCount = Math.max(0, availableCount - credits.size());
        if (missingCount > 0) {
            String missingText = credits.isEmpty()
                    ? AppText.get(R.string.phone_expiration_details_are_not_available_yet_730fc)
                    : missingCount + AppText.get(R.string.phone_additional_credit_7b987) + AppText.nounSuffix(missingCount)
                            + AppText.get(R.string.phone_without_expiration_details_6140f);
            CardItemView missing = Ui.actionRow(this, AppText.get(R.string.phone_more_credits_272f4), missingText, 0, null);
            missing.setShowTopDivider(!credits.isEmpty());
            card.addView(missing);
        } else if (availableCount == 0) {
            card.addView(Ui.actionRow(this, AppText.get(R.string.phone_no_available_credits_151c0),
                    AppText.get(R.string.phone_earn_credits_from_chatgpt_codex_75980), 0, null));
        }
    }

    private void refreshDetailsIfNeeded() {
        ResetCreditsSnapshot resetCreditsSnapshotLoadResetCredits = AppPreferences.loadResetCredits(this);
        long now = System.currentTimeMillis();
        long jMax = resetCreditsSnapshotLoadResetCredits == null ? Long.MAX_VALUE : Math.max(0L, now - resetCreditsSnapshotLoadResetCredits.fetchedAtMillis);
        boolean missingDetails = resetCreditsSnapshotLoadResetCredits != null
                && resetCreditsSnapshotLoadResetCredits.availableCount > 0
                && resetCreditsSnapshotLoadResetCredits.availableCreditsByExpiry(now).size()
                        < resetCreditsSnapshotLoadResetCredits.availableCount;
        if (SecureTokenStore.isSignedIn(this) && (jMax >= 300000 || missingDetails)) {
            final Context applicationContext = getApplicationContext();
            this.executor.execute(new Runnable() { // from class: dev.bennett.codexmeter.ResetCreditActivity.4
                @Override // java.lang.Runnable
                public void run() {
                    try {
                        ResetCreditApi.refreshAndCache(applicationContext);
                        ResetCreditActivity.this.runOnUiThread(new Runnable() { // from class: dev.bennett.codexmeter.ResetCreditActivity.4.1
                            @Override // java.lang.Runnable
                            public void run() {
                                ResetCreditActivity.this.rebuild();
                            }
                        });
                    } catch (Exception e) {
                        AppPreferences.setResetCreditsError(applicationContext, ResetCreditActivity.safeMessage(e));
                    }
                }
            });
        }
    }

    public void confirmUse() {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(AppText.get(R.string.phone_use_one_codex_reset_65f15)).setMessage(AppText.get(R.string.phone_the_available_credit_expiring_soonest_will_be_us_4d5b8)).setNegativeButton(AppText.get(R.string.widget_config_cancel), (DialogInterface.OnClickListener) null).setPositiveButton(AppText.get(R.string.phone_use_1_reset_9f6ed), new DialogInterface.OnClickListener() { // from class: dev.bennett.codexmeter.ResetCreditActivity.5
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialogInterface, int i) {
                ResetCreditActivity.this.consume();
            }
        }).create();
        dialog.show();
    }

    private void maybePromptUseReset(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(
                AppConstants.EXTRA_PROMPT_USE_RESET, false)) {
            return;
        }
        this.expiryNotificationId = intent.getIntExtra(
                AppConstants.EXTRA_NOTIFICATION_ID, -1);
        intent.removeExtra(AppConstants.EXTRA_PROMPT_USE_RESET);
        intent.removeExtra(AppConstants.EXTRA_NOTIFICATION_ID);
        ResetCreditsSnapshot snapshot = AppPreferences.loadResetCredits(this);
        if (snapshot != null && snapshot.availableCount > 0
                && SecureTokenStore.isSignedIn(this)) {
            confirmUse();
        }
    }

    public void consume() {
        if (this.useButton != null) {
            this.useButton.setEnabled(false);
            this.useButton.setText(AppText.get(R.string.phone_applying_e578c));
        }
        final Context applicationContext = getApplicationContext();
        this.executor.execute(new Runnable() { // from class: dev.bennett.codexmeter.ResetCreditActivity.6
            @Override // java.lang.Runnable
            public void run() {
                try {
                    final ResetConsumeResult resetConsumeResultConsumeBestAvailable = ResetCreditApi.consumeBestAvailable(applicationContext);
                    ResetCreditActivity.this.runOnUiThread(new Runnable() { // from class: dev.bennett.codexmeter.ResetCreditActivity.6.1
                        @Override // java.lang.Runnable
                        public void run() {
                            Toast.makeText(ResetCreditActivity.this, resetConsumeResultConsumeBestAvailable.userMessage(), Toast.LENGTH_LONG).show();
                            if (!resetConsumeResultConsumeBestAvailable.applied()) {
                                ResetCreditActivity.this.rebuild();
                            } else {
                                ResetNotificationManager.dismissResetCreditExpiryNotification(
                                        ResetCreditActivity.this,
                                        ResetCreditActivity.this.expiryNotificationId);
                                ResetCreditActivity.this.finish();
                            }
                        }
                    });
                } catch (Exception e) {
                    AppPreferences.setResetCreditsError(applicationContext, ResetCreditActivity.safeMessage(e));
                    ResetCreditActivity.this.runOnUiThread(new Runnable() { // from class: dev.bennett.codexmeter.ResetCreditActivity.6.2
                        @Override // java.lang.Runnable
                        public void run() {
                            Toast.makeText(ResetCreditActivity.this, ResetCreditActivity.safeMessage(e), Toast.LENGTH_LONG).show();
                            ResetCreditActivity.this.rebuild();
                        }
                    });
                }
            }
        });
    }

    public static String safeMessage(Exception exc) {
        String message = exc == null ? "" : exc.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return AppText.get(R.string.phone_the_reset_could_not_be_applied_47326);
        }
        String strTrim = message.trim();
        return strTrim.length() > 240 ? strTrim.substring(0, 240) : strTrim;
    }
}
