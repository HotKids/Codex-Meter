package me.pipi.codexmeter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowAppWidgetManager;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "zh-rCN-mdpi", shadows = {
        SettingsAccountCardTest.InMemoryTokenStore.class, WidgetUpdateOrderingTest.Host.class})
public class WidgetUpdateOrderingTest {
    private static final int WIDGET_ID = 887;

    @Test
    public void completedRefreshWinsOverAnOlderBusyUpdateWaitingToPublish() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = new AuthTokens(
                "synthetic-access-not-valid", "synthetic-refresh-not-valid", "",
                Long.MAX_VALUE, "synthetic-account", "account@example.test");
        WidgetRefreshStatus.clear(app);
        ShadowAlarmManager.setAutoSchedule(false);
        Settings.Global.putInt(app.getContentResolver(), Settings.Global.BOOT_COUNT, 7);
        AppPreferences.saveSnapshot(app, UsageCardFixtures.plus());
        JobScheduler scheduler = app.getSystemService(JobScheduler.class);
        scheduler.cancelAll();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        Host host = (Host) Shadows.shadowOf(manager);
        AppWidgetProviderInfo provider = new AppWidgetProviderInfo();
        provider.provider = new ComponentName(app, CodexUsageWidget.class);
        host.addInstalledProvider(provider);
        host.bindAppWidgetId(WIDGET_ID, provider.provider);
        Bundle size = new Bundle();
        size.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 350);
        size.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 350);
        size.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 170);
        size.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 170);
        manager.updateAppWidgetOptions(WIDGET_ID, size);

        ExecutorService updates = Executors.newFixedThreadPool(2);
        Future<?> older = null;
        Future<?> terminal = null;
        CountDownLatch stateEnded = new CountDownLatch(1);
        try {
            assertTrue(RefreshScheduler.scheduleManual(app));
            JobInfo manual = scheduler.getPendingJob(73101);
            WidgetRefreshStatus.started(app, manual.getExtras());
            assertTrue(WidgetRefreshStatus.isRefreshing(app));
            host.holdNext.set(true);
            older = updates.submit(() -> WidgetRenderer.update(app, manager, WIDGET_ID));
            assertTrue("The older update must reach the real host publication boundary",
                    host.olderReady.await(5, TimeUnit.SECONDS));
            View captured = host.olderViews.apply(app, new FrameLayout(app));
            assertNotNull("The captured production RemoteViews must contain the busy spinner",
                    captured.findViewById(R.id.md_refresh_spinner));

            terminal = updates.submit(() -> {
                assertTrue(WidgetRefreshStatus.finished(app, manual.getExtras()));
                stateEnded.countDown();
                WidgetRenderer.update(app, manager, WIDGET_ID);
            });
            assertTrue("The refresh must end while its older update is still waiting",
                    stateEnded.await(5, TimeUnit.SECONDS));
            assertFalse(WidgetRefreshStatus.isRefreshing(app));
            // A serialized renderer may defer this publication until the older update releases.
            // Without serialization it publishes now, and the older busy view overwrites it.
            host.terminalPublished.await(2, TimeUnit.SECONDS);
            host.releaseOlder.countDown();
            older.get(5, TimeUnit.SECONDS);
            terminal.get(5, TimeUnit.SECONDS);

            View published = host.getViewFor(WIDGET_ID);
            assertNotNull(published);
            assertNull("A completed refresh must not leave an older spinner on the host",
                    published.findViewById(R.id.md_refresh_spinner));
            assertTrue("The final host view must show the resting refresh control",
                    published.findViewById(R.id.md_refresh).getVisibility() == View.VISIBLE);
        } finally {
            host.releaseOlder.countDown();
            if (older != null) older.cancel(true);
            if (terminal != null) terminal.cancel(true);
            updates.shutdownNow();
            assertTrue("Update workers must be released even when the regression fails",
                    updates.awaitTermination(5, TimeUnit.SECONDS));
            WidgetRefreshStatus.clear(app);
            scheduler.cancelAll();
            SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = null;
        }
    }

    @Implements(AppWidgetManager.class)
    public static class Host extends ShadowAppWidgetManager {
        final AtomicBoolean holdNext = new AtomicBoolean();
        final CountDownLatch olderReady = new CountDownLatch(1);
        final CountDownLatch releaseOlder = new CountDownLatch(1);
        final CountDownLatch terminalPublished = new CountDownLatch(1);
        volatile RemoteViews olderViews;

        @Implementation
        protected void updateAppWidget(int appWidgetId, RemoteViews views) {
            boolean held = appWidgetId == WIDGET_ID && holdNext.compareAndSet(true, false);
            if (held) {
                olderViews = views;
                olderReady.countDown();
                try {
                    assertTrue("The paused host update must have a finite release",
                            releaseOlder.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("The paused host update was interrupted", exception);
                }
            }
            super.updateAppWidget(appWidgetId, views);
            if (appWidgetId == WIDGET_ID && !held && olderViews != null) {
                terminalPublished.countDown();
            }
        }
    }
}
