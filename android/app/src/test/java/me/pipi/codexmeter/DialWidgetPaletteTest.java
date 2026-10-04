package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.WidgetMeters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Parcel;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DialWidgetPaletteTest {
    @Test
    public void pickerShowsArcsWithoutAProviderUpdate() {
        Context host = themedContext(Configuration.UI_MODE_NIGHT_NO);
        RemoteViews preview = new RemoteViews(host.getPackageName(), R.layout.widget_rings);
        View applied = apply(host, preview);
        for (int id : new int[] {R.id.primary_samsung_progress, R.id.secondary_samsung_progress}) {
            Bitmap arc = draw(applied.findViewById(id));
            assertTrue("The static picker needs a complete track",
                    hasOpaquePixel(arc, host.getColor(R.color.widget_material_track)));
            assertTrue("The static picker needs sample remaining progress",
                    hasOpaquePixel(arc, host.getColor(R.color.widget_material_fill)));
            arc.recycle();
        }
    }

    @Test
    public void twoDialArcsAdvanceWithRemainingUsageInsteadOfStayingAtZero() {
        Context host = themedContext(Configuration.UI_MODE_NIGHT_NO);
        View nearlyFull = apply(host, build(host, 100,
                UsageCardFixtures.snapshot("plus", 3, 3, false), false));
        View nearlyEmpty = apply(host, build(host, 100,
                UsageCardFixtures.snapshot("plus", 97, 97, false), false));
        for (int id : new int[]{R.id.primary_samsung_progress, R.id.secondary_samsung_progress}) {
            int fullPixels = opaquePixels(draw(nearlyFull.findViewById(id)),
                    host.getColor(R.color.widget_material_fill));
            int emptyPixels = opaquePixels(draw(nearlyEmpty.findViewById(id)),
                    host.getColor(R.color.widget_material_fill));
            assertTrue("97% remaining must fill more of the arc than 3% remaining",
                    fullPixels > emptyPixels * 4);
        }
        assertEquals("97%", ((TextView) nearlyFull.findViewById(
                R.id.primary_samsung_value)).getText().toString());
    }

    @Test
    public void twoDialArcsHaveFullHalfAndEmptyRemainingBoundaries() {
        Context host = themedContext(Configuration.UI_MODE_NIGHT_NO);
        int[] filled = new int[3];
        int[] used = {0, 50, 100};
        for (int index = 0; index < used.length; index++) {
            View applied = apply(host, build(host, 100,
                    UsageCardFixtures.snapshot("plus", used[index], used[index], false), false));
            filled[index] = opaquePixels(draw(applied.findViewById(R.id.primary_samsung_progress)),
                    host.getColor(R.color.widget_material_fill));
        }
        assertTrue("A full allowance must fill more than half of the arc", filled[0] > filled[1]);
        assertTrue("Half an allowance must have a visible fill", filled[1] > 0);
        assertEquals("No remaining allowance must have no fill", 0, filled[2]);
    }

    @Test
    public void serializedTwoDialsUseTheHostNightPaletteWithoutRebuilding() {
        Context light = themedContext(Configuration.UI_MODE_NIGHT_NO);
        Context night = themedContext(Configuration.UI_MODE_NIGHT_YES);
        RemoteViews remote = build(light, 100,
                UsageCardFixtures.snapshot("plus", 50, 50, false), false);
        for (Context host : new Context[]{light, night}) {
            View applied = apply(host, remote);
            Bitmap arc = draw(applied.findViewById(R.id.primary_samsung_progress));
            assertTrue("The track must resolve the host palette",
                    hasOpaquePixel(arc, host.getColor(R.color.widget_material_track)));
            assertTrue("The fill must resolve the host palette",
                    hasOpaquePixel(arc, host.getColor(R.color.widget_material_fill)));
            arc.recycle();
            assertEquals(host.getColor(R.color.widget_material_text),
                    ((TextView) applied.findViewById(R.id.primary_samsung_value)).getCurrentTextColor());
        }
    }

    @Test
    public void sameRemoteViewsUseTheHostNightPaletteWithoutRebuilding() {
        Context light = themedContext(Configuration.UI_MODE_NIGHT_NO);
        Context night = themedContext(Configuration.UI_MODE_NIGHT_YES);
        RemoteViews remote = build(light, 100);
        assertForegroundPalette(light, apply(light, remote));
        View applied = apply(night, remote);

        for (int role : new int[] {R.color.widget_material_track, R.color.widget_material_fill,
                R.color.widget_material_text}) {
            assertNotEquals("The fixture must distinguish day and night roles",
                    light.getColor(role), night.getColor(role));
        }
        assertForegroundPalette(night, applied);
        Bitmap widget = draw(applied);
        assertEquals(night.getColor(R.color.widget_material_surface), widget.getPixel(130, 4));
        widget.recycle();
    }

    @Test
    @Config(sdk = 30)
    public void preAndroid12DialsUseTheSameResolvedNightRolesAsTheirSurface() {
        Context night = themedContext(Configuration.UI_MODE_NIGHT_YES);
        View applied = apply(night, build(night, 100));
        assertForegroundPalette(night, applied);
        Bitmap widget = draw(applied);
        assertEquals(night.getColor(R.color.widget_material_surface), widget.getPixel(130, 4));
        widget.recycle();
    }

    @Test
    public void theRetainedRemoteViewsKeepTheirBackgroundOffInNightMode() {
        Context light = themedContext(Configuration.UI_MODE_NIGHT_NO);
        Context night = themedContext(Configuration.UI_MODE_NIGHT_YES);
        View applied = apply(night, build(light, 0));
        assertEquals(View.GONE, applied.findViewById(R.id.dial_surface).getVisibility());
        Bitmap widget = draw(applied);
        assertEquals(0, Color.alpha(widget.getPixel(130, 4)));
        widget.recycle();
    }

    private static RemoteViews build(Context context, int opacity) {
        return build(context, opacity, UsageCardFixtures.plus(), true);
    }

    private static RemoteViews build(Context context, int opacity, UsageSnapshot snapshot,
            boolean four) {
        WidgetOptions options = new WidgetOptions("auto", "system", "app", opacity,
                "hidden", "remaining");
        RemoteViews remote = DialWidgetRenderer.build(context, 1, options,
                WidgetMeters.defaultVisible(), UsageCardFixtures.state(snapshot, 2));
        Parcel parcel = Parcel.obtain();
        try {
            remote.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return RemoteViews.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    private static Context themedContext(int nightMode) {
        Context app = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(app.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | nightMode;
        return app.createConfigurationContext(configuration);
    }

    private static View apply(Context context, RemoteViews remote) {
        View view = remote.apply(context, new FrameLayout(context));
        view.measure(View.MeasureSpec.makeMeasureSpec(260, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(90, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 260, 90);
        return view;
    }

    private static Bitmap draw(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
                Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static void assertForegroundPalette(Context host, View applied) {
        Bitmap foreground = draw(applied.findViewById(R.id.primary_samsung_progress));
        for (int role : new int[] {R.color.widget_material_track, R.color.widget_material_fill}) {
            assertTrue("The dial must use the host's colour for " + role,
                    hasOpaquePixel(foreground, host.getColor(role)));
        }
        foreground.recycle();
        assertEquals(host.getColor(R.color.widget_material_text),
                ((TextView) applied.findViewById(R.id.primary_samsung_value)).getCurrentTextColor());
    }

    private static boolean hasOpaquePixel(Bitmap bitmap, int color) {
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (bitmap.getPixel(x, y) == color) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int opaquePixels(Bitmap bitmap, int color) {
        int count = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (bitmap.getPixel(x, y) == color) count++;
            }
        }
        bitmap.recycle();
        return count;
    }
}
