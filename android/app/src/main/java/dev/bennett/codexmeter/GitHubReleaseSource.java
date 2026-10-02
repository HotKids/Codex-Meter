package dev.bennett.codexmeter;

/** Canonical production repository used for release discovery and project links. */
public final class GitHubReleaseSource {
    public static final String REPOSITORY_URL =
            "https://github.com/HotKids/Codex-Meter"; // pragma: allowlist secret
    public static final String RELEASES_API_URL =
            "https://api.github.com/repos/HotKids/Codex-Meter/releases?per_page=30"; // pragma: allowlist secret

    /** Emulator-side address of the local release fixture server that debug builds may use. */
    private static final String LOCAL_DEBUG_HOST = "10.0.2.2";
    private static final int LOCAL_DEBUG_PORT = 8765;

    private GitHubReleaseSource() {
    }

    /**
     * True when the URL parts point at the plain-HTTP local fixture server. Callers decide
     * whether the current build may trust it.
     */
    static boolean isLocalDebugServer(String scheme, String host, int port) {
        return "http".equalsIgnoreCase(scheme)
                && LOCAL_DEBUG_HOST.equals(host)
                && port == LOCAL_DEBUG_PORT;
    }
}
