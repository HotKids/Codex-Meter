package dev.bennett.codexmeter;

import static org.junit.Assert.*;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

/** Exercise the response shapes read by AI-Usage through our real widget catalog. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = QuotaCardsTest.TestApp.class)
public class WidgetWindowParsingTest {
    private JSONObject window(long seconds, int used) throws Exception {
        return new JSONObject().put("limit_window_seconds", seconds).put("used_percent", used)
                .put("reset_at", 1_800_000_000L);
    }

    private JSONArray spark(String rateKey, String primaryKey, String secondaryKey) throws Exception {
        return new JSONArray().put(new JSONObject().put("limit_name", "Codex Spark")
                .put("metered_feature", "spark").put(rateKey, new JSONObject()
                    .put(primaryKey, window(18000, 10)).put(secondaryKey, window(604800, 20))));
    }

    @Test public void nestedAdditionalLimitsReachTheWidgetCatalog() throws Exception {
        JSONObject payload = new JSONObject().put("plan_type", "pro").put("rate_limit",
                new JSONObject().put("primary_window", window(604800, 67))
                    .put("additional_rate_limits", spark("rate_limit", "primary_window", "secondary_window")));
        UsageSnapshot parsed = UsageParser.parse(payload.toString(), 1234);
        assertEquals(1, parsed.additionalLimits.size());
        List<String> available = QuotaCardOptions.available(parsed);
        assertEquals(6, available.size());
        assertEquals(List.of("five_hour", "weekly", "next_reset", "reset_credits"), available.subList(0, 4));
        assertEquals(90, QuotaCardOptions.window(available.get(4), parsed).remainingPercent());
        assertEquals(80, QuotaCardOptions.window(available.get(5), parsed).remainingPercent());
        assertEquals(available, QuotaCardOptions.available(UsageSnapshot.fromJson(parsed.toJson())));
    }

    @Test public void camelCaseRateLimitAndWindowContainersAreRecognized() throws Exception {
        JSONObject payload = new JSONObject().put("rateLimit",
                new JSONObject().put("primaryWindow", window(604800, 67)))
                .put("additional_rate_limits", spark("rateLimit", "primaryWindow", "secondaryWindow"));
        UsageSnapshot parsed = UsageParser.parse(payload.toString(), 1234);
        assertEquals(6, QuotaCardOptions.available(parsed).size());
        assertNotNull(parsed.weekly);
        assertEquals(1, parsed.additionalLimits.size());
    }

    @Test public void directWindowsFillMissingPrimaryWindowsWithoutDuplicates() throws Exception {
        JSONObject payload = new JSONObject().put("rate_limit",
                new JSONObject().put("primary_window", window(604800, 67)))
                .put("five_hour", window(18000, 20)).put("weekly", window(604800, 75));
        UsageSnapshot parsed = UsageParser.parse(payload.toString(), 1234);
        assertEquals(List.of("five_hour", "weekly", "next_reset", "reset_credits"),
                QuotaCardOptions.available(parsed));
        assertEquals(67, parsed.weekly.usedPercent);
        assertEquals(20, parsed.fiveHour.usedPercent);
    }

    @Test public void weeklyOnlyResponseOffersBuiltinsWithoutInventingFiveHourData() throws Exception {
        UsageSnapshot parsed = UsageParser.parse(new JSONObject().put("rate_limit",
                new JSONObject().put("primary_window", window(604800, 67))).toString(), 1234);
        assertEquals(List.of("five_hour", "weekly", "next_reset", "reset_credits"),
                QuotaCardOptions.available(parsed));
        assertNull(parsed.fiveHour);
    }

    @Test public void parsedAdditionalWindowsAreSelectableInTheRealEditor() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        UsageSnapshot parsed = UsageParser.parse(new JSONObject().put("rate_limit", new JSONObject()
                .put("primary_window", window(604800, 67))
                .put("additional_rate_limits", spark("rate_limit", "primary_window", "secondary_window")))
                .toString(), 1234);
        AppPreferences.saveSnapshot(context, parsed);
        AppPreferences.saveWidgetOptions(context, 42, WidgetOptions.defaults().withVisibleMeters("weekly"));
        Intent intent = new Intent(context, WidgetConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 42);
        try (var controller = Robolectric.buildActivity(WidgetConfigActivity.class, intent).setup()) {
            WidgetConfigActivity activity = controller.get();
            RecyclerView list = ReflectionHelpers.getField(activity, "metersList");
            list.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY));
            list.layout(0, 0, 400, 500);
            assertEquals(6, list.getAdapter().getItemCount());
            for (int index = 4; index <= 5; index++) {
                RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(index);
                assertNotNull(holder);
                TextView title = ReflectionHelpers.getField(holder, "title");
                assertTrue(title.getText().toString().contains("Codex Spark"));
                CompoundButton toggle = ReflectionHelpers.getField(holder, "toggle");
                toggle.setChecked(true);
            }
            ReflectionHelpers.callInstanceMethod(activity, "save");
            List<String> available = QuotaCardOptions.available(parsed);
            assertEquals(List.of("weekly", available.get(4), available.get(5)), QuotaCardOptions.resolve(
                    AppPreferences.loadWidgetOptions(context, 42).visibleMeters));
        }
    }
}
