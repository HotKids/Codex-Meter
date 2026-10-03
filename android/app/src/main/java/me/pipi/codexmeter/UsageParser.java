package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageCredits;
import dev.bennett.codexmeter.UsageLimit;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsageWindow;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Parses the Codex usage endpoint into the {@link UsageSnapshot} model. */
public final class UsageParser {
    private static final long HOUR = 60 * 60;
    private static final long DAY = 24 * HOUR;

    private static final long FIVE_HOURS = 5 * HOUR;
    private static final long FIVE_HOURS_MIN = 3 * HOUR;
    private static final long FIVE_HOURS_MAX = 8 * HOUR;
    private static final long WEEK = 7 * DAY;
    private static final long WEEK_MIN = 5 * DAY;
    private static final long WEEK_MAX = 9 * DAY;
    private static final long MONTH = 30 * DAY;
    // Free-tier accounts report a single ~30-day Codex window; accept 10-45 days so calendar
    // months and drifting billing periods still classify while staying clear of the weekly
    // window's 9-day ceiling.
    private static final long MONTH_MIN = 10 * DAY;
    private static final long MONTH_MAX = 45 * DAY;

    private UsageParser() {
    }

    public static UsageSnapshot parse(String json, long fetchedAtMillis) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject rateLimit = nullableObject(root, "rate_limit");
        boolean allowed = true;
        boolean limitReached = false;
        List<UsageWindow> windows = new ArrayList<>();
        if (rateLimit != null) {
            allowed = rateLimit.optBoolean("allowed", true);
            limitReached = rateLimit.optBoolean("limit_reached", false);
            addIfPresent(windows, primaryWindow(rateLimit));
            addIfPresent(windows, secondaryWindow(rateLimit));
        }

        UsageWindow fiveHour = nearestWindow(windows, FIVE_HOURS, FIVE_HOURS_MIN, FIVE_HOURS_MAX);
        UsageWindow weekly = nearestWindow(windows, WEEK, WEEK_MIN, WEEK_MAX, fiveHour);
        UsageWindow monthly = nearestWindow(windows, MONTH, MONTH_MIN, MONTH_MAX,
                fiveHour, weekly);
        List<UsageLimit> additionalLimits = parseAdditionalLimits(root, rateLimit);
        JSONObject resetCredits = nullableObject(root, "rate_limit_reset_credits");
        UsageCredits usageCredits = UsageCredits.fromJson(nullableObject(root, "credits"));
        return new UsageSnapshot(
                root.optString("plan_type", ""),
                allowed,
                limitReached,
                fiveHour,
                weekly,
                monthly,
                additionalLimits,
                usageCredits,
                resetCredits == null ? -1 : resetCredits.optInt("available_count", -1),
                fetchedAtMillis);
    }

    /**
     * Reads model-specific limits. They normally sit at the response root; some responses nest
     * the array inside {@code rate_limit}, which is accepted as a fallback.
     */
    private static List<UsageLimit> parseAdditionalLimits(JSONObject root,
            JSONObject rateLimit) {
        List<UsageLimit> limits = new ArrayList<>();
        JSONArray entries = root.optJSONArray("additional_rate_limits");
        if (entries == null && rateLimit != null) {
            entries = rateLimit.optJSONArray("additional_rate_limits");
        }
        if (entries == null) {
            return limits;
        }
        for (int index = 0; index < entries.length(); index++) {
            UsageLimit limit = parseAdditionalLimit(entries.optJSONObject(index), index);
            if (limit != null) {
                limits.add(limit);
            }
        }
        return limits;
    }

    /**
     * Parses one additional limit, or returns null when it has no usable window. The array
     * index is appended to the ID so entries that share a name stay distinct.
     */
    private static UsageLimit parseAdditionalLimit(JSONObject entry, int index) {
        if (entry == null) {
            return null;
        }
        JSONObject rateLimit = nullableObject(entry, "rate_limit");
        if (rateLimit == null) {
            rateLimit = entry;
        }
        UsageWindow primary = primaryWindow(rateLimit);
        UsageWindow secondary = secondaryWindow(rateLimit);
        if (primary == null && secondary == null) {
            return null;
        }
        String name = entry.optString("limit_name", "");
        String feature = entry.optString("metered_feature", "");
        String id = firstNonEmpty(entry.optString("limit_id", ""), name, feature, "additional");
        return new UsageLimit(
                id + "-" + index,
                name,
                feature,
                rateLimit.optBoolean("allowed", true),
                rateLimit.optBoolean("limit_reached", false),
                primary,
                secondary);
    }

    private static UsageWindow primaryWindow(JSONObject rateLimit) {
        return UsageWindow.fromJson(nullableObject(rateLimit, "primary_window"));
    }

    private static UsageWindow secondaryWindow(JSONObject rateLimit) {
        return UsageWindow.fromJson(nullableObject(rateLimit, "secondary_window"));
    }

    private static void addIfPresent(List<UsageWindow> windows, UsageWindow window) {
        if (window != null) {
            windows.add(window);
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static JSONObject nullableObject(JSONObject parent, String key) {
        if (parent == null || parent.isNull(key)) {
            return null;
        }
        return parent.optJSONObject(key);
    }

    /**
     * Returns the window whose length is closest to {@code targetSeconds} within the inclusive
     * bounds, skipping windows already claimed by another cadence. Ties keep the earliest window.
     */
    private static UsageWindow nearestWindow(List<UsageWindow> windows, long targetSeconds,
            long minimumSeconds, long maximumSeconds, UsageWindow... excluded) {
        UsageWindow best = null;
        long bestDistance = Long.MAX_VALUE;
        for (UsageWindow candidate : windows) {
            if (isExcluded(candidate, excluded)
                    || candidate.windowSeconds < minimumSeconds
                    || candidate.windowSeconds > maximumSeconds) {
                continue;
            }
            long distance = Math.abs(candidate.windowSeconds - targetSeconds);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean isExcluded(UsageWindow candidate, UsageWindow[] excluded) {
        for (UsageWindow window : excluded) {
            if (candidate == window) {
                return true;
            }
        }
        return false;
    }
}
