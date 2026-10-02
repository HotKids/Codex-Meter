package dev.bennett.codexmeter;

import android.content.Context;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONObject;

/** Public, unauthenticated GitHub release discovery client. */
public final class ReleaseUpdateClient {
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;
    private static final int MAX_ERROR_BODY_BYTES = 16 * 1024;
    private static final int READ_BUFFER_BYTES = 8192;
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 25_000;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final Object LOCK = new Object();

    private ReleaseUpdateClient() {
    }

    public static List<GitHubRelease> check(Context context) throws Exception {
        Context app = context.getApplicationContext();
        if (app == null) {
            app = context;
        }
        synchronized (LOCK) {
            long started = SystemClock.elapsedRealtime();
            DiagnosticLog.info(app, "update", "release_check_started");
            try {
                List<GitHubRelease> releases = request(app, true);
                DiagnosticLog.info(app, "update", "release_check_succeeded",
                        "duration_ms", SystemClock.elapsedRealtime() - started,
                        "release_count", releases.size());
                return releases;
            } catch (Exception exception) {
                DiagnosticLog.error(app, "update", "release_check_failed", exception,
                        "duration_ms", SystemClock.elapsedRealtime() - started);
                UpdatePreferences.saveError(app, safeMessage(exception));
                throw exception;
            }
        }
    }

    /**
     * Fetches the release list. A conditional request sends the cached ETag; when GitHub answers
     * 304 but the cache is unusable, the ETag is dropped and the list is fetched again in full.
     */
    private static List<GitHubRelease> request(Context app, boolean conditional)
            throws Exception {
        HttpURLConnection connection = null;
        try {
            String endpoint = BuildConfig.DEBUG
                    ? BuildConfig.UPDATE_API_URL : GitHubReleaseSource.RELEASES_API_URL;
            boolean localDebugServer = isLocalDebugServer(endpoint);
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            long started = SystemClock.elapsedRealtime();
            DiagnosticLog.info(app, "network", "request_started",
                    "operation", "github_release_check",
                    "method", "GET",
                    "url", DiagnosticSanitizer.safeUrl(endpoint),
                    "conditional", conditional);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            connection.setRequestProperty("User-Agent", AppConstants.updaterUserAgent());
            String etag = conditional ? UpdatePreferences.etag(app) : "";
            if (!etag.isEmpty()) {
                connection.setRequestProperty("If-None-Match", etag);
            }
            int status = connection.getResponseCode();
            URL finalUrl = connection.getURL();
            DiagnosticLog.info(app, "network", "request_finished",
                    "operation", "github_release_check",
                    "status", status,
                    "duration_ms", SystemClock.elapsedRealtime() - started,
                    "url", DiagnosticSanitizer.safeUrl(finalUrl.toString()));
            if (!isTrustedApiUrl(finalUrl, localDebugServer)) {
                throw new SecurityException(
                        "GitHub redirected the update check to an untrusted host.");
            }
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                if (!UpdatePreferences.hasUsableCache(app)) {
                    UpdatePreferences.clearEtag(app);
                    return request(app, false);
                }
                UpdatePreferences.markNotModified(app);
                return UpdatePreferences.releases(app);
            }
            if (status != HttpURLConnection.HTTP_OK) {
                String detail = read(connection.getErrorStream(), MAX_ERROR_BODY_BYTES);
                throw new IllegalStateException(githubError(status, detail));
            }
            String json = read(connection.getInputStream(), MAX_RESPONSE_BYTES);
            UpdatePreferences.saveSuccess(app, json, connection.getHeaderField("ETag"));
            return UpdatePreferences.releases(app);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean isLocalDebugServer(String value) {
        try {
            URL url = new URL(value);
            return BuildConfig.DEBUG && GitHubReleaseSource.isLocalDebugServer(
                    url.getProtocol(), url.getHost(), url.getPort());
        } catch (Exception exception) {
            return false;
        }
    }

    private static boolean isTrustedApiUrl(URL url, boolean localDebugServer) {
        if (localDebugServer) {
            return GitHubReleaseSource.isLocalDebugServer(
                    url.getProtocol(), url.getHost(), url.getPort());
        }
        return "https".equalsIgnoreCase(url.getProtocol())
                && "api.github.com".equalsIgnoreCase(url.getHost());
    }

    /** Reads {@code input} as UTF-8, failing once more than {@code limit} bytes arrive. */
    private static String read(InputStream input, int limit) throws Exception {
        if (input == null) {
            return "";
        }
        try (InputStream stream = input;
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[READ_BUFFER_BYTES];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw new IllegalStateException("GitHub returned too much release metadata.");
                }
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String githubError(int status, String detail) {
        String message = "";
        try {
            message = new JSONObject(detail).optString("message", "");
        } catch (Exception ignored) {
            // Not a JSON error body; fall back to the status code alone.
        }
        if (status == HttpURLConnection.HTTP_FORBIDDEN || status == HTTP_TOO_MANY_REQUESTS) {
            return "GitHub temporarily limited update checks. Try again later.";
        }
        if (!message.isEmpty()) {
            return "GitHub update check failed (" + status + "): " + message;
        }
        return "GitHub update check failed with HTTP " + status + ".";
    }

    static String safeMessage(Exception exception) {
        String message = exception == null ? "" : exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = "Could not check GitHub releases.";
        }
        return message.length() <= UpdatePreferences.MAX_ERROR_LENGTH
                ? message : message.substring(0, UpdatePreferences.MAX_ERROR_LENGTH);
    }
}
