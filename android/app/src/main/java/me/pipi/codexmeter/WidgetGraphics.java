package me.pipi.codexmeter;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.util.DisplayMetrics;
import androidx.core.graphics.PathParser;

/** One UI arc masks used by the two-column home widget. */
public final class WidgetGraphics {
    private static final Path TWO_DIAL_ARC =
            PathParser.createPathFromPathData("M 14 93 A 60 60 0 1 1 124 93");
    private WidgetGraphics() {
    }

    /** Preserves upstream's 138px viewport and 18px stroke while trimming its exact SVG arc. */
    static Bitmap twoDialArc(int remainingPercent) {
        Bitmap mask = Bitmap.createBitmap(138, 138, Bitmap.Config.ALPHA_8);
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
        paint.setColor(android.graphics.Color.WHITE);
        new Canvas(mask).drawPath(visible, paint);
        return mask;
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
