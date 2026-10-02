package dev.bennett.codexmeter;

import android.annotation.SuppressLint;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import dev.oneuiproject.oneui.widget.CardItemView;
import dev.oneuiproject.oneui.widget.RoundedLinearLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** Single-account AI-Usage settings, with the upstream Android 2×1 dial exception. */
public final class WidgetConfigActivity extends AppCompatActivity {
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean dark;
    private WidgetOptions savedAppearance;
    private Spinner referenceStyleSpinner;
    private Spinner refreshSpinner;
    private Spinner previewSpinner;
    private Spinner themeSpinner;
    private Spinner accentSpinner;
    private SwitchCompat backgroundSwitch;
    private CardItemView referenceStyleRow;
    private CardItemView refreshRow;
    private CardItemView previewRow;
    private CardItemView themeRow;
    private CardItemView accentRow;
    private TextView accountSummary;
    private TextView metersHint;
    private FrameLayout previewContainer;
    private RecyclerView metersList;
    private MeterAdapter meterAdapter;
    private ItemTouchHelper meterTouchHelper;
    private UsageSnapshot snapshot;
    private WidgetUsageSnapshot widgetSnapshot;
    private final List<String> meterOrder = new ArrayList<>();
    private final LinkedHashSet<String> selectedMeters = new LinkedHashSet<>();
    // Preserve unsaved choices independently of refreshed API data.
    private String requestedSelection;
    private boolean selectionEdited;
    private SharedPreferences observedPreferences;
    private SharedPreferences observedWindows;
    private SharedPreferences observedRefresh;
    private final SharedPreferences.OnSharedPreferenceChangeListener snapshotListener = (prefs, key) -> {
        if (key == null || "last_snapshot".equals(key) || "reset_credits_snapshot".equals(key)
                || "last_error".equals(key)) runOnUiThread(this::reloadSnapshot);
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener windowsListener = (prefs, key) -> {
        if (key == null || key.equals(WidgetUsageStore.key(this))) runOnUiThread(this::reloadSnapshot);
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener refreshListener = (prefs, key) -> {
        if (key == null || "error".equals(key) || "failed_at".equals(key)) runOnUiThread(this::renderPreview);
    };

    @Override protected void onCreate(Bundle state) {
        Ui.applySelectedTheme(this);
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        appWidgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Toast.makeText(this, R.string.phone_no_widget_was_selected_85c04, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        dark = Ui.isDark(this);
        savedAppearance = AppPreferences.loadWidgetOptions(this, appWidgetId);
        snapshot = AppPreferences.loadSnapshot(this);
        widgetSnapshot = loadWidgetSnapshot();
        requestedSelection = state == null ? savedAppearance.visibleMeters
                : state.getString("selection", savedAppearance.visibleMeters);
        selectionEdited = state != null && state.getBoolean("selection_edited");
        if (state != null && state.getStringArrayList("meter_order") != null)
            meterOrder.addAll(state.getStringArrayList("meter_order"));
        synchronizeWindows();
        build(state);
    }

    @Override protected void onStart() {
        super.onStart();
        if (meterAdapter == null) return;
        observedPreferences = ReferenceWidgetPreferences.preferences(this);
        observedPreferences.registerOnSharedPreferenceChangeListener(snapshotListener);
        observedWindows = WidgetUsageStore.prefs(this);
        observedWindows.registerOnSharedPreferenceChangeListener(windowsListener);
        observedRefresh = WidgetRefreshScheduler.state(this);
        observedRefresh.registerOnSharedPreferenceChangeListener(refreshListener);
        reloadSnapshot();
        WidgetRefreshScheduler.requestInitial(this);
    }

    @Override protected void onResume() {
        super.onResume();
        if (meterAdapter != null) reloadSnapshot();
    }

    @Override protected void onStop() {
        if (observedPreferences != null) {
            observedPreferences.unregisterOnSharedPreferenceChangeListener(snapshotListener);
            observedPreferences = null;
        }
        if (observedWindows != null) {
            observedWindows.unregisterOnSharedPreferenceChangeListener(windowsListener);
            observedWindows = null;
        }
        if (observedRefresh != null) {
            observedRefresh.unregisterOnSharedPreferenceChangeListener(refreshListener);
            observedRefresh = null;
        }
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (meterAdapter == null) return;
        state.putString("selection", requestedSelection);
        state.putBoolean("selection_edited", selectionEdited);
        state.putStringArrayList("meter_order", new ArrayList<>(meterOrder));
        state.putInt("chrome", referenceStyleSpinner.getSelectedItemPosition());
        state.putInt("refresh", refreshSpinner.getSelectedItemPosition());
        state.putInt("preview", previewSpinner.getSelectedItemPosition());
        state.putInt("theme", themeSpinner.getSelectedItemPosition());
        state.putInt("accent", accentSpinner.getSelectedItemPosition());
        state.putBoolean("background", backgroundSwitch.isChecked());
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    private void build(Bundle state) {
        Ui.ConfigPage page = Ui.installConfigPage(this, getString(R.string.phone_customize_widget_65d95));
        previewContainer = page.preview;
        LinearLayout content = page.content;
        RoundedLinearLayout previewCard = Ui.seslRowCard(this, dark);
        previewSpinner = spinner(R.array.reference_preview_labels, initialPreview());
        previewRow = optionRow(previewCard, R.string.reference_preview, previewSpinner,
                R.array.reference_preview_labels, false);
        content.addView(previewCard);

        content.addView(Ui.separator(this, getString(R.string.reference_account)));
        RoundedLinearLayout accountCard = Ui.seslRowCard(this, dark);
        accountSummary = Ui.text(this, "", 15, Ui.secondaryText(dark));
        accountSummary.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16));
        accountCard.addView(accountSummary);
        content.addView(accountCard);

        content.addView(Ui.separator(this, getString(R.string.reference_windows)));
        RoundedLinearLayout windowsCard = Ui.seslRowCard(this, dark);
        metersHint = Ui.text(this, getString(R.string.reference_window_help), 13, Ui.secondaryText(dark));
        metersHint.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 8));
        windowsCard.addView(metersHint);
        metersList = new RecyclerView(this);
        metersList.setLayoutManager(new LinearLayoutManager(this));
        metersList.setNestedScrollingEnabled(false);
        meterAdapter = new MeterAdapter();
        metersList.setAdapter(meterAdapter);
        meterTouchHelper = new ItemTouchHelper(new MeterReorderCallback());
        meterTouchHelper.attachToRecyclerView(metersList);
        windowsCard.addView(metersList, new LinearLayout.LayoutParams(-1, -2));
        content.addView(windowsCard);

        content.addView(Ui.separator(this, getString(R.string.reference_style)));
        RoundedLinearLayout styleCard = Ui.seslRowCard(this, dark);
        referenceStyleSpinner = spinner(R.array.reference_style_labels,
                WidgetOptions.REFERENCE_CLEAR.equals(savedAppearance.referenceStyle) ? 1 : 0);
        referenceStyleRow = optionRow(styleCard, R.string.reference_style, referenceStyleSpinner,
                R.array.reference_style_labels, false);
        note(styleCard, R.string.reference_style_help);
        content.addView(styleCard);

        content.addView(Ui.separator(this, getString(R.string.reference_refresh)));
        RoundedLinearLayout refreshCard = Ui.seslRowCard(this, dark);
        int interval = ReferenceWidgetPreferences.refreshMinutes(this);
        int index = 0;
        for (int i = 0; i < ReferenceWidgetPreferences.REFRESH_MINUTES.length; i++)
            if (ReferenceWidgetPreferences.REFRESH_MINUTES[i] == interval) index = i;
        refreshSpinner = spinner(R.array.reference_refresh_labels, index);
        refreshRow = optionRow(refreshCard, R.string.reference_refresh_interval, refreshSpinner,
                R.array.reference_refresh_labels, false);
        note(refreshCard, R.string.reference_refresh_help);
        content.addView(refreshCard);

        content.addView(Ui.separator(this, getString(R.string.phone_appearance_41def)));
        RoundedLinearLayout appearanceCard = Ui.seslRowCard(this, dark);
        themeSpinner = Ui.spinner(this, WidgetOptionCatalog.labels(this, WidgetOptionCatalog.THEME_LABELS), dark);
        accentSpinner = Ui.spinner(this, WidgetOptionCatalog.labels(this, WidgetOptionCatalog.ACCENT_LABELS), dark);
        WidgetOptionCatalog.selectString(themeSpinner, WidgetOptionCatalog.THEME_VALUES, savedAppearance.theme);
        WidgetOptionCatalog.selectString(accentSpinner, WidgetOptionCatalog.ACCENT_VALUES, savedAppearance.accent);
        themeRow = optionRow(appearanceCard, R.string.phone_theme_a797e, themeSpinner,
                WidgetOptionCatalog.labels(this, WidgetOptionCatalog.THEME_LABELS), false);
        backgroundSwitch = new SwitchCompat(this);
        backgroundSwitch.setChecked(savedAppearance.opacity > 0);
        LinearLayout backgroundRow = new LinearLayout(this);
        backgroundRow.setGravity(Gravity.CENTER_VERTICAL);
        backgroundRow.setMinimumHeight(Ui.dp(this, 64));
        backgroundRow.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), 0);
        backgroundRow.addView(Ui.text(this, getString(R.string.widget_background), 18, Ui.mainText(dark)),
                new LinearLayout.LayoutParams(0, -2, 1));
        backgroundRow.addView(backgroundSwitch);
        backgroundRow.setOnClickListener(view -> backgroundSwitch.toggle());
        appearanceCard.addView(backgroundRow);
        accentRow = optionRow(appearanceCard, R.string.widget_dial_accent, accentSpinner,
                WidgetOptionCatalog.labels(this, WidgetOptionCatalog.ACCENT_LABELS), true);
        content.addView(appearanceCard);

        if (state != null) {
            restoreSelection(referenceStyleSpinner, state, "chrome");
            restoreSelection(refreshSpinner, state, "refresh");
            restoreSelection(previewSpinner, state, "preview");
            restoreSelection(themeSpinner, state, "theme");
            restoreSelection(accentSpinner, state, "accent");
            backgroundSwitch.setChecked(state.getBoolean("background", savedAppearance.opacity > 0));
        }
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSummaries();
                renderPreview();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        };
        for (Spinner spinner : new Spinner[]{previewSpinner, referenceStyleSpinner, refreshSpinner, themeSpinner, accentSpinner})
            spinner.setOnItemSelectedListener(listener);
        backgroundSwitch.setOnCheckedChangeListener((button, enabled) -> renderPreview());
        page.cancel.setOnClickListener(view -> finish());
        page.save.setOnClickListener(view -> save());
        updateSummaries();
        renderPreview();
    }

    private Spinner spinner(int labels, int index) {
        Spinner spinner = Ui.spinner(this, getResources().getStringArray(labels), dark);
        spinner.setSelection(index);
        return spinner;
    }

    private void restoreSelection(Spinner spinner, Bundle state, String key) {
        spinner.setSelection(Math.max(0, Math.min(spinner.getCount() - 1,
                state.getInt(key, spinner.getSelectedItemPosition()))));
    }

    private CardItemView optionRow(RoundedLinearLayout card, int title, Spinner spinner,
            int labels, boolean divider) {
        return optionRow(card, title, spinner, getResources().getStringArray(labels), divider);
    }

    private CardItemView optionRow(RoundedLinearLayout card, int title, Spinner spinner,
            String[] labels, boolean divider) {
        CardItemView row = new CardItemView(this);
        row.setTitle(getString(title));
        row.setSummary(labels[spinner.getSelectedItemPosition()]);
        row.setShowTopDivider(divider);
        row.setShowBottomDivider(false);
        row.setOnClickListener(view -> OneUiChoiceDialog.show(this, getString(title), labels,
                spinner.getSelectedItemPosition(), position -> {
                    spinner.setSelection(position);
                    updateSummaries();
                    renderPreview();
                }));
        card.addView(row);
        return row;
    }

    private void note(RoundedLinearLayout card, int text) {
        TextView note = Ui.text(this, getString(text), 13, Ui.secondaryText(dark));
        note.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 12));
        card.addView(note);
    }

    private void synchronizeWindows() {
        requestedSelection = WidgetMeters.serialize(QuotaCardOptions.effectiveWindows(
                requestedSelection, widgetSnapshot, snapshot));
        List<String> previous = WidgetMeters.parse(QuotaCardOptions.migrateToWindowIds(
                WidgetMeters.serialize(meterOrder), widgetSnapshot, snapshot));
        List<String> available = QuotaCardOptions.availableWindows(widgetSnapshot, requestedSelection, snapshot);
        List<String> selected = QuotaCardOptions.effectiveWindows(requestedSelection, widgetSnapshot, snapshot);
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        // Match upstream: saved selected meters fill slots first. During a live refresh retain
        // the current row order, including unfinished drags and temporarily missing selections.
        if (previous.isEmpty()) ordered.addAll(selected);
        else for (String key : previous) if (available.contains(key)) ordered.add(key);
        ordered.addAll(selected);
        ordered.addAll(available);
        meterOrder.clear();
        meterOrder.addAll(ordered);
        selectedMeters.clear();
        selectedMeters.addAll(selected);
        requestedSelection = WidgetMeters.serialize(orderedSelectedMeters());
    }

    private List<String> orderedSelectedMeters() {
        List<String> ordered = new ArrayList<>();
        for (String key : meterOrder) if (selectedMeters.contains(key)) ordered.add(key);
        return ordered;
    }

    private void lockMeterListScrolling() {
        if (metersList != null && metersList.getParent() != null)
            metersList.getParent().requestDisallowInterceptTouchEvent(true);
    }

    private WidgetUsageSnapshot loadWidgetSnapshot() {
        WidgetUsageSnapshot complete = WidgetUsageStore.load(this);
        return complete != null ? complete : WidgetUsageSnapshot.fromLegacy(snapshot);
    }

    private void reloadSnapshot() {
        snapshot = AppPreferences.loadSnapshot(this);
        widgetSnapshot = loadWidgetSnapshot();
        // Saving unrelated settings must not replace a temporarily missing selection with fallback.
        if (!selectionEdited) {
            String saved = AppPreferences.loadWidgetOptions(this, appWidgetId).visibleMeters;
            String oldOrder = QuotaCardOptions.migrateToWindowIds(requestedSelection, widgetSnapshot, snapshot);
            String savedOrder = QuotaCardOptions.migrateToWindowIds(saved, widgetSnapshot, snapshot);
            if (!oldOrder.equals(savedOrder)) meterOrder.clear();
            requestedSelection = saved;
        }
        synchronizeWindows();
        if (meterAdapter != null) meterAdapter.notifyDataSetChanged();
        updateSummaries();
        renderPreview();
    }

    private WidgetOptions currentOptions() {
        int opacity = backgroundSwitch.isChecked()
                ? savedAppearance.opacity > 0 ? savedAppearance.opacity : 100 : 0;
        return new WidgetOptions(WidgetOptions.STYLE_CARDS, WidgetOptions.DENSITY_AUTO,
                WidgetOptions.SURFACE_ONE_UI, WidgetOptions.GRAPHIC_AUTO,
                WidgetOptionCatalog.THEME_VALUES[themeSpinner.getSelectedItemPosition()],
                WidgetOptionCatalog.ACCENT_VALUES[accentSpinner.getSelectedItemPosition()], opacity,
                WidgetOptions.RESET_HIDDEN, savedAppearance.displayMode, WidgetOptions.METRIC_BOTH,
                false, false, false, false, false, false)
                .withPercentSymbol(savedAppearance.showPercentSymbol)
                .withReferenceStyle(referenceStyleSpinner.getSelectedItemPosition() == 1
                        ? WidgetOptions.REFERENCE_CLEAR : WidgetOptions.REFERENCE_COLOR)
                .withVisibleMeters(WidgetMeters.serialize(QuotaCardOptions.resolve(requestedSelection)));
    }

    private final class MeterAdapter extends RecyclerView.Adapter<MeterHolder> {
        @Override public MeterHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = Ui.horizontal(WidgetConfigActivity.this, Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.dp(WidgetConfigActivity.this, 64));
            row.setPadding(Ui.dp(WidgetConfigActivity.this, 20), Ui.dp(WidgetConfigActivity.this, 8),
                    Ui.dp(WidgetConfigActivity.this, 12), Ui.dp(WidgetConfigActivity.this, 8));
            row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            TextView title = Ui.text(WidgetConfigActivity.this, "", 17, Ui.mainText(dark));
            row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            SwitchCompat toggle = new SwitchCompat(WidgetConfigActivity.this);
            LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(-2, -2);
            toggleParams.setMargins(Ui.dp(WidgetConfigActivity.this, 8), 0, Ui.dp(WidgetConfigActivity.this, 4), 0);
            row.addView(toggle, toggleParams);
            ImageView handle = new ImageView(WidgetConfigActivity.this);
            handle.setImageResource(R.drawable.ic_oui_reorder);
            handle.setImageTintList(ColorStateList.valueOf(Ui.secondaryText(dark)));
            handle.setContentDescription(getString(R.string.phone_reorder_33d99));
            int pad = Ui.dp(WidgetConfigActivity.this, 12);
            handle.setPadding(pad, pad, pad, pad);
            row.addView(handle, new LinearLayout.LayoutParams(Ui.dp(WidgetConfigActivity.this, 48),
                    Ui.dp(WidgetConfigActivity.this, 48)));
            row.setOnClickListener(view -> toggle.toggle());
            MeterHolder holder = new MeterHolder(row, title, toggle, handle);
            bindDragHandle(holder);
            return holder;
        }

        @SuppressLint("ClickableViewAccessibility")
        private void bindDragHandle(MeterHolder holder) {
            holder.handle.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && meterTouchHelper != null) {
                    lockMeterListScrolling();
                    meterTouchHelper.startDrag(holder);
                    return true;
                }
                return false;
            });
        }

        @Override public void onBindViewHolder(MeterHolder holder, int position) {
            String key = meterOrder.get(position);
            holder.title.setText(QuotaCardRenderer.windowLabel(WidgetConfigActivity.this, key, widgetSnapshot, snapshot));
            holder.toggle.setContentDescription(holder.title.getText());
            holder.toggle.setOnCheckedChangeListener(null);
            holder.toggle.setChecked(selectedMeters.contains(key));
            holder.title.setAlpha(selectedMeters.contains(key) ? 1f : .45f);
            holder.toggle.setOnCheckedChangeListener((button, checked) -> {
                if (holder.restoring) return;
                int index = holder.getBindingAdapterPosition();
                if (index < 0 || index >= meterOrder.size()) return;
                String rowKey = meterOrder.get(index);
                if (!checked && selectedMeters.contains(rowKey) && selectedMeters.size() == 1) {
                    holder.restoring = true;
                    button.setChecked(true);
                    holder.restoring = false;
                    Toast.makeText(WidgetConfigActivity.this, R.string.card_selection_minimum, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (checked) selectedMeters.add(rowKey); else selectedMeters.remove(rowKey);
                holder.title.setAlpha(checked ? 1f : .45f);
                requestedSelection = WidgetMeters.serialize(orderedSelectedMeters());
                selectionEdited = true;
                renderPreview();
            });
        }
        @Override public int getItemCount() { return meterOrder.size(); }
    }

    private static final class MeterHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final SwitchCompat toggle;
        final ImageView handle;
        boolean restoring;
        MeterHolder(View row, TextView title, SwitchCompat toggle, ImageView handle) {
            super(row); this.title = title; this.toggle = toggle; this.handle = handle;
        }
    }

    private final class MeterReorderCallback extends ItemTouchHelper.Callback {
        @Override public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder holder) {
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }
        @Override public boolean isLongPressDragEnabled() { return true; }
        @Override public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder from,
                RecyclerView.ViewHolder to) {
            int fromPosition = from.getBindingAdapterPosition();
            int toPosition = to.getBindingAdapterPosition();
            if (fromPosition < 0 || toPosition < 0 || fromPosition >= meterOrder.size()
                    || toPosition >= meterOrder.size()) return false;
            if (fromPosition < toPosition) {
                for (int i = fromPosition; i < toPosition; i++) Collections.swap(meterOrder, i, i + 1);
            } else {
                for (int i = fromPosition; i > toPosition; i--) Collections.swap(meterOrder, i, i - 1);
            }
            recyclerView.getAdapter().notifyItemMoved(fromPosition, toPosition);
            requestedSelection = WidgetMeters.serialize(orderedSelectedMeters());
            selectionEdited = true;
            updateSummaries();
            renderPreview();
            return true;
        }
        @Override public void onSelectedChanged(RecyclerView.ViewHolder holder, int actionState) {
            super.onSelectedChanged(holder, actionState);
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && holder != null) {
                lockMeterListScrolling();
                holder.itemView.setAlpha(.85f);
                holder.itemView.setElevation(Ui.dp(holder.itemView.getContext(), 4));
            }
        }
        @Override public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder holder) {
            super.clearView(recyclerView, holder);
            holder.itemView.setAlpha(1f);
            holder.itemView.setElevation(0f);
            updateSummaries();
            renderPreview();
        }
        @Override public void onSwiped(RecyclerView.ViewHolder holder, int direction) { }
    }

    private void updateSummaries() {
        if (referenceStyleRow == null) return;
        referenceStyleRow.setSummary(getResources().getStringArray(R.array.reference_style_labels)[referenceStyleSpinner.getSelectedItemPosition()]);
        refreshRow.setSummary(getResources().getStringArray(R.array.reference_refresh_labels)[refreshSpinner.getSelectedItemPosition()]);
        previewRow.setSummary(getResources().getStringArray(R.array.reference_preview_labels)[previewSpinner.getSelectedItemPosition()]);
        themeRow.setSummary(WidgetOptionCatalog.labels(this, WidgetOptionCatalog.THEME_LABELS)[themeSpinner.getSelectedItemPosition()]);
        accentRow.setSummary(WidgetOptionCatalog.labels(this, WidgetOptionCatalog.ACCENT_LABELS)[accentSpinner.getSelectedItemPosition()]);
        accentRow.setVisibility(previewSpinner.getSelectedItemPosition() == 0 ? View.VISIBLE : View.GONE);
        accountSummary.setText(widgetSnapshot == null ? getString(R.string.reference_bound_account)
                : getString(R.string.reference_bound_plan, UsageFormat.planLabel(widgetSnapshot.planType)));
        metersHint.setText(getString(meterOrder.isEmpty() ? R.string.reference_no_windows : R.string.reference_window_help));
    }

    private int initialPreview() {
        Bundle size = AppWidgetManager.getInstance(this).getAppWidgetOptions(appWidgetId);
        int width = size.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 158);
        int height = size.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 158);
        if (QuotaCardRenderer.usesDials(size, width, height)) return 0;
        return width >= 240 ? 2 : 1;
    }

    private Bundle previewSize() {
        int family = previewSpinner.getSelectedItemPosition();
        int width = family == 0 ? 180 : family == 2 ? 338 : 158;
        int height = family == 0 ? 100 : 158;
        Bundle size = new Bundle();
        size.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width);
        size.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width);
        size.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height);
        size.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height);
        size.putInt("semAppWidgetColumnSpan", family == 2 ? 4 : 2);
        size.putInt("semAppWidgetRowSpan", family == 0 ? 1 : 2);
        return size;
    }

    private void renderPreview() {
        if (previewContainer == null || themeSpinner == null) return;
        previewContainer.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            try {
                Bundle size = previewSize();
                RemoteViews remote = WidgetRenderer.buildPreview(this, appWidgetId, currentOptions(), size);
                FrameLayout surface = new FrameLayout(this);
                View widget = remote.apply(getApplicationContext(), surface);
                disablePreviewActions(widget);
                surface.addView(widget, new FrameLayout.LayoutParams(-1, -1));
                int width = Ui.dp(this, size.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));
                int height = Ui.dp(this, size.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT));
                int inset = Ui.dp(this, 14);
                int maxWidth = previewContainer.getWidth() > 0 ? previewContainer.getWidth() - 2 * inset : width;
                int maxHeight = previewContainer.getHeight() > 0 ? previewContainer.getHeight() - 2 * inset : height;
                float scale = Math.min(1, Math.min(maxWidth / (float) width, maxHeight / (float) height));
                surface.setScaleX(scale); surface.setScaleY(scale);
                previewContainer.removeAllViews();
                ImageView backdrop = new ImageView(this);
                backdrop.setImageResource(R.drawable.codex_meter_icon_bg);
                backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
                backdrop.setContentDescription(null);
                previewContainer.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
                previewContainer.addView(surface, new FrameLayout.LayoutParams(width, height, Gravity.CENTER));
            } catch (RuntimeException error) {
                Log.w("CodexMeterPreview", "Unable to render widget preview", error);
            }
        });
    }

    private static void disablePreviewActions(View view) {
        view.setOnClickListener(null);
        view.setClickable(false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) disablePreviewActions(group.getChildAt(i));
        }
    }

    private void save() {
        WidgetOptions options = currentOptions();
        ReferenceWidgetPreferences.save(this, appWidgetId, options,
                ReferenceWidgetPreferences.REFRESH_MINUTES[refreshSpinner.getSelectedItemPosition()]);
        AppPreferences.saveWidgetOptions(this, appWidgetId, options);
        AppPreferences.saveWidgetTapAction(this, appWidgetId, WidgetOptions.TAP_OPEN_APP);
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        for (int id : manager.getAppWidgetIds(new android.content.ComponentName(this, CodexUsageWidget.class)))
            WidgetRenderer.update(this, manager, id);
        WidgetRenderer.update(this, manager, appWidgetId);
        WidgetRefreshScheduler.schedule(this);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId));
        Toast.makeText(this, R.string.phone_widget_updated_3d7aa, Toast.LENGTH_SHORT).show();
        finish();
    }
}
