package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.preference.PreferenceGroupAdapter;
import androidx.recyclerview.widget.RecyclerView;
import dev.oneuiproject.oneui.preference.LayoutPreference;
import dev.oneuiproject.oneui.widget.CardItemView;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class,
        shadows = SettingsAccountCardTest.InMemoryTokenStore.class)
public class SettingsAccountCardTest {
    private Application app;

    @Before
    public void resetSyntheticAccount() {
        app = RuntimeEnvironment.getApplication();
        InMemoryTokenStore.syntheticTokens = null;
        InMemoryTokenStore.clearCalls = 0;
        InMemoryTokenStore.saveCalls = 0;
        InMemoryTokenStore.loadCalls = 0;
        InMemoryTokenStore.saveFailure = null;
        app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test
    public void rootShowsAccountAndOpensSignInDirectly() {
        try (ActivityController<SettingsActivity> controller = Robolectric
                .buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity activity = controller.get();
            SettingsRootFragment fragment = (SettingsRootFragment) activity
                    .getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            assertNotNull(fragment);
            assertTrue("The account belongs on the main settings page",
                    fragment.findPreference("account_card") instanceof LayoutPreference);
            LayoutPreference card = fragment.findPreference("account_card");
            TextView title = card.findViewById(R.id.settings_account_title);
            assertEquals(activity.getString(R.string.settings_account_title_signed_out),
                    title.getText().toString());
            CardItemView action = card.findViewById(R.id.settings_account_action);
            assertEquals(activity.getString(R.string.settings_account_sign_in),
                    action.getTitleView().getText().toString());

            action.findViewById(dev.oneuiproject.oneui.design.R.id.cardview_container)
                    .performClick();
            Intent intent = shadowOf(activity).getNextStartedActivity();
            assertNotNull(intent);
            assertEquals(MainActivity.class.getName(), intent.getComponent().getClassName());
            assertTrue(intent.getBooleanExtra("start_sign_in", false));
            assertTrue(activity.isFinishing());
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    public void accountCardRereadsIdentityAndPlanOnResume() {
        InMemoryTokenStore.syntheticTokens = syntheticTokens("first");
        AppPreferences.saveSnapshot(app, UsageCardFixtures.plus());
        try (ActivityController<SettingsActivity> controller = Robolectric
                .buildActivity(SettingsActivity.class).setup()) {
            SettingsRootFragment fragment = (SettingsRootFragment) controller.get()
                    .getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            View card = visibleAccountCard(fragment);
            assertEquals("first@example.test", text(card, R.id.settings_account_summary));
            assertEquals("Plus", text(card, R.id.settings_account_plan));
            assertFalse(card.findViewById(R.id.settings_account_plan).isClickable());

            controller.pause();
            InMemoryTokenStore.syntheticTokens = syntheticTokens("second");
            AppPreferences.saveSnapshot(app, UsageCardFixtures.snapshot("pro200", 20, 30, false));
            controller.resume();
            card = visibleAccountCard(fragment);
            assertEquals("second@example.test", text(card, R.id.settings_account_summary));
            assertEquals(app.getString(R.string.settings_account_title_signed_in),
                    text(card, R.id.settings_account_title));
            assertEquals("Pro 200", text(card, R.id.settings_account_plan));
            assertEquals(View.VISIBLE, card.findViewById(R.id.settings_account_plan).getVisibility());
            CardItemView action = card.findViewById(R.id.settings_account_action);
            assertEquals(app.getString(R.string.settings_account_sign_out),
                    action.getTitleView().getText().toString());
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    public void accountCardReturnsToSignedOutStateOnResume() {
        InMemoryTokenStore.syntheticTokens = syntheticTokens("first");
        AppPreferences.saveSnapshot(app, UsageCardFixtures.plus());
        try (ActivityController<SettingsActivity> controller = Robolectric
                .buildActivity(SettingsActivity.class).setup()) {
            SettingsRootFragment fragment = (SettingsRootFragment) controller.get()
                    .getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            View card = visibleAccountCard(fragment);
            assertEquals(app.getString(R.string.settings_account_title_signed_in),
                    text(card, R.id.settings_account_title));

            controller.pause();
            InMemoryTokenStore.syntheticTokens = null;
            controller.resume();
            card = visibleAccountCard(fragment);
            assertEquals(app.getString(R.string.settings_account_title_signed_out),
                    text(card, R.id.settings_account_title));
            assertEquals(app.getString(R.string.settings_account_summary_signed_out),
                    text(card, R.id.settings_account_summary));
            assertEquals(View.VISIBLE, card.findViewById(R.id.settings_account_plan).getVisibility());
            assertEquals("重新登录", text(card, R.id.settings_account_plan));
            CardItemView action = card.findViewById(R.id.settings_account_action);
            assertEquals(app.getString(R.string.settings_account_sign_in),
                    action.getTitleView().getText().toString());

            card.findViewById(R.id.settings_account_plan).performClick();
            Intent intent = shadowOf(controller.get()).getNextStartedActivity();
            assertNotNull(intent);
            assertTrue(intent.getBooleanExtra("start_sign_in", false));
            assertTrue(intent.getBooleanExtra("reauthenticate", false));
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    public void signedInWithoutUsageShowsANonInteractivePlanFallback() {
        AuthTokens original = syntheticTokens("retained");
        InMemoryTokenStore.syntheticTokens = original;
        try (ActivityController<SettingsActivity> controller = Robolectric
                .buildActivity(SettingsActivity.class).setup()) {
            SettingsRootFragment fragment = (SettingsRootFragment) controller.get()
                    .getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            View card = visibleAccountCard(fragment);
            View plan = card.findViewById(R.id.settings_account_plan);
            assertEquals(View.VISIBLE, plan.getVisibility());
            assertEquals(app.getString(R.string.settings_account_plan_fallback),
                    text(card, R.id.settings_account_plan));
            assertFalse(plan.isClickable());
            assertNotNull(plan.getBackground());
            assertEquals("retained@example.test", text(card, R.id.settings_account_summary));

            assertFalse(plan.performClick());
            assertNull(shadowOf(controller.get()).getNextStartedActivity());
            assertSame(original, InMemoryTokenStore.syntheticTokens);
            assertEquals(0, InMemoryTokenStore.clearCalls);
            assertEquals(0, InMemoryTokenStore.saveCalls);
        }
    }

    @Test
    public void ordinaryRefreshFailureKeepsThePlanInsteadOfBecomingReauthentication() {
        InMemoryTokenStore.syntheticTokens = syntheticTokens("retained");
        AppPreferences.saveSnapshot(app, UsageCardFixtures.plus());
        AppPreferences.setLastError(app, "Synthetic connection timeout");
        try (ActivityController<SettingsActivity> controller = Robolectric
                .buildActivity(SettingsActivity.class).setup()) {
            SettingsRootFragment fragment = (SettingsRootFragment) controller.get()
                    .getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            View card = visibleAccountCard(fragment);
            assertEquals("Plus", text(card, R.id.settings_account_plan));
            assertFalse(card.findViewById(R.id.settings_account_plan).isClickable());
            assertFalse(card.findViewById(R.id.settings_account_plan).performClick());
            assertEquals(0, InMemoryTokenStore.clearCalls);
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    public void confirmedRejectionReplacesOnlyThePlanPillAndStartsReauthentication()
            throws Exception {
        AuthTokens original = syntheticTokens("retained");
        InMemoryTokenStore.syntheticTokens = original;
        AppPreferences.saveSnapshot(app, UsageCardFixtures.plus());
        try (ActivityController<SettingsActivity> controller = Robolectric
                .buildActivity(SettingsActivity.class).setup()) {
            SettingsRootFragment fragment = (SettingsRootFragment) controller.get()
                    .getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            View card = visibleAccountCard(fragment);
            View originalPill = card.findViewById(R.id.settings_account_plan);
            assertEquals("Plus", text(card, R.id.settings_account_plan));

            controller.pause();
            UsageApi.recordAuthenticationRejection(app, new UsageApi.Response(401, ""));
            AppPreferences.clearLastError(app);
            controller.resume();
            card = visibleAccountCard(fragment);
            assertSame(originalPill, card.findViewById(R.id.settings_account_plan));
            assertEquals("重新登录", text(card, R.id.settings_account_plan));
            assertEquals("retained@example.test", text(card, R.id.settings_account_summary));
            assertEquals(app.getString(R.string.settings_account_title_signed_in),
                    text(card, R.id.settings_account_title));
            assertEquals("plus", AppPreferences.loadSnapshot(app).planType);
            assertTrue(originalPill.performClick());
            Intent intent = shadowOf(controller.get()).getNextStartedActivity();
            assertNotNull(intent);
            assertTrue(intent.getBooleanExtra("start_sign_in", false));
            assertTrue(intent.getBooleanExtra("reauthenticate", false));
            assertSame(original, InMemoryTokenStore.syntheticTokens);
            assertEquals(0, InMemoryTokenStore.clearCalls);
            assertEquals(0, InMemoryTokenStore.saveCalls);
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    public void successfullySavedSessionRestoresTheCachedPlanOnResume() throws Exception {
        InMemoryTokenStore.syntheticTokens = syntheticTokens("retained");
        AppPreferences.saveSnapshot(app, UsageCardFixtures.plus());
        AppPreferences.setReauthenticationRequired(app, true);
        try (ActivityController<SettingsActivity> controller = Robolectric
                .buildActivity(SettingsActivity.class).setup()) {
            SettingsRootFragment fragment = (SettingsRootFragment) controller.get()
                    .getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            View card = visibleAccountCard(fragment);
            assertEquals("重新登录", text(card, R.id.settings_account_plan));
            controller.pause();
            UsageApi.saveTokens(app, syntheticTokens("retained"));
            controller.resume();
            card = visibleAccountCard(fragment);
            assertEquals("Plus", text(card, R.id.settings_account_plan));
            assertFalse(card.findViewById(R.id.settings_account_plan).isClickable());
            assertEquals("retained@example.test", text(card, R.id.settings_account_summary));
            assertFalse(AppPreferences.isReauthenticationRequired(app));
            assertEquals(0, InMemoryTokenStore.clearCalls);
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    public void backgroundRejectionUpdatesTheVisibleAccountWithoutResuming() throws Exception {
        AuthTokens original = syntheticTokens("retained");
        InMemoryTokenStore.syntheticTokens = original;
        AppPreferences.saveSnapshot(app, UsageCardFixtures.plus());
        try (ActivityController<SettingsActivity> controller = Robolectric
                .buildActivity(SettingsActivity.class).setup()) {
            SettingsRootFragment fragment = (SettingsRootFragment) controller.get()
                    .getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            View card = visibleAccountCard(fragment);
            View originalPill = card.findViewById(R.id.settings_account_plan);
            assertEquals("Plus", text(card, R.id.settings_account_plan));

            runInBackground(() -> {
                UsageApi.recordAuthenticationRejection(app, new UsageApi.Response(401, ""));
                return null;
            });

            assertSame(originalPill, card.findViewById(R.id.settings_account_plan));
            assertEquals("重新登录", text(card, R.id.settings_account_plan));
            assertEquals("retained@example.test", text(card, R.id.settings_account_summary));
            assertTrue(originalPill.performClick());
            Intent intent = shadowOf(controller.get()).getNextStartedActivity();
            assertNotNull(intent);
            assertTrue(intent.getBooleanExtra("reauthenticate", false));
            assertSame(original, InMemoryTokenStore.syntheticTokens);
            assertEquals("plus", AppPreferences.loadSnapshot(app).planType);
            assertEquals(0, InMemoryTokenStore.clearCalls);
            assertEquals(0, InMemoryTokenStore.saveCalls);
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    public void sameAccountRecoveryUpdatesTheVisibleAccountWithoutResuming() throws Exception {
        InMemoryTokenStore.syntheticTokens = syntheticTokens("retained");
        AppPreferences.saveSnapshot(app, UsageCardFixtures.plus());
        AppPreferences.setReauthenticationRequired(app, true);
        try (ActivityController<SettingsActivity> controller = Robolectric
                .buildActivity(SettingsActivity.class).setup()) {
            SettingsRootFragment fragment = (SettingsRootFragment) controller.get()
                    .getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            View card = visibleAccountCard(fragment);
            View originalPill = card.findViewById(R.id.settings_account_plan);
            assertEquals("重新登录", text(card, R.id.settings_account_plan));

            AuthTokens refreshed = syntheticTokens("retained");
            runInBackground(() -> {
                UsageApi.saveTokens(app, refreshed);
                return null;
            });

            assertSame(originalPill, card.findViewById(R.id.settings_account_plan));
            assertEquals("Plus", text(card, R.id.settings_account_plan));
            assertFalse(originalPill.isClickable());
            assertSame(refreshed, InMemoryTokenStore.syntheticTokens);
            assertFalse(AppPreferences.isReauthenticationRequired(app));
            assertEquals(0, InMemoryTokenStore.clearCalls);
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    public void pausedAccountWaitsUntilResumeToApplyARejection() throws Exception {
        InMemoryTokenStore.syntheticTokens = syntheticTokens("retained");
        AppPreferences.saveSnapshot(app, UsageCardFixtures.plus());
        try (ActivityController<SettingsActivity> controller = Robolectric
                .buildActivity(SettingsActivity.class).setup()) {
            SettingsRootFragment fragment = (SettingsRootFragment) controller.get()
                    .getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            View card = visibleAccountCard(fragment);
            controller.pause();
            int loadsWhilePaused = InMemoryTokenStore.loadCalls;

            runInBackground(() -> {
                UsageApi.recordAuthenticationRejection(app, new UsageApi.Response(401, ""));
                return null;
            });

            assertEquals("Plus", text(card, R.id.settings_account_plan));
            assertEquals(loadsWhilePaused, InMemoryTokenStore.loadCalls);
            controller.resume();
            assertEquals("重新登录", text(card, R.id.settings_account_plan));
            assertTrue(card.findViewById(R.id.settings_account_plan).isClickable());
        }
    }

    @Test
    public void unrelatedSettingsDoNotReloadTheVisibleAccount() throws Exception {
        InMemoryTokenStore.syntheticTokens = syntheticTokens("retained");
        AppPreferences.saveSnapshot(app, UsageCardFixtures.plus());
        try (ActivityController<SettingsActivity> controller = Robolectric
                .buildActivity(SettingsActivity.class).setup()) {
            SettingsRootFragment fragment = (SettingsRootFragment) controller.get()
                    .getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            View card = visibleAccountCard(fragment);
            int loads = InMemoryTokenStore.loadCalls;

            runInBackground(() -> {
                AppPreferences.setLastError(app, "Synthetic connection timeout");
                AppPreferences.setDashboardOrder(app, Collections.singletonList("weekly"));
                return null;
            });

            assertEquals(loads, InMemoryTokenStore.loadCalls);
            assertEquals("Plus", text(card, R.id.settings_account_plan));
            assertFalse(card.findViewById(R.id.settings_account_plan).isClickable());
        }
    }

    @Test
    public void explicitReauthenticationLaunchStartsOAuthWithoutClearingCredentials() {
        AuthTokens original = syntheticTokens("retained");
        InMemoryTokenStore.syntheticTokens = original;
        AppPreferences.completeOnboarding(app);
        Intent intent = new Intent(app, MainActivity.class)
                .putExtra("start_sign_in", true).putExtra("reauthenticate", true);
        try (ActivityController<MainActivity> controller = Robolectric
                .buildActivity(MainActivity.class, intent).setup()) {
            Intent service = shadowOf(app).getNextStartedService();
            assertNotNull("Explicit reauthentication must reach the OAuth service", service);
            assertEquals(OAuthService.class.getName(), service.getComponent().getClassName());
            assertEquals(OAuthService.ACTION_START, service.getAction());
            assertTrue(service.getBooleanExtra("reauthenticate", false));
            assertSame(original, InMemoryTokenStore.syntheticTokens);
            assertEquals(0, InMemoryTokenStore.clearCalls);
        }
    }

    @Test
    public void dashboardReturnKeepsAnExplicitReauthenticationFlowPending() {
        InMemoryTokenStore.syntheticTokens = syntheticTokens("retained");
        AppPreferences.completeOnboarding(app);
        AppPreferences.setOAuthPending(app, true, "https://example.invalid/synthetic-authorize");
        try (ActivityController<MainActivity> controller = Robolectric
                .buildActivity(MainActivity.class).setup()) {
            assertTrue(AppPreferences.isOAuthPending(app));
            assertEquals("https://example.invalid/synthetic-authorize",
                    AppPreferences.getOAuthUrl(app));
            assertEquals(0, InMemoryTokenStore.clearCalls);
        }
    }

    @Test
    public void existingDashboardAcceptsTheExplicitReauthenticationIntent() {
        InMemoryTokenStore.syntheticTokens = syntheticTokens("retained");
        AppPreferences.completeOnboarding(app);
        try (ActivityController<MainActivity> controller = Robolectric
                .buildActivity(MainActivity.class).setup()) {
            controller.newIntent(new Intent(app, MainActivity.class)
                    .putExtra("start_sign_in", true).putExtra("reauthenticate", true));
            Intent service = shadowOf(app).getNextStartedService();
            assertNotNull("A reused dashboard must also dispatch the explicit request", service);
            assertTrue(service.getBooleanExtra("reauthenticate", false));
            assertEquals(0, InMemoryTokenStore.clearCalls);
        }
    }

    @Test
    public void stoppedDashboardWaitsForItsOAuthReceiverBeforeStartingReauthentication() {
        InMemoryTokenStore.syntheticTokens = syntheticTokens("retained");
        AppPreferences.completeOnboarding(app);
        try (ActivityController<MainActivity> controller = Robolectric
                .buildActivity(MainActivity.class).setup()) {
            controller.pause().stop();
            controller.newIntent(new Intent(app, MainActivity.class)
                    .putExtra("start_sign_in", true).putExtra("reauthenticate", true));
            assertNull(shadowOf(app).getNextStartedService());
            controller.start().resume();
            Intent service = shadowOf(app).getNextStartedService();
            assertNotNull(service);
            assertTrue(service.getBooleanExtra("reauthenticate", false));
            assertEquals(0, InMemoryTokenStore.clearCalls);
        }
    }

    @Test
    public void oauthServiceStartsExplicitReauthenticationWithoutReplacingTheSession()
            throws Exception {
        AuthTokens original = syntheticTokens("retained");
        InMemoryTokenStore.syntheticTokens = original;
        ServiceController<OAuthService> controller = Robolectric
                .buildService(OAuthService.class).create();
        try {
            OAuthService service = controller.get();
            QueuedExecutor executor = queueOAuthWork(service);
            service.onStartCommand(new Intent(app, OAuthService.class)
                    .setAction(OAuthService.ACTION_START).putExtra("reauthenticate", true),
                    0, 1);
            assertEquals("The explicit request must pass the already-signed-in check",
                    1, executor.queued);
            assertSame(original, InMemoryTokenStore.syntheticTokens);
            assertEquals(0, InMemoryTokenStore.clearCalls);
            assertEquals(0, InMemoryTokenStore.saveCalls);
        } finally {
            controller.destroy();
        }
    }

    @Test
    public void ordinaryOAuthStartStillKeepsAnExistingSession() throws Exception {
        AuthTokens original = syntheticTokens("retained");
        InMemoryTokenStore.syntheticTokens = original;
        ServiceController<OAuthService> controller = Robolectric
                .buildService(OAuthService.class).create();
        try {
            OAuthService service = controller.get();
            QueuedExecutor executor = queueOAuthWork(service);
            service.onStartCommand(new Intent(app, OAuthService.class)
                    .setAction(OAuthService.ACTION_START), 0, 1);
            assertEquals(0, executor.queued);
            assertSame(original, InMemoryTokenStore.syntheticTokens);
            assertEquals(0, InMemoryTokenStore.clearCalls);
            assertEquals(0, InMemoryTokenStore.saveCalls);
        } finally {
            controller.destroy();
        }
    }

    private static QueuedExecutor queueOAuthWork(OAuthService service) throws Exception {
        Field field = OAuthService.class.getDeclaredField("executor");
        field.setAccessible(true);
        ((ExecutorService) field.get(service)).shutdownNow();
        QueuedExecutor executor = new QueuedExecutor();
        field.set(service, executor);
        return executor;
    }

    /** Records entry into the real service flow without opening a socket or making a request. */
    private static final class QueuedExecutor extends AbstractExecutorService {
        int queued;
        boolean shutdown;

        @Override
        public void execute(Runnable command) {
            queued++;
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown();
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }
    }

    private static AuthTokens syntheticTokens(String identity) {
        return new AuthTokens("synthetic-access-token-not-valid", "synthetic-refresh-token-not-valid",
                "", Long.MAX_VALUE, "synthetic-" + identity, identity + "@example.test");
    }

    private static void runInBackground(Callable<Void> operation) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(operation).get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static String text(View card, int id) {
        return ((TextView) card.findViewById(id)).getText().toString();
    }

    private static View visibleAccountCard(SettingsRootFragment fragment) {
        shadowOf(Looper.getMainLooper()).idle();
        RecyclerView list = fragment.getListView();
        list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 2400);
        int position = ((PreferenceGroupAdapter) list.getAdapter())
                .getPreferenceAdapterPosition(fragment.findPreference("account_card"));
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
        assertNotNull(holder);
        TextView title = holder.itemView.findViewById(R.id.settings_account_title);
        View identity = (View) title.getParent();
        View plan = holder.itemView.findViewById(R.id.settings_account_plan);
        assertEquals("Plan and reauthentication must stay beside the account identity",
                identity.getParent(), plan.getParent());
        assertTrue("The trailing account label must not cover the identity",
                plan.getLeft() >= identity.getRight());
        return holder.itemView;
    }

    /** Authentication fixtures stay in memory and never enter a credential store or API request. */
    @Implements(value = SecureTokenStore.class, isInAndroidSdk = false)
    public static class InMemoryTokenStore {
        static AuthTokens syntheticTokens;
        static int clearCalls;
        static int saveCalls;
        static int loadCalls;
        static Exception saveFailure;

        @Implementation
        protected static AuthTokens load(Context context) {
            loadCalls++;
            return syntheticTokens;
        }

        @Implementation
        protected static boolean isSignedIn(Context context) {
            return syntheticTokens != null;
        }

        @Implementation
        protected static void clear(Context context) {
            clearCalls++;
            syntheticTokens = null;
        }

        @Implementation
        protected static void save(Context context, AuthTokens tokens) throws Exception {
            saveCalls++;
            if (saveFailure != null) {
                throw saveFailure;
            }
            syntheticTokens = tokens;
        }
    }
}
