package com.nexxserve.nauth.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Shared refresh-token mechanics: cryptographically random raw tokens and their
 * SHA-256 hashes (only hashes are ever persisted). Used by both the platform
 * and organisation refresh-token services so the crypto is defined once.
 */
public final class RefreshTokens {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private RefreshTokens() {
    }

    public static String generateRaw() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
