package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageLimit;
import dev.bennett.codexmeter.UsageCredits;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsageWindow;
import dev.bennett.codexmeter.WidgetMeters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.Application;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "en-rUS")
public class WidgetMeterTest {
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);
    private static final long DAY = TimeUnit.DAYS.toMillis(1);

    @Test
    public void missingUsageKeepsItsSlotWithNoInventedValue() {
        UsageCardState empty = new UsageCardState(true, null, null, "", UsageCardFixtures.NOW);
        for (UsageCardState state : new UsageCardState[] {empty, UsageCardFixtures.signedOut()}) {
            for (String key : UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY)) {
                WidgetMeter meter = meter(key, WidgetOptions.defaults(), state);
                assertNull(meter.window);
                assertEquals("—", meter.value);
                assertEquals(0, meter.progress);
            }
        }
    }

    @Test
    public void usageAlwaysShowsRemainingAndHonorsPercentSymbol() {
        UsageCardState state = UsageCardFixtures.state(UsageCardFixtures.plus(), 0);
        WidgetMeter remaining = meter(WidgetMeters.FIVE_HOUR,
                options(WidgetOptions.DISPLAY_REMAINING, true), state);
        assertEquals("64%", remaining.value);
        assertEquals(64, remaining.progress);
        assertEquals(R.drawable.ic_oui_time, remaining.icon);

        WidgetMeter used = meter(WidgetMeters.FIVE_HOUR,
                options(WidgetOptions.DISPLAY_USED, false), state);
        assertEquals("64", used.value);
        assertEquals(64, used.progress);

        WidgetMeter weekly = meter(WidgetMeters.WEEKLY,
                options(WidgetOptions.DISPLAY_REMAINING, false), state);
        assertEquals("42", weekly.value);
        assertEquals(42, weekly.progress);
        assertEquals(R.drawable.ic_oui_calendar_week, weekly.icon);
    }

    @Test
    public void remainingBalanceUsesDashboardPrecisionAndKeepsTheReportedValue() {
        UsageCredits credits = new UsageCredits(true, false, "62437.28578");
        UsageSnapshot snapshot = new UsageSnapshot("plus", true, false, null, null, null,
                Collections.emptyList(), credits, -1, UsageCardFixtures.NOW);
        WidgetMeter meter = meter(WidgetOptions.USAGE_CREDITS, WidgetOptions.defaults(),
                UsageCardFixtures.state(snapshot, 0));

        assertEquals("62,437.29", meter.value);
        assertEquals("62437.28578", credits.balance);
    }

    @Test
    public void nextResetChoosesEarliestCoreWindowAndIgnoresEarlierModelLimit() {
        UsageWindow fiveHour = UsageCardFixtures.window(20, TimeUnit.HOURS.toSeconds(5), 3 * HOUR);
        UsageWindow weekly = UsageCardFixtures.window(30, TimeUnit.DAYS.toSeconds(7), DAY);
        UsageSnapshot snapshot = snapshot(fiveHour, weekly, null, earlierModelLimit());
        WidgetMeter shortReset = nextReset(snapshot);
        assertEquals(UsageCardFormat.absoluteReset(UsageCardFixtures.NOW + 3 * HOUR), shortReset.value);
        assertEquals(60, shortReset.progress);
        assertEquals(R.drawable.ic_oui_alarm, shortReset.icon);

        UsageWindow earlierWeekly = UsageCardFixtures.window(30, TimeUnit.DAYS.toSeconds(7), HOUR);
        WidgetMeter longReset = nextReset(snapshot(fiveHour, earlierWeekly, null,
                earlierModelLimit()));
        assertEquals(UsageCardFormat.absoluteReset(UsageCardFixtures.NOW + HOUR), longReset.value);
        assertEquals(1, longReset.progress);
    }

    @Test
    public void expiredShortWindowDoesNotHideTheFutureMonthlyLongWindow() {
        UsageWindow expired = UsageCardFixtures.window(20, TimeUnit.HOURS.toSeconds(5), -HOUR);
        UsageWindow monthly = UsageCardFixtures.window(10, TimeUnit.DAYS.toSeconds(30), 3 * DAY);
        WidgetMeter meter = nextReset(snapshot(expired, null, monthly, Collections.emptyList()));

        assertEquals(UsageCardFormat.absoluteReset(UsageCardFixtures.NOW + 3 * DAY), meter.value);
        assertEquals(10, meter.progress);
    }

    @Test
    public void nextResetDoesNotInventATimelineOrUseOnlyAModelReset() {
        UsageWindow untimed = new UsageWindow(0, TimeUnit.HOURS.toSeconds(5), 0, 0);
        UsageWindow expired = UsageCardFixtures.window(20, TimeUnit.HOURS.toSeconds(5), -HOUR);
        for (UsageWindow fiveHour : new UsageWindow[] {untimed, expired}) {
            WidgetMeter meter = nextReset(snapshot(fiveHour, null, null, earlierModelLimit()));
            assertEquals("—", meter.value);
            assertEquals(0, meter.progress);
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    public void exactResetTimeStaysAnchoredAcrossRenders() {
        UsageWindow weekly = UsageCardFixtures.window(30, TimeUnit.DAYS.toSeconds(7),
                6 * DAY + 11 * HOUR);
        UsageSnapshot snapshot = snapshot(null, weekly, null, Collections.emptyList());
        String date = UsageCardFormat.absoluteReset(UsageCardFixtures.NOW + 6 * DAY + 11 * HOUR);
        assertEquals(date, nextReset(snapshot).value);
        UsageCardState later = new UsageCardState(true, snapshot, null, "",
                UsageCardFixtures.NOW + 2 * HOUR);
        assertEquals(date, meter(WidgetMeters.NEXT_RESET, WidgetOptions.defaults(), later).value);
    }

    private static WidgetMeter nextReset(UsageSnapshot snapshot) {
        return meter(WidgetMeters.NEXT_RESET, WidgetOptions.defaults(),
                UsageCardFixtures.state(snapshot, 0));
    }

    private static WidgetMeter meter(String key, WidgetOptions options, UsageCardState state) {
        Application app = RuntimeEnvironment.getApplication();
        return new WidgetMeter(app, key, options, state);
    }

    private static WidgetOptions options(String displayMode, boolean showPercentSymbol) {
        return new WidgetOptions(WidgetOptions.STYLE_AUTO, WidgetOptions.THEME_SYSTEM,
                WidgetOptions.ACCENT_APP, WidgetOptions.DEFAULT_OPACITY,
                WidgetOptions.RESET_HIDDEN, displayMode).withPercentSymbol(showPercentSymbol);
    }

    private static UsageSnapshot snapshot(UsageWindow fiveHour, UsageWindow weekly,
            UsageWindow monthly, List<UsageLimit> additionalLimits) {
        return new UsageSnapshot("plus", true, false, fiveHour, weekly, monthly,
                additionalLimits, null, 0, UsageCardFixtures.NOW);
    }

    private static List<UsageLimit> earlierModelLimit() {
        UsageWindow primary = UsageCardFixtures.window(10, TimeUnit.HOURS.toSeconds(5), HOUR / 2);
        return Collections.singletonList(new UsageLimit("extra", "Extra model", "extra",
                true, false, primary, null));
    }
}
