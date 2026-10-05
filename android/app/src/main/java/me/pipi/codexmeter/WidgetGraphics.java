package me.pipi.codexmeter;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Shader;
import android.util.DisplayMetrics;
import android.widget.RemoteViews;
import androidx.core.graphics.PathParser;

/** One UI arc masks used by the two-column home widget. */
public final class WidgetGraphics {
    private static final Path TWO_DIAL_ARC =
            PathParser.createPathFromPathData("M 14 93 A 60 60 0 1 1 124 93");
    private WidgetGraphics() {
    }

    static void tintTrack(RemoteViews views, int viewId) {
        // Replace retained legacy filters; tint lists alone cannot clear ImageView's old filter.
        views.setColorInt(viewId, "setColorFilter", 0xFF000000, 0xFFFCFCFF);
        views.setColorInt(viewId, "setImageAlpha", 0x1A, 0x33);
    }

    /** Keeps the upstream arc and stroke, excluding empty pixels below its 93px + 9px end cap. */
    static Bitmap twoDialArc(int remainingPercent) {
        return twoDialArc(remainingPercent, false);
    }

    static Bitmap twoDialArc(int remainingPercent, boolean square) {
        return twoDialArc(remainingPercent, square, Color.WHITE, Color.WHITE, true);
    }

    static Bitmap classicDialArc(int remainingPercent, boolean square, int start, int end) {
        return twoDialArc(remainingPercent, square, start, end, false);
    }

    private static Bitmap twoDialArc(int remainingPercent, boolean square,
            int start, int end, boolean alphaOnly) {
        Bitmap mask = Bitmap.createBitmap(138, square ? 138 : 102,
                alphaOnly ? Bitmap.Config.ALPHA_8 : Bitmap.Config.ARGB_8888);
        mask.setDensity(DisplayMetrics.DENSITY_MEDIUM);
        int percent = clampPercent(remainingPercent);
        if (percent == 0) return mask;

        PathMeasure measure = new PathMeasure(TWO_DIAL_ARC, false);
        Path visible = new Path();
        measure.getSegment(0, measure.getLength() * percent / 100f, visible, true);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(18f);
        paint.setColor(start);
        if (start != end) {
            paint.setShader(new LinearGradient(14f, 93f, 69f, 33f,
                    start, end, Shader.TileMode.CLAMP));
        }
        new Canvas(mask).drawPath(visible, paint);
        return mask;
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
