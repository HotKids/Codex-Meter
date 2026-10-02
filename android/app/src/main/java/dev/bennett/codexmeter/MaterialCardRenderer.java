package dev.bennett.codexmeter;

import android.app.PendingIntent;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import androidx.annotation.RequiresApi;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * The "Clear" home-widget style: a Material You card with a Codex header and refresh button
 * above one tonal panel per usage window (title, remaining percentage, a thick progress bar,
 * when the window resets and how much of it has elapsed). Narrow widgets stack the first two
 * windows; wide widgets lay up to four out in two columns. Panels share the height, so the card
 * fills any size; text and spacing scale from a reference panel. On Android 12+ the colours are
 * resolved by the launcher from the wallpaper palette and follow its night mode.
 */
final class MaterialCardRenderer {
    /** Reference metrics in dp at scale 1; PANEL_HEIGHT fits a panel's three rows. */
    private static final float PANEL_WIDTH = 170f;
    private static final float PANEL_HEIGHT = 74f;
    private static final float HEADER_HEIGHT = 24f;
    private static final float PADDING = 12f;
    private static final float FIRST_ROW_GAP = 10f;
    private static final float GAP = 8f;
    private static final float ICON = 22f;
    private static final float TITLE_TEXT = 17f;
    private static final float STATUS_TEXT = 10.5f;
    private static final float NAME_TEXT = 15f;
    private static final float VALUE_TEXT = 15.5f;
    private static final float DETAIL_TEXT = 12f;
    private static final float PANEL_PADDING_H = 12f;
    private static final float PANEL_PADDING_V = 9f;
    /** Space between a panel's title row, bar and detail row. */
    private static final float PANEL_GAP = 7f;
    private static final float BAR_HEIGHT = 9f;
    private static final float MIN_SCALE = 0.7f;
    private static final float MAX_SCALE = 1.25f;
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
    private static final int[] ELAPSED = {R.id.md_elapsed_0, R.id.md_elapsed_1,
            R.id.md_elapsed_2, R.id.md_elapsed_3};

    /** Colour roles; each resource follows the night mode (see widget_material_colors). */
    private enum Role {
        SURFACE(R.color.widget_material_surface),
        PANEL(R.color.widget_material_panel),
        TRACK(R.color.widget_material_track),
        FILL(R.color.widget_material_fill),
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
    private final UsageCardRenderer.Style style;
    private final UsageCardState state;
    private final List<String> keys;
    private final int columns;
    private final int rowCount;
    private final float scale;
    private final float panelContentWidth;

    private MaterialCardRenderer(Context context, UsageCardRenderer.Style style,
            List<String> keys, UsageCardState state, float widthDp, float heightDp) {
        this.context = context;
        this.resources = context.getResources();
        this.style = style;
        this.state = state;
        boolean wide = widthDp >= UsageCardRenderer.MEDIUM_MIN_WIDTH_DP;
        int capacity = wide ? PANELS.length : ROWS.length;
        this.keys = keys.subList(0, Math.min(keys.size(), capacity));
        int shown = Math.max(1, this.keys.size());
        this.columns = wide && shown > 1 ? 2 : 1;
        this.rowCount = (shown + columns - 1) / columns;
        float neededWidth = PADDING * 2f + columns * PANEL_WIDTH + (columns - 1) * GAP;
        float neededHeight = PADDING * 2f + HEADER_HEIGHT + FIRST_ROW_GAP
                + rowCount * PANEL_HEIGHT + (rowCount - 1) * GAP;
        float fit = Math.min(widthDp / neededWidth, heightDp / neededHeight);
        this.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, fit));
        float panelWidth = (widthDp - PADDING * 2f * scale - (columns - 1) * GAP * scale)
                / columns;
        this.panelContentWidth = Math.max(1f, panelWidth - PANEL_PADDING_H * 2f * scale);
        this.views = new RemoteViews(context.getPackageName(), style.transparent()
                ? R.layout.widget_material_shadow : R.layout.widget_material);
    }

    /**
     * Renders the Material card for one host size. {@code keys} is the widget's resolved window
     * selection; narrow cards show the first two and wide cards up to four.
     */
    static RemoteViews build(Context context, int appWidgetId, UsageCardRenderer.Style style,
            List<String> keys, UsageCardState state, float widthDp, float heightDp) {
        MaterialCardRenderer renderer = new MaterialCardRenderer(context, style, keys, state,
                widthDp, heightDp);
        renderer.render(appWidgetId);
        return renderer.views;
    }

    private void render(int appWidgetId) {
        renderSurface();
        renderHeader();
        for (int row = 0; row < ROWS.length; row++) {
            views.setViewVisibility(ROWS[row], row < rowCount ? View.VISIBLE : View.GONE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                margin(ROWS[row], RemoteViews.MARGIN_TOP, row == 0 ? FIRST_ROW_GAP : GAP);
            }
        }
        for (int slot = 0; slot < PANELS.length; slot++) {
            int item = itemForSlot(slot);
            if (item < 0) {
                views.setViewVisibility(PANELS[slot], View.GONE);
                continue;
            }
            views.setViewVisibility(PANELS[slot], View.VISIBLE);
            renderPanel(slot, item);
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
        if (style.transparent()) {
            views.setViewVisibility(R.id.md_surface, View.GONE);
            return;
        }
        views.setViewVisibility(R.id.md_surface, View.VISIBLE);
        color(R.id.md_surface, "setColorFilter", Role.SURFACE);
        views.setInt(R.id.md_surface, "setImageAlpha", Math.round(style.opacity * 2.55f));
    }

    private void renderHeader() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (int icon : new int[] {R.id.md_logo, R.id.md_refresh}) {
                views.setViewLayoutWidth(icon, ICON * scale, TypedValue.COMPLEX_UNIT_DIP);
                views.setViewLayoutHeight(icon, ICON * scale, TypedValue.COMPLEX_UNIT_DIP);
            }
        }
        textSize(R.id.md_title, TITLE_TEXT);
        textSize(R.id.md_status, STATUS_TEXT);
        String status = state.statusMessage(resources, false);
        views.setTextViewText(R.id.md_status, status);
        views.setViewVisibility(R.id.md_status, status.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        if (style.transparent()) {
            views.setTextColor(R.id.md_title, Color.WHITE);
            views.setTextColor(R.id.md_status, Color.WHITE);
            views.setInt(R.id.md_logo, "setColorFilter", Color.WHITE);
            views.setInt(R.id.md_refresh, "setColorFilter", Color.WHITE);
            return;
        }
        color(R.id.md_title, "setTextColor", Role.TEXT);
        color(R.id.md_status, "setTextColor", Role.SECONDARY);
        color(R.id.md_logo, "setColorFilter", Role.ACCENT);
        color(R.id.md_refresh, "setColorFilter", Role.TEXT);
    }

    private void renderPanel(int slot, int item) {
        String key = item < keys.size() ? keys.get(item) : null;
        UsageSnapshot snapshot = state.snapshot;
        UsageWindow window = key == null ? null : UsageCardWindows.window(key, snapshot);
        long resetAt = window == null ? 0L
                : window.effectiveResetAtMillis(state.fetchedAtMillis());
        String title = key == null
                ? resources.getString(R.string.widget_card_current_usage)
                : UsageCardWindows.title(resources, key, snapshot);

        color(PANEL_BACKGROUNDS[slot], "setColorFilter", Role.PANEL);
        int horizontal = px(PANEL_PADDING_H);
        int vertical = px(PANEL_PADDING_V);
        views.setViewPadding(CONTENTS[slot], horizontal, vertical, horizontal, vertical);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && slot % 2 == 1) {
            margin(PANELS[slot], RemoteViews.MARGIN_START, GAP);
        }

        views.setTextViewText(NAMES[slot], title);
        textSize(NAMES[slot], NAME_TEXT);
        color(NAMES[slot], "setTextColor", Role.TEXT);
        views.setTextViewText(VALUES[slot], UsageCardFormat.percent(window));
        textSize(VALUES[slot], VALUE_TEXT);
        color(VALUES[slot], "setTextColor", Role.TEXT);

        renderBar(slot, window);

        views.setTextViewText(RESETS[slot], resetText(window, resetAt));
        textSize(RESETS[slot], DETAIL_TEXT);
        color(RESETS[slot], "setTextColor", Role.SECONDARY);
        String elapsed = elapsedText(window, resetAt);
        if (elapsed.isEmpty()) {
            views.setViewVisibility(ELAPSED[slot], View.GONE);
        } else {
            views.setViewVisibility(ELAPSED[slot], View.VISIBLE);
            views.setTextViewText(ELAPSED[slot], elapsed);
            views.setContentDescription(ELAPSED[slot],
                    resources.getString(R.string.widget_material_elapsed_description, elapsed));
            textSize(ELAPSED[slot], DETAIL_TEXT);
            color(ELAPSED[slot], "setTextColor", Role.SECONDARY);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            margin(DETAILS[slot], RemoteViews.MARGIN_TOP, PANEL_GAP);
        }
    }

    private void renderBar(int slot, UsageWindow window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewLayoutHeight(BARS[slot], BAR_HEIGHT * scale,
                    TypedValue.COMPLEX_UNIT_DIP);
            margin(BARS[slot], RemoteViews.MARGIN_TOP, PANEL_GAP);
        }
        color(TRACKS[slot], "setColorFilter", Role.TRACK);
        color(FILLS[slot], "setColorFilter", Role.FILL);
        int remaining = window == null ? 0 : window.remainingPercent();
        int level = 0;
        if (remaining > 0) {
            // Never thinner than a dot, so a nearly empty window still shows its colour.
            float fraction = Math.max(remaining / 100f, BAR_HEIGHT * scale / panelContentWidth);
            level = Math.round(Math.min(1f, fraction) * LEVEL_MAX);
        }
        views.setInt(FILLS[slot], "setImageLevel", level);
    }

    /** "3小时59分后重置" within a day, otherwise the weekday and time ("周二下午12:51 重置"). */
    private String resetText(UsageWindow window, long resetAt) {
        if (window == null || resetAt <= 0L) {
            return UsageCardFormat.MISSING;
        }
        if (resetAt - state.nowMillis < DAY_MILLIS) {
            return UsageCardFormat.relativeReset(resources, resetAt, state.nowMillis);
        }
        Locale locale = resources.getConfiguration().getLocales().get(0);
        String skeleton = DateFormat.is24HourFormat(context) ? "EEEHm" : "EEEhma";
        String pattern = DateFormat.getBestDateTimePattern(locale, skeleton);
        String when = new SimpleDateFormat(pattern, locale).format(new Date(resetAt));
        return resources.getString(R.string.widget_material_resets_at, when);
    }

    /** Share of the window's duration that has already passed, or "" when it is unknown. */
    private String elapsedText(UsageWindow window, long resetAt) {
        if (window == null || resetAt <= 0L || window.windowSeconds <= 0L) {
            return "";
        }
        long length = TimeUnit.SECONDS.toMillis(window.windowSeconds);
        long left = Math.max(0L, Math.min(length, resetAt - state.nowMillis));
        long percent = Math.round((length - left) * 100.0 / length);
        return resources.getString(R.string.widget_material_percent, percent);
    }

    private void bindActions(int appWidgetId) {
        PendingIntent refresh = WidgetActions.refresh(context, appWidgetId);
        views.setOnClickPendingIntent(android.R.id.background,
                WidgetActions.openApp(context, appWidgetId));
        views.setOnClickPendingIntent(R.id.md_refresh, refresh);
        views.setOnClickPendingIntent(R.id.md_status, refresh);
    }

    /**
     * Applies a colour role. On Android 12+ the launcher resolves the resource, so wallpaper
     * colours and night mode stay live without a re-render.
     */
    private void color(int viewId, String method, Role role) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColor(viewId, method, role.resource);
        } else {
            views.setInt(viewId, method, context.getColor(role.resource));
        }
    }

    private void textSize(int viewId, float sizeDp) {
        views.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_DIP, sizeDp * scale);
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private void margin(int viewId, int which, float valueDp) {
        views.setViewLayoutMargin(viewId, which, valueDp * scale, TypedValue.COMPLEX_UNIT_DIP);
    }

    private int px(float dp) {
        return Math.round(dp * scale * resources.getDisplayMetrics().density);
    }
}
