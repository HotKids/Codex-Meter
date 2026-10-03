package me.pipi.codexmeter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/** Durable updater state, isolated from account and usage preferences. */
public final class UpdatePreferences {
    /** Longest check or install error message that is stored and shown. */
    static final int MAX_ERROR_LENGTH = 240;

    private static final String PREFS = "codex_meter_updates_v1";
    private static final String KEY_AUTOMATIC = "automatic";
    private static final String KEY_CHANNEL = "release_channel";
    private static final String KEY_NOTIFY = "notify_updates";
    private static final String KEY_CHECK_INTERVAL_HOURS = "check_interval_hours";
    private static final String KEY_NOTIFIED_VERSION = "notified_version";
    private static final String KEY_ETAG = "etag";
    private static final String KEY_RELEASES = "releases";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final String KEY_INSTALL_ERROR = "install_error";
    private static final int MAX_CACHE_LENGTH = 512 * 1024;

    /** Parsed form of the most recently seen releases JSON, guarded by {@link #CACHE_LOCK}. */
    private static final Object CACHE_LOCK = new Object();
    private static String cachedJson;
    private static List<GitHubRelease> cachedReleases;

    private UpdatePreferences() {
    }

    public static boolean automaticChecks(Context context) {
        return prefs(context).getBoolean(KEY_AUTOMATIC, true);
    }

    /** Turning automatic checks off also turns update notifications off. */
    public static void setAutomaticChecks(Context context, boolean enabled) {
        SharedPreferences.Editor editor = prefs(context).edit().putBoolean(KEY_AUTOMATIC, enabled);
        if (!enabled) {
            editor.putBoolean(KEY_NOTIFY, false);
        }
        editor.apply();
        if (!enabled) {
            resetUpdateNotification(context);
        }
    }

    public static String channel(Context context) {
        if (!UpdateChannel.STABLE.equals(
                prefs(context).getString(KEY_CHANNEL, UpdateChannel.STABLE))) {
            setChannel(context, UpdateChannel.STABLE);
        }
        return UpdateChannel.STABLE;
    }

    public static void setChannel(Context context, String channel) {
        String normalized = UpdateChannel.STABLE;
        if (normalized.equals(prefs(context).getString(KEY_CHANNEL, UpdateChannel.STABLE))) {
            return;
        }
        prefs(context).edit().putString(KEY_CHANNEL, normalized).apply();
        resetUpdateNotification(context);
        broadcast(context);
        UpdateNotificationManager.onReleasesUpdated(context);
    }

    public static boolean notifyUpdatesEnabled(Context context) {
        return automaticChecks(context) && prefs(context).getBoolean(KEY_NOTIFY, false);
    }

    public static void setNotifyUpdatesEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_NOTIFY, enabled && automaticChecks(context)).apply();
        if (!enabled) {
            resetUpdateNotification(context);
        }
    }

    public static int checkIntervalHours(Context context) {
        return UpdateCheckFrequency.normalize(
                prefs(context).getInt(KEY_CHECK_INTERVAL_HOURS, UpdateCheckFrequency.DAILY));
    }

    public static void setCheckIntervalHours(Context context, int hours) {
        prefs(context).edit()
                .putInt(KEY_CHECK_INTERVAL_HOURS, UpdateCheckFrequency.normalize(hours))
                .apply();
    }

    /** Localized name of an automatic check interval, such as "Every 6 hours". */
    public static String checkIntervalLabel(Context context, int hours) {
        switch (UpdateCheckFrequency.normalize(hours)) {
            case UpdateCheckFrequency.HOURLY:
                return context.getString(R.string.updates_frequency_hourly);
            case UpdateCheckFrequency.EVERY_6_HOURS:
                return context.getString(R.string.updates_frequency_every_6_hours);
            case UpdateCheckFrequency.EVERY_12_HOURS:
                return context.getString(R.string.updates_frequency_every_12_hours);
            case UpdateCheckFrequency.WEEKLY:
                return context.getString(R.string.updates_frequency_weekly);
            case UpdateCheckFrequency.DAILY:
            default:
                return context.getString(R.string.updates_frequency_daily);
        }
    }

    /** Localized summary of the automatic-checks switch for an interval. */
    public static String checkIntervalSummary(Context context, int hours) {
        switch (UpdateCheckFrequency.normalize(hours)) {
            case UpdateCheckFrequency.HOURLY:
                return context.getString(R.string.updates_frequency_summary_hourly);
            case UpdateCheckFrequency.EVERY_6_HOURS:
                return context.getString(R.string.updates_frequency_summary_every_6_hours);
            case UpdateCheckFrequency.EVERY_12_HOURS:
                return context.getString(R.string.updates_frequency_summary_every_12_hours);
            case UpdateCheckFrequency.WEEKLY:
                return context.getString(R.string.updates_frequency_summary_weekly);
            case UpdateCheckFrequency.DAILY:
            default:
                return context.getString(R.string.updates_frequency_summary_daily);
        }
    }

    public static String notifiedVersion(Context context) {
        return prefs(context).getString(KEY_NOTIFIED_VERSION, "");
    }

    public static void setNotifiedVersion(Context context, String version) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (version == null || version.trim().isEmpty()) {
            editor.remove(KEY_NOTIFIED_VERSION);
        } else {
            editor.putString(KEY_NOTIFIED_VERSION, version.trim());
        }
        editor.apply();
    }

    public static void clearNotifiedVersion(Context context) {
        prefs(context).edit().remove(KEY_NOTIFIED_VERSION).apply();
    }

    public static long lastCheckMillis(Context context) {
        return prefs(context).getLong(KEY_LAST_CHECK, 0L);
    }

    public static String etag(Context context) {
        return prefs(context).getString(KEY_ETAG, "");
    }

    public static String lastError(Context context) {
        return prefs(context).getString(KEY_LAST_ERROR, "");
    }

    /** Validates and stores a fresh release list, then notifies listeners. */
    public static void saveSuccess(Context context, String json, String etag) throws Exception {
        if (json == null || json.length() > MAX_CACHE_LENGTH) {
            throw new IllegalArgumentException(
                    context.getString(R.string.updates_error_too_much_metadata));
        }
        List<GitHubRelease> parsed = enabledReleases(
                    GitHubReleaseParser.parse(json, BuildConfig.DEBUG));
        SharedPreferences.Editor editor = prefs(context).edit()
                .putString(KEY_RELEASES, json)
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .remove(KEY_LAST_ERROR);
        if (etag == null || etag.trim().isEmpty()) {
            editor.remove(KEY_ETAG);
        } else {
            editor.putString(KEY_ETAG, etag.trim());
        }
        editor.apply();
        rememberParsed(json, parsed);
        broadcast(context);
        UpdateNotificationManager.onReleasesUpdated(context);
    }

    public static void markNotModified(Context context) {
        prefs(context).edit()
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .remove(KEY_LAST_ERROR)
                .apply();
        broadcast(context);
        UpdateNotificationManager.onReleasesUpdated(context);
    }

    public static void saveError(Context context, String error) {
        prefs(context).edit()
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .putString(KEY_LAST_ERROR, sanitize(error,
                        context.getString(R.string.updates_error_check_failed)))
                .apply();
        broadcast(context);
    }

    /** Cached releases, newest first; empty when nothing valid has been stored. */
    public static List<GitHubRelease> releases(Context context) {
        String json = prefs(context).getString(KEY_RELEASES, "[]");
        synchronized (CACHE_LOCK) {
            if (json.equals(cachedJson) && cachedReleases != null) {
                return cachedReleases;
            }
        }
        try {
            List<GitHubRelease> parsed = enabledReleases(
                    GitHubReleaseParser.parse(json, BuildConfig.DEBUG));
            rememberParsed(json, parsed);
            return parsed;
        } catch (Exception exception) {
            return Collections.emptyList();
        }
    }

    public static boolean hasUsableCache(Context context) {
        SharedPreferences preferences = prefs(context);
        if (!preferences.contains(KEY_RELEASES)) {
            return false;
        }
        String json = preferences.getString(KEY_RELEASES, "");
        try {
            GitHubReleaseParser.parse(json, BuildConfig.DEBUG);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    public static void clearEtag(Context context) {
        prefs(context).edit().remove(KEY_ETAG).apply();
    }

    public static GitHubRelease findVersion(Context context, String version) {
        return GitHubReleaseParser.findVersion(releases(context), version);
    }

    public static GitHubRelease availableUpdate(Context context) {
        return UpdateChannel.selectUpdate(releases(context), installedVersion(context),
                channel(context));
    }

    public static String installedVersion(Context context) {
        try {
            String version = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
            return version == null || version.trim().isEmpty()
                    ? AppConstants.VERSION_NAME : version;
        } catch (Exception exception) {
            return AppConstants.VERSION_NAME;
        }
    }

    public static void setInstallError(Context context, String error) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (error == null || error.trim().isEmpty()) {
            editor.remove(KEY_INSTALL_ERROR);
        } else {
            editor.putString(KEY_INSTALL_ERROR, sanitize(error,
                    context.getString(R.string.updates_error_install_failed)));
        }
        editor.apply();
        broadcast(context);
    }

    public static String installError(Context context) {
        return prefs(context).getString(KEY_INSTALL_ERROR, "");
    }

    private static SharedPreferences prefs(Context context) {
        Context app = context.getApplicationContext();
        return (app == null ? context : app).getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static List<GitHubRelease> enabledReleases(List<GitHubRelease> parsed) {
        List<GitHubRelease> enabled = new ArrayList<>();
        for (GitHubRelease release : parsed) {
            if (!release.prerelease) {
                enabled.add(release);
            }
        }
        return Collections.unmodifiableList(enabled);
    }

    private static void rememberParsed(String json, List<GitHubRelease> parsed) {
        synchronized (CACHE_LOCK) {
            cachedJson = json;
            cachedReleases = parsed;
        }
    }

    private static void resetUpdateNotification(Context context) {
        UpdateNotificationManager.dismiss(context);
        clearNotifiedVersion(context);
    }

    /** Tells in-app screens that release metadata or updater state changed. */
    private static void broadcast(Context context) {
        context.sendBroadcast(new Intent(AppConstants.ACTION_RELEASES_UPDATED)
                        .setPackage(context.getPackageName())
                        .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY),
                AppConstants.INTERNAL_PERMISSION);
    }

    /** Trims {@code value}, substitutes {@code fallback} when blank, and caps the length. */
    private static String sanitize(String value, String fallback) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty()) {
            result = fallback;
        }
        return result.length() <= MAX_ERROR_LENGTH
                ? result : result.substring(0, MAX_ERROR_LENGTH);
    }
}
