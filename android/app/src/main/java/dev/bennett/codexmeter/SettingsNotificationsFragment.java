package dev.bennett.codexmeter;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;
import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Notifications page: low-usage alerts, reset-credit alerts with configurable expiry reminder
 * times, and troubleshooting shortcuts.
 */
public final class SettingsNotificationsFragment extends SettingsPageFragment {
    private static final int REQUEST_NOTIFICATION_PERMISSION = 8601;
    private static final long WEEK_MILLIS = TimeUnit.DAYS.toMillis(7);
    /** Reminder lead-time units in spinner order: minutes, hours, days, weeks. */
    private static final long[] LEAD_TIME_UNIT_MILLIS = {
            TimeUnit.MINUTES.toMillis(1),
            TimeUnit.HOURS.toMillis(1),
            TimeUnit.DAYS.toMillis(1),
            WEEK_MILLIS
    };
    private static final int DEFAULT_LEAD_TIME_UNIT = 1;

    private PreferenceCategory lowUsageCategory;
    private PreferenceCategory resetCreditCategory;
    private PreferenceCategory troubleshootingCategory;
    private ListPreference stylePreference;
    private Preference expiryTimesPreference;
    private Preference permissionPreference;
    private Preference testNotificationPreference;

    @Override
    void onCreatePage() {
        addPreferencesFromResource(R.xml.preferences_settings_notifications);
        lowUsageCategory = findPreference("notification_low_usage_category");
        resetCreditCategory = findPreference("notification_reset_credit_category");
        troubleshootingCategory = findPreference("notification_troubleshooting_category");
        bindLowUsageAlerts();
        bindResetCreditAlerts();
        bindTroubleshooting();
        updatePermissionSummary();
        updateNotificationEnabledState();
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePermissionSummary();
    }

    private void bindLowUsageAlerts() {
        Context context = requireContext();
        SwitchPreferenceCompat allow = findPreference("notifications_allowed_ui");
        allow.setEnabled(true);
        allow.setChecked(ResetAlertPreferences.enabled(context));
        allow.setOnPreferenceChangeListener((preference, value) -> {
            setNotificationsEnabled((Boolean) value);
            return true;
        });

        stylePreference = findPreference("notification_style_ui");
        String currentStyle = ResetAlertPreferences.getStyle(context);
        stylePreference.setValue(ResetAlertPreferences.STYLE_OFF.equals(currentStyle)
                ? ResetAlertPreferences.STYLE_NOTIFICATION : currentStyle);
        stylePreference.setEnabled(ResetAlertPreferences.enabled(context));
        stylePreference.setOnPreferenceChangeListener((preference, value) -> {
            saveAlert(String.valueOf(value), ResetAlertPreferences.getMetric(requireContext()),
                    ResetAlertPreferences.getThreshold(requireContext()));
            return true;
        });

        ListPreference metric = findPreference("notification_metric_ui");
        metric.setValue(ResetAlertPreferences.getMetric(context));
        metric.setOnPreferenceChangeListener((preference, value) -> {
            saveAlert(ResetAlertPreferences.getStyle(requireContext()), String.valueOf(value),
                    ResetAlertPreferences.getThreshold(requireContext()));
            return true;
        });

        ListPreference threshold = findPreference("notification_threshold_ui");
        threshold.setValue(String.valueOf(ResetAlertPreferences.getThreshold(context)));
        threshold.setOnPreferenceChangeListener((preference, value) -> {
            saveAlert(ResetAlertPreferences.getStyle(requireContext()),
                    ResetAlertPreferences.getMetric(requireContext()),
                    Integer.parseInt(String.valueOf(value)));
            return true;
        });
    }

    private void bindResetCreditAlerts() {
        Context context = requireContext();
        SwitchPreferenceCompat unexpectedRefills = findPreference("unexpected_refills_ui");
        unexpectedRefills.setPersistent(false);
        unexpectedRefills.setChecked(ResetAlertPreferences.unexpectedRefillsEnabled(context));
        unexpectedRefills.setOnPreferenceChangeListener((preference, value) -> {
            ResetAlertPreferences.setUnexpectedRefillsEnabled(requireContext(), (Boolean) value);
            return true;
        });

        SwitchPreferenceCompat resetCreditIncreases = findPreference("reset_credit_increases_ui");
        resetCreditIncreases.setPersistent(false);
        resetCreditIncreases.setChecked(
                ResetAlertPreferences.resetCreditIncreasesEnabled(context));
        resetCreditIncreases.setOnPreferenceChangeListener((preference, value) -> {
            ResetAlertPreferences.setResetCreditIncreasesEnabled(requireContext(),
                    (Boolean) value);
            return true;
        });

        SwitchPreferenceCompat resetCreditExpiry = findPreference("reset_credit_expiry_ui");
        resetCreditExpiry.setPersistent(false);
        resetCreditExpiry.setChecked(ResetAlertPreferences.resetCreditExpiryEnabled(context));
        resetCreditExpiry.setOnPreferenceChangeListener((preference, value) -> {
            boolean enabled = (Boolean) value;
            ResetAlertPreferences.setResetCreditExpiryEnabled(requireContext(), enabled);
            expiryTimesPreference.setEnabled(enabled);
            scheduleResetCreditExpiryReminders();
            return true;
        });

        expiryTimesPreference = findPreference("reset_credit_expiry_times_ui");
        expiryTimesPreference.setEnabled(ResetAlertPreferences.resetCreditExpiryEnabled(context));
        expiryTimesPreference.setOnPreferenceClickListener(preference -> {
            showExpiryReminderTimesDialog();
            return true;
        });
        updateExpiryTimesSummary();
    }

    private void bindTroubleshooting() {
        permissionPreference = findPreference("notification_permission");
        permissionPreference.setOnPreferenceClickListener(preference -> {
            openAppNotificationSettings();
            return true;
        });
        testNotificationPreference = findPreference("notification_test");
        testNotificationPreference.setOnPreferenceClickListener(preference -> {
            boolean sent = ResetNotificationManager.sendTestNotification(requireContext());
            if (sent) {
                showToast(getString(R.string.alerts_notifications_test_sent),
                        Toast.LENGTH_SHORT);
            } else {
                showToast(getString(R.string.alerts_notifications_test_failed),
                        Toast.LENGTH_LONG);
            }
            return true;
        });
    }

    private void setNotificationsEnabled(boolean enabled) {
        String selectedStyle = stylePreference == null
                ? ResetAlertPreferences.STYLE_NOTIFICATION
                : stylePreference.getValue();
        saveAlert(enabled ? selectedStyle : ResetAlertPreferences.STYLE_OFF,
                ResetAlertPreferences.getMetric(requireContext()),
                ResetAlertPreferences.getThreshold(requireContext()));
        if (enabled && lacksNotificationPermission()) {
            requestNotificationPermission(REQUEST_NOTIFICATION_PERMISSION);
        }
        if (enabled) {
            ResetNotificationManager.ensureChannel(requireContext());
        } else {
            ResetNotificationManager.clearNotificationHistory(requireContext());
        }
        updateNotificationEnabledState();
    }

    private void saveAlert(String style, String metric, int threshold) {
        Context context = requireContext();
        ResetAlertPreferences.save(context, style, metric, threshold);
        if (!ResetAlertPreferences.STYLE_OFF.equals(style)) {
            ResetNotificationManager.ensureChannel(context);
            ResetNotificationManager.onUsageUpdated(context, AppPreferences.loadSnapshot(context));
            ResetNotificationManager.onResetCreditsUpdated(context,
                    AppPreferences.loadResetCredits(context));
        }
        ResetAlertScheduler.scheduleFromSnapshot(context, AppPreferences.loadSnapshot(context));
        scheduleResetCreditExpiryReminders();
    }

    /** The detailed alert settings are only shown while alerts are on. */
    private void updateNotificationEnabledState() {
        boolean enabled = ResetAlertPreferences.enabled(requireContext());
        if (stylePreference != null) {
            stylePreference.setEnabled(enabled);
        }
        if (lowUsageCategory != null) {
            lowUsageCategory.setVisible(enabled);
        }
        if (resetCreditCategory != null) {
            resetCreditCategory.setVisible(enabled);
        }
        if (troubleshootingCategory != null) {
            troubleshootingCategory.setVisible(enabled);
        }
    }

    private void updatePermissionSummary() {
        if (permissionPreference == null || getContext() == null) {
            return;
        }
        boolean allowed = areAppNotificationsEnabled() && !lacksNotificationPermission();
        permissionPreference.setSummary(allowed
                ? R.string.alerts_notifications_permission_allowed
                : R.string.alerts_notifications_permission_denied);
        if (testNotificationPreference != null) {
            testNotificationPreference.setEnabled(
                    allowed && ResetAlertPreferences.enabled(requireContext()));
        }
    }

    private void showExpiryReminderTimesDialog() {
        Context context = requireContext();
        List<Long> leadTimes = ResetAlertPreferences.getResetCreditExpiryLeadTimes(context);
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(R.string.alerts_reminder_times_title)
                .setNeutralButton(R.string.alerts_add,
                        (dialog, which) -> showAddExpiryReminderDialog())
                .setNegativeButton(R.string.alerts_done, null);
        if (leadTimes.isEmpty()) {
            builder.setMessage(R.string.alerts_reminder_times_empty_message);
        } else {
            String[] labels = new String[leadTimes.size()];
            for (int i = 0; i < leadTimes.size(); i++) {
                labels[i] = context.getString(R.string.alerts_reminder_time_remove_item,
                        formatLeadTime(context, leadTimes.get(i)));
            }
            builder.setItems(labels, (dialog, which) -> {
                List<Long> updated = new ArrayList<>(leadTimes);
                long removed = updated.remove(which);
                saveExpiryLeadTimes(updated);
                showToast(getString(R.string.alerts_reminder_time_removed,
                        formatLeadTime(requireContext(), removed)), Toast.LENGTH_SHORT);
            });
        }
        builder.show();
    }

    private void showAddExpiryReminderDialog() {
        Context context = requireContext();
        boolean dark = Ui.isDark(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(Ui.dp(context, 24), Ui.dp(context, 8), Ui.dp(context, 24), 0);
        TextView explanation = Ui.text(context,
                context.getString(R.string.alerts_reminder_add_explanation),
                14.0f, Ui.secondaryText(dark));
        container.addView(explanation, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        LinearLayout inputRow = Ui.horizontal(context, 12);
        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        rowParams.setMargins(0, Ui.dp(context, 16), 0, 0);
        EditText amount = new EditText(context);
        amount.setHint(R.string.alerts_reminder_amount_hint);
        amount.setSingleLine(true);
        amount.setTextColor(Ui.mainText(dark));
        amount.setHintTextColor(Ui.secondaryText(dark));
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        inputRow.addView(amount, new LinearLayout.LayoutParams(0, Ui.dp(context, 54), 1.0f));
        String[] units = context.getResources().getStringArray(R.array.alerts_reminder_units);
        Spinner unit = Ui.spinner(context, units, dark);
        unit.setSelection(DEFAULT_LEAD_TIME_UNIT);
        LinearLayout.LayoutParams unitParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 142), Ui.dp(context, 54));
        unitParams.setMargins(Ui.dp(context, 8), 0, 0, 0);
        inputRow.addView(unit, unitParams);
        container.addView(inputRow, rowParams);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.alerts_reminder_add_title)
                .setView(container)
                .setNegativeButton(R.string.alerts_cancel, null)
                .setPositiveButton(R.string.alerts_add, null)
                .create();
        // Validate before dismissing so an invalid amount keeps the dialog open with an error.
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    Long leadTime = parseLeadTime(amount.getText().toString(),
                            unit.getSelectedItemPosition());
                    if (leadTime == null) {
                        amount.setError(context.getString(
                                R.string.alerts_reminder_invalid_amount));
                        return;
                    }
                    List<Long> updated = new ArrayList<>(
                            ResetAlertPreferences.getResetCreditExpiryLeadTimes(requireContext()));
                    if (!updated.contains(leadTime)) {
                        updated.add(leadTime);
                    }
                    saveExpiryLeadTimes(updated);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    /**
     * Parses a locale-formatted amount of the unit at {@code unitPosition} into milliseconds, or
     * returns null unless it is a whole number of minutes within the supported reminder range.
     */
    private static Long parseLeadTime(String amount, int unitPosition) {
        if (amount == null || amount.trim().isEmpty()
                || unitPosition < 0 || unitPosition >= LEAD_TIME_UNIT_MILLIS.length) {
            return null;
        }
        try {
            char decimalSeparator = DecimalFormatSymbols.getInstance().getDecimalSeparator();
            String trimmed = amount.trim();
            String normalized = decimalSeparator == '.'
                    ? trimmed : trimmed.replace(decimalSeparator, '.');
            long value = new BigDecimal(normalized)
                    .multiply(BigDecimal.valueOf(LEAD_TIME_UNIT_MILLIS[unitPosition]))
                    .longValueExact();
            boolean valid = value >= ResetCreditExpiryReminder.MIN_LEAD_TIME_MS
                    && value <= ResetCreditExpiryReminder.MAX_LEAD_TIME_MS
                    && value % ResetCreditExpiryReminder.MIN_LEAD_TIME_MS == 0L;
            return valid ? value : null;
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }

    private void saveExpiryLeadTimes(List<Long> leadTimes) {
        ResetAlertPreferences.setResetCreditExpiryLeadTimes(requireContext(), leadTimes);
        updateExpiryTimesSummary();
        scheduleResetCreditExpiryReminders();
    }

    private void updateExpiryTimesSummary() {
        if (expiryTimesPreference == null) {
            return;
        }
        Context context = requireContext();
        List<Long> leadTimes = ResetAlertPreferences.getResetCreditExpiryLeadTimes(context);
        if (leadTimes.isEmpty()) {
            expiryTimesPreference.setSummary(R.string.alerts_reminder_times_none);
            return;
        }
        List<String> labels = new ArrayList<>();
        for (Long leadTime : leadTimes) {
            labels.add(formatLeadTime(context, leadTime));
        }
        expiryTimesPreference.setSummary(context.getString(
                R.string.alerts_reminder_times_summary,
                String.join(context.getString(R.string.alerts_reminder_times_separator),
                        labels)));
    }

    /** Formats a lead time using the largest unit that divides it exactly, e.g. "2 hours". */
    private static String formatLeadTime(Context context, long millis) {
        if (millis % WEEK_MILLIS == 0L) {
            return countLabel(context, R.plurals.alerts_lead_time_weeks, millis / WEEK_MILLIS);
        }
        long dayMillis = TimeUnit.DAYS.toMillis(1);
        if (millis % dayMillis == 0L) {
            return countLabel(context, R.plurals.alerts_lead_time_days, millis / dayMillis);
        }
        long hourMillis = TimeUnit.HOURS.toMillis(1);
        if (millis % hourMillis == 0L) {
            return countLabel(context, R.plurals.alerts_lead_time_hours, millis / hourMillis);
        }
        return countLabel(context, R.plurals.alerts_lead_time_minutes,
                millis / TimeUnit.MINUTES.toMillis(1));
    }

    /** Lead times stay within a year of minutes, so the count always fits in an int. */
    private static String countLabel(Context context, int pluralsRes, long count) {
        int quantity = (int) count;
        return context.getResources().getQuantityString(pluralsRes, quantity, quantity);
    }

    private void scheduleResetCreditExpiryReminders() {
        ResetNotificationManager.onResetCreditExpirySettingsChanged(requireContext(),
                AppPreferences.loadResetCredits(requireContext()));
    }
}
