package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageWindow;
import dev.bennett.codexmeter.WidgetMeters;

import android.app.PendingIntent;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The "Clear" home-widget style: a Material You card with an account-plan header and refresh button
 * above one tonal panel per selected meter (title, value, a thick progress bar,
 * and when the window resets). Narrow widgets stack the first two
 * windows; wide widgets lay up to four out in two columns. Panels share the height, so the card
 * fills any size; names and values share a row above the progress bar and reset detail. The colours are
 * resolved by the launcher from the wallpaper palette and follow its night mode.
 */
final class MaterialCardRenderer {
    static final float MEDIUM_MIN_WIDTH_DP = 250f;
    /** Reference metrics in dp at scale 1; panel typography also fits its actual height. */
    private static final float PANEL_WIDTH = 170f;
    private static final float PANEL_HEIGHT = 74f;
    private static final float HEADER_HEIGHT = 24f;
    private static final float PADDING = 12f;
    private static final float FIRST_ROW_GAP = 10f;
    private static final float GAP = 8f;
    /** The XML header's start margins and status start padding do not scale with the card. */
    private static final float HEADER_SPACING = 8f;
    private static final float ICON = 22f;
    /** Source glyph bounds: Sync 160..800/960; Sync Problem 120..840/960. */
    private static final float REFRESH_END_INSET = 0.16666667f;
    private static final float SYNC_PROBLEM_INSET = 0.125f;
    private static final float REFRESH_TARGET = 50f;
    private static final float STATUS_TEXT = 10.5f;
    /** The native health widget's label-medium size is in sp, independent of card scale. */
    private static final float UPDATED_TEXT = 12f;
    private static final float NAME_TEXT = 15f;
    private static final float VALUE_TEXT = 15.5f;
    private static final float DETAIL_TEXT = 9.5f;
    private static final float PANEL_PADDING_H = 12f;
    private static final float PANEL_PADDING_V = 4f;
    /** Normal spacing; minimum-height cards use their remaining vertical space. */
    private static final float PANEL_GAP = 5f;
    private static final float VALUE_GAP = 2f;
    private static final float BAR_HEIGHT = 9f;
    private static final float MIN_SCALE = 0.7f;
    private static final float MAX_SCALE = 1.25f;
    /** Typography matches the generic 2x2 card at its 170dp reference height. */
    private static final float TEXT_REFERENCE_HEIGHT = 170f;
    private static final float TEXT_REFERENCE_SCALE = 0.9f;
    private static final int LEVEL_MAX = 10000;
    private static final long DAY_MILLIS = TimeUnit.DAYS.toMillis(1);

    private static final int[] ROWS = {R.id.md_row_0, R.id.md_row_1};
    private static final int[] PANELS = {R.id.md_panel_0, R.id.md_panel_1, R.id.md_panel_2,
            R.id.md_panel_3};
    private static final int[] PANEL_BACKGROUNDS = {R.id.md_panel_bg_0, R.id.md_panel_bg_1,
            R.id.md_panel_bg_2, R.id.md_panel_bg_3};
    private static final int[] CONTENTS = {R.id.md_panel_content_0, R.id.md_panel_content_1,
            R.id.md_panel_content_2, R.id.md_panel_content_3};
    private static final int[] NAMES = {R.id.md_name_0, R.id.md_name_1, R.id.md_name_2,
            R.id.md_name_3};
    private static final int[] VALUES = {R.id.md_value_0, R.id.md_value_1, R.id.md_value_2,
            R.id.md_value_3};
    private static final int[] BARS = {R.id.md_bar_0, R.id.md_bar_1, R.id.md_bar_2,
            R.id.md_bar_3};
    private static final int[] TRACKS = {R.id.md_track_0, R.id.md_track_1, R.id.md_track_2,
            R.id.md_track_3};
    private static final int[] FILLS = {R.id.md_fill_0, R.id.md_fill_1, R.id.md_fill_2,
            R.id.md_fill_3};
    private static final int[] DETAILS = {R.id.md_detail_0, R.id.md_detail_1,
            R.id.md_detail_2, R.id.md_detail_3};
    private static final int[] RESETS = {R.id.md_reset_0, R.id.md_reset_1, R.id.md_reset_2,
            R.id.md_reset_3};

    /** Colour roles; each resource follows the night mode (see widget_material_colors). */
    private enum Role {
        SURFACE(R.color.widget_material_surface),
        PANEL(R.color.widget_material_panel),
        TRACK(R.color.widget_material_track),
        TEXT(R.color.widget_material_text),
        SECONDARY(R.color.widget_material_secondary),
        ACCENT(R.color.widget_material_accent);

        final int resource;

        Role(int resource) {
            this.resource = resource;
        }
    }

    private final Context context;
    private final Resources resources;
    private final RemoteViews views;
    private final WidgetOptions options;
    private final UsageCardState state;
    private final List<String> keys;
    private final int columns;
    private final int rowCount;
    private final boolean singleUsage;
    private final float widthDp;
    private final float scale;
    private final float textScale;
    private final boolean showUpdated;
    private final float panelContentWidth;
    private final int headerHeightPx;
    private final int panelHeightPx;

    private MaterialCardRenderer(Context context, WidgetOptions options,
            List<String> keys, UsageCardState state, float widthDp, float heightDp) {
        this.context = context;
        this.resources = context.getResources();
        this.options = options;
        this.widthDp = widthDp;
        this.showUpdated = widthDp >= 280f;
        this.state = state;
        boolean wide = widthDp >= MEDIUM_MIN_WIDTH_DP;
        int capacity = wide ? PANELS.length : ROWS.length;
        this.keys = keys.subList(0, Math.min(keys.size(), capacity));
        this.singleUsage = this.keys.size() == 1 && (WidgetMeters.FIVE_HOUR.equals(this.keys.get(0))
                || WidgetMeters.WEEKLY.equals(this.keys.get(0)));
        int shown = Math.max(1, this.keys.size());
        this.columns = wide && shown > 1 ? 2 : 1;
        this.rowCount = (shown + columns - 1) / columns;
        float neededWidth = PADDING * 2f + columns * PANEL_WIDTH + (columns - 1) * GAP;
        float panelHeight = singleUsage ? PANEL_HEIGHT + VALUE_TEXT * 2f + PANEL_GAP : PANEL_HEIGHT;
        float neededHeight = PADDING * 2f + HEADER_HEIGHT + FIRST_ROW_GAP
                + rowCount * panelHeight + (rowCount - 1) * GAP;
        float fit = Math.min(widthDp / neededWidth, heightDp / neededHeight);
        this.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, fit));
        this.textScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE,
                TEXT_REFERENCE_SCALE * heightDp / TEXT_REFERENCE_HEIGHT));
        boolean failure = state.signedIn && !state.refreshError.isEmpty();
        float metadataSize = showUpdated || failure
                ? TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, UPDATED_TEXT,
                        resources.getDisplayMetrics())
                : STATUS_TEXT * textScale * resources.getDisplayMetrics().density;
        this.headerHeightPx = Math.max(px(ICON), Math.max(lineHeight(VALUE_TEXT, true),
                lineHeightPx(metadataSize, Typeface.create("sans-serif-medium", Typeface.NORMAL))));
        this.panelHeightPx = Math.max(0, (Math.round(heightDp
                * resources.getDisplayMetrics().density) - px(PADDING) * 2 - headerHeightPx
                - px(FIRST_ROW_GAP) - (rowCount - 1) * px(GAP)) / rowCount);
        float panelWidth = (widthDp - PADDING * 2f * scale - (columns - 1) * GAP * scale)
                / columns;
        this.panelContentWidth = Math.max(1f, panelWidth - PANEL_PADDING_H * 2f * scale);
        // New layout identities reload cached foregrounds without changing the common layouts.
        this.views = new RemoteViews(context.getPackageName(), options.opacity <= 0
                ? R.layout.widget_material_refresh_shadow : R.layout.widget_material_refresh,
                android.R.id.background);
    }

    /**
     * Renders the Material card for one host size. {@code keys} is the widget's resolved window
     * selection; narrow cards show the first two and wide cards up to four.
     */
    static RemoteViews build(Context context, int appWidgetId, WidgetOptions options,
            List<String> keys, UsageCardState state, float widthDp, float heightDp) {
        MaterialCardRenderer renderer = new MaterialCardRenderer(context, options, keys, state,
                widthDp, heightDp);
        renderer.render(appWidgetId);
        return renderer.views;
    }

    private void render(int appWidgetId) {
        renderSurface();
        renderHeader();
        for (int row = 0; row < ROWS.length; row++) {
            views.setViewVisibility(ROWS[row], row < rowCount ? View.VISIBLE : View.GONE);
            margin(ROWS[row], RemoteViews.MARGIN_TOP, row == 0 ? FIRST_ROW_GAP : GAP);
        }
        for (int slot = 0; slot < PANELS.length; slot++) {
            int item = itemForSlot(slot);
            if (item < 0) {
                views.setViewVisibility(PANELS[slot], View.GONE);
                continue;
            }
            views.setViewVisibility(PANELS[slot], View.VISIBLE);
            renderPanel(slot, item, appWidgetId);
        }
        bindActions(appWidgetId);
    }

    /** Index into {@link #keys} shown by a layout slot, or -1 when the slot stays hidden. */
    private int itemForSlot(int slot) {
        int row = slot / 2;
        int column = slot % 2;
        if (row >= rowCount || column >= columns) {
            return -1;
        }
        int item = row * columns + column;
        int shown = Math.max(1, keys.size());
        return item < shown ? item : -1;
    }

    private void renderSurface() {
        int padding = px(PADDING);
        views.setViewPadding(R.id.md_content, padding, padding, padding, padding);
        if (options.opacity <= 0) {
            views.setViewVisibility(R.id.md_surface, View.GONE);
            return;
        }
        views.setViewVisibility(R.id.md_surface, View.VISIBLE);
        color(R.id.md_surface, "setColorFilter", Role.SURFACE);
        views.setInt(R.id.md_surface, "setImageAlpha", Math.round(options.opacity * 2.55f));
    }

    private void renderHeader() {
        boolean classic = OneUiWidgetAppearance.classicPalette(options.colorStyle);
        views.setImageViewResource(R.id.md_logo, classic ? R.drawable.ic_codex_logo_color
                : R.drawable.ic_notification);
        views.setColorStateList(R.id.md_logo, "setImageTintList", null);
        boolean failure = state.signedIn && !state.refreshError.isEmpty();
        String currentStatus = state.statusMessage(resources, false);
        boolean problem = !currentStatus.isEmpty();
        String status = state.refreshing ? resources.getString(R.string.widget_card_refreshing)
                : currentStatus;
        boolean wide = widthDp >= MEDIUM_MIN_WIDTH_DP;
        boolean mediumStatus = showUpdated || failure;
        // Reapply can retain a host view's previous offset; the header shares the panel edge.
        views.setViewLayoutMargin(R.id.md_header, RemoteViews.MARGIN_START, 0,
                TypedValue.COMPLEX_UNIT_PX);
        for (int icon : new int[] {R.id.md_logo, R.id.md_refresh}) {
            views.setViewLayoutWidth(icon, ICON * scale, TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutHeight(icon, ICON * scale, TypedValue.COMPLEX_UNIT_DIP);
        }
        int iconWidth = Math.round(ICON * resources.getDisplayMetrics().density
                * scale);
        boolean rtl = resources.getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int shift = Math.round(iconWidth * (problem ? SYNC_PROBLEM_INSET : REFRESH_END_INSET));
        views.setViewPadding(R.id.md_refresh, rtl ? -shift : shift, 0, rtl ? shift : -shift, 0);
        float endCenter = ColorOsWidgetAppearance.cardCornerRadiusPx(context);
        int endMargin = Math.round(endCenter - px(PADDING) - iconWidth / 2f + shift);
        views.setViewLayoutMargin(R.id.md_refresh, RemoteViews.MARGIN_END, endMargin,
                TypedValue.COMPLEX_UNIT_PX);
        float topCenter = px(PADDING) + (headerHeightPx - iconWidth) / 2 + iconWidth / 2f;
        // RemoteViews margins use pixel offsets (truncate), unlike explicit sizes (round).
        int rowGap = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                FIRST_ROW_GAP * scale, resources.getDisplayMetrics());
        float rowTop = px(PADDING) + headerHeightPx + rowGap;
        // Align with the shell's corner axis while retaining the original header center line.
        int targetSize = Math.min(Math.round(REFRESH_TARGET * resources.getDisplayMetrics().density),
                (int) (2 * Math.min(endCenter, Math.min(topCenter, rowTop - topCenter))));
        targetSize -= (targetSize - iconWidth) & 1;
        views.setViewLayoutWidth(R.id.md_refresh_button, targetSize, TypedValue.COMPLEX_UNIT_PX);
        views.setViewLayoutHeight(R.id.md_refresh_button, targetSize, TypedValue.COMPLEX_UNIT_PX);
        views.setViewLayoutMargin(R.id.md_refresh_button, RemoteViews.MARGIN_END,
                Math.round(endCenter - targetSize / 2f), TypedValue.COMPLEX_UNIT_PX);
        views.setViewLayoutMargin(R.id.md_refresh_button, RemoteViews.MARGIN_TOP,
                Math.round(topCenter - targetSize / 2f),
                TypedValue.COMPLEX_UNIT_PX);
        textSize(R.id.md_title, VALUE_TEXT);
        if (mediumStatus) {
            views.setTextViewTextSize(R.id.md_status, TypedValue.COMPLEX_UNIT_SP, UPDATED_TEXT);
        } else {
            textSize(R.id.md_status, STATUS_TEXT);
        }
        views.setTextViewTextSize(R.id.md_updated, TypedValue.COMPLEX_UNIT_SP, UPDATED_TEXT);
        String updated = UsageCardFormat.time(context, state.fetchedAtMillis());
        boolean updatedVisible = showUpdated && state.fetchedAtMillis() > 0L && !problem && !state.refreshing;
        views.setTextViewText(R.id.md_updated, metadataText(updated, showUpdated));
        views.setViewVisibility(R.id.md_updated,
                updatedVisible ? View.VISIBLE : View.GONE);
        String plan = UsageFormat.planLabel(state.planType());
        String title = plan.isEmpty() ? resources.getString(R.string.widget_material_title) : plan;
        views.setTextViewText(R.id.md_title, title);
        boolean statusVisible = wide && !status.isEmpty();
        float density = resources.getDisplayMetrics().density;
        int spacing = Math.round(HEADER_SPACING * density);
        int available = Math.round(widthDp * density) - 2 * px(PADDING)
                - 2 * iconWidth - 2 * spacing - endMargin;
        Paint paint = new Paint();
        Typeface metadataFace = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        float metadataSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                UPDATED_TEXT, resources.getDisplayMetrics());
        int titleMaxWidth = Math.max(1, available);
        if (updatedVisible) {
            paint.setTypeface(metadataFace);
            paint.setTextSize(metadataSize);
            titleMaxWidth = Math.max(1, available - spacing
                    - (int) Math.ceil(paint.measureText(updated)));
        }
        if (!status.isEmpty()) {
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(VALUE_TEXT * textScale * density);
            int titleWidth = Math.min(titleMaxWidth, (int) Math.ceil(paint.measureText(title)));
            paint.setTypeface(metadataFace);
            paint.setTextSize(metadataSize);
            statusVisible = wide
                    && available - titleWidth - spacing >= Math.ceil(paint.measureText(status));
        }
        views.setInt(R.id.md_title, "setMaxWidth", titleMaxWidth);
        views.setTextViewText(R.id.md_status, metadataText(statusVisible ? status : "", mediumStatus));
        views.setContentDescription(R.id.md_status, null);
        views.setViewVisibility(R.id.md_status,
                statusVisible ? View.VISIBLE : View.INVISIBLE);
        views.setImageViewResource(R.id.md_refresh,
                problem ? R.drawable.ic_ms_sync_problem : R.drawable.ic_ms_sync);
        views.setViewVisibility(R.id.md_refresh, state.refreshing ? View.INVISIBLE : View.VISIBLE);
        views.removeAllViews(R.id.md_refresh_button);
        if (state.refreshing) {
            RemoteViews spinner = new RemoteViews(context.getPackageName(),
                    problem ? R.layout.widget_refresh_problem_spinner
                            : R.layout.widget_refresh_spinner);
            spinner.setViewLayoutWidth(R.id.md_refresh_spinner, ICON * scale,
                    TypedValue.COMPLEX_UNIT_DIP);
            spinner.setViewLayoutHeight(R.id.md_refresh_spinner, ICON * scale,
                    TypedValue.COMPLEX_UNIT_DIP);
            if (options.opacity <= 0) {
                spinner.setColorStateList(R.id.md_refresh_spinner, "setIndeterminateTintList",
                        android.content.res.ColorStateList.valueOf(Color.WHITE));
            } else {
                spinner.setColorStateList(R.id.md_refresh_spinner, "setIndeterminateTintList",
                        Role.TEXT.resource);
            }
            views.addStableView(R.id.md_refresh_button, spinner, 1);
        }
        views.setContentDescription(R.id.md_refresh_button, !status.isEmpty() ? status
                : resources.getString(R.string.widget_material_refresh));
        if (options.opacity <= 0) {
            views.setTextColor(R.id.md_title, Color.WHITE);
            views.setTextColor(R.id.md_status, Color.WHITE);
            views.setTextColor(R.id.md_updated, Color.WHITE);
            views.setInt(R.id.md_logo, "setColorFilter", classic ? Color.TRANSPARENT : Color.WHITE);
            views.setInt(R.id.md_refresh, "setColorFilter", Color.WHITE);
            return;
        }
        color(R.id.md_title, "setTextColor", Role.TEXT);
        color(R.id.md_status, "setTextColor", Role.SECONDARY);
        color(R.id.md_updated, "setTextColor", Role.SECONDARY);
        if (classic) {
            views.setInt(R.id.md_logo, "setColorFilter", Color.TRANSPARENT);
        } else {
            color(R.id.md_logo, "setColorFilter", Role.ACCENT);
        }
        color(R.id.md_refresh, "setColorFilter", Role.TEXT);
    }

    private CharSequence metadataText(String text, boolean medium) {
        if (!medium || text.isEmpty()) {
            return text;
        }
        SpannableString value = new SpannableString(text);
        value.setSpan(new TypefaceSpan("sans-serif-medium"), 0, value.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return value;
    }

    private void renderPanel(int slot, int item, int appWidgetId) {
        String key = item < keys.size() ? keys.get(item) : null;
        WidgetMeter meter = new WidgetMeter(context, key, options, state);
        views.setOnClickPendingIntent(PANELS[slot],
                WidgetActions.openSection(context, appWidgetId, meter.dashboardSection));
        UsageWindow window = meter.window;
        long resetAt = meter.resetAtMillis;
        boolean reset = WidgetMeters.NEXT_RESET.equals(key);
        String value = reset ? UsageCardFormat.bareReset(resources, resetAt, state.nowMillis)
                : meter.value;

        color(PANEL_BACKGROUNDS[slot], "setColorFilter", Role.PANEL);
        if (slot % 2 == 1) {
            margin(PANELS[slot], RemoteViews.MARGIN_START, GAP);
        }

        views.setTextViewText(NAMES[slot], meter.title);
        color(NAMES[slot], "setTextColor", Role.TEXT);
        views.setTextViewText(VALUES[slot], value);
        boolean usage = WidgetMeters.FIVE_HOUR.equals(key) || WidgetMeters.WEEKLY.equals(key);
        textSize(NAMES[slot], NAME_TEXT);
        textSize(VALUES[slot], VALUE_TEXT);
        color(VALUES[slot], "setTextColor", Role.TEXT);

        boolean balance = WidgetOptions.USAGE_CREDITS.equals(key);
        boolean stackedBalance = balance && balanceNeedsSecondLine(slot, meter.title, value);
        boolean detailVisible = usage || reset || stackedBalance;
        int fixedHeight = Math.max(lineHeight(NAME_TEXT, true), lineHeight(VALUE_TEXT, true))
                + (balance ? 0 : px(BAR_HEIGHT))
                + (detailVisible ? lineHeight(stackedBalance ? VALUE_TEXT : DETAIL_TEXT,
                        stackedBalance) : 0);
        int gapCount = (balance ? 0 : 1) + (detailVisible ? 1 : 0);
        int freeHeight = Math.max(0, panelHeightPx - fixedHeight);
        int vertical = Math.min(px(PANEL_PADDING_V), freeHeight / 2);
        int gap = gapCount == 0 ? 0 : Math.min(Math.round(PANEL_GAP
                * resources.getDisplayMetrics().density), (freeHeight - 2 * vertical) / gapCount);
        int horizontal = px(PANEL_PADDING_H);
        views.setViewPadding(CONTENTS[slot], horizontal, vertical, horizontal, vertical);
        panelGap(BARS[slot], gap);
        panelGap(DETAILS[slot], gap);
        views.setViewVisibility(VALUES[slot], stackedBalance ? View.GONE : View.VISIBLE);
        views.setViewVisibility(BARS[slot], balance ? View.GONE : View.VISIBLE);
        if (!balance) {
            renderBar(slot, meter, usage);
        }
        views.setViewVisibility(DETAILS[slot], detailVisible
                ? View.VISIBLE : View.GONE);
        String detail = stackedBalance ? value : reset
                ? resources.getString(R.string.widget_material_reset_credits, state.availableCredits())
                : resetText(window, resetAt);
        if (stackedBalance) {
            SpannableString amount = new SpannableString(detail);
            amount.setSpan(new StyleSpan(Typeface.BOLD), 0, amount.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            views.setTextViewText(RESETS[slot], amount);
        } else {
            views.setTextViewText(RESETS[slot], detail);
        }
        textSize(RESETS[slot], stackedBalance ? VALUE_TEXT : DETAIL_TEXT);
        color(RESETS[slot], "setTextColor", stackedBalance ? Role.TEXT : Role.SECONDARY);
    }

    private boolean balanceNeedsSecondLine(int slot, String title, String value) {
        float density = resources.getDisplayMetrics().density;
        int rowColumns = itemForSlot(slot / 2 * 2 + 1) >= 0 ? 2 : 1;
        float gap = rowColumns == 1 ? 0f : px(GAP);
        float available = (widthDp * density - 2 * px(PADDING) - gap) / rowColumns
                - 2 * px(PANEL_PADDING_H);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(NAME_TEXT * textScale * density);
        float nameWidth = paint.measureText(title);
        paint.setTextSize(VALUE_TEXT * textScale * density);
        // The XML name/value margin is fixed at 8dp.
        return nameWidth + 8f * density + paint.measureText(value) > available;
    }

    private void renderBar(int slot, WidgetMeter meter, boolean usage) {
        int progress = meter.progress;
        views.setViewLayoutHeight(BARS[slot], BAR_HEIGHT * scale,
                TypedValue.COMPLEX_UNIT_DIP);
        WidgetGraphics.tintTrack(views, TRACKS[slot]);
        boolean classic = OneUiWidgetAppearance.classicPalette(options.colorStyle);
        int fillColor = WidgetUsageColors.color(meter.window, usage);
        boolean gradient = classic && fillColor == R.color.widget_classic_progress_normal;
        views.setImageViewResource(FILLS[slot], gradient ? R.drawable.widget_classic_capsule_fill
                : R.drawable.widget_card_capsule_fill);
        views.setColorStateList(FILLS[slot], "setImageTintList", null);
        if (gradient) {
            views.setInt(FILLS[slot], "setColorFilter", Color.TRANSPARENT);
        } else {
            views.setColor(FILLS[slot], "setColorFilter", classic ? fillColor
                    : WidgetUsageColors.materialColor(meter.window, usage));
        }
        int level = 0;
        if (progress > 0) {
            // Never thinner than a dot, so a nearly empty window still shows its colour.
            float fraction = Math.max(progress / 100f, BAR_HEIGHT * scale / panelContentWidth);
            level = Math.round(Math.min(1f, fraction) * LEVEL_MAX);
        }
        views.setInt(FILLS[slot], "setImageLevel", level);
    }

    private String resetText(UsageWindow window, long resetAt) {
        return window == null || resetAt <= 0L ? UsageCardFormat.MISSING
                : resources.getString(R.string.widget_material_resets_at,
                        UsageCardFormat.absoluteReset(resetAt));
    }

    private void bindActions(int appWidgetId) {
        PendingIntent refresh = WidgetActions.refresh(context, appWidgetId);
        views.setOnClickPendingIntent(android.R.id.background,
                WidgetActions.openApp(context, appWidgetId));
        views.setOnClickPendingIntent(R.id.md_refresh_button, refresh);
        views.setOnClickPendingIntent(R.id.md_status, refresh);
    }

    /**
     * Applies a colour role. The launcher resolves the resource, so wallpaper
     * colours and night mode stay live without a re-render.
     */
    private void color(int viewId, String method, Role role) {
        views.setColor(viewId, method, role.resource);
    }

    private void textSize(int viewId, float sizeDp) {
        views.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_DIP, sizeDp * textScale);
    }

    private int lineHeight(float sizeDp, boolean bold) {
        return lineHeightPx(sizeDp * textScale * resources.getDisplayMetrics().density,
                bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    private static int lineHeightPx(float size, Typeface typeface) {
        Paint paint = new Paint();
        paint.setTypeface(typeface);
        paint.setTextSize(size);
        Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
        return metrics.descent - metrics.ascent;
    }

    private void panelGap(int viewId, int pixels) {
        views.setViewLayoutMargin(viewId, RemoteViews.MARGIN_TOP, pixels,
                TypedValue.COMPLEX_UNIT_PX);
    }

    private void margin(int viewId, int which, float valueDp) {
        views.setViewLayoutMargin(viewId, which, valueDp * scale, TypedValue.COMPLEX_UNIT_DIP);
    }

    private int px(float dp) {
        return Math.round(dp * scale * resources.getDisplayMetrics().density);
    }
}
