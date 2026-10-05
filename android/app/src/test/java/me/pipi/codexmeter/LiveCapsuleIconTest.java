package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "xxxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LiveCapsuleIconTest {
    @Test
    public void bitmapCapsulesKeepTheColoredMarkCenteredAtNinetyFivePercent() throws IOException {
        Application app = RuntimeEnvironment.getApplication();
        Drawable source = app.getDrawable(R.drawable.ic_codex_logo_color);
        Drawable capsule = app.getDrawable(R.drawable.ic_live_capsule);
        Drawable samsung = app.getDrawable(R.drawable.ic_live_capsule_samsung);
        // ColorOS forces VectorDrawable small icons to white before drawing its capsule.
        assertTrue(capsule instanceof BitmapDrawable);
        assertTrue(((LayerDrawable) samsung).getDrawable(0) instanceof BitmapDrawable);
        assertEquals(96, capsule.getIntrinsicWidth());
        assertEquals(96, capsule.getIntrinsicHeight());
        assertEquals(source.getIntrinsicWidth(), capsule.getIntrinsicWidth());
        assertEquals(source.getIntrinsicHeight(), capsule.getIntrinsicHeight());
        assertEquals(app.getDrawable(R.drawable.ic_codex_logo_on_accent).getIntrinsicWidth(),
                samsung.getIntrinsicWidth());
        assertEquals(app.getDrawable(R.drawable.ic_codex_logo_on_accent).getIntrinsicHeight(),
                samsung.getIntrinsicHeight());

        Bitmap expected = render(source, true);
        Bitmap actual = render(capsule, false);
        writePreview("expected-96", expected);
        writePreview("actual-96", actual);
        writePreview("source-96", render(source, false));
        for (int size : new int[]{24, 36, 48, 72, 96}) {
            writePreview("android-inset-" + size, render(source, true, size));
        }
        assertColoredAndTransparent(actual);
        assertClosePixels(expected, actual);
        assertClosePixels(expected, render(samsung, false));
    }

    private static Bitmap render(Drawable drawable, boolean inset) {
        return render(drawable, inset, 96);
    }

    private static Bitmap render(Drawable drawable, boolean inset, int size) {
        // Each reference size needs its own vector bitmap cache.
        drawable = drawable.getConstantState().newDrawable().mutate();
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (inset) {
            canvas.translate(size * 0.025f, size * 0.025f);
            canvas.scale(0.95f, 0.95f);
        }
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        return bitmap;
    }

    private static void writePreview(String name, Bitmap bitmap) throws IOException {
        File output = new File("build/reports/live-capsule-previews/" + name + ".png");
        output.getParentFile().mkdirs();
        try (FileOutputStream stream = new FileOutputStream(output)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
    }

    private static void assertColoredAndTransparent(Bitmap bitmap) {
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0));
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(32, 48));
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(60, 60));
        int top = bitmap.getPixel(48, 15);
        int bottom = bitmap.getPixel(48, 76);
        assertEquals(255, Color.alpha(top));
        assertEquals(255, Color.alpha(bottom));
        assertTrue(Color.blue(top) > Color.red(top));
        assertTrue(Color.red(top) > Color.red(bottom));
    }

    private static void assertClosePixels(Bitmap expected, Bitmap actual) {
        long difference = 0L;
        for (int y = 0; y < 96; y++) {
            for (int x = 0; x < 96; x++) {
                int left = expected.getPixel(x, y);
                int right = actual.getPixel(x, y);
                difference += Math.abs(Color.alpha(left) - Color.alpha(right));
                difference += Math.abs(premultiply(Color.red(left), left)
                        - premultiply(Color.red(right), right));
                difference += Math.abs(premultiply(Color.green(left), left)
                        - premultiply(Color.green(right), right));
                difference += Math.abs(premultiply(Color.blue(left), left)
                        - premultiply(Color.blue(right), right));
            }
        }
        // SVG rasterization and Android's vector renderer round antialiased edges differently.
        assertTrue("Capsule differs from the centered 95% source: " + difference,
                difference < 96L * 96L * 4L);
    }

    private static int premultiply(int component, int pixel) {
        return component * Color.alpha(pixel) / 255;
    }
}
