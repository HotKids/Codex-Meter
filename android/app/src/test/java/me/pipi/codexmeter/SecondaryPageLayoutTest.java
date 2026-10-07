package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;
import dev.bennett.codexmeter.PlanPricing;
import dev.bennett.codexmeter.UsageHistory;
import dev.bennett.codexmeter.UsageSample;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsageWindow;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "zh-rCN")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SecondaryPageLayoutTest {
    @Test
    public void accountIdentityRemainsReadableAtNarrowWidthAndLargeFont() {
        Context app = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(app.getResources().getConfiguration());
        configuration.fontScale = 2f;
        Context context = new ContextThemeWrapper(app.createConfigurationContext(configuration),
                R.style.AppTheme);
        View card = LayoutInflater.from(context)
                .inflate(R.layout.preference_account_card, null, false);
        TextView title = card.findViewById(R.id.settings_account_title);
        TextView summary = card.findViewById(R.id.settings_account_summary);
        TextView plan = card.findViewById(R.id.settings_account_plan);
        title.setText(R.string.settings_account_title_signed_in);
        summary.setText("long.account.identity@example.test");
        plan.setText(R.string.settings_account_sign_in_again);
        measure(card, Ui.dp(context, 300));

        assertTextFits(title);
        assertTextFits(summary);
        assertTextFits(plan);
        assertTrue("The account title must retain a readable column",
                title.getLineCount() <= 2);
        assertTrue("The account identity must not collapse into a letter-by-letter column",
                summary.getLineCount() <= 8);
        View identity = (View) title.getParent();
        assertEquals("The account action must remain beside the identity",
                identity.getParent(), plan.getParent());
        assertTrue("The account action must remain on the right without covering the identity",
                plan.getLeft() >= identity.getRight());
        View header = (View) plan.getParent();
        assertTrue("The account action must stay inside the header",
                plan.getRight() <= header.getWidth() - header.getPaddingRight());
        assertTrue("The reauthentication action must retain a full touch target",
                plan.getHeight() >= Ui.dp(context, 48));
    }

    @Test
    public void selectedHistoryWindowExposesSelectionAndMovesOnClick() throws Exception {
        try (ActivityController<UsageHistoryActivity> controller = Robolectric
                .buildActivity(UsageHistoryActivity.class).create()) {
            UsageHistoryActivity activity = controller.get();
            UsageSnapshot snapshot = UsageCardFixtures.plus();
            UsageWindow window = snapshot.fiveHour;
            long currentReset = window.effectiveResetAtMillis(snapshot.fetchedAtMillis);
            long pastReset = currentReset - TimeUnit.HOURS.toMillis(5);
            UsageHistory history = new UsageHistory(UsageHistory.FIVE_HOUR, Arrays.asList(
                    new UsageSample(pastReset - TimeUnit.HOURS.toMillis(1), 60, pastReset,
                            TimeUnit.HOURS.toSeconds(5)),
                    new UsageSample(snapshot.fetchedAtMillis, 36, currentReset,
                            TimeUnit.HOURS.toSeconds(5))));
            Method build = UsageHistoryActivity.class.getDeclaredMethod("buildChartCard",
                    String.class, UsageWindow.class, UsageSnapshot.class, UsageHistory.class,
                    PlanPricing.class);
            build.setAccessible(true);
            LinearLayout card = (LinearLayout) build.invoke(activity, "5-hour", window,
                    snapshot, history, null);
            List<View> rows = new ArrayList<>();
            collectWindowRows(card, rows);
            assertEquals(2, rows.size());
            assertTrue("The current chart window must expose its selected state",
                    rows.get(0).isSelected());
            assertSelectionIndicator(rows.get(0), true);
            assertSelectionIndicator(rows.get(1), false);
            clickWindowRow(rows.get(1));
            assertTrue("Choosing another window must move the selected state",
                    rows.get(1).isSelected());
            assertEquals(1, rows.stream().filter(View::isSelected).count());
            assertSelectionIndicator(rows.get(0), false);
            assertSelectionIndicator(rows.get(1), true);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void hiddenDashboardSectionKeepsReadableLabelsAndNamedControls() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        AppPreferences.setDashboardOrder(app, Collections.emptyList());
        AppPreferences.setShowDashboardFiveHour(app, false);
        try (ActivityController<DashboardReorderActivity> controller = Robolectric
                .buildActivity(DashboardReorderActivity.class).create()) {
            DashboardReorderActivity activity = controller.get();
            Field field = DashboardReorderActivity.class.getDeclaredField("recycler");
            field.setAccessible(true);
            RecyclerView list = (RecyclerView) field.get(activity);
            RecyclerView.Adapter<RecyclerView.ViewHolder> adapter =
                    (RecyclerView.Adapter<RecyclerView.ViewHolder>) list.getAdapter();
            RecyclerView.ViewHolder holder = adapter.createViewHolder(list, 0);
            adapter.bindViewHolder(holder, 0);
            LinearLayout row = (LinearLayout) holder.itemView;
            LinearLayout labels = (LinearLayout) row.getChildAt(0);
            TextView title = (TextView) labels.getChildAt(0);
            TextView summary = (TextView) labels.getChildAt(1);
            SwitchCompat toggle = (SwitchCompat) row.getChildAt(1);
            ImageView handle = (ImageView) row.getChildAt(2);
            assertEquals(1f, title.getAlpha(), 0f);
            assertEquals(1f, summary.getAlpha(), 0f);
            assertFalse(toggle.isChecked());
            assertEquals(activity.getString(R.string.dashboard_edit_show_section,
                    title.getText()), toggle.getContentDescription());
            assertEquals(activity.getString(R.string.dashboard_edit_reorder_section,
                    title.getText()), handle.getContentDescription());
            toggle.setChecked(true);
            assertTrue(AppPreferences.showDashboardFiveHour(app));
        }
    }

    private static void assertSelectionIndicator(View row, boolean selected) {
        ImageView indicator = row.findViewById(dev.oneuiproject.oneui.design.R.id.end_view);
        assertNotNull("Window selection must have a non-color indicator", indicator);
        assertNotNull(indicator.getDrawable());
        assertEquals(selected ? View.VISIBLE : View.INVISIBLE, indicator.getVisibility());
    }

    private static void clickWindowRow(View row) {
        View container = row.findViewById(dev.oneuiproject.oneui.design.R.id.cardview_container);
        (container == null ? row : container).performClick();
    }

    private static void collectWindowRows(View view, List<View> rows) {
        if (view.isClickable() && view.getContentDescription() != null) {
            rows.add(view);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                collectWindowRows(group.getChildAt(index), rows);
            }
        }
    }

    private static void measure(View view, int width) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, width, view.getMeasuredHeight());
    }

    private static void assertTextFits(TextView text) {
        assertNotNull(text.getLayout());
        for (int line = 0; line < text.getLayout().getLineCount(); line++) {
            assertEquals("Account identity must be readable without ellipsis", 0,
                    text.getLayout().getEllipsisCount(line));
            assertTrue("Account text must fit its column without covering the action",
                    text.getLayout().getLineWidth(line) <= text.getWidth()
                            - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight() + 1);
        }
        assertTrue("Account identity must fit vertically after font scaling",
                text.getLayout().getHeight() <= text.getHeight() - text.getCompoundPaddingTop()
                        - text.getCompoundPaddingBottom());
    }
}
