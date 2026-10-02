package dev.bennett.codexmeter;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Source behavior from AI-Usage's PlanBadge, WidgetProgress and transparent palette. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = QuotaCardsTest.TestApp.class, qualifiers = "zh-rCN-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReferenceWidgetStyleTest {
    private Context context;
    private UsageSnapshot snapshot;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        snapshot = new UsageSnapshot("plus", true, false,
                new UsageWindow(20, 18000, 0, (QuotaCardsTest.NOW + 7200000) / 1000),
                new UsageWindow(64, 604800, 0, QuotaCardsTest.RESET / 1000), QuotaCardsTest.NOW);
    }

    @Test public void clearRemovesCapsuleAndUsesMonochromeProgressWithAnEmptyTrack() {
        for (boolean dark : new boolean[]{false, true}) {
            Bitmap colorBadge = ReferenceWidgetGraphics.badge(context, "plus", dark, false, 1, "color");
            Bitmap clearBadge = ReferenceWidgetGraphics.badge(context, "plus", dark, false, 1, "clear");
            assertEquals(colorBadge.getWidth(), clearBadge.getWidth());
            assertEquals(colorBadge.getHeight(), clearBadge.getHeight());
            assertEquals(255, Color.alpha(colorBadge.getPixel(2, colorBadge.getHeight() / 2)));
            assertEquals(0, clearBadge.getPixel(2, clearBadge.getHeight() / 2));
            Bitmap color = ReferenceWidgetGraphics.progress(context, 100, 7, 36, dark, false, "color");
            Bitmap clear = ReferenceWidgetGraphics.progress(context, 100, 7, 36, dark, false, "clear");
            assertEquals(dark ? 0xffff9f0a : 0xffff9500, color.getPixel(20, 3));
            assertEquals(dark ? Color.WHITE : Color.BLACK, clear.getPixel(20, 3));
            assertEquals(dark ? 0xff55565c : 0xffc7c8cc, color.getPixel(75, 3));
            assertEquals(Color.TRANSPARENT, clear.getPixel(75, 3));
            assertTrue(Color.alpha(clear.getPixel(75, 0)) > 0);
        }
    }

    @Test public void transparentColorHasWhiteTrackAndBrightSeverityColorsInEitherTheme() {
        for (boolean dark : new boolean[]{false, true}) {
            for (int remaining : new int[]{80, 40, 15}) {
                Bitmap progress = ReferenceWidgetGraphics.progress(context, 100, 7, remaining,
                        dark, true, "color");
                assertEquals(remaining == 15 ? 0xffff453a : remaining == 40 ? 0xffff9f0a : 0xff30d158,
                        progress.getPixel(5, 3));
                assertEquals(0x29ffffff, progress.getPixel(90, 3));
            }
            // Clear deliberately keeps system label semantics, including in transparent mode.
            Bitmap clear = ReferenceWidgetGraphics.progress(context, 100, 7, 36, dark, true, "clear");
            assertEquals(dark ? Color.WHITE : Color.BLACK, clear.getPixel(20, 3));
            assertEquals(Color.TRANSPARENT, clear.getPixel(75, 3));
        }
    }

    @Test public void ordinaryReferenceBackgroundUsesOpaqueSystemColorsWithLegacyOpacity() {
        for (boolean dark : new boolean[]{false, true}) {
            View view = render(options(dark, 88, WidgetOptions.DISPLAY_REMAINING)
                    .withVisibleMeters("weekly"), snapshot, 136, 196);
            ImageView background = (ImageView) view.findViewById(R.id.quota_background);
            assertEquals(255, background.getImageAlpha());
            Bitmap backing = Bitmap.createBitmap(136, 196, Bitmap.Config.ARGB_8888);
            background.draw(new android.graphics.Canvas(backing));
            assertEquals(dark ? Color.BLACK : Color.WHITE, backing.getPixel(68, 98));
        }
    }

    @Test public void referenceAlwaysDisplaysRemainingWithPercentDespiteLegacyOptions() {
        WidgetOptions options = options(false, 100, WidgetOptions.DISPLAY_USED).withPercentSymbol(false);
        for (String style : new String[]{"color", "clear"}) {
            View small = render(options.withReferenceStyle(style).withVisibleMeters("weekly"), snapshot, 136, 196);
            assertEquals("36%", text(small, R.id.quota_hero_value).getText().toString());
            assertEquals(context.getString(R.string.card_remaining_label), text(small, R.id.quota_hero_caption).getText().toString());
            View dual = render(options.withReferenceStyle(style).withVisibleMeters("five_hour,weekly"), snapshot, 250, 158);
            assertEquals("80%", text(dual, R.id.quota_value_0).getText().toString());
            assertEquals("36%", text(dual, R.id.quota_value_1).getText().toString());
            Bitmap progress = ((BitmapDrawable) ((ImageView) dual.findViewById(R.id.quota_graphic_1)).getDrawable()).getBitmap();
            assertEquals(style.equals("clear") ? Color.BLACK : 0xffff9500, progress.getPixel(20, progress.getHeight() / 2));
        }
    }

    @Test public void transparencyUsesSourceBackingWhiteTextAndSuppressesBothWatermarks() {
        for (boolean dark : new boolean[]{false, true}) {
            for (String style : new String[]{"color", "clear"}) {
                View view = render(options(dark, 0, WidgetOptions.DISPLAY_REMAINING)
                        .withVisibleMeters("weekly").withReferenceStyle(style), snapshot, 136, 196);
                ImageView background = (ImageView) view.findViewById(R.id.quota_background);
                assertEquals(255, background.getImageAlpha());
                assertEquals(0x26000000, ((android.graphics.drawable.GradientDrawable)
                        background.getDrawable()).getColor().getDefaultColor());
                assertEquals(Color.WHITE, text(view, R.id.quota_hero_value).getCurrentTextColor());
                assertEquals(0xe0ffffff, text(view, R.id.quota_status).getCurrentTextColor());
                assertEquals(View.GONE, view.findViewById(R.id.quota_watermark).getVisibility());
                assertEquals(View.GONE, view.findViewById(R.id.quota_watermark_wide).getVisibility());
                Bitmap backing = Bitmap.createBitmap(136, 196, Bitmap.Config.ARGB_8888);
                background.draw(new android.graphics.Canvas(backing));
                assertEquals(0x26000000, backing.getPixel(68, 98));
            }
        }
    }

    @Test public void ordinaryStylesKeepWatermarkWithOpaqueSourceBackground() {
        for (String style : new String[]{"color", "clear"}) {
            WidgetOptions options = options(false, 72, WidgetOptions.DISPLAY_REMAINING).withReferenceStyle(style);
            View small = render(options.withVisibleMeters("weekly"), snapshot, 136, 196);
            assertEquals(View.VISIBLE, small.findViewById(R.id.quota_watermark).getVisibility());
            assertEquals(255, ((ImageView) small.findViewById(R.id.quota_background)).getImageAlpha());
            assertEquals(Color.BLACK, text(small, R.id.quota_hero_value).getCurrentTextColor());
            View medium = render(options, snapshot, 250, 158);
            assertEquals(View.VISIBLE, medium.findViewById(R.id.quota_watermark_wide).getVisibility());
            assertEquals(0, text(small, R.id.quota_hero_value).getShadowRadius(), .01f);
        }
    }

    @Test public void transparentShadowsFollowLayoutWithoutShadowingSingleMetadata() {
        WidgetOptions options = options(false, 0, WidgetOptions.DISPLAY_REMAINING);
        View small = render(options.withVisibleMeters("weekly"), snapshot, 136, 196);
        assertEquals(2, text(small, R.id.quota_title_0).getShadowRadius(), .01f);
        assertEquals(2, text(small, R.id.quota_hero_value).getShadowRadius(), .01f);
        assertEquals(0, text(small, R.id.quota_meta_value).getShadowRadius(), .01f);
        assertEquals(0, text(small, R.id.quota_status).getShadowRadius(), .01f);
        View mediumSingle = render(options.withVisibleMeters("weekly"), snapshot, 250, 158);
        assertEquals(2, text(mediumSingle, R.id.quota_status).getShadowRadius(), .01f);
        View mediumDual = render(options, snapshot, 250, 158);
        assertEquals(2, text(mediumDual, R.id.quota_detail_0).getShadowRadius(), .01f);
        assertEquals(0, text(mediumDual, R.id.quota_status).getShadowRadius(), .01f);
        UsageSnapshot multiple = QuotaCardsTest.multiWindowSnapshot();
        View stacked = render(options.withVisibleMeters(WidgetMeters.serialize(QuotaCardOptions.available(multiple))), multiple, 250, 158);
        assertEquals(2, text(stacked, R.id.quota_inline_detail_0).getShadowRadius(), .01f);
        assertEquals(2, text(stacked, R.id.quota_status).getShadowRadius(), .01f);
    }

    @Test public void referenceTimestampAndInvisibleOverlayHaveNoRefreshAction() {
        for (String style : new String[]{"color", "clear"}) {
            View view = render(options(false, 100, WidgetOptions.DISPLAY_REMAINING)
                    .withReferenceStyle(style), snapshot, 250, 158);
            assertFalse(view.findViewById(R.id.quota_status).hasOnClickListeners());
            assertFalse(view.findViewById(R.id.quota_status_label).hasOnClickListeners());
            assertFalse(view.findViewById(R.id.refresh_button).hasOnClickListeners());
            assertEquals(View.GONE, view.findViewById(R.id.quota_refresh_position).getVisibility());
            assertFalse(view.findViewById(R.id.quota_status).performClick());
            assertTrue(shadowOf((Application) context).getBroadcastIntents().stream()
                    .noneMatch(intent -> AppConstants.ACTION_REFRESH_WIDGET.equals(intent.getAction())));
            assertTrue(view.hasOnClickListeners());
        }
    }

    @Test public void twoByOneKeepsLegacyUsedModePercentPreferenceAndRefreshAction() {
        WidgetOptions options = options(false, 0, WidgetOptions.DISPLAY_USED).withPercentSymbol(false);
        for (String style : new String[]{"color", "clear"}) {
            View view = render(options.withReferenceStyle(style), snapshot, 180, 100);
            assertEquals("20", text(view, R.id.primary_samsung_value).getText().toString());
            assertEquals("64", text(view, R.id.secondary_samsung_value).getText().toString());
            assertEquals("Refresh", view.findViewById(R.id.refresh_button).getContentDescription().toString());
            assertTrue(view.findViewById(R.id.refresh_button).hasOnClickListeners());
            assertNull(view.findViewById(R.id.quota_badge));
        }
    }

    private WidgetOptions options(boolean dark, int opacity, String displayMode) {
        return new WidgetOptions(WidgetOptions.STYLE_CARDS, "auto", WidgetOptions.SURFACE_ONE_UI, "auto",
                dark ? WidgetOptions.THEME_DARK : WidgetOptions.THEME_LIGHT, WidgetOptions.ACCENT_BLUE,
                opacity, WidgetOptions.RESET_HIDDEN, displayMode, "both", false, false, false, false, false, false)
                .withVisibleMeters("five_hour,weekly");
    }

    private View render(WidgetOptions options, UsageSnapshot snapshot, int width, int height) {
        View view = QuotaCardRenderer.build(context, 42, options, width, height, snapshot,
                null, null, true, "", QuotaCardsTest.NOW).apply(context, new FrameLayout(context));
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        return view;
    }

    private static TextView text(View view, int id) {
        return (TextView) view.findViewById(id);
    }
}
