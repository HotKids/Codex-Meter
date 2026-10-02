package dev.bennett.codexmeter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Compact local-history chart showing actual, sustainable, and projected quota burn. */
public final class UsageBurnChartView extends View {
    /** Reports finger-scrub positions so hosts can surface point-in-time detail. */
    public interface OnScrubListener {
        void onScrub(long timeMillis, double usedPercent, boolean historicalWindow);

        void onScrubEnd();
    }

    /** {@link #setSelectedWindow} value that points scrubbing back at the current window. */
    static final int CURRENT_WINDOW = -1;

    /** Recent windows loaded from history, oldest first; the last one is the current window. */
    private static final int RECENT_WINDOW_COUNT = 5;
    private static final long HAPTIC_TICKS_PER_AXIS = 24L;
    private static final long ONE_DAY_SECONDS = TimeUnit.DAYS.toSeconds(1);
    // Plot geometry, in dp.
    private static final float SIDE_INSET_DP = 16f;
    private static final float PLOT_TOP_DP = 34f;
    private static final float PLOT_BOTTOM_INSET_DP = 24f;
    private static final float HEADER_BASELINE_DP = 20f;
    private static final float AXIS_LABEL_BASELINE_INSET_DP = 7f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF plot = new RectF();
    private final RectF bubbleRect = new RectF();
    private final DashPathEffect budgetDash;
    private final DashPathEffect projectionDash;
    private final Typeface regularTypeface = Typeface.create("sec", Typeface.NORMAL);
    private final Typeface boldTypeface = Typeface.create("sec", Typeface.BOLD);
    private String label = "";
    private UsageWindow window;
    private List<UsageSample> samples = Collections.emptyList();
    private List<List<UsageSample>> windows = Collections.emptyList();
    private UsagePace.Assessment pace;
    private long observedAtMillis;
    private boolean scrubEnabled;
    private OnScrubListener scrubListener;
    private int selectedWindowIndex = CURRENT_WINDOW;
    private boolean scrubbing;
    private long scrubTimeMillis;
    private double scrubPercent = -1d;
    private long lastHapticBucket = Long.MIN_VALUE;

    public UsageBurnChartView(Context context) {
        this(context, null);
    }

    public UsageBurnChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = density();
        budgetDash = new DashPathEffect(new float[]{5f * density, 5f * density}, 0);
        projectionDash = new DashPathEffect(new float[]{7f * density, 5f * density}, 0);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setData(String label, UsageWindow window, UsageHistory history,
            long observedAtMillis, UsagePace.Assessment pace) {
        this.label = label == null ? "" : label;
        this.window = window;
        this.samples = history == null
                ? Collections.emptyList() : history.currentWindowSamples();
        this.windows = history == null
                ? Collections.emptyList() : history.recentWindows(RECENT_WINDOW_COUNT);
        this.observedAtMillis = observedAtMillis;
        this.pace = pace;
        this.selectedWindowIndex = CURRENT_WINDOW;
        String detail = samples.size() < 2 ? "Building local history"
                : samples.size() + " local samples";
        if (pace != null && pace.available) {
            detail += ", projected exhaustion "
                    + UsageFormat.relative(pace.estimatedExhaustionAtMillis,
                            System.currentTimeMillis());
        }
        if (scrubEnabled) {
            detail += ". Touch and drag to inspect points in time";
        }
        setContentDescription(this.label + " usage burn chart. " + detail + ".");
        invalidate();
    }

    /** Enables finger scrubbing across the burn line for point-in-time inspection. */
    public void setScrubEnabled(boolean enabled) {
        this.scrubEnabled = enabled;
    }

    public void setOnScrubListener(OnScrubListener listener) {
        this.scrubListener = listener;
    }

    /**
     * Highlights one recorded window and points scrubbing at it. Accepts an index into
     * {@code recentWindows(5)} ordering (oldest first); any other value, such as
     * {@link #CURRENT_WINDOW}, selects the current window.
     */
    public void setSelectedWindow(int index) {
        this.selectedWindowIndex = isHistoricalIndex(index) ? index : CURRENT_WINDOW;
        this.scrubbing = false;
        invalidate();
    }

    public int windowCount() {
        return windows.size();
    }

    /** Completed windows precede the last entry of {@link #windows}, the current window. */
    private boolean isHistoricalIndex(int index) {
        return index >= 0 && index < windows.size() - 1;
    }

    private boolean historicalSelection() {
        return isHistoricalIndex(selectedWindowIndex);
    }

    private List<UsageSample> activeSamples() {
        return historicalSelection() ? windows.get(selectedWindowIndex) : samples;
    }

    /** Start and end of the time axis for the actively scrubbed window, or null if unknown. */
    private long[] activeAxis() {
        if (historicalSelection()) {
            List<UsageSample> selected = windows.get(selectedWindowIndex);
            UsageSample reference = selected.get(selected.size() - 1);
            return new long[]{windowStart(reference), reference.resetAtMillis};
        }
        if (window == null || observedAtMillis <= 0L) {
            return null;
        }
        long resetAt = window.effectiveResetAtMillis(observedAtMillis);
        long startAt = resetAt - window.windowSeconds * 1000L;
        if (resetAt <= startAt) {
            return null;
        }
        return new long[]{startAt, resetAt};
    }

    private static long windowStart(UsageSample reference) {
        return reference.resetAtMillis - reference.windowSeconds * 1000L;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!scrubEnabled) {
            return super.onTouchEvent(event);
        }
        List<UsageSample> active = activeSamples();
        long[] axis = activeAxis();
        if (active.isEmpty() || axis == null) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                // Keep scrolling parents from stealing a horizontal scrub.
                getParent().requestDisallowInterceptTouchEvent(true);
                updateScrub(event.getX(), active, axis);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                scrubbing = false;
                lastHapticBucket = Long.MIN_VALUE;
                if (scrubListener != null) {
                    scrubListener.onScrubEnd();
                }
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void updateScrub(float touchX, List<UsageSample> active, long[] axis) {
        float density = density();
        float left = SIDE_INSET_DP * density;
        float right = getWidth() - SIDE_INSET_DP * density;
        double ratio = Math.max(0d, Math.min(1d,
                (touchX - left) / (double) Math.max(1f, right - left)));
        long axisStart = axis[0];
        long axisSpan = axis[1] - axis[0];
        long time = axisStart + Math.round(ratio * axisSpan);
        long first = active.get(0).observedAtMillis;
        long last = active.get(active.size() - 1).observedAtMillis;
        long clamped = Math.max(first, Math.min(last, time));
        double percent = UsageStats.usedPercentAt(active, clamped);
        if (percent < 0d) {
            percent = active.get(active.size() - 1).usedPercent;
        }
        boolean changed = !scrubbing || clamped != scrubTimeMillis;
        scrubbing = true;
        scrubTimeMillis = clamped;
        scrubPercent = percent;
        // One gentle tick per 24th of the axis keeps scrubbing tactile without buzzing.
        long bucket = axisSpan <= 0L
                ? 0L : (clamped - axisStart) / Math.max(1L, axisSpan / HAPTIC_TICKS_PER_AXIS);
        if (bucket != lastHapticBucket) {
            lastHapticBucket = bucket;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        if (changed && scrubListener != null) {
            scrubListener.onScrub(clamped, percent, historicalSelection());
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = density();
        boolean dark = Ui.isDark(getContext());
        plot.set(SIDE_INSET_DP * density, PLOT_TOP_DP * density,
                getWidth() - SIDE_INSET_DP * density,
                getHeight() - PLOT_BOTTOM_INSET_DP * density);
        drawHeader(canvas, density, dark);
        drawFrame(canvas, density, dark);
        if (window == null || observedAtMillis <= 0L) {
            drawEmpty(canvas, dark, density, "Waiting for usage data");
            return;
        }
        long resetAt = window.effectiveResetAtMillis(observedAtMillis);
        long startAt = resetAt - window.windowSeconds * 1000L;
        if (resetAt <= startAt) {
            drawEmpty(canvas, dark, density, "Reset window unavailable");
            return;
        }
        drawBudgetLine(canvas, density, dark);
        drawHistoricalWindows(canvas, density, dark);
        if (!samples.isEmpty()) {
            drawCurrentWindow(canvas, startAt, resetAt, density, dark);
        }
        if (pace != null && pace.available && !samples.isEmpty() && !historicalSelection()) {
            drawProjection(canvas, startAt, resetAt, density, dark);
        }
        if (scrubbing && scrubPercent >= 0d) {
            drawScrub(canvas, density, dark);
        }
        drawAxisLabels(canvas, density, dark);
    }

    /** Window label on the left and the sample count on the right. */
    private void drawHeader(Canvas canvas, float density, boolean dark) {
        float baseline = HEADER_BASELINE_DP * density;
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(boldTypeface);
        paint.setTextSize(14f * density);
        paint.setColor(Ui.mainText(dark));
        canvas.drawText(label, plot.left, baseline, paint);

        paint.setTypeface(regularTypeface);
        paint.setTextSize(10f * density);
        paint.setColor(Ui.secondaryText(dark));
        String sampleLabel = samples.size() < 2 ? "Building history"
                : samples.size() + " samples";
        canvas.drawText(sampleLabel, plot.right - paint.measureText(sampleLabel), baseline,
                paint);
    }

    /** Hairlines marking 0% and 100% used. */
    private void drawFrame(Canvas canvas, float density, boolean dark) {
        paint.setStrokeWidth(1f * density);
        paint.setColor(Color.argb(dark ? 52 : 38, 128, 128, 128));
        canvas.drawLine(plot.left, plot.bottom, plot.right, plot.bottom, paint);
        canvas.drawLine(plot.left, plot.top, plot.right, plot.top, paint);
    }

    /** Sustainable budget: reaching 100% used exactly at reset. */
    private void drawBudgetLine(Canvas canvas, float density, boolean dark) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * density);
        paint.setPathEffect(budgetDash);
        paint.setColor(Color.argb(dark ? 115 : 95, 128, 128, 128));
        canvas.drawLine(plot.left, plot.bottom, plot.right, plot.top, paint);
        paint.setPathEffect(null);
    }

    /**
     * Normalizes completed windows to the same x-axis so historical burn shapes are comparable;
     * the selected window is emphasized and drawn last.
     */
    private void drawHistoricalWindows(Canvas canvas, float density, boolean dark) {
        for (int pass = 0; pass < 2; pass++) {
            for (int index = 0; index < windows.size() - 1; index++) {
                boolean selected = index == selectedWindowIndex;
                if ((pass == 0) == selected) {
                    continue;
                }
                List<UsageSample> historical = windows.get(index);
                if (historical.size() < 2) {
                    continue;
                }
                UsageSample reference = historical.get(historical.size() - 1);
                long historicalStart = windowStart(reference);
                path.reset();
                for (int sampleIndex = 0; sampleIndex < historical.size(); sampleIndex++) {
                    UsageSample sample = historical.get(sampleIndex);
                    float historicalX = plotX(sample.observedAtMillis, historicalStart,
                            reference.resetAtMillis);
                    float historicalY = plotY(sample.usedPercent);
                    if (sampleIndex == 0) {
                        path.moveTo(historicalX, historicalY);
                    } else {
                        path.lineTo(historicalX, historicalY);
                    }
                }
                paint.setStrokeWidth(selected ? 2.5f * density : 1.5f * density);
                paint.setColor(selected ? Ui.desaturatedAccent(getContext(), dark)
                        : Color.argb(dark ? 62 : 48, 128, 128, 128));
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                canvas.drawPath(path, paint);
            }
        }
    }

    /** The current window's burn line, dimmed while a recorded window is selected. */
    private void drawCurrentWindow(Canvas canvas, long startAt, long resetAt, float density,
            boolean dark) {
        path.reset();
        boolean started = false;
        for (UsageSample sample : samples) {
            float x = plotX(sample.observedAtMillis, startAt, resetAt);
            float y = plotY(sample.usedPercent);
            if (!started) {
                path.moveTo(x, y);
                started = true;
            } else {
                path.lineTo(x, y);
            }
        }
        boolean dimmed = historicalSelection();
        int accent = Ui.accent(getContext(), dark);
        paint.setColor(dimmed
                ? Color.argb(96, Color.red(accent), Color.green(accent), Color.blue(accent))
                : accent);
        paint.setStrokeWidth(3f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(path, paint);
    }

    /** Dashed projection from the latest sample toward the estimated exhaustion time. */
    private void drawProjection(Canvas canvas, long startAt, long resetAt, float density,
            boolean dark) {
        UsageSample latest = samples.get(samples.size() - 1);
        float fromX = plotX(latest.observedAtMillis, startAt, resetAt);
        float fromY = plotY(latest.usedPercent);
        float toX = plotX(Math.min(resetAt, pace.estimatedExhaustionAtMillis), startAt, resetAt);
        float toY = plotY(pace.estimatedExhaustionAtMillis <= resetAt ? 100 : latest.usedPercent);
        paint.setColor(pace.accelerated ? Ui.warning(dark)
                : Ui.desaturatedAccent(getContext(), dark));
        paint.setStrokeWidth(2f * density);
        paint.setPathEffect(projectionDash);
        canvas.drawLine(fromX, fromY, toX, toY, paint);
        paint.setPathEffect(null);
    }

    private void drawScrub(Canvas canvas, float density, boolean dark) {
        long[] axis = activeAxis();
        if (axis == null) {
            return;
        }
        float scrubX = plotX(scrubTimeMillis, axis[0], axis[1]);
        float scrubY = plotY((int) Math.round(scrubPercent));
        int accent = historicalSelection() ? Ui.desaturatedAccent(getContext(), dark)
                : Ui.accent(getContext(), dark);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * density);
        paint.setColor(Color.argb(dark ? 130 : 110, Color.red(accent), Color.green(accent),
                Color.blue(accent)));
        canvas.drawLine(scrubX, plot.top, scrubX, plot.bottom, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.cardColor(getContext(), dark));
        canvas.drawCircle(scrubX, scrubY, 6f * density, paint);
        paint.setColor(accent);
        canvas.drawCircle(scrubX, scrubY, 4f * density, paint);

        String bubble = scrubTimeLabel() + " · " + Math.round(scrubPercent) + "%";
        paint.setTypeface(regularTypeface);
        paint.setTextSize(11f * density);
        float textWidth = paint.measureText(bubble);
        float padding = 8f * density;
        float bubbleLeft = Math.max(plot.left, Math.min(plot.right - textWidth - padding * 2f,
                scrubX - textWidth / 2f - padding));
        float bubbleTop = plot.top - 30f * density;
        bubbleRect.set(bubbleLeft, bubbleTop, bubbleLeft + textWidth + padding * 2f,
                bubbleTop + 22f * density);
        paint.setColor(Ui.controlSurface(getContext(), dark));
        canvas.drawRoundRect(bubbleRect, 11f * density, 11f * density, paint);
        paint.setColor(Ui.mainText(dark));
        canvas.drawText(bubble, bubbleLeft + padding, bubbleTop + 15f * density, paint);
    }

    private String scrubTimeLabel() {
        boolean multiDay = window != null && window.windowSeconds > ONE_DAY_SECONDS;
        boolean is24Hour = DateFormat.is24HourFormat(getContext());
        String pattern = multiDay
                ? (is24Hour ? "EEE HH:mm" : "EEE h:mm a")
                : (is24Hour ? "HH:mm" : "h:mm a");
        return new SimpleDateFormat(pattern, Locale.getDefault())
                .format(new Date(scrubTimeMillis));
    }

    private void drawAxisLabels(Canvas canvas, float density, boolean dark) {
        float baseline = getHeight() - AXIS_LABEL_BASELINE_INSET_DP * density;
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(regularTypeface);
        paint.setTextSize(10f * density);
        paint.setColor(Ui.secondaryText(dark));
        canvas.drawText("0%", plot.left, baseline, paint);
        String reset = "reset";
        canvas.drawText(reset, plot.right - paint.measureText(reset), baseline, paint);
    }

    private void drawEmpty(Canvas canvas, boolean dark, float density, String text) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(regularTypeface);
        paint.setTextSize(12f * density);
        paint.setColor(Ui.secondaryText(dark));
        canvas.drawText(text, plot.left, plot.top + 24f * density, paint);
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    /** X position of {@code time} on an axis spanning {@code start}..{@code end}. */
    private float plotX(long time, long start, long end) {
        double ratio = Math.max(0d, Math.min(1d, (time - start) / (double) (end - start)));
        return plot.left + (float) ratio * (plot.right - plot.left);
    }

    private float plotY(int usedPercent) {
        return plot.bottom - Math.max(0, Math.min(100, usedPercent)) / 100f
                * (plot.bottom - plot.top);
    }
}
