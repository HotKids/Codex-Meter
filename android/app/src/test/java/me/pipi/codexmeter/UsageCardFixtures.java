package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageLimit;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsageWindow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Deterministic usage data shared by the card tests and preview renders. */
final class UsageCardFixtures {
    static final long NOW = 1_790_000_000_000L;
    static final String SPARK_WEEKLY = "limit:codex_bengalfox:secondary";
    static final String SPARK_FIVE_HOUR = "limit:codex_bengalfox:primary";

    private UsageCardFixtures() {
    }

    /** Plus account: 5-hour 36% used, weekly 58% used, plus a Codex Spark limit. */
    static UsageSnapshot plus() {
        return snapshot("plus", 36, 58, true);
    }

    static UsageSnapshot snapshot(String plan, int fiveHourUsed, int weeklyUsed, boolean spark) {
        UsageWindow fiveHour = window(fiveHourUsed, TimeUnit.HOURS.toSeconds(5),
                TimeUnit.MINUTES.toMillis(170));
        UsageWindow weekly = window(weeklyUsed, TimeUnit.DAYS.toSeconds(7),
                TimeUnit.HOURS.toMillis(71));
        List<UsageLimit> limits = new ArrayList<>();
        if (spark) {
            limits.add(new UsageLimit("codex_bengalfox", "GPT-5.3-Codex-Spark",
                    "codex_bengalfox", true, false,
                    window(88, TimeUnit.HOURS.toSeconds(5), TimeUnit.MINUTES.toMillis(184)),
                    window(12, TimeUnit.DAYS.toSeconds(7), TimeUnit.HOURS.toMillis(146))));
        }
        return new UsageSnapshot(plan, true, false, fiveHour, weekly, null, limits, null, 2,
                NOW - TimeUnit.MINUTES.toMillis(1));
    }

    static UsageWindow window(int usedPercent, long windowSeconds, long resetInMillis) {
        return new UsageWindow(usedPercent, windowSeconds, 0L,
                TimeUnit.MILLISECONDS.toSeconds(NOW + resetInMillis));
    }

    static ResetCreditsSnapshot credits(int available) {
        List<RateLimitResetCredit> entries = new ArrayList<>();
        for (int index = 0; index < available; index++) {
            entries.add(new RateLimitResetCredit("credit-" + index, "both", "available",
                    NOW - TimeUnit.DAYS.toMillis(2),
                    NOW + TimeUnit.DAYS.toMillis(3 + index), "Reset credit", ""));
        }
        return new ResetCreditsSnapshot(available, entries, NOW);
    }

    static UsageCardState state(UsageSnapshot snapshot, int credits) {
        return new UsageCardState(true, snapshot, credits > 0 ? credits(credits) : null, "",
                NOW);
    }

    static UsageCardState signedOut() {
        return new UsageCardState(false, null, null, "", NOW);
    }

    static List<String> keys(String... keys) {
        List<String> list = new ArrayList<>();
        Collections.addAll(list, keys);
        return list;
    }
}
