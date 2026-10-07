package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.WidgetMeters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RotateDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RemoteViews;
import android.widget.TextView;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Refresh status checks apply the production RemoteViews with synthetic usage data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "zh-rCN-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MaterialCardStatusTest {
    private static final int WIDGET_ID = 73;

    @Test
    public void everyFailureUsesSyncProblemAndEitherACompleteStatusOrNoStatus() {
        for (boolean cached : new boolean[] {true, false}) {
            for (int width : new int[] {110, 140, 170, 190, 249, 250, 350, 430}) {
                View applied = build(failed(cached), width, meters());
                assertErrorIcon(applied, width);
                if (width >= 140) {
                    assertEquals("The plan stays whole where both labels fit", 0,
                            ((TextView) applied.findViewById(R.id.md_title))
                                    .getLayout().getEllipsisCount(0));
                }
                if (width >= 280) {
                    assertEquals("Status and refresh time share their type size",
                            ((TextView) applied.findViewById(R.id.md_updated)).getTextSize(),
                            status(applied).getTextSize(), 0f);
                }
            }
        }
    }

    @Test
    public void minimumWidthFailureMarkKeepsVisibleSpaceBesideTheProPlan() {
        View applied = build(failed(true), 110, meters());
        assertErrorIcon(applied, 110);
        TextView title = applied.findViewById(R.id.md_title);
        assertEquals("Pro 200", title.getText().toString());
        assertTrue("Only the title yields space to the failure icon at the minimum width",
                title.getLayout().getEllipsisCount(0) > 0);
    }

    @Test
    public void wideFailuresKeepTheirFullMessageEvenWithOneMeter() {
        for (int width : new int[] {250, 350, 430}) {
            for (List<String> keys : List.of(meters(), UsageCardFixtures.keys(WidgetMeters.WEEKLY))) {
                View applied = build(failed(true), width, keys);
                TextView status = status(applied);
                assertEquals(message(R.string.widget_card_refresh_failed), status.getText().toString());
                assertNull(status.getContentDescription());
                assertErrorIcon(applied, width);
            }
        }
    }

    @Test
    public void failedRefreshHidesTheClockAndSuccessfulReapplyRestoresIt() {
        Context context = RuntimeEnvironment.getApplication();
        UsageCardState ready = UsageCardFixtures.state(snapshot(), 0);
        View applied = build(ready, 350, meters());
        TextView updated = applied.findViewById(R.id.md_updated);
        assertEquals(View.VISIBLE, updated.getVisibility());
        remote(failed(true), 350, meters()).reapply(context, applied);
        assertEquals("A failed refresh must not retain the previous timestamp",
                View.GONE, updated.getVisibility());
        assertErrorIcon(applied, 350);
        remote(ready, 350, meters()).reapply(context, applied);
        assertEquals(View.VISIBLE, updated.getVisibility());
        assertNormalRefreshIcon(applied);
    }

    @Test
    public void everyNarrowNonnormalStatusHidesItsTextAndKeepsItsAccessibleRefreshState() {
        for (int width : new int[] {110, 170, 249}) {
            for (UsageCardState state : nonnormalStates()) {
                View applied = build(state, width, meters());
                assertStatus(applied, state, width);
                assertRefreshPresentation(applied, state);
                assertEquals(View.GONE, applied.findViewById(R.id.md_updated).getVisibility());
            }
        }
    }

    @Test
    public void wideNonnormalStatusesShowTheApprovedChineseWithoutAnOldClock() {
        String[] messages = {"尚未登录", "等待数据", "刷新失败", "数据较旧",
                "正在刷新", "正在刷新", "正在刷新", "正在刷新"};
        UsageCardState[] states = nonnormalStates();
        for (int width : new int[] {250, 350}) {
            for (int i = 0; i < states.length; i++) {
                View applied = build(states[i], width, meters());
                assertEquals(messages[i], status(applied).getText().toString());
                assertStatus(applied, states[i], width);
                assertRefreshPresentation(applied, states[i]);
                assertEquals(View.GONE, applied.findViewById(R.id.md_updated).getVisibility());
            }
        }
    }

    @Test
    public void reapplyClearsThePreviousClockStatusAndSpinnerInBothDirections() {
        Context context = RuntimeEnvironment.getApplication();
        UsageCardState ready = UsageCardFixtures.state(snapshot(), 0);
        for (int width : new int[] {249, 350}) {
            View applied = build(ready, width, meters());
            for (UsageCardState state : nonnormalStates()) {
                remote(state, width, meters()).reapply(context, applied);
                layout(applied, width);
                assertStatus(applied, state, width);
                assertRefreshPresentation(applied, state);
                assertEquals(View.GONE, applied.findViewById(R.id.md_updated).getVisibility());
                remote(ready, width, meters()).reapply(context, applied);
                layout(applied, width);
                assertEquals("", status(applied).getText().toString());
                assertEquals(View.INVISIBLE, status(applied).getVisibility());
                assertNormalRefreshIcon(applied);
                assertEquals(width >= 280 ? View.VISIBLE : View.GONE,
                        applied.findViewById(R.id.md_updated).getVisibility());
                TextView freshTitle = build(ready, width, meters()).findViewById(R.id.md_title);
                assertEquals(freshTitle.getMaxWidth(),
                        ((TextView) applied.findViewById(R.id.md_title)).getMaxWidth());
            }
        }
    }

    @Test
    public void refreshingRotatesThePreRefreshGlyphAcrossStylesAndBackgrounds() {
        UsageCardState[] states = nonnormalStates();
        for (String style : new String[] {WidgetOptions.COLOR_NATIVE, WidgetOptions.COLOR_CLASSIC}) {
            for (int opacity : new int[] {0, 100}) {
                for (int i = 4; i < states.length; i++) {
                    WidgetOptions options = new WidgetOptions(WidgetOptions.LAYOUT_AUTO,
                            WidgetOptions.THEME_SYSTEM, WidgetOptions.ACCENT_APP, opacity,
                            WidgetOptions.RESET_ABSOLUTE, WidgetOptions.DISPLAY_REMAINING)
                            .withColorStyle(style);
                    RemoteViews remote = MaterialCardRenderer.build(RuntimeEnvironment.getApplication(),
                            WIDGET_ID, options, meters(), states[i], 170, 200);
                    View applied = remote.apply(RuntimeEnvironment.getApplication(),
                            new FrameLayout(RuntimeEnvironment.getApplication()));
                    layout(applied, 170);
                    assertRefreshPresentation(applied, states[i]);
                    remote.reapply(RuntimeEnvironment.getApplication(), applied);
                    layout(applied, 170);
                    assertRefreshPresentation(applied, states[i]);
                }
            }
        }
    }

    @Test
    public void wideFailureReapplyKeepsSyncProblemAndShowsTheFullStatusText() {
        Context context = RuntimeEnvironment.getApplication();
        View applied = build(failed(true), 110, meters());
        remote(failed(true), 350, meters()).reapply(context, applied);
        assertEquals(message(R.string.widget_card_refresh_failed), status(applied).getText().toString());
        assertEquals(View.VISIBLE, status(applied).getVisibility());
        ImageView icon = applied.findViewById(R.id.md_refresh);
        assertEquals(R.drawable.ic_ms_sync_problem,
                Shadows.shadowOf(icon.getDrawable()).getCreatedFromResId());
        assertEquals(message(R.string.widget_card_refresh_failed),
                applied.findViewById(R.id.md_refresh_button).getContentDescription());
    }

    @Test
    public void theFailureMarkStillRequestsRefreshForItsOwnWidget() {
        Application application = RuntimeEnvironment.getApplication();
        View refreshButton = build(failed(true), 170, meters()).findViewById(R.id.md_refresh_button);
        assertTrue(refreshButton.performClick());
        List<Intent> broadcasts = Shadows.shadowOf(application).getBroadcastIntents();
        Intent refresh = broadcasts.get(broadcasts.size() - 1);
        assertEquals(new ComponentName(application, WidgetRefreshReceiver.class), refresh.getComponent());
        assertEquals(AppConstants.ACTION_REFRESH_WIDGET, refresh.getAction());
        assertEquals(WIDGET_ID, refresh.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1));
    }

    private static UsageCardState failed(boolean cached) {
        return new UsageCardState(true, cached ? snapshot() : null, null,
                "Synthetic network failure", UsageCardFixtures.NOW);
    }

    private static UsageCardState[] nonnormalStates() {
        UsageSnapshot snapshot = snapshot();
        long staleAt = snapshot.fetchedAtMillis + UsageCardState.STALE_AFTER_MS;
        return new UsageCardState[] {
                new UsageCardState(false, snapshot, null, "", UsageCardFixtures.NOW, true),
                new UsageCardState(true, null, null, "", UsageCardFixtures.NOW),
                failed(true),
                new UsageCardState(true, snapshot, null, "", staleAt),
                new UsageCardState(true, snapshot, null, "", UsageCardFixtures.NOW, true),
                new UsageCardState(true, null, null, "", UsageCardFixtures.NOW, true),
                new UsageCardState(true, snapshot, null, "Synthetic network failure",
                        UsageCardFixtures.NOW, true),
                new UsageCardState(true, snapshot, null, "", staleAt, true)
        };
    }

    private static UsageSnapshot snapshot() {
        return UsageCardFixtures.snapshot("pro200", 19, 23, false);
    }

    private static List<String> meters() {
        return UsageCardFixtures.keys(WidgetMeters.WEEKLY, WidgetMeters.NEXT_RESET);
    }

    private static String message(int resource) {
        return RuntimeEnvironment.getApplication().getString(resource);
    }

    private static void assertErrorIcon(View applied, int width) {
        assertStatus(applied, failed(true), width);
        assertRefreshPresentation(applied, failed(true));
        ImageView icon = applied.findViewById(R.id.md_refresh);
        assertTrue(icon.getWidth() > 0 && icon.getHeight() > 0);
        View header = applied.findViewById(R.id.md_header);
        int[] glyphPosition = new int[2];
        int[] headerPosition = new int[2];
        icon.getLocationOnScreen(glyphPosition);
        header.getLocationOnScreen(headerPosition);
        assertTrue("The complete failure icon stays inside the header",
                glyphPosition[0] >= headerPosition[0]
                        && glyphPosition[0] + icon.getWidth() <= headerPosition[0] + header.getWidth()
                        && glyphPosition[1] >= headerPosition[1]
                        && glyphPosition[1] + icon.getHeight() <= headerPosition[1] + header.getHeight() + 1);
    }

    private static void assertStatus(View applied, UsageCardState state, int width) {
        TextView status = status(applied);
        String label = state.refreshing ? message(R.string.widget_card_refreshing)
                : state.statusMessage(RuntimeEnvironment.getApplication().getResources(), false);
        if (width >= 250 && !label.isEmpty()) {
            assertEquals(View.VISIBLE, status.getVisibility());
            assertEquals(label, status.getText().toString());
            assertTrue("The complete status must fit without ellipsis",
                    status.getLayout() != null && status.getLayout().getLineCount() == 1
                            && status.getLayout().getEllipsisCount(0) == 0);
        } else {
            assertEquals("", status.getText().toString());
            assertEquals(View.INVISIBLE, status.getVisibility());
        }
        assertNull(status.getContentDescription());
    }

    private static void assertRefreshPresentation(View applied, UsageCardState state) {
        String currentStatus = state.statusMessage(RuntimeEnvironment.getApplication().getResources(), false);
        int glyph = currentStatus.isEmpty() ? R.drawable.ic_ms_sync : R.drawable.ic_ms_sync_problem;
        String description = state.refreshing ? message(R.string.widget_card_refreshing)
                : currentStatus.isEmpty() ? message(R.string.widget_material_refresh) : currentStatus;
        assertEquals(description, applied.findViewById(R.id.md_refresh_button).getContentDescription());
        ImageView icon = applied.findViewById(R.id.md_refresh);
        assertEquals(glyph, Shadows.shadowOf(icon.getDrawable()).getCreatedFromResId());
        ProgressBar spinner = applied.findViewById(R.id.md_refresh_spinner);
        if (state.refreshing) {
            assertEquals(View.INVISIBLE, icon.getVisibility());
            assertTrue("Busy feedback uses the launcher's native indeterminate ProgressBar", spinner != null);
            assertEquals(View.VISIBLE, spinner.getVisibility());
            assertTrue(spinner.isIndeterminate());
            assertTrue(spinner.getIndeterminateDrawable() instanceof RotateDrawable);
            RotateDrawable rotating = (RotateDrawable) spinner.getIndeterminateDrawable();
            // ConstantState-created nested drawables do not retain Robolectric's resource-id marker.
            Context context = RuntimeEnvironment.getApplication();
            Drawable actual = rotating.getDrawable().getConstantState()
                    .newDrawable(context.getResources()).mutate();
            assertArrayEquals("Rotation must preserve the pre-refresh status glyph",
                    alphaShape(context.getDrawable(glyph).mutate()), alphaShape(actual));
        } else {
            assertEquals(View.VISIBLE, icon.getVisibility());
            assertNull("A completed refresh must remove the previous animated child", spinner);
        }
    }

    private static int[] alphaShape(Drawable drawable) {
        drawable.clearColorFilter();
        drawable.setTint(Color.WHITE);
        drawable.setAlpha(255);
        drawable.setBounds(0, 0, 96, 96);
        Bitmap bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        try {
            drawable.draw(new Canvas(bitmap));
            int[] alpha = new int[96 * 96];
            bitmap.getPixels(alpha, 0, 96, 0, 0, 96, 96);
            boolean visible = false;
            for (int i = 0; i < alpha.length; i++) {
                alpha[i] = Color.alpha(alpha[i]);
                visible |= alpha[i] > 0;
            }
            assertTrue("The native reference and spinner glyph must render visible pixels", visible);
            return alpha;
        } finally {
            bitmap.recycle();
        }
    }

    private static void assertNormalRefreshIcon(View applied) {
        assertRefreshPresentation(applied, UsageCardFixtures.state(snapshot(), 0));
    }

    private static TextView status(View applied) {
        return applied.findViewById(R.id.md_status);
    }

    private static RemoteViews remote(UsageCardState state, int width, List<String> keys) {
        return MaterialCardRenderer.build(RuntimeEnvironment.getApplication(), WIDGET_ID,
                WidgetOptions.defaults(), keys, state, width, 200);
    }

    private static View build(UsageCardState state, int width, List<String> keys) {
        Context context = RuntimeEnvironment.getApplication();
        View applied = remote(state, width, keys).apply(context, new FrameLayout(context));
        layout(applied, width);
        return applied;
    }

    private static void layout(View applied, int width) {
        Context context = RuntimeEnvironment.getApplication();
        float density = context.getResources().getDisplayMetrics().density;
        int widthPx = Math.round(width * density);
        int heightPx = Math.round(200 * density);
        applied.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        applied.layout(0, 0, widthPx, heightPx);
    }
}
