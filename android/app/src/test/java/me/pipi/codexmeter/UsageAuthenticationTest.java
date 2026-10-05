package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import dev.bennett.codexmeter.UsageHistory;
import dev.bennett.codexmeter.UsageSnapshot;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** Runs authentication decisions against synthetic transports and an in-memory credential store. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class,
        shadows = {SettingsAccountCardTest.InMemoryTokenStore.class,
                UsageAuthenticationTest.SyntheticBackend.class,
                UsageAuthenticationTest.SyntheticTokenEndpoint.class})
public class UsageAuthenticationTest {
    private Application app;
    private AuthTokens original;

    @Before
    public void resetSyntheticSession() {
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        original = tokens("original", Long.MAX_VALUE);
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = original;
        SettingsAccountCardTest.InMemoryTokenStore.clearCalls = 0;
        SettingsAccountCardTest.InMemoryTokenStore.saveCalls = 0;
        SettingsAccountCardTest.InMemoryTokenStore.saveFailure = null;
        SyntheticBackend.responses.clear();
        SyntheticBackend.failure = null;
        SyntheticBackend.requests = 0;
        SyntheticTokenEndpoint.failure = null;
        SyntheticTokenEndpoint.refreshed = tokens("refreshed", Long.MAX_VALUE);
        SyntheticTokenEndpoint.requests = 0;
        AppPreferences.saveSnapshot(app, UsageCardFixtures.plus());
    }

    @Test
    public void expiredSessionInvalidGrantRequiresReauthenticationWithoutDiscardingTheAccount() {
        original = tokens("expired", 1L);
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = original;
        SyntheticTokenEndpoint.failure = OAuthClient.tokenEndpointFailure(app,
                "oauth_token_refresh", 400,
                "{\"error\":\"invalid_grant\",\"error_description\":\"Synthetic revoked session\"}");

        OAuthClient.TokenEndpointException failure = assertThrows(
                OAuthClient.TokenEndpointException.class, () -> UsageApi.refreshAndCache(app));

        assertEquals(400, failure.status);
        assertEquals("invalid_grant", failure.errorCode);
        assertEquals("Synthetic revoked session", failure.getLocalizedMessage());
        assertTrue(AppPreferences.isReauthenticationRequired(app));
        assertRetainedAccount();
        assertEquals(0, SyntheticBackend.requests);
        assertEquals(1, SyntheticTokenEndpoint.requests);
        assertEquals(0, SettingsAccountCardTest.InMemoryTokenStore.saveCalls);
    }

    @Test
    public void unauthorizedAfterRefreshRequiresReauthenticationAndKeepsTheCachedPlan() {
        SyntheticBackend.responses.add(new UsageApi.Response(401, ""));
        SyntheticBackend.responses.add(new UsageApi.Response(401, ""));

        assertThrows(Exception.class, () -> UsageApi.refreshAndCache(app));

        assertTrue(AppPreferences.isReauthenticationRequired(app));
        assertSame(SyntheticTokenEndpoint.refreshed,
                SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens);
        assertEquals("plus", AppPreferences.loadSnapshot(app).planType);
        assertEquals(UsageCardFixtures.plus().fetchedAtMillis,
                AppPreferences.loadSnapshot(app).fetchedAtMillis);
        assertEquals(0, SettingsAccountCardTest.InMemoryTokenStore.clearCalls);
        assertEquals(1, SettingsAccountCardTest.InMemoryTokenStore.saveCalls);
        assertEquals(2, SyntheticBackend.requests);
        assertEquals(1, SyntheticTokenEndpoint.requests);
    }

    @Test
    public void unauthorizedFollowedByRefreshTimeoutDoesNotDeclareTheSessionInvalid() {
        SyntheticBackend.responses.add(new UsageApi.Response(401, ""));
        SyntheticTokenEndpoint.failure = new SocketTimeoutException("Synthetic refresh timeout");

        assertThrows(SocketTimeoutException.class, () -> UsageApi.refreshAndCache(app));

        assertFalse(AppPreferences.isReauthenticationRequired(app));
        assertRetainedAccount();
        assertEquals(1, SyntheticBackend.requests);
        assertEquals(1, SyntheticTokenEndpoint.requests);
        assertEquals(0, SettingsAccountCardTest.InMemoryTokenStore.saveCalls);
    }

    @Test
    public void ordinaryTransportTimeoutDoesNotDeclareTheSessionInvalid() {
        SyntheticBackend.failure = new SocketTimeoutException("Synthetic usage timeout");

        assertThrows(SocketTimeoutException.class, () -> UsageApi.refreshAndCache(app));

        assertFalse(AppPreferences.isReauthenticationRequired(app));
        assertRetainedAccount();
        assertEquals(0, SyntheticTokenEndpoint.requests);
    }

    @Test
    public void successfulRefreshRetriesOnceAndClearsTheDurableReauthenticationState()
            throws Exception {
        AppPreferences.setReauthenticationRequired(app, true);
        SyntheticBackend.responses.add(new UsageApi.Response(401, ""));
        SyntheticBackend.responses.add(new UsageApi.Response(200,
                "{\"plan_type\":\"plus\",\"rate_limit\":{\"allowed\":true,"
                        + "\"primary_window\":{\"used_percent\":37,\"limit_window_seconds\":18000,"
                        + "\"reset_at\":2000000000}}}"));

        UsageSnapshot snapshot = UsageApi.refreshAndCache(app);

        assertEquals("plus", snapshot.planType);
        assertFalse(AppPreferences.isReauthenticationRequired(app));
        assertSame(SyntheticTokenEndpoint.refreshed,
                SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens);
        assertEquals(2, SyntheticBackend.requests);
        assertEquals(1, SyntheticTokenEndpoint.requests);
        assertEquals(0, SettingsAccountCardTest.InMemoryTokenStore.clearCalls);
    }

    @Test
    public void invalidGrantInDescriptionWithoutItsErrorCodeDoesNotInvalidateTheSession() {
        SyntheticTokenEndpoint.failure = OAuthClient.tokenEndpointFailure(app,
                "oauth_token_refresh", 400,
                "{\"error\":\"temporarily_unavailable\","
                        + "\"error_description\":\"Synthetic invalid_grant text\"}");

        assertThrows(OAuthClient.TokenEndpointException.class,
                () -> UsageApi.refreshAndSave(app, original));

        assertFalse(AppPreferences.isReauthenticationRequired(app));
        assertRetainedAccount();
    }

    @Test
    public void serverErrorWithInvalidGrantDoesNotInvalidateTheExistingSession() {
        SyntheticTokenEndpoint.failure = OAuthClient.tokenEndpointFailure(app,
                "oauth_token_refresh", 503,
                "{\"error\":\"invalid_grant\","
                        + "\"error_description\":\"Synthetic gateway failure\"}");

        OAuthClient.TokenEndpointException failure = assertThrows(
                OAuthClient.TokenEndpointException.class,
                () -> UsageApi.refreshAndSave(app, original));

        assertEquals(503, failure.status);
        assertEquals("invalid_grant", failure.errorCode);
        assertFalse(failure.rejectsRefreshToken());
        assertFalse(AppPreferences.isReauthenticationRequired(app));
        assertRetainedAccount();
        assertEquals(0, SettingsAccountCardTest.InMemoryTokenStore.saveCalls);
        assertEquals(1, SyntheticTokenEndpoint.requests);
    }

    @Test
    public void authorizationCodeInvalidGrantDoesNotRejectAnExistingRefreshSession() {
        OAuthClient.TokenEndpointException failure = OAuthClient.tokenEndpointFailure(app,
                "oauth_code_exchange", 400, "{\"error\":\"invalid_grant\"}");

        assertFalse(failure.rejectsRefreshToken());
        assertFalse(AppPreferences.isReauthenticationRequired(app));
        assertRetainedAccount();
    }

    @Test
    public void aFailedCredentialSaveCannotClearTheReauthenticationState() throws Exception {
        AppPreferences.setReauthenticationRequired(app, true);
        SettingsAccountCardTest.InMemoryTokenStore.saveFailure =
                new Exception("Synthetic credential save failure");

        assertThrows(Exception.class, () -> UsageApi.refreshAndSave(app, original));

        assertTrue(AppPreferences.isReauthenticationRequired(app));
        assertRetainedAccount();
    }

    @Test
    public void aDifferentAccountIdClearsCachedDataBeforeItsFirstUsageRequestFails()
            throws Exception {
        seedAccountCaches();
        AuthTokens replacement = accountTokens("other", "synthetic-other-account",
                original.email);
        SyntheticBackend.responses.add(new UsageApi.Response(500, ""));

        UsageApi.saveTokens(app, replacement);
        assertAccountCachesCleared();
        assertThrows(Exception.class, () -> UsageApi.refreshAndCache(app));

        assertSame(replacement, SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens);
        assertAccountCachesCleared();
        assertEquals(1, SyntheticBackend.requests);
        assertEquals(0, SettingsAccountCardTest.InMemoryTokenStore.clearCalls);
    }

    @Test
    public void sameAccountReloginAndRefreshKeepCachesEvenWhenTheEmailChanges()
            throws Exception {
        seedAccountCaches();
        AppPreferences.setReauthenticationRequired(app, true);
        AuthTokens replacement = accountTokens("relogin", original.accountId,
                "renamed@example.test");

        UsageApi.saveTokens(app, replacement);

        assertFalse(AppPreferences.isReauthenticationRequired(app));
        assertAccountCachesRetained();
        SyntheticTokenEndpoint.refreshed = accountTokens("refresh", original.accountId,
                "another-name@example.test");
        UsageApi.refreshAndSave(app, replacement);
        SyntheticBackend.responses.add(new UsageApi.Response(500, ""));
        assertThrows(Exception.class, () -> UsageApi.refreshAndCache(app));

        assertSame(SyntheticTokenEndpoint.refreshed,
                SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens);
        assertAccountCachesRetained();
        assertEquals(2, SettingsAccountCardTest.InMemoryTokenStore.saveCalls);
    }

    @Test
    public void differentEmailsConfirmAnAccountChangeWhenIdsCannotBeCompared()
            throws Exception {
        seedAccountCaches();

        UsageApi.saveTokens(app, accountTokens("no-id", "", "other@example.test"));

        assertAccountCachesCleared();
    }

    @Test
    public void unconfirmedIdentityDoesNotDiscardTheCachedAccountData() throws Exception {
        seedAccountCaches();

        UsageApi.saveTokens(app, accountTokens("same-email", "", "ACCOUNT@EXAMPLE.TEST"));
        assertAccountCachesRetained();
        UsageApi.saveTokens(app, accountTokens("unknown", "", ""));

        assertAccountCachesRetained();
    }

    @Test
    public void aFailedChangedAccountSaveKeepsTheOriginalIdentityAndCaches() throws Exception {
        seedAccountCaches();
        AppPreferences.setReauthenticationRequired(app, true);
        SettingsAccountCardTest.InMemoryTokenStore.saveFailure =
                new Exception("Synthetic credential save failure");

        assertThrows(Exception.class, () -> UsageApi.saveTokens(app,
                accountTokens("other", "synthetic-other-account", "other@example.test")));

        assertTrue(AppPreferences.isReauthenticationRequired(app));
        assertRetainedAccount();
        assertAccountCachesRetained();
    }

    private void seedAccountCaches() {
        UsageSnapshot snapshot = UsageCardFixtures.plus();
        assertTrue(AppPreferences.saveResetCredits(app, UsageCardFixtures.credits(3)));
        for (String kind : new String[] {
                UsageHistory.FIVE_HOUR, UsageHistory.WEEKLY, UsageHistory.MONTHLY}) {
            assertTrue(AppPreferences.saveUsageHistory(app, UsageHistory.empty(kind)
                    .append(snapshot.fiveHour, snapshot.fetchedAtMillis)));
        }
        AppPreferences.setLastError(app, "Synthetic old usage error");
        AppPreferences.setResetCreditsError(app, "Synthetic old credits error");
        AppPreferences.recordRefreshFailure(app);
        assertAccountCachesRetained();
    }

    private void assertAccountCachesCleared() {
        assertNull(AppPreferences.loadSnapshot(app));
        assertNull(AppPreferences.loadResetCredits(app));
        assertEquals("", AppPreferences.getLastError(app));
        assertEquals("", AppPreferences.getResetCreditsError(app));
        assertEquals(0, AppPreferences.getRefreshFailures(app));
        for (String kind : new String[] {
                UsageHistory.FIVE_HOUR, UsageHistory.WEEKLY, UsageHistory.MONTHLY}) {
            assertTrue(AppPreferences.loadUsageHistory(app, kind).samples.isEmpty());
        }
    }

    private void assertAccountCachesRetained() {
        assertEquals("plus", AppPreferences.loadSnapshot(app).planType);
        assertEquals(UsageCardFixtures.plus().fetchedAtMillis,
                AppPreferences.loadSnapshot(app).fetchedAtMillis);
        assertEquals(3, AppPreferences.loadResetCredits(app).availableCount);
        assertEquals("Synthetic old usage error", AppPreferences.getLastError(app));
        assertEquals("Synthetic old credits error", AppPreferences.getResetCreditsError(app));
        assertEquals(1, AppPreferences.getRefreshFailures(app));
        for (String kind : new String[] {
                UsageHistory.FIVE_HOUR, UsageHistory.WEEKLY, UsageHistory.MONTHLY}) {
            assertEquals(1, AppPreferences.loadUsageHistory(app, kind).samples.size());
        }
    }

    private static AuthTokens accountTokens(String name, String accountId, String email) {
        return new AuthTokens("synthetic-" + name + "-access-not-valid",
                "synthetic-" + name + "-refresh-not-valid", "", Long.MAX_VALUE,
                accountId, email);
    }

    private void assertRetainedAccount() {
        assertSame(original, SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens);
        assertEquals("plus", AppPreferences.loadSnapshot(app).planType);
        assertEquals(UsageCardFixtures.plus().fetchedAtMillis,
                AppPreferences.loadSnapshot(app).fetchedAtMillis);
        assertEquals(0, SettingsAccountCardTest.InMemoryTokenStore.clearCalls);
    }

    private static AuthTokens tokens(String name, long expiresAtMillis) {
        return new AuthTokens("synthetic-" + name + "-access-not-valid",
                "synthetic-" + name + "-refresh-not-valid", "", expiresAtMillis,
                "synthetic-account", "account@example.test");
    }

    /** Only the network boundary is replaced; refresh, retry, cache and status logic remain real. */
    @Implements(value = UsageApi.class, isInAndroidSdk = false)
    public static class SyntheticBackend {
        static final ArrayDeque<UsageApi.Response> responses = new ArrayDeque<>();
        static Exception failure;
        static int requests;

        @Implementation
        protected static UsageApi.Response send(Context context, String operation, String method,
                String url, AuthTokens tokens, byte[] payload, boolean logRequestBytes)
                throws Exception {
            if (!"usage".equals(operation)) {
                return new UsageApi.Response(503, "");
            }
            requests++;
            if (failure != null) {
                throw failure;
            }
            if (responses.isEmpty()) {
                throw new AssertionError("Missing synthetic usage response");
            }
            return responses.removeFirst();
        }
    }

    @Implements(value = OAuthClient.class, isInAndroidSdk = false)
    public static class SyntheticTokenEndpoint {
        static Exception failure;
        static AuthTokens refreshed;
        static int requests;

        @Implementation
        protected static AuthTokens refresh(Context context, AuthTokens tokens) throws Exception {
            requests++;
            if (failure != null) {
                throw failure;
            }
            return refreshed;
        }
    }
}
