package dev.bennett.codexmeter;

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
        content = Ui.installPage(this, AppText.get(R.string.phone_app_update_45b5d), true).content;
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

    private void checkReleases(String requestedVersion) {
        operationRunning = true;
        content.removeAllViews();
        content.addView(Ui.indeterminateLoading(this, AppText.get(R.string.phone_checking_for_updates_78948)));
        executor.execute(() -> {
            try {
                List<GitHubRelease> releases = ReleaseUpdateClient.check(getApplicationContext());
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
                    renderError(ReleaseUpdateClient.safeMessage(exception));
                });
            }
        });
    }

    private void render() {
        content.removeAllViews();
        if (release == null) {
            String error = UpdatePreferences.lastError(this);
            renderError(error.isEmpty()
                    ? AppText.get(R.string.phone_no_installable_github_releases_are_published_yet_d039a)
                    : error);
            return;
        }
        String installedVersion = UpdatePreferences.installedVersion(this);
        int comparison = ReleaseVersion.compare(release.version, installedVersion);
        boolean irreversible = ReleaseUpdatePolicy.isIrreversible(release.version);
        boolean returnToStable = UpdateChannel.isReturnToStable(release, installedVersion);
        LinearLayout card = Ui.card(this, dark);
        TextView title = Ui.text(this,
                comparison > 0 ? "Codex Meter " + release.version + AppText.get(R.string.phone_is_available_7b679)
                        : comparison == 0 ? "Codex Meter " + release.version
                        : returnToStable ? AppText.get(R.string.phone_return_to_codex_meter_399f2) + release.version
                        : AppText.get(R.string.phone_older_release_a0253) + release.version,
                20, Ui.mainText(dark));
        title.setTypeface(Ui.mediumTypeface(this));
        card.addView(title);
        String detail;
        if (irreversible) {
            detail = ReleaseUpdatePolicy.irreversibleSummary()
                    + AppText.get(R.string.phone_installed_b82eb) + installedVersion;
        } else if (comparison > 0) {
            detail = AppText.get(R.string.phone_installed_fac97) + installedVersion + (release.prerelease
                    ? AppText.get(R.string.phone_verified_github_alpha_upgrade_8f3a3) : AppText.get(R.string.phone_verified_github_upgrade_306e0));
        } else if (comparison == 0) {
            detail = AppText.get(R.string.phone_this_version_is_currently_installed_you_can_veri_bed16);
        } else if (returnToStable) {
            detail = AppText.get(R.string.phone_installed_fac97) + installedVersion + AppText.get(R.string.phone_alpha_builds_share_the_stable_version_code_so_th_812e3);
        } else {
            detail = AppText.get(R.string.phone_installed_fac97) + installedVersion
                    + AppText.get(R.string.phone_android_requires_uninstalling_before_this_downgr_acfb8);
        }
        TextView summary = Ui.text(this, detail, 14,
                irreversible ? Ui.danger(dark) : Ui.secondaryText(dark));
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, -2);
        summaryParams.setMargins(0, Ui.dp(this, 8), 0, Ui.dp(this, 18));
        card.addView(summary, summaryParams);

        if (irreversible) {
            TextView irreversibleDetail = Ui.text(this, ReleaseUpdatePolicy.irreversibleDetail(),
                    13, Ui.secondaryText(dark));
            LinearLayout.LayoutParams irreversibleParams = new LinearLayout.LayoutParams(-1, -2);
            irreversibleParams.setMargins(0, 0, 0, Ui.dp(this, 18));
            card.addView(irreversibleDetail, irreversibleParams);
            Button github = Ui.nativePrimaryButton(this, AppText.get(R.string.phone_open_on_github_8b81a));
            github.setOnClickListener(view -> openReleasePage());
            card.addView(github, new LinearLayout.LayoutParams(-1, Ui.dp(this, 60)));
            progress = null;
            status = null;
        } else {
            Button action = Ui.nativePrimaryButton(this,
                    returnToStable ? AppText.get(R.string.phone_return_to_stable_dcc5d)
                            : comparison < 0 ? AppText.get(R.string.phone_download_older_apk_df551)
                            : comparison == 0 ? AppText.get(R.string.phone_verify_and_reinstall_7aefc)
                            : AppText.get(R.string.phone_download_and_install_0388e));
            action.setOnClickListener(view -> {
                if (comparison < 0 && !returnToStable) {
                    confirmOlderDownload();
                } else {
                    requestInstall();
                }
            });
            card.addView(action, new LinearLayout.LayoutParams(-1, Ui.dp(this, 60)));

            progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(1000);
            progress.setVisibility(View.GONE);
            LinearLayout.LayoutParams progressParams =
                    new LinearLayout.LayoutParams(-1, Ui.dp(this, 8));
            progressParams.setMargins(0, Ui.dp(this, 18), 0, 0);
            card.addView(progress, progressParams);
            status = Ui.text(this, "", 13, Ui.secondaryText(dark));
            status.setVisibility(View.GONE);
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
            statusParams.setMargins(0, Ui.dp(this, 10), 0, 0);
            card.addView(status, statusParams);
        }
        content.addView(card);

        if (!release.notes.isEmpty()) {
            TextView heading = Ui.text(this, AppText.get(R.string.phone_what_s_new_36970), 15, Ui.secondaryText(dark));
            heading.setTypeface(Ui.mediumTypeface(this));
            LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
            headingParams.setMargins(Ui.dp(this, 4), Ui.dp(this, 24), 0, Ui.dp(this, 10));
            content.addView(heading, headingParams);
            LinearLayout notesCard = Ui.card(this, dark);
            notesCard.addView(ReleaseNotesUi.create(this, release.notes, dark));
            content.addView(notesCard);
        }

        Button history = Ui.button(this, AppText.get(R.string.phone_release_history_5044b), false, dark);
        history.setOnClickListener(view ->
                Ui.startSecondaryActivity(this, ReleaseHistoryActivity.class));
        LinearLayout.LayoutParams historyParams =
                new LinearLayout.LayoutParams(-1, Ui.dp(this, 56));
        historyParams.setMargins(0, Ui.dp(this, 20), 0, 0);
        content.addView(history, historyParams);

        if (startInstallPending && (comparison > 0 || returnToStable) && !irreversible) {
            startInstallPending = false;
            content.post(this::requestInstall);
        } else {
            startInstallPending = false;
        }
    }

    private void renderError(String message) {
        startInstallPending = false;
        content.removeAllViews();
        LinearLayout card = Ui.card(this, dark);
        TextView title = Ui.text(this, AppText.get(R.string.phone_update_check_unavailable_2aa80), 20, Ui.mainText(dark));
        title.setTypeface(Ui.mediumTypeface(this));
        card.addView(title);
        TextView detail = Ui.text(this, message, 14, Ui.secondaryText(dark));
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.setMargins(0, Ui.dp(this, 8), 0, Ui.dp(this, 18));
        card.addView(detail, detailParams);
        Button retry = Ui.nativePrimaryButton(this, AppText.get(R.string.phone_check_again_e1850));
        retry.setOnClickListener(view -> checkReleases(getIntent().getStringExtra(EXTRA_VERSION)));
        card.addView(retry, new LinearLayout.LayoutParams(-1, Ui.dp(this, 60)));
        content.addView(card);

        Button history = Ui.button(this, AppText.get(R.string.phone_release_history_5044b), false, dark);
        history.setOnClickListener(view ->
                Ui.startSecondaryActivity(this, ReleaseHistoryActivity.class));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(this, 56));
        params.setMargins(0, Ui.dp(this, 20), 0, 0);
        content.addView(history, params);
    }

    private void requestInstall() {
        if (operationRunning || release == null
                || ReleaseUpdatePolicy.isIrreversible(release.version)) {
            return;
        }
        UpdatePreferences.setInstallError(this, "");
        if (!canInstallPackages()) {
            waitingForInstallPermission = true;
            new AlertDialog.Builder(this)
                    .setTitle(AppText.get(R.string.phone_allow_app_installs_47f4f))
                    .setMessage(AppText.get(R.string.phone_android_requires_permission_for_codex_meter_to_h_7e7f1))
                    .setNegativeButton(AppText.get(R.string.widget_config_cancel), (dialog, which) ->
                            waitingForInstallPermission = false)
                    .setPositiveButton(AppText.get(R.string.phone_open_settings_fd710), (dialog, which) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (RuntimeException exception) {
                            waitingForInstallPermission = false;
                            Toast.makeText(this, AppText.get(R.string.phone_could_not_open_install_permission_settings_82d23),
                                    Toast.LENGTH_LONG).show();
                        }
                    })
                    .show();
            return;
        }
        beginInstall();
    }

    private boolean canInstallPackages() {
        return getPackageManager().canRequestPackageInstalls();
    }

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
                                postUi(() -> {
                                    if (progress != null && total > 0L) {
                                        progress.setProgress((int) Math.min(1000L,
                                                downloaded * 1000L / total));
                                    }
                                }));
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

    private void setStatus(String message, int color) {
        if (status == null) {
            return;
        }
        status.setTextColor(color);
        status.setText(message);
        status.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void confirmOlderDownload() {
        new AlertDialog.Builder(this)
                .setTitle(AppText.get(R.string.phone_downgrade_requires_uninstalling_c7971))
                .setMessage(AppText.get(R.string.phone_android_blocks_in_place_downgrades_for_ordinary_2c884))
                .setNegativeButton(AppText.get(R.string.widget_config_cancel), null)
                .setPositiveButton(AppText.get(R.string.phone_open_apk_download_3d435), (dialog, which) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl)));
                    } catch (RuntimeException exception) {
                        Toast.makeText(this, AppText.get(R.string.phone_no_browser_can_open_the_apk_download_75d83),
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void openReleasePage() {
        String url = release == null ? "" : release.pageUrl;
        if (url.isEmpty() && release != null) {
            url = release.apkUrl;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException exception) {
            Toast.makeText(this, AppText.get(R.string.phone_no_browser_can_open_the_github_release_page_ed52f),
                    Toast.LENGTH_LONG).show();
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception == null ? "" : exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = AppText.get(R.string.phone_the_update_could_not_be_prepared_3c971);
        }
        return message.length() <= 240 ? message : message.substring(0, 240);
    }

    private void postUi(Runnable action) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                action.run();
            }
        });
    }
}
