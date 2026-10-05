package me.pipi.codexmeter;

import android.os.Build;

/** Release identity, OAuth/backend endpoints, and internal broadcast contract for the phone. */
public final class AppConstants {
    public static final int VERSION_CODE = 5;
    public static final String VERSION_NAME = "0.3.1";

    /**
     * Broadcast actions and the internal permission are namespaced by the installed application
     * ID rather than the Java package, so this build can sit beside an upstream install.
     */
    public static final String ACTION_OAUTH_READY = action("OAUTH_READY");
    public static final String ACTION_OAUTH_RESULT = action("OAUTH_RESULT");
    public static final String ACTION_INSTALL_STATUS = action("INSTALL_STATUS");
    public static final String ACTION_RELEASES_UPDATED = action("RELEASES_UPDATED");
    public static final String ACTION_REFRESH_WIDGET = action("REFRESH_WIDGET");
    public static final String ACTION_RESET_ALERT = action("RESET_ALERT");
    public static final String ACTION_RESET_CREDIT_EXPIRY_ALERT =
            action("RESET_CREDIT_EXPIRY_ALERT");
    public static final String ACTION_RESET_CREDITS_UPDATED = action("RESET_CREDITS_UPDATED");
    public static final String ACTION_USAGE_UPDATED = action("USAGE_UPDATED");
    /** Signature-level permission that guards the app's internal broadcasts. */
    public static final String INTERNAL_PERMISSION =
            BuildConfig.APPLICATION_ID + ".permission.INTERNAL";

    public static final String EXTRA_AUTH_URL = "auth_url";
    public static final String EXTRA_CREDIT_ID = "credit_id";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";
    public static final String EXTRA_PROMPT_USE_RESET = "prompt_use_reset";
    public static final String EXTRA_SUCCESS = "success";

    public static final String APP_LINK = "codexmeter://auth/complete";
    public static final String OAUTH_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann";
    /** Loopback ports registered for the OAuth redirect, tried in order. */
    public static final int[] OAUTH_PORTS = {1455, 1457};
    public static final String OAUTH_SCOPE = "openid profile email offline_access";
    public static final String ORIGINATOR = "codex-meter-android";

    public static final String AUTH_BASE = "https://auth.openai.com";
    public static final String AUTHORIZE_URL = AUTH_BASE + "/oauth/authorize";
    public static final String TOKEN_URL = AUTH_BASE + "/oauth/token";
    public static final String REVOKE_URL = AUTH_BASE + "/oauth/revoke";

    public static final String CHATGPT_BACKEND = "https://chatgpt.com/backend-api";
    public static final String USAGE_URL = CHATGPT_BACKEND + "/wham/usage";
    public static final String RESET_CREDITS_URL =
            CHATGPT_BACKEND + "/wham/rate-limit-reset-credits";
    public static final String RESET_CREDITS_CONSUME_URL = RESET_CREDITS_URL + "/consume";

    private AppConstants() {
    }

    /** Returns {@code <applicationId>.action.<name>}. */
    static String action(String name) {
        return BuildConfig.APPLICATION_ID + ".action." + name;
    }

    public static String userAgent() {
        String release = Build.VERSION.RELEASE == null ? "unknown" : Build.VERSION.RELEASE;
        String model = Build.MODEL == null ? "Android" : Build.MODEL;
        // Keep the literal version in sync with VERSION_NAME; release checks grep for it.
        return "codex-meter-android/0.3.1 (Android " + release + "; " + model + ")";
    }

    public static String updaterUserAgent() {
        return "codex-meter-android/" + VERSION_NAME + " updater";
    }
}
