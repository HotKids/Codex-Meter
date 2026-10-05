package me.pipi.codexmeter;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import dev.oneuiproject.oneui.utils.EdgeToEdge;
import dev.oneuiproject.oneui.widget.CardItemView;
import dev.oneuiproject.oneui.widget.RoundedLinearLayout;

/** About page: app header that collapses into developer and credit links. */
public final class AboutActivity extends AppCompatActivity {
    private static final int MENU_GITHUB = 8201;
    private static final int MENU_APP_INFO = 8202;
    /** Taps on the version (or icon) that unlock the diagnostics page. */
    private static final int DIAGNOSTIC_TAPS = 7;
    /** A countdown toast starts once this many taps remain. */
    private static final int DIAGNOSTIC_HINT_TAPS = 3;
    // Content fades in over the middle of the collapse: from 25% to 80% of the scroll range.
    private static final float CONTENT_FADE_START = 0.25f;
    private static final float CONTENT_FADE_RANGE = 0.55f;

    private boolean dark;
    private int versionTaps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applySelectedTheme(this);
        super.onCreate(savedInstanceState);
        dark = Ui.isDark(this);
        EdgeToEdge.apply(this, () -> new kotlin.Pair<>(dark, dark));
        setContentView(R.layout.activity_about);
        applySystemBarInsets();
        setupToolbar();
        setupCollapsingContent();
        TextView version = findViewById(R.id.about_header_version);
        version.setText(versionLabel());
        version.setOnClickListener(this::onVersionTap);
        findViewById(R.id.about_header_icon).setOnClickListener(this::onVersionTap);
        buildContent(findViewById(R.id.about_content));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_GITHUB, 0, "GitHub")
                .setIcon(R.drawable.ic_github_24)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, MENU_APP_INFO, 1, R.string.auth_about_app_info)
                .setIcon(R.drawable.ic_ms_info)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        if (itemId == MENU_GITHUB) {
            openUrl(GitHubReleaseSource.REPOSITORY_URL);
            return true;
        }
        if (itemId == MENU_APP_INFO) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private String versionLabel() {
        return getString(R.string.about_version, Ui.versionName(this));
    }

    private void buildContent(LinearLayout content) {
        content.addView(buildAppCard());
        content.addView(sectionTitle(getString(R.string.auth_about_development)));
        RoundedLinearLayout developer = Ui.cardGroup(this, dark);
        developer.addView(personRow("HotKids", "", R.drawable.hotkids_avatar, false,
                "https://github.com/HotKids"));
        content.addView(developer);
        content.addView(sectionTitle(getString(R.string.auth_about_credits)));
        RoundedLinearLayout credits = Ui.cardGroup(this, dark);
        credits.addView(personRow(getString(R.string.about_benit_title),
                "",
                R.drawable.benit_github_avatar, false, "https://github.com/BenItBuhner/Codex-Meter"));
        credits.addView(personRow(getString(R.string.about_oneui_title),
                "",
                R.drawable.tribalfs_github_avatar, true, "https://github.com/tribalfs/oneui-design"));
        credits.addView(personRow(getString(R.string.auth_about_tjg_title),
                "",
                R.drawable.codex_profile_avatar, true, "https://tjg.gg"));
        content.addView(credits);
    }

    private LinearLayout buildAppCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        card.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Ui.text(this, getString(R.string.app_name), 18, Ui.mainText(this, dark)));
        text.addView(Ui.text(this, versionLabel(), 14, Ui.secondaryText(dark)));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1);
        textParams.setMargins(Ui.dp(this, 20), 0, 0, 0);
        card.addView(text, textParams);
        card.setOnClickListener(this::onVersionTap);
        return card;
    }

    private void onVersionTap(View ignored) {
        versionTaps++;
        int remaining = DIAGNOSTIC_TAPS - versionTaps;
        if (remaining <= 0) {
            Toast.makeText(this, R.string.auth_about_diagnostics_unlocked, Toast.LENGTH_SHORT)
                    .show();
            startActivity(SettingsActivity.diagnosticsIntent(this));
            versionTaps = 0;
        } else if (remaining <= DIAGNOSTIC_HINT_TAPS) {
            Toast.makeText(this, getResources().getQuantityString(
                    R.plurals.auth_about_diagnostics_taps_remaining, remaining, remaining),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private CardItemView personRow(String title, String summary, int avatar, boolean divider,
            String url) {
        CardItemView row = Ui.actionRow(this, title, summary, avatar, view -> openUrl(url));
        row.setIconSize(Ui.dp(this, 44));
        Ui.makeAvatar(row.getIconImageView());
        row.setShowTopDivider(divider);
        return row;
    }

    /** Transparent toolbar over the collapsing header, with an up arrow and no title. */
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.about_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setOverflowIcon(androidx.appcompat.content.res.AppCompatResources.getDrawable(
                this, R.drawable.ic_ms_more_vert));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_ms_arrow_back);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        AppBarLayout appBar = findViewById(R.id.about_app_bar);
        appBar.setBackgroundColor(Color.TRANSPARENT);
        appBar.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        appBar.setElevation(0);
        appBar.setStateListAnimator(null);
        CollapsingToolbarLayout collapsing = findViewById(R.id.about_collapsing_toolbar);
        collapsing.setBackgroundColor(Color.TRANSPARENT);
        collapsing.setContentScrim(new ColorDrawable(Color.TRANSPARENT));
        collapsing.setStatusBarScrim(new ColorDrawable(Color.TRANSPARENT));
        toolbar.setBackgroundColor(Color.TRANSPARENT);
        toolbar.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        toolbar.setElevation(0);
    }

    private void applySystemBarInsets() {
        View root = findViewById(R.id.about_root);
        AppBarLayout appBar = findViewById(R.id.about_app_bar);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, 0, bars.right, 0);
            appBar.setPadding(0, bars.top, 0, 0);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    /**
     * Cross-fades the header and the content as the app bar collapses: the swipe hint fades out
     * in the first half, the gradient fades in, and the content appears mid-collapse.
     */
    private void setupCollapsingContent() {
        View content = findViewById(R.id.about_content);
        View hint = findViewById(R.id.about_swipe_hint);
        View fade = findViewById(R.id.about_gradient_fade);
        content.setAlpha(0);
        AppBarLayout appBarLayout = findViewById(R.id.about_app_bar);
        appBarLayout.addOnOffsetChangedListener((appBar, offset) -> {
            float range = Math.max(1, appBar.getTotalScrollRange());
            float progress = Math.abs(offset) / range;
            content.setAlpha(clamp((progress - CONTENT_FADE_START) / CONTENT_FADE_RANGE));
            fade.setAlpha(clamp(progress * 2f));
            hint.setAlpha(clamp(1f - progress * 2f));
            hint.setVisibility(hint.getAlpha() == 0 ? View.INVISIBLE : View.VISIBLE);
        });
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private TextView sectionTitle(String title) {
        TextView label = Ui.text(this, title, 14, Ui.secondaryText(dark));
        label.setTypeface(Ui.mediumTypeface(this));
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.setMargins(Ui.dp(this, 20), Ui.dp(this, 20), 0, Ui.dp(this, 10));
        label.setLayoutParams(params);
        return label;
    }
}
