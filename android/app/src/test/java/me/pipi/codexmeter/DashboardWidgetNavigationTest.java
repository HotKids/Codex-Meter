package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import androidx.core.widget.NestedScrollView;
import dev.bennett.codexmeter.DashboardSections;
import dev.bennett.codexmeter.UsageCredits;
import dev.bennett.codexmeter.UsageSnapshot;
import java.util.Arrays;
import java.lang.reflect.Field;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 35}, application = Application.class, qualifiers = "w390dp-h320dp-xhdpi",
        shadows = DashboardWidgetNavigationTest.SignedInAccount.class)
public class DashboardWidgetNavigationTest {
    private static final String SECTION_EXTRA = "dashboard_section";
    private Application app;

    @Before
    public void prepareSyntheticDashboard() {
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        AppPreferences.completeOnboarding(app);
        AppPreferences.setAutomaticRefresh(app, false);
        AppPreferences.setRefreshOnLaunch(app, false);
        UpdatePreferences.setAutomaticChecks(app, false);
        UsageSnapshot original = UsageCardFixtures.plus();
        AppPreferences.saveSnapshot(app, new UsageSnapshot(original.planType, original.allowed,
                original.limitReached, original.fiveHour, original.weekly, original.monthly,
                original.additionalLimits, new UsageCredits(true, false, "62437.29"),
                original.resetCreditsAvailable, original.fetchedAtMillis));
    }

    @Test
    public void coldLaunchFindsBalanceAfterTheSavedOrder() {
        AppPreferences.setDashboardOrder(app, Arrays.asList(DashboardSections.USAGE_HISTORY,
                DashboardSections.WEEKLY, DashboardSections.USAGE_CREDITS,
                DashboardSections.FIVE_HOUR, DashboardSections.RESET_CREDITS));
        try (ActivityController<MainActivity> controller = launch(DashboardSections.USAGE_CREDITS)) {
            assertPositioned(controller.get(), DashboardSections.USAGE_CREDITS);
            assertFalse(controller.get().getIntent().hasExtra(SECTION_EXTRA));
        }
    }

    @Test
    public void runningDashboardUsesTheLatestTargetWithoutRepeatingOnResume() {
        try (ActivityController<MainActivity> controller = launch(DashboardSections.USAGE_CREDITS)) {
            MainActivity activity = controller.get();
            assertPositioned(activity, DashboardSections.USAGE_CREDITS);
            activity.onNewIntent(sectionIntent(DashboardSections.WEEKLY));
            layout(activity);
            assertPositioned(activity, DashboardSections.WEEKLY);
            NestedScrollView scroll = activity.findViewById(R.id.dashboard_scroll);
            scroll.scrollTo(0, 0);
            controller.pause().resume();
            layout(activity);
            assertEquals(0, scroll.getScrollY());
        }
    }

    @Test
    public void hiddenTargetDoesNotChangeTheSavedVisibility() {
        AppPreferences.setShowDashboardUsageCredits(app, false);
        try (ActivityController<MainActivity> controller = launch(DashboardSections.USAGE_CREDITS)) {
            assertNull(controller.get().findViewById(R.id.dashboard_content)
                    .findViewWithTag(DashboardSections.USAGE_CREDITS));
            assertFalse(AppPreferences.showDashboardUsageCredits(app));
            assertFalse(controller.get().getIntent().hasExtra(SECTION_EXTRA));
        }
    }

    @Test
    public void missingTargetDoesNotLeaveADeferredNavigation() {
        AppPreferences.saveSnapshot(app, UsageCardFixtures.plus());
        try (ActivityController<MainActivity> controller = launch(DashboardSections.USAGE_CREDITS)) {
            assertNull(controller.get().findViewById(R.id.dashboard_content)
                    .findViewWithTag(DashboardSections.USAGE_CREDITS));
            assertTrue(AppPreferences.showDashboardUsageCredits(app));
            assertFalse(controller.get().getIntent().hasExtra(SECTION_EXTRA));
        }
    }

    @Test
    public void normalAppLaunchCancelsAnUndrawnWidgetTarget() {
        try (ActivityController<MainActivity> controller = Robolectric
                .buildActivity(MainActivity.class, sectionIntent(DashboardSections.USAGE_CREDITS))
                .create()) {
            MainActivity activity = controller.get();
            activity.onNewIntent(new Intent(app, MainActivity.class).setAction(Intent.ACTION_MAIN));
            controller.start().resume().visible();
            layout(activity);
            NestedScrollView scroll = activity.findViewById(R.id.dashboard_scroll);
            assertEquals(0, scroll.getScrollY());
        }
    }

    @Test
    public void newestWidgetTargetWinsBeforeTheFirstLayout() {
        try (ActivityController<MainActivity> controller = Robolectric
                .buildActivity(MainActivity.class, sectionIntent(DashboardSections.USAGE_CREDITS))
                .create()) {
            MainActivity activity = controller.get();
            activity.onNewIntent(sectionIntent(DashboardSections.WEEKLY));
            controller.start().resume().visible();
            layout(activity);
            assertPositioned(activity, DashboardSections.WEEKLY);
        }
    }

    @Test
    public void refreshBeforeLayoutNavigatesUsingTheRebuiltCardOrder() throws Exception {
        try (ActivityController<MainActivity> controller = Robolectric
                .buildActivity(MainActivity.class, sectionIntent(DashboardSections.USAGE_CREDITS))
                .create().start()) {
            AppPreferences.setDashboardOrder(app, Arrays.asList(DashboardSections.USAGE_HISTORY,
                    DashboardSections.WEEKLY, DashboardSections.FIVE_HOUR,
                    DashboardSections.RESET_CREDITS, DashboardSections.USAGE_CREDITS));
            Field receiver = MainActivity.class.getDeclaredField("appEventsReceiver");
            receiver.setAccessible(true);
            ((BroadcastReceiver) receiver.get(controller.get())).onReceive(app,
                    new Intent(AppConstants.ACTION_USAGE_UPDATED));
            controller.resume().visible();
            layout(controller.get());
            assertPositioned(controller.get(), DashboardSections.USAGE_CREDITS);
            assertFalse(controller.get().getIntent().hasExtra(SECTION_EXTRA));
        }
    }

    private ActivityController<MainActivity> launch(String section) {
        ActivityController<MainActivity> controller = Robolectric
                .buildActivity(MainActivity.class, sectionIntent(section)).setup();
        layout(controller.get());
        return controller;
    }

    private Intent sectionIntent(String section) {
        return new Intent(app, MainActivity.class).setAction(WidgetActions.ACTION_OPEN)
                .putExtra(SECTION_EXTRA, section);
    }

    private static void layout(MainActivity activity) {
        View root = activity.getWindow().getDecorView();
        for (int pass = 0; pass < 2; pass++) {
            root.measure(View.MeasureSpec.makeMeasureSpec(780, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 780, 640);
            root.getViewTreeObserver().dispatchOnGlobalLayout();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }

    private static void assertPositioned(MainActivity activity, String key) {
        View card = activity.findViewById(R.id.dashboard_content).findViewWithTag(key);
        assertNotNull("The visible section must be addressable after sorting", card);
        NestedScrollView scroll = activity.findViewById(R.id.dashboard_scroll);
        Rect bounds = new Rect();
        card.getDrawingRect(bounds);
        scroll.offsetDescendantRectToMyCoords(card, bounds);
        int range = Math.max(0, scroll.getChildAt(0).getHeight() - scroll.getHeight()
                + scroll.getPaddingTop() + scroll.getPaddingBottom());
        assertEquals(Math.min(range, bounds.top), scroll.getScrollY());
    }

    @Implements(value = SecureTokenStore.class, isInAndroidSdk = false)
    public static class SignedInAccount {
        @Implementation
        protected static boolean isSignedIn(Context context) {
            return true;
        }
    }
}
