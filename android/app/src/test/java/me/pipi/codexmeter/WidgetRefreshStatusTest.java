package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.AlarmManager;
import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.provider.Settings;
import java.lang.reflect.Field;
import java.net.SocketTimeoutException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowSystemClock;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, shadows = {
        SettingsAccountCardTest.InMemoryTokenStore.class, UsageSessionCancellationTest.Backend.class,
        UsageSessionCancellationTest.TokenEndpoint.class, WidgetRefreshStatusTest.Job.class})
public class WidgetRefreshStatusTest {
    private Application app;
    private JobScheduler scheduler;

    @Before
    public void resetSyntheticSession() {
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        WidgetRefreshStatus.clear(app);
        scheduler = app.getSystemService(JobScheduler.class);
        scheduler.cancelAll();
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = new AuthTokens(
                "synthetic-access-not-valid", "synthetic-refresh-not-valid", "",
                Long.MAX_VALUE, "synthetic-account", "account@example.test");
        UsageSessionCancellationTest.Backend.entered = new CountDownLatch(1);
        UsageSessionCancellationTest.Backend.release = new CountDownLatch(1);
        UsageSessionCancellationTest.Backend.operation = "usage";
        UsageSessionCancellationTest.Backend.failure = null;
        UsageSessionCancellationTest.TokenEndpoint.block = false;
        AppPreferences.saveSnapshot(app, UsageCardFixtures.plus());
        Job.jobs.clear();
        ShadowAlarmManager.setAutoSchedule(false);
        Settings.Global.putInt(app.getContentResolver(), Settings.Global.BOOT_COUNT, 7);
    }

    @Test
    public void acceptedManualRequestIsRefreshingWhileQueuedAndHasAnInexactWatchdog() {
        JobInfo job = manual();
        assertTrue(WidgetRefreshStatus.isRefreshing(app));
        assertFalse(RefreshScheduler.generation(job.getExtras()).isEmpty());
        ShadowAlarmManager.ScheduledAlarm alarm = checkAlarm();
        assertEquals(AlarmManager.ELAPSED_REALTIME, alarm.type);
        assertTrue(alarm.getWindowLengthMs() != ShadowAlarmManager.WINDOW_EXACT);
        assertEquals(WidgetRefreshStatus.ACTION_RECONCILE,
                Shadows.shadowOf(alarm.operation).getSavedIntent().getAction());
    }

    @Test
    public void queuedFeedbackExpiresAtFiveMinutesWithoutCancellingItsPendingJob() {
        JobInfo job = manual();
        ShadowSystemClock.advanceBy(TimeUnit.MINUTES.toMillis(5) - 1L,
                TimeUnit.MILLISECONDS);
        assertTrue(WidgetRefreshStatus.isRefreshing(app));
        ShadowSystemClock.advanceBy(1L, TimeUnit.MILLISECONDS);
        assertFalse(WidgetRefreshStatus.isRefreshing(app));
        assertTrue(WidgetRefreshStatus.reconcile(app));
        assertEquals(RefreshScheduler.generation(job.getExtras()),
                RefreshScheduler.generation(scheduler.getPendingJob(job.getId()).getExtras()));
        assertTrue(Shadows.shadowOf(app.getSystemService(AlarmManager.class))
                .getScheduledAlarms().isEmpty());
    }

    @Test
    public void expiredQueuedFeedbackLeavesTheJobAndItsFailureRetryIntact() throws Exception {
        JobInfo job = manual();
        ShadowSystemClock.advanceBy(5L, TimeUnit.MINUTES);
        assertTrue(WidgetRefreshStatus.reconcile(app));
        runCompletedManual(job, new SocketTimeoutException("Synthetic timeout"), true, false);
        assertEquals(1, AppPreferences.getRefreshFailures(app));
        assertEquals("Synthetic timeout", AppPreferences.getVisibleRefreshError(app));
    }

    @Test
    public void aRunningRequestDoesNotExpireAtItsFormerQueuedDeadline() throws Exception {
        ServiceController<UsageRefreshJobService> controller = Robolectric
                .buildService(UsageRefreshJobService.class).create();
        try {
            UsageRefreshJobService service = controller.get();
            assertTrue(service.onStartJob(parameters(manual())));
            assertTrue(UsageSessionCancellationTest.Backend.entered.await(5, TimeUnit.SECONDS));
            ShadowSystemClock.advanceBy(6L, TimeUnit.MINUTES);
            assertTrue(WidgetRefreshStatus.isRefreshing(app));
            assertFalse(WidgetRefreshStatus.reconcile(app));
            UsageSessionCancellationTest.Backend.release.countDown();
            drain(service);
            assertFalse(WidgetRefreshStatus.isRefreshing(app));
            assertTrue(Shadows.shadowOf(service).getIsJobFinished());
            assertFalse(Shadows.shadowOf(service).getIsRescheduleNeeded());
        } finally {
            UsageSessionCancellationTest.Backend.release.countDown();
            controller.destroy();
        }
    }

    @Test
    public void anOldQueuedDeadlineCannotExpireOrFinishTheReplacement() {
        JobInfo first = manual();
        ShadowSystemClock.advanceBy(4L, TimeUnit.MINUTES);
        JobInfo next = manual();
        ShadowSystemClock.advanceBy(1L, TimeUnit.MINUTES);
        assertFalse(WidgetRefreshStatus.finished(app, first.getExtras()));
        assertTrue(WidgetRefreshStatus.isRefreshing(app));
        assertFalse(WidgetRefreshStatus.reconcile(app));
        assertEquals(RefreshScheduler.generation(next.getExtras()),
                RefreshScheduler.generation(scheduler.getPendingJob(next.getId()).getExtras()));
        assertTrue(WidgetRefreshStatus.finished(app, next.getExtras()));
    }

    @Test
    public void watchdogRejectsQueuedFeedbackFromAnEarlierBootWithoutCancellingTheJob() {
        JobInfo job = manual();
        Settings.Global.putInt(app.getContentResolver(), Settings.Global.BOOT_COUNT, 8);
        assertFalse(WidgetRefreshStatus.isRefreshing(app));
        assertTrue(WidgetRefreshStatus.reconcile(app));
        assertEquals(RefreshScheduler.generation(job.getExtras()),
                RefreshScheduler.generation(scheduler.getPendingJob(job.getId()).getExtras()));
    }

    @Test
    public void watchdogRejectsAQueuedDeadlineFromARewoundElapsedClock() {
        JobInfo job = manual();
        app.getSharedPreferences(WidgetRefreshStatus.PREFS, Context.MODE_PRIVATE).edit()
                .putLong("queued_until", SystemClock.elapsedRealtime()
                        + TimeUnit.MINUTES.toMillis(5) + 1L).commit();
        assertFalse(WidgetRefreshStatus.isRefreshing(app));
        assertTrue(WidgetRefreshStatus.reconcile(app));
        assertEquals(RefreshScheduler.generation(job.getExtras()),
                RefreshScheduler.generation(scheduler.getPendingJob(job.getId()).getExtras()));
    }

    @Test
    public void signedOutNoOpDoesNotStartRefreshing() {
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = null;
        assertTrue(RefreshScheduler.scheduleManual(app));
        assertFalse(WidgetRefreshStatus.isRefreshing(app));
        assertTrue(scheduler.getAllPendingJobs().isEmpty());
    }

    @Test
    public void rejectedScheduleDoesNotStartRefreshing() {
        Shadows.shadowOf(scheduler).failOnJob(73101);
        assertFalse(RefreshScheduler.scheduleManual(app));
        assertFalse(WidgetRefreshStatus.isRefreshing(app));
        assertTrue(Shadows.shadowOf(app.getSystemService(AlarmManager.class))
                .getScheduledAlarms().isEmpty());
    }

    @Test
    public void aRejectedReplacementKeepsThePreviouslyAcceptedRequest() {
        JobInfo first = manual();
        Shadows.shadowOf(scheduler).failOnJob(73101);
        assertFalse(RefreshScheduler.scheduleManual(app));
        assertTrue(WidgetRefreshStatus.isRefreshing(app));
        assertEquals(RefreshScheduler.generation(first.getExtras()),
                RefreshScheduler.generation(scheduler.getPendingJob(73101).getExtras()));
    }

    @Test
    public void ordinaryLaunchAndPeriodicJobsDoNotStartManualFeedback() {
        assertTrue(RefreshScheduler.scheduleImmediate(app));
        assertTrue(RefreshScheduler.schedulePeriodic(app));
        assertFalse(WidgetRefreshStatus.isRefreshing(app));
    }

    @Test
    public void automaticReplacementKeepsBusyFeedbackButCannotReviveAFinishedRequest() {
        JobInfo first = manual();
        assertTrue(RefreshScheduler.scheduleImmediate(app));
        JobInfo next = scheduler.getPendingJob(73101);
        assertEquals(RefreshScheduler.REASON_MANUAL, RefreshScheduler.reason(next.getExtras()));
        assertNotEquals(RefreshScheduler.generation(first.getExtras()),
                RefreshScheduler.generation(next.getExtras()));
        assertFalse(WidgetRefreshStatus.finished(app, first.getExtras()));
        assertTrue(WidgetRefreshStatus.isRefreshing(app));
        assertTrue(WidgetRefreshStatus.finished(app, next.getExtras()));
        assertTrue(RefreshScheduler.scheduleImmediate(app));
        assertEquals(RefreshScheduler.REASON_MANUAL,
                RefreshScheduler.reason(scheduler.getPendingJob(73101).getExtras()));
        assertFalse(WidgetRefreshStatus.isRefreshing(app));
    }

    @Test
    public void manualSuccessStopsFeedbackAndKeepsTheExistingCompletionSemantics() throws Exception {
        runCompletedManual(null, false);
    }

    @Test
    public void manualFailureStopsFeedbackAndKeepsTheExistingSystemRetry() throws Exception {
        runCompletedManual(new SocketTimeoutException("Synthetic timeout"), true);
        assertEquals(1, AppPreferences.getRefreshFailures(app));
        assertEquals("Synthetic timeout", AppPreferences.getVisibleRefreshError(app));
    }

    private void runCompletedManual(Exception failure, boolean retry) throws Exception {
        runCompletedManual(manual(), failure, retry, true);
    }

    private void runCompletedManual(JobInfo job, Exception failure, boolean retry,
            boolean feedback) throws Exception {
        UsageSessionCancellationTest.Backend.failure = failure;
        ServiceController<UsageRefreshJobService> controller = Robolectric
                .buildService(UsageRefreshJobService.class).create();
        try {
            UsageRefreshJobService service = controller.get();
            assertTrue(service.onStartJob(parameters(job)));
            assertTrue(UsageSessionCancellationTest.Backend.entered.await(5, TimeUnit.SECONDS));
            assertEquals(feedback, WidgetRefreshStatus.isRefreshing(app));
            UsageSessionCancellationTest.Backend.release.countDown();
            drain(service);
            assertFalse(WidgetRefreshStatus.isRefreshing(app));
            assertTrue(Shadows.shadowOf(service).getIsJobFinished());
            assertEquals(retry, Shadows.shadowOf(service).getIsRescheduleNeeded());
        } finally {
            UsageSessionCancellationTest.Backend.release.countDown();
            controller.destroy();
        }
    }

    @Test
    public void stopParametersFromAnOlderScheduleCannotCancelTheNewRun() throws Exception {
        ServiceController<UsageRefreshJobService> controller = Robolectric
                .buildService(UsageRefreshJobService.class).create();
        try {
            UsageRefreshJobService service = controller.get();
            JobInfo first = manual();
            assertTrue(service.onStartJob(parameters(first)));
            assertTrue(UsageSessionCancellationTest.Backend.entered.await(5, TimeUnit.SECONDS));
            JobInfo next = manual();
            assertTrue(service.onStartJob(parameters(next)));
            assertTrue(service.onStopJob(parameters(first)));
            assertTrue("Old stop cannot clear the newer queued/running request",
                    WidgetRefreshStatus.isRefreshing(app));
            UsageSessionCancellationTest.Backend.release.countDown();
            drain(service);
            assertFalse(WidgetRefreshStatus.isRefreshing(app));
            assertTrue("The replacement must still be allowed to finish",
                    Shadows.shadowOf(service).getIsJobFinished());
            assertEquals(0, AppPreferences.getRefreshFailures(app));
        } finally {
            UsageSessionCancellationTest.Backend.release.countDown();
            controller.destroy();
        }
    }

    @Test
    public void aStoppedRunClearsFeedbackBeforeItsLateResponse() throws Exception {
        ServiceController<UsageRefreshJobService> controller = Robolectric
                .buildService(UsageRefreshJobService.class).create();
        try {
            UsageRefreshJobService service = controller.get();
            JobInfo job = manual();
            assertTrue(service.onStartJob(parameters(job)));
            assertTrue(UsageSessionCancellationTest.Backend.entered.await(5, TimeUnit.SECONDS));
            assertTrue(service.onStopJob(parameters(job)));
            assertFalse(WidgetRefreshStatus.isRefreshing(app));
            UsageSessionCancellationTest.Backend.release.countDown();
            drain(service);
            assertEquals(0, AppPreferences.getRefreshFailures(app));
            assertEquals("", AppPreferences.getLastError(app));
        } finally {
            UsageSessionCancellationTest.Backend.release.countDown();
            controller.destroy();
        }
    }

    @Test
    public void stoppingTheReplacementBeforeItsWorkerStartsStillEndsItsFeedback() throws Exception {
        ServiceController<UsageRefreshJobService> controller = Robolectric
                .buildService(UsageRefreshJobService.class).create();
        try {
            UsageRefreshJobService service = controller.get();
            assertTrue(service.onStartJob(parameters(manual())));
            assertTrue(UsageSessionCancellationTest.Backend.entered.await(5, TimeUnit.SECONDS));
            JobInfo queued = manual();
            assertTrue(service.onStartJob(parameters(queued)));
            assertTrue(service.onStopJob(parameters(queued)));
            assertFalse(WidgetRefreshStatus.isRefreshing(app));
            UsageSessionCancellationTest.Backend.release.countDown();
            drain(service);
            assertFalse(Shadows.shadowOf(service).getIsJobFinished());
            assertEquals(0, AppPreferences.getRefreshFailures(app));
        } finally {
            UsageSessionCancellationTest.Backend.release.countDown();
            controller.destroy();
        }
    }

    @Test
    public void serviceDestructionClearsFeedbackWithoutWaitingForHttp() throws Exception {
        ServiceController<UsageRefreshJobService> controller = Robolectric
                .buildService(UsageRefreshJobService.class).create();
        UsageRefreshJobService service = controller.get();
        try {
            assertTrue(service.onStartJob(parameters(manual())));
            assertTrue(UsageSessionCancellationTest.Backend.entered.await(5, TimeUnit.SECONDS));
        } finally {
            controller.destroy();
            assertFalse(WidgetRefreshStatus.isRefreshing(app));
            UsageSessionCancellationTest.Backend.release.countDown();
            assertTrue(executor(service).awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void cancelAllClearsQueuedFeedbackAndItsWatchdog() {
        manual();
        RefreshScheduler.cancelAll(app);
        assertFalse(WidgetRefreshStatus.isRefreshing(app));
        assertTrue(scheduler.getAllPendingJobs().isEmpty());
        assertTrue(Shadows.shadowOf(app.getSystemService(AlarmManager.class))
                .getScheduledAlarms().isEmpty());
    }

    @Test
    public void watchdogClearsALostQueuedJobWithoutSchedulingNewBusinessWork() {
        JobInfo job = manual();
        scheduler.cancel(job.getId());
        new WidgetRefreshReceiver().onReceive(app, new Intent(WidgetRefreshStatus.ACTION_RECONCILE));
        assertFalse(WidgetRefreshStatus.isRefreshing(app));
        assertTrue(scheduler.getAllPendingJobs().isEmpty());
    }

    @Test
    public void watchdogClearsARunFromTheDeadProcessButKeepsTheSystemJob() {
        JobInfo job = manual();
        WidgetRefreshStatus.started(app, job.getExtras());
        app.getSharedPreferences(WidgetRefreshStatus.PREFS, Context.MODE_PRIVATE).edit()
                .putString("owner", "synthetic-previous-process").commit();
        new WidgetRefreshReceiver().onReceive(app, new Intent(WidgetRefreshStatus.ACTION_RECONCILE));
        assertFalse(WidgetRefreshStatus.isRefreshing(app));
        assertEquals(RefreshScheduler.generation(job.getExtras()),
                RefreshScheduler.generation(scheduler.getPendingJob(job.getId()).getExtras()));
    }

    private JobInfo manual() {
        assertTrue(RefreshScheduler.scheduleManual(app));
        return scheduler.getPendingJob(73101);
    }

    private ShadowAlarmManager.ScheduledAlarm checkAlarm() {
        return Shadows.shadowOf(app.getSystemService(AlarmManager.class)).getScheduledAlarms()
                .stream().filter(alarm -> WidgetRefreshStatus.ACTION_RECONCILE.equals(
                        Shadows.shadowOf(alarm.operation).getSavedIntent().getAction()))
                .findFirst().orElseThrow();
    }

    private static JobParameters parameters(JobInfo job) {
        JobParameters params = Shadow.newInstanceOf(JobParameters.class);
        Job.jobs.put(params, job);
        return params;
    }

    private static ExecutorService executor(UsageRefreshJobService service) throws Exception {
        Field field = UsageRefreshJobService.class.getDeclaredField("executor");
        field.setAccessible(true);
        return (ExecutorService) field.get(service);
    }

    private static void drain(UsageRefreshJobService service) throws Exception {
        executor(service).submit(() -> {}).get(5, TimeUnit.SECONDS);
    }

    @Implements(JobParameters.class)
    public static class Job {
        static final Map<JobParameters, JobInfo> jobs = new IdentityHashMap<>();
        @RealObject private JobParameters parameters;
        @Implementation protected int getJobId() { return jobs.get(parameters).getId(); }
        @Implementation protected PersistableBundle getExtras() {
            return jobs.get(parameters).getExtras();
        }
    }
}
