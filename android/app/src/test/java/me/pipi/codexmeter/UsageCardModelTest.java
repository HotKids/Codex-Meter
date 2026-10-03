package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsagePace;
import dev.bennett.codexmeter.UsageWindow;
import dev.bennett.codexmeter.WidgetMeters;
import dev.bennett.codexmeter.PlanPricing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.SizeF;
import android.widget.RemoteViews;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class UsageCardModelTest {
    private static final long MINUTE = TimeUnit.MINUTES.toMillis(1);
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);
    private static final long DAY = TimeUnit.DAYS.toMillis(1);

    @Test
    public void defaultSelectionIncludesTheThreePhoneMeters() {
        assertEquals(UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY,
                        WidgetMeters.NEXT_RESET),
                WidgetRenderer.selectedKeys(WidgetOptions.defaults(), UsageCardFixtures.plus()));
    }

    @Test
    public void oldModelSelectionsMigrateToTheUpstreamUsageMeters() {
        WidgetOptions saved = WidgetOptions.defaults().withVisibleMeters(
                WidgetMeters.WEEKLY + "," + UsageCardFixtures.SPARK_WEEKLY);
        assertEquals(UsageCardFixtures.keys(WidgetMeters.WEEKLY, WidgetMeters.FIVE_HOUR),
                WidgetRenderer.selectedKeys(saved, UsageCardFixtures.plus()));
    }

    @Test
    public void weeklyMeterUsesTheFreeMonthlyWindow() {
        UsageWindow monthly = UsageCardFixtures.window(10, TimeUnit.DAYS.toSeconds(30), DAY);
        UsageSnapshot free = new UsageSnapshot("free", true, false, null, null, monthly, null,
                null, 0, UsageCardFixtures.NOW);
        assertSame(monthly, WidgetMeters.meterWindow(WidgetMeters.WEEKLY, free));
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    public void meterTitlesAreLocalizedByStableKeys() {
        Resources resources = RuntimeEnvironment.getApplication().getResources();
        UsageSnapshot plus = UsageCardFixtures.plus();
        assertEquals("会话", WidgetMeter.title(resources, WidgetMeters.FIVE_HOUR, plus));
        assertEquals("每周", WidgetMeter.title(resources, WidgetMeters.WEEKLY, plus));
        assertEquals("重置", WidgetMeter.title(resources, WidgetMeters.NEXT_RESET, plus));
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    public void chineseResetCopy() {
        Resources resources = RuntimeEnvironment.getApplication().getResources();
        long now = UsageCardFixtures.NOW;
        assertEquals("重置时间：2h 50m",
                UsageCardFormat.relativeReset(resources, now + 2 * HOUR + 50 * MINUTE, now));
        assertEquals("重置时间：2d 23h",
                UsageCardFormat.relativeReset(resources, now + 2 * DAY + 23 * HOUR, now));
        assertEquals("3d 8h", UsageCardFormat.bareReset(resources, now + 3 * DAY + 8 * HOUR,
                now));
        assertEquals("45m", UsageCardFormat.bareReset(resources, now + 45 * MINUTE, now));
        assertEquals("即将重置", UsageCardFormat.relativeReset(resources, now + 30_000L, now));
        assertEquals("已重置", UsageCardFormat.relativeReset(resources, now - MINUTE, now));
        assertEquals(UsageCardFormat.MISSING, UsageCardFormat.relativeReset(resources, 0L, now));
    }

    @Test
    @Config(qualifiers = "en-rUS")
    public void englishResetCopy() {
        Resources resources = RuntimeEnvironment.getApplication().getResources();
        assertEquals("Resets in 2h 50m", UsageCardFormat.relativeReset(resources,
                UsageCardFixtures.NOW + 2 * HOUR + 50 * MINUTE, UsageCardFixtures.NOW));
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    public void homeExhaustionEstimateUsesCompactUnitsWithoutChangingOtherEstimateCopy() {
        Application app = RuntimeEnvironment.getApplication();
        long now = UsageCardFixtures.NOW;
        long remaining = DAY + 14 * HOUR;
        UsageWindow weekly = UsageCardFixtures.window(50, TimeUnit.DAYS.toSeconds(7),
                7 * DAY - remaining);
        UsagePace.Assessment assessment = UsagePace.assess(weekly, now, now, UsagePace.BALANCED);
        assertTrue(assessment.available);
        assertEquals(remaining, assessment.estimatedRemainingMillis);
        assertEquals("预计 1d 14h 后耗尽", UsageFormat.estimatedExhaustion(app, assessment));
        assertEquals("预计可用：1d 14h", UsageFormat.estimatedRemaining(app, assessment));
        assertEquals("预计 1d 14h 后耗尽", UsageFormat.estimatedRemainingSpoken(app, assessment));
        assertEquals("预计已耗尽", UsageFormat.estimatedExhaustion(app,
                UsagePace.assess(weekly, now, now + remaining, UsagePace.BALANCED)));
        assertEquals("", UsageFormat.estimatedExhaustion(app,
                UsagePace.assess(null, now, now, UsagePace.BALANCED)));
    }

    @Test
    @Config(qualifiers = "en-rUS")
    public void englishHomeExhaustionEstimateKeepsMinutePrecision() {
        Application app = RuntimeEnvironment.getApplication();
        long now = UsageCardFixtures.NOW;
        long remaining = 2 * HOUR + 25 * MINUTE;
        UsageWindow session = UsageCardFixtures.window(50, TimeUnit.HOURS.toSeconds(5),
                5 * HOUR - remaining);
        UsagePace.Assessment assessment = UsagePace.assess(session, now, now, UsagePace.BALANCED);
        assertEquals("Estimated to run out in 2h 25m",
                UsageFormat.estimatedExhaustion(app, assessment));
        assertEquals("Est. 2h 25m", UsageFormat.estimatedRemaining(app, assessment));
    }

    @Test
    public void homeShowsTheFirstThreeAvailableExpiriesAndKeepsUnknownTimesOut() {
        long now = UsageCardFixtures.NOW;
        ResetCreditsSnapshot credits = new ResetCreditsSnapshot(5, Arrays.asList(
                new RateLimitResetCredit("fourth", "both", "available", now, now + 40 * DAY,
                        "", ""),
                new RateLimitResetCredit("used", "both", "redeemed", now, now + DAY, "", ""),
                new RateLimitResetCredit("unknown", "both", "available", now, 0L, "", ""),
                new RateLimitResetCredit("second", "both", "available", now,
                        now + 26 * DAY + 6 * HOUR, "", ""),
                new RateLimitResetCredit("first", "both", "available", now,
                        now + 19 * DAY + 7 * HOUR, "", ""),
                new RateLimitResetCredit("expired", "both", "available", now, now - MINUTE,
                        "", ""),
                new RateLimitResetCredit("third", "both", "available", now,
                        now + 33 * DAY + HOUR, "", "")), now);
        assertEquals("19d 7h · 26d 6h · 33d 1h",
                UsageFormat.resetCreditExpiryCountdowns(credits, now));
        assertEquals("", UsageFormat.resetCreditExpiryCountdowns(
                new ResetCreditsSnapshot(1, Arrays.asList(credits.credits.get(2)), now), now));
        assertEquals("", UsageFormat.resetCreditExpiryCountdowns(
                ResetCreditsSnapshot.summary(2, now), now));
        assertEquals("", UsageFormat.resetCreditExpiryCountdowns(
                new ResetCreditsSnapshot(0, credits.credits, now), now));
        assertEquals("", UsageFormat.resetCreditExpiryCountdowns(null, now));
    }

    @Test
    public void planLabelsUseTitleCaseAndKeepTheirPhoneTierAliases() {
        for (String[] tier : new String[][] {
                {"Free", "free"}, {"Plus", "plus"}, {"Team", "team"},
                {"Pro 100", "prolite", "pro5x", "pro100"},
                {"Pro 200", "pro", "pro10x",
                        "PRO-10X", "pro 10×", "pro200"},
                {"Pro 500", "pro25x", "Pro 25×", "pro500"}}) {
            for (int index = 1; index < tier.length; index++) {
                assertEquals(tier[index], tier[0], UsageFormat.planLabel(tier[index]));
            }
        }
        assertEquals("BUSINESS", UsageFormat.planLabel("business"));
        for (String plan : new String[] {"unknown", "pro20x", "pro_20x", "Pro20x", "Pro 20x"}) {
            assertEquals(plan, "", UsageFormat.planLabel(plan));
        }
    }

    @Test
    @Config(qualifiers = "en-rUS")
    public void missingHistoryPlanLabelKeepsThePhoneTierAlias() {
        assertEquals("Pro 200 · $200/month", SharedLabels.planPriceTitle(
                RuntimeEnvironment.getApplication(), "", PlanPricing.forPlan("pro")));
    }

    @Test
    public void legacyAndImportedStylesAlwaysUseClear() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        WidgetOptions legacy = WidgetOptions.defaults().withCardStyle("color");
        AppPreferences.saveWidgetOptions(app, 42, legacy);
        assertEquals(WidgetOptions.CARD_CLEAR, AppPreferences.loadWidgetOptions(app, 42).cardStyle);
        AppPreferences.deleteWidgetOptions(app, 42);
        assertEquals(WidgetOptions.CARD_CLEAR, AppPreferences.loadWidgetOptions(app, 42).cardStyle);
        JSONObject json = SettingsTransfer.widgetOptionsToJson(legacy);
        json.put("card_style", "color");
        assertEquals(WidgetOptions.CARD_CLEAR, SettingsTransfer.widgetOptionsFromJson(json).cardStyle);
    }

    @Test
    public void oneRowHostSizesUseDialsAndLargerSizesUseClear() {
        assertTrue(WidgetRenderer.oneRow(null, 90f));
        assertTrue(WidgetRenderer.oneRow(null, 129f));
        assertFalse(WidgetRenderer.oneRow(null, 130f));
        assertFalse(WidgetRenderer.oneRow(null, 170f));
        Bundle samsung = new Bundle();
        samsung.putInt("semAppWidgetRowSpan", 1);
        assertTrue(WidgetRenderer.oneRow(samsung, 170f));
        samsung.putInt("semAppWidgetRowSpan", 2);
        assertFalse(WidgetRenderer.oneRow(samsung, 90f));
    }

    @Test
    @Config(sdk = {26, 35})
    public void cardProviderKeepsClearAtItsLegalMinimumHeight() {
        Application app = RuntimeEnvironment.getApplication();
        int widgetId = 43;
        AppWidgetManager manager = bindProvider(app, widgetId, CodexUsageWidget.class);
        WidgetOptions options = WidgetOptions.defaults();
        UsageCardState state = UsageCardFixtures.state(UsageCardFixtures.plus(), 2);
        for (int height : new int[] {110, 129}) {
            Bundle host = new Bundle();
            host.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180);
            host.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height);
            manager.updateAppWidgetOptions(widgetId, host);
            assertEquals("The card provider must match its Clear picker preview at " + height,
                    R.layout.widget_material, WidgetRenderer.build(app, widgetId, options, state,
                            180f, height, manager.getAppWidgetOptions(widgetId)).getLayoutId());
        }
    }

    @Test
    @Config(sdk = {26, 35})
    public void dialProviderStillChangesToClearAfterVerticalExpansion() {
        Application app = RuntimeEnvironment.getApplication();
        int widgetId = 44;
        AppWidgetManager manager = bindProvider(app, widgetId, CodexDialWidget.class);
        WidgetOptions options = WidgetOptions.defaults();
        UsageCardState state = UsageCardFixtures.state(UsageCardFixtures.plus(), 2);
        for (int height : new int[] {90, 170}) {
            Bundle host = new Bundle();
            host.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180);
            host.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height);
            manager.updateAppWidgetOptions(widgetId, host);
            assertEquals(height == 90 ? R.layout.widget_rings : R.layout.widget_material,
                    WidgetRenderer.build(app, widgetId, options, state, 180f, height,
                            manager.getAppWidgetOptions(widgetId)).getLayoutId());
        }
    }

    @Test
    public void legacyHostDimensionsKeepTheirActualResponsiveSizes() {
        Application app = RuntimeEnvironment.getApplication();
        int widgetId = 45;
        AppWidgetManager manager = bindProvider(app, widgetId, CodexUsageWidget.class);
        Bundle host = new Bundle();
        host.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 90);
        host.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 180);
        host.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110);
        host.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 190);
        manager.updateAppWidgetOptions(widgetId, host);
        assertFalse(manager.getAppWidgetOptions(widgetId)
                .containsKey(AppWidgetManager.OPTION_APPWIDGET_SIZES));

        WidgetRenderer.update(app, manager, widgetId);
        RemoteViews published = publishedViews(manager, widgetId);
        for (SizeF size : new SizeF[] {new SizeF(90f, 190f), new SizeF(180f, 110f)}) {
            RemoteViews selected = ReflectionHelpers.callInstanceMethod(published,
                    "getRemoteViewsToApply", ClassParameter.from(Context.class, app),
                    ClassParameter.from(SizeF.class, size));
            assertEquals("The selected variant must use the host's actual content size",
                    size, ReflectionHelpers.callInstanceMethod(selected, "getIdealSize"));
            assertEquals(R.layout.widget_material, selected.getLayoutId());
        }
    }

    @Test
    public void unknownHostDimensionsKeepTheExistingResponsiveFallback() {
        Application app = RuntimeEnvironment.getApplication();
        int widgetId = 46;
        AppWidgetManager manager = bindProvider(app, widgetId, CodexUsageWidget.class);
        WidgetRenderer.update(app, manager, widgetId);
        RemoteViews selected = ReflectionHelpers.callInstanceMethod(
                publishedViews(manager, widgetId), "getRemoteViewsToApply",
                ClassParameter.from(Context.class, app),
                ClassParameter.from(SizeF.class, new SizeF(110f, 60f)));
        assertEquals(new SizeF(110f, 60f),
                ReflectionHelpers.callInstanceMethod(selected, "getIdealSize"));
        assertEquals(R.layout.widget_material, selected.getLayoutId());
    }

    @Test
    public void shadowLayoutIsGeneratedFromTheClearLayout() throws Exception {
        File layouts = new File("src/main/res/layout");
        String material = read(new File(layouts, "widget_material.xml"));
        String shadow = read(new File(layouts, "widget_material_shadow.xml"));
        String body = material.substring(material.indexOf('\n') + 1)
                .replace("@style/WidgetMaterialHeader.", "@style/WidgetMaterialHeaderShadow.");
        assertTrue("Run android/tools/widget-card-shadow.sh after editing widget_material.xml",
                shadow.endsWith(body));
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static AppWidgetManager bindProvider(Application app, int widgetId, Class<?> type) {
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        info.provider = new ComponentName(app, type);
        org.robolectric.Shadows.shadowOf(manager).addInstalledProvider(info);
        org.robolectric.Shadows.shadowOf(manager).bindAppWidgetId(widgetId, info.provider);
        assertEquals(info.provider, manager.getAppWidgetInfo(widgetId).provider);
        return manager;
    }

    private static RemoteViews publishedViews(AppWidgetManager manager, int widgetId) {
        // The shadow inflates without a host size; select from the published variants explicitly.
        Map<Integer, ?> widgets = ReflectionHelpers.getField(
                org.robolectric.Shadows.shadowOf(manager), "widgetInfos");
        return ReflectionHelpers.getField(widgets.get(widgetId), "lastRemoteViews");
    }
}
