package com.nexxserve.nexxauth.security;

import com.nexxserve.nexxauth.entity.OrganisationSigningKey;
import com.nexxserve.nexxauth.entity.OrganisationUser;
import com.nexxserve.nexxauth.service.OrgKeyService;
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
import java.util.List;

/**
 * Issues and verifies organisation access tokens. Each organisation signs its
 * own tokens with its private key (RS256) so other services can verify them
 * with the organisation's public key; the key id travels in the JWT header.
 * Claims carry only the user's roles - permissions are an internal nexxauth
 * concern and are resolved from the database on every request, never exposed
 * in the token.
 */
@Service
public class OrgJwtService {

    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ORG_ACCESS = "org-access";
    public static final String TYPE_ACTION_TOKEN = "action-token";
    public static final String CLAIM_ORG_ID = "orgId";
    public static final String CLAIM_ORG_SLUG = "orgSlug";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_DATA_HASH = "dataHash";
    public static final String CLAIM_ACTIONS = "actions";
    public static final String CLAIM_IDENTIFIER = "identifier";
    public static final String CLAIM_IDENTIFIER_TYPE = "identifierType";
    public static final String CLAIM_PURPOSE = "purpose";
    public static final Duration ACTION_TOKEN_TTL = Duration.ofMinutes(5);

    private final JwtProperties properties;
    private final OrgKeyService orgKeyService;
    private final ObjectMapper objectMapper;

    public OrgJwtService(JwtProperties properties, OrgKeyService orgKeyService,
                         ObjectMapper objectMapper) {
        this.properties = properties;
        this.orgKeyService = orgKeyService;
        this.objectMapper = objectMapper;
    }

    private javax.crypto.SecretKey hmacKey() {
        return io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                properties.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public String generateActionToken(com.nexxserve.nexxauth.entity.Organisation organisation,
                                      String identifier,
                                      com.nexxserve.nexxauth.entity.OrgIdentifierType identifierType,
                                      Long userId,
                                      List<com.nexxserve.nexxauth.entity.OrgUserAction> actions) {
        return generateActionToken(organisation, identifier, identifierType, userId, actions, null, ACTION_TOKEN_TTL);
    }

    public String generateActionToken(com.nexxserve.nexxauth.entity.Organisation organisation,
                                      String identifier,
                                      com.nexxserve.nexxauth.entity.OrgIdentifierType identifierType,
                                      Long userId,
                                      List<com.nexxserve.nexxauth.entity.OrgUserAction> actions,
                                      String purpose,
                                      Duration ttl) {
        Instant now = Instant.now();
        Duration tokenTtl = ttl != null ? ttl : ACTION_TOKEN_TTL;
        var builder = Jwts.builder()
                .subject(userId != null ? String.valueOf(userId) : (identifier != null ? identifier : ""))
                .issuer(properties.issuer())
                .claim(CLAIM_ORG_ID, organisation.getId())
                .claim(CLAIM_ORG_SLUG, organisation.getSlug())
                .claim(CLAIM_IDENTIFIER, identifier != null ? identifier : "")
                .claim(CLAIM_ACTIONS, actions != null ? actions.stream().map(Enum::name).toList() : java.util.List.of())
                .claim(CLAIM_TYPE, TYPE_ACTION_TOKEN)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(tokenTtl)));
        if (identifierType != null) {
            builder.claim(CLAIM_IDENTIFIER_TYPE, identifierType.name());
        }
        if (purpose != null) {
            builder.claim(CLAIM_PURPOSE, purpose);
        }
        return builder.signWith(hmacKey(), Jwts.SIG.HS256).compact();
    }

    public Claims parseActionToken(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtException("Action token is blank");
        }
        Claims claims = Jwts.parser()
                .verifyWith(hmacKey())
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!TYPE_ACTION_TOKEN.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("Not an action token");
        }
        return claims;
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
                .claim(CLAIM_DATA_HASH, user.getDataHash())
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
        } catch (com.nexxserve.nexxauth.exception.ResourceNotFoundException e) {
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
