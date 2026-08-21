package com.nexxserve.nexxauth.security;

import com.nexxserve.nexxauth.entity.PlatformUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and verifies short-lived access tokens (HS256).
 */
@Service
public class JwtService {

    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ACCESS = "access";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
    }

    public String generateAccessToken(PlatformUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuer(properties.issuer())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("platformId", user.getPlatform().getId())
                .claim("platformSlug", user.getPlatform().getSlug())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Parses and validates the token. Throws {@link io.jsonwebtoken.JwtException}
     * for any invalid/expired/tampered token.
     */
    public Claims parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new io.jsonwebtoken.JwtException("Not an access token");
        }
        return claims;
    }
}
