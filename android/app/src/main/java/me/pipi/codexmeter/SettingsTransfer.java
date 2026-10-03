package me.pipi.codexmeter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Portable Codex Meter transfer document. Pure JSON — no Android APIs — so the
 * format can be round-tripped in {@link ParserSelfTest}.
 *
 * <p>Authentication sections contain usable ChatGPT OAuth tokens and must never
 * be shared. The document always embeds {@link #SECURITY_WARNING} when auth is
 * present so a casual file open still surfaces the risk.
 */
public final class SettingsTransfer {
    public static final String FORMAT = "codex_meter_transfer";
    public static final int VERSION = 1;

    public static final String SECTION_APP_SETTINGS = "app_settings";
    public static final String SECTION_NOTIFICATIONS = "notifications";
    public static final String SECTION_NOW_BAR = "now_bar";
    public static final String SECTION_AUTHENTICATION = "authentication";

    /**
     * Always written into exported files in English. Dialogs show the localized
     * {@code settings_transfer_security_warning} resource, whose English text run-tests.sh
     * keeps identical to this constant.
     */
    public static final String SECURITY_WARNING =
            "IMPORTANT: This file contains ChatGPT authentication tokens. "
                    + "Anyone with this file can access your ChatGPT account. "
                    + "Do not share it, upload it, or send it to anyone. "
                    + "Keep it only on devices you trust.";

    public static final String[] ALL_SECTIONS = {
            SECTION_APP_SETTINGS,
            SECTION_NOTIFICATIONS,
            SECTION_NOW_BAR,
            SECTION_AUTHENTICATION
    };

    public static final String[] SETTINGS_SECTIONS = {
            SECTION_APP_SETTINGS,
            SECTION_NOTIFICATIONS,
            SECTION_NOW_BAR
    };

    /** Notification-section key holding reminder lead times; validated before import. */
    static final String KEY_RESET_CREDIT_EXPIRY_LEAD_TIMES = "reset_credit_expiry_lead_times";

    private static final String KEY_FORMAT = "format";
    private static final String KEY_VERSION = "version";
    private static final String KEY_EXPORTED_AT = "exported_at";
    private static final String KEY_SECTIONS = "sections";
    private static final String KEY_CONTAINS_AUTHENTICATION = "contains_authentication";
    private static final String KEY_SECURITY_WARNING = "security_warning";

    /**
     * Why a transfer file or one of its values was rejected. Stable across languages so the
     * Android layer can show a localized message for each {@link TransferException}.
     */
    public enum Problem {
        EMPTY_FILE,
        NOT_TRANSFER_FILE,
        UNSUPPORTED_VERSION,
        NO_SECTIONS,
        LEAD_TIMES_NOT_ARRAY,
        LEAD_TIMES_INVALID_ENTRY,
        LEAD_TIMES_NULL_ENTRY,
        LEAD_TIMES_NON_INTEGER_ENTRY,
        LEAD_TIMES_EMPTY_ENTRY,
        LEAD_TIMES_NON_NUMERIC_ENTRY,
        LEAD_TIMES_OUT_OF_RANGE_ENTRY
    }

    /**
     * A rejected transfer file. The message stays English; {@link #problem} and
     * {@link #argument} (the unsupported version or the offending JSON key) identify it.
     */
    public static final class TransferException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        public final Problem problem;
        public final String argument;

        TransferException(Problem problem, String argument, String message) {
            super(message);
            this.problem = problem;
            this.argument = argument == null ? "" : argument;
        }
    }

    private SettingsTransfer() {
    }

    public static boolean isKnownSection(String section) {
        if (section == null || section.isEmpty()) {
            return false;
        }
        for (String known : ALL_SECTIONS) {
            if (known.equals(section)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAuthenticationSection(String section) {
        return SECTION_AUTHENTICATION.equals(section);
    }

    public static Document create(long exportedAtMillis, JSONObject appSettings,
            JSONObject notifications, JSONObject nowBar, JSONObject authentication)
            throws JSONException {
        Document document = new Document();
        document.exportedAtMillis = Math.max(0L, exportedAtMillis);
        document.appSettings = copyObject(appSettings);
        document.notifications = copyObject(notifications);
        document.nowBar = copyObject(nowBar);
        document.authentication = copyObject(authentication);
        return document;
    }

    public static Document parse(String json) throws Exception {
        if (json == null || json.trim().isEmpty()) {
            throw new TransferException(Problem.EMPTY_FILE, "", "Transfer file is empty.");
        }
        return parse(new JSONObject(json));
    }

    public static Document parse(JSONObject json) throws Exception {
        if (json == null) {
            throw new TransferException(Problem.EMPTY_FILE, "", "Transfer file is empty.");
        }
        String format = json.optString(KEY_FORMAT, "");
        if (!FORMAT.equals(format)) {
            throw new TransferException(Problem.NOT_TRANSFER_FILE, "",
                    "Not a Codex Meter transfer file.");
        }
        int version = json.optInt(KEY_VERSION, 0);
        if (version < 1 || version > VERSION) {
            throw new TransferException(Problem.UNSUPPORTED_VERSION, String.valueOf(version),
                    "Unsupported transfer file version: " + version);
        }
        Document document = new Document();
        document.exportedAtMillis = Math.max(0L, json.optLong(KEY_EXPORTED_AT, 0L));
        JSONObject sections = json.optJSONObject(KEY_SECTIONS);
        if (sections == null) {
            sections = new JSONObject();
        }
        document.appSettings = copyObject(sections.optJSONObject(SECTION_APP_SETTINGS));
        document.notifications = copyObject(sections.optJSONObject(SECTION_NOTIFICATIONS));
        document.nowBar = copyObject(sections.optJSONObject(SECTION_NOW_BAR));
        document.authentication = copyObject(sections.optJSONObject(SECTION_AUTHENTICATION));
        if (!document.hasAnySection()) {
            throw new TransferException(Problem.NO_SECTIONS, "",
                    "Transfer file does not contain any sections.");
        }
        return document;
    }

    public static JSONObject widgetOptionsToJson(WidgetOptions options) throws JSONException {
        WidgetOptions safe = options == null ? WidgetOptions.defaults() : options;
        JSONObject json = new JSONObject();
        json.put("style", safe.layout);
        json.put("density", safe.density);
        json.put("surface_style", safe.surfaceStyle);
        json.put("graphic_scale", safe.graphicScale);
        json.put("theme", safe.theme);
        json.put("accent", safe.accent);
        json.put("opacity", safe.opacity);
        json.put("reset_mode", safe.resetMode);
        json.put("display_mode", safe.displayMode);
        json.put("metric_mode", safe.metricMode);
        json.put("visible_meters", safe.effectiveVisibleMeters());
        json.put("show_title", safe.showTitle);
        json.put("show_plan", safe.showPlan);
        json.put("show_updated", safe.showUpdated);
        json.put("show_refresh", safe.showRefresh);
        json.put("show_reset_credits", safe.showResetCredits);
        json.put("show_reset_action", safe.showResetAction);
        json.put("show_percent_symbol", safe.showPercentSymbol);
        json.put("card_style", safe.cardStyle);
        return json;
    }

    public static WidgetOptions widgetOptionsFromJson(JSONObject json) {
        return widgetOptionsFromJson(json, WidgetOptions.defaults());
    }

    /**
     * Parses widget options, filling only missing keys from {@code base} so partial
     * transfer objects cannot silently reset device defaults to product defaults.
     */
    public static WidgetOptions widgetOptionsFromJson(JSONObject json, WidgetOptions base) {
        WidgetOptions fallback = base == null ? WidgetOptions.defaults() : base;
        if (json == null) {
            return fallback;
        }
        WidgetOptions options = new WidgetOptions(
                stringOr(json, "style", fallback.layout),
                stringOr(json, "density", fallback.density),
                stringOr(json, "surface_style", fallback.surfaceStyle),
                stringOr(json, "graphic_scale", fallback.graphicScale),
                stringOr(json, "theme", fallback.theme),
                stringOr(json, "accent", fallback.accent),
                intOr(json, "opacity", fallback.opacity),
                stringOr(json, "reset_mode", fallback.resetMode),
                stringOr(json, "display_mode", fallback.displayMode),
                stringOr(json, "metric_mode", fallback.metricMode),
                booleanOr(json, "show_title", fallback.showTitle),
                booleanOr(json, "show_plan", fallback.showPlan),
                booleanOr(json, "show_updated", fallback.showUpdated),
                booleanOr(json, "show_refresh", fallback.showRefresh),
                booleanOr(json, "show_reset_credits", fallback.showResetCredits),
                booleanOr(json, "show_reset_action", fallback.showResetAction));
        WidgetOptions restored = options.withPercentSymbol(
                booleanOr(json, "show_percent_symbol", fallback.showPercentSymbol))
                .withVisibleMeters(stringOr(json, "visible_meters", fallback.visibleMeters))
                .withCardStyle(stringOr(json, "card_style", fallback.cardStyle));
        return restored.withVisibleMeters(restored.effectiveVisibleMeters());
    }

    public static JSONArray leadTimesToJson(List<Long> leadTimes) {
        JSONArray array = new JSONArray();
        if (leadTimes != null) {
            for (Long leadTime : leadTimes) {
                if (leadTime != null && leadTime > 0L) {
                    array.put(leadTime.longValue());
                }
            }
        }
        return array;
    }

    public static List<Long> leadTimesFromJson(JSONArray array) {
        if (array == null) {
            throw leadTimesProblem(Problem.LEAD_TIMES_NOT_ARRAY,
                    "reset_credit_expiry_lead_times must be a JSON array.");
        }
        List<Long> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            values.add(parseLeadTimeEntry(array, i));
        }
        Collections.sort(values);
        return values;
    }

    /** Accepts integral numbers and numeric strings within the reminder lead-time range. */
    private static long parseLeadTimeEntry(JSONArray array, int index) {
        Object raw;
        try {
            raw = array.get(index);
        } catch (JSONException exception) {
            throw leadTimesProblem(Problem.LEAD_TIMES_INVALID_ENTRY,
                    "reset_credit_expiry_lead_times contains an invalid entry.");
        }
        if (raw == null || raw == JSONObject.NULL) {
            throw leadTimesProblem(Problem.LEAD_TIMES_NULL_ENTRY,
                    "reset_credit_expiry_lead_times contains a null entry.");
        }
        long value;
        if (raw instanceof Number) {
            double asDouble = ((Number) raw).doubleValue();
            if (Double.isNaN(asDouble) || Double.isInfinite(asDouble)
                    || asDouble != Math.rint(asDouble)) {
                throw leadTimesProblem(Problem.LEAD_TIMES_NON_INTEGER_ENTRY,
                        "reset_credit_expiry_lead_times contains a non-integer entry.");
            }
            value = ((Number) raw).longValue();
        } else if (raw instanceof String) {
            String text = ((String) raw).trim();
            if (text.isEmpty()) {
                throw leadTimesProblem(Problem.LEAD_TIMES_EMPTY_ENTRY,
                        "reset_credit_expiry_lead_times contains an empty entry.");
            }
            try {
                value = Long.parseLong(text);
            } catch (NumberFormatException exception) {
                throw leadTimesProblem(Problem.LEAD_TIMES_NON_NUMERIC_ENTRY,
                        "reset_credit_expiry_lead_times contains a non-numeric entry.");
            }
        } else {
            throw leadTimesProblem(Problem.LEAD_TIMES_NON_NUMERIC_ENTRY,
                    "reset_credit_expiry_lead_times contains a non-numeric entry.");
        }
        if (!ResetCreditExpiryReminder.isValidLeadTime(value)) {
            throw leadTimesProblem(Problem.LEAD_TIMES_OUT_OF_RANGE_ENTRY,
                    "reset_credit_expiry_lead_times contains an out-of-range entry.");
        }
        return value;
    }

    private static TransferException leadTimesProblem(Problem problem, String message) {
        return new TransferException(problem, KEY_RESET_CREDIT_EXPIRY_LEAD_TIMES, message);
    }

    /** Requires a JSON array when the lead-times key is present; rejects wrong types. */
    public static List<Long> requireLeadTimes(JSONObject json, String key) throws JSONException {
        if (json == null || !json.has(key) || json.isNull(key)) {
            throw new TransferException(Problem.LEAD_TIMES_NOT_ARRAY, key,
                    key + " must be a JSON array.");
        }
        Object raw = json.get(key);
        if (!(raw instanceof JSONArray)) {
            throw new TransferException(Problem.LEAD_TIMES_NOT_ARRAY, key,
                    key + " must be a JSON array.");
        }
        return leadTimesFromJson((JSONArray) raw);
    }

    private static String stringOr(JSONObject json, String key, String fallback) {
        return json.has(key) ? json.optString(key, fallback) : fallback;
    }

    private static int intOr(JSONObject json, String key, int fallback) {
        return json.has(key) ? json.optInt(key, fallback) : fallback;
    }

    private static boolean booleanOr(JSONObject json, String key, boolean fallback) {
        return json.has(key) ? json.optBoolean(key, fallback) : fallback;
    }

    /**
     * English title of a section id. The app shows the localized
     * {@code settings_transfer_section_*} resources keyed by the same {@code SECTION_*} ids;
     * this copy serves the self-tests and unknown ids.
     */
    public static String sectionTitle(String section) {
        if (SECTION_APP_SETTINGS.equals(section)) {
            return "App settings";
        }
        if (SECTION_NOTIFICATIONS.equals(section)) {
            return "Usage alerts";
        }
        if (SECTION_NOW_BAR.equals(section)) {
            return "Now Bar";
        }
        if (SECTION_AUTHENTICATION.equals(section)) {
            return "Authentication";
        }
        return section == null ? "" : section;
    }

    /** English summary of a section id; see {@link #sectionTitle(String)}. */
    public static String sectionSummary(String section) {
        if (SECTION_APP_SETTINGS.equals(section)) {
            return "Appearance, Home visibility, refresh, updates, and default widget settings";
        }
        if (SECTION_NOTIFICATIONS.equals(section)) {
            return "Low-usage alerts and reset-credit reminders";
        }
        if (SECTION_NOW_BAR.equals(section)) {
            return "Display mode, percentage mode, and auto-start";
        }
        if (SECTION_AUTHENTICATION.equals(section)) {
            return "ChatGPT sign-in tokens (sensitive)";
        }
        return "";
    }

    private static JSONObject copyObject(JSONObject json) throws JSONException {
        if (json == null) {
            return null;
        }
        return new JSONObject(json.toString());
    }

    public static final class Document {
        public long exportedAtMillis;
        public JSONObject appSettings;
        public JSONObject notifications;
        public JSONObject nowBar;
        public JSONObject authentication;

        public boolean hasAppSettings() {
            return appSettings != null;
        }

        public boolean hasNotifications() {
            return notifications != null;
        }

        public boolean hasNowBar() {
            return nowBar != null;
        }

        public boolean hasAuthentication() {
            return authentication != null;
        }

        public boolean hasAnySection() {
            return hasAppSettings() || hasNotifications() || hasNowBar() || hasAuthentication();
        }

        public boolean containsSection(String section) {
            if (SECTION_APP_SETTINGS.equals(section)) {
                return hasAppSettings();
            }
            if (SECTION_NOTIFICATIONS.equals(section)) {
                return hasNotifications();
            }
            if (SECTION_NOW_BAR.equals(section)) {
                return hasNowBar();
            }
            if (SECTION_AUTHENTICATION.equals(section)) {
                return hasAuthentication();
            }
            return false;
        }

        public List<String> presentSections() {
            List<String> sections = new ArrayList<>();
            for (String section : ALL_SECTIONS) {
                if (containsSection(section)) {
                    sections.add(section);
                }
            }
            return sections;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject root = new JSONObject();
            root.put(KEY_FORMAT, FORMAT);
            root.put(KEY_VERSION, VERSION);
            root.put(KEY_EXPORTED_AT, exportedAtMillis);
            JSONObject sections = new JSONObject();
            if (appSettings != null) {
                sections.put(SECTION_APP_SETTINGS, appSettings);
            }
            if (notifications != null) {
                sections.put(SECTION_NOTIFICATIONS, notifications);
            }
            if (nowBar != null) {
                sections.put(SECTION_NOW_BAR, nowBar);
            }
            if (authentication != null) {
                sections.put(SECTION_AUTHENTICATION, authentication);
            }
            root.put(KEY_SECTIONS, sections);
            root.put(KEY_CONTAINS_AUTHENTICATION, authentication != null);
            if (authentication != null) {
                root.put(KEY_SECURITY_WARNING, SECURITY_WARNING);
            }
            return root;
        }

        public String toJsonString() throws JSONException {
            return toJson().toString(2);
        }

        public Document selecting(boolean appSettingsSelected, boolean notificationsSelected,
                boolean nowBarSelected, boolean authenticationSelected) throws JSONException {
            return create(
                    exportedAtMillis,
                    appSettingsSelected ? appSettings : null,
                    notificationsSelected ? notifications : null,
                    nowBarSelected ? nowBar : null,
                    authenticationSelected ? authentication : null);
        }
    }
}
