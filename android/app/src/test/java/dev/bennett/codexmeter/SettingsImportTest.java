package dev.bennett.codexmeter;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = QuotaCardsTest.TestApp.class)
public class SettingsImportTest {
    private Context context;
    private SharedPreferences preferences;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE);
        preferences.edit().clear().apply();
        AppPreferences.setAppTheme(context, WidgetOptions.THEME_LIGHT);
    }

    @Test public void missingLaterSectionDoesNotChangeEarlierPreferences() throws Exception {
        SettingsTransfer.Document doc = SettingsTransfer.create(0,
                new JSONObject().put("app_theme", "dark"), null, null, null);
        assertRejectedWithoutWrites(doc, true, true, false, false);
    }

    @Test public void malformedReminderDoesNotPartiallyImportAppSettings() throws Exception {
        SettingsTransfer.Document doc = SettingsTransfer.create(0,
                new JSONObject().put("app_theme", "dark"),
                new JSONObject().put("reset_credit_expiry_lead_times", "invalid"), null, null);
        assertRejectedWithoutWrites(doc, true, true, false, false);
    }

    @Test public void invalidAuthenticationDoesNotPartiallyImportSettings() throws Exception {
        SettingsTransfer.Document doc = SettingsTransfer.create(0,
                new JSONObject().put("app_theme", "dark"), null, null, new JSONObject());
        assertRejectedWithoutWrites(doc, true, false, false, true);
    }

    @Test public void emptySelectionDoesNotMutateAnything() throws Exception {
        SettingsTransfer.Document doc = SettingsTransfer.create(0, new JSONObject(), null, null, null);
        assertRejectedWithoutWrites(doc, false, false, false, false);
    }

    @Test public void unselectedInvalidSectionDoesNotBlockValidImport() throws Exception {
        SettingsTransfer.Document doc = SettingsTransfer.create(0,
                new JSONObject().put("app_theme", "dark").put("default_widget",
                        new JSONObject().put("style", "quota_cards").put("visible_meters", "weekly,next_reset")),
                new JSONObject().put("reset_credit_expiry_lead_times", "invalid"), null, null);
        SettingsTransferStore.ApplyResult result = SettingsTransferStore.apply(context, doc, true, false, false, false);
        assertTrue(result.themeChanged);
        assertEquals("dark", AppPreferences.getAppTheme(context));
        assertEquals("weekly,next_reset", AppPreferences.loadDefaultWidgetOptions(context).visibleMeters);
    }

    private void assertRejectedWithoutWrites(SettingsTransfer.Document doc, boolean app,
            boolean notifications, boolean nowBar, boolean auth) {
        Map<String, ?> before = new HashMap<>(preferences.getAll());
        assertThrows(IllegalArgumentException.class,
                () -> SettingsTransferStore.apply(context, doc, app, notifications, nowBar, auth));
        assertEquals(before, preferences.getAll());
    }
}
