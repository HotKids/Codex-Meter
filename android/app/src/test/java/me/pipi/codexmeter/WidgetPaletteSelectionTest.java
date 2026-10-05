package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Parcel;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowBuild;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetPaletteSelectionTest {
    private static final String[][] HOSTS = {
            {"Google", "Pixel 11 Pro", "com.google.android.apps.nexuslauncher"},
            {"Samsung", "SM-S9480", "com.sec.android.app.launcher"},
            {"OPPO", "PLP110", "com.android.launcher"},
            {"Xiaomi", "Xiaomi Pad", "com.miui.home"},
            {"Google", "Nexus 9", "com.google.android.apps.nexuslauncher"}};
    private static final String[] STYLES = {WidgetOptions.COLOR_AUTO,
            WidgetOptions.COLOR_NATIVE, WidgetOptions.COLOR_CLASSIC};

    @Before
    public void prepareSyntheticLauncherSettings() {
        Settings.Secure.putString(RuntimeEnvironment.getApplication().getContentResolver(),
                "layout_icon_size", "52");
    }

    @Test
    public void automaticSelectionIsLimitedToGooglePixelAndExplicitChoicesIgnoreTheDevice() {
        for (int host = 0; host < HOSTS.length; host++) {
            setHost(HOSTS[host]);
            assertEquals(HOSTS[host][1], host != 0,
                    OneUiWidgetAppearance.classicPalette(WidgetOptions.COLOR_AUTO));
            assertFalse(OneUiWidgetAppearance.classicPalette(WidgetOptions.COLOR_NATIVE));
            assertTrue(OneUiWidgetAppearance.classicPalette(WidgetOptions.COLOR_CLASSIC));
        }
        setHost(new String[] {"Samsung", "Pixel 11 Pro", "com.sec.android.app.launcher"});
        assertTrue("A Pixel-like model name alone cannot select the Google palette",
                OneUiWidgetAppearance.classicPalette(WidgetOptions.COLOR_AUTO));
    }

    @Test
    public void dispatcherAppliesEverySelectionAcrossHostsAndNightModes() {
        for (int host = 0; host < HOSTS.length; host++) {
            setHost(HOSTS[host]);
            for (int mode : new int[] {Configuration.UI_MODE_NIGHT_NO,
                    Configuration.UI_MODE_NIGHT_YES}) {
                Context context = themed(mode);
                for (String style : STYLES) {
                    boolean classic = WidgetOptions.COLOR_CLASSIC.equals(style)
                            || (WidgetOptions.COLOR_AUTO.equals(style) && host != 0);
                    View widget = apply(context, build(context, style, 100, 170, 218), 170, 218);
                    int shell = context.getColor(classic
                            ? host == 2 ? R.color.widget_coloros_classic_surface
                                    : R.color.widget_oneui_surface
                            : R.color.widget_material_surface);
                    int panel = context.getColor(classic
                            ? host == 2 ? R.color.widget_coloros_classic_panel
                                    : R.color.widget_oneui_panel
                            : R.color.widget_material_panel);
                    if (host == 1) {
                        assertEquals(shell, ((ColorDrawable) widget.getBackground()).getColor());
                    } else {
                        assertCenteredColor(widget.findViewById(R.id.md_surface), shell);
                    }
                    assertCenteredColor(widget.findViewById(R.id.md_panel_bg_0), panel);
                    assertEquals(255, ((ImageView) widget.findViewById(
                            R.id.md_panel_bg_0)).getImageAlpha());
                    assertEquals(context.getColor(R.color.widget_material_text),
                            ((TextView) widget.findViewById(R.id.md_value_0)).getCurrentTextColor());
                }
            }
        }
    }

    @Test
    public void serializedSamsungNativeShellKeepsHostOpacityAndResolvesDayAndNightResources() {
        setHost(HOSTS[1]);
        Context light = themed(Configuration.UI_MODE_NIGHT_NO);
        Context night = themed(Configuration.UI_MODE_NIGHT_YES);
        int[] stored = {56, 65, 100};
        int[] alpha = {77, 168, 255};
        for (int index = 0; index < stored.length; index++) {
            for (int height : new int[] {90, 218}) {
                RemoteViews remote = build(light, WidgetOptions.COLOR_NATIVE,
                        stored[index], 188, height);
                for (Context host : new Context[] {light, night}) {
                    View widget = apply(host, remote, 188, height);
                    assertTrue(widget.getBackground() instanceof ColorDrawable);
                    int actual = ((ColorDrawable) widget.getBackground()).getColor();
                    assertEquals(alpha[index], Color.alpha(actual));
                    assertEquals(host.getColor(R.color.widget_material_surface) & 0x00FFFFFF,
                            actual & 0x00FFFFFF);
                    assertEquals(1f, widget.getAlpha(), 0f);
                    assertEquals(View.GONE, widget.findViewById(height == 90
                            ? R.id.dial_surface : R.id.md_surface).getVisibility());
                    if (height != 90) {
                        assertCenteredColor(widget.findViewById(R.id.md_panel_bg_0),
                                (alpha[index] << 24)
                                        | (host.getColor(R.color.widget_material_panel) & 0x00FFFFFF));
                    }
                }
            }
        }
    }

    @Test
    public void selectingColorsPreservesHostGeometryTypographyAndOpacity() {
        for (String[] host : new String[][] {HOSTS[0], HOSTS[1], HOSTS[2]}) {
            setHost(host);
            Context context = themed(Configuration.UI_MODE_NIGHT_NO);
            for (int[] size : new int[][] {{180, 90}, {170, 218}}) {
                RemoteViews baselineRemote = build(context, WidgetOptions.COLOR_CLASSIC,
                        65, size[0], size[1]);
                View baseline = apply(context, baselineRemote, size[0], size[1]);
                for (String style : new String[] {WidgetOptions.COLOR_AUTO,
                        WidgetOptions.COLOR_NATIVE}) {
                    RemoteViews selectedRemote = build(context, style, 65, size[0], size[1]);
                    View selected = apply(context, selectedRemote, size[0], size[1]);
                    assertEquals(baselineRemote.getLayoutId(), selectedRemote.getLayoutId());
                    assertEquals(baseline.getClipToOutline(), selected.getClipToOutline());
                    int surfaceId = size[1] == 90 ? R.id.dial_surface : R.id.md_surface;
                    ImageView originalSurface = baseline.findViewById(surfaceId);
                    ImageView selectedSurface = selected.findViewById(surfaceId);
                    assertSameGeometry(originalSurface, selectedSurface);
                    assertEquals(shadowOf(originalSurface.getDrawable()).getCreatedFromResId(),
                            shadowOf(selectedSurface.getDrawable()).getCreatedFromResId());
                    assertEquals(originalSurface.getImageAlpha(), selectedSurface.getImageAlpha());
                    if (OneUiWidgetAppearance.isStockLauncher(context)) {
                        assertEquals(Color.alpha(((ColorDrawable) baseline.getBackground()).getColor()),
                                Color.alpha(((ColorDrawable) selected.getBackground()).getColor()));
                    }
                    int[] contentIds = size[1] == 90
                            ? new int[] {R.id.primary_samsung_progress, R.id.primary_samsung_track,
                                    R.id.primary_samsung_icon, R.id.primary_samsung_value}
                            : new int[] {R.id.md_content, R.id.md_header, R.id.md_logo,
                                    R.id.md_title, R.id.md_refresh, R.id.md_panel_0,
                                    R.id.md_panel_bg_0, R.id.md_name_0, R.id.md_value_0,
                                    R.id.md_bar_0, R.id.md_reset_0};
                    for (int id : contentIds) {
                        View before = baseline.findViewById(id);
                        View after = selected.findViewById(id);
                        assertNotNull(before);
                        assertNotNull(after);
                        assertSameGeometry(before, after);
                        if (before instanceof TextView) {
                            TextView beforeText = (TextView) before;
                            TextView afterText = (TextView) after;
                            assertEquals(beforeText.getText().toString(), afterText.getText().toString());
                            assertEquals(beforeText.getTextSize(), afterText.getTextSize(), 0f);
                            assertEquals(beforeText.getLineHeight(), afterText.getLineHeight());
                            assertEquals(beforeText.getTypeface(), afterText.getTypeface());
                        }
                    }
                }
            }
        }
    }

    private static RemoteViews build(Context context, String style, int opacity,
            int width, int height) {
        WidgetOptions options = new WidgetOptions("auto", "system", "app", opacity,
                "hidden", "remaining").withVisibleMeters("five_hour,weekly")
                .withColorStyle(style);
        RemoteViews remote = WidgetRenderer.build(context, 1, options,
                UsageCardFixtures.state(UsageCardFixtures.plus(), 2), width, height, null);
        Parcel parcel = Parcel.obtain();
        try {
            remote.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return RemoteViews.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    private static View apply(Context context, RemoteViews remote, int width, int height) {
        View widget = remote.apply(context, new FrameLayout(context));
        widget.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        widget.layout(0, 0, width, height);
        return widget;
    }

    private static void assertSameGeometry(View before, View after) {
        assertEquals(before.getVisibility(), after.getVisibility());
        assertEquals(before.getLeft(), after.getLeft());
        assertEquals(before.getTop(), after.getTop());
        assertEquals(before.getWidth(), after.getWidth());
        assertEquals(before.getHeight(), after.getHeight());
        assertEquals(before.getPaddingLeft(), after.getPaddingLeft());
        assertEquals(before.getPaddingTop(), after.getPaddingTop());
        assertEquals(before.getPaddingRight(), after.getPaddingRight());
        assertEquals(before.getPaddingBottom(), after.getPaddingBottom());
        assertEquals(before.getScaleX(), after.getScaleX(), 0f);
        assertEquals(before.getScaleY(), after.getScaleY(), 0f);
    }

    private static void assertCenteredColor(View view, int expected) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
                Bitmap.Config.ARGB_8888);
        try {
            view.draw(new Canvas(bitmap));
            int actual = bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 2);
            if (Color.alpha(expected) == 255) {
                assertEquals(expected, actual);
            } else {
                assertTrue(Math.abs(Color.alpha(expected) - Color.alpha(actual)) <= 1);
                // Premultiplied bitmap channels are quantized when the image alpha is reduced.
                int tolerance = (int) Math.ceil(255f / Color.alpha(actual));
                assertTrue(Math.abs(Color.red(expected) - Color.red(actual)) <= tolerance);
                assertTrue(Math.abs(Color.green(expected) - Color.green(actual)) <= tolerance);
                assertTrue(Math.abs(Color.blue(expected) - Color.blue(actual)) <= tolerance);
            }
        } finally {
            bitmap.recycle();
        }
    }

    private static Context themed(int mode) {
        Context app = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(app.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | mode;
        return app.createConfigurationContext(configuration);
    }

    private static void setHost(String[] host) {
        ShadowBuild.setManufacturer(host[0]);
        ShadowBuild.setModel(host[1]);
        Context app = RuntimeEnvironment.getApplication();
        ResolveInfo home = new ResolveInfo();
        home.isDefault = true;
        home.activityInfo = new ActivityInfo();
        home.activityInfo.packageName = host[2];
        home.activityInfo.name = "Launcher";
        home.activityInfo.enabled = true;
        home.activityInfo.exported = true;
        shadowOf(app.getPackageManager()).setResolveInfosForIntent(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                Collections.singletonList(home));
    }
}
