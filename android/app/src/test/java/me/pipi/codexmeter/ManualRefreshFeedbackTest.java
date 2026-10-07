package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import dev.bennett.codexmeter.UsageSnapshot;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class,
        shadows = SettingsAccountCardTest.InMemoryTokenStore.class)
public class ManualRefreshFeedbackTest {
    private Application app;

    @Before
    public void resetSyntheticSession() {
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE).edit().clear().commit();
        app.getSystemService(JobScheduler.class).cancelAll();
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = new AuthTokens(
                "synthetic-access-not-valid", "synthetic-refresh-not-valid", "",
                Long.MAX_VALUE, "synthetic-account", "account@example.test");
    }

    @Test
    public void widgetRefreshPreservesThatTheRequestWasManual() {
        new WidgetRefreshReceiver().onReceive(app,
                new Intent(AppConstants.ACTION_REFRESH_WIDGET));
        JobInfo job = app.getSystemService(JobScheduler.class).getAllPendingJobs().get(0);
        assertEquals("manual", job.getExtras().getString("reason"));
    }

    @Test
    public void liveNotificationRefreshAlsoPreservesManualFeedback() {
        new NowBarActionReceiver().onReceive(app, new Intent(NowBarManager.ACTION_REFRESH));
        JobInfo job = app.getSystemService(JobScheduler.class).getAllPendingJobs().get(0);
        assertEquals("manual", job.getExtras().getString("reason"));
    }

    @Test
    public void automaticLaunchRefreshCannotReplacePendingManualFeedback() {
        new WidgetRefreshReceiver().onReceive(app, new Intent(AppConstants.ACTION_REFRESH_WIDGET));
        RefreshScheduler.scheduleImmediate(app);
        JobInfo job = app.getSystemService(JobScheduler.class).getAllPendingJobs().get(0);
        assertEquals("manual", job.getExtras().getString("reason"));
    }

    @Test
    public void manualFailureStaysVisibleUntilSuccessEvenWithFreshCachedUsage() throws Exception {
        UsageSnapshot fresh = UsageSnapshot.fromJson(UsageCardFixtures.plus().toJson()
                .put("fetched_at", System.currentTimeMillis() - 1000));
        AppPreferences.saveSnapshot(app, fresh);

        AppPreferences.setLastError(app, "Synthetic manual refresh failure", true);
        assertEquals("Synthetic manual refresh failure", UsageCardState.load(app).refreshError);
        assertEquals(fresh.fetchedAtMillis, AppPreferences.loadSnapshot(app).fetchedAtMillis);
        AppPreferences.setLastError(app, "Synthetic automatic retry failure");
        assertEquals("Synthetic automatic retry failure", UsageCardState.load(app).refreshError);

        AppPreferences.saveSnapshot(app, fresh);
        assertTrue(UsageCardState.load(app).refreshError.isEmpty());
        AppPreferences.setLastError(app, "Synthetic fresh background failure");
        assertTrue(UsageCardState.load(app).refreshError.isEmpty());
    }

    @Test
    public void staleBackgroundFailuresRemainVisible() throws Exception {
        AppPreferences.saveSnapshot(app, UsageSnapshot.fromJson(UsageCardFixtures.plus().toJson()
                .put("fetched_at", System.currentTimeMillis() - 16 * 60_000L)));
        AppPreferences.setLastError(app, "Synthetic stale background failure");
        assertEquals("Synthetic stale background failure", UsageCardState.load(app).refreshError);
    }
}
