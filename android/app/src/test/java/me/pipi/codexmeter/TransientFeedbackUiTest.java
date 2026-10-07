package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
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
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class,
        shadows = SettingsAccountCardTest.InMemoryTokenStore.class)
public class TransientFeedbackUiTest {
    @Before
    public void resetSyntheticAccount() {
        RuntimeEnvironment.getApplication()
                .getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = null;
        ShadowToast.reset();
    }

    @Test
    public void signInProgressKeepsAccountPageViewsAndUsesToast() throws Exception {
        AppPreferences.setOnboardingStep(RuntimeEnvironment.getApplication(),
                OnboardingFlow.STEP_ACCOUNT);
        try (ActivityController<OnboardingActivity> controller = Robolectric
                .buildActivity(OnboardingActivity.class).create()) {
            OnboardingActivity activity = controller.get();
            LinearLayout content = (LinearLayout) field(activity, "content");
            List<View> children = children(content);
            invoke(activity, "startSignIn");
            assertEquals(activity.getString(R.string.auth_onboarding_status_preparing),
                    ShadowToast.getTextOfLatestToast());
            assertSameChildren(children, content);

            invoke(activity, "onAuthUrlReady", String.class, "https://example.test/login");
            assertEquals(activity.getString(R.string.auth_onboarding_status_browser_open),
                    ShadowToast.getTextOfLatestToast());
            assertSameChildren(children, content);
            assertEquals(activity.getString(R.string.auth_onboarding_sign_in_continue),
                    ((Button) field(activity, "signInButton")).getText().toString());
        }
    }

    @Test
    public void repeatedUpdateCheckKeepsCurrentPageWhileOneRequestRuns() throws Exception {
        UpdateActivity activity = Robolectric.buildActivity(UpdateActivity.class).get();
        activity.setTheme(R.style.AppTheme);
        LinearLayout content = new LinearLayout(activity);
        TextView currentRelease = new TextView(activity);
        currentRelease.setText("Cached release");
        content.addView(currentRelease);
        setField(activity, "content", content);
        ((ExecutorService) field(activity, "executor")).shutdownNow();
        QueuedExecutor executor = new QueuedExecutor();
        setField(activity, "executor", executor);

        invoke(activity, "checkReleases", String.class, null);
        assertEquals(activity.getString(R.string.updates_checking),
                ShadowToast.getTextOfLatestToast());
        assertSame(currentRelease, content.getChildAt(0));
        assertEquals(1, content.getChildCount());
        assertEquals(1, executor.tasks.size());
        invoke(activity, "checkReleases", String.class, null);
        assertEquals(1, executor.tasks.size());
        assertSame(currentRelease, content.getChildAt(0));
    }

    private static List<View> children(LinearLayout content) {
        List<View> result = new ArrayList<>();
        for (int index = 0; index < content.getChildCount(); index++) {
            result.add(content.getChildAt(index));
        }
        return result;
    }

    private static void assertSameChildren(List<View> expected, LinearLayout content) {
        assertEquals(expected.size(), content.getChildCount());
        for (int index = 0; index < expected.size(); index++) {
            assertSame(expected.get(index), content.getChildAt(index));
        }
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void setField(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static void invoke(Object owner, String name) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(owner);
    }

    private static void invoke(Object owner, String name, Class<?> type, Object value)
            throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name, type);
        method.setAccessible(true);
        method.invoke(owner, value);
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
