package dev.bennett.codexmeter;

import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;

/** Immutable ChatGPT OAuth credentials; missing values are normalized to empty strings. */
public final class AuthTokens {
    /** Refresh slightly before expiry so a request never starts with a dying access token. */
    private static final long REFRESH_MARGIN_MILLIS = TimeUnit.MINUTES.toMillis(5);

    public final String accessToken;
    public final String accountId;
    public final String email;
    public final long expiresAtMillis;
    public final String idToken;
    public final String refreshToken;

    public AuthTokens(String accessToken, String refreshToken, String idToken,
            long expiresAtMillis, String accountId, String email) {
        this.accessToken = safe(accessToken);
        this.refreshToken = safe(refreshToken);
        this.idToken = safe(idToken);
        this.expiresAtMillis = expiresAtMillis;
        this.accountId = safe(accountId);
        this.email = safe(email);
    }

    public boolean isUsable() {
        return !accessToken.isEmpty() && !refreshToken.isEmpty();
    }

    public boolean shouldRefresh(long nowMillis) {
        return expiresAtMillis <= nowMillis + REFRESH_MARGIN_MILLIS;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("access_token", accessToken);
        json.put("refresh_token", refreshToken);
        json.put("id_token", idToken);
        json.put("expires_at", expiresAtMillis);
        json.put("account_id", accountId);
        json.put("email", email);
        return json;
    }

    public static AuthTokens fromJson(JSONObject json) {
        return new AuthTokens(
                json.optString("access_token", ""),
                json.optString("refresh_token", ""),
                json.optString("id_token", ""),
                json.optLong("expires_at", 0L),
                json.optString("account_id", ""),
                json.optString("email", ""));
    }

    /** Overlays a token-endpoint response on these credentials, keeping values it omitted. */
    public AuthTokens mergeRefresh(AuthTokens refreshed) {
        return new AuthTokens(
                prefer(refreshed.accessToken, accessToken),
                prefer(refreshed.refreshToken, refreshToken),
                prefer(refreshed.idToken, idToken),
                refreshed.expiresAtMillis > 0 ? refreshed.expiresAtMillis : expiresAtMillis,
                prefer(refreshed.accountId, accountId),
                prefer(refreshed.email, email));
    }

    private static String prefer(String value, String fallback) {
        return value.isEmpty() ? fallback : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
