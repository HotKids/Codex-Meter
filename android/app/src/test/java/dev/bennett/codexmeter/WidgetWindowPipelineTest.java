package dev.bennett.codexmeter;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Looper;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import org.json.JSONArray;
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
import org.robolectric.util.ReflectionHelpers;

/** Offline end-to-end checks of complete API windows through cache, editor and RemoteViews. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = QuotaCardsTest.TestApp.class,
        qualifiers = "zh-rCN-w411dp-h891dp-mdpi",
        shadows = ReferenceWidgetSettingsTest.Account.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetWindowPipelineTest {
    private static final String WEEKLY = "codex:weekly";
    private static final String MONTHLY = "codex:monthly";
    private static final String SPARK = "extra:codex-spark:weekly";
    private static final List<String> THREE_IDS = List.of(WEEKLY, MONTHLY, SPARK);
    private static final List<String> BUILTINS = List.of(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY,
            WidgetMeters.NEXT_RESET, WidgetMeters.RESET_CREDITS);
    private Context context;
    private long now;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        now = System.currentTimeMillis();
        ReferenceWidgetSettingsTest.Account.id = "account-a";
        ReferenceWidgetPreferences.preferences(context).edit().clear().commit();
        WidgetUsageStore.prefs(context).edit().clear().commit();
        context.getSharedPreferences("codex_meter_home_widget_refresh_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSystemService(JobScheduler.class).cancelAll();
    }

    @Test public void completeWindowsSurviveCacheEditorSaveAndThirdRenderedCard() throws Exception {
        // The phone's compact snapshot deliberately contains only weekly usage. The complete
        // widget cache must preserve monthly and Spark without changing that phone snapshot.
        saveLegacyWeekly();
        assertTrue(WidgetUsageStore.save(context,
                WidgetUsageParser.parse(response(true, true, 85d), now)));
        assertEquals(THREE_IDS, ids(WidgetUsageStore.load(context)));
        AppPreferences.saveWidgetOptions(context, 42,
                WidgetOptions.defaults().withVisibleMeters(WEEKLY));

        try (var controller = editor(42)) {
            WidgetConfigActivity activity = controller.get();
            layoutRows(activity);
            assertEquals(6, list(activity).getAdapter().getItemCount());
            assertCatalog(activity, MONTHLY, SPARK);
            assertEquals("每月", title(activity, position(activity, MONTHLY)));
            assertSparkTitle(title(activity, position(activity, SPARK)));
            toggle(activity, position(activity, MONTHLY)).setChecked(true);
            toggle(activity, position(activity, SPARK)).setChecked(true);
            assertTrue(toggle(activity, position(activity, WidgetMeters.WEEKLY)).isChecked());
            assertTrue(toggle(activity, position(activity, MONTHLY)).isChecked());
            assertTrue(toggle(activity, position(activity, SPARK)).isChecked());
            captureTestDataEditor(activity);
            assertTrue(activity.findViewById(R.id.config_save).performClick());
        }

        WidgetOptions saved = AppPreferences.loadWidgetOptions(context, 42);
        assertEquals(List.of(WidgetMeters.WEEKLY, MONTHLY, SPARK), WidgetMeters.parse(saved.visibleMeters));
        QuotaCardState state = QuotaCardState.load(context);
        assertNotNull(state.widgetSnapshot);
        assertEquals(THREE_IDS, ids(state.widgetSnapshot));
        View rendered = render(saved, state);
        assertEquals(View.VISIBLE, rendered.findViewById(R.id.quota_card_2).getVisibility());
        assertEquals(View.GONE, rendered.findViewById(R.id.quota_card_3).getVisibility());
        assertSparkTitle(((TextView) rendered.findViewById(R.id.quota_title_2)).getText().toString());
        assertEquals("15%", ((TextView) rendered.findViewById(R.id.quota_value_2)).getText().toString());
        assertNull(AppPreferences.loadSnapshot(context).monthly);
        assertTrue(AppPreferences.loadSnapshot(context).additionalLimits.isEmpty());
    }

    @Test public void fiveHourAliasesSurviveCacheEditorSelectionSaveAndTwoCardPreview() throws Exception {
        // An actual primary/secondary response contract, with the reference's inner aliases.
        // Keep the old phone cache weekly-only so a five-hour result must come from widget data.
        saveLegacyWeekly();
        String fiveHourId = "codex:primary_window";
        String weeklyId = "codex:secondary_window";
        List<String> expectedIds = List.of(fiveHourId, weeklyId);
        JSONObject primary = new JSONObject().put("usedPercent", 25d)
                .put("window_seconds", 18000)
                .put("reset_at", (now + 2 * 3_600_000L) / 1000L);
        JSONObject secondary = new JSONObject().put("used_percent", 37d)
                .put("limit_window_seconds", 604800)
                .put("reset_at", (now + 3 * 86_400_000L) / 1000L);
        String raw = new JSONObject().put("plan_type", "pro")
                .put("rate_limit", new JSONObject().put("primary_window", primary)
                        .put("secondary_window", secondary)).toString();
        assertTrue(WidgetUsageStore.save(context, WidgetUsageParser.parse(raw, now)));
        WidgetUsageSnapshot complete = WidgetUsageStore.load(context);
        assertEquals("pro", complete.planType);
        assertEquals(expectedIds, ids(complete));
        assertEquals("five_hour", complete.windows.get(0).kind);
        assertEquals(25d, complete.windows.get(0).usedPercent, 0.001);
        assertEquals(18000L, complete.windows.get(0).windowSeconds);
        assertEquals("weekly", complete.windows.get(1).kind);
        // Start from the user's existing weekly-only choice, rather than the default two rows.
        AppPreferences.saveWidgetOptions(context, 42,
                WidgetOptions.defaults().withVisibleMeters(WidgetMeters.WEEKLY));

        try (var controller = editor(42)) {
            WidgetConfigActivity activity = controller.get();
            layoutRows(activity);
            assertEquals(4, list(activity).getAdapter().getItemCount());
            assertCatalog(activity);
            assertFalse(toggle(activity, position(activity, WidgetMeters.FIVE_HOUR)).isChecked());
            assertTrue(toggle(activity, position(activity, WidgetMeters.WEEKLY)).isChecked());
            View weeklyOnly = render(AppPreferences.loadWidgetOptions(context, 42),
                    QuotaCardState.load(context));
            assertEquals("每周", ((TextView) weeklyOnly.findViewById(R.id.quota_title_0)).getText().toString());
            assertEquals(View.GONE, weeklyOnly.findViewById(R.id.quota_card_1).getVisibility());

            toggle(activity, position(activity, WidgetMeters.FIVE_HOUR)).setChecked(true);
            assertTrue(toggle(activity, position(activity, WidgetMeters.FIVE_HOUR)).isChecked());
            assertTrue(toggle(activity, position(activity, WidgetMeters.WEEKLY)).isChecked());
            captureTestDataEditor(activity, "widget-five-hour-windows.png", "5-hour + weekly / Pro TEST DATA");
            assertTrue(activity.findViewById(R.id.config_save).performClick());
        }

        WidgetOptions saved = AppPreferences.loadWidgetOptions(context, 42);
        assertEquals(List.of(WidgetMeters.WEEKLY, WidgetMeters.FIVE_HOUR), WidgetMeters.parse(saved.visibleMeters));
        View rendered = render(saved, QuotaCardState.load(context));
        assertEquals(View.VISIBLE, rendered.findViewById(R.id.quota_card_0).getVisibility());
        assertEquals(View.VISIBLE, rendered.findViewById(R.id.quota_card_1).getVisibility());
        assertEquals(View.GONE, rendered.findViewById(R.id.quota_card_2).getVisibility());
        assertEquals(View.GONE, rendered.findViewById(R.id.quota_card_3).getVisibility());
        assertEquals("每周", ((TextView) rendered.findViewById(R.id.quota_title_0)).getText().toString());
        assertEquals("5 小时", ((TextView) rendered.findViewById(R.id.quota_title_1)).getText().toString());
        assertEquals("63%", ((TextView) rendered.findViewById(R.id.quota_value_0)).getText().toString());
        assertEquals("75%", ((TextView) rendered.findViewById(R.id.quota_value_1)).getText().toString());
        assertNull(AppPreferences.loadSnapshot(context).fiveHour);
    }

    @Test public void completeCacheUpdatesAddRowsWhileEditorRemainsOpenAndPreserveUnsavedChoice()
            throws Exception {
        assertTrue(WidgetUsageStore.save(context,
                WidgetUsageParser.parse(response(false, false, 85d), now)));
        try (var controller = editor(42)) {
            WidgetConfigActivity activity = controller.get();
            assertEquals(4, list(activity).getAdapter().getItemCount());
            assertTrue(WidgetUsageStore.save(context,
                    WidgetUsageParser.parse(response(true, true, 85d), now + 1L)));
            shadowOf(Looper.getMainLooper()).idle();
            layoutRows(activity);
            assertSame(activity, controller.get());
            assertCatalog(activity, MONTHLY, SPARK);
            assertEquals(6, list(activity).getAdapter().getItemCount());
            toggle(activity, position(activity, MONTHLY)).setChecked(true);

            assertTrue(WidgetUsageStore.save(context,
                    WidgetUsageParser.parse(response(false, false, 85d), now + 2L)));
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(5, list(activity).getAdapter().getItemCount());
            assertCatalog(activity, MONTHLY);
            assertTrue(WidgetUsageStore.save(context,
                    WidgetUsageParser.parse(response(true, true, 85d), now + 3L)));
            shadowOf(Looper.getMainLooper()).idle();
            layoutRows(activity);
            assertTrue(toggle(activity, position(activity, MONTHLY)).isChecked());
            assertTrue(activity.findViewById(R.id.config_save).performClick());
        }
        assertTrue(WidgetMeters.parse(AppPreferences.loadWidgetOptions(context, 42).visibleMeters)
                .contains(MONTHLY));
    }

    @Test public void weeklyOnlyResponseOffersFourBuiltinChoicesAndMissingFiveHourRendersDash() throws Exception {
        assertTrue(WidgetUsageStore.save(context,
                WidgetUsageParser.parse(response(false, false, 85d), now)));
        assertEquals(List.of(WEEKLY), ids(WidgetUsageStore.load(context)));
        AppPreferences.saveWidgetOptions(context, 42,
                WidgetOptions.defaults().withVisibleMeters(WidgetMeters.WEEKLY));
        try (var controller = editor(42)) {
            WidgetConfigActivity activity = controller.get();
            layoutRows(activity);
            assertCatalog(activity);
            assertEquals(4, list(activity).getAdapter().getItemCount());
            assertFalse(toggle(activity, position(activity, WidgetMeters.FIVE_HOUR)).isChecked());
            toggle(activity, position(activity, WidgetMeters.FIVE_HOUR)).setChecked(true);
            assertTrue(toggle(activity, position(activity, WidgetMeters.FIVE_HOUR)).isChecked());
            captureTestDataEditor(activity, "widget-upstream-editor.png", "Weekly-only response / all 4 upstream choices");
            assertTrue(activity.findViewById(R.id.config_save).performClick());
        }
        WidgetOptions saved = AppPreferences.loadWidgetOptions(context, 42);
        assertEquals(List.of(WidgetMeters.WEEKLY, WidgetMeters.FIVE_HOUR), WidgetMeters.parse(saved.visibleMeters));
        try (var controller = editor(42)) {
            layoutRows(controller.get());
            assertCatalog(controller.get());
            assertTrue(toggle(controller.get(), position(controller.get(), WidgetMeters.FIVE_HOUR)).isChecked());
        }
        View rendered = render(saved, QuotaCardState.load(context));
        assertEquals(View.VISIBLE, rendered.findViewById(R.id.quota_card_1).getVisibility());
        assertEquals("5 小时", ((TextView) rendered.findViewById(R.id.quota_title_1)).getText().toString());
        assertEquals("—", ((TextView) rendered.findViewById(R.id.quota_value_1)).getText().toString());
        assertEquals(List.of(WEEKLY), ids(WidgetUsageStore.load(context)));
    }

    @Test public void noResponseStillOffersFourBuiltinChoicesWithoutInventingCacheData() {
        assertNull(WidgetUsageStore.load(context));
        assertNull(AppPreferences.loadSnapshot(context));
        AppPreferences.saveWidgetOptions(context, 42, WidgetOptions.defaults());
        try (var controller = editor(42)) {
            WidgetConfigActivity activity = controller.get();
            layoutRows(activity);
            assertCatalog(activity);
            assertEquals(4, list(activity).getAdapter().getItemCount());
        }
        View rendered = render(WidgetOptions.defaults(), QuotaCardState.load(context));
        assertEquals(View.VISIBLE, rendered.findViewById(R.id.quota_card_0).getVisibility());
        assertEquals(View.VISIBLE, rendered.findViewById(R.id.quota_card_1).getVisibility());
        assertEquals("—", ((TextView) rendered.findViewById(R.id.quota_value_0)).getText().toString());
        assertEquals("—", ((TextView) rendered.findViewById(R.id.quota_value_1)).getText().toString());
        assertNull(WidgetUsageStore.load(context));
    }

    @Test public void realDragCallbackPreservesOrderThroughSaveReopenAndSmallMediumRendering()
            throws Exception {
        JSONObject payload = new JSONObject(response(false, false, 85d)).put("rate_limit_reset_credits",
                new JSONObject().put("available_count", 2));
        assertTrue(WidgetUsageStore.save(context, WidgetUsageParser.parse(payload.toString(), now)));
        AppPreferences.saveWidgetOptions(context, 42,
                WidgetOptions.defaults().withVisibleMeters(WidgetMeters.serialize(BUILTINS)));
        List<String> dragged = List.of(WidgetMeters.WEEKLY, WidgetMeters.FIVE_HOUR,
                WidgetMeters.NEXT_RESET, WidgetMeters.RESET_CREDITS);
        try (var controller = editor(42)) {
            WidgetConfigActivity activity = controller.get();
            layoutRows(activity);
            assertEquals(BUILTINS, order(activity));
            ItemTouchHelper helper = ReflectionHelpers.getField(activity, "meterTouchHelper");
            assertNotNull("The real editor must attach its drag helper", helper);
            ItemTouchHelper.Callback callback = ReflectionHelpers.getField(helper, "mCallback");
            assertTrue(callback.onMove(list(activity), holder(activity, 1), holder(activity, 0)));
            layoutRows(activity);
            assertEquals(dragged, order(activity));
            assertTrue(activity.findViewById(R.id.config_save).performClick());
        }
        WidgetOptions saved = AppPreferences.loadWidgetOptions(context, 42);
        assertEquals(dragged, WidgetMeters.parse(saved.visibleMeters));
        try (var controller = editor(42)) {
            WidgetConfigActivity activity = controller.get();
            layoutRows(activity);
            assertEquals(dragged, order(activity));
            for (int index = 0; index < BUILTINS.size(); index++) assertTrue(toggle(activity, index).isChecked());
        }
        QuotaCardState state = QuotaCardState.load(context);
        View small = render(saved, state, 158, 158);
        assertEquals("每周", ((TextView) small.findViewById(R.id.quota_title_0)).getText().toString());
        assertEquals("5 小时", ((TextView) small.findViewById(R.id.quota_title_1)).getText().toString());
        assertEquals("63%", ((TextView) small.findViewById(R.id.quota_value_0)).getText().toString());
        assertEquals("—", ((TextView) small.findViewById(R.id.quota_value_1)).getText().toString());
        assertEquals(View.GONE, small.findViewById(R.id.quota_card_2).getVisibility());
        View medium = render(saved, state);
        for (int card : new int[]{R.id.quota_card_0, R.id.quota_card_1, R.id.quota_card_2, R.id.quota_card_3})
            assertEquals(View.VISIBLE, medium.findViewById(card).getVisibility());
        assertEquals("63%", ((TextView) medium.findViewById(R.id.quota_inline_value_0)).getText().toString());
        assertEquals("—", ((TextView) medium.findViewById(R.id.quota_inline_value_1)).getText().toString());
        String nextReset = ((TextView) medium.findViewById(R.id.quota_inline_value_2)).getText().toString();
        assertFalse(nextReset.isEmpty());
        assertNotEquals("—", nextReset);
        assertFalse(nextReset.contains("%"));
        assertEquals("2", ((TextView) medium.findViewById(R.id.quota_inline_value_3)).getText().toString());
        assertEquals(List.of(WEEKLY), ids(WidgetUsageStore.load(context)));
    }

    @Test public void resetOnlySparkWindowSurvivesTheCompletePipelineWithoutInventingPercent()
            throws Exception {
        assertTrue(WidgetUsageStore.save(context,
                WidgetUsageParser.parse(response(true, true, null), now)));
        assertEquals(THREE_IDS, ids(WidgetUsageStore.load(context)));
        assertNull(WidgetUsageStore.load(context).windows.get(2).usedPercent);
        try (var controller = editor(42)) {
            WidgetConfigActivity activity = controller.get();
            layoutRows(activity);
            assertEquals(6, list(activity).getAdapter().getItemCount());
            assertSparkTitle(title(activity, position(activity, SPARK)));
        }
        WidgetOptions onlySpark = WidgetOptions.defaults().withVisibleMeters(SPARK);
        View rendered = render(onlySpark, QuotaCardState.load(context));
        assertSparkTitle(((TextView) rendered.findViewById(R.id.quota_title_0)).getText().toString());
        assertEquals("—", ((TextView) rendered.findViewById(R.id.quota_hero_value)).getText().toString());
    }

    @Test public void fullSavedSelectionKeepsExtraOrderWhileSizesRenderOnlyLeadingTwoOrFour()
            throws Exception {
        assertTrue(WidgetUsageStore.save(context,
                WidgetUsageParser.parse(response(true, true, 85d), now)));
        List<String> selected = List.of(SPARK, WidgetMeters.WEEKLY, MONTHLY,
                WidgetMeters.FIVE_HOUR, WidgetMeters.NEXT_RESET, WidgetMeters.RESET_CREDITS);
        AppPreferences.saveWidgetOptions(context, 42,
                WidgetOptions.defaults().withVisibleMeters(WidgetMeters.serialize(selected)));
        try (var controller = editor(42)) {
            WidgetConfigActivity activity = controller.get();
            layoutRows(activity);
            assertEquals(selected, order(activity));
            assertCatalog(activity, MONTHLY, SPARK);
            for (int index = 0; index < selected.size(); index++) assertTrue(toggle(activity, index).isChecked());
            assertTrue(activity.findViewById(R.id.config_save).performClick());
        }
        WidgetOptions saved = AppPreferences.loadWidgetOptions(context, 42);
        assertEquals(selected, WidgetMeters.parse(saved.visibleMeters));
        try (var controller = editor(42)) {
            layoutRows(controller.get());
            assertEquals(selected, order(controller.get()));
        }
        QuotaCardState state = QuotaCardState.load(context);
        View small = render(saved, state, 158, 158);
        assertSparkTitle(((TextView) small.findViewById(R.id.quota_title_0)).getText().toString());
        assertEquals("每周", ((TextView) small.findViewById(R.id.quota_title_1)).getText().toString());
        assertEquals("15%", ((TextView) small.findViewById(R.id.quota_value_0)).getText().toString());
        assertEquals("63%", ((TextView) small.findViewById(R.id.quota_value_1)).getText().toString());
        assertEquals(View.GONE, small.findViewById(R.id.quota_card_2).getVisibility());
        View medium = render(saved, state);
        assertSparkTitle(((TextView) medium.findViewById(R.id.quota_inline_title_0)).getText().toString());
        assertEquals("每周", ((TextView) medium.findViewById(R.id.quota_inline_title_1)).getText().toString());
        assertEquals("每月", ((TextView) medium.findViewById(R.id.quota_inline_title_2)).getText().toString());
        assertEquals("5 小时", ((TextView) medium.findViewById(R.id.quota_inline_title_3)).getText().toString());
        assertEquals("15%", ((TextView) medium.findViewById(R.id.quota_inline_value_0)).getText().toString());
        assertEquals("38%", ((TextView) medium.findViewById(R.id.quota_inline_value_2)).getText().toString());
        assertEquals("—", ((TextView) medium.findViewById(R.id.quota_inline_value_3)).getText().toString());
        assertEquals(THREE_IDS, ids(WidgetUsageStore.load(context)));
    }

    @Test public void openingWithoutCompleteCacheRequestsInitialFetchDespiteFreshPhoneCache() {
        saveLegacyWeekly();
        assertNull(WidgetUsageStore.load(context));
        int widgetId = shadowOf(AppWidgetManager.getInstance(context))
                .createWidget(CodexUsageWidget.class, R.layout.widget_rings);
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        scheduler.cancelAll();
        try (var controller = editor(widgetId)) {
            JobInfo initial = scheduler.getAllPendingJobs().stream()
                    .filter(job -> WidgetRefreshJobService.class.getName()
                            .equals(job.getService().getClassName())
                            && job.getExtras().getBoolean(WidgetRefreshScheduler.EXTRA_INITIAL))
                    .findFirst().orElse(null);
            assertNotNull("Opening the editor must request a complete widget response", initial);
            assertTrue(WidgetRefreshScheduler.shouldFetchInitial(context, System.currentTimeMillis()));
            assertNull(WidgetUsageStore.load(context));
        }
    }

    private String response(boolean monthly, boolean spark, Double sparkPercent) throws Exception {
        JSONObject rateLimit = new JSONObject().put("weekly", window(37d, 604800));
        if (monthly) rateLimit.put("monthly", window(62d, 2592000));
        JSONObject payload = new JSONObject().put("plan_type", "pro").put("rate_limit", rateLimit);
        if (spark) payload.put("additional_rate_limits", new JSONArray().put(new JSONObject()
                .put("limit_name", "Codex Spark").put("metered_feature", "codex-spark")
                .put("rate_limit", new JSONObject().put("weekly", window(sparkPercent, 604800)))));
        return payload.toString();
    }

    private JSONObject window(Double percent, int seconds) throws Exception {
        return new JSONObject().put("used_percent", percent == null ? JSONObject.NULL : percent)
                .put("window_seconds", seconds).put("reset_at", (now + 3 * 86_400_000L) / 1000L);
    }

    private void saveLegacyWeekly() {
        assertTrue(AppPreferences.saveSnapshot(context, new UsageSnapshot("pro", true, false,
                null, new UsageWindow(37, 604800, 0, (now + 3 * 86_400_000L) / 1000L), now)));
    }

    private ActivityController<WidgetConfigActivity> editor(int id) {
        return Robolectric.buildActivity(WidgetConfigActivity.class,
                new Intent(context, WidgetConfigActivity.class)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)).setup();
    }

    private List<String> ids(WidgetUsageSnapshot snapshot) {
        assertNotNull(snapshot);
        return snapshot.windows.stream().map(window -> window.id).toList();
    }

    private List<String> order(WidgetConfigActivity activity) {
        return ReflectionHelpers.getField(activity, "meterOrder");
    }

    private int position(WidgetConfigActivity activity, String key) {
        int position = order(activity).indexOf(key);
        assertTrue("The complete editor catalog must contain " + key, position >= 0);
        return position;
    }

    private void assertCatalog(WidgetConfigActivity activity, String... additional) {
        java.util.Set<String> expected = new java.util.LinkedHashSet<>(BUILTINS);
        expected.addAll(List.of(additional));
        assertEquals(expected, new java.util.LinkedHashSet<>(order(activity)));
        assertEquals("No duplicate ordinary aliases in the editor", expected.size(), order(activity).size());
    }

    private RecyclerView list(WidgetConfigActivity activity) {
        return ReflectionHelpers.getField(activity, "metersList");
    }

    private void layoutRows(WidgetConfigActivity activity) {
        shadowOf(Looper.getMainLooper()).idle();
        RecyclerView rows = list(activity);
        rows.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        rows.layout(0, 0, 400, 800);
    }

    private RecyclerView.ViewHolder holder(WidgetConfigActivity activity, int position) {
        RecyclerView.ViewHolder holder = list(activity).findViewHolderForAdapterPosition(position);
        assertNotNull("The real quota row must be laid out", holder);
        return holder;
    }

    private CompoundButton toggle(WidgetConfigActivity activity, int position) {
        return ReflectionHelpers.getField(holder(activity, position), "toggle");
    }

    private String title(WidgetConfigActivity activity, int position) {
        TextView title = ReflectionHelpers.getField(holder(activity, position), "title");
        return title.getText().toString();
    }

    private void assertSparkTitle(String title) {
        assertTrue("Expected a model-specific Codex Spark title: " + title, title.contains("Codex Spark"));
        assertTrue("Expected the weekly cadence in the Spark title: " + title, title.contains("每周"));
    }

    private View render(WidgetOptions options, QuotaCardState state) {
        return render(options, state, 338, 158);
    }

    private View render(WidgetOptions options, QuotaCardState state, int width, int height) {
        RemoteViews remote = QuotaCardRenderer.build(context, 42, options, width, height, state);
        View view = remote.apply(context, new FrameLayout(context));
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        return view;
    }

    private void captureTestDataEditor(WidgetConfigActivity activity) throws Exception {
        captureTestDataEditor(activity, "widget-complete-windows.png",
                "Weekly + monthly + Codex Spark weekly");
    }

    private void captureTestDataEditor(WidgetConfigActivity activity, String filename, String caption)
            throws Exception {
        View content = activity.findViewById(R.id.widget_settings_content);
        content.measure(View.MeasureSpec.makeMeasureSpec(411, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        content.layout(0, 0, 411, content.getMeasuredHeight());
        shadowOf(Looper.getMainLooper()).idle();
        int banner = 56;
        Bitmap bitmap = Bitmap.createBitmap(411, content.getHeight() + banner, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xffffe6a3);
        canvas.drawRect(0, 0, 411, banner, paint);
        paint.setColor(Color.BLACK);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(20);
        canvas.drawText("TEST DATA / OFFLINE FIXTURE", 14, 24, paint);
        paint.setTextSize(14);
        canvas.drawText(caption, 14, 45, paint);
        canvas.translate(0, banner);
        content.draw(canvas);
        File folder = new File("build/previews");
        assertTrue(folder.exists() || folder.mkdirs());
        try (FileOutputStream stream = new FileOutputStream(new File(folder, filename))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
    }
}
