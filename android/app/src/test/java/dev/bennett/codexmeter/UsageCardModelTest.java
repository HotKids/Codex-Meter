package dev.bennett.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.res.Resources;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class UsageCardModelTest {
    private static final long MINUTE = TimeUnit.MINUTES.toMillis(1);
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);
    private static final long DAY = TimeUnit.DAYS.toMillis(1);

    @Test
    public void defaultSelectionIsTheFirstTwoReportedWindows() {
        assertEquals(UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY),
                UsageCardWindows.resolve(null, UsageCardFixtures.plus()));
        // Upstream's default meters include helper keys; cards keep only the windows.
        assertEquals(UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY),
                UsageCardWindows.resolve(WidgetMeters.defaultVisible(), UsageCardFixtures.plus()));
    }

    @Test
    public void selectionKeepsOrderMissingWindowsAndCapsAtFour() {
        List<String> saved = UsageCardFixtures.keys(UsageCardFixtures.SPARK_WEEKLY,
                WidgetMeters.WEEKLY, "limit:gone:primary", WidgetMeters.FIVE_HOUR,
                UsageCardFixtures.SPARK_FIVE_HOUR);
        assertEquals(UsageCardFixtures.keys(UsageCardFixtures.SPARK_WEEKLY, WidgetMeters.WEEKLY,
                "limit:gone:primary", WidgetMeters.FIVE_HOUR),
                UsageCardWindows.resolve(saved, UsageCardFixtures.plus()));
    }

    @Test
    public void catalogOffersModelLimitsAfterTheSelection() {
        List<String> catalog = UsageCardWindows.catalog(
                UsageCardFixtures.keys(WidgetMeters.WEEKLY), UsageCardFixtures.plus());
        assertEquals(UsageCardFixtures.keys(WidgetMeters.WEEKLY, WidgetMeters.FIVE_HOUR,
                UsageCardFixtures.SPARK_FIVE_HOUR, UsageCardFixtures.SPARK_WEEKLY), catalog);
    }

    @Test
    public void monthlyIsOfferedSeparatelyOnlyWhenWeeklyAlsoExists() {
        UsageWindow monthly = UsageCardFixtures.window(10, TimeUnit.DAYS.toSeconds(30), DAY);
        UsageSnapshot free = new UsageSnapshot("free", true, false, null, null, monthly, null,
                null, 0, UsageCardFixtures.NOW);
        assertEquals(UsageCardFixtures.keys(WidgetMeters.WEEKLY),
                UsageCardWindows.availableKeys(free));
        assertSame(monthly, UsageCardWindows.window(WidgetMeters.WEEKLY, free));
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    public void chineseTitlesFollowAiUsage() {
        Resources resources = RuntimeEnvironment.getApplication().getResources();
        UsageSnapshot plus = UsageCardFixtures.plus();
        assertEquals("5 小时", UsageCardWindows.title(resources, WidgetMeters.FIVE_HOUR, plus));
        assertEquals("每周", UsageCardWindows.title(resources, WidgetMeters.WEEKLY, plus));
        assertEquals("Codex Spark 每周",
                UsageCardWindows.title(resources, UsageCardFixtures.SPARK_WEEKLY, plus));
        assertEquals("Codex Spark 5 小时",
                UsageCardWindows.title(resources, UsageCardFixtures.SPARK_FIVE_HOUR, plus));
        assertEquals("3 天", UsageCardWindows.durationTitle(resources,
                TimeUnit.DAYS.toSeconds(3)));
    }

    @Test
    @Config(qualifiers = "en-rUS")
    public void englishTitles() {
        Resources resources = RuntimeEnvironment.getApplication().getResources();
        assertEquals("Codex Spark Weekly", UsageCardWindows.title(resources,
                UsageCardFixtures.SPARK_WEEKLY, UsageCardFixtures.plus()));
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    public void chineseResetCopy() {
        Resources resources = RuntimeEnvironment.getApplication().getResources();
        long now = UsageCardFixtures.NOW;
        assertEquals("2小时50分后重置",
                UsageCardFormat.relativeReset(resources, now + 2 * HOUR + 50 * MINUTE, now));
        assertEquals("2天23小时后重置",
                UsageCardFormat.relativeReset(resources, now + 2 * DAY + 23 * HOUR, now));
        assertEquals("3天8小时", UsageCardFormat.bareReset(resources, now + 3 * DAY + 8 * HOUR,
                now));
        assertEquals("45分钟", UsageCardFormat.bareReset(resources, now + 45 * MINUTE, now));
        assertEquals("即将重置", UsageCardFormat.relativeReset(resources, now + 30_000L, now));
        assertEquals("已重置", UsageCardFormat.relativeReset(resources, now - MINUTE, now));
        assertEquals(UsageCardFormat.MISSING, UsageCardFormat.relativeReset(resources, 0L, now));
    }

    @Test
    @Config(qualifiers = "en-rUS")
    public void englishResetCopy() {
        Resources resources = RuntimeEnvironment.getApplication().getResources();
        long now = UsageCardFixtures.NOW;
        assertEquals("Resets in 2h 50m",
                UsageCardFormat.relativeReset(resources, now + 2 * HOUR + 50 * MINUTE, now));
        assertEquals("1d 0h", UsageCardFormat.bareReset(resources, now + DAY, now));
    }

    @Test
    public void percentShowsRemaining() {
        assertEquals("64%", UsageCardFormat.percent(UsageCardFixtures.plus().fiveHour));
        assertEquals(UsageCardFormat.MISSING, UsageCardFormat.percent(null));
    }

    @Test
    public void severityFollowsUsedPercentage() {
        UsageCardPalette light = UsageCardPalette.LIGHT;
        assertEquals(light.green, light.severity(UsageCardFixtures.window(59, 18000, HOUR)));
        assertEquals(light.orange, light.severity(UsageCardFixtures.window(60, 18000, HOUR)));
        assertEquals(light.red, light.severity(UsageCardFixtures.window(85, 18000, HOUR)));
    }

    @Test
    public void planBadgesUseAiUsageRecipes() {
        assertEquals("PRO 10X", PlanBadge.text("Pro 10x"));
        assertEquals("CODEX", PlanBadge.text(""));
        assertSame(PlanBadge.recipeFor("Pro 20x"), PlanBadge.recipeFor("Pro 10x"));
        assertSame(PlanBadge.recipeFor("Plus"), PlanBadge.recipeFor("plus"));
        assertSame(PlanBadge.recipeFor("Business"), PlanBadge.recipeFor("business"));
        assertSame(PlanBadge.recipeFor("unknown plan"), PlanBadge.recipeFor("Go"));
    }

    @Test
    public void proPlansAreLabelledTenX() {
        for (String plan : new String[] {"pro", "pro20x", "pro_20x", "Pro 20x", "pro10x",
                "PRO-10X", "pro 10\u00d7"}) {
            assertEquals(plan, "Pro 10x", UsageFormat.planLabel(plan));
        }
        assertEquals("Pro 5x", UsageFormat.planLabel("prolite"));
        assertEquals("Business", UsageFormat.planLabel("business"));
        assertEquals("", UsageFormat.planLabel("unknown"));
    }

    @Test
    public void variantFollowsSizeAndWindowCount() {
        assertEquals(UsageCardRenderer.Variant.SMALL_SINGLE,
                UsageCardRenderer.Variant.of(false, 1));
        assertEquals(UsageCardRenderer.Variant.SMALL_DUAL, UsageCardRenderer.Variant.of(false, 4));
        assertEquals(UsageCardRenderer.Variant.MEDIUM_DUAL, UsageCardRenderer.Variant.of(true, 2));
        assertEquals(UsageCardRenderer.Variant.MEDIUM_TRIPLE,
                UsageCardRenderer.Variant.of(true, 3));
        assertEquals(UsageCardRenderer.Variant.MEDIUM_QUAD, UsageCardRenderer.Variant.of(true, 4));
    }

    @Test
    public void cardStyleSurvivesPreferencesAndTransfer() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        WidgetOptions clear = WidgetOptions.defaults().withCardStyle(WidgetOptions.CARD_CLEAR);
        AppPreferences.saveWidgetOptions(app, 42, clear);
        assertTrue(AppPreferences.loadWidgetOptions(app, 42).clearCard());
        AppPreferences.deleteWidgetOptions(app, 42);
        assertFalse(AppPreferences.loadWidgetOptions(app, 42).clearCard());

        JSONObject json = SettingsTransfer.widgetOptionsToJson(clear);
        assertTrue(SettingsTransfer.widgetOptionsFromJson(json).clearCard());
        assertEquals(WidgetOptions.CARD_COLOR,
                WidgetOptions.defaults().withCardStyle("bogus").cardStyle);
    }

    @Test
    public void shadowLayoutIsGeneratedFromTheCardLayout() throws Exception {
        File layouts = new File("src/main/res/layout");
        String card = read(new File(layouts, "widget_card.xml"));
        String shadow = read(new File(layouts, "widget_card_shadow.xml"));
        String body = card.substring(card.indexOf('\n') + 1)
                .replace("@style/WidgetCardText.", "@style/WidgetCardShadow.");
        assertTrue("Run android/tools/widget-card-shadow.sh after editing widget_card.xml",
                shadow.endsWith(body));

        String material = read(new File(layouts, "widget_material.xml"));
        String materialShadow = read(new File(layouts, "widget_material_shadow.xml"));
        String materialBody = material.substring(material.indexOf('\n') + 1)
                .replace("@style/WidgetMaterialHeader.", "@style/WidgetMaterialHeaderShadow.");
        assertTrue("Run android/tools/widget-card-shadow.sh after editing widget_material.xml",
                materialShadow.endsWith(materialBody));
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
