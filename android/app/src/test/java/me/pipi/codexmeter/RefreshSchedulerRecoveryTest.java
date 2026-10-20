package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.AlarmManager;
import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsageWindow;
import java.lang.reflect.Field;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowSystemClock;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, shadows = {
        SettingsAccountCardTest.InMemoryTokenStore.class, UsageSessionCancellationTest.Backend.class,
        UsageSessionCancellationTest.TokenEndpoint.class, WidgetRefreshStatusTest.Job.class})
public class RefreshSchedulerRecoveryTest {
    private static final int PERIODIC = 73100;
    private static final int IMMEDIATE = 73101;
    private static final int RESET = 73102;
    private static final int CHAIN_A = 73103;
    private static final int CHAIN_B = 73104;

    private Application app;
    private JobScheduler scheduler;

    @Before
    public void resetSyntheticSession() {
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        app.getSharedPreferences("codex_meter_reset_alerts_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        app.getSharedPreferences("codex_meter_notification_state_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        WidgetRefreshStatus.clear(app);
        scheduler = app.getSystemService(JobScheduler.class);
        scheduler.cancelAll();
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = new AuthTokens(
                "synthetic-access-not-valid", "synthetic-refresh-not-valid", "",
                Long.MAX_VALUE, "synthetic-account", "account@example.test");
        SettingsAccountCardTest.InMemoryTokenStore.saveFailure = null;
        UsageSessionCancellationTest.TokenEndpoint.block = false;
        resetResponse(null);
        long now = System.currentTimeMillis();
        UsageWindow window = new UsageWindow(36, TimeUnit.HOURS.toSeconds(5), 0L,
                TimeUnit.MILLISECONDS.toSeconds(now + TimeUnit.HOURS.toMillis(3)));
        AppPreferences.saveSnapshot(app, new UsageSnapshot("plus", true, false, window,
                null, null, Collections.emptyList(), null, 0, now));
        WidgetRefreshStatusTest.Job.jobs.clear();
        ShadowAlarmManager.setAutoSchedule(false);
        Settings.Global.putInt(app.getContentResolver(), Settings.Global.BOOT_COUNT, 7);
    }

    @After
    public void releaseSyntheticResponse() {
        UsageSessionCancellationTest.Backend.release.countDown();
        WidgetRefreshStatus.clear(app);
        scheduler.cancelAll();
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = null;
        WidgetRefreshStatusTest.Job.jobs.clear();
    }

    @Test
    public void widgetRefreshRestoresMissingAdaptiveChainWhenOnlyResetRemains() {
        widgetRefreshRecovers(true, 30);
    }

    @Test
    public void widgetRefreshRestoresMissingFixedPeriodicJobWhenOnlyResetRemains() {
        widgetRefreshRecovers(false, 30);
    }

    @Test
    public void widgetRefreshRestoresMissingShortIntervalChainWhenOnlyResetRemains() {
        widgetRefreshRecovers(false, 5);
    }

    @Test
    public void immediateRefreshAlsoRestoresEachMissingBackgroundMode() {
        for (int mode = 0; mode < 3; mode++) {
            configure(mode == 0, mode == 2 ? 5 : 30);
            leaveOnlyReset();
            assertTrue(RefreshScheduler.scheduleImmediate(app));
            assertNotNull(scheduler.getPendingJob(IMMEDIATE));
            assertBackgroundMode(mode == 0, mode == 2 ? 5 : 30);
            assertFalse(WidgetRefreshStatus.isRefreshing(app));
        }
    }

    @Test
    public void ensuringHealthySchedulesKeepsTheirGenerationAndOriginalWaitingPeriod() {
        for (int mode = 0; mode < 3; mode++) {
            configure(mode == 0, mode == 2 ? 5 : 30);
            scheduler.cancelAll();
            assertTrue(RefreshScheduler.schedulePeriodic(app));
            JobInfo original = assertBackgroundMode(mode == 0, mode == 2 ? 5 : 30);
            ShadowSystemClock.advanceBy(1, TimeUnit.MINUTES);
            assertTrue(RefreshScheduler.schedulePeriodic(app));
            assertUnchanged(original, assertBackgroundMode(mode == 0, mode == 2 ? 5 : 30));
            assertTrue(RefreshScheduler.scheduleManual(app));
            assertUnchanged(original, assertBackgroundMode(mode == 0, mode == 2 ? 5 : 30));
            assertTrue(RefreshScheduler.scheduleImmediate(app));
            assertUnchanged(original, assertBackgroundMode(mode == 0, mode == 2 ? 5 : 30));
            WidgetRefreshStatus.clear(app);
        }
    }

    @Test
    public void ensuringAHealthyAlternatingSuccessorDoesNotRestartAtTheFirstChainId()
            throws Exception {
        for (boolean automatic : new boolean[] {true, false}) {
            configure(automatic, 5);
            scheduler.cancelAll();
            assertTrue(RefreshScheduler.schedulePeriodic(app));
            JobInfo running = scheduler.getPendingJob(CHAIN_A);
            scheduler.cancel(CHAIN_A);
            runToCompletion(running, null, false, null);
            JobInfo successor = assertBackgroundMode(automatic, 5);
            assertEquals(CHAIN_B, successor.getId());
            ShadowSystemClock.advanceBy(1, TimeUnit.MINUTES);
            assertTrue(RefreshScheduler.schedulePeriodic(app));
            assertUnchanged(successor, assertBackgroundMode(automatic, 5));
        }
    }

    @Test
    public void signedOutEntriesDoNotScheduleBackgroundOrImmediateWork() {
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = null;
        assertTrue(RefreshScheduler.schedulePeriodic(app));
        assertTrue(RefreshScheduler.scheduleManual(app));
        assertTrue(RefreshScheduler.scheduleImmediate(app));
        assertTrue(scheduler.getAllPendingJobs().isEmpty());
        assertFalse(WidgetRefreshStatus.isRefreshing(app));
        assertTrue(Shadows.shadowOf(app.getSystemService(AlarmManager.class))
                .getScheduledAlarms().isEmpty());
    }

    @Test
    public void changingModeOrFixedIntervalReplacesOnlyTheObsoleteBackgroundSchedule() {
        configure(true, 30);
        assertTrue(RefreshScheduler.schedulePeriodic(app));
        JobInfo previous = assertBackgroundMode(true, 30);
        boolean[] automatic = {false, false, false, false, true};
        int[] minutes = {30, 60, 5, 10, 10};
        for (int index = 0; index < minutes.length; index++) {
            configure(automatic[index], minutes[index]);
            assertTrue(RefreshScheduler.schedulePeriodic(app));
            JobInfo replacement = assertBackgroundMode(automatic[index], minutes[index]);
            assertNotEquals(generation(previous), generation(replacement));
            previous = replacement;
        }
    }

    @Test
    public void aRejectedModeChangeKeepsThePreviouslyAcceptedJobAvailableForSystemRetry() {
        configure(true, 30);
        assertTrue(RefreshScheduler.schedulePeriodic(app));
        JobInfo original = assertBackgroundMode(true, 30);
        configure(false, 30);
        Shadows.shadowOf(scheduler).failOnJob(PERIODIC);
        assertFalse(RefreshScheduler.schedulePeriodic(app));
        assertUnchanged(original, scheduler.getPendingJob(original.getId()));
        assertEquals(1, backgroundJobs().size());
    }

    @Test
    public void manualSuccessKeepsTheRecoveredPeriodicJobAndDoesNotRequestSystemRetry()
            throws Exception {
        completedManualKeepsPeriodic(null, false);
    }

    @Test
    public void manualFailureKeepsTheRecoveredPeriodicJobAndItsSystemRetry() throws Exception {
        completedManualKeepsPeriodic(new SocketTimeoutException("Synthetic timeout"), true);
        assertEquals(1, AppPreferences.getRefreshFailures(app));
    }

    @Test
    public void chainedSuccessRequestsSystemRetryWhenTheNextLinkIsRejected() throws Exception {
        completedChain(true, null, true);
    }

    @Test
    public void chainedFailureRequestsSystemRetryWhenTheNextLinkIsRejected() throws Exception {
        completedChain(false, new SocketTimeoutException("Synthetic timeout"), true);
    }

    @Test
    public void anAcceptedSuccessorOwnsTheNextRefreshWithoutAdditionalSystemRetry()
            throws Exception {
        completedChain(true, null, false);
        completedChain(false, new SocketTimeoutException("Synthetic timeout"), false);
    }

    @Test
    public void anOldAdaptiveRunCannotAddASecondChainAfterSwitchingToShortFixedMode()
            throws Exception {
        completedOldGenerationKeepsReplacement(true, 30, false, 5);
    }

    @Test
    public void anOldShortRunCannotReplaceTheNewPeriodicGenerationAfterModeChange()
            throws Exception {
        completedOldGenerationKeepsReplacement(false, 5, false, 30);
    }

    private void widgetRefreshRecovers(boolean automatic, int minutes) {
        configure(automatic, minutes);
        leaveOnlyReset();
        JobInfo reset = scheduler.getPendingJob(RESET);
        new WidgetRefreshReceiver().onReceive(app,
                new Intent(AppConstants.ACTION_REFRESH_WIDGET));
        JobInfo manual = scheduler.getPendingJob(IMMEDIATE);
        assertNotNull(manual);
        assertEquals(RefreshScheduler.REASON_MANUAL, RefreshScheduler.reason(manual.getExtras()));
        assertTrue(WidgetRefreshStatus.isRefreshing(app));
        assertBackgroundMode(automatic, minutes);
        assertUnchanged(reset, scheduler.getPendingJob(RESET));
    }

    private void completedManualKeepsPeriodic(Exception failure, boolean retry) throws Exception {
        configure(false, 30);
        leaveOnlyReset();
        assertTrue(RefreshScheduler.scheduleManual(app));
        JobInfo periodic = scheduler.getPendingJob(PERIODIC);
        runToCompletion(scheduler.getPendingJob(IMMEDIATE), failure, retry, null);
        JobInfo after = assertBackgroundMode(false, 30);
        assertNotNull("The periodic job must already exist while the manual refresh is queued",
                periodic);
        assertUnchanged(periodic, after);
        assertFalse(WidgetRefreshStatus.isRefreshing(app));
    }

    private void completedChain(boolean automatic, Exception failure, boolean rejected)
            throws Exception {
        configure(automatic, 5);
        scheduler.cancelAll();
        assertTrue(RefreshScheduler.schedulePeriodic(app));
        JobInfo running = scheduler.getPendingJob(CHAIN_A);
        scheduler.cancel(CHAIN_A);
        if (rejected) Shadows.shadowOf(scheduler).failOnJob(CHAIN_B);
        runToCompletion(running, failure, rejected, null);
        if (rejected) {
            assertTrue("No accepted successor exists to keep the chain alive",
                    backgroundJobs().isEmpty());
        } else {
            assertEquals(CHAIN_B, assertBackgroundMode(automatic, 5).getId());
        }
    }

    private void completedOldGenerationKeepsReplacement(boolean oldAutomatic, int oldMinutes,
            boolean newAutomatic, int newMinutes) throws Exception {
        configure(oldAutomatic, oldMinutes);
        assertTrue(RefreshScheduler.schedulePeriodic(app));
        JobInfo running = scheduler.getPendingJob(CHAIN_A);
        scheduler.cancel(CHAIN_A);
        JobInfo[] replacement = new JobInfo[1];
        runToCompletion(running, null, false, () -> {
            configure(newAutomatic, newMinutes);
            assertTrue(RefreshScheduler.schedulePeriodic(app));
            replacement[0] = assertBackgroundMode(newAutomatic, newMinutes);
        });
        assertUnchanged(replacement[0], assertBackgroundMode(newAutomatic, newMinutes));
    }

    private void runToCompletion(JobInfo job, Exception failure, boolean retry,
            Runnable whileRunning) throws Exception {
        assertNotNull(job);
        resetResponse(failure);
        ServiceController<UsageRefreshJobService> controller = Robolectric
                .buildService(UsageRefreshJobService.class).create();
        try {
            UsageRefreshJobService service = controller.get();
            JobParameters parameters = Shadow.newInstanceOf(JobParameters.class);
            WidgetRefreshStatusTest.Job.jobs.put(parameters, job);
            assertTrue(service.onStartJob(parameters));
            assertTrue("The real refresh must reach the synthetic transport",
                    UsageSessionCancellationTest.Backend.entered.await(5, TimeUnit.SECONDS));
            Future<?> completion = activeTask(service, job.getId());
            if (whileRunning != null) whileRunning.run();
            UsageSessionCancellationTest.Backend.release.countDown();
            completion.get(5, TimeUnit.SECONDS);
            assertTrue(Shadows.shadowOf(service).getIsJobFinished());
            assertEquals("A missing successor must fall back to JobScheduler retry", retry,
                    Shadows.shadowOf(service).getIsRescheduleNeeded());
        } finally {
            UsageSessionCancellationTest.Backend.release.countDown();
            controller.destroy();
            assertTrue("The synthetic worker must stop before shared fixtures are cleared",
                    executor(controller.get()).awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private void leaveOnlyReset() {
        scheduler.cancelAll();
        WidgetRefreshStatus.clear(app);
        assertTrue(RefreshScheduler.scheduleAtNextReset(app, AppPreferences.loadSnapshot(app)));
        assertEquals(1, scheduler.getAllPendingJobs().size());
        assertEquals(RESET, scheduler.getAllPendingJobs().get(0).getId());
    }

    private void configure(boolean automatic, int minutes) {
        AppPreferences.setAutomaticRefresh(app, automatic);
        AppPreferences.setRefreshMinutes(app, minutes);
    }

    private JobInfo assertBackgroundMode(boolean automatic, int minutes) {
        List<JobInfo> jobs = backgroundJobs();
        assertEquals("Exactly one recurring refresh must remain scheduled", 1, jobs.size());
        JobInfo job = jobs.get(0);
        String reason = RefreshScheduler.reason(job.getExtras());
        if (automatic || minutes < 15) {
            assertTrue(job.getId() == CHAIN_A || job.getId() == CHAIN_B);
            assertFalse(job.isPeriodic());
            assertEquals(automatic ? RefreshScheduler.REASON_ADAPTIVE
                    : RefreshScheduler.REASON_SHORT_PERIODIC, reason);
            assertTrue(job.getMinLatencyMillis() > 0L);
            if (!automatic) {
                assertEquals(TimeUnit.MINUTES.toMillis(minutes), job.getMinLatencyMillis());
            }
        } else {
            assertEquals(PERIODIC, job.getId());
            assertTrue(job.isPeriodic());
            assertTrue(job.isPersisted());
            assertEquals("periodic", reason);
            assertEquals(TimeUnit.MINUTES.toMillis(minutes), job.getIntervalMillis());
        }
        return job;
    }

    private List<JobInfo> backgroundJobs() {
        List<JobInfo> jobs = new ArrayList<>();
        for (JobInfo job : scheduler.getAllPendingJobs()) {
            if (job.getId() == PERIODIC || job.getId() == CHAIN_A || job.getId() == CHAIN_B) {
                jobs.add(job);
            }
        }
        return jobs;
    }

    private static void assertUnchanged(JobInfo expected, JobInfo actual) {
        assertNotNull(actual);
        assertEquals(expected.getId(), actual.getId());
        assertEquals("An existing healthy job must keep its original generation",
                generation(expected), generation(actual));
        assertEquals(expected.getMinLatencyMillis(), actual.getMinLatencyMillis());
        assertEquals(expected.getMaxExecutionDelayMillis(), actual.getMaxExecutionDelayMillis());
        assertEquals(expected.getIntervalMillis(), actual.getIntervalMillis());
    }

    private static String generation(JobInfo job) {
        return RefreshScheduler.generation(job.getExtras());
    }

    private static void resetResponse(Exception failure) {
        UsageSessionCancellationTest.Backend.entered = new CountDownLatch(1);
        UsageSessionCancellationTest.Backend.release = new CountDownLatch(1);
        UsageSessionCancellationTest.Backend.operation = "usage";
        UsageSessionCancellationTest.Backend.failure = failure;
    }

    private static ExecutorService executor(UsageRefreshJobService service) throws Exception {
        Field field = UsageRefreshJobService.class.getDeclaredField("executor");
        field.setAccessible(true);
        return (ExecutorService) field.get(service);
    }

    private static Future<?> activeTask(UsageRefreshJobService service, int jobId) throws Exception {
        Field activeField = UsageRefreshJobService.class.getDeclaredField("active");
        activeField.setAccessible(true);
        Object run = ((Map<?, ?>) activeField.get(service)).get(jobId);
        assertNotNull(run);
        Field taskField = run.getClass().getDeclaredField("task");
        taskField.setAccessible(true);
        return (Future<?>) taskField.get(run);
    }
}
