package dev.bennett.codexmeter;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import dev.bennett.codexmeter.wear.PhoneWearSync;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The One UI dashboard. Cards are built programmatically from the cached usage snapshot and
 * rebuilt whenever usage, reset credits, releases, or the sign-in state change.
 */
public final class MainActivity extends AppCompatActivity {
    private static final int MENU_SETTINGS = 8101;
    private static final int MENU_REORDER = 8102;
    private static final String PAGE_TITLE = "Codex Meter";
    private static final String EXTRA_START_SIGN_IN = "start_sign_in";
    // OAuth redirects come back as codexmeter://auth/complete…
    private static final String AUTH_CALLBACK_SCHEME = "codexmeter";
    private static final String AUTH_CALLBACK_HOST = "auth";
    private static final String AUTH_COMPLETE_PATH_PREFIX = "/complete";
    /** Opening the app refreshes usage once the cached snapshot is older than this. */
    private static final long LAUNCH_REFRESH_AGE_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final long HOUR_SECONDS = TimeUnit.HOURS.toSeconds(1);
    private static final long DAY_SECONDS = TimeUnit.DAYS.toSeconds(1);
    // Additional-limit windows in these ranges are labelled "Weekly" and "N-hour".
    private static final long WEEKLY_MIN_SECONDS = TimeUnit.DAYS.toSeconds(5);
    private static final long WEEKLY_MAX_SECONDS = TimeUnit.DAYS.toSeconds(9);
    private static final long HOURLY_MIN_SECONDS = TimeUnit.HOURS.toSeconds(3);
    private static final long HOURLY_MAX_SECONDS = TimeUnit.HOURS.toSeconds(8);
    private static final int SECTION_SPACING_DP = 20;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 240;
    private static final int MAX_FAILURE_DETAIL_LENGTH = 200;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    /** Theme inputs this activity was created with; a change on resume recreates it. */
    private String appliedTheme;
    private boolean appliedMaterialYou;
    private boolean dark;
    private LinearLayout content;
    private SwipeRefreshLayout swipeRefresh;
    private boolean receiverRegistered;
    private boolean launchSignInRequested;
    private String lastLaunchedAuthUrl = "";

    private final BroadcastReceiver appEventsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent == null ? null : intent.getAction();
            if (AppConstants.ACTION_OAUTH_READY.equals(action)) {
                String authUrl = intent.getStringExtra(AppConstants.EXTRA_AUTH_URL);
                if (authUrl != null && !authUrl.isEmpty()) {
                    openAuthUrl(authUrl);
                }
            } else if (AppConstants.ACTION_OAUTH_RESULT.equals(action)) {
                onOAuthResult(intent);
            } else if (AppConstants.ACTION_USAGE_UPDATED.equals(action)
                    || AppConstants.ACTION_RESET_CREDITS_UPDATED.equals(action)
                    || AppConstants.ACTION_RELEASES_UPDATED.equals(action)) {
                rebuild();
            }
        }
    };

    // -------------------------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        this.appliedTheme = AppPreferences.getAppTheme(this);
        this.appliedMaterialYou = AppPreferences.isMaterialYouEnabled(this);
        Ui.applySelectedTheme(this);
        super.onCreate(savedInstanceState);
        PhoneWearSync.pushAll(this);
        if (routeToOnboarding(getIntent())) {
            return;
        }
        this.dark = Ui.isDark(this);
        this.content = Ui.installPage(this, PAGE_TITLE, false).content;
        setUpSwipeRefresh();
        handleLaunchIntent(getIntent());
        WidgetUpgradeRepair.runIfNeeded(this);
        rebuild();
        RefreshScheduler.schedulePeriodic(this);
        ReleaseUpdateScheduler.ensureScheduled(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_REORDER, 0, "Edit dashboard")
                .setIcon(R.drawable.ic_oui_edit_outline)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, MENU_SETTINGS, 1, "Settings")
                .setIcon(R.drawable.ic_oui_settings_outline)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_SETTINGS) {
            DiagnosticLog.info(this, "user", "settings_opened");
            Ui.startSecondaryActivity(this, SettingsActivity.class);
            return true;
        }
        if (item.getItemId() == MENU_REORDER) {
            DiagnosticLog.info(this, "user", "dashboard_editor_opened");
            Ui.startSecondaryActivity(this, DashboardReorderActivity.class);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (routeToOnboarding(intent)) {
            return;
        }
        handleLaunchIntent(intent);
        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appearanceChanged()) {
            recreate();
            return;
        }
        handleLaunchIntent(getIntent());
        rebuild();
    }

    @Override
    protected void onStart() {
        super.onStart();
        RefreshEngagement.onForeground(this);
        registerAppEventsReceiver();
        rebuild();
        if (this.launchSignInRequested) {
            this.launchSignInRequested = false;
            startOrContinueSignIn();
        }
        if (SecureTokenStore.isSignedIn(this)) {
            AppPreferences.setOAuthPending(this, false, "");
            refreshIfStaleOnLaunch();
        }
    }

    @Override
    protected void onStop() {
        RefreshEngagement.onBackground(this);
        if (AppPreferences.getAutomaticRefresh(this)) {
            RefreshScheduler.schedulePeriodic(this);
        }
        unregisterAppEventsReceiver();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        this.executor.shutdownNow();
        super.onDestroy();
    }

    private void setUpSwipeRefresh() {
        this.swipeRefresh = findViewById(R.id.dashboard_refresh);
        int refreshAccent = Ui.accent(this, this.dark);
        // OneUI's four-dot SwipeRefresh drawable indexes two palette entries while drawing.
        this.swipeRefresh.setColorSchemeColors(refreshAccent, refreshAccent);
        this.swipeRefresh.setProgressBackgroundColorSchemeColor(Ui.cardColor(this, this.dark));
        this.swipeRefresh.setOnRefreshListener(this::refreshFromPull);
    }

    /** True when the theme, dark mode, or Material You setting changed since creation. */
    private boolean appearanceChanged() {
        String appTheme = AppPreferences.getAppTheme(this);
        boolean isDark = Ui.isDark(this);
        boolean materialYou = AppPreferences.isMaterialYouEnabled(this);
        return !appTheme.equals(this.appliedTheme)
                || isDark != this.dark
                || materialYou != this.appliedMaterialYou;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerAppEventsReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(AppConstants.ACTION_OAUTH_READY);
        filter.addAction(AppConstants.ACTION_OAUTH_RESULT);
        filter.addAction(AppConstants.ACTION_USAGE_UPDATED);
        filter.addAction(AppConstants.ACTION_RESET_CREDITS_UPDATED);
        filter.addAction(AppConstants.ACTION_RELEASES_UPDATED);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(this.appEventsReceiver, filter, AppConstants.INTERNAL_PERMISSION,
                        null, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(this.appEventsReceiver, filter, AppConstants.INTERNAL_PERMISSION,
                        null);
            }
            this.receiverRegistered = true;
        } catch (RuntimeException e) {
            this.receiverRegistered = false;
            AppPreferences.setSchedulerError(this, "App update receiver: " + failureDetail(e));
        }
    }

    private void unregisterAppEventsReceiver() {
        if (!this.receiverRegistered) {
            return;
        }
        try {
            unregisterReceiver(this.appEventsReceiver);
        } catch (RuntimeException ignored) {
            // Already unregistered.
        }
        this.receiverRegistered = false;
    }

    private void refreshIfStaleOnLaunch() {
        UsageSnapshot snapshot = AppPreferences.loadSnapshot(this);
        if (AppPreferences.getRefreshOnLaunch(this)
                && (snapshot == null
                        || System.currentTimeMillis() - snapshot.fetchedAtMillis
                                > LAUNCH_REFRESH_AGE_MILLIS)) {
            RefreshScheduler.scheduleImmediate(this);
        }
    }

    // -------------------------------------------------------------------------------------------
    // Launch intents and onboarding
    // -------------------------------------------------------------------------------------------

    private void handleLaunchIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        if (intent.getBooleanExtra(EXTRA_START_SIGN_IN, false)) {
            this.launchSignInRequested = true;
            intent.removeExtra(EXTRA_START_SIGN_IN);
        }
        if (isAuthCallback(intent.getData())) {
            if (SecureTokenStore.isSignedIn(this)) {
                AppPreferences.setOAuthPending(this, false, "");
                RefreshScheduler.scheduleImmediate(this);
            }
            intent.setData(null);
        }
    }

    /** Sends first-run users to onboarding; returns true when this activity is finishing. */
    private boolean routeToOnboarding(Intent intent) {
        boolean oauthReturn = isOAuthReturnIntent(intent);
        int action = OnboardingFlow.launchAction(
                AppPreferences.isOnboardingComplete(this),
                SecureTokenStore.isSignedIn(this),
                oauthReturn);
        if (action == OnboardingFlow.LAUNCH_MAIN_AND_COMPLETE) {
            // Existing signed-in installs predate onboarding and should not be interrupted.
            AppPreferences.completeOnboarding(this);
            return false;
        }
        if (action != OnboardingFlow.LAUNCH_ONBOARDING) {
            return false;
        }
        if (oauthReturn) {
            if (SecureTokenStore.isSignedIn(this)) {
                AppPreferences.setOAuthPending(this, false, "");
            }
            intent.setData(null);
        }
        startActivity(new Intent(this, OnboardingActivity.class)
                .putExtra(OnboardingActivity.EXTRA_AUTH_RETURN, oauthReturn)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
        return true;
    }

    private static boolean isAuthCallback(Uri data) {
        return data != null
                && AUTH_CALLBACK_SCHEME.equals(data.getScheme())
                && AUTH_CALLBACK_HOST.equals(data.getHost());
    }

    private static boolean isOAuthReturnIntent(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        return isAuthCallback(data)
                && data.getPath() != null
                && data.getPath().startsWith(AUTH_COMPLETE_PATH_PREFIX);
    }

    // -------------------------------------------------------------------------------------------
    // Page assembly
    // -------------------------------------------------------------------------------------------

    /**
     * Rebuilds the page: update prompt, dashboard cards, then either the sign-in button
     * (signed out) or an empty-state hint (signed in with nothing to show).
     */
    private void rebuild() {
        if (this.content == null) {
            return;
        }
        this.content.removeAllViews();
        GitHubRelease update = UpdatePreferences.availableUpdate(this);
        if (update != null) {
            this.content.addView(buildUpdateCard(update));
            Ui.addSpacer(this.content, SECTION_SPACING_DP);
        }
        LinearLayout dashboard = buildUsageDashboard();
        if (dashboard.getChildCount() > 0) {
            this.content.addView(dashboard);
            Ui.addSpacer(this.content, SECTION_SPACING_DP);
        }
        boolean signedIn = SecureTokenStore.isSignedIn(this);
        if (!signedIn) {
            addSignInButton();
        }
        if (signedIn && dashboard.getChildCount() == 0) {
            addEmptyDashboardHint();
        }
    }

    private void addSignInButton() {
        Button signIn = Ui.nativePrimaryButton(this,
                AppPreferences.isOAuthPending(this) ? "Continue sign-in" : "Sign in with ChatGPT");
        signIn.setOnClickListener(view -> startOrContinueSignIn());
        this.content.addView(signIn, new LinearLayout.LayoutParams(MATCH_PARENT, Ui.dp(this, 60)));
        Ui.addSpacer(this.content, SECTION_SPACING_DP);
    }

    private void addEmptyDashboardHint() {
        TextView empty = Ui.text(this,
                "No dashboard items are available. Refresh usage or choose items in "
                        + "Settings → Refresh & usage.",
                14.0f, Ui.secondaryText(this.dark));
        empty.setGravity(Gravity.CENTER);
        this.content.addView(empty, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
    }

    private LinearLayout buildUpdateCard(GitHubRelease release) {
        boolean returnToStable = UpdateChannel.isReturnToStable(release,
                UpdatePreferences.installedVersion(this));
        LinearLayout card = Ui.card(this, this.dark);
        TextView title = Ui.text(this, updateTitle(release, returnToStable), 18,
                Ui.mainText(this.dark));
        title.setTypeface(Ui.mediumTypeface(this));
        card.addView(title);
        TextView summary = Ui.text(this, updateSummary(release, returnToStable), 13,
                Ui.secondaryText(this.dark));
        LinearLayout.LayoutParams summaryParams =
                new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        summaryParams.setMargins(0, Ui.dp(this, 7), 0, Ui.dp(this, 14));
        card.addView(summary, summaryParams);
        Button update = Ui.nativePrimaryButton(this, "Review update");
        update.setOnClickListener(view -> startActivity(new Intent(this, UpdateActivity.class)
                .putExtra(UpdateActivity.EXTRA_VERSION, release.version)));
        card.addView(update, new LinearLayout.LayoutParams(MATCH_PARENT, Ui.dp(this, 60)));
        return card;
    }

    private static String updateTitle(GitHubRelease release, boolean returnToStable) {
        if (returnToStable) {
            return "Return to Codex Meter " + release.version;
        }
        return "Codex Meter " + release.version + " is ready";
    }

    private static String updateSummary(GitHubRelease release, boolean returnToStable) {
        if (returnToStable) {
            return "You are back on the stable channel. The stable APK installs in place "
                    + "over this alpha build after checksum verification.";
        }
        if (release.prerelease) {
            return "A signed alpha release is available. The APK will be checksum-verified "
                    + "before Android asks you to approve installation.";
        }
        return "A signed GitHub release is available. The APK will be checksum-verified "
                + "before Android asks you to approve installation.";
    }

    // -------------------------------------------------------------------------------------------
    // Dashboard sections
    // -------------------------------------------------------------------------------------------

    /**
     * Builds the visible dashboard sections in the user's saved order. Returns an empty column
     * when signed out or when nothing is available.
     */
    private LinearLayout buildUsageDashboard() {
        UsageSnapshot snapshot = AppPreferences.loadSnapshot(this);
        boolean signedIn = SecureTokenStore.isSignedIn(this);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        if (!signedIn) {
            return column;
        }
        Map<String, List<UsageLimit>> limitsByKey = groupAdditionalLimits(snapshot);
        List<String> available = availableSections(snapshot, limitsByKey.keySet());
        // Successive meter cards alternate their wave phase.
        boolean inverted = false;
        for (String key : DashboardSections.resolveOrder(
                AppPreferences.getDashboardOrder(this), available)) {
            if (DashboardSections.FIVE_HOUR.equals(key)) {
                addDashboardCard(column, buildMetricCard(
                        "5-hour", snapshot, snapshot.fiveHour, inverted));
                inverted = !inverted;
            } else if (DashboardSections.WEEKLY.equals(key)) {
                addDashboardCard(column, buildMetricCard(
                        "Weekly", snapshot, snapshot.weekly, inverted));
                inverted = !inverted;
            } else if (DashboardSections.MONTHLY.equals(key)) {
                addDashboardCard(column, buildMetricCard(
                        "Monthly", snapshot, snapshot.monthly, inverted));
                inverted = !inverted;
            } else if (DashboardSections.USAGE_CREDITS.equals(key)) {
                addDashboardCard(column, buildUsageCreditsCard(snapshot.usageCredits));
            } else if (DashboardSections.USAGE_HISTORY.equals(key)) {
                addDashboardCard(column, buildUsageHistoryCard());
            } else if (DashboardSections.RESET_CREDITS.equals(key)) {
                addDashboardCard(column, buildResetCreditsCard());
            } else {
                List<UsageLimit> group = limitsByKey.get(key);
                if (group == null) {
                    continue;
                }
                for (UsageLimit limit : group) {
                    if (limit.primary != null) {
                        addDashboardCard(column, buildMetricCard(
                                limitWindowLabel(limit, limit.primary), snapshot, limit.primary,
                                inverted));
                        inverted = !inverted;
                    }
                    if (limit.secondary != null) {
                        addDashboardCard(column, buildMetricCard(
                                limitWindowLabel(limit, limit.secondary), snapshot,
                                limit.secondary, inverted));
                        inverted = !inverted;
                    }
                }
            }
        }
        return column;
    }

    /** Groups the snapshot's additional (model-specific) limits by dashboard section key. */
    private static Map<String, List<UsageLimit>> groupAdditionalLimits(UsageSnapshot snapshot) {
        Map<String, List<UsageLimit>> limitsByKey = new LinkedHashMap<>();
        if (snapshot == null) {
            return limitsByKey;
        }
        for (UsageLimit limit : snapshot.additionalLimits) {
            String key = DashboardSections.limitKey(limit);
            List<UsageLimit> group = limitsByKey.get(key);
            if (group == null) {
                group = new ArrayList<>();
                limitsByKey.put(key, group);
            }
            group.add(limit);
        }
        return limitsByKey;
    }

    /** Section keys that are enabled in settings and have data to show, in default order. */
    private List<String> availableSections(UsageSnapshot snapshot,
            Collection<String> additionalLimitKeys) {
        List<String> available = new ArrayList<>();
        if (snapshot != null) {
            if (AppPreferences.showDashboardFiveHour(this) && snapshot.fiveHour != null) {
                available.add(DashboardSections.FIVE_HOUR);
            }
            if (AppPreferences.showDashboardWeekly(this) && snapshot.weekly != null) {
                available.add(DashboardSections.WEEKLY);
            }
            if (AppPreferences.showDashboardMonthly(this) && snapshot.monthly != null) {
                available.add(DashboardSections.MONTHLY);
            }
            if (AppPreferences.showDashboardAdditionalLimits(this)) {
                for (String key : additionalLimitKeys) {
                    if (!AppPreferences.isDashboardSectionHidden(this, key)) {
                        available.add(key);
                    }
                }
            }
            // Zero, near-zero, and negative balances are always hidden regardless of settings.
            if (AppPreferences.showDashboardUsageCredits(this) && snapshot.usageCredits != null
                    && snapshot.usageCredits.shouldDisplay()) {
                available.add(DashboardSections.USAGE_CREDITS);
            }
            // Usage history only appears once at least one window can feed a burn chart.
            if (AppPreferences.showDashboardUsageHistory(this)
                    && snapshot.fetchedAtMillis > 0L
                    && (snapshot.fiveHour != null || snapshot.weekly != null
                            || snapshot.monthly != null)) {
                available.add(DashboardSections.USAGE_HISTORY);
            }
        }
        // Zero available resets always hide the card, even when the Edit dashboard switch is on.
        if (AppPreferences.showDashboardResetCredits(this)
                && shouldShowResetCreditsCard(snapshot)) {
            available.add(DashboardSections.RESET_CREDITS);
        }
        return available;
    }

    private void addDashboardCard(LinearLayout column, View card) {
        if (column.getChildCount() > 0) {
            Ui.addSpacer(column, SECTION_SPACING_DP);
        }
        column.addView(card);
    }

    /** Full-bleed animated wave card for one usage window. */
    private LinearLayout buildMetricCard(String label, UsageSnapshot snapshot, UsageWindow window,
            boolean invertedWave) {
        LinearLayout card = Ui.card(this, this.dark);
        card.setPadding(0, 0, 0, 0);
        card.setMinimumHeight(Ui.dp(this, 103.0f));
        long now = System.currentTimeMillis();
        String reset = UsageFormat.reset(this, window, WidgetOptions.RESET_RELATIVE,
                snapshot.fetchedAtMillis, now);
        UsagePace.Assessment pace = UsagePacePreferences.assess(this, snapshot, window, now);
        UsageWaveView wave = new UsageWaveView(this);
        wave.setUsage(label, reset, UsageFormat.estimatedRemaining(pace),
                window.remainingPercent(),
                window.windowSeconds >= DAY_SECONDS
                        ? R.drawable.ic_oui_calendar_week : R.drawable.ic_oui_time,
                invertedWave, pace.accelerated);
        card.addView(wave, new LinearLayout.LayoutParams(MATCH_PARENT, Ui.dp(this, 103.0f)));
        return card;
    }

    private LinearLayout buildUsageHistoryCard() {
        LinearLayout card = Ui.card(this, this.dark);
        card.setPadding(Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 12));
        UsageSnapshot snapshot = AppPreferences.loadSnapshot(this);
        long now = System.currentTimeMillis();
        TextView title = Ui.text(this, "Usage history", 18, Ui.mainText(this.dark));
        title.setTypeface(Ui.mediumTypeface(this));
        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        titleParams.setMargins(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        card.addView(title, titleParams);
        TextView detail = Ui.text(this,
                "On-device burn trends improve estimates as samples accumulate.",
                12, Ui.secondaryText(this.dark));
        LinearLayout.LayoutParams detailParams =
                new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        detailParams.setMargins(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        card.addView(detail, detailParams);

        // Windows still waiting for usage data would only render a blank "Waiting for usage
        // data" chart, so they are dropped from the card until OpenAI reports them.
        boolean hasCharts = false;
        UsageWindow fiveWindow = snapshot == null ? null : snapshot.fiveHour;
        if (fiveWindow != null && snapshot.fetchedAtMillis > 0L) {
            addBurnChart(card, "5-hour", fiveWindow, UsageHistory.FIVE_HOUR, snapshot, now);
            hasCharts = true;
        }
        UsageWindow weeklyWindow = snapshot == null ? null : snapshot.weekly;
        if (weeklyWindow != null && snapshot.fetchedAtMillis > 0L) {
            addBurnChart(card, "Weekly", weeklyWindow, UsageHistory.WEEKLY, snapshot, now);
            hasCharts = true;
        }
        UsageWindow monthlyWindow = snapshot == null ? null : snapshot.monthly;
        if (monthlyWindow != null && snapshot.fetchedAtMillis > 0L) {
            addBurnChart(card, "Monthly", monthlyWindow, UsageHistory.MONTHLY, snapshot,
                    now);
            hasCharts = true;
        }

        if (!hasCharts) {
            TextView waiting = Ui.text(this,
                    "Charts appear once OpenAI reports your 5-hour, weekly, or monthly "
                            + "usage windows.",
                    12, Ui.secondaryText(this.dark));
            LinearLayout.LayoutParams waitingParams =
                    new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            waitingParams.setMargins(Ui.dp(this, 10), Ui.dp(this, 8),
                    Ui.dp(this, 10), Ui.dp(this, 8));
            card.addView(waiting, waitingParams);
        }

        Button open = Ui.button(this, "View history", false, this.dark);
        open.setOnClickListener(
                view -> Ui.startSecondaryActivity(this, UsageHistoryActivity.class));
        LinearLayout.LayoutParams openParams =
                new LinearLayout.LayoutParams(MATCH_PARENT, Ui.dp(this, 54));
        openParams.setMargins(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), 0);
        card.addView(open, openParams);
        return card;
    }

    private void addBurnChart(LinearLayout card, String label, UsageWindow window,
            String historyKind, UsageSnapshot snapshot, long now) {
        UsageBurnChartView chart = new UsageBurnChartView(this);
        chart.setData(label, window,
                AppPreferences.loadUsageHistory(this, historyKind),
                snapshot.fetchedAtMillis,
                UsagePacePreferences.assess(this, snapshot, window, now));
        card.addView(chart, new LinearLayout.LayoutParams(MATCH_PARENT, Ui.dp(this, 126)));
    }

    private LinearLayout buildUsageCreditsCard(UsageCredits credits) {
        LinearLayout card = Ui.card(this, this.dark);
        TextView title = Ui.text(this, "Usage credits", 18, Ui.mainText(this.dark));
        title.setTypeface(Ui.mediumTypeface(this));
        card.addView(title);
        card.addView(buildIconDetailRow(R.drawable.ic_oui_credit_card_outline,
                usageCreditBalance(credits), usageCreditsSummary(credits)));
        return card;
    }

    /** Left-aligned icon + value + summary row used inside the credit dashboard cards. */
    private LinearLayout buildIconDetailRow(int icon, String value, String summary) {
        LinearLayout row = Ui.horizontal(this, Gravity.CENTER_VERTICAL);
        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setImageTintList(ColorStateList.valueOf(Ui.mainText(this.dark)));
        row.addView(image, new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView valueText = Ui.text(this, value, 17.0f, Ui.mainText(this.dark));
        valueText.setTypeface(Ui.mediumTypeface(this));
        labels.addView(valueText);
        TextView summaryText = Ui.text(this, summary, 13.0f, Ui.secondaryText(this.dark));
        LinearLayout.LayoutParams summaryParams =
                new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        summaryParams.setMargins(0, Ui.dp(this, 2), 0, 0);
        labels.addView(summaryText, summaryParams);
        LinearLayout.LayoutParams labelParams =
                new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f);
        labelParams.setMargins(Ui.dp(this, 16), 0, 0, 0);
        row.addView(labels, labelParams);

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        rowParams.setMargins(0, Ui.dp(this, 12), 0, 0);
        row.setLayoutParams(rowParams);
        return row;
    }

    private static String usageCreditBalance(UsageCredits credits) {
        if (credits.unlimited) {
            return "Unlimited";
        }
        if (credits.balance.isEmpty()) {
            return credits.hasCredits ? "Credits available" : "No purchased credits";
        }
        try {
            BigDecimal amount = new BigDecimal(credits.balance.replace(",", ""));
            NumberFormat format = NumberFormat.getNumberInstance(Locale.getDefault());
            format.setMaximumFractionDigits(2);
            return format.format(amount) + " credits";
        } catch (NumberFormatException ignored) {
            return credits.balance;
        }
    }

    private static String usageCreditsSummary(UsageCredits credits) {
        if (credits.unlimited) {
            return "Usage-credit balance is not capped";
        }
        if (credits.balance.isEmpty() && !credits.hasCredits) {
            return "Purchase credits in ChatGPT Codex";
        }
        return "Purchased Codex usage-credit balance";
    }

    /** Card title for one window of an additional limit, e.g. "GPT-5 Spark · Weekly". */
    private static String limitWindowLabel(UsageLimit limit, UsageWindow window) {
        return limitTitle(limit) + " · " + cadenceLabel(window);
    }

    private static String cadenceLabel(UsageWindow window) {
        long seconds = window.windowSeconds;
        if (seconds >= WEEKLY_MIN_SECONDS && seconds <= WEEKLY_MAX_SECONDS) {
            return "Weekly";
        }
        if (seconds >= HOURLY_MIN_SECONDS && seconds <= HOURLY_MAX_SECONDS) {
            long hours = Math.max(1L, Math.round(seconds / (double) HOUR_SECONDS));
            return hours + "-hour";
        }
        if (seconds % DAY_SECONDS == 0L) {
            return seconds / DAY_SECONDS + "-day";
        }
        if (seconds % HOUR_SECONDS == 0L) {
            return seconds / HOUR_SECONDS + "-hour";
        }
        return "Usage";
    }

    private static String limitTitle(UsageLimit limit) {
        if (limit.limitReached) {
            return limit.displayName() + " (limit reached)";
        }
        if (!limit.allowed) {
            return limit.displayName() + " (unavailable)";
        }
        return limit.displayName();
    }

    private LinearLayout buildResetCreditsCard() {
        boolean signedIn = SecureTokenStore.isSignedIn(this);
        ResetCreditsSnapshot credits = AppPreferences.loadResetCredits(this);
        int available = credits == null ? 0 : credits.availableCount;
        long now = System.currentTimeMillis();
        long nextExpiry = credits == null ? 0L : credits.nextExpiryMillis(now);

        LinearLayout card = Ui.card(this, this.dark);
        TextView title = Ui.text(this, "Reset credits", 18, Ui.mainText(this.dark));
        title.setTypeface(Ui.mediumTypeface(this));
        card.addView(title);
        card.addView(buildIconDetailRow(R.drawable.ic_oui_battery,
                resetCreditsTitle(signedIn, available),
                resetCreditsSummary(signedIn, available, nextExpiry, now)));

        if (signedIn) {
            card.setOnClickListener(view -> openResetCredits());
            Button button = Ui.nativePrimaryButton(this,
                    available > 0 ? "Use 1 reset" : "No resets available");
            button.setEnabled(available > 0);
            button.setOnClickListener(view -> openResetCredits());
            LinearLayout.LayoutParams buttonParams =
                    new LinearLayout.LayoutParams(MATCH_PARENT, Ui.dp(this, 60.0f));
            buttonParams.setMargins(0, Ui.dp(this, 16.0f), 0, 0);
            card.addView(button, buttonParams);
        }
        return card;
    }

    private static String resetCreditsTitle(boolean signedIn, int available) {
        if (!signedIn) {
            return "Reset credits";
        }
        return ResetCreditActivity.availableResetsLabel(available);
    }

    private String resetCreditsSummary(boolean signedIn, int available, long nextExpiry,
            long now) {
        if (!signedIn) {
            return "Sign in to view reset credits";
        }
        if (nextExpiry > 0L) {
            return "Next expires " + ResetCreditActivity.expiryText(this, nextExpiry, now);
        }
        if (available > 0) {
            return "Expiration details unavailable";
        }
        return "Earn credits from ChatGPT Codex";
    }

    private void openResetCredits() {
        Ui.startSecondaryActivity(this, ResetCreditActivity.class);
    }

    /**
     * Prefer the detailed reset-credits cache; fall back to the usage-endpoint summary count.
     * Unknown inventory never surfaces an empty card.
     */
    private boolean shouldShowResetCreditsCard(UsageSnapshot snapshot) {
        ResetCreditsSnapshot credits = AppPreferences.loadResetCredits(this);
        if (credits != null) {
            return credits.shouldDisplay();
        }
        return snapshot != null
                && ResetCreditsSnapshot.shouldDisplayCount(snapshot.resetCreditsAvailable);
    }

    // -------------------------------------------------------------------------------------------
    // Sign-in and refresh
    // -------------------------------------------------------------------------------------------

    private void onOAuthResult(Intent intent) {
        boolean success = intent.getBooleanExtra(AppConstants.EXTRA_SUCCESS, false);
        String message = intent.getStringExtra(AppConstants.EXTRA_MESSAGE);
        if (message == null) {
            message = success ? "Signed in." : "Sign-in failed.";
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        PhoneWearSync.pushAll(this);
        rebuild();
    }

    /** Starts (or resumes) the browser OAuth flow; the service broadcasts the URL back. */
    private void startOrContinueSignIn() {
        DiagnosticLog.info(this, "user", "sign_in_requested",
                "already_signed_in", SecureTokenStore.isSignedIn(this));
        if (SecureTokenStore.isSignedIn(this)) {
            AppPreferences.setOAuthPending(this, false, "");
            rebuild();
            return;
        }
        try {
            startForegroundService(new Intent(this, OAuthService.class)
                    .setAction(OAuthService.ACTION_START));
            String status = AppPreferences.isOAuthPending(this)
                    ? "Resuming secure OpenAI sign-in…"
                    : "Opening secure OpenAI sign-in…";
            Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            DiagnosticLog.error(this, "auth", "sign_in_service_start_failed", e);
            AppPreferences.setOAuthPending(this, false, "");
            Toast.makeText(this, "Could not start sign-in: " + failureDetail(e),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Opens the sign-in page in the browser. A repeated URL is only relaunched while the
     * dashboard has focus, so a broadcast arriving behind the browser does not reopen it.
     */
    private void openAuthUrl(String url) {
        if (url.equals(this.lastLaunchedAuthUrl) && !hasWindowFocus()) {
            return;
        }
        this.lastLaunchedAuthUrl = url;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException e) {
            Toast.makeText(this, "No browser is available to complete sign-in.",
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Pull-to-refresh: fetches usage on the background executor, then rebuilds. */
    private void refreshFromPull() {
        DiagnosticLog.info(this, "user", "manual_refresh_requested", "source", "pull");
        if (!SecureTokenStore.isSignedIn(this)) {
            DiagnosticLog.warn(this, "user", "manual_refresh_rejected",
                    "source", "pull", "reason", "signed_out");
            this.swipeRefresh.setRefreshing(false);
            Toast.makeText(this, "Sign in from Settings to refresh usage.",
                    Toast.LENGTH_SHORT).show();
            Ui.startSecondaryActivity(this, SettingsActivity.class);
            return;
        }
        Context app = getApplicationContext();
        this.executor.execute(() -> {
            try {
                RefreshScheduler.scheduleAtNextReset(app, UsageApi.refreshAndCache(app));
                WidgetRenderer.updateAll(app);
                runOnUiThread(() -> {
                    DiagnosticLog.info(app, "user", "manual_refresh_finished", "source", "pull");
                    this.swipeRefresh.setRefreshing(false);
                    rebuild();
                });
            } catch (Exception e) {
                DiagnosticLog.error(app, "user", "manual_refresh_failed", e, "source", "pull");
                AppPreferences.setLastError(app, safeMessage(e));
                WidgetRenderer.updateAll(app);
                runOnUiThread(() -> {
                    this.swipeRefresh.setRefreshing(false);
                    Toast.makeText(this, safeMessage(e), Toast.LENGTH_LONG).show();
                    rebuild();
                });
            }
        });
    }

    /** User-facing error text: the exception message capped in length, or a generic failure. */
    public static String safeMessage(Exception exc) {
        String message = exc.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "The operation failed.";
        }
        return message.length() > MAX_ERROR_MESSAGE_LENGTH
                ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH)
                : message;
    }

    /** Short diagnostic text for a runtime failure, falling back to the exception type. */
    private static String failureDetail(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > MAX_FAILURE_DETAIL_LENGTH
                ? message.substring(0, MAX_FAILURE_DETAIL_LENGTH)
                : message;
    }
}
