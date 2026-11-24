package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.OrganisationUser;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Emits signed lifecycle events to CavGo. The token is signed with the same
 * organisation key that signs org-access tokens, but has a distinct type,
 * short TTL and single-use id so it cannot be replayed as an access token or
 * as a webhook.
 *
 * Delivery is best-effort: a webhook outage must never break login or logout.
 */
@Component
public class AuthWebhookEmitter {

    private static final Logger log = LoggerFactory.getLogger(AuthWebhookEmitter.class);
    private static final String WEBHOOK_TYPE = "org-webhook";
    private static final String ISSUER = "nexxauth";

    private final RestClient restClient;
    private final OrgKeyService orgKeyService;
    private final String url;
    private final String issuer;

    public AuthWebhookEmitter(
            RestClient.Builder builder,
            OrgKeyService orgKeyService,
            @Value("${app.auth-webhooks.url:}") String url,
            @Value("${app.jwt.issuer:nexxauth}") String issuer,
            @Value("${app.auth-webhooks.connect-timeout-ms:1500}") int connectTimeoutMs,
            @Value("${app.auth-webhooks.read-timeout-ms:2500}") int readTimeoutMs
    ) {
        this.orgKeyService = orgKeyService;
        this.url = url == null ? "" : url.trim();
        this.issuer = issuer == null || issuer.isBlank() ? ISSUER : issuer;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = builder
                .requestFactory(requestFactory)
                .defaultHeader("X-Webhook-Source", "nexxauth")
                .build();
    }

    public boolean isConfigured() {
        return !url.isBlank();
    }

    public void userSignedIn(OrganisationUser user) {
        emit(user, "USER_SIGNED_IN", "LOGIN");
    }

    public void userSessionRevoked(OrganisationUser user, String reason) {
        emit(user, "USER_SESSION_REVOKED", reason == null || reason.isBlank() ? "LOGOUT" : reason);
    }

    private void emit(OrganisationUser user, String event, String reason) {
        if (!isConfigured() || user == null) return;
        Organisation organisation = user.getOrganisation();
        if (organisation == null || organisation.getId() == null || user.getId() == null) return;

        try {
            var signingKey = orgKeyService.activeKey(organisation);
            RSAPrivateKey privateKey = orgKeyService.privateKeyOf(signingKey);
            Instant now = Instant.now();
            String token = Jwts.builder()
                    .subject(String.valueOf(user.getId()))
                    .issuer(issuer)
                    .claim("type", WEBHOOK_TYPE)
                    .claim("orgId", organisation.getId())
                    .claim("orgSlug", organisation.getSlug())
                    .claim("event", event)
                    .claim("reason", reason)
                    .id(UUID.randomUUID().toString())
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(now.plusSeconds(60)))
                    .signWith(privateKey, Jwts.SIG.RS256)
                    .header().keyId(signingKey.getKid()).and()
                    .compact();

            Map<String, Object> body = Map.of(
                    "event", event,
                    "data", Map.of(
                            "userId", user.getId(),
                            "orgId", organisation.getId(),
                            "reason", reason
                    )
            );

            restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Auth webhook delivered: event={} user={}", event, user.getId());
        } catch (Exception e) {
            // Delivery is best-effort and must never break authentication.
            log.warn("Auth webhook delivery failed for event={} user={}: {}",
                    event, user.getId(), e.getMessage());
        }
    }
}
