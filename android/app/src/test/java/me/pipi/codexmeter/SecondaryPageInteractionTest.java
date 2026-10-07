package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SeslProgressBar;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, shadows = {
        SettingsAccountCardTest.InMemoryTokenStore.class,
        SecondaryPageInteractionTest.FailingResetCreditApi.class,
        SecondaryPageInteractionTest.SyntheticReleaseClient.class})
public class SecondaryPageInteractionTest {
    private static final String RELEASE_JSON = "[{\"tag_name\":\"v0.9\","
            + "\"html_url\":\"https://github.com/HotKids/Codex-Meter/releases/tag/v0.9\","
            + "\"assets\":[{\"name\":\"CodexMeter-me.pipi.codexmeter-0.9.apk\",\"size\":100,"
            + "\"browser_download_url\":\"https://github.com/HotKids/Codex-Meter/releases/download/v0.9/app.apk\"},"
            + "{\"name\":\"SHA256SUMS.txt\","
            + "\"browser_download_url\":\"https://github.com/HotKids/Codex-Meter/releases/download/v0.9/SHA256SUMS.txt\"}]}]";
    private static final String RESET_FAILURE = "Synthetic reset inventory failure";
    private Application app;

    @Before
    public void resetSyntheticState() {
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        app.getSharedPreferences("codex_meter_updates_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = null;
        SyntheticReleaseClient.fail = false;
        ShadowToast.reset();
    }

    @Test
    public void firstUpdateCheckShowsInstalledVersionWithoutLoadingText() throws Exception {
        try (ActivityController<UpdateActivity> controller = Robolectric
                .buildActivity(UpdateActivity.class)) {
            UpdateActivity activity = controller.get();
            QueuedExecutor executor = replaceExecutor(activity);
            controller.create();
            LinearLayout content = (LinearLayout) field(activity, "content");
            assertTrue("A check must not leave an empty page", content.getChildCount() > 0);
            assertTrue(hasText(content, activity.getString(R.string.updates_current_version_title,
                    UpdatePreferences.installedVersion(activity))));
            assertFalse(hasText(content, activity.getString(R.string.updates_checking)));
            assertFalse(hasText(content,
                    activity.getString(R.string.updates_no_installable_releases_published)));
            assertFalse(firstButton(content).isEnabled());
            assertEquals(activity.getString(R.string.updates_checking),
                    ShadowToast.getTextOfLatestToast());
            assertEquals(1, executor.tasks.size());
            assertRefreshStopped(activity);
        }
    }

    @Test
    public void cachedUpdateCheckKeepsCardAndDisablesActionsUntilComplete() throws Exception {
        UpdatePreferences.saveSuccess(app, RELEASE_JSON, "");
        assertEquals("0.9.0", UpdatePreferences.findVersion(app, "0.9").version);
        Intent intent = new Intent(app, UpdateActivity.class)
                .putExtra(UpdateActivity.EXTRA_VERSION, "0.9")
                .putExtra(UpdateActivity.EXTRA_FORCE_CHECK, true);
        try (ActivityController<UpdateActivity> controller = Robolectric
                .buildActivity(UpdateActivity.class, intent)) {
            UpdateActivity activity = controller.get();
            QueuedExecutor executor = replaceExecutor(activity);
            controller.create();
            LinearLayout content = (LinearLayout) field(activity, "content");
            View card = content.getChildAt(0);
            assertNotNull("Cached release content must render before checking", card);
            assertTrue(hasText(content,
                    activity.getString(R.string.updates_version_available_title, "0.9.0")));
            assertFalse(firstButton(content).isEnabled());
            assertTrue(field(activity, "progress") instanceof SeslProgressBar);
            assertEquals(1000, ((SeslProgressBar) field(activity, "progress")).getMax());
            invoke(activity, "checkReleases", new Class<?>[]{String.class}, (Object) null);
            assertEquals(1, executor.tasks.size());
            assertSame(card, content.getChildAt(0));
            executor.tasks.remove(0).run();
            assertTrue(firstButton(content).isEnabled());
            assertTrue(hasText(content,
                    activity.getString(R.string.updates_version_available_title, "0.9.0")));
            assertRefreshStopped(activity);
        }
    }

    @Test
    public void retryKeepsErrorCardAndRecoversEnabledActionAfterFailure() throws Exception {
        try (ActivityController<UpdateActivity> controller = Robolectric
                .buildActivity(UpdateActivity.class)) {
            UpdateActivity activity = controller.get();
            QueuedExecutor executor = replaceExecutor(activity);
            controller.create();
            SyntheticReleaseClient.fail = true;
            executor.tasks.remove(0).run();
            LinearLayout content = (LinearLayout) field(activity, "content");
            View errorCard = content.getChildAt(0);
            Button retry = firstButton(content);
            assertTrue(retry.isEnabled());
            retry.performClick();
            assertSame(errorCard, content.getChildAt(0));
            assertFalse(retry.isEnabled());
            assertEquals(1, executor.tasks.size());
            executor.tasks.remove(0).run();
            assertTrue(firstButton(content).isEnabled());
        }
    }

    @Test
    public void failedResetRefreshUpdatesVisiblePage() throws Exception {
        try (ActivityController<ResetCreditActivity> controller = Robolectric
                .buildActivity(ResetCreditActivity.class).create()) {
            ResetCreditActivity activity = controller.get();
            QueuedExecutor executor = replaceExecutor(activity);
            signInSyntheticAccount();
            invoke(activity, "refreshDetailsIfNeeded", new Class<?>[0]);
            assertEquals(1, executor.tasks.size());
            executor.tasks.remove(0).run();
            assertTrue(hasText((ViewGroup) field(activity, "content"), RESET_FAILURE));
            assertRefreshStopped(activity);
        }
    }

    @Test
    public void resetRefreshCompletionDoesNotChangeDestroyedPage() throws Exception {
        ActivityController<ResetCreditActivity> controller = Robolectric
                .buildActivity(ResetCreditActivity.class).create();
        ResetCreditActivity activity = controller.get();
        QueuedExecutor executor = replaceExecutor(activity);
        signInSyntheticAccount();
        invoke(activity, "refreshDetailsIfNeeded", new Class<?>[0]);
        LinearLayout content = (LinearLayout) field(activity, "content");
        View originalCard = content.getChildAt(1);
        controller.destroy();
        executor.tasks.remove(0).run();
        assertEquals(RESET_FAILURE, AppPreferences.getResetCreditsError(app));
        assertSame(originalCard, content.getChildAt(1));
        assertFalse(hasText(content, RESET_FAILURE));
    }

    @Test
    public void signedOutQueuedResetRefreshDoesNotWriteOrChangePage() throws Exception {
        try (ActivityController<ResetCreditActivity> controller = Robolectric
                .buildActivity(ResetCreditActivity.class).create()) {
            ResetCreditActivity activity = controller.get();
            QueuedExecutor executor = replaceExecutor(activity);
            signInSyntheticAccount();
            invoke(activity, "refreshDetailsIfNeeded", new Class<?>[0]);
            LinearLayout content = (LinearLayout) field(activity, "content");
            View originalCard = content.getChildAt(1);
            UsageApi.signOut(app);
            executor.tasks.remove(0).run();
            assertEquals("", AppPreferences.getResetCreditsError(app));
            assertSame(originalCard, content.getChildAt(1));
        }
    }

    @Test
    public void restoredSecondaryPagesCannotKeepAnOldRefreshSpinner() throws Exception {
        try (ActivityController<ResetCreditActivity> reset = Robolectric
                .buildActivity(ResetCreditActivity.class).setup();
                ActivityController<UpdateActivity> update = Robolectric
                        .buildActivity(UpdateActivity.class)) {
            replaceExecutor(update.get());
            update.setup();
            Bundle resetState = new Bundle();
            Bundle updateState = new Bundle();
            ((SwipeRefreshLayout) reset.get().findViewById(R.id.dashboard_refresh))
                    .setRefreshing(true);
            ((SwipeRefreshLayout) update.get().findViewById(R.id.dashboard_refresh))
                    .setRefreshing(true);
            reset.saveInstanceState(resetState);
            update.saveInstanceState(updateState);
            try (ActivityController<ResetCreditActivity> restoredReset = Robolectric
                    .buildActivity(ResetCreditActivity.class);
                    ActivityController<UpdateActivity> restoredUpdate = Robolectric
                            .buildActivity(UpdateActivity.class)) {
                replaceExecutor(restoredUpdate.get());
                restoredReset.setup(resetState);
                restoredUpdate.setup(updateState);
                assertRefreshStopped(restoredReset.get());
                assertRefreshStopped(restoredUpdate.get());
            }
        }
    }

    @Test
    public void historyCustomizationUsesSeslDialog() throws Exception {
        try (ActivityController<UsageHistoryActivity> controller = Robolectric
                .buildActivity(UsageHistoryActivity.class).setup()) {
            invoke(controller.get(), "showCustomizeDialog", new Class<?>[0]);
            assertTrue(ShadowDialog.getLatestDialog() instanceof AlertDialog);
            ShadowDialog.getLatestDialog().dismiss();
        }
    }

    private static void assertRefreshStopped(android.app.Activity activity) {
        SwipeRefreshLayout refresh = activity.findViewById(R.id.dashboard_refresh);
        assertFalse(refresh.isEnabled());
        assertFalse(refresh.isRefreshing());
    }

    private static void signInSyntheticAccount() {
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = new AuthTokens(
                "synthetic-access-not-valid", "synthetic-refresh-not-valid", "",
                Long.MAX_VALUE, "synthetic-account", "synthetic@example.test");
    }

    private static Button firstButton(ViewGroup root) {
        for (int index = 0; index < root.getChildCount(); index++) {
            View child = root.getChildAt(index);
            if (child instanceof Button) {
                return (Button) child;
            }
            if (child instanceof ViewGroup) {
                Button button = firstButton((ViewGroup) child);
                if (button != null) {
                    return button;
                }
            }
        }
        return null;
    }

    private static boolean hasText(ViewGroup root, String text) {
        for (int index = 0; index < root.getChildCount(); index++) {
            View child = root.getChildAt(index);
            if (child instanceof TextView && text.contentEquals(((TextView) child).getText())) {
                return true;
            }
            if (child instanceof ViewGroup && hasText((ViewGroup) child, text)) {
                return true;
            }
        }
        return false;
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static QueuedExecutor replaceExecutor(Object owner) throws Exception {
        ((ExecutorService) field(owner, "executor")).shutdownNow();
        QueuedExecutor executor = new QueuedExecutor();
        Field field = owner.getClass().getDeclaredField("executor");
        field.setAccessible(true);
        field.set(owner, executor);
        return executor;
    }

    private static void invoke(Object owner, String name, Class<?>[] types, Object... values)
            throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(owner, values);
    }

    @Implements(value = ResetCreditApi.class, isInAndroidSdk = false)
    public static class FailingResetCreditApi {
        @Implementation
        protected static ResetCreditsSnapshot refreshAndCache(Context context,
                UsageApi.Session session) throws Exception {
            session.requireCurrent();
            throw new IllegalStateException(RESET_FAILURE);
        }
    }

    @Implements(value = ReleaseUpdateClient.class, isInAndroidSdk = false)
    public static class SyntheticReleaseClient {
        static boolean fail;

        @Implementation
        protected static List<GitHubRelease> check(Context context) throws Exception {
            if (fail) {
                throw new IllegalStateException("Synthetic update check failure");
            }
            UpdatePreferences.saveSuccess(context, RELEASE_JSON, "");
            return UpdatePreferences.releases(context);
        }
    }

    private static final class QueuedExecutor extends AbstractExecutorService {
        final List<Runnable> tasks = new ArrayList<>();
        private boolean stopped;

        @Override public void execute(Runnable task) { tasks.add(task); }
        @Override public void shutdown() { stopped = true; }
        @Override public List<Runnable> shutdownNow() {
            stopped = true;
            return Collections.emptyList();
        }
        @Override public boolean isShutdown() { return stopped; }
        @Override public boolean isTerminated() { return stopped; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return stopped; }
    }
}
