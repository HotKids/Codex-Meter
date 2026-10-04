package me.pipi.codexmeter;

import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsageWindow;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.SizeF;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import java.util.List;
import java.util.Locale;

/**
 * Builds and refreshes the Samsung lock-screen/AOD widgets. Every manifest-registered provider
 * is listed in {@link #PROVIDERS} with the shape, style and metric it renders.
 */
final class SamsungLockWidgetSupport {
    private static final String TAG = "CodexMeterLock";

    private static final ProviderSpec[] PROVIDERS = {
            new ProviderSpec(SamsungLockSquareWidget.class, Shape.SQUARE, Style.NUMBERS, Metric.BOTH),
            new ProviderSpec(SamsungLockWideWidget.class, Shape.WIDE, Style.NUMBERS, Metric.BOTH),
            new ProviderSpec(SamsungLockRingsSquareWidget.class, Shape.SQUARE, Style.RINGS, Metric.BOTH),
            new ProviderSpec(SamsungLockRingsWideWidget.class, Shape.WIDE, Style.RINGS, Metric.BOTH),
            new ProviderSpec(SamsungLockDialsSquareWidget.class, Shape.SQUARE, Style.DIALS, Metric.BOTH),
            new ProviderSpec(SamsungLockDialsWideWidget.class, Shape.WIDE, Style.DIALS, Metric.BOTH),
            new ProviderSpec(SamsungLockBarsSquareWidget.class, Shape.SQUARE, Style.BARS, Metric.BOTH),
            new ProviderSpec(SamsungLockBarsWideWidget.class, Shape.WIDE, Style.BARS, Metric.BOTH),
            new ProviderSpec(SamsungLockFiveHourWidget.class, Shape.SQUARE, Style.DIALS, Metric.FIVE_HOUR),
            new ProviderSpec(SamsungLockWeeklyWidget.class, Shape.SQUARE, Style.DIALS, Metric.WEEKLY)
    };

    // Default lock widget cell size in dp, used when the host does not report one.
    private static final int DEFAULT_HEIGHT_DP = 56;
    private static final int SQUARE_WIDTH_DP = 56;
    private static final int WIDE_WIDTH_DP = 124;
    private static final float WIDE_ASPECT_RATIO = (float) WIDE_WIDTH_DP / DEFAULT_HEIGHT_DP;

    private static final String ACTION_OPEN = AppConstants.action("LOCK_WIDGET_OPEN");
    private static final String ACTION_RESET = AppConstants.action("LOCK_WIDGET_RESET");
    private static final String TARGET_OPEN = "open";
    private static final String TARGET_RESET = "reset";
    private static final int TAP_REQUEST_CODE_BASE = 82000;
    private static final int RESET_TAP_REQUEST_CODE_OFFSET = 50000;

    private static final long MIN_COUNTDOWN_MILLIS = 1000L;
    private static final String NO_VALUE = "—";
    /** Joins meters and the reset count on one line of a tile. */
    private static final String INLINE_SEPARATOR = "  ·  ";
    private static final String LABEL_SEPARATOR = " · ";

    // Enum order is part of each widget's tap request code; append new constants only.
    enum Shape {
        WIDE,
        SQUARE
    }

    enum Style {
        NUMBERS,
        RINGS,
        DIALS,
        BARS
    }

    enum Metric {
        BOTH,
        FIVE_HOUR,
        WEEKLY
    }

    private static final class ProviderSpec {
        final Class<?> provider;
        final Shape shape;
        final Style style;
        final Metric metric;

        ProviderSpec(Class<?> provider, Shape shape, Style style, Metric metric) {
            this.provider = provider;
            this.shape = shape;
            this.style = style;
            this.metric = metric;
        }
    }

    private SamsungLockWidgetSupport() {
    }

    static void updateAll(Context context) {
        if (context == null) {
            return;
        }
        Context app = application(context);
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        for (ProviderSpec spec : PROVIDERS) {
            try {
                updateIds(app, manager, appWidgetIds(app, manager, spec), spec.shape, spec.style,
                        spec.metric);
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to enumerate lock widgets", e);
            }
        }
    }

    static void updateById(Context context, int appWidgetId) {
        if (context == null || appWidgetId == 0) {
            return;
        }
        Context app = application(context);
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        ProviderSpec spec = findSpec(manager, app, appWidgetId);
        if (spec != null) {
            update(app, manager, appWidgetId, spec.shape, spec.style, spec.metric);
        }
    }

    /** Number of lock widgets currently placed across all providers. */
    static int countAll(Context context) {
        if (context == null) {
            return 0;
        }
        Context app = application(context);
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int count = 0;
        for (ProviderSpec spec : PROVIDERS) {
            try {
                count += appWidgetIds(app, manager, spec).length;
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to enumerate lock widgets", e);
            }
        }
        return count;
    }

    static void enableAllProviders(Context context) {
        Context app = application(context);
        PackageManager packageManager = app.getPackageManager();
        for (ProviderSpec spec : PROVIDERS) {
            try {
                packageManager.setComponentEnabledSetting(
                        new ComponentName(app, spec.provider),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
            } catch (RuntimeException exception) {
                Log.w(TAG, "Unable to re-enable a lock widget provider", exception);
            }
        }
    }

    static void updateIds(Context context, AppWidgetManager manager, int[] appWidgetIds,
            Shape shape, Style style, Metric metric) {
        if (context == null || manager == null || appWidgetIds == null) {
            return;
        }
        for (int appWidgetId : appWidgetIds) {
            update(context, manager, appWidgetId, shape, style, metric);
        }
    }

    static void update(Context context, AppWidgetManager manager, int appWidgetId, Shape shape,
            Style style, Metric metric) {
        if (context == null || manager == null || appWidgetId == 0) {
            return;
        }
        try {
            manager.updateAppWidget(appWidgetId,
                    buildViews(context, manager, appWidgetId, shape, style, metric));
        } catch (RuntimeException e) {
            Log.w(TAG, "Samsung lock widget update failed", e);
        }
    }

    /** Views for a surface without a widget id (Samsung ServiceBox), using default options. */
    static RemoteViews buildViews(Context context, Shape shape, Style style) {
        return buildViews(context, null, 0, shape, style, Metric.BOTH);
    }

    static RemoteViews buildViews(Context context, AppWidgetManager manager, int appWidgetId,
            Shape shape, Style style, Metric metric) {
        boolean signedIn = SecureTokenStore.isSignedIn(context);
        UsageSnapshot snapshot = AppPreferences.loadSnapshot(context);
        LockWidgetOptions options = AppPreferences.loadLockWidgetOptions(context, appWidgetId);
        ResetCreditsSnapshot resetCredits = AppPreferences.loadResetCredits(context);
        int resetCount = resetCredits == null ? 0 : resetCredits.availableCount;
        if (metric != Metric.BOTH) {
            RemoteViews views = buildSingleMetricViews(context, manager, appWidgetId, metric,
                    snapshot, signedIn);
            applyTapAction(context, views, R.id.lock_graphic_root, appWidgetId, shape, style,
                    options, signedIn, resetCount);
            return views;
        }

        LockMeterBinding binding = LockMeterBinding.bind(context, snapshot, options);
        RemoteViews views;
        int rootId;
        if (style == Style.NUMBERS) {
            views = buildNumberViews(context, shape, signedIn, binding, options, resetCount);
            rootId = shape == Shape.SQUARE ? R.id.lock_square_root : R.id.lock_wide_root;
        } else if (style == Style.BARS) {
            views = buildBarViews(context, shape, signedIn, binding, options, resetCount);
            rootId = R.id.lock_graphic_root;
        } else {
            views = buildDialViews(context, manager, appWidgetId, shape, style, signedIn,
                    binding);
            rootId = R.id.lock_graphic_root;
        }
        applyCountdowns(views, shape, style, options, binding);
        views.setContentDescription(rootId,
                contentDescription(context, signedIn, binding, style, options, resetCount));
        applyTapAction(context, views, rootId, appWidgetId, shape, style, options, signedIn,
                resetCount);
        return views;
    }

    /** One-cell dial for a single allowance (the five-hour and weekly providers). */
    private static RemoteViews buildSingleMetricViews(Context context, AppWidgetManager manager,
            int appWidgetId, Metric metric, UsageSnapshot snapshot, boolean signedIn) {
        boolean fiveHour = metric == Metric.FIVE_HOUR;
        boolean monthly = metric == Metric.WEEKLY && snapshot != null
                && snapshot.longWindowIsMonthly();
        UsageWindow window = snapshot == null ? null
                : fiveHour ? snapshot.fiveHour : snapshot.longWindow();
        int value = LockMeterBinding.remaining(window);
        int[] size = grantedSize(manager, appWidgetId, Shape.SQUARE);
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.widget_lock_dial_single);
        views.setImageViewBitmap(R.id.lock_graphic_image,
                SamsungLockGraphics.renderSingle(context, metric, value, signedIn, size[0],
                        size[1]));
        int description = fiveHour ? R.string.dashboard_lock_single_five_hour
                : monthly ? R.string.dashboard_lock_single_monthly
                : R.string.dashboard_lock_single_weekly;
        views.setContentDescription(R.id.lock_graphic_root, signedIn
                ? context.getString(description, percentText(value))
                : context.getString(R.string.dashboard_lock_sign_in_required));
        return views;
    }

    private static RemoteViews buildNumberViews(Context context, Shape shape, boolean signedIn,
            LockMeterBinding binding, LockWidgetOptions options, int resetCount) {
        boolean square = shape == Shape.SQUARE;
        int valueId = square ? R.id.lock_square_value : R.id.lock_wide_value;
        RemoteViews views = new RemoteViews(context.getPackageName(),
                square ? R.layout.widget_lock_square : R.layout.widget_lock_wide);
        views.setTextViewText(valueId,
                numberText(context, signedIn, binding, shape, options, resetCount));
        setTextSizeSp(views, valueId,
                numberTextSize(shape, binding.singleMetric(), showsResetCount(options)));
        return views;
    }

    private static String numberText(Context context, boolean signedIn,
            LockMeterBinding binding, Shape shape, LockWidgetOptions options, int resetCount) {
        boolean square = shape == Shape.SQUARE;
        if (!signedIn) {
            return context.getString(square
                    ? R.string.dashboard_lock_sign_in_stacked : R.string.dashboard_lock_sign_in);
        }
        String primaryLabel = binding.primaryLabel();
        String secondaryLabel = binding.secondaryLabel();
        String text;
        if (binding.singleMetric()) {
            text = primaryLabel + (square ? '\n' : ' ') + percentText(binding.primaryRemaining);
        } else if (square) {
            text = meter(primaryLabel, compactText(binding.primaryRemaining)) + '\n'
                    + meter(secondaryLabel, compactText(binding.secondaryRemaining));
        } else {
            text = meter(primaryLabel, percentText(binding.primaryRemaining)) + INLINE_SEPARATOR
                    + meter(secondaryLabel, percentText(binding.secondaryRemaining));
        }
        if (showsResetCount(options)) {
            String count = resetCountText(context, resetCount);
            text += square ? '\n' + count : INLINE_SEPARATOR + count;
        }
        return text;
    }

    /** A face label and its value, e.g. "5H 64%". */
    private static String meter(String label, String value) {
        return label + ' ' + value;
    }

    /** The compact reset-credit count drawn on a tile, e.g. "R2". */
    private static String resetCountText(Context context, int resetCount) {
        return context.getString(R.string.dashboard_lock_face_reset_count,
                Math.max(0, resetCount));
    }

    private static float numberTextSize(Shape shape, boolean singleMetric,
            boolean showResetCount) {
        if (shape == Shape.WIDE) {
            if (singleMetric) {
                return showResetCount ? 14.0f : 16.0f;
            }
            return showResetCount ? 12.0f : 14.0f;
        }
        if (singleMetric) {
            return showResetCount ? 10.5f : 13.0f;
        }
        return showResetCount ? 9.2f : 11.0f;
    }

    private static RemoteViews buildBarViews(Context context, Shape shape, boolean signedIn,
            LockMeterBinding binding, LockWidgetOptions options, int resetCount) {
        boolean square = shape == Shape.SQUARE;
        RemoteViews views = new RemoteViews(context.getPackageName(),
                square ? R.layout.widget_lock_bars_square : R.layout.widget_lock_bars_wide);
        if (!signedIn) {
            views.setViewVisibility(R.id.lock_bar_primary_group, View.VISIBLE);
            views.setViewVisibility(R.id.lock_bar_secondary_group, View.GONE);
            views.setTextViewText(R.id.lock_bar_primary_label, "");
            views.setTextViewText(R.id.lock_bar_primary_value,
                    context.getString(R.string.dashboard_lock_sign_in));
            setTextSizeSp(views, R.id.lock_bar_primary_value, square ? 10.0f : 12.0f);
            views.setViewVisibility(R.id.lock_bar_primary_progress, View.GONE);
            return views;
        }
        boolean showPrimary = binding.showPrimary;
        boolean showSecondary = binding.showSecondary;
        views.setViewVisibility(R.id.lock_bar_primary_group, visibility(showPrimary));
        views.setViewVisibility(R.id.lock_bar_secondary_group, visibility(showSecondary));
        views.setViewVisibility(R.id.lock_bar_primary_progress, visibility(showPrimary));
        views.setViewVisibility(R.id.lock_bar_secondary_progress, visibility(showSecondary));
        // The reset count rides on the first visible label.
        boolean showResetCount = showsResetCount(options);
        views.setTextViewText(R.id.lock_bar_primary_label, labelWithResetCount(context,
                binding.primaryLabel(), showResetCount && showPrimary, resetCount));
        views.setTextViewText(R.id.lock_bar_secondary_label, labelWithResetCount(context,
                binding.secondaryLabel(), showResetCount && !showPrimary && showSecondary,
                resetCount));
        views.setTextViewText(R.id.lock_bar_primary_value, compactText(binding.primaryRemaining));
        views.setTextViewText(R.id.lock_bar_secondary_value,
                compactText(binding.secondaryRemaining));
        views.setProgressBar(R.id.lock_bar_primary_progress, 100,
                clampPercent(binding.primaryRemaining), false);
        views.setProgressBar(R.id.lock_bar_secondary_progress, 100,
                clampPercent(binding.secondaryRemaining), false);
        float valueSize;
        float labelSize;
        if (binding.singleMetric()) {
            valueSize = square ? 15.0f : 17.0f;
            labelSize = square ? 9.0f : 10.0f;
        } else {
            valueSize = square ? 10.0f : 11.0f;
            labelSize = square ? 7.5f : 8.0f;
        }
        setTextSizeSp(views, R.id.lock_bar_primary_value, valueSize);
        setTextSizeSp(views, R.id.lock_bar_secondary_value, valueSize);
        setTextSizeSp(views, R.id.lock_bar_primary_label, labelSize);
        setTextSizeSp(views, R.id.lock_bar_secondary_label, labelSize);
        return views;
    }

    /**
     * Rings and dials draw both meters into one bitmap; the layout's text views are only used
     * for the signed-out prompt.
     */
    private static RemoteViews buildDialViews(Context context, AppWidgetManager manager,
            int appWidgetId, Shape shape, Style style, boolean signedIn,
            LockMeterBinding binding) {
        RemoteViews views = new RemoteViews(context.getPackageName(), dialLayout(shape, style));
        int[] size = grantedSize(manager, appWidgetId, shape);
        views.setImageViewBitmap(R.id.lock_graphic_image,
                SamsungLockGraphics.render(context, shape, binding.primaryRemaining,
                        binding.secondaryRemaining, signedIn, size[0], size[1],
                        binding.primaryIconRes(), binding.secondaryIconRes()));
        if (shape == Shape.WIDE) {
            if (signedIn) {
                views.setViewVisibility(R.id.lock_graphic_primary_group, View.GONE);
                views.setViewVisibility(R.id.lock_graphic_secondary_group, View.GONE);
            } else {
                views.setViewVisibility(R.id.lock_graphic_primary_group, View.VISIBLE);
                views.setViewVisibility(R.id.lock_graphic_secondary_group, View.GONE);
                views.setViewVisibility(R.id.lock_graphic_primary_progress, View.GONE);
                views.setViewVisibility(R.id.lock_graphic_primary_icon, View.GONE);
                views.setTextViewText(R.id.lock_graphic_primary_value,
                        context.getString(R.string.dashboard_lock_sign_in));
                setTextSizeSp(views, R.id.lock_graphic_primary_value, 11.0f);
            }
        } else if (signedIn) {
            views.setViewVisibility(R.id.lock_graphic_center_value, View.GONE);
        } else {
            views.setTextViewText(R.id.lock_graphic_center_value,
                    context.getString(R.string.dashboard_lock_sign_in));
            setTextSizeSp(views, R.id.lock_graphic_center_value, 10.0f);
        }
        return views;
    }

    private static int dialLayout(Shape shape, Style style) {
        boolean square = shape == Shape.SQUARE;
        if (style == Style.RINGS) {
            return square ? R.layout.widget_lock_rings_square : R.layout.widget_lock_rings_wide;
        }
        return square ? R.layout.widget_lock_dials_square : R.layout.widget_lock_dials_wide;
    }

    private static void applyCountdowns(RemoteViews views, Shape shape, Style style,
            LockWidgetOptions options, LockMeterBinding binding) {
        UsageWindow primaryWindow = binding.primaryWindow();
        UsageWindow secondaryWindow = binding.secondaryWindow();
        boolean perMeterCountdowns = style == Style.BARS
                || (shape == Shape.WIDE && (style == Style.RINGS || style == Style.DIALS));
        if (perMeterCountdowns) {
            applyCountdown(views, R.id.lock_primary_countdown,
                    options.showCountdown && binding.showPrimary, primaryWindow);
            applyCountdown(views, R.id.lock_secondary_countdown,
                    options.showCountdown && binding.showSecondary, secondaryWindow);
        } else {
            UsageWindow window = binding.singleMetric()
                    ? primaryWindow
                    : earlierReset(primaryWindow, secondaryWindow);
            applyCountdown(views, R.id.lock_countdown, options.showCountdown, window);
        }
    }

    /** The window whose upcoming reset comes first, or {@code null} if neither has one. */
    private static UsageWindow earlierReset(UsageWindow first, UsageWindow second) {
        long now = System.currentTimeMillis();
        long firstReset = first != null && first.showsResetCountdown()
                ? first.resetAtMillis() : 0L;
        long secondReset = second != null && second.showsResetCountdown()
                ? second.resetAtMillis() : 0L;
        boolean firstPending = firstReset > now;
        boolean secondPending = secondReset > now;
        if (firstPending && (!secondPending || firstReset <= secondReset)) {
            return first;
        }
        return secondPending ? second : null;
    }

    /** Shows a live countdown chronometer to the window's reset, or hides the view. */
    private static void applyCountdown(RemoteViews views, int viewId, boolean enabled,
            UsageWindow window) {
        long now = System.currentTimeMillis();
        long resetAt = window == null ? 0L : window.resetAtMillis();
        if (!enabled || window == null || !window.showsResetCountdown() || resetAt <= now) {
            views.setViewVisibility(viewId, View.GONE);
            return;
        }
        long base = SystemClock.elapsedRealtime() + Math.max(MIN_COUNTDOWN_MILLIS, resetAt - now);
        views.setViewVisibility(viewId, View.VISIBLE);
        views.setChronometer(viewId, base, null, true);
        views.setChronometerCountDown(viewId, true);
    }

    /**
     * Opens the app, or the reset confirmation when the widget offers it and a credit is
     * available. A reset is never consumed directly from the lock screen.
     */
    private static void applyTapAction(Context context, RemoteViews views, int rootId,
            int appWidgetId, Shape shape, Style style, LockWidgetOptions options,
            boolean signedIn, int resetCount) {
        boolean openReset = options.showResetAction && signedIn && resetCount > 0;
        Class<?> activity = openReset ? ResetCreditActivity.class : MainActivity.class;
        String target = openReset ? TARGET_RESET : TARGET_OPEN;
        // The data URI keeps each widget's PendingIntent distinct.
        Intent intent = new Intent(context, activity)
                .setAction(openReset ? ACTION_RESET : ACTION_OPEN)
                .setData(Uri.parse("codexmeter://widget/lock/v" + AppConstants.VERSION_CODE + "/"
                        + appWidgetId + "/"
                        + shape.name().toLowerCase(Locale.US) + "/"
                        + style.name().toLowerCase(Locale.US) + "/"
                        + target))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int requestCode = TAP_REQUEST_CODE_BASE + appWidgetId + (shape.ordinal() * 1000)
                + (style.ordinal() * 100) + (openReset ? RESET_TAP_REQUEST_CODE_OFFSET : 0);
        views.setOnClickPendingIntent(rootId, PendingIntent.getActivity(context, requestCode,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    private static String contentDescription(Context context, boolean signedIn,
            LockMeterBinding binding, Style style, LockWidgetOptions options, int resetCount) {
        if (!signedIn) {
            return context.getString(R.string.dashboard_lock_sign_in_required);
        }
        String separator = context.getString(R.string.dashboard_list_separator);
        StringBuilder description = new StringBuilder(context.getString(
                R.string.dashboard_lock_description_style,
                context.getString(styleLabel(style)).toLowerCase(Locale.getDefault())));
        if (binding.showPrimary) {
            description.append(separator).append(context.getString(
                    R.string.dashboard_lock_description_meter, binding.primary.label,
                    percentText(binding.primaryRemaining)));
        }
        if (binding.showSecondary) {
            description.append(separator).append(context.getString(
                    R.string.dashboard_lock_description_meter, binding.secondary.label,
                    percentText(binding.secondaryRemaining)));
        }
        if (options.showCountdown) {
            description.append(separator)
                    .append(context.getString(R.string.dashboard_lock_description_countdown));
        }
        if (showsResetCount(options)) {
            description.append(separator).append(context.getResources().getQuantityString(
                    R.plurals.dashboard_lock_description_credits, resetCount, resetCount));
        }
        if (options.showResetAction && resetCount > 0) {
            return context.getString(R.string.dashboard_lock_description_tap, description);
        }
        return description.toString();
    }

    private static int styleLabel(Style style) {
        if (style == Style.RINGS) {
            return R.string.dashboard_lock_style_rings;
        }
        if (style == Style.DIALS) {
            return R.string.dashboard_lock_style_gauges;
        }
        if (style == Style.BARS) {
            return R.string.dashboard_lock_style_bars;
        }
        return R.string.dashboard_lock_style_numbers;
    }

    /** The reset action also implies showing how many credits are left. */
    private static boolean showsResetCount(LockWidgetOptions options) {
        return options.showResetCredits || options.showResetAction;
    }

    private static String labelWithResetCount(Context context, String label,
            boolean showResetCount, int resetCount) {
        return showResetCount
                ? label + LABEL_SEPARATOR + resetCountText(context, resetCount) : label;
    }

    private static String percentText(int remaining) {
        return remaining < 0 ? NO_VALUE : remaining + "%";
    }

    private static String compactText(int remaining) {
        return remaining < 0 ? NO_VALUE : Integer.toString(remaining);
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static int visibility(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }

    private static void setTextSizeSp(RemoteViews views, int viewId, float size) {
        views.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, size);
    }

    private static int[] appWidgetIds(Context context, AppWidgetManager manager,
            ProviderSpec spec) {
        return manager.getAppWidgetIds(new ComponentName(context, spec.provider));
    }

    private static ProviderSpec findSpec(AppWidgetManager manager, Context context,
            int appWidgetId) {
        for (ProviderSpec spec : PROVIDERS) {
            try {
                for (int id : appWidgetIds(context, manager, spec)) {
                    if (id == appWidgetId) {
                        return spec;
                    }
                }
            } catch (RuntimeException ignored) {
                // A provider the host cannot enumerate cannot own this widget.
            }
        }
        return null;
    }

    /** The widget's size in dp as granted by the host, or the default cell size. */
    private static int[] grantedSize(AppWidgetManager manager, int appWidgetId, Shape shape) {
        int defaultWidth = shape == Shape.SQUARE ? SQUARE_WIDTH_DP : WIDE_WIDTH_DP;
        if (manager == null || appWidgetId == 0) {
            return new int[] {defaultWidth, DEFAULT_HEIGHT_DP};
        }
        try {
            Bundle options = manager.getAppWidgetOptions(appWidgetId);
            List<SizeF> sizes = options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
            SizeF best = sizes == null || sizes.isEmpty() ? null : bestSize(sizes, shape);
            if (best != null) {
                return new int[] {Math.round(best.getWidth()), Math.round(best.getHeight())};
            }
            int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                    defaultWidth);
            int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                    DEFAULT_HEIGHT_DP);
            return new int[] {
                    minWidth <= 0 ? defaultWidth : minWidth,
                    minHeight <= 0 ? DEFAULT_HEIGHT_DP : minHeight
            };
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to read lock widget size", e);
            return new int[] {defaultWidth, DEFAULT_HEIGHT_DP};
        }
    }

    /**
     * Picks the reported size closest to the shape's aspect ratio, breaking near-ties by
     * closeness to the default cell height.
     */
    private static SizeF bestSize(List<SizeF> sizes, Shape shape) {
        float targetAspect = shape == Shape.SQUARE ? 1.0f : WIDE_ASPECT_RATIO;
        SizeF best = null;
        float bestScore = Float.MAX_VALUE;
        for (SizeF size : sizes) {
            if (size == null || size.getWidth() <= 0.0f || size.getHeight() <= 0.0f) {
                continue;
            }
            float score = (Math.abs((size.getWidth() / size.getHeight()) - targetAspect) * 100.0f)
                    + (Math.abs(size.getHeight() - DEFAULT_HEIGHT_DP) * 0.2f);
            if (best == null || score < bestScore) {
                best = size;
                bestScore = score;
            }
        }
        return best;
    }

    private static Context application(Context context) {
        Context applicationContext = context.getApplicationContext();
        return applicationContext == null ? context : applicationContext;
    }
}
