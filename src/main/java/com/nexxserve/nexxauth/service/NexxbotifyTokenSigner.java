package com.nexxserve.nexxauth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Generates short-lived (60s), single-use cryptographically signed JWT tokens
 * for machine-to-machine calls from nexxauth to nexxnotify.
 *
 * <p>Each token carries a unique {@code jti} (UUID) and 60-second expiration.
 * nexxnotify records seen JTIs, guaranteeing that no token can be replayed twice.
 *
 * <p>Supports asymmetric Ed25519 or RSA private keys (recommended), or HMAC-SHA256
 * shared secret fallback.
 */
@Component
public class NexxbotifyTokenSigner {

    private static final Logger log = LoggerFactory.getLogger(NexxbotifyTokenSigner.class);

    private final PrivateKey privateKey;
    private final SecretKey hmacKey;

    public NexxbotifyTokenSigner(NexxbotifyProperties properties) {
        PrivateKey parsedPriv = null;
        SecretKey parsedHmac = null;

        String rawPriv = properties.getPrivateKey() != null ? properties.getPrivateKey().trim() : "";
        String rawApi = properties.getApiKey() != null ? properties.getApiKey().trim() : "";

        if (!rawPriv.isBlank()) {
            try {
                parsedPriv = parsePrivateKey(rawPriv);
                log.info("Initialized asymmetric token signer for nexxnotify (algorithm: {})", parsedPriv.getAlgorithm());
            } catch (Exception e) {
                log.error("Failed to parse nexxnotify private key: {}", e.getMessage(), e);
            }
        } else if (!rawApi.isBlank()) {
            try {
                byte[] keyBytes = rawApi.getBytes(StandardCharsets.UTF_8);
                // HMAC-SHA256 requires at least 256 bits (32 bytes)
                if (keyBytes.length >= 32) {
                    parsedHmac = Keys.hmacShaKeyFor(keyBytes);
                    log.info("Initialized HMAC-SHA256 token signer for nexxnotify with shared secret");
                }
            } catch (Exception e) {
                log.warn("Could not derive HMAC key from api-key: {}", e.getMessage());
            }
        }

        this.privateKey = parsedPriv;
        this.hmacKey = parsedHmac;
    }

    /**
     * Whether a cryptographic signer is active (asymmetric private key or HMAC secret).
     */
    public boolean isConfigured() {
        return privateKey != null || hmacKey != null;
    }

    /**
     * Generates a fresh 60-second JWT token with a unique JTI.
     * Returns null if no signing key is configured.
     */
    public String generateToken() {
        if (!isConfigured()) {
            return null;
        }

        Instant now = Instant.now();
        Instant exp = now.plusSeconds(60);
        String jti = UUID.randomUUID().toString();

        var builder = Jwts.builder()
                .issuer("nexxauth")
                .audience().add("nexxnotify").and()
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp));

        if (privateKey != null) {
            builder.signWith(privateKey);
        } else {
            builder.signWith(hmacKey);
        }

        return builder.compact();
    }

    private PrivateKey parsePrivateKey(String pemOrBase64) throws Exception {
        String clean = pemOrBase64.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN ED25519 PRIVATE KEY-----", "")
                .replace("-----END ED25519 PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] keyBytes = Base64.getDecoder().decode(clean);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);

        // Try Ed25519 first (Java 15+)
        try {
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            return kf.generatePrivate(spec);
        } catch (Exception e) {
            // Fallback to RSA
            try {
                KeyFactory kf = KeyFactory.getInstance("RSA");
                return kf.generatePrivate(spec);
            } catch (Exception e2) {
                // Fallback to ECDSA
                KeyFactory kf = KeyFactory.getInstance("EC");
                return kf.generatePrivate(spec);
            }
        }
    }
}
