package dev.bennett.codexmeter;

import android.content.Context;
import android.os.SystemClock;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/** Fetches every quota window for phone widgets while keeping the App/Wear usage model intact. */
public final class WidgetUsageApi {
    private static final String USER_AGENT = "Codex Desktop/0.147.0-alpha.6.5 (Mac OS 27.0.0; arm64) unknown (Codex Desktop; 26.803.61601)";
    private static final String ORIGINATOR = "Codex Desktop";

    private WidgetUsageApi() { }

    public static WidgetUsageSnapshot refreshAndCache(Context context) throws Exception {
        long started = SystemClock.elapsedRealtime();
        DiagnosticLog.info(context, "widget", "widget_usage_refresh_started");
        try {
            synchronized (UsageApi.NETWORK_LOCK) {
                UsageApi.installCookieManager();
                AuthTokens tokens = usableTokens(context);
                Response response = request(context, tokens);
                if (response.status == 401) {
                    DiagnosticLog.warn(context, "auth", "widget_usage_token_rejected_refreshing");
                    tokens = refreshTokens(context, tokens);
                    response = request(context, tokens);
                }
                if (response.status < 200 || response.status >= 300) {
                    String message = response.status == 403
                            ? "Codex usage access was denied for this account."
                            : response.status == 404
                            ? "The Codex usage endpoint is unavailable or has changed."
                            : "Usage refresh failed (HTTP " + response.status + ").";
                    throw new Exception(OAuthClient.readError(response.body, message));
                }
                WidgetUsageSnapshot snapshot = WidgetUsageParser.parse(response.body, System.currentTimeMillis());
                if (snapshot.windows.isEmpty()) {
                    throw new Exception("OpenAI returned no recognizable Codex quota windows.");
                }
                if (!WidgetUsageStore.save(context, snapshot, tokens)) {
                    throw new Exception("Widget usage could not be saved for the active account.");
                }
                // Preserve the existing reset-credit detail refresh: widgets prefer that cache
                // over an embedded summary, and a detail failure must not discard valid windows.
                try {
                    ResetCreditApi.refreshAndCacheForWidgetLocked(context, tokens);
                } catch (Exception error) {
                    DiagnosticLog.error(context, "widget", "widget_reset_credit_side_refresh_failed", error);
                    AppPreferences.setResetCreditsError(context, UsageApi.safeMessage(error));
                }
                DiagnosticLog.info(context, "widget", "widget_usage_refresh_succeeded",
                        "duration_ms", SystemClock.elapsedRealtime() - started,
                        "windows", snapshot.windows.size());
                return snapshot;
            }
        } catch (Exception error) {
            DiagnosticLog.error(context, "widget", "widget_usage_refresh_failed", error,
                    "duration_ms", SystemClock.elapsedRealtime() - started);
            throw error;
        }
    }

    private static AuthTokens usableTokens(Context context) throws Exception {
        AuthTokens tokens = SecureTokenStore.load(context);
        if (tokens == null) throw new Exception("Sign in to ChatGPT first.");
        if (tokens.shouldRefresh(System.currentTimeMillis())) {
            DiagnosticLog.info(context, "auth", "widget_token_refresh_due");
            return refreshTokens(context, tokens);
        }
        return tokens;
    }

    private static AuthTokens refreshTokens(Context context, AuthTokens expected) throws Exception {
        AuthTokens refreshed = OAuthClient.refresh(context, expected);
        if (!SecureTokenStore.saveIfCurrent(context, expected, refreshed)) {
            throw new Exception("The signed-in account changed during widget refresh.");
        }
        return refreshed;
    }

    static void applyHeaders(HttpsURLConnection connection, AuthTokens tokens) {
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(25000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + tokens.accessToken);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("originator", ORIGINATOR);
        if (!tokens.accountId.isEmpty()) connection.setRequestProperty("ChatGPT-Account-Id", tokens.accountId);
    }

    private static Response request(Context context, AuthTokens tokens) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) URI.create(AppConstants.USAGE_URL).toURL().openConnection();
        long started = SystemClock.elapsedRealtime();
        DiagnosticLog.info(context, "network", "request_started", "operation", "widget_usage",
                "method", "GET", "url", DiagnosticSanitizer.safeUrl(AppConstants.USAGE_URL));
        try {
            applyHeaders(connection, tokens);
            connection.setRequestMethod("GET");
            int status = connection.getResponseCode();
            String body = OAuthClient.readBody(connection, status);
            DiagnosticLog.info(context, "network", "request_finished", "operation", "widget_usage",
                    "status", status, "duration_ms", SystemClock.elapsedRealtime() - started,
                    "response_bytes", body.getBytes(StandardCharsets.UTF_8).length);
            return new Response(status, body);
        } finally {
            connection.disconnect();
        }
    }

    private static final class Response {
        final int status;
        final String body;
        Response(int status, String body) { this.status = status; this.body = body == null ? "" : body; }
    }
}
