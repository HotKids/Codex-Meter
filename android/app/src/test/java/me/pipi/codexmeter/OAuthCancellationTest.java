package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import dev.bennett.codexmeter.UsageSnapshot;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, shadows = {
        SettingsAccountCardTest.InMemoryTokenStore.class,
        OAuthCancellationTest.ControlledExchange.class,
        OAuthCancellationTest.ControlledUsage.class})
public class OAuthCancellationTest {
    private Application app;
    private AuthTokens original;

    @Before
    public void resetSyntheticSession() {
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        original = tokens("old");
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = original;
        SettingsAccountCardTest.InMemoryTokenStore.saveCalls = 0;
        SettingsAccountCardTest.InMemoryTokenStore.clearCalls = 0;
        SettingsAccountCardTest.InMemoryTokenStore.saveFailure = null;
        ControlledExchange.entered = new CountDownLatch(1);
        ControlledExchange.release = new CountDownLatch(1);
        ControlledExchange.returning = new CountDownLatch(1);
        ControlledExchange.replacement = tokens("new");
        ControlledUsage.entered = new CountDownLatch(1);
        ControlledUsage.release = new CountDownLatch(0);
    }

    @Test
    public void cancellationDuringTheExchangeCannotCommitOrBroadcastSuccess() throws Exception {
        try (Flow flow = startCallback()) {
            cancel(flow.service);
            ControlledExchange.release.countDown();
            flow.awaitCompletion();
            assertUncommitted();
            assertEquals(1, resultCount(false));
        }
    }

    @Test
    public void cancellationWhileWaitingForTheUsageLockCannotCommitLater() throws Exception {
        try (Flow flow = startCallback()) {
            synchronized (UsageApi.NETWORK_LOCK) {
                ControlledExchange.release.countDown();
                assertTrue(ControlledExchange.returning.await(5, TimeUnit.SECONDS));
                cancel(flow.service);
            }
            flow.awaitCompletion();
            assertUncommitted();
        }
    }

    @Test
    public void destructionDuringTheExchangeCannotCommitLater() throws Exception {
        try (Flow flow = startCallback()) {
            flow.controller.destroy();
            ControlledExchange.release.countDown();
            assertTrue(flow.executor.awaitTermination(5, TimeUnit.SECONDS));
            assertUncommitted();
        }
    }

    @Test
    public void signOutDuringTheExchangeCannotRestoreTheSignedOutAccount() throws Exception {
        try (Flow flow = startCallback()) {
            UsageApi.signOut(app);
            ControlledExchange.release.countDown();
            flow.awaitCompletion();
            assertNull(SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens);
            assertEquals(0, SettingsAccountCardTest.InMemoryTokenStore.saveCalls);
            assertEquals(0, resultCount(true));
            assertFalse(AppPreferences.isOAuthPending(app));
        }
    }

    @Test
    public void cancellationAfterCommitCannotTurnSuccessIntoFailure() throws Exception {
        ControlledUsage.release = new CountDownLatch(1);
        try (Flow flow = startCallback()) {
            ControlledExchange.release.countDown();
            assertTrue(ControlledUsage.entered.await(5, TimeUnit.SECONDS));
            cancel(flow.service);
            ControlledUsage.release.countDown();
            flow.awaitCompletion();
            assertSame(ControlledExchange.replacement,
                    SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens);
            assertEquals(1, SettingsAccountCardTest.InMemoryTokenStore.saveCalls);
            assertEquals(1, resultCount(true));
            assertEquals(0, resultCount(false));
            assertFalse(AppPreferences.isOAuthPending(app));
        }
    }

    @Test
    @Config(shadows = {SettingsAccountCardTest.InMemoryTokenStore.class,
            ControlledExchange.class, ControlledUsage.class, FailedStatusWrite.class})
    public void aSettingsFailureAfterCredentialSaveIsStillACommittedSession() throws Exception {
        try (Flow flow = startCallback()) {
            ControlledExchange.release.countDown();
            flow.awaitCompletion();
            cancel(flow.service);
            assertSame(ControlledExchange.replacement,
                    SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens);
            assertEquals(1, SettingsAccountCardTest.InMemoryTokenStore.saveCalls);
            assertEquals(1, resultCount(true));
            assertEquals(0, resultCount(false));
        }
    }

    private void assertUncommitted() {
        assertSame(original, SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens);
        assertEquals(0, SettingsAccountCardTest.InMemoryTokenStore.saveCalls);
        assertEquals(0, resultCount(true));
    }

    private int resultCount(boolean success) {
        int count = 0;
        for (Intent intent : shadowOf(app).getBroadcastIntents()) {
            if (AppConstants.ACTION_OAUTH_RESULT.equals(intent.getAction())
                    && intent.getBooleanExtra(AppConstants.EXTRA_SUCCESS, false) == success) {
                count++;
            }
        }
        return count;
    }

    private void cancel(OAuthService service) {
        service.onStartCommand(new Intent(app, OAuthService.class)
                .setAction(OAuthService.ACTION_CANCEL), 0, 2);
    }

    private Flow startCallback() throws Exception {
        Flow flow = new Flow();
        try {
            flow.service.onStartCommand(new Intent(app, OAuthService.class)
                    .setAction(OAuthService.ACTION_START)
                    .putExtra(OAuthService.EXTRA_REAUTHENTICATE, true), 0, 1);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            String authorization;
            while ((authorization = AppPreferences.getOAuthUrl(app)).isEmpty()
                    && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertFalse("The real service must publish a synthetic callback URL",
                    authorization.isEmpty());
            Uri uri = Uri.parse(authorization);
            Uri redirect = Uri.parse(uri.getQueryParameter("redirect_uri"));
            flow.browser = new Socket("127.0.0.1", redirect.getPort());
            String request = "GET /auth/callback?code=synthetic-code-not-valid&state="
                    + uri.getQueryParameter("state") + " HTTP/1.1\r\nHost: localhost\r\n\r\n";
            flow.browser.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
            flow.browser.getOutputStream().flush();
            assertTrue("The real callback must reach the synthetic exchange",
                    ControlledExchange.entered.await(5, TimeUnit.SECONDS));
            return flow;
        } catch (Throwable failure) {
            flow.close();
            throw failure;
        }
    }

    private static AuthTokens tokens(String name) {
        return new AuthTokens("synthetic-" + name + "-access-not-valid",
                "synthetic-" + name + "-refresh-not-valid", "", Long.MAX_VALUE,
                "synthetic-" + name + "-account", name + "@example.test");
    }

    private static class Flow implements AutoCloseable {
        final ServiceController<OAuthService> controller = Robolectric
                .buildService(OAuthService.class).create();
        final OAuthService service = controller.get();
        final ExecutorService executor;
        Socket browser;

        Flow() throws Exception {
            Field field = OAuthService.class.getDeclaredField("executor");
            field.setAccessible(true);
            executor = (ExecutorService) field.get(service);
        }

        void awaitCompletion() throws Exception {
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws Exception {
            ControlledExchange.release.countDown();
            ControlledUsage.release.countDown();
            if (browser != null) browser.close();
            controller.destroy();
            assertTrue("The service worker must terminate", executor.awaitTermination(
                    5, TimeUnit.SECONDS));
        }
    }

    @Implements(value = OAuthClient.class, isInAndroidSdk = false)
    public static class ControlledExchange {
        static CountDownLatch entered;
        static CountDownLatch release;
        static CountDownLatch returning;
        static AuthTokens replacement;

        @Implementation
        protected static AuthTokens exchangeCode(Context context, String code,
                String redirectUri, String verifier) throws Exception {
            entered.countDown();
            // A completed HTTP exchange can return even after its caller was interrupted.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (release.getCount() > 0 && System.nanoTime() < deadline) {
                try {
                    release.await(10, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                    // The race concerns a late successful response, not interrupted HTTP I/O.
                }
            }
            assertEquals("The synthetic exchange must be released", 0, release.getCount());
            returning.countDown();
            return replacement;
        }
    }

    @Implements(value = UsageApi.class, isInAndroidSdk = false)
    public static class ControlledUsage {
        static CountDownLatch entered;
        static CountDownLatch release;

        @Implementation
        protected static UsageSnapshot refreshAndCache(Context context) throws Exception {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            throw new Exception("Synthetic usage unavailable");
        }

        @Implementation
        protected static UsageSnapshot refreshAndCache(Context context,
                UsageApi.Session session) throws Exception {
            return refreshAndCache(context);
        }
    }

    @Implements(value = AppPreferences.class, isInAndroidSdk = false)
    public static class FailedStatusWrite {
        @Implementation
        protected static void setReauthenticationRequired(Context context, boolean required)
                throws Exception {
            throw new Exception("Synthetic settings commit failure");
        }
    }
}
