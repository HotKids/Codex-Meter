package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class,
        shadows = SettingsAccountCardTest.InMemoryTokenStore.class)
public class SettingsAccountCardTest {
    private Application app;

    @Before
    public void resetSyntheticAccount() {
        app = RuntimeEnvironment.getApplication();
        InMemoryTokenStore.syntheticTokens = null;
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
            assertEquals(View.GONE, card.findViewById(R.id.settings_account_plan).getVisibility());
            CardItemView action = card.findViewById(R.id.settings_account_action);
            assertEquals(app.getString(R.string.settings_account_sign_in),
                    action.getTitleView().getText().toString());
        }
    }

    private static AuthTokens syntheticTokens(String identity) {
        return new AuthTokens("synthetic-access-token-not-valid", "synthetic-refresh-token-not-valid",
                "", Long.MAX_VALUE, "synthetic-" + identity, identity + "@example.test");
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
        return holder.itemView;
    }

    /** Authentication fixtures stay in memory and never enter a credential store or API request. */
    @Implements(value = SecureTokenStore.class, isInAndroidSdk = false)
    public static class InMemoryTokenStore {
        static AuthTokens syntheticTokens;

        @Implementation
        protected static AuthTokens load(Context context) {
            return syntheticTokens;
        }

        @Implementation
        protected static boolean isSignedIn(Context context) {
            return syntheticTokens != null;
        }
    }
}
