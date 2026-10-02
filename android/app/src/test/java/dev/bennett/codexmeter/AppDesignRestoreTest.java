package dev.bennett.codexmeter;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.preference.PreferenceFragmentCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import dev.bennett.codexmeter.wear.PhoneWearSync;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** Keep widget reference work from replacing the phone's original dashboard and settings. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = QuotaCardsTest.TestApp.class,
        qualifiers = "zh-rCN-w411dp-h891dp-mdpi",
        shadows = {AppDesignRestoreTest.SignedIn.class, AppDesignRestoreTest.OfflineWear.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AppDesignRestoreTest {
    @Implements(SecureTokenStore.class)
    public static class SignedIn {
        @Implementation protected static boolean isSignedIn(Context context) { return true; }
        @Implementation protected static AuthTokens load(Context context) { return null; }
    }

    @Implements(value = PhoneWearSync.class, callThroughByDefault = false)
    public static class OfflineWear { }

    @Before public void setup() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        AppPreferences.completeOnboarding(context);
        AppPreferences.setAppTheme(context, WidgetOptions.THEME_LIGHT);
        AppPreferences.setRefreshOnLaunch(context, false);
        long now = System.currentTimeMillis();
        AppPreferences.saveSnapshot(context, new UsageSnapshot("pro", true, false, null,
                new UsageWindow(64, 604800, 0, (now + 259200000L) / 1000), now));
    }

    @Test public void mainOpensOriginalWaveDashboardAndOriginalMenu() throws Exception {
        // An old widget launch extra must no longer switch the app into a separate account page.
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class)
                .putExtra("account_detail", true);
        try (var controller = Robolectric.buildActivity(MainActivity.class, intent).setup()) {
            MainActivity activity = controller.get();
            View content = activity.findViewById(android.R.id.content);
            assertEquals(1, countViews(content, UsageWaveView.class));
            assertTrue(countViews(content, UsageBurnChartView.class) > 0);
            SwipeRefreshLayout refresh = activity.findViewById(R.id.dashboard_refresh);
            assertTrue(refresh.isEnabled());
            MenuBuilder menu = new MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            assertEquals(2, menu.size());
            assertEquals(activity.getString(R.string.phone_edit_dashboard_fbfce), menu.getItem(0).getTitle());
            assertEquals(activity.getString(R.string.phone_settings_c7f73), menu.getItem(1).getTitle());
            save(content, "restored-dashboard");
            activity.onOptionsItemSelected(menu.getItem(0));
            assertEquals(DashboardReorderActivity.class.getName(),
                    shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
            activity.onOptionsItemSelected(menu.getItem(1));
            assertEquals(SettingsActivity.class.getName(),
                    shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
        }
    }

    @Test public void settingsKeepsOriginalAccountAndPageLinks() throws Exception {
        try (var controller = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity activity = controller.get();
            PreferenceFragmentCompat fragment = (PreferenceFragmentCompat)
                    activity.getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            for (String key : new String[]{"account_card", "settings_appearance", "settings_refresh_usage",
                    "settings_notifications", "settings_now_bar", "settings_updates", "settings_transfer",
                    "settings_privacy", "about_codex_meter"}) {
                assertNotNull(key, fragment.findPreference(key));
            }
            assertNull(fragment.findPreference("overview_display_mode_ui"));
            assertNull(fragment.findPreference("settings_widgets"));
            save(activity.findViewById(android.R.id.content), "restored-settings");
        }
    }

    private static int countViews(View view, Class<? extends View> type) {
        int count = type.isInstance(view) ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) count += countViews(group.getChildAt(i), type);
        }
        return count;
    }

    private static void save(View view, String name) throws Exception {
        view.measure(View.MeasureSpec.makeMeasureSpec(411, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(891, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 411, 891);
        Bitmap bitmap = Bitmap.createBitmap(411, 891, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        File directory = new File("build/reports/app-restore-previews");
        directory.mkdirs();
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        bitmap.recycle();
    }
}
