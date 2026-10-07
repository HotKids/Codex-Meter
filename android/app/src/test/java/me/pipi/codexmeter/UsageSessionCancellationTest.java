package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.NotificationManager;
import android.app.job.JobParameters;
import android.content.Context;
import android.os.Parcel;
import android.os.PersistableBundle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;

/** Late synthetic responses exercise the real sign-out and job cancellation boundaries. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, shadows = {
        SettingsAccountCardTest.InMemoryTokenStore.class, UsageSessionCancellationTest.Backend.class,
        UsageSessionCancellationTest.TokenEndpoint.class, UsageSessionCancellationTest.Job.class})
public class UsageSessionCancellationTest {
    private Application app;

    @Before
    public void resetSyntheticSession() {
        app = RuntimeEnvironment.getApplication();
        for (String name : new String[] {"codex_meter_settings_v1", "codex_meter_reset_alerts_v1",
                "codex_meter_notification_state_v1"}) {
            app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit();
        }
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = tokens(Long.MAX_VALUE);
        SettingsAccountCardTest.InMemoryTokenStore.saveCalls = 0;
        SettingsAccountCardTest.InMemoryTokenStore.clearCalls = 0;
        SettingsAccountCardTest.InMemoryTokenStore.saveFailure = null;
        Backend.entered = new CountDownLatch(1);
        Backend.release = new CountDownLatch(1);
        Backend.operation = "usage";
        Backend.failure = null;
        TokenEndpoint.block = false;
        TokenEndpoint.entered = new CountDownLatch(1);
        TokenEndpoint.release = new CountDownLatch(1);
        AppPreferences.saveSnapshot(app, UsageCardFixtures.plus());
        ResetAlertPreferences.save(app, ResetAlertPreferences.STYLE_NOTIFICATION,
                ResetAlertPreferences.METRIC_BOTH, 25);
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        Shadows.shadowOf(app.getSystemService(NotificationManager.class))
                .setNotificationsEnabled(true);
    }

    @Test
    public void signOutDoesNotWaitForHttpOrAllowALateUsageSnapshot() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> request = worker.submit(() -> {
                try { UsageApi.refreshAndCache(app); }
                catch (Exception exception) { throw new RuntimeException(exception); }
            });
            assertTrue(Backend.entered.await(5, TimeUnit.SECONDS));
            signOutThroughSettings();
            assertEquals("Sign-out must finish while HTTP is still blocked", 1,
                    Backend.release.getCount());
            Backend.release.countDown();
            awaitRequest(request);
            assertSignedOutAndEmpty();
        } finally {
            Backend.release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void aRefreshTokenResponseCannotRestoreTheSignedOutAccount() throws Exception {
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = tokens(1L);
        TokenEndpoint.block = true;
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> request = worker.submit(() -> {
                try { UsageApi.refreshAndCache(app); }
                catch (Exception exception) { throw new RuntimeException(exception); }
            });
            assertTrue(TokenEndpoint.entered.await(5, TimeUnit.SECONDS));
            signOutThroughSettings();
            TokenEndpoint.release.countDown();
            Backend.release.countDown();
            awaitRequest(request);
            assertSignedOutAndEmpty();
            assertEquals(0, SettingsAccountCardTest.InMemoryTokenStore.saveCalls);
        } finally {
            TokenEndpoint.release.countDown();
            Backend.release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void signingInDoesNotWaitForHttpOrAllowThePreviousAccountResponse() throws Exception {
        AuthTokens replacement = new AuthTokens("synthetic-new-access-not-valid",
                "synthetic-new-refresh-not-valid", "", Long.MAX_VALUE,
                "synthetic-new-account", "replacement@example.test");
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> request = workers.submit(() -> {
                try { UsageApi.refreshAndCache(app); }
                catch (Exception exception) { throw new RuntimeException(exception); }
            });
            assertTrue(Backend.entered.await(5, TimeUnit.SECONDS));
            Future<?> signIn = workers.submit(() -> {
                try { UsageApi.saveTokens(app, replacement); }
                catch (Exception exception) { throw new RuntimeException(exception); }
            });
            signIn.get(1, TimeUnit.SECONDS);
            assertEquals("Sign-in must finish while the old HTTP request is still blocked", 1,
                    Backend.release.getCount());
            assertSame(replacement, SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens);
            assertNull(AppPreferences.loadSnapshot(app));
            Backend.release.countDown();
            awaitRequest(request);
            assertSame(replacement, SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens);
            assertNull(AppPreferences.loadSnapshot(app));
            assertNull(AppPreferences.loadResetCredits(app));
        } finally {
            Backend.release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void anIndependentResetCreditResponseCannotRestoreTheSignedOutCache() throws Exception {
        Backend.operation = "reset_credit_list";
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> request = worker.submit(() -> {
                try { ResetCreditApi.refreshAndCache(app); }
                catch (Exception exception) { throw new RuntimeException(exception); }
            });
            assertTrue(Backend.entered.await(5, TimeUnit.SECONDS));
            signOutThroughSettings();
            Backend.release.countDown();
            awaitRequest(request);
            assertSignedOutAndEmpty();
        } finally {
            Backend.release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void aStoppedJobCannotCommitALateSuccessfulUsageResponse() throws Exception {
        runStoppedJob(null);
        assertEquals("The original cached snapshot must remain untouched",
                UsageCardFixtures.plus().fetchedAtMillis,
                AppPreferences.loadSnapshot(app).fetchedAtMillis);
        assertEquals(0, AppPreferences.getRefreshFailures(app));
        assertEquals("", AppPreferences.getLastError(app));
    }

    @Test
    public void aStoppedJobDoesNotRecordItsLateNetworkFailure() throws Exception {
        runStoppedJob(new SocketTimeoutException("Synthetic late timeout"));
        assertEquals(0, AppPreferences.getRefreshFailures(app));
        assertEquals("", AppPreferences.getLastError(app));
    }

    private void runStoppedJob(Exception failure) throws Exception {
        Backend.failure = failure;
        ServiceController<UsageRefreshJobService> controller = Robolectric
                .buildService(UsageRefreshJobService.class).create();
        try {
            UsageRefreshJobService service = controller.get();
            JobParameters params = Shadow.newInstanceOf(JobParameters.class);
            assertTrue(service.onStartJob(params));
            assertTrue(Backend.entered.await(5, TimeUnit.SECONDS));
            Parcel parcel = Parcel.obtain();
            try {
                params.writeToParcel(parcel, 0);
                parcel.setDataPosition(0);
                JobParameters stopParams = JobParameters.CREATOR.createFromParcel(parcel);
                assertTrue("Binder delivers a separate stop-parameters object",
                        params != stopParams);
                service.onStopJob(stopParams);
            } finally {
                parcel.recycle();
            }
            Backend.release.countDown();
            Field field = UsageRefreshJobService.class.getDeclaredField("executor");
            field.setAccessible(true);
            ((ExecutorService) field.get(service)).submit(() -> {}).get(5, TimeUnit.SECONDS);
        } finally {
            Backend.release.countDown();
            controller.destroy();
        }
    }

    private void signOutThroughSettings() throws Exception {
        try (ActivityController<SettingsActivity> controller = Robolectric
                .buildActivity(SettingsActivity.class).setup()) {
            SettingsRootFragment fragment = (SettingsRootFragment) controller.get()
                    .getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            Method method = SettingsRootFragment.class.getDeclaredMethod("signOut");
            method.setAccessible(true);
            method.invoke(fragment);
        }
    }

    private void assertSignedOutAndEmpty() {
        assertNull(SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens);
        assertNull(AppPreferences.loadSnapshot(app));
        assertNull(AppPreferences.loadResetCredits(app));
        assertFalse(AppPreferences.isReauthenticationRequired(app));
        assertEquals(0, app.getSystemService(NotificationManager.class)
                .getActiveNotifications().length);
    }

    private static void awaitRequest(Future<?> request) throws Exception {
        try { request.get(5, TimeUnit.SECONDS); }
        catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) cause = cause.getCause();
            assertTrue("Only stale-session cancellation is expected", cause instanceof CancellationException);
        }
    }

    private static AuthTokens tokens(long expires) {
        return new AuthTokens("synthetic-access-not-valid", "synthetic-refresh-not-valid", "",
                expires, "synthetic-account", "account@example.test");
    }

    private static void waitForLateResponse(CountDownLatch entered, CountDownLatch release)
            throws Exception {
        entered.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (release.getCount() > 0 && System.nanoTime() < deadline) {
            try { release.await(10, TimeUnit.MILLISECONDS); }
            catch (InterruptedException ignored) { /* HTTP may return after interruption. */ }
        }
        assertEquals("The synthetic HTTP response must be released", 0, release.getCount());
    }

    @Implements(value = UsageApi.class, isInAndroidSdk = false)
    public static class Backend {
        static CountDownLatch entered;
        static CountDownLatch release;
        static String operation;
        static Exception failure;

        @Implementation
        protected static UsageApi.Response send(Context context, String name, String method,
                String url, AuthTokens tokens, byte[] payload, boolean logBytes) throws Exception {
            if (operation.equals(name)) {
                waitForLateResponse(entered, release);
                if (failure != null) throw failure;
            }
            if ("usage".equals(name)) {
                return new UsageApi.Response(200, "{\"plan_type\":\"plus\",\"rate_limit\":{"
                        + "\"primary_window\":{\"used_percent\":1,\"limit_window_seconds\":18000,"
                        + "\"reset_at\":2000000000}}}");
            }
            return new UsageApi.Response(200, "{\"credits\":[],\"available_count\":2}");
        }
    }

    @Implements(value = OAuthClient.class, isInAndroidSdk = false)
    public static class TokenEndpoint {
        static boolean block;
        static CountDownLatch entered;
        static CountDownLatch release;

        @Implementation
        protected static AuthTokens refresh(Context context, AuthTokens tokens) throws Exception {
            if (block) waitForLateResponse(entered, release);
            return tokens(Long.MAX_VALUE);
        }

        @Implementation
        protected static void revokeBestEffort(Context context, AuthTokens tokens) {}
    }

    @Implements(JobParameters.class)
    public static class Job {
        @Implementation protected int getJobId() { return 73101; }
        @Implementation protected PersistableBundle getExtras() { return new PersistableBundle(); }
    }
}
