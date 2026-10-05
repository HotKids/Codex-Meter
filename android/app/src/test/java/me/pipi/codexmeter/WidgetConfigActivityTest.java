package me.pipi.codexmeter;

import dev.bennett.codexmeter.WidgetMeters;
import dev.bennett.codexmeter.UsageCredits;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsageWindow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.XmlResourceParser;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.ImageView;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SwitchCompat;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.robolectric.shadows.ShadowDialog;
import org.xmlpull.v1.XmlPullParser;

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
    public void proPlansDefaultToWeeklyAndReset() {
        for (String plan : new String[] {"prolite", "pro5x", "pro100", "pro", "pro10x",
                "pro200", "pro25x", "pro500", " Pro_100 ", "PRO-200", "Pro 25×"}) {
            assertTrue(AppPreferences.saveSnapshot(app,
                    UsageCardFixtures.snapshot(plan, 20, 30, false)));
            assertEquals(plan, "weekly,next_reset",
                    AppPreferences.loadDefaultWidgetOptions(app).effectiveVisibleMeters());
            assertEquals(plan, "weekly,next_reset",
                    AppPreferences.loadWidgetOptions(app, WIDGET_ID).effectiveVisibleMeters());
            assertTrue(AppPreferences.showDashboardFiveHour(app));
        }
    }

    @Test
    public void nonProAndUnknownPlansKeepTheExistingDefault() {
        assertTrue(AppPreferences.loadWidgetOptions(app, WIDGET_ID).showsFiveHour());
        for (String plan : new String[] {"free", "plus", "team", "business", "enterprise",
                "premium", "go", "pro20x", "unknown", "", null}) {
            assertTrue(AppPreferences.saveSnapshot(app,
                    UsageCardFixtures.snapshot(plan, 20, 30, false)));
            assertEquals(plan, "five_hour,weekly,next_reset",
                    AppPreferences.loadWidgetOptions(app, WIDGET_ID).effectiveVisibleMeters());
        }
    }

    @Test
    public void newProWidgetsRemoveInheritedSessionWhileSavedSelectionsSurvivePlanChanges() {
        AppPreferences.saveSnapshot(app, UsageCardFixtures.snapshot("pro", 20, 30, false));
        AppPreferences.saveDefaultWidgetOptions(app, WidgetOptions.defaults()
                .withVisibleMeters("five_hour,usage_credits"));
        assertEquals("usage_credits",
                AppPreferences.loadWidgetOptions(app, WIDGET_ID).effectiveVisibleMeters());
        assertEquals("five_hour,usage_credits",
                AppPreferences.loadDefaultWidgetOptions(app).effectiveVisibleMeters());

        saveSelection(WidgetMeters.NEXT_RESET, WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY);
        assertEquals("next_reset,five_hour,weekly",
                AppPreferences.loadWidgetOptions(app, WIDGET_ID).effectiveVisibleMeters());
        AppPreferences.saveSnapshot(app, UsageCardFixtures.snapshot("plus", 20, 30, false));
        assertEquals("next_reset,five_hour,weekly",
                AppPreferences.loadWidgetOptions(app, WIDGET_ID).effectiveVisibleMeters());
    }

    @Test
    public void legacyPerWidgetContentKeepsPriorityOverProInitialization() {
        AppPreferences.saveSnapshot(app, UsageCardFixtures.snapshot("pro500", 20, 30, false));
        for (String prefix : new String[] {"default_", "widget_42_"}) {
            for (String[] legacy : new String[][] {
                    {"five_hour", "five_hour"}, {"both", "five_hour,weekly,next_reset"},
                    {"weekly", "weekly"}}) {
                app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                        .edit().remove("default_metric_mode").remove("widget_42_metric_mode")
                        .putString(prefix + "metric_mode", legacy[0]).commit();
                String expected = "default_".equals(prefix) && !"weekly".equals(legacy[0])
                        ? "weekly,next_reset" : legacy[1];
                assertEquals(prefix + legacy[0], expected,
                        AppPreferences.loadWidgetOptions(app, WIDGET_ID).effectiveVisibleMeters());
            }
        }
    }

    @Test
    public void newProWidgetsDropOnlySessionAndKeepTheInheritedOrderAndAppearance() {
        AppPreferences.saveSnapshot(app, UsageCardFixtures.snapshot("pro200", 20, 30, false));
        WidgetOptions defaults = new WidgetOptions("auto", "system", "app", 65,
                "hidden", "remaining").withVisibleMeters("usage_credits,five_hour,next_reset,weekly")
                .withColorStyle(WidgetOptions.COLOR_NATIVE).withPercentSymbol(false);
        AppPreferences.saveDefaultWidgetOptions(app, defaults);

        WidgetOptions added = AppPreferences.loadWidgetOptions(app, WIDGET_ID);
        assertEquals("usage_credits,next_reset,weekly", added.effectiveVisibleMeters());
        assertEquals(defaults.colorStyle, added.colorStyle);
        assertEquals(defaults.opacity, added.opacity);
        assertEquals(defaults.showPercentSymbol, added.showPercentSymbol);
        assertEquals(defaults.effectiveVisibleMeters(),
                AppPreferences.loadDefaultWidgetOptions(app).effectiveVisibleMeters());
    }

    @Test
    public void newProSessionOnlyDefaultsFallBackWithoutChangingAnExistingManualSelection() {
        AppPreferences.saveSnapshot(app, UsageCardFixtures.snapshot("pro500", 20, 30, false));
        WidgetOptions session = WidgetOptions.defaults().withVisibleMeters("five_hour");
        AppPreferences.saveDefaultWidgetOptions(app, session);
        AppPreferences.saveWidgetOptions(app, WIDGET_ID, session);

        assertEquals("weekly,next_reset",
                AppPreferences.loadWidgetOptions(app, 43).effectiveVisibleMeters());
        assertEquals("five_hour",
                AppPreferences.loadWidgetOptions(app, WIDGET_ID).effectiveVisibleMeters());
        assertEquals("five_hour",
                AppPreferences.loadDefaultWidgetOptions(app).effectiveVisibleMeters());
    }

    @Test
    public void nonProNewWidgetsKeepInheritedSessionSelections() {
        AppPreferences.saveDefaultWidgetOptions(app, WidgetOptions.defaults()
                .withVisibleMeters("usage_credits,five_hour,next_reset"));
        for (String plan : new String[] {"plus", "free", "team", "pro20x", "unknown"}) {
            AppPreferences.saveSnapshot(app, UsageCardFixtures.snapshot(plan, 20, 30, false));
            assertEquals(plan, "usage_credits,five_hour,next_reset",
                    AppPreferences.loadWidgetOptions(app, WIDGET_ID).effectiveVisibleMeters());
        }
    }

    @Test
    public void proDefaultKeepsTheFiveHourSwitchAvailableOnBothWidgetTypes() throws Exception {
        AppPreferences.saveSnapshot(app, UsageCardFixtures.snapshot("pro100", 20, 30, false));
        for (Class<?> provider : new Class<?>[] {CodexUsageWidget.class, CodexDialWidget.class}) {
            AppPreferences.deleteWidgetOptions(app, WIDGET_ID);
            bindProvider(provider, provider == CodexDialWidget.class ? 90 : 180);
            WidgetConfigActivity activity = openEditor();
            assertEquals("weekly,next_reset", currentOptions(activity).effectiveVisibleMeters());
            RecyclerView list = findWindowList(activity.findViewById(android.R.id.content));
            assertEquals(provider == CodexDialWidget.class ? 3 : 4,
                    list.getAdapter().getItemCount());
            rowSummary(activity, 2);
            SwitchCompat toggle = findToggle(list.findViewHolderForAdapterPosition(2).itemView);
            assertNotNull(toggle);
            assertFalse(toggle.isChecked());
            toggle.performClick();
            assertTrue(toggle.isChecked());
            AppPreferences.saveWidgetOptions(app, WIDGET_ID, currentOptions(activity));
            assertTrue(currentOptions(openEditor()).showsFiveHour());
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

    @Test
    @Config(qualifiers = "zh-rCN")
    public void cardDescriptionsStayVisibleAndDistinguishMissingDataFromZeroBalance()
            throws Exception {
        saveSelection(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY,
                WidgetMeters.NEXT_RESET, WidgetOptions.USAGE_CREDITS);
        WidgetConfigActivity activity = openEditor();
        String[] descriptions = {"5h 会话用量及重置时间", "每周用量及重置时间",
                "下次限额重置倒计时及可用重置额度", "当前余额"};
        for (int position = 0; position < descriptions.length; position++) {
            TextView summary = rowSummary(activity, position);
            assertEquals(View.VISIBLE, summary.getVisibility());
            assertEquals(descriptions[position] + " · 暂无数据", summary.getText().toString());
        }

        Field snapshot = WidgetConfigActivity.class.getDeclaredField("snapshot");
        snapshot.setAccessible(true);
        snapshot.set(activity, new UsageSnapshot("plus", true, false,
                new UsageWindow(20, 18000L, 3600L, 0L),
                new UsageWindow(30, 604800L, 7200L, 0L), null, null,
                new UsageCredits(true, false, "0"), 0, System.currentTimeMillis()));
        RecyclerView list = findWindowList(activity.findViewById(android.R.id.content));
        list.getAdapter().notifyDataSetChanged();
        for (int position = 0; position < descriptions.length; position++) {
            TextView summary = rowSummary(activity, position);
            assertEquals(View.VISIBLE, summary.getVisibility());
            assertEquals(descriptions[position], summary.getText().toString());
        }
        SwitchCompat toggle = findToggle(list.findViewHolderForAdapterPosition(0).itemView);
        toggle.performClick();
        assertFalse(toggle.isChecked());
        assertEquals(View.VISIBLE, rowSummary(activity, 0).getVisibility());
        assertEquals(descriptions[0], rowSummary(activity, 0).getText().toString());
        assertNotNull(findText(activity.findViewById(android.R.id.content),
                activity.getString(R.string.widget_editor_refresh_hint)));
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    public void dialDescriptionsAndRefreshHintMatchTheOneRowControls() throws Exception {
        Bundle size = new Bundle();
        size.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180);
        size.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 90);
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        org.robolectric.Shadows.shadowOf(manager).bindAppWidgetId(WIDGET_ID,
                new ComponentName(app, CodexDialWidget.class));
        manager.updateAppWidgetOptions(WIDGET_ID, size);
        saveSelection(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY, WidgetMeters.NEXT_RESET);
        WidgetConfigActivity activity = openEditor();
        RecyclerView list = findWindowList(activity.findViewById(android.R.id.content));
        assertEquals(3, list.getAdapter().getItemCount());
        String[] descriptions = {"5h 会话用量", "每周用量", "下次限额重置倒计时"};
        for (int position = 0; position < descriptions.length; position++) {
            assertEquals(descriptions[position] + " · 暂无数据",
                    rowSummary(activity, position).getText().toString());
        }
        assertNotNull(findText(activity.findViewById(android.R.id.content),
                activity.getString(R.string.widget_editor_refresh_hint_dial)));
    }

    @Test
    @Config(sdk = {31, 35}, qualifiers = "zh-rCN")
    public void minimumHeightCardProviderKeepsAllControlsAndCardRefreshHint() throws Exception {
        bindProvider(CodexUsageWidget.class, 110);
        saveSelection(WidgetOptions.USAGE_CREDITS);
        WidgetConfigActivity activity = openEditor();
        assertFourRows(activity);
        assertEquals(UsageCardFixtures.keys(WidgetOptions.USAGE_CREDITS), selection(activity));
        assertNotNull(findText(activity.findViewById(android.R.id.content),
                activity.getString(R.string.widget_editor_refresh_hint)));
    }

    @Test
    @Config(sdk = {31, 35}, qualifiers = "zh-rCN")
    public void remainingCreditsOnlyShrunkToOneRowKeepsTheRenderedWeeklySelection()
            throws Exception {
        AppWidgetManager manager = bindProvider(CodexDialWidget.class, 170);
        saveSelection(WidgetOptions.USAGE_CREDITS);
        WidgetConfigActivity expanded = openEditor();
        assertFourRows(expanded);
        assertEquals(UsageCardFixtures.keys(WidgetOptions.USAGE_CREDITS), selection(expanded));

        Bundle size = new Bundle(manager.getAppWidgetOptions(WIDGET_ID));
        size.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 90);
        manager.updateAppWidgetOptions(WIDGET_ID, size);
        RemoteViews remote = WidgetRenderer.build(app, WIDGET_ID,
                AppPreferences.loadWidgetOptions(app, WIDGET_ID),
                UsageCardFixtures.state(UsageCardFixtures.plus(), 2), 180f, 90f, size);
        View rendered = remote.apply(app, new FrameLayout(app));
        assertEquals("42%", ((TextView) rendered.findViewById(
                R.id.primary_samsung_value)).getText().toString());

        WidgetConfigActivity shrunk = openEditor();
        RecyclerView list = findWindowList(shrunk.findViewById(android.R.id.content));
        assertEquals(3, list.getAdapter().getItemCount());
        assertEquals(UsageCardFixtures.keys(WidgetMeters.WEEKLY), selection(shrunk));
        rowSummary(shrunk, 0);
        assertTrue(findToggle(list.findViewHolderForAdapterPosition(0).itemView).isChecked());
        for (int position = 1; position < 3; position++) {
            rowSummary(shrunk, position);
            assertFalse(findToggle(list.findViewHolderForAdapterPosition(position).itemView)
                    .isChecked());
        }
        AppPreferences.saveWidgetOptions(app, WIDGET_ID, currentOptions(shrunk));
        WidgetConfigActivity reopened = openEditor();
        assertEquals(UsageCardFixtures.keys(WidgetMeters.WEEKLY), selection(reopened));
    }

    @Test
    public void firstAddRequiresConfigurationAndCancellationReturnsTheAllocatedId()
            throws Exception {
        for (Class<?> provider : new Class<?>[] {CodexUsageWidget.class, CodexDialWidget.class}) {
            int metadata = app.getPackageManager().getReceiverInfo(new ComponentName(app, provider),
                    PackageManager.GET_META_DATA).metaData.getInt("android.appwidget.provider");
            XmlResourceParser parser = app.getResources().getXml(metadata);
            try {
                while (parser.next() != XmlPullParser.START_TAG) {
                    assertTrue(parser.getEventType() != XmlPullParser.END_DOCUMENT);
                }
                String android = "http://schemas.android.com/apk/res/android";
                assertEquals(WidgetConfigActivity.class.getName(),
                        parser.getAttributeValue(android, "configure"));
                assertEquals(AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE,
                        parser.getAttributeIntValue(android, "widgetFeatures", -1));
            } finally {
                parser.close();
            }
            bindProvider(provider, provider == CodexDialWidget.class ? 90 : 180);
            Map<String, ?> before = new HashMap<>(app.getSharedPreferences(
                    "codex_meter_settings_v1", Context.MODE_PRIVATE).getAll());
            WidgetConfigActivity activity = openEditor();
            assertFalse(activity.isFinishing());
            assertTrue(setSelected(activity, WidgetMeters.FIVE_HOUR, false));
            activity.findViewById(R.id.config_cancel).performClick();
            assertTrue(activity.isFinishing());
            assertEquals(Activity.RESULT_CANCELED,
                    org.robolectric.Shadows.shadowOf(activity).getResultCode());
            Intent result = org.robolectric.Shadows.shadowOf(activity).getResultIntent();
            assertNotNull("The launcher needs its allocated ID even when setup is canceled", result);
            assertEquals(WIDGET_ID, result.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID));
            assertEquals(before, app.getSharedPreferences("codex_meter_settings_v1",
                    Context.MODE_PRIVATE).getAll());
        }
    }

    @Test
    public void rotationRestoresTheAllocatedInstanceAndSaveOnlyChangesThatInstance()
            throws Exception {
        AppWidgetManager manager = bindProvider(CodexUsageWidget.class, 180);
        org.robolectric.Shadows.shadowOf(manager).bindAppWidgetId(43,
                new ComponentName(app, CodexUsageWidget.class));
        saveSelection(WidgetMeters.FIVE_HOUR);
        AppPreferences.saveWidgetOptions(app, 43,
                WidgetOptions.defaults().withVisibleMeters(WidgetMeters.WEEKLY));
        WidgetConfigActivity original = openEditor();
        ActivityController<WidgetConfigActivity> controller = activities.remove(activities.size() - 1);
        Bundle state = new Bundle();
        controller.saveInstanceState(state).pause().stop().destroy();
        Intent restoredIntent = new Intent(original.getIntent());
        restoredIntent.removeExtra(AppWidgetManager.EXTRA_APPWIDGET_ID);
        ActivityController<WidgetConfigActivity> restored = Robolectric
                .buildActivity(WidgetConfigActivity.class, restoredIntent)
                .create(state).start().resume().visible();
        activities.add(restored);
        WidgetConfigActivity activity = restored.get();
        assertFalse("The saved allocation must survive a recreated launch Intent", activity.isFinishing());
        assertEquals(UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR), selection(activity));
        assertTrue(setSelected(activity, WidgetMeters.NEXT_RESET, true));
        activity.findViewById(R.id.config_save).performClick();
        assertEquals(Activity.RESULT_OK, org.robolectric.Shadows.shadowOf(activity).getResultCode());
        assertEquals(WIDGET_ID, org.robolectric.Shadows.shadowOf(activity).getResultIntent()
                .getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID));
        assertEquals("five_hour,next_reset",
                AppPreferences.loadWidgetOptions(app, WIDGET_ID).effectiveVisibleMeters());
        assertEquals("weekly", AppPreferences.loadWidgetOptions(app, 43).effectiveVisibleMeters());
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    public void colorStyleChoicesSitBelowBackgroundAndRefreshThePreview() throws Exception {
        bindProvider(CodexUsageWidget.class, 180);
        WidgetConfigActivity activity = openEditor();
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
        WidgetOptions before = currentOptions(activity);
        View styleRow = colorStyleRow(activity);
        ViewGroup appearance = (ViewGroup) styleRow.getParent();
        int stylePosition = appearance.indexOfChild(styleRow);
        assertNotNull(findText(appearance.getChildAt(stylePosition - 2), "背景"));
        assertEquals(R.id.opacity_slider,
                appearance.getChildAt(stylePosition + 2).findViewById(R.id.opacity_slider).getId());
        assertEquals(WidgetOptions.COLOR_AUTO, before.colorStyle);
        assertNotNull(findText(styleRow, "自动"));

        FrameLayout preview = previewContainer(activity);
        View automaticPreview = preview.getChildAt(1);
        AlertDialog dialog = openColorStyleChoices(activity);
        assertEquals(0, dialog.getListView().getCheckedItemPosition());
        assertEquals("自动", dialog.getListView().getAdapter().getItem(0));
        assertEquals("原生", dialog.getListView().getAdapter().getItem(1));
        assertEquals("经典", dialog.getListView().getAdapter().getItem(2));
        dialog.getListView().performItemClick(null, 1, 1);
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(dialog.isShowing());
        assertEquals(WidgetOptions.COLOR_NATIVE, currentOptions(activity).colorStyle);
        assertNotNull(findText(styleRow, "原生"));
        assertNotSame(automaticPreview, preview.getChildAt(1));
        assertEquals(before.opacity, currentOptions(activity).opacity);
        assertEquals(before.layout, currentOptions(activity).layout);
        assertEquals(before.surfaceStyle, currentOptions(activity).surfaceStyle);
        assertEquals(before.effectiveVisibleMeters(),
                currentOptions(activity).effectiveVisibleMeters());

        dialog = openColorStyleChoices(activity);
        assertEquals(1, dialog.getListView().getCheckedItemPosition());
        dialog.getListView().performItemClick(null, 2, 2);
        assertEquals(WidgetOptions.COLOR_CLASSIC, currentOptions(activity).colorStyle);
        assertNotNull(findText(styleRow, "经典"));
    }

    @Test
    public void colorStyleSavesOnlyThisWidgetAndCancelKeepsTheSavedChoice() throws Exception {
        bindProvider(CodexUsageWidget.class, 180);
        AppPreferences.saveWidgetOptions(app, WIDGET_ID,
                WidgetOptions.defaults().withColorStyle(WidgetOptions.COLOR_CLASSIC));
        AppPreferences.saveWidgetOptions(app, 43,
                WidgetOptions.defaults().withColorStyle(WidgetOptions.COLOR_CLASSIC));
        WidgetConfigActivity activity = openEditor();
        AlertDialog dialog = openColorStyleChoices(activity);
        assertEquals(2, dialog.getListView().getCheckedItemPosition());
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        assertEquals(WidgetOptions.COLOR_CLASSIC, currentOptions(activity).colorStyle);

        dialog = openColorStyleChoices(activity);
        dialog.getListView().performItemClick(null, 1, 1);
        assertEquals(WidgetOptions.COLOR_CLASSIC,
                AppPreferences.loadWidgetOptions(app, WIDGET_ID).colorStyle);
        activity.findViewById(R.id.config_save).performClick();
        assertEquals(WidgetOptions.COLOR_NATIVE,
                AppPreferences.loadWidgetOptions(app, WIDGET_ID).colorStyle);
        assertEquals(WidgetOptions.COLOR_CLASSIC,
                AppPreferences.loadWidgetOptions(app, 43).colorStyle);
        assertEquals(WidgetOptions.COLOR_NATIVE, currentOptions(openEditor()).colorStyle);

        WidgetConfigActivity canceled = openEditor();
        dialog = openColorStyleChoices(canceled);
        dialog.getListView().performItemClick(null, 2, 2);
        canceled.findViewById(R.id.config_cancel).performClick();
        assertEquals(WidgetOptions.COLOR_NATIVE,
                AppPreferences.loadWidgetOptions(app, WIDGET_ID).colorStyle);
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    public void recreationKeepsTheUnsavedColorChoiceUntilSaveOrCancel() throws Exception {
        bindProvider(CodexUsageWidget.class, 180);
        for (boolean save : new boolean[] {true, false}) {
            AppPreferences.saveWidgetOptions(app, WIDGET_ID,
                    WidgetOptions.defaults().withColorStyle(WidgetOptions.COLOR_CLASSIC));
            WidgetConfigActivity activity = openEditor();
            AlertDialog dialog = openColorStyleChoices(activity);
            dialog.getListView().performItemClick(null, 1, 1);
            assertNativeColorDraft(activity);

            ActivityController<WidgetConfigActivity> original = activities.remove(
                    activities.size() - 1);
            Bundle state = new Bundle();
            original.saveInstanceState(state).pause().stop().destroy();
            ActivityController<WidgetConfigActivity> restored = Robolectric
                    .buildActivity(WidgetConfigActivity.class, new Intent(activity.getIntent()))
                    .create(state).start().resume().visible();
            activities.add(restored);
            activity = restored.get();
            assertNativeColorDraft(activity);
            assertEquals(WidgetOptions.COLOR_CLASSIC,
                    AppPreferences.loadWidgetOptions(app, WIDGET_ID).colorStyle);
            activity.findViewById(save ? R.id.config_save : R.id.config_cancel).performClick();
            assertEquals(save ? WidgetOptions.COLOR_NATIVE : WidgetOptions.COLOR_CLASSIC,
                    AppPreferences.loadWidgetOptions(app, WIDGET_ID).colorStyle);
        }
    }

    private static void assertNativeColorDraft(WidgetConfigActivity activity) throws Exception {
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(WidgetOptions.COLOR_NATIVE, currentOptions(activity).colorStyle);
        assertNotNull(findText(colorStyleRow(activity), "原生"));
        AlertDialog dialog = openColorStyleChoices(activity);
        assertEquals(1, dialog.getListView().getCheckedItemPosition());
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        ImageView shell = previewContainer(activity).getChildAt(1)
                .findViewById(R.id.md_surface);
        assertNotNull(shell);
        assertTrue(shell.getColorFilter() instanceof PorterDuffColorFilter);
        assertEquals(activity.getColor(R.color.widget_material_surface),
                org.robolectric.Shadows.shadowOf(
                        (PorterDuffColorFilter) shell.getColorFilter()).getColor());
    }

    private static View colorStyleRow(WidgetConfigActivity activity) throws Exception {
        Field field = WidgetConfigActivity.class.getDeclaredField("colorStyleRow");
        field.setAccessible(true);
        return (View) field.get(activity);
    }

    private static FrameLayout previewContainer(WidgetConfigActivity activity) throws Exception {
        Field field = WidgetConfigActivity.class.getDeclaredField("previewContainer");
        field.setAccessible(true);
        return (FrameLayout) field.get(activity);
    }

    private static AlertDialog openColorStyleChoices(WidgetConfigActivity activity)
            throws Exception {
        View target = colorStyleRow(activity).findViewById(
                dev.oneuiproject.oneui.design.R.id.cardview_container);
        assertNotNull(target);
        assertTrue(target.performClick());
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        return dialog;
    }

    private AppWidgetManager bindProvider(Class<?> type, int height) {
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        info.provider = new ComponentName(app, type);
        org.robolectric.Shadows.shadowOf(manager).addInstalledProvider(info);
        org.robolectric.Shadows.shadowOf(manager).bindAppWidgetId(WIDGET_ID, info.provider);
        assertEquals(info.provider, manager.getAppWidgetInfo(WIDGET_ID).provider);
        Bundle size = new Bundle();
        size.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180);
        size.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height);
        manager.updateAppWidgetOptions(WIDGET_ID, size);
        return manager;
    }

    private static TextView rowSummary(WidgetConfigActivity activity, int position)
            throws Exception {
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
        RecyclerView list = findWindowList(activity.findViewById(android.R.id.content));
        list.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1400, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 800, 1400);
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
        assertNotNull(holder);
        Field summary = holder.getClass().getDeclaredField("summary");
        summary.setAccessible(true);
        return (TextView) summary.get(holder);
    }

    private static TextView findText(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().equals(text)) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView found = findText(group.getChildAt(index), text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
