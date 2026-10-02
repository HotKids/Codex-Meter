package dev.bennett.codexmeter;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Offline checks of real JobScheduler registrations and the cache/backoff policy. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = QuotaCardsTest.TestApp.class)
public class WidgetRefreshSchedulerTest {
    private Context context;
    private SharedPreferences preferences;
    private JobScheduler scheduler;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        preferences = ReferenceWidgetPreferences.preferences(context);
        preferences.edit().clear().commit();
        context.getSharedPreferences("codex_meter_home_widget_refresh_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        scheduler = context.getSystemService(JobScheduler.class);
        scheduler.cancelAll();
    }

    @Test public void firstWidgetUsesIndependentAutomaticJobAndDefaultsToThirtyMinutes() {
        addWidget();
        JobInfo job = automaticJob();
        assertNotNull(job);
        assertEquals(WidgetRefreshJobService.class.getName(), job.getService().getClassName());
        assertEquals(30, job.getExtras().getInt("widget_refresh_minutes"));
        assertEquals(0L, job.getMinLatencyMillis());
        assertTrue(job.isPersisted());
        assertEquals(JobInfo.NETWORK_TYPE_ANY, job.getNetworkType());
        assertFalse(job.isPeriodic());
        assertNull(scheduler.getPendingJob(73100));
        assertNull(scheduler.getPendingJob(73101));
    }

    @Test public void manualModeRendersOnUpdateWithoutAutomaticNetworkJob() {
        setMinutes(0);
        addWidget();
        assertNull(automaticJob());
        assertNull(scheduler.getPendingJob(WidgetRefreshScheduler.MANUAL_JOB));
        assertFalse(WidgetRefreshScheduler.shouldFetch(context, false, System.currentTimeMillis()));
    }

    @Test public void fiveMinuteChoiceUsesOneShotRatherThanFifteenMinutePeriodicMinimum() {
        setMinutes(5);
        long now = System.currentTimeMillis();
        cache(now);
        addWidget();
        JobInfo job = automaticJob();
        assertFalse(job.isPeriodic());
        assertTrue(job.getMinLatencyMillis() <= TimeUnit.MINUTES.toMillis(5));
        assertTrue(job.getMinLatencyMillis() >= TimeUnit.MINUTES.toMillis(5) - 10_000L);
        assertEquals(0L, job.getMaxExecutionDelayMillis());
    }

    @Test public void allAutomaticChoicesHonorTimeSinceLatestSharedSnapshot() {
        long now = System.currentTimeMillis();
        for (int minutes : new int[]{5, 15, 30, 60}) {
            long interval = TimeUnit.MINUTES.toMillis(minutes);
            assertEquals(now + interval,
                    WidgetRefreshScheduler.nextAutomaticAt(now, 0L, minutes, now));
            assertEquals(now,
                    WidgetRefreshScheduler.nextAutomaticAt(now - interval, 0L, minutes, now));
        }
        assertEquals(Long.MAX_VALUE,
                WidgetRefreshScheduler.nextAutomaticAt(0L, 0L, 0, now));
    }

    @Test public void appRefreshingBeforeTheJobRunsPreventsAnotherAutomaticFetch() {
        setMinutes(5);
        addWidget();
        long now = System.currentTimeMillis();
        assertTrue(WidgetRefreshScheduler.shouldFetch(context, false, now));
        cache(now);
        assertFalse(WidgetRefreshScheduler.shouldFetch(context, false, now));
        assertTrue(WidgetRefreshScheduler.shouldFetch(context, false,
                now + TimeUnit.MINUTES.toMillis(5)));
        assertTrue(WidgetRefreshScheduler.shouldFetch(context, true, now));
    }

    @Test public void failureBackoffIsIndependentAndSuccessResetsIt() {
        setMinutes(5);
        addWidget();
        long now = System.currentTimeMillis();
        for (int minutes : new int[]{5, 15, 30, 60, 60}) {
            WidgetRefreshScheduler.recordFailure(context, now, "Offline fixture error");
            assertFalse(WidgetRefreshScheduler.shouldFetch(context, false,
                    now + TimeUnit.MINUTES.toMillis(minutes) - 1L));
            assertTrue(WidgetRefreshScheduler.shouldFetch(context, false,
                    now + TimeUnit.MINUTES.toMillis(minutes)));
        }
        assertEquals(0, AppPreferences.getRefreshFailures(context));
        WidgetRefreshScheduler.recordSuccess(context);
        assertTrue(WidgetRefreshScheduler.shouldFetch(context, false, now));
        WidgetRefreshScheduler.recordFailure(context, now, "Offline fixture error");
        assertTrue(WidgetRefreshScheduler.shouldFetch(context, false,
                now + TimeUnit.MINUTES.toMillis(5)));
    }

    @Test public void changingWidgetIntervalAndDisablingItLeaveAppJobsAndPreferencesUntouched() {
        preferences.edit().putInt("refresh_minutes", 60)
                .putBoolean("automatic_refresh", false).commit();
        JobInfo appJob = new JobInfo.Builder(73100,
                new ComponentName(context, UsageRefreshJobService.class))
                .setPeriodic(TimeUnit.HOURS.toMillis(1)).build();
        scheduler.schedule(appJob);
        addWidget();
        setMinutes(15);
        WidgetRefreshScheduler.schedule(context);
        assertEquals(15, automaticJob().getExtras().getInt("widget_refresh_minutes"));
        setMinutes(0);
        WidgetRefreshScheduler.schedule(context);
        assertNull(automaticJob());
        assertEquals(TimeUnit.HOURS.toMillis(1), scheduler.getPendingJob(73100).getIntervalMillis());
        assertEquals(60, AppPreferences.getRefreshMinutes(context));
        assertFalse(AppPreferences.getAutomaticRefresh(context));
    }

    @Test public void repeatedUpdatesDoNotRestartThePendingAutomaticTimer() {
        cache(System.currentTimeMillis());
        int id = addWidget();
        JobInfo original = automaticJob();
        new CodexUsageWidget().onUpdate(context, AppWidgetManager.getInstance(context), new int[]{id});
        assertSame(original, automaticJob());
    }

    @Test public void explicitHomeTapCanForceRefreshInManualMode() {
        setMinutes(0);
        int id = addWidget();
        new WidgetRefreshReceiver().onReceive(context,
                new Intent(AppConstants.ACTION_REFRESH_WIDGET)
                        .setData(Uri.parse("codexmeter://widget/home/v30/" + id + "/refresh")));
        JobInfo manual = scheduler.getPendingJob(WidgetRefreshScheduler.MANUAL_JOB);
        assertNotNull(manual);
        assertTrue(manual.getExtras().getBoolean(WidgetRefreshScheduler.EXTRA_FORCE));
        assertFalse(manual.isPersisted());
        assertNull(automaticJob());
    }

    @Test public void repeatedExplicitTapsCoalesceAndCannotBypassActiveFailureBackoff() {
        setMinutes(0);
        addWidget();
        assertTrue(WidgetRefreshScheduler.scheduleImmediate(context));
        JobInfo first = scheduler.getPendingJob(WidgetRefreshScheduler.MANUAL_JOB);
        assertTrue(WidgetRefreshScheduler.scheduleImmediate(context));
        assertSame(first, scheduler.getPendingJob(WidgetRefreshScheduler.MANUAL_JOB));
        scheduler.cancel(WidgetRefreshScheduler.MANUAL_JOB);
        long now = System.currentTimeMillis();
        WidgetRefreshScheduler.recordFailure(context, now, "Offline fixture error");
        assertFalse(WidgetRefreshScheduler.scheduleImmediate(context));
        assertFalse(WidgetRefreshScheduler.shouldFetch(context, true, now));
        assertFalse(WidgetRefreshScheduler.shouldFetch(context, true,
                now + TimeUnit.MINUTES.toMillis(5) - 1L));
        assertTrue(WidgetRefreshScheduler.shouldFetch(context, true,
                now + TimeUnit.MINUTES.toMillis(5)));
        assertNull(scheduler.getPendingJob(WidgetRefreshScheduler.MANUAL_JOB));
    }

    @Test public void widgetErrorsAreSanitizedAndBecomeObsoleteAfterAnAppRefresh() {
        long now = System.currentTimeMillis();
        cache(now);
        WidgetRefreshScheduler.recordFailure(context, now + 1L,
                "Fixture error Authorization: Bearer offline-fixture-secret");
        assertFalse(WidgetRefreshScheduler.lastError(context).contains("offline-fixture-secret"));
        assertTrue(WidgetRefreshScheduler.lastError(context).contains("Fixture error"));
        assertEquals("", AppPreferences.getLastError(context));
        cache(now + 2L);
        assertEquals("", WidgetRefreshScheduler.lastError(context));
        assertTrue(WidgetRefreshScheduler.shouldFetch(context, true, now + 2L));
    }

    @Test public void firstWidgetFailureAfterAnAppSuccessStartsAgainWithFiveMinuteBackoff() {
        setMinutes(5);
        addWidget();
        long now = System.currentTimeMillis();
        cache(now - TimeUnit.HOURS.toMillis(1));
        for (int attempt = 0; attempt < 4; attempt++) {
            WidgetRefreshScheduler.recordFailure(context, now + attempt, "Previous failure cycle");
        }
        assertFalse(WidgetRefreshScheduler.shouldFetch(context, true,
                now + TimeUnit.MINUTES.toMillis(59)));

        // A successful App refresh writes shared usage without calling widget recordSuccess.
        cache(now + 4L);
        assertEquals("", WidgetRefreshScheduler.lastError(context));
        long newFailureAt = now + 5L;
        WidgetRefreshScheduler.recordFailure(context, newFailureAt, "New failure cycle");
        assertFalse(WidgetRefreshScheduler.shouldFetch(context, false,
                newFailureAt + TimeUnit.MINUTES.toMillis(5) - 1L));
        assertTrue(WidgetRefreshScheduler.shouldFetch(context, false,
                newFailureAt + TimeUnit.MINUTES.toMillis(5)));
    }

    @Test public void signedOutWidgetStateHidesOldWidgetAndAppErrors() {
        assertFalse(SecureTokenStore.isSignedIn(context));
        long now = System.currentTimeMillis();
        cache(now);
        WidgetRefreshScheduler.recordFailure(context, now + 1L, "Old widget fixture error");
        AppPreferences.setLastError(context, "Old App fixture error");
        // Simulate the cache clearing performed on sign-out, without invoking Wear APIs.
        preferences.edit().remove("last_snapshot").commit();
        assertEquals("Old widget fixture error", WidgetRefreshScheduler.lastError(context));
        QuotaCardState state = QuotaCardState.load(context);
        assertFalse(state.signedIn);
        assertNull(state.snapshot);
        assertEquals("", state.error);
    }

    @Test public void homeRoutingPreservesNonHomeAndLockSources() {
        assertTrue(WidgetRefreshReceiver.isHomeSource(Uri.parse("pipi-usage://widget/42/refresh")));
        assertTrue(WidgetRefreshReceiver.isHomeSource(Uri.parse("codexmeter://widget/home/v30/42/root-refresh")));
        assertFalse(WidgetRefreshReceiver.isHomeSource(Uri.parse("codexmeter://widget/lock/v30/42/refresh")));
        assertFalse(WidgetRefreshReceiver.isHomeSource(null));
    }

    @Test public void noHomeWidgetsDoNotScheduleOrCancelExistingAppJobs() {
        JobInfo appJob = new JobInfo.Builder(73100,
                new ComponentName(context, UsageRefreshJobService.class))
                .setPeriodic(TimeUnit.HOURS.toMillis(1)).build();
        scheduler.schedule(appJob);
        WidgetRefreshScheduler.schedule(context);
        assertFalse(WidgetRefreshScheduler.scheduleImmediate(context));
        assertEquals(1, scheduler.getAllPendingJobs().size());
        assertSame(appJob, scheduler.getPendingJob(73100));
    }

    private void setMinutes(int minutes) {
        preferences.edit().putInt("reference_widget_refresh_minutes", minutes).commit();
    }

    private int addWidget() {
        int id = shadowOf(AppWidgetManager.getInstance(context))
                .createWidget(CodexUsageWidget.class, R.layout.widget_rings);
        WidgetRefreshScheduler.schedule(context);
        return id;
    }

    private JobInfo automaticJob() {
        JobInfo first = scheduler.getPendingJob(WidgetRefreshScheduler.AUTOMATIC_JOB_A);
        return first != null ? first : scheduler.getPendingJob(WidgetRefreshScheduler.AUTOMATIC_JOB_B);
    }

    private void cache(long fetchedAt) {
        UsageWindow weekly = new UsageWindow(30, 604800, 0, (fetchedAt + 86_400_000L) / 1000L);
        assertTrue(AppPreferences.saveSnapshot(context,
                new UsageSnapshot("pro", true, false, null, weekly, fetchedAt)));
    }
}
