package dev.bennett.codexmeter.wear;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import dev.bennett.codexmeter.AppPreferences;
import dev.bennett.codexmeter.DiagnosticLog;
import dev.bennett.codexmeter.NowBarManager;
import dev.bennett.codexmeter.NowBarPreferences;
import dev.bennett.codexmeter.RefreshScheduler;
import dev.bennett.codexmeter.SecureTokenStore;
import dev.bennett.codexmeter.UsageSnapshot;
import dev.bennett.codexmeter.UsagePacePreferences;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Phone side of the Wear Data Layer protocol: publishes usage, settings, monitor state, and
 * sync status as urgent data items, and applies newer settings edited on the watch.
 */
public final class PhoneWearSync {
    private static final String KEY_LAST_APPLIED_SETTINGS_AT = "last_applied_settings_at";
    private static final String KEY_LOCAL_SETTINGS_AT = "local_settings_at";
    private static final String KEY_PAYLOAD = "payload";
    private static final String KEY_SENT_AT = "sent_at_millis";
    private static final String PREFS = "codex_meter_wear_sync_v1";
    private static final ThreadLocal<Boolean> SUPPRESS_SETTINGS_PUSH = new ThreadLocal<>();
    private static final String TAG = "CodexWearSync";

    private PhoneWearSync() {
    }

    public static void pushUsage(Context context, UsageSnapshot snapshot) {
        if (context == null) return;
        long now = System.currentTimeMillis();
        pushJson(context, WearSyncPaths.PATH_USAGE,
                new WearUsageState(snapshot, now, WearSettingsState.SOURCE_PHONE,
                        SecureTokenStore.isSignedIn(context))::toJson);
        pushStatus(context, false, "");
    }

    public static void pushSettings(Context context) {
        if (context == null || isSettingsPushSuppressed()) return;
        Context app = context.getApplicationContext();
        long now = System.currentTimeMillis();
        prefs(app).edit().putLong(KEY_LOCAL_SETTINGS_AT, now).apply();
        pushJson(app, WearSyncPaths.PATH_SETTINGS, settingsState(app, now)::toJson);
    }

    public static void pushMonitorState(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        long now = System.currentTimeMillis();
        boolean active = NowBarManager.isActive(app);
        pushJson(app, WearSyncPaths.PATH_MONITOR, new WearMonitorState(
                active,
                NowBarManager.isPreview(app),
                active ? NowBarManager.activeUntil(app) : 0L,
                active ? NowBarManager.activeFocusMetric(app) : null,
                active ? NowBarManager.postedDisplayMode(app)
                        : NowBarPreferences.getDisplayMode(app),
                now)::toJson);
    }

    public static void pushStatus(Context context, boolean refreshInProgress, String error) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        UsageSnapshot snapshot = AppPreferences.loadSnapshot(app);
        String visibleError = error == null ? AppPreferences.getLastError(app) : error;
        pushJson(app, WearSyncPaths.PATH_STATUS, new WearSyncStatus(
                SecureTokenStore.isSignedIn(app),
                refreshInProgress,
                snapshot == null ? 0L : snapshot.fetchedAtMillis,
                visibleError,
                appVersion(app),
                System.currentTimeMillis())::toJson);
    }

    public static void pushAll(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        pushUsage(app, AppPreferences.loadSnapshot(app));
        pushSettings(app);
        pushMonitorState(app);
        pushStatus(app, false, AppPreferences.getLastError(app));
    }

    /**
     * Applies settings edited on the watch when they are newer than the phone's own edits.
     * Settings pushes triggered while applying them are suppressed so nothing echoes back.
     */
    public static boolean applyRemoteSettings(Context context, WearSettingsState remote) {
        if (context == null || remote == null
                || WearSettingsState.SOURCE_PHONE.equals(remote.sourceNode)) {
            return false;
        }
        Context app = context.getApplicationContext();
        SharedPreferences prefs = prefs(app);
        long localStamp = Math.max(prefs.getLong(KEY_LOCAL_SETTINGS_AT, 0L),
                prefs.getLong(KEY_LAST_APPLIED_SETTINGS_AT, 0L));
        if (remote.updatedAtMillis <= localStamp) {
            return false;
        }

        boolean wasActive = NowBarManager.isActive(app);
        SUPPRESS_SETTINGS_PUSH.set(Boolean.TRUE);
        try {
            NowBarPreferences.setDisplayMode(app, remote.displayMode);
            NowBarPreferences.setPercentMode(app, remote.percentMode);
            NowBarPreferences.save(app, remote.autoStartEnabled, remote.metric, remote.threshold);
            NowBarPreferences.setAcceleratedStartEnabled(app, remote.acceleratedStartEnabled);
            UsagePacePreferences.setEnabled(app, remote.usagePaceEnabled);
            UsagePacePreferences.setSensitivity(app, remote.usagePaceSensitivity);
            // Phone owns the refresh interval. Wear may echo a cached/default value before the
            // first phone→Wear settings item arrives; never let that clobber the phone schedule.
            if (remote.monitorActive != wasActive) {
                if (remote.monitorActive) {
                    NowBarManager.start(app);
                } else {
                    NowBarManager.stop(app, true);
                }
            } else {
                pushMonitorState(app);
            }
        } finally {
            SUPPRESS_SETTINGS_PUSH.remove();
        }
        prefs.edit()
                .putLong(KEY_LAST_APPLIED_SETTINGS_AT, remote.updatedAtMillis)
                .putLong(KEY_LOCAL_SETTINGS_AT, remote.updatedAtMillis)
                .apply();
        DiagnosticLog.info(app, "wear", "remote_settings_applied",
                "monitor_active", remote.monitorActive,
                "source", remote.sourceNode);
        return true;
    }

    static void sendMessageToWear(Context context, String path) {
        if (context == null || path == null || path.isEmpty()) return;
        Context app = context.getApplicationContext();
        Wearable.getNodeClient(app).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    DiagnosticLog.info(app, "wear", "nodes_resolved",
                            "path", path, "node_count", nodes.size());
                    sendMessageToNodes(app, nodes, path);
                })
                .addOnFailureListener(error -> {
                    DiagnosticLog.error(app, "wear", "node_resolution_failed", error,
                            "path", path);
                    Log.w(TAG, "Could not resolve Wear nodes for " + path, error);
                });
    }

    private static void sendMessageToNodes(Context context, List<Node> nodes, String path) {
        byte[] auth = PhoneWearTrust.messageAuthPayload(context);
        for (Node node : nodes) {
            Wearable.getMessageClient(context)
                    .sendMessage(node.getId(), path, auth)
                    .addOnSuccessListener(result -> DiagnosticLog.info(context, "wear",
                            "message_sent", "path", path))
                    .addOnFailureListener(error -> {
                        DiagnosticLog.error(context, "wear", "message_send_failed", error,
                                "path", path);
                        Log.w(TAG, "Could not send Wear message " + path, error);
                    });
        }
    }

    private static WearSettingsState settingsState(Context context, long updatedAtMillis) {
        return new WearSettingsState(
                NowBarPreferences.getDisplayMode(context),
                NowBarPreferences.getPercentMode(context),
                NowBarPreferences.isAutoStartEnabled(context),
                NowBarPreferences.getMetric(context),
                NowBarPreferences.getThreshold(context),
                NowBarManager.isActive(context),
                RefreshScheduler.effectiveRefreshMinutes(context),
                updatedAtMillis,
                WearSettingsState.SOURCE_PHONE,
                context.getPackageName(),
                UsagePacePreferences.isEnabled(context),
                UsagePacePreferences.getSensitivity(context),
                NowBarPreferences.isAcceleratedStartEnabled(context));
    }

    private static void pushJson(Context context, String path, JsonState state) {
        try {
            String json = state.toJson().toString();
            PutDataMapRequest map = PutDataMapRequest.create(path);
            map.getDataMap().putString(KEY_PAYLOAD, json);
            map.getDataMap().putLong(KEY_SENT_AT, System.currentTimeMillis());
            PutDataRequest request = map.asPutDataRequest().setUrgent();
            Wearable.getDataClient(context.getApplicationContext())
                    .putDataItem(request)
                    .addOnSuccessListener(item -> DiagnosticLog.info(context, "wear",
                            "data_pushed", "path", path,
                            "payload_bytes", json.getBytes(StandardCharsets.UTF_8).length))
                    .addOnFailureListener(error -> {
                        DiagnosticLog.error(context, "wear", "data_push_failed", error,
                                "path", path);
                        Log.w(TAG, "Could not push Wear data " + path, error);
                    });
        } catch (Exception exception) {
            DiagnosticLog.error(context, "wear", "data_encode_failed", exception,
                    "path", path);
            Log.w(TAG, "Could not encode Wear data " + path, exception);
        }
    }

    /** Reads the JSON payload of a data item, falling back to its raw bytes. */
    static String payloadString(DataItem item) {
        try {
            return DataMapItem.fromDataItem(item)
                    .getDataMap()
                    .getString(KEY_PAYLOAD);
        } catch (RuntimeException exception) {
            byte[] bytes = item == null ? null : item.getData();
            return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static boolean isSettingsPushSuppressed() {
        return Boolean.TRUE.equals(SUPPRESS_SETTINGS_PUSH.get());
    }

    private static String appVersion(Context context) {
        try {
            String value = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
            return value == null ? "" : value;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Encodes a Wear state on demand so encoding failures are logged instead of thrown. */
    private interface JsonState {
        JSONObject toJson() throws JSONException;
    }
}
