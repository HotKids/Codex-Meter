package me.pipi.codexmeter;

import dev.bennett.codexmeter.DashboardSections;
import dev.bennett.codexmeter.UsageCredits;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsageWindow;
import dev.bennett.codexmeter.WidgetMeters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/** Applies real widget click actions against synthetic usage, without loading account tokens. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "en-rUS")
public class WidgetModuleNavigationTest {
    private static final int WIDGET_ID = 81;
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);

    @Test
    public void wideCardClicksFollowTheSelectedOrderIncludingBalance() {
        View card = card(350, state(window(2 * HOUR), window(3 * HOUR), null),
                UsageCardFixtures.keys(WidgetOptions.USAGE_CREDITS, WidgetMeters.WEEKLY,
                        WidgetMeters.FIVE_HOUR, WidgetMeters.NEXT_RESET));
        assertClickSection(card, R.id.md_panel_0, DashboardSections.USAGE_CREDITS);
        assertClickSection(card, R.id.md_panel_1, DashboardSections.WEEKLY);
        assertClickSection(card, R.id.md_panel_2, DashboardSections.FIVE_HOUR);
        assertClickSection(card, R.id.md_panel_3, DashboardSections.FIVE_HOUR);
    }

    @Test
    public void narrowCardsFollowTheirStackedSlotsAndReapplyUpdatesTargets() {
        UsageCardState state = state(window(2 * HOUR), window(3 * HOUR), null);
        View card = card(170, state,
                UsageCardFixtures.keys(WidgetMeters.WEEKLY, WidgetMeters.FIVE_HOUR));
        assertClickSection(card, R.id.md_panel_0, DashboardSections.WEEKLY);
        assertClickSection(card, R.id.md_panel_2, DashboardSections.FIVE_HOUR);
        cardRemote(170, state,
                UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY))
                .reapply(context(), card);
        assertClickSection(card, R.id.md_panel_0, DashboardSections.FIVE_HOUR);
        assertClickSection(card, R.id.md_panel_2, DashboardSections.WEEKLY);
    }

    @Test
    public void dialsNavigateToTheirSelectedWindowsAndResetTarget() {
        UsageCardState state = state(window(3 * HOUR), window(HOUR), null);
        View dials = apply(DialWidgetRenderer.build(context(), WIDGET_ID,
                WidgetOptions.defaults(),
                UsageCardFixtures.keys(WidgetMeters.WEEKLY, WidgetMeters.NEXT_RESET), state));
        assertClickSection(dials, R.id.primary_section, DashboardSections.WEEKLY);
        assertClickSection(dials, R.id.secondary_section, DashboardSections.WEEKLY);
        assertClickSection(dials, android.R.id.background, null);
        assertRefresh(dials, R.id.refresh_button);
    }

    @Test
    public void monthlyFallbackUsesTheActualHomepageMonthlySection() {
        View card = card(350, state(window(3 * HOUR), null, window(HOUR)),
                UsageCardFixtures.keys(WidgetMeters.WEEKLY, WidgetMeters.NEXT_RESET));
        assertClickSection(card, R.id.md_panel_0, DashboardSections.MONTHLY);
        assertClickSection(card, R.id.md_panel_1, DashboardSections.MONTHLY);
    }

    @Test
    public void resetTargetPrefersWeeklyWhenPresentAndIgnoresExpiredWindows() {
        View card = card(350, state(window(-HOUR), window(HOUR), window(HOUR / 2)),
                UsageCardFixtures.keys(WidgetMeters.NEXT_RESET, WidgetMeters.WEEKLY));
        assertClickSection(card, R.id.md_panel_0, DashboardSections.WEEKLY);
        assertClickSection(card, R.id.md_panel_1, DashboardSections.WEEKLY);
    }

    @Test
    public void missingAndUnknownMetersOpenOnlyTheHomepage() {
        UsageCardState empty = UsageCardFixtures.signedOut();
        View card = card(350, empty,
                UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY,
                        WidgetMeters.NEXT_RESET, WidgetOptions.USAGE_CREDITS));
        for (int slot : new int[] {R.id.md_panel_0, R.id.md_panel_1,
                R.id.md_panel_2, R.id.md_panel_3}) {
            assertClickSection(card, slot, null);
        }
        WidgetMeter unknown = new WidgetMeter(context(), "unknown", WidgetOptions.defaults(),
                state(window(HOUR), window(2 * HOUR), null));
        assertEquals("", unknown.dashboardSection);
    }

    @Test
    public void differentSectionsAndWidgetsKeepIndependentPendingIntents() throws Exception {
        PendingIntent weekly = WidgetActions.openSection(context(), WIDGET_ID,
                DashboardSections.WEEKLY);
        PendingIntent balance = WidgetActions.openSection(context(), WIDGET_ID,
                DashboardSections.USAGE_CREDITS);
        PendingIntent otherWidget = WidgetActions.openSection(context(), WIDGET_ID + 1,
                DashboardSections.WEEKLY);
        assertNotEquals(weekly, balance);
        assertNotEquals(weekly, otherWidget);
        weekly.send();
        assertStartedSection(DashboardSections.WEEKLY);
        balance.send();
        assertStartedSection(DashboardSections.USAGE_CREDITS);
        weekly.send();
        assertStartedSection(DashboardSections.WEEKLY);
        otherWidget.send();
        assertStartedSection(DashboardSections.WEEKLY);
    }

    @Test
    public void headerAndStatusRetainRootAndRefreshActions() {
        UsageCardState ready = state(window(HOUR), window(2 * HOUR), null);
        UsageCardState failed = new UsageCardState(true, ready.snapshot, null,
                "Synthetic refresh failure", UsageCardFixtures.NOW);
        View card = card(350, failed, UsageCardFixtures.keys(WidgetMeters.WEEKLY));
        assertClickSection(card, android.R.id.background, null);
        assertRefresh(card, R.id.md_refresh_button);
        assertRefresh(card, R.id.md_status);
    }

    private static Context context() {
        return RuntimeEnvironment.getApplication();
    }

    private static UsageWindow window(long resetAfterMillis) {
        return UsageCardFixtures.window(25, TimeUnit.DAYS.toSeconds(7), resetAfterMillis);
    }

    private static UsageCardState state(UsageWindow fiveHour, UsageWindow weekly,
            UsageWindow monthly) {
        UsageSnapshot snapshot = new UsageSnapshot("plus", true, false, fiveHour, weekly,
                monthly, Collections.emptyList(), new UsageCredits(true, false, "62437.29"),
                2, UsageCardFixtures.NOW);
        return UsageCardFixtures.state(snapshot, 2);
    }

    private static RemoteViews cardRemote(int widthDp, UsageCardState state, List<String> keys) {
        return MaterialCardRenderer.build(context(), WIDGET_ID, WidgetOptions.defaults(), keys,
                state, widthDp, 250);
    }

    private static View card(int widthDp, UsageCardState state, List<String> keys) {
        return apply(cardRemote(widthDp, state, keys));
    }

    private static View apply(RemoteViews views) {
        return views.apply(context(), new FrameLayout(context()));
    }

    private static void assertClickSection(View widget, int viewId, String section) {
        assertTrue("The rendered module has its own click target",
                widget.findViewById(viewId).performClick());
        assertStartedSection(section);
    }

    private static void assertStartedSection(String section) {
        Intent intent = Shadows.shadowOf((Application) context()).getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(new ComponentName(context(), MainActivity.class), intent.getComponent());
        assertEquals(WidgetActions.ACTION_OPEN, intent.getAction());
        if (section == null) {
            assertNull(intent.getStringExtra(WidgetActions.EXTRA_DASHBOARD_SECTION));
        } else {
            assertEquals(section, intent.getStringExtra(WidgetActions.EXTRA_DASHBOARD_SECTION));
        }
    }

    private static void assertRefresh(View widget, int viewId) {
        assertTrue(widget.findViewById(viewId).performClick());
        List<Intent> broadcasts = Shadows.shadowOf((Application) context()).getBroadcastIntents();
        Intent intent = broadcasts.get(broadcasts.size() - 1);
        assertEquals(new ComponentName(context(), WidgetRefreshReceiver.class),
                intent.getComponent());
        assertEquals(AppConstants.ACTION_REFRESH_WIDGET, intent.getAction());
        assertEquals(WIDGET_ID, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1));
    }
}
