package me.pipi.codexmeter;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
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
        return parseTokens(context, response, null);
    }

    public static AuthTokens refresh(Context context, AuthTokens tokens) throws Exception {
        JSONObject request = new JSONObject();
        request.put("grant_type", "refresh_token");
        request.put("refresh_token", tokens.refreshToken);
        request.put("client_id", AppConstants.OAUTH_CLIENT_ID);
        String response = postJson(context, "oauth_token_refresh", AppConstants.TOKEN_URL,
                request);
        return tokens.mergeRefresh(parseTokens(context, response, tokens));
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
    private static AuthTokens parseTokens(Context context, String body, AuthTokens previous)
            throws Exception {
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
            throw userError(context, R.string.auth_error_incomplete_credentials);
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
            String body = readBody(context, connection, status);
            DiagnosticLog.info(context, "network", "request_finished",
                    "operation", operation,
                    "status", status,
                    "duration_ms", SystemClock.elapsedRealtime() - started,
                    "response_bytes", body.getBytes(StandardCharsets.UTF_8).length);
            if (!isSuccessful(status)) {
                throw responseError(context, body, R.string.auth_error_authentication_http,
                        status);
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
    static String readBody(Context context, HttpURLConnection connection, int status)
            throws Exception {
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
                    throw userError(context, R.string.auth_error_response_too_large);
                }
            }
            return body.toString(StandardCharsets.UTF_8.name());
        }
    }

    /**
     * The failure for an unsuccessful response: the server's own error message when it sent one,
     * otherwise the string {@code fallbackRes} formatted with {@code args}.
     */
    static Exception responseError(Context context, String body, int fallbackRes,
            Object... args) {
        String serverMessage = readError(body);
        return serverMessage.isEmpty()
                ? userError(context, fallbackRes, args)
                : new Exception(serverMessage);
    }

    /** Extracts a server-provided error message, or returns "" when there is none. */
    private static String readError(String body) {
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
        return "";
    }

    /**
     * A failure written for the user from the string resource {@code messageRes}: its
     * {@link Exception#getMessage() message} is the English text for diagnostic logs, and its
     * {@link Exception#getLocalizedMessage() localized message} is in the app's language.
     */
    static UserFacingException userError(Context context, int messageRes, Object... args) {
        Configuration configuration =
                new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(Locale.ENGLISH);
        Resources english = context.createConfigurationContext(configuration).getResources();
        return new UserFacingException(format(english, messageRes, args),
                format(context.getResources(), messageRes, args));
    }

    private static String format(Resources resources, int messageRes, Object... args) {
        return args.length == 0
                ? resources.getString(messageRes)
                : resources.getString(messageRes, args);
    }

    /** See {@link #userError}; {@link #toString()} also stays English for stack traces. */
    static final class UserFacingException extends Exception {
        private final String localizedMessage;

        UserFacingException(String englishMessage, String localizedMessage) {
            super(englishMessage);
            this.localizedMessage = localizedMessage;
        }

        @Override
        public String getLocalizedMessage() {
            return localizedMessage;
        }

        @Override
        public String toString() {
            return getClass().getName() + ": " + getMessage();
        }
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
