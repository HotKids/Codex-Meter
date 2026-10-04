package me.pipi.codexmeter;

import android.os.SystemClock;
import android.util.Log;

/** Monotonic phase timestamps without accepting authentication or account values. */
final class OAuthTiming {
    private static final String TAG = "CodexMeterAuthTiming";

    enum Phase {
        SOCKET_ACCEPTED,
        REQUEST_READ,
        CALLBACK_VALID,
        EXCHANGE_STARTED,
        EXCHANGE_COMPLETED,
        SAVE_COMPLETED,
        BROWSER_HTTP_WRITE_COMPLETED,
        APP_RETURN_RECEIVED
    }

    private OAuthTiming() {
    }

    static void mark(Phase phase) {
        try {
            Log.i(TAG, phase.name() + " elapsed_ms=" + SystemClock.elapsedRealtime());
        } catch (RuntimeException ignored) {
            // Timing collection must not change the authentication result.
        }
    }
}
