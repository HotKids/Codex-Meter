package me.pipi.codexmeter;

import dev.bennett.codexmeter.WidgetMeters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Parcel;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Header alignment follows the card geometry without moving its body. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "zh-rCN-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MaterialCardHeaderTest {
    private static final List<String> METERS = UsageCardFixtures.keys(
            WidgetMeters.WEEKLY, WidgetMeters.NEXT_RESET);
    @Test
    public void logoAlignsWithThePanelEdgeWhilePlanSpacingStaysUnchanged() {
        for (int width : new int[] {110, 140, 170, 250, 350, 430, 500}) {
            View applied = build(width, 200, false, 100);
            ImageView logo = applied.findViewById(R.id.md_logo);
            TextView title = applied.findViewById(R.id.md_title);
            float density = context().getResources().getDisplayMetrics().density;
            int panelLeft = leftIn(applied.findViewById(R.id.md_panel_0), applied);
            assertEquals("The logo shares the content panel's leading edge",
                    panelLeft, leftIn(logo, applied));
            assertEquals("The existing glyph's visible edge remains at the panel edge",
                    panelLeft,
                    leftIn(logo, applied) + firstOpaqueColumn(logo), 1);
            assertEquals("The logo and plan keep their original 8dp gap",
                    Math.round(8f * density), title.getLeft() - logo.getRight());
        }
    }

    @Test
    public void reapplyClearsExistingPositiveAndNegativeHeaderOffsets() {
        for (int[] size : new int[][] {{140, 200}, {500, 300}}) {
            for (int offset : new int[] {12, -3}) {
                RemoteViews stale = remote(size[0], size[1], false, 100);
                stale.setViewLayoutMargin(R.id.md_header, RemoteViews.MARGIN_START, offset,
                        TypedValue.COMPLEX_UNIT_DIP);
                View applied = apply(stale, size[0], size[1]);
                int bodyTop = topIn(applied.findViewById(R.id.md_row_0), applied);
                remote(size[0], size[1], false, 100).reapply(context(), applied);
                measure(applied, size[0], size[1]);
                View header = applied.findViewById(R.id.md_header);
                assertEquals(0,
                        ((ViewGroup.MarginLayoutParams) header.getLayoutParams()).getMarginStart());
                assertEquals(leftIn(applied.findViewById(R.id.md_panel_0), applied),
                        leftIn(applied.findViewById(R.id.md_logo), applied));
                assertEquals("Reapplying the alignment leaves the body top unchanged", bodyTop,
                        topIn(applied.findViewById(R.id.md_row_0), applied));
            }
        }
    }

    @Test
    public void sharedPickerUsesTheCurrentPanelAlignment() {
        for (int layout : new int[] {R.layout.widget_material_preview}) {
            for (int[] size : new int[][] {{140, 200}, {350, 170}}) {
                View applied = apply(new RemoteViews(context().getPackageName(), layout),
                        size[0], size[1]);
                assertEquals("The shared picker inherits the panel alignment",
                        leftIn(applied.findViewById(R.id.md_panel_0), applied),
                        leftIn(applied.findViewById(R.id.md_logo), applied));
                ImageView refresh = applied.findViewById(R.id.md_refresh);
                View panel = applied.findViewById(R.id.md_panel_0);
                assertEquals(0, refresh.getPaddingLeft() + refresh.getPaddingRight());
                assertEquals(leftIn(panel, applied) + panel.getWidth(),
                        leftIn(refresh, applied) + lastOpaqueColumn(refresh) + 1, 1);
                assertEquals(Math.round(8f * context().getResources().getDisplayMetrics().density),
                        applied.findViewById(R.id.md_title).getLeft()
                                - applied.findViewById(R.id.md_logo).getRight());
            }
        }
    }

    @Test
    public void refreshGlyphSharesThePanelEdgeAcrossSuccessfulAndFailedReapply() {
        for (int width : new int[] {140, 170, 350, 430}) {
            View applied = build(width, 200, false, 100);
            for (boolean failed : new boolean[] {false, true, false}) {
                remote(width, 200, failed, 100).reapply(context(), applied);
                measure(applied, width, 200);
                ImageView refresh = applied.findViewById(R.id.md_refresh);
                View rightPanel = applied.findViewById(R.id.md_panel_1);
                if (rightPanel.getVisibility() != View.VISIBLE) {
                    rightPanel = applied.findViewById(R.id.md_panel_0);
                }
                assertEquals("The visible refresh glyph shares the panel's trailing edge",
                        leftIn(rightPanel, applied) + rightPanel.getWidth(),
                        leftIn(refresh, applied) + lastOpaqueColumn(refresh) + 1, 1);
            }
        }
    }

    @Test
    @Config(qualifiers = "ar-rEG-ldrtl-xhdpi")
    public void rtlRefreshGlyphSharesTheLogicalPanelEnd() {
        for (int width : new int[] {140, 350}) {
            FrameLayout host = new FrameLayout(context());
            host.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            View applied = remote(width, 200, false, 100).apply(context(), host);
            host.addView(applied);
            for (boolean failed : new boolean[] {false, true, false}) {
                remote(width, 200, failed, 100).reapply(context(), applied);
                measure(applied, width, 200);
                ImageView refresh = applied.findViewById(R.id.md_refresh);
                assertEquals(View.LAYOUT_DIRECTION_RTL, refresh.getLayoutDirection());
                View endPanel = applied.findViewById(R.id.md_panel_1);
                if (endPanel.getVisibility() != View.VISIBLE) {
                    endPanel = applied.findViewById(R.id.md_panel_0);
                }
                assertEquals(leftIn(endPanel, applied),
                        leftIn(refresh, applied) + firstOpaqueColumn(refresh), 1);
            }
        }
    }

    @Test
    public void wideTimeAndStatusShareTheNativeTwelveSpMediumTypeface() {
        for (int width : new int[] {350, 430}) {
            for (boolean failed : new boolean[] {false, true}) {
                View applied = build(width, 200, failed, 100);
                float size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f,
                        context().getResources().getDisplayMetrics());
                for (int id : new int[] {R.id.md_status, R.id.md_updated}) {
                    assertEquals(size, ((TextView) applied.findViewById(id)).getTextSize(), 0f);
                }
                TextView visible = applied.findViewById(failed ? R.id.md_status : R.id.md_updated);
                assertEquals(500, renderedTypeface(visible).getWeight());
                assertEquals(View.VISIBLE, visible.getVisibility());
                assertEquals(0, visible.getLayout().getEllipsisCount(0));
                assertEquals(failed ? View.GONE : View.VISIBLE,
                        applied.findViewById(R.id.md_updated).getVisibility());
            }
        }
    }

    @Test
    public void failureMetadataHasTheSameFontAcrossCardWidths() {
        float size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f,
                context().getResources().getDisplayMetrics());
        for (int width : new int[] {140, 170, 190, 350, 430}) {
            View applied = build(width, 200, true, 100);
            TextView status = applied.findViewById(R.id.md_status);
            assertEquals(size, status.getTextSize(), 0f);
            if (status.getVisibility() == View.VISIBLE) {
                assertEquals(500, renderedTypeface(status).getWeight());
                assertEquals(0, status.getLayout().getEllipsisCount(0));
            }
        }
    }

    @Test
    public void metadataTypefaceSurvivesTheRemoteViewsParcelBoundary() {
        Parcel parcel = Parcel.obtain();
        try {
            remote(350, 200, true, 0).writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            View applied = apply(RemoteViews.CREATOR.createFromParcel(parcel), 350, 200);
            for (int id : new int[] {R.id.md_status, R.id.md_updated}) {
                TextView text = applied.findViewById(id);
                assertEquals(500, renderedTypeface(text).getWeight());
                assertTrue("The background-off variant keeps its header shadow",
                        text.getShadowRadius() > 0f);
                assertEquals(Color.WHITE, text.getCurrentTextColor());
            }
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void horizontalAlignmentAndMetadataLeaveTopHeightAndBodyPixelsUnchanged() {
        for (int[] size : new int[][] {{140, 200}, {350, 170}, {430, 200}, {500, 200}}) {
            for (int opacity : new int[] {0, 100}) {
                View applied = build(size[0], size[1], false, opacity);
                View baseline = previousHeader(applied, size[0], size[1], opacity);
                for (int id : new int[] {R.id.md_header, R.id.md_logo, R.id.md_row_0,
                        R.id.md_row_1, R.id.md_panel_0, R.id.md_panel_1, R.id.md_panel_2}) {
                    View before = baseline.findViewById(id);
                    View after = applied.findViewById(id);
                    assertEquals("Header/body tops remain unchanged", topIn(before, baseline),
                            topIn(after, applied));
                    assertEquals("Header/body heights remain unchanged", before.getHeight(),
                            after.getHeight());
                }
                View beforeHeader = baseline.findViewById(R.id.md_header);
                View afterHeader = applied.findViewById(R.id.md_header);
                assertEquals("The refresh-side header edge remains unchanged",
                        leftIn(beforeHeader, baseline) + beforeHeader.getWidth(),
                        leftIn(afterHeader, applied) + afterHeader.getWidth());
                int bodyTop = topIn(applied.findViewById(R.id.md_row_0), applied);
                Bitmap before = draw(baseline);
                Bitmap after = draw(applied);
                for (int y = bodyTop; y < after.getHeight(); y++) {
                    for (int x = 0; x < after.getWidth(); x++) {
                        assertEquals("Body pixels are protected", before.getPixel(x, y),
                                after.getPixel(x, y));
                    }
                }
            }
        }
    }

    @Test
    public void narrowNonFailureReapplyRestoresItsOriginalStatusTypography() {
        View applied = build(350, 200, true, 0);
        remote(140, 200, false, 0).reapply(context(), applied);
        measure(applied, 140, 200);
        TextView status = applied.findViewById(R.id.md_status);
        assertEquals(400, renderedTypeface(status).getWeight());
        TextView fresh = build(140, 200, false, 0).findViewById(R.id.md_status);
        assertEquals(fresh.getTextSize(), status.getTextSize(), 0f);
        assertTrue("Transparent header shadow remains present", status.getShadowRadius() > 0f);
    }

    @Test
    public void largerSystemFontsKeepFailureTextCompleteOrHiddenWithoutClippingTheErrorIcon() {
        RuntimeEnvironment.setFontScale(1.3f);
        try {
            for (int width : new int[] {110, 140, 170, 190, 249, 250, 350, 430}) {
                View applied = build(width, 200, true, 100);
                TextView status = applied.findViewById(R.id.md_status);
                if (status.getVisibility() == View.VISIBLE) {
                    assertEquals(context().getString(R.string.widget_card_refresh_failed),
                            status.getText().toString());
                    assertEquals(0, status.getLayout().getEllipsisCount(0));
                    assertEquals(500, renderedTypeface(status).getWeight());
                } else {
                    assertEquals(View.INVISIBLE, status.getVisibility());
                    assertEquals("", status.getText().toString());
                }
                ImageView refresh = applied.findViewById(R.id.md_refresh);
                View header = applied.findViewById(R.id.md_header);
                assertTrue(refresh.getLeft() >= 0 && refresh.getRight() <= header.getWidth());
                float expected = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f,
                        context().getResources().getDisplayMetrics());
                assertEquals(expected, status.getTextSize(), 0f);
                if (width >= 280) {
                    assertEquals(expected,
                            ((TextView) applied.findViewById(R.id.md_updated)).getTextSize(), 0f);
                }
            }
        } finally {
            RuntimeEnvironment.setFontScale(1f);
        }
    }

    @Test
    @Config(sdk = 30)
    public void oldHostsKeepTheOriginalHeaderStartWithoutAnUnsupportedMarginCall() {
        View applied = build(350, 200, false, 100);
        View header = applied.findViewById(R.id.md_header);
        assertEquals(0, ((ViewGroup.MarginLayoutParams) header.getLayoutParams()).getMarginStart());
    }

    @Test
    public void writesSourceBasedHeaderPreview() throws Exception {
        PreviewSheet sheet = new PreviewSheet("material-source-header");
        float density = context().getResources().getDisplayMetrics().density;
        for (int[] size : new int[][] {{140, 200}, {350, 170}, {430, 170}}) {
            for (boolean failed : new boolean[] {false, true}) {
                sheet.add("header " + size[0] + "x" + size[1] + (failed ? " failed" : " current"),
                        build(size[0], size[1], failed, 100),
                        Math.round(size[0] * density), Math.round(size[1] * density));
            }
        }
        assertTrue(sheet.writeSheet().isFile());
    }

    private static View previousHeader(View current, int width, int height, int opacity) {
        RemoteViews baseline = remote(width, height, false, opacity);
        baseline.setViewLayoutMargin(R.id.md_header, RemoteViews.MARGIN_START, 0,
                TypedValue.COMPLEX_UNIT_PX);
        float density = context().getResources().getDisplayMetrics().density;
        float textScale = ((TextView) current.findViewById(R.id.md_title)).getTextSize()
                / (15.5f * density);
        baseline.setTextViewTextSize(R.id.md_updated, TypedValue.COMPLEX_UNIT_DIP, 14f * textScale);
        baseline.setTextViewTextSize(R.id.md_status, TypedValue.COMPLEX_UNIT_DIP,
                (width >= 280 ? 14f : 10.5f) * textScale);
        for (int id : new int[] {R.id.md_status, R.id.md_updated}) {
            baseline.setTextViewText(id, ((TextView) current.findViewById(id)).getText().toString());
        }
        return apply(baseline, width, height);
    }

    private static Typeface renderedTypeface(TextView text) {
        TextPaint paint = new TextPaint(text.getPaint());
        if (text.getText() instanceof Spanned) {
            Spanned value = (Spanned) text.getText();
            for (TypefaceSpan span : value.getSpans(0, value.length(), TypefaceSpan.class)) {
                span.updateMeasureState(paint);
            }
        }
        return paint.getTypeface();
    }

    private static int firstOpaqueColumn(ImageView logo) {
        Bitmap bitmap = draw(logo);
        for (int x = 0; x < bitmap.getWidth(); x++) {
            for (int y = 0; y < bitmap.getHeight(); y++) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 127) {
                    return x;
                }
            }
        }
        throw new AssertionError("The existing Codex logo must be visible");
    }

    private static int lastOpaqueColumn(ImageView image) {
        Bitmap bitmap = draw(image);
        for (int x = bitmap.getWidth() - 1; x >= 0; x--) {
            for (int y = 0; y < bitmap.getHeight(); y++) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 127) {
                    return x;
                }
            }
        }
        throw new AssertionError("The refresh glyph must be visible");
    }

    private static Bitmap draw(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static int leftIn(View child, View ancestor) {
        int left = child.getLeft();
        for (View parent = (View) child.getParent(); parent != ancestor;
                parent = (View) parent.getParent()) {
            left += parent.getLeft();
        }
        return left;
    }

    private static int topIn(View child, View ancestor) {
        int top = child.getTop();
        for (View parent = (View) child.getParent(); parent != ancestor;
                parent = (View) parent.getParent()) {
            top += parent.getTop();
        }
        return top;
    }

    private static Context context() {
        return RuntimeEnvironment.getApplication();
    }

    private static RemoteViews remote(int width, int height, boolean failed, int opacity) {
        UsageCardState state = new UsageCardState(true,
                UsageCardFixtures.snapshot("pro200", 19, 23, false), null,
                failed ? "Synthetic network failure" : "", UsageCardFixtures.NOW);
        WidgetOptions options = new WidgetOptions("auto", "system", "app", opacity,
                "hidden", "remaining");
        return MaterialCardRenderer.build(context(), 73, options,
                METERS, state, width, height);
    }

    private static View build(int width, int height, boolean failed, int opacity) {
        return apply(remote(width, height, failed, opacity), width, height);
    }

    private static View apply(RemoteViews remote, int width, int height) {
        View applied = remote.apply(context(), new FrameLayout(context()));
        measure(applied, width, height);
        return applied;
    }

    private static void measure(View view, int width, int height) {
        float density = context().getResources().getDisplayMetrics().density;
        int widthPx = Math.round(width * density);
        int heightPx = Math.round(height * density);
        view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, widthPx, heightPx);
    }
}
