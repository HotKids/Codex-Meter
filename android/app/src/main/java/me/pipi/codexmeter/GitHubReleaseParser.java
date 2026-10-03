package me.pipi.codexmeter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Parses only release entries that match the repository's signed APK release contract. */
public final class GitHubReleaseParser {
    private static final int MAX_NOTES = 6000;
    private static final int MAX_TAG_LENGTH = 100;
    private static final int MAX_NAME_LENGTH = 160;
    private static final int MAX_PUBLISHED_AT_LENGTH = 80;
    private static final String CHECKSUM_ASSET_NAME = "SHA256SUMS.txt";

    private GitHubReleaseParser() {
    }

    public static List<GitHubRelease> parse(String json) throws Exception {
        return parse(json, false);
    }

    static List<GitHubRelease> parse(String json, boolean allowLocalDebugServer)
            throws Exception {
        JSONArray releases = new JSONArray(json == null ? "[]" : json);
        List<GitHubRelease> parsed = new ArrayList<>();
        Set<String> seenVersions = new HashSet<>();
        for (int index = 0; index < releases.length(); index++) {
            GitHubRelease release = parseRelease(releases.optJSONObject(index), seenVersions,
                    allowLocalDebugServer);
            if (release != null) {
                seenVersions.add(release.version);
                parsed.add(release);
            }
        }
        // Newest first.
        parsed.sort((left, right) -> ReleaseVersion.compare(right.version, left.version));
        return Collections.unmodifiableList(parsed);
    }

    public static GitHubRelease latestStable(List<GitHubRelease> releases) {
        if (releases == null) {
            return null;
        }
        for (GitHubRelease release : releases) {
            if (release != null && !release.prerelease) {
                return release;
            }
        }
        return null;
    }

    public static GitHubRelease findVersion(List<GitHubRelease> releases, String version) {
        ReleaseVersion wanted = ReleaseVersion.parse(version);
        if (wanted == null || releases == null) {
            return null;
        }
        for (GitHubRelease release : releases) {
            ReleaseVersion candidate = ReleaseVersion.parse(release.version);
            if (candidate != null && candidate.compareTo(wanted) == 0) {
                return release;
            }
        }
        return null;
    }

    static boolean isGitHubHttps(String value) {
        return isTrustedReleaseUrl(value, false);
    }

    /**
     * Returns the installable release described by {@code release}, or null when it is a draft,
     * repeats an already accepted version, or lacks a trusted APK and checksum asset.
     */
    private static GitHubRelease parseRelease(JSONObject release, Set<String> seenVersions,
            boolean allowLocalDebugServer) {
        if (release == null || release.optBoolean("draft", false)) {
            return null;
        }
        String tag = clean(release.optString("tag_name", ""), MAX_TAG_LENGTH);
        ReleaseVersion version = ReleaseVersion.parse(tag);
        if (version == null || seenVersions.contains(version.normalized())) {
            return null;
        }
        String expectedApk = "CodexMeter-me.pipi.codexmeter-" + tag.replaceFirst("^[vV]", "") + ".apk";
        JSONArray assets = release.optJSONArray("assets");
        JSONObject apk = findAsset(assets, expectedApk);
        JSONObject checksum = findAsset(assets, CHECKSUM_ASSET_NAME);
        if (apk == null || checksum == null) {
            return null;
        }
        String apkUrl = apk.optString("browser_download_url", "");
        String checksumUrl = checksum.optString("browser_download_url", "");
        String pageUrl = release.optString("html_url", "");
        if (!isTrustedReleaseUrl(apkUrl, allowLocalDebugServer)
                || !isTrustedReleaseUrl(checksumUrl, allowLocalDebugServer)
                || !isTrustedReleaseUrl(pageUrl, allowLocalDebugServer)) {
            return null;
        }
        long apkSize = apk.optLong("size", -1L);
        if (apkSize <= 0L) {
            return null;
        }
        String releaseName = clean(release.optString("name", ""), MAX_NAME_LENGTH);
        if (releaseName.isEmpty()) {
            releaseName = "Codex Meter " + version.normalized();
        }
        String notes = clean(release.optString("body", ""), MAX_NOTES);
        String publishedAt = clean(release.optString("published_at", ""),
                MAX_PUBLISHED_AT_LENGTH);
        boolean prerelease = release.optBoolean("prerelease", false) || version.isPrerelease();
        return new GitHubRelease(version.normalized(), tag, releaseName, notes, publishedAt,
                pageUrl, expectedApk, apkUrl, apkSize, checksumUrl, prerelease);
    }

    /** Returns the last asset named {@code name}, or null when none matches. */
    private static JSONObject findAsset(JSONArray assets, String name) {
        if (assets == null) {
            return null;
        }
        JSONObject found = null;
        for (int index = 0; index < assets.length(); index++) {
            JSONObject asset = assets.optJSONObject(index);
            if (asset != null && name.equals(asset.optString("name", ""))) {
                found = asset;
            }
        }
        return found;
    }

    private static boolean isTrustedReleaseUrl(String value, boolean allowLocalDebugServer) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if ("https".equalsIgnoreCase(scheme) && isGitHubHost(host)) {
                return true;
            }
            return allowLocalDebugServer
                    && GitHubReleaseSource.isLocalDebugServer(scheme, host, uri.getPort());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isGitHubHost(String host) {
        return host != null
                && ("github.com".equalsIgnoreCase(host)
                || host.toLowerCase(Locale.US).endsWith(".github.com"));
    }

    private static String clean(String value, int maxLength) {
        String cleaned = value == null ? "" : value.trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}
