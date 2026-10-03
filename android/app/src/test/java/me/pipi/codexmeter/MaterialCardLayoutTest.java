package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsageWindow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Native previews and bounds checks use the real Clear RemoteViews with synthetic account data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "en-rUS-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MaterialCardLayoutTest {
    @Test
    public void resetInventoryAppearsUnderTheResetBar() {
        for (int[] size : new int[][] {{140, 200}, {350, 170}}) {
            View absent = build(size[0], size[1], null);
            View zero = build(size[0], size[1], ResetCreditsSnapshot.summary(0, UsageCardFixtures.NOW));
            View available = build(size[0], size[1], ResetCreditsSnapshot.summary(2, UsageCardFixtures.NOW));
            assertEquals(visibleText(absent), visibleText(zero));
            int resetSlot = size[0] < MaterialCardRenderer.MEDIUM_MIN_WIDTH_DP ? 2 : 1;
            assertEquals("Available resets stay below the reset bar",
                    View.VISIBLE, available.findViewById(id("md_detail_", resetSlot)).getVisibility());
            assertEquals(RuntimeEnvironment.getApplication().getString(
                    R.string.widget_material_reset_credits, 2),
                    ((TextView) available.findViewById(id("md_reset_", resetSlot))).getText().toString());
            assertTrue(available.findViewById(id("md_bar_", resetSlot)).getVisibility() == View.VISIBLE);
            assertTrue(((TextView) available.findViewById(id("md_value_", resetSlot)))
                    .getText().length() > 0);
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-xhdpi")
    public void chineseResetInventoryAppearsUnderTheResetBar() {
        resetInventoryAppearsUnderTheResetBar();
    }

    @Test
    @Config(qualifiers = "zh-rCN-xhdpi")
    public void narrowPanelsKeepNamesAndCompleteCountdownsInOneRow() {
        for (int[] size : new int[][] {{140, 200}, {170, 170}, {180, 200}}) {
            View applied = build(size[0], size[1], null);
            String date = UsageCardFormat.absoluteReset(UsageCardFixtures.NOW
                    + TimeUnit.DAYS.toMillis(6) + TimeUnit.HOURS.toMillis(22));
            assertEquals("重置时间：" + date,
                    ((TextView) applied.findViewById(R.id.md_reset_0)).getText().toString());
            assertEquals("6d 22h",
                    ((TextView) applied.findViewById(R.id.md_value_2)).getText().toString());
            assertPanelTextFits(applied, 0);
            assertPanelTextFits(applied, 2);
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-xhdpi")
    public void narrowCardsKeepReadableTextWithoutChangingRows() {
        View applied = build(140, 200, ResetCreditsSnapshot.summary(2, UsageCardFixtures.NOW));
        float density = RuntimeEnvironment.getApplication().getResources().getDisplayMetrics().density;
        assertTrue("The account plan remains readable at a narrow width",
                ((TextView) applied.findViewById(R.id.md_title)).getTextSize() >= 15f * density);
        for (int slot : new int[] {0, 2}) {
            assertTrue("Meter names do not shrink with the entire card",
                    ((TextView) applied.findViewById(id("md_name_", slot))).getTextSize() >= 13f * density);
            assertPanelTextFits(applied, slot);
        }
    }

    @Test
    public void widePanelsMatchNarrowSpacingAndKeepTheResetValueWhole() {
        View applied = build(350, 170, null);
        View narrow = build(140, 200, null);
        assertEquals(View.GONE, narrow.findViewById(R.id.md_updated).getVisibility());
        for (int width : new int[] {350, 430}) {
            View wide = width == 350 ? applied : build(width, 170, null);
            TextView updated = wide.findViewById(R.id.md_updated);
            TextView status = wide.findViewById(R.id.md_status);
            assertEquals(View.VISIBLE, updated.getVisibility());
            assertTrue("Wide-card refresh time is more readable than status text",
                    updated.getTextSize() > status.getTextSize());
            assertTrue("The complete refresh time fits beside the refresh button",
                    updated.getLayout() != null && updated.getLayout().getEllipsisCount(0) == 0);
        }
        int barGap = ((ViewGroup.MarginLayoutParams) narrow.findViewById(R.id.md_bar_0)
                .getLayoutParams()).topMargin;
        int detailGap = ((ViewGroup.MarginLayoutParams) narrow.findViewById(R.id.md_detail_0)
                .getLayoutParams()).topMargin;
        for (int slot : new int[] {0, 1}) {
            assertPanelTextFits(applied, slot);
            View panel = applied.findViewById(id("md_panel_", slot));
            TextView name = applied.findViewById(id("md_name_", slot));
            TextView value = applied.findViewById(id("md_value_", slot));
            View bar = applied.findViewById(id("md_bar_", slot));
            View detail = applied.findViewById(id("md_detail_", slot));
            assertEquals("Wide cards use the same title-to-bar gap as narrow cards", barGap,
                    ((ViewGroup.MarginLayoutParams) bar.getLayoutParams()).topMargin);
            assertEquals("Wide cards use the same bar-to-detail gap as narrow cards", detailGap,
                    ((ViewGroup.MarginLayoutParams) detail.getLayoutParams()).topMargin);
            assertEquals(name.getBaseline(), value.getBaseline(), 2);
            assertTrue("Progress follows the compact title row", topIn(bar, panel) >= topIn(value, panel) + value.getHeight());
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-xhdpi")
    public void writesProductionClearLayoutPreviews() throws Exception {
        PreviewSheet sheet = new PreviewSheet("material-clear-layout");
        Context context = RuntimeEnvironment.getApplication();
        float density = context.getResources().getDisplayMetrics().density;
        Bitmap[] tiles = new Bitmap[2];
        int[][] sizes = {{140, 200}, {350, 170}};
        for (int i = 0; i < sizes.length; i++) {
            int[] size = sizes[i];
            tiles[i] = sheet.add("clear " + size[0] + "x" + size[1], build(size[0], size[1], null),
                    Math.round(size[0] * density), Math.round(size[1] * density));
        }
        assertTrue(sheet.writeSheet().isFile());
        Bitmap pair = Bitmap.createBitmap(tiles[0].getWidth() + tiles[1].getWidth() + 72,
                Math.max(tiles[0].getHeight(), tiles[1].getHeight()) + 72, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(pair);
        canvas.drawColor(Color.rgb(32, 34, 38));
        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(Color.WHITE);
        label.setTextSize(18f);
        int x = 24;
        for (int i = 0; i < tiles.length; i++) {
            canvas.drawText((i == 0 ? "2x2 Clear" : "4x2 Clear") + " / " + sizes[i][0]
                    + "x" + sizes[i][1] + " dp", x, 30, label);
            canvas.drawBitmap(tiles[i], x, 48, null);
            x += tiles[i].getWidth() + 24;
        }
        File preview = new File(PreviewSheet.OUTPUT_DIR, "clear-request-preview.png");
        try (FileOutputStream output = new FileOutputStream(preview)) {
            assertTrue(pair.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
    }

    private static void assertPanelTextFits(View applied, int slot) {
        View panel = applied.findViewById(id("md_panel_", slot));
        TextView name = applied.findViewById(id("md_name_", slot));
        TextView value = applied.findViewById(id("md_value_", slot));
        assertEquals("Title and value share their baseline", name.getBaseline(), value.getBaseline(), 2);
        TextView reset = applied.findViewById(id("md_reset_", slot));
        if (((View) reset.getParent()).getVisibility() == View.VISIBLE) {
            assertTrue("The complete reset detail stays inside its panel",
                    topIn(reset, panel) + reset.getHeight() <= panel.getHeight());
            assertTrue("The reset detail is not truncated", reset.getLayout() != null
                    && reset.getLayout().getEllipsisCount(0) == 0);
        }
        for (TextView text : new TextView[] {name, value}) {
            assertTrue("Panel text stays inside its panel", topIn(text, panel) >= 0
                    && topIn(text, panel) + text.getHeight() <= panel.getHeight());
            assertTrue("The label and complete duration must fit without ellipsis: "
                            + text.getText() + ", width=" + text.getWidth() + ", size=" + text.getTextSize(),
                    text.getLayout() != null && text.getLayout().getLineCount() == 1
                            && text.getLayout().getEllipsisCount(0) == 0);
        }
    }

    private static View build(int width, int height, ResetCreditsSnapshot credits) {
        Context context = RuntimeEnvironment.getApplication();
        UsageWindow weekly = UsageCardFixtures.window(5, TimeUnit.DAYS.toSeconds(7),
                TimeUnit.DAYS.toMillis(6) + TimeUnit.HOURS.toMillis(22));
        UsageSnapshot snapshot = new UsageSnapshot("pro10x", true, false, null, weekly,
                UsageCardFixtures.NOW);
        UsageCardState state = new UsageCardState(true, snapshot, credits, "", UsageCardFixtures.NOW);
        WidgetOptions options = WidgetOptions.defaults().withVisibleMeters("weekly,next_reset");
        RemoteViews remote = WidgetRenderer.build(context, 1, options, state, width, height, null);
        View applied = remote.apply(context, new FrameLayout(context));
        float density = context.getResources().getDisplayMetrics().density;
        int widthPx = Math.round(width * density);
        int heightPx = Math.round(height * density);
        applied.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        applied.layout(0, 0, widthPx, heightPx);
        return applied;
    }

    private static int id(String prefix, int slot) {
        Context context = RuntimeEnvironment.getApplication();
        return context.getResources().getIdentifier(prefix + slot, "id", context.getPackageName());
    }

    private static int topIn(View child, View ancestor) {
        int top = child.getTop();
        for (View parent = (View) child.getParent(); parent != ancestor;
                parent = (View) parent.getParent()) {
            top += parent.getTop();
        }
        return top;
    }

    private static String visibleText(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            return "";
        }
        if (view instanceof TextView) {
            return ((TextView) view).getText().toString() + "\n";
        }
        StringBuilder text = new StringBuilder();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                text.append(visibleText(group.getChildAt(i)));
            }
        }
        return text.toString();
    }
}
