package dev.bennett.codexmeter;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.SizeF;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.Spinner;
import android.widget.CompoundButton;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = QuotaCardsTest.TestApp.class, qualifiers = "zh-rCN-mdpi")
public class HomeWidgetFlowTest {
    private Context context;
    private SharedPreferences preferences;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        WidgetUsageStore.prefs(context).edit().clear().commit();
    }

    @Test public void pinWithoutConfigurationUsesDefaultQuotaWindows() {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int id = shadowOf(manager).createWidget(CodexUsageWidget.class, R.layout.widget_quota_cards);
        assertCards(AppPreferences.loadWidgetOptions(context, id), "five_hour,weekly");
        View view = shadowOf(manager).getViewFor(id);
        assertNotNull(view.findViewById(R.id.primary_samsung_progress));
        assertNotNull(view.findViewById(R.id.secondary_samsung_progress));
    }

    @Test public void oldDialsAndBarsReachCardsWithoutOpeningEditor() {
        for (String style : Arrays.asList("auto", "dials", "bars")) {
            preferences.edit().clear().putString("widget_42_style", style)
                    .putString("widget_42_visible_meters", "five_hour,weekly,next_reset,reset_credits")
                    .apply();
            WidgetOptions loaded = AppPreferences.loadWidgetOptions(context, 42);
            assertCards(loaded, "five_hour,weekly,next_reset,reset_credits");
            RemoteViews responsive = WidgetRenderer.buildResponsiveWidget(context, 42, loaded, new Bundle());
            RemoteViews selected = ReflectionHelpers.callInstanceMethod(responsive,
                    "getRemoteViewsToApply", ClassParameter.from(Context.class, context),
                    ClassParameter.from(SizeF.class, new SizeF(136, 196)));
            assertNotNull(selected.apply(context, new FrameLayout(context)).findViewById(R.id.quota_card_0));
        }
    }

    @Test public void migrationPreservesExplicitPlusChoiceOrderAndAppearance() {
        preferences.edit().putString("widget_42_layout", "bars")
                .putString("widget_42_visible_meters", "five_hour,weekly")
                .putString("widget_42_theme", "dark").putString("widget_42_accent", "violet")
                .putInt("widget_42_opacity", 0).putBoolean("widget_42_show_percent_symbol", false).apply();
        WidgetOptions loaded = AppPreferences.loadWidgetOptions(context, 42);
        assertCards(loaded, "five_hour,weekly");
        assertEquals("dark", loaded.theme);
        assertEquals("violet", loaded.accent);
        assertEquals(0, loaded.opacity);
        assertFalse(loaded.showPercentSymbol);
        assertCards(AppPreferences.loadWidgetOptions(context, 42), "five_hour,weekly");
    }

    @Test public void oldMetricOnlySettingsAndDefaultSettingsMigrate() {
        preferences.edit().putString("default_style", "auto")
                .putString("default_metric_mode", "both")
                .putString("widget_42_style", "rings").putString("widget_42_metric_mode", "five_hour").apply();
        assertCards(AppPreferences.loadDefaultWidgetOptions(context), "five_hour,weekly,next_reset,reset_credits");
        assertCards(AppPreferences.loadWidgetOptions(context, 42), "five_hour");
        assertCards(AppPreferences.loadWidgetOptions(context, 43), "five_hour,weekly,next_reset,reset_credits");
        preferences.edit().putString("widget_45_metric_mode", "five_hour").apply();
        assertCards(AppPreferences.loadWidgetOptions(context, 45), "five_hour");
    }

    @Test public void oldStyleImportsKeepSelectionButUseAdaptiveLayout() {
        preferences.edit().putString("widget_42_style", "auto").apply();
        AppPreferences.loadWidgetOptions(context, 42);
        AppPreferences.saveWidgetOptions(context, 42, WidgetOptions.defaults()
                .withLayout(WidgetOptions.STYLE_DIALS).withVisibleMeters("weekly"));
        assertEquals(WidgetOptions.STYLE_CARDS, AppPreferences.loadWidgetOptions(context, 42).layout);
        assertEquals("weekly", AppPreferences.loadWidgetOptions(context, 42).visibleMeters);
    }

    @Test public void savingAndImportingRetainBuiltinHelpersAndRejectUnsupportedItems() throws Exception {
        WidgetOptions imported = SettingsTransfer.widgetOptionsFromJson(new JSONObject()
                .put("style", "quota_cards")
                .put("visible_meters", "five_hour,weekly,next_reset,usage_trend"));
        AppPreferences.saveDefaultWidgetOptions(context, imported);
        assertCards(AppPreferences.loadWidgetOptions(context, 42), "five_hour,weekly,next_reset");
        AppPreferences.saveWidgetOptions(context, 42, imported.withVisibleMeters("usage_trend"));
        assertCards(AppPreferences.loadWidgetOptions(context, 42), "five_hour,weekly");
        AppPreferences.saveWidgetOptions(context, 43, imported.withLayout(WidgetOptions.STYLE_BARS));
        assertEquals("five_hour,weekly,next_reset", AppPreferences.loadWidgetOptions(context, 43).visibleMeters);
    }

    @Test public void restoredWidgetKeepsSelectionAndDeletesOnlyOldInstance() {
        AppPreferences.saveWidgetOptions(context, 42, WidgetOptions.defaults().withVisibleMeters("next_reset"));
        AppPreferences.saveWidgetOptions(context, 43, WidgetOptions.defaults().withVisibleMeters("five_hour,weekly"));
        new CodexUsageWidget().onRestored(context, new int[]{42}, new int[]{44});
        assertFalse(AppPreferences.hasWidgetOptions(context, 42));
        assertCards(AppPreferences.loadWidgetOptions(context, 43), "five_hour,weekly");
        assertCards(AppPreferences.loadWidgetOptions(context, 44), "next_reset");
    }

    @Test public void editorUsesPersistedDefaultsAndSavesSameLayout() {
        AppPreferences.saveDefaultWidgetOptions(context,
                WidgetOptions.defaults().withVisibleMeters("five_hour,weekly"));
        Intent intent = new Intent(context, WidgetConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 42);
        try (var controller = Robolectric.buildActivity(WidgetConfigActivity.class, intent).setup()) {
            WidgetConfigActivity activity = controller.get();
            Spinner styles = ReflectionHelpers.getField(activity, "referenceStyleSpinner");
            assertEquals(0, styles.getSelectedItemPosition());
            ReflectionHelpers.callInstanceMethod(activity, "save");
            assertCards(AppPreferences.loadWidgetOptions(context, 42), "five_hour,weekly");
            assertEquals(android.app.Activity.RESULT_OK, shadowOf(activity).getResultCode());
        }
    }

    @Test @Config(sdk = 28)
    public void preAndroid12LauncherAlsoUsesCards() {
        int id = shadowOf(AppWidgetManager.getInstance(context))
                .createWidget(CodexUsageWidget.class, R.layout.widget_quota_cards);
        View view = shadowOf(AppWidgetManager.getInstance(context)).getViewFor(id);
        assertNotNull(view.findViewById(R.id.primary_samsung_progress));
    }

    @Test @Config(sdk = {28, 35})
    public void platformSelectedProviderMetadataUsesNewLayouts() throws Exception {
        try (android.content.res.XmlResourceParser xml = context.getResources().getXml(R.xml.codex_widget_info)) {
            while (xml.next() != org.xmlpull.v1.XmlPullParser.START_TAG) { }
            String androidNs = "http://schemas.android.com/apk/res/android";
            String appNs = "http://schemas.android.com/apk/res-auto";
            assertEquals(R.layout.widget_quota_cards,
                    xml.getAttributeResourceValue(androidNs, "initialLayout", 0));
            assertEquals(R.layout.widget_quota_cards,
                    xml.getAttributeResourceValue(appNs, "previewLayoutMedium", 0));
            assertEquals(R.layout.widget_rings,
                    xml.getAttributeResourceValue(appNs, "previewLayoutSmall", 0));
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                assertEquals(R.layout.widget_quota_cards,
                        xml.getAttributeResourceValue(androidNs, "previewLayout", 0));
                assertEquals(2, xml.getAttributeIntValue(androidNs, "targetCellHeight", 0));
            }
        }
    }

    @Test public void editorAllowsEveryCatalogItemAndSavesAllSelectedSlots() {
        UsageSnapshot snapshot=QuotaCardsTest.multiWindowSnapshot();
        AppPreferences.saveSnapshot(context,snapshot);
        Intent intent=new Intent(context,WidgetConfigActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,42);
        try(var controller=Robolectric.buildActivity(WidgetConfigActivity.class,intent).setup()) {
            WidgetConfigActivity activity=controller.get();
            RecyclerView list=ReflectionHelpers.getField(activity,"metersList");
            list.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1200,View.MeasureSpec.EXACTLY));
            list.layout(0,0,400,1200);
            List<String> order=ReflectionHelpers.getField(activity,"meterOrder");
            assertEquals(7,order.size());
            for (int i = 0; i < order.size(); i++) {
                toggle(list, i).setChecked(true);
                assertTrue(toggle(list, i).isChecked());
            }
            ReflectionHelpers.callInstanceMethod(activity,"save");
            assertEquals(order,QuotaCardOptions.resolve(AppPreferences.loadWidgetOptions(context,42).visibleMeters));
        }
    }

    @Test public void editorRejectsDisablingTheFinalSelectedMeter() {
        AppPreferences.saveWidgetOptions(context, 42, WidgetOptions.defaults().withVisibleMeters("next_reset"));
        Intent intent = new Intent(context, WidgetConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 42);
        try (var controller = Robolectric.buildActivity(WidgetConfigActivity.class, intent).setup()) {
            WidgetConfigActivity activity = controller.get();
            RecyclerView list = ReflectionHelpers.getField(activity, "metersList");
            list.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
            list.layout(0, 0, 400, 800);
            toggle(list, 0).setChecked(false);
            assertTrue(toggle(list, 0).isChecked());
            activity.findViewById(R.id.config_save).performClick();
        }
        assertCards(AppPreferences.loadWidgetOptions(context, 42), "next_reset");
    }

    @Test public void sameVersionAppLaunchSchedulesSchemaRepair() {
        long version;
        try {
            version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
        } catch (Exception error) {
            throw new AssertionError(error);
        }
        context.getSharedPreferences("codex_meter_migrations_v1", Context.MODE_PRIVATE)
                .edit().putLong("widget_repaired_version", version).apply();
        android.app.job.JobScheduler scheduler = context.getSystemService(android.app.job.JobScheduler.class);
        scheduler.cancelAll();
        WidgetUpgradeRepair.runIfNeeded(context);
        assertTrue(scheduler.getAllPendingJobs().stream()
                .anyMatch(job -> WidgetRepairJobService.class.getName().equals(job.getService().getClassName())));
    }

    private static CompoundButton toggle(RecyclerView list, int position) {
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
        assertNotNull("The real editor row must be laid out", holder);
        return ReflectionHelpers.getField(holder, "toggle");
    }

    private static void assertCards(WidgetOptions options, String visible) {
        assertEquals(WidgetOptions.STYLE_CARDS, options.layout);
        assertEquals(visible, options.effectiveVisibleMeters());
    }
}
