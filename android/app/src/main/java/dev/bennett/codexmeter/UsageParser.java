package dev.bennett.codexmeter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public final class UsageParser {
    private static final long FIVE_HOURS = 18000;
    private static final long WEEK = 604800;
    private static final long MONTH = 2592000;
    // Free-tier accounts report a single ~30-day Codex window; accept 10-45 days so calendar
    // months and drifting billing periods still classify while staying clear of the weekly
    // window's 9-day ceiling.
    private static final long MONTH_MIN = 864000;
    private static final long MONTH_MAX = 3888000;

    private UsageParser() {
    }

    public static UsageSnapshot parse(String str, long j) throws JSONException {
        boolean z;
        boolean z2;
        UsageWindow usageWindow;
        JSONObject jSONObject = new JSONObject(str);
        String strOptString = jSONObject.optString("plan_type", "");
        JSONObject jSONObjectNullableObject = firstObject(jSONObject, "rate_limit", "rateLimit");
        if (jSONObjectNullableObject == null) jSONObjectNullableObject = jSONObject;
        ArrayList<UsageWindow> arrayList = new ArrayList<>();
        ArrayList<UsageLimit> additionalLimits = new ArrayList<>();
        UsageWindow usageWindowFromJson = null;
        if (jSONObjectNullableObject == null) {
            z = true;
            z2 = false;
            usageWindow = null;
        } else {
            boolean zOptBoolean = jSONObjectNullableObject.optBoolean("allowed", true);
            boolean zOptBoolean2 = jSONObjectNullableObject.optBoolean("limit_reached", false);
            UsageWindow usageWindowFromJson2 = UsageWindow.fromJson(firstObject(jSONObjectNullableObject, "primary_window", "primaryWindow"));
            usageWindowFromJson = UsageWindow.fromJson(firstObject(jSONObjectNullableObject, "secondary_window", "secondaryWindow"));
            if (usageWindowFromJson2 != null) {
                arrayList.add(usageWindowFromJson2);
            }
            if (usageWindowFromJson != null) {
                arrayList.add(usageWindowFromJson);
            }
            z = zOptBoolean;
            z2 = zOptBoolean2;
            usageWindow = usageWindowFromJson2;
        }
        // AI-Usage also reads named windows in rate_limit and directly on the response.
        // Keep primary/secondary values first so duplicate aliases cannot override them.
        addNamedWindows(arrayList, jSONObjectNullableObject);
        if (jSONObjectNullableObject != jSONObject) addNamedWindows(arrayList, jSONObject);
        JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("additional_rate_limits");
        if (jSONArrayOptJSONArray == null) {
            jSONArrayOptJSONArray = jSONObjectNullableObject.optJSONArray("additional_rate_limits");
        }
        if (jSONArrayOptJSONArray != null) {
            for (int i = 0; i < jSONArrayOptJSONArray.length(); i++) {
                JSONObject jSONObjectOptJSONObject = jSONArrayOptJSONArray.optJSONObject(i);
                if (jSONObjectOptJSONObject != null) {
                    JSONObject jSONObjectNullableObject2 = firstObject(jSONObjectOptJSONObject, "rate_limit", "rateLimit");
                    if (jSONObjectNullableObject2 == null) {
                        jSONObjectNullableObject2 = jSONObjectOptJSONObject;
                    }
                    UsageWindow usageWindowFromJson3 = UsageWindow.fromJson(firstObject(jSONObjectNullableObject2, "primary_window", "primaryWindow"));
                    UsageWindow usageWindowFromJson4 = UsageWindow.fromJson(firstObject(jSONObjectNullableObject2, "secondary_window", "secondaryWindow"));
                    if (usageWindowFromJson3 != null || usageWindowFromJson4 != null) {
                        String name = jSONObjectOptJSONObject.optString("limit_name", "");
                        String feature = jSONObjectOptJSONObject.optString("metered_feature", "");
                        String id = firstNonEmpty(
                                jSONObjectOptJSONObject.optString("limit_id", ""),
                                feature, name, "additional");
                        additionalLimits.add(new UsageLimit(
                                limitIdentityPart(id),
                                name,
                                feature,
                                jSONObjectNullableObject2.optBoolean("allowed", true),
                                jSONObjectNullableObject2.optBoolean("limit_reached", false),
                                usageWindowFromJson3,
                                usageWindowFromJson4));
                    }
                }
            }
        }
        additionalLimits = distinguishAdditionalLimits(additionalLimits);
        UsageWindow usageWindowNearest = nearest(arrayList, FIVE_HOURS, 10800L, 28800L);
        UsageWindow usageWindowNearestExcluding = nearestExcluding(arrayList, WEEK, 432000L, 777600L, usageWindowNearest);
        UsageWindow usageWindowMonthly = nearestExcluding(arrayList, MONTH, MONTH_MIN, MONTH_MAX,
                usageWindowNearest, usageWindowNearestExcluding);
        JSONObject jSONObjectNullableObject3 = nullableObject(jSONObject, "rate_limit_reset_credits");
        UsageCredits usageCredits = UsageCredits.fromJson(nullableObject(jSONObject, "credits"));
        return new UsageSnapshot(
                strOptString,
                z,
                z2,
                usageWindowNearest,
                usageWindowNearestExcluding,
                usageWindowMonthly,
                additionalLimits,
                usageCredits,
                jSONObjectNullableObject3 == null
                        ? -1 : jSONObjectNullableObject3.optInt("available_count", -1),
                j);
    }

    private static JSONObject firstObject(JSONObject parent, String... keys) {
        for (String key : keys) {
            JSONObject value = nullableObject(parent, key);
            if (value != null) return value;
        }
        return null;
    }

    private static void addNamedWindows(List<UsageWindow> windows, JSONObject parent) {
        for (String key : new String[]{"five_hour", "weekly", "monthly"}) {
            UsageWindow window = UsageWindow.fromJson(nullableObject(parent, key));
            if (window != null) windows.add(window);
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

    /** Match WidgetMeters' case-insensitive keys without collapsing commas into underscores. */
    static String limitIdentityPart(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace("%", "%25").replace(",", "%2c").replace("~", "%7e");
    }

    /**
     * An API array position is not an identity. Prefer its ID/feature/name, and only qualify
     * collisions using static metadata and cadence, never the changing usage or reset values.
     * Indistinguishable duplicate records deliberately share a key.
     */
    private static ArrayList<UsageLimit> distinguishAdditionalLimits(List<UsageLimit> limits) {
        Map<String, Integer> counts = new HashMap<>();
        for (UsageLimit limit : limits) counts.merge(limit.id, 1, Integer::sum);
        ArrayList<UsageLimit> result = new ArrayList<>();
        for (UsageLimit limit : limits) {
            String id = limit.id;
            if (counts.get(id) > 1) {
                id += "~" + limitIdentityPart(limit.meteredFeature) + "~"
                        + limitIdentityPart(limit.name) + "~"
                        + (limit.primary == null ? 0 : limit.primary.windowSeconds) + "~"
                        + (limit.secondary == null ? 0 : limit.secondary.windowSeconds);
            }
            result.add(new UsageLimit(id, limit.name, limit.meteredFeature, limit.allowed,
                    limit.limitReached, limit.primary, limit.secondary));
        }
        return result;
    }

    private static JSONObject nullableObject(JSONObject jSONObject, String str) {
        if (jSONObject == null || jSONObject.isNull(str)) {
            return null;
        }
        return jSONObject.optJSONObject(str);
    }

    private static UsageWindow nearest(List<UsageWindow> list, long j, long j2, long j3) {
        UsageWindow usageWindow;
        long j4;
        UsageWindow usageWindow2 = null;
        long j5 = Long.MAX_VALUE;
        for (UsageWindow usageWindow3 : list) {
            if (usageWindow3.windowSeconds < j2 || usageWindow3.windowSeconds > j3) {
                long j6 = j5;
                usageWindow = usageWindow2;
                j4 = j6;
            } else {
                long jAbs = Math.abs(usageWindow3.windowSeconds - j);
                if (jAbs < j5) {
                    usageWindow = usageWindow3;
                    j4 = jAbs;
                } else {
                    long j7 = j5;
                    usageWindow = usageWindow2;
                    j4 = j7;
                }
            }
            usageWindow2 = usageWindow;
            j5 = j4;
        }
        return usageWindow2;
    }

    private static UsageWindow nearestExcluding(List<UsageWindow> list, long target,
            long minimumSeconds, long maximumSeconds, UsageWindow... excluded) {
        UsageWindow best = null;
        long bestDistance = Long.MAX_VALUE;
        for (UsageWindow candidate : list) {
            if (isExcluded(candidate, excluded)
                    || candidate.windowSeconds < minimumSeconds
                    || candidate.windowSeconds > maximumSeconds) {
                continue;
            }
            long distance = Math.abs(candidate.windowSeconds - target);
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
