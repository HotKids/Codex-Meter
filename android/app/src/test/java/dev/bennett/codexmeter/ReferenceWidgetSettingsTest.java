package dev.bennett.codexmeter;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Spinner;
import androidx.recyclerview.widget.RecyclerView;
import dev.oneuiproject.oneui.widget.RadioItemViewGroup;
import java.io.File;
import java.io.FileOutputStream;
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
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;

/** Real editor interactions, persistence, lifecycle and native preview regressions. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = QuotaCardsTest.TestApp.class, qualifiers = "zh-rCN-w411dp-h891dp-mdpi",
        shadows = ReferenceWidgetSettingsTest.Account.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReferenceWidgetSettingsTest {
    @Implements(SecureTokenStore.class)
    public static class Account {
        static String id = "account-a";
        @Implementation protected static AuthTokens load(Context context) {
            return new AuthTokens("test-only", "test-only", "", Long.MAX_VALUE, id, "");
        }
        @Implementation protected static boolean isSignedIn(Context context) { return true; }
    }
    private Context context;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        Account.id = "account-a";
        ReferenceWidgetPreferences.preferences(context).edit().clear().commit();
        WidgetUsageStore.prefs(context).edit().clear().commit();
        context.getSharedPreferences("codex_meter_home_widget_refresh_v1", 0).edit().clear().commit();
        AppPreferences.saveSnapshot(context, weekly());
    }

    private UsageSnapshot weekly() {
        return new UsageSnapshot("pro", true, false, null,
                new UsageWindow(67, 604800, 0, (System.currentTimeMillis() + 3 * 86400000L) / 1000),
                System.currentTimeMillis());
    }

    private ActivityController<WidgetConfigActivity> editor() {
        return editor(42);
    }

    private ActivityController<WidgetConfigActivity> editor(int widgetId) {
        return Robolectric.buildActivity(WidgetConfigActivity.class,
                new Intent(context, WidgetConfigActivity.class)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)).setup();
    }

    @Test public void weeklyOnlyAccountOffersAllBuiltinsAndReferencePickers() {
        try (var controller = editor()) {
            WidgetConfigActivity activity = controller.get();
            assertEquals(List.of("five_hour", "weekly", "next_reset", "reset_credits"), order(activity));
            assertEquals(2, spinner(activity, "referenceStyleSpinner").getCount());
            assertEquals(5, spinner(activity, "refreshSpinner").getCount());
            assertEquals(3, spinner(activity, "previewSpinner").getCount());
            choose(activity, "referenceStyleRow", 1);
            assertEquals(1, spinner(activity, "referenceStyleSpinner").getSelectedItemPosition());
            assertNull(findText(activity.findViewById(R.id.widget_settings_content), "显示百分号"));
            assertNull(findText(activity.findViewById(R.id.widget_settings_content), "已用百分比"));
        }
    }

    @Test public void missingSnapshotStillOffersAllBuiltinMeters() {
        ReferenceWidgetPreferences.preferences(context).edit().remove("last_snapshot").commit();
        try (var controller = editor()) {
            assertEquals(List.of("five_hour", "weekly", "next_reset", "reset_credits"), order(controller.get()));
            assertEquals(2, spinner(controller.get(), "referenceStyleSpinner").getCount());
        }
    }

    @Test public void sourceChoicesSaveForOtherWidgetsWithoutChangingAppRefreshPreferences() {
        AppPreferences.saveWidgetOptions(context, 43, WidgetOptions.defaults().withVisibleMeters("five_hour"));
        var prefs = ReferenceWidgetPreferences.preferences(context);
        prefs.edit().putInt("refresh_minutes", 60).putBoolean("automatic_refresh", true).commit();
        try (var controller = editor()) {
            WidgetConfigActivity activity = controller.get();
            choose(activity, "referenceStyleRow", 1);
            choose(activity, "refreshRow", 0);
            activity.findViewById(R.id.config_save).performClick();
        }
        assertEquals("clear", AppPreferences.loadWidgetOptions(context, 43).referenceStyle);
        assertEquals(0, ReferenceWidgetPreferences.refreshMinutes(context));
        assertEquals(60, prefs.getInt("refresh_minutes", 0));
        assertTrue(prefs.getBoolean("automatic_refresh", false));
    }

    @Test public void appearanceSaveKeepsTemporarilyUnavailableSavedWindowAndItReturns() {
        AppPreferences.saveWidgetOptions(context, 42, WidgetOptions.defaults()
                .withVisibleMeters("limit:spark:primary,weekly"));
        try (var controller = editor()) {
            WidgetConfigActivity activity = controller.get();
            assertEquals(5, list(activity).getAdapter().getItemCount());
            assertTrue(order(activity).contains("limit:spark:primary"));
            choose(activity, "referenceStyleRow", 1);
            activity.findViewById(R.id.config_save).performClick();
        }
        assertEquals("limit:spark:primary,weekly", AppPreferences.loadWidgetOptions(context, 42).visibleMeters);
        AppPreferences.saveSnapshot(context, QuotaCardsTest.multiWindowSnapshot());
        assertEquals(List.of("limit:spark:primary", "weekly"), QuotaCardOptions.effective(
                AppPreferences.loadWidgetOptions(context, 43).visibleMeters, AppPreferences.loadSnapshot(context)));
    }

    @Test public void liveSnapshotChangesRefreshAllCandidateRowsAndKeepUnsavedChoice() {
        try (var controller = editor()) {
            WidgetConfigActivity activity = controller.get();
            assertEquals(4, list(activity).getAdapter().getItemCount());
            AppPreferences.saveSnapshot(context, QuotaCardsTest.multiWindowSnapshot());
            shadowOf(Looper.getMainLooper()).idle();
            layoutRows(activity);
            assertEquals(7, list(activity).getAdapter().getItemCount());
            toggle(activity, "limit:spark:primary").setChecked(true);
            AppPreferences.saveSnapshot(context, weekly());
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(5, list(activity).getAdapter().getItemCount());
            assertTrue(order(activity).contains("limit:spark:primary"));
            AppPreferences.saveSnapshot(context, QuotaCardsTest.multiWindowSnapshot());
            shadowOf(Looper.getMainLooper()).idle();
            layoutRows(activity);
            assertTrue(toggle(activity, "limit:spark:primary").isChecked());
            activity.findViewById(R.id.config_save).performClick();
            assertTrue(AppPreferences.loadWidgetOptions(context, 42).visibleMeters.contains("limit:spark:primary"));
        }
    }

    @Test public void previewSizeDoesNotResizeLauncherOrPersistAPreviewFamily() throws Exception {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int id = shadowOf(manager).createWidget(CodexUsageWidget.class, R.layout.widget_quota_cards);
        Bundle actual = new Bundle();
        actual.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 136);
        actual.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 196);
        manager.updateAppWidgetOptions(id, actual);
        try (var controller = editor(id)) {
            WidgetConfigActivity activity = controller.get();
            choose(activity, "previewRow", 2);
            Bundle preview = ReflectionHelpers.callInstanceMethod(activity, "previewSize");
            assertEquals(338, preview.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));
            assertEquals(136, AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));
            capture(activity, "reference-editor-medium-color");
            choose(activity, "referenceStyleRow", 1);
            capture(activity, "reference-editor-medium-clear");
            activity.findViewById(R.id.config_save).performClick();
        }
        assertTrue(ReferenceWidgetPreferences.preferences(context).getAll().keySet().stream()
                .noneMatch(key -> key.contains("preview_family")));
    }

    @Test public void cancelLeavesReferenceSettingsAndWindowSelectionUntouched() {
        AppPreferences.saveWidgetOptions(context, 42, WidgetOptions.defaults().withVisibleMeters("weekly"));
        try (var controller = editor()) {
            layoutRows(controller.get());
            toggle(controller.get(), "five_hour").setChecked(true);
            choose(controller.get(), "referenceStyleRow", 1);
            choose(controller.get(), "refreshRow", 0);
            controller.get().findViewById(R.id.config_cancel).performClick();
        }
        assertEquals("color", AppPreferences.loadWidgetOptions(context, 42).referenceStyle);
        assertEquals("weekly", AppPreferences.loadWidgetOptions(context, 42).visibleMeters);
        assertEquals(30, ReferenceWidgetPreferences.refreshMinutes(context));
    }

    @Test public void recreationKeepsUnsavedWindowAndReferenceSettings() {
        AppPreferences.saveSnapshot(context, QuotaCardsTest.multiWindowSnapshot());
        try (var controller = editor()) {
            WidgetConfigActivity activity = controller.get();
            layoutRows(activity);
            toggle(activity, "limit:spark:primary").setChecked(true);
            choose(activity, "referenceStyleRow", 1);
            choose(activity, "refreshRow", 1);
            choose(activity, "previewRow", 2);
            controller.recreate();
            WidgetConfigActivity restored = controller.get();
            assertEquals(1, spinner(restored, "referenceStyleSpinner").getSelectedItemPosition());
            assertEquals(1, spinner(restored, "refreshSpinner").getSelectedItemPosition());
            assertEquals(2, spinner(restored, "previewSpinner").getSelectedItemPosition());
            layoutRows(restored);
            assertTrue(toggle(restored, "limit:spark:primary").isChecked());
            restored.findViewById(R.id.config_save).performClick();
            assertEquals(5, ReferenceWidgetPreferences.refreshMinutes(context));
        }
    }

    @Test public void sharedWindowChoicesAreAccountScopedWhileChromeIsGlobal() {
        ReferenceWidgetPreferences.save(context, 42, WidgetOptions.defaults().withVisibleMeters("weekly"), 15);
        Account.id = "account-b";
        ReferenceWidgetPreferences.save(context, 43, WidgetOptions.defaults().withVisibleMeters("five_hour")
                .withReferenceStyle("clear"), 30);
        assertEquals("five_hour", AppPreferences.loadWidgetOptions(context, 44).visibleMeters);
        Account.id = "account-a";
        assertEquals("weekly", AppPreferences.loadWidgetOptions(context, 44).visibleMeters);
        assertEquals("clear", AppPreferences.loadWidgetOptions(context, 44).referenceStyle);
    }

    @Test public void legacyMigrationSurvivesSavingWhileCacheStillHasOldKeys() throws Exception {
        var prefs = ReferenceWidgetPreferences.preferences(context);
        prefs.edit().putInt("widget_42_schema_version", 3).putString("widget_42_style", "quota_cards")
                .putString("widget_42_visible_meters", "limit:spark-0:primary,weekly").commit();
        UsageSnapshot old = new UsageSnapshot("pro", true, false, null, weekly().weekly,
                List.of(new UsageLimit("spark-0", "Spark", "spark", true, false,
                        new UsageWindow(20, 18000, 0, 1800000000L), null)), null, 0, System.currentTimeMillis());
        AppPreferences.saveSnapshot(context, old);
        try (var controller = editor()) {
            choose(controller.get(), "referenceStyleRow", 1);
            controller.get().findViewById(R.id.config_save).performClick();
        }
        assertEquals("limit:spark-0:primary,weekly", AppPreferences.loadWidgetOptions(context, 43).visibleMeters);
        AppPreferences.saveSnapshot(context, QuotaCardsTest.multiWindowSnapshot());
        assertEquals("limit:spark:primary,weekly", AppPreferences.loadWidgetOptions(context, 43).visibleMeters);
        assertEquals("limit:spark:primary,weekly", AppPreferences.loadWidgetOptions(context, 42).visibleMeters);
    }

    @Test public void rejectedFinalWindowToggleDoesNotOverwriteAMissingSelection() {
        AppPreferences.saveWidgetOptions(context, 42, WidgetOptions.defaults().withVisibleMeters("limit:spark:primary"));
        try (var controller = editor()) {
            layoutRows(controller.get());
            toggle(controller.get(), 0).setChecked(false);
            assertTrue(toggle(controller.get(), 0).isChecked());
            controller.get().findViewById(R.id.config_save).performClick();
        }
        assertEquals("limit:spark:primary", AppPreferences.loadWidgetOptions(context, 42).visibleMeters);
    }

    @Test public void changingAnotherWindowRetainsMigrationForSelectedLegacyWindow() {
        var prefs = ReferenceWidgetPreferences.preferences(context);
        prefs.edit().putInt("widget_42_schema_version", 3).putString("widget_42_style", "quota_cards")
                .putString("widget_42_visible_meters", "limit:spark-0:primary,weekly").commit();
        AppPreferences.saveSnapshot(context, oldSpark());
        try (var controller = editor()) {
            layoutRows(controller.get());
            toggle(controller.get(), "weekly").setChecked(false);
            controller.get().findViewById(R.id.config_save).performClick();
        }
        AppPreferences.saveSnapshot(context, QuotaCardsTest.multiWindowSnapshot());
        assertEquals("limit:spark:primary", AppPreferences.loadWidgetOptions(context, 43).visibleMeters);
    }

    @Test public void newWidgetInheritsPendingMigrationFromDefaults() {
        var prefs = ReferenceWidgetPreferences.preferences(context);
        prefs.edit().putInt("default_schema_version", 3).putString("default_style", "quota_cards")
                .putString("default_visible_meters", "limit:spark-0:primary,weekly").commit();
        AppPreferences.saveSnapshot(context, oldSpark());
        try (var controller = editor()) {
            choose(controller.get(), "referenceStyleRow", 1);
            controller.get().findViewById(R.id.config_save).performClick();
        }
        AppPreferences.saveSnapshot(context, QuotaCardsTest.multiWindowSnapshot());
        assertEquals("limit:spark:primary,weekly", AppPreferences.loadWidgetOptions(context, 43).visibleMeters);
    }

    @Test public void launcherRestoreKeepsPendingMigrationWithItsNewWidgetId() {
        var prefs = ReferenceWidgetPreferences.preferences(context);
        prefs.edit().putInt("widget_42_schema_version", 3).putString("widget_42_style", "quota_cards")
                .putString("widget_42_visible_meters", "limit:spark-0:primary,weekly").commit();
        AppPreferences.saveSnapshot(context, oldSpark());
        new CodexUsageWidget().onRestored(context, new int[]{42}, new int[]{44});
        AppPreferences.saveSnapshot(context, QuotaCardsTest.multiWindowSnapshot());
        assertEquals("limit:spark:primary,weekly", AppPreferences.loadWidgetOptions(context, 44).visibleMeters);
    }

    private UsageSnapshot oldSpark() {
        return new UsageSnapshot("pro", true, false, null, weekly().weekly,
                List.of(new UsageLimit("spark-0", "Spark", "spark", true, false,
                        new UsageWindow(20, 18000, 0, 1800000000L), null)), null, 0, System.currentTimeMillis());
    }

    private RecyclerView list(WidgetConfigActivity activity) { return ReflectionHelpers.getField(activity, "metersList"); }
    private List<String> order(WidgetConfigActivity activity) { return ReflectionHelpers.getField(activity, "meterOrder"); }
    private Spinner spinner(WidgetConfigActivity activity, String field) { return ReflectionHelpers.getField(activity, field); }
    private void layoutRows(WidgetConfigActivity activity) {
        RecyclerView list = list(activity);
        list.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 400, 800);
    }
    private CompoundButton toggle(WidgetConfigActivity activity, int index) {
        var holder = list(activity).findViewHolderForAdapterPosition(index);
        assertNotNull(holder);
        return ReflectionHelpers.getField(holder, "toggle");
    }
    private CompoundButton toggle(WidgetConfigActivity activity, String key) {
        int index = order(activity).indexOf(key);
        assertTrue("Missing editor row: " + key, index >= 0);
        return toggle(activity, index);
    }
    private void choose(WidgetConfigActivity activity, String field, int position) {
        View row = ReflectionHelpers.getField(activity, field);
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(clickRow(row));
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        RadioItemViewGroup group = findGroup(dialog.getWindow().getDecorView());
        assertNotNull(group);
        group.check(group.getChildAt(position).getId());
        shadowOf(Looper.getMainLooper()).idle();
    }
    private boolean clickRow(View view) {
        if (view.hasOnClickListeners() && view.performClick()) return true;
        if (view instanceof ViewGroup) {
            var group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (clickRow(group.getChildAt(i))) return true;
        }
        return false;
    }
    private RadioItemViewGroup findGroup(View view) {
        if (view instanceof RadioItemViewGroup) return (RadioItemViewGroup) view;
        if (view instanceof ViewGroup) {
            var group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                var found = findGroup(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
    private View findText(View view, String text) {
        if (view instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            var group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                var found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
    private void capture(WidgetConfigActivity activity, String name) throws Exception {
        View content = activity.findViewById(R.id.widget_settings_content);
        content.measure(View.MeasureSpec.makeMeasureSpec(411, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        content.layout(0, 0, 411, content.getMeasuredHeight());
        shadowOf(Looper.getMainLooper()).idle();
        Bitmap bitmap = Bitmap.createBitmap(411, content.getHeight(), Bitmap.Config.ARGB_8888);
        content.draw(new Canvas(bitmap));
        File folder = new File("build/reports/widget-previews");
        assertTrue(folder.exists() || folder.mkdirs());
        try (FileOutputStream stream = new FileOutputStream(new File(folder, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
    }
}
