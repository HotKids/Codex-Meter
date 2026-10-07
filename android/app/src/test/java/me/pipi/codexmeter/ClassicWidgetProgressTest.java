package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Parcel;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RemoteViews;
import java.util.HashSet;
import java.util.Set;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.WidgetMeters;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ClassicWidgetProgressTest {
    @Test
    public void cardsAndDialsUseUsedPercentForBothWarningBoundaries() {
        Context context = RuntimeEnvironment.getApplication();
        for (int used : new int[] {59, 60, 84, 85, 99}) {
            for (boolean dial : new boolean[] {false, true}) {
                View widget = render(context, WidgetOptions.COLOR_CLASSIC, used, dial,
                        WidgetMeters.WEEKLY);
                ImageView fill = widget.findViewById(dial ? R.id.primary_samsung_fill : R.id.md_fill_0);
                assertStatusColor("used=" + used + ", dial=" + dial, fill,
                        used >= 85 ? 0xFFED5F64 : used >= 60 ? 0xFFFF9F0A : 0);
            }
        }
    }

    @Test
    public void resetCountdownKeepsTheNormalColorEvenWhenItsWindowIsNearlyExhausted() {
        Context context = RuntimeEnvironment.getApplication();
        for (boolean dial : new boolean[] {false, true}) {
            View widget = render(context, WidgetOptions.COLOR_CLASSIC, 99, dial,
                    WidgetMeters.NEXT_RESET);
            assertStatusColor("reset", widget.findViewById(dial
                    ? R.id.primary_samsung_fill : R.id.md_fill_0), 0);
        }
    }

    @Test
    public void nativeModeUsesSystemSeverityAfterReapplyingOverClassicMode() {
        Context context = RuntimeEnvironment.getApplication();
        for (boolean dial : new boolean[] {false, true}) {
            int width = dial ? 180 : 170;
            int height = dial ? 90 : 218;
            RemoteViews classic = remote(context, WidgetOptions.COLOR_CLASSIC, 85, dial,
                    WidgetMeters.WEEKLY);
            View widget = classic.apply(context, new FrameLayout(context));
            remote(context, WidgetOptions.COLOR_NATIVE, 85, dial, WidgetMeters.WEEKLY)
                    .reapply(context, widget);
            layout(widget, width, height);
            ImageView fill = widget.findViewById(dial ? R.id.primary_samsung_fill : R.id.md_fill_0);
            assertStatusColor("native", fill, context.getColor(android.R.color.system_accent3_600));
        }
    }

    @Test
    public void materialCardsAndDialsUseAllThreeWallpaperPalettes() throws Exception {
        assertMaterialSeverity(false);
        writeMaterialPreview("light");
    }

    @Test
    @Config(qualifiers = "night-mdpi")
    public void darkMaterialCardsAndDialsUseTheLightWallpaperTones() throws Exception {
        assertMaterialSeverity(true);
        writeMaterialPreview("dark");
    }

    @Test
    public void materialResetCountdownKeepsPrimaryWhenUsageIsCritical() {
        assertMaterialResetColor(false);
    }

    @Test
    @Config(qualifiers = "night-mdpi")
    public void darkMaterialResetCountdownKeepsPrimaryWhenUsageIsCritical() {
        assertMaterialResetColor(true);
    }

    @Test
    public void serializedMaterialSeverityResolvesTheApplyingHostsNightPalette() {
        Context app = RuntimeEnvironment.getApplication();
        Configuration night = new Configuration(app.getResources().getConfiguration());
        night.uiMode = (night.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | Configuration.UI_MODE_NIGHT_YES;
        Context host = app.createConfigurationContext(night);
        int[] used = {20, 60, 85};
        int[] colors = {android.R.color.system_accent1_200,
                android.R.color.system_accent2_200, android.R.color.system_accent3_200};
        for (boolean dial : new boolean[] {false, true}) {
            for (int tier = 0; tier < used.length; tier++) {
                RemoteViews remote = remote(app, WidgetOptions.COLOR_NATIVE, used[tier], dial,
                        WidgetMeters.WEEKLY);
                View applied = remote.apply(host, new FrameLayout(host));
                layout(applied, dial ? 180 : 170, dial ? 90 : 218);
                assertStatusColor("Host resolves tier " + tier, applied.findViewById(dial
                        ? R.id.primary_samsung_fill : R.id.md_fill_0), host.getColor(colors[tier]));
            }
        }
    }

    private static void assertMaterialSeverity(boolean dark) {
        Context context = RuntimeEnvironment.getApplication();
        int[] colors = dark ? new int[] {android.R.color.system_accent1_200,
                android.R.color.system_accent2_200, android.R.color.system_accent3_200}
                : new int[] {android.R.color.system_accent1_600,
                        android.R.color.system_accent2_600, android.R.color.system_accent3_600};
        for (boolean dial : new boolean[] {false, true}) {
            for (String key : new String[] {WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY}) {
                View retained = render(context, WidgetOptions.COLOR_NATIVE, 1, dial, key);
                for (int used : new int[] {59, 60, 84, 85, 99, 60, 59}) {
                    remote(context, WidgetOptions.COLOR_NATIVE, used, dial, key)
                            .reapply(context, retained);
                    layout(retained, dial ? 180 : 170, dial ? 90 : 218);
                    int expected = context.getColor(colors[used >= 85 ? 2 : used >= 60 ? 1 : 0]);
                    int fillId = dial ? R.id.primary_samsung_fill : R.id.md_fill_0;
                    String label = "used=" + used + ", dial=" + dial + ", key=" + key;
                    assertStatusColor(label + " reapplied", retained.findViewById(fillId), expected);
                    assertStatusColor(label + " fresh", render(context, WidgetOptions.COLOR_NATIVE,
                            used, dial, key).findViewById(fillId), expected);
                }
            }
        }
    }

    private static void assertMaterialResetColor(boolean dark) {
        Context context = RuntimeEnvironment.getApplication();
        for (boolean dial : new boolean[] {false, true}) {
            View widget = render(context, WidgetOptions.COLOR_NATIVE, 99, dial,
                    WidgetMeters.NEXT_RESET);
            assertStatusColor("Material reset", widget.findViewById(dial
                    ? R.id.primary_samsung_fill : R.id.md_fill_0), context.getColor(dark
                    ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600));
        }
    }

    private static void writeMaterialPreview(String theme) throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        PreviewSheet sheet = new PreviewSheet("material-severity-" + theme);
        for (int used : new int[] {20, 60, 85}) {
            sheet.add("Material remaining " + (100 - used), render(context,
                    WidgetOptions.COLOR_NATIVE, used, false, WidgetMeters.WEEKLY), 170, 218);
        }
        assertTrue(sheet.writeSheet().isFile());
    }

    @Test
    public void missingUsageDoesNotShowAnExhaustedAllowance() {
        Context context = RuntimeEnvironment.getApplication();
        WidgetOptions options = WidgetOptions.defaults().withColorStyle(WidgetOptions.COLOR_CLASSIC);
        RemoteViews remote = MaterialCardRenderer.build(context, 1, options,
                UsageCardFixtures.keys(WidgetMeters.WEEKLY), UsageCardFixtures.signedOut(), 170, 218);
        View widget = remote.apply(context, new FrameLayout(context));
        layout(widget, 170, 218);
        ImageView fill = widget.findViewById(R.id.md_fill_0);
        assertEquals(0, fill.getDrawable().getLevel());
    }

    @Test
    public void cardHeaderAndFillKeepTheirColorsThroughRepeatedStyleChanges() {
        Context context = RuntimeEnvironment.getApplication();
        View retained = remote(context, WidgetOptions.COLOR_NATIVE, 85, false,
                WidgetMeters.WEEKLY).apply(context, new FrameLayout(context));
        for (String style : new String[] {WidgetOptions.COLOR_CLASSIC,
                WidgetOptions.COLOR_NATIVE, WidgetOptions.COLOR_CLASSIC}) {
            RemoteViews next = remote(context, style, 85, false, WidgetMeters.WEEKLY);
            next.reapply(context, retained);
            layout(retained, 170, 218);
            View fresh = next.apply(context, new FrameLayout(context));
            layout(fresh, 170, 218);
            for (int id : new int[] {R.id.md_logo, R.id.md_fill_0}) {
                Bitmap actual = draw(retained.findViewById(id));
                Bitmap expected = draw(fresh.findViewById(id));
                assertTrue("A reused host must match a fresh " + style + " image",
                        expected.sameAs(actual));
                if (id == R.id.md_logo && WidgetOptions.COLOR_CLASSIC.equals(style)) {
                    Set<Integer> colors = new HashSet<>();
                    for (int y = 0; y < actual.getHeight(); y++) {
                        for (int x = 0; x < actual.getWidth(); x++) {
                            int color = actual.getPixel(x, y);
                            if (android.graphics.Color.alpha(color) == 255) colors.add(color);
                        }
                    }
                    assertTrue("The classic logo must keep its gradient, not a theme tint",
                            colors.size() > 10);
                }
                expected.recycle();
                actual.recycle();
            }
        }
    }

    private static View render(Context context, String style, int used, boolean dial, String key) {
        View widget = remote(context, style, used, dial, key)
                .apply(context, new FrameLayout(context));
        layout(widget, dial ? 180 : 170, dial ? 90 : 218);
        return widget;
    }

    private static RemoteViews remote(Context context, String style, int used,
            boolean dial, String key) {
        WidgetOptions options = WidgetOptions.defaults().withColorStyle(style);
        UsageSnapshot snapshot = UsageCardFixtures.snapshot("plus", used, used, false);
        UsageCardState state = UsageCardFixtures.state(snapshot, 2);
        RemoteViews remote = dial ? DialWidgetRenderer.build(context, 1, options,
                UsageCardFixtures.keys(key), state, 180, 90)
                : MaterialCardRenderer.build(context, 1, options,
                        UsageCardFixtures.keys(key), state, 170, 218);
        Parcel parcel = Parcel.obtain();
        try {
            remote.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return RemoteViews.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    private static void layout(View widget, int width, int height) {
        widget.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        widget.layout(0, 0, width, height);
    }

    private static void assertStatusColor(String message, ImageView fill, int expected) {
        Bitmap image = draw(fill);
        boolean found = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getPixel(x, y);
                if (expected != 0 ? color == expected :
                        android.graphics.Color.alpha(color) == 255
                        && android.graphics.Color.red(color) >= 9
                        && android.graphics.Color.red(color) <= 111
                        && android.graphics.Color.green(color) >= 194
                        && android.graphics.Color.green(color) <= 202
                        && android.graphics.Color.blue(color) >= 75
                        && android.graphics.Color.blue(color) <= 101) {
                    found = true;
                }
            }
        }
        image.recycle();
        assertTrue(message, found);
    }

    private static Bitmap draw(View view) {
        Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        return image;
    }
}
