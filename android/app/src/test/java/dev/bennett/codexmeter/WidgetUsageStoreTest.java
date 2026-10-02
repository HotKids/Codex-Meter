package dev.bennett.codexmeter;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.Collections;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** Complete cache/account boundaries and real JobScheduler bootstrap, without network access. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = QuotaCardsTest.TestApp.class,
        shadows = WidgetUsageStoreTest.Tokens.class)
public class WidgetUsageStoreTest {
    @Implements(SecureTokenStore.class)
    public static class Tokens {
        static AuthTokens current;
        @Implementation protected static AuthTokens load(Context context) { return current; }
        @Implementation protected static boolean isSignedIn(Context context) { return current != null; }
    }

    private Context context;
    private JobScheduler jobs;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        Tokens.current = account("test-account-a");
        WidgetUsageStore.prefs(context).edit().clear().commit();
        ReferenceWidgetPreferences.preferences(context).edit().clear().commit();
        clearRetry("test-account-a");
        clearRetry("test-account-b");
        jobs = context.getSystemService(JobScheduler.class);
        jobs.cancelAll();
    }

    @Test public void completeWindowsRoundTripWithoutWritingAppCacheOrIdentity() throws Exception {
        WidgetUsageSnapshot snapshot = snapshot(System.currentTimeMillis(), 12.5);
        assertTrue(WidgetUsageStore.save(context, snapshot));
        WidgetUsageSnapshot loaded = WidgetUsageStore.load(context);
        assertNotNull(loaded);
        assertEquals(2, loaded.windows.size());
        assertEquals(12.5, loaded.windows.get(0).usedPercent, 0.001);
        assertEquals("monthly", loaded.windows.get(1).kind);
        assertNull(AppPreferences.loadSnapshot(context));
        String key = WidgetUsageStore.key(context);
        assertEquals("snapshot_".length() + 64, key.length());
        String cache = WidgetUsageStore.prefs(context).getString(key, "");
        assertFalse(cache.contains("test-account-a"));
        assertFalse(cache.contains("fixture-access"));
        assertFalse(cache.contains("fixture-refresh"));
    }

    @Test public void missingCompleteCacheDoesNotReadFreshLegacyAppData() {
        legacy(System.currentTimeMillis());
        assertNull(WidgetUsageStore.load(context));
    }

    @Test public void accountSwitchAndSignOutCannotExposeAnotherAccountsData() throws Exception {
        assertTrue(WidgetUsageStore.save(context, snapshot(1000L, 10)));
        String accountAKey = WidgetUsageStore.key(context);
        Tokens.current = account("test-account-b");
        assertNotEquals(accountAKey, WidgetUsageStore.key(context));
        assertNull(WidgetUsageStore.load(context));
        assertTrue(WidgetUsageStore.save(context, snapshot(2000L, 20)));
        Tokens.current = account("test-account-a");
        assertEquals(1000L, WidgetUsageStore.load(context).fetchedAtMillis);
        Tokens.current = null;
        assertEquals("", WidgetUsageStore.key(context));
        assertNull(WidgetUsageStore.load(context));
        assertFalse(WidgetUsageStore.save(context, snapshot(3000L, 30)));
    }

    @Test public void responseForPreviousAccountCannotOverwriteActiveAccount() throws Exception {
        AuthTokens requested = Tokens.current;
        Tokens.current = account("test-account-b");
        assertFalse(WidgetUsageStore.save(context, snapshot(1000L, 10), requested));
        assertNull(WidgetUsageStore.load(context));
    }

    @Test public void jwtAccountIdTakesPrecedenceOverSharedEmailFallback() {
        String email = "fixture@example.invalid";
        Tokens.current = new AuthTokens("fixture-access", "fixture-refresh", jwt("org-a"),
                Long.MAX_VALUE, "", email);
        String first = WidgetUsageStore.key(context);
        Tokens.current = new AuthTokens("fixture-access", "fixture-refresh", jwt("org-b"),
                Long.MAX_VALUE, "", email);
        assertNotEquals(first, WidgetUsageStore.key(context));
    }

    @Test public void incompleteCorruptAndFutureSchemaCachesAreRejected() throws Exception {
        String key = WidgetUsageStore.key(context);
        JSONObject payload = new JSONObject().put("schema", 1)
                .put("snapshot", snapshot(1000L, 10).toJson());
        WidgetUsageStore.prefs(context).edit().putString(key, payload.toString()).commit();
        assertNull(WidgetUsageStore.load(context));
        payload.put("complete", true).put("schema", 2);
        WidgetUsageStore.prefs(context).edit().putString(key, payload.toString()).commit();
        assertNull(WidgetUsageStore.load(context));
        WidgetUsageStore.prefs(context).edit().putString(key, "invalid fixture").commit();
        assertNull(WidgetUsageStore.load(context));
        assertFalse(WidgetUsageStore.save(context,
                new WidgetUsageSnapshot("pro", 1000L, null, Collections.emptyList())));
    }

    @Test public void editorCanBootstrapBeforePinningAndRepeatedStartsCoalesce() throws Exception {
        assertFalse(WidgetRefreshScheduler.hasWidgets(context));
        legacy(System.currentTimeMillis());
        assertTrue(WidgetRefreshScheduler.requestInitial(context));
        JobInfo first = jobs.getPendingJob(WidgetRefreshScheduler.MANUAL_JOB);
        assertNotNull(first);
        assertTrue(first.getExtras().getBoolean(WidgetRefreshScheduler.EXTRA_INITIAL));
        assertFalse(first.getExtras().getBoolean(WidgetRefreshScheduler.EXTRA_FORCE));
        assertFalse(first.isPersisted());
        assertEquals(0L, first.getMinLatencyMillis());
        assertTrue(WidgetRefreshScheduler.requestInitial(context));
        assertSame(first, jobs.getPendingJob(WidgetRefreshScheduler.MANUAL_JOB));
        WidgetRefreshScheduler.schedule(context);
        assertSame(first, jobs.getPendingJob(WidgetRefreshScheduler.MANUAL_JOB));
        assertTrue(WidgetUsageStore.save(context, snapshot(System.currentTimeMillis(), 10)));
        assertFalse(WidgetRefreshScheduler.shouldFetchInitial(context, System.currentTimeMillis()));
        assertFalse(WidgetRefreshScheduler.requestInitial(context));
    }

    @Test public void signedInFreshnessAndFailureBackoffUseCompleteWidgetData() throws Exception {
        ReferenceWidgetPreferences.preferences(context).edit()
                .putInt("reference_widget_refresh_minutes", 5).commit();
        shadowOf(AppWidgetManager.getInstance(context)).createWidget(CodexUsageWidget.class, R.layout.widget_rings);
        long now = System.currentTimeMillis();
        legacy(now);
        assertTrue(WidgetRefreshScheduler.shouldFetch(context, false, now));
        WidgetRefreshScheduler.recordFailure(context, now + 1L, "Fixture failure");
        legacy(now + 2L);
        assertEquals("Fixture failure", WidgetRefreshScheduler.lastError(context));
        assertFalse(WidgetRefreshScheduler.shouldFetch(context, true, now + 2L));
        assertFalse(WidgetRefreshScheduler.requestInitial(context));
        assertTrue(WidgetUsageStore.save(context, snapshot(now + 3L, 10)));
        assertEquals("", WidgetRefreshScheduler.lastError(context));
        assertFalse(WidgetRefreshScheduler.shouldFetch(context, false, now + 3L));
        assertTrue(WidgetRefreshScheduler.shouldFetch(context, false, now + 300_003L));
    }

    @Test public void manualModeStillRequestsInitialCompleteWindows() {
        ReferenceWidgetPreferences.preferences(context).edit()
                .putInt("reference_widget_refresh_minutes", 0).commit();
        shadowOf(AppWidgetManager.getInstance(context)).createWidget(CodexUsageWidget.class, R.layout.widget_rings);
        legacy(System.currentTimeMillis());
        WidgetRefreshScheduler.schedule(context);
        assertNotNull(jobs.getPendingJob(WidgetRefreshScheduler.MANUAL_JOB));
        assertNull(jobs.getPendingJob(WidgetRefreshScheduler.AUTOMATIC_JOB_A));
        assertNull(jobs.getPendingJob(WidgetRefreshScheduler.AUTOMATIC_JOB_B));
    }

    @Test public void accountSwitchDoesNotInheritFailureDelay() {
        long now = System.currentTimeMillis();
        WidgetRefreshScheduler.recordFailure(context, now, "Account A fixture failure");
        assertFalse(WidgetRefreshScheduler.shouldFetchInitial(context, now));
        Tokens.current = account("test-account-b");
        assertEquals("", WidgetRefreshScheduler.lastError(context));
        assertTrue(WidgetRefreshScheduler.shouldFetchInitial(context, now));
    }

    @Test public void previousAccountsJobCompletionDoesNotRecordNewAccountsError() {
        String requested = WidgetUsageStore.key(context);
        Tokens.current = account("test-account-b");
        long now = System.currentTimeMillis();
        WidgetRefreshScheduler.recordFailure(context, now, "Old account response", requested);
        assertEquals("", WidgetRefreshScheduler.lastError(context));
        assertTrue(WidgetRefreshScheduler.shouldFetchInitial(context, now));
    }

    @Test public void onlyWidgetRequestsUseReferenceIdentityHeaders() throws Exception {
        FakeConnection widget = new FakeConnection();
        WidgetUsageApi.applyHeaders(widget, Tokens.current);
        assertEquals("Codex Desktop", widget.getRequestProperty("originator"));
        assertTrue(widget.getRequestProperty("User-Agent").startsWith("Codex Desktop/"));
        assertEquals("Bearer fixture-access", widget.getRequestProperty("Authorization"));
        assertEquals("test-account-a", widget.getRequestProperty("ChatGPT-Account-Id"));
        FakeConnection app = new FakeConnection();
        UsageApi.applyHeaders(app, Tokens.current);
        assertEquals(AppConstants.ORIGINATOR, app.getRequestProperty("originator"));
        assertEquals(AppConstants.userAgent(), app.getRequestProperty("User-Agent"));
    }

    private static AuthTokens account(String id) {
        return new AuthTokens("fixture-access", "fixture-refresh", "", Long.MAX_VALUE, id, "");
    }

    private static String jwt(String account) {
        String payload = "{\"chatgpt_account_id\":\"" + account + "\"}";
        return "fixture." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".fixture";
    }

    private WidgetUsageSnapshot snapshot(long fetchedAt, double used) throws Exception {
        return WidgetUsageParser.parse("{\"weekly\":{\"used_percent\":" + used
                + ",\"limit_window_seconds\":604800},\"monthly\":{\"used_percent\":30,\"limit_window_seconds\":2592000}}", fetchedAt);
    }

    private void legacy(long fetchedAt) {
        assertTrue(AppPreferences.saveSnapshot(context, new UsageSnapshot("pro", true, false,
                null, new UsageWindow(30, 604800, 0, 0), fetchedAt)));
    }

    private void clearRetry(String id) {
        context.getSharedPreferences("codex_meter_home_widget_refresh_v1_" + WidgetUsageStore.key(account(id)),
                Context.MODE_PRIVATE).edit().clear().commit();
    }

    private static final class FakeConnection extends HttpsURLConnection {
        FakeConnection() throws Exception { super(new URL("https://example.invalid/fixture")); }
        @Override public String getCipherSuite() { return "fixture"; }
        @Override public Certificate[] getLocalCertificates() { return null; }
        @Override public Certificate[] getServerCertificates() { return new Certificate[0]; }
        @Override public void disconnect() { }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }
}
