package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.WidgetMeters;

import android.annotation.SuppressLint;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SeslSeekBar;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import dev.oneuiproject.oneui.widget.CardItemView;
import dev.oneuiproject.oneui.widget.RoundedLinearLayout;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Per-widget editor for the four upstream meters, their order, and background opacity.
 * The preview renders the real RemoteViews at the widget's current size.
 */
public final class WidgetConfigActivity extends AppCompatActivity {
    private static final String OPTION_MIN_WIDTH = AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH;
    private static final String OPTION_MAX_WIDTH = AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH;
    private static final String OPTION_MIN_HEIGHT = AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT;
    private static final String OPTION_MAX_HEIGHT = AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT;
    private static final String STATE_COLOR_STYLE = "widget_color_style";
    /** A 2x2 cell on a typical phone grid, used before the launcher reports a size. */
    private static final int DEFAULT_PREVIEW_DP = 170;
    private static final float DIMMED_ALPHA = 0.45f;
    private static final float DRAGGED_ALPHA = 0.85f;

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean dark;
    private Bundle widgetSize = new Bundle();
    private UsageSnapshot snapshot;
    private WidgetOptions saved;

    private final List<String> windowOrder = new ArrayList<>();
    private final LinkedHashSet<String> selectedWindows = new LinkedHashSet<>();

    private FrameLayout previewContainer;
    private RecyclerView windowList;
    private SwitchCompat backgroundSwitch;
    private SeslSeekBar opacitySlider;
    private View opacityControl;
    private String colorStyle;
    private CardItemView colorStyleRow;

    @Override
    protected void onCreate(Bundle state) {
        Ui.applySelectedTheme(this);
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        appWidgetId = state == null ? AppWidgetManager.INVALID_APPWIDGET_ID
                : state.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                        AppWidgetManager.INVALID_APPWIDGET_ID);
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            appWidgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Toast.makeText(this, R.string.widget_editor_no_widget, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setResult(RESULT_CANCELED, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                appWidgetId));
        dark = Ui.isDark(this);
        colorStyle = state == null ? null : state.getString(STATE_COLOR_STYLE);
        refreshWidgetSize();
        build();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        state.putString(STATE_COLOR_STYLE, colorStyle);
        super.onSaveInstanceState(state);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void build() {
        Ui.ConfigPage page = Ui.installConfigPage(this, getString(R.string.widget_editor_title));
        previewContainer = page.preview;
        LinearLayout content = page.content;
        saved = AppPreferences.loadWidgetOptions(this, appWidgetId);
        snapshot = SecureTokenStore.isSignedIn(this) ? AppPreferences.loadSnapshot(this) : null;
        loadSelection();

        content.addView(Ui.separator(this, getString(R.string.widget_editor_windows)));
        content.addView(buildWindowCard());
        content.addView(Ui.separator(this, getString(R.string.widget_editor_appearance)));
        content.addView(buildAppearanceCard());
        TextView refreshHint = Ui.text(this, getString(
                WidgetRenderer.oneRow(this, appWidgetId, widgetSize, previewHeightDp())
                        ? R.string.widget_editor_refresh_hint_dial
                        : R.string.widget_editor_refresh_hint), 13, Ui.secondaryText(dark));
        refreshHint.setPadding(Ui.dp(this, 24), Ui.dp(this, 12), Ui.dp(this, 24),
                Ui.dp(this, 16));
        content.addView(refreshHint);

        page.cancel.setOnClickListener(view -> finish());
        page.save.setOnClickListener(view -> save());
        updateSliderVisuals();
        renderPreview();
    }

    // ---------------------------------------------------------------------------------------
    // Usage windows

    /** Keep all controls available even when a meter is disabled or has no data. */
    private void loadSelection() {
        boolean dial = WidgetRenderer.oneRow(this, appWidgetId, widgetSize, previewHeightDp());
        List<String> selected = WidgetRenderer.selectedKeys(saved, snapshot, dial);
        windowOrder.clear();
        List<String> catalog = new ArrayList<>(WidgetOptions.availableMeterKeys());
        if (dial) {
            catalog.remove(WidgetOptions.USAGE_CREDITS);
        }
        windowOrder.addAll(selected);
        for (String key : catalog) {
            if (!windowOrder.contains(key)) {
                windowOrder.add(key);
            }
        }
        selectedWindows.clear();
        selectedWindows.addAll(selected);
    }

    private List<String> orderedSelection() {
        List<String> ordered = new ArrayList<>();
        for (String key : windowOrder) {
            if (selectedWindows.contains(key)) {
                ordered.add(key);
            }
        }
        return ordered;
    }

    private RoundedLinearLayout buildWindowCard() {
        RoundedLinearLayout card = Ui.seslRowCard(this, dark);
        TextView hint = Ui.text(this, getString(R.string.widget_editor_windows_hint), 13,
                Ui.secondaryText(dark));
        hint.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 8));
        card.addView(hint);
        windowList = new RecyclerView(this);
        windowList.setLayoutManager(new LinearLayoutManager(this));
        windowList.setNestedScrollingEnabled(false);
        ItemTouchHelper touchHelper = new ItemTouchHelper(new ReorderCallback());
        windowList.setAdapter(new WindowAdapter(touchHelper));
        touchHelper.attachToRecyclerView(windowList);
        card.addView(windowList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    /** Applies a switch change, keeping between one and four windows selected. */
    private boolean setSelected(String key, boolean selected) {
        if (selected) {
            if (selectedWindows.size() >= windowOrder.size()) {
                Toast.makeText(this, R.string.widget_editor_windows_limit, Toast.LENGTH_SHORT)
                        .show();
                return false;
            }
            selectedWindows.add(key);
        } else {
            if (selectedWindows.size() <= 1) {
                Toast.makeText(this, R.string.widget_editor_windows_minimum,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
            selectedWindows.remove(key);
        }
        renderPreview();
        return true;
    }

    private void lockListScrolling() {
        if (windowList != null && windowList.getParent() != null) {
            windowList.getParent().requestDisallowInterceptTouchEvent(true);
        }
    }

    private String editorMeterTitle(String key) {
        if (WidgetMeters.FIVE_HOUR.equals(key)) {
            return getString(R.string.widget_editor_five_hour_limit);
        }
        if (WidgetMeters.WEEKLY.equals(key)) {
            return getString(R.string.widget_editor_weekly_limit);
        }
        if (WidgetOptions.USAGE_CREDITS.equals(key)) {
            return getString(R.string.widget_editor_remaining_credits);
        }
        return getString(R.string.widget_editor_reset_time);
    }

    private String editorMeterSummary(String key) {
        boolean dial = WidgetRenderer.oneRow(this, appWidgetId, widgetSize, previewHeightDp());
        if (WidgetMeters.FIVE_HOUR.equals(key)) {
            return getString(dial ? R.string.widget_editor_five_hour_summary_dial
                    : R.string.dashboard_section_five_hour_summary);
        }
        if (WidgetMeters.WEEKLY.equals(key)) {
            return getString(dial ? R.string.widget_editor_weekly_summary_dial
                    : R.string.dashboard_section_weekly_summary);
        }
        if (WidgetOptions.USAGE_CREDITS.equals(key)) {
            return getString(R.string.dashboard_credits_purchased);
        }
        return getString(dial ? R.string.widget_editor_reset_summary_dial
                : R.string.widget_editor_reset_summary_card);
    }

    private final class WindowAdapter extends RecyclerView.Adapter<WindowHolder> {
        private final ItemTouchHelper touchHelper;

        WindowAdapter(ItemTouchHelper touchHelper) {
            this.touchHelper = touchHelper;
        }

        @Override
        public WindowHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            WidgetConfigActivity activity = WidgetConfigActivity.this;
            LinearLayout row = Ui.horizontal(activity, Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.dp(activity, 64));
            row.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 8), Ui.dp(activity, 12),
                    Ui.dp(activity, 8));
            row.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout labels = new LinearLayout(activity);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView title = Ui.text(activity, "", 17f, Ui.mainText(activity, dark));
            TextView summary = Ui.text(activity, "", 13f, Ui.secondaryText(dark));
            labels.addView(title);
            labels.addView(summary);
            row.addView(labels, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            SwitchCompat toggle = new SwitchCompat(activity);
            toggle.setContentDescription(getString(R.string.widget_editor_show));
            LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            toggleParams.setMargins(Ui.dp(activity, 8), 0, Ui.dp(activity, 4), 0);
            row.addView(toggle, toggleParams);

            ImageView handle = new ImageView(activity);
            handle.setImageResource(R.drawable.ic_ms_drag_handle);
            handle.setImageTintList(ColorStateList.valueOf(Ui.mainText(activity, dark)));
            handle.setContentDescription(getString(R.string.widget_editor_reorder));
            int padding = Ui.dp(activity, 12);
            handle.setPadding(padding, padding, padding, padding);
            row.addView(handle, new LinearLayout.LayoutParams(Ui.dp(activity, 48),
                    Ui.dp(activity, 48)));

            WindowHolder holder = new WindowHolder(row, title, summary, toggle, handle);
            bindDragHandle(holder);
            return holder;
        }

        @SuppressLint("ClickableViewAccessibility")
        private void bindDragHandle(WindowHolder holder) {
            holder.handle.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    lockListScrolling();
                    touchHelper.startDrag(holder);
                    return true;
                }
                return false;
            });
        }

        @Override
        public void onBindViewHolder(WindowHolder holder, int position) {
            String key = windowOrder.get(position);
            holder.title.setText(editorMeterTitle(key));
            String description = editorMeterSummary(key);
            WidgetMeter meter = new WidgetMeter(WidgetConfigActivity.this, key, saved,
                    new UsageCardState(snapshot != null, snapshot, null, "",
                            System.currentTimeMillis()));
            holder.summary.setText(UsageCardFormat.MISSING.equals(meter.value)
                    ? getString(R.string.widget_editor_window_missing, description) : description);
            holder.summary.setVisibility(View.VISIBLE);
            holder.toggle.setOnCheckedChangeListener(null);
            boolean selected = selectedWindows.contains(key);
            holder.toggle.setChecked(selected);
            holder.title.setAlpha(selected ? 1f : DIMMED_ALPHA);
            holder.toggle.setOnCheckedChangeListener((button, checked) -> {
                int bound = holder.getBindingAdapterPosition();
                if (bound < 0 || bound >= windowOrder.size()) {
                    return;
                }
                if (!setSelected(windowOrder.get(bound), checked)) {
                    button.setChecked(!checked);
                    return;
                }
                holder.title.setAlpha(checked ? 1f : DIMMED_ALPHA);
            });
        }

        @Override
        public int getItemCount() {
            return windowOrder.size();
        }
    }

    private static final class WindowHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView summary;
        final SwitchCompat toggle;
        final ImageView handle;

        WindowHolder(View row, TextView title, TextView summary, SwitchCompat toggle,
                ImageView handle) {
            super(row);
            this.title = title;
            this.summary = summary;
            this.toggle = toggle;
            this.handle = handle;
        }
    }

    private final class ReorderCallback extends ItemTouchHelper.Callback {
        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder holder) {
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return true;
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder from,
                RecyclerView.ViewHolder to) {
            int fromPosition = from.getBindingAdapterPosition();
            int toPosition = to.getBindingAdapterPosition();
            if (fromPosition < 0 || toPosition < 0) {
                return false;
            }
            windowOrder.add(toPosition, windowOrder.remove(fromPosition));
            recyclerView.getAdapter().notifyItemMoved(fromPosition, toPosition);
            return true;
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder holder, int actionState) {
            super.onSelectedChanged(holder, actionState);
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && holder != null) {
                lockListScrolling();
                holder.itemView.setAlpha(DRAGGED_ALPHA);
                holder.itemView.setElevation(Ui.dp(holder.itemView.getContext(), 4));
            }
        }

        @Override
        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder holder) {
            super.clearView(recyclerView, holder);
            holder.itemView.setAlpha(1f);
            holder.itemView.setElevation(0f);
            renderPreview();
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder holder, int direction) {
        }
    }

    // ---------------------------------------------------------------------------------------
    // Appearance

    private RoundedLinearLayout buildAppearanceCard() {
        RoundedLinearLayout card = Ui.seslRowCard(this, dark);

        backgroundSwitch = new SwitchCompat(this);
        backgroundSwitch.setChecked(saved.opacity > 0);
        card.addView(switchRow(getString(R.string.widget_background), backgroundSwitch));
        card.addView(divider(), dividerParams());
        colorStyle = colorStyle == null ? saved.colorStyle
                : saved.withColorStyle(colorStyle).colorStyle;
        colorStyleRow = Ui.actionRow(this, getString(R.string.widget_color_style),
                colorStyleLabel(), 0, view -> showColorStyleChoices());
        card.addView(colorStyleRow);
        View opacityDivider = divider();
        card.addView(opacityDivider, dividerParams());
        opacityControl = LayoutInflater.from(this).inflate(R.layout.view_widget_opacity, card,
                false);
        opacityControl.setTag(opacityDivider);
        card.addView(opacityControl);
        opacitySlider = opacityControl.findViewById(R.id.opacity_slider);
        opacitySlider.setProgress(WidgetOptions.opacityIndex(saved.opacity));
        opacitySlider.setAlpha(0f);
        if (opacitySlider.getProgressDrawable() != null) {
            opacitySlider.getProgressDrawable().setAlpha(0);
        }
        applyBackgroundEnabled(backgroundSwitch.isChecked());
        backgroundSwitch.setOnCheckedChangeListener((button, checked) -> {
            applyBackgroundEnabled(checked);
            updateSliderVisuals();
            renderPreview();
        });
        opacitySlider.setOnSeekBarChangeListener(new SeslSeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeslSeekBar seekBar, int progress, boolean fromUser) {
                updateSliderVisuals();
                renderPreview();
            }

            @Override
            public void onStartTrackingTouch(SeslSeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeslSeekBar seekBar) {
            }
        });
        return card;
    }

    private String colorStyleLabel() {
        if (WidgetOptions.COLOR_NATIVE.equals(colorStyle)) {
            return getString(R.string.widget_color_style_native);
        }
        if (WidgetOptions.COLOR_CLASSIC.equals(colorStyle)) {
            return getString(R.string.widget_color_style_classic);
        }
        return getString(R.string.widget_color_style_auto);
    }

    private void showColorStyleChoices() {
        String[] values = {WidgetOptions.COLOR_AUTO, WidgetOptions.COLOR_NATIVE,
                WidgetOptions.COLOR_CLASSIC};
        String[] labels = {getString(R.string.widget_color_style_auto),
                getString(R.string.widget_color_style_native),
                getString(R.string.widget_color_style_classic)};
        int selected = WidgetOptions.COLOR_NATIVE.equals(colorStyle) ? 1
                : WidgetOptions.COLOR_CLASSIC.equals(colorStyle) ? 2 : 0;
        new AlertDialog.Builder(this)
                .setTitle(R.string.widget_color_style)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    colorStyle = values[which];
                    colorStyleRow.setSummary(colorStyleLabel());
                    renderPreview();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private LinearLayout switchRow(String title, SwitchCompat toggle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout content = new LinearLayout(this);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setMinimumHeight(Ui.dp(this, 64));
        content.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), 0);
        content.addView(Ui.text(this, title, 18, Ui.mainText(this, dark)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.setOnClickListener(view -> toggle.toggle());
        row.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(Ui.divider(dark));
        return divider;
    }

    private LinearLayout.LayoutParams dividerParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1));
        params.setMargins(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        return params;
    }

    private void applyBackgroundEnabled(boolean enabled) {
        int visibility = enabled ? View.VISIBLE : View.GONE;
        opacityControl.setVisibility(visibility);
        Object divider = opacityControl.getTag();
        if (divider instanceof View) {
            ((View) divider).setVisibility(visibility);
        }
        opacitySlider.setEnabled(enabled);
    }

    private void updateSliderVisuals() {
        if (opacityControl == null || opacityControl.getVisibility() != View.VISIBLE) {
            return;
        }
        int[] ticks = {R.id.opacity_tick_0, R.id.opacity_tick_1, R.id.opacity_tick_2};
        int level = Math.max(0, Math.min(ticks.length - 1, opacitySlider.getProgress()));
        for (int index = 0; index < ticks.length; index++) {
            opacityControl.findViewById(ticks[index]).setAlpha(index == level ? 0f : 1f);
        }
        View thumb = opacityControl.findViewById(R.id.opacity_thumb_visual);
        GradientDrawable thumbBackground = new GradientDrawable();
        thumbBackground.setShape(GradientDrawable.OVAL);
        thumbBackground.setColor(dark ? 0xFF1F1F22 : 0xFFFCFCFF);
        thumbBackground.setStroke(Ui.dp(this, 2), Ui.accent(this, dark));
        thumb.setBackground(thumbBackground);
        thumb.post(() -> {
            View tick = opacityControl.findViewById(ticks[level]);
            thumb.setTranslationX(tick.getX() + tick.getWidth() / 2f - thumb.getWidth() / 2f);
        });
    }

    // ---------------------------------------------------------------------------------------
    // Preview and save

    private WidgetOptions currentOptions() {
        int opacity = backgroundSwitch.isChecked()
                ? WidgetOptions.OPACITY_LEVELS[Math.max(0, Math.min(
                        WidgetOptions.OPACITY_LEVELS.length - 1,
                        opacitySlider.getProgress()))]
                : 0;
        return new WidgetOptions(saved.layout, WidgetOptions.DENSITY_AUTO,
                WidgetOptions.SURFACE_ONE_UI, WidgetOptions.GRAPHIC_AUTO,
                WidgetOptions.THEME_SYSTEM, WidgetOptions.ACCENT_APP,
                opacity, WidgetOptions.RESET_HIDDEN, saved.displayMode, WidgetOptions.METRIC_BOTH,
                false, false, false, false, false, false)
                .withColorStyle(colorStyle)
                .withPercentSymbol(saved.showPercentSymbol)
                .withVisibleMeters(WidgetMeters.serialize(orderedSelection()));
    }

    private void refreshWidgetSize() {
        try {
            Bundle options = AppWidgetManager.getInstance(this).getAppWidgetOptions(appWidgetId);
            if (options != null && !options.isEmpty()) {
                widgetSize = new Bundle(options);
            }
        } catch (RuntimeException ignored) {
            // Keep the last known size; the preview falls back to a 2x2 cell.
        }
    }

    /** Portrait size of the placed widget in dp: min width by max height. */
    private int previewWidthDp() {
        return positiveOption(OPTION_MIN_WIDTH, OPTION_MAX_WIDTH, DEFAULT_PREVIEW_DP);
    }

    private int previewHeightDp() {
        return positiveOption(OPTION_MAX_HEIGHT, OPTION_MIN_HEIGHT, DEFAULT_PREVIEW_DP);
    }

    private int positiveOption(String first, String second, int fallback) {
        int value = widgetSize.getInt(first, 0);
        if (value <= 0) {
            value = widgetSize.getInt(second, 0);
        }
        return value > 0 ? value : fallback;
    }

    /** Renders the real widget at its own size, scaled down to fit the preview pane. */
    private void renderPreview() {
        if (previewContainer == null || opacitySlider == null) {
            return;
        }
        previewContainer.post(() -> {
            try {
                refreshWidgetSize();
                int widthDp = previewWidthDp();
                int heightDp = previewHeightDp();
                RemoteViews remote = WidgetRenderer.buildPreview(this, appWidgetId,
                        currentOptions(), widthDp, heightDp);
                FrameLayout surface = new FrameLayout(this);
                surface.setClipToOutline(true);
                // Use the application inflater so AppCompat does not substitute its views;
                // RemoteViews only accepts framework widgets.
                View widget = remote.apply(getApplicationContext(), surface);
                widget.setClickable(false);
                FrameLayout.LayoutParams widgetParams = ColorOsWidgetAppearance.isStockLauncher(this)
                        ? new FrameLayout.LayoutParams(widget.getLayoutParams())
                        : new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT);
                widgetParams.gravity = Gravity.CENTER;
                surface.addView(widget, widgetParams);
                surface.setOnClickListener(view -> { });

                int width = Ui.dp(this, widthDp);
                int height = Ui.dp(this, heightDp);
                int inset = Ui.dp(this, 14f);
                float fit = Math.min(1f, Math.min(
                        (previewContainer.getWidth() - 2f * inset) / width,
                        (previewContainer.getHeight() - 2f * inset) / height));
                surface.setScaleX(fit);
                surface.setScaleY(fit);
                previewContainer.removeAllViews();
                ImageView backdrop = new ImageView(this);
                backdrop.setImageResource(R.drawable.codex_meter_icon_bg);
                backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
                backdrop.setContentDescription(null);
                previewContainer.addView(backdrop, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                previewContainer.addView(surface,
                        new FrameLayout.LayoutParams(width, height, Gravity.CENTER));
            } catch (RuntimeException exception) {
                Log.w("CodexMeterPreview", "Unable to render widget preview", exception);
            }
        });
    }

    private void save() {
        if (!AppPreferences.saveWidgetOptionsAndFollow(this, appWidgetId, currentOptions())) {
            Toast.makeText(this, R.string.widget_editor_save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        AppPreferences.saveWidgetTapAction(this, appWidgetId, WidgetOptions.TAP_OPEN_APP);
        WidgetRenderer.update(this, AppWidgetManager.getInstance(this), appWidgetId);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                appWidgetId));
        Toast.makeText(this, R.string.widget_editor_saved, Toast.LENGTH_SHORT).show();
        finish();
    }
}
