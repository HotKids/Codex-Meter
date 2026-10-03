package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.widget.TextView;
import dev.oneuiproject.oneui.preference.LayoutPreference;
import dev.oneuiproject.oneui.widget.CardItemView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class SettingsAccountCardTest {
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
}
