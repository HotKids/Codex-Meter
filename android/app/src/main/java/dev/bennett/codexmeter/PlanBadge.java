package dev.bennett.codexmeter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import java.util.Locale;

/**
 * The plan pill at the top of a usage card: an OpenAI glyph and the upper-case plan label on a
 * left-to-right gradient capsule, following AI-Usage's Codex badge recipes. RemoteViews cannot
 * draw gradients, so the badge is rendered as a bitmap at the host density.
 */
final class PlanBadge {
    /** Badge metrics in design points: small cards use the compact size. */
    enum Size {
        SMALL(17f, 11f, 9f, 4f, 6f),
        MEDIUM(19f, 12f, 9.5f, 4.5f, 7f);

        final float height;
        final float logo;
        final float text;
        final float spacing;
        final float horizontalPadding;

        Size(float height, float logo, float text, float spacing, float horizontalPadding) {
            this.height = height;
            this.logo = logo;
            this.text = text;
            this.spacing = spacing;
            this.horizontalPadding = horizontalPadding;
        }
    }

    /** Gradient stops for light and dark appearance plus the label colour. */
    static final class Recipe {
        final int[] light;
        final int[] dark;
        final int foregroundLight;
        final int foregroundDark;

        Recipe(int[] light, int[] dark, int foregroundLight, int foregroundDark) {
            this.light = light;
            this.dark = dark;
            this.foregroundLight = foregroundLight;
            this.foregroundDark = foregroundDark;
        }

        Recipe(int[] light, int[] dark, int foreground) {
            this(light, dark, foreground, foreground);
        }
    }

    private static final Recipe PLUS = new Recipe(
            colors(0xFFF1F5F9, 0xFFE4E4E7, 0xFFCBD5E1),
            colors(0xFFD4D4D8, 0xFF94A3B8, 0xFF71717A), 0xFF1E293B);
    private static final Recipe PRO_LARGE = new Recipe(
            colors(0xFFFDE047, 0xFFF59E0B, 0xFFEA580C),
            colors(0xFFFDE047, 0xFFFBBF24, 0xFFF97316), Color.BLACK);
    private static final Recipe PRO_5X = new Recipe(
            colors(0xFFFBBF24, 0xFFEAB308, 0xFFFB923C),
            colors(0xFFFBBF24, 0xFFFACC15, 0xFFFB923C), Color.BLACK);
    private static final Recipe PRO = new Recipe(
            colors(0xFFFCD34D, 0xFFFACC15, 0xFFF59E0B),
            colors(0xFFFCD34D, 0xFFFACC15, 0xFFF59E0B), Color.BLACK);
    private static final Recipe TEAM = new Recipe(
            colors(0xFF8B5CF6, 0xFF4F46E5), colors(0xFFA78BFA, 0xFF6366F1), Color.WHITE);
    private static final Recipe PREMIUM = new Recipe(
            colors(0xFFD946EF, 0xFF9333EA), colors(0xFFE879F9, 0xFFA855F7), Color.WHITE);
    private static final Recipe BUSINESS = new Recipe(
            colors(0xFF334155, 0xFF0F172A), colors(0xFF64748B, 0xFF334155), Color.WHITE);
    private static final Recipe ENTERPRISE = new Recipe(
            colors(0xFF27272A, 0xFF0F172A, 0xFF000000),
            colors(0xFF52525B, 0xFF334155, 0xFF18181B), 0xFFFEF3C7);
    private static final Recipe FREE = new Recipe(
            colors(0xFFF1F5F9), colors(0x1AFFFFFF), 0xFF64748B, 0xA6FFFFFF);
    private static final Recipe OTHER = new Recipe(
            colors(0xFF94A3B8, 0xFF64748B), colors(0xFF64748B, 0xFF475569), Color.WHITE);

    private static Bitmap logo;

    private PlanBadge() {
    }

    /** Recipe for a display label such as "Plus", "Pro 10x" or "Team". */
    static Recipe recipeFor(String label) {
        String normalized = normalize(label);
        switch (normalized) {
            case "plus":
                return PLUS;
            case "pro-10x":
            case "pro-20x":
                return PRO_LARGE;
            case "pro-5x":
                return PRO_5X;
            case "pro":
                return PRO;
            case "team":
                return TEAM;
            case "premium":
                return PREMIUM;
            case "business":
                return BUSINESS;
            case "enterprise":
                return ENTERPRISE;
            case "free":
                return FREE;
            default:
                return OTHER;
        }
    }

    /** Badge text: the label in upper case, or CODEX when the plan is unknown. */
    static String text(String label) {
        String trimmed = label == null ? "" : label.trim();
        return trimmed.isEmpty() ? "CODEX" : trimmed.toUpperCase(Locale.ROOT);
    }

    static String normalize(String label) {
        if (label == null) {
            return "";
        }
        return label.trim().toLowerCase(Locale.ROOT).replace('×', 'x').replaceAll("[\\s_]+", "-");
    }

    /**
     * Draws the badge. In the clear style the capsule is omitted and the glyph and label take
     * {@code clearForeground}, as AI-Usage's minimal chrome does.
     */
    static Bitmap render(Context context, String label, Size size, float scale, float density,
            boolean dark, boolean clear, int clearForeground) {
        Recipe recipe = recipeFor(label);
        String text = text(label);
        float unit = scale * density;
        float height = size.height * unit;
        float logoSize = size.logo * unit;
        float padding = size.horizontalPadding * unit;
        float spacing = size.spacing * unit;

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(size.text * unit);
        float textWidth = textPaint.measureText(text);
        int width = Math.max(1, Math.round(padding * 2f + logoSize + spacing + textWidth));
        int heightPx = Math.max(1, Math.round(height));

        Bitmap bitmap = Bitmap.createBitmap(width, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int foreground;
        if (clear) {
            foreground = clearForeground;
        } else {
            int[] stops = dark ? recipe.dark : recipe.light;
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (stops.length == 1) {
                fill.setColor(stops[0]);
            } else {
                fill.setShader(new LinearGradient(0f, 0f, width, 0f, stops, null,
                        Shader.TileMode.CLAMP));
            }
            float radius = heightPx / 2f;
            canvas.drawRoundRect(new RectF(0f, 0f, width, heightPx), radius, radius, fill);
            foreground = dark ? recipe.foregroundDark : recipe.foregroundLight;
        }

        Bitmap glyph = logo(context);
        if (glyph != null) {
            Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            glyphPaint.setColorFilter(new PorterDuffColorFilter(foreground, PorterDuff.Mode.SRC_IN));
            float top = (heightPx - logoSize) / 2f;
            canvas.drawBitmap(glyph, null,
                    new RectF(padding, top, padding + logoSize, top + logoSize), glyphPaint);
        }
        textPaint.setColor(foreground);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = heightPx / 2f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(text, padding + logoSize + spacing, baseline, textPaint);
        return bitmap;
    }

    private static synchronized Bitmap logo(Context context) {
        if (logo == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            logo = BitmapFactory.decodeResource(context.getResources(), R.drawable.ai_usage_openai,
                    options);
        }
        return logo;
    }

    private static int[] colors(int... values) {
        return values;
    }
}
