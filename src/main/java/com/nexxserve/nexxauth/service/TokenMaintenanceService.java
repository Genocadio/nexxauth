package com.nexxserve.nexxauth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly maintenance for the rotating refresh-token tables. The scheduled job
 * lives here instead of on the token services so it can be switched off on all
 * but one instance of a multi-instance deployment
 * ({@code app.maintenance.token-cleanup-enabled=false}); the deletes are
 * idempotent, so running it on more than one instance is harmless, just
 * wasteful.
 */
@Component
@ConditionalOnProperty(name = "app.maintenance.token-cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class TokenMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(TokenMaintenanceService.class);

    private final RefreshTokenService refreshTokenService;
    private final OrganisationRefreshTokenService organisationRefreshTokenService;

    public TokenMaintenanceService(RefreshTokenService refreshTokenService,
                                   OrganisationRefreshTokenService organisationRefreshTokenService) {
        this.refreshTokenService = refreshTokenService;
        this.organisationRefreshTokenService = organisationRefreshTokenService;
    }

    /** Expired/revoked/evicted refresh tokens are purged daily at 03:00. */
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupExpiredTokens() {
        try {
            refreshTokenService.cleanupExpired();
            organisationRefreshTokenService.cleanupExpired();
        } catch (RuntimeException e) {
            log.warn("Token cleanup failed (will retry tomorrow)", e);
        }
    }
}
