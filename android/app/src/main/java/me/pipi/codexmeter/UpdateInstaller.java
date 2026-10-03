package me.pipi.codexmeter;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Downloads, authenticates, and submits one release APK to Android's package installer. Failure
 * messages are shown on the update page, so they are read from string resources.
 */
public final class UpdateInstaller {
    private static final long MAX_APK_BYTES = 150L * 1024L * 1024L;
    private static final int MAX_CHECKSUM_BYTES = 64 * 1024;
    private static final int CHECKSUM_BUFFER_BYTES = 4096;
    private static final int COPY_BUFFER_BYTES = 64 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final String UPDATE_DIRECTORY = "verified-updates";
    private static final String PARTIAL_SUFFIX = ".part";
    private static final String SESSION_APK_NAME = "base.apk";

    public interface ProgressListener {
        void onProgress(long downloaded, long total);
    }

    public static final class PreparedUpdate {
        public final File apk;
        public final String versionName;
        public final long versionCode;

        PreparedUpdate(File apk, String versionName, long versionCode) {
            this.apk = apk;
            this.versionName = versionName;
            this.versionCode = versionCode;
        }
    }

    public static final class DowngradeNotSupportedException extends Exception {
        DowngradeNotSupportedException(String message) {
            super(message);
        }
    }

    private UpdateInstaller() {
    }

    /**
     * Downloads the release APK into the cache and verifies, in order, its size, its SHA-256
     * against the release checksum file, and its package name, version, signing certificate,
     * and versionCode against the installed app. Any partial or rejected file is deleted.
     */
    public static PreparedUpdate prepare(Context context, GitHubRelease release,
            ProgressListener listener) throws Exception {
        if (release == null) {
            throw new IllegalArgumentException(
                    context.getString(R.string.updates_error_no_release_selected));
        }
        if (release.apkSize <= 0L || release.apkSize > MAX_APK_BYTES) {
            throw new IllegalStateException(
                    context.getString(R.string.updates_error_unsafe_apk_size));
        }
        long started = SystemClock.elapsedRealtime();
        DiagnosticLog.info(context, "update", "update_prepare_started",
                "version", release.version,
                "expected_bytes", release.apkSize);
        String checksumFile = downloadText(context, "update_checksum",
                release.checksumUrl, MAX_CHECKSUM_BYTES);
        String expected = ReleaseIntegrity.expectedSha256(checksumFile, release.apkName);
        if (expected.isEmpty()) {
            throw new SecurityException(context.getString(
                    R.string.updates_error_checksum_missing_apk, release.apkName));
        }
        File directory = new File(context.getCacheDir(), UPDATE_DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException(context.getString(R.string.updates_error_storage));
        }
        File partial = new File(directory, release.apkName + PARTIAL_SUFFIX);
        File apk = new File(directory, release.apkName);
        deleteQuietly(partial);
        deleteQuietly(apk);
        try {
            downloadFile(context, "update_apk", release.apkUrl, partial, release.apkSize,
                    listener);
            verifyDownload(context, partial, release.apkSize, expected);
            moveFile(partial, apk);
            PreparedUpdate prepared = verifyPackage(context, release, apk);
            DiagnosticLog.info(context, "update", "update_prepare_succeeded",
                    "version", prepared.versionName,
                    "version_code", prepared.versionCode,
                    "duration_ms", SystemClock.elapsedRealtime() - started);
            return prepared;
        } catch (Exception exception) {
            DiagnosticLog.error(context, "update", "update_prepare_failed", exception,
                    "version", release.version,
                    "duration_ms", SystemClock.elapsedRealtime() - started);
            deleteQuietly(partial);
            deleteQuietly(apk);
            throw exception;
        }
    }

    /**
     * Streams a prepared APK into a new PackageInstaller session and commits it. The session
     * is abandoned if anything fails before the commit is handed to Android.
     */
    public static int commit(Context context, PreparedUpdate update) throws Exception {
        if (update == null || update.apk == null || !update.apk.isFile()) {
            throw new IllegalArgumentException(
                    context.getString(R.string.updates_error_verified_apk_missing));
        }
        long apkBytes = update.apk.length();
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        params.setSize(apkBytes);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        }
        int sessionId = installer.createSession(params);
        DiagnosticLog.info(context, "update", "installer_session_created",
                "session_id", sessionId,
                "version", update.versionName,
                "apk_bytes", apkBytes);
        PackageInstaller.Session session = null;
        try {
            session = installer.openSession(sessionId);
            try (InputStream input = new FileInputStream(update.apk);
                    OutputStream output = session.openWrite(SESSION_APK_NAME, 0L, apkBytes)) {
                copyStream(input, output);
                session.fsync(output);
            }
            session.commit(statusCallback(context, sessionId, update.versionName)
                    .getIntentSender());
            DiagnosticLog.info(context, "update", "installer_session_committed",
                    "session_id", sessionId,
                    "version", update.versionName);
            return sessionId;
        } catch (Exception exception) {
            DiagnosticLog.error(context, "update", "installer_session_failed", exception,
                    "session_id", sessionId,
                    "version", update.versionName);
            try {
                installer.abandonSession(sessionId);
            } catch (RuntimeException ignored) {
                // The original failure is the one worth reporting.
            }
            throw exception;
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    /** Broadcast that {@link UpdateInstallReceiver} receives with the session's status. */
    private static PendingIntent statusCallback(Context context, int sessionId,
            String versionName) {
        Intent result = new Intent(context, UpdateInstallReceiver.class)
                .setAction(AppConstants.ACTION_INSTALL_STATUS)
                .putExtra(UpdateInstallReceiver.EXTRA_VERSION, versionName);
        // Mutable so PackageInstaller can attach the status and confirmation extras.
        return PendingIntent.getBroadcast(context, sessionId, result,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    private static void verifyDownload(Context context, File download, long expectedSize,
            String expectedSha256) throws Exception {
        if (download.length() != expectedSize) {
            throw new SecurityException(
                    context.getString(R.string.updates_error_apk_size_mismatch));
        }
        String actual = ReleaseIntegrity.sha256(download);
        if (!ReleaseIntegrity.digestsMatch(expectedSha256, actual)) {
            throw new SecurityException(
                    context.getString(R.string.updates_error_sha256_mismatch));
        }
    }

    private static PreparedUpdate verifyPackage(Context context, GitHubRelease release, File apk)
            throws Exception {
        PackageManager manager = context.getPackageManager();
        int flags = signingInfoFlags();
        PackageInfo archive = manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (archive == null) {
            throw new SecurityException(context.getString(R.string.updates_error_apk_unreadable));
        }
        if (!context.getPackageName().equals(archive.packageName)) {
            throw new SecurityException(
                    context.getString(R.string.updates_error_package_name_mismatch));
        }
        ReleaseVersion expected = ReleaseVersion.parse(release.version);
        ReleaseVersion actual = ReleaseVersion.parse(archive.versionName);
        if (expected == null || actual == null || expected.compareTo(actual) != 0) {
            throw new SecurityException(
                    context.getString(R.string.updates_error_version_mismatch));
        }
        PackageInfo installed = manager.getPackageInfo(context.getPackageName(), flags);
        if (!sameSigners(currentSigners(installed), currentSigners(archive))) {
            throw new SecurityException(
                    context.getString(R.string.updates_error_signature_mismatch));
        }
        long installedCode = longVersionCode(installed);
        long archiveCode = longVersionCode(archive);
        if (archiveCode < installedCode) {
            throw new DowngradeNotSupportedException(
                    context.getString(R.string.updates_error_downgrade_unsupported));
        }
        return new PreparedUpdate(apk, archive.versionName, archiveCode);
    }

    @SuppressWarnings("deprecation")
    private static int signingInfoFlags() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
    }

    @SuppressWarnings("deprecation")
    private static long longVersionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? info.getLongVersionCode() : info.versionCode;
    }

    @SuppressWarnings("deprecation")
    private static Signature[] currentSigners(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            return info.signingInfo.getApkContentsSigners();
        }
        return info.signatures == null ? new Signature[0] : info.signatures;
    }

    /** True when both sets of signers are identical and non-empty. */
    private static boolean sameSigners(Signature[] installed, Signature[] archive) {
        Set<String> installedValues = signatureValues(installed);
        Set<String> archiveValues = signatureValues(archive);
        return !installedValues.isEmpty() && installedValues.equals(archiveValues);
    }

    private static Set<String> signatureValues(Signature[] signatures) {
        Set<String> result = new HashSet<>();
        if (signatures == null) {
            return result;
        }
        for (Signature signature : signatures) {
            if (signature != null) {
                result.add(signature.toCharsString());
            }
        }
        return result;
    }

    private static String downloadText(Context context, String operation, String url, int limit)
            throws Exception {
        long started = SystemClock.elapsedRealtime();
        DiagnosticLog.info(context, "network", "request_started",
                "operation", operation,
                "method", "GET",
                "url", DiagnosticSanitizer.safeUrl(url));
        HttpURLConnection connection = open(context, url);
        try {
            int status = requireOk(context, connection);
            try (InputStream input = connection.getInputStream();
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[CHECKSUM_BUFFER_BYTES];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > limit) {
                        throw new SecurityException(
                                context.getString(R.string.updates_error_checksum_too_large));
                    }
                    output.write(buffer, 0, read);
                }
                String result = output.toString(StandardCharsets.UTF_8.name());
                DiagnosticLog.info(context, "network", "request_finished",
                        "operation", operation,
                        "status", status,
                        "duration_ms", SystemClock.elapsedRealtime() - started,
                        "response_bytes", result.getBytes(StandardCharsets.UTF_8).length);
                return result;
            }
        } catch (Exception exception) {
            logRequestFailed(context, operation, started, exception);
            throw exception;
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Streams {@code url} into {@code destination}. Rejects a known Content-Length other than
     * {@code expected} and stops as soon as more than {@code expected} bytes arrive.
     * Interrupting the thread cancels the download.
     */
    private static void downloadFile(Context context, String operation, String url,
            File destination, long expected, ProgressListener listener) throws Exception {
        long started = SystemClock.elapsedRealtime();
        DiagnosticLog.info(context, "network", "request_started",
                "operation", operation,
                "method", "GET",
                "url", DiagnosticSanitizer.safeUrl(url),
                "expected_bytes", expected);
        HttpURLConnection connection = open(context, url);
        try {
            int status = requireOk(context, connection);
            long declared = connection.getContentLengthLong();
            if (declared > MAX_APK_BYTES || (declared > 0L && declared != expected)) {
                throw new SecurityException(
                        context.getString(R.string.updates_error_download_size_changed));
            }
            try (InputStream input = connection.getInputStream();
                    OutputStream output = new FileOutputStream(destination)) {
                byte[] buffer = new byte[COPY_BUFFER_BYTES];
                long total = 0L;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException(
                                context.getString(R.string.updates_error_download_canceled));
                    }
                    total += read;
                    if (total > expected || total > MAX_APK_BYTES) {
                        throw new SecurityException(
                                context.getString(R.string.updates_error_download_too_large));
                    }
                    output.write(buffer, 0, read);
                    if (listener != null) {
                        listener.onProgress(total, expected);
                    }
                }
                DiagnosticLog.info(context, "network", "request_finished",
                        "operation", operation,
                        "status", status,
                        "duration_ms", SystemClock.elapsedRealtime() - started,
                        "response_bytes", total);
            }
        } catch (Exception exception) {
            logRequestFailed(context, operation, started, exception);
            throw exception;
        } finally {
            connection.disconnect();
        }
    }

    private static void logRequestFailed(Context context, String operation, long started,
            Exception exception) {
        DiagnosticLog.error(context, "network", "request_failed", exception,
                "operation", operation,
                "duration_ms", SystemClock.elapsedRealtime() - started);
    }

    private static HttpURLConnection open(Context context, String value) throws Exception {
        URL url = new URL(value);
        if (!isTrustedDownloadUrl(url)) {
            throw new SecurityException(
                    context.getString(R.string.updates_error_untrusted_download_url));
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/octet-stream");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("User-Agent", AppConstants.updaterUserAgent());
        return connection;
    }

    /**
     * Connects, rejects redirects that left the trusted hosts, and requires HTTP 200. Returns the
     * status code.
     */
    private static int requireOk(Context context, HttpURLConnection connection)
            throws Exception {
        int status = connection.getResponseCode();
        URL finalUrl = connection.getURL();
        if (!isTrustedDownloadUrl(finalUrl)) {
            throw new SecurityException(
                    context.getString(R.string.updates_error_download_redirect_untrusted));
        }
        if (status != HttpURLConnection.HTTP_OK) {
            throw new IllegalStateException(
                    context.getString(R.string.updates_error_download_http, status));
        }
        return status;
    }

    private static boolean isAllowedHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.US);
        return "github.com".equals(normalized)
                || normalized.endsWith(".github.com")
                || "githubusercontent.com".equals(normalized)
                || normalized.endsWith(".githubusercontent.com");
    }

    private static boolean isTrustedDownloadUrl(URL url) {
        if ("https".equalsIgnoreCase(url.getProtocol()) && isAllowedHost(url.getHost())) {
            return true;
        }
        return BuildConfig.DEBUG && GitHubReleaseSource.isLocalDebugServer(
                url.getProtocol(), url.getHost(), url.getPort());
    }

    /** Renames {@code source} onto {@code destination}, falling back to copy and delete. */
    private static void moveFile(File source, File destination) throws Exception {
        if (source.renameTo(destination)) {
            return;
        }
        try (InputStream input = new FileInputStream(source);
                OutputStream output = new FileOutputStream(destination)) {
            copyStream(input, output);
        }
        deleteQuietly(source);
    }

    private static void copyStream(InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }
}
