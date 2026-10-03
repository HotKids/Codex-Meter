package me.pipi.codexmeter;

import dev.bennett.codexmeter.NowBarCopy;
import dev.bennett.codexmeter.NowBarDisplayMode;
import dev.bennett.codexmeter.NowBarPercentMode;
import dev.bennett.codexmeter.UsagePace;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsageWindow;
import dev.bennett.codexmeter.WidgetMeters;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import androidx.annotation.RequiresApi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Posts a finite Live Update that Samsung may surface in the Now Bar.
 * Users can start it manually, or it can auto-start when remaining allowance hits a
 * configured threshold (same metric/threshold pattern as low-usage notifications).
 */
public final class NowBarManager {
    static final String ACTION_END = AppConstants.action("NOW_BAR_END");
    static final String ACTION_REFRESH = AppConstants.action("NOW_BAR_REFRESH");
    static final String ACTION_STOP = AppConstants.action("NOW_BAR_STOP");

    private static final String CHANNEL_ID = "codex_live_monitor_v2";
    private static final String EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing";
    private static final String SAMSUNG_ONGOING_PREFIX = "android.ongoingActivityNoti.";
    private static final String TAG = "CodexNowBar";

    // Session state.
    private static final String PREFS = "codex_meter_now_bar_v1";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_AUTO_TRIGGER_FOCUS = "auto_trigger_focus";
    private static final String KEY_FOCUS_METRIC = "focus_metric";
    private static final String KEY_POSTED_MODE = "posted_mode";
    private static final String KEY_POSTED_PROMOTION_ALLOWED = "posted_promotion_allowed";
    private static final String KEY_PREVIEW = "preview";
    private static final String KEY_START_REASON = "start_reason";
    private static final String KEY_UNTIL = "until";

    // Why the current session started (KEY_START_REASON values).
    private static final String START_ACCELERATED = "accelerated";
    private static final String START_LOW = "low";
    private static final String START_MANUAL = "manual";
    private static final String START_PREVIEW = "preview";

    private static final int NOTIFICATION_ID = 8610;
    private static final int REQUEST_END = 8611;
    private static final int REQUEST_REFRESH = 8612;
    private static final int REQUEST_STOP = 8613;
    private static final int REQUEST_OPEN = 8614;

    private static final int PROGRESS_MAX = 100;
    private static final int PREVIEW_USED_PERCENT = 18;
    private static final long PREVIEW_DURATION_MS = TimeUnit.MINUTES.toMillis(20);
    private static final long PREVIEW_RESET_AFTER_DAYS = 4;
    private static final long PROMOTION_LOG_DELAY_MS = 1000L;

    private NowBarManager() {
    }

    // ---------------------------------------------------------------------------------------
    // Starting
    // ---------------------------------------------------------------------------------------

    public static synchronized boolean start(Context context) {
        return startInternal(context, START_MANUAL, null, 0L);
    }

    /**
     * Starts a live session from the cached usage.
     *
     * @param triggerFocus the window that triggered an auto-start, which also locks the focus
     * @param requestedUntil the session end, or 0 to end at the next usage reset
     */
    private static boolean startInternal(Context context, String reason, String triggerFocus,
            long requestedUntil) {
        DiagnosticLog.info(context, "now_bar", "start_requested", "reason", reason);
        UsageSnapshot snapshot = AppPreferences.loadSnapshot(context);
        if (!hasUsage(snapshot)) {
            DiagnosticLog.warn(context, "now_bar", "start_rejected",
                    "reason", "missing_usage");
            return false;
        }
        long now = System.currentTimeMillis();
        long until = requestedUntil > now ? requestedUntil : snapshot.nextResetMillis(now);
        if (until <= now) {
            return false;
        }
        NowBarPreferences.clearSuppression(context);
        boolean fromLowAutoStart = START_LOW.equals(reason);
        String focus = NowBarPercentMode.normalizeFocusMetric(triggerFocus);
        if (focus == null) {
            focus = computeInitialFocus(context, snapshot, fromLowAutoStart, now);
        }
        String autoTrigger;
        if (triggerFocus != null) {
            autoTrigger = focus;
        } else if (fromLowAutoStart) {
            autoTrigger = lowUsageTrigger(context,
                    currentFiveHour(snapshot, now), currentLongWindow(snapshot, now));
        } else {
            autoTrigger = null;
        }
        saveState(context, false, until, focus, true, autoTrigger, reason);
        if (post(context, snapshot, until, false)) {
            DiagnosticLog.info(context, "now_bar", "started",
                    "reason", reason,
                    "focus", focus,
                    "until", until);
            return true;
        }
        stop(context, false);
        DiagnosticLog.warn(context, "now_bar", "start_failed", "reason", reason);
        return false;
    }

    /** Shows a short-lived monitor with sample usage so the user can see how it looks. */
    public static synchronized boolean startPreview(Context context) {
        long now = System.currentTimeMillis();
        long until = now + PREVIEW_DURATION_MS;
        UsageSnapshot preview = previewSnapshot(now,
                TimeUnit.DAYS.toSeconds(PREVIEW_RESET_AFTER_DAYS),
                (now + TimeUnit.DAYS.toMillis(PREVIEW_RESET_AFTER_DAYS)) / 1000L);
        NowBarPreferences.clearSuppression(context);
        String focus = computeInitialFocus(context, preview, false, now);
        saveState(context, true, until, focus, true, null, START_PREVIEW);
        if (post(context, preview, until, true)) {
            return true;
        }
        stop(context, false);
        return false;
    }

    /**
     * Starts the live monitor when auto-start is enabled, notifications are allowed,
     * the user has not dismissed the current window, and remaining usage meets the threshold.
     */
    public static synchronized boolean maybeAutoStart(Context context, UsageSnapshot snapshot) {
        if (context == null || isActive(context) || isPreview(context)) {
            return false;
        }
        boolean lowEnabled = NowBarPreferences.isAutoStartEnabled(context);
        boolean paceEnabled = UsagePacePreferences.areWarningsEnabled(context)
                && NowBarPreferences.isAcceleratedStartEnabled(context);
        if (!lowEnabled && !paceEnabled) {
            return false;
        }
        if (NowBarPreferences.isSuppressed(context) || !canPostNotifications(context)) {
            return false;
        }
        if (lowEnabled && NowBarPreferences.meetsThreshold(context, snapshot)) {
            return startInternal(context, START_LOW, null, 0L);
        }
        if (!paceEnabled) {
            return false;
        }
        long now = System.currentTimeMillis();
        int acceleratedWindow = acceleratedWindow(context, snapshot, now);
        if (acceleratedWindow == UsagePace.WINDOW_NONE) {
            return false;
        }
        String focus = focusForPaceWindow(acceleratedWindow);
        long until = acceleratedUntil(context, snapshot, focus, now);
        return until > now && startInternal(context, START_ACCELERATED, focus, until);
    }

    // ---------------------------------------------------------------------------------------
    // Keeping an active session current
    // ---------------------------------------------------------------------------------------

    public static synchronized void onUsageUpdated(Context context, UsageSnapshot snapshot) {
        if (isPreview(context)) {
            return;
        }
        if (!hasStoredActiveState(context)) {
            maybeAutoStart(context, snapshot);
            return;
        }
        if (!hasUsage(snapshot)) {
            stop(context, false);
            return;
        }
        long now = System.currentTimeMillis();
        if (START_ACCELERATED.equals(sessionStartReason(context))
                && !retargetAcceleratedSession(context, snapshot, now)) {
            stop(context, false);
            maybeAutoStart(context, snapshot);
            return;
        }
        long until = activeUntil(context);
        if (until <= now) {
            stop(context, false);
            return;
        }
        if (!post(context, snapshot, until, false)) {
            stop(context, false);
        }
    }

    /**
     * Points an accelerated-usage session at the window that is accelerating now and ends it at
     * that window's reset; returns false when no window is accelerating any more.
     */
    private static boolean retargetAcceleratedSession(Context context, UsageSnapshot snapshot,
            long now) {
        int acceleratedWindow = acceleratedWindow(context, snapshot, now);
        if (acceleratedWindow == UsagePace.WINDOW_NONE) {
            return false;
        }
        String focus = focusForPaceWindow(acceleratedWindow);
        long acceleratedUntil = acceleratedUntil(context, snapshot, focus, now);
        if (acceleratedUntil <= now) {
            return false;
        }
        saveState(context, false, acceleratedUntil, focus, true, focus, START_ACCELERATED);
        return true;
    }

    public static synchronized void onPaceSettingsChanged(Context context) {
        UsageSnapshot snapshot = AppPreferences.loadSnapshot(context);
        if (START_ACCELERATED.equals(sessionStartReason(context))) {
            onUsageUpdated(context, snapshot);
        } else if (hasStoredActiveState(context)) {
            repostActive(context);
        } else {
            maybeAutoStart(context, snapshot);
        }
    }

    /** Re-posts the stored session after a reboot or app update, or else considers auto-start. */
    public static synchronized void restore(Context context) {
        if (!repostActive(context)) {
            maybeAutoStart(context, AppPreferences.loadSnapshot(context));
        }
    }

    /** Re-posts the stored session; stops it and returns false when that is not possible. */
    public static synchronized boolean repostActive(Context context) {
        if (!hasStoredActiveState(context)) {
            return false;
        }
        long until = activeUntil(context);
        if (until <= System.currentTimeMillis()) {
            stop(context, false);
            return false;
        }
        boolean posted;
        if (isPreview(context)) {
            posted = startPreviewWithEnd(context, until);
        } else {
            UsageSnapshot snapshot = AppPreferences.loadSnapshot(context);
            if (START_ACCELERATED.equals(sessionStartReason(context))) {
                // Re-evaluates which window is accelerating, stopping when none is.
                onUsageUpdated(context, snapshot);
                return isActive(context);
            }
            posted = hasUsage(snapshot) && post(context, snapshot, until, false);
        }
        if (!posted) {
            stop(context, false);
        }
        return posted;
    }

    /**
     * Rebuilds an active notification when Android's Live Update permission changes. Without
     * this, a monitor posted through Samsung compatibility remains stuck on that contract after
     * the user grants promotion access, and an explicit Android Live Update is not promoted until
     * some unrelated usage refresh reposts it.
     */
    public static synchronized boolean refreshActiveNotificationContract(Context context) {
        if (!isActive(context) || !canPostNotifications(context)) {
            return true;
        }
        SharedPreferences postedState = state(context);
        String postedMode = postedState.getString(KEY_POSTED_MODE, null);
        boolean postedPromotionAllowed = postedState.getBoolean(
                KEY_POSTED_PROMOTION_ALLOWED, false);
        String resolvedMode = resolveDisplayMode(context);
        boolean promotionAllowedNow = canPostPromotedNotifications(context);
        if (!NowBarDisplayMode.notificationContractChanged(postedMode,
                postedPromotionAllowed, resolvedMode, promotionAllowedNow)) {
            return true;
        }
        return repostActive(context);
    }

    private static boolean startPreviewWithEnd(Context context, long until) {
        long now = System.currentTimeMillis();
        UsageSnapshot preview = previewSnapshot(now);
        if (!hasStoredActiveState(context) || lockedFocusMetric(context) == null) {
            String focus = computeInitialFocus(context, preview, false, now);
            saveState(context, true, until, focus, true, null, START_PREVIEW);
        }
        return post(context, preview, until, true);
    }

    /**
     * Recomputes the progress focus from the current percent-mode setting and usage,
     * then reposts when a monitor is already active. Used when the user changes the
     * percentage preference in Settings. AUTO restores the session auto-start trigger
     * when one was recorded, so cycling modes does not drop that lock.
     */
    public static synchronized boolean applyPercentModeChange(Context context) {
        if (!hasStoredActiveState(context)) {
            return false;
        }
        long until = activeUntil(context);
        if (until <= System.currentTimeMillis()) {
            stop(context, false);
            return false;
        }
        boolean preview = isPreview(context);
        long now = System.currentTimeMillis();
        UsageSnapshot snapshot = preview
                ? previewSnapshot(now) : AppPreferences.loadSnapshot(context);
        if (!preview && !hasUsage(snapshot)) {
            stop(context, false);
            return false;
        }
        String focus = NowBarPercentMode.focusForSettingsChange(
                NowBarPreferences.getPercentMode(context),
                currentFiveHour(snapshot, now), currentLongWindow(snapshot, now),
                sessionAutoTriggerFocus(context));
        // Preserve KEY_AUTO_TRIGGER_FOCUS across mode switches.
        saveState(context, preview, until, focus, false, null, sessionStartReason(context));
        if (post(context, snapshot, until, preview)) return true;
        stop(context, false);
        return false;
    }

    // ---------------------------------------------------------------------------------------
    // Stopping and session queries
    // ---------------------------------------------------------------------------------------

    public static synchronized void stop(Context context) {
        stop(context, true);
    }

    /**
     * Ends the session and removes the notification.
     *
     * @param suppressAutoRestart whether auto-start stays off until the session would have ended
     */
    public static synchronized void stop(Context context, boolean suppressAutoRestart) {
        long until = activeUntil(context);
        boolean wasActive = hasStoredActiveState(context);
        DiagnosticLog.info(context, "now_bar", "stop_requested",
                "was_active", wasActive,
                "suppress_auto_restart", suppressAutoRestart);
        state(context).edit().clear().apply();
        if (suppressAutoRestart && wasActive && until > System.currentTimeMillis()) {
            // Respect an explicit stop/dismiss until the window would have ended anyway.
            NowBarPreferences.markSuppressedUntil(context, until);
        }
        NotificationManager manager = manager(context);
        if (manager != null) {
            try {
                manager.cancel(NOTIFICATION_ID);
            } catch (RuntimeException exception) {
                DiagnosticLog.error(context, "now_bar", "notification_cancel_failed",
                        exception);
                Log.w(TAG, "Could not cancel live monitor notification", exception);
            }
        }
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) {
            try {
                alarms.cancel(endIntent(context));
            } catch (RuntimeException exception) {
                DiagnosticLog.error(context, "now_bar", "expiry_cancel_failed", exception);
                Log.w(TAG, "Could not cancel live monitor expiry", exception);
            }
        }
    }

    public static boolean isActive(Context context) {
        return state(context).getBoolean(KEY_ACTIVE, false)
                && activeUntil(context) > System.currentTimeMillis();
    }

    public static boolean isPreview(Context context) {
        return state(context).getBoolean(KEY_PREVIEW, false);
    }

    public static long activeUntil(Context context) {
        return state(context).getLong(KEY_UNTIL, 0L);
    }

    public static boolean canPostNotifications(Context context) {
        NotificationManager manager = manager(context);
        if (manager == null || !manager.areNotificationsEnabled()
                || (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED)) {
            return false;
        }
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    public static boolean canPostPromotedNotifications(Context context) {
        NotificationManager manager = manager(context);
        return Build.VERSION.SDK_INT >= 36 && manager != null
                && Api36.canPostPromotedNotifications(manager);
    }

    public static boolean isPromoted(Context context) {
        NotificationManager manager = manager(context);
        return Build.VERSION.SDK_INT >= 36 && manager != null
                && Api36.isPostedNotificationPromoted(manager, NOTIFICATION_ID);
    }

    public static String postedDisplayMode(Context context) {
        String posted = state(context).getString(KEY_POSTED_MODE, null);
        return posted == null ? resolveDisplayMode(context) : NowBarDisplayMode.normalize(posted);
    }

    private static String resolveDisplayMode(Context context) {
        return NowBarDisplayMode.resolve(NowBarPreferences.getDisplayMode(context),
                isSamsungDevice(), Build.VERSION.SDK_INT,
                canPostPromotedNotifications(context));
    }

    private static boolean isSamsungDevice() {
        return "samsung".equalsIgnoreCase(Build.MANUFACTURER)
                || "samsung".equalsIgnoreCase(Build.BRAND);
    }

    // ---------------------------------------------------------------------------------------
    // Posting the notification
    // ---------------------------------------------------------------------------------------

    private static boolean post(Context context, UsageSnapshot snapshot, long until,
            boolean preview) {
        NotificationManager manager = manager(context);
        if (manager == null || !canPostNotifications(context)) {
            return false;
        }
        try {
            createChannel(context, manager);
        } catch (RuntimeException exception) {
            DiagnosticLog.error(context, "now_bar", "channel_create_failed", exception);
            Log.w(TAG, "Could not create live monitor notification channel", exception);
            return false;
        }

        MonitorContent content = new MonitorContent(context, snapshot, preview);
        String displayMode = resolveDisplayMode(context);
        Notification.Builder builder = baseBuilder(context, content, preview);
        if (NowBarDisplayMode.SAMSUNG_COMPATIBILITY.equals(displayMode)) {
            applySamsungCompatibility(context, builder, content, until, preview);
        } else {
            Bundle promotionExtras = new Bundle();
            promotionExtras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true);
            builder.addExtras(promotionExtras);
            if (Build.VERSION.SDK_INT >= 36) {
                Api36.applyLiveUpdateStyle(context, builder, content.used,
                        content.focusCritical, content.accelerated);
            }
        }
        Notification notification;
        boolean legacyColorizedFallback = false;
        try {
            notification = builder.build();
            // Early Android 16 releases require colorization for promotability, while newer
            // releases reject colorized Live Updates. Trust the running framework's predicate:
            // use the modern uncolorized contract first and retry only where it is required.
            if (Build.VERSION.SDK_INT >= 36
                    && NowBarDisplayMode.ANDROID_LIVE_UPDATE.equals(displayMode)
                    && !Api36.hasPromotableCharacteristics(notification)) {
                builder.setColorized(true);
                Notification colorizedCandidate = builder.build();
                if (Api36.hasPromotableCharacteristics(colorizedCandidate)) {
                    notification = colorizedCandidate;
                    legacyColorizedFallback = true;
                }
            }
        } catch (RuntimeException exception) {
            DiagnosticLog.error(context, "now_bar", "notification_build_failed", exception);
            Log.w(TAG, "Could not build live monitor notification", exception);
            return false;
        }
        logPosting(context, notification, content, displayMode, legacyColorizedFallback,
                preview);
        try {
            manager.notify(NOTIFICATION_ID, notification);
            state(context).edit()
                    .putString(KEY_POSTED_MODE, displayMode)
                    .putBoolean(KEY_POSTED_PROMOTION_ALLOWED,
                            canPostPromotedNotifications(context))
                    .apply();
            if (Build.VERSION.SDK_INT >= 36
                    && NowBarDisplayMode.ANDROID_LIVE_UPDATE.equals(displayMode)) {
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> Api36.logPostedPromotionState(manager, NOTIFICATION_ID),
                        PROMOTION_LOG_DELAY_MS);
            }
        } catch (RuntimeException exception) {
            DiagnosticLog.error(context, "now_bar", "notification_post_failed", exception,
                    "display_mode", displayMode);
            Log.w(TAG, "Could not post live monitor notification", exception);
            try {
                manager.cancel(NOTIFICATION_ID);
            } catch (RuntimeException ignored) {
            }
            return false;
        }
        try {
            scheduleEnd(context, until);
        } catch (RuntimeException exception) {
            DiagnosticLog.error(context, "now_bar", "expiry_schedule_failed", exception,
                    "until", until);
            Log.w(TAG, "Could not schedule live monitor expiry", exception);
        }
        return true;
    }

    /** The notification fields shared by every display mode, with Stop and Refresh actions. */
    private static Notification.Builder baseBuilder(Context context, MonitorContent content,
            boolean preview) {
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, REQUEST_OPEN, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopIntent = actionIntent(context, REQUEST_STOP, ACTION_STOP);
        PendingIntent refreshIntent = actionIntent(context, REQUEST_REFRESH, ACTION_REFRESH);
        Icon stopActionIcon = Icon.createWithResource(context, R.drawable.ic_notification);
        Icon refreshActionIcon = Icon.createWithResource(context, R.drawable.ic_refresh);
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.alerts_now_bar_title))
                .setContentText(content.windowText)
                .setContentIntent(contentIntent)
                .setDeleteIntent(stopIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColor(accentColor(content.accelerated))
                .setShowWhen(false)
                .addAction(new Notification.Action.Builder(
                        stopActionIcon, context.getString(R.string.alerts_now_bar_stop),
                        stopIntent).build());
        if (!preview) {
            builder.addAction(new Notification.Action.Builder(
                    refreshActionIcon, context.getString(R.string.alerts_now_bar_refresh),
                    refreshIntent).build());
        }
        return builder;
    }

    /** Adds Samsung's ongoing-activity extras so One UI shows the monitor in the Now Bar. */
    private static void applySamsungCompatibility(Context context, Notification.Builder builder,
            MonitorContent content, long until, boolean preview) {
        Icon chipIcon = Icon.createWithResource(context, R.drawable.ic_codex_logo_on_accent);
        Icon nowBarIcon = themeAdaptiveCodexLogo(context);
        Icon progressDot = Icon.createWithResource(context, R.drawable.ic_now_bar_progress_dot);
        String chipLabel = content.weeklyFocus || !content.showFiveHour
                ? content.longLabel : content.fiveHourLabel;
        String secondaryInfo = content.accelerated && !content.estimate.isEmpty()
                ? content.estimate : availableWindowsText(context, content);

        Bundle extras = new Bundle();
        extras.putInt(SAMSUNG_ONGOING_PREFIX + "style", 1);
        extras.putParcelable(SAMSUNG_ONGOING_PREFIX + "chipIcon", chipIcon);
        extras.putInt(SAMSUNG_ONGOING_PREFIX + "chipBgColor", accentColor(content.accelerated));
        extras.putCharSequence(SAMSUNG_ONGOING_PREFIX + "chipExpandedText",
                NowBarText.chipExpandedText(content.strings, chipLabel, content.progressWindow,
                        content.observedAt, content.now));
        extras.putCharSequence(SAMSUNG_ONGOING_PREFIX + "primaryInfo",
                content.windowText);
        extras.putCharSequence(SAMSUNG_ONGOING_PREFIX + "secondaryInfo", secondaryInfo);
        extras.putString(SAMSUNG_ONGOING_PREFIX + "description",
                context.getString(R.string.alerts_now_bar_samsung_description));
        extras.putInt(SAMSUNG_ONGOING_PREFIX + "progress", content.used);
        extras.putInt(SAMSUNG_ONGOING_PREFIX + "progressMax", PROGRESS_MAX);
        extras.putParcelable(SAMSUNG_ONGOING_PREFIX + "progressSegments.icon", progressDot);
        extras.putParcelable(SAMSUNG_ONGOING_PREFIX + "nowbarIcon", nowBarIcon);
        extras.putString(SAMSUNG_ONGOING_PREFIX + "nowbarPrimaryInfo", content.firstMeterText);
        extras.putString(SAMSUNG_ONGOING_PREFIX + "nowbarSecondaryInfo",
                content.remainingMeterText);
        extras.putString(SAMSUNG_ONGOING_PREFIX + "nowbarIconType", "progress");
        builder.addExtras(extras)
                .setSubText(context.getString(preview
                        ? R.string.alerts_now_bar_preview_subtext
                        : R.string.alerts_now_bar_until_reset_subtext))
                .setProgress(PROGRESS_MAX, content.used, false)
                .setCategory(Notification.CATEGORY_STATUS)
                .setShowWhen(true)
                .setWhen(until)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setTimeoutAfter(Math.max(1L, until - content.now));
    }

    private static String availableWindowsText(Context context, MonitorContent content) {
        if (content.fiveHour != null && content.longWindow != null) {
            return context.getString(R.string.alerts_now_bar_both_windows);
        }
        if (content.fiveHour != null) {
            return context.getString(R.string.alerts_now_bar_five_hour_window);
        }
        if (content.longWindow != null) {
            return context.getString(content.longIsMonthly
                    ? R.string.alerts_now_bar_monthly_window
                    : R.string.alerts_now_bar_weekly_window);
        }
        return context.getString(R.string.alerts_now_bar_window_unavailable);
    }

    private static void logPosting(Context context, Notification notification,
            MonitorContent content, String displayMode, boolean legacyColorizedFallback,
            boolean preview) {
        boolean promotable = Build.VERSION.SDK_INT >= 36
                && Api36.hasPromotableCharacteristics(notification);
        Log.i(TAG, "Posting live monitor: mode=" + displayMode + " promotable=" + promotable
                + " allowed=" + canPostPromotedNotifications(context)
                + " legacyColorizedFallback=" + legacyColorizedFallback
                + " preview=" + preview + " remaining=" + content.remaining
                + " focusCritical=" + content.focusCritical);
        DiagnosticLog.info(context, "now_bar", "notification_posting",
                "display_mode", displayMode,
                "promotable", promotable,
                "promotion_allowed", canPostPromotedNotifications(context),
                "legacy_colorized_fallback", legacyColorizedFallback,
                "preview", preview,
                "remaining_percent", content.remaining,
                "focus", content.focus);
    }

    /** Blue accent normally; the warning color while usage is accelerating. */
    private static int accentColor(boolean accelerated) {
        return accelerated ? Ui.warning(false) : Color.rgb(3, 129, 254);
    }

    /**
     * Picks a fixed (non night-qualified) Codex logo resource from the posting process's
     * current UI mode so SystemUI loads the intended contrast without re-resolving
     * {@code drawable-night}.
     */
    private static Icon themeAdaptiveCodexLogo(Context context) {
        return Icon.createWithResource(context, R.mipmap.ic_launcher);
    }

    private static int themeAdaptiveCodexLogoRes(Context context) {
        int night = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES
                ? R.drawable.ic_codex_logo_dark
                : R.drawable.ic_codex_logo;
    }

    /** Creates (or renames, for the current locale) the live monitor channel. */
    private static void createChannel(Context context, NotificationManager manager) {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.alerts_now_bar_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(
                context.getString(R.string.alerts_now_bar_channel_description));
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
    }

    /** Ends the session at {@code until} even if no usage refresh happens before then. */
    private static void scheduleEnd(Context context, long until) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, endIntent(context));
        }
    }

    private static PendingIntent endIntent(Context context) {
        return actionIntent(context, REQUEST_END, ACTION_END);
    }

    /** A broadcast to {@link NowBarActionReceiver} for one of the session actions. */
    private static PendingIntent actionIntent(Context context, int requestCode, String action) {
        return PendingIntent.getBroadcast(context, requestCode,
                new Intent(context, NowBarActionReceiver.class).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static NotificationManager manager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    // ---------------------------------------------------------------------------------------
    // Focus, usage windows, and session state
    // ---------------------------------------------------------------------------------------

    /**
     * The window that drives the progress bar: an accelerated session stays on its trigger,
     * AUTO mode keeps the session's auto-start trigger or initial focus, and the explicit
     * five-hour and weekly modes use that window.
     */
    private static String resolveFocus(Context context, UsageWindow fiveHour,
            UsageWindow longWindow) {
        String percentMode = NowBarPreferences.getPercentMode(context);
        String lockedForAuto = null;
        if (NowBarPercentMode.AUTO.equals(NowBarPercentMode.normalize(percentMode))) {
            lockedForAuto = sessionAutoTriggerFocus(context);
            if (lockedForAuto == null) {
                lockedForAuto = lockedFocusMetric(context);
            }
        }
        String acceleratedTrigger = START_ACCELERATED.equals(sessionStartReason(context))
                ? sessionAutoTriggerFocus(context) : null;
        if (acceleratedTrigger != null) {
            return NowBarPercentMode.resolveFocus(NowBarPercentMode.AUTO, fiveHour, longWindow,
                    acceleratedTrigger);
        }
        return NowBarPercentMode.resolveFocus(percentMode, fiveHour, longWindow, lockedForAuto);
    }

    private static String computeInitialFocus(Context context, UsageSnapshot snapshot,
            boolean fromAutoStart, long now) {
        UsageWindow fiveHour = currentFiveHour(snapshot, now);
        UsageWindow longWindow = currentLongWindow(snapshot, now);
        String mode = NowBarPreferences.getPercentMode(context);
        if (!NowBarPercentMode.AUTO.equals(NowBarPercentMode.normalize(mode))) {
            return NowBarPercentMode.resolveFocus(mode, fiveHour, longWindow, null);
        }
        if (fromAutoStart) {
            String triggered = lowUsageTrigger(context, fiveHour, longWindow);
            if (triggered != null) {
                return triggered;
            }
        }
        return NowBarPercentMode.lowerRemainingFocus(fiveHour, longWindow);
    }

    /** The window that crossed the low-usage auto-start threshold, or null. */
    private static String lowUsageTrigger(Context context, UsageWindow fiveHour,
            UsageWindow longWindow) {
        return NowBarPercentMode.triggeredFocus(
                NowBarPreferences.getMetric(context),
                NowBarPreferences.getThreshold(context),
                fiveHour, longWindow);
    }

    private static int acceleratedWindow(Context context, UsageSnapshot snapshot, long now) {
        if (!UsagePacePreferences.areWarningsEnabled(context)
                || !NowBarPreferences.isAcceleratedStartEnabled(context)) {
            return UsagePace.WINDOW_NONE;
        }
        return UsagePace.mostAcceleratedWindow(snapshot, now,
                UsagePacePreferences.getSensitivity(context));
    }

    private static String focusForPaceWindow(int window) {
        // The monthly window occupies the long-window (weekly) slot of the monitor.
        return window == UsagePace.WINDOW_WEEKLY || window == UsagePace.WINDOW_MONTHLY
                ? NowBarPercentMode.WEEKLY : NowBarPercentMode.FIVE_HOUR;
    }

    /** When the focused window's acceleration ends (its reset), or 0 if it is not accelerating. */
    private static long acceleratedUntil(Context context, UsageSnapshot snapshot, String focus,
            long now) {
        if (snapshot == null) {
            return 0L;
        }
        UsageWindow window = NowBarPercentMode.selectWindow(focus,
                currentFiveHour(snapshot, now), currentLongWindow(snapshot, now));
        UsagePace.Assessment assessment = UsagePacePreferences.assess(
                context, snapshot, window, now);
        return assessment.accelerated ? assessment.resetAtMillis : 0L;
    }

    private static boolean hasUsage(UsageSnapshot snapshot) {
        return snapshot != null && (snapshot.fiveHour != null || snapshot.longWindow() != null);
    }

    private static UsageWindow currentFiveHour(UsageSnapshot snapshot, long now) {
        return snapshot == null ? null
                : PhoneUsageWindows.currentWindow(snapshot.fiveHour, snapshot.fetchedAtMillis, now);
    }

    private static UsageWindow currentLongWindow(UsageSnapshot snapshot, long now) {
        return snapshot == null ? null
                : PhoneUsageWindows.currentWindow(snapshot.longWindow(), snapshot.fetchedAtMillis,
                        now);
    }

    /** Sample usage for previews: one weekly window with {@value #PREVIEW_USED_PERCENT}% used. */
    private static UsageSnapshot previewSnapshot(long now, long resetAfterSeconds,
            long resetAtSeconds) {
        return new UsageSnapshot("plus", true, false, null,
                new UsageWindow(PREVIEW_USED_PERCENT, TimeUnit.DAYS.toSeconds(7),
                        resetAfterSeconds, resetAtSeconds),
                now);
    }

    /** {@link #previewSnapshot(long, long, long)} without a reset time. */
    private static UsageSnapshot previewSnapshot(long now) {
        return previewSnapshot(now, 0L, 0L);
    }

    private static SharedPreferences state(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean hasStoredActiveState(Context context) {
        return state(context).getBoolean(KEY_ACTIVE, false);
    }

    private static String lockedFocusMetric(Context context) {
        return NowBarPercentMode.normalizeFocusMetric(
                state(context).getString(KEY_FOCUS_METRIC, null));
    }

    private static String sessionAutoTriggerFocus(Context context) {
        return NowBarPercentMode.normalizeFocusMetric(
                state(context).getString(KEY_AUTO_TRIGGER_FOCUS, null));
    }

    private static String sessionStartReason(Context context) {
        String reason = state(context).getString(KEY_START_REASON, START_MANUAL);
        if (START_ACCELERATED.equals(reason) || START_LOW.equals(reason)
                || START_PREVIEW.equals(reason)) {
            return reason;
        }
        return START_MANUAL;
    }

    /**
     * @param updateAutoTrigger when true, writes or clears {@link #KEY_AUTO_TRIGGER_FOCUS};
     *        when false, leaves any existing session auto-start trigger untouched
     */
    private static void saveState(Context context, boolean preview, long until, String focus,
            boolean updateAutoTrigger, String autoTriggerFocus, String startReason) {
        SharedPreferences.Editor editor = state(context).edit()
                .putBoolean(KEY_ACTIVE, true)
                .putBoolean(KEY_PREVIEW, preview)
                .putLong(KEY_UNTIL, until)
                .putString(KEY_START_REASON, startReason == null ? START_MANUAL : startReason);
        String normalizedFocus = NowBarPercentMode.normalizeFocusMetric(focus);
        if (normalizedFocus == null) {
            editor.remove(KEY_FOCUS_METRIC);
        } else {
            editor.putString(KEY_FOCUS_METRIC, normalizedFocus);
        }
        if (updateAutoTrigger) {
            String normalizedTrigger = NowBarPercentMode.normalizeFocusMetric(autoTriggerFocus);
            if (normalizedTrigger == null) {
                editor.remove(KEY_AUTO_TRIGGER_FOCUS);
            } else {
                editor.putString(KEY_AUTO_TRIGGER_FOCUS, normalizedTrigger);
            }
        }
        editor.apply();
    }

    /** Usage values and copy for one post of the monitor notification. */
    private static final class MonitorContent {
        final NowBarText.Strings strings;
        final long now;
        /** Remote observation time that anchors reset countdowns. */
        final long observedAt;
        final UsageWindow fiveHour;
        /** The long-cadence window: weekly, or monthly on the Free tier. */
        final UsageWindow longWindow;
        final boolean longIsMonthly;
        final String fiveHourLabel;
        final String longLabel;
        final String focus;
        final boolean weeklyFocus;
        /** The focused window that drives the progress bar. */
        final UsageWindow progressWindow;
        final int used;
        final int remaining;
        final boolean accelerated;
        final String estimate;
        final boolean showFiveHour;
        final String fiveHourText;
        final String longWindowText;
        final String windowText;
        final String firstMeterText;
        final String remainingMeterText;
        final String focusCritical;

        MonitorContent(Context context, UsageSnapshot snapshot, boolean preview) {
            strings = new ResourceStrings(context);
            showFiveHour = AppPreferences.showDashboardFiveHour(context);
            now = System.currentTimeMillis();
            // Weekly slot carries the long-cadence window; on the Free tier that is monthly.
            longIsMonthly = snapshot != null && snapshot.longWindowIsMonthly();
            fiveHourLabel = context.getString(R.string.alerts_window_five_hour);
            longLabel = context.getString(longIsMonthly
                    ? R.string.alerts_window_monthly : R.string.alerts_window_weekly);
            UsageWindow snapshotFiveHour = snapshot == null || !showFiveHour
                    ? null : snapshot.fiveHour;
            UsageWindow snapshotLongWindow = snapshot == null ? null : snapshot.longWindow();
            if (preview) {
                fiveHour = snapshotFiveHour;
                longWindow = snapshotLongWindow;
            } else {
                long fetchedAt = snapshot == null ? 0L : snapshot.fetchedAtMillis;
                fiveHour = PhoneUsageWindows.currentWindow(snapshotFiveHour, fetchedAt, now);
                longWindow = PhoneUsageWindows.currentWindow(snapshotLongWindow, fetchedAt, now);
            }
            focus = resolveFocus(context, fiveHour, longWindow);
            weeklyFocus = NowBarPercentMode.isWeeklyFocus(focus);
            progressWindow = NowBarPercentMode.selectWindow(focus, fiveHour, longWindow);
            UsagePace.Assessment pace = UsagePacePreferences.assess(
                    context, snapshot, progressWindow, now);
            accelerated = !preview && pace.accelerated;
            estimate = UsageFormat.estimatedRemaining(context, pace);
            remaining = progressWindow == null ? 0 : progressWindow.remainingPercent();
            used = progressWindow == null ? 0 : progressWindow.usedPercent;
            // Preview snapshots invent their own windows without a remote observation time;
            // live monitors must use fetchedAt so reset_after_seconds stays anchored.
            observedAt = preview || snapshot == null ? now : snapshot.fetchedAtMillis;
            fiveHourText = showFiveHour
                    ? NowBarText.limitText(strings, fiveHourLabel, fiveHour, observedAt, now)
                    : "";
            longWindowText = NowBarText.limitText(strings, longLabel, longWindow, observedAt,
                    now);
            List<String> meterText = new ArrayList<>();
            WidgetOptions widgetOptions = AppPreferences.loadNowBarWidgetOptions(context);
            if (widgetOptions == null) {
                if (!fiveHourText.isEmpty()) {
                    meterText.add(fiveHourText);
                }
                meterText.add(longWindowText);
            } else {
                UsageCardState widgetState = new UsageCardState(true, snapshot,
                        AppPreferences.loadResetCredits(context), "", now);
                for (String key : WidgetRenderer.selectedKeys(widgetOptions, snapshot)) {
                    if (WidgetMeters.FIVE_HOUR.equals(key) && !showFiveHour) {
                        continue;
                    }
                    WidgetMeter meter = new WidgetMeter(context, key, widgetOptions, widgetState);
                    String value = WidgetMeters.NEXT_RESET.equals(key)
                            ? UsageCardFormat.bareReset(context.getResources(), meter.resetAtMillis,
                                    now) : meter.value;
                    meterText.add(context.getString(R.string.alerts_now_bar_meter_value,
                            meter.title, value)
                            + (WidgetMeters.NEXT_RESET.equals(key) ? " · "
                            + context.getString(R.string.widget_material_reset_credits,
                                    widgetState.availableCredits()) : ""));
                }
                if (meterText.isEmpty()) {
                    meterText.add(context.getString(R.string.alerts_now_bar_window_unavailable));
                }
            }
            windowText = String.join(" · ", meterText);
            firstMeterText = meterText.get(0);
            remainingMeterText = String.join(" · ", meterText.subList(1, meterText.size()));
            // Keep the capsule aligned with upstream while the expanded notification is localized.
            focusCritical = NowBarCopy.focusCriticalText(
                    weeklyFocus ? (longIsMonthly ? "M " : "W ") : "",
                    progressWindow, observedAt, now);
        }
    }

    /**
     * {@link NowBarText} copy from string resources. In English it renders exactly like the
     * shared {@link NowBarCopy}.
     */
    private static final class ResourceStrings implements NowBarText.Strings {
        private final Resources resources;

        ResourceStrings(Context context) {
            this.resources = context.getResources();
        }

        @Override
        public String fiveHourLabel() {
            return resources.getString(R.string.alerts_window_five_hour);
        }

        @Override
        public String percent(int percent) {
            return resources.getString(R.string.alerts_now_bar_percent, percent);
        }

        @Override
        public String daysHours(long days, long hours) {
            return resources.getString(R.string.alerts_now_bar_duration_days_hours, days, hours);
        }

        @Override
        public String days(long days) {
            return resources.getString(R.string.alerts_now_bar_duration_days, days);
        }

        @Override
        public String hoursMinutes(long hours, long minutes) {
            return resources.getString(R.string.alerts_now_bar_duration_hours_minutes,
                    hours, minutes);
        }

        @Override
        public String hours(long hours) {
            return resources.getString(R.string.alerts_now_bar_duration_hours, hours);
        }

        @Override
        public String minutes(long minutes) {
            return resources.getString(R.string.alerts_now_bar_duration_minutes, minutes);
        }

        @Override
        public String chip(String windowLabel, String value) {
            return resources.getString(R.string.alerts_now_bar_chip, windowLabel, value);
        }

        @Override
        public String chipUnavailable(String windowLabel) {
            return resources.getString(R.string.alerts_now_bar_chip_unavailable, windowLabel);
        }

        @Override
        public String limitLeft(String label, int percent) {
            return resources.getString(R.string.alerts_now_bar_limit_left, label, percent);
        }

        @Override
        public String limitResetsIn(String label, String duration) {
            return resources.getString(R.string.alerts_now_bar_limit_resets_in, label, duration);
        }

        @Override
        public String limitUnavailable(String label) {
            return resources.getString(R.string.alerts_now_bar_limit_unavailable, label);
        }
    }

    /** Keeps API 36 class references out of code paths verified on older Android releases. */
    @RequiresApi(36)
    private static final class Api36 {
        static void applyLiveUpdateStyle(Context context, Notification.Builder builder, int used,
                String criticalText, boolean accelerated) {
            Notification.ProgressStyle style = new Notification.ProgressStyle()
                    .setProgress(used)
                    .setStyledByProgress(true)
                    // Plain circle tracker — not the brand glyph (that belongs in setSmallIcon).
                    .setProgressTrackerIcon(
                            Icon.createWithResource(context, R.drawable.ic_now_bar_progress_dot))
                    .setProgressSegments(Collections.singletonList(
                            new Notification.ProgressStyle.Segment(PROGRESS_MAX)
                                    .setColor(accentColor(accelerated))));
            builder.setStyle(style).setShortCriticalText(criticalText);
        }

        static boolean canPostPromotedNotifications(NotificationManager manager) {
            return manager.canPostPromotedNotifications();
        }

        static boolean hasPromotableCharacteristics(Notification notification) {
            return notification.hasPromotableCharacteristics();
        }

        static boolean isPostedNotificationPromoted(NotificationManager manager,
                int notificationId) {
            try {
                for (StatusBarNotification active : manager.getActiveNotifications()) {
                    if (active.getId() == notificationId) {
                        return (active.getNotification().flags
                                & Notification.FLAG_PROMOTED_ONGOING) != 0;
                    }
                }
            } catch (RuntimeException exception) {
                Log.w(TAG, "Could not read live monitor promotion state", exception);
            }
            return false;
        }

        static void logPostedPromotionState(NotificationManager manager, int notificationId) {
            try {
                for (StatusBarNotification active : manager.getActiveNotifications()) {
                    if (active.getId() != notificationId) {
                        continue;
                    }
                    Notification posted = active.getNotification();
                    boolean promoted = (posted.flags & Notification.FLAG_PROMOTED_ONGOING) != 0;
                    Log.i(TAG, "Posted live monitor state: promotedFlag=" + promoted
                            + " flags=0x" + Integer.toHexString(posted.flags)
                            + " requestExtra="
                            + posted.extras.getBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, false));
                    return;
                }
                Log.i(TAG, "Posted live monitor state: notification not found in active list");
            } catch (RuntimeException exception) {
                Log.w(TAG, "Could not read posted live monitor promotion state", exception);
            }
        }
    }
}
