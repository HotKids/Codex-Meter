package dev.bennett.codexmeter;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Advanced release picker with explicit downgrade constraints. */
public final class ReleaseHistoryActivity extends AppCompatActivity {
    /** Length of the {@code yyyy-MM-dd} prefix of GitHub's ISO-8601 timestamps. */
    private static final int ISO_DATE_LENGTH = 10;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private boolean dark;

    @Override
    protected void onCreate(Bundle bundle) {
        Ui.applySelectedTheme(this);
        super.onCreate(bundle);
        dark = Ui.isDark(this);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
        content = Ui.installPage(this, "Release history", true).content;
        List<GitHubRelease> cached = UpdatePreferences.releases(this);
        if (cached.isEmpty()) {
            showLoading();
        } else {
            render(cached, false);
        }
        refresh();
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

    private void showLoading() {
        content.removeAllViews();
        content.addView(Ui.indeterminateLoading(this, "Loading release history"));
    }

    /** Re-checks GitHub in the background; on failure shows the cached list with the error. */
    private void refresh() {
        executor.execute(() -> {
            try {
                List<GitHubRelease> releases = ReleaseUpdateClient.check(getApplicationContext());
                runOnUiThread(() -> render(releases, false));
            } catch (Exception exception) {
                List<GitHubRelease> cached = UpdatePreferences.releases(getApplicationContext());
                runOnUiThread(() -> render(cached, true));
            }
        });
    }

    private void render(List<GitHubRelease> releases, boolean failed) {
        content.removeAllViews();
        String installedVersion = UpdatePreferences.installedVersion(this);
        addInstalledVersionNotice(installedVersion);

        if (failed) {
            TextView warning = Ui.text(this,
                    UpdatePreferences.lastError(this), 13, Ui.danger(dark));
            content.addView(warning, wrapContentParams(4, 16, 0, 0));
        }
        if (releases == null || releases.isEmpty()) {
            addEmptyState();
            return;
        }

        TextView heading = Ui.text(this, "Published versions", 15, Ui.secondaryText(dark));
        heading.setTypeface(Ui.mediumTypeface(this));
        content.addView(heading, wrapContentParams(4, 24, 0, 10));
        for (GitHubRelease release : releases) {
            addRelease(release, installedVersion);
        }
    }

    private void addInstalledVersionNotice(String installedVersion) {
        LinearLayout notice = Ui.card(this, dark);
        TextView current = Ui.text(this, "Installed version " + installedVersion, 18,
                Ui.mainText(dark));
        current.setTypeface(Ui.mediumTypeface(this));
        notice.addView(current);
        String note = "Newer and matching releases from Codex Meter "
                + ReleaseUpdatePolicy.FIRST_IN_APP_UPDATE_VERSION
                + " onward are checksum- and signature-verified in the app. Releases before "
                + ReleaseUpdatePolicy.FIRST_IN_APP_UPDATE_VERSION
                + " are irreversible and must be installed from GitHub because those builds lack "
                + "working in-app updates. Other older versions still require uninstalling first, "
                + "which removes local data and widgets. Alpha builds are the exception: they "
                + "share the stable version code, so the newest stable release always installs "
                + "back in place.";
        TextView detail = Ui.text(this, note, 13, Ui.secondaryText(dark));
        notice.addView(detail, wrapContentParams(0, 8, 0, 0));
        content.addView(notice);
    }

    private void addEmptyState() {
        LinearLayout empty = Ui.card(this, dark);
        TextView title = Ui.text(this, "No installable releases yet", 18, Ui.mainText(dark));
        title.setTypeface(Ui.mediumTypeface(this));
        empty.addView(title);
        TextView detail = Ui.text(this,
                "GitHub currently has no published release containing both the expected APK "
                        + "and SHA256SUMS.txt.", 14, Ui.secondaryText(dark));
        empty.addView(detail, wrapContentParams(0, 8, 0, 0));
        content.addView(empty, wrapContentParams(0, 20, 0, 0));
    }

    private void addRelease(GitHubRelease release, String installedVersion) {
        int comparison = ReleaseVersion.compare(release.version, installedVersion);
        boolean irreversible = ReleaseUpdatePolicy.isIrreversible(release.version);
        boolean returnToStable = UpdateChannel.isReturnToStable(release, installedVersion);
        LinearLayout card = Ui.card(this, dark);
        TextView title = Ui.text(this, release.name, 18, Ui.mainText(dark));
        title.setTypeface(Ui.mediumTypeface(this));
        card.addView(title);
        TextView summary = Ui.text(this,
                "v" + release.version + statusSuffix(release, irreversible, comparison)
                        + " · " + publishedDate(release),
                13, irreversible ? Ui.danger(dark) : Ui.secondaryText(dark));
        card.addView(summary, wrapContentParams(0, 6, 0, irreversible ? 8 : 14));

        if (irreversible) {
            TextView irreversibleNote = Ui.text(this,
                    ReleaseUpdatePolicy.irreversibleSummary()
                            + ". Update manually from the GitHub release page.",
                    13, Ui.secondaryText(dark));
            card.addView(irreversibleNote, wrapContentParams(0, 0, 0, 14));
        }

        if (!release.notes.isEmpty()) {
            TextView notesHeading = Ui.text(this, "What’s new", 13, Ui.secondaryText(dark));
            notesHeading.setTypeface(Ui.mediumTypeface(this));
            card.addView(notesHeading, wrapContentParams(0, 0, 0, 8));
            card.addView(ReleaseNotesUi.create(this, release.notes, dark));
            card.addView(new Space(this), fixedHeightParams(14));
        }

        if (irreversible) {
            Button github = Ui.nativePrimaryButton(this, "Open on GitHub");
            github.setOnClickListener(view -> openUrl(release.pageUrl));
            card.addView(github, fixedHeightParams(54));
            Button details = Ui.button(this, "View release details", false, dark);
            details.setOnClickListener(view -> openReleaseDetails(release));
            LinearLayout.LayoutParams detailsParams = fixedHeightParams(54);
            detailsParams.setMargins(0, Ui.dp(this, 10), 0, 0);
            card.addView(details, detailsParams);
        } else {
            Button action = Ui.button(this, actionLabel(comparison, returnToStable),
                    comparison > 0 || returnToStable, dark);
            action.setOnClickListener(view -> openReleaseDetails(release));
            card.addView(action, fixedHeightParams(54));
        }
        content.addView(card, wrapContentParams(0, 0, 0, 14));
    }

    private static String statusSuffix(GitHubRelease release, boolean irreversible,
            int comparison) {
        if (release.prerelease) {
            return " · Prerelease";
        }
        if (irreversible) {
            return " · Irreversible";
        }
        if (comparison > 0) {
            return " · Update";
        }
        if (comparison == 0) {
            return " · Installed";
        }
        return " · Older";
    }

    private static String publishedDate(GitHubRelease release) {
        return release.publishedAt.length() >= ISO_DATE_LENGTH
                ? release.publishedAt.substring(0, ISO_DATE_LENGTH) : "Unknown date";
    }

    private static String actionLabel(int comparison, boolean returnToStable) {
        if (returnToStable) {
            return "View stable return";
        }
        if (comparison < 0) {
            return "View downgrade";
        }
        if (comparison == 0) {
            return "View reinstall";
        }
        return "View update";
    }

    private void openReleaseDetails(GitHubRelease release) {
        startActivity(new Intent(this, UpdateActivity.class)
                .putExtra(UpdateActivity.EXTRA_VERSION, release.version));
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException exception) {
            Toast.makeText(this, "No browser can open the GitHub release page.",
                    Toast.LENGTH_LONG).show();
        }
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
