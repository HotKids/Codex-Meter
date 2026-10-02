package dev.bennett.codexmeter;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Parses the reset-credit list endpoint into a {@link ResetCreditsSnapshot}. */
public final class ResetCreditsParser {
    private ResetCreditsParser() {
    }

    public static ResetCreditsSnapshot parse(String json, long fetchedAtMillis)
            throws JSONException {
        JSONObject root = new JSONObject(json == null ? "{}" : json);
        List<RateLimitResetCredit> credits = new ArrayList<>();
        JSONArray entries = root.optJSONArray("credits");
        if (entries != null) {
            for (int index = 0; index < entries.length(); index++) {
                RateLimitResetCredit credit =
                        RateLimitResetCredit.fromApiJson(entries.optJSONObject(index));
                if (credit != null) {
                    credits.add(credit);
                }
            }
        }
        // Without a summary count, count the listed credits that are still available.
        int availableCount = root.optInt("available_count", countAvailable(credits));
        return new ResetCreditsSnapshot(availableCount, credits, fetchedAtMillis);
    }

    private static int countAvailable(List<RateLimitResetCredit> credits) {
        int available = 0;
        for (RateLimitResetCredit credit : credits) {
            if (credit != null && credit.isAvailable()) {
                available++;
            }
        }
        return available;
    }
}
