package dev.bennett.codexmeter;

import static org.junit.Assert.*;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Saved quota choices must keep identifying the same limits across API refreshes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = QuotaCardsTest.TestApp.class)
public class QuotaWindowIdentityTest {
    private JSONObject window(long seconds, int used) throws Exception {
        return new JSONObject().put("limit_window_seconds", seconds).put("used_percent", used)
                .put("reset_at", 1_800_000_000L + used);
    }

    private JSONObject limit(String id, String feature, String name, int used) throws Exception {
        return new JSONObject().put("limit_id", id).put("metered_feature", feature)
                .put("limit_name", name).put("rate_limit", new JSONObject()
                    .put("primary_window", window(18000, used))
                    .put("secondary_window", window(604800, used + 1)));
    }

    private UsageSnapshot snapshot(JSONObject... limits) throws Exception {
        JSONArray additional = new JSONArray();
        for (JSONObject limit : limits) additional.put(limit);
        return UsageParser.parse(new JSONObject().put("rate_limit", new JSONObject()
                .put("primary_window", window(604800, 40)))
                .put("additional_rate_limits", additional).toString(), 1234);
    }

    private String primary(UsageSnapshot snapshot, int index) {
        return WidgetMeters.limitPrimaryKey(snapshot.additionalLimits.get(index));
    }

    private String secondary(UsageSnapshot snapshot, int index) {
        return WidgetMeters.limitSecondaryKey(snapshot.additionalLimits.get(index));
    }

    @Test public void serverIdsSurviveReorderAndChangedDisplayMetadata() throws Exception {
        UsageSnapshot first = snapshot(limit("a", "feature_a", "Same name", 10),
                limit("b", "feature_b", "Same name", 20));
        UsageSnapshot refreshed = snapshot(limit("b", "renamed_feature", "New name", 30),
                limit("a", "feature_a", "Same name", 40));
        assertEquals(primary(first, 0), primary(refreshed, 1));
        assertEquals(primary(first, 1), primary(refreshed, 0));
        assertEquals("New name", refreshed.additionalLimits.get(0).displayName());
        assertEquals(40, QuotaCardOptions.window(primary(first, 0), refreshed).usedPercent);
    }

    @Test public void featureFallbackDistinguishesLimitsWithTheSameName() throws Exception {
        UsageSnapshot first = snapshot(limit("", "spark_a", "Codex Spark", 10),
                limit("", "spark_b", "Codex Spark", 20));
        UsageSnapshot reordered = snapshot(limit("", "spark_b", "Codex Spark", 30),
                limit("", "spark_a", "Codex Spark", 40));
        assertNotEquals(primary(first, 0), primary(first, 1));
        assertEquals(primary(first, 0), primary(reordered, 1));
        assertEquals(primary(first, 1), primary(reordered, 0));
    }

    @Test public void blankIdentityFieldsFallBackToTheName() throws Exception {
        UsageSnapshot first = snapshot(limit(" ", " ", "Named limit", 10));
        UsageSnapshot refreshed = snapshot(limit("", "", "Named limit", 80));
        assertEquals(primary(first, 0), primary(refreshed, 0));
        assertEquals(80, QuotaCardOptions.window(primary(first, 0), refreshed).usedPercent);
    }

    @Test public void collidingFallbackNamesUseCadenceInsteadOfArrayPosition() throws Exception {
        JSONObject shortLimit = limit("", "", "Same name", 10);
        JSONObject longLimit = limit("", "", "Same name", 20);
        longLimit.getJSONObject("rate_limit").put("primary_window", window(86400, 20));
        UsageSnapshot first = snapshot(shortLimit, longLimit);
        UsageSnapshot reordered = snapshot(longLimit, shortLimit);
        assertNotEquals(primary(first, 0), primary(first, 1));
        assertEquals(primary(first, 0), primary(reordered, 1));
        assertEquals(primary(first, 1), primary(reordered, 0));
        assertEquals(QuotaCardOptions.available(first),
                QuotaCardOptions.available(UsageSnapshot.fromJson(first.toJson())));
    }

    @Test public void commaIdsDoNotCollideWithUnderscoreIds() throws Exception {
        UsageSnapshot snapshot = snapshot(limit("a,b", "", "One", 10),
                limit("a_b", "", "Two", 20));
        assertNotEquals(primary(snapshot, 0), primary(snapshot, 1));
        // The old comma-to-underscore format already lost this distinction; do not guess.
        assertEquals("limit:a_b-7:primary", QuotaCardOptions.migrateLegacySelection(
                "limit:a_b-7:primary", snapshot));
    }

    @Test public void legacyNameAndServerIdKeysMigrateAndDeduplicate() throws Exception {
        UsageSnapshot snapshot = snapshot(limit("stable-id", "spark", "Codex Spark", 10));
        String saved = "limit:stable-id-0:primary,limit:codex spark-9:primary,"
                + "limit:codex spark-9:secondary,weekly";
        assertEquals(primary(snapshot, 0) + "," + secondary(snapshot, 0) + ",weekly",
                QuotaCardOptions.migrateLegacySelection(saved, snapshot));
    }

    @Test public void legacyNameKeysCanMigrateToFeatureFallback() throws Exception {
        UsageSnapshot snapshot = snapshot(limit("", "spark", "Codex Spark", 10));
        assertEquals(primary(snapshot, 0), QuotaCardOptions.migrateLegacySelection(
                "limit:codex spark-5:primary", snapshot));
    }

    @Test public void anExactServerIdEndingInDigitsIsNeverStripped() throws Exception {
        UsageSnapshot snapshot = snapshot(limit("model-0", "", "One", 10),
                limit("model", "", "Two", 20));
        assertEquals(primary(snapshot, 0), QuotaCardOptions.migrateLegacySelection(
                primary(snapshot, 0), snapshot));
        assertEquals(primary(snapshot, 0), QuotaCardOptions.migrateLegacySelection(
                "limit:model-0-7:primary", snapshot));
    }

    @Test public void effectiveNeverTreatsAnAbsentStableNumericIdAsLegacy() throws Exception {
        UsageSnapshot first = snapshot(limit("model-0", "", "One", 10),
                limit("model", "", "Two", 20));
        String saved = primary(first, 0) + ",weekly";
        UsageSnapshot missing = snapshot(limit("model", "", "Two", 20));
        assertEquals(List.of(primary(first, 0), "weekly"), QuotaCardOptions.effective(saved, missing));
        assertEquals(List.of(primary(first, 0), "weekly"), QuotaCardOptions.resolve(saved));
    }

    @Test public void pendingMigrationOnlyRewritesKnownLegacyKeys() throws Exception {
        UsageSnapshot snapshot = snapshot(limit("model", "", "Model", 10),
                limit("spark", "", "Spark", 20));
        String stableMissing = "limit:model-0:primary";
        String oldSpark = "limit:spark-3:primary";
        QuotaCardOptions.LegacySelectionMigration migrated = QuotaCardOptions.migrateLegacySelection(
                stableMissing + "," + oldSpark, oldSpark, snapshot);
        assertEquals(stableMissing + "," + primary(snapshot, 1), migrated.selection);
        assertEquals("", migrated.remainingPending);
        QuotaCardOptions.LegacySelectionMigration repeated = QuotaCardOptions.migrateLegacySelection(
                migrated.selection, migrated.remainingPending, snapshot);
        assertEquals(migrated.selection, repeated.selection);
    }

    @Test public void exactLegacyCacheMatchStaysPendingUntilTheNewIdArrives() throws Exception {
        String oldKey = "limit:spark-0:primary";
        UsageSnapshot oldCache = new UsageSnapshot("pro", true, false, null, null,
                List.of(new UsageLimit("spark-0", "Codex Spark", "spark", true, false,
                        new UsageWindow(10, 18000, 0, 1_800_000_000), null)), null, 0, 1234);
        QuotaCardOptions.LegacySelectionMigration first = QuotaCardOptions.migrateLegacySelection(
                oldKey, oldKey, oldCache);
        assertEquals(oldKey, first.selection);
        assertEquals(oldKey, first.remainingPending);
        QuotaCardOptions.LegacySelectionMigration missing = QuotaCardOptions.migrateLegacySelection(
                first.selection, first.remainingPending, snapshot());
        assertEquals(oldKey, missing.selection);
        assertEquals(oldKey, missing.remainingPending);
        UsageSnapshot refreshed = snapshot(limit("", "spark", "Codex Spark", 20));
        QuotaCardOptions.LegacySelectionMigration completed = QuotaCardOptions.migrateLegacySelection(
                missing.selection, missing.remainingPending, refreshed);
        assertEquals(primary(refreshed, 0), completed.selection);
        assertEquals("", completed.remainingPending);
    }

    @Test public void ambiguousLegacyNamesAndMissingLimitsRemainSaved() throws Exception {
        UsageSnapshot snapshot = snapshot(limit("", "one", "Same name", 10),
                limit("", "two", "Same name", 20));
        String saved = "limit:same name-0:primary,limit:gone-9:secondary";
        assertEquals(saved, QuotaCardOptions.migrateLegacySelection(saved, snapshot));
        assertEquals(List.of("limit:same name-0:primary", "limit:gone-9:secondary"),
                QuotaCardOptions.resolve(saved));
    }

    @Test public void effectiveChoicesKeepSavedCsvOrderRegardlessOfApiOrder() throws Exception {
        UsageSnapshot snapshot = snapshot(limit("a", "", "A", 10),
                limit("b", "", "B", 20));
        String saved = primary(snapshot, 1) + ",weekly," + secondary(snapshot, 0)
                + "," + primary(snapshot, 0);
        assertEquals(List.of(primary(snapshot, 1), "weekly", secondary(snapshot, 0),
                primary(snapshot, 0)), QuotaCardOptions.effective(saved, snapshot));
    }

    @Test public void temporaryOmissionKeepsItsSlotAndRestoredDataKeepsSavedOrder()
            throws Exception {
        JSONObject a = limit("a", "", "A", 10);
        JSONObject b = limit("b", "", "B", 20);
        UsageSnapshot first = snapshot(a, b);
        String saved = primary(first, 0) + "," + primary(first, 1);
        assertEquals(List.of(primary(first, 0), primary(first, 1)), QuotaCardOptions.effective(saved, snapshot(a)));
        assertEquals(saved, QuotaCardOptions.migrateLegacySelection(saved, snapshot(a)));
        UsageSnapshot restored = snapshot(b, a);
        assertEquals(List.of(primary(first, 0), primary(first, 1)),
                QuotaCardOptions.effective(saved, restored));
    }

    @Test public void legacySecondaryCanMigrateWhileThatWindowIsMissing() throws Exception {
        JSONObject limit = limit("stable", "spark", "Codex Spark", 10);
        limit.getJSONObject("rate_limit").remove("secondary_window");
        UsageSnapshot missing = snapshot(limit);
        String migrated = QuotaCardOptions.migrateLegacySelection(
                "limit:codex spark-0:secondary", missing);
        assertEquals(secondary(missing, 0), migrated);
        assertEquals(List.of(migrated), QuotaCardOptions.resolve(migrated));
        assertFalse(QuotaCardOptions.available(missing).contains(migrated));
        UsageSnapshot restored = snapshot(limit("stable", "spark", "Codex Spark", 10));
        assertEquals(List.of(migrated), QuotaCardOptions.effective(migrated, restored));
    }

    @Test public void weeklyOnlyResponseStillOffersAllBuiltinMeters() throws Exception {
        assertEquals(List.of("five_hour", "weekly", "next_reset", "reset_credits"),
                QuotaCardOptions.available(snapshot()));
        assertEquals(List.of("five_hour", "weekly"), QuotaCardOptions.effective("five_hour,weekly", snapshot()));
    }
}
