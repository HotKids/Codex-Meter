package dev.bennett.codexmeter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OAuthService extends Service {
    public static final String ACTION_START = "dev.bennett.codexmeter.oauth.START";
    public static final String ACTION_CANCEL = "dev.bennett.codexmeter.oauth.CANCEL";
    public static final String ACTION_CANCEL_SILENT = "dev.bennett.codexmeter.oauth.CANCEL_SILENT";
    private static final String CHANNEL_ID = "oauth_sign_in";
    private static final int NOTIFICATION_ID = 7301;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ServerSocket serverSocket;
    private volatile boolean cancelled;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        DiagnosticLog.info(this, "auth", "oauth_service_command",
                "action", action == null ? "" : action);
        if (ACTION_CANCEL.equals(action) || ACTION_CANCEL_SILENT.equals(action)) {
            cancelFlow(AppText.get(R.string.phone_sign_in_cancelled_24d3d), ACTION_CANCEL.equals(action));
            return START_NOT_STICKY;
        }
        if (SecureTokenStore.isSignedIn(this)) {
            AppPreferences.setOAuthPending(this, false, "");
            broadcastResult(true, AppText.get(R.string.phone_already_signed_in_2c7ee));
            finishService();
            return START_NOT_STICKY;
        }
        if (running.compareAndSet(false, true)) {
            cancelled = false;
            startForegroundCompat(buildNotification(AppText.get(R.string.phone_preparing_secure_sign_in_78f0c), null));
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    runFlow();
                }
            });
        } else {
            String url = AppPreferences.getOAuthUrl(this);
            if (!url.isEmpty()) broadcastReady(url);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        closeServer();
        executor.shutdownNow();
        super.onDestroy();
    }

    private void runFlow() {
        Socket browser = null;
        boolean credentialsCommitted = false;
        long started = android.os.SystemClock.elapsedRealtime();
        DiagnosticLog.info(this, "auth", "oauth_flow_started");
        try {
            Pkce pkce = Pkce.generate();
            int port = bindServer();
            String redirectUri = "http://localhost:" + port + "/auth/callback";
            String authUrl = buildAuthorizeUrl(redirectUri, pkce);
            AppPreferences.setOAuthPending(this, true, authUrl);
            updateNotification(AppText.get(R.string.phone_complete_sign_in_in_your_browser_fb6f2), authUrl);
            broadcastReady(authUrl);

            while (!cancelled) {
                try {
                    browser = serverSocket.accept();
                } catch (SocketTimeoutException timeout) {
                    throw new Exception(AppText.get(R.string.phone_sign_in_timed_out_start_again_from_the_app_abf8e));
                }
                browser.setSoTimeout(30000);
                Callback callback = readCallback(browser);
                if (!"/auth/callback".equals(callback.path)) {
                    writeBrowser(browser, 404, AppText.get(R.string.phone_not_found_475c8), false);
                    closeQuietly(browser);
                    browser = null;
                    continue;
                }
                if (!secureEquals(pkce.state, callback.parameters.get("state"))) {
                    DiagnosticLog.warn(this, "auth", "oauth_callback_state_mismatch");
                    writeBrowser(browser, 400,
                            AppText.get(R.string.phone_the_sign_in_state_did_not_match_return_to_codex_39f3f), false);
                    closeQuietly(browser);
                    browser = null;
                    continue;
                }
                String error = callback.parameters.get("error");
                if (error != null && !error.isEmpty()) {
                    String description = callback.parameters.get("error_description");
                    String message = description == null || description.isEmpty() ? error : description;
                    writeBrowser(browser, 400, message, false);
                    closeQuietly(browser);
                    browser = null;
                    throw new Exception(AppText.get(R.string.phone_sign_in_failed_77442) + message);
                }
                String code = callback.parameters.get("code");
                if (code == null || code.isEmpty()) {
                    writeBrowser(browser, 400,
                            AppText.get(R.string.phone_the_authorization_response_did_not_include_a_cod_c4d91), false);
                    closeQuietly(browser);
                    browser = null;
                    throw new Exception(AppText.get(R.string.phone_sign_in_failed_because_no_authorization_code_was_9ae3b));
                }

                updateNotification(AppText.get(R.string.phone_securing_your_chatgpt_session_5b6b3), null);
                AuthTokens tokens = OAuthClient.exchangeCode(this, code, redirectUri,
                        pkce.verifier);
                SecureTokenStore.save(this, tokens);
                credentialsCommitted = true;
                AppPreferences.setOAuthPending(this, false, "");

                // The browser callback is complete as soon as credentials are safely stored.
                // Usage retrieval and JobScheduler setup must never turn a successful OAuth
                // exchange into a misleading browser error page.
                try {
                    writeBrowser(browser, 200,
                            AppText.get(R.string.phone_your_chatgpt_account_is_connected_returning_to_c_463a3), true);
                } catch (Exception ignored) {
                    // The user may have closed the browser after authorization. Authentication
                    // remains valid and the application still receives the result broadcast.
                }
                closeQuietly(browser);
                browser = null;
                broadcastResult(true, AppText.get(R.string.phone_signed_in_successfully_d4c69));

                updateNotification(AppText.get(R.string.phone_loading_codex_usage_0a807), null);
                performPostAuthenticationSetup();
                DiagnosticLog.info(this, "auth", "oauth_flow_succeeded",
                        "duration_ms", android.os.SystemClock.elapsedRealtime() - started);
                finishService();
                return;
            }
        } catch (Exception exception) {
            DiagnosticLog.error(this, "auth", "oauth_flow_failed", exception,
                    "credentials_committed", credentialsCommitted,
                    "duration_ms", android.os.SystemClock.elapsedRealtime() - started);
            if (credentialsCommitted) {
                // A post-commit failure is not an authentication failure. Preserve the session,
                // show a valid success state, and let manual refresh recover later.
                AppPreferences.setOAuthPending(this, false, "");
                AppPreferences.setLastError(this, cleanMessage(exception));
                broadcastResult(true, AppText.get(R.string.phone_signed_in_usage_can_be_refreshed_from_the_app_0fe32));
                safeWidgetUpdate();
                finishService();
                return;
            }
            if (browser != null) {
                try {
                    writeBrowser(browser, 500, cleanMessage(exception), false);
                } catch (Exception ignored) {
                    // The callback connection may already be gone.
                }
            }
            if (!cancelled) {
                String message = cleanMessage(exception);
                AppPreferences.setOAuthPending(this, false, "");
                broadcastResult(false, message);
            }
            finishService();
        } finally {
            closeQuietly(browser);
            closeServer();
        }
    }

    private void performPostAuthenticationSetup() {
        try {
            UsageSnapshot snapshot = UsageApi.refreshAndCache(this);
            RefreshScheduler.scheduleAtNextReset(this, snapshot);
        } catch (Exception refreshError) {
            AppPreferences.setLastError(this, cleanMessage(refreshError));
        }
        RefreshScheduler.schedulePeriodic(this);
        safeWidgetUpdate();
        broadcastUsageUpdated();
    }

    private int bindServer() throws Exception {
        Exception last = null;
        for (int port : AppConstants.OAUTH_PORTS) {
            try {
                ServerSocket candidate = new ServerSocket();
                candidate.setReuseAddress(true);
                candidate.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
                candidate.setSoTimeout(5 * 60 * 1000);
                serverSocket = candidate;
                return port;
            } catch (Exception exception) {
                last = exception;
            }
        }
        throw new Exception(AppText.get(R.string.phone_could_not_open_the_local_oauth_callback_port_145_c009d), last);
    }

    private static String buildAuthorizeUrl(String redirectUri, Pkce pkce) throws Exception {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        params.put("response_type", "code");
        params.put("client_id", AppConstants.OAUTH_CLIENT_ID);
        params.put("redirect_uri", redirectUri);
        params.put("scope", AppConstants.OAUTH_SCOPE);
        params.put("code_challenge", pkce.challenge);
        params.put("code_challenge_method", "S256");
        params.put("id_token_add_organizations", "true");
        params.put("codex_cli_simplified_flow", "true");
        params.put("state", pkce.state);
        params.put("originator", AppConstants.ORIGINATOR);
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (query.length() > 0) query.append('&');
            query.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            query.append('=');
            query.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return AppConstants.AUTHORIZE_URL + "?" + query;
    }

    private static Callback readCallback(Socket socket) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), StandardCharsets.US_ASCII));
        String requestLine = reader.readLine();
        if (requestLine == null) throw new Exception(AppText.get(R.string.phone_the_browser_callback_was_empty_5f9d1));
        String headerLine;
        while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
            // Consume the request headers before writing the response.
        }
        String[] parts = requestLine.split(" ");
        if (parts.length < 2 || !"GET".equals(parts[0])) {
            return new Callback("", Collections.emptyMap());
        }
        String target = parts[1];
        int queryIndex = target.indexOf('?');
        String path = queryIndex >= 0 ? target.substring(0, queryIndex) : target;
        String query = queryIndex >= 0 ? target.substring(queryIndex + 1) : "";
        Map<String, String> parameters = new HashMap<>();
        if (!query.isEmpty()) {
            for (String pair : query.split("&")) {
                int equals = pair.indexOf('=');
                String key = equals >= 0 ? pair.substring(0, equals) : pair;
                String value = equals >= 0 ? pair.substring(equals + 1) : "";
                parameters.put(URLDecoder.decode(key, "UTF-8"), URLDecoder.decode(value, "UTF-8"));
            }
        }
        return new Callback(path, parameters);
    }

    private static void writeBrowser(Socket socket, int status, String message, boolean success) throws Exception {
        String html = OAuthBrowserPage.render(message, success, AppConstants.APP_LINK,
                AppText.resources().getConfiguration().getLocales().get(0).toLanguageTag(),
                AppText.get(success ? R.string.oauth_connected : R.string.oauth_retry),
                AppText.get(success ? R.string.oauth_complete : R.string.oauth_attention),
                AppText.get(success ? R.string.oauth_open : R.string.oauth_back),
                AppText.get(success ? R.string.oauth_returning : R.string.oauth_restart));
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        // HTTP status text is protocol data, not localized UI copy.
        String reason = status >= 200 && status < 300 ? "OK" : "Error";
        ByteArrayOutputStream response = new ByteArrayOutputStream(body.length + 256);
        response.write(("HTTP/1.1 " + status + " " + reason + "\r\n").getBytes(StandardCharsets.US_ASCII));
        response.write("Content-Type: text/html; charset=utf-8\r\n".getBytes(StandardCharsets.US_ASCII));
        response.write(("Content-Length: " + body.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
        response.write("Cache-Control: no-store\r\n".getBytes(StandardCharsets.US_ASCII));
        response.write("Connection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        response.write(body);
        socket.getOutputStream().write(response.toByteArray());
        socket.getOutputStream().flush();
    }

    private void cancelFlow(String message, boolean broadcast) {
        cancelled = true;
        AppPreferences.setOAuthPending(this, false, "");
        closeServer();
        if (broadcast) {
            broadcastResult(false, message);
        }
        finishService();
    }

    private void finishService() {
        running.set(false);
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (RuntimeException ignored) {
            // The service may not have reached foreground state if startup was interrupted.
        }
        stopSelf();
    }

    private void closeServer() {
        ServerSocket socket = serverSocket;
        serverSocket = null;
        if (socket != null) {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private void safeWidgetUpdate() {
        try {
            WidgetRenderer.updateAll(this);
        } catch (RuntimeException exception) {
            AppPreferences.setLastError(this, "Widget update: " + cleanMessage(exception));
        }
    }

    private void broadcastReady(String authUrl) {
        Intent intent = new Intent(AppConstants.ACTION_OAUTH_READY)
                .setPackage(getPackageName())
                .putExtra(AppConstants.EXTRA_AUTH_URL, authUrl);
        sendBroadcast(intent, AppConstants.INTERNAL_PERMISSION);
    }

    private void broadcastResult(boolean success, String message) {
        Intent intent = new Intent(AppConstants.ACTION_OAUTH_RESULT)
                .setPackage(getPackageName())
                .putExtra(AppConstants.EXTRA_SUCCESS, success)
                .putExtra(AppConstants.EXTRA_MESSAGE, message);
        sendBroadcast(intent, AppConstants.INTERNAL_PERMISSION);
    }

    private void broadcastUsageUpdated() {
        sendBroadcast(new Intent(AppConstants.ACTION_USAGE_UPDATED).setPackage(getPackageName()),
                AppConstants.INTERNAL_PERMISSION);
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.oauth_channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.oauth_channel_description));
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text, String authUrl) {
        Intent openIntent;
        if (authUrl == null || authUrl.isEmpty()) {
            openIntent = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        } else {
            openIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
        }
        PendingIntent open = PendingIntent.getActivity(this, 7302, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent cancelIntent = new Intent(this, OAuthService.class).setAction(ACTION_CANCEL);
        PendingIntent cancel = PendingIntent.getService(this, 7303, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_oui_notification)
                .setContentTitle(AppText.get(R.string.phone_codex_meter_sign_in_2b155))
                .setContentText(text)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_oui_close), AppText.get(R.string.widget_config_cancel), cancel).build())
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private void updateNotification(String text, String authUrl) {
        startForegroundCompat(buildNotification(text, authUrl));
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private static boolean secureEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String cleanMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) return AppText.get(R.string.phone_sign_in_could_not_be_completed_b22cf);
        return message.length() > 240 ? message.substring(0, 240) : message;
    }

    private static String cleanMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) return exception.getClass().getSimpleName();
        return message.length() > 200 ? message.substring(0, 200) : message;
    }

    private static final class Callback {
        final String path;
        final Map<String, String> parameters;

        Callback(String path, Map<String, String> parameters) {
            this.path = path;
            this.parameters = parameters;
        }
    }
}
