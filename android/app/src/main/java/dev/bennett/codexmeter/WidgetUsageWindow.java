package dev.bennett.codexmeter;

import org.json.JSONException;
import org.json.JSONObject;

/** A complete reference window, kept separate from the App/Wear usage model. */
public final class WidgetUsageWindow {
    public final String id;
    public final String label;
    public final String kind;
    public final Double usedPercent;
    public final long windowSeconds;
    public final long resetAtMillis;

    public WidgetUsageWindow(String id, String label, String kind, Double usedPercent,
            long windowSeconds, long resetAtMillis) {
        this.id = id == null ? "" : id;
        this.label = label == null ? "" : label;
        this.kind = kind == null ? "unknown" : kind;
        this.usedPercent = usedPercent;
        this.windowSeconds = windowSeconds;
        this.resetAtMillis = resetAtMillis;
    }

    /** Reset-only windows deliberately have no invented percentage. */
    public Double remainingPercent() {
        return usedPercent == null ? null : Math.max(0d, Math.min(100d, 100d - usedPercent));
    }

    public boolean showsResetCountdown() {
        return resetAtMillis > 0L;
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("id", id).put("label", label).put("kind", kind)
                .put("used_percent", usedPercent == null ? JSONObject.NULL : usedPercent)
                .put("window_seconds", windowSeconds).put("reset_at_millis", resetAtMillis);
    }

    static WidgetUsageWindow fromJson(JSONObject object) {
        if (object == null || object.optString("id", "").isEmpty()) return null;
        double used = object.optDouble("used_percent", Double.NaN);
        return new WidgetUsageWindow(object.optString("id", ""), object.optString("label", ""),
                object.optString("kind", "unknown"), Double.isFinite(used) ? used : null,
                object.optLong("window_seconds", 0L), object.optLong("reset_at_millis", 0L));
    }
}
