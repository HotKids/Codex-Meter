package me.pipi.codexmeter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure scheduling model for reset-credit expiry reminders. */
public final class ResetCreditExpiryReminder {
    public static final long MIN_LEAD_TIME_MS = 60_000L;
    public static final long MAX_LEAD_TIME_MS = 365L * 24L * 60L * 60L * 1000L;

    private static final Comparator<ResetCreditExpiryReminder> CHRONOLOGICAL = Comparator
            .comparingLong((ResetCreditExpiryReminder reminder) -> reminder.triggerAtMillis)
            .thenComparingLong(reminder -> reminder.expiresAtMillis)
            .thenComparingLong(reminder -> reminder.leadTimeMillis)
            .thenComparing(reminder -> reminder.creditId);

    public final String creditId;
    public final long expiresAtMillis;
    public final long leadTimeMillis;
    public final long triggerAtMillis;

    private ResetCreditExpiryReminder(String creditId, long expiresAtMillis,
            long leadTimeMillis) {
        this.creditId = creditId == null ? "" : creditId;
        this.expiresAtMillis = expiresAtMillis;
        this.leadTimeMillis = leadTimeMillis;
        this.triggerAtMillis = Math.max(0L, expiresAtMillis - leadTimeMillis);
    }

    /** Identifies this reminder for deduplication across reschedules. */
    public String token() {
        return token(creditId, expiresAtMillis, leadTimeMillis);
    }

    public static String token(String creditId, long expiresAtMillis, long leadTimeMillis) {
        return (creditId == null ? "" : creditId) + ":" + expiresAtMillis + ":"
                + leadTimeMillis;
    }

    static boolean isValidLeadTime(long leadTimeMillis) {
        return leadTimeMillis >= MIN_LEAD_TIME_MS && leadTimeMillis <= MAX_LEAD_TIME_MS;
    }

    /**
     * Plans one reminder per available, unexpired credit and valid distinct lead time, ordered
     * by trigger time.
     */
    public static List<ResetCreditExpiryReminder> plan(List<RateLimitResetCredit> credits,
            Collection<Long> leadTimes, long nowMillis) {
        List<ResetCreditExpiryReminder> reminders = new ArrayList<>();
        if (credits == null || leadTimes == null) {
            return reminders;
        }
        Set<Long> uniqueLeadTimes = new HashSet<>(leadTimes);
        for (RateLimitResetCredit credit : credits) {
            if (credit == null || !credit.isAvailable()
                    || credit.expiresAtMillis <= nowMillis) {
                continue;
            }
            for (Long leadTime : uniqueLeadTimes) {
                if (leadTime == null || !isValidLeadTime(leadTime)) {
                    continue;
                }
                reminders.add(new ResetCreditExpiryReminder(credit.id,
                        credit.expiresAtMillis, leadTime));
            }
        }
        reminders.sort(CHRONOLOGICAL);
        return reminders;
    }
}
