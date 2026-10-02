package dev.bennett.codexmeter;

import android.content.Context;
import android.graphics.*;
import java.util.Locale;

/** AI-Usage's badge and semantic usage colors, drawn at Android density. */
final class ReferenceWidgetGraphics {
    private ReferenceWidgetGraphics() { }

    static int usageColor(int remaining, boolean dark) {
        return usageColor(remaining, dark, false);
    }

    static int usageColor(int remaining, boolean dark, boolean transparent) {
        boolean bright = dark || transparent;
        return remaining <= 15 ? (bright ? 0xffff453a : 0xffff3b30)
                : remaining <= 40 ? (bright ? 0xffff9f0a : 0xffff9500)
                : (bright ? 0xff30d158 : 0xff34c759);
    }

    static int primaryColor(boolean dark, boolean transparent) {
        return dark || transparent ? Color.WHITE : Color.BLACK;
    }

    static int secondaryColor(boolean dark, boolean transparent) {
        return transparent ? 0xe0ffffff : dark ? 0x99ebebf5 : 0x993c3c43;
    }

    static Bitmap badge(Context context, String plan, boolean dark, boolean wide) {
        return badge(context, plan, dark, wide, 1);
    }

    static Bitmap badge(Context context, String plan, boolean dark, boolean wide, float scale) {
        return badge(context, plan, dark, wide, scale, "color");
    }

    static Bitmap badge(Context context, String plan, boolean dark, boolean wide, float scale,
            String referenceStyle) {
        boolean clear = "clear".equals(referenceStyle);
        String label = UsageFormat.planLabel(plan);
        if (label.isEmpty()) label = "CODEX";
        label = label.toUpperCase(Locale.ROOT);
        int[] colors;
        int ink = Color.WHITE;
        switch (label) {
            case "PRO 10X":
                colors = dark ? new int[]{0xfffde047,0xfffbbf24,0xfff97316}
                        : new int[]{0xfffde047,0xfff59e0b,0xffea580c}; ink = Color.BLACK; break;
            case "PRO 5X":
                colors = new int[]{0xfffbbf24,dark ? 0xfffacc15 : 0xffeab308,0xfffb923c}; ink = Color.BLACK; break;
            case "PLUS":
                colors = dark ? new int[]{0xffd4d4d8,0xff94a3b8,0xff71717a}
                        : new int[]{0xfff1f5f9,0xffe4e4e7,0xffcbd5e1}; ink = 0xff1e293b; break;
            case "TEAM": colors = dark ? new int[]{0xffa78bfa,0xff6366f1} : new int[]{0xff8b5cf6,0xff4f46e5}; break;
            case "PREMIUM": colors = dark ? new int[]{0xffe879f9,0xffa855f7} : new int[]{0xffd946ef,0xff9333ea}; break;
            case "BUSINESS": colors = dark ? new int[]{0xff64748b,0xff334155} : new int[]{0xff334155,0xff0f172a}; break;
            case "ENTERPRISE":
                colors = dark ? new int[]{0xff52525b,0xff334155,0xff18181b}
                        : new int[]{0xff27272a,0xff0f172a,0xff000000}; ink = 0xfffef3c7; break;
            case "FREE":
                colors = dark ? new int[]{0x1affffff,0x1affffff} : new int[]{0xfff1f5f9,0xfff1f5f9};
                ink = dark ? 0xa6ffffff : 0xff64748b; break;
            default: colors = dark ? new int[]{0xff64748b,0xff475569} : new int[]{0xff94a3b8,0xff64748b};
        }
        if (clear) ink = primaryColor(dark, false);
        float density = context.getResources().getDisplayMetrics().density * scale;
        float height = wide ? 19 : 17, icon = wide ? 12 : 11, padding = wide ? 7 : 6;
        float spacing = wide ? 4.5f : 4;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(wide ? 9.5f : 9);
        float width = padding * 2 + icon + spacing + paint.measureText(label);
        Bitmap bitmap = Bitmap.createBitmap((int)Math.ceil(width * density),
                (int)Math.ceil(height * density), Bitmap.Config.ARGB_8888);
        bitmap.setDensity(context.getResources().getDisplayMetrics().densityDpi);
        Canvas canvas = new Canvas(bitmap); canvas.scale(density, density);
        if (!clear) {
            paint.setShader(new LinearGradient(0, 0, width, 0, colors, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(0, 0, width, height, height / 2, height / 2, paint);
        }
        paint.setShader(null); paint.setColor(ink);
        Bitmap logo = BitmapFactory.decodeResource(context.getResources(), R.drawable.ai_usage_openai);
        paint.setColorFilter(new PorterDuffColorFilter(ink, PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(logo, null, new RectF(padding, (height-icon)/2, padding+icon, (height+icon)/2), paint);
        paint.setColorFilter(null);
        canvas.drawText(label, padding+icon+spacing, (height-paint.ascent()-paint.descent())/2, paint);
        return bitmap;
    }

    static Bitmap progress(Context context, float width, float height, int remaining,
            boolean dark, boolean transparent, String referenceStyle) {
        float density = context.getResources().getDisplayMetrics().density;
        int w = Math.max(1, Math.round(width * density));
        int h = Math.max(1, Math.round(height * density));
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(context.getResources().getDisplayMetrics().densityDpi);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float radius = h / 2f;
        boolean clear = "clear".equals(referenceStyle);
        if (!clear) {
            paint.setColor(transparent ? 0x29ffffff : dark ? 0xff55565c : 0xffc7c8cc);
            canvas.drawRoundRect(0, 0, w, h, radius, radius, paint);
        }
        if (clear || !transparent) {
            paint.setColor(clear ? secondaryColor(dark, false) : dark ? 0x1affffff : 0x12000000);
            paint.setStyle(Paint.Style.STROKE);
            float border = (clear ? 1 : .5f) * density;
            paint.setStrokeWidth(border);
            canvas.drawRoundRect(border / 2, border / 2, w - border / 2, h - border / 2,
                    radius, radius, paint);
        }
        if (remaining > 0) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(clear ? primaryColor(dark, false) : usageColor(remaining, dark, transparent));
            canvas.drawRoundRect(0, 0, Math.max(h, w * Math.min(100, remaining) / 100f), h,
                    radius, radius, paint);
        }
        return bitmap;
    }
}
