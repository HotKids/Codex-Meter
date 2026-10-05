package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class WidgetColorOptionsTest {
    private Application app;
    private SharedPreferences prefs;

    @Before
    public void resetSyntheticSettings() {
        app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    @Test
    public void colorChoiceDefaultsAndValidationStayIndependentOfAppSurface() {
        WidgetOptions defaults = WidgetOptions.defaults();
        assertEquals(WidgetOptions.COLOR_AUTO, defaults.colorStyle);
        for (String value : new String[] {null, "", "one_ui", "material", "unknown"}) {
            assertEquals(WidgetOptions.COLOR_AUTO, defaults.withColorStyle(value).colorStyle);
        }
        for (String value : new String[] {WidgetOptions.COLOR_AUTO, WidgetOptions.COLOR_NATIVE,
                WidgetOptions.COLOR_CLASSIC}) {
            WidgetOptions selected = defaults.withColorStyle(value);
            assertEquals(value, selected.colorStyle);
            assertEquals(defaults.surfaceStyle, selected.surfaceStyle);
            assertEquals(defaults.opacity, selected.opacity);
            assertEquals(defaults.effectiveVisibleMeters(), selected.effectiveVisibleMeters());
        }
        assertEquals(WidgetOptions.COLOR_AUTO, defaults.colorStyle);
    }

    @Test
    public void immutableContentCopiesKeepTheSelectedColorChoice() {
        WidgetOptions selected = WidgetOptions.defaults()
                .withColorStyle(WidgetOptions.COLOR_CLASSIC);
        WidgetOptions copied = selected.withVisibleMeters("weekly,next_reset")
                .withPercentSymbol(false).withCardStyle("legacy");
        assertEquals(WidgetOptions.COLOR_CLASSIC, copied.colorStyle);
        assertEquals("weekly,next_reset", copied.visibleMeters);
        assertFalse(copied.showPercentSymbol);
        assertEquals(WidgetOptions.CARD_CLEAR, copied.cardStyle);
    }

    @Test
    public void defaultAndPlacedSelectionsRoundTripWithoutMigratingSurfaceStyle() {
        prefs.edit().putString("default_surface_style", WidgetOptions.SURFACE_ONE_UI)
                .putString("widget_42_surface_style", WidgetOptions.SURFACE_MATERIAL).commit();
        assertEquals(WidgetOptions.COLOR_AUTO,
                AppPreferences.loadDefaultWidgetOptions(app).colorStyle);
        assertEquals(WidgetOptions.COLOR_AUTO,
                AppPreferences.loadWidgetOptions(app, 42).colorStyle);

        AppPreferences.saveDefaultWidgetOptions(app, WidgetOptions.defaults()
                .withColorStyle(WidgetOptions.COLOR_CLASSIC));
        assertEquals(WidgetOptions.COLOR_CLASSIC, prefs.getString("default_color_style", ""));
        assertEquals(WidgetOptions.COLOR_CLASSIC,
                AppPreferences.loadDefaultWidgetOptions(app).colorStyle);
        assertEquals(WidgetOptions.COLOR_CLASSIC,
                AppPreferences.loadWidgetOptions(app, 42).colorStyle);

        AppPreferences.saveWidgetOptions(app, 42, WidgetOptions.defaults()
                .withColorStyle(WidgetOptions.COLOR_NATIVE));
        assertEquals(WidgetOptions.COLOR_NATIVE, prefs.getString("widget_42_color_style", ""));
        assertEquals(WidgetOptions.COLOR_NATIVE,
                AppPreferences.loadWidgetOptions(app, 42).colorStyle);
        assertEquals(WidgetOptions.COLOR_CLASSIC,
                AppPreferences.loadWidgetOptions(app, 43).colorStyle);
        assertEquals(WidgetOptions.SURFACE_ONE_UI, AppPreferences.getAppStyle(app));

        prefs.edit().putString("widget_42_color_style", "unknown").commit();
        assertEquals(WidgetOptions.COLOR_AUTO,
                AppPreferences.loadWidgetOptions(app, 42).colorStyle);
    }

    @Test
    public void hostIdRemappingAndDeletionIncludeColorChoice() {
        AppPreferences.saveWidgetOptions(app, 42, WidgetOptions.defaults()
                .withColorStyle(WidgetOptions.COLOR_NATIVE));
        AppPreferences.saveWidgetOptions(app, 43, WidgetOptions.defaults()
                .withColorStyle(WidgetOptions.COLOR_CLASSIC));
        AppPreferences.restoreWidgetOptions(app, new int[] {42, 43}, new int[] {43, 44});

        assertFalse(prefs.contains("widget_42_color_style"));
        assertEquals(WidgetOptions.COLOR_NATIVE,
                AppPreferences.loadWidgetOptions(app, 43).colorStyle);
        assertEquals(WidgetOptions.COLOR_CLASSIC,
                AppPreferences.loadWidgetOptions(app, 44).colorStyle);
        AppPreferences.deleteWidgetOptions(app, 43);
        assertFalse(prefs.contains("widget_43_color_style"));
        assertEquals(WidgetOptions.COLOR_CLASSIC,
                AppPreferences.loadWidgetOptions(app, 44).colorStyle);
    }

    @Test
    public void transferRoundTripAndMissingKeysRespectExistingImportDefaults() throws Exception {
        WidgetOptions selected = WidgetOptions.defaults()
                .withColorStyle(WidgetOptions.COLOR_CLASSIC).withVisibleMeters("weekly");
        JSONObject json = SettingsTransfer.widgetOptionsToJson(selected);
        assertEquals(WidgetOptions.COLOR_CLASSIC, json.getString("color_style"));
        assertEquals(WidgetOptions.COLOR_CLASSIC,
                SettingsTransfer.widgetOptionsFromJson(json).colorStyle);

        json.remove("color_style");
        assertEquals(WidgetOptions.COLOR_AUTO,
                SettingsTransfer.widgetOptionsFromJson(json).colorStyle);
        assertEquals(WidgetOptions.COLOR_NATIVE, SettingsTransfer.widgetOptionsFromJson(json,
                WidgetOptions.defaults().withColorStyle(WidgetOptions.COLOR_NATIVE)).colorStyle);
        json.put("color_style", "invalid");
        assertEquals(WidgetOptions.COLOR_AUTO,
                SettingsTransfer.widgetOptionsFromJson(json, selected).colorStyle);
    }
}
