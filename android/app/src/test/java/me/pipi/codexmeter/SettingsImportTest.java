package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.app.Application;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** A settings import either applies every selected section or changes nothing. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class SettingsImportTest {
    @Test
    public void unusableTokensLeaveEarlierSectionsUntouched() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        AppPreferences.setAppTheme(app, WidgetOptions.THEME_LIGHT);
        JSONObject appSettings = new JSONObject().put("app_theme", WidgetOptions.THEME_DARK);
        JSONObject unusableAuth = new JSONObject().put("access_token", "");
        SettingsTransfer.Document document = SettingsTransfer.create(1L, appSettings, null,
                null, unusableAuth);
        try {
            SettingsTransferStore.apply(app, document, true, false, false, true);
            fail("unusable tokens must reject the import");
        } catch (Exception expected) {
            // Rejected before anything was written.
        }
        assertEquals(WidgetOptions.THEME_LIGHT, AppPreferences.getAppTheme(app));
    }

    @Test
    public void badLeadTimesLeaveEarlierSectionsUntouched() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        AppPreferences.setAppTheme(app, WidgetOptions.THEME_LIGHT);
        JSONObject appSettings = new JSONObject().put("app_theme", WidgetOptions.THEME_DARK);
        JSONObject notifications = new JSONObject()
                .put("reset_credit_expiry_lead_times", new JSONArray().put("soon"));
        SettingsTransfer.Document document = SettingsTransfer.create(1L, appSettings,
                notifications, null, null);
        try {
            SettingsTransferStore.apply(app, document, true, true, false, false);
            fail("malformed lead times must reject the import");
        } catch (Exception expected) {
            // Rejected before anything was written.
        }
        assertEquals(WidgetOptions.THEME_LIGHT, AppPreferences.getAppTheme(app));
    }
}
