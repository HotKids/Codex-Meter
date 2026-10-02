package dev.bennett.codexmeter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/** Phone-only card selection. Stable keys are independent of translated labels and Wear. */
public final class QuotaCardOptions {
    public static final String TREND = "usage_trend";
    public static final String BALANCE = "usage_credits";
    public static final int MAX_CARDS = 4;
    private QuotaCardOptions() { }

    public static List<String> available() { return WidgetMeters.availableKeys(null); }

    /** The upstream phone catalog always offers its four built-ins, then actual extra windows. */
    public static List<String> available(UsageSnapshot snapshot) {
        LinkedHashSet<String> keys = new LinkedHashSet<>(available());
        if (snapshot != null) {
            if (snapshot.weekly != null && snapshot.monthly != null) keys.add("monthly");
            for (UsageLimit limit : snapshot.additionalLimits) {
                if (limit.primary != null) keys.add(WidgetMeters.limitPrimaryKey(limit));
                if (limit.secondary != null) keys.add(WidgetMeters.limitSecondaryKey(limit));
            }
        }
        return new ArrayList<>(keys);
    }

    public static List<String> defaults() {
        return new ArrayList<>(Arrays.asList(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY));
    }

    /** Keep saved model-window identifiers even when an API response temporarily omits them. */
    public static List<String> resolve(String csv) {
        List<String> result = new ArrayList<>();
        for (String key : WidgetMeters.parse(csv)) {
            if (isWindow(key) && !result.contains(key)) result.add(key);
        }
        return result.isEmpty() ? defaults() : result;
    }

    static List<String> effective(String csv, UsageSnapshot snapshot) {
        return resolve(csv);
    }

    /** Built-ins remain configurable before a response; known ordinary aliases share their row. */
    static List<String> availableWindows(WidgetUsageSnapshot snapshot) {
        LinkedHashSet<String> keys = new LinkedHashSet<>(available());
        if (snapshot != null) for (WidgetUsageWindow window : snapshot.windows) {
            // The built-in long-window row already offers a monthly-only account's quota.
            if (isOrdinaryWindow(window.id) && "monthly".equals(window.kind)
                    && ordinaryWindow(snapshot, "weekly") == null) continue;
            keys.add(canonicalKey(window.id, snapshot));
        }
        return new ArrayList<>(keys);
    }

    static List<String> availableWindows(WidgetUsageSnapshot snapshot, String savedCsv, UsageSnapshot legacy) {
        LinkedHashSet<String> keys = new LinkedHashSet<>(availableWindows(snapshot));
        List<String> selected = effectiveWindows(savedCsv, snapshot, legacy);
        // An explicit monthly choice keeps its identity if weekly data temporarily vanishes.
        // Reuse that selected row instead of offering the same monthly quota twice.
        if (!selected.contains(WidgetMeters.WEEKLY)) {
            WidgetUsageWindow longer = widgetWindow(WidgetMeters.WEEKLY, snapshot);
            if (longer != null && "monthly".equals(longer.kind)) {
                for (String key : selected) if (widgetWindow(key, snapshot) == longer) {
                    keys.remove(WidgetMeters.WEEKLY);
                    break;
                }
            }
        }
        keys.addAll(selected);
        return new ArrayList<>(keys);
    }

    static WidgetUsageWindow widgetWindow(String key, WidgetUsageSnapshot snapshot) {
        if (snapshot != null) for (WidgetUsageWindow window : snapshot.windows)
            if (window.id.equals(key)) return window;
        if (WidgetMeters.FIVE_HOUR.equals(key)) return ordinaryWindow(snapshot, "five_hour");
        if (WidgetMeters.WEEKLY.equals(key)) {
            WidgetUsageWindow weekly = ordinaryWindow(snapshot, "weekly");
            return weekly != null ? weekly : ordinaryWindow(snapshot, "monthly");
        }
        return null;
    }

    static List<String> effectiveWindows(String csv, WidgetUsageSnapshot snapshot,
            UsageSnapshot legacy) {
        // Missing selected windows occupy a dash slot; the renderer alone caps host capacity.
        return resolve(migrateToWindowIds(csv, snapshot, legacy));
    }

    /** Upgrade selections only when their original window can be identified; retain absent IDs. */
    static String migrateToWindowIds(String csv, WidgetUsageSnapshot snapshot, UsageSnapshot legacy) {
        List<String> result = new ArrayList<>();
        for (String key : WidgetMeters.parse(csv)) {
            String replacement = canonicalKey(key, snapshot);
            if (widgetWindow(key, snapshot) == null && snapshot != null) {
                if ("monthly".equals(key)) {
                    WidgetUsageWindow monthly = ordinaryWindow(snapshot, "monthly");
                    if (monthly != null) replacement = canonicalKey(monthly.id, snapshot);
                } else if (WidgetMeters.isLimitKey(key)) {
                    UsageLimit limit = WidgetMeters.findLimit(key, legacy);
                    if (limit != null) {
                        String feature = limit.meteredFeature.isEmpty() ? limit.name : limit.meteredFeature;
                        String slug = feature.trim().toLowerCase(java.util.Locale.ROOT)
                                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
                        String prefix = "extra:" + slug + ":";
                        String field = WidgetMeters.isLimitPrimary(key) ? "primary" : "secondary";
                        for (WidgetUsageWindow window : snapshot.windows) {
                            if (window.id.equals(prefix + field + "_window")
                                    || window.id.equals(prefix + field + "Window")) {
                                replacement = window.id; break;
                            }
                        }
                    }
                }
            }
            if (!result.contains(replacement)) result.add(replacement);
        }
        return WidgetMeters.serialize(result);
    }

    private static boolean isOrdinaryWindow(String key) {
        return key.startsWith("codex:") || key.startsWith("direct:")
                || WidgetMeters.FIVE_HOUR.equals(key) || WidgetMeters.WEEKLY.equals(key)
                || "monthly".equals(key);
    }

    private static WidgetUsageWindow ordinaryWindow(WidgetUsageSnapshot snapshot, String kind) {
        if (snapshot != null) for (WidgetUsageWindow window : snapshot.windows)
            if (isOrdinaryWindow(window.id) && kind.equals(window.kind)) return window;
        return null;
    }

    private static String canonicalKey(String key, WidgetUsageSnapshot snapshot) {
        if (snapshot != null) for (WidgetUsageWindow window : snapshot.windows) {
            if (!window.id.equals(key) || !isOrdinaryWindow(window.id)) continue;
            if ("five_hour".equals(window.kind)) return WidgetMeters.FIVE_HOUR;
            if ("weekly".equals(window.kind)) return WidgetMeters.WEEKLY;
        }
        return key;
    }

    /**
     * Upgrade old ID/name/feature-plus-array-index keys when the current snapshot identifies a
     * unique match. Keep missing or ambiguous keys for a later response, including missing
     * primary/secondary windows of an otherwise recognized limit. Persist the returned CSV to
     * complete migration. Call only for selections known to use the old indexed schema:
     * a temporarily absent real server ID can itself end in a numeric suffix.
     */
    public static String migrateLegacySelection(String csv, UsageSnapshot snapshot) {
        return migrateLegacySelection(csv, csv, snapshot).selection;
    }

    /** A schema migration tracks old keys separately so new numeric IDs never get reinterpreted. */
    public static final class LegacySelectionMigration {
        public final String selection;
        public final String remainingPending;

        private LegacySelectionMigration(String selection, String remainingPending) {
            this.selection = selection;
            this.remainingPending = remainingPending;
        }
    }

    /**
     * Migrate only keys explicitly recorded as legacy by the caller's stored schema. Exact
     * matches, absent limits and ambiguous aliases remain pending until a later snapshot;
     * successful replacements clear their old pending key. User deselection should remove the
     * corresponding pending key at the persistence boundary.
     */
    public static LegacySelectionMigration migrateLegacySelection(String csv,
            String knownLegacyKeys, UsageSnapshot snapshot) {
        LinkedHashSet<String> pending = new LinkedHashSet<>();
        for (String key : WidgetMeters.parse(knownLegacyKeys)) {
            if (WidgetMeters.isLimitKey(key)) pending.add(key);
        }
        LinkedHashSet<String> migrated = new LinkedHashSet<>();
        for (String key : WidgetMeters.parse(csv)) {
            String replacement = pending.contains(key) ? migrateLegacyKey(key, snapshot) : key;
            migrated.add(replacement);
            if (!replacement.equals(key)) pending.remove(key);
        }
        return new LegacySelectionMigration(WidgetMeters.serialize(new ArrayList<>(migrated)),
                WidgetMeters.serialize(new ArrayList<>(pending)));
    }

    private static String migrateLegacyKey(String key, UsageSnapshot snapshot) {
        if (snapshot == null || !WidgetMeters.isLimitKey(key)
                || WidgetMeters.findLimit(key, snapshot) != null) return key;
        String suffix = WidgetMeters.isLimitPrimary(key) ? ":primary" : ":secondary";
        String identity = key.substring("limit:".length(), key.length() - suffix.length());
        int separator = identity.lastIndexOf('-');
        if (separator <= 0 || separator == identity.length() - 1) return key;
        for (int index = separator + 1; index < identity.length(); index++) {
            char character = identity.charAt(index);
            if (character < '0' || character > '9') return key;
        }
        String legacyIdentity = identity.substring(0, separator);
        UsageLimit match = null;
        for (UsageLimit limit : snapshot.additionalLimits) {
            String current = WidgetMeters.limitIdentity(limit);
            String base = current.split("~", 2)[0];
            if (legacyIdentity.equals(base)
                    || legacyIdentity.equals(legacyIdentity(base.replace("%7e", "~")
                            .replace("%2c", ",").replace("%25", "%")))
                    || legacyIdentity.equals(legacyIdentity(limit.name))
                    || legacyIdentity.equals(legacyIdentity(limit.meteredFeature))) {
                if (match != null && !WidgetMeters.limitIdentity(match).equals(current)) return key;
                match = limit;
            }
        }
        return match == null ? key : WidgetMeters.isLimitPrimary(key)
                ? WidgetMeters.limitPrimaryKey(match) : WidgetMeters.limitSecondaryKey(match);
    }

    private static String legacyIdentity(String value) {
        return value.trim().toLowerCase(java.util.Locale.ROOT).replace(',', '_');
    }

    static UsageWindow window(String key, UsageSnapshot snapshot) {
        if (snapshot == null) return null;
        if (WidgetMeters.FIVE_HOUR.equals(key)) return snapshot.fiveHour;
        if (WidgetMeters.WEEKLY.equals(key)) return snapshot.longWindow();
        UsageLimit limit = WidgetMeters.findLimit(key, snapshot);
        return limit == null ? null : WidgetMeters.isLimitPrimary(key) ? limit.primary : limit.secondary;
    }

    private static boolean isWindow(String key) {
        return WidgetMeters.FIVE_HOUR.equals(key) || WidgetMeters.WEEKLY.equals(key)
                || WidgetMeters.NEXT_RESET.equals(key) || WidgetMeters.RESET_CREDITS.equals(key)
                || "monthly".equals(key) || WidgetMeters.isLimitKey(key)
                || isOrdinaryWindow(key) || key.startsWith("extra:");
    }

    public static boolean canEnable(List<String> selected, String key) {
        return isWindow(key);
    }

    static List<String> limitSelection(List<String> selected) {
        return new ArrayList<>(selected);
    }

    /** Prevent old-account/previous-window samples from appearing as this week's history. */
    public static List<UsageSample> currentSamples(UsageHistory history, UsageWindow window,
            long observedAt, long now) {
        List<UsageSample> result = new ArrayList<>();
        if (history == null || window == null) return result;
        long resetAt = window.effectiveResetAtMillis(observedAt);
        if (resetAt <= now) return result;
        for (UsageSample sample : history.samples) {
            if (Math.abs(sample.resetAtMillis - resetAt) <= 60_000L
                    && sample.windowSeconds == window.windowSeconds
                    && sample.observedAtMillis <= now) result.add(sample);
        }
        result.sort(java.util.Comparator.comparingLong(s -> s.observedAtMillis));
        return result;
    }
}
