package me.pipi.codexmeter;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

/** The ChatGPT account ID and email carried in an OAuth ID or access token. */
public final class JwtClaims {
    private static final JwtClaims EMPTY = new JwtClaims("", "");

    public final String accountId;
    public final String email;

    private JwtClaims(String accountId, String email) {
        this.accountId = accountId == null ? "" : accountId;
        this.email = email == null ? "" : email;
    }

    /** Reads claims from the ID token, falling back to the access token for missing values. */
    public static JwtClaims fromTokens(String idToken, String accessToken) {
        JwtClaims primary = parse(idToken);
        JwtClaims fallback = parse(accessToken);
        return new JwtClaims(
                primary.accountId.isEmpty() ? fallback.accountId : primary.accountId,
                primary.email.isEmpty() ? fallback.email : primary.email);
    }

    public static JwtClaims parse(String token) {
        if (token == null) {
            return EMPTY;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return EMPTY;
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(pad(parts[1]));
            JSONObject claims = new JSONObject(new String(payload, StandardCharsets.UTF_8));
            return new JwtClaims(accountId(claims), claims.optString("email", ""));
        } catch (Exception e) {
            return EMPTY;
        }
    }

    private static String accountId(JSONObject claims) {
        String accountId = claims.optString("chatgpt_account_id", "");
        if (!accountId.isEmpty()) {
            return accountId;
        }
        JSONObject auth = claims.optJSONObject("https://api.openai.com/auth");
        if (auth != null) {
            accountId = auth.optString("chatgpt_account_id", "");
            if (!accountId.isEmpty()) {
                return accountId;
            }
        }
        JSONArray organizations = claims.optJSONArray("organizations");
        if (organizations == null || organizations.length() == 0) {
            return accountId;
        }
        JSONObject firstOrganization = organizations.optJSONObject(0);
        return firstOrganization == null ? accountId : firstOrganization.optString("id", "");
    }

    /** Restores the Base64 padding that JWT segments omit. */
    private static String pad(String segment) {
        int remainder = segment.length() % 4;
        if (remainder == 0) {
            return segment;
        }
        StringBuilder padded = new StringBuilder(segment);
        for (int count = remainder; count < 4; count++) {
            padded.append('=');
        }
        return padded.toString();
    }
}
