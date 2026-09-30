package com.nexxserve.nexxauth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class NexxbotifyTokenSignerTest {

    @Test
    void generatesValidTokenWithEd25519() throws Exception {
        // Generate an Ed25519 keypair for testing
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();

        String privPem = "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()) +
                "\n-----END PRIVATE KEY-----";

        NexxbotifyProperties props = new NexxbotifyProperties();
        props.setPrivateKey(privPem);

        NexxbotifyTokenSigner signer = new NexxbotifyTokenSigner(props);
        assertThat(signer.isConfigured()).isTrue();

        String token1 = signer.generateToken();
        String token2 = signer.generateToken();

        assertThat(token1).isNotNull();
        assertThat(token2).isNotNull();
        assertThat(token1).isNotEqualTo(token2); // Different tokens (unique JTI)

        // Verify token1 with the public key
        Claims claims = Jwts.parser()
                .verifyWith(kp.getPublic())
                .requireIssuer("nexxauth")
                .requireAudience("nexxnotify")
                .build()
                .parseSignedClaims(token1)
                .getPayload();

        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getIssuer()).isEqualTo("nexxauth");
    }

    @Test
    void generatesValidTokenWithHmacSharedSecret() {
        NexxbotifyProperties props = new NexxbotifyProperties();
        props.setApiKey("this-is-a-very-long-secret-key-for-hmac-sha256-signing-12345");

        NexxbotifyTokenSigner signer = new NexxbotifyTokenSigner(props);
        assertThat(signer.isConfigured()).isTrue();

        String token = signer.generateToken();
        assertThat(token).isNotNull();

        Claims claims = Jwts.parser()
                .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(props.getApiKey().getBytes()))
                .requireIssuer("nexxauth")
                .requireAudience("nexxnotify")
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getId()).isNotBlank();
    }
}
