package dev.bennett.codexmeter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Cached reset-credit inventory: the account's available count plus the detailed credits. */
public final class ResetCreditsSnapshot {
    public final int availableCount;
    public final List<RateLimitResetCredit> credits;
    public final long fetchedAtMillis;

    public ResetCreditsSnapshot(int availableCount, List<RateLimitResetCredit> credits,
            long fetchedAtMillis) {
        this.availableCount = Math.max(0, availableCount);
        this.credits = Collections.unmodifiableList(
                credits == null ? new ArrayList<>() : new ArrayList<>(credits));
        this.fetchedAtMillis = Math.max(0L, fetchedAtMillis);
    }

    public static ResetCreditsSnapshot summary(int availableCount, long fetchedAtMillis) {
        return new ResetCreditsSnapshot(availableCount, Collections.emptyList(), fetchedAtMillis);
    }

    /**
     * Whether inventory is worth surfacing on the dashboard. Zero available resets always
     * hide the card, even when the Edit dashboard switch is on — matching usage-credit
     * auto-hide and data-gated 5-hour / weekly cards.
     */
    public boolean shouldDisplay() {
        return availableCount > 0;
    }

    /**
     * Same rule for a summary count from the usage endpoint when the detailed credits
     * snapshot is not cached yet. Negative / unknown counts never display.
     */
    public static boolean shouldDisplayCount(int availableCount) {
        return availableCount > 0;
    }

    public RateLimitResetCredit nextExpiringAvailable(long nowMillis) {
        List<RateLimitResetCredit> available = availableCreditsByExpiry(nowMillis);
        return available.isEmpty() ? null : available.get(0);
    }

    /** Unexpired available credits, soonest expiry first; credits without expiry sort last. */
    public List<RateLimitResetCredit> availableCreditsByExpiry(long nowMillis) {
        List<RateLimitResetCredit> available = new ArrayList<>();
        for (RateLimitResetCredit credit : credits) {
            if (credit == null || !credit.isAvailable()) {
                continue;
            }
            long expiry = credit.expiresAtMillis;
            if (expiry > 0L && expiry <= nowMillis) {
                continue;
            }
            available.add(credit);
        }
        available.sort(Comparator
                .comparingLong((RateLimitResetCredit credit) ->
                        credit.expiresAtMillis > 0L ? credit.expiresAtMillis : Long.MAX_VALUE)
                .thenComparing(credit -> credit.id));
        return Collections.unmodifiableList(available);
    }

    public String preferredCreditId(long nowMillis) {
        RateLimitResetCredit credit = nextExpiringAvailable(nowMillis);
        return credit == null ? "" : credit.id;
    }

    public long nextExpiryMillis(long nowMillis) {
        RateLimitResetCredit credit = nextExpiringAvailable(nowMillis);
        return credit == null ? 0L : credit.expiresAtMillis;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("available_count", availableCount);
        json.put("fetched_at", fetchedAtMillis);
        JSONArray entries = new JSONArray();
        for (RateLimitResetCredit credit : credits) {
            if (credit != null) {
                entries.put(credit.toJson());
            }
        }
        json.put("credits", entries);
        return json;
    }

    public static ResetCreditsSnapshot fromJson(JSONObject json) {
        if (json == null) {
            return null;
        }
        List<RateLimitResetCredit> credits = new ArrayList<>();
        JSONArray entries = json.optJSONArray("credits");
        if (entries != null) {
            for (int index = 0; index < entries.length(); index++) {
                RateLimitResetCredit credit =
                        RateLimitResetCredit.fromJson(entries.optJSONObject(index));
                if (credit != null) {
                    credits.add(credit);
                }
            }
        }
        return new ResetCreditsSnapshot(json.optInt("available_count", 0), credits,
                json.optLong("fetched_at", 0L));
    }
}
