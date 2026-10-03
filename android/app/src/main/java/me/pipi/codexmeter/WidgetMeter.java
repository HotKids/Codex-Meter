package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageCredits;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsageWindow;
import dev.bennett.codexmeter.WidgetMeters;

import android.content.Context;
import android.content.res.Resources;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/** Selected phone meter data; the renderer chooses a dial, bar or numeric balance. */
final class WidgetMeter {
    final UsageWindow window;
    final long resetAtMillis;
    final String title;
    final String value;
    final int progress;
    final int icon;

    WidgetMeter(Context context, String key, WidgetOptions options, UsageCardState state) {
        title = title(context.getResources(), key, state.snapshot);
        if (WidgetMeters.NEXT_RESET.equals(key)) {
            icon = R.drawable.ic_oui_alarm;
            window = nextWindow(state);
            resetAtMillis = resetAt(window, state.fetchedAtMillis());
            value = UsageCardFormat.absoluteReset(resetAtMillis);
            long duration = window == null ? 0L : TimeUnit.SECONDS.toMillis(window.windowSeconds);
            progress = duration <= 0 ? 0 : (int) Math.max(0L, Math.min(100L,
                    Math.round((resetAtMillis - state.nowMillis) * 100.0 / duration)));
        } else if (WidgetOptions.USAGE_CREDITS.equals(key)) {
            icon = R.drawable.ic_ms_account_balance_wallet;
            window = null;
            resetAtMillis = 0L;
            progress = 0;
            UsageCredits credits = state.snapshot == null ? null : state.snapshot.usageCredits;
            BigDecimal balance = credits == null ? null : credits.numericBalance();
            value = credits != null && credits.unlimited ? "∞"
                    : balance != null ? UsageFormat.creditBalance(balance)
                    : credits != null && !credits.balance.isEmpty() ? credits.balance
                    : UsageCardFormat.MISSING;
        } else {
            icon = WidgetMeters.WEEKLY.equals(key) ? R.drawable.ic_oui_calendar_week
                    : R.drawable.ic_oui_time;
            window = WidgetMeters.meterWindow(key, state.snapshot);
            resetAtMillis = resetAt(window, state.fetchedAtMillis());
            int percent = window == null ? -1 : window.remainingPercent();
            value = percent < 0 ? UsageCardFormat.MISSING
                    : percent + (options.showPercentSymbol ? "%" : "");
            progress = Math.max(0, percent);
        }
    }

    static String title(Resources resources, String key, UsageSnapshot snapshot) {
        if (WidgetMeters.FIVE_HOUR.equals(key)) {
            return resources.getString(R.string.widget_card_five_hour);
        }
        if (WidgetMeters.WEEKLY.equals(key)) {
            return resources.getString(WidgetMeters.weeklyMeterIsMonthly(snapshot)
                    ? R.string.widget_card_monthly : R.string.widget_card_weekly);
        }
        if (WidgetOptions.USAGE_CREDITS.equals(key)) {
            return resources.getString(R.string.widget_card_remaining_credits);
        }
        return resources.getString(R.string.widget_editor_next_reset);
    }

    private static UsageWindow nextWindow(UsageCardState state) {
        if (state.snapshot == null) {
            return null;
        }
        UsageWindow shortWindow = state.snapshot.fiveHour;
        UsageWindow longWindow = state.snapshot.longWindow();
        long shortReset = resetAt(shortWindow, state.fetchedAtMillis());
        long longReset = resetAt(longWindow, state.fetchedAtMillis());
        if (shortReset > state.nowMillis
                && (longReset <= state.nowMillis || shortReset <= longReset)) {
            return shortWindow;
        }
        return longReset > state.nowMillis ? longWindow : null;
    }

    private static long resetAt(UsageWindow window, long observedAt) {
        return window != null && window.showsResetCountdown()
                ? window.effectiveResetAtMillis(observedAt) : 0L;
    }
}
