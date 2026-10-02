package dev.bennett.codexmeter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import androidx.appcompat.content.res.AppCompatResources;

/** Animated percentage fill used by the Figma usage-counter cards. */
public final class UsageWaveView extends View {
    private static final long NORMAL_WAVE_DURATION_MS = 2400L;
    private static final long WARNING_WAVE_DURATION_MS = 950L;
    private static final String FONT_FAMILY = "sec";

    // Wave edge shape: segment count, horizontal swing, and the phase span top-to-bottom.
    private static final int NORMAL_WAVE_STEPS = 28;
    private static final int WARNING_WAVE_STEPS = 36;
    private static final float NORMAL_AMPLITUDE_DP = 8f;
    private static final float WARNING_AMPLITUDE_DP = 10f;
    private static final double NORMAL_WAVE_SPAN = Math.PI * 2;
    private static final double WARNING_WAVE_SPAN = Math.PI * 4;

    // Text and icon placement, in dp from the view's top/left (or right edge for the icon).
    private static final float TEXT_START_DP = 12f;
    private static final float PACE_AFTER_RESET_START_DP = 18f;
    private static final float TITLE_BASELINE_DP = 34f;
    private static final float RESET_TOP_BASELINE_DP = 67f;
    private static final float BOTTOM_LINE_BASELINE_DP = 87f;
    private static final float RIGHT_COLUMN_CENTER_FROM_END_DP = 48f;
    private static final float ICON_SIZE_DP = 36f;
    private static final float ICON_TOP_DP = 13f;
    private static final float PERCENT_BASELINE_DP = 85f;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint resetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint percentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path fillPath = new Path();
    private ValueAnimator animator;
    private Drawable icon;
    private String title = "";
    private String resetTop = "";
    private String resetBottom = "";
    private String pace = "";
    private int percent;
    private boolean warning;
    private float phase;
    private float phaseOffset;

    public UsageWaveView(Context context) {
        this(context, null);
    }

    public UsageWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        titlePaint.setTypeface(Typeface.create(FONT_FAMILY, Typeface.BOLD));
        resetPaint.setTypeface(Typeface.create(FONT_FAMILY, Typeface.NORMAL));
        pacePaint.setTypeface(Typeface.create(FONT_FAMILY, Typeface.BOLD));
        percentPaint.setTypeface(Typeface.create(FONT_FAMILY, Typeface.BOLD));
        percentPaint.setTextAlign(Paint.Align.CENTER);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    /**
     * @param reset full reset sentence, e.g. "Resets in 2h 50m"; spoken and, when there is no
     *     {@code countdown}, drawn as is
     * @param countdown time left until the reset, e.g. "2h 50m", or "" when there is none; drawn
     *     on its own line below a "Resets in" heading
     * @param paceAssessment usage-life estimate and accelerated-usage warning for the window
     */
    public void setUsage(String label, String reset, String countdown,
            UsagePace.Assessment paceAssessment, int remainingPercent, int iconRes,
            boolean invertedWave) {
        Context context = getContext();
        title = label;
        percent = Math.max(0, Math.min(100, remainingPercent));
        pace = UsageFormat.estimatedRemaining(context, paceAssessment);
        warning = paceAssessment != null && paceAssessment.accelerated;
        if (animator != null) {
            animator.setDuration(waveDurationMillis());
        }
        phaseOffset = invertedWave ? (float) Math.PI : 0f;
        splitResetText(reset, countdown);
        icon = AppCompatResources.getDrawable(context, iconRes);
        setContentDescription(accessibilityDescription(label, reset,
                UsageFormat.estimatedRemainingSpoken(context, paceAssessment)));
        invalidate();
    }

    /** Puts a countdown below a "Resets in" heading; any other reset text stays on one line. */
    private void splitResetText(String reset, String countdown) {
        if (countdown != null && !countdown.isEmpty()) {
            resetTop = getContext().getString(R.string.dashboard_wave_resets_in);
            resetBottom = countdown;
        } else {
            resetTop = reset == null ? "" : reset;
            resetBottom = "";
        }
    }

    private String accessibilityDescription(String label, String reset, String spokenPace) {
        Context context = getContext();
        String description = context.getString(R.string.dashboard_wave_description, label,
                percent, reset == null ? "" : reset);
        if (!spokenPace.isEmpty()) {
            description = context.getString(R.string.dashboard_sentence_join, description,
                    spokenPace);
        }
        if (warning) {
            description = context.getString(R.string.dashboard_sentence_join, description,
                    context.getString(R.string.dashboard_wave_accelerated));
        }
        return description;
    }

    private long waveDurationMillis() {
        return warning ? WARNING_WAVE_DURATION_MS : NORMAL_WAVE_DURATION_MS;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        animator.setDuration(waveDurationMillis());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            phase = (Float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        boolean dark = Ui.isDark(getContext());
        if (warning) {
            trackPaint.setColor(Ui.warningTrack(getContext(), dark));
            canvas.drawRect(0f, 0f, getWidth(), getHeight(), trackPaint);
        }
        drawWaveFill(canvas, density, dark);

        int foreground = warning ? (dark ? Color.WHITE : Color.BLACK) : Ui.mainText(dark);
        drawLabels(canvas, density, foreground);
        drawIconAndPercent(canvas, density, foreground);
    }

    /** Fills from the left edge up to the remaining percentage, with a wavy right edge. */
    private void drawWaveFill(Canvas canvas, float density, boolean dark) {
        float edge = getWidth() * percent / 100f;
        float amplitude = (warning ? WARNING_AMPLITUDE_DP : NORMAL_AMPLITUDE_DP) * density;
        fillPath.reset();
        fillPath.moveTo(0, 0);
        fillPath.lineTo(edge, 0);
        int steps = warning ? WARNING_WAVE_STEPS : NORMAL_WAVE_STEPS;
        for (int i = 1; i <= steps; i++) {
            float y = getHeight() * i / (float) steps;
            // The envelope pins the wave to the straight edge at the top and bottom.
            float envelope = (float) Math.sin(Math.PI * y / getHeight());
            float angle = (float) ((warning ? WARNING_WAVE_SPAN : NORMAL_WAVE_SPAN)
                    * y / getHeight() + phase + phaseOffset);
            float wave = (float) Math.sin(angle);
            float x = edge + amplitude * envelope * wave;
            fillPath.lineTo(x, y);
        }
        fillPath.lineTo(0, getHeight());
        fillPath.close();
        fillPaint.setColor(warning ? Ui.warning(dark)
                : Ui.desaturatedAccent(getContext(), dark));
        canvas.drawPath(fillPath, fillPaint);
    }

    private void drawLabels(Canvas canvas, float density, int foreground) {
        titlePaint.setColor(foreground);
        titlePaint.setTextSize(20f * density);
        // Reset duration must match title/percent contrast (black light / white dark).
        resetPaint.setColor(foreground);
        resetPaint.setTextSize(13f * density);
        pacePaint.setColor(foreground);
        pacePaint.setTextSize(12.5f * density);
        float textStart = TEXT_START_DP * density;
        float bottomBaseline = BOTTOM_LINE_BASELINE_DP * density;
        canvas.drawText(title, textStart, TITLE_BASELINE_DP * density, titlePaint);
        if (!resetTop.isEmpty()) {
            canvas.drawText(resetTop, textStart, RESET_TOP_BASELINE_DP * density, resetPaint);
            if (!resetBottom.isEmpty()) {
                canvas.drawText(resetBottom, textStart, bottomBaseline, resetPaint);
                if (!pace.isEmpty()) {
                    float paceX = PACE_AFTER_RESET_START_DP * density
                            + resetPaint.measureText(resetBottom);
                    canvas.drawText("· " + pace, paceX, bottomBaseline, pacePaint);
                }
            }
        } else if (!pace.isEmpty()) {
            canvas.drawText(pace, textStart, bottomBaseline, pacePaint);
        }
    }

    private void drawIconAndPercent(Canvas canvas, float density, int foreground) {
        float rightCenter = getWidth() - RIGHT_COLUMN_CENTER_FROM_END_DP * density;
        if (icon != null) {
            int size = Math.round(ICON_SIZE_DP * density);
            int left = Math.round(rightCenter - size / 2f);
            int top = Math.round(ICON_TOP_DP * density);
            icon.setBounds(left, top, left + size, top + size);
            icon.setTint(foreground);
            icon.draw(canvas);
        }
        percentPaint.setColor(foreground);
        percentPaint.setTextSize(22f * density);
        canvas.drawText(percent + "%", rightCenter, PERCENT_BASELINE_DP * density, percentPaint);
    }
}
