package dev.bennett.codexmeter;

import static org.junit.Assert.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Complete reference-window parsing, independent of App/Wear's fixed-window model. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = QuotaCardsTest.TestApp.class)
public class WidgetUsageParserTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final long WEEK = 604800L;
    private static final long MONTH = 2592000L;

    private JSONObject window(long seconds, double used) throws Exception {
        return new JSONObject().put("limit_window_seconds", seconds).put("used_percent", used)
                .put("reset_at", 1_800_001_000L);
    }

    private WidgetUsageSnapshot parse(JSONObject payload) throws Exception {
        return WidgetUsageParser.parse(payload.toString(), NOW);
    }

    private List<String> ids(WidgetUsageSnapshot snapshot) {
        List<String> result = new ArrayList<>();
        for (WidgetUsageWindow window : snapshot.windows) result.add(window.id);
        return result;
    }

    private WidgetUsageWindow find(WidgetUsageSnapshot snapshot, String id) {
        for (WidgetUsageWindow window : snapshot.windows) if (id.equals(window.id)) return window;
        throw new AssertionError("Missing window: " + id);
    }

    @Test public void weeklyOnlyProHasExactlyTheReportedWindow() throws Exception {
        WidgetUsageSnapshot snapshot = parse(new JSONObject().put("plan_type", "pro")
                .put("rate_limit", new JSONObject().put("weekly", window(WEEK, 64))));
        assertEquals("pro", snapshot.planType);
        assertEquals(NOW, snapshot.fetchedAtMillis);
        assertEquals(List.of("codex:weekly"), ids(snapshot));
        assertEquals("每周", snapshot.windows.get(0).label);
        assertEquals(Double.valueOf(36), snapshot.windows.get(0).remainingPercent());
    }

    @Test public void namedSparkWeeklyIsASeparateSelectableWindow() throws Exception {
        WidgetUsageSnapshot snapshot = parse(new JSONObject().put("rate_limit", new JSONObject()
                .put("weekly", window(WEEK, 64))).put("additional_rate_limits", new JSONArray()
                .put(new JSONObject().put("limit_name", "GPT-5.3-Codex-Spark")
                    .put("metered_feature", "Codex Spark").put("rate_limit", new JSONObject()
                        .put("weekly", window(WEEK, 10))))));
        assertEquals(List.of("codex:weekly", "extra:codex-spark:weekly"), ids(snapshot));
        assertEquals("Codex Spark 每周", snapshot.windows.get(1).label);
        assertEquals(Double.valueOf(90), snapshot.windows.get(1).remainingPercent());
    }

    @Test public void weeklyAndMonthlyBothSurviveInCadenceOrder() throws Exception {
        WidgetUsageSnapshot snapshot = parse(new JSONObject().put("rate_limit", new JSONObject()
                .put("monthly", window(MONTH, 20)).put("weekly", window(WEEK, 64))));
        assertEquals(List.of("codex:weekly", "codex:monthly"), ids(snapshot));
        assertEquals("monthly", snapshot.windows.get(1).kind);
        assertEquals("每月", snapshot.windows.get(1).label);
    }

    @Test public void arbitraryTwoDayWindowIsNotDiscardedOrRenamed() throws Exception {
        WidgetUsageSnapshot snapshot = parse(new JSONObject().put("rate_limit", new JSONObject()
                .put("primary_window", window(172800, 10)).put("secondary_window", window(WEEK, 64))));
        assertEquals(List.of("codex:primary_window", "codex:secondary_window"), ids(snapshot));
        assertEquals("unknown", snapshot.windows.get(0).kind);
        assertEquals("2 天", snapshot.windows.get(0).label);
        assertEquals(172800L, snapshot.windows.get(0).windowSeconds);
    }

    @Test public void allSevenKeysAreReadInMainAndAdditionalGroups() throws Exception {
        String[] keys = {"primary_window", "primaryWindow", "secondary_window", "secondaryWindow",
                "five_hour", "weekly", "monthly"};
        JSONObject main = new JSONObject();
        JSONObject extra = new JSONObject();
        for (int index = 0; index < keys.length; index++) {
            main.put(keys[index], window(18000, index + 1));
            extra.put(keys[index], window(18000, index + 11));
        }
        WidgetUsageSnapshot snapshot = parse(new JSONObject().put("rate_limit", main)
                .put("additional_rate_limits", new JSONArray().put(new JSONObject()
                    .put("metered_feature", "Feature A").put("rateLimit", extra))));
        assertEquals(14, snapshot.windows.size());
        for (String key : keys) {
            assertNotNull(find(snapshot, "codex:" + key));
            assertNotNull(find(snapshot, "extra:feature-a:" + key));
        }
        assertEquals("weekly", find(snapshot, "extra:feature-a:weekly").kind);
        assertEquals("Feature A 每周", find(snapshot, "extra:feature-a:weekly").label);
        assertEquals("monthly", find(snapshot, "codex:monthly").kind);
    }

    @Test public void camelCaseContainersAndNestedExtrasAreAccepted() throws Exception {
        JSONObject root = new JSONObject().put("primaryWindow", window(WEEK, 64))
                .put("additional_rate_limits", new JSONArray().put(new JSONObject()
                    .put("limit_name", "Extra").put("weekly", window(WEEK, 10))));
        WidgetUsageSnapshot snapshot = parse(new JSONObject().put("rateLimit", root));
        assertEquals(List.of("codex:primaryWindow", "extra:extra:weekly"), ids(snapshot));
        assertEquals("Extra 每周", snapshot.windows.get(1).label);
    }

    @Test public void aliasesAndResetOnlyWindowKeepFractionalAndNullablePercentages() throws Exception {
        JSONObject root = new JSONObject().put("primaryWindow", new JSONObject()
                .put("window_seconds", "172800").put("remainingPercent", "87.5")
                .put("resetAt", "2027-01-15T12:00:00Z"))
                .put("weekly", new JSONObject().put("limit_window", WEEK)
                    .put("reset_time_ms", 1_800_001_000_000L));
        WidgetUsageSnapshot snapshot = parse(new JSONObject().put("rateLimit", root));
        assertEquals(2, snapshot.windows.size());
        WidgetUsageWindow twoDay = find(snapshot, "codex:primaryWindow");
        assertEquals(Double.valueOf(12.5), twoDay.usedPercent);
        assertEquals(Double.valueOf(87.5), twoDay.remainingPercent());
        assertEquals(Instant.parse("2027-01-15T12:00:00Z").toEpochMilli(), twoDay.resetAtMillis);
        WidgetUsageWindow weekly = find(snapshot, "codex:weekly");
        assertNull(weekly.usedPercent);
        assertNull(weekly.remainingPercent());
        assertEquals(1_800_001_000_000L, weekly.resetAtMillis);
    }

    @Test public void nullishAliasPriorityDoesNotSkipInvalidFirstValues() throws Exception {
        WidgetUsageSnapshot snapshot = parse(new JSONObject().put("rate_limit", new JSONObject()
                .put("primary_window", new JSONObject().put("limit_window_seconds", "")
                    .put("window_seconds", 18000).put("used_percent", "")
                    .put("usedPercent", 90).put("remaining_percent", 40)
                    .put("reset_at", "bad").put("resetAt", "2027-01-15T12:00:00Z"))));
        WidgetUsageWindow window = snapshot.windows.get(0);
        assertEquals("unknown", window.kind);
        assertEquals("限额", window.label);
        assertEquals(0L, window.windowSeconds);
        assertEquals(Double.valueOf(60), window.usedPercent);
        assertEquals(0L, window.resetAtMillis);
    }

    @Test public void deduplicationIsPerGroupByKindResetAndExactPercentNotDuration() throws Exception {
        JSONObject main = new JSONObject().put("primary_window", window(18000, 12.25))
                .put("primaryWindow", window(20000, 12.25))
                .put("five_hour", window(18000, 12.5));
        WidgetUsageSnapshot snapshot = parse(new JSONObject().put("rate_limit", main)
                .put("additional_rate_limits", new JSONArray().put(new JSONObject()
                    .put("limit_name", "Extra").put("five_hour", window(18000, 12.25)))));
        assertEquals(3, snapshot.windows.size());
        assertFalse(ids(snapshot).contains("codex:primaryWindow"));
        assertNotNull(find(snapshot, "codex:primary_window"));
        assertNotNull(find(snapshot, "codex:five_hour"));
        assertNotNull(find(snapshot, "extra:extra:five_hour"));
    }

    @Test public void duplicateFeatureIdsKeepFirstAndOrdinaryWindowsSortBeforeExtras() throws Exception {
        WidgetUsageSnapshot snapshot = parse(new JSONObject().put("rate_limit", new JSONObject()
                .put("monthly", window(MONTH, 20))).put("additional_rate_limits", new JSONArray()
                .put(new JSONObject().put("metered_feature", "Feature A")
                    .put("five_hour", window(18000, 10)))
                .put(new JSONObject().put("metered_feature", "feature-a")
                    .put("five_hour", window(18000, 90)))));
        assertEquals(List.of("codex:monthly", "extra:feature-a:five_hour"), ids(snapshot));
        assertEquals(Double.valueOf(10), snapshot.windows.get(1).usedPercent);
    }

    @Test public void directWindowsOnlyFillMissingOrdinaryKinds() throws Exception {
        WidgetUsageSnapshot snapshot = parse(new JSONObject().put("rate_limit", new JSONObject()
                .put("secondary_window", window(WEEK, 10)))
                .put("weekly", window(WEEK, 99)).put("monthly", window(MONTH, 30))
                .put("additional_rate_limits", new JSONArray().put(new JSONObject()
                    .put("limit_name", "Extra").put("five_hour", window(18000, 20))))
                .put("five_hour", window(18000, 40)));
        assertEquals(List.of("direct:five_hour", "codex:secondary_window", "direct:monthly",
                "extra:extra:five_hour"), ids(snapshot));
        assertEquals(Double.valueOf(10), find(snapshot, "codex:secondary_window").usedPercent);
    }

    @Test public void wrappersUseTheSourcesSnakeCaseFalsyRules() throws Exception {
        JSONObject outer = new JSONObject().put("used_percent", 0)
                .put("resetAt", "2027-01-15T12:00:00Z")
                .put("primary_window", window(WEEK, 25));
        WidgetUsageSnapshot snapshot = parse(new JSONObject().put("rate_limit", new JSONObject()
                .put("primary_window", outer)));
        assertEquals("每周", snapshot.windows.get(0).label);
        assertEquals(Double.valueOf(25), snapshot.windows.get(0).usedPercent);
        outer.put("used_percent", "0");
        snapshot = parse(new JSONObject().put("rate_limit", new JSONObject().put("primary_window", outer)));
        assertEquals("unknown", snapshot.windows.get(0).kind);
        assertEquals(Double.valueOf(0), snapshot.windows.get(0).usedPercent);
    }

    @Test public void missingZeroAndNegativeCadencesHaveDistinctKindsAndOrdering() throws Exception {
        WidgetUsageSnapshot snapshot = parse(new JSONObject().put("rate_limit", new JSONObject()
                .put("primary_window", new JSONObject().put("used_percent", 10))
                .put("primaryWindow", new JSONObject().put("used_percent", 20).put("window_seconds", 0))
                .put("secondary_window", new JSONObject().put("used_percent", 30).put("window_seconds", -1))));
        assertEquals("codex:secondary_window", snapshot.windows.get(0).id);
        assertEquals(-1L, snapshot.windows.get(0).windowSeconds);
        assertEquals("unknown", find(snapshot, "codex:primary_window").kind);
        assertEquals("five_hour", find(snapshot, "codex:primaryWindow").kind);
    }

    @Test public void epochAliasesShareSecondsMillisAndZeroRules() throws Exception {
        WidgetUsageSnapshot snapshot = parse(new JSONObject().put("rate_limit", new JSONObject()
                .put("primary_window", new JSONObject().put("reset_at", "1800001000"))
                .put("primaryWindow", new JSONObject().put("reset_time", "1800002000000"))
                .put("weekly", new JSONObject().put("reset_time_ms", 10))
                .put("monthly", new JSONObject().put("reset_at", 0))));
        assertEquals(1_800_001_000_000L, find(snapshot, "codex:primary_window").resetAtMillis);
        assertEquals(1_800_002_000_000L, find(snapshot, "codex:primaryWindow").resetAtMillis);
        assertEquals(10_000L, find(snapshot, "codex:weekly").resetAtMillis);
        assertNotNull(find(snapshot, "codex:monthly"));
        assertNull(find(snapshot, "codex:monthly").usedPercent);
    }

    @Test public void numericResetDedupUsesFractionalMillisecondsBeforeDtoConversion() throws Exception {
        WidgetUsageSnapshot snapshot = parse(new JSONObject().put("rate_limit", new JSONObject()
                .put("primary_window", new JSONObject().put("used_percent", 10).put("reset_at", 1.0001))
                .put("primaryWindow", new JSONObject().put("used_percent", 10).put("reset_at", 1.0002))));
        assertEquals(2, snapshot.windows.size());
        assertEquals(1000L, snapshot.windows.get(0).resetAtMillis);
        assertEquals(1000L, snapshot.windows.get(1).resetAtMillis);
    }

    @Test public void percentagesAcceptFiniteNumbersAndClampWithoutRounding() throws Exception {
        WidgetUsageSnapshot snapshot = parse(new JSONObject().put("rate_limit", new JSONObject()
                .put("primary_window", new JSONObject().put("usedPercent", "1.25e1"))
                .put("primaryWindow", new JSONObject().put("used_percent", "0x10"))
                .put("weekly", new JSONObject().put("used_percent", 110))
                .put("monthly", new JSONObject().put("remainingPercent", 110))));
        assertEquals(Double.valueOf(12.5), find(snapshot, "codex:primary_window").usedPercent);
        assertEquals(Double.valueOf(16), find(snapshot, "codex:primaryWindow").usedPercent);
        assertEquals(Double.valueOf(100), find(snapshot, "codex:weekly").usedPercent);
        assertEquals(Double.valueOf(0), find(snapshot, "codex:monthly").usedPercent);
    }

    @Test public void balanceAndResetCreditsNeverBecomeWindows() throws Exception {
        WidgetUsageSnapshot snapshot = parse(new JSONObject().put("credits", new JSONObject()
                .put("balance", "20").put("has_credits", true).put("unlimited", false))
                .put("rateLimitResetCredits", new JSONObject().put("availableCount", "3.9")));
        assertTrue(snapshot.windows.isEmpty());
        assertEquals(Integer.valueOf(3), snapshot.resetCreditsAvailable);
        assertNull(parse(new JSONObject().put("available_count", true)).resetCreditsAvailable);
        assertEquals(Integer.valueOf(0), parse(new JSONObject().put("available_count", -2)).resetCreditsAvailable);
    }

    @Test public void completeCacheRoundTripKeepsOrderNullablePercentAndAllMetadata() throws Exception {
        WidgetUsageSnapshot snapshot = parse(new JSONObject().put("plan_type", "pro")
                .put("rate_limit_reset_credits", new JSONObject().put("available_count", 2))
                .put("rate_limit", new JSONObject().put("primary_window", window(172800, 12.5))
                    .put("weekly", new JSONObject().put("resetAt", "2027-01-15T12:00:00Z"))));
        WidgetUsageSnapshot restored = WidgetUsageSnapshot.fromJson(new JSONObject(snapshot.toJson().toString()));
        assertNotNull(restored);
        assertEquals(ids(snapshot), ids(restored));
        assertEquals(snapshot.planType, restored.planType);
        assertEquals(snapshot.fetchedAtMillis, restored.fetchedAtMillis);
        assertEquals(snapshot.resetCreditsAvailable, restored.resetCreditsAvailable);
        for (int index = 0; index < snapshot.windows.size(); index++) {
            WidgetUsageWindow first = snapshot.windows.get(index);
            WidgetUsageWindow second = restored.windows.get(index);
            assertEquals(first.label, second.label);
            assertEquals(first.kind, second.kind);
            assertEquals(first.usedPercent, second.usedPercent);
            assertEquals(first.windowSeconds, second.windowSeconds);
            assertEquals(first.resetAtMillis, second.resetAtMillis);
        }
        assertTrue(snapshot.toJson().getJSONArray("windows").getJSONObject(1).isNull("used_percent"));
        assertNull(WidgetUsageSnapshot.fromJson(new JSONObject().put("weekly", window(WEEK, 10))));
        List<WidgetUsageWindow> mutable = new ArrayList<>(snapshot.windows);
        WidgetUsageSnapshot copy = new WidgetUsageSnapshot("pro", NOW, null, mutable);
        mutable.clear();
        assertEquals(2, copy.windows.size());
        try { copy.windows.clear(); fail("Snapshot windows must be immutable"); }
        catch (UnsupportedOperationException expected) { }
    }

    @Test public void legacyConversionPreservesSelectionsAndDoesNotFoldSimultaneousMonthly() {
        UsageLimit spark = new UsageLimit("stable-id", "Codex Spark", "spark", true, false,
                new UsageWindow(10, 18000, 50, 0), new UsageWindow(20, WEEK, 0, 1_800_001_000L));
        UsageSnapshot legacy = new UsageSnapshot("pro", true, false,
                new UsageWindow(25, 18000, 0, 0), new UsageWindow(30, WEEK, 0, 0),
                new UsageWindow(40, MONTH, 0, 0), List.of(spark), null, -1, NOW);
        WidgetUsageSnapshot snapshot = WidgetUsageSnapshot.fromLegacy(legacy);
        assertEquals(List.of("five_hour", "weekly", "monthly", WidgetMeters.limitPrimaryKey(spark),
                WidgetMeters.limitSecondaryKey(spark)), ids(snapshot));
        assertEquals("Codex Spark 5 小时", snapshot.windows.get(3).label);
        assertEquals(NOW + 50_000L, snapshot.windows.get(3).resetAtMillis);
        assertNull(snapshot.resetCreditsAvailable);
        UsageSnapshot monthlyOnly = new UsageSnapshot("free", true, false, null, null,
                legacy.monthly, List.of(), null, 0, NOW);
        WidgetUsageSnapshot monthly = WidgetUsageSnapshot.fromLegacy(monthlyOnly);
        assertEquals(List.of("weekly"), ids(monthly));
        assertEquals("monthly", monthly.windows.get(0).kind);
        assertEquals("每月", monthly.windows.get(0).label);
        assertEquals(Integer.valueOf(0), monthly.resetCreditsAvailable);
    }
}
