package dev.bennett.codexmeter;

import java.math.BigInteger;
import java.text.Collator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** The AI-Usage Codex extraction contract, isolated from App and frozen Wear parsing. */
public final class WidgetUsageParser {
    private static final String[] KEYS = {"primary_window", "primaryWindow", "secondary_window",
            "secondaryWindow", "five_hour", "weekly", "monthly"};
    private static final Pattern FIVE = Pattern.compile("5\\s*h|five|session");
    private static final Pattern MONTH = Pattern.compile("30\\s*d|month");
    private static final Pattern WEEK = Pattern.compile("7\\s*d|week");
    private static final Pattern DECIMAL = Pattern.compile(
            "[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");
    private static final Pattern NUMERIC_EPOCH = Pattern.compile("\\d+(?:\\.\\d+)?");

    private WidgetUsageParser() { }

    public static WidgetUsageSnapshot parse(String json, long now) throws JSONException {
        JSONObject payload = new JSONObject(json);
        JSONObject root = firstObject(payload, "rate_limit", "rateLimit");
        if (root == null) root = payload;
        List<ParsedWindow> windows = new ArrayList<>(collect(root, "codex", "", ""));

        Object additional = nullish(payload, "additional_rate_limits");
        if (additional == null) additional = nullish(root, "additional_rate_limits");
        if (additional instanceof JSONArray) {
            JSONArray array = (JSONArray) additional;
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) continue;
                JSONObject rateLimit = firstObject(item, "rate_limit", "rateLimit");
                if (rateLimit == null) rateLimit = item;
                String name = stringValue(item.opt("limit_name"));
                String feature = stringValue(item.opt("metered_feature"));
                String sourceText = name + " " + feature;
                String featureId = slug(!feature.isEmpty() ? feature : name);
                if (featureId.isEmpty()) featureId = "unknown-" + index;
                String labelPrefix = sourceText.toLowerCase(Locale.ROOT).contains("spark")
                        ? "Codex Spark" : !name.isEmpty() ? name
                        : !feature.isEmpty() ? feature : "Codex 附加限额";
                windows.addAll(collect(rateLimit, "extra:" + featureId, sourceText, labelPrefix));
            }
        }

        for (String key : new String[]{"five_hour", "weekly", "monthly"}) {
            ParsedWindow parsed = parseWindow(payload.opt(key), "direct:" + key, key);
            boolean existing = false;
            for (ParsedWindow window : windows)
                if (ordinary(window.window.id) && window.window.kind.equals(key)) existing = true;
            if (parsed != null && !existing) windows.add(parsed);
        }

        LinkedHashMap<String, ParsedWindow> unique = new LinkedHashMap<>();
        for (ParsedWindow window : windows) unique.putIfAbsent(window.window.id, window);
        windows = new ArrayList<>(unique.values());
        // IDs contain ASCII field names/slugs; English collation matches JS localeCompare,
        // including underscore/camel-case ties which String.compareTo orders differently.
        Collator ids = Collator.getInstance(Locale.ENGLISH);
        windows.sort((left, right) -> {
            int sourceOrder = Boolean.compare(!ordinary(left.window.id), !ordinary(right.window.id));
            if (sourceOrder != 0) return sourceOrder;
            int cadence = Double.compare(sortSeconds(left.seconds), sortSeconds(right.seconds));
            return cadence != 0 ? cadence : ids.compare(left.window.id, right.window.id);
        });
        List<WidgetUsageWindow> result = new ArrayList<>();
        for (ParsedWindow window : windows) result.add(window.window);
        return new WidgetUsageSnapshot(stringValue(payload.opt("plan_type")), now,
                resetCredits(payload), result);
    }

    private static List<ParsedWindow> collect(JSONObject rateLimit, String prefix,
            String hint, String labelPrefix) {
        List<ParsedWindow> windows = new ArrayList<>();
        for (String key : KEYS) {
            ParsedWindow parsed = parseWindow(rateLimit.opt(key), prefix + ":" + key, hint + " " + key);
            if (parsed == null) continue;
            boolean duplicate = false;
            for (ParsedWindow window : windows)
                if (sameWindow(window, parsed)) duplicate = true;
            if (duplicate) continue;
            if (!labelPrefix.isEmpty()) {
                WidgetUsageWindow window = parsed.window;
                parsed = new ParsedWindow(new WidgetUsageWindow(window.id,
                        labelPrefix + " " + window.label, window.kind, window.usedPercent,
                        window.windowSeconds, window.resetAtMillis), parsed.seconds, parsed.reset);
            }
            windows.add(parsed);
        }
        return windows;
    }

    private static ParsedWindow parseWindow(Object value, String id, String hint) {
        if (!(value instanceof JSONObject)) return null;
        JSONObject object = (JSONObject) value;
        if (!truthy(object.opt("reset_at")) && !truthy(object.opt("used_percent"))
                && object.optJSONObject("primary_window") != null)
            object = object.optJSONObject("primary_window");
        Double seconds = number(nullish(object, "limit_window_seconds", "window_seconds", "limit_window"));
        String kind = kindFor(seconds, id + " " + hint);
        Double reset = epoch(nullish(object, "reset_at", "reset_time_ms", "resetAt", "reset_time"));
        Double used = number(nullish(object, "used_percent", "usedPercent"));
        if (used == null) {
            Double remaining = number(nullish(object, "percent_left", "remaining_percent", "remainingPercent"));
            if (remaining != null) used = clamp(100d - remaining);
        } else used = clamp(used);
        if (used == null && reset == null) return null;
        return new ParsedWindow(new WidgetUsageWindow(id, labelFor(kind, seconds), kind, used,
                seconds == null ? 0L : seconds.longValue(), reset == null ? 0L : reset.longValue()), seconds, reset);
    }

    static String kindFor(Double seconds, String hint) {
        String normalized = hint.toLowerCase(Locale.ROOT);
        if (FIVE.matcher(normalized).find()) return "five_hour";
        if (MONTH.matcher(normalized).find()) return "monthly";
        if (WEEK.matcher(normalized).find()) return "weekly";
        if (seconds == null) return "unknown";
        if (seconds <= 6 * 3600) return "five_hour";
        if (seconds >= 25 * 86400) return "monthly";
        if (seconds >= 6 * 86400) return "weekly";
        return "unknown";
    }

    static String labelFor(String kind, Double seconds) {
        if ("five_hour".equals(kind)) return "5 小时";
        if ("weekly".equals(kind)) return "每周";
        if ("monthly".equals(kind)) return "每月";
        return seconds != null && seconds >= 86400 ? Math.round(seconds / 86400d) + " 天" : "限额";
    }

    private static boolean sameWindow(ParsedWindow left, ParsedWindow right) {
        return left.window.kind.equals(right.window.kind) && Objects.equals(left.reset, right.reset)
                && Objects.equals(left.window.usedPercent, right.window.usedPercent);
    }

    private static boolean ordinary(String id) {
        return id.startsWith("codex:") || id.startsWith("direct:");
    }

    private static double sortSeconds(Double seconds) {
        return seconds == null || seconds == 0d ? 1e20 : seconds;
    }

    private static JSONObject firstObject(JSONObject parent, String... keys) {
        for (String key : keys) {
            JSONObject object = parent.optJSONObject(key);
            if (object != null) return object;
        }
        return null;
    }

    private static Object nullish(JSONObject object, String... keys) {
        for (String key : keys) {
            Object value = object.opt(key);
            if (value != null && value != JSONObject.NULL) return value;
        }
        return null;
    }

    private static boolean truthy(Object value) {
        if (value == null || value == JSONObject.NULL || Boolean.FALSE.equals(value)) return false;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0d;
        return !(value instanceof String) || !((String) value).isEmpty();
    }

    private static Double number(Object value) {
        if (value instanceof Number) {
            double result = ((Number) value).doubleValue();
            return Double.isFinite(result) ? result : null;
        }
        if (!(value instanceof String)) return null;
        String text = trim((String) value);
        try {
            double result;
            if (text.matches("0[xX][0-9a-fA-F]+")) result = new BigInteger(text.substring(2), 16).doubleValue();
            else if (text.matches("0[bB][01]+")) result = new BigInteger(text.substring(2), 2).doubleValue();
            else if (text.matches("0[oO][0-7]+")) result = new BigInteger(text.substring(2), 8).doubleValue();
            else if (DECIMAL.matcher(text).matches()) result = Double.parseDouble(text);
            else return null;
            return Double.isFinite(result) ? result : null;
        } catch (NumberFormatException ignored) { return null; }
    }

    private static String trim(String value) {
        return value.replaceAll("^[\\s\\p{Zs}\\uFEFF]+|[\\s\\p{Zs}\\uFEFF]+$", "");
    }

    private static String stringValue(Object value) {
        return value instanceof String ? trim((String) value) : "";
    }

    private static String slug(String value) {
        return trim(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static double clamp(double value) { return Math.max(0d, Math.min(100d, value)); }

    private static Double epoch(Object value) {
        if (value instanceof String && !NUMERIC_EPOCH.matcher((String) value).matches()) {
            String text = (String) value;
            try { return (double) Instant.parse(text).toEpochMilli(); }
            catch (DateTimeParseException ignored) { }
            try { return (double) OffsetDateTime.parse(text).toInstant().toEpochMilli(); }
            catch (DateTimeParseException ignored) { }
            try { return (double) LocalDate.parse(text).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(); }
            catch (DateTimeParseException ignored) { }
            try { return (double) LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
            catch (DateTimeParseException ignored) { return null; }
        }
        Double numeric = number(value);
        if (numeric == null) return null;
        double millis = numeric > 1e11 ? numeric : numeric * 1000d;
        // JavaScript Date TimeClip rejects timestamps beyond 100 million days.
        return Double.isFinite(millis) && Math.abs(millis) <= 8.64e15 ? millis : null;
    }

    private static Integer resetCredits(JSONObject payload) {
        JSONObject container = firstObject(payload, "rate_limit_reset_credits", "rateLimitResetCredits");
        if (container == null) container = payload;
        Double count = number(nullish(container, "available_count", "availableCount"));
        return count == null ? null : (int) Math.min(Integer.MAX_VALUE, Math.max(0d, Math.floor(count)));
    }

    private static final class ParsedWindow {
        final WidgetUsageWindow window;
        final Double seconds;
        final Double reset;
        ParsedWindow(WidgetUsageWindow window, Double seconds, Double reset) {
            this.window = window;
            this.seconds = seconds;
            this.reset = reset;
        }
    }
}
