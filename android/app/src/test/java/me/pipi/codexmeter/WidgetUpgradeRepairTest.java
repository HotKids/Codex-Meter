package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.widget.RemoteViews;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowAppWidgetManager;

/** Cached previews must not be marked repaired after an unsuccessful invalidation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class,
        shadows = {WidgetUpgradeRepairTest.PreviewManager.class,
                ColorOsWidgetAppearanceTest.SyntheticSignedOutStore.class})
public class WidgetUpgradeRepairTest {
    @Test
    public void previewCleanupFailureStaysPendingAndCanRetryTheSameVersion() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        PreviewManager shadow = Shadow.extract(manager);
        ComponentName provider = new ComponentName(app, CodexUsageWidget.class);
        manager.setWidgetPreview(provider, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                new RemoteViews(app.getPackageName(), R.layout.widget_material_preview));
        manager.setWidgetPreview(provider, AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD,
                new RemoteViews(app.getPackageName(), R.layout.widget_material_preview));
        SharedPreferences preferences = app.getSharedPreferences("codex_meter_migrations_v1",
                Context.MODE_PRIVATE);
        long version = app.getPackageManager().getPackageInfo(app.getPackageName(), 0)
                .getLongVersionCode();
        preferences.edit().putLong("widget_repaired_version", version)
                .putBoolean("clean_after_replace", true).commit();
        shadow.refuseRemoval = true;

        WidgetUpgradeRepair.perform(app);

        assertTrue(preferences.getBoolean("clean_after_replace", false));
        assertNotNull(manager.getWidgetPreview(provider, Process.myUserHandle(),
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN));
        assertNotNull(manager.getWidgetPreview(provider, Process.myUserHandle(),
                AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD));
        shadow.refuseRemoval = false;

        WidgetUpgradeRepair.perform(app);

        assertFalse(preferences.contains("clean_after_replace"));
        assertEquals(version, preferences.getLong("widget_repaired_version", -1L));
        assertNull(manager.getWidgetPreview(provider, Process.myUserHandle(),
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN));
        assertNotNull(manager.getWidgetPreview(provider, Process.myUserHandle(),
                AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD));
    }

    @Test
    @Config(sdk = 33)
    public void preGeneratedPreviewDevicesStillCompleteReplacementRepair() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences preferences = app.getSharedPreferences("codex_meter_migrations_v1",
                Context.MODE_PRIVATE);
        preferences.edit().putBoolean("clean_after_replace", true).commit();

        WidgetUpgradeRepair.perform(app);

        assertFalse(preferences.contains("clean_after_replace"));
        assertEquals(app.getPackageManager().getPackageInfo(app.getPackageName(), 0)
                        .getLongVersionCode(),
                preferences.getLong("widget_repaired_version", -1L));
    }

    @Implements(AppWidgetManager.class)
    public static class PreviewManager extends ShadowAppWidgetManager {
        boolean refuseRemoval;

        @Implementation(minSdk = 35)
        protected void removeWidgetPreview(ComponentName provider, int categories) {
            if (refuseRemoval) {
                throw new IllegalStateException("Synthetic unavailable widget service");
            }
            super.removeWidgetPreview(provider, categories);
        }
    }
}
