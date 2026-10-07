package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.RotateDrawable;
import android.graphics.Color;
import android.os.Parcel;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RemoteViews;
import dev.bennett.codexmeter.WidgetMeters;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetRefreshFeedbackTest {
    @Test
    public void refreshGlyphAndRippleShareTheCompleteTouchTargetCenter() {
        assertCenteredRefresh(View.LAYOUT_DIRECTION_LTR);
    }

    @Test
    @Config(qualifiers = "ar-rEG-ldrtl-mdpi")
    public void rtlRefreshGlyphAndRippleKeepTheSameCenter() {
        assertCenteredRefresh(View.LAYOUT_DIRECTION_RTL);
    }

    private static void assertCenteredRefresh(int direction) {
        Context context = RuntimeEnvironment.getApplication();
        for (int[] size : new int[][] {{110, 130}, {372, 214}, {500, 300}}) {
            for (int opacity : new int[] {0, 100}) {
                FrameLayout host = new FrameLayout(context);
                host.setLayoutDirection(direction);
                View card = remote(context, WidgetOptions.COLOR_NATIVE, opacity, size[0], size[1], false)
                        .apply(context, host);
                host.addView(card);
                for (boolean failed : new boolean[] {false, true, false}) {
                    remote(context, WidgetOptions.COLOR_NATIVE, opacity, size[0], size[1], failed)
                            .reapply(context, card);
                    host.measure(View.MeasureSpec.makeMeasureSpec(size[0], View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(size[1], View.MeasureSpec.EXACTLY));
                    host.layout(0, 0, size[0], size[1]);
                    ImageView glyph = card.findViewById(R.id.md_refresh);
                    View target = card.findViewById(R.id.md_refresh_button);
                    android.graphics.Rect bounds = glyph.getDrawable().getBounds();
                    float[] center = {bounds.exactCenterX(), bounds.exactCenterY()};
                    glyph.getImageMatrix().mapPoints(center);
                    assertEquals("The visible glyph shares the target's horizontal center",
                            target.getLeft() + target.getWidth() / 2f,
                            positionIn(glyph, card, false) + glyph.getPaddingLeft() + center[0], 0.5f);
                    assertEquals("The visible glyph shares the target's vertical center",
                            target.getTop() + target.getHeight() / 2f,
                            positionIn(glyph, card, true) + glyph.getPaddingTop() + center[1], 0.5f);
                    assertTrue(target.getLeft() >= 0 && target.getRight() <= card.getWidth());
                    assertTrue(target.getTop() >= 0 && target.getBottom() <= card.getHeight());
                    target.draw(new android.graphics.Canvas(android.graphics.Bitmap.createBitmap(
                            target.getWidth(), target.getHeight(), android.graphics.Bitmap.Config.ARGB_8888)));
                    LayerDrawable feedback = (LayerDrawable) target.getForeground();
                    RippleDrawable rippleDrawable = (RippleDrawable) feedback.getDrawable(0);
                    android.graphics.Rect ripple = rippleDrawable.getBounds();
                    assertEquals(11, rippleDrawable.getRadius());
                    Drawable mask = rippleDrawable.findDrawableByLayerId(android.R.id.mask);
                    assertTrue(mask instanceof GradientDrawable);
                    assertEquals(GradientDrawable.OVAL, ((GradientDrawable) mask).getShape());
                    assertTrue("Feedback stays inside the compact target", ripple.top >= 0
                            && ripple.left >= 0 && ripple.right <= target.getWidth()
                            && ripple.bottom <= target.getHeight());
                    int rowTop = positionIn(card.findViewById(R.id.md_row_0), card, true);
                    assertTrue("The refresh target cannot intercept the first content row",
                            target.getBottom() <= rowTop);
                    assertTrue("The entire visible wave stays above the first content row",
                            target.getTop() + ripple.bottom <= rowTop);
                    assertEquals(target.getWidth() / 2f, ripple.exactCenterX(), 0.5f);
                    assertEquals(target.getHeight() / 2f, ripple.exactCenterY(), 0.5f);
                }
            }
        }
    }
    @Test
    @Config(sdk = {31, 35})
    public void frameworkReuseRejectsTheOldTreeThenAcceptsTheUpdatedControls() {
        Context context = RuntimeEnvironment.getApplication();
        for (int opacity : new int[] {0, 100}) {
            int layout = opacity == 0 ? R.layout.widget_material_shadow : R.layout.widget_material;
            View previous = new RemoteViews(context.getPackageName(), layout)
                    .apply(context, new FrameLayout(context));
            View oversized = new RemoteViews(context.getPackageName(), layout, android.R.id.background)
                    .apply(context, new FrameLayout(context));
            RemoteViews next = remote(context, WidgetOptions.COLOR_NATIVE, opacity);
            assertFalse("An existing host must reload the changed hierarchy",
                    canRecycle(next, previous));
            assertFalse("The oversized foreground also requires a fresh inflation",
                    canRecycle(next, oversized));
            View updated = next.apply(context, new FrameLayout(context));
            assertTouchFeedback(updated);
            RemoteViews repeat = remote(context, WidgetOptions.COLOR_NATIVE, opacity);
            assertTrue("Subsequent updates can reuse the corrected controls", canRecycle(repeat, updated));
            repeat.reapply(context, updated);
            assertTouchFeedback(updated);
        }
    }

    private static int positionIn(View view, View ancestor, boolean vertical) {
        int position = vertical ? view.getTop() : view.getLeft();
        for (View parent = (View) view.getParent(); parent != ancestor;
                parent = (View) parent.getParent()) {
            position += vertical ? parent.getTop() : parent.getLeft();
        }
        return position;
    }

    private static boolean canRecycle(RemoteViews remote, View view) {
        // Robolectric's host shadow always reinflates; use the framework's actual reuse predicate.
        return ReflectionHelpers.callInstanceMethod(remote, "canRecycleView",
                ClassParameter.from(View.class, view));
    }

    @Test
    public void refreshAreaAroundTheGlyphRefreshesWithoutOpeningTheApp() {
        Context context = RuntimeEnvironment.getApplication();
        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        for (String style : new String[] {WidgetOptions.COLOR_NATIVE, WidgetOptions.COLOR_CLASSIC}) {
            for (int opacity : new int[] {0, 100}) {
                for (int width : new int[] {170, 350}) {
                    View card = remote(context, style, opacity, width)
                            .apply(context, new FrameLayout(context));
                    host.setContentView(card, new ViewGroup.LayoutParams(width, 170));
                    card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(170, View.MeasureSpec.EXACTLY));
                    card.layout(0, 0, width, 170);
                    View target = card.findViewById(R.id.md_refresh_button);
                    for (int[] point : new int[][] {
                            {target.getLeft() + 2, target.getTop() + target.getHeight() / 2},
                            {target.getRight() - 2, target.getTop() + target.getHeight() / 2},
                            {target.getLeft() + target.getWidth() / 2, target.getTop() + 2},
                            {target.getLeft() + target.getWidth() / 2, target.getBottom() - 2}}) {
                        int before = Shadows.shadowOf((Application) context).getBroadcastIntents().size();
                        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN,
                                point[0], point[1], 0);
                        MotionEvent up = MotionEvent.obtain(0, 150, MotionEvent.ACTION_UP,
                                point[0], point[1], 0);
                        try {
                            card.dispatchTouchEvent(down);
                            assertTrue(card.findViewById(R.id.md_refresh_button).isPressed());
                            assertTrue(pressed(card.findViewById(R.id.md_refresh_button)));
                            card.dispatchTouchEvent(up);
                            org.robolectric.shadows.ShadowLooper.idleMainLooper();
                        } finally {
                            down.recycle();
                            up.recycle();
                        }
                        Application app = (Application) context;
                        assertNull("Touching the refresh area must not open the app",
                                Shadows.shadowOf(app).getNextStartedActivity());
                        java.util.List<Intent> broadcasts = Shadows.shadowOf(app).getBroadcastIntents();
                        assertEquals("Each touch must send refresh", before + 1, broadcasts.size());
                        assertEquals(AppConstants.ACTION_REFRESH_WIDGET,
                                broadcasts.get(broadcasts.size() - 1).getAction());
                    }
                }
            }
        }
    }

    @Test
    public void touchingContentBelowRefreshKeepsTheOriginalOpenAppAction() {
        Context context = RuntimeEnvironment.getApplication();
        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        for (int width : new int[] {110, 170, 350}) {
            for (int opacity : new int[] {0, 100}) {
                View card = remote(context, WidgetOptions.COLOR_NATIVE, opacity, width)
                        .apply(context, new FrameLayout(context));
                host.setContentView(card, new ViewGroup.LayoutParams(width, 170));
                card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(170, View.MeasureSpec.EXACTLY));
                card.layout(0, 0, width, 170);
                View target = card.findViewById(R.id.md_refresh_button);
                int x = target.getLeft() + target.getWidth() / 2;
                int y = positionIn(card.findViewById(R.id.md_row_0), card, true) + 2;
                Application app = (Application) context;
                int before = Shadows.shadowOf(app).getBroadcastIntents().size();
                MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0);
                MotionEvent up = MotionEvent.obtain(0, 100, MotionEvent.ACTION_UP, x, y, 0);
                try {
                    card.dispatchTouchEvent(down);
                    card.dispatchTouchEvent(up);
                    org.robolectric.shadows.ShadowLooper.idleMainLooper();
                } finally {
                    down.recycle();
                    up.recycle();
                }
                assertEquals("Content cannot be intercepted by refresh", before,
                        Shadows.shadowOf(app).getBroadcastIntents().size());
                assertEquals(MainActivity.class.getName(),
                        Shadows.shadowOf(app).getNextStartedActivity().getComponent().getClassName());
            }
        }
    }

    @Test
    public void refreshUsesNativePressedFeedbackThroughRemoteViewsApplyAndReapply() {
        Context context = RuntimeEnvironment.getApplication();
        for (String style : new String[] {WidgetOptions.COLOR_NATIVE, WidgetOptions.COLOR_CLASSIC}) {
            for (int opacity : new int[] {0, 100}) {
                RemoteViews remote = remote(context, style, opacity);
                View card = remote.apply(context, new FrameLayout(context));
                assertTouchFeedback(card);
                remote.reapply(context, card);
                assertTouchFeedback(card);
            }
        }
    }

    @Test
    @Config(qualifiers = "night-mdpi")
    public void darkRefreshKeepsTheSameNativeTouchFeedback() {
        refreshUsesNativePressedFeedbackThroughRemoteViewsApplyAndReapply();
    }

    @Test
    public void appThemedPreviewUsesTheSameFrameworkRippleAsTheLauncher() {
        Context app = RuntimeEnvironment.getApplication();
        for (int theme : new int[] {R.style.AppTheme, R.style.AppTheme_MaterialYou}) {
            Context context = new ContextThemeWrapper(app, theme);
            View card = remote(context, WidgetOptions.COLOR_NATIVE, 100)
                    .apply(context, new FrameLayout(context));
            assertTouchFeedback(card);
            View button = card.findViewById(R.id.md_refresh_button);
            android.util.TypedValue color = new android.util.TypedValue();
            assertTrue(button.getContext().getTheme().resolveAttribute(
                    android.R.attr.colorControlHighlight, color, true));
            int highlight = color.resourceId == 0 ? color.data
                    : button.getContext().getColor(color.resourceId);
            assertTrue(Color.alpha(highlight) > 0);
            boolean night = (context.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            assertEquals(night ? 255 : 0, Color.red(highlight));
        }
    }

    @Test
    @Config(qualifiers = "night-mdpi")
    public void darkAppThemedPreviewKeepsVisibleFrameworkFeedback() {
        appThemedPreviewUsesTheSameFrameworkRippleAsTheLauncher();
    }

    @Test
    @Config(sdk = {31, 35})
    public void busyRefreshUsesTheCurrentGlyphAndNativeFramesUntilTerminalState() {
        Context context = RuntimeEnvironment.getApplication();
        UsageCardState[] states = {
                new UsageCardState(true, UsageCardFixtures.plus(), null, "", UsageCardFixtures.NOW, true),
                new UsageCardState(true, null, null, "", UsageCardFixtures.NOW, true),
                new UsageCardState(true, UsageCardFixtures.plus(), null, "",
                        UsageCardFixtures.NOW + UsageCardState.STALE_AFTER_MS, true),
                new UsageCardState(true, UsageCardFixtures.plus(), null, "Refresh failed",
                        UsageCardFixtures.NOW, true)};
        try (ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity host = controller.get();
            for (int index = 0; index < states.length; index++) {
                UsageCardState state = states[index];
                RemoteViews busy = remote(context, WidgetOptions.COLOR_NATIVE, 100, 350, 170, state);
                View card = busy.apply(context, new FrameLayout(context));
                host.setContentView(card, new ViewGroup.LayoutParams(350, 170));
                layout(card, 350, 170);
                int icon = index == 0 ? R.drawable.ic_ms_sync : R.drawable.ic_ms_sync_problem;
                ProgressBar spinner = assertBusySpinner(card, icon);
                assertTrue("The production spinner attaches to the host", spinner.isAttachedToWindow());
                assertEquals("Native animation requires a visible host window", View.VISIBLE,
                        spinner.getWindowVisibility());
                assertTrue("The visible indeterminate ProgressBar begins native animation", spinner.isAnimating());
                Drawable rotation = spinner.getIndeterminateDrawable();
                int firstFrame = drawAnimationFrame(spinner, 0);
                int nextFrame = drawAnimationFrame(spinner, 125);
                assertTrue("Native ProgressBar drawing must advance the RotateDrawable level",
                        nextFrame > firstFrame);

                busy.reapply(context, card);
                layout(card, 350, 170);
                assertSame("Repeated busy updates reuse the stable spinner", spinner,
                        card.findViewById(R.id.md_refresh_spinner));
                assertSame("Reapply retains the native drawable and its animation", rotation,
                        spinner.getIndeterminateDrawable());
                assertEquals("Reapply must not restart the current frame", nextFrame, rotation.getLevel());
                assertTrue("The retained spinner keeps advancing after reapply",
                        drawAnimationFrame(spinner, 125) > nextFrame);

                View button = card.findViewById(R.id.md_refresh_button);
                Application app = (Application) context;
                int before = Shadows.shadowOf(app).getBroadcastIntents().size();
                touch(button, MotionEvent.ACTION_DOWN);
                touch(button, MotionEvent.ACTION_UP);
                org.robolectric.shadows.ShadowLooper.idleMainLooper();
                assertNull("The busy spinner cannot turn refresh into open-app",
                        Shadows.shadowOf(app).getNextStartedActivity());
                java.util.List<Intent> broadcasts = Shadows.shadowOf(app).getBroadcastIntents();
                assertEquals(before + 1, broadcasts.size());
                assertEquals(AppConstants.ACTION_REFRESH_WIDGET,
                        broadcasts.get(broadcasts.size() - 1).getAction());

                UsageCardState terminal = new UsageCardState(true, UsageCardFixtures.plus(), null,
                        state.refreshError, UsageCardFixtures.NOW, false);
                remote(context, WidgetOptions.COLOR_NATIVE, 100, 350, 170, terminal).reapply(context, card);
                assertNull("Success and failure both remove the busy child",
                        card.findViewById(R.id.md_refresh_spinner));
                assertFalse("Removing the spinner ends its host attachment", spinner.isAttachedToWindow());
                assertFalse(spinner.isAnimating());
                ImageView refresh = card.findViewById(R.id.md_refresh);
                assertEquals(View.VISIBLE, refresh.getVisibility());
                assertEquals(state.refreshError.isEmpty() ? R.drawable.ic_ms_sync
                                : R.drawable.ic_ms_sync_problem,
                        Shadows.shadowOf(refresh.getDrawable()).getCreatedFromResId());
            }
        }
    }

    @Test
    @Config(sdk = {31, 35})
    public void busyRefreshKeepsPaletteAndCenteredNonclickableSpinner() {
        Context context = RuntimeEnvironment.getApplication();
        UsageCardState state = new UsageCardState(true, UsageCardFixtures.plus(), null, "",
                UsageCardFixtures.NOW, true);
        for (String style : new String[] {WidgetOptions.COLOR_NATIVE, WidgetOptions.COLOR_CLASSIC}) {
            for (int opacity : new int[] {0, 100}) {
                for (int[] size : new int[][] {{110, 130}, {372, 214}}) {
                    FrameLayout host = new FrameLayout(context);
                    host.setLayoutDirection(context.getResources().getConfiguration().getLayoutDirection());
                    View card = remote(context, style, opacity, size[0], size[1], state).apply(context, host);
                    host.addView(card);
                    layout(host, size[0], size[1]);
                    ProgressBar spinner = assertBusySpinner(card, R.drawable.ic_ms_sync);
                    assertNotNull(spinner.getIndeterminateTintList());
                    assertEquals("The busy glyph uses the same widget text palette",
                            opacity == 0 ? Color.WHITE : context.getColor(R.color.widget_material_text),
                            spinner.getIndeterminateTintList().getDefaultColor());
                    View button = card.findViewById(R.id.md_refresh_button);
                    assertTrue("The busy control stays above content at narrow sizes",
                            button.getBottom() <= positionIn(card.findViewById(R.id.md_row_0), card, true));
                }
            }
        }
    }

    @Test
    @Config(sdk = {31, 35}, qualifiers = "night-mdpi")
    public void darkBusyRefreshKeepsPaletteAndCenteredNonclickableSpinner() {
        busyRefreshKeepsPaletteAndCenteredNonclickableSpinner();
    }

    @Test
    @Config(sdk = {31, 35}, qualifiers = "ar-rEG-ldrtl-mdpi")
    public void rtlBusyRefreshKeepsTheCenteredNonclickableSpinner() {
        busyRefreshKeepsPaletteAndCenteredNonclickableSpinner();
    }

    private static ProgressBar assertBusySpinner(View card, int icon) {
        assertEquals("Busy retains the static glyph's header space", View.INVISIBLE,
                card.findViewById(R.id.md_refresh).getVisibility());
        ProgressBar spinner = card.findViewById(R.id.md_refresh_spinner);
        assertNotNull("Production RemoteViews must include its native busy spinner", spinner);
        assertTrue(spinner.isIndeterminate());
        assertTrue(spinner.getIndeterminateDrawable() instanceof RotateDrawable);
        RotateDrawable rotation = (RotateDrawable) spinner.getIndeterminateDrawable();
        assertGlyphShape(spinner.getContext(), rotation.getDrawable(), icon);
        assertFalse("The spinner cannot intercept its parent's refresh action", spinner.isClickable());
        assertFalse(spinner.hasOnClickListeners());
        View button = card.findViewById(R.id.md_refresh_button);
        assertTrue(button.hasOnClickListeners());
        assertEquals(button.getWidth() / 2f,
                positionIn(spinner, button, false) + spinner.getWidth() / 2f, 0.5f);
        assertEquals(button.getHeight() / 2f,
                positionIn(spinner, button, true) + spinner.getHeight() / 2f, 0.5f);
        return spinner;
    }

    private static void assertGlyphShape(Context context, Drawable actual, int icon) {
        Drawable expected = context.getDrawable(icon);
        android.graphics.Bitmap rendered = drawablePixels(actual);
        android.graphics.Bitmap reference = drawablePixels(expected);
        try {
            int nontransparent = 0;
            for (int y = 0; y < rendered.getHeight(); y++) {
                for (int x = 0; x < rendered.getWidth(); x++) {
                    int alpha = Color.alpha(rendered.getPixel(x, y));
                    if (alpha > 0) nontransparent++;
                    assertEquals("Busy retains the current glyph's actual vector shape",
                            Color.alpha(reference.getPixel(x, y)), alpha);
                }
            }
            assertTrue("The native vector comparison must contain visible pixels", nontransparent > 0);
        } finally {
            rendered.recycle();
            reference.recycle();
        }
    }

    private static android.graphics.Bitmap drawablePixels(Drawable drawable) {
        android.graphics.Rect previous = new android.graphics.Rect(drawable.getBounds());
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                44, 44, android.graphics.Bitmap.Config.ARGB_8888);
        try {
            drawable.setBounds(0, 0, 44, 44);
            drawable.draw(new android.graphics.Canvas(bitmap));
        } finally {
            drawable.setBounds(previous);
        }
        return bitmap;
    }

    private static int drawAnimationFrame(ProgressBar spinner, long elapsedMillis) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(elapsedMillis));
        Object attachment = ReflectionHelpers.getField(spinner, "mAttachInfo");
        assertNotNull(attachment);
        // Robolectric does not render hardware frames; supply ViewRoot's clock to a real attached draw.
        ReflectionHelpers.setField(attachment, "mDrawingTime", SystemClock.uptimeMillis());
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                spinner.getWidth(), spinner.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        try {
            spinner.draw(new android.graphics.Canvas(bitmap));
            return spinner.getIndeterminateDrawable().getLevel();
        } finally {
            bitmap.recycle();
        }
    }

    private static void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static void assertTouchFeedback(View card) {
        card.measure(View.MeasureSpec.makeMeasureSpec(350, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(170, View.MeasureSpec.EXACTLY));
        card.layout(0, 0, 350, 170);
        View refresh = card.findViewById(R.id.md_refresh);
        View button = card.findViewById(R.id.md_refresh_button);
        assertTrue("The launcher must receive a native ripple background",
                containsRipple(button.getForeground()));
        assertTrue("The refresh PendingIntent belongs to the enlarged target", button.hasOnClickListeners());
        assertFalse(refresh.hasOnClickListeners());
        touch(button, MotionEvent.ACTION_DOWN);
        assertTrue(button.isPressed());
        assertTrue("The feedback belongs to the pressed target", pressed(button));
        touch(button, MotionEvent.ACTION_CANCEL);
        assertFalse(button.isPressed());
        assertFalse(pressed(button));
    }

    private static boolean pressed(View view) {
        for (int state : view.getDrawableState()) {
            if (state == android.R.attr.state_pressed) return true;
        }
        return false;
    }

    private static void touch(View view, int action) {
        MotionEvent event = MotionEvent.obtain(0, 0, action,
                view.getWidth() / 2f, view.getHeight() / 2f, 0);
        try {
            view.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private static boolean containsRipple(Drawable drawable) {
        if (drawable instanceof RippleDrawable) return true;
        if (drawable instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) drawable;
            for (int i = 0; i < layers.getNumberOfLayers(); i++) {
                if (containsRipple(layers.getDrawable(i))) return true;
            }
        }
        return false;
    }

    private static RemoteViews remote(Context context, String style, int opacity) {
        return remote(context, style, opacity, 350);
    }

    private static RemoteViews remote(Context context, String style, int opacity, int width) {
        return remote(context, style, opacity, width, 170, false);
    }

    private static RemoteViews remote(Context context, String style, int opacity, int width,
            int height, boolean failed) {
        return remote(context, style, opacity, width, height,
                new UsageCardState(true, UsageCardFixtures.plus(), null,
                        failed ? "Refresh failed" : "", 0L));
    }

    private static RemoteViews remote(Context context, String style, int opacity, int width,
            int height, UsageCardState state) {
        WidgetOptions options = new WidgetOptions(WidgetOptions.LAYOUT_AUTO,
                WidgetOptions.THEME_SYSTEM, WidgetOptions.ACCENT_APP, opacity,
                WidgetOptions.RESET_ABSOLUTE, WidgetOptions.DISPLAY_REMAINING).withColorStyle(style);
        RemoteViews remote = MaterialCardRenderer.build(context, 1, options,
                UsageCardFixtures.keys(WidgetMeters.WEEKLY, WidgetMeters.NEXT_RESET),
                state, width, height);
        Parcel parcel = Parcel.obtain();
        try {
            remote.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return RemoteViews.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }
}
