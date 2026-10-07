package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.TypedArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "zh-rCN-mdpi",
        shadows = SettingsAccountCardTest.InMemoryTokenStore.class)
public class AppDynamicColorTest {
    private Application app;

    @Before
    public void resetSyntheticSettings() {
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = null;
    }

    @Test
    public void newInstallationUsesTheSystemLightPalette() {
        assertSystemPalette();
    }

    @Test
    @Config(qualifiers = "zh-rCN-night-mdpi")
    public void newInstallationUsesTheSystemDarkPalette() {
        assertSystemPalette();
    }

    @Test
    public void legacyFalseCannotRestoreFixedAppColors() {
        app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .edit().putBoolean("material_you", false).commit();
        assertSystemPalette();
    }

    @Test
    @Config(qualifiers = "zh-rCN-night-mdpi")
    public void legacyFalseCannotRestoreFixedDarkAppColors() {
        legacyFalseCannotRestoreFixedAppColors();
    }

    @Test
    public void appearancePageKeepsThemeControlsWithoutTheRemovedColorSwitch() {
        try (ActivityController<SettingsActivity> controller = Robolectric.buildActivity(
                SettingsActivity.class, SettingsActivity.pageIntent(app,
                        SettingsActivity.PAGE_APPEARANCE)).setup()) {
            SettingsAppearanceFragment fragment = (SettingsAppearanceFragment) controller.get()
                    .getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            assertNull(fragment.findPreference("material_you"));
            assertNotNull(fragment.findPreference("app_theme"));
            assertNotNull(fragment.findPreference("theme_system_ui"));
        }
    }

    @Test
    public void rootAppearanceSummaryOnlyDescribesTheSelectedTheme() {
        try (ActivityController<SettingsActivity> controller = Robolectric
                .buildActivity(SettingsActivity.class).setup()) {
            SettingsRootFragment fragment = (SettingsRootFragment) controller.get()
                    .getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            assertEquals(app.getString(R.string.settings_theme_system_default),
                    fragment.findPreference("settings_appearance").getSummary().toString());
        }
    }

    @Test
    public void exportOmitsTheRetiredFlagAndPreservesOtherAppearanceSettings() throws Exception {
        app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .edit().putBoolean("material_you", false).commit();
        AppPreferences.setAppTheme(app, WidgetOptions.THEME_DARK);
        AppPreferences.saveDefaultWidgetOptions(app,
                WidgetOptions.defaults().withColorStyle(WidgetOptions.COLOR_CLASSIC));
        JSONObject settings = SettingsTransferStore.collect(app, true, false, false, false)
                .appSettings;
        assertFalse(settings.has("material_you"));
        assertEquals(WidgetOptions.THEME_DARK, settings.getString("app_theme"));
        assertEquals(WidgetOptions.COLOR_CLASSIC,
                settings.getJSONObject("default_widget").getString("color_style"));
    }

    @Test
    public void legacyImportCannotDisableAppColorsOrChangePlacedWidgetStyles() throws Exception {
        app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .edit().putBoolean("material_you", true).commit();
        AppPreferences.saveWidgetOptions(app, 771,
                WidgetOptions.defaults().withColorStyle(WidgetOptions.COLOR_NATIVE));
        AppPreferences.saveWidgetOptions(app, 772,
                WidgetOptions.defaults().withColorStyle(WidgetOptions.COLOR_CLASSIC));
        JSONObject settings = new JSONObject().put("material_you", false)
                .put("automatic_refresh", false).put("refresh_minutes", 30)
                .put("default_widget", SettingsTransfer.widgetOptionsToJson(
                        WidgetOptions.defaults().withColorStyle(WidgetOptions.COLOR_CLASSIC)));
        SettingsTransferStore.ApplyResult result = SettingsTransferStore.apply(app,
                SettingsTransfer.create(1L, settings, null, null, null), true, false, false, false);
        assertFalse("A retired flag must not report a theme change", result.themeChanged);
        assertSystemPalette();
        assertFalse(AppPreferences.getAutomaticRefresh(app));
        assertEquals(30, AppPreferences.getRefreshMinutes(app));
        assertEquals(WidgetOptions.COLOR_CLASSIC,
                AppPreferences.loadDefaultWidgetOptions(app).colorStyle);
        assertEquals(WidgetOptions.COLOR_NATIVE, AppPreferences.loadWidgetOptions(app, 771).colorStyle);
        assertEquals(WidgetOptions.COLOR_CLASSIC, AppPreferences.loadWidgetOptions(app, 772).colorStyle);
    }

    private void assertSystemPalette() {
        try (ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class)) {
            Activity activity = controller.get();
            Ui.applySelectedTheme(activity);
            controller.setup();
            boolean dark = Ui.isDark(activity);
            int accent = activity.getColor(dark ? android.R.color.system_accent1_200
                    : android.R.color.system_accent1_600);
            TypedArray attributes = activity.obtainStyledAttributes(new int[]{
                    androidx.appcompat.R.attr.colorPrimary,
                    androidx.appcompat.R.attr.colorControlActivated});
            try {
                assertEquals("The activity theme uses the system palette", accent,
                        attributes.getColor(0, 0));
                assertEquals("Activated controls use the same system palette", accent,
                        attributes.getColor(1, 0));
            } finally {
                attributes.recycle();
            }
            assertEquals("Programmatic accents match the activity theme", accent,
                    Ui.accent(activity, dark));
        }
    }
}
