package dev.bennett.codexmeter;

import android.app.PendingIntent;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the AI-Usage style usage card for small and medium home widgets.
 *
 * <p>AI-Usage lays out every row at a fixed y offset on a 158pt-tall canvas. The card layout
 * gives each row its own full-size container, and this renderer positions the containers with
 * padding (supported by RemoteViews on every API level), scaling all offsets and text sizes by
 * the host cell so the proportions hold on any launcher grid.
 */
final class UsageCardRenderer {
    /** Width (dp) from which a card uses the medium layouts with up to four windows. */
    static final float MEDIUM_MIN_WIDTH_DP = 250f;

    private static final float CANVAS_HEIGHT = 158f;
    private static final float SMALL_REFERENCE_WIDTH = 158f;
    private static final float MEDIUM_REFERENCE_WIDTH = 300f;
    private static final float MIN_SCALE = 0.7f;
    private static final float MAX_SCALE = 1.15f;
    /** SF Pro line box: AI-Usage rows stack at 1.193 x the font size. */
    private static final float LINE_HEIGHT = 1.193f;
    private static final int LEVEL_MAX = 10000;

    private static final int[] TITLE_POS = {R.id.card_pos_title_0, R.id.card_pos_title_1};
    private static final int[] TITLES = {R.id.card_title_0, R.id.card_title_1};
    private static final int[] DETAIL_POS = {R.id.card_pos_detail_0, R.id.card_pos_detail_1};
    private static final int[] RESETS = {R.id.card_reset_0, R.id.card_reset_1};
    private static final int[] VALUES = {R.id.card_value_0, R.id.card_value_1};
    private static final int[] LINE_POS = {
            R.id.card_pos_line_0, R.id.card_pos_line_1, R.id.card_pos_line_2, R.id.card_pos_line_3};
    private static final int[] LINE_TITLES = {R.id.card_line_title_0, R.id.card_line_title_1,
            R.id.card_line_title_2, R.id.card_line_title_3};
    private static final int[] LINE_RESETS = {R.id.card_line_reset_0, R.id.card_line_reset_1,
            R.id.card_line_reset_2, R.id.card_line_reset_3};
    private static final int[] LINE_VALUES = {R.id.card_line_value_0, R.id.card_line_value_1,
            R.id.card_line_value_2, R.id.card_line_value_3};
    private static final int[] BAR_POS = {
            R.id.card_pos_bar_0, R.id.card_pos_bar_1, R.id.card_pos_bar_2, R.id.card_pos_bar_3};
    private static final int[] BARS = {R.id.card_bar_0, R.id.card_bar_1, R.id.card_bar_2,
            R.id.card_bar_3};
    private static final int[] TRACKS = {R.id.card_bar_track_0, R.id.card_bar_track_1,
            R.id.card_bar_track_2, R.id.card_bar_track_3};
    private static final int[] FILLS = {R.id.card_bar_fill_0, R.id.card_bar_fill_1,
            R.id.card_bar_fill_2, R.id.card_bar_fill_3};
    private static final int[] OUTLINES = {R.id.card_bar_outline_0, R.id.card_bar_outline_1,
            R.id.card_bar_outline_2, R.id.card_bar_outline_3};
    /** Every positioned container, hidden before a variant shows the ones it uses. */
    private static final int[] ALL_POSITIONS = {
            R.id.card_pos_header, R.id.card_pos_hero_title, R.id.card_pos_hero_value,
            R.id.card_pos_title_0, R.id.card_pos_title_1, R.id.card_pos_detail_0,
            R.id.card_pos_detail_1, R.id.card_pos_line_0, R.id.card_pos_line_1,
            R.id.card_pos_line_2, R.id.card_pos_line_3, R.id.card_pos_bar_0, R.id.card_pos_bar_1,
            R.id.card_pos_bar_2, R.id.card_pos_bar_3, R.id.card_pos_meta_reset,
            R.id.card_pos_meta_credits, R.id.card_pos_meta_status, R.id.card_pos_credits_line,
            R.id.card_pos_error};

    /** The six AI-Usage single-account layouts. */
    enum Variant {
        SMALL_SINGLE(false), SMALL_DUAL(false), MEDIUM_SINGLE(true), MEDIUM_DUAL(true),
        MEDIUM_TRIPLE(true), MEDIUM_QUAD(true);

        final boolean medium;

        Variant(boolean medium) {
            this.medium = medium;
        }

        static Variant of(boolean medium, int windowCount) {
            if (!medium) {
                return windowCount <= 1 ? SMALL_SINGLE : SMALL_DUAL;
            }
            if (windowCount <= 1) {
                return MEDIUM_SINGLE;
            }
            if (windowCount == 2) {
                return MEDIUM_DUAL;
            }
            return windowCount == 3 ? MEDIUM_TRIPLE : MEDIUM_QUAD;
        }

        int capacity() {
            switch (this) {
                case SMALL_SINGLE:
                case MEDIUM_SINGLE:
                    return 1;
                case SMALL_DUAL:
                case MEDIUM_DUAL:
                    return 2;
                case MEDIUM_TRIPLE:
                    return 3;
                default:
                    return 4;
            }
        }
    }

    /** Per-widget theme and background chosen in the widget editor (shared by both styles). */
    static final class Style {
        final String theme;
        final int opacity;

        Style(String theme, int opacity) {
            this.theme = theme == null ? WidgetOptions.THEME_SYSTEM : theme;
            this.opacity = Math.max(0, Math.min(100, opacity));
        }

        boolean transparent() {
            return opacity <= 0;
        }
    }

    /** One window row: title, value, reset text and the window behind them. */
    private static final class Row {
        final String title;
        final UsageWindow window;
        final long resetAtMillis;

        Row(String title, UsageWindow window, long resetAtMillis) {
            this.title = title;
            this.window = window;
            this.resetAtMillis = resetAtMillis;
        }
    }

    private final Context context;
    private final Resources resources;
    private final RemoteViews views;
    private final Style style;
    private final UsageCardState state;
    private final Variant variant;
    private final List<Row> rows;
    private final UsageCardPalette day;
    private final UsageCardPalette night;
    private final boolean dayNight;
    private final boolean showCredits;
    private final float scale;
    private final float density;
    private final float padding;
    private final float offset;
    private final float contentWidth;

    private UsageCardRenderer(Context context, Style style, List<String> keys,
            UsageCardState state, float widthDp, float heightDp) {
        this.context = context;
        this.resources = context.getResources();
        this.style = style;
        this.state = state;
        boolean medium = widthDp >= MEDIUM_MIN_WIDTH_DP;
        this.variant = Variant.of(medium, keys.size());
        this.rows = rows(keys.subList(0, Math.min(keys.size(), variant.capacity())));
        this.views = new RemoteViews(context.getPackageName(),
                style.transparent() ? R.layout.widget_card_shadow : R.layout.widget_card);
        this.dayNight = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        if (style.transparent()) {
            day = UsageCardPalette.TRANSPARENT;
            night = UsageCardPalette.TRANSPARENT;
        } else if (WidgetOptions.THEME_LIGHT.equals(style.theme)) {
            day = UsageCardPalette.LIGHT;
            night = UsageCardPalette.LIGHT;
        } else if (WidgetOptions.THEME_DARK.equals(style.theme)) {
            day = UsageCardPalette.DARK;
            night = UsageCardPalette.DARK;
        } else {
            day = UsageCardPalette.LIGHT;
            night = UsageCardPalette.DARK;
        }
        this.showCredits = state.availableCredits() > 0 && variant != Variant.MEDIUM_QUAD;
        float referenceWidth = medium ? MEDIUM_REFERENCE_WIDTH : SMALL_REFERENCE_WIDTH;
        float fit = Math.min(heightDp / CANVAS_HEIGHT, widthDp / referenceWidth);
        this.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, fit));
        this.density = resources.getDisplayMetrics().density;
        this.padding = medium ? 20f : 12f;
        this.offset = Math.max(0f, (heightDp / scale - CANVAS_HEIGHT) / 2f);
        float minimumContent = medium ? 180f : 90f;
        this.contentWidth = Math.max(minimumContent, widthDp / scale - padding * 2f);
    }

    /**
     * Renders the card for one host size. {@code keys} is the widget's resolved window
     * selection; small cards show the first two and medium cards up to four.
     */
    static RemoteViews build(Context context, int appWidgetId, Style style, List<String> keys,
            UsageCardState state, float widthDp, float heightDp) {
        UsageCardRenderer renderer = new UsageCardRenderer(context, style, keys, state,
                widthDp, heightDp);
        renderer.render(appWidgetId);
        return renderer.views;
    }

    private List<Row> rows(List<String> keys) {
        List<Row> result = new ArrayList<>();
        UsageSnapshot snapshot = state.snapshot;
        long observedAt = state.fetchedAtMillis();
        for (String key : keys) {
            UsageWindow window = UsageCardWindows.window(key, snapshot);
            long resetAt = window == null ? 0L : window.effectiveResetAtMillis(observedAt);
            result.add(new Row(UsageCardWindows.title(resources, key, snapshot), window,
                    resetAt));
        }
        if (result.isEmpty()) {
            result.add(new Row(resources.getString(R.string.widget_card_current_usage), null,
                    0L));
        }
        return result;
    }

    private void render(int appWidgetId) {
        for (int position : ALL_POSITIONS) {
            views.setViewVisibility(position, View.GONE);
        }
        renderSurface();
        renderHeader();
        switch (variant) {
            case SMALL_SINGLE:
                renderSmallSingle();
                break;
            case SMALL_DUAL:
                renderSmallDual();
                break;
            case MEDIUM_SINGLE:
                renderMediumSingle();
                break;
            case MEDIUM_DUAL:
                renderMediumDual();
                break;
            default:
                renderMediumList();
                break;
        }
        bindActions(appWidgetId);
    }

    // ---------------------------------------------------------------------------------------
    // Chrome

    private void renderSurface() {
        color(R.id.card_surface, "setColorFilter", day.background, night.background);
        views.setInt(R.id.card_surface, "setImageAlpha", style.transparent()
                ? UsageCardPalette.SCRIM_ALPHA : Math.round(style.opacity * 2.55f));
        if (style.transparent()) {
            views.setViewVisibility(R.id.card_watermark, View.GONE);
            return;
        }
        views.setViewVisibility(R.id.card_watermark, View.VISIBLE);
        color(R.id.card_watermark, "setColorFilter", day.watermark, night.watermark);
        views.setInt(R.id.card_watermark, "setImageAlpha", UsageCardPalette.WATERMARK_ALPHA);
        if (dayNight) {
            float size = variant.medium ? 140f : 96f;
            views.setViewLayoutWidth(R.id.card_watermark, size * scale, TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutHeight(R.id.card_watermark, size * scale,
                    TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutMargin(R.id.card_watermark, RemoteViews.MARGIN_END,
                    (variant.medium ? -8f : -6f) * scale, TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutMargin(R.id.card_watermark, RemoteViews.MARGIN_BOTTOM,
                    (variant.medium ? -12f : -6f) * scale, TypedValue.COMPLEX_UNIT_DIP);
        }
    }

    private void renderHeader() {
        float top;
        switch (variant) {
            case SMALL_SINGLE:
                top = 11f;
                break;
            case SMALL_DUAL:
                top = showCredits ? 8f : 11f;
                break;
            case MEDIUM_SINGLE:
            case MEDIUM_DUAL:
                top = showCredits ? 9f : 10f;
                break;
            case MEDIUM_TRIPLE:
                top = 18f;
                break;
            default:
                top = 9f;
                break;
        }
        place(R.id.card_pos_header, top);
        PlanBadge.Size size = variant.medium ? PlanBadge.Size.MEDIUM : PlanBadge.Size.SMALL;
        String label = UsageFormat.planLabel(state.planType());
        Bitmap dayBadge = badge(label, size, day);
        Bitmap nightBadge = day == night ? dayBadge : badge(label, size, night);
        if (dayNight) {
            views.setIcon(R.id.card_badge, "setImageIcon", Icon.createWithBitmap(dayBadge),
                    Icon.createWithBitmap(nightBadge));
            views.setViewLayoutHeight(R.id.card_badge, size.height * scale,
                    TypedValue.COMPLEX_UNIT_DIP);
        } else {
            views.setImageViewBitmap(R.id.card_badge, isNight() ? nightBadge : dayBadge);
        }
        views.setContentDescription(R.id.card_badge, PlanBadge.text(label));
        if (!variant.medium) {
            views.setViewVisibility(R.id.card_header_status, View.GONE);
            return;
        }
        views.setViewVisibility(R.id.card_header_status, View.VISIBLE);
        float statusSize = variant == Variant.MEDIUM_TRIPLE || variant == Variant.MEDIUM_QUAD
                ? 9f : 10f;
        text(R.id.card_header_status, headerStatus(), statusSize, day.secondary,
                night.secondary);
    }

    private Bitmap badge(String label, PlanBadge.Size size, UsageCardPalette palette) {
        return PlanBadge.render(context, label, size, scale, density, palette.dark);
    }

    // ---------------------------------------------------------------------------------------
    // Variants (offsets from AI-Usage SmallLayouts.tsx / MediumLayouts.tsx)

    private void renderSmallSingle() {
        Row row = rows.get(0);
        place(R.id.card_pos_hero_title, showCredits ? 35f : 36f);
        text(R.id.card_hero_title, row.title, 14f, day.primary, night.primary);
        heroValue(row, showCredits ? 53f : 55f, 32f, 12f, true);
        bar(0, showCredits ? 92f : 96f, 7f, row.window);
        float spacing = showCredits ? 3.5f : 5f;
        float top = showCredits ? 106f : 112f;
        top = metaReset(row, top, 10f, spacing);
        if (showCredits) {
            top = metaCredits(top, 10f, spacing, false);
        }
        metaStatus(top, 10f);
    }

    private void renderSmallDual() {
        float[] tops = showCredits ? new float[] {32f, 79f} : new float[] {38f, 89f};
        for (int index = 0; index < 2; index++) {
            Row row = index < rows.size() ? rows.get(index) : null;
            block(index, row, tops[index], 14f, 18f, 10f, 12f, 35f, 6f);
        }
        if (showCredits) {
            metaCredits(124.5f, 10f, 0f, true);
        }
        metaStatus(showCredits ? 140f : 136f, 10f);
    }

    private void renderMediumSingle() {
        Row row = rows.get(0);
        place(R.id.card_pos_hero_title, showCredits ? 36f : 39f);
        text(R.id.card_hero_title, row.title, 16f, day.primary, night.primary);
        heroValue(row, showCredits ? 56f : 62f, 40f, 16f, false);
        bar(0, showCredits ? 104f : 111f, 7f, row.window);
        float spacing = showCredits ? 3.5f : 0f;
        float top = metaReset(row, showCredits ? 118f : 128f, 10.5f, spacing);
        if (showCredits) {
            metaCredits(top, 10.5f, spacing, false);
        }
        errorLine();
    }

    private void renderMediumDual() {
        float[] tops = showCredits ? new float[] {34f, 87f} : new float[] {38f, 96f};
        for (int index = 0; index < 2; index++) {
            Row row = index < rows.size() ? rows.get(index) : null;
            block(index, row, tops[index], 16f, 20f, 12f, 14f, 40f, 6.5f);
        }
        if (showCredits) {
            metaCredits(140f, 10.5f, 0f, false);
        }
        errorLine();
    }

    private void renderMediumList() {
        float[] tops;
        if (variant == Variant.MEDIUM_QUAD) {
            tops = new float[] {32f, 63f, 94f, 125f};
        } else {
            tops = showCredits ? new float[] {42f, 76f, 110f} : new float[] {44f, 78f, 112f};
        }
        for (int index = 0; index < tops.length; index++) {
            Row row = index < rows.size() ? rows.get(index) : null;
            place(LINE_POS[index], tops[index]);
            text(LINE_TITLES[index], row == null ? UsageCardFormat.MISSING : row.title, 13f,
                    day.primary, night.primary);
            text(LINE_RESETS[index], resetText(row), 10f, day.secondary, night.secondary);
            text(LINE_VALUES[index], UsageCardFormat.percent(row == null ? null : row.window),
                    14f, day.primary, night.primary);
            bar(index, tops[index] + 19f, 5.5f, row == null ? null : row.window);
        }
        if (showCredits) {
            place(R.id.card_pos_credits_line, 145f);
            String count = creditsLabel();
            String expiry = creditsExpiry(false);
            text(R.id.card_credits_line, expiry.isEmpty() ? count
                    : resources.getString(R.string.widget_card_credits_line, count, expiry),
                    8f, day.secondary, night.secondary);
        }
        errorLine();
    }

    // ---------------------------------------------------------------------------------------
    // Building blocks

    /** Hero percentage and its "剩余" caption on a shared baseline. */
    private void heroValue(Row row, float top, float valueSize, float captionSize,
            boolean captionAtEnd) {
        place(R.id.card_pos_hero_value, top);
        text(R.id.card_hero_value, UsageCardFormat.percent(row.window), valueSize, day.primary,
                night.primary);
        text(R.id.card_hero_caption, resources.getString(R.string.widget_card_remaining),
                captionSize, day.secondary, night.secondary);
        views.setViewVisibility(R.id.card_hero_spacer, captionAtEnd ? View.VISIBLE : View.GONE);
        views.setViewPadding(R.id.card_hero_caption, px(8f), 0, 0, 0);
    }

    /** Two-line window block: title, then reset text with the percentage, then the bar. */
    private void block(int index, Row row, float top, float titleSize, float detailOffset,
            float resetSize, float valueSize, float barOffset, float barHeight) {
        place(TITLE_POS[index], top);
        text(TITLES[index], row == null ? UsageCardFormat.MISSING : row.title, titleSize,
                day.primary, night.primary);
        place(DETAIL_POS[index], top + detailOffset);
        text(RESETS[index], resetText(row), resetSize, day.secondary, night.secondary);
        text(VALUES[index], UsageCardFormat.percent(row == null ? null : row.window), valueSize,
                day.primary, night.primary);
        views.setViewPadding(VALUES[index], px(variant.medium ? 8f : 4f), 0, 0, 0);
        bar(index, top + barOffset, barHeight, row == null ? null : row.window);
    }

    private void bar(int index, float top, float height, UsageWindow window) {
        place(BAR_POS[index], top);
        if (dayNight) {
            views.setViewLayoutHeight(BARS[index], height * scale, TypedValue.COMPLEX_UNIT_DIP);
        }
        int remaining = window == null ? 0 : window.remainingPercent();
        int level = 0;
        if (remaining > 0) {
            float fraction = Math.max(remaining / 100f, height / contentWidth);
            level = Math.round(Math.min(1f, fraction) * LEVEL_MAX);
        }
        views.setInt(FILLS[index], "setImageLevel", level);
        views.setViewVisibility(TRACKS[index], View.VISIBLE);
        color(TRACKS[index], "setColorFilter", day.track, night.track);
        views.setInt(TRACKS[index], "setImageAlpha", day.trackAlpha);
        color(FILLS[index], "setColorFilter", day.severity(window), night.severity(window));
        if (style.transparent()) {
            views.setViewVisibility(OUTLINES[index], View.GONE);
        } else {
            views.setViewVisibility(OUTLINES[index], View.VISIBLE);
            views.setImageViewResource(OUTLINES[index], R.drawable.widget_card_capsule_hairline);
            color(OUTLINES[index], "setColorFilter", day.border, night.border);
            views.setInt(OUTLINES[index], "setImageAlpha", UsageCardPalette.BORDER_ALPHA);
        }
    }

    /** "重置时间" footer row; returns the top of the next row. */
    private float metaReset(Row row, float top, float size, float spacing) {
        place(R.id.card_pos_meta_reset, top);
        text(R.id.card_meta_reset_label, resources.getString(R.string.widget_card_reset_time),
                size, day.secondary, night.secondary);
        text(R.id.card_meta_reset_value, UsageCardFormat.bareReset(resources, row.resetAtMillis,
                state.nowMillis), size, day.primary, night.primary);
        return nextRow(top, size, spacing);
    }

    /** "重置N次 … 最近到期" footer row; returns the top of the next row. */
    private float metaCredits(float top, float size, float spacing, boolean shortUnknown) {
        place(R.id.card_pos_meta_credits, top);
        text(R.id.card_meta_credits_label, creditsLabel(), size, day.secondary, night.secondary);
        text(R.id.card_meta_credits_value, creditsExpiry(shortUnknown), size, day.primary,
                night.primary);
        return nextRow(top, size, spacing);
    }

    /** Small cards: "刷新时间 … 14:48 刷新", replaced by a status message when relevant. */
    private void metaStatus(float top, float size) {
        place(R.id.card_pos_meta_status, top);
        text(R.id.card_meta_status_label, resources.getString(R.string.widget_card_refresh_time),
                size, day.secondary, night.secondary);
        String status = state.statusMessage(resources, false);
        if (status.isEmpty()) {
            long fetchedAt = state.fetchedAtMillis();
            status = fetchedAt <= 0L ? UsageCardFormat.MISSING : resources.getString(
                    R.string.widget_card_refreshed_value, UsageCardFormat.time(context, fetchedAt));
        }
        text(R.id.card_meta_status_value, status, size, day.secondary, night.secondary);
    }

    /** Medium cards keep the refresh time in the header and add a warning line at the bottom. */
    private void errorLine() {
        String message = state.statusMessage(resources, true);
        if (message.isEmpty()) {
            return;
        }
        views.setViewVisibility(R.id.card_pos_error, View.VISIBLE);
        views.setViewPadding(R.id.card_pos_error, px(padding), 0, px(padding), px(2f));
        text(R.id.card_error, message, 8f, day.warn(), night.warn());
    }

    private String headerStatus() {
        long fetchedAt = state.fetchedAtMillis();
        if (fetchedAt <= 0L) {
            return UsageCardFormat.MISSING;
        }
        return resources.getString(R.string.widget_card_refreshed_header,
                UsageCardFormat.time(context, fetchedAt));
    }

    private String resetText(Row row) {
        return row == null ? UsageCardFormat.MISSING
                : UsageCardFormat.relativeReset(resources, row.resetAtMillis, state.nowMillis);
    }

    private String creditsLabel() {
        int count = state.availableCredits();
        return resources.getQuantityString(R.plurals.widget_card_credits, count, count);
    }

    private String creditsExpiry(boolean smallUnknown) {
        long expiry = state.nextCreditExpiryMillis();
        if (expiry <= 0L) {
            return resources.getString(R.string.widget_card_expiry_unknown);
        }
        String date = UsageCardFormat.expiryDate(context, expiry);
        return variant.medium ? resources.getString(R.string.widget_card_next_expiry, date) : date;
    }

    private void bindActions(int appWidgetId) {
        PendingIntent open = WidgetActions.openApp(context, appWidgetId);
        PendingIntent refresh = WidgetActions.refresh(context, appWidgetId);
        views.setOnClickPendingIntent(android.R.id.background, open);
        views.setOnClickPendingIntent(R.id.card_meta_status_row, refresh);
        views.setOnClickPendingIntent(R.id.card_header_status, refresh);
    }

    // ---------------------------------------------------------------------------------------
    // RemoteViews helpers

    private void place(int containerId, float top) {
        views.setViewVisibility(containerId, View.VISIBLE);
        views.setViewPadding(containerId, px(padding), px(top + offset), px(padding), 0);
    }

    private void text(int viewId, CharSequence value, float size, int dayColor, int nightColor) {
        views.setTextViewText(viewId, value);
        views.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_DIP, size * scale);
        color(viewId, "setTextColor", dayColor, nightColor);
    }

    /** Day/night colour pair on Android 12+, otherwise the colour for the current mode. */
    private void color(int viewId, String method, int dayColor, int nightColor) {
        if (dayNight) {
            views.setColorInt(viewId, method, dayColor, nightColor);
        } else {
            views.setInt(viewId, method, isNight() ? nightColor : dayColor);
        }
    }

    private boolean isNight() {
        return (resources.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private float nextRow(float top, float size, float spacing) {
        return top + size * LINE_HEIGHT + spacing;
    }

    private int px(float designUnits) {
        return Math.round(designUnits * scale * density);
    }
}
