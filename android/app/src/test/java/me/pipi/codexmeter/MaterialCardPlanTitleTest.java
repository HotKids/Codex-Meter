package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.WidgetMeters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class MaterialCardPlanTitleTest {
    @Test
    public void titleUsesTheUsageResponsePlanForBothBackgroundLayouts() throws Exception {
        for (int opacity : new int[] {100, 0}) {
            assertTitle("Free", state("free"), opacity);
            assertTitle("Plus", state("plus"), opacity);
            assertTitle("Team", state("team"), opacity);
        }
    }

    @Test
    public void explicitProTiersUsePricesAndKeepExistingAliases() throws Exception {
        for (String[] tier : new String[][] {
                {"Pro 100", "prolite", "pro5x", "pro100"},
                {"Pro 200", "pro", "pro10x", "pro200"},
                {"Pro 500", "pro_25x", "pro500"}}) {
            for (int index = 1; index < tier.length; index++) {
                assertTitle(tier[0], state(tier[index]), 100);
            }
        }
    }

    @Test
    public void noAccountOrPlanDoesNotClaimATier() throws Exception {
        UsageCardState signedOut = new UsageCardState(false, snapshot("team"), null, "",
                UsageCardFixtures.NOW);
        UsageCardState waiting = new UsageCardState(true, null, null, "", UsageCardFixtures.NOW);
        for (UsageCardState state : new UsageCardState[] {signedOut, waiting, state(""),
                state("unrecognized-plan"), state("pro20x"), state("pro_20x"),
                state("Pro20x"), state("Pro 20x")}) {
            assertTitle("—", state, 100);
        }
    }

    @Test
    public void oneRowPlacementsKeepTheirHeaderlessDials() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        for (int columns : new int[] {2, 3}) {
            Bundle host = new Bundle();
            host.putInt("semAppWidgetRowSpan", 1);
            host.putInt("semAppWidgetColumnSpan", columns);
            RemoteViews remote = WidgetRenderer.build(context, 1, WidgetOptions.defaults(),
                    state("team"), 260f, 170f, host);
            assertNull(remote.apply(context, new FrameLayout(context)).findViewById(R.id.md_title));
        }
    }

    private static void assertTitle(String expected, UsageCardState state, int opacity) {
        Context context = RuntimeEnvironment.getApplication();
        WidgetOptions options = new WidgetOptions("auto", "system", "app", opacity,
                "hidden", "remaining");
        RemoteViews remote = MaterialCardRenderer.build(context, 1, options,
                WidgetMeters.defaultVisible(), state, 170f, 190f);
        View applied = remote.apply(context, new FrameLayout(context));
        TextView title = applied.findViewById(R.id.md_title);
        assertEquals(expected, title.getText().toString());
    }

    private static UsageCardState state(String plan) throws Exception {
        return UsageCardFixtures.state(snapshot(plan), 0);
    }

    private static UsageSnapshot snapshot(String plan) throws Exception {
        JSONObject response = new JSONObject();
        response.put("plan_type", plan);
        response.put("rate_limit", new JSONObject().put("primary_window", new JSONObject()
                .put("used_percent", 25).put("limit_window_seconds", 18_000)
                .put("reset_at", UsageCardFixtures.NOW / 1_000L + 3_600)));
        return UsageParser.parse(response.toString(), UsageCardFixtures.NOW);
    }
}
