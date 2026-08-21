package com.nexxserve.nauth.security;

import com.nexxserve.nauth.entity.OrganisationSigningKey;
import com.nexxserve.nauth.entity.OrganisationUser;
import com.nexxserve.nauth.service.OrgKeyService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Issues and verifies organisation access tokens. Each organisation signs its
 * own tokens with its private key (RS256) so other services can verify them
 * with the organisation's public key; the key id travels in the JWT header.
 * Claims carry only the user's roles - permissions are an internal nauth
 * concern and are resolved from the database on every request, never exposed
 * in the token.
 */
@Service
public class OrgJwtService {

    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ORG_ACCESS = "org-access";
    public static final String CLAIM_ORG_ID = "orgId";
    public static final String CLAIM_ORG_SLUG = "orgSlug";
    public static final String CLAIM_ROLES = "roles";

    private final JwtProperties properties;
    private final OrgKeyService orgKeyService;
    private final ObjectMapper objectMapper;

    public OrgJwtService(JwtProperties properties, OrgKeyService orgKeyService, ObjectMapper objectMapper) {
        this.properties = properties;
        this.orgKeyService = orgKeyService;
        this.objectMapper = objectMapper;
    }

    public String generateAccessToken(OrganisationUser user, OrganisationSigningKey signingKey,
                                      Duration accessTokenTtl) {
        Instant now = Instant.now();
        RSAPrivateKey privateKey = orgKeyService.privateKeyOf(signingKey);
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuer(properties.issuer())
                .claim(CLAIM_ORG_ID, user.getOrganisation().getId())
                .claim(CLAIM_ORG_SLUG, user.getOrganisation().getSlug())
                .claim(CLAIM_ROLES, user.getRoles().stream().map(role -> role.getName()).toList())
                .claim(CLAIM_TYPE, TYPE_ORG_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .header().keyId(signingKey.getKid()).and()
                .compact();
    }

    /**
     * Verifies the token with the organisation's key selected by the JWT
     * header's {@code kid}. Throws {@link JwtException} for any
     * invalid/expired/tampered token or unknown key.
     */
    public Claims parseAccessToken(String token) {
        String kid = extractKid(token);
        OrganisationSigningKey signingKey;
        try {
            signingKey = orgKeyService.findByKid(kid);
        } catch (com.nexxserve.nauth.exception.ResourceNotFoundException e) {
            throw new JwtException("Unknown signing key: " + kid, e);
        }
        RSAPublicKey publicKey = orgKeyService.publicKeyOf(signingKey);
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!TYPE_ORG_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("Not an organisation access token");
        }
        return claims;
    }

    private String extractKid(String token) {
        // Read only the (unsigned) JOSE header - no signature verification here,
        // that happens below against the key the kid selects. The first segment
        // is base64url-decoded and parsed as JSON so a kid value containing
        // quotes/escapes cannot break the scan (jjwt's parse() refuses signed
        // tokens without a key, so the header can't be read through the parser).
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new JwtException("Token is malformed");
            }
            JsonNode header = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode kidNode = header.get("kid");
            String kid = kidNode == null ? null : kidNode.asText();
            if (kid == null || kid.isEmpty()) {
                throw new JwtException("Token header has no kid");
            }
            return kid;
        } catch (JwtException e) {
            throw e;
        } catch (Exception e) {
            throw new JwtException("Token header is malformed", e);
        }
    }
}
