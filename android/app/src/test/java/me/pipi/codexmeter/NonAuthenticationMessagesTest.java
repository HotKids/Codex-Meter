package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import androidx.fragment.app.Fragment;
import java.io.IOException;
import java.lang.reflect.Method;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "zh-rCN",
        shadows = SettingsAccountCardTest.InMemoryTokenStore.class)
public class NonAuthenticationMessagesTest {
    private Context app;

    @Before
    public void resetSyntheticState() {
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("codex_meter_settings_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        app.getSharedPreferences("codex_meter_updates_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = null;
    }

    @Test
    public void transferFailuresKeepStableProblemsAndHideUnknownProviderText() throws Exception {
        try (ActivityController<SettingsActivity> controller = Robolectric.buildActivity(
                SettingsActivity.class,
                SettingsActivity.pageIntent(app, SettingsActivity.PAGE_TRANSFER)).setup()) {
            Fragment fragment = controller.get().getSupportFragmentManager()
                    .findFragmentById(R.id.settings_fragment);
            assertEquals(app.getString(R.string.settings_transfer_read_failed),
                    transferMessage(fragment, new IOException("Synthetic provider failure")));
            assertEquals(app.getString(R.string.settings_transfer_error_unsupported_version, "99"),
                    transferMessage(fragment, new SettingsTransfer.TransferException(
                            SettingsTransfer.Problem.UNSUPPORTED_VERSION, "99",
                            "Synthetic unsupported transfer version")));
            Exception noSelection = assertThrows(Exception.class,
                    () -> SettingsTransferStore.collect(app, false, false, false, false));
            assertEquals(app.getString(R.string.settings_transfer_select_export),
                    transferMessage(fragment, noSelection));
        }
    }

    @Test
    public void diagnosticsExportKeepsKnownFailureAndHidesUnknownProviderText() throws Exception {
        try (ActivityController<SettingsActivity> controller = Robolectric.buildActivity(
                SettingsActivity.class, SettingsActivity.diagnosticsIntent(app)).setup()) {
            Fragment fragment = controller.get().getSupportFragmentManager()
                    .findFragmentById(R.id.settings_fragment);
            Method reason = SettingsDiagnosticsFragment.class.getDeclaredMethod(
                    "exportFailureReason", Exception.class);
            reason.setAccessible(true);
            assertEquals(app.getString(R.string.dashboard_operation_failed),
                    reason.invoke(fragment, new IOException("Synthetic export failure")));
            assertEquals(app.getString(R.string.settings_diagnostics_export_file_unavailable),
                    reason.invoke(fragment, new DiagnosticLog.ExportFileUnavailableException()));
        }
    }

    @Test
    public void githubFailuresUseHttpStatusInsteadOfRemoteEnglish() throws Exception {
        assertEquals(app.getString(R.string.updates_error_check_http, 404),
                githubMessage(404, "{\"message\":\"Synthetic remote not found\"}"));
        assertEquals(app.getString(R.string.updates_error_rate_limited),
                githubMessage(403, "{\"message\":\"Synthetic rate limit\"}"));
        assertEquals(app.getString(R.string.updates_error_rate_limited),
                githubMessage(429, "{\"message\":\"Synthetic retry later\"}"));
    }

    @Test
    public void releaseCheckHidesUnexpectedExceptions() {
        assertEquals(app.getString(R.string.updates_error_check_failed),
                ReleaseUpdateClient.safeMessage(app,
                        new IllegalStateException("Synthetic platform check failure")));
    }

    @Test
    public void updatePreparationHidesUnknownTextAndKeepsKnownValidation() throws Exception {
        try (ActivityController<UpdateActivity> controller =
                Robolectric.buildActivity(UpdateActivity.class)) {
            UpdateActivity activity = controller.get();
            assertEquals(app.getString(R.string.updates_error_prepare_failed),
                    updateMessage(activity, new IllegalStateException("Synthetic platform failure")));
            Exception noRelease = assertThrows(Exception.class,
                    () -> UpdateInstaller.prepare(app, null, null));
            assertEquals(app.getString(R.string.updates_error_no_release_selected),
                    updateMessage(activity, noRelease));
            assertEquals(app.getString(R.string.updates_error_downgrade_unsupported),
                    updateMessage(activity, new UpdateInstaller.DowngradeNotSupportedException(
                            "Synthetic platform downgrade text")));
            assertEquals(app.getString(R.string.updates_error_download_canceled),
                    updateMessage(activity, new InterruptedException("Synthetic interrupted download")));
        }
    }

    @Test
    public void installerFailureUsesStableStatusAndSuccessClearsIt() {
        UpdateInstallReceiver receiver = new UpdateInstallReceiver();
        Intent failure = new Intent(AppConstants.ACTION_INSTALL_STATUS)
                .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE_CONFLICT)
                .putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "Synthetic INSTALL_FAILED_CONFLICT");
        receiver.onReceive(app, failure);
        assertEquals(app.getString(R.string.updates_error_install_rejected,
                PackageInstaller.STATUS_FAILURE_CONFLICT), UpdatePreferences.installError(app));
        assertFalse(UpdatePreferences.installError(app).contains("Synthetic"));
        receiver.onReceive(app, new Intent(AppConstants.ACTION_INSTALL_STATUS)
                .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_SUCCESS));
        assertEquals("", UpdatePreferences.installError(app));
    }

    private static String transferMessage(Fragment fragment, Exception exception) throws Exception {
        Method method = SettingsTransferFragment.class.getDeclaredMethod(
                "failureMessage", Exception.class, int.class);
        method.setAccessible(true);
        return (String) method.invoke(fragment, exception, R.string.settings_transfer_read_failed);
    }

    private String githubMessage(int status, String body) throws Exception {
        Method method = ReleaseUpdateClient.class.getDeclaredMethod(
                "githubError", Context.class, int.class, String.class);
        method.setAccessible(true);
        Object failure = method.invoke(null, app, status, body);
        return failure instanceof Exception
                ? ((Exception) failure).getLocalizedMessage() : (String) failure;
    }

    private static String updateMessage(UpdateActivity activity, Exception exception) throws Exception {
        Method method = UpdateActivity.class.getDeclaredMethod("safeMessage", Exception.class);
        method.setAccessible(true);
        return (String) method.invoke(activity, exception);
    }
}
