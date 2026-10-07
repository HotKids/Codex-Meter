package me.pipi.codexmeter;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.Context;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class,
        shadows = {UpdateDownloadOwnershipTest.SyntheticDownloads.class,
                SettingsAccountCardTest.InMemoryTokenStore.class})
public class UpdateDownloadOwnershipTest {
    private static final byte[] PAYLOAD = "synthetic apk bytes".getBytes(StandardCharsets.UTF_8);
    private Context app;
    private File directory;

    @Before
    public void resetSyntheticDownloads() {
        app = RuntimeEnvironment.getApplication();
        directory = new File(app.getCacheDir(), "verified-updates");
        for (File file : files()) { assertTrue(file.delete()); }
        SyntheticDownloads.requests = 0;
        SettingsAccountCardTest.InMemoryTokenStore.syntheticTokens = null;
    }

    @Test
    public void canceledOldDownloadCannotRemoveTheNewDownloadsFile() throws Exception {
        CountDownLatch firstReady = new CountDownLatch(1);
        CountDownLatch secondReady = new CountDownLatch(1);
        CountDownLatch firstRelease = new CountDownLatch(1);
        CountDownLatch secondRelease = new CountDownLatch(1);
        AtomicReference<File> firstFile = new AtomicReference<>();
        AtomicReference<File> secondFile = new AtomicReference<>();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        Thread first = worker(firstFailure, () -> {
            firstFile.set(files()[0]);
            firstReady.countDown();
            await(firstRelease);
        });
        Thread second = worker(secondFailure, () -> {
            for (File file : files()) {
                if (!file.equals(firstFile.get())) { secondFile.set(file); }
            }
            secondReady.countDown();
            await(secondRelease);
        });
        try {
            first.start();
            assertTrue("First download reaches its real progress callback",
                    firstReady.await(5, TimeUnit.SECONDS));
            second.start();
            assertTrue("Second download reaches its real progress callback",
                    secondReady.await(5, TimeUnit.SECONDS));
            assertNotNull("Concurrent downloads must own separate temporary files", secondFile.get());
            first.interrupt();
            firstRelease.countDown();
            first.join(5000);
            assertFalse(first.isAlive());
            assertTrue(firstFailure.get() instanceof InterruptedException);
            assertTrue("Cleanup of the old attempt must preserve the new attempt",
                    secondFile.get().exists());
        } finally {
            first.interrupt();
            second.interrupt();
            firstRelease.countDown();
            secondRelease.countDown();
            if (first.getState() != Thread.State.NEW) { first.join(5000); }
            if (second.getState() != Thread.State.NEW) { second.join(5000); }
        }
        assertEquals("Canceled attempts leave no partial or verified APK", 0, files().length);
    }

    @Test
    public void alreadyCanceledDownloadNeverStartsNetworkOrCreatesAFile() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class,
                    () -> UpdateInstaller.prepare(app, release(), null));
            assertEquals(0, SyntheticDownloads.requests);
            assertEquals(0, files().length);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void canceledHandoffRemovesOwnedApkWithoutCreatingAnInstallerSession() throws Exception {
        assertTrue(directory.exists() || directory.mkdirs());
        File apk = File.createTempFile("update-", ".apk", directory);
        Files.write(apk.toPath(), PAYLOAD);
        android.content.pm.PackageInstaller installer = app.getPackageManager().getPackageInstaller();
        int sessions = installer.getAllSessions().size();
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> UpdateInstaller.commit(app,
                    new UpdateInstaller.PreparedUpdate(apk, "0.4", 6)));
            assertEquals(sessions, installer.getAllSessions().size());
            assertFalse(apk.exists());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @Config(shadows = {SyntheticDownloads.class, SettingsAccountCardTest.InMemoryTokenStore.class,
            WidgetUpgradeRepairTest.PreviewManager.class})
    public void delayedUpgradeRepairCannotRemoveActiveDownloadFiles() throws Exception {
        assertTrue(directory.exists() || directory.mkdirs());
        File active = File.createTempFile("update-", ".part", directory);
        File legacy = new File(directory, "synthetic-old.apk");
        Files.write(active.toPath(), PAYLOAD);
        Files.write(legacy.toPath(), PAYLOAD);
        try {
            WidgetUpgradeRepair.afterPackageReplaced(app);
            WidgetUpgradeRepair.perform(app);
            assertTrue("The active attempt keeps ownership during delayed repair", active.exists());
            assertFalse("Legacy fixed-name downloads are still removed", legacy.exists());
        } finally {
            active.delete();
            legacy.delete();
        }
    }

    private Thread worker(AtomicReference<Throwable> failure, Runnable progress) {
        return new Thread(() -> {
            try {
                UpdateInstaller.prepare(app, release(), (done, total) -> progress.run());
            } catch (Throwable exception) {
                failure.set(exception);
            }
        }, "synthetic-update-download");
    }

    private static void await(CountDownLatch latch) {
        boolean interrupted = false;
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (latch.getCount() > 0 && System.nanoTime() < deadline) {
                try { latch.await(100, TimeUnit.MILLISECONDS); }
                catch (InterruptedException exception) { interrupted = true; }
            }
        } finally {
            if (interrupted) { Thread.currentThread().interrupt(); }
        }
    }

    private File[] files() {
        File[] files = directory.listFiles();
        return files == null ? new File[0] : files;
    }

    private static GitHubRelease release() {
        return new GitHubRelease("0.4", "v0.4", "Synthetic release", "", "", "",
                "synthetic.apk", "https://github.com/synthetic.apk", PAYLOAD.length,
                "https://github.com/SHA256SUMS.txt", false);
    }

    @Implements(value = UpdateInstaller.class, isInAndroidSdk = false)
    public static class SyntheticDownloads {
        static volatile int requests;

        @Implementation
        protected static HttpURLConnection open(Context context, String value) throws Exception {
            requests++;
            byte[] bytes = value.endsWith(".txt")
                    ? (HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(PAYLOAD))
                            + "  synthetic.apk\n").getBytes(StandardCharsets.UTF_8)
                    : PAYLOAD;
            return new HttpURLConnection(new URL(value)) {
                @Override public int getResponseCode() { return HTTP_OK; }
                @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
                @Override public long getContentLengthLong() { return bytes.length; }
                @Override public void connect() {}
                @Override public void disconnect() {}
                @Override public boolean usingProxy() { return false; }
            };
        }
    }
}
