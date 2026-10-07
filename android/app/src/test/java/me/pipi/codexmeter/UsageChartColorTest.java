package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import androidx.appcompat.view.ContextThemeWrapper;
import dev.bennett.codexmeter.UsageHistory;
import dev.bennett.codexmeter.UsagePace;
import dev.bennett.codexmeter.UsageSample;
import dev.bennett.codexmeter.UsageWindow;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "zh-rCN-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UsageChartColorTest {
    private static final int WIDTH = 480;
    private static final int HEIGHT = 220;

    @Test
    public void lightChartRestoresDynamicActualPaceProjectionAndTranslucentBudget() {
        assertChartRoles(false);
    }

    @Test
    @Config(qualifiers = "zh-rCN-night-mdpi")
    public void darkChartRestoresDynamicActualPaceProjectionAndTranslucentBudget() {
        assertChartRoles(true);
    }

    private static void assertChartRoles(boolean dark) {
        Context context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(),
                R.style.AppTheme_MaterialYou);
        assertEquals(dark, Ui.isDark(context));
        int primary = context.getColor(dark
                ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600);
        int reference = Color.argb(dark ? 115 : 95, 128, 128, 128);

        for (boolean accelerated : new boolean[] {false, true}) {
            int projection = accelerated ? 0xFFE65B17 : Ui.desaturatedAccent(context, dark);
            int used = accelerated ? 80 : 45;
            UsageWindow window = UsageCardFixtures.window(used, TimeUnit.HOURS.toSeconds(5),
                    TimeUnit.HOURS.toMillis(2));
            long resetAt = window.effectiveResetAtMillis(UsageCardFixtures.NOW);
            UsageHistory history = new UsageHistory(UsageHistory.FIVE_HOUR, Arrays.asList(
                    new UsageSample(UsageCardFixtures.NOW - TimeUnit.MINUTES.toMillis(150),
                            accelerated ? 10 : 5, resetAt, window.windowSeconds),
                    new UsageSample(UsageCardFixtures.NOW - TimeUnit.MINUTES.toMillis(90),
                            accelerated ? 40 : 20, resetAt, window.windowSeconds),
                    new UsageSample(UsageCardFixtures.NOW, used, resetAt, window.windowSeconds)));
            UsagePace.Assessment pace = UsagePace.assess(window, history, UsageCardFixtures.NOW,
                    UsageCardFixtures.NOW, UsagePace.BALANCED);
            assertTrue("The fixture must produce a visible prediction", pace.available);
            assertEquals("The fixture must exercise the intended pace state",
                    accelerated, pace.accelerated);

            UsageBurnChartView chart = new UsageBurnChartView(context);
            chart.setData("Five-hour limit", window, history, UsageCardFixtures.NOW, pace);
            chart.layout(0, 0, WIDTH, HEIGHT);
            Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
            chart.draw(new Canvas(bitmap));
            assertVisiblePlotColor(bitmap, primary, "actual usage", accelerated);
            assertVisiblePlotColor(bitmap, projection, "prediction", accelerated);
            assertVisiblePlotColor(bitmap, reference, "budget reference", accelerated);
            bitmap.recycle();
        }
    }

    private static void assertVisiblePlotColor(Bitmap bitmap, int expected, String role,
            boolean accelerated) {
        int count = 0;
        // Exclude the header, frame and axis labels so only the plotted lines prove each role.
        for (int y = 36; y < HEIGHT - 26; y++) {
            for (int x = 18; x < WIDTH - 18; x++) {
                int pixel = bitmap.getPixel(x, y);
                // Native Canvas unpremultiplies translucent gray with channel rounding.
                boolean translucentMatch = Color.alpha(expected) < 255
                        && Math.abs(Color.alpha(pixel) - Color.alpha(expected)) <= 1
                        && Math.abs(Color.red(pixel) - Color.red(expected)) <= 2
                        && Math.abs(Color.green(pixel) - Color.green(expected)) <= 2
                        && Math.abs(Color.blue(pixel) - Color.blue(expected)) <= 2;
                if (pixel == expected || translucentMatch) {
                    count++;
                }
            }
        }
        assertTrue(role + " must have rendered pixels, accelerated=" + accelerated
                + ", pixels=" + count, count >= 8);
    }
}
