package me.pipi.codexmeter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** A PKCE verifier with its S256 challenge, plus an independent OAuth state value. */
public final class Pkce {
    private static final int VERIFIER_BYTES = 64;
    private static final int STATE_BYTES = 32;

    public final String challenge;
    public final String state;
    public final String verifier;

    private Pkce(String verifier, String challenge, String state) {
        this.verifier = verifier;
        this.challenge = challenge;
        this.state = state;
    }

    public static Pkce generate() throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] verifierBytes = new byte[VERIFIER_BYTES];
        byte[] stateBytes = new byte[STATE_BYTES];
        random.nextBytes(verifierBytes);
        random.nextBytes(stateBytes);
        String verifier = base64Url(verifierBytes);
        byte[] challengeDigest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return new Pkce(verifier, base64Url(challengeDigest), base64Url(stateBytes));
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
