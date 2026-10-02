package dev.bennett.codexmeter;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** AI-Usage's global chrome/refresh settings and account-wide window selection. */
final class ReferenceWidgetPreferences {
    static final int[] REFRESH_MINUTES = {0, 5, 15, 30, 60};
    private static final String STYLE = "reference_widget_chrome";
    private static final String INTERVAL = "reference_widget_refresh_minutes";

    private ReferenceWidgetPreferences() { }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE);
    }

    static WidgetOptions apply(Context context, WidgetOptions options) {
        SharedPreferences preferences = preferences(context);
        String key = selectionKey(context);
        String selection = preferences.getString(key, options.visibleMeters);
        String pending = preferences.getString(key + "_legacy", "");
        if (!pending.isEmpty()) {
            QuotaCardOptions.LegacySelectionMigration result = QuotaCardOptions.migrateLegacySelection(
                    selection, pending, AppPreferences.loadSnapshot(context));
            selection = result.selection;
            if (!selection.equals(preferences.getString(key, ""))
                    || !pending.equals(result.remainingPending)) {
                preferences.edit().putString(key, selection)
                        .putString(key + "_legacy", result.remainingPending).apply();
            }
        }
        String canonicalSelection = QuotaCardOptions.migrateToWindowIds(selection,
                WidgetUsageStore.load(context), AppPreferences.loadSnapshot(context));
        if (!canonicalSelection.equals(selection)) {
            selection = canonicalSelection;
            preferences.edit().putString(key, selection).apply();
        }
        return options.withReferenceStyle(preferences.getString(STYLE, options.referenceStyle))
                .withVisibleMeters(selection);
    }

    static int refreshMinutes(Context context) {
        int stored = preferences(context).getInt(INTERVAL, 30);
        for (int minutes : REFRESH_MINUTES) if (minutes == stored) return stored;
        return 30;
    }

    static void save(Context context, int widgetId, WidgetOptions options, int minutes) {
        boolean valid = false;
        for (int candidate : REFRESH_MINUTES) valid |= candidate == minutes;
        SharedPreferences preferences = preferences(context);
        String key = selectionKey(context);
        String legacy = preferences.getString(key + "_legacy",
                preferences.getString("widget_" + widgetId + "_legacy_window_keys",
                        preferences.getString("default_legacy_window_keys", "")));
        preferences.edit().putString(STYLE, options.referenceStyle)
                .putString(key, options.visibleMeters)
                .putString(key + "_legacy", HomeWidgetPreferences.retainedLegacyKeys(legacy, options.visibleMeters))
                .putInt(INTERVAL, valid ? minutes : 30).apply();
    }

    static void restorePending(Context context, int oldId, int newId) {
        SharedPreferences preferences = preferences(context);
        String pending = preferences.getString("widget_" + oldId + "_legacy_window_keys",
                preferences.getString("default_legacy_window_keys", ""));
        String selected = preferences.getString("widget_" + newId + "_visible_meters", "");
        preferences.edit().putString("widget_" + newId + "_legacy_window_keys",
                HomeWidgetPreferences.retainedLegacyKeys(pending, selected)).apply();
    }

    private static String selectionKey(Context context) {
        AuthTokens tokens = SecureTokenStore.load(context);
        String account = tokens == null ? "" : tokens.accountId;
        // Old credentials may not contain an account ID. Keep their existing single-account scope.
        if (account == null || account.isEmpty()) return "reference_widget_windows_current";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(account.getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder("reference_widget_windows_");
            for (byte value : digest) key.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return key.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
