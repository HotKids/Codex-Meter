package dev.bennett.codexmeter;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import dev.oneuiproject.oneui.widget.CardItemView;
import dev.oneuiproject.oneui.widget.RoundedLinearLayout;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
        this.content = Ui.installPage(this, "Codex reset", true).content;
        rebuild();
        refreshDetailsIfNeeded();
        if (savedInstanceState == null) {
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

    @Override
    protected void onDestroy() {
        this.executor.shutdownNow();
        super.onDestroy();
    }

    /** "No resets available", "1 reset available", or "N resets available". */
    static String availableResetsLabel(int available) {
        if (available <= 0) {
            return "No resets available";
        }
        if (available == 1) {
            return "1 reset available";
        }
        return available + " resets available";
    }

    /** Absolute expiry time followed by the relative countdown, e.g. "Fri 10:00 · in 2d". */
    static String expiryText(Context context, long expiresAtMillis, long nowMillis) {
        return UsageFormat.absolute(context, expiresAtMillis, nowMillis)
                + " · " + UsageFormat.relative(expiresAtMillis, nowMillis);
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

        this.content.addView(Ui.separator(this, "Available credits"));
        RoundedLinearLayout summaryCard = Ui.seslRowCard(this, this.dark);
        summaryCard.addView(Ui.actionRow(this, availableResetsLabel(available),
                summaryText(available, nextExpiry, now), R.drawable.ic_oui_battery, null));
        this.content.addView(summaryCard);

        this.content.addView(Ui.separator(this, "Credit expirations"));
        RoundedLinearLayout expirations = Ui.seslRowCard(this, this.dark);
        addCreditExpirations(expirations, availableCredits, available, now);
        this.content.addView(expirations);

        addErrorCardIfNeeded();
        addUseButton(available);
    }

    private String summaryText(int available, long nextExpiry, long now) {
        if (nextExpiry > 0L) {
            return "Next expires " + expiryText(this, nextExpiry, now);
        }
        if (available > 0) {
            return "OpenAI will choose an eligible credit";
        }
        return "No reset credit is currently available";
    }

    /**
     * One row per known credit (soonest first), plus a catch-all row for credits the server
     * counted but did not describe, or a placeholder when none are available.
     */
    private void addCreditExpirations(RoundedLinearLayout card,
            List<RateLimitResetCredit> credits, int availableCount, long nowMillis) {
        for (int index = 0; index < credits.size(); index++) {
            RateLimitResetCredit credit = credits.get(index);
            CardItemView row = Ui.actionRow(this, creditTitle(credit, index),
                    creditExpiryText(credit, nowMillis), 0, null);
            row.setShowTopDivider(index > 0);
            card.addView(row);
        }

        int missingCount = Math.max(0, availableCount - credits.size());
        if (missingCount > 0) {
            String missingText = credits.isEmpty()
                    ? "Expiration details are not available yet"
                    : missingCount + " additional credit" + (missingCount == 1 ? "" : "s")
                            + " without expiration details";
            CardItemView missing = Ui.actionRow(this, "More credits", missingText, 0, null);
            missing.setShowTopDivider(!credits.isEmpty());
            card.addView(missing);
        } else if (availableCount == 0) {
            card.addView(Ui.actionRow(this, "No available credits",
                    "Earn credits from ChatGPT Codex", 0, null));
        }
    }

    /** The credit's own title, or "Reset credit N"; the first dated credit is marked "next". */
    private static String creditTitle(RateLimitResetCredit credit, int index) {
        String title = credit.title.trim().isEmpty()
                ? "Reset credit " + (index + 1)
                : credit.title.trim();
        if (index == 0 && credit.expiresAtMillis > 0L) {
            title = title + " · Next";
        }
        return title;
    }

    private String creditExpiryText(RateLimitResetCredit credit, long nowMillis) {
        if (credit.expiresAtMillis > 0L) {
            return expiryText(this, credit.expiresAtMillis, nowMillis);
        }
        return "Expiration unavailable";
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
        this.useButton = Ui.nativePrimaryButton(this,
                available > 0 ? "Use 1 reset" : "No resets available");
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
        this.executor.execute(() -> {
            try {
                ResetCreditApi.refreshAndCache(app);
                runOnUiThread(this::rebuild);
            } catch (Exception e) {
                AppPreferences.setResetCreditsError(app, safeMessage(e));
            }
        });
    }

    private void confirmUse() {
        new AlertDialog.Builder(this)
                .setTitle("Use one Codex reset?")
                .setMessage("The available credit expiring soonest will be used. "
                        + "This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Use 1 reset", (dialog, which) -> consume())
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
            this.useButton.setText("Applying…");
        }
        Context app = getApplicationContext();
        this.executor.execute(() -> {
            try {
                ResetConsumeResult result = ResetCreditApi.consumeBestAvailable(app);
                runOnUiThread(() -> onConsumeFinished(result));
            } catch (Exception e) {
                AppPreferences.setResetCreditsError(app, safeMessage(e));
                runOnUiThread(() -> {
                    Toast.makeText(this, safeMessage(e), Toast.LENGTH_LONG).show();
                    rebuild();
                });
            }
        });
    }

    private void onConsumeFinished(ResetConsumeResult result) {
        Toast.makeText(this, result.userMessage(), Toast.LENGTH_LONG).show();
        if (!result.applied()) {
            rebuild();
            return;
        }
        ResetNotificationManager.dismissResetCreditExpiryNotification(this,
                this.expiryNotificationId);
        finish();
    }

    private static String safeMessage(Exception exception) {
        String message = exception == null ? "" : exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "The reset could not be applied.";
        }
        String trimmed = message.trim();
        return trimmed.length() > MAX_ERROR_MESSAGE_LENGTH
                ? trimmed.substring(0, MAX_ERROR_MESSAGE_LENGTH)
                : trimmed;
    }
}
