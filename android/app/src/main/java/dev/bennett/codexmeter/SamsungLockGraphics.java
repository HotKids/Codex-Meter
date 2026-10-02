package dev.bennett.codexmeter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.DisplayMetrics;

/** Battery-widget-inspired monochrome dials for Samsung lock and AOD surfaces. */
final class SamsungLockGraphics {
    private static final int TRACK = Color.argb(51, 0, 0, 0);
    private static final int FOREGROUND = Color.BLACK;

    // Requested sizes (dp) are clamped to what the lock screen can actually show.
    private static final int MIN_SIZE_DP = 44;
    private static final int MAX_SIZE_DP = 96;
    private static final int MAX_WIDE_WIDTH_DP = 200;
    private static final int DEFAULT_SIZE_DP = 56;
    private static final int DEFAULT_WIDE_WIDTH_DP = 124;

    // Supersample so the bitmap stays sharp when the host scales it.
    private static final float MIN_BITMAP_SCALE = 4.0f;
    private static final float MAX_BITMAP_SCALE = 4.5f;

    /** Unscaled footprint of one dial (20dp-radius ring, 6dp stroke) and its percentage. */
    private static final float DIAL_WIDTH = 46.0f;
    private static final float DIAL_HEIGHT = 56.0f;
    /** Width at which two dials render at full size. */
    private static final float DIAL_PAIR_WIDTH = 124.0f;
    private static final float DIAL_RADIUS = 20.0f;
    private static final float DIAL_STROKE = 6.0f;
    private static final float ARC_START_DEGREES = 156.43f;
    private static final float ARC_SWEEP_DEGREES = 227.14f;
    private static final float ARC_SWEEP_PER_PERCENT = ARC_SWEEP_DEGREES / 100.0f;

    private static final String SAMSUNG_FONT_FAMILY = "sec";
    private static final int SEMI_BOLD_WEIGHT = 600;
    private static final float SIGN_IN_TEXT_SIZE = 9.0f;

    private SamsungLockGraphics() {
    }

    /** Draws two dials side by side; signed-out widgets get a blank bitmap. */
    static Bitmap render(Context context, SamsungLockWidgetSupport.Shape shape, int primary,
            int secondary, boolean signedIn, int requestedWidth, int requestedHeight,
            int primaryIconRes, int secondaryIconRes) {
        boolean square = shape == SamsungLockWidgetSupport.Shape.SQUARE;
        int width = clamp(requestedWidth, MIN_SIZE_DP, square ? MAX_SIZE_DP : MAX_WIDE_WIDTH_DP,
                square ? DEFAULT_SIZE_DP : DEFAULT_WIDE_WIDTH_DP);
        int height = clamp(requestedHeight, MIN_SIZE_DP, MAX_SIZE_DP, DEFAULT_SIZE_DP);
        float scale = bitmapScale(context);
        Bitmap bitmap = createBitmap(width, height, scale);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(scale, scale);
        Paint paint = newPaint();
        if (!signedIn) {
            return bitmap;
        }
        float dialScale = Math.min(1.0f,
                Math.min(height / DIAL_HEIGHT, width / DIAL_PAIR_WIDTH));
        float top = (height - (DIAL_HEIGHT * dialScale)) / 2.0f;
        // Spread the two dials so the three gaps around them are equal.
        float gap = (width - (2.0f * DIAL_WIDTH * dialScale)) / 3.0f;
        Drawable primaryIcon = context == null ? null : context.getDrawable(primaryIconRes);
        Drawable secondaryIcon = context == null ? null : context.getDrawable(secondaryIconRes);
        drawDial(canvas, paint, gap + (0.5f * DIAL_WIDTH * dialScale), top, dialScale,
                primary, primaryIcon);
        drawDial(canvas, paint, (2.0f * gap) + (1.5f * DIAL_WIDTH * dialScale), top, dialScale,
                secondary, secondaryIcon);
        return bitmap;
    }

    /** Draws one centered dial, or a sign-in prompt when signed out. */
    static Bitmap renderSingle(Context context, SamsungLockWidgetSupport.Metric metric, int value,
            boolean signedIn, int requestedWidth, int requestedHeight) {
        int width = clamp(requestedWidth, MIN_SIZE_DP, MAX_SIZE_DP, DEFAULT_SIZE_DP);
        int height = clamp(requestedHeight, MIN_SIZE_DP, MAX_SIZE_DP, DEFAULT_SIZE_DP);
        float scale = bitmapScale(context);
        Bitmap bitmap = createBitmap(width, height, scale);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(scale, scale);
        Paint paint = newPaint();
        if (!signedIn) {
            drawSignIn(canvas, paint, width, height);
            return bitmap;
        }
        float dialScale = Math.min(1.0f, Math.min(height / DIAL_HEIGHT, width / DIAL_WIDTH));
        float top = (height - (DIAL_HEIGHT * dialScale)) / 2.0f;
        Drawable icon = context == null ? null : context.getDrawable(
                metric == SamsungLockWidgetSupport.Metric.FIVE_HOUR
                        ? R.drawable.ic_oui_time
                        : R.drawable.ic_oui_calendar_week);
        drawDial(canvas, paint, width / 2.0f, top, dialScale, value, icon);
        return bitmap;
    }

    private static float bitmapScale(Context context) {
        DisplayMetrics metrics = context == null
                ? null : context.getResources().getDisplayMetrics();
        float density = metrics == null || metrics.density <= 0.0f ? 1.0f : metrics.density;
        return Math.max(MIN_BITMAP_SCALE, Math.min(MAX_BITMAP_SCALE, density));
    }

    private static Bitmap createBitmap(int width, int height, float scale) {
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)), Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Math.round(DisplayMetrics.DENSITY_DEFAULT * scale));
        bitmap.eraseColor(Color.TRANSPARENT);
        return bitmap;
    }

    private static Paint newPaint() {
        return new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    }

    private static void drawSignIn(Canvas canvas, Paint paint, int width, int height) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(FOREGROUND);
        paint.setTypeface(semiBoldTypeface());
        paint.setTextSize(SIGN_IN_TEXT_SIZE);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = (height / 2.0f) - ((metrics.ascent + metrics.descent) / 2.0f);
        canvas.drawText("SIGN IN", width / 2.0f, baseline, paint);
    }

    /** Draws a dial whose bounding box is centered on {@code cx} and starts at {@code top}. */
    private static void drawDial(Canvas canvas, Paint paint, float cx, float top, float scale,
            int value, Drawable icon) {
        float graphicTop = top + (3.0f * scale);
        float cy = graphicTop + (23.0f * scale);
        float radius = DIAL_RADIUS * scale;
        RectF arc = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(DIAL_STROKE * scale);
        paint.setColor(TRACK);
        canvas.drawArc(arc, ARC_START_DEGREES, ARC_SWEEP_DEGREES, false, paint);
        if (value >= 0) {
            paint.setColor(FOREGROUND);
            canvas.drawArc(arc, ARC_START_DEGREES, clampPercent(value) * ARC_SWEEP_PER_PERCENT,
                    false, paint);
        }

        if (icon != null) {
            int size = Math.max(7, Math.round(20.0f * scale));
            int left = Math.round(cx - (size / 2.0f));
            int iconTop = Math.round(graphicTop + (12.0f * scale));
            icon.setBounds(left, iconTop, left + size, iconTop + size);
            icon.setTint(FOREGROUND);
            icon.draw(canvas);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(FOREGROUND);
        paint.setTypeface(semiBoldTypeface());
        paint.setTextSize(11.0f * scale);
        canvas.drawText(value < 0 ? "—" : clampPercent(value) + "%", cx,
                top + (48.0f * scale), paint);
    }

    /** Samsung's system font at weight 600 where supported, bold before Android P. */
    private static Typeface semiBoldTypeface() {
        return Build.VERSION.SDK_INT >= 28
                ? Typeface.create(Typeface.create(SAMSUNG_FONT_FAMILY, Typeface.NORMAL),
                        SEMI_BOLD_WEIGHT, false)
                : Typeface.create(SAMSUNG_FONT_FAMILY, Typeface.BOLD);
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    /** Clamps a requested size into range, using {@code fallback} when the host reports none. */
    private static int clamp(int value, int min, int max, int fallback) {
        return value <= 0 ? fallback : Math.max(min, Math.min(max, value));
    }
}
