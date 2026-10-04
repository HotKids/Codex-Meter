package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Rect;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import dev.bennett.codexmeter.UsageCredits;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.WidgetMeters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Balances keep their full amount when a narrow card moves it below the label. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35}, application = Application.class, qualifiers = "zh-rCN-notnight-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MaterialCardBalanceLayoutTest {
    private static final String AMOUNT = "62,437.29";

    @Test
    public void narrowBalancesUseTwoCompleteLinesInEitherPanelAndBackgroundLayout() {
        for (int opacity : new int[] {100, 0}) {
            for (int slot : new int[] {0, 2}) {
                FrameLayout host = apply(keysWithBalance(slot), opacity, 140, 200);
                assertStackedBalance(host, slot);
            }
        }
    }

    @Test
    public void wideThreeItemCardsKeepTheBalanceOnOneLineInTheirLastRow() {
        for (int opacity : new int[] {100, 0}) {
            FrameLayout host = apply(List.of(WidgetMeters.WEEKLY, WidgetMeters.NEXT_RESET,
                    WidgetOptions.USAGE_CREDITS), opacity, 350, 170);
            assertEquals(View.GONE, host.findViewById(R.id.md_panel_3).getVisibility());
            assertInlineBalance(host, 2);
        }
    }

    @Test
    public void cachedCardRestoresBothBalanceArrangementsWhenItsWidthChanges() {
        List<String> keys = List.of(WidgetOptions.USAGE_CREDITS, WidgetMeters.WEEKLY);
        for (int opacity : new int[] {100, 0}) {
            FrameLayout host = apply(keys, opacity, 140, 200);
            assertStackedBalance(host, 0);
            reapply(host, keys, opacity, 350, 170);
            assertInlineBalance(host, 0);
            reapply(host, keys, opacity, 140, 200);
            assertStackedBalance(host, 0);
        }
    }

    @Test
    public void changingABalanceToWeeklyRestoresDetailSizeWeightColorAndProgress() {
        for (int opacity : new int[] {100, 0}) {
            for (int slot : new int[] {0, 2}) {
                List<String> balanceKeys = keysWithBalance(slot);
                List<String> weeklyKeys = slot == 0
                        ? List.of(WidgetMeters.WEEKLY, WidgetMeters.NEXT_RESET)
                        : List.of(WidgetMeters.NEXT_RESET, WidgetMeters.WEEKLY);
                FrameLayout host = apply(balanceKeys, opacity, 140, 200);
                assertStackedBalance(host, slot);
                reapply(host, weeklyKeys, opacity, 140, 200);
                FrameLayout freshWeekly = apply(weeklyKeys, opacity, 140, 200);
                TextView detail = text(host, "md_reset_", slot);
                TextView expected = text(freshWeekly, "md_reset_", slot);
                assertEquals(View.VISIBLE, view(host, "md_detail_", slot).getVisibility());
                assertEquals(View.VISIBLE, view(host, "md_bar_", slot).getVisibility());
                assertEquals(View.VISIBLE, view(host, "md_value_", slot).getVisibility());
                assertEquals(expected.getText().toString(), detail.getText().toString());
                assertTrue(detail.getText().toString().startsWith("重置时间："));
                assertEquals(expected.getTextSize(), detail.getTextSize(), 0f);
                assertTrue(detail.getTextSize() < text(host, "md_value_", slot).getTextSize());
                assertFalse("A reset detail must not retain the balance's bold span", bold(detail));
                assertEquals(app().getColor(R.color.widget_material_secondary),
                        detail.getCurrentTextColor());
                assertEquals(expected.getCurrentTextColor(), detail.getCurrentTextColor());
                assertCompleteLine(detail);
                assertInsidePanel(host, slot, detail);
                reapply(host, balanceKeys, opacity, 140, 200);
                assertStackedBalance(host, slot);
            }
        }
    }

    private static void assertStackedBalance(FrameLayout host, int slot) {
        TextView label = text(host, "md_name_", slot);
        TextView amount = text(host, "md_reset_", slot);
        assertEquals("剩余额度", label.getText().toString());
        assertEquals(AMOUNT, amount.getText().toString());
        assertEquals(View.GONE, view(host, "md_value_", slot).getVisibility());
        assertEquals(View.GONE, view(host, "md_bar_", slot).getVisibility());
        assertEquals(View.VISIBLE, view(host, "md_detail_", slot).getVisibility());
        assertEquals(text(host, "md_value_", slot).getTextSize(), amount.getTextSize(), 0f);
        assertTrue("The second-line balance keeps the value's bold weight", bold(amount));
        assertEquals(app().getColor(R.color.widget_material_text), amount.getCurrentTextColor());
        assertCompleteLine(label);
        assertCompleteLine(amount);
        assertInsidePanel(host, slot, label);
        assertInsidePanel(host, slot, amount);
        ViewGroup content = (ViewGroup) view(host, "md_panel_content_", slot);
        assertTrue("The full amount is below the unchanged label",
                bounds(content, label).bottom <= bounds(content, amount).top);
    }

    private static void assertInlineBalance(FrameLayout host, int slot) {
        TextView label = text(host, "md_name_", slot);
        TextView amount = text(host, "md_value_", slot);
        assertEquals("剩余额度", label.getText().toString());
        assertEquals(AMOUNT, amount.getText().toString());
        assertEquals(View.VISIBLE, amount.getVisibility());
        assertEquals(View.GONE, view(host, "md_detail_", slot).getVisibility());
        assertEquals(View.GONE, view(host, "md_bar_", slot).getVisibility());
        assertTrue(bold(amount));
        assertCompleteLine(label);
        assertCompleteLine(amount);
        assertInsidePanel(host, slot, label);
        assertInsidePanel(host, slot, amount);
        assertEquals("The inline label and amount share a native baseline",
                label.getBaseline(), amount.getBaseline(), 2);
    }

    private static void assertCompleteLine(TextView text) {
        assertEquals(View.VISIBLE, text.getVisibility());
        assertNotNull(text.getLayout());
        assertEquals(1, text.getLayout().getLineCount());
        assertEquals("The full line must remain visible: " + text.getText(),
                0, text.getLayout().getEllipsisCount(0));
        assertTrue("The native line fits its backing TextView",
                text.getLayout().getHeight() <= text.getHeight()
                        - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom());
        assertTrue("All rendered glyphs fit the available width: " + text.getText(),
                renderedPaint(text).measureText(text.getText().toString())
                        <= text.getWidth() - text.getCompoundPaddingLeft()
                        - text.getCompoundPaddingRight() + 1f);
    }

    private static void assertInsidePanel(FrameLayout host, int slot, TextView text) {
        ViewGroup content = (ViewGroup) view(host, "md_panel_content_", slot);
        Rect bounds = bounds(content, text);
        assertTrue("The text stays below the panel's top padding: " + bounds,
                bounds.top >= content.getPaddingTop());
        assertTrue("The text stays above the panel's bottom padding: " + bounds,
                bounds.bottom <= content.getHeight() - content.getPaddingBottom());
    }

    private static Rect bounds(ViewGroup parent, View child) {
        Rect bounds = new Rect(0, 0, child.getWidth(), child.getHeight());
        parent.offsetDescendantRectToMyCoords(child, bounds);
        return bounds;
    }

    private static boolean bold(TextView text) {
        TextPaint paint = renderedPaint(text);
        return paint.isFakeBoldText() || paint.getTypeface() != null && paint.getTypeface().isBold();
    }

    private static TextPaint renderedPaint(TextView text) {
        TextPaint paint = new TextPaint(text.getPaint());
        if (text.getText() instanceof Spanned) {
            Spanned value = (Spanned) text.getText();
            for (MetricAffectingSpan span : value.getSpans(0, value.length(), MetricAffectingSpan.class)) {
                span.updateMeasureState(paint);
            }
        }
        return paint;
    }

    private static List<String> keysWithBalance(int slot) {
        return slot == 0 ? List.of(WidgetOptions.USAGE_CREDITS, WidgetMeters.WEEKLY)
                : List.of(WidgetMeters.WEEKLY, WidgetOptions.USAGE_CREDITS);
    }

    private static FrameLayout apply(List<String> keys, int opacity, int width, int height) {
        FrameLayout host = new FrameLayout(app());
        View widget = remote(keys, opacity, width, height).apply(app(), host);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) widget.getLayoutParams();
        params.gravity = Gravity.CENTER;
        host.addView(widget, params);
        measure(host, width, height);
        return host;
    }

    private static void reapply(FrameLayout host, List<String> keys, int opacity, int width, int height) {
        remote(keys, opacity, width, height).reapply(app(), host.getChildAt(0));
        measure(host, width, height);
    }

    private static RemoteViews remote(List<String> keys, int opacity, int width, int height) {
        UsageSnapshot base = UsageCardFixtures.snapshot("pro200", 36, 58, false);
        UsageSnapshot balance = new UsageSnapshot(base.planType, base.allowed, base.limitReached,
                base.fiveHour, base.weekly, base.monthly, base.additionalLimits,
                new UsageCredits(true, false, "62437.28578"), base.resetCreditsAvailable,
                base.fetchedAtMillis);
        WidgetOptions options = new WidgetOptions("auto", "system", "app", opacity,
                "hidden", "remaining").withVisibleMeters(WidgetMeters.serialize(keys));
        return MaterialCardRenderer.build(app(), 42, options, keys,
                UsageCardFixtures.state(balance, 2), width, height);
    }

    private static void measure(FrameLayout host, int width, int height) {
        float density = app().getResources().getDisplayMetrics().density;
        int widthPx = Math.round(width * density);
        int heightPx = Math.round(height * density);
        host.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        host.layout(0, 0, widthPx, heightPx);
    }

    private static View view(FrameLayout host, String prefix, int slot) {
        return host.findViewById(app().getResources().getIdentifier(prefix + slot, "id",
                app().getPackageName()));
    }

    private static TextView text(FrameLayout host, String prefix, int slot) {
        return (TextView) view(host, prefix, slot);
    }

    private static Application app() {
        return RuntimeEnvironment.getApplication();
    }
}
