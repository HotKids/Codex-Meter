package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Process;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowBuild;

/** Exercises the stock Samsung geometry through the shared widget dispatcher. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class OneUiDialAppearanceTest {
    @Test
    public void fallbackCardRetainsTheSelectedBackgroundPalette() throws Exception {
        java.lang.reflect.Method fallback = WidgetRenderer.class.getDeclaredMethod(
                "buildFallback", Context.class, int.class, WidgetOptions.class);
        fallback.setAccessible(true);
        for (String[] host : new String[][] {{"Google", "com.google.android.apps.nexuslauncher"},
                {"Samsung", "com.sec.android.app.launcher"}, {"oppo", "com.android.launcher"}}) {
            setHost(host[0], host[1]);
            for (int mode : new int[] {Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES}) {
                Context context = themed(mode);
                for (String style : new String[] {WidgetOptions.COLOR_AUTO,
                        WidgetOptions.COLOR_NATIVE, WidgetOptions.COLOR_CLASSIC}) {
                    View card = apply(context, parcel((RemoteViews) fallback.invoke(null, context, 1,
                            WidgetOptions.defaults().withColorStyle(style))), 170, 170);
                    boolean classic = WidgetOptions.COLOR_CLASSIC.equals(style)
                            || (WidgetOptions.COLOR_AUTO.equals(style) && !"Google".equals(host[0]));
                    int expected = classic
                            ? "oppo".equals(host[0])
                                    ? mode == Configuration.UI_MODE_NIGHT_YES ? 0xFF373737 : 0xFFFFFFFF
                                    : mode == Configuration.UI_MODE_NIGHT_YES ? 0xFF010102 : 0xFFFCFCFF
                            : context.getColor(mode == Configuration.UI_MODE_NIGHT_YES
                                    ? android.R.color.system_accent2_800 : android.R.color.system_accent2_50);
                    if ("Samsung".equals(host[0])) {
                        assertEquals(expected, ((ColorDrawable) card.getBackground()).getColor());
                    } else {
                        assertColor(card.findViewById(R.id.md_surface), expected);
                    }
                }
            }
        }
    }

    @Test
    public void stockBackgroundExposesOpacityToTheLauncherWithoutFadingTextAndIcons() {
        Context light = themed(Configuration.UI_MODE_NIGHT_NO);
        Context night = themed(Configuration.UI_MODE_NIGHT_YES);
        setHost("Samsung", "com.sec.android.app.launcher");
        for (int opacity : new int[] {0, 56, 65, 100}) {
            WidgetOptions options = new WidgetOptions("auto", "system", "app", opacity,
                    "hidden", "remaining").withVisibleMeters("five_hour,weekly");
            for (int height : new int[] {90, 218}) {
                Bundle host = new Bundle();
                host.putInt("semAppWidgetRowSpan", height == 90 ? 1 : 2);
                RemoteViews remote = parcel(WidgetRenderer.build(light, 1, options,
                        UsageCardFixtures.state(UsageCardFixtures.plus(), 2), 188, height, host));
                for (Context context : new Context[] {light, night}) {
                    View widget = apply(context, remote, 188, height);
                    View background = widget.findViewById(android.R.id.background);
                    assertTrue("One UI reads the root ColorDrawable alpha", background.getBackground()
                            instanceof ColorDrawable);
                    int expected = nativeAlpha(opacity) << 24
                            | (context == night ? 0x00010102 : 0x00FCFCFF);
                    assertEquals(expected, ((ColorDrawable) background.getBackground()).getColor());
                    Bitmap painted = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
                    background.getBackground().setBounds(0, 0, 1, 1);
                    background.getBackground().draw(new Canvas(painted));
                    assertTrue("The native Canvas paint retains the requested opacity",
                            matchesColor(painted.getPixel(0, 0), expected));
                    painted.recycle();
                    if (height != 90) {
                        assertCenteredColor(widget.findViewById(R.id.md_panel_bg_0),
                                nativeAlpha(opacity) << 24
                                        | (context == night ? 0x0029292C : 0x00F2F2F5));
                    } else {
                        for (int id : new int[] {R.id.primary_samsung_track,
                                R.id.secondary_samsung_track}) {
                            assertEquals("The 2x1 track keeps its original neutral opacity",
                                    Color.alpha(context.getColor(R.color.widget_material_track)),
                                    ((ImageView) widget.findViewById(id)).getImageAlpha());
                        }
                        for (int id : new int[] {R.id.primary_samsung_fill,
                                R.id.secondary_samsung_fill}) {
                            assertEquals("The 2x1 progress does not follow card opacity", 255,
                                    ((ImageView) widget.findViewById(id)).getImageAlpha());
                        }
                    }
                    int surface = height == 90 ? R.id.dial_surface : R.id.md_surface;
                    assertEquals("Do not paint the translucent background twice", View.GONE,
                            widget.findViewById(surface).getVisibility());
                    assertEquals("The foreground retains its original opacity", 1f,
                            background.getAlpha(), 0f);
                }
            }
        }
    }

    @Test
    public void switchingToAnotherHostClearsTheNativeCardBackgroundOnReapply() {
        Context context = themed(Configuration.UI_MODE_NIGHT_NO);
        WidgetOptions options = new WidgetOptions("auto", "system", "app", 65,
                "hidden", "remaining").withVisibleMeters("five_hour,weekly");
        UsageCardState state = UsageCardFixtures.state(UsageCardFixtures.plus(), 2);
        setHost("Samsung", "com.sec.android.app.launcher");
        View card = apply(context, WidgetRenderer.build(context, 1, options, state,
                188, 218, null), 188, 218);
        setHost("Samsung", "com.example.launcher");
        WidgetRenderer.build(context, 1, options, state, 188, 218, null).reapply(context, card);
        assertEquals(Color.TRANSPARENT, ((ColorDrawable) card.getBackground()).getColor());
        ImageView surface = card.findViewById(R.id.md_surface);
        assertEquals(View.VISIBLE, surface.getVisibility());
        assertEquals(166, surface.getImageAlpha());
        assertCenteredColor(card.findViewById(R.id.md_panel_bg_0),
                0xA6000000 | (context.getColor(R.color.widget_oneui_panel) & 0x00FFFFFF));
        setHost("Samsung", "com.sec.android.app.launcher");
        WidgetRenderer.build(context, 1, options, state, 188, 218, null).reapply(context, card);
        assertCenteredColor(card.findViewById(R.id.md_panel_bg_0), 0xA8F2F2F5);
        assertEquals(168, Color.alpha(((ColorDrawable) card.getBackground()).getColor()));
    }

    @Test
    public void commonCardLayersFollowShellOpacityAcrossHostsPalettesAndEditorPreview() {
        UsageCardState state = UsageCardFixtures.state(UsageCardFixtures.plus(), 2);
        for (Context context : new Context[] {themed(Configuration.UI_MODE_NIGHT_NO),
                themed(Configuration.UI_MODE_NIGHT_YES)}) {
            for (int opacity : new int[] {0, 56, 65, 100}) {
                WidgetOptions options = new WidgetOptions("auto", "system", "app", opacity,
                        "hidden", "remaining").withVisibleMeters("weekly,next_reset");
                View retained = null;
                for (String[] host : new String[][] {{"Samsung", "com.sec.android.app.launcher"},
                        {"oppo", "com.android.launcher"},
                        {"Google", "com.google.android.apps.nexuslauncher"}}) {
                    setHost(host[0], host[1]);
                    for (String style : new String[] {WidgetOptions.COLOR_NATIVE,
                            WidgetOptions.COLOR_CLASSIC, WidgetOptions.COLOR_NATIVE}) {
                        WidgetOptions selected = options.withColorStyle(style);
                        RemoteViews remote = parcel(WidgetRenderer.build(context, 1, selected,
                                state, 188, 218, null));
                        if (retained == null) {
                            retained = apply(context, remote, 188, 218);
                        } else {
                            remote.reapply(context, retained);
                        }
                        boolean samsung = "Samsung".equals(host[0]);
                        int alpha = samsung ? Color.alpha(((ColorDrawable)
                                retained.getBackground()).getColor())
                                : ((ImageView) retained.findViewById(R.id.md_surface)).getImageAlpha();
                        for (int id : new int[] {R.id.md_panel_bg_0, R.id.md_panel_bg_1,
                                R.id.md_panel_bg_2, R.id.md_panel_bg_3}) {
                            assertEquals("Every host reapplies panel opacity from the same shell",
                                    alpha, ((ImageView) retained.findViewById(id)).getImageAlpha());
                        }
                        for (int id : new int[] {R.id.md_panel_bg_0, R.id.md_panel_bg_2}) {
                            Bitmap pixels = draw(retained.findViewById(id));
                            assertEquals("The rendered panel must retain the shell's actual opacity",
                                    alpha, Color.alpha(pixels.getPixel(pixels.getWidth() / 2,
                                            pixels.getHeight() / 2)), alpha == 0 || alpha == 255 ? 0 : 1);
                            pixels.recycle();
                        }
                        for (int id : new int[] {R.id.md_content, R.id.md_title, R.id.md_name_0,
                                R.id.md_value_0, R.id.md_reset_0}) {
                            assertEquals("Text and content must not fade with backgrounds", 1f,
                                    retained.findViewById(id).getAlpha(), 0f);
                        }
                        for (int id : new int[] {R.id.md_logo, R.id.md_refresh}) {
                            assertEquals("Icons must retain their image opacity", 255,
                                    ((ImageView) retained.findViewById(id)).getImageAlpha());
                        }
                        float progressAlpha = opacity <= 0 ? 1f
                                : "Google".equals(host[0]) ? opacity / 100f
                                : opacity == 56 ? 0.30f : opacity == 65 ? 0.66f : 1f;
                        for (int id : new int[] {R.id.md_bar_0, R.id.md_bar_1,
                                R.id.md_bar_2, R.id.md_bar_3}) {
                            assertEquals("Each host's opacity ticks fade card progress; "
                                            + "Background off restores full opacity",
                                    progressAlpha, retained.findViewById(id).getAlpha(), 0f);
                        }
                        for (int id : new int[] {R.id.md_fill_0, R.id.md_fill_1,
                                R.id.md_fill_2, R.id.md_fill_3}) {
                            assertEquals("Progress opacity must preserve the fill image's own alpha",
                                    255, ((ImageView) retained.findViewById(id)).getImageAlpha());
                        }
                        int trackAlpha = Color.alpha(context.getColor(R.color.widget_material_track));
                        for (int id : new int[] {R.id.md_track_0, R.id.md_track_2}) {
                            assertEquals("The track keeps its neutral color's original alpha",
                                    trackAlpha, ((ImageView) retained.findViewById(id)).getImageAlpha());
                        }
                        View fill = retained.findViewById(R.id.md_fill_0);
                        int fillVisibility = fill.getVisibility();
                        fill.setVisibility(View.INVISIBLE);
                        View bar = retained.findViewById(R.id.md_bar_0);
                        // Image alpha is quantized by the drawable before the parent composites it.
                        bar.setAlpha(1f);
                        Bitmap basePixels = draw((View) bar.getParent());
                        int sampleX = bar.getLeft() + bar.getWidth() / 2;
                        int sampleY = bar.getTop() + bar.getHeight() / 2;
                        int baseAlpha = Color.alpha(basePixels.getPixel(sampleX, sampleY));
                        basePixels.recycle();
                        assertTrue("The reference track must contain actual paint", baseAlpha > 0);
                        bar.setAlpha(progressAlpha);
                        Bitmap progressPixels = draw((View) bar.getParent());
                        int paintedAlpha = Color.alpha(progressPixels.getPixel(sampleX, sampleY));
                        assertEquals("The parent must composite the actual track at selected opacity",
                                Math.round(baseAlpha * progressAlpha), paintedAlpha,
                                progressAlpha == 1f ? 0 : 1);
                        if (progressAlpha < 1f) {
                            assertTrue("Partial opacity must visibly fade the rendered track",
                                    paintedAlpha < baseAlpha);
                        }
                        progressPixels.recycle();
                        fill.setVisibility(fillVisibility);
                        View preview = apply(context, parcel(WidgetRenderer.buildPreview(context,
                                AppWidgetManager.INVALID_APPWIDGET_ID, selected, 188, 218)),
                                188, 218);
                        int shellAlpha = ((ImageView) preview.findViewById(R.id.md_surface))
                                .getImageAlpha();
                        assertEquals(alpha, shellAlpha);
                        assertEquals("The editor uses the same panel opacity as the placed card",
                                shellAlpha, ((ImageView) preview.findViewById(R.id.md_panel_bg_0))
                                        .getImageAlpha());
                        for (int id : new int[] {R.id.md_bar_0, R.id.md_bar_2}) {
                            assertEquals("The editor uses the same progress opacity as the card",
                                    progressAlpha, preview.findViewById(id).getAlpha(), 0f);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void homeProvidersDeclareStandardSizesWithOnePickerEntryEach() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Class<?>[] providers = {CodexDialWidget.class, CodexUsageWidget.class};
        for (int index = 0; index < providers.length; index++) {
            ActivityInfo info = context.getPackageManager().getReceiverInfo(
                    new ComponentName(context, providers[index]), PackageManager.GET_META_DATA);
            assertNotNull("The Samsung host must recognize these as standard widgets", info.metaData);
            int resource = info.metaData.getInt("samsung.appwidget.colorful.info");
            assertTrue("The native background requires a Samsung size declaration", resource != 0);
            try (XmlResourceParser xml = context.getResources().getXml(resource)) {
                while (xml.next() != XmlResourceParser.START_TAG) { }
                assertEquals("samsung-appwidget-info", xml.getName());
                String app = "http://schemas.android.com/apk/res-auto";
                int sizes = xml.getAttributeIntValue(app, "widgetSize", 0);
                int preview = xml.getAttributeIntValue(app, "previewSize", 0);
                assertEquals(index == 0 ? 2 : 8, preview);
                assertEquals(preview, sizes & preview);
                assertEquals("Home declarations must not expose new lock-screen widgets", 1,
                        xml.getAttributeIntValue(app, "targetHost", 0));
                assertEquals("The declarations cover the provider's existing placements",
                        index == 0 ? 2 | 8 : 8 | 16 | 32, sizes);
            }
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-notnight-xhdpi")
    public void stockPickerUsesClassicSizeSpecificLayoutsWithoutChangingProviderBounds() throws Exception {
        setHost("Samsung", "com.sec.android.app.launcher");
        for (Context context : new Context[] {themed(Configuration.UI_MODE_NIGHT_NO),
                themed(Configuration.UI_MODE_NIGHT_YES)}) {
            for (boolean dial : new boolean[] {true, false}) {
                int metadata = dial ? R.xml.codex_dial_widget_oneui_info
                        : R.xml.codex_widget_oneui_info;
                int layout;
                try (XmlResourceParser xml = context.getResources().getXml(metadata)) {
                    while (xml.next() != XmlResourceParser.START_TAG) { }
                    layout = xml.getAttributeResourceValue("http://schemas.android.com/apk/res-auto",
                            dial ? "previewLayoutSmall" : "previewLayoutMedium", 0);
                }
                assertTrue("One UI reads its size-specific static preview before the generic layout",
                        layout != 0);
                assertEquals(dial ? "widget_oneui_dial_preview" : "widget_oneui_card_preview",
                        context.getResources().getResourceEntryName(layout));
                FrameLayout host = new FrameLayout(context);
                View preview = new RemoteViews(context.getPackageName(), layout).apply(context, host);
                host.addView(preview);
                int unconstrained = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                host.measure(unconstrained, unconstrained);
                host.layout(0, 0, host.getMeasuredWidth(), host.getMeasuredHeight());
                float density = context.getResources().getDisplayMetrics().density;
                assertEquals(Math.round(125 * density), preview.getWidth());
                assertEquals(Math.round((dial ? 52 : 130) * density), preview.getHeight());
                assertEquals(context.getColor(R.color.widget_oneui_surface),
                        ((ColorDrawable) preview.getBackground()).getColor());
                if (dial) {
                    for (int id : new int[] {R.id.primary_samsung_value, R.id.secondary_samsung_value}) {
                        assertPickerTextVisible(preview.findViewById(id));
                    }
                } else {
                    RemoteViews common = MaterialCardRenderer.build(context,
                            AppWidgetManager.INVALID_APPWIDGET_ID,
                            WidgetOptions.defaults().withColorStyle(WidgetOptions.COLOR_CLASSIC),
                            dev.bennett.codexmeter.WidgetMeters.defaultVisible(),
                            UsageCardFixtures.state(UsageCardFixtures.plus(), 2), 125, 130);
                    View commonCard = apply(context, parcel(common),
                            Math.round(125 * density), Math.round(130 * density));
                    for (int id : new int[] {R.id.md_title, R.id.md_name_0, R.id.md_value_0,
                            R.id.md_reset_0, R.id.md_name_2, R.id.md_value_2, R.id.md_reset_2}) {
                        assertPickerTextVisible(preview.findViewById(id));
                        assertEquals("Static preview typography follows the common renderer",
                                ((TextView) commonCard.findViewById(id)).getTextSize(),
                                ((TextView) preview.findViewById(id)).getTextSize(), 0.5f);
                    }
                    assertCenteredColor(preview.findViewById(R.id.md_panel_bg_0),
                            context.getColor(R.color.widget_oneui_panel));
                    ImageView fill = preview.findViewById(R.id.md_fill_0);
                    Bitmap expected = Bitmap.createBitmap(fill.getWidth(), fill.getHeight(),
                            Bitmap.Config.ARGB_8888);
                    android.graphics.drawable.Drawable gradient = context.getDrawable(
                            R.drawable.widget_classic_capsule_fill);
                    gradient.setBounds(0, 0, fill.getWidth(), fill.getHeight());
                    gradient.setLevel(6400);
                    gradient.draw(new Canvas(expected));
                    Bitmap actual = draw(fill);
                    assertTrue("Static samples use the same untinted Classic gradient as real meters",
                            expected.sameAs(actual));
                    actual.recycle();
                    expected.recycle();
                    ImageView track = preview.findViewById(R.id.md_track_0);
                    int trackColor = context.getColor(R.color.widget_material_track);
                    assertEquals(trackColor, track.getImageTintList().getDefaultColor());
                    Bitmap trackPixels = draw(track);
                    int trackPixel = trackPixels.getPixel(trackPixels.getWidth() / 2,
                            trackPixels.getHeight() / 2);
                    assertEquals(Color.alpha(trackColor), Color.alpha(trackPixel));
                    // Low-alpha RGB loses precision in premultiplied bitmap channels.
                    int tolerance = (int) Math.ceil(255.0 / Color.alpha(trackColor));
                    assertEquals(Color.red(trackColor), Color.red(trackPixel), tolerance);
                    assertEquals(Color.green(trackColor), Color.green(trackPixel), tolerance);
                    assertEquals(Color.blue(trackColor), Color.blue(trackPixel), tolerance);
                    trackPixels.recycle();
                }
            }
        }
        try (XmlResourceParser xml = RuntimeEnvironment.getApplication().getResources()
                .getXml(R.xml.codex_widget_info)) {
            while (xml.next() != XmlResourceParser.START_TAG) { }
            String android = "http://schemas.android.com/apk/res/android";
            assertTrue(xml.getAttributeValue(android, "minWidth").matches("110(\\.0)?(dp|dip)"));
            assertTrue(xml.getAttributeValue(android, "minHeight").matches("110(\\.0)?(dp|dip)"));
            assertEquals(R.layout.widget_material_preview,
                    xml.getAttributeResourceValue(android, "previewLayout", 0));
        }
    }

    private static void assertPickerTextVisible(TextView text) {
        assertNotNull(text.getLayout());
        assertEquals("No ellipsis for " + text.getText(), 0, text.getLayout().getEllipsisCount(0));
        android.graphics.Rect visible = new android.graphics.Rect();
        assertTrue("The picker must show " + text.getText(), text.getLocalVisibleRect(visible));
        assertEquals("No bottom crop for " + text.getText(), text.getHeight(), visible.height());
        assertTrue("The full line must fit for " + text.getText(),
                text.getLayout().getHeight() <= text.getHeight());
    }

    @Test
    public void editorPreviewKeepsItsOwnRoundedShellWithoutALauncher() {
        Context context = themed(Configuration.UI_MODE_NIGHT_NO);
        setHost("Samsung", "com.sec.android.app.launcher");
        for (int opacity : new int[] {56, 65, 100}) {
            WidgetOptions options = new WidgetOptions("auto", "system", "app", opacity,
                    "hidden", "remaining");
            for (int height : new int[] {90, 218}) {
                RemoteViews preview = WidgetRenderer.buildPreview(context,
                        AppWidgetManager.INVALID_APPWIDGET_ID, options, 188, height);
                View widget = apply(context, parcel(preview), 188, height);
                assertEquals(Color.TRANSPARENT, ((ColorDrawable) widget.getBackground()).getColor());
                ImageView surface = widget.findViewById(height == 90 ? R.id.dial_surface : R.id.md_surface);
                assertEquals(View.VISIBLE, surface.getVisibility());
                assertEquals(nativeAlpha(opacity), surface.getImageAlpha());
                Bitmap pixels = draw(surface);
                assertEquals("The editor retains rounded rather than rectangular corners", 0,
                        Color.alpha(pixels.getPixel(0, 0)));
                pixels.recycle();
            }
        }
    }

    @Test
    public void genericCardsRetainDynamicInnerColorsAndStoredOpacityTicks() {
        for (Context context : new Context[] {themed(Configuration.UI_MODE_NIGHT_NO),
                themed(Configuration.UI_MODE_NIGHT_YES)}) {
            setHost("Google", "com.google.android.apps.nexuslauncher");
            for (int opacity : new int[] {56, 65, 100}) {
                WidgetOptions options = new WidgetOptions("auto", "system", "app", opacity,
                        "hidden", "remaining").withVisibleMeters("five_hour,weekly");
                View card = apply(context, parcel(WidgetRenderer.build(context, 1, options,
                        UsageCardFixtures.state(UsageCardFixtures.plus(), 2), 188, 218, null)),
                        188, 218);
                ImageView surface = card.findViewById(R.id.md_surface);
                assertEquals(Math.round(opacity * 2.55f), surface.getImageAlpha());
                assertColor(surface, (Math.round(opacity * 2.55f) << 24)
                        | (context.getColor(R.color.widget_material_surface) & 0x00FFFFFF));
                assertCenteredColor(card.findViewById(R.id.md_panel_bg_0),
                        (Math.round(opacity * 2.55f) << 24)
                                | (context.getColor(R.color.widget_material_panel) & 0x00FFFFFF));
            }
        }
    }

    @Test
    public void nativeCardLayersKeepTheGenericForegroundAndLayout() {
        for (Context context : new Context[] {themed(Configuration.UI_MODE_NIGHT_NO),
                themed(Configuration.UI_MODE_NIGHT_YES)}) {
            setHost("Samsung", "com.sec.android.app.launcher");
            for (int[] size : new int[][] {{140, 200}, {170, 170}, {330, 170}, {430, 200}}) {
                WidgetOptions options = new WidgetOptions("auto", "system", "app", 100,
                        "hidden", "remaining").withVisibleMeters("weekly,next_reset");
                UsageCardState state = UsageCardFixtures.state(UsageCardFixtures.plus(), 2);
                RemoteViews original = MaterialCardRenderer.build(context, 1, options,
                        WidgetRenderer.selectedKeys(options, state.snapshot, false), state,
                        size[0], size[1]);
                View baseline = apply(context, parcel(original), size[0], size[1]);
                View nativeCard = apply(context, parcel(WidgetRenderer.build(context, 1,
                        options, state, size[0], size[1], null)), size[0], size[1]);
                for (int id : new int[] {R.id.md_content, R.id.md_header, R.id.md_logo,
                        R.id.md_title, R.id.md_refresh, R.id.md_status, R.id.md_updated,
                        R.id.md_row_0, R.id.md_panel_0, R.id.md_name_0, R.id.md_value_0,
                        R.id.md_reset_0, R.id.md_bar_0, R.id.md_panel_2}) {
                    View expected = baseline.findViewById(id);
                    View actual = nativeCard.findViewById(id);
                    assertEquals(expected.getLeft(), actual.getLeft());
                    assertEquals(expected.getTop(), actual.getTop());
                    assertEquals(expected.getWidth(), actual.getWidth());
                    assertEquals(expected.getHeight(), actual.getHeight());
                    assertEquals(expected.getVisibility(), actual.getVisibility());
                    if (expected instanceof TextView) {
                        assertEquals(((TextView) expected).getText().toString(),
                                ((TextView) actual).getText().toString());
                        assertEquals(((TextView) expected).getTextSize(),
                                ((TextView) actual).getTextSize(), 0.001f);
                    }
                }
                for (View view : new View[] {baseline, nativeCard}) {
                    view.setBackgroundColor(Color.TRANSPARENT);
                    for (int id : new int[] {R.id.md_surface, R.id.md_panel_bg_0,
                            R.id.md_panel_bg_1, R.id.md_panel_bg_2, R.id.md_panel_bg_3}) {
                        view.findViewById(id).setVisibility(View.INVISIBLE);
                    }
                }
                Bitmap expected = draw(baseline);
                Bitmap actual = draw(nativeCard);
                assertTrue("Only backgrounds may change the card's actual foreground pixels",
                        expected.sameAs(actual));
                expected.recycle();
                actual.recycle();
            }
        }
    }

    @Test
    public void onlyStockSamsungUsesSquareHeightDrivenDials() {
        Context context = themed(Configuration.UI_MODE_NIGHT_NO);
        for (String[] host : new String[][] {{"Google", "com.google.android.apps.nexuslauncher"},
                {"Samsung", "com.example.launcher"}, {"Samsung", null}}) {
            setHost(host[0], host[1]);
            assertEquals("Other hosts must retain the common dial layout", R.layout.widget_rings,
                    build(context, 90, 100, 36, "five_hour,weekly").getLayoutId());
        }
        setHost("Samsung", "com.sec.android.app.launcher");
        float previousArc = 0f;
        float previousText = 0f;
        for (int height : new int[] {90, 120}) {
            RemoteViews remote = build(context, height, 100, 36, "weekly,next_reset");
            assertNotEquals(R.layout.widget_rings, remote.getLayoutId());
            View widget = apply(context, remote, height);
            View arc = widget.findViewById(R.id.primary_samsung_track);
            assertEquals("The native arc retains the full 138-square viewport",
                    arc.getWidth(), arc.getHeight());
            assertTrue("A taller cell must increase its arc", arc.getHeight() > previousArc);
            TextView value = widget.findViewById(R.id.primary_samsung_value);
            assertTrue("A taller cell must increase its value text", value.getTextSize() > previousText);
            previousArc = arc.getHeight();
            previousText = value.getTextSize();
            for (int id : new int[] {R.id.primary_samsung_value, R.id.secondary_samsung_value}) {
                TextView text = widget.findViewById(id);
                assertNotNull(text.getLayout());
                assertEquals(1, text.getLayout().getLineCount());
                assertEquals(0, text.getLayout().getEllipsisCount(0));
                assertTrue("The text row must retain its complete glyph height: height="
                                + text.getHeight() + ", lineBottom=" + text.getLayout().getLineBottom(0)
                                + ", textSize=" + text.getTextSize() + ", value=" + text.getText(),
                        text.getLayout().getLineBottom(0) <= text.getHeight());
                assertTrue("The complete value must fit the native cell",
                        text.getPaint().measureText(text.getText().toString()) <= text.getWidth() + 1f);
            }
        }
    }

    @Test
    public void parcelledDialsResolveSharedNeutralColorsAndRetainDynamicFill() {
        Context light = themed(Configuration.UI_MODE_NIGHT_NO);
        Context night = themed(Configuration.UI_MODE_NIGHT_YES);
        for (String[] host : new String[][] {{"Google", "com.google.android.apps.nexuslauncher"},
                {"Samsung", "com.sec.android.app.launcher"}}) {
            setHost(host[0], host[1]);
            RemoteViews remote = parcel(build(light, 90, 100, 50, "five_hour,weekly",
                    WidgetOptions.COLOR_NATIVE));
            for (Context context : new Context[] {light, night}) {
                boolean dark = context == night;
                assertEquals(context.getColor(dark ? android.R.color.system_accent2_800
                                : android.R.color.system_accent2_50),
                        context.getColor(R.color.widget_material_surface));
                assertEquals(dark ? 0x33FCFCFF : 0x1A000000,
                        context.getColor(R.color.widget_material_track));
                View widget = apply(context, remote, 90);
                if ("Samsung".equals(host[0])) {
                    assertEquals(context.getColor(R.color.widget_material_surface),
                            ((ColorDrawable) widget.getBackground()).getColor());
                } else {
                    assertColor(widget.findViewById(R.id.dial_surface),
                            context.getColor(R.color.widget_material_surface));
                }
                assertColor(widget.findViewById(R.id.primary_samsung_track),
                        context.getColor(R.color.widget_material_track));
                assertColor(widget.findViewById(R.id.primary_samsung_fill),
                        context.getColor(R.color.widget_material_fill));
            }
            View transparent = apply(night, parcel(build(light, 90, 0, 50,
                    "five_hour,weekly", WidgetOptions.COLOR_NATIVE)), 90);
            assertEquals(View.GONE, transparent.findViewById(R.id.dial_surface).getVisibility());
        }
    }

    @Test
    public void nativeArcStillRepresentsRemainingAllowance() {
        Context context = themed(Configuration.UI_MODE_NIGHT_NO);
        setHost("Samsung", "com.sec.android.app.launcher");
        int previous = Integer.MAX_VALUE;
        for (int used : new int[] {0, 50, 100}) {
            View widget = apply(context, parcel(build(context, 90, 100, used,
                    "five_hour,weekly", WidgetOptions.COLOR_NATIVE)), 90);
            assertEquals((100 - used) + "%", ((TextView) widget.findViewById(
                    R.id.primary_samsung_value)).getText().toString());
            int pixels = coloredPixels(widget.findViewById(R.id.primary_samsung_fill),
                    context.getColor(R.color.widget_material_fill));
            assertTrue("The filled arc must decrease as used allowance increases", pixels < previous);
            previous = pixels;
        }
        assertEquals("An exhausted allowance must have no fill", 0, previous);
    }

    @Test
    public void cachedTracksReplaceTheLegacyOpaqueColorFilterOnReapply() {
        Context context = themed(Configuration.UI_MODE_NIGHT_YES);
        setHost("Google", "com.google.android.apps.nexuslauncher");
        RemoteViews dial = build(context, 90, 100, 50, "five_hour,weekly");
        View dialWidget = apply(context, dial, 90);
        ImageView dialTrack = dialWidget.findViewById(R.id.primary_samsung_track);
        dialTrack.setColorFilter(0xFF506070);
        dial.reapply(context, dialWidget);
        assertColor(dialTrack, context.getColor(R.color.widget_material_track));

        WidgetOptions options = new WidgetOptions("auto", "system", "app", 100,
                "hidden", "remaining").withVisibleMeters("five_hour,weekly");
        RemoteViews card = WidgetRenderer.build(context, 1, options,
                UsageCardFixtures.state(UsageCardFixtures.plus(), 2), 170, 170, null);
        View cardWidget = apply(context, card, 170, 170);
        ImageView cardTrack = cardWidget.findViewById(R.id.md_track_0);
        cardTrack.setColorFilter(0xFF506070);
        card.reapply(context, cardWidget);
        assertEquals("The retained night track must restore native alpha", 51,
                cardTrack.getImageAlpha());
        View fresh = apply(context, card, 170, 170);
        Bitmap expected = draw(fresh.findViewById(R.id.md_track_0));
        Bitmap actual = draw(cardTrack);
        assertTrue("Reapply must match a fresh track, including its curved-edge coverage",
                expected.sameAs(actual));
        expected.recycle();
        actual.recycle();
    }

    @Test
    @Config(shadows = {ColorOsWidgetAppearanceTest.ProviderPreviewCache.class})
    public void nativePickerPreviewUsesSamplesAndOnlyRemovesItsOwnCache() {
        Context context = themed(Configuration.UI_MODE_NIGHT_NO);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, CodexDialWidget.class);
        ComponentName cardProvider = new ComponentName(context, CodexUsageWidget.class);
        int category = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN;
        setHost("Google", "com.google.android.apps.nexuslauncher");
        OneUiWidgetAppearance.publishPreview(context, manager);
        setHost("Samsung", "com.sec.android.app.launcher");
        OneUiWidgetAppearance.publishPreview(context, manager);
        RemoteViews preview = manager.getWidgetPreview(provider, Process.myUserHandle(), category);
        assertNotNull(preview);
        assertEquals(R.layout.widget_oneui_rings, preview.getLayoutId());
        View widget = apply(context, parcel(preview), 180, 90);
        assertEquals("76%", ((TextView) widget.findViewById(R.id.primary_samsung_value)).getText());
        assertEquals("75%", ((TextView) widget.findViewById(R.id.secondary_samsung_value)).getText());
        RemoteViews cardPreview = manager.getWidgetPreview(cardProvider, Process.myUserHandle(), category);
        assertNotNull(cardPreview);
        assertEquals(R.layout.widget_material, cardPreview.getLayoutId());
        View card = apply(context, parcel(cardPreview), 170, 170);
        assertCenteredColor(card.findViewById(R.id.md_panel_bg_0), 0xFFF2F2F5);
        assertEquals("76%", ((TextView) card.findViewById(R.id.md_value_0)).getText());
        assertEquals("75%", ((TextView) card.findViewById(R.id.md_value_2)).getText());
        OneUiWidgetAppearance.publishPreview(context, manager);
        assertSame("An unchanged process must avoid repeated publication", preview,
                manager.getWidgetPreview(provider, Process.myUserHandle(), category));
        assertSame(cardPreview, manager.getWidgetPreview(cardProvider, Process.myUserHandle(), category));
        setHost("Samsung", "com.example.launcher");
        OneUiWidgetAppearance.publishPreview(context, manager);
        assertNull(manager.getWidgetPreview(provider, Process.myUserHandle(), category));
        assertNull(manager.getWidgetPreview(cardProvider, Process.myUserHandle(), category));
        RemoteViews foreign = new RemoteViews(context.getPackageName(), R.layout.widget_material_preview);
        manager.setWidgetPreview(provider, category, foreign);
        manager.setWidgetPreview(cardProvider, category, foreign);
        OneUiWidgetAppearance.publishPreview(context, manager);
        assertSame("Another appearance's preview must remain untouched", foreign,
                manager.getWidgetPreview(provider, Process.myUserHandle(), category));
        assertSame(foreign, manager.getWidgetPreview(cardProvider, Process.myUserHandle(), category));
        setHost("Samsung", "com.sec.android.app.launcher");
        OneUiWidgetAppearance.publishPreview(context, manager);
        assertEquals("Returning to stock One UI must regenerate its preview", R.layout.widget_oneui_rings,
                manager.getWidgetPreview(provider, Process.myUserHandle(), category).getLayoutId());
        assertEquals(R.layout.widget_material,
                manager.getWidgetPreview(cardProvider, Process.myUserHandle(), category).getLayoutId());
    }

    @Test
    @Config(shadows = {ColorOsWidgetAppearanceTest.ProviderPreviewCache.class})
    public void nativePickerRetriesRejectedOrFailedSecondProviderBeforeFinishing() {
        Context context = themed(Configuration.UI_MODE_NIGHT_NO);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ColorOsWidgetAppearanceTest.ProviderPreviewCache cache =
                (ColorOsWidgetAppearanceTest.ProviderPreviewCache) shadowOf(manager);
        ComponentName provider = new ComponentName(context, CodexUsageWidget.class);
        int category = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN;
        for (boolean throwsException : new boolean[] {false, true}) {
            setHost("Google", "com.google.android.apps.nexuslauncher");
            OneUiWidgetAppearance.publishPreview(context, manager);
            setHost("Samsung", "com.sec.android.app.launcher");
            RemoteViews stale = new RemoteViews(context.getPackageName(), R.layout.widget_material);
            stale.setInt(R.id.md_panel_bg_0, "setColorFilter", Color.MAGENTA);
            assertTrue(manager.setWidgetPreview(provider, category, stale));
            cache.failNextProvider = provider;
            cache.throwOnFailure = throwsException;
            OneUiWidgetAppearance.publishPreview(context, manager);
            assertSame("The failed second provider must retain its previous preview", stale,
                    manager.getWidgetPreview(provider, Process.myUserHandle(), category));
            OneUiWidgetAppearance.publishPreview(context, manager);
            RemoteViews current = manager.getWidgetPreview(provider, Process.myUserHandle(), category);
            assertNotSame("A successful first provider must not suppress retrying the second", stale,
                    current);
            assertCenteredColor(apply(context, parcel(current), 170, 170)
                    .findViewById(R.id.md_panel_bg_0), 0xFFF2F2F5);
            OneUiWidgetAppearance.publishPreview(context, manager);
            assertSame("Both successful providers close the retry", current,
                    manager.getWidgetPreview(provider, Process.myUserHandle(), category));
        }
    }

    private static RemoteViews build(Context context, int height, int opacity, int used, String meters) {
        return build(context, height, opacity, used, meters, WidgetOptions.COLOR_AUTO);
    }

    private static RemoteViews build(Context context, int height, int opacity, int used, String meters,
            String colorStyle) {
        WidgetOptions options = new WidgetOptions("auto", "system", "app", opacity,
                "hidden", "remaining").withVisibleMeters(meters).withColorStyle(colorStyle);
        Bundle host = new Bundle();
        host.putInt("semAppWidgetRowSpan", 1);
        return WidgetRenderer.build(context, 1, options, UsageCardFixtures.state(
                UsageCardFixtures.snapshot("plus", used, used, false), 2), 260, height, host);
    }

    private static Context themed(int mode) {
        Context app = RuntimeEnvironment.getApplication();
        Configuration config = new Configuration(app.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | mode;
        return app.createConfigurationContext(config);
    }

    private static void setHost(String manufacturer, String launcher) {
        ShadowBuild.setManufacturer(manufacturer);
        ShadowBuild.setModel("Google".equals(manufacturer) ? "Pixel 11 Pro" : "Test device");
        Context app = RuntimeEnvironment.getApplication();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = new ResolveInfo();
        info.isDefault = true;
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = launcher;
        info.activityInfo.name = "Launcher";
        info.activityInfo.enabled = true;
        info.activityInfo.exported = true;
        shadowOf(app.getPackageManager()).setResolveInfosForIntent(home, launcher == null
                ? Collections.emptyList() : Collections.singletonList(info));
    }

    private static View apply(Context context, RemoteViews remote, int height) {
        return apply(context, remote, 260, height);
    }

    private static View apply(Context context, RemoteViews remote, int width, int height) {
        View widget = remote.apply(context, new FrameLayout(context));
        widget.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        widget.layout(0, 0, width, height);
        return widget;
    }

    private static RemoteViews parcel(RemoteViews remote) {
        Parcel parcel = Parcel.obtain();
        try {
            remote.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return RemoteViews.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    private static void assertColor(View view, int color) {
        if (coloredPixels(view, color) > 0) return;
        Bitmap pixels = draw(view);
        int mostOpaque = 0;
        for (int y = 0; y < pixels.getHeight(); y++) {
            for (int x = 0; x < pixels.getWidth(); x++) {
                int pixel = pixels.getPixel(x, y);
                if (Color.alpha(pixel) > Color.alpha(mostOpaque)) mostOpaque = pixel;
            }
        }
        pixels.recycle();
        throw new AssertionError("The rendered layer must use " + Integer.toHexString(color)
                + "; most opaque actual pixel=" + Integer.toHexString(mostOpaque));
    }

    private static int nativeAlpha(int storedOpacity) {
        return storedOpacity == 0 ? 0 : storedOpacity == 56 ? 77
                : storedOpacity == 65 ? 168 : 255;
    }

    private static void assertCenteredColor(View view, int color) {
        Bitmap pixels = draw(view);
        int actual = pixels.getPixel(pixels.getWidth() / 2, pixels.getHeight() / 2);
        if (Color.alpha(color) == 255) {
            assertEquals("The inner surface uses the exact opaque theme color", color, actual);
        } else {
            assertTrue("The inner surface retains its requested color and opacity: expected "
                    + Integer.toHexString(color) + ", actual " + Integer.toHexString(actual),
                    matchesColor(actual, color));
        }
        pixels.recycle();
    }

    private static int coloredPixels(View view, int color) {
        Bitmap pixels = draw(view);
        int count = 0;
        for (int y = 0; y < pixels.getHeight(); y++) {
            for (int x = 0; x < pixels.getWidth(); x++) {
                if (matchesColor(pixels.getPixel(x, y), color)) count++;
            }
        }
        pixels.recycle();
        return count;
    }

    private static Bitmap draw(View view) {
        Bitmap pixels = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(pixels));
        return pixels;
    }

    private static boolean matchesColor(int actual, int expected) {
        int alpha = Color.alpha(expected);
        if (alpha == 255) return actual == expected;
        int actualAlpha = Color.alpha(actual);
        if (alpha == 0) return actualAlpha == 0;
        if (Math.abs(actualAlpha - alpha) > 1) return false;
        // Bitmap premultiplication quantizes low-alpha channels during Canvas rendering.
        for (int shift : new int[] {0, 8, 16}) {
            int actualChannel = Math.round(((actual >> shift) & 255) * actualAlpha / 255f);
            int expectedChannel = Math.round(((expected >> shift) & 255) * actualAlpha / 255f);
            if (Math.abs(actualChannel - expectedChannel) > 1) return false;
        }
        return true;
    }
}
