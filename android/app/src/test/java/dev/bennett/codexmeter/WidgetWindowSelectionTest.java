package dev.bennett.codexmeter;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = QuotaCardsTest.TestApp.class)
public class WidgetWindowSelectionTest {
    private static final String RESPONSE = """
        {"rate_limit":{
          "weekly":{"used_percent":25,"limit_window_seconds":604800},
          "monthly":{"used_percent":35,"limit_window_seconds":2592000}},
         "additional_rate_limits":[{"limit_id":"server-123","metered_feature":"spark",
          "limit_name":"Codex Spark","rate_limit":{
            "primary_window":{"used_percent":10,"limit_window_seconds":18000}}}]}
        """;

    @Test public void upgradesExistingWeeklyAndExtraChoicesWithoutSelectingMonthly() throws Exception {
        WidgetUsageSnapshot snapshot = WidgetUsageParser.parse(RESPONSE, 1234);
        UsageSnapshot legacy = UsageParser.parse(RESPONSE, 1234);
        String saved = "weekly," + WidgetMeters.limitPrimaryKey(legacy.additionalLimits.get(0));
        assertEquals("weekly,extra:spark:primary_window",
                QuotaCardOptions.migrateToWindowIds(saved, snapshot, legacy));
        assertEquals(List.of("weekly", "extra:spark:primary_window"),
                QuotaCardOptions.effectiveWindows(saved, snapshot, legacy));
    }

    @Test public void referenceIdsSurvivePreferenceNormalizationAndMissingWindows() throws Exception {
        String saved = "codex:monthly,extra:spark:primary_window,extra:absent:weekly";
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        AppPreferences.saveWidgetOptions(context, 72, WidgetOptions.defaults().withVisibleMeters(saved));
        WidgetOptions options = AppPreferences.loadWidgetOptions(context, 72);
        assertEquals(saved, options.visibleMeters);
        assertEquals(saved, QuotaCardOptions.migrateToWindowIds(saved,
                WidgetUsageParser.parse(RESPONSE, 1234), null));
        assertEquals(List.of("codex:monthly", "extra:spark:primary_window", "extra:absent:weekly"),
                QuotaCardOptions.effectiveWindows(saved, WidgetUsageParser.parse(RESPONSE, 1234), null));
    }

    @Test public void keepsUserOrderWithBuiltinCatalogAndActualExtraWindows() throws Exception {
        WidgetUsageSnapshot snapshot = WidgetUsageParser.parse(RESPONSE, 1234);
        assertEquals(List.of("five_hour", "weekly", "next_reset", "reset_credits",
                "codex:monthly", "extra:spark:primary_window"),
                QuotaCardOptions.availableWindows(snapshot));
        assertEquals(List.of("extra:spark:primary_window", "codex:monthly", "weekly"),
                QuotaCardOptions.effectiveWindows("extra:spark:primary_window,codex:monthly,codex:weekly", snapshot, null));
    }

    @Test public void legacyLongWindowAliasStillMapsMonthlyOnlyAccounts() throws Exception {
        WidgetUsageSnapshot snapshot = WidgetUsageParser.parse(
                "{\"monthly\":{\"remainingPercent\":75}}", 1234);
        assertEquals(List.of("weekly"), QuotaCardOptions.effectiveWindows("weekly", snapshot, null));
        assertEquals("monthly", QuotaCardOptions.widgetWindow("weekly", snapshot).kind);
        assertEquals(List.of("five_hour", "weekly", "next_reset", "reset_credits"),
                QuotaCardOptions.availableWindows(snapshot));
        assertEquals("extra:missing:weekly", QuotaCardOptions.migrateToWindowIds(
                "extra:missing:weekly", snapshot, null));
    }

    @Test public void monthlyChoiceDuringInitialLoadSurvivesCompleteResponse() throws Exception {
        WidgetUsageSnapshot snapshot = WidgetUsageParser.parse(RESPONSE, 1234);
        assertEquals("codex:monthly", QuotaCardOptions.migrateToWindowIds("monthly", snapshot, null));
        assertEquals(List.of("codex:monthly"), QuotaCardOptions.effectiveWindows("monthly", snapshot, null));
    }

    @Test @Config(shadows = ReferenceWidgetSettingsTest.Account.class)
    public void explicitMonthlyChoiceSurvivesWeeklyOmissionAndRecovery() throws Exception {
        WidgetUsageSnapshot both = WidgetUsageParser.parse(RESPONSE, 1234);
        WidgetUsageSnapshot monthlyOnly = WidgetUsageParser.parse(
                "{\"monthly\":{\"used_percent\":35,\"window_seconds\":2592000}}", 2345);
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        ReferenceWidgetSettingsTest.Account.id = "monthly-selection-test";
        WidgetUsageStore.prefs(context).edit().clear().commit();
        ReferenceWidgetPreferences.preferences(context).edit().clear().commit();
        WidgetOptions selected = WidgetOptions.defaults().withVisibleMeters("codex:monthly");
        ReferenceWidgetPreferences.save(context, 72, selected, 30);
        assertTrue(WidgetUsageStore.save(context, both));
        assertEquals("codex:monthly", ReferenceWidgetPreferences.apply(context, selected).visibleMeters);
        assertTrue(WidgetUsageStore.save(context, monthlyOnly));
        assertEquals("codex:monthly", ReferenceWidgetPreferences.apply(context, selected).visibleMeters);
        List<String> rows = QuotaCardOptions.availableWindows(monthlyOnly, "codex:monthly", null);
        assertEquals(4, rows.size());
        assertTrue(rows.contains("codex:monthly"));
        assertFalse(rows.contains("weekly"));
        assertTrue(WidgetUsageStore.save(context, both));
        WidgetOptions restored = ReferenceWidgetPreferences.apply(context, selected);
        assertEquals("codex:monthly", restored.visibleMeters);
        assertEquals("monthly", QuotaCardOptions.widgetWindow(restored.visibleMeters, both).kind);
    }

    @Test public void builtinCatalogIsAvailableBeforeDataAndForWeeklyOnlyAccounts() throws Exception {
        List<String> builtins = List.of("five_hour", "weekly", "next_reset", "reset_credits");
        assertEquals(builtins, QuotaCardOptions.availableWindows(null));
        WidgetUsageSnapshot weekly = WidgetUsageParser.parse(
                "{\"weekly\":{\"used_percent\":25,\"window_seconds\":604800}}", 1234);
        assertEquals(builtins, QuotaCardOptions.availableWindows(weekly));
        assertNull(QuotaCardOptions.widgetWindow("five_hour", weekly));
        assertEquals(List.of("five_hour", "weekly"),
                QuotaCardOptions.effectiveWindows("five_hour,weekly", weekly, null));
    }

    @Test public void recognizedOrdinarySourceIdsCollapseIntoStableBuiltinRows() throws Exception {
        WidgetUsageSnapshot snapshot = WidgetUsageParser.parse(
                "{\"primary_window\":{\"used_percent\":10,\"window_seconds\":18000},"
                        + "\"weekly\":{\"used_percent\":25,\"window_seconds\":604800}}", 1234);
        assertEquals("weekly,five_hour,next_reset,reset_credits", QuotaCardOptions.migrateToWindowIds(
                "codex:weekly,codex:primary_window,weekly,next_reset,reset_credits", snapshot, null));
        assertEquals("five_hour", QuotaCardOptions.widgetWindow("five_hour", snapshot).kind);
    }

    @Test public void allSelectedWindowsAndMissingRowsRemainAvailableInSavedOrder() throws Exception {
        String saved = "extra:absent:weekly,reset_credits,codex:monthly,five_hour,next_reset,weekly";
        WidgetUsageSnapshot snapshot = WidgetUsageParser.parse(RESPONSE, 1234);
        assertEquals(WidgetMeters.parse(saved), QuotaCardOptions.effectiveWindows(saved, snapshot, null));
        assertEquals(6, QuotaCardOptions.resolve(saved).size());
        assertTrue(QuotaCardOptions.canEnable(QuotaCardOptions.resolve(saved), "extra:spark:primary_window"));
        assertTrue(QuotaCardOptions.availableWindows(snapshot, saved, null).contains("extra:absent:weekly"));
    }
}
