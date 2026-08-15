package com.nexxserve.nexxauth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generation and hashing of client static tokens. Tokens are long-lived opaque
 * secrets generated server-side and shown exactly once; only the SHA-256 hash
 * is persisted, so a database leak does not expose usable tokens.
 */
public final class ClientTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private ClientTokens() {
    }

    /** A fresh opaque token, e.g. {@code nx_abCd...} (32 random bytes, URL-safe base64). */
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "nx_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex digest of a token (the stored form). */
    public static String hash(String token) {
        return HexFormat.of().formatHex(sha256(token));
    }

    /** Constant-time comparison of a presented token against the stored hash. */
    public static boolean matches(String presented, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        try {
            return MessageDigest.isEqual(sha256(presented), HexFormat.of().parseHex(storedHash));
        } catch (IllegalArgumentException malformedStoredHash) {
            return false;
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
