package me.pipi.codexmeter;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-facing secure download and PackageInstaller hand-off flow. */
public final class UpdateActivity extends AppCompatActivity {
    public static final String EXTRA_VERSION = "release_version";
    public static final String EXTRA_FORCE_CHECK = "force_check";
    public static final String EXTRA_START_INSTALL = "start_install";

    /** Download progress is shown in tenths of a percent. */
    private static final int PROGRESS_MAX = 1000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private GitHubRelease release;
    private ProgressBar progress;
    private TextView status;
    private boolean waitingForInstallPermission;
    private boolean operationRunning;
    private boolean startInstallPending;
    private boolean dark;

    @Override
    protected void onCreate(Bundle bundle) {
        Ui.applySelectedTheme(this);
        super.onCreate(bundle);
        dark = Ui.isDark(this);
        content = Ui.installPage(this, getString(R.string.updates_app_update_title), true).content;
        String requested = getIntent().getStringExtra(EXTRA_VERSION);
        release = UpdatePreferences.findVersion(this, requested);
        boolean force = getIntent().getBooleanExtra(EXTRA_FORCE_CHECK, false);
        startInstallPending = getIntent().getBooleanExtra(EXTRA_START_INSTALL, false);
        UpdateNotificationManager.dismiss(this);
        if (release == null || force) {
            checkReleases(requested);
        } else {
            render();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String requested = intent.getStringExtra(EXTRA_VERSION);
        startInstallPending = intent.getBooleanExtra(EXTRA_START_INSTALL, false);
        UpdateNotificationManager.dismiss(this);
        if (requested != null && !requested.trim().isEmpty()) {
            release = UpdatePreferences.findVersion(this, requested);
        }
        if (release == null) {
            checkReleases(requested);
        } else {
            render();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForInstallPermission && canInstallPackages()) {
            waitingForInstallPermission = false;
            beginInstall();
            return;
        }
        if (status != null && !operationRunning) {
            String error = UpdatePreferences.installError(this);
            if (!error.isEmpty()) {
                setStatus(error, Ui.danger(dark));
            }
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    /**
     * Refreshes the release list in the background, then shows {@code requestedVersion} or, when
     * it is not published, the newest release of the selected channel.
     */
    private void checkReleases(String requestedVersion) {
        operationRunning = true;
        content.removeAllViews();
        content.addView(Ui.indeterminateLoading(this, getString(R.string.updates_checking)));
        executor.execute(() -> {
            try {
                ReleaseUpdateClient.check(getApplicationContext());
                List<GitHubRelease> releases = UpdatePreferences.releases(getApplicationContext());
                GitHubRelease selected = GitHubReleaseParser.findVersion(releases, requestedVersion);
                if (selected == null) {
                    selected = UpdateChannel.trackedRelease(releases,
                            UpdatePreferences.channel(getApplicationContext()));
                }
                GitHubRelease result = selected;
                postUi(() -> {
                    operationRunning = false;
                    release = result;
                    render();
                });
            } catch (Exception exception) {
                postUi(() -> {
                    operationRunning = false;
                    renderError(ReleaseUpdateClient.safeMessage(this, exception));
                });
            }
        });
    }

    private void render() {
        content.removeAllViews();
        if (release == null) {
            String error = UpdatePreferences.lastError(this);
            renderError(error.isEmpty()
                    ? getString(R.string.updates_no_installable_releases_published)
                    : error);
            return;
        }
        String installedVersion = UpdatePreferences.installedVersion(this);
        int comparison = ReleaseVersion.compare(release.version, installedVersion);
        boolean irreversible = ReleaseUpdatePolicy.isIrreversible(release.version);
        boolean returnToStable = UpdateChannel.isReturnToStable(release, installedVersion);

        LinearLayout card = Ui.card(this, dark);
        TextView title = Ui.text(this, titleText(comparison, returnToStable), 20,
                Ui.mainText(dark));
        title.setTypeface(Ui.mediumTypeface(this));
        card.addView(title);
        TextView summary = Ui.text(this,
                detailText(installedVersion, comparison, irreversible, returnToStable), 14,
                irreversible ? Ui.danger(dark) : Ui.secondaryText(dark));
        card.addView(summary, wrapContentParams(0, 8, 0, 18));
        if (irreversible) {
            addIrreversibleActions(card);
        } else {
            addInstallActions(card, comparison, returnToStable);
        }
        content.addView(card);

        if (!release.notes.isEmpty()) {
            addReleaseNotes();
        }

        boolean autoInstall = startInstallPending && (comparison > 0 || returnToStable)
                && !irreversible;
        startInstallPending = false;
        if (autoInstall) {
            content.post(this::requestInstall);
        }
    }

    private String titleText(int comparison, boolean returnToStable) {
        if (comparison > 0) {
            return getString(R.string.updates_version_available_title, release.version);
        }
        if (comparison == 0) {
            return getString(R.string.updates_current_version_title, release.version);
        }
        if (returnToStable) {
            return getString(R.string.updates_return_to_version_title, release.version);
        }
        return getString(R.string.updates_older_release_title, release.version);
    }

    private String detailText(String installedVersion, int comparison, boolean irreversible,
            boolean returnToStable) {
        if (irreversible) {
            return getString(R.string.updates_detail_irreversible, installedVersion);
        }
        if (comparison > 0) {
            return getString(release.prerelease
                    ? R.string.updates_detail_alpha_upgrade : R.string.updates_detail_upgrade,
                    installedVersion);
        }
        if (comparison == 0) {
            return getString(R.string.updates_detail_reinstall);
        }
        if (returnToStable) {
            return getString(R.string.updates_detail_return_to_stable, installedVersion);
        }
        return getString(R.string.updates_detail_downgrade, installedVersion);
    }

    /** Pre-updater releases can only be installed manually from GitHub. */
    private void addIrreversibleActions(LinearLayout card) {
        TextView irreversibleDetail = Ui.text(this, getString(R.string.updates_irreversible_detail,
                ReleaseUpdatePolicy.FIRST_IN_APP_UPDATE_VERSION), 13, Ui.secondaryText(dark));
        card.addView(irreversibleDetail, wrapContentParams(0, 0, 0, 18));
        Button github = Ui.nativePrimaryButton(this, getString(R.string.updates_open_on_github));
        github.setOnClickListener(view -> openReleasePage());
        card.addView(github, fixedHeightParams(60));
        progress = null;
        status = null;
    }

    private void addInstallActions(LinearLayout card, int comparison, boolean returnToStable) {
        Button action = Ui.nativePrimaryButton(this,
                getString(actionLabel(comparison, returnToStable)));
        action.setOnClickListener(view -> {
            if (comparison < 0 && !returnToStable) {
                confirmOlderDownload();
            } else {
                requestInstall();
            }
        });
        card.addView(action, fixedHeightParams(60));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(PROGRESS_MAX);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = fixedHeightParams(8);
        progressParams.setMargins(0, Ui.dp(this, 18), 0, 0);
        card.addView(progress, progressParams);
        status = Ui.text(this, "", 13, Ui.secondaryText(dark));
        status.setVisibility(View.GONE);
        card.addView(status, wrapContentParams(0, 10, 0, 0));
    }

    /** String resource for the primary action of a release relative to the installed one. */
    private static int actionLabel(int comparison, boolean returnToStable) {
        if (returnToStable) {
            return R.string.updates_action_return_to_stable;
        }
        if (comparison < 0) {
            return R.string.updates_action_download_older;
        }
        if (comparison == 0) {
            return R.string.updates_action_reinstall;
        }
        return R.string.updates_action_install;
    }

    private void addReleaseNotes() {
        TextView heading = Ui.text(this, getString(R.string.updates_whats_new), 15,
                Ui.secondaryText(dark));
        heading.setTypeface(Ui.mediumTypeface(this));
        content.addView(heading, wrapContentParams(4, 24, 0, 10));
        LinearLayout notesCard = Ui.card(this, dark);
        notesCard.addView(ReleaseNotesUi.create(this, release.notes, dark));
        content.addView(notesCard);
    }

    private void renderError(String message) {
        startInstallPending = false;
        content.removeAllViews();
        LinearLayout card = Ui.card(this, dark);
        TextView title = Ui.text(this, getString(R.string.updates_check_unavailable_title), 20,
                Ui.mainText(dark));
        title.setTypeface(Ui.mediumTypeface(this));
        card.addView(title);
        TextView detail = Ui.text(this, message, 14, Ui.secondaryText(dark));
        card.addView(detail, wrapContentParams(0, 8, 0, 18));
        Button retry = Ui.nativePrimaryButton(this, getString(R.string.updates_check_again));
        retry.setOnClickListener(view -> checkReleases(getIntent().getStringExtra(EXTRA_VERSION)));
        card.addView(retry, fixedHeightParams(60));
        content.addView(card);
    }

    /** Asks for the "install unknown apps" permission first when it is missing. */
    private void requestInstall() {
        if (operationRunning || release == null
                || ReleaseUpdatePolicy.isIrreversible(release.version)) {
            return;
        }
        UpdatePreferences.setInstallError(this, "");
        if (!canInstallPackages()) {
            waitingForInstallPermission = true;
            new AlertDialog.Builder(this)
                    .setTitle(R.string.updates_install_permission_title)
                    .setMessage(R.string.updates_install_permission_message)
                    .setNegativeButton(android.R.string.cancel,
                            (dialog, which) -> waitingForInstallPermission = false)
                    .setPositiveButton(R.string.updates_open_settings,
                            (dialog, which) -> openInstallPermissionSettings())
                    .show();
            return;
        }
        beginInstall();
    }

    private void openInstallPermissionSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException exception) {
            waitingForInstallPermission = false;
            Toast.makeText(this, R.string.updates_install_permission_settings_failed,
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean canInstallPackages() {
        return getPackageManager().canRequestPackageInstalls();
    }

    /** Downloads and verifies the APK off the main thread, then hands it to PackageInstaller. */
    private void beginInstall() {
        if (operationRunning || release == null
                || ReleaseUpdatePolicy.isIrreversible(release.version)) {
            return;
        }
        operationRunning = true;
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        setStatus(getString(R.string.update_downloading), Ui.secondaryText(dark));
        executor.execute(() -> {
            try {
                UpdateInstaller.PreparedUpdate prepared = UpdateInstaller.prepare(
                        getApplicationContext(), release, (downloaded, total) ->
                                postUi(() -> showDownloadProgress(downloaded, total)));
                postUi(() -> setStatus(getString(R.string.update_opening_installer),
                        Ui.secondaryText(dark)));
                UpdateInstaller.commit(getApplicationContext(), prepared);
                postUi(() -> {
                    operationRunning = false;
                    setStatus(getString(R.string.update_waiting_for_installer),
                            Ui.secondaryText(dark));
                });
            } catch (Exception exception) {
                UpdatePreferences.setInstallError(getApplicationContext(),
                        safeMessage(exception));
                postUi(() -> {
                    operationRunning = false;
                    progress.setVisibility(View.GONE);
                    setStatus(safeMessage(exception), Ui.danger(dark));
                });
            }
        });
    }

    private void showDownloadProgress(long downloaded, long total) {
        if (progress != null && total > 0L) {
            progress.setProgress((int) Math.min(PROGRESS_MAX, downloaded * PROGRESS_MAX / total));
        }
    }

    private void setStatus(String message, int color) {
        if (status == null) {
            return;
        }
        status.setTextColor(color);
        status.setText(message);
        status.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Android cannot downgrade in place, so older releases are offered as a browser download. */
    private void confirmOlderDownload() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.updates_downgrade_title)
                .setMessage(R.string.updates_downgrade_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.updates_open_apk_download,
                        (dialog, which) -> openApkDownload())
                .show();
    }

    private void openApkDownload() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl)));
        } catch (RuntimeException exception) {
            Toast.makeText(this, R.string.updates_no_browser_for_apk, Toast.LENGTH_LONG).show();
        }
    }

    private void openReleasePage() {
        String url = release == null ? "" : release.pageUrl;
        if (url.isEmpty() && release != null) {
            url = release.apkUrl;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException exception) {
            Toast.makeText(this, R.string.updates_no_browser_for_release_page,
                    Toast.LENGTH_LONG).show();
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception == null ? "" : exception.getLocalizedMessage();
        if (message == null || message.trim().isEmpty()) {
            message = getString(R.string.updates_error_prepare_failed);
        }
        return message.length() <= UpdatePreferences.MAX_ERROR_LENGTH
                ? message : message.substring(0, UpdatePreferences.MAX_ERROR_LENGTH);
    }

    /** Runs {@code action} on the main thread unless the activity is going away. */
    private void postUi(Runnable action) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                action.run();
            }
        });
    }

    /** Full-width, wrap-content params with margins in dp. */
    private LinearLayout.LayoutParams wrapContentParams(int leftDp, int topDp, int rightDp,
            int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(Ui.dp(this, leftDp), Ui.dp(this, topDp), Ui.dp(this, rightDp),
                Ui.dp(this, bottomDp));
        return params;
    }

    /** Full-width params with a fixed height in dp. */
    private LinearLayout.LayoutParams fixedHeightParams(int heightDp) {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, heightDp));
    }
}
