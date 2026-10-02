package dev.bennett.codexmeter;

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
            showToast("No file picker is available to import a transfer file.",
                    Toast.LENGTH_LONG);
        }
    }

    private void showExportSectionDialog() {
        boolean signedIn = SecureTokenStore.isSignedIn(requireContext());
        String[] labels = {
                sectionLabel(SettingsTransfer.SECTION_APP_SETTINGS),
                sectionLabel(SettingsTransfer.SECTION_NOTIFICATIONS),
                sectionLabel(SettingsTransfer.SECTION_NOW_BAR),
                SettingsTransfer.sectionTitle(SettingsTransfer.SECTION_AUTHENTICATION) + "\n"
                        + (signedIn
                        ? SettingsTransfer.sectionSummary(SettingsTransfer.SECTION_AUTHENTICATION)
                        : "Sign in first to export ChatGPT authentication")
        };
        // Authentication is opt-in; everything else starts selected.
        boolean[] checked = {true, true, true, false};
        new AlertDialog.Builder(requireContext())
                .setTitle("Export sections")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                    if (which == EXPORT_AUTHENTICATION && isChecked && !signedIn) {
                        checked[EXPORT_AUTHENTICATION] = false;
                        ((AlertDialog) dialog).getListView()
                                .setItemChecked(EXPORT_AUTHENTICATION, false);
                        showToast("Sign in before exporting authentication.", Toast.LENGTH_LONG);
                        return;
                    }
                    checked[which] = isChecked;
                })
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) -> onExportSectionsChosen(checked))
                .show();
    }

    private void onExportSectionsChosen(boolean[] checked) {
        boolean appSettings = checked[EXPORT_APP_SETTINGS];
        boolean notifications = checked[EXPORT_NOTIFICATIONS];
        boolean nowBar = checked[EXPORT_NOW_BAR];
        boolean authentication = checked[EXPORT_AUTHENTICATION];
        if (!(appSettings || notifications || nowBar || authentication)) {
            showToast("Select at least one section to export.", Toast.LENGTH_LONG);
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
                .setTitle("Authentication will be included")
                .setMessage(SettingsTransfer.SECURITY_WARNING
                        + "\n\nOnly continue if you are moving Codex Meter to another device "
                        + "you control.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Export anyway", (dialog, which) ->
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
            showToast("No file picker is available to export a transfer file.",
                    Toast.LENGTH_LONG);
        }
    }

    private void finishExport(Uri uri) {
        try {
            SettingsTransfer.Document document = SettingsTransferStore.collect(requireContext(),
                    pendingExportAppSettings, pendingExportNotifications,
                    pendingExportNowBar, pendingExportAuthentication);
            SettingsTransferStore.write(requireContext(), uri, document);
            String message = document.hasAuthentication()
                    ? "Exported. Keep this file private — it includes ChatGPT authentication."
                    : "Settings exported.";
            showToast(message, Toast.LENGTH_LONG);
        } catch (Exception exception) {
            showToast(messageOr(exception, "Could not export transfer file."),
                    Toast.LENGTH_LONG);
        }
    }

    private void beginImport(Uri uri) {
        try {
            showImportSectionDialog(SettingsTransferStore.read(requireContext(), uri));
        } catch (Exception exception) {
            showToast(messageOr(exception, "Could not read transfer file."), Toast.LENGTH_LONG);
        }
    }

    private void showImportSectionDialog(SettingsTransfer.Document document) {
        List<String> present = document.presentSections();
        if (present.isEmpty()) {
            showToast("This transfer file has no sections.", Toast.LENGTH_LONG);
            return;
        }
        String[] labels = new String[present.size()];
        boolean[] checked = new boolean[present.size()];
        for (int i = 0; i < present.size(); i++) {
            String section = present.get(i);
            boolean authentication = SettingsTransfer.isAuthenticationSection(section);
            String warning = authentication
                    ? "\nWarning: replaces ChatGPT sign-in on this device" : "";
            labels[i] = sectionLabel(section) + warning;
            // Replacing the signed-in account is opt-in.
            checked[i] = !authentication;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Import sections")
                .setMultiChoiceItems(labels, checked,
                        (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue",
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
            showToast("Select at least one section to import.", Toast.LENGTH_LONG);
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
                .setTitle("Import authentication?")
                .setMessage(SettingsTransfer.SECURITY_WARNING
                        + "\n\nThis replaces ChatGPT sign-in on this device with the tokens "
                        + "from the file.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Import anyway", (dialog, which) ->
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
                titles.add(SettingsTransfer.sectionTitle(section));
            }
            String message = "Imported " + String.join(", ", titles) + ".";
            if (result.authenticationImported) {
                message += " Authentication replaced — keep the file private.";
            }
            showToast(message, Toast.LENGTH_LONG);
            requireActivity().recreate();
        } catch (Exception exception) {
            showToast(messageOr(exception, "Could not import transfer file."),
                    Toast.LENGTH_LONG);
        }
    }

    private static String sectionLabel(String section) {
        return SettingsTransfer.sectionTitle(section) + "\n"
                + SettingsTransfer.sectionSummary(section);
    }

    /** The exception's own message when it has one, otherwise {@code fallback}. */
    private static String messageOr(Exception exception, String fallback) {
        String message = exception.getMessage();
        return message == null || message.isEmpty() ? fallback : message;
    }
}
