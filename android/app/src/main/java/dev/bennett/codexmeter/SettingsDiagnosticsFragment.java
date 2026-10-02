package dev.bennett.codexmeter;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Diagnostics page: local diagnostic tracing plus exporting or clearing the saved logs. */
public final class SettingsDiagnosticsFragment extends SettingsPageFragment {
    private static final int REQUEST_EXPORT_DIAGNOSTICS = 9203;

    @Override
    void onCreatePage() {
        addPreferencesFromResource(R.xml.preferences_settings_diagnostics);
        bindDiagnostics();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSummary();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (requestCode == REQUEST_EXPORT_DIAGNOSTICS) {
            finishExport(data.getData());
        }
    }

    private void bindDiagnostics() {
        SwitchPreferenceCompat enabled = findPreference("diagnostic_logging_enabled");
        enabled.setPersistent(false);
        enabled.setChecked(DiagnosticLog.isEnabled(requireContext()));
        enabled.setOnPreferenceChangeListener((preference, value) -> {
            boolean loggingEnabled = (Boolean) value;
            DiagnosticLog.setEnabled(requireContext(), loggingEnabled);
            enabled.setChecked(loggingEnabled);
            updateSummary();
            showToast(getString(loggingEnabled
                            ? R.string.settings_diagnostics_tracing_enabled
                            : R.string.settings_diagnostics_tracing_disabled),
                    Toast.LENGTH_LONG);
            return true;
        });
        findPreference("export_diagnostic_logs").setOnPreferenceClickListener(preference -> {
            launchExport();
            return true;
        });
        findPreference("clear_diagnostic_logs").setOnPreferenceClickListener(preference -> {
            confirmClear();
            return true;
        });
        updateSummary();
    }

    private void confirmClear() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_diagnostics_clear_dialog_title)
                .setMessage(R.string.settings_diagnostics_clear_dialog_message)
                .setNegativeButton(R.string.settings_dialog_cancel, null)
                .setPositiveButton(R.string.settings_diagnostics_clear_confirm,
                        (dialog, which) -> {
                            DiagnosticLog.clear(requireContext());
                            updateSummary();
                            showToast(getString(R.string.settings_diagnostics_cleared),
                                    Toast.LENGTH_SHORT);
                        })
                .show();
    }

    private void updateSummary() {
        if (getContext() == null) {
            return;
        }
        boolean enabled = DiagnosticLog.isEnabled(requireContext());
        SwitchPreferenceCompat toggle = findPreference("diagnostic_logging_enabled");
        if (toggle != null) {
            toggle.setChecked(enabled);
        }
        DiagnosticLog.Stats stats = DiagnosticLog.stats(requireContext());
        Preference status = findPreference("diagnostic_log_status");
        status.setSummary(getResources().getQuantityString(enabled
                        ? R.plurals.settings_diagnostics_status_tracing_on
                        : R.plurals.settings_diagnostics_status_tracing_off,
                stats.files, DiagnosticLog.formatBytes(stats.bytes), stats.files));
        findPreference("export_diagnostic_logs").setEnabled(stats.hasLogs());
        findPreference("clear_diagnostic_logs").setEnabled(stats.hasLogs());
    }

    private void launchExport() {
        DiagnosticLog.Stats stats = DiagnosticLog.stats(requireContext());
        if (!stats.hasLogs()) {
            showToast(getString(R.string.settings_diagnostics_export_empty), Toast.LENGTH_SHORT);
            return;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/x-ndjson")
                .putExtra(Intent.EXTRA_TITLE, "codex-meter-diagnostics-" + stamp + ".jsonl");
        try {
            startActivityForResult(create, REQUEST_EXPORT_DIAGNOSTICS);
        } catch (RuntimeException exception) {
            DiagnosticLog.error(requireContext(), "diagnostics", "export_picker_failed",
                    exception);
            showToast(getString(R.string.settings_diagnostics_export_no_picker),
                    Toast.LENGTH_LONG);
        }
    }

    private void finishExport(Uri uri) {
        try {
            DiagnosticLog.export(requireContext(), uri);
            updateSummary();
            showToast(getString(R.string.settings_diagnostics_exported), Toast.LENGTH_LONG);
        } catch (Exception exception) {
            DiagnosticLog.error(requireContext(), "diagnostics", "export_failed", exception);
            showToast(getString(R.string.settings_diagnostics_export_failed,
                    exportFailureReason(exception)), Toast.LENGTH_LONG);
        }
    }

    /** Localized reason for a failed export; other failures keep their own message. */
    private String exportFailureReason(Exception exception) {
        if (exception instanceof DiagnosticLog.ExportFileUnavailableException) {
            return getString(R.string.settings_diagnostics_export_file_unavailable);
        }
        return MainActivity.safeMessage(exception);
    }
}
