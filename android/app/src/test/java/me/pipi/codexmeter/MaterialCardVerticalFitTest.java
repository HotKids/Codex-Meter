package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import dev.bennett.codexmeter.UsageCredits;
import dev.bennett.codexmeter.UsageSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowBuild;

/** Typography follows card height, and compact panels retain complete native text rows. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "zh-rCN-notnight-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MaterialCardVerticalFitTest {
    @Test
    public void matchingHeightsKeepEveryFieldSizeAcrossWidthsAndLongAmounts() {
        Application app = RuntimeEnvironment.getApplication();
        UsageCardState state = stateWithLongBalance();
        for (List<String> keys : List.of(List.of("weekly", "next_reset"),
                List.of("usage_credits", "weekly"))) {
            View reference = card(app, keys, state, 170, 170);
            float[] expected = fieldSizes(app, reference, 170);
            float density = app.getResources().getDisplayMetrics().density;
            float[] baselineDp = {15.5f, 15f, 15.5f, 9.5f, 15f, 15.5f, 9.5f};
            if (WidgetOptions.USAGE_CREDITS.equals(keys.get(0))) {
                baselineDp[3] = 15.5f;
            }
            for (int index = 0; index < expected.length; index++) {
                assertEquals("Field " + index + " retains the established 2x2 baseline",
                        baselineDp[index] * 0.9f * density, expected[index], 0.001f);
            }
            for (int width : new int[] {110, 170, 350, 430}) {
                View actual = card(app, keys, state, width, 170);
                float[] sizes = fieldSizes(app, actual, width);
                for (int index = 0; index < expected.length; index++) {
                    assertEquals("Field " + index + " keeps its 2x2 size at " + width
                            + "x170dp for " + keys, expected[index], sizes[index], 0f);
                }
                if ("usage_credits".equals(keys.get(0))) {
                    assertTrue("The long balance must exercise the actual numeric field",
                            ((TextView) actual.findViewById(R.id.md_value_0)).getText().length()
                                    > 16);
                }
            }
        }
    }

    @Test
    public void changingHeightChangesEachFieldSizeForTheSameContentAndWidth() {
        Application app = RuntimeEnvironment.getApplication();
        UsageCardState state = stateWithLongBalance();
        List<String> keys = List.of("weekly", "next_reset");
        float[] shortSizes = fieldSizes(app, card(app, keys, state, 170, 150), 170);
        float[] tallSizes = fieldSizes(app, card(app, keys, state, 170, 220), 170);
        for (int index = 0; index < shortSizes.length; index++) {
            assertTrue("Field " + index + " grows when only the available height increases",
                    tallSizes[index] > shortSizes[index]);
        }
    }

    @Test
    public void narrowCardShowsTheCompleteAbsoluteResetTimeAtTheUnifiedDetailSize() {
        Application app = RuntimeEnvironment.getApplication();
        UsageCardState state = UsageCardFixtures.state(
                UsageCardFixtures.snapshot("pro200", 36, 58, false), 2);
        String resetTime = "重置时间：10/10 14:26";
        RemoteViews remote = MaterialCardRenderer.build(app, 42, WidgetOptions.defaults(),
                List.of("weekly", "next_reset"), state, 140, 200);
        remote.setTextViewText(R.id.md_reset_0, resetTime);
        TextView detail = apply(app, remote, 140, 200).findViewById(R.id.md_reset_0);
        assertEquals(resetTime, detail.getText().toString());
        assertTrue(detail.getLayout() != null);
        assertEquals(1, detail.getLayout().getLineCount());
        int available = detail.getWidth() - detail.getCompoundPaddingLeft()
                - detail.getCompoundPaddingRight();
        float needed = detail.getPaint().measureText(resetTime);
        assertTrue("140x200dp reset detail: available=" + available + "px, required="
                        + needed + "px, actual type size=" + detail.getTextSize()
                        + "px, ellipsis=" + detail.getLayout().getEllipsisCount(0),
                detail.getLayout().getEllipsisCount(0) == 0 && needed <= available);
    }

    @Test
    public void colorOsCardsKeepChinesePanelContentInsideVerticalPadding() {
        Application app = RuntimeEnvironment.getApplication();
        ShadowBuild.setManufacturer("OPPO");
        ResolveInfo launcher = new ResolveInfo();
        launcher.isDefault = true;
        launcher.activityInfo = new ActivityInfo();
        launcher.activityInfo.packageName = "com.android.launcher";
        launcher.activityInfo.name = "com.android.launcher.Launcher";
        launcher.activityInfo.enabled = true;
        launcher.activityInfo.exported = true;
        shadowOf(app.getPackageManager()).setResolveInfosForIntent(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                Collections.singletonList(launcher));
        Settings.Secure.putString(app.getContentResolver(), "layout_icon_size", "52");
        assertTrue(ColorOsWidgetAppearance.isStockLauncher(app));
        AppWidgetProviderInfo provider = new AppWidgetProviderInfo();
        provider.provider = new ComponentName(app, CodexUsageWidget.class);
        shadowOf(AppWidgetManager.getInstance(app)).addBoundWidget(42, provider);
        WidgetOptions options = WidgetOptions.defaults().withVisibleMeters("weekly,next_reset");
        UsageCardState state = UsageCardFixtures.state(
                UsageCardFixtures.snapshot("pro200", 36, 58, false), 2);
        List<String> overflow = new ArrayList<>();
        List<String> geometry = new ArrayList<>();
        for (int[] size : new int[][] {{140, 140}, {140, 150}, {170, 170}, {330, 170}}) {
            RemoteViews remote = WidgetRenderer.build(app, 42, options, state,
                    size[0], size[1], null);
            View widget = apply(app, remote, size[0], size[1]);
            for (int slot : size[0] < MaterialCardRenderer.MEDIUM_MIN_WIDTH_DP
                    ? new int[] {0, 2} : new int[] {0, 1}) {
                ViewGroup content = widget.findViewById(id(app, "md_panel_content_", slot));
                int top = content.getPaddingTop();
                int bottom = content.getHeight() - content.getPaddingBottom();
                TextView name = widget.findViewById(id(app, "md_name_", slot));
                TextView value = widget.findViewById(id(app, "md_value_", slot));
                TextView detail = widget.findViewById(id(app, "md_reset_", slot));
                String label = size[0] + "x" + size[1] + "dp slot=" + slot;
                StringBuilder measured = new StringBuilder(label).append(" inner=")
                        .append(top).append("..").append(bottom).append("px")
                        .append(" nameLine=").append(name.getLineHeight())
                        .append(" valueLine=").append(value.getLineHeight())
                        .append(" detailLine=").append(detail.getLineHeight()).append("px");
                for (int index = 0; index < content.getChildCount(); index++) {
                    View child = content.getChildAt(index);
                    if (child.getVisibility() == View.GONE) {
                        continue;
                    }
                    measured.append(" child").append(index).append('=')
                            .append(child.getTop()).append("..").append(child.getBottom());
                    if (child.getTop() < top || child.getBottom() > bottom) {
                        overflow.add(label + " child=" + index + " bounds=" + child.getTop()
                                + ".." + child.getBottom() + "px outside " + top + ".."
                                + bottom + "px");
                    }
                }
                for (TextView text : new TextView[] {name, value, detail}) {
                    int available = text.getHeight() - text.getCompoundPaddingTop()
                            - text.getCompoundPaddingBottom();
                    int needed = text.getLayout().getHeight();
                    measured.append(" text[").append(text.getText()).append("]=")
                            .append(needed).append('/').append(available).append("px");
                    if (needed > available) {
                        overflow.add(label + " text=" + text.getText() + " native line needs "
                                + needed + "px but measured text area is " + available + "px");
                    }
                }
                geometry.add(measured.toString());
            }
        }
        assertTrue("Native panel geometry: " + geometry + "; overflow: " + overflow,
                overflow.isEmpty());
    }

    private static UsageCardState stateWithLongBalance() {
        UsageSnapshot snapshot = UsageCardFixtures.snapshot("pro200", 36, 58, false);
        UsageSnapshot balance = new UsageSnapshot(snapshot.planType, snapshot.allowed,
                snapshot.limitReached, snapshot.fiveHour, snapshot.weekly, snapshot.monthly,
                snapshot.additionalLimits, new UsageCredits(true, false, "123456789012345.6789"),
                snapshot.resetCreditsAvailable, snapshot.fetchedAtMillis);
        return UsageCardFixtures.state(balance, 2);
    }

    private static View card(Application app, List<String> keys, UsageCardState state,
            int width, int height) {
        return apply(app, MaterialCardRenderer.build(app, 42, WidgetOptions.defaults(),
                keys, state, width, height), width, height);
    }

    private static float[] fieldSizes(Application app, View widget, int width) {
        float[] sizes = new float[7];
        sizes[0] = ((TextView) widget.findViewById(R.id.md_title)).getTextSize();
        int index = 1;
        for (int slot : width < MaterialCardRenderer.MEDIUM_MIN_WIDTH_DP
                ? new int[] {0, 2} : new int[] {0, 1}) {
            for (String field : new String[] {"md_name_", "md_value_", "md_reset_"}) {
                sizes[index++] = ((TextView) widget.findViewById(id(app, field, slot))).getTextSize();
            }
        }
        return sizes;
    }

    private static View apply(Application app, RemoteViews remote, int width, int height) {
        FrameLayout host = new FrameLayout(app);
        View widget = remote.apply(app, host);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) widget.getLayoutParams();
        params.gravity = Gravity.CENTER;
        host.addView(widget, params);
        float density = app.getResources().getDisplayMetrics().density;
        int widthPx = Math.round(width * density);
        int heightPx = Math.round(height * density);
        host.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        host.layout(0, 0, widthPx, heightPx);
        return widget;
    }

    private static int id(Application app, String prefix, int slot) {
        return app.getResources().getIdentifier(prefix + slot, "id", app.getPackageName());
    }
}
