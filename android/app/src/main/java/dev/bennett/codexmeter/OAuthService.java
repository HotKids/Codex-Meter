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
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the browser OAuth sign-in as a foreground service: it listens on a loopback port for
 * the redirect, exchanges the code, stores the credentials, and loads the first usage.
 */
public final class OAuthService extends Service {
    public static final String ACTION_START = "dev.bennett.codexmeter.oauth.START";
    public static final String ACTION_CANCEL = "dev.bennett.codexmeter.oauth.CANCEL";
    public static final String ACTION_CANCEL_SILENT = "dev.bennett.codexmeter.oauth.CANCEL_SILENT";
    private static final String CHANNEL_ID = "oauth_sign_in";
    private static final int NOTIFICATION_ID = 7301;
    private static final int REQUEST_OPEN = 7302;
    private static final int REQUEST_CANCEL = 7303;
    private static final String CALLBACK_PATH = "/auth/callback";
    private static final String LOOPBACK_ADDRESS = "127.0.0.1";
    private static final int CALLBACK_WAIT_TIMEOUT_MS = (int) TimeUnit.MINUTES.toMillis(5);
    private static final int BROWSER_READ_TIMEOUT_MS = (int) TimeUnit.SECONDS.toMillis(30);
    private static final int MAX_ERROR_LENGTH = 240;
    private static final int MAX_WIDGET_ERROR_LENGTH = 200;

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
            cancelFlow("Sign-in cancelled.", ACTION_CANCEL.equals(action));
            return START_NOT_STICKY;
        }
        if (SecureTokenStore.isSignedIn(this)) {
            AppPreferences.setOAuthPending(this, false, "");
            broadcastResult(true, "Already signed in.");
            finishService();
            return START_NOT_STICKY;
        }
        if (running.compareAndSet(false, true)) {
            cancelled = false;
            startForegroundCompat(buildNotification("Preparing secure sign-in…", null));
            executor.execute(this::runFlow);
        } else {
            // A flow is already waiting; hand its authorization URL to the new caller.
            String url = AppPreferences.getOAuthUrl(this);
            if (!url.isEmpty()) {
                broadcastReady(url);
            }
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
        long started = SystemClock.elapsedRealtime();
        DiagnosticLog.info(this, "auth", "oauth_flow_started");
        try {
            Pkce pkce = Pkce.generate();
            int port = bindServer();
            String redirectUri = "http://localhost:" + port + CALLBACK_PATH;
            String authUrl = buildAuthorizeUrl(redirectUri, pkce);
            AppPreferences.setOAuthPending(this, true, authUrl);
            updateNotification("Complete sign-in in your browser", authUrl);
            broadcastReady(authUrl);

            while (!cancelled) {
                try {
                    browser = serverSocket.accept();
                } catch (SocketTimeoutException timeout) {
                    throw new Exception("Sign-in timed out. Start again from the app.");
                }
                browser.setSoTimeout(BROWSER_READ_TIMEOUT_MS);
                Callback callback = readCallback(browser);
                if (!CALLBACK_PATH.equals(callback.path)) {
                    rejectRequest(browser, 404, "Not found");
                    browser = null;
                    continue;
                }
                if (!secureEquals(pkce.state, callback.parameters.get("state"))) {
                    DiagnosticLog.warn(this, "auth", "oauth_callback_state_mismatch");
                    rejectRequest(browser, 400, "The sign-in state did not match. "
                            + "Return to Codex Meter and try again.");
                    browser = null;
                    continue;
                }
                String error = callback.parameters.get("error");
                if (error != null && !error.isEmpty()) {
                    String description = callback.parameters.get("error_description");
                    String message = description == null || description.isEmpty()
                            ? error : description;
                    rejectRequest(browser, 400, message);
                    browser = null;
                    throw new Exception("Sign-in failed: " + message);
                }
                String code = callback.parameters.get("code");
                if (code == null || code.isEmpty()) {
                    rejectRequest(browser, 400,
                            "The authorization response did not include a code.");
                    browser = null;
                    throw new Exception(
                            "Sign-in failed because no authorization code was returned.");
                }

                updateNotification("Securing your ChatGPT session…", null);
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
                            "Your ChatGPT account is connected. Returning to Codex Meter…", true);
                } catch (Exception ignored) {
                    // The user may have closed the browser after authorization. Authentication
                    // remains valid and the application still receives the result broadcast.
                }
                closeQuietly(browser);
                browser = null;
                broadcastResult(true, "Signed in successfully.");

                updateNotification("Loading Codex usage…", null);
                performPostAuthenticationSetup();
                DiagnosticLog.info(this, "auth", "oauth_flow_succeeded",
                        "duration_ms", SystemClock.elapsedRealtime() - started);
                finishService();
                return;
            }
        } catch (Exception exception) {
            DiagnosticLog.error(this, "auth", "oauth_flow_failed", exception,
                    "credentials_committed", credentialsCommitted,
                    "duration_ms", SystemClock.elapsedRealtime() - started);
            if (credentialsCommitted) {
                // A post-commit failure is not an authentication failure. Preserve the session,
                // show a valid success state, and let manual refresh recover later.
                AppPreferences.setOAuthPending(this, false, "");
                AppPreferences.setLastError(this, cleanMessage(exception));
                broadcastResult(true, "Signed in. Usage can be refreshed from the app.");
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

    /** Binds the first free registered loopback port and returns it. */
    private int bindServer() throws Exception {
        Exception last = null;
        for (int port : AppConstants.OAUTH_PORTS) {
            try {
                ServerSocket candidate = new ServerSocket();
                candidate.setReuseAddress(true);
                InetAddress loopback = InetAddress.getByName(LOOPBACK_ADDRESS);
                candidate.bind(new InetSocketAddress(loopback, port));
                candidate.setSoTimeout(CALLBACK_WAIT_TIMEOUT_MS);
                serverSocket = candidate;
                return port;
            } catch (Exception exception) {
                last = exception;
            }
        }
        throw new Exception("Could not open the local OAuth callback port (1455 or 1457).", last);
    }

    private static String buildAuthorizeUrl(String redirectUri, Pkce pkce) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
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
        return AppConstants.AUTHORIZE_URL + "?" + OAuthClient.formEncode(params);
    }

    /** Reads the browser's request line and query; non-GET requests yield an empty path. */
    private static Callback readCallback(Socket socket) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), StandardCharsets.US_ASCII));
        String requestLine = reader.readLine();
        if (requestLine == null) {
            throw new Exception("The browser callback was empty.");
        }
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
        return new Callback(path, parseQuery(query));
    }

    private static Map<String, String> parseQuery(String query) throws Exception {
        Map<String, String> parameters = new HashMap<>();
        if (query.isEmpty()) {
            return parameters;
        }
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            String key = equals >= 0 ? pair.substring(0, equals) : pair;
            String value = equals >= 0 ? pair.substring(equals + 1) : "";
            parameters.put(URLDecoder.decode(key, "UTF-8"), URLDecoder.decode(value, "UTF-8"));
        }
        return parameters;
    }

    /** Answers a request that cannot complete sign-in with an error page and closes it. */
    private static void rejectRequest(Socket socket, int status, String message)
            throws Exception {
        writeBrowser(socket, status, message, false);
        closeQuietly(socket);
    }

    private static void writeBrowser(Socket socket, int status, String message, boolean success)
            throws Exception {
        String html = OAuthBrowserPage.render(message, success, AppConstants.APP_LINK);
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        String reason = status >= 200 && status < 300 ? "OK" : "Error";
        ByteArrayOutputStream response = new ByteArrayOutputStream(body.length + 256);
        writeAscii(response, "HTTP/1.1 " + status + " " + reason + "\r\n");
        writeAscii(response, "Content-Type: text/html; charset=utf-8\r\n");
        writeAscii(response, "Content-Length: " + body.length + "\r\n");
        writeAscii(response, "Cache-Control: no-store\r\n");
        writeAscii(response, "Connection: close\r\n\r\n");
        response.write(body);
        socket.getOutputStream().write(response.toByteArray());
        socket.getOutputStream().flush();
    }

    private static void writeAscii(ByteArrayOutputStream output, String text) throws Exception {
        output.write(text.getBytes(StandardCharsets.US_ASCII));
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
            try {
                socket.close();
            } catch (Exception ignored) {
                // Closing only unblocks a pending accept; nothing to recover.
            }
        }
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {
                // The browser may already have dropped the connection.
            }
        }
    }

    private void safeWidgetUpdate() {
        try {
            WidgetRenderer.updateAll(this);
        } catch (RuntimeException exception) {
            AppPreferences.setLastError(this, "Widget update: " + widgetErrorMessage(exception));
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
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.oauth_channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.oauth_channel_description));
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    /** Tapping opens the authorization page while one is pending, otherwise the app. */
    private Notification buildNotification(String text, String authUrl) {
        Intent openIntent;
        if (authUrl == null || authUrl.isEmpty()) {
            openIntent = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        } else {
            openIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
        }
        PendingIntent open = PendingIntent.getActivity(this, REQUEST_OPEN, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent cancelIntent = new Intent(this, OAuthService.class).setAction(ACTION_CANCEL);
        PendingIntent cancel = PendingIntent.getService(this, REQUEST_CANCEL, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Action cancelAction = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_oui_close), "Cancel", cancel).build();
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_oui_notification)
                .setContentTitle("Codex Meter sign-in")
                .setContentText(text)
                .setContentIntent(open)
                .addAction(cancelAction)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private void updateNotification(String text, String authUrl) {
        startForegroundCompat(buildNotification(text, authUrl));
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    /** Constant-time comparison so the OAuth state cannot be probed byte by byte. */
    private static boolean secureEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String cleanMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Sign-in could not be completed.";
        }
        return truncate(message, MAX_ERROR_LENGTH);
    }

    private static String widgetErrorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return exception.getClass().getSimpleName();
        }
        return truncate(message, MAX_WIDGET_ERROR_LENGTH);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    /** The path and decoded query parameters of the browser's redirect request. */
    private static final class Callback {
        final String path;
        final Map<String, String> parameters;

        Callback(String path, Map<String, String> parameters) {
            this.path = path;
            this.parameters = parameters;
        }
    }
}
