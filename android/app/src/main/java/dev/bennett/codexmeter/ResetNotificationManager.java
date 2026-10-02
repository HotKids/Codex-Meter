package dev.bennett.codexmeter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Posts and deduplicates Codex usage, reset-time, and reset-credit notifications. */
public final class ResetNotificationManager {
    private static final String CHANNEL_ALARM = "codex_reset_alarm";
    private static final String CHANNEL_NOTIFY = "codex_reset_notify";
    private static final String CHANNEL_SILENT = "codex_reset_silent";
    private static final String PREFS = "codex_meter_notification_state_v1";
    private static final String KEY_FIVE_HOUR_WINDOW = "low_five_hour_window";
    private static final String KEY_WEEKLY_WINDOW = "low_weekly_window";
    private static final String KEY_MONTHLY_WINDOW = "low_monthly_window";
    private static final String KEY_CREDIT_COUNT = "known_reset_credit_count";
    private static final String KEY_CREDIT_EXPIRY_ANNOUNCED =
            "reset_credit_expiry_announced";
    private static final String KEY_USER_RESET_FIVE_HOUR_UNTIL = "user_reset_five_hour_until";
    private static final String KEY_USER_RESET_WEEKLY_UNTIL = "user_reset_weekly_until";
    private static final String KEY_USER_RESET_MONTHLY_UNTIL = "user_reset_monthly_until";
    private static final long UNKNOWN_USER_RESET_SUPPRESSION_MS = 15 * 60 * 1000L;
    private static final int NOTIFICATION_TEST = 74400;
    private static final int NOTIFICATION_RESET_FIVE_HOUR = 74405;
    private static final int NOTIFICATION_RESET_WEEKLY = 74407;
    private static final int NOTIFICATION_RESET_MONTHLY = 74408;
    private static final int NOTIFICATION_LOW_FIVE_HOUR = 74505;
    private static final int NOTIFICATION_LOW_WEEKLY = 74507;
    private static final int NOTIFICATION_LOW_MONTHLY = 74508;
    private static final int NOTIFICATION_NEW_CREDIT = 74509;
    private static final int NOTIFICATION_CREDIT_EXPIRY_BASE = 74600;
    private static final int NOTIFICATION_REFILL_FIVE_HOUR = 74511;
    private static final int NOTIFICATION_REFILL_WEEKLY = 74512;
    private static final int NOTIFICATION_REFILL_BOTH = 74513;
    private static final int NOTIFICATION_REFILL_MONTHLY = 74514;
    private static final Object EXPIRY_STATE_LOCK = new Object();

    private ResetNotificationManager() {
    }

    public static void onUsageUpdated(Context context, UsageSnapshot snapshot) {
        onUsageUpdated(context, null, snapshot);
    }

    public static void onUsageUpdated(Context context, UsageSnapshot previous,
            UsageSnapshot snapshot) {
        if (context == null || snapshot == null) return;
        int unexpectedRefills = suppressUserResetRefills(context,
                CelebrationDetector.detectUnexpectedRefills(previous, snapshot),
                snapshot.fetchedAtMillis);
        if (!ResetAlertPreferences.enabled(context)) return;
        String metric = ResetAlertPreferences.getMetric(context);
        if (!ResetAlertPreferences.METRIC_WEEKLY.equals(metric)) {
            notifyLowWindow(context, snapshot.fiveHour, snapshot.fetchedAtMillis,
                    AppText.get(R.string.five_hour), KEY_FIVE_HOUR_WINDOW, NOTIFICATION_LOW_FIVE_HOUR);
        }
        if (!ResetAlertPreferences.METRIC_FIVE_HOUR.equals(metric)) {
            notifyLowWindow(context, snapshot.weekly, snapshot.fetchedAtMillis,
                    AppText.get(R.string.weekly), KEY_WEEKLY_WINDOW, NOTIFICATION_LOW_WEEKLY);
            // The monthly free-tier window rides on the same long-cadence metric as weekly.
            notifyLowWindow(context, snapshot.monthly, snapshot.fetchedAtMillis,
                    AppText.get(R.string.phone_monthly_d31ed), KEY_MONTHLY_WINDOW, NOTIFICATION_LOW_MONTHLY);
        }
        if (ResetAlertPreferences.unexpectedRefillsEnabled(context)) {
            notifyUnexpectedRefill(context, unexpectedRefills);
        }
    }

    public static void onResetCreditsUpdated(Context context, ResetCreditsSnapshot snapshot) {
        if (context == null || snapshot == null) return;
        onResetCreditCountUpdated(context, snapshot.availableCount);
        pruneResetCreditExpiryHistory(context, snapshot);
        try {
            ResetCreditExpiryScheduler.scheduleFromSnapshot(context, snapshot);
        } catch (RuntimeException ignored) {
        }
    }

    static void onResetCreditSummaryUpdated(Context context, int availableCount) {
        if (context == null || availableCount < 0) return;
        onResetCreditCountUpdated(context, availableCount);
    }

    private static void onResetCreditCountUpdated(Context context, int current) {
        SharedPreferences state = state(context);
        if (!state.contains(KEY_CREDIT_COUNT)) {
            state.edit().putInt(KEY_CREDIT_COUNT, current).apply();
            return;
        }
        int previous = state.getInt(KEY_CREDIT_COUNT, current);
        int added = CelebrationDetector.resetCreditsAdded(previous, current);
        if (!ResetAlertPreferences.enabled(context)
                || !ResetAlertPreferences.resetCreditIncreasesEnabled(context)
                || added <= 0) {
            state.edit().putInt(KEY_CREDIT_COUNT, current).apply();
            return;
        }
        String text = added == 1
                ? AppText.get(R.string.phone_one_codex_reset_credit_was_added_you_now_have_05a3e) + current + "."
                : added + AppText.get(R.string.phone_codex_reset_credits_were_added_you_now_have_188ac) + current + ".";
        if (post(context, NOTIFICATION_NEW_CREDIT,
                added == 1 ? AppText.get(R.string.phone_codex_reset_credit_added_6f383) : AppText.get(R.string.phone_codex_reset_credits_added_7a30f), text,
                NOTIFICATION_NEW_CREDIT)) {
            state.edit().putInt(KEY_CREDIT_COUNT, current).apply();
        }
    }

    /**
     * Marks cached non-full windows as user-reset so delayed propagation cannot be mistaken for
     * an external refill on a later refresh.
     */
    public static void markUserReset(Context context, UsageSnapshot snapshot) {
        if (context == null || snapshot == null) return;
        long now = System.currentTimeMillis();
        SharedPreferences.Editor editor = state(context).edit();
        markUserResetWindow(editor, KEY_USER_RESET_FIVE_HOUR_UNTIL,
                snapshot, snapshot.fiveHour, now);
        markUserResetWindow(editor, KEY_USER_RESET_WEEKLY_UNTIL,
                snapshot, snapshot.weekly, now);
        markUserResetWindow(editor, KEY_USER_RESET_MONTHLY_UNTIL,
                snapshot, snapshot.monthly, now);
        editor.apply();
    }

    public static void showResetNotification(Context context, String metric) {
        String label;
        int id;
        if (ResetAlertPreferences.METRIC_WEEKLY.equals(metric)) {
            label = AppText.get(R.string.weekly);
            id = NOTIFICATION_RESET_WEEKLY;
        } else if ("monthly".equals(metric)) {
            label = AppText.get(R.string.phone_monthly_d31ed);
            id = NOTIFICATION_RESET_MONTHLY;
        } else {
            label = AppText.get(R.string.five_hour);
            id = NOTIFICATION_RESET_FIVE_HOUR;
        }
        post(context, id, "Codex " + label + AppText.get(R.string.phone_usage_reset_f467a),
                AppText.get(R.string.phone_your_1cd4b) + label + AppText.get(R.string.phone_allowance_should_be_available_again_refreshing_u_d42a1),
                id);
    }

    public static boolean showResetCreditExpiryNotification(Context context, String creditId,
            long expiresAtMillis, long leadTimeMillis) {
        if (context == null || expiresAtMillis <= System.currentTimeMillis()
                || !ResetAlertPreferences.enabled(context)
                || !ResetAlertPreferences.resetCreditExpiryEnabled(context)) {
            return false;
        }
        String token = ResetCreditExpiryReminder.token(creditId, expiresAtMillis,
                leadTimeMillis);
        synchronized (EXPIRY_STATE_LOCK) {
            if (isResetCreditExpiryReminderAnnouncedLocked(context, token)) return false;
            int notificationId = notificationIdForCredit(creditId, token);
            long now = System.currentTimeMillis();
            String text = AppText.get(R.string.phone_one_reset_credit_expires_e579d)
                    + UsageFormat.absolute(context, expiresAtMillis, now) + " ("
                    + UsageFormat.relative(expiresAtMillis, now)
                    + AppText.get(R.string.phone_use_it_before_it_expires_4da5f);
            if (!postResetCreditExpiry(context, notificationId,
                    AppText.get(R.string.phone_codex_reset_credit_expires_soon_31c1b), text)) {
                return false;
            }
            Set<String> announced = new HashSet<>(state(context).getStringSet(
                    KEY_CREDIT_EXPIRY_ANNOUNCED, new HashSet<>()));
            announced.add(token);
            state(context).edit().putStringSet(
                    KEY_CREDIT_EXPIRY_ANNOUNCED, announced).apply();
            return true;
        }
    }

    static boolean isResetCreditExpiryReminderAnnounced(Context context, String token) {
        if (context == null || token == null) return false;
        synchronized (EXPIRY_STATE_LOCK) {
            return isResetCreditExpiryReminderAnnouncedLocked(context, token);
        }
    }

    public static void onResetCreditExpirySettingsChanged(Context context,
            ResetCreditsSnapshot snapshot) {
        if (context == null) return;
        if (!ResetAlertPreferences.resetCreditExpiryEnabled(context)) {
            clearResetCreditExpiryReminderHistory(context);
        } else if (snapshot != null) {
            pruneResetCreditExpiryHistory(context, snapshot);
        }
        ResetCreditExpiryScheduler.scheduleFromSnapshot(context, snapshot);
    }

    public static void dismissResetCreditExpiryNotification(Context context,
            int notificationId) {
        NotificationManager notificationManager = context == null ? null : manager(context);
        if (notificationManager != null && notificationId >= NOTIFICATION_CREDIT_EXPIRY_BASE) {
            notificationManager.cancel(notificationId);
        }
    }

    public static boolean sendTestNotification(Context context) {
        if (context == null || !ResetAlertPreferences.enabled(context)) {
            return false;
        }
        return post(context, NOTIFICATION_TEST, AppText.get(R.string.phone_codex_meter_notifications_are_working_3b46e),
                AppText.get(R.string.phone_low_usage_scheduled_resets_surprise_refills_and_93b24),
                NOTIFICATION_TEST);
    }

    public static void ensureChannel(Context context) {
        if (context == null) return;
        NotificationManager manager = manager(context);
        if (manager != null) createChannel(manager, ResetAlertPreferences.getStyle(context));
    }

    public static void clearState(Context context) {
        if (context == null) return;
        synchronized (EXPIRY_STATE_LOCK) {
            state(context).edit().clear().apply();
        }
    }

    public static void clearNotificationHistory(Context context) {
        if (context == null) return;
        state(context).edit()
                .remove(KEY_FIVE_HOUR_WINDOW)
                .remove(KEY_WEEKLY_WINDOW)
                .remove(KEY_MONTHLY_WINDOW)
                .remove(KEY_CREDIT_COUNT)
                .apply();
        clearResetCreditExpiryReminderHistory(context);
    }

    private static void pruneResetCreditExpiryHistory(Context context,
            ResetCreditsSnapshot snapshot) {
        Set<String> active = new HashSet<>();
        for (ResetCreditExpiryReminder reminder : ResetCreditExpiryReminder.plan(
                snapshot.credits, ResetAlertPreferences.getResetCreditExpiryLeadTimes(context),
                System.currentTimeMillis())) {
            active.add(reminder.token());
        }
        synchronized (EXPIRY_STATE_LOCK) {
            Set<String> announced = new HashSet<>(state(context).getStringSet(
                    KEY_CREDIT_EXPIRY_ANNOUNCED, new HashSet<>()));
            if (announced.retainAll(active)) {
                state(context).edit().putStringSet(
                        KEY_CREDIT_EXPIRY_ANNOUNCED, announced).apply();
            }
        }
    }

    private static void clearResetCreditExpiryReminderHistory(Context context) {
        synchronized (EXPIRY_STATE_LOCK) {
            state(context).edit().remove(KEY_CREDIT_EXPIRY_ANNOUNCED).apply();
        }
    }

    private static boolean isResetCreditExpiryReminderAnnouncedLocked(
            Context context, String token) {
        return state(context).getStringSet(
                KEY_CREDIT_EXPIRY_ANNOUNCED, new HashSet<>()).contains(token);
    }

    private static int notificationIdForCredit(String creditId, String fallbackToken) {
        return NOTIFICATION_CREDIT_EXPIRY_BASE
                + Math.floorMod((creditId == null || creditId.isEmpty()
                ? fallbackToken : creditId).hashCode(), 1000);
    }

    private static void notifyLowWindow(Context context, UsageWindow window, long fetchedAt,
            String label, String stateKey, int notificationId) {
        if (window == null || window.remainingPercent() > ResetAlertPreferences.getThreshold(context)) return;
        long windowId = window.effectiveResetAtMillis(fetchedAt);
        if (windowId <= 0L) return;
        SharedPreferences state = state(context);
        long previousWindowId = state.getLong(stateKey, 0L);
        if (!UsageWindow.shouldAnnounceLowUsage(previousWindowId, windowId, window.windowSeconds)) {
            // Keep the stored reset aligned with API drift so slow skew cannot re-arm the alert.
            if (previousWindowId != windowId) {
                state.edit().putLong(stateKey, windowId).apply();
            }
            return;
        }
        int remaining = window.remainingPercent();
        if (post(context, notificationId, label + AppText.get(R.string.phone_codex_usage_is_low_73247),
                remaining + AppText.get(R.string.phone_remaining_in_the_current_d79ae)
                        + label.toLowerCase(Locale.ROOT) + AppText.get(R.string.phone_window_4bd49),
                notificationId, true)) {
            state.edit().putLong(stateKey, windowId).apply();
        }
    }

    private static void notifyUnexpectedRefill(Context context, int refills) {
        if (refills == 0) return;
        boolean fiveHour = (refills & CelebrationDetector.FIVE_HOUR) != 0;
        boolean weekly = (refills & CelebrationDetector.WEEKLY) != 0;
        boolean monthly = (refills & CelebrationDetector.MONTHLY) != 0;
        if ((fiveHour && weekly) || (fiveHour && monthly)) {
            post(context, NOTIFICATION_REFILL_BOTH, AppText.get(R.string.phone_surprise_codex_refill_239e7),
                    AppText.get(R.string.phone_your_codex_allowances_jumped_to_100_before_their_fd531),
                    NOTIFICATION_REFILL_BOTH);
        } else if (weekly) {
            post(context, NOTIFICATION_REFILL_WEEKLY, AppText.get(R.string.phone_surprise_weekly_codex_refill_7ccd0),
                    AppText.get(R.string.phone_your_weekly_allowance_jumped_to_100_before_its_s_2c694),
                    NOTIFICATION_REFILL_WEEKLY);
        } else if (monthly) {
            post(context, NOTIFICATION_REFILL_MONTHLY, AppText.get(R.string.phone_surprise_monthly_codex_refill_fefa7),
                    AppText.get(R.string.phone_your_monthly_allowance_jumped_to_100_before_its_907c9),
                    NOTIFICATION_REFILL_MONTHLY);
        } else {
            post(context, NOTIFICATION_REFILL_FIVE_HOUR, AppText.get(R.string.phone_surprise_5_hour_codex_refill_e2975),
                    AppText.get(R.string.phone_your_5_hour_allowance_jumped_to_100_before_its_s_3255f),
                    NOTIFICATION_REFILL_FIVE_HOUR);
        }
    }

    private static int suppressUserResetRefills(Context context, int refills, long observedAt) {
        SharedPreferences preferences = state(context);
        long fiveHourUntil = preferences.getLong(KEY_USER_RESET_FIVE_HOUR_UNTIL, 0L);
        long weeklyUntil = preferences.getLong(KEY_USER_RESET_WEEKLY_UNTIL, 0L);
        long monthlyUntil = preferences.getLong(KEY_USER_RESET_MONTHLY_UNTIL, 0L);
        if (fiveHourUntil <= 0L && weeklyUntil <= 0L && monthlyUntil <= 0L) return refills;
        int filtered = CelebrationDetector.withoutUserResetRefills(refills, observedAt,
                fiveHourUntil, weeklyUntil, monthlyUntil);
        boolean clearFiveHour = shouldClearSuppression(CelebrationDetector.FIVE_HOUR,
                refills, filtered, observedAt, fiveHourUntil);
        boolean clearWeekly = shouldClearSuppression(CelebrationDetector.WEEKLY,
                refills, filtered, observedAt, weeklyUntil);
        boolean clearMonthly = shouldClearSuppression(CelebrationDetector.MONTHLY,
                refills, filtered, observedAt, monthlyUntil);
        if (clearFiveHour || clearWeekly || clearMonthly) {
            SharedPreferences.Editor editor = preferences.edit();
            if (clearFiveHour) editor.remove(KEY_USER_RESET_FIVE_HOUR_UNTIL);
            if (clearWeekly) editor.remove(KEY_USER_RESET_WEEKLY_UNTIL);
            if (clearMonthly) editor.remove(KEY_USER_RESET_MONTHLY_UNTIL);
            editor.apply();
        }
        return filtered;
    }

    private static boolean shouldClearSuppression(int window, int before, int after,
            long observedAt, long suppressUntil) {
        return suppressUntil > 0L && (observedAt >= suppressUntil
                || ((before & window) != 0 && (after & window) == 0));
    }

    private static void markUserResetWindow(SharedPreferences.Editor editor, String key,
            UsageSnapshot snapshot, UsageWindow window, long now) {
        if (window == null || window.usedPercent <= 0) {
            editor.remove(key);
            return;
        }
        long suppressUntil = CelebrationDetector.expectedResetMillis(snapshot, window);
        if (suppressUntil <= now) {
            suppressUntil = now + UNKNOWN_USER_RESET_SUPPRESSION_MS;
        }
        editor.putLong(key, suppressUntil);
    }

    private static boolean post(Context context, int id, String title, String text, int requestCode) {
        return post(context, id, title, text, requestCode, false);
    }

    private static boolean post(Context context, int id, String title, String text, int requestCode,
            boolean onlyAlertOnce) {
        NotificationManager manager = manager(context);
        if (manager == null) return false;
        String channel = createChannel(manager, ResetAlertPreferences.getStyle(context));
        if (!canPost(context, manager, channel)) return false;
        PendingIntent contentIntent = PendingIntent.getActivity(context, requestCode,
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_oui_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(onlyAlertOnce)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(true)
                .build();
        manager.notify(id, notification);
        return true;
    }

    private static boolean postResetCreditExpiry(Context context, int id, String title,
            String text) {
        NotificationManager manager = manager(context);
        if (manager == null) return false;
        String channel = createChannel(manager, ResetAlertPreferences.getStyle(context));
        if (!canPost(context, manager, channel)) return false;
        Intent detailsIntent = new Intent(context, ResetCreditActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent details = PendingIntent.getActivity(context, id, detailsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent useIntent = new Intent(context, ResetCreditActivity.class)
                .setAction("me.pipi.usage.action.USE_RESET_FROM_NOTIFICATION")
                .putExtra(AppConstants.EXTRA_PROMPT_USE_RESET, true)
                .putExtra(AppConstants.EXTRA_NOTIFICATION_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent useReset = PendingIntent.getActivity(context, id + 10_000, useIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_reset_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(details)
                .addAction(new Notification.Action.Builder(R.drawable.ic_reset_notification,
                        AppText.get(R.string.phone_use_reset_c3295), useReset).build())
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(true)
                .build();
        manager.notify(id, notification);
        return true;
    }

    private static boolean canPost(Context context, NotificationManager manager,
            String channelId) {
        if (!manager.areNotificationsEnabled()
                || (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED)) {
            return false;
        }
        NotificationChannel channel = manager.getNotificationChannel(channelId);
        return channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    private static NotificationManager manager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static SharedPreferences state(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String createChannel(NotificationManager manager, String style) {
        if (ResetAlertPreferences.STYLE_SILENT.equals(style)) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_SILENT,
                    AppText.get(R.string.phone_codex_usage_alerts_fe469), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(AppText.get(R.string.phone_low_usage_scheduled_resets_surprise_refills_and_06d96));
            channel.setSound(null, null);
            channel.enableVibration(false);
            manager.createNotificationChannel(channel);
            return CHANNEL_SILENT;
        }
        if (ResetAlertPreferences.STYLE_ALARM.equals(style)) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ALARM,
                    AppText.get(R.string.phone_codex_usage_alarms_e2aca), NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(AppText.get(R.string.phone_low_usage_scheduled_resets_surprise_refills_and_06d96));
            channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
            return CHANNEL_ALARM;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_NOTIFY,
                AppText.get(R.string.phone_codex_usage_alerts_fe469), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(AppText.get(R.string.phone_low_usage_scheduled_resets_surprise_refills_and_06d96));
        channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        manager.createNotificationChannel(channel);
        return CHANNEL_NOTIFY;
    }
}
