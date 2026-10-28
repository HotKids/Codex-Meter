package me.pipi.codexmeter;

import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;
import dev.bennett.codexmeter.UsageCredits;
import dev.bennett.codexmeter.UsageSnapshot;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowBuild;

/** Reproducible, synthetic light/dark review sheets rendered by the production widget path. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "zh-rCN-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetVisualPolishPreviewTest {
    @Before
    public void usePixelHost() {
        ShadowBuild.setManufacturer("Google");
        ShadowBuild.setModel("Pixel 11 Pro");
    }

    @Test
    public void renderLightReviewSheet() throws Exception {
        render("light");
    }

    @Test
    @Config(qualifiers = "zh-rCN-night-xhdpi")
    public void renderDarkReviewSheet() throws Exception {
        render("dark");
    }

    private static void render(String theme) throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        float density = context.getResources().getDisplayMetrics().density;
        List<Bitmap> tiles = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (String style : new String[] {WidgetOptions.COLOR_NATIVE, WidgetOptions.COLOR_CLASSIC}) {
            for (int[] size : new int[][] {{140, 200}, {350, 170}}) {
                for (int used : new int[] {20, 60, 85}) {
                    UsageSnapshot base = UsageCardFixtures.snapshot("plus", used, used, false);
                    UsageSnapshot snapshot = new UsageSnapshot(base.planType, base.allowed,
                            base.limitReached, base.fiveHour, base.weekly, base.monthly,
                            base.additionalLimits, new UsageCredits(true, false, "53147.01"),
                            base.resetCreditsAvailable, base.fetchedAtMillis);
                    WidgetOptions options = WidgetOptions.defaults().withColorStyle(style)
                            .withVisibleMeters(size[0] < 250 ? "weekly,next_reset"
                                    : "five_hour,weekly,next_reset,usage_credits");
                    View widget = WidgetRenderer.build(context, 1, options,
                            UsageCardFixtures.state(snapshot, 2), size[0], size[1], null)
                            .apply(context, new FrameLayout(context));
                    int width = Math.round(size[0] * density);
                    int height = Math.round(size[1] * density);
                    widget.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                    widget.layout(0, 0, width, height);
                    Bitmap tile = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(tile);
                    canvas.drawColor(Color.rgb(70, 78, 88));
                    widget.draw(canvas);
                    tiles.add(tile);
                    labels.add(style + " " + size[0] + "x" + size[1] + " · "
                            + (100 - used) + "% left");
                }
            }
        }
        int cellWidth = 700;
        int cellHeight = 400;
        int gap = 24;
        int labelHeight = 32;
        Bitmap sheet = Bitmap.createBitmap(gap + 3 * (cellWidth + gap),
                gap + 4 * (cellHeight + labelHeight + gap), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawColor(Color.rgb(32, 34, 38));
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(21);
        for (int index = 0; index < tiles.size(); index++) {
            int x = gap + index % 3 * (cellWidth + gap);
            int y = gap + index / 3 * (cellHeight + labelHeight + gap);
            canvas.drawText(labels.get(index), x, y + 24, text);
            canvas.drawBitmap(tiles.get(index), x, y + labelHeight, null);
        }
        File folder = new File("build/reports/widget-visual-polish");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        File file = new File(folder, "cards-" + theme + "-sheet.png");
        try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        writeComparison(folder, theme, tiles, labels);
        for (Bitmap tile : tiles) tile.recycle();
        sheet.recycle();
    }

    private static void writeComparison(File folder, String theme, List<Bitmap> tiles,
            List<String> labels) throws Exception {
        File baseline = new File(folder, "before/cards-" + theme + "-sheet.png");
        if (!baseline.isFile()) return;
        Bitmap before = BitmapFactory.decodeFile(baseline.getPath());
        Bitmap comparison = Bitmap.createBitmap(1472, 1640, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(comparison);
        canvas.drawColor(Color.rgb(32, 34, 38));
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(26);
        canvas.drawText("Before", 24, 34, text);
        canvas.drawText("After", 748, 34, text);
        int[] selected = {4, 9, 10, 11};
        text.setTextSize(21);
        for (int row = 0; row < selected.length; row++) {
            int index = selected[row];
            int x = 24 + index % 3 * 724;
            int y = 24 + index / 3 * 456 + 32;
            int top = 56 + row * 396;
            canvas.drawText(labels.get(index), 24, top + 24, text);
            canvas.drawText(labels.get(index), 748, top + 24, text);
            canvas.drawBitmap(before, new Rect(x, y, x + 700, y + 340),
                    new Rect(24, top + 32, 724, top + 372), null);
            canvas.drawBitmap(tiles.get(index), 748, top + 32, null);
        }
        try (FileOutputStream output = new FileOutputStream(
                new File(folder, "comparison-" + theme + ".png"))) {
            assertTrue(comparison.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        before.recycle();
        comparison.recycle();
    }
}
