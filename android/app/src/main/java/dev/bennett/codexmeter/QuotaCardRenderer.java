package dev.bennett.codexmeter;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import java.util.*;

/** AI-Usage information hierarchy using native text; only the 2×1 host keeps its original arcs. */
final class QuotaCardRenderer {
    private static final int[] CARDS = {R.id.quota_card_0, R.id.quota_card_1, R.id.quota_card_2, R.id.quota_card_3};
    private static final int[] TITLES = {R.id.quota_title_0, R.id.quota_title_1, R.id.quota_title_2, R.id.quota_title_3};
    private static final int[] VALUES = {R.id.quota_value_0, R.id.quota_value_1, R.id.quota_value_2, R.id.quota_value_3};
    private static final int[] DETAILS = {R.id.quota_detail_0, R.id.quota_detail_1, R.id.quota_detail_2, R.id.quota_detail_3};
    private static final int[] GRAPHICS = {R.id.quota_graphic_0, R.id.quota_graphic_1, R.id.quota_graphic_2, R.id.quota_graphic_3};
    private static final int[] TITLE_POS = {R.id.quota_title_position_0, R.id.quota_title_position_1, R.id.quota_title_position_2, R.id.quota_title_position_3};
    private static final int[] VALUE_POS = {R.id.quota_value_position_0, R.id.quota_value_position_1, R.id.quota_value_position_2, R.id.quota_value_position_3};
    private static final int[] GRAPHIC_POS = {R.id.quota_graphic_position_0, R.id.quota_graphic_position_1, R.id.quota_graphic_position_2, R.id.quota_graphic_position_3};
    private static final int[] INLINE_POS = {R.id.quota_inline_position_0, R.id.quota_inline_position_1, R.id.quota_inline_position_2, R.id.quota_inline_position_3};
    private static final int[] INLINE_TITLES = {R.id.quota_inline_title_0, R.id.quota_inline_title_1, R.id.quota_inline_title_2, R.id.quota_inline_title_3};
    private static final int[] INLINE_DETAILS = {R.id.quota_inline_detail_0, R.id.quota_inline_detail_1, R.id.quota_inline_detail_2, R.id.quota_inline_detail_3};
    private static final int[] INLINE_VALUES = {R.id.quota_inline_value_0, R.id.quota_inline_value_1, R.id.quota_inline_value_2, R.id.quota_inline_value_3};
    private QuotaCardRenderer() { }

    static boolean usesDials(int width, int height) {
        // Launchers report dp rather than cell spans. A single short, two-column host is 2×1.
        return height < 130 && width < 240 && width >= height * 1.2f;
    }

    static boolean usesDials(Bundle host, int width, int height) {
        int rows = host == null ? 0 : host.getInt("semAppWidgetRowSpan", 0);
        int columns = host == null ? 0 : host.getInt("semAppWidgetColumnSpan", 0);
        return rows > 0 && columns > 0 ? rows == 1 && columns == 2 : usesDials(width, height);
    }

    static RemoteViews buildForHost(Context c, int id, WidgetOptions options, int width, int height,
            QuotaCardState state, Bundle host) {
        return build(c, id, options, width, height, state.widgetSnapshot, state.snapshot, state.credits,
                state.signedIn, state.error, state.now, usesDials(host, width, height));
    }

    static List<SizeF> responsiveSizes(Bundle hostOptions) {
        List<SizeF> sizes = new ArrayList<>();
        List<SizeF> reported = hostOptions == null || Build.VERSION.SDK_INT < 31 ? null
                : hostOptions.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
        if (reported != null) for (SizeF size : reported) {
            if (size != null && Float.isFinite(size.getWidth()) && Float.isFinite(size.getHeight())
                    && size.getWidth() >= 1 && size.getHeight() >= 1 && !sizes.contains(size)) {
                sizes.add(size); if (sizes.size() == 16) break;
            }
        }
        if (!sizes.isEmpty()) return sizes;
        for (int[] size : new int[][]{{90,60}, {180,100}, {90,130}, {136,158}, {136,196},
                {180,220}, {240,60}, {250,100}, {250,158}, {300,260}, {330,360}})
            sizes.add(new SizeF(size[0], size[1]));
        return sizes;
    }

    static RemoteViews build(Context context, int id, WidgetOptions options, int width, int height) {
        return build(context, id, options, width, height, QuotaCardState.load(context));
    }
    static RemoteViews build(Context context, int id, WidgetOptions options, int width, int height, QuotaCardState state) {
        return build(context, id, options, width, height, state.widgetSnapshot, state.snapshot, state.credits,
                state.signedIn, state.error, state.now, usesDials(width, height));
    }
    static RemoteViews build(Context c, int id, WidgetOptions options, int width, int height,
            UsageSnapshot snapshot, ResetCreditsSnapshot credits, UsageHistory history,
            boolean signedIn, String error, long now) {
        return build(c, id, options, width, height, WidgetUsageSnapshot.fromLegacy(snapshot), snapshot,
                credits, signedIn, error, now, usesDials(width, height));
    }
    private static RemoteViews build(Context c, int id, WidgetOptions options, int width, int height,
            WidgetUsageSnapshot snapshot, UsageSnapshot legacy, ResetCreditsSnapshot credits,
            boolean signedIn, String error, long now, boolean useDials) {
        boolean dark = WidgetOptions.THEME_DARK.equals(options.theme)
                || (WidgetOptions.THEME_SYSTEM.equals(options.theme)
                    && (c.getResources().getConfiguration().uiMode & 48) == 32);
        if (!signedIn) { snapshot = null; legacy = null; credits = null; }
        List<Window> windows = new ArrayList<>();
        List<String> effective = useDials && snapshot == null
                ? QuotaCardOptions.resolve(options.visibleMeters)
                : QuotaCardOptions.effectiveWindows(options.visibleMeters, snapshot, legacy);
        for (String key : effective)
            windows.add(new Window(c, key, snapshot, legacy, credits, options, useDials, now));
        if (windows.isEmpty()) windows.add(new Window(c, "", snapshot, legacy, credits, options, useDials, now));
        if (useDials) return upstreamDials(c, id, options, windows, dark);

        boolean transparent = options.opacity == 0;
        int count = Math.min(width >= 240 ? 4 : 2, windows.size());
        // Reset credits are an explicit, ordered slot. An automatic row would undo deselection
        // and repeat the same helper when it is selected.
        boolean optional = false;
        ReferenceWidgetLayout g = new ReferenceWidgetLayout(width, height, count, optional);
        int ink = ReferenceWidgetGraphics.primaryColor(dark, transparent);
        int secondary = ReferenceWidgetGraphics.secondaryColor(dark, transparent);
        int layout = !transparent ? R.layout.widget_quota_cards
                : g.wide && (g.single || g.stacked) ? R.layout.widget_quota_cards_transparent_header_shadow
                : R.layout.widget_quota_cards_transparent;
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);
        if (transparent) {
            // Keep alpha in the solid color: ImageView alpha modulation of an opaque
            // GradientDrawable would round 38 down to 37 in the actual rendered pixel.
            v.setImageViewResource(R.id.quota_background, R.drawable.quota_transparent_surface);
            v.setInt(R.id.quota_background, "setImageAlpha", 255);
        } else {
            v.setInt(R.id.quota_background, "setColorFilter", dark ? Color.BLACK : Color.WHITE);
            v.setInt(R.id.quota_background, "setImageAlpha", 255);
        }
        int watermarkId = g.wide ? R.id.quota_watermark_wide : R.id.quota_watermark;
        v.setImageViewBitmap(watermarkId, watermark(c, width, height, g.wide, g.scale, dark));
        v.setViewVisibility(R.id.quota_watermark, !g.wide && options.opacity > 0 ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.quota_watermark_wide, g.wide && options.opacity > 0 ? View.VISIBLE : View.GONE);
        Bitmap badge = ReferenceWidgetGraphics.badge(c, snapshot == null ? "" : snapshot.planType,
                dark, g.wide, g.scale, options.referenceStyle);
        v.setImageViewBitmap(R.id.quota_badge, badge);
        v.setContentDescription(R.id.quota_badge, snapshot == null ? "Codex" : UsageFormat.planLabel(snapshot.planType));
        position(c, v, R.id.quota_badge_position, g.padding, g.badge, g.padding);

        String fetched = snapshot == null || snapshot.fetchedAtMillis <= 0 ? "—"
                : c.getString(R.string.ref_updated_at, new java.text.SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(snapshot.fetchedAtMillis)));
        String issue = !signedIn ? c.getString(R.string.card_signed_out)
                : error != null && !error.isEmpty() ? c.getString(R.string.card_refresh_failed)
                : snapshot == null ? c.getString(R.string.card_waiting_update) : "";
        String status = !g.wide && !issue.isEmpty()
                ? error != null && !error.isEmpty() ? c.getString(R.string.reference_refresh_error) : issue : fetched;
        v.setTextViewText(R.id.quota_status, status);
        v.setContentDescription(R.id.quota_status, issue.isEmpty() ? fetched : issue);
        v.setTextColor(R.id.quota_status, secondary);
        v.setTextColor(R.id.quota_status_label, secondary);
        v.setViewVisibility(R.id.quota_status_label, g.wide ? View.GONE : View.VISIBLE);
        float statusLeft = g.wide ? g.padding + badge.getWidth() / c.getResources().getDisplayMetrics().density + 8 * g.scale : g.padding;
        position(c, v, R.id.quota_status_position, statusLeft, g.fetched, g.padding);
        fitPair(c, v, R.id.quota_status_label, R.id.quota_status,
                g.wide ? "" : c.getString(R.string.ref_refresh_time), status, (g.stacked ? 9 : 10) * g.scale,
                width - statusLeft - g.padding, secondary, secondary);
        v.setTextViewText(R.id.quota_error, issue);
        v.setTextColor(R.id.quota_error, ReferenceWidgetGraphics.usageColor(40, dark, transparent));
        textSize(v, R.id.quota_error, 8 * g.scale);
        v.setViewVisibility(R.id.quota_error, g.wide && !issue.isEmpty() ? View.VISIBLE : View.GONE);
        position(c, v, R.id.quota_error_position, g.padding, height - 12 * g.scale, g.padding);

        for (int i = 0; i < 4; i++) {
            v.setViewVisibility(CARDS[i], i < count ? View.VISIBLE : View.GONE);
            if (i >= count) continue;
            Window window = windows.get(i);
            float top = g.rowTop(i);
            v.setViewVisibility(TITLE_POS[i], g.stacked ? View.GONE : View.VISIBLE);
            v.setViewVisibility(VALUE_POS[i], g.single || g.stacked ? View.GONE : View.VISIBLE);
            v.setViewVisibility(INLINE_POS[i], g.stacked ? View.VISIBLE : View.GONE);
            position(c, v, TITLE_POS[i], g.padding, top, g.padding);
            position(c, v, VALUE_POS[i], g.padding, top + (g.wide ? 20 : 18) * g.scale, g.padding);
            position(c, v, INLINE_POS[i], g.padding, top, g.padding);
            float graphicTop = g.single ? g.progress : top + (g.stacked ? 19 : g.wide ? 40 : 35) * g.scale;
            position(c, v, GRAPHIC_POS[i], g.padding, graphicTop, g.padding);
            float contentWidth = width - 2 * g.padding;
            String relative = window.usage ? resetText(c, window.resetAt, now, true) : window.detail;
            setText(v, TITLES[i], window.title, ink, g.titleSize);
            setText(v, VALUES[i], window.value, ink, (g.wide ? 14 : 12) * g.scale);
            float valueWidth = measure(c, window.value, (g.wide ? 14 : 12) * g.scale, true);
            setText(v, DETAILS[i], relative, secondary, fitted(c, relative, g.detailSize, contentWidth - valueWidth - 4 * g.scale, false));
            if (g.stacked) {
                setText(v, INLINE_VALUES[i], window.value, ink, 14 * g.scale);
                setText(v, INLINE_DETAILS[i], relative, secondary, 10 * g.scale);
                float rightWidth = measure(c, window.value, 14 * g.scale, true) + measure(c, relative, 10 * g.scale, false) + 11 * g.scale;
                float titleWidth = Math.max(32 * g.scale, contentWidth - rightWidth);
                setText(v, INLINE_TITLES[i], window.title, ink, fitted(c, window.title, 13 * g.scale, titleWidth, true));
                // Long model names yield room to their actual quota/reset values.
                v.setInt(INLINE_DETAILS[i], "setMaxWidth", dp(c, Math.max(0, contentWidth * .48f)));
            }
            v.setImageViewBitmap(GRAPHICS[i], ReferenceWidgetGraphics.progress(c, contentWidth,
                    g.progressHeight, window.progress, dark, transparent, options.referenceStyle));
            v.setContentDescription(CARDS[i], c.getString(R.string.card_data_description, window.title, window.value, relative));
        }
        v.setViewVisibility(R.id.quota_hero_position, g.single ? View.VISIBLE : View.GONE);
        Window first = windows.get(0);
        v.setViewVisibility(R.id.quota_meta_position,
                g.single && (first.usage || !first.detail.isEmpty()) ? View.VISIBLE : View.GONE);
        if (g.single) {
            Window window = windows.get(0);
            position(c, v, R.id.quota_hero_position, g.padding, g.value, g.padding);
            String caption = window.usage ? c.getString(R.string.card_remaining_label) : "";
            float captionSize = (g.wide ? 16 : 12) * g.scale;
            float heroSpace = width - 2 * g.padding - measure(c, caption, captionSize, true) - 8 * g.scale;
            setText(v, R.id.quota_hero_value, window.value, ink, fitted(c, window.value, g.valueSize, heroSpace, true));
            setText(v, R.id.quota_hero_caption, caption, secondary, captionSize);
            v.setViewVisibility(R.id.quota_hero_caption, window.usage ? View.VISIBLE : View.GONE);
            v.setViewVisibility(R.id.quota_hero_spacer, g.wide ? View.GONE : View.VISIBLE);
            position(c, v, R.id.quota_meta_position, g.padding, g.reset, g.padding);
            fitPair(c, v, R.id.quota_meta_label, R.id.quota_meta_value,
                    window.usage ? c.getString(R.string.ref_reset_time) : "",
                    window.usage ? resetText(c, window.resetAt, now, false) : window.detail,
                    g.metaSize, width - 2 * g.padding, secondary, ink);
        }
        v.setViewVisibility(R.id.quota_optional_position, View.GONE);
        bindActions(c, id, v, true);
        v.setViewVisibility(R.id.quota_refresh_position, View.GONE);
        return v;
    }

    /** The upstream 2x1 RemoteViews, metrics and fixed typography; no translated copy. */
    private static RemoteViews upstreamDials(Context c, int id, WidgetOptions options, List<Window> windows, boolean dark) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_rings);
        if ("samsung".equalsIgnoreCase(Build.MANUFACTURER) || "samsung".equalsIgnoreCase(Build.BRAND)) {
            int alpha = Math.round(options.opacity * 2.55f);
            v.setInt(android.R.id.background, "setBackgroundColor", dark ? Color.argb(alpha,0,0,0) : Color.argb(alpha,255,255,255));
        } else {
            v.setInt(android.R.id.background, "setBackgroundResource", WidgetRenderer.backgroundResource(c, dark, options.opacity, options.surfaceStyle));
        }
        int[] sections = {R.id.primary_section, R.id.secondary_section};
        int[] values = {R.id.primary_samsung_value, R.id.secondary_samsung_value};
        int[] icons = {R.id.primary_samsung_icon, R.id.secondary_samsung_icon};
        int[] bars = {R.id.primary_samsung_progress, R.id.secondary_samsung_progress};
        for (int i = 0; i < 2; i++) {
            v.setViewVisibility(sections[i], i < windows.size() ? View.VISIBLE : View.GONE);
            if (i >= windows.size()) continue;
            Window window = windows.get(i);
            v.setTextViewText(values[i], window.value);
            v.setTextColor(values[i], WidgetGraphics.mainTextColor(dark));
            v.setImageViewResource(icons[i], window.icon);
            v.setInt(icons[i], "setColorFilter", WidgetGraphics.mainTextColor(dark));
            String label = window.dialLabel;
            v.setContentDescription(icons[i], label);
            v.setContentDescription(sections[i], label + ", " + window.value);
            v.setProgressBar(bars[i], 100, Math.max(0, window.progress), false);
            WidgetRenderer.applyProgressColors(v, bars[i], WidgetGraphics.accentColor(c, options.accent, dark), WidgetGraphics.trackColor(dark));
        }
        v.setContentDescription(R.id.refresh_button, "Refresh");
        bindActions(c, id, v, false);
        return v;
    }

    private static void bindActions(Context c, int id, RemoteViews v, boolean reference) {
        Uri base = Uri.parse("pipi-usage://widget/" + id);
        Intent open = new Intent(c, MainActivity.class).setData(base.buildUpon().appendPath("open").build())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(android.R.id.background, PendingIntent.getActivity(c, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        if (reference) return;
        Intent refresh = new Intent(c, WidgetRefreshReceiver.class).setAction(AppConstants.ACTION_REFRESH_WIDGET)
                .setData(base.buildUpon().appendPath("refresh").build()).putExtra("appWidgetId", id);
        PendingIntent action = PendingIntent.getBroadcast(c, id, refresh, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.refresh_button, action);
    }

    private static void position(Context c, RemoteViews v, int id, float left, float top, float right) {
        v.setViewPadding(id, dp(c,left), dp(c,top), dp(c,right), 0);
    }
    private static int dp(Context c, float value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    private static void textSize(RemoteViews v, int id, float size) { v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, size); }
    private static void setText(RemoteViews v, int id, String value, int color, float size) {
        v.setTextViewText(id, value); v.setTextColor(id, color); textSize(v, id, size);
    }
    private static float measure(Context c, String value, float size, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        p.setTextLocale(c.getResources().getConfiguration().getLocales().get(0));
        p.setTextSize((float)Math.ceil(size)); return p.measureText(value);
    }
    private static float fitted(Context c, String value, float size, float space, boolean bold) {
        float needed = measure(c,value,size,bold);
        space = Math.max(1, space - 2); // Reserve rounding room for native fallback glyph advances.
        return needed <= space || needed <= 0 ? size : Math.max(size * .65f, size * Math.max(1,space) / needed);
    }
    private static void fitPair(Context c, RemoteViews v, int labelId, int valueId, String label, String value,
            float size, float width, int labelColor, int valueColor) {
        float needed = measure(c,label,size,true) + measure(c,value,size,false) + 6;
        float fit = needed <= width ? size : Math.max(size * .65f, size * (width - 6) / (needed - 6));
        setText(v, labelId, label, labelColor, fit); setText(v, valueId, value, valueColor, fit);
    }
    static String label(Context c, String key, UsageSnapshot snapshot) {
        if (WidgetMeters.FIVE_HOUR.equals(key)) return c.getString(R.string.ref_five_hour);
        if (WidgetMeters.WEEKLY.equals(key)) return c.getString(snapshot != null && snapshot.longWindowIsMonthly() ? R.string.ref_monthly : R.string.ref_weekly);
        if (WidgetMeters.isLimitKey(key)) {
            UsageLimit limit = WidgetMeters.findLimit(key, snapshot);
            UsageWindow window = QuotaCardOptions.window(key, snapshot);
            if (limit != null && window != null) {
                long seconds = window.windowSeconds;
                String cadence = seconds >= 25 * 86400 ? c.getString(R.string.ref_monthly)
                        : seconds >= 6 * 86400 ? c.getString(R.string.ref_weekly)
                        : seconds <= 6 * 3600 ? c.getString(R.string.ref_five_hour)
                        : seconds >= 86400 ? c.getString(R.string.reference_days, Math.round(seconds / 86400d))
                        : c.getString(R.string.reference_limit);
                String name = (limit.name + " " + limit.meteredFeature).toLowerCase(Locale.ROOT).contains("spark")
                        ? "Codex Spark" : limit.displayName();
                return name + " · " + cadence;
            }
            return WidgetMeters.configLabel(key, snapshot);
        }
        return c.getString(R.string.reference_current_usage);
    }

    static String windowLabel(Context c, String key, WidgetUsageSnapshot snapshot, UsageSnapshot legacy) {
        if (WidgetMeters.NEXT_RESET.equals(key)) return c.getString(R.string.widget_meter_next_reset);
        if (WidgetMeters.RESET_CREDITS.equals(key)) return c.getString(R.string.widget_meter_credits);
        WidgetUsageWindow window = QuotaCardOptions.widgetWindow(key, snapshot);
        if (window == null) return label(c, key, legacy);
        if (WidgetMeters.FIVE_HOUR.equals(key) || WidgetMeters.WEEKLY.equals(key) || "monthly".equals(key)) {
            if ("monthly".equals(window.kind)) return c.getString(R.string.ref_monthly);
            if ("weekly".equals(window.kind)) return c.getString(R.string.ref_weekly);
            if ("five_hour".equals(window.kind)) return c.getString(R.string.ref_five_hour);
        }
        // Keep provider labels verbatim, as in AI-Usage. Old caches retain localized legacy labels.
        if (key.startsWith("codex:") || key.startsWith("direct:") || key.startsWith("extra:"))
            return window.label;
        if ("monthly".equals(key)) return c.getString(R.string.ref_monthly);
        return label(c, key, legacy);
    }
    static String resetText(Context c, long time, long now, boolean relative) {
        if (time <= 0) return "—";
        if (time < now) return c.getString(R.string.reference_reset_done);
        long minutes = (time - now) / 60_000;
        if (minutes == 0) return c.getString(R.string.reference_reset_soon);
        String duration;
        if ("zh".equals(c.getResources().getConfiguration().getLocales().get(0).getLanguage())) {
            duration = minutes >= 1440 ? (minutes / 1440) + "天" + (minutes % 1440 / 60) + "小时"
                    : minutes >= 60 ? (minutes / 60) + "小时" + (minutes % 60) + "分" : minutes + "分钟";
        } else duration = minutes >= 1440 ? (minutes / 1440) + "d " + (minutes % 1440 / 60) + "h"
                : minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
        return relative ? c.getString(R.string.time_reset_relative, duration) : duration;
    }
    private static String smallDate(Context c, long time) {
        boolean zh = "zh".equals(c.getResources().getConfiguration().getLocales().get(0).getLanguage());
        return new java.text.SimpleDateFormat(zh ? "MM月dd日 HH:mm" : "MMM d HH:mm", Locale.getDefault()).format(new Date(time));
    }
    private static Bitmap watermark(Context c, int width, int height, boolean wide, float scale, boolean dark) {
        Bitmap source = BitmapFactory.decodeResource(c.getResources(), R.drawable.ai_usage_watermark);
        int side = Math.max(1, dp(c, (wide ? 140 : 96) * scale));
        Bitmap bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(c.getResources().getDisplayMetrics().densityDpi);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setColorFilter(new PorterDuffColorFilter(dark ? 0xfff5f5f7 : 0xff232326, PorterDuff.Mode.SRC_IN));
        paint.setAlpha(dark ? 19 : 23);
        Canvas canvas = new Canvas(bitmap);
        float originX = dp(c, width + (wide ? 8 : 6)) - side;
        float originY = dp(c, height + (wide ? 12 : 6)) - side;
        Path clip = new Path();
        clip.addRoundRect(new RectF(-originX,-originY,dp(c,width)-originX,dp(c,height)-originY),dp(c,28),dp(c,28),Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.drawBitmap(source, null, new RectF(0,0,side,side), paint);
        return bitmap;
    }
    private static final class Window {
        final String key, title, value, kind, dialLabel, detail;
        final int progress, remaining, icon;
        final boolean usage;
        final long resetAt;
        Window(Context c, String key, WidgetUsageSnapshot snapshot, UsageSnapshot legacy,
                ResetCreditsSnapshot credits, WidgetOptions options, boolean useDials, long now) {
            this.key = key;
            title = windowLabel(c, key, snapshot, legacy);
            if (WidgetMeters.NEXT_RESET.equals(key)) {
                WidgetUsageWindow next = nextResetWindow(snapshot, now);
                kind = "next_reset";
                usage = false;
                icon = R.drawable.ic_oui_alarm;
                dialLabel = "Reset";
                resetAt = next == null ? 0L : next.resetAtMillis;
                long duration = next == null ? 1L : "five_hour".equals(next.kind) ? 18_000_000L
                        : Math.max(86_400_000L, next.windowSeconds * 1000L);
                progress = next == null ? 0 : (int) Math.max(0L, Math.min(100L,
                        Math.round((resetAt - now) * 100d / duration)));
                remaining = progress;
                value = next == null ? "—" : useDials ? englishCountdown(resetAt - now)
                        : resetText(c, resetAt, now, false);
                detail = "";
                return;
            }
            if (WidgetMeters.RESET_CREDITS.equals(key)) {
                Integer count = credits != null ? Integer.valueOf(credits.availableCount)
                        : snapshot == null ? null : snapshot.resetCreditsAvailable;
                kind = "reset_credits";
                usage = false;
                icon = R.drawable.ic_oui_refresh;
                dialLabel = "Credits";
                resetAt = 0L;
                progress = count == null ? -1 : Math.round(Math.min(4, Math.max(0, count)) / 4f * 100);
                remaining = progress;
                value = count == null ? "—" : String.valueOf(Math.max(0, count));
                long expiry = credits == null ? 0L : credits.nextExpiryMillis(now);
                detail = expiry > 0 ? c.getString(R.string.card_credit_expiry, smallDate(c, expiry)) : "";
                return;
            }
            usage = true;
            detail = "";
            WidgetUsageWindow window = QuotaCardOptions.widgetWindow(key, snapshot);
            kind = window == null ? WidgetMeters.FIVE_HOUR.equals(key) ? "five_hour"
                    : WidgetMeters.WEEKLY.equals(key) ? "weekly" : "" : window.kind;
            icon = "five_hour".equals(kind) || WidgetMeters.isLimitPrimary(key)
                    ? R.drawable.ic_oui_time : R.drawable.ic_oui_calendar_week;
            String cadence = "five_hour".equals(kind) ? "5h" : "weekly".equals(kind) ? "Wk"
                    : "monthly".equals(kind) ? "Mo"
                    : window != null && window.windowSeconds >= 86400
                    ? Math.round(window.windowSeconds / 86400d) + "d" : "Usage";
            dialLabel = key.startsWith("codex:") || key.startsWith("direct:") || key.startsWith("extra:")
                    ? (key.startsWith("extra:") && window != null
                            ? window.label.replaceFirst("\\s+(5 小时|每周|每月|[0-9]+ 天|限额)$", "") + " " : "") + cadence
                    : WidgetMeters.WEEKLY.equals(key) ? "monthly".equals(kind) ? "Mo" : "Wk"
                    : "monthly".equals(key) ? "Mo" : WidgetMeters.shortLabel(key, legacy);
            progress = window == null || window.usedPercent == null ? -1
                    : (int) Math.round(useDials && WidgetOptions.DISPLAY_USED.equals(options.displayMode)
                            ? window.usedPercent : 100 - window.usedPercent);
            remaining = window == null || window.usedPercent == null ? 100 : (int) Math.round(100 - window.usedPercent);
            value = progress < 0 ? "—" : progress + (!useDials || options.showPercentSymbol ? "%" : "");
            resetAt = window == null ? 0 : window.resetAtMillis;
        }
    }

    /** Match upstream: only the five-hour and long Codex windows feed the reset helper. */
    private static WidgetUsageWindow nextResetWindow(WidgetUsageSnapshot snapshot, long now) {
        WidgetUsageWindow five = QuotaCardOptions.widgetWindow(WidgetMeters.FIVE_HOUR, snapshot);
        WidgetUsageWindow longer = QuotaCardOptions.widgetWindow(WidgetMeters.WEEKLY, snapshot);
        boolean fiveFuture = five != null && five.resetAtMillis > now;
        boolean longFuture = longer != null && longer.resetAtMillis > now;
        if (fiveFuture && (!longFuture || five.resetAtMillis <= longer.resetAtMillis)) return five;
        return longFuture ? longer : null;
    }

    /** The original dial uses English even on a localized phone. */
    private static String englishCountdown(long remaining) {
        long minutes = Math.max(0L, remaining) / 60_000L;
        if (minutes >= 1440L) return minutes / 1440L + "d " + minutes % 1440L / 60L + "h";
        if (minutes >= 60L) return minutes / 60L + "h " + minutes % 60L + "m";
        return minutes > 0L ? minutes + "m" : "now";
    }
}
