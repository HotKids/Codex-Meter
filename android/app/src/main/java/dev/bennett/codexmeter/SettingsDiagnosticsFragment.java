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
            showToast(loggingEnabled
                            ? "Diagnostic tracing enabled."
                            : "Diagnostic tracing disabled. Saved logs were kept.",
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
                .setTitle("Clear diagnostic logs?")
                .setMessage("This permanently deletes all saved diagnostic events.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    DiagnosticLog.clear(requireContext());
                    updateSummary();
                    showToast("Diagnostic logs cleared.", Toast.LENGTH_SHORT);
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
        status.setSummary((enabled ? "Tracing on" : "Tracing off")
                + " · " + DiagnosticLog.formatBytes(stats.bytes)
                + (stats.files == 1 ? " in 1 file" : " across " + stats.files + " files"));
        findPreference("export_diagnostic_logs").setEnabled(stats.hasLogs());
        findPreference("clear_diagnostic_logs").setEnabled(stats.hasLogs());
    }

    private void launchExport() {
        DiagnosticLog.Stats stats = DiagnosticLog.stats(requireContext());
        if (!stats.hasLogs()) {
            showToast("There are no diagnostic logs to export.", Toast.LENGTH_SHORT);
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
            showToast("No file picker is available to export diagnostic logs.",
                    Toast.LENGTH_LONG);
        }
    }

    private void finishExport(Uri uri) {
        try {
            DiagnosticLog.export(requireContext(), uri);
            updateSummary();
            showToast("Diagnostic logs exported. Review the file before sharing it.",
                    Toast.LENGTH_LONG);
        } catch (Exception exception) {
            DiagnosticLog.error(requireContext(), "diagnostics", "export_failed", exception);
            showToast("Could not export diagnostic logs: " + MainActivity.safeMessage(exception),
                    Toast.LENGTH_LONG);
        }
    }
}
