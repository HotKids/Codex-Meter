package me.pipi.codexmeter;

import dev.bennett.codexmeter.HistorySections;
import dev.bennett.codexmeter.PlanPricing;
import dev.bennett.codexmeter.UsageHistory;
import dev.bennett.codexmeter.UsagePace;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsageStats;
import dev.bennett.codexmeter.UsageWindow;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Full local-history view: scrubbable charts always, with every extra highlight —
 * chart guide, previous-window list, insight rows, and value estimates — individually
 * customizable so the page can stay as minimal as the user likes.
 */
public final class UsageHistoryActivity extends AppCompatActivity {
    private static final int MAX_BREAKDOWN_WINDOWS = 5;
    private static final int MENU_CUSTOMIZE = 8201;
    private static final int CHART_HEIGHT_DP = 200;
    private static final int CLEAR_BUTTON_HEIGHT_DP = 58;
    /** Differences under this many percentage points count as "on par" with typical pace. */
    private static final long PACE_TOLERANCE_POINTS = 2L;
    /** Date skeleton for window ranges: abbreviated month and day, e.g. "May 2". */
    private static final String DAY_SKELETON = "MMMd";
    private static final String DETAIL_SEPARATOR = " · ";
    private static final String RANGE_SEPARATOR = " – ";
    private static final String APPROXIMATELY = "≈ ";

    private LinearLayout content;
    private boolean dark;

    @Override
    protected void onCreate(Bundle state) {
        Ui.applySelectedTheme(this);
        super.onCreate(state);
        dark = Ui.isDark(this);
        content = Ui.installPage(this, getString(R.string.dashboard_usage_history), true).content;
        render();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_CUSTOMIZE, 0, R.string.dashboard_history_customize)
                .setIcon(R.drawable.ic_ms_tune)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_CUSTOMIZE) {
            showCustomizeDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Checklist of every optional highlight; changes persist and apply immediately. */
    private void showCustomizeDialog() {
        List<String> keys = HistorySections.all();
        String[] labels = new String[keys.size()];
        boolean[] checked = new boolean[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            labels[i] = SharedLabels.historySection(this, keys.get(i));
            checked[i] = visible(keys.get(i));
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.dashboard_history_highlights_title)
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) ->
                        AppPreferences.setHistorySectionVisible(this, keys.get(which), isChecked))
                .setPositiveButton(R.string.dashboard_history_done, null)
                .setOnDismissListener(dialog -> render())
                .show();
    }

    private boolean visible(String key) {
        return AppPreferences.isHistorySectionVisible(this, key);
    }

    private void render() {
        content.removeAllViews();
        UsageSnapshot snapshot = AppPreferences.loadSnapshot(this);
        UsageHistory five = AppPreferences.loadUsageHistory(this, UsageHistory.FIVE_HOUR);
        UsageHistory weekly = AppPreferences.loadUsageHistory(this, UsageHistory.WEEKLY);
        UsageHistory monthly = AppPreferences.loadUsageHistory(this, UsageHistory.MONTHLY);
        // Dollar figures ride on the value-estimates highlight; hiding it hides them all.
        PlanPricing pricing = snapshot == null || !visible(HistorySections.VALUE_ESTIMATES)
                ? null : PlanPricing.forPlan(snapshot.planType);

        if (visible(HistorySections.GUIDE)) {
            addNoteCard(getString(R.string.dashboard_history_guide));
        }

        // Windows still waiting for usage data are skipped instead of rendering blank charts.
        boolean hasCharts = false;
        UsageWindow fiveWindow = snapshot == null ? null : snapshot.fiveHour;
        if (fiveWindow != null && snapshot.fetchedAtMillis > 0L) {
            addWindowSection(getString(R.string.five_hour),
                    getString(R.string.dashboard_history_section_five_hour), fiveWindow,
                    snapshot, five, pricing);
            hasCharts = true;
        }
        UsageWindow weeklyWindow = snapshot == null ? null : snapshot.weekly;
        if (weeklyWindow != null && snapshot.fetchedAtMillis > 0L) {
            addWindowSection(getString(R.string.weekly),
                    getString(R.string.dashboard_history_section_weekly), weeklyWindow,
                    snapshot, weekly, pricing);
            hasCharts = true;
        }
        UsageWindow monthlyWindow = snapshot == null ? null : snapshot.monthly;
        if (monthlyWindow != null && snapshot.fetchedAtMillis > 0L) {
            addWindowSection(getString(R.string.dashboard_window_monthly),
                    getString(R.string.dashboard_history_section_monthly), monthlyWindow,
                    snapshot, monthly, pricing);
            hasCharts = true;
        }
        if (!hasCharts) {
            addNoteCard(getString(R.string.dashboard_history_waiting));
        }

        if (pricing != null && hasCharts) {
            content.addView(Ui.separator(this,
                    getString(R.string.dashboard_history_estimated_value)));
            content.addView(buildValueCard(snapshot, pricing));
            Ui.addSpacer(content, 20);
        }

        addClearHistoryButton(!five.samples.isEmpty() || !weekly.samples.isEmpty()
                || !monthly.samples.isEmpty());
    }

    /** A card holding one paragraph of secondary text, followed by section spacing. */
    private void addNoteCard(String text) {
        LinearLayout card = Ui.card(this, dark);
        card.addView(Ui.text(this, text, 13, Ui.secondaryText(dark)));
        content.addView(card);
        Ui.addSpacer(content, 20);
    }

    private void addClearHistoryButton(boolean hasSamples) {
        Button clear = Ui.button(this, getString(R.string.dashboard_history_clear), false, dark);
        clear.setEnabled(hasSamples);
        clear.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.dashboard_history_clear_title)
                .setMessage(R.string.dashboard_history_clear_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.dashboard_history_clear_confirm, (dialog, which) -> {
                    AppPreferences.clearUsageHistory(this);
                    render();
                })
                .show());
        content.addView(clear, new LinearLayout.LayoutParams(MATCH_PARENT,
                Ui.dp(this, CLEAR_BUTTON_HEIGHT_DP)));
    }

    /**
     * @param label chart label, e.g. "5-hour"
     * @param sectionTitle separator above the chart, e.g. "5-hour window"
     */
    private void addWindowSection(String label, String sectionTitle, UsageWindow window,
            UsageSnapshot snapshot, UsageHistory history, PlanPricing pricing) {
        content.addView(Ui.separator(this, sectionTitle));
        content.addView(buildChartCard(label, window, snapshot, history, pricing));
        Ui.addSpacer(content, 12);
        LinearLayout insights = buildInsightsCard(window, snapshot, history, pricing);
        if (insights != null) {
            content.addView(insights);
            Ui.addSpacer(content, 12);
        }
        Ui.addSpacer(content, 8);
    }

    private LinearLayout buildChartCard(String label, UsageWindow window, UsageSnapshot snapshot,
            UsageHistory history, PlanPricing pricing) {
        LinearLayout card = Ui.card(this, dark);
        card.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        long now = System.currentTimeMillis();
        UsagePace.Assessment pace = snapshot == null
                ? UsagePace.assess(null, 0L, now, UsagePace.BALANCED)
                : UsagePacePreferences.assess(this, snapshot, window, now);
        UsageBurnChartView chart = new UsageBurnChartView(this);
        chart.setScrubEnabled(true);
        chart.setData(label, window, history,
                snapshot == null ? now : snapshot.fetchedAtMillis, pace);
        card.addView(chart, new LinearLayout.LayoutParams(MATCH_PARENT,
                Ui.dp(this, CHART_HEIGHT_DP)));

        List<UsageStats.WindowStats> breakdown =
                UsageStats.windowBreakdown(history, MAX_BREAKDOWN_WINDOWS);
        boolean showWindowRows = visible(HistorySections.WINDOW_LIST) && breakdown.size() > 1;

        String defaultDetail = getString(showWindowRows
                ? R.string.dashboard_history_drag_compare : R.string.dashboard_history_drag);
        TextView scrubDetail = Ui.text(this, defaultDetail, 12, Ui.secondaryText(dark));
        LinearLayout.LayoutParams scrubParams =
                new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        scrubParams.setMargins(Ui.dp(this, 12), Ui.dp(this, 2), Ui.dp(this, 12), Ui.dp(this, 6));
        card.addView(scrubDetail, scrubParams);
        chart.setOnScrubListener(new UsageBurnChartView.OnScrubListener() {
            @Override
            public void onScrub(long timeMillis, double usedPercent, boolean historicalWindow) {
                String moment = UsageFormat.absolute(UsageHistoryActivity.this, timeMillis,
                        System.currentTimeMillis());
                long used = Math.round(usedPercent);
                String text = pricing == null
                        ? getString(R.string.dashboard_history_scrub, moment, used)
                        : getString(R.string.dashboard_history_scrub_value, moment, used,
                                PlanPricing.formatUsd(
                                        pricing.estimatedValueUsd(history.kind, usedPercent)));
                scrubDetail.setTextColor(Ui.mainText(UsageHistoryActivity.this, dark));
                scrubDetail.setText(text);
            }

            @Override
            public void onScrubEnd() {
                scrubDetail.setTextColor(Ui.secondaryText(dark));
                scrubDetail.setText(defaultDetail);
            }
        });

        if (showWindowRows) {
            View divider = new View(this);
            divider.setBackgroundColor(Ui.divider(dark));
            LinearLayout.LayoutParams dividerParams =
                    new LinearLayout.LayoutParams(MATCH_PARENT, 1);
            dividerParams.setMargins(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12),
                    Ui.dp(this, 4));
            card.addView(divider, dividerParams);
            addWindowRows(card, chart, history, breakdown, pricing);
        }
        return card;
    }

    /** Tappable per-window rows that select a window on the chart for scrubbing. */
    private void addWindowRows(LinearLayout card, UsageBurnChartView chart, UsageHistory history,
            List<UsageStats.WindowStats> breakdown, PlanPricing pricing) {
        boolean dayGranularity = UsageHistory.WEEKLY.equals(history.kind)
                || UsageHistory.MONTHLY.equals(history.kind);
        TextView[] titles = new TextView[breakdown.size()];
        // Rows run newest first; breakdown and the chart's windows are both oldest first.
        for (int index = breakdown.size() - 1; index >= 0; index--) {
            UsageStats.WindowStats stats = breakdown.get(index);
            boolean current = !stats.complete;
            String rowTitle = current ? getString(R.string.dashboard_history_current_window)
                    : windowRangeLabel(stats, dayGranularity);
            String subtitle = windowSubtitle(stats, history, pricing);

            LinearLayout row = Ui.horizontal(this, Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView titleView = Ui.text(this, rowTitle, 14,
                    current ? Ui.accent(this, dark) : Ui.mainText(this, dark));
            titleView.setTypeface(Ui.mediumTypeface(this));
            texts.addView(titleView);
            texts.addView(Ui.text(this, subtitle, 12, Ui.secondaryText(dark)));
            row.addView(texts, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
            card.addView(row, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            titles[index] = titleView;
            int chartWindowIndex = chart.windowCount() - breakdown.size() + index;
            int rowIndex = index;
            row.setOnClickListener(view -> {
                chart.setSelectedWindow(
                        current ? UsageBurnChartView.CURRENT_WINDOW : chartWindowIndex);
                for (int i = 0; i < titles.length; i++) {
                    titles[i].setTextColor(i == rowIndex ? Ui.accent(this, dark)
                            : Ui.mainText(this, dark));
                }
            });
            row.setClickable(true);
            row.setFocusable(true);
            row.setContentDescription(
                    getString(R.string.dashboard_history_inspect, rowTitle, subtitle));
        }
    }

    /** Facts about one window joined by " · ", e.g. "82% used · avg 4.1%/h · ≈ $96". */
    private String windowSubtitle(UsageStats.WindowStats stats, UsageHistory history,
            PlanPricing pricing) {
        StringBuilder subtitle = new StringBuilder(
                getString(R.string.dashboard_percent_used, stats.finalPercent));
        if (stats.averageBurnPercentPerHour > 0d) {
            subtitle.append(DETAIL_SEPARATOR).append(getString(R.string.dashboard_history_average,
                    formatRate(stats.averageBurnPercentPerHour)));
        }
        if (pricing != null) {
            subtitle.append(DETAIL_SEPARATOR).append(approximately(PlanPricing.formatUsd(
                    pricing.estimatedValueUsd(history.kind, stats.finalPercent))));
        }
        if (stats.exhausted) {
            subtitle.append(DETAIL_SEPARATOR)
                    .append(getString(R.string.dashboard_history_hit_limit));
        }
        return subtitle.toString();
    }

    private LinearLayout buildInsightsCard(UsageWindow window, UsageSnapshot snapshot,
            UsageHistory history, PlanPricing pricing) {
        long now = System.currentTimeMillis();
        long observedAt = snapshot == null ? now : snapshot.fetchedAtMillis;
        LinearLayout card = Ui.card(this, dark);
        TextView title = Ui.text(this, getString(R.string.dashboard_history_insights), 16,
                Ui.mainText(this, dark));
        title.setTypeface(Ui.mediumTypeface(this));
        card.addView(title);
        int rows = 0;

        if (visible(HistorySections.INSIGHT_PACE)) {
            String comparison = paceComparedToTypical(window, history, observedAt, now);
            if (comparison != null) {
                addStatRow(card, getString(R.string.dashboard_history_pace_vs_previous),
                        comparison);
                rows++;
            }
        }

        if (visible(HistorySections.INSIGHT_EXHAUSTION)) {
            UsagePace.Assessment pace = snapshot == null ? null
                    : UsagePacePreferences.assess(this, snapshot, window, now);
            if (pace != null && pace.available) {
                addStatRow(card, getString(R.string.dashboard_history_projected_exhaustion),
                        UsageFormat.relative(this, pace.estimatedExhaustionAtMillis, now));
                rows++;
            }
        }

        if (visible(HistorySections.INSIGHT_AVERAGE)) {
            double averageFinal = UsageStats.averageFinalPercent(history);
            if (averageFinal >= 0d) {
                addStatRow(card, getString(R.string.dashboard_history_avg_completed),
                        getString(R.string.dashboard_percent_used, Math.round(averageFinal)));
                rows++;
            }
        }

        if (visible(HistorySections.INSIGHT_PEAK)) {
            double peakBurn = UsageStats.peakBurnPercentPerHour(history);
            if (peakBurn > 0d) {
                String value = formatRate(peakBurn);
                if (pricing != null) {
                    value += DETAIL_SEPARATOR + approximately(SharedLabels.perHour(this,
                            pricing.windowValueUsd(history.kind) * peakBurn / 100d));
                }
                addStatRow(card, getString(R.string.dashboard_history_peak_burn), value);
                rows++;
            }
        }

        if (pricing != null) {
            addStatRow(card, getString(R.string.dashboard_history_value_used),
                    getString(R.string.dashboard_history_value_of,
                            PlanPricing.formatUsd(pricing.estimatedValueUsd(history.kind,
                                    window.usedPercent)),
                            PlanPricing.formatUsd(pricing.windowValueUsd(history.kind))));
            rows++;
        }
        return rows == 0 ? null : card;
    }

    /**
     * Current position against the typical pace of completed windows at the same point in the
     * window, or null when the window timing or comparable history is unavailable.
     */
    private String paceComparedToTypical(UsageWindow window, UsageHistory history,
            long observedAt, long now) {
        long resetAt = window.effectiveResetAtMillis(observedAt);
        long durationMillis = window.windowSeconds * 1000L;
        if (resetAt <= 0L || durationMillis <= 0L) {
            return null;
        }
        double elapsedFraction = 1d - Math.max(0d, Math.min(1d,
                (resetAt - now) / (double) durationMillis));
        double typical = UsageStats.typicalUsedPercentAt(history, elapsedFraction);
        if (typical < 0d) {
            return null;
        }
        long delta = Math.round(window.usedPercent - typical);
        if (delta >= PACE_TOLERANCE_POINTS) {
            return getString(R.string.dashboard_history_pace_ahead, delta);
        }
        if (delta <= -PACE_TOLERANCE_POINTS) {
            return getString(R.string.dashboard_history_pace_behind, -delta);
        }
        return getString(R.string.dashboard_history_pace_on_par);
    }

    private LinearLayout buildValueCard(UsageSnapshot snapshot, PlanPricing pricing) {
        LinearLayout card = Ui.card(this, dark);
        TextView title = Ui.text(this, SharedLabels.planPriceTitle(this,
                UsageFormat.planLabel(snapshot.planType), pricing), 16, Ui.mainText(this, dark));
        title.setTypeface(Ui.mediumTypeface(this));
        card.addView(title);
        addStatRow(card, getString(R.string.dashboard_history_included_usage),
                approximately(SharedLabels.perMonth(this, pricing.monthlyValueUsd)));
        addStatRow(card, getString(R.string.dashboard_history_weekly_allowance),
                approximately(PlanPricing.formatUsd(pricing.weeklyValueUsd())));
        addStatRow(card, getString(R.string.dashboard_history_five_hour_allowance),
                approximately(PlanPricing.formatUsd(pricing.fiveHourValueUsd())));
        addStatRow(card, getString(R.string.dashboard_history_vs_price),
                getString(R.string.dashboard_history_value_multiplier,
                        Math.round(pricing.valueMultiplier())));
        if (snapshot.weekly != null) {
            addStatRow(card, getString(R.string.dashboard_history_weekly_value_remaining),
                    approximately(PlanPricing.formatUsd(pricing.estimatedValueUsd(
                            UsageHistory.WEEKLY, snapshot.weekly.remainingPercent()))));
        }
        TextView disclaimer = Ui.text(this, getString(R.string.dashboard_history_disclaimer),
                12, Ui.secondaryText(dark));
        LinearLayout.LayoutParams disclaimerParams =
                new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        disclaimerParams.setMargins(0, Ui.dp(this, 10), 0, 0);
        card.addView(disclaimer, disclaimerParams);
        return card;
    }

    private void addStatRow(LinearLayout card, String label, String value) {
        LinearLayout row = Ui.horizontal(this, Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        rowParams.setMargins(0, Ui.dp(this, 8), 0, 0);
        TextView labelView = Ui.text(this, label, 13, Ui.secondaryText(dark));
        row.addView(labelView, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        TextView valueView = Ui.text(this, value, 13, Ui.mainText(this, dark));
        valueView.setTypeface(Typeface.create("sec", Typeface.NORMAL));
        valueView.setGravity(Gravity.END);
        row.addView(valueView, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        card.addView(row, rowParams);
    }

    /** "May 2 – May 9" for day-long windows, else "May 2 · 9:00 AM – 2:00 PM". */
    private String windowRangeLabel(UsageStats.WindowStats stats, boolean dayGranularity) {
        Date start = new Date(stats.windowStartMillis);
        Date end = new Date(stats.resetAtMillis);
        String startDay = UsageFormat.localizedPattern(DAY_SKELETON, start);
        if (dayGranularity) {
            return startDay + RANGE_SEPARATOR + UsageFormat.localizedPattern(DAY_SKELETON, end);
        }
        String times = UsageFormat.clockTime(this, start) + RANGE_SEPARATOR
                + UsageFormat.clockTime(this, end);
        return startDay + DETAIL_SEPARATOR + times;
    }

    /** Burn rate in percent per hour, with one decimal below 10%/h. */
    private String formatRate(double percentPerHour) {
        String amount = percentPerHour >= 10d
                ? Long.toString(Math.round(percentPerHour))
                : String.format(Locale.US, "%.1f", percentPerHour);
        return getString(R.string.dashboard_rate_per_hour, amount);
    }

    /** Prefixes an estimated amount with "≈ ". */
    private static String approximately(String amount) {
        return APPROXIMATELY + amount;
    }
}
