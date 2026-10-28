package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Parcel;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowBuild;

/** Clear-card visual changes must not alter the accepted one-row dials. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "zh-rCN-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetCardVisualStyleTest {
    @Before
    public void usePixelHost() {
        ShadowBuild.setManufacturer("Google");
        ShadowBuild.setModel("Pixel 11 Pro");
    }

    @Test
    public void meterNamesAreLighterThanTheNumericValues() {
        Context context = RuntimeEnvironment.getApplication();
        View card = apply(context, remote(context, WidgetOptions.COLOR_NATIVE, 20, false),
                170, 218);
        TextView name = card.findViewById(R.id.md_name_0);
        TextView value = card.findViewById(R.id.md_value_0);
        assertTrue("The label must use medium weight", name.getTypeface().getWeight() <= 500);
        assertTrue("The value retains its bold emphasis", value.getTypeface().getWeight() >= 700);
    }

    @Test
    public void legacyOpaqueAndTransparentLayoutsCannotRetainTheOldBoldLabel() {
        Context context = RuntimeEnvironment.getApplication();
        for (int opacity : new int[] {0, 100}) {
            RemoteViews legacy = new RemoteViews(context.getPackageName(), opacity == 0
                    ? R.layout.widget_material_refresh_shadow : R.layout.widget_material_refresh,
                    android.R.id.background);
            View previous = legacy.apply(context, new FrameLayout(context));
            TextView previousName = previous.findViewById(R.id.md_name_0);
            previousName.setTypeface(Typeface.DEFAULT_BOLD);
            WidgetOptions options = new WidgetOptions("auto", "system", "app", opacity,
                    "hidden", "remaining")
                    .withVisibleMeters("weekly,next_reset");
            RemoteViews current = WidgetRenderer.build(context, 1, options,
                    UsageCardFixtures.state(UsageCardFixtures.plus(), 2), 170, 218, null);
            assertNotEquals(legacy.getLayoutId(), current.getLayoutId());
            assertThrows(RuntimeException.class, () -> current.reapply(context, previous));
            TextView currentName = current.apply(context, new FrameLayout(context))
                    .findViewById(R.id.md_name_0);
            assertTrue(currentName.getTypeface().getWeight() <= 500);
        }
    }

    @Test
    public void classicCardsKeepARealGradientAcrossAllThreeSeverityBandsAndStyleChanges() {
        Context context = RuntimeEnvironment.getApplication();
        View card = apply(context, remote(context, WidgetOptions.COLOR_CLASSIC, 20, false),
                170, 218);
        for (int used : new int[] {20, 60, 85, 60, 20}) {
            remote(context, WidgetOptions.COLOR_CLASSIC, used, false).reapply(context, card);
            layout(card, 170, 218);
            assertTrue("Classic card must have a gradient at " + used + "% used",
                    opaqueColors(card.findViewById(R.id.md_fill_0)).size() > 4);
            remote(context, WidgetOptions.COLOR_NATIVE, used, false).reapply(context, card);
            layout(card, 170, 218);
            assertEquals("Native fill must clear the previous classic gradient", 1,
                    opaqueColors(card.findViewById(R.id.md_fill_0)).size());
        }
    }

    @Test
    public void oneRowClassicWarningAndCriticalDialsRetainTheirSolidColors() {
        Context context = RuntimeEnvironment.getApplication();
        for (int used : new int[] {60, 85}) {
            View dial = apply(context, remote(context, WidgetOptions.COLOR_CLASSIC, used, true),
                    180, 90);
            Set<Integer> colors = opaqueColors(dial.findViewById(R.id.primary_samsung_fill));
            assertEquals(1, colors.size());
            assertTrue(colors.contains(context.getColor(used == 60
                    ? R.color.widget_classic_progress_warning
                    : R.color.widget_classic_progress_critical)));
        }
    }

    @Test
    public void serializedCardSurfacesUseTheApplyingHostsPaletteWithoutChangingDials() {
        Context light = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(light.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | Configuration.UI_MODE_NIGHT_YES;
        Context dark = light.createConfigurationContext(configuration);
        for (Context host : new Context[] {light, dark}) {
            View card = apply(host, remote(light, WidgetOptions.COLOR_NATIVE, 20, false), 170, 218);
            assertCenterColor(card.findViewById(R.id.md_surface), color(host, "widget_card_surface"));
            assertCenterColor(card.findViewById(R.id.md_panel_bg_0), color(host, "widget_card_panel"));
            View dial = apply(host, remote(light, WidgetOptions.COLOR_NATIVE, 20, true), 180, 90);
            assertCenterColor(dial.findViewById(R.id.dial_surface),
                    host.getColor(R.color.widget_material_surface));
            View classicCard = apply(host, remote(light, WidgetOptions.COLOR_CLASSIC, 20, false),
                    170, 218);
            assertCenterColor(classicCard.findViewById(R.id.md_panel_bg_0),
                    color(host, "widget_card_classic_panel"));
        }
    }

    private static RemoteViews remote(Context context, String style, int used, boolean dial) {
        WidgetOptions options = WidgetOptions.defaults().withColorStyle(style)
                .withVisibleMeters("weekly,next_reset");
        UsageCardState state = UsageCardFixtures.state(
                UsageCardFixtures.snapshot("plus", used, used, false), 2);
        RemoteViews remote = WidgetRenderer.build(context, 1, options, state,
                dial ? 180 : 170, dial ? 90 : 218, null);
        Parcel parcel = Parcel.obtain();
        try {
            remote.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return RemoteViews.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    private static int color(Context context, String name) {
        int resource = context.getResources().getIdentifier(name, "color", context.getPackageName());
        assertTrue("Missing card-only color role: " + name, resource != 0);
        return context.getColor(resource);
    }

    private static View apply(Context context, RemoteViews remote, int width, int height) {
        View view = remote.apply(context, new FrameLayout(context));
        layout(view, width, height);
        return view;
    }

    private static void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static Set<Integer> opaqueColors(View view) {
        Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixel(x, y);
                if (Color.alpha(pixel) == 255) colors.add(pixel);
            }
        }
        image.recycle();
        return colors;
    }

    private static void assertCenterColor(View view, int expected) {
        Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        assertEquals(expected, image.getPixel(image.getWidth() / 2, image.getHeight() / 2));
        image.recycle();
    }
}
