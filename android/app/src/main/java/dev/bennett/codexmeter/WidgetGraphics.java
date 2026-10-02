package dev.bennett.codexmeter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;

/** Colours and the compact One UI dial bitmap used by the one-row home widgets. */
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

    public static int accentColor(Context context, String accent, boolean dark) {
        if (WidgetOptions.ACCENT_APP.equals(accent)) {
            return Ui.accent(context, dark);
        }
        return accentColor(accent, dark);
    }

    public static int accentColor(String accent, boolean dark) {
        if (WidgetOptions.ACCENT_BLUE.equals(accent)) {
            return dark ? Color.rgb(92, 169, 255) : Color.rgb(3, 129, 254);
        }
        if (WidgetOptions.ACCENT_AMBER.equals(accent)) {
            return Color.rgb(244, 185, 95);
        }
        if (WidgetOptions.ACCENT_VIOLET.equals(accent)) {
            return Color.rgb(155, 140, 255);
        }
        if (WidgetOptions.ACCENT_ROSE.equals(accent)) {
            return Color.rgb(255, 122, 162);
        }
        if (WidgetOptions.ACCENT_CYAN.equals(accent)) {
            return Color.rgb(71, 200, 232);
        }
        if (WidgetOptions.ACCENT_LIME.equals(accent)) {
            return Color.rgb(164, 214, 94);
        }
        if (WidgetOptions.ACCENT_MONO.equals(accent)) {
            return dark ? Color.rgb(244, 247, 248) : Color.rgb(32, 35, 38);
        }
        return dark ? Color.rgb(66, 214, 164) : Color.rgb(20, 168, 121);
    }

    public static int trackColor(boolean dark) {
        return dark ? Color.argb(72, 255, 255, 255) : Color.argb(48, 17, 19, 21);
    }

    public static int mainTextColor(boolean dark) {
        return dark ? Color.WHITE : Color.rgb(17, 19, 21);
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
