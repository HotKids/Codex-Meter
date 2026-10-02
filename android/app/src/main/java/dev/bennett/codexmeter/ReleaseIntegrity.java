package dev.bennett.codexmeter;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Checksum parsing and hashing for downloaded release APKs. */
public final class ReleaseIntegrity {
    private static final int SHA256_HEX_LENGTH = 64;
    /** A checksum line holds the digest, a separator, and at least one file-name character. */
    private static final int MIN_CHECKSUM_LINE_LENGTH = SHA256_HEX_LENGTH + 2;
    private static final int HASH_BUFFER_BYTES = 32 * 1024;

    private ReleaseIntegrity() {
    }

    /**
     * Returns the lower-case SHA-256 listed for {@code fileName} in a {@code sha256sum}-style
     * checksum file, or an empty string when the name is unsafe, missing, or listed twice.
     */
    public static String expectedSha256(String checksumFile, String fileName) {
        if (checksumFile == null || fileName == null || fileName.contains("/")
                || fileName.contains("\\") || fileName.trim().isEmpty()) {
            return "";
        }
        String found = "";
        for (String rawLine : checksumFile.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.length() < MIN_CHECKSUM_LINE_LENGTH) {
                continue;
            }
            String digest = line.substring(0, SHA256_HEX_LENGTH);
            if (!isSha256(digest)) {
                continue;
            }
            String listedName = line.substring(SHA256_HEX_LENGTH).trim();
            if (listedName.startsWith("*")) {
                // Binary-mode marker.
                listedName = listedName.substring(1);
            }
            if (fileName.equals(listedName)) {
                if (!found.isEmpty()) {
                    return "";
                }
                found = digest.toLowerCase(Locale.US);
            }
        }
        return found;
    }

    public static String sha256(File file) throws Exception {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("Downloaded APK is missing.");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[HASH_BUFFER_BYTES];
        try (InputStream input = new FileInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder result = new StringBuilder(SHA256_HEX_LENGTH);
        for (byte value : digest.digest()) {
            result.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return result.toString();
    }

    /** Compares two hex digests case-insensitively in constant time. */
    static boolean digestsMatch(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.toLowerCase(Locale.US).getBytes(StandardCharsets.US_ASCII),
                actual.toLowerCase(Locale.US).getBytes(StandardCharsets.US_ASCII));
    }

    private static boolean isSha256(String value) {
        if (value.length() != SHA256_HEX_LENGTH) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = Character.toLowerCase(value.charAt(index));
            if (!(character >= '0' && character <= '9')
                    && !(character >= 'a' && character <= 'f')) {
                return false;
            }
        }
        return true;
    }
}
