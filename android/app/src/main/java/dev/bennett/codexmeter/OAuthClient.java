package dev.bennett.codexmeter;

import android.content.Context;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;

/** OAuth token endpoint calls plus the response-reading helpers shared by backend requests. */
public final class OAuthClient {
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 25_000;
    private static final int READ_BUFFER_BYTES = 8192;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final long MIN_EXPIRES_IN_SECONDS = 60L;
    private static final long DEFAULT_EXPIRES_IN_SECONDS = 3600L;
    private static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
    private static final String CONTENT_TYPE_JSON = "application/json";

    private OAuthClient() {
    }

    public static AuthTokens exchangeCode(Context context, String code, String redirectUri,
            String codeVerifier) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        form.put("client_id", AppConstants.OAUTH_CLIENT_ID);
        form.put("code_verifier", codeVerifier);
        String response = postForm(context, "oauth_code_exchange", AppConstants.TOKEN_URL, form);
        return parseTokens(response, null);
    }

    public static AuthTokens refresh(Context context, AuthTokens tokens) throws Exception {
        JSONObject request = new JSONObject();
        request.put("grant_type", "refresh_token");
        request.put("refresh_token", tokens.refreshToken);
        request.put("client_id", AppConstants.OAUTH_CLIENT_ID);
        String response = postJson(context, "oauth_token_refresh", AppConstants.TOKEN_URL,
                request);
        return tokens.mergeRefresh(parseTokens(response, tokens));
    }

    /** Revokes the refresh token, retrying as a form post when the JSON request fails. */
    public static void revokeBestEffort(Context context, AuthTokens tokens) {
        if (tokens == null || tokens.refreshToken.isEmpty()) {
            return;
        }
        JSONObject request = new JSONObject();
        try {
            request.put("token", tokens.refreshToken);
            request.put("token_type_hint", "refresh_token");
            request.put("client_id", AppConstants.OAUTH_CLIENT_ID);
            postJson(context, "oauth_token_revoke", AppConstants.REVOKE_URL, request);
        } catch (Exception firstError) {
            DiagnosticLog.warn(context, "auth", "json_revocation_failed_trying_form",
                    "error", firstError.getClass().getSimpleName());
            try {
                Map<String, String> form = new LinkedHashMap<>();
                form.put("token", tokens.refreshToken);
                form.put("token_type_hint", "refresh_token");
                form.put("client_id", AppConstants.OAUTH_CLIENT_ID);
                postForm(context, "oauth_token_revoke", AppConstants.REVOKE_URL, form);
            } catch (Exception secondError) {
                DiagnosticLog.error(context, "auth", "token_revocation_failed", secondError);
            }
        }
    }

    /**
     * Builds credentials from a token-endpoint response. When refreshing, values the server
     * omitted fall back to the {@code previous} credentials.
     */
    private static AuthTokens parseTokens(String body, AuthTokens previous) throws Exception {
        JSONObject response = new JSONObject(body);
        String accessToken = response.optString("access_token", "");
        String refreshToken = response.optString("refresh_token", "");
        String idToken = response.optString("id_token", "");
        if (previous != null) {
            refreshToken = orFallback(refreshToken, previous.refreshToken);
            idToken = orFallback(idToken, previous.idToken);
        }
        long expiresInSeconds = Math.max(MIN_EXPIRES_IN_SECONDS,
                response.optLong("expires_in", DEFAULT_EXPIRES_IN_SECONDS));
        JwtClaims claims = JwtClaims.fromTokens(idToken, accessToken);
        String accountId = claims.accountId;
        String email = claims.email;
        if (previous != null) {
            accountId = orFallback(accountId, previous.accountId);
            email = orFallback(email, previous.email);
        }
        long expiresAtMillis = expiresInSeconds * 1000L + System.currentTimeMillis();
        AuthTokens tokens = new AuthTokens(accessToken, refreshToken, idToken, expiresAtMillis,
                accountId, email);
        if (!tokens.isUsable()) {
            throw new Exception("The authorization server returned incomplete credentials.");
        }
        return tokens;
    }

    private static String orFallback(String value, String fallback) {
        return value.isEmpty() ? fallback : value;
    }

    private static String postForm(Context context, String operation, String url,
            Map<String, String> form) throws Exception {
        return post(context, operation, url, CONTENT_TYPE_FORM,
                formEncode(form).getBytes(StandardCharsets.UTF_8));
    }

    private static String postJson(Context context, String operation, String url,
            JSONObject json) throws Exception {
        return post(context, operation, url, CONTENT_TYPE_JSON,
                json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String post(Context context, String operation, String url,
            String contentType, byte[] payload) throws Exception {
        HttpsURLConnection connection =
                (HttpsURLConnection) URI.create(url).toURL().openConnection();
        long started = SystemClock.elapsedRealtime();
        DiagnosticLog.info(context, "network", "request_started",
                "operation", operation,
                "method", "POST",
                "url", DiagnosticSanitizer.safeUrl(url),
                "request_bytes", payload.length);
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-Type", contentType);
            connection.setRequestProperty("Accept", CONTENT_TYPE_JSON);
            connection.setRequestProperty("User-Agent", AppConstants.userAgent());
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
            int status = connection.getResponseCode();
            String body = readBody(connection, status);
            DiagnosticLog.info(context, "network", "request_finished",
                    "operation", operation,
                    "status", status,
                    "duration_ms", SystemClock.elapsedRealtime() - started,
                    "response_bytes", body.getBytes(StandardCharsets.UTF_8).length);
            if (!isSuccessful(status)) {
                throw new Exception(readError(body,
                        "Authentication failed (HTTP " + status + ")."));
            }
            return body;
        } catch (Exception exception) {
            DiagnosticLog.error(context, "network", "request_failed", exception,
                    "operation", operation,
                    "duration_ms", SystemClock.elapsedRealtime() - started);
            throw exception;
        } finally {
            connection.disconnect();
        }
    }

    static boolean isSuccessful(int status) {
        return status >= 200 && status < 300;
    }

    /** Reads a response body (or error body) as UTF-8, refusing anything over 2 MiB. */
    static String readBody(HttpURLConnection connection, int status) throws Exception {
        InputStream stream = status < 200 || status >= 400
                ? connection.getErrorStream()
                : connection.getInputStream();
        if (stream == null) {
            return "";
        }
        try (InputStream input = stream) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            byte[] buffer = new byte[READ_BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) != -1) {
                body.write(buffer, 0, read);
                if (body.size() > MAX_RESPONSE_BYTES) {
                    throw new Exception("Server response was unexpectedly large.");
                }
            }
            return body.toString(StandardCharsets.UTF_8.name());
        }
    }

    /** Extracts a server-provided error message, or returns {@code fallback}. */
    static String readError(String body, String fallback) {
        try {
            JSONObject object = new JSONObject(body == null ? "" : body);
            String description = object.optString("error_description", "");
            if (!description.isEmpty()) {
                return description;
            }
            Object error = object.opt("error");
            if (error instanceof String && !((String) error).isEmpty()) {
                return (String) error;
            }
            if (error instanceof JSONObject) {
                String message = ((JSONObject) error).optString("message", "");
                if (!message.isEmpty()) {
                    return message;
                }
            }
            String message = object.optString("message", "");
            if (!message.isEmpty()) {
                return message;
            }
        } catch (Exception ignored) {
            // Do not surface arbitrary HTML from a proxy or gateway.
        }
        return fallback;
    }

    /** Encodes parameters as {@code application/x-www-form-urlencoded}, preserving order. */
    static String formEncode(Map<String, String> parameters) throws Exception {
        StringBuilder encoded = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (encoded.length() > 0) {
                encoded.append('&');
            }
            encoded.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return encoded.toString();
    }
}
