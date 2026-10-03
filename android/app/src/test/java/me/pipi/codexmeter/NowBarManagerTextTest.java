package me.pipi.codexmeter;

import dev.bennett.codexmeter.NowBarPercentMode;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsageWindow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "zh-rCN")
@LooperMode(LooperMode.Mode.PAUSED)
public class NowBarManagerTextTest {
    private Application app;

    @Before
    public void resetSyntheticSettings() {
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .edit().clear().putBoolean("usage_pace_enabled", false).commit();
        app.getSharedPreferences("codex_meter_now_bar_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        app.getSharedPreferences("codex_meter_now_bar_prefs_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        Shadows.shadowOf(app).grantPermissions("android.permission.POST_NOTIFICATIONS");
    }

    @Test
    public void weeklyAndMonthlyCapsulesKeepUpstreamMarkersInChineseLocale() throws Exception {
        Object weekly = content(snapshot(null, window(40, 604800L, 3600L), null));
        assertEquals("W 60%", text(weekly, "focusCritical"));
        assertEquals("每周：剩余 60%", text(weekly, "longWindowText"));

        Object monthly = content(snapshot(null, null, window(40, 2592000L, 3600L)));
        assertEquals("M 60%", text(monthly, "focusCritical"));
        assertEquals("每月：剩余 60%", text(monthly, "longWindowText"));
    }

    @Test
    public void exhaustedCapsuleKeepsUpstreamResetUnitsInChineseLocale() throws Exception {
        Object weekly = content(snapshot(null,
                window(100, 604800L, TimeUnit.HOURS.toSeconds(52)), null));
        assertEquals("W 2d 4h", text(weekly, "focusCritical"));
        assertEquals("每周：2d 4h后重置", text(weekly, "longWindowText"));

        Object hours = content(snapshot(window(100, 18000L,
                TimeUnit.MINUTES.toSeconds(200)), null, null));
        assertEquals("3h 20m", text(hours, "focusCritical"));
        assertEquals("5h：3h 20分后重置", text(hours, "fiveHourText"));

        Object minutes = content(snapshot(window(100, 18000L,
                TimeUnit.MINUTES.toSeconds(12)), null, null));
        assertEquals("12m", text(minutes, "focusCritical"));
    }

    @Test
    public void missingResetAndMissingWindowKeepUpstreamFallbacks() throws Exception {
        Object noReset = content(snapshot(window(100, 18000L, 0L), null, null));
        assertEquals("0%", text(noReset, "focusCritical"));
        assertEquals("—", text(content(null), "focusCritical"));
    }

    @Test
    public void notificationUsesProductNameAndOmitsHiddenDashboardFiveHour() throws Exception {
        AppPreferences.setShowDashboardFiveHour(app, false);
        Object content = content(snapshot(window(40, 18000L, 3600L),
                window(1, 604800L, 3600L), null));
        Method baseBuilder = NowBarManager.class.getDeclaredMethod("baseBuilder", Context.class,
                content.getClass(), boolean.class);
        baseBuilder.setAccessible(true);
        Notification notification = ((Notification.Builder) baseBuilder.invoke(null, app,
                content, true)).build();
        assertEquals("Codex Meter", notification.extras.getString(Notification.EXTRA_TITLE));
        assertEquals("每周：剩余 99%",
                notification.extras.getString(Notification.EXTRA_TEXT));
    }

    @Test
    public void liveNotificationKeepsCodexCapsuleAndOmitsRightIcon() throws Exception {
        Object content = content(snapshot(null, window(1, 604800L, 3600L), null));
        Method baseBuilder = NowBarManager.class.getDeclaredMethod("baseBuilder", Context.class,
                content.getClass(), boolean.class);
        baseBuilder.setAccessible(true);
        Notification notification = ((Notification.Builder) baseBuilder.invoke(null, app,
                content, true)).build();
        assertEquals(R.drawable.ic_notification, notification.getSmallIcon().getResId());
        assertNull(notification.getLargeIcon());
        Notification samsung = samsungNotification(content);
        android.graphics.drawable.Icon chip = samsung.extras.getParcelable(
                "android.ongoingActivityNoti.chipIcon");
        android.graphics.drawable.Icon expanded = samsung.extras.getParcelable(
                "android.ongoingActivityNoti.nowbarIcon");
        assertEquals(R.drawable.ic_codex_logo_on_accent, chip.getResId());
        assertEquals(R.mipmap.ic_launcher, expanded.getResId());
    }

    @Test
    public void dashboardFiveHourChangesRepostAnActiveNotificationImmediately() {
        assertTrue(NowBarManager.startPreview(app));
        assertTrue(monitorText().contains("5h"));

        AppPreferences.setShowDashboardFiveHour(app, false);
        assertFalse(monitorText().contains("5h"));
        assertTrue(monitorText().contains("每周"));

        AppPreferences.setShowDashboardFiveHour(app, true);
        assertTrue(monitorText().contains("5h"));
    }

    @Test
    public void hiddenFiveHourCannotDriveCapsuleOrProgress() throws Exception {
        AppPreferences.setShowDashboardFiveHour(app, false);
        UsageWindow weekly = window(1, 604800L, 3600L);
        UsageSnapshot snapshot = snapshot(window(95, 18000L, 3600L), weekly, null);
        app.getSharedPreferences("codex_meter_now_bar_v1", Context.MODE_PRIVATE).edit()
                .putString("focus_metric", NowBarPercentMode.FIVE_HOUR).commit();
        for (String mode : new String[]{NowBarPercentMode.AUTO, NowBarPercentMode.FIVE_HOUR}) {
            NowBarPreferences.setPercentMode(app, mode);
            Object content = content(snapshot);
            assertSame(weekly, value(content, "progressWindow"));
            assertEquals(true, value(content, "weeklyFocus"));
            assertEquals("W 99%", text(content, "focusCritical"));
            assertEquals("Codex · 每周 99%", samsungNotification(content).extras.getString(
                    "android.ongoingActivityNoti.chipExpandedText"));
            assertEquals(mode, NowBarPreferences.getPercentMode(app));
            assertEquals(NowBarPercentMode.FIVE_HOUR, app.getSharedPreferences(
                    "codex_meter_now_bar_v1", Context.MODE_PRIVATE)
                    .getString("focus_metric", null));
        }
    }

    @Test
    public void hidingTheOnlyWindowLeavesCapsuleUnavailable() throws Exception {
        AppPreferences.setShowDashboardFiveHour(app, false);
        Object content = content(snapshot(window(95, 18000L, 3600L), null, null));
        assertNull(value(content, "progressWindow"));
        assertEquals("—", text(content, "focusCritical"));
        assertEquals("Codex · 每周 暂无数据", samsungNotification(content).extras.getString(
                "android.ongoingActivityNoti.chipExpandedText"));
    }

    @Test
    public void secondLineFollowsTheSavedWidgetSelectionAndOrder() throws Exception {
        saveWidgetAsUser(42, WidgetOptions.defaults().withVisibleMeters("weekly,next_reset"));
        UsageSnapshot usage = snapshot(window(40, 18000L, 7200L),
                window(1, 604800L, 3630L), null);
        String reset = "1h 0m";
        Object selected = content(usage);
        assertEquals("每周：99% · 重置：" + reset + " · " + creditDetail(0),
                text(selected, "windowText"));
        assertEquals("每周：99%", samsungNotification(selected).extras.getString(
                "android.ongoingActivityNoti.nowbarPrimaryInfo"));
        assertEquals("重置：" + reset + " · " + creditDetail(0), samsungNotification(selected).extras.getString(
                "android.ongoingActivityNoti.nowbarSecondaryInfo"));

        saveWidgetAsUser(42, WidgetOptions.defaults().withVisibleMeters("next_reset,weekly"));
        assertEquals("重置：" + reset + " · " + creditDetail(0) + " · 每周：99%", text(content(usage), "windowText"));
    }

    @Test
    public void followedResetUsesDaysAndHoursWhileWidgetDetailsKeepTheirDate() throws Exception {
        saveWidgetAsUser(42, WidgetOptions.defaults().withVisibleMeters("next_reset"));
        long resetAfter = TimeUnit.DAYS.toSeconds(6) + TimeUnit.HOURS.toSeconds(11) + 30L;
        UsageSnapshot usage = snapshot(null, window(1, 604800L, resetAfter), null);
        assertEquals("重置：6d 11h · " + creditDetail(0), text(content(usage), "windowText"));
        WidgetMeter widget = new WidgetMeter(app, "next_reset", WidgetOptions.defaults(),
                new UsageCardState(true, usage, null, "", System.currentTimeMillis()));
        assertEquals(UsageCardFormat.absoluteReset(widget.resetAtMillis), widget.value);
    }

    @Test
    public void latestUserSaveChangesTheActiveNotificationButBackgroundWritesDoNot() throws Exception {
        assertTrue(NowBarManager.startPreview(app));
        saveWidgetAsUser(42, WidgetOptions.defaults().withVisibleMeters("weekly"));
        assertTrue(monitorText().startsWith("每周："));
        assertFalse(monitorText().contains("5h"));

        saveWidgetAsUser(43, WidgetOptions.defaults().withVisibleMeters("reset_credits,next_reset"));
        assertTrue(monitorText().startsWith("重置："));
        assertTrue(monitorText().endsWith(" · " + creditDetail(0)));
        assertFalse(monitorText().contains("每周"));
        AppPreferences.saveWidgetOptions(app, 42,
                WidgetOptions.defaults().withVisibleMeters("five_hour"));
        assertTrue(monitorText().startsWith("重置："));
        assertTrue(monitorText().endsWith(" · " + creditDetail(0)));

        new CodexUsageWidget().onRestored(app, new int[]{43}, new int[]{143});
        assertTrue(monitorText().startsWith("重置："));
        assertTrue(monitorText().endsWith(" · " + creditDetail(0)));
        AppPreferences.deleteWidgetOptions(app, 143);
        assertTrue(monitorText().contains("5h"));
    }

    @Test
    public void legacyHelperSelectionKeepsMissingResetAndOneCreditDetail() throws Exception {
        saveWidgetAsUser(42, WidgetOptions.defaults().withVisibleMeters("next_reset,reset_credits"));
        assertEquals("next_reset", AppPreferences.loadWidgetOptions(app, 42).effectiveVisibleMeters());
        assertEquals("重置：— · " + creditDetail(0), text(content(null), "windowText"));
        assertTrue(AppPreferences.saveResetCredits(app,
                ResetCreditsSnapshot.summary(5, System.currentTimeMillis())));
        assertEquals("重置：— · " + creditDetail(5), text(content(null), "windowText"));
    }

    @Test
    public void refreshedCreditInventoryUpdatesTheActiveSecondLineImmediately() throws Exception {
        assertTrue(NowBarManager.startPreview(app));
        saveWidgetAsUser(42, WidgetOptions.defaults().withVisibleMeters("next_reset"));
        assertTrue(monitorText().startsWith("重置："));
        assertTrue(monitorText().endsWith(" · " + creditDetail(0)));
        assertTrue(AppPreferences.saveResetCredits(app,
                ResetCreditsSnapshot.summary(5, System.currentTimeMillis())));
        assertTrue(monitorText().startsWith("重置："));
        assertTrue(monitorText().endsWith(" · " + creditDetail(5)));
    }

    @Test
    public void cancellingAnotherWidgetEditorDoesNotChangeTheFollowedSelection() throws Exception {
        saveWidgetAsUser(42, WidgetOptions.defaults().withVisibleMeters("weekly"));
        AppPreferences.saveWidgetOptions(app, 43,
                WidgetOptions.defaults().withVisibleMeters("reset_credits"));
        Intent intent = new Intent(app, WidgetConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 43);
        var controller = Robolectric.buildActivity(WidgetConfigActivity.class, intent).setup();
        controller.get().finish();
        controller.pause().stop().destroy();
        assertEquals("每周：99%", text(content(snapshot(null,
                window(1, 604800L, 3600L), null)), "windowText"));
    }

    @Test
    public void overlappingRestoredIdsPreserveTheOriginalFollowedWidgetAndBothSelections() throws Exception {
        saveWidgetAsUser(42, WidgetOptions.defaults().withVisibleMeters("weekly"));
        AppPreferences.saveWidgetOptions(app, 43,
                WidgetOptions.defaults().withVisibleMeters("reset_credits"));
        new CodexUsageWidget().onRestored(app, new int[]{42, 43}, new int[]{43, 143});
        assertEquals("每周：99%", text(content(snapshot(null,
                window(1, 604800L, 3600L), null)), "windowText"));
        assertEquals("weekly", AppPreferences.loadWidgetOptions(app, 43).effectiveVisibleMeters());
        assertEquals("next_reset", AppPreferences.loadWidgetOptions(app, 143).effectiveVisibleMeters());
    }

    @Test
    public void selectedMonthlyValueAndHomeFiveHourVisibilityKeepTheirExistingRules() throws Exception {
        saveWidgetAsUser(42, WidgetOptions.defaults().withPercentSymbol(false)
                .withVisibleMeters("five_hour,weekly"));
        AppPreferences.setShowDashboardFiveHour(app, false);
        Object selected = content(snapshot(window(40, 18000L, 3600L), null,
                window(40, 2592000L, 7200L)));
        assertEquals("每月：60", text(selected, "windowText"));
        assertEquals("M 60%", text(selected, "focusCritical"));
    }

    private void saveWidgetAsUser(int id, WidgetOptions options) throws Exception {
        AppPreferences.saveWidgetOptions(app, id, options);
        Intent intent = new Intent(app, WidgetConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        var controller = Robolectric.buildActivity(WidgetConfigActivity.class, intent).setup();
        Method save = WidgetConfigActivity.class.getDeclaredMethod("save");
        save.setAccessible(true);
        save.invoke(controller.get());
        controller.pause().stop().destroy();
    }

    private Notification samsungNotification(Object content) throws Exception {
        Method compatibility = NowBarManager.class.getDeclaredMethod(
                "applySamsungCompatibility", Context.class, Notification.Builder.class,
                content.getClass(), long.class, boolean.class);
        compatibility.setAccessible(true);
        Notification.Builder builder = new Notification.Builder(app, "test_live_monitor");
        compatibility.invoke(null, app, builder, content,
                System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(20), true);
        return builder.build();
    }

    private String creditDetail(int count) {
        return app.getString(R.string.widget_material_reset_credits, count);
    }

    private String monitorText() {
        NotificationManager manager = (NotificationManager)
                app.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager.getActiveNotifications()[0].getNotification().extras
                .getString(Notification.EXTRA_TEXT);
    }

    private static UsageWindow window(int used, long length, long resetAfter) {
        return new UsageWindow(used, length, resetAfter, 0L);
    }

    private static UsageSnapshot snapshot(UsageWindow fiveHour, UsageWindow weekly,
            UsageWindow monthly) {
        return new UsageSnapshot(monthly == null ? "plus" : "free", true, false,
                fiveHour, weekly, monthly, null, null, 0, System.currentTimeMillis());
    }

    private static Object content(UsageSnapshot snapshot) throws Exception {
        Class<?> type = Class.forName(NowBarManager.class.getName() + "$MonitorContent");
        Constructor<?> constructor = type.getDeclaredConstructor(Context.class,
                UsageSnapshot.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(RuntimeEnvironment.getApplication(), snapshot, true);
    }

    private static String text(Object content, String name) throws Exception {
        return (String) value(content, name);
    }

    private static Object value(Object content, String name) throws Exception {
        Field field = content.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(content);
    }
}
