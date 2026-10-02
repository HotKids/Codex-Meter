package dev.bennett.codexmeter;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import org.json.JSONObject;

/** Complete phone-widget usage, isolated from the App/Wear cache and from other accounts. */
public final class WidgetUsageStore {
    private static final String FILE = "codex_meter_widget_usage_v1";
    private static final String KEY_PREFIX = "snapshot_";
    private static final int SCHEMA = 1;

    private WidgetUsageStore() { }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** The active account's opaque key; no account identity or credentials enter cached data. */
    public static String key(Context context) {
        return key(SecureTokenStore.load(context));
    }

    static String key(AuthTokens tokens) {
        if (tokens == null) return "";
        String account = tokens.accountId.trim();
        String email = tokens.email.trim();
        if (account.isEmpty() || email.isEmpty()) {
            JwtClaims claims = JwtClaims.fromTokens(tokens.idToken, tokens.accessToken);
            if (account.isEmpty()) account = claims.accountId.trim();
            if (email.isEmpty()) email = claims.email.trim();
        }
        String identity = !account.isEmpty() ? "account:" + account
                : !email.isEmpty() ? "email:" + email.toLowerCase(Locale.ROOT) : "";
        if (identity.isEmpty()) return "";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(KEY_PREFIX);
            for (byte value : digest) hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /** Returns complete data only. A legacy App snapshot cannot establish window completeness. */
    public static WidgetUsageSnapshot load(Context context) {
        String key = key(context);
        if (key.isEmpty()) return null;
        String value = prefs(context).getString(key, null);
        if (value == null || value.isEmpty()) return null;
        try {
            JSONObject object = new JSONObject(value);
            if (object.optInt("schema", 0) != SCHEMA || !object.optBoolean("complete", false)) {
                return null;
            }
            WidgetUsageSnapshot snapshot = WidgetUsageSnapshot.fromJson(object.optJSONObject("snapshot"));
            return snapshot == null || snapshot.windows.isEmpty() ? null : snapshot;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean save(Context context, WidgetUsageSnapshot snapshot) {
        return save(context, snapshot, SecureTokenStore.load(context));
    }

    /** Bind a network response to the account that requested it, even if sign-in changes. */
    static boolean save(Context context, WidgetUsageSnapshot snapshot, AuthTokens requestedAccount) {
        String key = key(requestedAccount);
        if (snapshot == null || snapshot.windows.isEmpty() || key.isEmpty()
                || !key.equals(key(context))) return false;
        try {
            JSONObject object = new JSONObject().put("schema", SCHEMA).put("complete", true)
                    .put("snapshot", snapshot.toJson());
            return prefs(context).edit().putString(key, object.toString()).commit();
        } catch (Exception ignored) {
            return false;
        }
    }
}
