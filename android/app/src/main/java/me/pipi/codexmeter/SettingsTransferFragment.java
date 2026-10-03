package me.pipi.codexmeter;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Backup & transfer page: exports selected settings sections (optionally including ChatGPT
 * authentication) to a JSON file and imports them back, with extra confirmation whenever
 * authentication tokens are involved.
 */
public final class SettingsTransferFragment extends SettingsPageFragment {
    private static final int REQUEST_EXPORT_TRANSFER = 9201;
    private static final int REQUEST_IMPORT_TRANSFER = 9202;
    private static final String TRANSFER_MIME_TYPE = "application/json";

    // Rows of the export dialog, in display order (see showExportSectionDialog).
    private static final int EXPORT_APP_SETTINGS = 0;
    private static final int EXPORT_NOTIFICATIONS = 1;
    private static final int EXPORT_NOW_BAR = 2;
    private static final int EXPORT_AUTHENTICATION = 3;

    // Sections chosen in the export dialog, kept until the file picker returns.
    private boolean pendingExportAppSettings;
    private boolean pendingExportNotifications;
    private boolean pendingExportNowBar;
    private boolean pendingExportAuthentication;

    @Override
    void onCreatePage() {
        addPreferencesFromResource(R.xml.preferences_settings_transfer);
        bindTransfer();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT_TRANSFER) {
            finishExport(uri);
        } else if (requestCode == REQUEST_IMPORT_TRANSFER) {
            beginImport(uri);
        }
    }

    private void bindTransfer() {
        findPreference("export_settings_transfer").setOnPreferenceClickListener(preference -> {
            showExportSectionDialog();
            return true;
        });
        findPreference("import_settings_transfer").setOnPreferenceClickListener(preference -> {
            launchImportPicker();
            return true;
        });
    }

    private void launchImportPicker() {
        Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(TRANSFER_MIME_TYPE);
        open.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                TRANSFER_MIME_TYPE,
                "text/plain",
                "text/json",
                "*/*"
        });
        try {
            startActivityForResult(open, REQUEST_IMPORT_TRANSFER);
        } catch (RuntimeException exception) {
            showToast(getString(R.string.settings_transfer_import_no_picker),
                    Toast.LENGTH_LONG);
        }
    }

    private void showExportSectionDialog() {
        boolean signedIn = SecureTokenStore.isSignedIn(requireContext());
        String[] labels = {
                sectionLabel(SettingsTransfer.SECTION_APP_SETTINGS),
                sectionLabel(SettingsTransfer.SECTION_NOTIFICATIONS),
                sectionLabel(SettingsTransfer.SECTION_NOW_BAR),
                signedIn
                        ? sectionLabel(SettingsTransfer.SECTION_AUTHENTICATION)
                        : sectionTitle(SettingsTransfer.SECTION_AUTHENTICATION) + "\n"
                                + getString(R.string.settings_transfer_export_auth_signed_out)
        };
        // Authentication is opt-in; everything else starts selected.
        boolean[] checked = {true, true, true, false};
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_transfer_export_dialog_title)
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                    if (which == EXPORT_AUTHENTICATION && isChecked && !signedIn) {
                        checked[EXPORT_AUTHENTICATION] = false;
                        ((AlertDialog) dialog).getListView()
                                .setItemChecked(EXPORT_AUTHENTICATION, false);
                        showToast(getString(
                                R.string.settings_transfer_export_auth_sign_in_first),
                                Toast.LENGTH_LONG);
                        return;
                    }
                    checked[which] = isChecked;
                })
                .setNegativeButton(R.string.settings_dialog_cancel, null)
                .setPositiveButton(R.string.settings_dialog_continue,
                        (dialog, which) -> onExportSectionsChosen(checked))
                .show();
    }

    private void onExportSectionsChosen(boolean[] checked) {
        boolean appSettings = checked[EXPORT_APP_SETTINGS];
        boolean notifications = checked[EXPORT_NOTIFICATIONS];
        boolean nowBar = checked[EXPORT_NOW_BAR];
        boolean authentication = checked[EXPORT_AUTHENTICATION];
        if (!(appSettings || notifications || nowBar || authentication)) {
            showToast(getString(R.string.settings_transfer_select_export), Toast.LENGTH_LONG);
            return;
        }
        if (authentication) {
            confirmSensitiveExport(appSettings, notifications, nowBar);
        } else {
            launchExportPicker(appSettings, notifications, nowBar, false);
        }
    }

    /** Extra confirmation before ChatGPT tokens are written to a file. */
    private void confirmSensitiveExport(boolean appSettings, boolean notifications,
            boolean nowBar) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_transfer_export_auth_title)
                .setMessage(getString(R.string.settings_transfer_export_auth_message,
                        getString(R.string.settings_transfer_security_warning)))
                .setNegativeButton(R.string.settings_dialog_cancel, null)
                .setPositiveButton(R.string.settings_transfer_export_anyway, (dialog, which) ->
                        launchExportPicker(appSettings, notifications, nowBar, true))
                .show();
    }

    private void launchExportPicker(boolean appSettings, boolean notifications,
            boolean nowBar, boolean authentication) {
        pendingExportAppSettings = appSettings;
        pendingExportNotifications = notifications;
        pendingExportNowBar = nowBar;
        pendingExportAuthentication = authentication;
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
        String name = authentication
                ? "codex-meter-transfer-AUTH-" + stamp + ".json"
                : "codex-meter-transfer-" + stamp + ".json";
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(TRANSFER_MIME_TYPE)
                .putExtra(Intent.EXTRA_TITLE, name);
        try {
            startActivityForResult(create, REQUEST_EXPORT_TRANSFER);
        } catch (RuntimeException exception) {
            showToast(getString(R.string.settings_transfer_export_no_picker),
                    Toast.LENGTH_LONG);
        }
    }

    private void finishExport(Uri uri) {
        try {
            SettingsTransfer.Document document = SettingsTransferStore.collect(requireContext(),
                    pendingExportAppSettings, pendingExportNotifications,
                    pendingExportNowBar, pendingExportAuthentication);
            SettingsTransferStore.write(requireContext(), uri, document);
            showToast(getString(document.hasAuthentication()
                    ? R.string.settings_transfer_exported_with_auth
                    : R.string.settings_transfer_exported), Toast.LENGTH_LONG);
        } catch (Exception exception) {
            showToast(failureMessage(exception, R.string.settings_transfer_export_failed),
                    Toast.LENGTH_LONG);
        }
    }

    private void beginImport(Uri uri) {
        try {
            showImportSectionDialog(SettingsTransferStore.read(requireContext(), uri));
        } catch (Exception exception) {
            showToast(failureMessage(exception, R.string.settings_transfer_read_failed),
                    Toast.LENGTH_LONG);
        }
    }

    private void showImportSectionDialog(SettingsTransfer.Document document) {
        List<String> present = document.presentSections();
        if (present.isEmpty()) {
            showToast(getString(R.string.settings_transfer_file_has_no_sections),
                    Toast.LENGTH_LONG);
            return;
        }
        String[] labels = new String[present.size()];
        boolean[] checked = new boolean[present.size()];
        for (int i = 0; i < present.size(); i++) {
            String section = present.get(i);
            boolean authentication = SettingsTransfer.isAuthenticationSection(section);
            labels[i] = authentication
                    ? sectionLabel(section) + "\n"
                            + getString(R.string.settings_transfer_import_auth_warning)
                    : sectionLabel(section);
            // Replacing the signed-in account is opt-in.
            checked[i] = !authentication;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_transfer_import_dialog_title)
                .setMultiChoiceItems(labels, checked,
                        (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton(R.string.settings_dialog_cancel, null)
                .setPositiveButton(R.string.settings_dialog_continue,
                        (dialog, which) -> onImportSectionsChosen(document, present, checked))
                .show();
    }

    private void onImportSectionsChosen(SettingsTransfer.Document document,
            List<String> present, boolean[] checked) {
        boolean appSettings = false;
        boolean notifications = false;
        boolean nowBar = false;
        boolean authentication = false;
        for (int i = 0; i < present.size(); i++) {
            if (!checked[i]) {
                continue;
            }
            String section = present.get(i);
            if (SettingsTransfer.SECTION_APP_SETTINGS.equals(section)) {
                appSettings = true;
            } else if (SettingsTransfer.SECTION_NOTIFICATIONS.equals(section)) {
                notifications = true;
            } else if (SettingsTransfer.SECTION_NOW_BAR.equals(section)) {
                nowBar = true;
            } else if (SettingsTransfer.SECTION_AUTHENTICATION.equals(section)) {
                authentication = true;
            }
        }
        if (!(appSettings || notifications || nowBar || authentication)) {
            showToast(getString(R.string.settings_transfer_select_import), Toast.LENGTH_LONG);
            return;
        }
        if (authentication) {
            confirmSensitiveImport(document, appSettings, notifications, nowBar);
        } else {
            finishImport(document, appSettings, notifications, nowBar, false);
        }
    }

    /** Extra confirmation before imported tokens replace the current ChatGPT sign-in. */
    private void confirmSensitiveImport(SettingsTransfer.Document document,
            boolean appSettings, boolean notifications, boolean nowBar) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_transfer_import_auth_title)
                .setMessage(getString(R.string.settings_transfer_import_auth_message,
                        getString(R.string.settings_transfer_security_warning)))
                .setNegativeButton(R.string.settings_dialog_cancel, null)
                .setPositiveButton(R.string.settings_transfer_import_anyway, (dialog, which) ->
                        finishImport(document, appSettings, notifications, nowBar, true))
                .show();
    }

    private void finishImport(SettingsTransfer.Document document, boolean appSettings,
            boolean notifications, boolean nowBar, boolean authentication) {
        try {
            SettingsTransferStore.ApplyResult result = SettingsTransferStore.apply(
                    requireContext(), document, appSettings, notifications, nowBar,
                    authentication);
            List<String> titles = new ArrayList<>();
            for (String section : result.appliedSections) {
                titles.add(sectionTitle(section));
            }
            String sections = String.join(
                    getString(R.string.settings_transfer_section_separator), titles);
            showToast(getString(result.authenticationImported
                    ? R.string.settings_transfer_imported_with_auth
                    : R.string.settings_transfer_imported, sections), Toast.LENGTH_LONG);
            requireActivity().recreate();
        } catch (Exception exception) {
            showToast(failureMessage(exception, R.string.settings_transfer_import_failed),
                    Toast.LENGTH_LONG);
        }
    }

    /** Title and summary rows for a {@link SettingsTransfer} section id. */
    private String sectionLabel(String section) {
        return sectionTitle(section) + "\n" + sectionSummary(section);
    }

    private String sectionTitle(String section) {
        if (SettingsTransfer.SECTION_APP_SETTINGS.equals(section)) {
            return getString(R.string.settings_transfer_section_app_settings);
        }
        if (SettingsTransfer.SECTION_NOTIFICATIONS.equals(section)) {
            return getString(R.string.settings_transfer_section_notifications);
        }
        if (SettingsTransfer.SECTION_NOW_BAR.equals(section)) {
            return getString(R.string.settings_transfer_section_now_bar);
        }
        if (SettingsTransfer.SECTION_AUTHENTICATION.equals(section)) {
            return getString(R.string.settings_transfer_section_authentication);
        }
        return SettingsTransfer.sectionTitle(section);
    }

    private String sectionSummary(String section) {
        if (SettingsTransfer.SECTION_APP_SETTINGS.equals(section)) {
            return getString(R.string.settings_transfer_section_app_settings_summary);
        }
        if (SettingsTransfer.SECTION_NOTIFICATIONS.equals(section)) {
            return getString(R.string.settings_transfer_section_notifications_summary);
        }
        if (SettingsTransfer.SECTION_NOW_BAR.equals(section)) {
            return getString(R.string.settings_transfer_section_now_bar_summary);
        }
        if (SettingsTransfer.SECTION_AUTHENTICATION.equals(section)) {
            return getString(R.string.settings_transfer_section_authentication_summary);
        }
        return SettingsTransfer.sectionSummary(section);
    }

    /**
     * Localized text for a failed export or import: transfer-file problems by their stable
     * code, otherwise the exception's own (already localized) message, else {@code fallback}.
     */
    private String failureMessage(Exception exception, int fallback) {
        if (exception instanceof SettingsTransfer.TransferException) {
            return problemMessage((SettingsTransfer.TransferException) exception);
        }
        String message = exception.getLocalizedMessage();
        return message == null || message.isEmpty() ? getString(fallback) : message;
    }

    private String problemMessage(SettingsTransfer.TransferException exception) {
        switch (exception.problem) {
            case EMPTY_FILE:
                return getString(R.string.settings_transfer_error_empty);
            case NOT_TRANSFER_FILE:
                return getString(R.string.settings_transfer_error_not_transfer_file);
            case UNSUPPORTED_VERSION:
                return getString(R.string.settings_transfer_error_unsupported_version,
                        exception.argument);
            case NO_SECTIONS:
                return getString(R.string.settings_transfer_error_no_sections);
            case LEAD_TIMES_NOT_ARRAY:
                return getString(R.string.settings_transfer_error_lead_times_not_array,
                        exception.argument);
            case LEAD_TIMES_INVALID_ENTRY:
                return getString(R.string.settings_transfer_error_lead_times_invalid_entry,
                        exception.argument);
            case LEAD_TIMES_NULL_ENTRY:
                return getString(R.string.settings_transfer_error_lead_times_null_entry,
                        exception.argument);
            case LEAD_TIMES_NON_INTEGER_ENTRY:
                return getString(R.string.settings_transfer_error_lead_times_non_integer_entry,
                        exception.argument);
            case LEAD_TIMES_EMPTY_ENTRY:
                return getString(R.string.settings_transfer_error_lead_times_empty_entry,
                        exception.argument);
            case LEAD_TIMES_NON_NUMERIC_ENTRY:
                return getString(R.string.settings_transfer_error_lead_times_non_numeric_entry,
                        exception.argument);
            case LEAD_TIMES_OUT_OF_RANGE_ENTRY:
                return getString(
                        R.string.settings_transfer_error_lead_times_out_of_range_entry,
                        exception.argument);
            default:
                return exception.getLocalizedMessage();
        }
    }
}
