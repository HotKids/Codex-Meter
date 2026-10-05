package me.pipi.codexmeter;

import dev.bennett.codexmeter.DashboardSections;
import dev.bennett.codexmeter.HistorySections;
import dev.bennett.codexmeter.UsageHistory;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.WidgetMeters;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/**
 * Main phone settings store: cached usage data and errors, refresh settings, dashboard and
 * usage-history layout, appearance, widget options, OAuth progress, and onboarding state.
 */
public final class AppPreferences {
    private static final String PREFS = "codex_meter_settings_v1";

    // Cached usage data and errors.
    private static final String KEY_SNAPSHOT = "last_snapshot";
    private static final String KEY_ERROR = "last_error";
    private static final String KEY_ERROR_AT = "last_error_at";
    private static final String KEY_RESET_CREDITS = "reset_credits_snapshot";
    private static final String KEY_RESET_ERROR = "reset_credits_error";
    private static final String KEY_RESET_ERROR_AT = "reset_credits_error_at";
    private static final String KEY_SCHEDULER_ERROR = "scheduler_error";
    private static final String KEY_HISTORY_FIVE_HOUR = "usage_history_five_hour";
    private static final String KEY_HISTORY_WEEKLY = "usage_history_weekly";
    private static final String KEY_HISTORY_MONTHLY = "usage_history_monthly";

    // Refresh settings.
    private static final String KEY_AUTOMATIC_REFRESH = "automatic_refresh";
    private static final String KEY_REFRESH_FAILURES = "refresh_failures";
    private static final String KEY_REFRESH_MINUTES = "refresh_minutes";
    private static final String KEY_REFRESH_ON_LAUNCH = "refresh_on_launch";

    // Dashboard and usage-history layout.
    private static final String KEY_DASHBOARD_FIVE_HOUR = "dashboard_five_hour";
    private static final String KEY_DASHBOARD_MONTHLY = "dashboard_monthly";
    private static final String KEY_DASHBOARD_RESET_CREDITS = "dashboard_reset_credits";
    private static final String KEY_DASHBOARD_SECTION_ORDER = "dashboard_section_order";
    private static final String KEY_DASHBOARD_USAGE_CREDITS = "dashboard_usage_credits";
    private static final String KEY_DASHBOARD_USAGE_HISTORY = "dashboard_usage_history";
    private static final String KEY_DASHBOARD_WEEKLY = "dashboard_weekly";
    private static final String KEY_HISTORY_SECTION_OVERRIDES = "history_section_overrides";

    // Appearance.
    private static final String KEY_APP_STYLE = "app_surface_style";
    private static final String KEY_APP_THEME = "app_theme";
    private static final String KEY_MATERIAL_YOU = "material_you";

    // Widget option fields, stored per widget as "default_<field>" (defaults for new home-screen
    // widgets), "widget_<id>_<field>" (placed home-screen widgets), and "lock_widget_<id>_<field>".
    private static final String DEFAULT_WIDGET_PREFIX = "default_";
    private static final String KEY_NOW_BAR_WIDGET_ID = "now_bar_widget_id";
    private static final String FIELD_STYLE = "style";
    private static final String FIELD_LAYOUT = "layout";
    private static final String FIELD_DENSITY = "density";
    private static final String FIELD_SURFACE_STYLE = "surface_style";
    private static final String FIELD_COLOR_STYLE = "color_style";
    private static final String FIELD_GRAPHIC_SCALE = "graphic_scale";
    private static final String FIELD_THEME = "theme";
    private static final String FIELD_ACCENT = "accent";
    private static final String FIELD_OPACITY = "opacity";
    private static final String FIELD_RESET_MODE = "reset_mode";
    private static final String FIELD_DISPLAY_MODE = "display_mode";
    private static final String FIELD_METRIC_MODE = "metric_mode";
    private static final String FIELD_VISIBLE_METERS = "visible_meters";
    private static final String FIELD_SHOW_TITLE = "show_title";
    private static final String FIELD_SHOW_PLAN = "show_plan";
    private static final String FIELD_SHOW_UPDATED = "show_updated";
    private static final String FIELD_SHOW_REFRESH = "show_refresh";
    private static final String FIELD_SHOW_RESET_CREDITS = "show_reset_credits";
    private static final String FIELD_SHOW_RESET_ACTION = "show_reset_action";
    private static final String FIELD_SHOW_PERCENT_SYMBOL = "show_percent_symbol";
    private static final String FIELD_SHOW_COUNTDOWN = "show_countdown";
    private static final String FIELD_TAP_ACTION = "tap_action";
    private static final String FIELD_CARD_STYLE = "card_style";
    /** Every field {@link #saveWidgetOptions} writes for a home-screen widget. */
    private static final String[] HOME_WIDGET_FIELDS = {
            FIELD_STYLE, FIELD_LAYOUT, FIELD_DENSITY, FIELD_SURFACE_STYLE, FIELD_COLOR_STYLE,
            FIELD_GRAPHIC_SCALE,
            FIELD_THEME, FIELD_ACCENT, FIELD_OPACITY, FIELD_RESET_MODE, FIELD_DISPLAY_MODE,
            FIELD_METRIC_MODE, FIELD_VISIBLE_METERS, FIELD_SHOW_TITLE, FIELD_SHOW_PLAN,
            FIELD_SHOW_UPDATED, FIELD_SHOW_REFRESH, FIELD_SHOW_RESET_CREDITS,
            FIELD_SHOW_RESET_ACTION, FIELD_SHOW_PERCENT_SYMBOL, FIELD_CARD_STYLE
    };

    // OAuth and onboarding.
    private static final String KEY_OAUTH_PENDING = "oauth_pending";
    private static final String KEY_OAUTH_STARTED_AT = "oauth_started_at";
    private static final String KEY_OAUTH_URL = "oauth_url";
    private static final String KEY_REAUTHENTICATION_REQUIRED = "reauthentication_required";
    private static final String KEY_ONBOARDING_COMPLETE = "onboarding_complete";
    private static final String KEY_ONBOARDING_STEP = "onboarding_step";

    private static final int DEFAULT_REFRESH_MINUTES = 30;
    private static final int MAX_REFRESH_FAILURES = 3;
    private static final int MAX_ERROR_LENGTH = 240;
    /** Widget ID 0 stands for the defaults that new widgets start from. */
    private static final int DEFAULT_WIDGET_ID = 0;
    /** Refresh errors stay hidden while the cached usage is younger than this. */
    private static final long REFRESH_ERROR_GRACE_MS = TimeUnit.MINUTES.toMillis(15);
    /** Reset-credit errors stay hidden while the cached credits are younger than this. */
    private static final long RESET_CREDITS_ERROR_GRACE_MS = TimeUnit.MINUTES.toMillis(30);
    private static final long OAUTH_STALE_AFTER_MS = TimeUnit.MINUTES.toMillis(12);

    private AppPreferences() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------------------------------
    // Usage snapshot and refresh errors
    // ---------------------------------------------------------------------------------------

    public static boolean saveSnapshot(Context context, UsageSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        try {
            return prefs(context).edit()
                    .putString(KEY_SNAPSHOT, snapshot.toJson().toString())
                    .remove(KEY_ERROR)
                    .remove(KEY_ERROR_AT)
                    .commit();
        } catch (Exception e) {
            setLastError(context, context.getString(R.string.auth_error_usage_not_cached));
            return false;
        }
    }

    public static UsageSnapshot loadSnapshot(Context context) {
        String json = prefs(context).getString(KEY_SNAPSHOT, null);
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return UsageSnapshot.fromJson(new JSONObject(json));
        } catch (Exception e) {
            return null;
        }
    }

    /** Forgets all cached usage data and the state derived from it (signing out). */
    public static void clearSnapshot(Context context) {
        prefs(context).edit()
                .remove(KEY_SNAPSHOT).remove(KEY_ERROR).remove(KEY_ERROR_AT)
                .remove(KEY_RESET_CREDITS).remove(KEY_RESET_ERROR).remove(KEY_RESET_ERROR_AT)
                .remove(KEY_HISTORY_FIVE_HOUR).remove(KEY_HISTORY_WEEKLY)
                .remove(KEY_HISTORY_MONTHLY)
                .remove(KEY_REFRESH_FAILURES)
                .apply();
        NowBarManager.stop(context);
        NowBarPreferences.clearSuppression(context);
        ResetNotificationManager.clearState(context);
        ResetCreditExpiryScheduler.cancelAll(context);
    }

    public static void setLastError(Context context, String message) {
        if (isBlank(message)) {
            clearLastError(context);
            return;
        }
        prefs(context).edit()
                .putString(KEY_ERROR, clip(message))
                .putLong(KEY_ERROR_AT, System.currentTimeMillis())
                .apply();
    }

    public static void clearLastError(Context context) {
        prefs(context).edit().remove(KEY_ERROR).remove(KEY_ERROR_AT).apply();
    }

    public static String getLastError(Context context) {
        return prefs(context).getString(KEY_ERROR, "");
    }

    /**
     * Returns the last refresh error unless newer usage data superseded it (which also clears
     * it) or the cached usage is still fresh enough that the error is not worth showing.
     */
    public static String getVisibleRefreshError(Context context) {
        String lastError = getLastError(context);
        if (lastError.isEmpty()) {
            return "";
        }
        UsageSnapshot snapshot = loadSnapshot(context);
        if (snapshot == null) {
            return lastError;
        }
        long errorAt = prefs(context).getLong(KEY_ERROR_AT, 0L);
        if (errorAt > 0 && errorAt <= snapshot.fetchedAtMillis) {
            clearLastError(context);
            return "";
        }
        long snapshotAge = Math.max(0L, System.currentTimeMillis() - snapshot.fetchedAtMillis);
        return snapshotAge < REFRESH_ERROR_GRACE_MS ? "" : lastError;
    }

    // ---------------------------------------------------------------------------------------
    // Usage history
    // ---------------------------------------------------------------------------------------

    public static UsageHistory loadUsageHistory(Context context, String kind) {
        String stored = prefs(context).getString(historyKey(kind), null);
        if (stored == null || stored.isEmpty()) {
            return UsageHistory.empty(kind);
        }
        try {
            return UsageHistory.fromJson(new JSONObject(stored), kind);
        } catch (Exception ignored) {
            return UsageHistory.empty(kind);
        }
    }

    public static boolean saveUsageHistory(Context context, UsageHistory history) {
        if (history == null) {
            return false;
        }
        try {
            return prefs(context).edit()
                    .putString(historyKey(history.kind), history.toJson().toString())
                    .commit();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void clearUsageHistory(Context context) {
        prefs(context).edit()
                .remove(KEY_HISTORY_FIVE_HOUR)
                .remove(KEY_HISTORY_WEEKLY)
                .remove(KEY_HISTORY_MONTHLY)
                .apply();
    }

    private static String historyKey(String kind) {
        if (UsageHistory.WEEKLY.equals(kind)) {
            return KEY_HISTORY_WEEKLY;
        }
        if (UsageHistory.MONTHLY.equals(kind)) {
            return KEY_HISTORY_MONTHLY;
        }
        return KEY_HISTORY_FIVE_HOUR;
    }

    // ---------------------------------------------------------------------------------------
    // Reset credits
    // ---------------------------------------------------------------------------------------

    public static boolean saveResetCredits(Context context, ResetCreditsSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        boolean saved;
        try {
            saved = prefs(context).edit()
                    .putString(KEY_RESET_CREDITS, snapshot.toJson().toString())
                    .remove(KEY_RESET_ERROR)
                    .remove(KEY_RESET_ERROR_AT)
                    .commit();
        } catch (Exception e) {
            setResetCreditsError(context,
                    context.getString(R.string.auth_error_reset_credits_not_cached));
            return false;
        }
        if (saved) {
            NowBarManager.repostActive(context);
        }
        return saved;
    }

    /**
     * Loads the cached reset-credit details, falling back to the available-credit count from
     * the usage snapshot when no details are cached or the usage response reports a newer,
     * different count.
     */
    public static ResetCreditsSnapshot loadResetCredits(Context context) {
        ResetCreditsSnapshot stored = null;
        String json = prefs(context).getString(KEY_RESET_CREDITS, null);
        if (json != null && !json.isEmpty()) {
            try {
                stored = ResetCreditsSnapshot.fromJson(new JSONObject(json));
            } catch (Exception ignored) {
            }
        }
        UsageSnapshot usage = loadSnapshot(context);
        if (usage == null || usage.resetCreditsAvailable < 0) {
            return stored;
        }
        if (stored == null
                || (usage.fetchedAtMillis > stored.fetchedAtMillis
                        && usage.resetCreditsAvailable != stored.availableCount)) {
            return ResetCreditsSnapshot.summary(usage.resetCreditsAvailable,
                    usage.fetchedAtMillis);
        }
        return stored;
    }

    public static void setResetCreditsError(Context context, String message) {
        if (isBlank(message)) {
            clearResetCreditsError(context);
            return;
        }
        prefs(context).edit()
                .putString(KEY_RESET_ERROR, clip(message))
                .putLong(KEY_RESET_ERROR_AT, System.currentTimeMillis())
                .apply();
    }

    public static void clearResetCreditsError(Context context) {
        prefs(context).edit().remove(KEY_RESET_ERROR).remove(KEY_RESET_ERROR_AT).apply();
    }

    public static String getResetCreditsError(Context context) {
        return prefs(context).getString(KEY_RESET_ERROR, "");
    }

    /** Same visibility rules as {@link #getVisibleRefreshError}, for reset-credit refreshes. */
    public static String getVisibleResetCreditsError(Context context) {
        String error = getResetCreditsError(context);
        if (error.isEmpty()) {
            return "";
        }
        ResetCreditsSnapshot credits = loadResetCredits(context);
        if (credits == null) {
            return error;
        }
        long errorAt = prefs(context).getLong(KEY_RESET_ERROR_AT, 0L);
        if (errorAt > 0 && errorAt <= credits.fetchedAtMillis) {
            clearResetCreditsError(context);
            return "";
        }
        long creditsAge = Math.max(0L, System.currentTimeMillis() - credits.fetchedAtMillis);
        return creditsAge < RESET_CREDITS_ERROR_GRACE_MS ? "" : error;
    }

    // ---------------------------------------------------------------------------------------
    // Background refresh
    // ---------------------------------------------------------------------------------------

    public static void setSchedulerError(Context context, String message) {
        if (isBlank(message)) {
            prefs(context).edit().remove(KEY_SCHEDULER_ERROR).apply();
        } else {
            prefs(context).edit().putString(KEY_SCHEDULER_ERROR, clip(message)).apply();
        }
    }

    public static String getSchedulerError(Context context) {
        return prefs(context).getString(KEY_SCHEDULER_ERROR, "");
    }

    public static int getRefreshMinutes(Context context) {
        int minutes = prefs(context).getInt(KEY_REFRESH_MINUTES, DEFAULT_REFRESH_MINUTES);
        return isValidRefreshMinutes(minutes) ? minutes : DEFAULT_REFRESH_MINUTES;
    }

    public static void setRefreshMinutes(Context context, int minutes) {
        prefs(context).edit()
                .putInt(KEY_REFRESH_MINUTES,
                        isValidRefreshMinutes(minutes) ? minutes : DEFAULT_REFRESH_MINUTES)
                .apply();
    }

    private static boolean isValidRefreshMinutes(int minutes) {
        return minutes == 5 || minutes == 10 || minutes == 15 || minutes == 30
                || minutes == 60 || minutes == 120;
    }

    public static boolean getAutomaticRefresh(Context context) {
        return prefs(context).getBoolean(KEY_AUTOMATIC_REFRESH, true);
    }

    public static void setAutomaticRefresh(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTOMATIC_REFRESH, enabled).apply();
    }

    /** Consecutive failed background refreshes, capped at {@value #MAX_REFRESH_FAILURES}. */
    public static int getRefreshFailures(Context context) {
        int failures = prefs(context).getInt(KEY_REFRESH_FAILURES, 0);
        return Math.max(0, Math.min(MAX_REFRESH_FAILURES, failures));
    }

    public static void recordRefreshSuccess(Context context) {
        prefs(context).edit().remove(KEY_REFRESH_FAILURES).apply();
    }

    public static void recordRefreshFailure(Context context) {
        int failures = Math.min(MAX_REFRESH_FAILURES, getRefreshFailures(context) + 1);
        prefs(context).edit().putInt(KEY_REFRESH_FAILURES, failures).apply();
    }

    public static boolean getRefreshOnLaunch(Context context) {
        return prefs(context).getBoolean(KEY_REFRESH_ON_LAUNCH, true);
    }

    public static void setRefreshOnLaunch(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_REFRESH_ON_LAUNCH, enabled).apply();
    }

    // ---------------------------------------------------------------------------------------
    // Dashboard sections
    // ---------------------------------------------------------------------------------------

    public static boolean showDashboardFiveHour(Context context) {
        return prefs(context).getBoolean(KEY_DASHBOARD_FIVE_HOUR, true);
    }

    public static void setShowDashboardFiveHour(Context context, boolean show) {
        if (showDashboardFiveHour(context) == show) {
            return;
        }
        putBoolean(context, KEY_DASHBOARD_FIVE_HOUR, show);
        NowBarManager.repostActive(context);
    }

    public static boolean showDashboardWeekly(Context context) {
        return prefs(context).getBoolean(KEY_DASHBOARD_WEEKLY, true);
    }

    public static void setShowDashboardWeekly(Context context, boolean show) {
        putBoolean(context, KEY_DASHBOARD_WEEKLY, show);
    }

    public static boolean showDashboardMonthly(Context context) {
        return prefs(context).getBoolean(KEY_DASHBOARD_MONTHLY, true);
    }

    public static void setShowDashboardMonthly(Context context, boolean show) {
        putBoolean(context, KEY_DASHBOARD_MONTHLY, show);
    }

    public static boolean showDashboardUsageCredits(Context context) {
        return prefs(context).getBoolean(KEY_DASHBOARD_USAGE_CREDITS, true);
    }

    public static void setShowDashboardUsageCredits(Context context, boolean show) {
        putBoolean(context, KEY_DASHBOARD_USAGE_CREDITS, show);
    }

    public static boolean showDashboardUsageHistory(Context context) {
        return prefs(context).getBoolean(KEY_DASHBOARD_USAGE_HISTORY, true);
    }

    public static void setShowDashboardUsageHistory(Context context, boolean show) {
        putBoolean(context, KEY_DASHBOARD_USAGE_HISTORY, show);
    }

    public static boolean showDashboardResetCredits(Context context) {
        return prefs(context).getBoolean(KEY_DASHBOARD_RESET_CREDITS, true);
    }

    public static void setShowDashboardResetCredits(Context context, boolean show) {
        putBoolean(context, KEY_DASHBOARD_RESET_CREDITS, show);
    }

    public static void setDashboardVisibility(Context context, boolean fiveHour,
            boolean weekly, boolean monthly, boolean usageCredits,
            boolean resetCredits, boolean usageHistory) {
        prefs(context).edit()
                .putBoolean(KEY_DASHBOARD_FIVE_HOUR, fiveHour)
                .putBoolean(KEY_DASHBOARD_WEEKLY, weekly)
                .putBoolean(KEY_DASHBOARD_MONTHLY, monthly)
                .putBoolean(KEY_DASHBOARD_USAGE_CREDITS, usageCredits)
                .putBoolean(KEY_DASHBOARD_RESET_CREDITS, resetCredits)
                .putBoolean(KEY_DASHBOARD_USAGE_HISTORY, usageHistory)
                .apply();
    }

    /** Saved dashboard section order as a comma-separated {@link DashboardSections} key list. */
    public static String getDashboardOrder(Context context) {
        return prefs(context).getString(KEY_DASHBOARD_SECTION_ORDER, "");
    }

    public static void setDashboardOrder(Context context, List<String> order) {
        String csv = DashboardSections.serialize(order);
        if (csv.isEmpty()) {
            prefs(context).edit().remove(KEY_DASHBOARD_SECTION_ORDER).apply();
        } else {
            prefs(context).edit().putString(KEY_DASHBOARD_SECTION_ORDER, csv).apply();
        }
    }

    // ---------------------------------------------------------------------------------------
    // Usage-history highlights
    // ---------------------------------------------------------------------------------------

    /** Usage-history highlight overrides as a {@link HistorySections} CSV. */
    public static String getHistorySectionOverrides(Context context) {
        return prefs(context).getString(KEY_HISTORY_SECTION_OVERRIDES, "");
    }

    public static void setHistorySectionOverrides(Context context, String overridesCsv) {
        putOrRemoveIfBlank(context, KEY_HISTORY_SECTION_OVERRIDES, overridesCsv);
    }

    public static boolean isHistorySectionVisible(Context context, String key) {
        return HistorySections.isVisible(getHistorySectionOverrides(context), key);
    }

    public static void setHistorySectionVisible(Context context, String key, boolean visible) {
        setHistorySectionOverrides(context,
                HistorySections.setVisible(getHistorySectionOverrides(context), key, visible));
    }

    // ---------------------------------------------------------------------------------------
    // Appearance
    // ---------------------------------------------------------------------------------------

    public static String getAppTheme(Context context) {
        return normalizeAppTheme(
                prefs(context).getString(KEY_APP_THEME, WidgetOptions.THEME_SYSTEM));
    }

    public static void setAppTheme(Context context, String theme) {
        prefs(context).edit().putString(KEY_APP_THEME, normalizeAppTheme(theme)).commit();
    }

    private static String normalizeAppTheme(String theme) {
        return WidgetOptions.THEME_DARK.equals(theme) || WidgetOptions.THEME_LIGHT.equals(theme)
                ? theme : WidgetOptions.THEME_SYSTEM;
    }

    /** When enabled, accents follow Android Material You system colors (API 31+). */
    public static boolean isMaterialYouEnabled(Context context) {
        return prefs(context).getBoolean(KEY_MATERIAL_YOU, false);
    }

    public static void setMaterialYouEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_MATERIAL_YOU, enabled).commit();
    }

    /** The app always uses the One UI surface; other styles are no longer offered. */
    public static String getAppStyle(Context context) {
        return WidgetOptions.SURFACE_ONE_UI;
    }

    /** Stores the One UI surface regardless of {@code style}; see {@link #getAppStyle}. */
    public static void setAppStyle(Context context, String style) {
        prefs(context).edit().putString(KEY_APP_STYLE, WidgetOptions.SURFACE_ONE_UI).apply();
    }

    // ---------------------------------------------------------------------------------------
    // Home-screen widget options
    // ---------------------------------------------------------------------------------------

    public static WidgetOptions loadDefaultWidgetOptions(Context context) {
        SharedPreferences prefs = prefs(context);
        String prefix = DEFAULT_WIDGET_PREFIX;
        String defaultVisibleMeters = "";
        if (!prefs.contains(prefix + FIELD_VISIBLE_METERS)
                && !prefs.contains(prefix + FIELD_METRIC_MODE)) {
            UsageSnapshot snapshot = loadSnapshot(context);
            if (snapshot != null && UsageFormat.planLabel(snapshot.planType).startsWith("Pro ")) {
                defaultVisibleMeters = WidgetMeters.WEEKLY + "," + WidgetMeters.NEXT_RESET;
            }
        }
        return normalizeLoaded(new WidgetOptions(
                prefs.getString(prefix + FIELD_STYLE, WidgetOptions.STYLE_AUTO),
                prefs.getString(prefix + FIELD_DENSITY, "auto"),
                prefs.getString(prefix + FIELD_SURFACE_STYLE, WidgetOptions.SURFACE_ONE_UI),
                prefs.getString(prefix + FIELD_GRAPHIC_SCALE, "auto"),
                prefs.getString(prefix + FIELD_THEME, WidgetOptions.THEME_SYSTEM),
                prefs.getString(prefix + FIELD_ACCENT, WidgetOptions.ACCENT_BLUE),
                prefs.getInt(prefix + FIELD_OPACITY, WidgetOptions.DEFAULT_OPACITY),
                prefs.getString(prefix + FIELD_RESET_MODE, WidgetOptions.RESET_ABSOLUTE),
                prefs.getString(prefix + FIELD_DISPLAY_MODE, WidgetOptions.DISPLAY_REMAINING),
                prefs.getString(prefix + FIELD_METRIC_MODE, WidgetOptions.METRIC_BOTH),
                false,
                prefs.getBoolean(prefix + FIELD_SHOW_PLAN, false),
                prefs.getBoolean(prefix + FIELD_SHOW_UPDATED, false),
                prefs.getBoolean(prefix + FIELD_SHOW_REFRESH, true),
                prefs.getBoolean(prefix + FIELD_SHOW_RESET_CREDITS, false),
                prefs.getBoolean(prefix + FIELD_SHOW_RESET_ACTION, false))
                .withPercentSymbol(prefs.getBoolean(prefix + FIELD_SHOW_PERCENT_SYMBOL, true))
                .withVisibleMeters(prefs.getString(prefix + FIELD_VISIBLE_METERS,
                        defaultVisibleMeters))
                .withColorStyle(prefs.getString(prefix + FIELD_COLOR_STYLE,
                        WidgetOptions.COLOR_AUTO))
                .withCardStyle(prefs.getString(prefix + FIELD_CARD_STYLE,
                        WidgetOptions.CARD_CLEAR)));
    }

    public static void saveDefaultWidgetOptions(Context context, WidgetOptions options) {
        SharedPreferences.Editor editor = prefs(context).edit();
        putWidgetOptions(editor, DEFAULT_WIDGET_PREFIX, options, options.metricMode);
        editor.apply();
    }

    /** Loads a placed widget's options, falling back to the defaults for unset fields. */
    public static WidgetOptions loadWidgetOptions(Context context, int widgetId) {
        if (widgetId == DEFAULT_WIDGET_ID) {
            return loadDefaultWidgetOptions(context);
        }
        SharedPreferences prefs = prefs(context);
        WidgetOptions defaults = loadDefaultWidgetOptions(context);
        String prefix = widgetPrefix(widgetId);
        // A placed widget's legacy content choice takes precedence over account defaults.
        String inheritedMeters = prefs.contains(prefix + FIELD_METRIC_MODE)
                ? "" : defaults.visibleMeters;
        if (!prefs.contains(prefix + FIELD_VISIBLE_METERS)
                && !prefs.contains(prefix + FIELD_METRIC_MODE)) {
            UsageSnapshot snapshot = loadSnapshot(context);
            if (snapshot != null && UsageFormat.planLabel(snapshot.planType).startsWith("Pro ")) {
                // New Pro widgets must not inherit the session meter from older global defaults.
                List<String> meters = WidgetMeters.parse(defaults.effectiveVisibleMeters());
                meters.remove(WidgetMeters.FIVE_HOUR);
                if (meters.isEmpty()) {
                    meters.add(WidgetMeters.WEEKLY);
                    meters.add(WidgetMeters.NEXT_RESET);
                }
                inheritedMeters = WidgetMeters.serialize(meters);
            }
        }
        return normalizeLoaded(new WidgetOptions(
                prefs.getString(prefix + FIELD_STYLE, defaults.layout),
                prefs.getString(prefix + FIELD_DENSITY, defaults.density),
                prefs.getString(prefix + FIELD_SURFACE_STYLE, defaults.surfaceStyle),
                prefs.getString(prefix + FIELD_GRAPHIC_SCALE, defaults.graphicScale),
                prefs.getString(prefix + FIELD_THEME, defaults.theme),
                prefs.getString(prefix + FIELD_ACCENT, defaults.accent),
                prefs.getInt(prefix + FIELD_OPACITY, defaults.opacity),
                prefs.getString(prefix + FIELD_RESET_MODE, defaults.resetMode),
                prefs.getString(prefix + FIELD_DISPLAY_MODE, defaults.displayMode),
                prefs.getString(prefix + FIELD_METRIC_MODE, defaults.metricMode),
                false,
                prefs.getBoolean(prefix + FIELD_SHOW_PLAN, defaults.showPlan),
                prefs.getBoolean(prefix + FIELD_SHOW_UPDATED, defaults.showUpdated),
                prefs.getBoolean(prefix + FIELD_SHOW_REFRESH, defaults.showRefresh),
                prefs.getBoolean(prefix + FIELD_SHOW_RESET_CREDITS, defaults.showResetCredits),
                prefs.getBoolean(prefix + FIELD_SHOW_RESET_ACTION, defaults.showResetAction))
                .withPercentSymbol(prefs.getBoolean(prefix + FIELD_SHOW_PERCENT_SYMBOL,
                        defaults.showPercentSymbol))
                .withVisibleMeters(prefs.getString(prefix + FIELD_VISIBLE_METERS,
                        inheritedMeters))
                .withColorStyle(prefs.getString(prefix + FIELD_COLOR_STYLE,
                        defaults.colorStyle))
                .withCardStyle(prefs.getString(prefix + FIELD_CARD_STYLE,
                        defaults.cardStyle)));
    }

    /** Saves a placed widget's options; its metric mode is derived from the visible meters. */
    public static void saveWidgetOptions(Context context, int widgetId, WidgetOptions options) {
        writeWidgetOptions(context, widgetId, options, false);
    }

    /** A user save chooses which widget supplies the live activity's second line. */
    public static boolean saveWidgetOptionsAndFollow(Context context, int widgetId,
            WidgetOptions options) {
        return writeWidgetOptions(context, widgetId, options, true);
    }

    private static boolean writeWidgetOptions(Context context, int widgetId, WidgetOptions options,
            boolean followForNowBar) {
        String prefix = widgetPrefix(widgetId);
        String metricMode = metricModeFromVisible(options.effectiveVisibleMeters());
        SharedPreferences.Editor editor = prefs(context).edit();
        putWidgetOptions(editor, prefix, options, metricMode);
        if (followForNowBar) {
            editor.putInt(KEY_NOW_BAR_WIDGET_ID, widgetId);
            if (!editor.commit()) {
                return false;
            }
        } else {
            editor.apply();
        }
        if (followForNowBar || prefs(context).getInt(KEY_NOW_BAR_WIDGET_ID, 0) == widgetId) {
            NowBarManager.repostActive(context);
        }
        return true;
    }

    static WidgetOptions loadNowBarWidgetOptions(Context context) {
        int widgetId = prefs(context).getInt(KEY_NOW_BAR_WIDGET_ID, 0);
        return widgetId > 0 && prefs(context).contains(widgetPrefix(widgetId) + FIELD_VISIBLE_METERS)
                ? loadWidgetOptions(context, widgetId) : null;
    }

    public static void deleteWidgetOptions(Context context, int widgetId) {
        String prefix = widgetPrefix(widgetId);
        SharedPreferences.Editor editor = prefs(context).edit();
        for (String field : HOME_WIDGET_FIELDS) {
            editor.remove(prefix + field);
        }
        editor.remove(prefix + FIELD_TAP_ACTION);
        boolean followed = prefs(context).getInt(KEY_NOW_BAR_WIDGET_ID, 0) == widgetId;
        if (followed) {
            editor.remove(KEY_NOW_BAR_WIDGET_ID);
        }
        editor.apply();
        if (followed) {
            NowBarManager.repostActive(context);
        }
    }

    /** Old and new host IDs can overlap, so capture every old option before one atomic remap. */
    public static void restoreWidgetOptions(Context context, int[] oldIds, int[] newIds) {
        int count = Math.min(oldIds.length, newIds.length);
        if (count == 0) {
            return;
        }
        int followedId = prefs(context).getInt(KEY_NOW_BAR_WIDGET_ID, 0);
        WidgetOptions[] options = new WidgetOptions[count];
        String[] tapActions = new String[count];
        for (int index = 0; index < count; index++) {
            options[index] = loadWidgetOptions(context, oldIds[index]);
            tapActions[index] = getWidgetTapAction(context, oldIds[index]);
        }
        SharedPreferences.Editor editor = prefs(context).edit();
        for (int index = 0; index < count; index++) {
            String oldPrefix = widgetPrefix(oldIds[index]);
            for (String field : HOME_WIDGET_FIELDS) {
                editor.remove(oldPrefix + field);
            }
            editor.remove(oldPrefix + FIELD_TAP_ACTION);
        }
        boolean followed = false;
        for (int index = 0; index < count; index++) {
            String newPrefix = widgetPrefix(newIds[index]);
            putWidgetOptions(editor, newPrefix, options[index],
                    metricModeFromVisible(options[index].effectiveVisibleMeters()));
            editor.putString(newPrefix + FIELD_TAP_ACTION, tapActions[index]);
            if (followedId == oldIds[index]) {
                editor.putInt(KEY_NOW_BAR_WIDGET_ID, newIds[index]);
                followed = true;
            }
        }
        if (!editor.commit()) {
            throw new IllegalStateException("Could not restore widget settings");
        }
        if (followed) {
            NowBarManager.repostActive(context);
        }
    }

    public static String getWidgetTapAction(Context context, int appWidgetId) {
        if (appWidgetId == DEFAULT_WIDGET_ID) {
            return WidgetOptions.TAP_OPEN_APP;
        }
        return WidgetOptions.normalizeTapAction(prefs(context).getString(
                widgetPrefix(appWidgetId) + FIELD_TAP_ACTION, WidgetOptions.TAP_OPEN_APP));
    }

    public static void saveWidgetTapAction(Context context, int appWidgetId, String action) {
        if (appWidgetId == DEFAULT_WIDGET_ID) {
            return;
        }
        prefs(context).edit()
                .putString(widgetPrefix(appWidgetId) + FIELD_TAP_ACTION,
                        WidgetOptions.normalizeTapAction(action))
                .apply();
    }

    private static String widgetPrefix(int widgetId) {
        return "widget_" + widgetId + "_";
    }

    private static void putWidgetOptions(SharedPreferences.Editor editor, String prefix,
            WidgetOptions options, String metricMode) {
        editor.putString(prefix + FIELD_STYLE, options.layout)
                .putString(prefix + FIELD_LAYOUT, options.layout)
                .putString(prefix + FIELD_DENSITY, options.density)
                .putString(prefix + FIELD_SURFACE_STYLE, options.surfaceStyle)
                .putString(prefix + FIELD_COLOR_STYLE, options.colorStyle)
                .putString(prefix + FIELD_GRAPHIC_SCALE, options.graphicScale)
                .putString(prefix + FIELD_THEME, options.theme)
                .putString(prefix + FIELD_ACCENT, options.accent)
                .putInt(prefix + FIELD_OPACITY, options.opacity)
                .putString(prefix + FIELD_RESET_MODE, options.resetMode)
                .putString(prefix + FIELD_DISPLAY_MODE, options.displayMode)
                .putString(prefix + FIELD_METRIC_MODE, metricMode)
                .putString(prefix + FIELD_VISIBLE_METERS, options.visibleMeters)
                .putBoolean(prefix + FIELD_SHOW_TITLE, options.showTitle)
                .putBoolean(prefix + FIELD_SHOW_PLAN, options.showPlan)
                .putBoolean(prefix + FIELD_SHOW_UPDATED, options.showUpdated)
                .putBoolean(prefix + FIELD_SHOW_REFRESH, options.showRefresh)
                .putBoolean(prefix + FIELD_SHOW_RESET_CREDITS, options.showResetCredits)
                .putBoolean(prefix + FIELD_SHOW_RESET_ACTION, options.showResetAction)
                .putBoolean(prefix + FIELD_SHOW_PERCENT_SYMBOL, options.showPercentSymbol)
                .putString(prefix + FIELD_CARD_STYLE, options.cardStyle);
    }

    /**
     * Keeps One UI surface defaults and hides unused chrome flags without wiping the user's
     * layout preference or visible-meters selection.
     */
    private static WidgetOptions normalizeLoaded(WidgetOptions options) {
        return new WidgetOptions(options.layout, WidgetOptions.DENSITY_AUTO,
                WidgetOptions.SURFACE_ONE_UI, "auto", options.theme, options.accent,
                options.opacity, WidgetOptions.RESET_HIDDEN, options.displayMode,
                options.metricMode, false, false, false, false, false, false)
                .withPercentSymbol(options.showPercentSymbol)
                .withVisibleMeters(options.visibleMeters)
                .withColorStyle(options.colorStyle)
                .withCardStyle(options.cardStyle);
    }

    /** The legacy metric mode matching which of the 5-hour and weekly meters are visible. */
    private static String metricModeFromVisible(String visibleCsv) {
        List<String> keys = WidgetMeters.parse(visibleCsv);
        boolean fiveHour = WidgetMeters.contains(keys, WidgetMeters.FIVE_HOUR);
        boolean weekly = WidgetMeters.contains(keys, WidgetMeters.WEEKLY);
        if (fiveHour && !weekly) {
            return WidgetOptions.METRIC_FIVE_HOUR;
        }
        if (weekly && !fiveHour) {
            return WidgetOptions.METRIC_WEEKLY;
        }
        return WidgetOptions.METRIC_BOTH;
    }

    // ---------------------------------------------------------------------------------------
    // Lock-screen widget options
    // ---------------------------------------------------------------------------------------

    public static LockWidgetOptions loadLockWidgetOptions(Context context, int widgetId) {
        if (widgetId == DEFAULT_WIDGET_ID) {
            return LockWidgetOptions.defaults();
        }
        SharedPreferences prefs = prefs(context);
        return new LockWidgetOptions(
                prefs.getString(lockWidgetKey(widgetId, FIELD_METRIC_MODE),
                        WidgetOptions.METRIC_BOTH),
                prefs.getBoolean(lockWidgetKey(widgetId, FIELD_SHOW_RESET_CREDITS), false),
                prefs.getBoolean(lockWidgetKey(widgetId, FIELD_SHOW_RESET_ACTION), false),
                prefs.getBoolean(lockWidgetKey(widgetId, FIELD_SHOW_COUNTDOWN), true),
                prefs.getString(lockWidgetKey(widgetId, FIELD_VISIBLE_METERS), ""));
    }

    public static void saveLockWidgetOptions(Context context, int widgetId,
            LockWidgetOptions options) {
        if (widgetId == DEFAULT_WIDGET_ID || options == null) {
            return;
        }
        prefs(context).edit()
                .putString(lockWidgetKey(widgetId, FIELD_METRIC_MODE), options.metricMode)
                .putBoolean(lockWidgetKey(widgetId, FIELD_SHOW_RESET_CREDITS),
                        options.showResetCredits)
                .putBoolean(lockWidgetKey(widgetId, FIELD_SHOW_RESET_ACTION),
                        options.showResetAction)
                .putBoolean(lockWidgetKey(widgetId, FIELD_SHOW_COUNTDOWN), options.showCountdown)
                .putString(lockWidgetKey(widgetId, FIELD_VISIBLE_METERS), options.visibleMeters)
                .apply();
    }

    public static void deleteLockWidgetOptions(Context context, int widgetId) {
        prefs(context).edit()
                .remove(lockWidgetKey(widgetId, FIELD_METRIC_MODE))
                .remove(lockWidgetKey(widgetId, FIELD_SHOW_RESET_CREDITS))
                .remove(lockWidgetKey(widgetId, FIELD_SHOW_RESET_ACTION))
                .remove(lockWidgetKey(widgetId, FIELD_SHOW_COUNTDOWN))
                .remove(lockWidgetKey(widgetId, FIELD_VISIBLE_METERS))
                .apply();
    }

    private static String lockWidgetKey(int widgetId, String field) {
        return "lock_widget_" + widgetId + "_" + field;
    }

    // ---------------------------------------------------------------------------------------
    // OAuth sign-in
    // ---------------------------------------------------------------------------------------

    /** Confirmed remote rejection is independent from transient refresh errors and cached usage. */
    public static boolean isReauthenticationRequired(Context context) {
        return prefs(context).getBoolean(KEY_REAUTHENTICATION_REQUIRED, false);
    }

    static void setReauthenticationRequired(Context context, boolean required) throws Exception {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (required) {
            editor.putBoolean(KEY_REAUTHENTICATION_REQUIRED, true);
        } else {
            editor.remove(KEY_REAUTHENTICATION_REQUIRED);
        }
        if (!editor.commit()) {
            throw OAuthClient.userError(context, R.string.auth_error_status_not_saved);
        }
    }

    public static void setOAuthPending(Context context, boolean pending, String url) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .putBoolean(KEY_OAUTH_PENDING, pending)
                .putString(KEY_OAUTH_URL, url == null ? "" : url);
        if (pending) {
            editor.putLong(KEY_OAUTH_STARTED_AT, System.currentTimeMillis());
        } else {
            editor.remove(KEY_OAUTH_STARTED_AT);
        }
        editor.apply();
    }

    /** Whether a sign-in is in progress; a stale pending flag is cleared and reported false. */
    public static boolean isOAuthPending(Context context) {
        SharedPreferences prefs = prefs(context);
        if (!prefs.getBoolean(KEY_OAUTH_PENDING, false)) {
            return false;
        }
        long startedAt = prefs.getLong(KEY_OAUTH_STARTED_AT, 0L);
        if (startedAt <= 0 || System.currentTimeMillis() - startedAt > OAUTH_STALE_AFTER_MS) {
            setOAuthPending(context, false, "");
            return false;
        }
        return true;
    }

    public static String getOAuthUrl(Context context) {
        return isOAuthPending(context) ? prefs(context).getString(KEY_OAUTH_URL, "") : "";
    }

    // ---------------------------------------------------------------------------------------
    // Onboarding
    // ---------------------------------------------------------------------------------------

    public static boolean isOnboardingComplete(Context context) {
        return prefs(context).getBoolean(KEY_ONBOARDING_COMPLETE, false);
    }

    public static int getOnboardingStep(Context context) {
        return OnboardingFlow.normalizeStep(
                prefs(context).getInt(KEY_ONBOARDING_STEP, OnboardingFlow.STEP_WELCOME));
    }

    public static void setOnboardingStep(Context context, int step) {
        prefs(context).edit()
                .putInt(KEY_ONBOARDING_STEP, OnboardingFlow.normalizeStep(step))
                .apply();
    }

    public static void completeOnboarding(Context context) {
        prefs(context).edit()
                .putBoolean(KEY_ONBOARDING_COMPLETE, true)
                .remove(KEY_ONBOARDING_STEP)
                .apply();
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private static void putBoolean(Context context, String key, boolean value) {
        prefs(context).edit().putBoolean(key, value).apply();
    }

    private static void putOrRemoveIfBlank(Context context, String key, String value) {
        if (isBlank(value)) {
            prefs(context).edit().remove(key).apply();
        } else {
            prefs(context).edit().putString(key, value).apply();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** Trims a non-blank error message and caps it at {@value #MAX_ERROR_LENGTH} characters. */
    private static String clip(String message) {
        String trimmed = message.trim();
        return trimmed.length() > MAX_ERROR_LENGTH
                ? trimmed.substring(0, MAX_ERROR_LENGTH) : trimmed;
    }
}
