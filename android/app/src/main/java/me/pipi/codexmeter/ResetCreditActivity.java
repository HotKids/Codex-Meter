package me.pipi.codexmeter;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import dev.oneuiproject.oneui.widget.CardItemView;
import dev.oneuiproject.oneui.widget.RoundedLinearLayout;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;

/** Lists available Codex reset credits with their expirations and lets the user spend one. */
public final class ResetCreditActivity extends AppCompatActivity {
    /** Cached credit details older than this are refreshed when the page opens. */
    private static final long DETAILS_MAX_AGE_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 240;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private boolean dark;
    /** Expiry notification that opened this page; dismissed once a reset is applied. */
    private int expiryNotificationId = -1;
    private Button useButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applySelectedTheme(this);
        super.onCreate(savedInstanceState);
        this.dark = Ui.isDark(this);
        this.content = Ui.installPage(this,
                getString(R.string.alerts_reset_credits_page_title), true).content;
        stopUnusedRefresh();
        rebuild();
        refreshDetailsIfNeeded();
        if (savedInstanceState == null) {
            maybePromptUseReset(getIntent());
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        stopUnusedRefresh();
    }

    private void stopUnusedRefresh() {
        SwipeRefreshLayout refresh = findViewById(R.id.dashboard_refresh);
        refresh.setRefreshing(false);
        refresh.setEnabled(false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        rebuild();
        maybePromptUseReset(intent);
    }

    @Override
    protected void onDestroy() {
        this.executor.shutdownNow();
        super.onDestroy();
    }

    /** "No resets available", "1 reset available", or "N resets available", localized. */
    static String availableResetsLabel(Context context, int available) {
        if (available <= 0) {
            return context.getString(R.string.alerts_reset_credits_none_available);
        }
        return context.getResources().getQuantityString(
                R.plurals.alerts_reset_credits_available, available, available);
    }

    /** Absolute expiry time followed by the relative countdown, e.g. "Fri 10:00 · in 2d". */
    static String expiryText(Context context, long expiresAtMillis, long nowMillis) {
        return context.getString(R.string.alerts_reset_credit_expiry_time,
                UsageFormat.absolute(context, expiresAtMillis, nowMillis),
                UsageFormat.relative(context, expiresAtMillis, nowMillis));
    }

    private void rebuild() {
        this.content.removeAllViews();
        ResetCreditsSnapshot snapshot = AppPreferences.loadResetCredits(this);
        int available = snapshot == null ? 0 : snapshot.availableCount;
        long now = System.currentTimeMillis();
        List<RateLimitResetCredit> availableCredits = snapshot == null
                ? Collections.emptyList()
                : snapshot.availableCreditsByExpiry(now);
        long nextExpiry = snapshot == null ? 0L : snapshot.nextExpiryMillis(now);

        this.content.addView(Ui.separator(this,
                getString(R.string.alerts_reset_credits_section_available)));
        RoundedLinearLayout summaryCard = Ui.seslRowCard(this, this.dark);
        summaryCard.addView(Ui.actionRow(this, availableResetsLabel(this, available),
                summaryText(available, nextExpiry, now), R.drawable.ic_ms_restart_alt, null));
        this.content.addView(summaryCard);

        this.content.addView(Ui.separator(this,
                getString(R.string.alerts_reset_credits_section_expirations)));
        RoundedLinearLayout expirations = Ui.seslRowCard(this, this.dark);
        addCreditExpirations(expirations, availableCredits, available, now);
        this.content.addView(expirations);

        addErrorCardIfNeeded();
        addUseButton(available);
    }

    private String summaryText(int available, long nextExpiry, long now) {
        if (nextExpiry > 0L) {
            return getString(R.string.alerts_reset_credits_next_expires,
                    expiryText(this, nextExpiry, now));
        }
        if (available > 0) {
            return getString(R.string.alerts_reset_credits_openai_chooses);
        }
        return getString(R.string.alerts_reset_credits_none_summary);
    }

    /**
     * One row per known credit (soonest first), plus a catch-all row for credits the server
     * counted but did not describe, or a placeholder when none are available.
     */
    private void addCreditExpirations(RoundedLinearLayout card,
            List<RateLimitResetCredit> credits, int availableCount, long nowMillis) {
        for (int index = 0; index < credits.size(); index++) {
            RateLimitResetCredit credit = credits.get(index);
            CardItemView row = Ui.actionRow(this, creditTitle(this, credit, index),
                    creditExpiryText(credit, nowMillis), 0, null);
            row.setShowTopDivider(index > 0);
            card.addView(row);
        }

        int missingCount = Math.max(0, availableCount - credits.size());
        if (missingCount > 0) {
            String missingText = credits.isEmpty()
                    ? getString(R.string.alerts_reset_credits_expiration_pending)
                    : getResources().getQuantityString(
                            R.plurals.alerts_reset_credits_missing_details,
                            missingCount, missingCount);
            CardItemView missing = Ui.actionRow(this,
                    getString(R.string.alerts_reset_credits_more), missingText, 0, null);
            missing.setShowTopDivider(!credits.isEmpty());
            card.addView(missing);
        } else if (availableCount == 0) {
            card.addView(Ui.actionRow(this,
                    getString(R.string.alerts_reset_credits_none_title),
                    getString(R.string.alerts_reset_credits_earn), 0, null));
        }
    }

    /** The credit's own title, or "Reset credit N"; the first dated credit is marked "next". */
    private static String creditTitle(Context context, RateLimitResetCredit credit,
            int index) {
        String title = credit.title.trim().isEmpty()
                ? context.getString(R.string.alerts_reset_credit_numbered, index + 1)
                : credit.title.trim();
        if (index == 0 && credit.expiresAtMillis > 0L) {
            title = context.getString(R.string.alerts_reset_credit_next, title);
        }
        return title;
    }

    private String creditExpiryText(RateLimitResetCredit credit, long nowMillis) {
        if (credit.expiresAtMillis > 0L) {
            return expiryText(this, credit.expiresAtMillis, nowMillis);
        }
        return getString(R.string.alerts_reset_credit_expiration_unavailable);
    }

    private void addErrorCardIfNeeded() {
        String error = AppPreferences.getVisibleResetCreditsError(this);
        if (error.isEmpty()) {
            return;
        }
        Ui.addSpacer(this.content, 12);
        RoundedLinearLayout errorCard = Ui.seslCard(this, this.dark);
        errorCard.addView(Ui.text(this, error, 13.0f, Ui.danger(this.dark)));
        this.content.addView(errorCard);
    }

    private void addUseButton(int available) {
        this.useButton = Ui.nativePrimaryButton(this, getString(available > 0
                ? R.string.alerts_reset_credits_use_one
                : R.string.alerts_reset_credits_none_available));
        this.useButton.setEnabled(available > 0 && SecureTokenStore.isSignedIn(this));
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(MATCH_PARENT, Ui.dp(this, 60.0f));
        params.setMargins(0, Ui.dp(this, 22.0f), 0, Ui.dp(this, 8.0f));
        this.useButton.setOnClickListener(view -> confirmUse());
        this.content.addView(this.useButton, params);
    }

    /**
     * Refreshes credit details in the background when the cache is stale or knows about more
     * credits than it has expiration details for.
     */
    private void refreshDetailsIfNeeded() {
        ResetCreditsSnapshot snapshot = AppPreferences.loadResetCredits(this);
        long now = System.currentTimeMillis();
        long ageMillis = snapshot == null
                ? Long.MAX_VALUE
                : Math.max(0L, now - snapshot.fetchedAtMillis);
        boolean missingDetails = snapshot != null
                && snapshot.availableCount > 0
                && snapshot.availableCreditsByExpiry(now).size() < snapshot.availableCount;
        if (!SecureTokenStore.isSignedIn(this)
                || (ageMillis < DETAILS_MAX_AGE_MILLIS && !missingDetails)) {
            return;
        }
        Context app = getApplicationContext();
        UsageApi.Session session = UsageApi.session();
        this.executor.execute(() -> {
            try {
                ResetCreditApi.refreshAndCache(app, session);
                postUi(session, this::rebuild);
            } catch (CancellationException ignored) {
                return;
            } catch (Exception e) {
                onOperationFailed(app, session, e, false);
            }
        });
    }

    private void confirmUse() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.alerts_reset_credits_confirm_title)
                .setMessage(R.string.alerts_reset_credits_confirm_message)
                .setNegativeButton(R.string.alerts_cancel, null)
                .setPositiveButton(R.string.alerts_reset_credits_use_one,
                        (dialog, which) -> consume())
                .create()
                .show();
    }

    /** Handles the "Use reset" action from a credit-expiry notification. */
    private void maybePromptUseReset(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(
                AppConstants.EXTRA_PROMPT_USE_RESET, false)) {
            return;
        }
        this.expiryNotificationId = intent.getIntExtra(AppConstants.EXTRA_NOTIFICATION_ID, -1);
        intent.removeExtra(AppConstants.EXTRA_PROMPT_USE_RESET);
        intent.removeExtra(AppConstants.EXTRA_NOTIFICATION_ID);
        ResetCreditsSnapshot snapshot = AppPreferences.loadResetCredits(this);
        if (snapshot != null && snapshot.availableCount > 0
                && SecureTokenStore.isSignedIn(this)) {
            confirmUse();
        }
    }

    private void consume() {
        if (this.useButton != null) {
            this.useButton.setEnabled(false);
        }
        Toast.makeText(this, R.string.alerts_reset_credits_applying, Toast.LENGTH_SHORT).show();
        Context app = getApplicationContext();
        UsageApi.Session session = UsageApi.session();
        int notificationId = this.expiryNotificationId;
        this.executor.execute(() -> {
            try {
                ResetConsumeResult result = ResetCreditApi.consumeBestAvailable(app, session);
                if (result.applied()) {
                    session.commit(() -> ResetNotificationManager
                            .dismissResetCreditExpiryNotification(app, notificationId));
                }
                postUi(session, () -> onConsumeFinished(result));
            } catch (CancellationException ignored) {
                return;
            } catch (Exception e) {
                onOperationFailed(app, session, e, true);
            }
        });
    }

    private void onConsumeFinished(ResetConsumeResult result) {
        Toast.makeText(this, result.userMessage(this), Toast.LENGTH_LONG).show();
        if (!result.applied()) {
            rebuild();
            return;
        }
        finish();
    }

    private void onOperationFailed(Context app, UsageApi.Session session, Exception exception,
            boolean showToast) {
        String message = safeMessage(app, exception);
        try {
            session.commit(() -> AppPreferences.setResetCreditsError(app, message));
        } catch (CancellationException ignored) {
            return;
        } catch (Exception failure) {
            DiagnosticLog.error(app, "user", "reset_credit_feedback_failed", failure);
        }
        postUi(session, () -> {
            if (showToast) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
            rebuild();
        });
    }

    private void postUi(UsageApi.Session session, Runnable action) {
        runOnUiThread(() -> {
            if (session.isCurrent() && !isFinishing() && !isDestroyed()) {
                action.run();
            }
        });
    }

    /**
     * The exception's message, which the API layer words, or a localized fallback when it has
     * none.
     */
    private static String safeMessage(Context context, Exception exception) {
        String message = exception == null ? "" : exception.getLocalizedMessage();
        if (message == null || message.trim().isEmpty()) {
            return context.getString(R.string.alerts_reset_credits_apply_failed);
        }
        String trimmed = message.trim();
        return trimmed.length() > MAX_ERROR_MESSAGE_LENGTH
                ? trimmed.substring(0, MAX_ERROR_MESSAGE_LENGTH)
                : trimmed;
    }
}
