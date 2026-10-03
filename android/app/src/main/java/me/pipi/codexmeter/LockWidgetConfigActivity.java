package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsageWindow;
import dev.bennett.codexmeter.WidgetMeters;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import dev.oneuiproject.oneui.widget.RoundedLinearLayout;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Configuration screen for a placed Samsung lock-screen/AOD widget. */
public final class LockWidgetConfigActivity extends AppCompatActivity {
    private static final int MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;
    /** Preview values used when the cached snapshot has nothing for a meter. */
    private static final int SAMPLE_PRIMARY_PERCENT = 73;
    private static final int SAMPLE_SECONDARY_PERCENT = 44;
    private static final int PREVIEW_WIDTH_DP = 180;
    private static final int PREVIEW_HEIGHT_DP = 82;

    /** Enabled lock meters in the order the user picked them; never empty. */
    private final LinkedHashSet<String> selectedMeters = new LinkedHashSet<>();
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean dark;
    private ImageView preview;
    private TextView metersHint;
    private CheckBox showCountdown;
    private CheckBox showResetCredits;
    private CheckBox showResetAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applySelectedTheme(this);
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        appWidgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Toast.makeText(this, R.string.dashboard_lock_no_widget, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        dark = Ui.isDark(this);
        build();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void build() {
        Ui.ConfigPage page = Ui.installConfigPage(this, getString(R.string.dashboard_lock_title));
        LinearLayout content = page.content;
        buildPreview(page.preview);

        LockWidgetOptions saved = AppPreferences.loadLockWidgetOptions(this, appWidgetId);
        selectedMeters.clear();
        for (String key : WidgetMeters.parse(saved.effectiveVisibleMeters())) {
            if (isLockMeter(key)) {
                selectedMeters.add(key);
            }
        }
        if (selectedMeters.isEmpty()) {
            selectedMeters.add(WidgetMeters.FIVE_HOUR);
        }

        content.addView(Ui.separator(this, getString(R.string.dashboard_lock_meters)));
        content.addView(buildMetersCard());
        content.addView(Ui.separator(this, getString(R.string.dashboard_lock_content)));
        content.addView(buildContentCard(saved));

        CompoundButton.OnCheckedChangeListener previewCheckListener =
                (buttonView, isChecked) -> updatePreview();
        showCountdown.setOnCheckedChangeListener(previewCheckListener);
        showResetCredits.setOnCheckedChangeListener(previewCheckListener);
        showResetAction.setOnCheckedChangeListener(previewCheckListener);

        page.cancel.setOnClickListener(view -> finish());
        page.save.setOnClickListener(view -> save());
        updateMetersHint();
        updatePreview();
    }

    private void buildPreview(FrameLayout container) {
        container.setBackgroundColor(Ui.controlSurface(this, dark));
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int padding = Ui.dp(this, 28.0f);
        preview.setPadding(padding, padding, padding, padding);
        container.addView(preview, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
    }

    private RoundedLinearLayout buildMetersCard() {
        RoundedLinearLayout card = Ui.seslCard(this, dark);
        // updateMetersHint() fills in the capacity text once the selection is known.
        metersHint = Ui.text(this, "", 13.0f, Ui.secondaryText(dark));
        metersHint.setPadding(0, 0, 0, Ui.dp(this, 8));
        card.addView(metersHint);

        UsageSnapshot snapshot = AppPreferences.loadSnapshot(this);
        List<String> available = new ArrayList<>();
        for (String key : WidgetMeters.availableKeys(snapshot)) {
            if (isLockMeter(key)) {
                available.add(key);
            }
        }
        // Prefer saved selection order so the first enabled meter stays primary.
        List<String> ordered = new ArrayList<>();
        for (String key : selectedMeters) {
            if (available.contains(key)) {
                ordered.add(key);
            }
        }
        for (String key : available) {
            if (!ordered.contains(key)) {
                ordered.add(key);
            }
        }
        for (int i = 0; i < ordered.size(); i++) {
            String key = ordered.get(i);
            SwitchCompat toggle = new SwitchCompat(this);
            toggle.setChecked(selectedMeters.contains(key));
            card.addView(buildSwitchRow(SharedLabels.widgetMeterConfigLabel(this, key, snapshot),
                    toggle, i > 0));
            toggle.setOnCheckedChangeListener(
                    (button, checked) -> onMeterToggled(button, key, checked));
        }
        return card;
    }

    private void onMeterToggled(CompoundButton button, String key, boolean checked) {
        if (checked) {
            selectedMeters.add(key);
        } else {
            selectedMeters.remove(key);
            if (selectedMeters.isEmpty()) {
                // At least one meter must stay enabled; flip the switch back on.
                selectedMeters.add(key);
                button.setChecked(true);
                return;
            }
        }
        updateMetersHint();
        updatePreview();
    }

    private RoundedLinearLayout buildContentCard(LockWidgetOptions saved) {
        RoundedLinearLayout card = Ui.seslCard(this, dark);
        showCountdown = Ui.checkbox(this, getString(R.string.dashboard_lock_show_countdown),
                saved.showCountdown, dark);
        showResetCredits = Ui.checkbox(this, getString(R.string.dashboard_lock_show_credits),
                saved.showResetCredits, dark);
        showResetAction = Ui.checkbox(this, getString(R.string.dashboard_lock_tap_reset),
                saved.showResetAction, dark);
        card.addView(showCountdown);
        card.addView(showResetCredits);
        card.addView(showResetAction);
        TextView resetNote = Ui.text(this, getString(R.string.dashboard_lock_reset_note),
                12.0f, Ui.secondaryText(dark));
        LinearLayout.LayoutParams noteParams =
                new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        noteParams.setMargins(0, Ui.dp(this, 12.0f), 0, 0);
        card.addView(resetNote, noteParams);
        return card;
    }

    private LinearLayout buildSwitchRow(String title, SwitchCompat toggle, boolean topDivider) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        if (topDivider) {
            View divider = new View(this);
            divider.setBackgroundColor(Ui.divider(dark));
            LinearLayout.LayoutParams dividerParams =
                    new LinearLayout.LayoutParams(MATCH_PARENT, Ui.dp(this, 1));
            dividerParams.setMargins(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
            row.addView(divider, dividerParams);
        }
        LinearLayout content = new LinearLayout(this);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setMinimumHeight(Ui.dp(this, 52));
        content.addView(Ui.text(this, title, 16, Ui.mainText(dark)),
                new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f));
        content.addView(toggle, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        content.setOnClickListener(view -> toggle.toggle());
        row.addView(content, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        return row;
    }

    private void updateMetersHint() {
        if (metersHint == null) {
            return;
        }
        int selected = selectedMeters.size();
        int capacity = WidgetMeters.lockSlotCapacity();
        String message = getResources().getQuantityString(
                R.plurals.dashboard_lock_meters_capacity, capacity, capacity);
        if (selected > capacity) {
            int extra = selected - capacity;
            message = getString(R.string.dashboard_two_sentences, message,
                    getResources().getQuantityString(R.plurals.dashboard_lock_meters_extra,
                            extra, extra));
        }
        metersHint.setText(message);
    }

    private LockWidgetOptions currentOptions() {
        List<String> ordered = new ArrayList<>();
        for (String key : selectedMeters) {
            if (isLockMeter(key)) {
                ordered.add(key);
            }
        }
        return new LockWidgetOptions(metricModeForSelection(), showResetCredits.isChecked(),
                showResetAction.isChecked(), showCountdown.isChecked(),
                WidgetMeters.serialize(ordered));
    }

    /** Legacy metric mode mirroring the selection; meter resolution falls back to it. */
    private String metricModeForSelection() {
        if (selectedMeters.size() == 1) {
            if (selectedMeters.contains(WidgetMeters.FIVE_HOUR)) {
                return WidgetOptions.METRIC_FIVE_HOUR;
            }
            if (selectedMeters.contains(WidgetMeters.WEEKLY)) {
                return WidgetOptions.METRIC_WEEKLY;
            }
        }
        return WidgetOptions.METRIC_BOTH;
    }

    private void updatePreview() {
        if (preview == null || showCountdown == null) {
            return;
        }
        LockWidgetOptions options = currentOptions();
        UsageSnapshot snapshot = AppPreferences.loadSnapshot(this);
        List<String> available = WidgetMeters.availableKeys(snapshot);
        List<String> visible = WidgetMeters.cap(
                WidgetMeters.resolveVisibleForWidget(options.effectiveVisibleMeters(), available,
                        options.metricMode),
                WidgetMeters.lockSlotCapacity());
        int primary = SAMPLE_PRIMARY_PERCENT;
        int secondary = -1;
        int primaryIcon = R.drawable.ic_oui_time;
        int secondaryIcon = R.drawable.ic_oui_calendar_week;
        if (!visible.isEmpty()) {
            primary = previewRemaining(visible.get(0), snapshot, SAMPLE_PRIMARY_PERCENT);
            primaryIcon = previewIcon(visible.get(0));
        }
        if (visible.size() > 1) {
            secondary = previewRemaining(visible.get(1), snapshot, SAMPLE_SECONDARY_PERCENT);
            secondaryIcon = previewIcon(visible.get(1));
        }
        preview.setImageBitmap(SamsungLockGraphics.render(this,
                SamsungLockWidgetSupport.Shape.WIDE, primary, secondary, true,
                PREVIEW_WIDTH_DP, PREVIEW_HEIGHT_DP, primaryIcon, secondaryIcon));
    }

    private static boolean isLockMeter(String key) {
        return WidgetMeters.FIVE_HOUR.equals(key) || WidgetMeters.WEEKLY.equals(key);
    }

    private static int previewIcon(String key) {
        if (WidgetMeters.WEEKLY.equals(key)) {
            return R.drawable.ic_oui_calendar_week;
        }
        return R.drawable.ic_oui_time;
    }

    private static int previewRemaining(String key, UsageSnapshot snapshot, int fallback) {
        if (WidgetMeters.FIVE_HOUR.equals(key) && snapshot != null && snapshot.fiveHour != null) {
            return snapshot.fiveHour.remainingPercent();
        }
        if (WidgetMeters.WEEKLY.equals(key)) {
            UsageWindow longWindow = WidgetMeters.meterWindow(key, snapshot);
            if (longWindow != null) {
                return longWindow.remainingPercent();
            }
        }
        return fallback;
    }

    private void save() {
        AppPreferences.saveLockWidgetOptions(this, appWidgetId, currentOptions());
        SamsungLockWidgetSupport.updateById(this, appWidgetId);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                appWidgetId));
        Toast.makeText(this, R.string.dashboard_lock_saved, Toast.LENGTH_SHORT).show();
        finish();
    }
}
