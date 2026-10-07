package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.widget.LinearLayout;
import androidx.appcompat.view.ContextThemeWrapper;
import dev.bennett.codexmeter.UsagePace;
import dev.bennett.codexmeter.UsageWindow;
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
public class UsageWaveColorTest {
    @Test
    public void lightFillUsesDynamicAccentUnlessPaceIsAccelerated() throws Exception {
        assertFillRoles();
        writePreview("light");
    }

    @Test
    @Config(qualifiers = "zh-rCN-night-mdpi")
    public void darkFillUsesDynamicAccentUnlessPaceIsAccelerated() throws Exception {
        assertFillRoles();
        writePreview("dark");
    }

    @Test
    public void fullAndEmptyAllowancesKeepTheirFillGeometry() {
        Context context = themedContext();
        boolean dark = Ui.isDark(context);
        assertEquals(Ui.cardColor(context, dark), render(100, false).getPixel(1, 4));
        assertEquals(Ui.desaturatedAccent(context, dark), render(0, false).getPixel(359, 50));
    }

    @Test
    public void lightEstimateSharesCardTextAndWarningTrack() throws Exception {
        assertPaceRole(0xFFF8DFD5);
    }

    @Test
    @Config(qualifiers = "zh-rCN-night-mdpi")
    public void darkEstimateSharesCardTextAndWarningTrack() throws Exception {
        assertPaceRole(0xFF492719);
    }

    @Test
    public void availableEstimateAndPaceWarningRemainInTheCardDescription() {
        Context context = themedContext();
        UsagePace.Assessment pace = assessment(60, true);
        assertTrue(pace.available && pace.accelerated);
        UsageWaveView wave = wave(context, 60, pace);
        String description = wave.getContentDescription().toString();
        assertTrue(description.contains(UsageFormat.estimatedRemainingSpoken(context, pace)));
        assertTrue(description.contains(context.getString(R.string.dashboard_wave_accelerated)));
    }

    private static void assertFillRoles() {
        Context context = themedContext();
        int dynamicFill = Ui.desaturatedAccent(context, Ui.isDark(context));
        for (int used : new int[] {1, 59, 60, 84, 85, 99}) {
            for (boolean warningsEnabled : new boolean[] {false, true}) {
                boolean accelerated = assessment(used, warningsEnabled).accelerated;
                int expected = accelerated ? 0xFFE65B17 : dynamicFill;
                assertEquals("used=" + used + ", warningsEnabled=" + warningsEnabled,
                        expected, render(used, warningsEnabled).getPixel(1, 4));
            }
        }
    }

    private static Bitmap render(int used, boolean warningsEnabled) {
        Context context = themedContext();
        UsageWaveView wave = wave(context, used, assessment(used, warningsEnabled));
        return render(wave);
    }

    private static Bitmap render(UsageWaveView wave) {
        wave.layout(0, 0, 360, 103);
        Bitmap bitmap = Bitmap.createBitmap(360, 103, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Ui.cardColor(wave.getContext(), Ui.isDark(wave.getContext())));
        wave.draw(canvas);
        return bitmap;
    }

    private static void assertPaceRole(int warningTrack) throws Exception {
        Context context = themedContext();
        boolean dark = Ui.isDark(context);
        for (int used : new int[] {59, 60, 85}) {
            for (boolean enabled : new boolean[] {false, true}) {
                UsagePace.Assessment pace = assessment(used, enabled);
                assertEquals(enabled, pace.accelerated);
                UsageWaveView wave = wave(context, used, pace);
                Bitmap bitmap = render(wave);
                int track = enabled ? warningTrack : Ui.cardColor(context, dark);
                int foreground = enabled ? (dark ? Color.WHITE : Color.BLACK)
                        : Ui.mainText(context, dark);
                assertEquals("Used region follows the original pace state", track,
                        bitmap.getPixel(359, 50));
                for (String field : new String[] {"titlePaint", "resetPaint", "pacePaint",
                        "percentPaint"}) {
                    assertEquals("All quota labels share the original foreground", foreground,
                            paintColor(wave, field));
                }
                bitmap.recycle();
            }
        }
    }

    private static int paintColor(UsageWaveView wave, String name) throws Exception {
        java.lang.reflect.Field field = UsageWaveView.class.getDeclaredField(name);
        field.setAccessible(true);
        return ((Paint) field.get(wave)).getColor();
    }

    private static UsageWaveView wave(Context context, int used, UsagePace.Assessment pace) {
        UsageWaveView wave = new UsageWaveView(context);
        wave.setUsage("每周限额", "6d 后重置", "6d", pace, 100 - used,
                R.drawable.ic_ms_calendar_month, false);
        return wave;
    }

    private static UsagePace.Assessment assessment(int used, boolean warningsEnabled) {
        UsageWindow window = UsageCardFixtures.window(used, TimeUnit.DAYS.toSeconds(7),
                TimeUnit.DAYS.toMillis(6));
        return UsagePace.assess(window, UsageCardFixtures.NOW, UsageCardFixtures.NOW,
                warningsEnabled ? UsagePace.SENSITIVE : UsagePace.OFF);
    }

    private static Context themedContext() {
        Context app = RuntimeEnvironment.getApplication();
        return new ContextThemeWrapper(app, R.style.AppTheme_MaterialYou);
    }

    private static void writePreview(String theme) throws Exception {
        Context context = themedContext();
        PreviewSheet sheet = new PreviewSheet("home-usage-" + theme);
        for (int used : new int[] {20, 60, 85}) {
            LinearLayout card = Ui.card(context, Ui.isDark(context));
            card.setPadding(0, 0, 0, 0);
            card.addView(wave(context, used, assessment(used, false)),
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 103));
            sheet.add("home-" + theme + "-remaining-" + (100 - used), card, 360, 103);
        }
        sheet.writeSheet();
    }
}
