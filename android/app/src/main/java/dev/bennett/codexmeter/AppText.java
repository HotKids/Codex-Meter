package dev.bennett.codexmeter;

import android.content.Context;
import android.content.res.Resources;
import androidx.annotation.StringRes;

/** Phone-only resources for static presentation helpers; never used by shared/Wear code. */
final class AppText {
    private static Context application;

    private AppText() { }

    static void initialize(Context context) {
        application = context.getApplicationContext();
    }

    static String get(@StringRes int id, Object... arguments) {
        if (application == null) {
            throw new IllegalStateException("Application resources have not been initialized");
        }
        // Resolve on every call so a system/app locale change does not cache English labels.
        return application.getResources().getString(id, arguments);
    }

    static String nounSuffix(long count) {
        return application.getResources().getQuantityString(R.plurals.phone_noun_suffix, (int) Math.min(Integer.MAX_VALUE, count));
    }

    static Resources resources() {
        return application.getResources();
    }
}
