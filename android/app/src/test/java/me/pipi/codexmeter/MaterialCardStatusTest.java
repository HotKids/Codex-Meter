package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.WidgetMeters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
                assertErrorIcon(applied);
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
        assertErrorIcon(applied);
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
                assertErrorIcon(applied);
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
        assertErrorIcon(applied);
        remote(ready, 350, meters()).reapply(context, applied);
        assertEquals(View.VISIBLE, updated.getVisibility());
        assertNormalRefreshIcon(applied);
    }

    @Test
    public void narrowWaitingStaleAndSignedOutMessagesStayUnchanged() {
        UsageSnapshot snapshot = snapshot();
        UsageCardState[] states = {
                new UsageCardState(true, null, null, "", UsageCardFixtures.NOW),
                new UsageCardState(true, snapshot, null, "",
                        snapshot.fetchedAtMillis + UsageCardState.STALE_AFTER_MS),
                new UsageCardState(false, snapshot, null, "Synthetic network failure",
                        UsageCardFixtures.NOW)
        };
        int[] messages = {R.string.widget_card_waiting, R.string.widget_card_stale,
                R.string.widget_card_sign_in};
        for (int i = 0; i < states.length; i++) {
            View applied = build(states[i], 140, meters());
            TextView status = status(applied);
            assertEquals(message(messages[i]), status.getText().toString());
            assertNull(status.getContentDescription());
            assertNormalRefreshIcon(applied);
        }
    }

    @Test
    public void nonFailureReapplyRestoresTitleWidthAndClearsTheAccessibleFailureMessage() {
        Context context = RuntimeEnvironment.getApplication();
        UsageSnapshot snapshot = snapshot();
        UsageCardState[] states = {
                UsageCardFixtures.state(snapshot, 0),
                new UsageCardState(true, null, null, "", UsageCardFixtures.NOW),
                new UsageCardState(true, snapshot, null, "",
                        snapshot.fetchedAtMillis + UsageCardState.STALE_AFTER_MS),
                UsageCardFixtures.signedOut()
        };
        String[] messages = {"", message(R.string.widget_card_waiting),
                message(R.string.widget_card_stale), message(R.string.widget_card_sign_in)};
        for (int width : new int[] {110, 170}) {
            for (int i = 0; i < states.length; i++) {
                View applied = build(failed(true), width, meters());
                remote(states[i], width, meters()).reapply(context, applied);
                TextView status = status(applied);
                assertEquals(messages[i], status.getText().toString());
                assertEquals(i == 0 ? View.INVISIBLE : View.VISIBLE, status.getVisibility());
                assertNull(status.getContentDescription());
                assertNormalRefreshIcon(applied);
                TextView freshTitle = build(states[i], width, meters()).findViewById(R.id.md_title);
                assertEquals(freshTitle.getMaxWidth(),
                        ((TextView) applied.findViewById(R.id.md_title)).getMaxWidth());
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
        assertEquals(message(R.string.widget_card_refresh_failed), icon.getContentDescription());
    }

    @Test
    public void theFailureMarkStillRequestsRefreshForItsOwnWidget() {
        Application application = RuntimeEnvironment.getApplication();
        ImageView refreshIcon = build(failed(true), 170, meters()).findViewById(R.id.md_refresh);
        assertTrue(refreshIcon.performClick());
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

    private static UsageSnapshot snapshot() {
        return UsageCardFixtures.snapshot("pro200", 19, 23, false);
    }

    private static List<String> meters() {
        return UsageCardFixtures.keys(WidgetMeters.WEEKLY, WidgetMeters.NEXT_RESET);
    }

    private static String message(int resource) {
        return RuntimeEnvironment.getApplication().getString(resource);
    }

    private static void assertErrorIcon(View applied) {
        TextView status = status(applied);
        if (status.getVisibility() == View.VISIBLE) {
            assertEquals(message(R.string.widget_card_refresh_failed), status.getText().toString());
            assertTrue("The complete failure status must fit without ellipsis",
                    status.getLayout() != null && status.getLayout().getLineCount() == 1
                            && status.getLayout().getEllipsisCount(0) == 0);
        } else {
            assertEquals("", status.getText().toString());
            assertEquals(View.INVISIBLE, status.getVisibility());
        }
        assertNull(status.getContentDescription());
        ImageView icon = applied.findViewById(R.id.md_refresh);
        assertEquals(R.drawable.ic_ms_sync_problem,
                Shadows.shadowOf(icon.getDrawable()).getCreatedFromResId());
        assertEquals(message(R.string.widget_card_refresh_failed), icon.getContentDescription());
        assertEquals(View.VISIBLE, icon.getVisibility());
        assertTrue(icon.getWidth() > 0 && icon.getHeight() > 0);
        View header = applied.findViewById(R.id.md_header);
        assertTrue("The complete failure icon stays inside the header: right=" + icon.getRight()
                        + ", header=" + header.getWidth(),
                icon.getLeft() >= 0 && icon.getRight() <= header.getWidth()
                        && icon.getTop() >= 0 && icon.getBottom() <= header.getHeight());
    }

    private static void assertNormalRefreshIcon(View applied) {
        ImageView icon = applied.findViewById(R.id.md_refresh);
        assertEquals(R.drawable.ic_ms_sync,
                Shadows.shadowOf(icon.getDrawable()).getCreatedFromResId());
        assertEquals(message(R.string.widget_material_refresh), icon.getContentDescription());
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
        float density = context.getResources().getDisplayMetrics().density;
        int widthPx = Math.round(width * density);
        int heightPx = Math.round(200 * density);
        applied.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        applied.layout(0, 0, widthPx, heightPx);
        return applied;
    }
}
