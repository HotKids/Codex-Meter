package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Renders the actual adaptive launcher resource for visual review. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LauncherIconPreviewTest {
    @Test
    public void adaptiveBackgroundUsesGradientAcrossWholeLayer() {
        Drawable icon = RuntimeEnvironment.getApplication().getDrawable(R.mipmap.ic_launcher);
        assertTrue(icon instanceof AdaptiveIconDrawable);
        Drawable background = ((AdaptiveIconDrawable) icon).getBackground();
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        background.setBounds(0, 0, 64, 64);
        background.draw(new Canvas(bitmap));
        for (int[] point : new int[][]{{0, 0}, {63, 0}, {0, 63}, {63, 63}, {32, 32}}) {
            int color = bitmap.getPixel(point[0], point[1]);
            assertEquals(255, Color.alpha(color));
            assertNotEquals(Color.WHITE, color);
        }
        assertNotEquals(bitmap.getPixel(32, 0), bitmap.getPixel(32, 63));
    }

    @Test
    public void rendersAdaptiveLauncherIcon() throws Exception {
        Drawable icon = RuntimeEnvironment.getApplication().getDrawable(R.mipmap.ic_launcher);
        Bitmap bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888);
        icon.setBounds(0, 0, 1024, 1024);
        icon.draw(new Canvas(bitmap));
        File output = new File("build/reports/launcher-icon-previews/CodexMeter.png");
        assertTrue(output.getParentFile().isDirectory() || output.getParentFile().mkdirs());
        try (FileOutputStream stream = new FileOutputStream(output)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
    }
}
