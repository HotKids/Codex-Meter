package me.pipi.codexmeter;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.SeslProgressBar;
import dev.oneuiproject.oneui.ktx.ActivityKt;
import dev.oneuiproject.oneui.layout.ToolbarLayout;
import dev.oneuiproject.oneui.popover.PopOverOptions;
import dev.oneuiproject.oneui.utils.TypefaceUtilsKt;
import dev.oneuiproject.oneui.widget.CardItemView;
import dev.oneuiproject.oneui.widget.RoundedLinearLayout;
import dev.oneuiproject.oneui.widget.Separator;

/** Shared One UI theme, palette, typography, and programmatic view factories for the phone app. */
public final class Ui {
    private static final float POPUP_CORNER_RADIUS_DP = 18.0f;
    /** Large enough to round any control into a pill. */
    private static final float PILL_CORNER_RADIUS_DP = 999.0f;
    /** Samsung devices at least this wide (tablets, unfolded foldables) open pop-overs. */
    private static final int POP_OVER_MIN_SMALLEST_WIDTH_DP = 600;
    private static final float ACCENT_SATURATION_SCALE = 0.72f;
    /** Accents brighter than this get black text; darker ones get white. */
    private static final float ON_ACCENT_LUMINANCE_THRESHOLD = 0.55f;

    public static final class Page {
        public final ToolbarLayout toolbar;
        public final LinearLayout content;

        private Page(ToolbarLayout toolbar, LinearLayout content) {
            this.toolbar = toolbar;
            this.content = content;
        }
    }

    public static final class ConfigPage {
        public final ToolbarLayout toolbar;
        public final LinearLayout content;
        public final FrameLayout preview;
        public final TextView cancel;
        public final TextView save;

        private ConfigPage(ToolbarLayout toolbar, LinearLayout content, FrameLayout preview,
                TextView cancel, TextView save) {
            this.toolbar = toolbar;
            this.content = content;
            this.preview = preview;
            this.cancel = cancel;
            this.save = save;
        }
    }

    private Ui() {
    }

    // ---------------------------------------------------------------------------------------
    // Theme and page scaffolding
    // ---------------------------------------------------------------------------------------

    public static void applySelectedTheme(Activity activity) {
        String appTheme = AppPreferences.getAppTheme(activity);
        if (activity instanceof AppCompatActivity) {
            ((AppCompatActivity) activity).getDelegate().setLocalNightMode(nightModeFor(appTheme));
        }
        activity.setTheme(AppPreferences.isMaterialYouEnabled(activity)
                ? R.style.AppTheme_MaterialYou
                : R.style.AppTheme);
    }

    private static int nightModeFor(String appTheme) {
        if (WidgetOptions.THEME_DARK.equals(appTheme)) {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
        if (WidgetOptions.THEME_LIGHT.equals(appTheme)) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        }
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    public static Page installPage(AppCompatActivity activity, String title, boolean back) {
        ViewGroup parent = activity.findViewById(android.R.id.content);
        View root = LayoutInflater.from(activity)
                .inflate(R.layout.activity_oneui_dashboard, parent, false);
        ToolbarLayout toolbar = root.findViewById(R.id.toolbar_layout);
        LinearLayout content = root.findViewById(R.id.dashboard_content);
        configureReachToolbar(toolbar, title, back);
        activity.setContentView(root);
        return new Page(toolbar, content);
    }

    public static void configureReachToolbar(ToolbarLayout toolbar, String title, boolean back) {
        toolbar.setTitle(title);
        setToolbarBack(toolbar, back);
        // Force SESL to recalculate its responsive app-bar height after XML inflation.
        toolbar.setExpandable(false);
        toolbar.setExpandable(true);
        toolbar.setExpanded(true, false);
    }

    static void setToolbarBack(ToolbarLayout toolbar, boolean back) {
        toolbar.setShowNavigationButtonAsBack(back);
        toolbar.getToolbar().setOverflowIcon(
                androidx.appcompat.content.res.AppCompatResources.getDrawable(
                        toolbar.getContext(), R.drawable.ic_ms_more_vert));
        if (back) {
            toolbar.getToolbar().setNavigationIcon(R.drawable.ic_ms_arrow_back);
        }
    }

    /** Opens a secondary page, as a One UI pop-over on large Samsung screens. */
    public static void startSecondaryActivity(Activity activity,
            Class<? extends Activity> activityClass) {
        Intent intent = new Intent(activity, activityClass);
        boolean largeOneUiDevice = "samsung".equalsIgnoreCase(Build.MANUFACTURER)
                && activity.getResources().getConfiguration().smallestScreenWidthDp
                        >= POP_OVER_MIN_SMALLEST_WIDTH_DP;
        if (largeOneUiDevice) {
            ActivityKt.startPopOverActivity(
                    activity,
                    intent,
                    PopOverOptions.Companion.centerRightAnchored(activity));
        } else {
            activity.startActivity(intent);
        }
    }

    public static String versionName(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "";
        }
    }

    public static ConfigPage installConfigPage(AppCompatActivity activity, String title) {
        ViewGroup parent = activity.findViewById(android.R.id.content);
        View root = LayoutInflater.from(activity)
                .inflate(R.layout.activity_widget_settings, parent, false);
        ToolbarLayout toolbar = root.findViewById(R.id.widget_settings_root);
        LinearLayout content = root.findViewById(R.id.widget_settings_content);
        FrameLayout preview = root.findViewById(R.id.widget_preview_container);
        TextView cancel = root.findViewById(R.id.config_cancel);
        TextView save = root.findViewById(R.id.config_save);
        toolbar.setTitle(title);
        setToolbarBack(toolbar, true);
        activity.setContentView(root);
        configureSystemBars(activity, root, isDark(activity));
        return new ConfigPage(toolbar, content, preview, cancel, save);
    }

    /** Resolves the app theme preference, following the system night mode when unset. */
    public static boolean isDark(Context context) {
        String appTheme = AppPreferences.getAppTheme(context);
        if (WidgetOptions.THEME_DARK.equals(appTheme)) {
            return true;
        }
        if (WidgetOptions.THEME_LIGHT.equals(appTheme)) {
            return false;
        }
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    /** The phone app always renders One UI styling. */
    public static boolean isOneUi(Context context) {
        return true;
    }

    public static int pageHorizontalPadding(Context context) {
        return dp(context, 24.0f);
    }

    public static int pageTopPadding(Context context) {
        return dp(context, 8.0f);
    }

    // ---------------------------------------------------------------------------------------
    // Palette
    // ---------------------------------------------------------------------------------------

    public static int background(Context context, boolean dark) {
        return context.getColor(dev.oneuiproject.oneui.design.R.color.oui_des_round_and_bgcolor);
    }

    /** Legacy (pre-One UI) page background. */
    public static int background(boolean dark) {
        return dark ? Color.rgb(18, 18, 22) : Color.rgb(249, 247, 251);
    }

    public static int cardColor(Context context, boolean dark) {
        return context.getColor(dev.oneuiproject.oneui.design.R.color.oui_des_background_color);
    }

    /** Legacy (pre-One UI) card surface. */
    public static int card(boolean dark) {
        return dark ? Color.rgb(31, 30, 36) : Color.rgb(242, 239, 246);
    }

    public static int controlSurface(Context context, boolean dark) {
        return dark ? Color.rgb(42, 44, 50) : Color.rgb(238, 238, 241);
    }

    public static int mainText(Context context, boolean dark) {
        TypedArray colors = context.obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        try {
            return colors.getColorStateList(0).getDefaultColor();
        } finally {
            colors.recycle();
        }
    }

    public static int secondaryText(Context context, boolean dark) {
        TypedArray colors = context.obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
        try {
            return colors.getColorStateList(0).getDefaultColor();
        } finally {
            colors.recycle();
        }
    }

    public static int divider(Context context, boolean dark) {
        return context.getColor(dev.oneuiproject.oneui.design.R.color.oui_des_list_divider_color);
    }

    /** Official One UI Primary (#0381FE) / dark-mode accent (#5CA9FF). */
    public static int oneUiAccent(boolean dark) {
        return dark ? Color.rgb(92, 169, 255) : Color.rgb(3, 129, 254);
    }

    public static int accent(Context context, boolean dark) {
        int oneUi = oneUiAccent(dark);
        if (AppPreferences.isMaterialYouEnabled(context)) {
            // Material You system accents; fall back to One UI blues when unavailable.
            return systemColor(context, dark ? "system_accent1_200" : "system_accent1_600", oneUi);
        }
        return oneUi;
    }

    public static int desaturatedAccent(Context context, boolean dark) {
        float[] hsv = new float[3];
        Color.colorToHSV(accent(context, dark), hsv);
        hsv[1] *= ACCENT_SATURATION_SCALE;
        return Color.HSVToColor(hsv);
    }

    /**
     * One UI 8 / SESL functional orange. Samsung uses this informative color for
     * attention and notification states, with the same fill in light and dark themes.
     */
    public static int warning(boolean dark) {
        return Color.rgb(230, 91, 23);
    }

    /** The warning orange washed over the card color, for tracks behind warning fills. */
    public static int warningTrack(Context context, boolean dark) {
        int warning = warning(dark);
        int card = cardColor(context, dark);
        return blend(card, warning, dark ? 0.24f : 0.18f);
    }

    /** Legacy (pre-One UI) green accent. */
    public static int accent(boolean dark) {
        return dark ? Color.rgb(117, 220, 179) : Color.rgb(0, 113, 83);
    }

    /** Readable text color on top of the current accent. */
    public static int onAccent(Context context, boolean dark) {
        return Color.luminance(accent(context, dark)) > ON_ACCENT_LUMINANCE_THRESHOLD
                ? Color.BLACK
                : Color.WHITE;
    }

    public static int danger(boolean dark) {
        return dark ? Color.rgb(255, 177, 173) : Color.rgb(190, 35, 43);
    }

    public static int dp(Context context, float dp) {
        return Math.round(context.getResources().getDisplayMetrics().density * dp);
    }

    // ---------------------------------------------------------------------------------------
    // Typography
    // ---------------------------------------------------------------------------------------

    public static Typeface regularTypeface(Context context) {
        return TypefaceUtilsKt.getRegularFont();
    }

    public static Typeface mediumTypeface(Context context) {
        return TypefaceUtilsKt.getSemiBoldFont();
    }

    public static TextView text(Context context, String text, float sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(regularTypeface(context));
        view.setLineSpacing(0.0f, 1.12f);
        view.setIncludeFontPadding(false);
        return view;
    }

    public static TextView title(Context context, String title, boolean dark) {
        TextView view = text(context, title, 42.0f, mainText(context, dark));
        view.setTypeface(mediumTypeface(context));
        view.setLetterSpacing(-0.025f);
        return view;
    }

    public static TextView sectionTitle(Context context, String title, boolean dark) {
        TextView view = text(context, title, 13.0f, accent(context, dark));
        view.setTypeface(mediumTypeface(context));
        view.setLetterSpacing(0.01f);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.setMargins(dp(context, 4.0f), dp(context, 30.0f), 0, dp(context, 10.0f));
        view.setLayoutParams(params);
        return view;
    }

    // ---------------------------------------------------------------------------------------
    // Cards and containers
    // ---------------------------------------------------------------------------------------

    static final float CARD_HORIZONTAL_PADDING_DP = 22f;

    public static LinearLayout card(Context context, boolean dark) {
        RoundedLinearLayout card = new RoundedLinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, CARD_HORIZONTAL_PADDING_DP), dp(context, 20.0f),
                dp(context, CARD_HORIZONTAL_PADDING_DP),
                dp(context, 20.0f));
        card.setBackgroundColor(cardColor(context, dark));
        card.setElevation(0.0f);
        card.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        return card;
    }

    public static RoundedLinearLayout seslCard(Context context, boolean dark) {
        RoundedLinearLayout card = new RoundedLinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 18.0f), dp(context, 14.0f), dp(context, 18.0f),
                dp(context, 14.0f));
        card.setBackgroundColor(cardColor(context, dark));
        card.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        return card;
    }

    /** Unpadded rounded card whose children (usually {@link CardItemView} rows) fill it. */
    public static RoundedLinearLayout cardGroup(Context context, boolean dark) {
        RoundedLinearLayout group = new RoundedLinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackgroundColor(cardColor(context, dark));
        group.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        return group;
    }

    /** Same container as {@link #cardGroup}; named for pages built from SESL row items. */
    public static RoundedLinearLayout seslRowCard(Context context, boolean dark) {
        return cardGroup(context, dark);
    }

    public static Separator separator(Context context, String title) {
        Separator separator = new Separator(context);
        separator.setText(title);
        return separator;
    }

    public static LinearLayout horizontal(Context context, int gravity) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(gravity);
        return row;
    }

    public static void addSpacer(LinearLayout parent, int heightDp) {
        parent.addView(new View(parent.getContext()),
                new LinearLayout.LayoutParams(1, dp(parent.getContext(), heightDp)));
    }

    public static CardItemView actionRow(Context context, String title, String summary, int icon,
            View.OnClickListener listener) {
        CardItemView row = new CardItemView(context);
        row.setTitle(title);
        row.setSummary(summary);
        if (icon != 0) {
            row.setIcon(context.getDrawable(icon));
        }
        row.setShowTopDivider(false);
        row.setShowBottomDivider(false);
        if (listener != null) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(listener);
        }
        return row;
    }

    public static GradientDrawable pillBackground(Context context, boolean dark) {
        return shape(Color.argb(dark ? 36 : 16, 0, 0, 0), dp(context, 8.0f));
    }

    public static void makeAvatar(ImageView imageView) {
        GradientDrawable outline = new GradientDrawable();
        outline.setShape(GradientDrawable.OVAL);
        outline.setColor(Color.TRANSPARENT);
        imageView.setBackground(outline);
        imageView.setClipToOutline(true);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
    }

    // ---------------------------------------------------------------------------------------
    // Buttons
    // ---------------------------------------------------------------------------------------

    /** One UI contained button with an optional leading icon. */
    public static Button button(Context context, String label, boolean primary, boolean dark) {
        return button(context, label, 0, primary, dark);
    }

    public static Button button(Context context, String label, int icon, boolean primary,
            boolean dark) {
        Context themed = new ContextThemeWrapper(context, primary
                ? dev.oneuiproject.oneui.design.R.style.OneUI_ContainedPrimaryColorButtonTheme
                : dev.oneuiproject.oneui.design.R.style.OneUI_ContainedButtonTheme);
        Button button = new AppCompatButton(themed);
        button.setText(label);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMaxLines(2);
        button.setMinHeight(dp(context, 52.0f));
        if (primary) {
            applyDynamicPrimaryText(button, context);
        }
        if (icon != 0) {
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
            button.setCompoundDrawablePadding(dp(context, 8.0f));
            button.setCompoundDrawableTintList(button.getTextColors());
        }
        return button;
    }

    /** Uses the library's primary button background and enabled/disabled states. */
    public static Button nativePrimaryButton(Context context, String text) {
        Button button = (Button) LayoutInflater.from(context)
                .inflate(R.layout.view_oneui_primary_button, null, false);
        button.setText(text);
        applyDynamicPrimaryText(button, context);
        return button;
    }

    private static void applyDynamicPrimaryText(Button button, Context context) {
        if (AppPreferences.isMaterialYouEnabled(context)) {
            // The library's white label cannot contrast with pale dynamic accents in dark mode.
            button.setTextColor(onAccent(context, isDark(context)));
        }
    }

    /** Borderless-looking pill used for toolbar-style actions. */
    public static Button topAction(Context context, String label, boolean dark) {
        Button button = new AppCompatButton(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(18.0f);
        button.setTextColor(mainText(context, dark));
        button.setTypeface(mediumTypeface(context));
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setIncludeFontPadding(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(context, 18.0f), 0, dp(context, 18.0f), 0);
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(withAlpha(mainText(context, dark), dark ? 42 : 28)),
                shape(controlSurface(context, dark), dp(context, PILL_CORNER_RADIUS_DP)),
                null));
        button.setElevation(0.0f);
        button.setStateListAnimator(null);
        return button;
    }

    public static Button backAction(Context context, boolean dark) {
        Button button = topAction(context, "", dark);
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_ms_arrow_back, 0, 0, 0);
        button.setCompoundDrawableTintList(ColorStateList.valueOf(mainText(context, dark)));
        button.setPadding(dp(context, 12.0f), 0, dp(context, 12.0f), 0);
        return button;
    }

    // ---------------------------------------------------------------------------------------
    // Progress and form controls
    // ---------------------------------------------------------------------------------------

    public static SeslProgressBar progress(Context context, boolean dark) {
        SeslProgressBar progressBar = new SeslProgressBar(context, null,
                android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(ColorStateList.valueOf(accent(context, dark)));
        progressBar.setProgressBackgroundTintList(
                ColorStateList.valueOf(controlSurface(context, dark)));
        progressBar.setIndeterminate(false);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, dp(context, 7.0f)));
        return progressBar;
    }

    /** Transparent drop-down spinner: accent end-aligned value, card-colored popup rows. */
    public static Spinner spinner(Context context, String[] items, boolean dark) {
        Spinner spinner = new AppCompatSpinner(context, Spinner.MODE_DROPDOWN);
        spinner.setAdapter(new ArrayAdapter<String>(context,
                android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView item = (TextView) super.getView(position, convertView, parent);
                return styleSpinnerItem(context, item, dark, false);
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView item = (TextView) super.getDropDownView(position, convertView, parent);
                return styleSpinnerItem(context, item, dark, true);
            }
        });
        spinner.setBackground(new ColorDrawable(Color.TRANSPARENT));
        spinner.setPopupBackgroundDrawable(
                shape(cardColor(context, dark), dp(context, POPUP_CORNER_RADIUS_DP)));
        spinner.setPadding(0, 0, 0, 0);
        spinner.setElevation(0.0f);
        return spinner;
    }

    private static TextView styleSpinnerItem(Context context, TextView item, boolean dark,
            boolean dropDown) {
        item.setTextColor(dropDown ? mainText(context, dark) : accent(context, dark));
        item.setTextSize(14.0f);
        item.setTypeface(mediumTypeface(context));
        item.setIncludeFontPadding(false);
        item.setGravity((dropDown ? Gravity.START : Gravity.END) | Gravity.CENTER_VERTICAL);
        int horizontalPadding = dp(context, dropDown ? 16.0f : 10.0f);
        int verticalPadding = dp(context, 12.0f);
        item.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        item.setBackgroundColor(dropDown ? cardColor(context, dark) : Color.TRANSPARENT);
        return item;
    }

    /** Adds a "label … spinner" row followed by a hairline divider. */
    public static void addLabeledSpinner(LinearLayout parent, String label, Spinner spinner,
            boolean dark) {
        Context context = parent.getContext();
        LinearLayout row = horizontal(context, Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(context, 58.0f));
        row.addView(text(context, label, 15.0f, mainText(context, dark)),
                new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f));
        row.addView(spinner,
                new LinearLayout.LayoutParams(dp(context, 168.0f), dp(context, 54.0f)));
        parent.addView(row, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        View rule = new View(context);
        rule.setBackgroundColor(divider(context, dark));
        parent.addView(rule, new LinearLayout.LayoutParams(MATCH_PARENT, 1));
    }

    public static CheckBox checkbox(Context context, String label, boolean checked,
            boolean dark) {
        CheckBox checkBox = new AppCompatCheckBox(context);
        checkBox.setText(label);
        checkBox.setChecked(checked);
        checkBox.setTextSize(15.0f);
        checkBox.setTextColor(mainText(context, dark));
        checkBox.setTypeface(regularTypeface(context));
        checkBox.setButtonTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[0]},
                new int[]{accent(context, dark), secondaryText(context, dark)}));
        checkBox.setMinHeight(dp(context, 52.0f));
        checkBox.setPadding(0, dp(context, 4.0f), 0, dp(context, 4.0f));
        return checkBox;
    }

    // ---------------------------------------------------------------------------------------
    // Window chrome
    // ---------------------------------------------------------------------------------------

    /**
     * Paints the system bars with the page background and draws edge to edge
     * while padding {@code root} by the system-bar insets.
     */
    public static void configureSystemBars(Activity activity, View root, boolean dark) {
        Window window = activity.getWindow();
        int barColor = background(activity, dark);
        // Keep the bars tied to the selected app theme. Transparent bars can briefly expose the
        // previous activity window colour during a light/dark recreation on recent One UI builds.
        window.setStatusBarColor(barColor);
        window.setNavigationBarColor(barColor);
        window.setBackgroundDrawable(new ColorDrawable(barColor));
        window.getDecorView().setBackgroundColor(barColor);
        window.setNavigationBarContrastEnforced(false);
        window.setDecorFitsSystemWindows(false);
        WindowInsetsController insetsController = window.getInsetsController();
        if (insetsController != null) {
            int lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            insetsController.setSystemBarsAppearance(dark ? 0 : lightBars, lightBars);
        }
        int paddingLeft = root.getPaddingLeft();
        int paddingTop = root.getPaddingTop();
        int paddingRight = root.getPaddingRight();
        int paddingBottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsets.Type.systemBars());
            view.setPadding(paddingLeft + insets.left, paddingTop + insets.top,
                    paddingRight + insets.right, insets.bottom + paddingBottom);
            return windowInsets;
        });
    }

    // ---------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------

    private static GradientDrawable shape(int color, int cornerRadius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(cornerRadius);
        return drawable;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** Linear per-channel blend from {@code from} toward {@code to}; the result is opaque. */
    private static int blend(int from, int to, float amount) {
        return Color.rgb(
                blendChannel(Color.red(from), Color.red(to), amount),
                blendChannel(Color.green(from), Color.green(to), amount),
                blendChannel(Color.blue(from), Color.blue(to), amount));
    }

    private static int blendChannel(int from, int to, float amount) {
        return Math.round(from + (to - from) * amount);
    }

    /** Android 12+ system palette color by resource name, or {@code fallback}. */
    private static int systemColor(Context context, String name, int fallback) {
        int identifier = context.getResources().getIdentifier(name, "color", "android");
        if (identifier == 0) {
            return fallback;
        }
        try {
            return context.getColor(identifier);
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
