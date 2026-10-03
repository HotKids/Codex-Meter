package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroupAdapter;
import androidx.recyclerview.widget.RecyclerView;
import dev.bennett.codexmeter.UsagePace;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsageWindow;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "zh-rCN")
public class SettingsUsageLabelsTest {
    private Application app;

    @Before
    public void resetSyntheticSettings() {
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        app.getSharedPreferences("codex_meter_now_bar_prefs_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        app.getSharedPreferences("codex_meter_now_bar_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test
    public void reminderWindowLabelFollowsDataOnResumeAndKeepsSelection() {
        AppPreferences.saveSnapshot(app, monthlySnapshot());
        ResetAlertPreferences.save(app, ResetAlertPreferences.STYLE_NOTIFICATION, "weekly", 25);
        try (ActivityController<SettingsActivity> controller = page(
                SettingsActivity.PAGE_NOTIFICATIONS)) {
            SettingsNotificationsFragment fragment = (SettingsNotificationsFragment) controller
                    .get().getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            ListPreference metric = fragment.findPreference("notification_metric_ui");
            assertEquals("每月限额", metric.getEntry().toString());
            assertEquals("weekly", metric.getValue());
            TextView summary = visibleSummary(fragment, metric);
            assertEquals("每月限额", summary.getText().toString());

            controller.pause();
            AppPreferences.saveSnapshot(app, weeklySnapshot());
            controller.resume();
            assertEquals("每周限额", metric.getEntry().toString());
            assertEquals("每周限额", visibleSummary(fragment, metric).getText().toString());
            assertEquals("weekly", ResetAlertPreferences.getMetric(app));
        }
    }

    @Test
    public void livePercentAndTriggerLabelsFollowDataOnResumeAndKeepSelection() {
        AppPreferences.saveSnapshot(app, monthlySnapshot());
        NowBarPreferences.setPercentMode(app, "weekly");
        NowBarPreferences.save(app, true, "weekly", 25);
        try (ActivityController<SettingsActivity> controller = page(SettingsActivity.PAGE_NOW_BAR)) {
            SettingsNowBarFragment fragment = (SettingsNowBarFragment) controller.get()
                    .getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            ListPreference percent = fragment.findPreference("now_bar_percent_mode_ui");
            ListPreference metric = fragment.findPreference("now_bar_metric_ui");
            assertEquals("每月用量", percent.getEntry().toString());
            assertEquals("每月限额", metric.getEntry().toString());
            TextView percentSummary = visibleSummary(fragment, percent);
            TextView metricSummary = visibleSummary(fragment, metric);
            assertEquals("每月用量", percentSummary.getText().toString());
            assertEquals("每月限额", metricSummary.getText().toString());

            controller.pause();
            AppPreferences.saveSnapshot(app, weeklySnapshot());
            controller.resume();
            assertEquals("每周用量", percent.getEntry().toString());
            assertEquals("每周限额", metric.getEntry().toString());
            assertEquals("每周用量", visibleSummary(fragment, percent).getText().toString());
            assertEquals("每周限额", visibleSummary(fragment, metric).getText().toString());
            assertEquals("weekly", NowBarPreferences.getPercentMode(app));
            assertEquals("weekly", NowBarPreferences.getMetric(app));
        }
    }

    @Test
    public void rootReminderSummaryFollowsTheReportedWindow() {
        AppPreferences.saveSnapshot(app, monthlySnapshot());
        ResetAlertPreferences.save(app, ResetAlertPreferences.STYLE_NOTIFICATION, "weekly", 25);
        try (ActivityController<SettingsActivity> controller = Robolectric
                .buildActivity(SettingsActivity.class).setup()) {
            SettingsRootFragment fragment = (SettingsRootFragment) controller.get()
                    .getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            Preference reminders = fragment.findPreference("settings_notifications");
            assertTrue(reminders.getSummary().toString().contains("每月"));

            controller.pause();
            AppPreferences.saveSnapshot(app, weeklySnapshot());
            controller.resume();
            assertTrue(reminders.getSummary().toString().contains("每周"));
        }
    }

    @Test
    @Config(shadows = SettingsAccountCardTest.InMemoryTokenStore.class)
    public void rootLiveSummaryRecognizesAcceleratedOnlyStartAndBothPrerequisites() {
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = null;
        NowBarPreferences.save(app, false, "weekly", 25);
        NowBarPreferences.setAcceleratedStartEnabled(app, true);
        UsagePacePreferences.setEnabled(app, true);
        UsagePacePreferences.setSensitivity(app, UsagePace.BALANCED);
        try (ActivityController<SettingsActivity> controller = Robolectric
                .buildActivity(SettingsActivity.class).setup()) {
            SettingsRootFragment fragment = (SettingsRootFragment) controller.get()
                    .getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            Preference live = fragment.findPreference("settings_now_bar");
            assertEquals(app.getString(R.string.alerts_now_bar_summary_waiting),
                    visibleSummary(fragment, live).getText().toString());

            controller.pause();
            UsagePacePreferences.setEnabled(app, false);
            controller.resume();
            assertEquals(app.getString(R.string.settings_summary_now_bar_manual),
                    visibleSummary(fragment, live).getText().toString());

            controller.pause();
            UsagePacePreferences.setEnabled(app, true);
            UsagePacePreferences.setSensitivity(app, UsagePace.OFF);
            controller.resume();
            assertEquals(app.getString(R.string.settings_summary_now_bar_manual),
                    visibleSummary(fragment, live).getText().toString());
        }
    }

    @Test
    public void acceleratedStartExplainsBothPrerequisitesAndRestoresNormalSummary() {
        UsagePacePreferences.setEnabled(app, false);
        UsagePacePreferences.setSensitivity(app, UsagePace.BALANCED);
        try (ActivityController<SettingsActivity> controller = page(SettingsActivity.PAGE_NOW_BAR)) {
            SettingsNowBarFragment fragment = (SettingsNowBarFragment) controller.get()
                    .getSupportFragmentManager().findFragmentById(R.id.settings_fragment);
            Preference accelerated = fragment.findPreference("now_bar_accelerated_ui");
            assertUnavailable(accelerated);

            controller.pause();
            UsagePacePreferences.setEnabled(app, true);
            UsagePacePreferences.setSensitivity(app, UsagePace.OFF);
            controller.resume();
            assertUnavailable(accelerated);

            controller.pause();
            UsagePacePreferences.setSensitivity(app, UsagePace.BALANCED);
            controller.resume();
            assertTrue(accelerated.isEnabled());
            assertEquals(app.getString(R.string.alerts_now_bar_accelerated_summary),
                    accelerated.getSummary().toString());
        }
    }

    private void assertUnavailable(Preference preference) {
        assertFalse(preference.isEnabled());
        assertEquals(app.getString(R.string.alerts_now_bar_accelerated_unavailable_summary),
                preference.getSummary().toString());
    }

    private ActivityController<SettingsActivity> page(String name) {
        return Robolectric.buildActivity(SettingsActivity.class,
                SettingsActivity.pageIntent(app, name)).setup();
    }

    private static TextView visibleSummary(SettingsPageFragment fragment, Preference preference) {
        layoutPreferences(fragment);
        RecyclerView list = fragment.getListView();
        int position = ((PreferenceGroupAdapter) list.getAdapter())
                .getPreferenceAdapterPosition(preference);
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
        assertNotNull(holder);
        TextView summary = holder.itemView.findViewById(android.R.id.summary);
        assertNotNull(summary);
        return summary;
    }

    private static void layoutPreferences(SettingsPageFragment fragment) {
        shadowOf(Looper.getMainLooper()).idle();
        RecyclerView list = fragment.getListView();
        list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 2400);
    }

    private static UsageSnapshot monthlySnapshot() {
        // The reported window is authoritative even when the plan label has not caught up.
        return new UsageSnapshot("plus", true, false, null, null,
                new UsageWindow(40, 2592000L, 3600L, 0L), null, null, 0,
                System.currentTimeMillis());
    }

    private static UsageSnapshot weeklySnapshot() {
        return new UsageSnapshot("free", true, false, null,
                new UsageWindow(40, 604800L, 3600L, 0L),
                new UsageWindow(40, 2592000L, 3600L, 0L), null, null, 0,
                System.currentTimeMillis());
    }
}
