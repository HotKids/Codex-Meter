package dev.bennett.codexmeter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;

/** The compact One UI dial bitmap used by the four-dial one-row home widget. */
public final class WidgetGraphics {
    /** One UI's dial leaves a wide opening at the bottom rather than a 270-degree gauge. */
    private static final float ARC_START = 156f;
    private static final float ARC_SWEEP = 228f;
    private static final float MIN_SCALE = 0.8f;
    private static final float MAX_SCALE = 1.36f;

    private WidgetGraphics() {
    }

    /**
     * Draws one dial of the 4x1 widget: a 56px-radius arc filled to {@code value} percent, the
     * window icon in the middle and the value underneath. The bitmap is mdpi-sized so the host
     * scales it into the 58dp row like Samsung's own battery dials.
     */
    public static Bitmap compactDial(Context context, int value, int iconRes, int progressColor,
            int trackColor, int textColor, String valueText, float scale) {
        float factor = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        // A tight canvas keeps four-column widgets from shrinking the arc to fit padding.
        int width = Math.round(150f * factor);
        int height = Math.round(132f * factor);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(DisplayMetrics.DENSITY_MEDIUM);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(17f * factor);
        float centerX = width / 2f;
        float centerY = 64f * factor;
        float radius = 56f * factor;
        RectF arc = new RectF(centerX - radius, centerY - radius, centerX + radius,
                centerY + radius);
        paint.setColor(trackColor);
        canvas.drawArc(arc, ARC_START, ARC_SWEEP, false, paint);
        if (value >= 0) {
            paint.setColor(progressColor);
            canvas.drawArc(arc, ARC_START, ARC_SWEEP * clampPercent(value) / 100f, false,
                    paint);
        }

        if (context != null && iconRes != 0) {
            Drawable icon = context.getDrawable(iconRes);
            if (icon != null) {
                int iconSize = Math.round(52f * factor);
                int left = Math.round(centerX - iconSize / 2f);
                int top = Math.round(centerY - iconSize / 2f);
                icon.setBounds(left, top, left + iconSize, top + iconSize);
                icon.setTint(textColor);
                icon.draw(canvas);
            }
        }

        String text = valueText == null ? UsageCardFormat.MISSING : valueText;
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sec", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(textColor);
        paint.setTextSize((text.length() > 5 ? 24f : 33f) * factor);
        canvas.drawText(text, centerX, 121f * factor, paint);
        return bitmap;
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
