package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageSnapshot;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import org.json.JSONException;
import org.json.JSONObject;

/** Lists and redeems Codex rate-limit reset credits over the {@link UsageApi} transport. */
public final class ResetCreditApi {
    /** Cached credit details younger than this are trusted when choosing a credit to redeem. */
    private static final long DETAIL_FRESH_MS = TimeUnit.MINUTES.toMillis(5);

    private ResetCreditApi() {
    }

    public static ResetCreditsSnapshot refreshAndCache(Context context) throws Exception {
        return refreshAndCache(context, UsageApi.session());
    }

    static ResetCreditsSnapshot refreshAndCache(Context context, UsageApi.Session session)
            throws Exception {
        synchronized (UsageApi.NETWORK_LOCK) {
            session.requireCurrent();
            UsageApi.installCookieManager();
            return refreshAndCacheLocked(context, UsageApi.usableTokens(context, session), session);
        }
    }

    /** Fetches and caches the credit list; the caller must hold {@link UsageApi#NETWORK_LOCK}. */
    static ResetCreditsSnapshot refreshAndCacheLocked(Context context, AuthTokens tokens)
            throws Exception {
        return refreshAndCacheLocked(context, tokens, UsageApi.session());
    }

    static ResetCreditsSnapshot refreshAndCacheLocked(Context context, AuthTokens tokens,
            UsageApi.Session session) throws Exception {
        session.requireCurrent();
        long started = SystemClock.elapsedRealtime();
        DiagnosticLog.info(context, "refresh", "reset_credit_refresh_started");
        if (tokens == null) {
            tokens = UsageApi.usableTokens(context, session);
        }
        UsageApi.Response response = requestCredits(context, tokens);
        session.requireCurrent();
        if (response.status == HttpURLConnection.HTTP_UNAUTHORIZED) {
            DiagnosticLog.warn(context, "auth", "reset_credit_token_rejected_refreshing");
            response = requestCredits(context, UsageApi.refreshAndSave(context, tokens, session));
            session.requireCurrent();
        }
        ensureSuccess(context, response, session, R.string.auth_error_reset_credits_load_denied,
                R.string.auth_error_reset_credits_load_unavailable,
                R.string.auth_error_reset_credits_load_http);
        ResetCreditsSnapshot snapshot =
                ResetCreditsParser.parse(response.body, System.currentTimeMillis());
        session.commit(() -> {
            if (!AppPreferences.saveResetCredits(context, snapshot)) {
                throw OAuthClient.userError(context, R.string.auth_error_reset_credits_not_saved);
            }
            ResetNotificationManager.onResetCreditsUpdated(context, snapshot);
            notifyUpdated(context);
        });
        DiagnosticLog.info(context, "refresh", "reset_credit_refresh_succeeded",
                "duration_ms", SystemClock.elapsedRealtime() - started,
                "available", snapshot.availableCount);
        return snapshot;
    }

    public static ResetConsumeResult consumeBestAvailable(Context context) throws Exception {
        return consumeBestAvailable(context, UsageApi.session());
    }

    static ResetConsumeResult consumeBestAvailable(Context context, UsageApi.Session session)
            throws Exception {
        Context app = context.getApplicationContext() == null
                ? context : context.getApplicationContext();
        long started = SystemClock.elapsedRealtime();
        DiagnosticLog.info(app, "user", "reset_credit_use_requested");
        synchronized (UsageApi.NETWORK_LOCK) {
            session.requireCurrent();
            UsageApi.installCookieManager();
            AuthTokens tokens = UsageApi.usableTokens(app, session);
            ResetCreditsSnapshot credits = AppPreferences.loadResetCredits(app);
            long now = System.currentTimeMillis();

            if (needsRefresh(credits, now)) {
                try {
                    credits = refreshAndCacheLocked(app, tokens, session);
                    tokens = UsageApi.usableTokens(app, session);
                } catch (CancellationException exception) {
                    throw exception;
                } catch (Exception exception) {
                    // Still try to redeem when the credits on hand list one as available.
                    if (!hasAvailableCredit(credits)) {
                        throw exception;
                    }
                }
            }
            if (!hasAvailableCredit(credits)) {
                return new ResetConsumeResult(ResetConsumeResult.NO_CREDIT, 0, "");
            }

            byte[] payload = consumeRequestBody(credits, now);
            session.requireCurrent();
            UsageApi.Response response = requestConsume(app, tokens, payload);
            session.requireCurrent();
            if (response.status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                DiagnosticLog.warn(app, "auth", "reset_consume_token_rejected_refreshing");
                tokens = UsageApi.refreshAndSave(app, tokens, session);
                response = requestConsume(app, tokens, payload);
                session.requireCurrent();
            }
            ensureSuccess(app, response, session, R.string.auth_error_reset_apply_denied,
                    R.string.auth_error_reset_apply_unavailable,
                    R.string.auth_error_reset_apply_http);

            JSONObject result = new JSONObject(response.body);
            String code = result.optString("code", "");
            int windowsReset = result.optInt("windows_reset", 0);
            String refreshWarning = ResetConsumeResult.RESET.equals(code)
                    ? reloadUsageAfterReset(app, session) : "";

            try {
                refreshAndCacheLocked(app, UsageApi.usableTokens(app, session), session);
            } catch (CancellationException exception) {
                throw exception;
            } catch (Exception exception) {
                session.commit(() -> AppPreferences
                        .setResetCreditsError(app, UsageApi.safeMessage(app, exception)));
            }
            session.commit(() -> {
                WidgetRenderer.updateAll(app);
                notifyUpdated(app);
            });
            DiagnosticLog.info(app, "user", "reset_credit_use_finished",
                    "result", code,
                    "windows_reset", windowsReset,
                    "duration_ms", SystemClock.elapsedRealtime() - started);
            return new ResetConsumeResult(code, windowsReset, refreshWarning);
        }
    }

    private static boolean needsRefresh(ResetCreditsSnapshot credits, long now) {
        return credits == null || now - credits.fetchedAtMillis > DETAIL_FRESH_MS
                || credits.availableCount <= 0;
    }

    private static boolean hasAvailableCredit(ResetCreditsSnapshot credits) {
        return credits != null && credits.availableCount > 0;
    }

    private static byte[] consumeRequestBody(ResetCreditsSnapshot credits, long now)
            throws JSONException {
        JSONObject request = new JSONObject();
        request.put("redeem_request_id", UUID.randomUUID().toString());
        String creditId = credits.preferredCreditId(now);
        if (!creditId.isEmpty()) {
            request.put("credit_id", creditId);
        }
        return request.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Records the user-initiated reset, then reloads usage and its schedules. Returns a warning
     * for the result message when the new usage values could not be loaded.
     */
    private static String reloadUsageAfterReset(Context app, UsageApi.Session session)
            throws Exception {
        session.commit(() -> ResetNotificationManager
                .markUserReset(app, AppPreferences.loadSnapshot(app)));
        try {
            UsageSnapshot snapshot = UsageApi.refreshAndCache(app, session);
            session.commit(() -> {
                RefreshScheduler.scheduleAtNextReset(app, snapshot);
                ResetAlertScheduler.scheduleFromSnapshot(app, snapshot);
            });
            return "";
        } catch (CancellationException exception) {
            throw exception;
        } catch (Exception exception) {
            session.commit(() -> AppPreferences.setLastError(app,
                    UsageApi.safeMessage(app, exception), true));
            return app.getString(R.string.auth_reset_usage_reload_failed);
        }
    }

    private static UsageApi.Response requestCredits(Context context, AuthTokens tokens)
            throws Exception {
        return UsageApi.send(context, "reset_credit_list", "GET",
                AppConstants.RESET_CREDITS_URL, tokens, null);
    }

    private static UsageApi.Response requestConsume(Context context, AuthTokens tokens,
            byte[] payload) throws Exception {
        return UsageApi.send(context, "reset_credit_consume", "POST",
                AppConstants.RESET_CREDITS_CONSUME_URL, tokens, payload);
    }

    /**
     * Throws the server's error for an unsuccessful response, or else the failure text for a
     * forbidden (403), missing (404) or other HTTP status (formatted with the status code).
     */
    private static void ensureSuccess(Context context, UsageApi.Response response,
            UsageApi.Session session, int forbiddenRes, int notFoundRes, int httpStatusRes)
            throws Exception {
        session.requireCurrent();
        if (response.isSuccessful()) {
            return;
        }
        UsageApi.recordAuthenticationRejection(context, response, session);
        if (response.status == HttpURLConnection.HTTP_FORBIDDEN) {
            throw OAuthClient.responseError(context, response.body, forbiddenRes);
        }
        if (response.status == HttpURLConnection.HTTP_NOT_FOUND) {
            throw OAuthClient.responseError(context, response.body, notFoundRes);
        }
        throw OAuthClient.responseError(context, response.body, httpStatusRes, response.status);
    }

    private static void notifyUpdated(Context context) {
        try {
            context.sendBroadcast(new Intent(AppConstants.ACTION_RESET_CREDITS_UPDATED)
                    .setPackage(context.getPackageName()), AppConstants.INTERNAL_PERMISSION);
        } catch (RuntimeException ignored) {
            // Listeners reload the saved credits later; a failed hint is harmless.
        }
    }
}
