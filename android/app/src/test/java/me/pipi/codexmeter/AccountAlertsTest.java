package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.app.Notification;
import androidx.preference.SwitchPreferenceCompat;
import dev.bennett.codexmeter.UsageCredits;
import dev.bennett.codexmeter.UsageSnapshot;
import java.util.Collections;
import java.util.ArrayDeque;
import java.net.SocketTimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.android.controller.ActivityController;

/** Exercises real notification decisions with a synthetic account and Android services. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class,
        shadows = {SettingsAccountCardTest.InMemoryTokenStore.class,
                AccountAlertsTest.SyntheticBackend.class,
                UsageAuthenticationTest.SyntheticTokenEndpoint.class})
public class AccountAlertsTest {
    private Application app;
    private NotificationManager manager;

    @Before
    public void prepareSyntheticAccount() {
        app = RuntimeEnvironment.getApplication();
        for (String name : new String[] {"codex_meter_settings_v1",
                "codex_meter_reset_alerts_v1", "codex_meter_notification_state_v1"}) {
            app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit();
        }
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = new AuthTokens(
                "synthetic-access-not-valid", "synthetic-refresh-not-valid", "",
                Long.MAX_VALUE, "synthetic-account", "account@example.test");
        ResetAlertPreferences.save(app, ResetAlertPreferences.STYLE_NOTIFICATION,
                ResetAlertPreferences.METRIC_BOTH, 25);
        manager = app.getSystemService(NotificationManager.class);
        Shadows.shadowOf(manager).setNotificationsEnabled(true);
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        UsageAuthenticationTest.SyntheticBackend.responses.clear();
        UsageAuthenticationTest.SyntheticBackend.failure = null;
        SyntheticBackend.resetCreditResponses.clear();
        UsageAuthenticationTest.SyntheticTokenEndpoint.failure = null;
        UsageAuthenticationTest.SyntheticTokenEndpoint.refreshed =
                SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens;
        SettingsAccountCardTest.InMemoryTokenStore.saveFailure = null;
    }

    @Test
    public void purchasedBalanceExhaustionPostsOnceAndRearmsAfterRefill() {
        ResetNotificationManager.onUsageUpdated(app, snapshot("10", 1));
        assertEquals(0, manager.getActiveNotifications().length);
        ResetNotificationManager.onUsageUpdated(app, snapshot("0", 2));
        assertEquals(1, manager.getActiveNotifications().length);
        manager.cancelAll();
        ResetNotificationManager.onUsageUpdated(app, snapshot("0", 3));
        assertEquals(0, manager.getActiveNotifications().length);
        ResetNotificationManager.onUsageUpdated(app, snapshot("20", 4));
        ResetNotificationManager.onUsageUpdated(app, snapshot("0.004", 5));
        assertEquals(1, manager.getActiveNotifications().length);
    }

    @Test
    public void confirmedAuthenticationFailurePostsOnceAndSuccessfulSessionRearms()
            throws Exception {
        AppPreferences.setReauthenticationRequired(app, true);
        assertEquals(1, manager.getActiveNotifications().length);
        manager.cancelAll();
        AppPreferences.setReauthenticationRequired(app, true);
        assertEquals(0, manager.getActiveNotifications().length);
        UsageApi.saveTokens(app, SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens);
        AppPreferences.setReauthenticationRequired(app, true);
        assertEquals(1, manager.getActiveNotifications().length);
    }

    @Test
    public void firstExhaustedSnapshotIsOnlyABaseline() {
        ResetNotificationManager.onUsageUpdated(app, snapshot("0", 1));
        ResetNotificationManager.onUsageUpdated(app, snapshot("0", 2));
        assertEquals(0, manager.getActiveNotifications().length);
        ResetNotificationManager.onUsageUpdated(app, snapshot("0.005", 3));
        assertEquals(0, manager.getActiveNotifications().length);
        ResetNotificationManager.onUsageUpdated(app, snapshot("0.0049", 4));
        assertEquals(1, manager.getActiveNotifications().length);
    }

    @Test
    public void priorSavedPositiveBalanceSuppliesTheFirstReminderBaseline() {
        ResetNotificationManager.onUsageUpdated(app, snapshot("10", 1), snapshot("0", 2));
        assertEquals(1, manager.getActiveNotifications().length);
        manager.cancelAll();
        ResetNotificationManager.onUsageUpdated(app, snapshot("0", 2), snapshot("0", 3));
        assertEquals(0, manager.getActiveNotifications().length);
    }

    @Test
    public void missingUnlimitedAndInvalidBalancesDoNotAlert() {
        ResetNotificationManager.onUsageUpdated(app, snapshot("10", 1));
        for (UsageCredits credits : new UsageCredits[] {null,
                new UsageCredits(false, false, "0"),
                new UsageCredits(true, true, "0"),
                new UsageCredits(true, false, ""),
                new UsageCredits(true, false, "unknown")}) {
            ResetNotificationManager.onUsageUpdated(app, snapshot(credits, 2));
            assertEquals(0, manager.getActiveNotifications().length);
        }
    }

    @Test
    public void negativeBalanceUsesTheExistingExhaustionRule() {
        ResetNotificationManager.onUsageUpdated(app, snapshot("0.01", 1));
        ResetNotificationManager.onUsageUpdated(app, snapshot("-0.01", 2));
        assertEquals(1, manager.getActiveNotifications().length);
    }

    @Test
    public void aRevokedRefreshSessionPostsButAServiceFailureDoesNot() {
        UsageAuthenticationTest.SyntheticTokenEndpoint.failure = OAuthClient.tokenEndpointFailure(
                app, "oauth_token_refresh", 503, "{\"error\":\"invalid_grant\"}");
        assertThrows(Exception.class, () -> UsageApi.refreshAndSave(app,
                SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens));
        assertEquals(0, manager.getActiveNotifications().length);
        UsageAuthenticationTest.SyntheticTokenEndpoint.failure = OAuthClient.tokenEndpointFailure(
                app, "oauth_token_refresh", 400, "{\"error\":\"invalid_grant\"}");
        assertThrows(Exception.class, () -> UsageApi.refreshAndSave(app,
                SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens));
        assertEquals(1, manager.getActiveNotifications().length);
    }

    @Test
    public void signedOutAccountCannotAlertFromItsOldCachedBalance() throws Exception {
        ResetNotificationManager.onUsageUpdated(app, snapshot("10", 1));
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = null;
        ResetNotificationManager.onUsageUpdated(app, snapshot("0", 2));
        AppPreferences.setReauthenticationRequired(app, true);
        assertEquals(0, manager.getActiveNotifications().length);
    }

    @Test
    public void bothFeatureSwitchesAndTheMasterSwitchPreventPosting() throws Exception {
        ResetAlertPreferences.setUsageCreditsExhaustedEnabled(app, false);
        ResetAlertPreferences.setAuthenticationExpiredEnabled(app, false);
        ResetNotificationManager.onUsageUpdated(app, snapshot("10", 1));
        ResetNotificationManager.onUsageUpdated(app, snapshot("0", 2));
        AppPreferences.setReauthenticationRequired(app, true);
        assertEquals(0, manager.getActiveNotifications().length);
        ResetAlertPreferences.setUsageCreditsExhaustedEnabled(app, true);
        ResetAlertPreferences.setAuthenticationExpiredEnabled(app, true);
        ResetAlertPreferences.save(app, ResetAlertPreferences.STYLE_OFF,
                ResetAlertPreferences.METRIC_BOTH, 25);
        ResetNotificationManager.onUsageUpdated(app, snapshot("20", 3));
        ResetNotificationManager.onUsageUpdated(app, snapshot("0", 4));
        AppPreferences.setReauthenticationRequired(app, true);
        assertEquals(0, manager.getActiveNotifications().length);
    }

    @Test
    public void blockedPermissionsDoNotConsumePendingReminders() throws Exception {
        Shadows.shadowOf(app).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        ResetNotificationManager.onUsageUpdated(app, snapshot("10", 1));
        ResetNotificationManager.onUsageUpdated(app, snapshot("0", 2));
        AppPreferences.setReauthenticationRequired(app, true);
        assertEquals(0, manager.getActiveNotifications().length);
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        ResetNotificationManager.onUsageUpdated(app, snapshot("0", 3));
        AppPreferences.setReauthenticationRequired(app, true);
        assertEquals(2, manager.getActiveNotifications().length);
        manager.cancelAll();
        ResetNotificationManager.onUsageUpdated(app, snapshot("0", 4));
        AppPreferences.setReauthenticationRequired(app, true);
        assertEquals(0, manager.getActiveNotifications().length);
    }

    @Test
    public void authenticationNotificationUsesTheExistingReauthenticationEntry()
            throws Exception {
        AppPreferences.setReauthenticationRequired(app, true);
        Notification notification = manager.getActiveNotifications()[0].getNotification();
        Intent destination = Shadows.shadowOf(notification.contentIntent).getSavedIntent();
        assertEquals(MainActivity.class.getName(), destination.getComponent().getClassName());
        assertTrue(destination.getBooleanExtra("start_sign_in", false));
        assertTrue(destination.getBooleanExtra(OAuthService.EXTRA_REAUTHENTICATE, false));
    }

    @Test
    public void refreshTokenAcceptanceDoesNotRearmRepeatedUsageRejections() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            UsageAuthenticationTest.SyntheticBackend.responses.add(new UsageApi.Response(401, ""));
            UsageAuthenticationTest.SyntheticBackend.responses.add(new UsageApi.Response(401, ""));
            assertThrows(Exception.class, () -> UsageApi.refreshAndCache(app));
            assertEquals(attempt == 0 ? 1 : 0, manager.getActiveNotifications().length);
            manager.cancelAll();
        }
        UsageAuthenticationTest.SyntheticBackend.responses.add(new UsageApi.Response(200,
                "{\"plan_type\":\"plus\",\"rate_limit\":{\"primary_window\":{"
                        + "\"used_percent\":1,\"limit_window_seconds\":18000,"
                        + "\"reset_at\":2000000000}}}"));
        SyntheticBackend.resetCreditResponses.add(new UsageApi.Response(200,
                "{\"credits\":[],\"available_count\":0}"));
        UsageApi.refreshAndCache(app);
        AppPreferences.setReauthenticationRequired(app, true);
        assertEquals(1, manager.getActiveNotifications().length);
    }

    @Test
    public void mainUsageSuccessDoesNotRearmPersistentAuxiliaryAuthenticationFailure()
            throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            addSuccessfulUsageResponse();
            SyntheticBackend.resetCreditResponses.add(new UsageApi.Response(401, ""));
            SyntheticBackend.resetCreditResponses.add(new UsageApi.Response(401, ""));
            UsageApi.refreshAndCache(app);
            assertTrue(AppPreferences.isReauthenticationRequired(app));
            assertEquals(attempt == 0 ? 1 : 0, manager.getActiveNotifications().length);
            manager.cancelAll();
        }
        addSuccessfulUsageResponse();
        SyntheticBackend.resetCreditResponses.add(new UsageApi.Response(503, ""));
        UsageApi.refreshAndCache(app);
        assertEquals(0, manager.getActiveNotifications().length);
        addSuccessfulUsageResponse();
        SyntheticBackend.resetCreditResponses.add(new UsageApi.Response(401, ""));
        SyntheticBackend.resetCreditResponses.add(new UsageApi.Response(401, ""));
        UsageApi.refreshAndCache(app);
        assertEquals(0, manager.getActiveNotifications().length);
        addSuccessfulUsageResponse();
        SyntheticBackend.resetCreditResponses.add(new UsageApi.Response(200,
                "{\"credits\":[],\"available_count\":0}"));
        UsageApi.refreshAndCache(app);
        assertFalse(AppPreferences.isReauthenticationRequired(app));
        AppPreferences.setReauthenticationRequired(app, true);
        assertEquals(1, manager.getActiveNotifications().length);
    }

    private static void addSuccessfulUsageResponse() {
        UsageAuthenticationTest.SyntheticBackend.responses.add(new UsageApi.Response(200,
                "{\"plan_type\":\"plus\",\"rate_limit\":{\"primary_window\":{"
                        + "\"used_percent\":1,\"limit_window_seconds\":18000,"
                        + "\"reset_at\":2000000000}}}"));
    }

    @Test
    public void temporaryNetworkAndServerErrorsDoNotPostAuthenticationAlerts() {
        UsageAuthenticationTest.SyntheticBackend.failure =
                new SocketTimeoutException("Synthetic timeout");
        assertThrows(SocketTimeoutException.class, () -> UsageApi.refreshAndCache(app));
        UsageAuthenticationTest.SyntheticBackend.failure = null;
        UsageAuthenticationTest.SyntheticBackend.responses.add(new UsageApi.Response(503, ""));
        assertThrows(Exception.class, () -> UsageApi.refreshAndCache(app));
        assertFalse(AppPreferences.isReauthenticationRequired(app));
        assertEquals(0, manager.getActiveNotifications().length);
    }

    @Test
    public void clearingTheAccountCancelsBothRemindersAndTheirBalanceBaseline()
            throws Exception {
        ResetNotificationManager.onUsageUpdated(app, snapshot("10", 1));
        ResetNotificationManager.onUsageUpdated(app, snapshot("0", 2));
        AppPreferences.setReauthenticationRequired(app, true);
        assertEquals(2, manager.getActiveNotifications().length);
        AppPreferences.clearSnapshot(app);
        assertEquals(0, manager.getActiveNotifications().length);
        ResetNotificationManager.onUsageUpdated(app, snapshot("0", 3));
        assertEquals(0, manager.getActiveNotifications().length);
    }

    @Test
    public void notificationSettingsExposeBothSwitchesAndBackupRestoresTheirValues()
            throws Exception {
        try (ActivityController<SettingsActivity> controller = Robolectric.buildActivity(
                SettingsActivity.class, SettingsActivity.pageIntent(app,
                        SettingsActivity.PAGE_NOTIFICATIONS)).setup()) {
            SettingsNotificationsFragment fragment = (SettingsNotificationsFragment) controller
                    .get().getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            SwitchPreferenceCompat balance = fragment.findPreference("usage_credits_exhausted_ui");
            SwitchPreferenceCompat authentication = fragment.findPreference("authentication_expired_ui");
            assertNotNull(balance);
            assertNotNull(authentication);
            assertTrue(balance.callChangeListener(false));
            assertTrue(authentication.callChangeListener(false));
        }
        SettingsTransfer.Document backup = SettingsTransferStore.collect(app, false, true,
                false, false);
        assertFalse(backup.notifications.getBoolean("usage_credits_exhausted"));
        assertFalse(backup.notifications.getBoolean("authentication_expired"));
        ResetAlertPreferences.setUsageCreditsExhaustedEnabled(app, true);
        ResetAlertPreferences.setAuthenticationExpiredEnabled(app, true);
        SettingsTransferStore.apply(app, backup, false, true, false, false);
        assertFalse(ResetAlertPreferences.usageCreditsExhaustedEnabled(app));
        assertFalse(ResetAlertPreferences.authenticationExpiredEnabled(app));
    }

    private static UsageSnapshot snapshot(String balance, long fetchedAt) {
        return snapshot(new UsageCredits(true, false, balance), fetchedAt);
    }

    private static UsageSnapshot snapshot(UsageCredits credits, long fetchedAt) {
        return new UsageSnapshot("plus", true, false, null, null,
                Collections.emptyList(), credits, -1, fetchedAt);
    }

    /** Replaces only the network boundary so both real authenticated clients run unchanged. */
    @Implements(value = UsageApi.class, isInAndroidSdk = false)
    public static class SyntheticBackend {
        static final ArrayDeque<UsageApi.Response> resetCreditResponses = new ArrayDeque<>();

        @Implementation
        protected static UsageApi.Response send(Context context, String operation, String method,
                String url, AuthTokens tokens, byte[] payload, boolean logRequestBytes)
                throws Exception {
            if ("reset_credit_list".equals(operation)) {
                return resetCreditResponses.isEmpty() ? new UsageApi.Response(503, "")
                        : resetCreditResponses.removeFirst();
            }
            return UsageAuthenticationTest.SyntheticBackend.send(context, operation, method,
                    url, tokens, payload, logRequestBytes);
        }
    }
}
