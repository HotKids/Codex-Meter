package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.app.Application;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import dev.bennett.codexmeter.DashboardSections;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.WidgetMeters;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
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
@Config(sdk = 35, application = Application.class)
public class AdditionalLimitsRemovalTest {
    private Application app;

    @Before
    public void resetSyntheticSettings() {
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        AppPreferences.saveSnapshot(app, UsageCardFixtures.plus());
        UpdatePreferences.setAutomaticChecks(app, false);
    }

    @Test
    public void editorIgnoresModelDataAndRetainsOtherSavedOrder() {
        AppPreferences.setDashboardOrder(app, Arrays.asList("weekly", "additional_limits",
                "limit:codex_bengalfox", "five_hour", "monthly", "usage_credits",
                "usage_history", "reset_credits"));
        try (ActivityController<DashboardReorderActivity> controller = Robolectric
                .buildActivity(DashboardReorderActivity.class).setup()) {
            DashboardReorderActivity activity = controller.get();
            RecyclerView list = find(activity.findViewById(android.R.id.content),
                    RecyclerView.class);
            assertNotNull(list);
            RecyclerView.Adapter adapter = list.getAdapter();
            assertEquals(6, adapter.getItemCount());
            RecyclerView.ViewHolder first = adapter.createViewHolder(list,
                    adapter.getItemViewType(0));
            adapter.bindViewHolder(first, 0);
            assertEquals(activity.getString(R.string.dashboard_section_weekly_title),
                    find(first.itemView, TextView.class).getText().toString());
            RecyclerView.ViewHolder second = adapter.createViewHolder(list,
                    adapter.getItemViewType(1));
            adapter.bindViewHolder(second, 1);
            assertEquals(activity.getString(R.string.dashboard_section_five_hour_title),
                    find(second.itemView, TextView.class).getText().toString());
        }
    }

    @Test
    public void homeNeverIncludesModelSectionsWithAvailableWindows() throws Exception {
        try (ActivityController<MainActivity> controller = Robolectric
                .buildActivity(MainActivity.class).create()) {
            MainActivity activity = controller.get();
            UsageSnapshot snapshot = UsageCardFixtures.plus();
            for (Method method : MainActivity.class.getDeclaredMethods()) {
                if (method.getName().equals("availableSections")) {
                    method.setAccessible(true);
                    List<String> sections = (List<String>) (method.getParameterCount() == 1
                            ? method.invoke(activity, snapshot)
                            : method.invoke(activity, snapshot,
                                    Arrays.asList("limit:codex_bengalfox")));
                    for (String key : sections) {
                        assertFalse(DashboardSections.isLimitKey(key));
                    }
                    return;
                }
            }
            throw new AssertionError("The home must resolve its available sections");
        }
    }

    @Test
    public void exportOmitsRemovedPreferencesAndLegacyImportIgnoresThem() throws Exception {
        SettingsTransfer.Document exported = SettingsTransferStore.collect(app,
                true, false, false, false);
        assertFalse(exported.appSettings.has("dashboard_additional_limits"));
        assertFalse(exported.appSettings.has("dashboard_hidden_sections"));

        JSONObject oldSettings = new JSONObject()
                .put("dashboard_additional_limits", false)
                .put("dashboard_hidden_sections", "limit:codex_bengalfox")
                .put("dashboard_weekly", false);
        SettingsTransferStore.apply(app, SettingsTransfer.create(1L, oldSettings,
                null, null, null), true, false, false, false);
        assertFalse(AppPreferences.showDashboardWeekly(app));
        assertFalse(app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .contains("dashboard_additional_limits"));
        assertFalse(app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .contains("dashboard_hidden_sections"));
    }

    @Test
    public void legacyWidgetSelectionAndTransferMigrateToStandardMeters() throws Exception {
        WidgetOptions oldOptions = WidgetOptions.defaults()
                .withVisibleMeters(UsageCardFixtures.SPARK_WEEKLY);
        assertEquals("five_hour,weekly,next_reset", oldOptions.effectiveVisibleMeters());
        JSONObject exported = SettingsTransfer.widgetOptionsToJson(oldOptions);
        assertEquals("five_hour,weekly,next_reset", exported.getString("visible_meters"));
        WidgetOptions imported = SettingsTransfer.widgetOptionsFromJson(new JSONObject()
                .put("visible_meters", "weekly," + UsageCardFixtures.SPARK_FIVE_HOUR));
        assertEquals(Arrays.asList(WidgetMeters.WEEKLY, WidgetMeters.FIVE_HOUR),
                WidgetMeters.parse(imported.visibleMeters));
    }

    private static <T extends View> T find(View view, Class<T> type) {
        if (type.isInstance(view)) {
            return type.cast(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                T result = find(group.getChildAt(index), type);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
