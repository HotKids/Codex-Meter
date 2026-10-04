package me.pipi.codexmeter;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcel;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.TypedValue;
import android.util.SizeF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;
import dev.bennett.codexmeter.UsageCredits;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.WidgetMeters;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAppWidgetManager;
import org.robolectric.shadows.ShadowBuild;
import org.robolectric.util.ReflectionHelpers;

/** Compares host-specific parameters with the renderer's default path and native pixels. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "zh-rCN-mdpi",
        shadows = {ColorOsWidgetAppearanceTest.SyntheticSignedOutStore.class,
                ColorOsWidgetAppearanceTest.ProviderPreviewCache.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ColorOsWidgetAppearanceTest {
    private static final int[] PANELS = {R.id.md_panel_bg_0, R.id.md_panel_bg_1,
            R.id.md_panel_bg_2, R.id.md_panel_bg_3};
    private static final Size[] SIZES = {new Size(42, 170, 170, false),
            new Size(42, 330, 170, false), new Size(99, 180, 90, true)};
    private Application app;
    private UsageCardState state;

    @Before
    public void prepareSyntheticHost() {
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        state = UsageCardFixtures.state(UsageCardFixtures.plus(), 2);
        Settings.Secure.putString(app.getContentResolver(), "layout_icon_size", "52");
        bind(42, CodexUsageWidget.class, 170);
        bind(99, CodexDialWidget.class, 90);
        setHost("OPPO", "com.android.launcher");
    }

    @Test
    @Config(qualifiers = "zh-rCN-notnight-mdpi")
    public void normalLaunchersKeepTheCompleteOriginalLightPixels() {
        assertNormalProducts();
    }

    @Test
    @Config(qualifiers = "zh-rCN-night-mdpi")
    public void normalLaunchersKeepTheCompleteOriginalDarkPixels() {
        assertNormalProducts();
    }

    @Test
    @Config(sdk = 28)
    public void preAndroidTwelveOppoHostsKeepTheOriginalPixels() {
        for (Size size : SIZES) {
            assertPixelsEqual(apply(size, original(size, options(100))),
                    apply(size, dispatched(size, options(100))));
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-notnight-xhdpi")
    public void colorOsLightLayersKeepAllForegroundPixelsAndRenderReviewImages() throws Exception {
        assertColorOsLayers(0xFFF2F3F4, 0xFFFAFAFA, "light");
    }

    @Test
    @Config(qualifiers = "zh-rCN-night-xhdpi")
    public void colorOsDarkLayersKeepAllForegroundPixelsAndRenderReviewImages() throws Exception {
        assertColorOsLayers(0xFF252627, 0xFF282929, "dark");
    }

    @Test
    @Config(qualifiers = "zh-rCN-notnight-xhdpi")
    public void colorOsCardsReuseGenericTypographyAndFitLongValues() {
        state = proState();
        WidgetOptions options = options(100)
                .withVisibleMeters("weekly,next_reset,usage_credits");
        for (Size size : new Size[] {SIZES[0], SIZES[1]}) {
            View generic = apply(size, original(size, options));
            View colorOs = apply(size, dispatched(size, options));
            assertHeaderSharesPanelEdge(colorOs);
            for (int id : new int[] {R.id.md_title, R.id.md_name_0, R.id.md_value_0,
                    R.id.md_reset_0, size.width < MaterialCardRenderer.MEDIUM_MIN_WIDTH_DP
                            ? R.id.md_value_2 : R.id.md_value_1}) {
                assertEquals("ColorOS uses the generic card's typography",
                        text(generic, id).getTextSize(), text(colorOs, id).getTextSize(), 0.001f);
                assertEquals(text(generic, id).getLineHeight(), text(colorOs, id).getLineHeight());
                assertFullyShown(text(colorOs, id));
            }
        }
        View wide = apply(SIZES[1], dispatched(SIZES[1], options));
        TextView balance = text(wide, R.id.md_value_2);
        assertEquals("62,437.29", balance.getText().toString());
        assertFullyShown(balance);
        assertEquals(View.GONE, wide.findViewById(R.id.md_bar_2).getVisibility());
        View narrowBalance = apply(SIZES[0], dispatched(SIZES[0], options
                .withVisibleMeters("usage_credits,next_reset")));
        TextView fitted = text(narrowBalance, R.id.md_value_0);
        assertEquals(balance.getText().toString(), fitted.getText().toString());
        View genericBalance = apply(SIZES[0], original(SIZES[0], options
                .withVisibleMeters("usage_credits,next_reset")));
        assertEquals(text(genericBalance, R.id.md_value_0).getTextSize(),
                fitted.getTextSize(), 0.001f);
        assertFullyShown(fitted);
        assertFullyShown(text(narrowBalance, R.id.md_name_0));
        assertEquals(View.GONE, narrowBalance.findViewById(R.id.md_bar_0).getVisibility());
    }

    @Test
    public void cardsReuseGenericFailureStatusAndRestoreTheirClockOnSuccess() {
        UsageCardState failed = new UsageCardState(true,
                UsageCardFixtures.snapshot("plus", 19, 23, false), null,
                "Synthetic network failure", UsageCardFixtures.NOW);
        WidgetOptions options = options(100).withVisibleMeters("weekly,next_reset");
        for (Size size : new Size[] {SIZES[0], SIZES[1]}) {
            state = failed;
            View view = apply(size, dispatched(size, options));
            View generic = apply(size, original(size, options));
            TextView status = text(view, R.id.md_status);
            assertEquals(generic.findViewById(R.id.md_status).getVisibility(), status.getVisibility());
            assertEquals(text(generic, R.id.md_status).getText().toString(),
                    status.getText().toString());
            assertEquals(View.GONE, view.findViewById(R.id.md_updated).getVisibility());
            ImageView refresh = view.findViewById(R.id.md_refresh);
            assertEquals(R.drawable.ic_ms_sync_problem,
                    shadowOf(refresh.getDrawable()).getCreatedFromResId());
            assertEquals(app.getString(R.string.widget_card_refresh_failed),
                    refresh.getContentDescription());
            assertHeaderSharesPanelEdge(view);
            state = new UsageCardState(true, failed.snapshot, null, "", UsageCardFixtures.NOW);
            dispatched(size, options).reapply(app, view);
            measure(size, view);
            assertEquals(size.width >= 280 ? View.VISIBLE : View.GONE,
                    view.findViewById(R.id.md_updated).getVisibility());
            assertEquals(R.drawable.ic_ms_sync,
                    shadowOf(refresh.getDrawable()).getCreatedFromResId());
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-port-notnight-xhdpi")
    public void publicIconMirrorChangesActualScaledCornerAndKeepsBackgroundBounds() {
        for (int iconDp : new int[] {40, 64}) {
            Settings.Secure.putString(app.getContentResolver(), "layout_icon_size",
                    Integer.toString(iconDp));
            for (Size size : new Size[] {SIZES[0], SIZES[1]}) {
                View placed = apply(size, dispatched(size, options(100)));
                placed.findViewById(R.id.md_content).setVisibility(View.INVISIBLE);
                View host = (View) placed.getParent();
                ImageView outer = surface(size, placed);
                RectF transformed = new RectF(0, 0, outer.getWidth(), outer.getHeight());
                outer.getMatrix().mapRect(transformed);
                assertEquals(0f, transformed.left, 0f);
                assertEquals(0f, transformed.top, 0f);
                assertEquals(host.getWidth(), transformed.right, 1f);
                assertEquals(host.getHeight(), transformed.bottom, 1f);

                Bitmap actual = draw(host);
                Bitmap expected = Bitmap.createBitmap(host.getWidth(), host.getHeight(),
                        Bitmap.Config.ARGB_8888);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setShader(new LinearGradient(0, 0, host.getWidth(), host.getHeight(),
                        0xFFF2F3F4, 0xFFFAFAFA, Shader.TileMode.CLAMP));
                // Native big-folder geometry uses 28/56 of the final icon size at this density.
                float expectedRadius = px(iconDp) / 2f;
                new Canvas(expected).drawRoundRect(new RectF(0, 0, host.getWidth(), host.getHeight()),
                        expectedRadius, expectedRadius, paint);
                for (int y = 0; y < Math.ceil(expectedRadius); y++) {
                    for (int x = 0; x < Math.ceil(expectedRadius); x++) {
                        assertEquals("The scaled top-left corner must track the icon mirror at "
                                        + x + "," + y,
                                Color.alpha(expected.getPixel(x, y)),
                                Color.alpha(actual.getPixel(x, y)), 1);
                    }
                }
                for (int[] point : new int[][] {{host.getWidth() / 2, 0},
                        {host.getWidth() - 1, host.getHeight() / 2},
                        {host.getWidth() / 2, host.getHeight() - 1}, {0, host.getHeight() / 2}}) {
                    assertTrue("Scaled backgrounds must not leave a transparent edge",
                            Color.alpha(actual.getPixel(point[0], point[1])) > 70);
                }
                for (float position : new float[] {0.25f, 0.5f, 0.75f}) {
                    int x = Math.round(host.getWidth() * position);
                    int y = Math.round(host.getHeight() * position);
                    for (int shift : new int[] {0, 8, 16}) {
                        assertEquals("The parent draw must retain the native gradient RGB",
                                (expected.getPixel(x, y) >> shift) & 255,
                                (actual.getPixel(x, y) >> shift) & 255, 1);
                    }
                }
                actual.recycle();
                expected.recycle();
            }
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-port-notnight-xhdpi")
    public void portraitDialHeightUsesTheIconMirrorAndMissingValuesKeepOriginalGeometry() {
        Size dial = SIZES[2];
        String[] mirrors = {"40", "64", "100", null, "invalid", "NaN", "0.001"};
        int[] heights = {40, 64, 90, 90, 90, 90, 90};
        for (int index = 0; index < mirrors.length; index++) {
            Settings.Secure.putString(app.getContentResolver(), "layout_icon_size", mirrors[index]);
            View view = apply(dial, dispatched(dial, options(100)));
            View host = (View) view.getParent();
            assertEquals(px(dial.height), host.getHeight());
            assertEquals(px(heights[index]), view.getHeight());
            assertEquals((host.getHeight() - view.getHeight()) / 2, view.getTop());
            assertCapsulePixels(surface(dial, view));
            for (int id : new int[] {R.id.primary_samsung_value, R.id.secondary_samsung_value}) {
                assertFullyShown(text(view, id));
            }
            if (index >= 3) {
                View card = apply(SIZES[0], dispatched(SIZES[0], options(100)));
                assertEquals(1f, surface(SIZES[0], card).getScaleX(), 0f);
                assertEquals(1f, surface(SIZES[0], card).getScaleY(), 0f);
            }
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-port-notnight-xhdpi")
    @LooperMode(LooperMode.Mode.PAUSED)
    public void actualEditorPreviewKeepsThePlacedDialHeightAndNormalHostsKeepTheirFullHeight() {
        Size dial = new Size(99, 170, 90, true);
        WidgetOptions selected = options(100);
        AppPreferences.saveWidgetOptions(app, dial.id, selected);
        for (boolean colorOs : new boolean[] {true, false}) {
            setHost(colorOs ? "OPPO" : "Google", colorOs ? "com.android.launcher"
                    : "com.google.android.apps.nexuslauncher");
            for (String mirror : new String[] {"40", "64"}) {
                Settings.Secure.putString(app.getContentResolver(), "layout_icon_size", mirror);
                Intent intent = new Intent(app, WidgetConfigActivity.class)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, dial.id);
                ActivityController<WidgetConfigActivity> controller = Robolectric
                        .buildActivity(WidgetConfigActivity.class, intent).setup();
                try {
                    WidgetConfigActivity activity = controller.get();
                    FrameLayout container = activity.findViewById(R.id.widget_preview_container);
                    assertNotNull(container);
                    container.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
                    container.layout(0, 0, 800, 400);
                    ReflectionHelpers.callInstanceMethod(activity, "renderPreview");
                    shadowOf(Looper.getMainLooper()).idle();
                    container.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
                    container.layout(0, 0, 800, 400);
                    assertEquals(2, container.getChildCount());
                    FrameLayout previewSurface = (FrameLayout) container.getChildAt(1);
                    View widget = previewSurface.getChildAt(0);
                    assertEquals(px(dial.width), previewSurface.getWidth());
                    assertEquals(px(dial.height), previewSurface.getHeight());
                    assertEquals(px(colorOs ? Integer.parseInt(mirror) : dial.height), widget.getHeight());
                    assertEquals((previewSurface.getHeight() - widget.getHeight()) / 2, widget.getTop());
                    WidgetOptions editorOptions = ReflectionHelpers.callInstanceMethod(activity,
                            "currentOptions");
                    View placed = apply(dial, WidgetRenderer.build(app, dial.id, editorOptions,
                            UsageCardFixtures.signedOut(), dial.width, dial.height, null));
                    assertPixelsEqual(placed, widget);
                } finally {
                    controller.pause().stop().destroy();
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-port-notnight-xhdpi")
    @SuppressWarnings("deprecation")
    public void responsiveDialsKeepOrientationGeometryAfterParcelAndCachedReapply() {
        Bundle bounds = new Bundle();
        ArrayList<SizeF> sizes = new ArrayList<>();
        sizes.add(new SizeF(180f, 90f));
        sizes.add(new SizeF(260f, 64f));
        bounds.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, sizes);
        bounds.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180);
        bounds.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 260);
        bounds.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 64);
        bounds.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 90);
        WidgetOptions options = options(100);
        RemoteViews generated = ReflectionHelpers.callStaticMethod(WidgetRenderer.class,
                "buildResponsive", ReflectionHelpers.ClassParameter.from(Context.class, app),
                ReflectionHelpers.ClassParameter.from(int.class, 99),
                ReflectionHelpers.ClassParameter.from(WidgetOptions.class, options),
                ReflectionHelpers.ClassParameter.from(UsageCardState.class, state),
                ReflectionHelpers.ClassParameter.from(Bundle.class, bounds));
        RemoteViews cached = parcelRoundTrip(generated);
        FrameLayout host = new FrameLayout(app);
        View wrapper = null;
        for (boolean landscape : new boolean[] {false, true, false}) {
            Configuration configuration = new Configuration(app.getResources().getConfiguration());
            configuration.orientation = landscape ? Configuration.ORIENTATION_LANDSCAPE
                    : Configuration.ORIENTATION_PORTRAIT;
            Context context = app;
            context.getResources().updateConfiguration(configuration,
                    context.getResources().getDisplayMetrics());
            if (wrapper != null) {
                // Nested addView actions read the existing root's context after system configuration updates.
                wrapper.getContext().getResources().updateConfiguration(configuration,
                        wrapper.getContext().getResources().getDisplayMetrics());
                assertEquals(configuration.orientation,
                        wrapper.getContext().getResources().getConfiguration().orientation);
            }
            SizeF size = sizes.get(landscape ? 1 : 0);
            // AppWidgetHostView uses this framework selector before the public nested addView action.
            RemoteViews selected = ReflectionHelpers.callInstanceMethod(cached,
                    "getRemoteViewsToApply", ReflectionHelpers.ClassParameter.from(Context.class, context),
                    ReflectionHelpers.ClassParameter.from(SizeF.class, size));
            if (wrapper == null) {
                wrapper = selected.apply(context, host);
                host.addView(wrapper);
            } else {
                selected.reapply(context, wrapper);
            }
            host.measure(View.MeasureSpec.makeMeasureSpec(px((int) size.getWidth()),
                            View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(px((int) size.getHeight()),
                            View.MeasureSpec.EXACTLY));
            host.layout(0, 0, px((int) size.getWidth()), px((int) size.getHeight()));
            ViewGroup container = wrapper.findViewById(R.id.widget_coloros_dial_container);
            assertNotNull(container);
            assertEquals(1, container.getChildCount());
            View dial = container.getChildAt(0);
            assertEquals(px(landscape ? 64 : 52), dial.getHeight());
            assertEquals((container.getHeight() - dial.getHeight()) / 2, dial.getTop());
            assertFullyShown(text(dial, R.id.primary_samsung_value));
            assertFullyShown(text(dial, R.id.secondary_samsung_value));
            FrameLayout directHost = new FrameLayout(context);
            View direct = WidgetRenderer.build(context, 99, options, state, size.getWidth(),
                    size.getHeight(), bounds).apply(context, directHost);
            FrameLayout.LayoutParams directParams = (FrameLayout.LayoutParams) direct.getLayoutParams();
            directParams.gravity = Gravity.CENTER;
            directHost.addView(direct, directParams);
            directHost.measure(View.MeasureSpec.makeMeasureSpec(host.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(host.getHeight(), View.MeasureSpec.EXACTLY));
            directHost.layout(0, 0, host.getWidth(), host.getHeight());
            assertPixelsEqual(direct, dial);
        }

        // A dual-orientation value directly inside the sized map loses its ideal size on Binder.
        Map<SizeF, RemoteViews> unsupported = new LinkedHashMap<>();
        for (SizeF size : sizes) {
            RemoteViews leaf = DialWidgetRenderer.build(app, 99, options,
                    WidgetMeters.defaultVisible(), state);
            unsupported.put(size, new RemoteViews(new RemoteViews(leaf), leaf));
        }
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> parcelRoundTrip(new RemoteViews(unsupported)));
        assertTrue(failure.getMessage().contains("Expected RemoteViews to have ideal size"));
    }

    @Test
    public void reapplyingAcrossLaunchersRestoresFreshPixelsInBothDirections() {
        for (int opacity : new int[] {0, 15, 40, 100}) {
            for (Size size : SIZES) {
                WidgetOptions options = options(opacity);
                setHost("Google", "com.google.android.apps.nexuslauncher");
                RemoteViews ordinary = dispatched(size, options);
                View reused = apply(size, ordinary);
                for (boolean colorOs : new boolean[] {true, false, true}) {
                    setHost(colorOs ? "OPPO" : "samsung",
                            colorOs ? "com.android.launcher" : "com.sec.android.app.launcher");
                    RemoteViews next = dispatched(size, options);
                    assertEquals(ordinary.getLayoutId(), next.getLayoutId());
                    next.reapply(app, reused);
                    measure(size, reused);
                    View expected = apply(size, colorOs ? next : original(size, options));
                    assertPixelsEqual(expected, reused);
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-notnight-xhdpi")
    public void generatedLightPreviewsRenderTheSameNativeLayersDespiteXmlTint() throws Exception {
        assertPublishedPreviews(0xFFF2F3F4, 0xFFFAFAFA, "light");
    }

    @Test
    @Config(qualifiers = "zh-rCN-night-xhdpi")
    public void generatedDarkPreviewsRenderTheSameNativeLayersDespiteXmlTint() throws Exception {
        assertPublishedPreviews(0xFF252627, 0xFF282929, "dark");
    }

    @Test
    @Config(qualifiers = "zh-rCN-notnight-xhdpi")
    public void publishedCardsAdaptToUnknownParentBoundsWithoutCroppingTheirCorners() {
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        ComponentName provider = new ComponentName(app, CodexUsageWidget.class);
        for (String mirror : new String[] {"40", "64"}) {
            Settings.Secure.putString(app.getContentResolver(), "layout_icon_size", mirror);
            manager.removeWidgetPreview(provider, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
            ColorOsWidgetAppearance.publishPreviews(app, manager);
            RemoteViews preview = manager.getWidgetPreview(provider, Process.myUserHandle(),
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
            assertNotNull(preview);
            for (int height : new int[] {170, 190}) {
                Size hostSize = new Size(42, 170, height, false);
                View view = apply(hostSize, preview);
                ImageView background = surface(hostSize, view);
                assertEquals(px(hostSize.width), background.getWidth());
                assertEquals(px(height), background.getHeight());
                assertEquals(1f, background.getScaleX(), 0f);
                assertEquals(1f, background.getScaleY(), 0f);
                assertCompleteParentCorners(view);
            }

            // This is the former owning call, retained as a causal counterexample without reverting code.
            RemoteViews fixedPlacement = new RemoteViews(app.getPackageName(),
                    R.layout.widget_material_preview);
            ColorOsWidgetAppearance.apply(app, fixedPlacement, false, 100, 170f, 190f);
            View cropped = apply(SIZES[0], fixedPlacement);
            Bitmap oldPixels = drawBackgroundInParent(cropped);
            boolean asymmetric = false;
            for (int y = 0; y < px(28); y++) {
                for (int x = 0; x < px(28); x++) {
                    if (Math.abs(Color.alpha(oldPixels.getPixel(x, y))
                            - Color.alpha(oldPixels.getPixel(x, oldPixels.getHeight() - 1 - y))) > 70) {
                        asymmetric = true;
                    }
                }
            }
            assertTrue("A guessed 190dp preview height crops the corner in a 170dp host",
                    asymmetric);
            assertThrows("The complete-parent oracle must reject the former cropped preview",
                    AssertionError.class, () -> assertCompleteParentCorners(cropped));
            oldPixels.recycle();
        }
    }

    private void assertNormalProducts() {
        for (String[] host : new String[][] {{"Google", "com.google.android.apps.nexuslauncher"},
                {"samsung", "com.sec.android.app.launcher"}, {"OPPO", "org.example.launcher"},
                {"OPPO", null}}) {
            setHost(host[0], host[1]);
            for (int opacity : new int[] {0, 15, 40, 100}) {
                for (Size size : SIZES) {
                    WidgetOptions options = options(opacity);
                    assertPixelsEqual(apply(size, original(size, options)),
                            apply(size, dispatched(size, options)));
                }
            }
        }
    }

    private void assertColorOsLayers(int start, int end, String name) throws Exception {
        PreviewSheet sheet = new PreviewSheet("coloros-appearance-" + name);
        for (Size size : SIZES) {
            for (int opacity : new int[] {0, 15, 40, 100}) {
                WidgetOptions options = options(opacity);
                View view = apply(size, dispatched(size, options));
                ImageView outer = surface(size, view);
                assertEquals(opacity == 0 ? View.GONE : View.VISIBLE, outer.getVisibility());
                assertEquals(Math.round(opacity * 2.55f), outer.getImageAlpha());
                assertGradient(outer, start, end);
                if (!size.dial) {
                    assertEquals("This is the local default radius, not the host's smooth curve",
                            28f * density(), ((GradientDrawable) outer.getDrawable()).getCornerRadius(), 0f);
                    for (int panelId : PANELS) {
                        ImageView panel = view.findViewById(panelId);
                        if (isShown(panel)) {
                            assertAccentLayer(panel);
                        }
                    }
                } else if (opacity == 100) {
                    assertCapsulePixels(outer);
                }
                if (opacity == 100) {
                    sheet.add("ColorOS " + size.label() + " " + name, (View) view.getParent(),
                            px(size.width), px(size.height));
                }
                View baseline = apply(size, originalColorOsForeground(size, options));
                hideBackgrounds(size, view);
                hideBackgrounds(size, baseline);
                assertPixelsEqual(baseline, view);
            }
        }
        for (Size size : new Size[] {SIZES[0], SIZES[1]}) {
            RemoteViews proWidget = WidgetRenderer.build(app, size.id,
                    options(100).withVisibleMeters("weekly,next_reset,usage_credits"),
                    proState(), size.width, size.height, null);
            View placed = apply(size, proWidget);
            sheet.add("ColorOS Pro 200 " + size.label() + " " + name, (View) placed.getParent(),
                    px(size.width), px(size.height));
        }
        assertTrue(sheet.writeSheet().isFile());
    }

    private void assertPublishedPreviews(int start, int end, String name) throws Exception {
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        ColorOsWidgetAppearance.publishPreviews(app, manager);
        PreviewSheet sheet = new PreviewSheet("coloros-picker-" + name);
        for (Size size : new Size[] {SIZES[0], SIZES[2]}) {
            Class<?> provider = size.dial ? CodexDialWidget.class : CodexUsageWidget.class;
            RemoteViews preview = manager.getWidgetPreview(new ComponentName(app, provider),
                    Process.myUserHandle(), AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
            assertNotNull(preview);
            View view = apply(size, preview);
            ImageView outer = surface(size, view);
            assertGradient(outer, start, end);
            assertCompleteParentCorners(view);
            Bitmap outerPixels = draw(outer);
            assertEquals(0, Color.alpha(outerPixels.getPixel(0, 0)));
            assertEquals(255, Color.alpha(outerPixels.getPixel(outer.getWidth() / 2, 0)));
            outerPixels.recycle();
            if (size.dial) {
                assertCapsulePixels(outer);
            } else {
                assertHeaderSharesPanelEdge(view);
                for (int panelId : new int[] {R.id.md_panel_bg_0, R.id.md_panel_bg_2}) {
                    assertAccentLayer(view.findViewById(panelId));
                }
                View placed = apply(size, dispatched(size, options(100)));
                for (int id : new int[] {R.id.md_title, R.id.md_name_0, R.id.md_value_0,
                        R.id.md_reset_0, R.id.md_name_2, R.id.md_value_2, R.id.md_reset_2}) {
                    // XML dp text sizes round to pixels; RemoteViews retains the fraction.
                    assertEquals("The picker must use the placed widget's text size",
                            text(placed, id).getTextSize(), text(view, id).getTextSize(), 0.5f);
                    assertFullyShown(text(view, id));
                }
            }
            int[] texts = size.dial
                    ? new int[] {R.id.primary_samsung_value, R.id.secondary_samsung_value}
                    : new int[] {R.id.md_title, R.id.md_name_0, R.id.md_value_0,
                            R.id.md_name_2, R.id.md_value_2};
            for (int text : texts) {
                assertEquals(app.getColor(R.color.widget_material_text),
                        ((TextView) view.findViewById(text)).getCurrentTextColor());
            }
            sheet.add("ColorOS picker " + size.label() + " " + name, (View) view.getParent(),
                    px(size.width), px(size.height));
        }
        assertTrue(sheet.writeSheet().isFile());
    }

    private static void assertHeaderSharesPanelEdge(View widget) {
        View header = widget.findViewById(R.id.md_header);
        View logo = widget.findViewById(R.id.md_logo);
        View row = widget.findViewById(R.id.md_row_0);
        View panel = widget.findViewById(R.id.md_panel_0);
        assertEquals("ColorOS inherits the shared header and panel alignment",
                row.getLeft() + panel.getLeft(), header.getLeft() + logo.getLeft());
    }

    private static void assertCompleteParentCorners(View widget) {
        Bitmap pixels = drawBackgroundInParent(widget);
        for (int x : new int[] {0, pixels.getWidth() - 1}) {
            for (int y : new int[] {0, pixels.getHeight() - 1}) {
                assertEquals("All four corners must remain transparent in the actual parent draw",
                        0, Color.alpha(pixels.getPixel(x, y)));
            }
        }
        for (int[] point : new int[][] {{pixels.getWidth() / 2, 0},
                {pixels.getWidth() / 2, pixels.getHeight() - 1}}) {
            assertEquals(255, Color.alpha(pixels.getPixel(point[0], point[1])));
        }
        int cornerExtent = Math.min(pixels.getWidth(), pixels.getHeight()) / 2;
        for (boolean horizontal : new boolean[] {true, false}) {
            for (int depth = 0; depth < cornerExtent; depth++) {
                int boundary = cornerBoundary(pixels, false, false, horizontal, depth);
                for (boolean[] corner : new boolean[][] {{false, true}, {true, false}, {true, true}}) {
                    int actual = cornerBoundary(pixels, corner[0], corner[1], horizontal, depth);
                    // ColorOS derives its widget clip from alpha >70; Skia edge coverage can vary
                    // at reflected samples while the visible contour stays within one pixel.
                    assertEquals("The whole corner contour must survive the actual parent at depth "
                                    + depth + " right=" + corner[0] + " bottom=" + corner[1],
                            boundary, actual, 1);
                }
            }
        }
        pixels.recycle();
    }

    private static int cornerBoundary(Bitmap pixels, boolean right, boolean bottom,
            boolean horizontal, int depth) {
        int extent = (horizontal ? pixels.getWidth() : pixels.getHeight()) / 2;
        for (int offset = 0; offset < extent; offset++) {
            int x = horizontal ? offset : depth;
            int y = horizontal ? depth : offset;
            if (right) {
                x = pixels.getWidth() - 1 - x;
            }
            if (bottom) {
                y = pixels.getHeight() - 1 - y;
            }
            if (Color.alpha(pixels.getPixel(x, y)) > 70) {
                return offset;
            }
        }
        return extent;
    }

    private static Bitmap drawBackgroundInParent(View widget) {
        ViewGroup group = (ViewGroup) widget;
        int[] visibility = new int[group.getChildCount()];
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            visibility[index] = child.getVisibility();
            if (!(child instanceof ImageView)) {
                child.setVisibility(View.INVISIBLE);
            }
        }
        Bitmap pixels = draw((View) widget.getParent());
        for (int index = 0; index < group.getChildCount(); index++) {
            group.getChildAt(index).setVisibility(visibility[index]);
        }
        return pixels;
    }

    private void assertAccentLayer(ImageView panel) {
        GradientDrawable drawable = (GradientDrawable) panel.getDrawable();
        assertNotNull(drawable.getColor());
        int fill = drawable.getColor().getDefaultColor();
        assertEquals(25, Color.alpha(fill));
        assertEquals(app.getColor(R.color.widget_material_fill) & 0x00FFFFFF, fill & 0x00FFFFFF);
        Bitmap pixels = draw(panel);
        int actual = pixels.getPixel(panel.getWidth() / 2, panel.getHeight() / 2);
        assertEquals("The actual paint must override any inherited opaque XML tint",
                25, Color.alpha(actual));
        // Compare premultiplied channels because low alpha magnifies one storage quantization step.
        for (int shift : new int[] {0, 8, 16}) {
            int expectedPremultiplied = Math.round(((fill >> shift) & 255) * 25f / 255f);
            int actualPremultiplied = Math.round(((actual >> shift) & 255) * 25f / 255f);
            assertEquals(expectedPremultiplied, actualPremultiplied, 1);
        }
        pixels.recycle();
    }

    private static void assertGradient(ImageView image, int start, int end) {
        assertTrue(image.getDrawable() instanceof GradientDrawable);
        GradientDrawable gradient = (GradientDrawable) image.getDrawable();
        assertEquals(GradientDrawable.Orientation.TL_BR, gradient.getOrientation());
        assertArrayEquals(new int[] {start, end}, gradient.getColors());
        if (image.getVisibility() == View.VISIBLE && image.getImageAlpha() == 255) {
            Bitmap actual = draw(image);
            Bitmap reference = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
            Paint paint = new Paint();
            paint.setShader(new LinearGradient(0, 0, image.getWidth(), image.getHeight(),
                    start, end, Shader.TileMode.CLAMP));
            new Canvas(reference).drawRect(0, 0, image.getWidth(), image.getHeight(), paint);
            for (float position : new float[] {0.25f, 0.5f, 0.75f}) {
                int x = Math.round(image.getWidth() * position);
                int y = Math.round(image.getHeight() * position);
                int expectedPixel = reference.getPixel(x, y);
                int actualPixel = actual.getPixel(x, y);
                assertEquals(255, Color.alpha(actualPixel));
                for (int shift : new int[] {0, 8, 16}) {
                    assertEquals("The transparent filter must preserve the gradient's actual RGB",
                            (expectedPixel >> shift) & 255, (actualPixel >> shift) & 255, 1);
                }
            }
            actual.recycle();
            reference.recycle();
        }
    }

    private static void assertCapsulePixels(ImageView surface) {
        Bitmap actual = draw(surface);
        Bitmap expected = Bitmap.createBitmap(surface.getWidth(), surface.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(expected);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        float radius = Math.min(surface.getWidth(), surface.getHeight()) / 2f;
        canvas.drawRoundRect(new RectF(0, 0, surface.getWidth(), surface.getHeight()), radius, radius, paint);
        for (int y = 0; y < actual.getHeight(); y++) {
            for (int x = 0; x < actual.getWidth(); x++) {
                assertEquals("The dial backdrop must be a height-adaptive capsule at " + x + "," + y,
                        Color.alpha(expected.getPixel(x, y)), Color.alpha(actual.getPixel(x, y)), 1);
            }
        }
        actual.recycle();
        expected.recycle();
    }

    /** The unchanged renderer is the original-product oracle, with no copied layout logic. */
    private RemoteViews original(Size size, WidgetOptions options) {
        List<String> keys = WidgetRenderer.selectedKeys(options, state.snapshot, size.dial);
        return size.dial ? DialWidgetRenderer.build(app, size.id, options, keys, state)
                : MaterialCardRenderer.build(app, size.id, options, keys, state, size.width, size.height);
    }

    private RemoteViews originalColorOsForeground(Size size, WidgetOptions options) {
        if (size.dial) {
            RemoteViews reference = original(size, options);
            reference.setViewLayoutHeight(android.R.id.background, 52f,
                    TypedValue.COMPLEX_UNIT_DIP);
            return reference;
        }
        return original(size, options);
    }

    private static UsageCardState proState() {
        UsageSnapshot pro = UsageCardFixtures.snapshot("pro200", 36, 58, false);
        UsageSnapshot withBalance = new UsageSnapshot(pro.planType, pro.allowed, pro.limitReached,
                pro.fiveHour, pro.weekly, pro.monthly, pro.additionalLimits,
                new UsageCredits(true, false, "62437.28578"), pro.resetCreditsAvailable,
                pro.fetchedAtMillis);
        return UsageCardFixtures.state(withBalance, 2);
    }

    private static TextView text(View view, int id) {
        return view.findViewById(id);
    }

    private static void assertFullyShown(TextView text) {
        assertTrue(isShown(text));
        assertNotNull(text.getLayout());
        assertEquals(1, text.getLayout().getLineCount());
        assertEquals("No ellipsis for " + text.getText(), 0,
                text.getLayout().getEllipsisCount(0));
        assertTrue("The actual glyphs must fit for " + text.getText(),
                text.getPaint().measureText(text.getText().toString())
                        <= text.getWidth() - text.getCompoundPaddingLeft()
                        - text.getCompoundPaddingRight() + 1f);
    }

    private RemoteViews dispatched(Size size, WidgetOptions options) {
        return WidgetRenderer.build(app, size.id, options, state, size.width, size.height, null);
    }

    private static WidgetOptions options(int opacity) {
        WidgetOptions options = WidgetOptions.defaults().withVisibleMeters("five_hour,weekly");
        ReflectionHelpers.setField(options, "opacity", opacity);
        return options;
    }

    private void bind(int id, Class<?> provider, int height) {
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        info.provider = new ComponentName(app, provider);
        shadowOf(manager).addInstalledProvider(info);
        shadowOf(manager).bindAppWidgetId(id, info.provider);
        Bundle bounds = new Bundle();
        bounds.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 170);
        bounds.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height);
        manager.updateAppWidgetOptions(id, bounds);
    }

    private void setHost(String manufacturer, String launcher) {
        ShadowBuild.setManufacturer(manufacturer);
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        if (launcher == null) {
            shadowOf(app.getPackageManager()).setResolveInfosForIntent(home, Collections.emptyList());
            return;
        }
        ResolveInfo resolver = new ResolveInfo();
        resolver.isDefault = true;
        resolver.activityInfo = new ActivityInfo();
        resolver.activityInfo.packageName = launcher;
        resolver.activityInfo.name = launcher + ".Launcher";
        resolver.activityInfo.enabled = true;
        resolver.activityInfo.exported = true;
        shadowOf(app.getPackageManager()).setResolveInfosForIntent(home, Collections.singletonList(resolver));
        assertEquals(launcher, app.getPackageManager().resolveActivity(home,
                PackageManager.MATCH_DEFAULT_ONLY).activityInfo.packageName);
    }

    private View apply(Size size, RemoteViews remote) {
        FrameLayout host = new FrameLayout(app);
        View view = remote.apply(app, host);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        // AppWidgetHostView.prepareView preserves the RemoteViews dimensions and centers its child.
        params.gravity = Gravity.CENTER;
        host.addView(view, params);
        measure(size, view);
        return view;
    }

    private void measure(Size size, View view) {
        View host = (View) view.getParent();
        host.measure(View.MeasureSpec.makeMeasureSpec(px(size.width), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(px(size.height), View.MeasureSpec.EXACTLY));
        host.layout(0, 0, px(size.width), px(size.height));
    }

    private float density() {
        return app.getResources().getDisplayMetrics().density;
    }

    private static RemoteViews parcelRoundTrip(RemoteViews remote) {
        Parcel parcel = Parcel.obtain();
        try {
            remote.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return RemoteViews.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    private int px(int dp) {
        return Math.round(dp * density());
    }

    private static ImageView surface(Size size, View view) {
        return view.findViewById(size.dial ? R.id.dial_surface : R.id.md_surface);
    }

    private static boolean isShown(View view) {
        for (View current = view; current != null;) {
            if (current.getVisibility() != View.VISIBLE) {
                return false;
            }
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return true;
    }

    private static void hideBackgrounds(Size size, View view) {
        surface(size, view).setVisibility(View.INVISIBLE);
        if (!size.dial) {
            for (int panel : PANELS) {
                view.findViewById(panel).setVisibility(View.INVISIBLE);
            }
        }
    }

    private static Bitmap draw(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static void assertPixelsEqual(View expected, View actual) {
        Bitmap reference = draw(expected);
        Bitmap rendered = draw(actual);
        assertEquals(reference.getWidth(), rendered.getWidth());
        assertEquals(reference.getHeight(), rendered.getHeight());
        if (!reference.sameAs(rendered)) {
            for (int y = 0; y < reference.getHeight(); y++) {
                for (int x = 0; x < reference.getWidth(); x++) {
                    assertEquals("Widget pixels differ at " + x + "," + y,
                            reference.getPixel(x, y), rendered.getPixel(x, y));
                }
            }
        }
        reference.recycle();
        rendered.recycle();
    }

    private static final class Size {
        final int id;
        final int width;
        final int height;
        final boolean dial;

        Size(int id, int width, int height, boolean dial) {
            this.id = id;
            this.width = width;
            this.height = height;
            this.dial = dial;
        }

        String label() {
            return dial ? "2x1" : width < 250 ? "2x2" : "4x2";
        }
    }

    @Implements(SecureTokenStore.class)
    public static class SyntheticSignedOutStore {
        @Implementation
        public static boolean isSignedIn(Context context) {
            return false;
        }
    }

    /** The bundled shadow merges providers by category; retain the real per-provider contract. */
    @Implements(AppWidgetManager.class)
    public static class ProviderPreviewCache extends ShadowAppWidgetManager {
        final Map<ComponentName, Map<Integer, RemoteViews>> previews = new HashMap<>();

        @Implementation(minSdk = 35)
        protected boolean setWidgetPreview(ComponentName provider, int category, RemoteViews views) {
            previews.computeIfAbsent(provider, ignored -> new HashMap<>()).put(category, views);
            return true;
        }

        @Implementation(minSdk = 35)
        protected RemoteViews getWidgetPreview(ComponentName provider, UserHandle user, int category) {
            assertEquals(Process.myUserHandle(), user);
            Map<Integer, RemoteViews> categories = previews.get(provider);
            return categories == null ? null : categories.get(category);
        }

        @Implementation(minSdk = 35)
        protected void removeWidgetPreview(ComponentName provider, int category) {
            Map<Integer, RemoteViews> categories = previews.get(provider);
            if (categories != null) {
                categories.remove(category);
            }
        }
    }
}
