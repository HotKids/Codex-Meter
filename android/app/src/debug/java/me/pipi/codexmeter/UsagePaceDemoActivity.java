package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageCredits;
import dev.bennett.codexmeter.UsageHistory;
import dev.bennett.codexmeter.UsageLimit;
import dev.bennett.codexmeter.UsagePace;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsageWindow;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/** Debug-only entry point that opens the dashboard with deterministic usage and reset credits. */
public final class UsagePaceDemoActivity extends Activity {
    // Shortcut extras that jump straight to another screen instead of seeding the demo.
    private static final String EXTRA_OPEN_SETTINGS_PAGE = "open_settings_page";
    private static final String EXTRA_OPEN_USAGE_HISTORY = "open_usage_history";
    private static final String EXTRA_OPEN_WIDGET_METRIC = "open_widget_metric";
    private static final String EXTRA_RESUME_LIVE_SETTINGS = "resume_live_settings";
    // Demo seeding options.
    private static final String EXTRA_CREDITS_BALANCE = "credits_balance";
    private static final String EXTRA_CREDITS_NONE = "credits_none";
    private static final String EXTRA_START_LIVE_PREVIEW = "start_live_preview";
    private static final String EXTRA_APP_WIDGET_ID = "appWidgetId";
    private static final String EXTRA_SETTINGS_PAGE = "settings_page";
    private static final int DEFAULT_DEMO_WIDGET_ID = 42;

    private static final long FIVE_HOUR_WINDOW_SECONDS = TimeUnit.HOURS.toSeconds(5);
    private static final long WEEKLY_WINDOW_SECONDS = TimeUnit.DAYS.toSeconds(7);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (openRequestedShortcut()) {
            finish();
            return;
        }
        try {
            seedDemoAndOpen();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not seed usage pace demo", exception);
        } finally {
            finish();
        }
    }

    /** Handles the screen-shortcut extras; returns true when one was launched. */
    private boolean openRequestedShortcut() {
        Intent intent = getIntent();
        String settingsPage = intent.getStringExtra(EXTRA_OPEN_SETTINGS_PAGE);
        if (settingsPage != null && !settingsPage.isEmpty()) {
            startActivity(newTask(SettingsActivity.class)
                    .putExtra(EXTRA_SETTINGS_PAGE, settingsPage));
            return true;
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_USAGE_HISTORY, false)) {
            startActivity(newTask(UsageHistoryActivity.class));
            return true;
        }
        String widgetMetric = intent.getStringExtra(EXTRA_OPEN_WIDGET_METRIC);
        if (widgetMetric != null && !widgetMetric.isEmpty()) {
            int widgetId = intent.getIntExtra(EXTRA_APP_WIDGET_ID, DEFAULT_DEMO_WIDGET_ID);
            saveSingleMetricWidget(widgetId, widgetMetric);
            startActivity(newTask(WidgetConfigActivity.class)
                    .putExtra(EXTRA_APP_WIDGET_ID, widgetId));
            return true;
        }
        if (intent.getBooleanExtra(EXTRA_RESUME_LIVE_SETTINGS, false)) {
            startActivity(newTask(SettingsActivity.class));
            return true;
        }
        return false;
    }

    private Intent newTask(Class<?> target) {
        return new Intent(this, target)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** Stores the default widget options with only the display metric overridden. */
    private void saveSingleMetricWidget(int widgetId, String metric) {
        WidgetOptions defaults = AppPreferences.loadDefaultWidgetOptions(this);
        AppPreferences.saveWidgetOptions(this, widgetId, new WidgetOptions(
                defaults.layout, defaults.density, defaults.surfaceStyle,
                defaults.graphicScale, defaults.theme, defaults.accent, defaults.opacity,
                defaults.resetMode, defaults.displayMode, metric,
                defaults.showTitle, defaults.showPlan, defaults.showUpdated,
                defaults.showRefresh, defaults.showResetCredits, defaults.showResetAction)
                .withPercentSymbol(defaults.showPercentSymbol));
    }

    private void seedDemoAndOpen() throws Exception {
        Intent intent = getIntent();
        // Optional overrides for demoing the non-configurable zero-balance auto-hide.
        String creditsBalance = intent.getStringExtra(EXTRA_CREDITS_BALANCE);
        boolean creditsNone = intent.getBooleanExtra(EXTRA_CREDITS_NONE, false);
        long now = System.currentTimeMillis();
        // Both current windows started an hour ago.
        long fiveHourReset = now - TimeUnit.HOURS.toMillis(1) + TimeUnit.HOURS.toMillis(5);
        long weeklyReset = now - TimeUnit.HOURS.toMillis(1) + TimeUnit.DAYS.toMillis(7);
        UsageSnapshot snapshot = new UsageSnapshot("pro", true, false,
                new UsageWindow(37, FIVE_HOUR_WINDOW_SECONDS, 0L, fiveHourReset / 1000L),
                new UsageWindow(61, WEEKLY_WINDOW_SECONDS, 0L, weeklyReset / 1000L),
                Arrays.asList(new UsageLimit(
                        "codex-spark",
                        "GPT-5.3-Codex-Spark",
                        "codex_bengalfox",
                        true,
                        false,
                        new UsageWindow(24, FIVE_HOUR_WINDOW_SECONDS, 0L,
                                (now + TimeUnit.HOURS.toMillis(3)) / 1000L),
                        new UsageWindow(42, WEEKLY_WINDOW_SECONDS, 0L,
                                (now + TimeUnit.DAYS.toMillis(5)) / 1000L))),
                creditsNone
                        ? new UsageCredits(false, false, "")
                        : new UsageCredits(true, false,
                                creditsBalance == null ? "2500" : creditsBalance),
                3,
                now);
        SecureTokenStore.save(this, new AuthTokens(
                "debug-demo-access", "debug-demo-refresh", "", Long.MAX_VALUE,
                "debug-demo-account", "demo@codexmeter.local"));
        AppPreferences.saveSnapshot(this, snapshot);
        seedHistory(now, fiveHourReset, weeklyReset);
        AppPreferences.saveResetCredits(this, new ResetCreditsSnapshot(3, Arrays.asList(
                new RateLimitResetCredit("demo-soon", "both", "available", now,
                        now + TimeUnit.DAYS.toMillis(1), "Reset credit 1", ""),
                new RateLimitResetCredit("demo-middle", "both", "available", now,
                        now + TimeUnit.DAYS.toMillis(3), "Reset credit 2", ""),
                new RateLimitResetCredit("demo-later", "both", "available", now,
                        now + TimeUnit.DAYS.toMillis(7), "Reset credit 3", "")),
                now));
        AppPreferences.setRefreshOnLaunch(this, false);
        AppPreferences.setDashboardVisibility(this, true, true, true, true, true, true);
        AppPreferences.completeOnboarding(this);
        UsagePacePreferences.setEnabled(this, true);
        UsagePacePreferences.setSensitivity(this, UsagePace.BALANCED);
        boolean livePreview = intent.getBooleanExtra(EXTRA_START_LIVE_PREVIEW, false);
        if (livePreview && !NowBarManager.startPreview(this)) {
            throw new IllegalStateException("Could not start live notification preview");
        }
        startActivity(newTask(livePreview ? SettingsActivity.class : MainActivity.class));
    }

    private void seedHistory(long now, long fiveHourReset, long weeklyReset) {
        // Varied historical shapes exercise the per-window breakdown, typical-pace
        // comparison, and scrubbable overlays: quiet, steady, heavy, and bursty windows.
        int[][] fiveShapes = {
                {3, 7, 12, 18, 22},
                {6, 14, 25, 33, 41},
                {12, 30, 52, 74, 96},
                {10, 26, 38, 61, 83},
        };
        // Current 5-hour window climbs to the snapshot's 37% over the elapsed hour.
        int[] fiveUsed = {8, 15, 22, 30, 37};
        AppPreferences.saveUsageHistory(this, buildHistory(UsageHistory.FIVE_HOUR,
                FIVE_HOUR_WINDOW_SECONDS, TimeUnit.MINUTES.toMillis(45), fiveShapes,
                fiveHourReset, fiveUsed, now));

        int[][] weeklyShapes = {
                {5, 9, 14, 22, 30, 38},
                {8, 19, 33, 47, 58, 71},
                {15, 34, 52, 78, 95, 100},
                {11, 24, 39, 52, 66, 84},
        };
        // Current weekly window climbs to the snapshot's 61% over the elapsed hour.
        int[] weeklyUsed = {12, 28, 41, 53, 61};
        AppPreferences.saveUsageHistory(this, buildHistory(UsageHistory.WEEKLY,
                WEEKLY_WINDOW_SECONDS, TimeUnit.HOURS.toMillis(24), weeklyShapes,
                weeklyReset, weeklyUsed, now));
    }

    /**
     * Builds a history with one completed window per entry of {@code pastShapes} (oldest first,
     * samples every {@code sampleSpacingMillis} from each window's start) followed by the
     * current window's samples, taken every 12 minutes up to now.
     */
    private static UsageHistory buildHistory(String kind, long windowSeconds,
            long sampleSpacingMillis, int[][] pastShapes, long currentReset, int[] currentUsed,
            long now) {
        long windowMillis = TimeUnit.SECONDS.toMillis(windowSeconds);
        UsageHistory history = UsageHistory.empty(kind);
        for (int windowsAgo = pastShapes.length; windowsAgo >= 1; windowsAgo--) {
            long reset = currentReset - windowMillis * windowsAgo;
            int[] shape = pastShapes[pastShapes.length - windowsAgo];
            for (int point = 0; point < shape.length; point++) {
                long observed = reset - windowMillis + sampleSpacingMillis * (point + 1);
                history = history.append(new UsageWindow(shape[point], windowSeconds, 0L,
                        reset / 1000L), observed);
            }
        }
        for (int point = 0; point < currentUsed.length; point++) {
            history = history.append(new UsageWindow(currentUsed[point], windowSeconds, 0L,
                            currentReset / 1000L),
                    now - TimeUnit.MINUTES.toMillis(48L - 12L * point));
        }
        return history;
    }
}
