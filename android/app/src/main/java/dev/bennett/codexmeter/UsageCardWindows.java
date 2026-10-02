package dev.bennett.codexmeter;

import android.content.res.Resources;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * The usage windows a home-widget card can show, identified by stable keys: {@code five_hour},
 * {@code weekly} (the long window, monthly on Free plans), {@code monthly} (only when both long
 * windows exist) and {@link WidgetMeters} model-limit keys such as
 * {@code limit:<id>:primary}. Titles follow AI-Usage ("5 小时", "每周", "Codex Spark 每周").
 */
final class UsageCardWindows {
    static final String MONTHLY = "monthly";
    /** A small card shows the first two selected windows, a medium card up to four. */
    static final int MAX_SELECTED = 4;

    private static final long FIVE_HOUR_CEILING_SECONDS = TimeUnit.HOURS.toSeconds(6);
    private static final long WEEKLY_FLOOR_SECONDS = TimeUnit.DAYS.toSeconds(6);
    private static final long MONTHLY_FLOOR_SECONDS = TimeUnit.DAYS.toSeconds(25);
    private static final long DAY_SECONDS = TimeUnit.DAYS.toSeconds(1);

    private UsageCardWindows() {
    }

    /** Every window the account currently reports, ordinary windows first. */
    static List<String> availableKeys(UsageSnapshot snapshot) {
        List<String> keys = new ArrayList<>();
        if (snapshot == null) {
            return keys;
        }
        if (snapshot.fiveHour != null) {
            keys.add(WidgetMeters.FIVE_HOUR);
        }
        if (snapshot.longWindow() != null) {
            keys.add(WidgetMeters.WEEKLY);
        }
        if (snapshot.weekly != null && snapshot.monthly != null) {
            keys.add(MONTHLY);
        }
        for (UsageLimit limit : snapshot.additionalLimits) {
            if (limit == null) {
                continue;
            }
            if (limit.primary != null) {
                keys.add(WidgetMeters.limitPrimaryKey(limit));
            }
            if (limit.secondary != null) {
                keys.add(WidgetMeters.limitSecondaryKey(limit));
            }
        }
        return keys;
    }

    /** AI-Usage's default: the first two reported windows (5-hour and weekly on paid plans). */
    static List<String> defaultKeys(UsageSnapshot snapshot) {
        List<String> available = availableKeys(snapshot);
        if (available.isEmpty()) {
            List<String> fallback = new ArrayList<>();
            fallback.add(WidgetMeters.FIVE_HOUR);
            fallback.add(WidgetMeters.WEEKLY);
            return fallback;
        }
        return new ArrayList<>(available.subList(0, Math.min(2, available.size())));
    }

    /** Whether {@code key} names a usage window (rather than a legacy helper meter). */
    static boolean isWindowKey(String key) {
        return WidgetMeters.FIVE_HOUR.equals(key) || WidgetMeters.WEEKLY.equals(key)
                || MONTHLY.equals(key) || WidgetMeters.isLimitKey(key);
    }

    /**
     * The saved selection reduced to window keys in their saved order, capped at
     * {@link #MAX_SELECTED}; falls back to {@link #defaultKeys} when nothing usable is saved.
     * Saved windows that are missing right now are kept so the card shows "—" in their place.
     */
    static List<String> resolve(List<String> saved, UsageSnapshot snapshot) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (saved != null) {
            for (String key : saved) {
                if (isWindowKey(key)) {
                    keys.add(key);
                }
            }
        }
        List<String> result = new ArrayList<>(keys);
        if (result.isEmpty()) {
            return defaultKeys(snapshot);
        }
        return result.size() > MAX_SELECTED ? result.subList(0, MAX_SELECTED) : result;
    }

    /** Editor catalog: the saved selection first, then every other reported window. */
    static List<String> catalog(List<String> selected, UsageSnapshot snapshot) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (selected != null) {
            keys.addAll(selected);
        }
        keys.addAll(availableKeys(snapshot));
        return new ArrayList<>(keys);
    }

    static UsageWindow window(String key, UsageSnapshot snapshot) {
        if (snapshot == null || key == null) {
            return null;
        }
        if (WidgetMeters.FIVE_HOUR.equals(key)) {
            return snapshot.fiveHour;
        }
        if (WidgetMeters.WEEKLY.equals(key)) {
            return snapshot.longWindow();
        }
        if (MONTHLY.equals(key)) {
            return snapshot.monthly;
        }
        UsageLimit limit = WidgetMeters.findLimit(key, snapshot);
        if (limit == null) {
            return null;
        }
        return WidgetMeters.isLimitPrimary(key) ? limit.primary : limit.secondary;
    }

    /** Card title for a window key, using the reported duration where it is known. */
    static String title(Resources resources, String key, UsageSnapshot snapshot) {
        UsageWindow window = window(key, snapshot);
        if (WidgetMeters.FIVE_HOUR.equals(key)) {
            return resources.getString(R.string.widget_card_five_hour);
        }
        if (WidgetMeters.WEEKLY.equals(key)) {
            return resources.getString(snapshot != null && snapshot.longWindowIsMonthly()
                    ? R.string.widget_card_monthly : R.string.widget_card_weekly);
        }
        if (MONTHLY.equals(key)) {
            return resources.getString(R.string.widget_card_monthly);
        }
        if (WidgetMeters.isLimitKey(key)) {
            String windowTitle = window == null || window.windowSeconds <= 0
                    ? resources.getString(WidgetMeters.isLimitPrimary(key)
                            ? R.string.widget_card_five_hour : R.string.widget_card_weekly)
                    : durationTitle(resources, window.windowSeconds);
            return resources.getString(R.string.widget_card_limit_window,
                    limitPrefix(resources, WidgetMeters.findLimit(key, snapshot)), windowTitle);
        }
        return resources.getString(R.string.widget_card_limit);
    }

    /** "5 小时" / "每周" / "每月" / "N 天" / "限额" for a window length, as AI-Usage names them. */
    static String durationTitle(Resources resources, long windowSeconds) {
        if (windowSeconds <= 0) {
            return resources.getString(R.string.widget_card_limit);
        }
        if (windowSeconds <= FIVE_HOUR_CEILING_SECONDS) {
            return resources.getString(R.string.widget_card_five_hour);
        }
        if (windowSeconds >= MONTHLY_FLOOR_SECONDS) {
            return resources.getString(R.string.widget_card_monthly);
        }
        if (windowSeconds >= WEEKLY_FLOOR_SECONDS) {
            return resources.getString(R.string.widget_card_weekly);
        }
        if (windowSeconds >= DAY_SECONDS) {
            return resources.getString(R.string.widget_card_days,
                    Math.round(windowSeconds / (float) DAY_SECONDS));
        }
        return resources.getString(R.string.widget_card_limit);
    }

    private static String limitPrefix(Resources resources, UsageLimit limit) {
        if (limit == null) {
            return resources.getString(R.string.widget_card_extra_limit);
        }
        String combined = (limit.name + " " + limit.meteredFeature).toLowerCase(Locale.ROOT);
        if (combined.contains("spark")) {
            return resources.getString(R.string.widget_card_spark);
        }
        if (!limit.name.trim().isEmpty()) {
            return limit.name.trim();
        }
        if (!limit.meteredFeature.trim().isEmpty()) {
            return limit.meteredFeature.trim();
        }
        return resources.getString(R.string.widget_card_extra_limit);
    }
}
