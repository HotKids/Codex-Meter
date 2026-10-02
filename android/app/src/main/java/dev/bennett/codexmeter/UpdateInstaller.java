package dev.bennett.codexmeter;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Downloads, authenticates, and submits one release APK to Android's package installer. */
public final class UpdateInstaller {
    private static final long MAX_APK_BYTES = 150L * 1024L * 1024L;
    private static final int MAX_CHECKSUM_BYTES = 64 * 1024;

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

    public static PreparedUpdate prepare(Context context, GitHubRelease release,
            ProgressListener listener) throws Exception {
        if (release == null) {
            throw new IllegalArgumentException(AppText.get(R.string.phone_no_github_release_was_selected_c7c06));
        }
        if (release.apkSize <= 0L || release.apkSize > MAX_APK_BYTES) {
            throw new IllegalStateException(AppText.get(R.string.phone_the_release_apk_has_an_unsafe_file_size_5e27e));
        }
        long started = SystemClock.elapsedRealtime();
        DiagnosticLog.info(context, "update", "update_prepare_started",
                "version", release.version,
                "expected_bytes", release.apkSize);
        String checksumFile = downloadText(context, "update_checksum",
                release.checksumUrl, MAX_CHECKSUM_BYTES);
        String expected = ReleaseIntegrity.expectedSha256(checksumFile, release.apkName);
        if (expected.isEmpty()) {
            throw new SecurityException(AppText.get(R.string.phone_the_release_checksum_does_not_list_167bf)
                    + release.apkName + ".");
        }
        File directory = new File(context.getCacheDir(), "verified-updates");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException(AppText.get(R.string.phone_could_not_prepare_update_storage_11231));
        }
        File partial = new File(directory, release.apkName + ".part");
        File apk = new File(directory, release.apkName);
        deleteQuietly(partial);
        deleteQuietly(apk);
        try {
            downloadFile(context, "update_apk", release.apkUrl, partial, release.apkSize,
                    listener);
            if (partial.length() != release.apkSize) {
                throw new SecurityException(AppText.get(R.string.phone_the_apk_size_does_not_match_the_github_release_b4d3d));
            }
            String actual = ReleaseIntegrity.sha256(partial);
            if (!MessageDigestSupport.constantTimeEquals(expected, actual)) {
                throw new SecurityException(AppText.get(R.string.phone_the_downloaded_apk_failed_sha_256_verification_4a722));
            }
            if (!partial.renameTo(apk)) {
                copy(partial, apk);
                deleteQuietly(partial);
            }
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

    public static int commit(Context context, PreparedUpdate update) throws Exception {
        if (update == null || update.apk == null || !update.apk.isFile()) {
            throw new IllegalArgumentException(AppText.get(R.string.phone_the_verified_update_apk_is_missing_d5d34));
        }
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        params.setSize(update.apk.length());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        }
        int sessionId = installer.createSession(params);
        DiagnosticLog.info(context, "update", "installer_session_created",
                "session_id", sessionId,
                "version", update.versionName,
                "apk_bytes", update.apk.length());
        PackageInstaller.Session session = null;
        try {
            session = installer.openSession(sessionId);
            try (InputStream input = new FileInputStream(update.apk);
                    OutputStream output = session.openWrite("base.apk", 0L, update.apk.length())) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                session.fsync(output);
            }
            Intent result = new Intent(context, UpdateInstallReceiver.class)
                    .setAction(AppConstants.ACTION_INSTALL_STATUS)
                    .putExtra(UpdateInstallReceiver.EXTRA_VERSION, update.versionName);
            PendingIntent callback = PendingIntent.getBroadcast(context, sessionId, result,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            IntentSender sender = callback.getIntentSender();
            session.commit(sender);
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
            }
            throw exception;
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    private static PreparedUpdate verifyPackage(Context context, GitHubRelease release, File apk)
            throws Exception {
        PackageManager manager = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (archive == null) {
            throw new SecurityException(AppText.get(R.string.phone_android_could_not_read_the_downloaded_apk_5958d));
        }
        if (!context.getPackageName().equals(archive.packageName)) {
            throw new SecurityException(AppText.get(R.string.phone_the_apk_package_name_is_not_codex_meter_668e2));
        }
        ReleaseVersion expected = ReleaseVersion.parse(release.version);
        ReleaseVersion actual = ReleaseVersion.parse(archive.versionName);
        if (expected == null || actual == null || expected.compareTo(actual) != 0) {
            throw new SecurityException(AppText.get(R.string.phone_the_apk_version_does_not_match_the_selected_rele_cfc03));
        }
        PackageInfo installed = manager.getPackageInfo(context.getPackageName(), flags);
        if (!sameSigners(currentSigners(installed), currentSigners(archive))) {
            throw new SecurityException(
                    AppText.get(R.string.phone_the_apk_signing_certificate_does_not_match_this_1a40f));
        }
        long installedCode = Build.VERSION.SDK_INT >= 28
                ? installed.getLongVersionCode() : installed.versionCode;
        long archiveCode = Build.VERSION.SDK_INT >= 28
                ? archive.getLongVersionCode() : archive.versionCode;
        if (archiveCode < installedCode) {
            throw new DowngradeNotSupportedException(
                    AppText.get(R.string.phone_android_cannot_install_an_older_version_over_the_407a3));
        }
        return new PreparedUpdate(apk, archive.versionName, archiveCode);
    }

    private static Signature[] currentSigners(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            return info.signingInfo.getApkContentsSigners();
        }
        return info.signatures == null ? new Signature[0] : info.signatures;
    }

    private static boolean sameSigners(Signature[] installed, Signature[] archive) {
        Set<String> installedValues = signatureValues(installed);
        Set<String> archiveValues = signatureValues(archive);
        return !installedValues.isEmpty() && installedValues.equals(archiveValues);
    }

    private static Set<String> signatureValues(Signature[] signatures) {
        HashSet<String> result = new HashSet<>();
        if (signatures != null) {
            for (Signature signature : signatures) {
                if (signature != null) {
                    result.add(signature.toCharsString());
                }
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
        HttpURLConnection connection = open(url);
        try {
            int status = requireOk(connection);
            try (InputStream input = connection.getInputStream();
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > limit) {
                        throw new SecurityException(AppText.get(R.string.phone_the_checksum_file_is_unexpectedly_large_72717));
                    }
                    output.write(buffer, 0, read);
                }
                String result = output.toString(java.nio.charset.StandardCharsets.UTF_8.name());
                DiagnosticLog.info(context, "network", "request_finished",
                        "operation", operation,
                        "status", status,
                        "duration_ms", SystemClock.elapsedRealtime() - started,
                        "response_bytes", result.getBytes(
                                java.nio.charset.StandardCharsets.UTF_8).length);
                return result;
            }
        } catch (Exception exception) {
            DiagnosticLog.error(context, "network", "request_failed", exception,
                    "operation", operation,
                    "duration_ms", SystemClock.elapsedRealtime() - started);
            throw exception;
        } finally {
            connection.disconnect();
        }
    }

    private static void downloadFile(Context context, String operation, String url,
            File destination, long expected, ProgressListener listener) throws Exception {
        long started = SystemClock.elapsedRealtime();
        DiagnosticLog.info(context, "network", "request_started",
                "operation", operation,
                "method", "GET",
                "url", DiagnosticSanitizer.safeUrl(url),
                "expected_bytes", expected);
        HttpURLConnection connection = open(url);
        try {
            int status = requireOk(connection);
            long declared = connection.getContentLengthLong();
            if (declared > MAX_APK_BYTES || (declared > 0L && declared != expected)) {
                throw new SecurityException(AppText.get(R.string.phone_the_apk_download_size_changed_unexpectedly_6001d));
            }
            try (InputStream input = connection.getInputStream();
                    OutputStream output = new FileOutputStream(destination)) {
                byte[] buffer = new byte[64 * 1024];
                long total = 0L;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException(AppText.get(R.string.phone_update_download_canceled_b9e8a));
                    }
                    total += read;
                    if (total > expected || total > MAX_APK_BYTES) {
                        throw new SecurityException(AppText.get(R.string.phone_the_apk_download_exceeded_its_expected_size_abe99));
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
            DiagnosticLog.error(context, "network", "request_failed", exception,
                    "operation", operation,
                    "duration_ms", SystemClock.elapsedRealtime() - started);
            throw exception;
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String value) throws Exception {
        URL url = new URL(value);
        if (!trustedDownloadUrl(url)) {
            throw new SecurityException(AppText.get(R.string.phone_the_release_download_url_is_not_trusted_d09d6));
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(60_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/octet-stream");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("User-Agent", AppConstants.updaterUserAgent());
        return connection;
    }

    private static int requireOk(HttpURLConnection connection) throws Exception {
        int status = connection.getResponseCode();
        URL finalUrl = connection.getURL();
        if (!trustedDownloadUrl(finalUrl)) {
            throw new SecurityException(AppText.get(R.string.phone_github_redirected_the_download_to_an_untrusted_h_720ad));
        }
        if (status != HttpURLConnection.HTTP_OK) {
            throw new IllegalStateException(AppText.get(R.string.phone_github_download_failed_with_http_93da7) + status + ".");
        }
        return status;
    }

    private static boolean allowedHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.US);
        return "github.com".equals(normalized)
                || normalized.endsWith(".github.com")
                || "githubusercontent.com".equals(normalized)
                || normalized.endsWith(".githubusercontent.com");
    }

    private static boolean trustedDownloadUrl(URL url) {
        if ("https".equalsIgnoreCase(url.getProtocol()) && allowedHost(url.getHost())) {
            return true;
        }
        return BuildConfig.DEBUG
                && "http".equalsIgnoreCase(url.getProtocol())
                && "10.0.2.2".equals(url.getHost())
                && url.getPort() == 8765;
    }

    private static void copy(File source, File destination) throws Exception {
        try (InputStream input = new FileInputStream(source);
                OutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    private static final class MessageDigestSupport {
        static boolean constantTimeEquals(String left, String right) {
            if (left == null || right == null) {
                return false;
            }
            return java.security.MessageDigest.isEqual(
                    left.toLowerCase(Locale.US).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    right.toLowerCase(Locale.US).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }
    }
}
