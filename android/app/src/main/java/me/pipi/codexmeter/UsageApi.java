package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageSnapshot;

import android.content.Context;
import android.os.SystemClock;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/**
 * Refreshes Codex usage for the app, widgets, and notifications, and owns the
 * authenticated ChatGPT backend transport that {@link ResetCreditApi} shares.
 */
public final class UsageApi {
    /** Serializes backend calls so token refreshes and cache writes never interleave. */
    static final Object NETWORK_LOCK = new Object();
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 25_000;
    private static final int MAX_MESSAGE_LENGTH = 240;
    private static boolean cookiesInstalled;

    private UsageApi() {
    }

    public static UsageSnapshot refreshAndCache(Context context) throws Exception {
        long started = SystemClock.elapsedRealtime();
        DiagnosticLog.info(context, "refresh", "usage_refresh_started");
        UsageSnapshot snapshot;
        try {
            synchronized (NETWORK_LOCK) {
                snapshot = refreshAndCacheLocked(context);
            }
        } catch (Exception exception) {
            DiagnosticLog.error(context, "refresh", "usage_refresh_failed", exception,
                    "duration_ms", SystemClock.elapsedRealtime() - started);
            throw exception;
        }
        DiagnosticLog.info(context, "refresh", "usage_refresh_succeeded",
                "duration_ms", SystemClock.elapsedRealtime() - started,
                "five_hour", snapshot.fiveHour != null,
                "weekly", snapshot.weekly != null,
                "monthly", snapshot.monthly != null,
                "additional_limits", snapshot.additionalLimits.size());
        return snapshot;
    }

    private static UsageSnapshot refreshAndCacheLocked(Context context) throws Exception {
        installCookieManager();
        AuthTokens tokens = usableTokens(context);
        Response response = requestUsage(context, tokens);
        if (response.status == HttpURLConnection.HTTP_UNAUTHORIZED) {
            DiagnosticLog.warn(context, "auth", "usage_token_rejected_refreshing");
            tokens = refreshAndSave(context, tokens);
            response = requestUsage(context, tokens);
        }
        if (!response.isSuccessful()) {
            throw usageFailure(context, response);
        }
        UsageSnapshot snapshot = UsageParser.parse(response.body, System.currentTimeMillis());
        if (!snapshot.hasDisplayableData()) {
            throw OAuthClient.userError(context, R.string.auth_error_usage_unrecognized);
        }
        UsageSnapshot previous = AppPreferences.loadSnapshot(context);
        if (!AppPreferences.saveSnapshot(context, snapshot)) {
            throw OAuthClient.userError(context, R.string.auth_error_usage_not_saved);
        }
        UsageHistoryRecorder.record(context, snapshot);
        NowBarManager.onUsageUpdated(context, snapshot);
        ResetNotificationManager.onUsageUpdated(context, previous, snapshot);
        try {
            ResetAlertScheduler.scheduleFromSnapshot(context, snapshot);
        } catch (RuntimeException exception) {
            DiagnosticLog.error(context, "scheduler", "reset_alert_schedule_failed", exception);
        }
        refreshResetCredits(context, snapshot, tokens);
        return snapshot;
    }

    private static Exception usageFailure(Context context, Response response) {
        if (response.status == HttpURLConnection.HTTP_FORBIDDEN) {
            return OAuthClient.responseError(context, response.body,
                    R.string.auth_error_usage_denied);
        }
        if (response.status == HttpURLConnection.HTTP_NOT_FOUND) {
            return OAuthClient.responseError(context, response.body,
                    R.string.auth_error_usage_endpoint_unavailable);
        }
        return OAuthClient.responseError(context, response.body, R.string.auth_error_usage_http,
                response.status);
    }

    /** A reset-credit failure must not fail the usage refresh that already succeeded. */
    private static void refreshResetCredits(Context context, UsageSnapshot snapshot,
            AuthTokens tokens) {
        try {
            ResetCreditApi.refreshAndCacheLocked(context, tokens);
        } catch (Exception exception) {
            DiagnosticLog.error(context, "refresh", "reset_credit_side_refresh_failed",
                    exception);
            ResetNotificationManager.onResetCreditSummaryUpdated(context,
                    snapshot.resetCreditsAvailable);
            AppPreferences.setResetCreditsError(context, safeMessage(context, exception));
        }
    }

    /** Loads the stored credentials, refreshing them first when they are about to expire. */
    static AuthTokens usableTokens(Context context) throws Exception {
        AuthTokens tokens = SecureTokenStore.load(context);
        if (tokens == null) {
            throw OAuthClient.userError(context, R.string.auth_error_sign_in_required);
        }
        if (!tokens.shouldRefresh(System.currentTimeMillis())) {
            return tokens;
        }
        DiagnosticLog.info(context, "auth", "token_refresh_due");
        return refreshAndSave(context, tokens);
    }

    /** Exchanges the refresh token and persists the result before anything uses it. */
    static AuthTokens refreshAndSave(Context context, AuthTokens tokens) throws Exception {
        AuthTokens refreshed = OAuthClient.refresh(context, tokens);
        SecureTokenStore.save(context, refreshed);
        return refreshed;
    }

    private static Response requestUsage(Context context, AuthTokens tokens) throws Exception {
        // Usage requests have never reported request_bytes; keep their diagnostics unchanged.
        return send(context, "usage", "GET", AppConstants.USAGE_URL, tokens, null, false);
    }

    /**
     * Sends one authenticated backend request, writing {@code payload} as JSON when present,
     * and records its network diagnostics under {@code operation}.
     */
    static Response send(Context context, String operation, String method, String url,
            AuthTokens tokens, byte[] payload) throws Exception {
        return send(context, operation, method, url, tokens, payload, true);
    }

    private static Response send(Context context, String operation, String method, String url,
            AuthTokens tokens, byte[] payload, boolean logRequestBytes) throws Exception {
        HttpsURLConnection connection =
                (HttpsURLConnection) URI.create(url).toURL().openConnection();
        long started = SystemClock.elapsedRealtime();
        if (logRequestBytes) {
            DiagnosticLog.info(context, "network", "request_started",
                    "operation", operation,
                    "method", method,
                    "url", DiagnosticSanitizer.safeUrl(url),
                    "request_bytes", payload == null ? 0 : payload.length);
        } else {
            DiagnosticLog.info(context, "network", "request_started",
                    "operation", operation,
                    "method", method,
                    "url", DiagnosticSanitizer.safeUrl(url));
        }
        try {
            applyHeaders(connection, tokens);
            connection.setRequestMethod(method);
            if (payload != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(payload);
                }
            }
            int status = connection.getResponseCode();
            String body = OAuthClient.readBody(context, connection, status);
            DiagnosticLog.info(context, "network", "request_finished",
                    "operation", operation,
                    "status", status,
                    "duration_ms", SystemClock.elapsedRealtime() - started,
                    "response_bytes", body.getBytes(StandardCharsets.UTF_8).length);
            return new Response(status, body);
        } catch (Exception exception) {
            DiagnosticLog.error(context, "network", "request_failed", exception,
                    "operation", operation,
                    "duration_ms", SystemClock.elapsedRealtime() - started);
            throw exception;
        } finally {
            connection.disconnect();
        }
    }

    static void applyHeaders(HttpsURLConnection connection, AuthTokens tokens) {
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + tokens.accessToken);
        connection.setRequestProperty("User-Agent", AppConstants.userAgent());
        connection.setRequestProperty("originator", AppConstants.ORIGINATOR);
        if (!tokens.accountId.isEmpty()) {
            connection.setRequestProperty("ChatGPT-Account-Id", tokens.accountId);
        }
    }

    static void installCookieManager() {
        if (cookiesInstalled) {
            return;
        }
        try {
            if (CookieHandler.getDefault() == null) {
                CookieHandler.setDefault(
                        new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER));
            }
        } catch (Exception ignored) {
            // Requests still work without a shared cookie jar.
        }
        cookiesInstalled = true;
    }

    /**
     * A trimmed, bounded, localized exception message suitable for persisting as a visible
     * error, or a generic reset-credit refresh failure when the exception has none.
     */
    static String safeMessage(Context context, Exception exception) {
        String message = exception == null ? "" : exception.getLocalizedMessage();
        if (message == null || message.trim().isEmpty()) {
            return context.getString(R.string.auth_error_reset_credits_refresh_failed);
        }
        String trimmed = message.trim();
        return trimmed.length() > MAX_MESSAGE_LENGTH
                ? trimmed.substring(0, MAX_MESSAGE_LENGTH) : trimmed;
    }

    /** Status and body of a backend response; a missing body reads as empty. */
    static final class Response {
        final String body;
        final int status;

        Response(int status, String body) {
            this.status = status;
            this.body = body == null ? "" : body;
        }

        boolean isSuccessful() {
            return OAuthClient.isSuccessful(status);
        }
    }
}
