package me.pipi.codexmeter;

import dev.bennett.codexmeter.WidgetMeters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SwitchCompat;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class WidgetConfigActivityTest {
    private static final int WIDGET_ID = 42;
    private final List<ActivityController<WidgetConfigActivity>> activities = new ArrayList<>();
    private Application app;

    @Before
    public void resetSyntheticSettings() {
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @After
    public void destroyActivities() {
        for (ActivityController<WidgetConfigActivity> controller : activities) {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void weeklyOnlyWithoutSnapshotStillOffersAllFourMeters() throws Exception {
        saveSelection(WidgetMeters.WEEKLY);
        WidgetConfigActivity activity = openEditor();

        assertFourRows(activity);
        assertEquals(UsageCardFixtures.keys(WidgetMeters.WEEKLY), selection(activity));
    }

    @Test
    public void togglingOffAndOnChangesSelectionWithoutRemovingRows() throws Exception {
        saveSelection(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY);
        WidgetConfigActivity activity = openEditor();
        RecyclerView list = findWindowList(activity.findViewById(android.R.id.content));
        list.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 600, 1000);
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(0);
        assertNotNull(holder);
        SwitchCompat toggle = findToggle(holder.itemView);
        assertNotNull(toggle);
        assertTrue(toggle.isChecked());
        toggle.performClick();
        assertFalse(toggle.isChecked());
        assertEquals(UsageCardFixtures.keys(WidgetMeters.WEEKLY), selection(activity));
        assertFourRows(activity);

        toggle.performClick();
        assertTrue(toggle.isChecked());
        assertEquals(UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY),
                selection(activity));
        assertFourRows(activity);
    }

    @Test
    public void savedDisabledMeterStaysAvailableAndDisabledAfterReopening() throws Exception {
        saveSelection(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY);
        WidgetConfigActivity activity = openEditor();
        assertTrue(setSelected(activity, WidgetMeters.FIVE_HOUR, false));
        AppPreferences.saveWidgetOptions(app, WIDGET_ID, currentOptions(activity));

        WidgetConfigActivity reopened = openEditor();
        assertFourRows(reopened);
        assertEquals(UsageCardFixtures.keys(WidgetMeters.WEEKLY), selection(reopened));
    }

    @Test
    public void legacyCreditSelectionMigratesToNextResetWithoutChangingOrder() throws Exception {
        saveSelection(WidgetMeters.RESET_CREDITS);
        WidgetConfigActivity creditOnly = openEditor();
        assertEquals(UsageCardFixtures.keys(WidgetMeters.NEXT_RESET), selection(creditOnly));
        assertFourRows(creditOnly);

        saveSelection(WidgetMeters.WEEKLY, WidgetMeters.RESET_CREDITS,
                WidgetMeters.FIVE_HOUR, WidgetMeters.NEXT_RESET);
        WidgetConfigActivity ordered = openEditor();
        assertEquals(UsageCardFixtures.keys(WidgetMeters.WEEKLY, WidgetMeters.NEXT_RESET,
                WidgetMeters.FIVE_HOUR), selection(ordered));
        assertFourRows(ordered);
    }

    private void saveSelection(String... keys) {
        AppPreferences.saveWidgetOptions(app, WIDGET_ID, WidgetOptions.defaults()
                .withVisibleMeters(WidgetMeters.serialize(UsageCardFixtures.keys(keys))));
    }

    private WidgetConfigActivity openEditor() {
        Intent intent = new Intent(app, WidgetConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, WIDGET_ID);
        ActivityController<WidgetConfigActivity> controller = Robolectric
                .buildActivity(WidgetConfigActivity.class, intent).setup();
        activities.add(controller);
        return controller.get();
    }

    private static List<String> selection(WidgetConfigActivity activity) throws Exception {
        return WidgetMeters.parse(currentOptions(activity).effectiveVisibleMeters());
    }

    private static WidgetOptions currentOptions(WidgetConfigActivity activity) throws Exception {
        Method method = WidgetConfigActivity.class.getDeclaredMethod("currentOptions");
        method.setAccessible(true);
        return (WidgetOptions) method.invoke(activity);
    }

    private static boolean setSelected(WidgetConfigActivity activity, String key,
            boolean selected) throws Exception {
        Method method = WidgetConfigActivity.class.getDeclaredMethod("setSelected", String.class,
                boolean.class);
        method.setAccessible(true);
        return (boolean) method.invoke(activity, key, selected);
    }

    private static void assertFourRows(WidgetConfigActivity activity) {
        RecyclerView list = findWindowList(activity.findViewById(android.R.id.content));
        assertNotNull("The editor must expose the meter list", list);
        assertNotNull(list.getAdapter());
        assertEquals(4, list.getAdapter().getItemCount());
    }

    private static RecyclerView findWindowList(View view) {
        if (view instanceof RecyclerView) {
            return (RecyclerView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                RecyclerView list = findWindowList(group.getChildAt(index));
                if (list != null) {
                    return list;
                }
            }
        }
        return null;
    }

    private static SwitchCompat findToggle(View view) {
        if (view instanceof SwitchCompat) {
            return (SwitchCompat) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                SwitchCompat toggle = findToggle(group.getChildAt(index));
                if (toggle != null) {
                    return toggle;
                }
            }
        }
        return null;
    }
}
