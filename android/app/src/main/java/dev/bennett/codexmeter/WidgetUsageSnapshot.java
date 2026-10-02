package dev.bennett.codexmeter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Widget-only cache DTO: every reference window survives parsing and persistence. */
public final class WidgetUsageSnapshot {
    public final String planType;
    public final long fetchedAtMillis;
    public final Integer resetCreditsAvailable;
    public final List<WidgetUsageWindow> windows;

    public WidgetUsageSnapshot(String planType, long fetchedAtMillis,
            Integer resetCreditsAvailable, List<WidgetUsageWindow> windows) {
        this.planType = planType == null ? "" : planType;
        this.fetchedAtMillis = fetchedAtMillis;
        this.resetCreditsAvailable = resetCreditsAvailable;
        List<WidgetUsageWindow> copy = new ArrayList<>();
        if (windows != null) for (WidgetUsageWindow window : windows)
            if (window != null) copy.add(window);
        this.windows = Collections.unmodifiableList(copy);
    }

    /** Preserve the existing meter keys when only an older cache is available. */
    public static WidgetUsageSnapshot fromLegacy(UsageSnapshot snapshot) {
        if (snapshot == null) return null;
        List<WidgetUsageWindow> windows = new ArrayList<>();
        addLegacy(windows, WidgetMeters.FIVE_HOUR, "five_hour", snapshot.fiveHour,
                snapshot.fetchedAtMillis, "");
        addLegacy(windows, WidgetMeters.WEEKLY,
                snapshot.longWindowIsMonthly() ? "monthly" : "weekly", snapshot.longWindow(),
                snapshot.fetchedAtMillis, "");
        if (snapshot.weekly != null && snapshot.monthly != null)
            addLegacy(windows, "monthly", "monthly", snapshot.monthly, snapshot.fetchedAtMillis, "");
        for (UsageLimit limit : snapshot.additionalLimits) {
            if (limit == null) continue;
            String source = limit.name + " " + limit.meteredFeature;
            String labelPrefix = source.toLowerCase(Locale.ROOT).contains("spark") ? "Codex Spark"
                    : !limit.name.isEmpty() ? limit.name
                    : !limit.meteredFeature.isEmpty() ? limit.meteredFeature : "Codex 附加限额";
            addLegacy(windows, WidgetMeters.limitPrimaryKey(limit), null, limit.primary,
                    snapshot.fetchedAtMillis, labelPrefix);
            addLegacy(windows, WidgetMeters.limitSecondaryKey(limit), null, limit.secondary,
                    snapshot.fetchedAtMillis, labelPrefix);
        }
        return new WidgetUsageSnapshot(snapshot.planType, snapshot.fetchedAtMillis,
                snapshot.resetCreditsAvailable < 0 ? null : snapshot.resetCreditsAvailable, windows);
    }

    private static void addLegacy(List<WidgetUsageWindow> windows, String id, String kind,
            UsageWindow window, long observedAt, String labelPrefix) {
        if (window == null) return;
        String effectiveKind = kind == null
                ? WidgetUsageParser.kindFor((double) window.windowSeconds, labelPrefix) : kind;
        String label = WidgetUsageParser.labelFor(effectiveKind, (double) window.windowSeconds);
        if (!labelPrefix.isEmpty()) label = labelPrefix + " " + label;
        windows.add(new WidgetUsageWindow(id, label, effectiveKind, (double) window.usedPercent,
                window.windowSeconds, window.effectiveResetAtMillis(observedAt)));
    }

    public JSONObject toJson() throws JSONException {
        JSONArray array = new JSONArray();
        for (WidgetUsageWindow window : windows) array.put(window.toJson());
        return new JSONObject().put("plan_type", planType).put("fetched_at", fetchedAtMillis)
                .put("reset_credits_available", resetCreditsAvailable == null
                        ? JSONObject.NULL : resetCreditsAvailable)
                .put("windows", array);
    }

    /** A legacy fixed-window cache is not a complete widget cache. */
    public static WidgetUsageSnapshot fromJson(JSONObject object) {
        if (object == null) return null;
        JSONArray array = object.optJSONArray("windows");
        if (array == null) return null;
        List<WidgetUsageWindow> windows = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            WidgetUsageWindow window = WidgetUsageWindow.fromJson(array.optJSONObject(index));
            if (window != null) windows.add(window);
        }
        Integer credits = object.isNull("reset_credits_available") ? null
                : object.has("reset_credits_available")
                    ? object.optInt("reset_credits_available", -1) : null;
        if (credits != null && credits < 0) credits = null;
        return new WidgetUsageSnapshot(object.optString("plan_type", ""),
                object.optLong("fetched_at", 0L), credits, windows);
    }
}
