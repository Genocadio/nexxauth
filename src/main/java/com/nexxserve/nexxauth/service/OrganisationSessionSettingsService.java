package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.dto.request.UpdateOrganisationSessionSettingsRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationSessionSettingsResponse;
import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.OrganisationSessionSettings;
import com.nexxserve.nexxauth.exception.BadRequestException;
import com.nexxserve.nexxauth.repository.OrganisationSessionSettingsRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * Organisation-level session settings: access/refresh token lifetimes and the
 * maximum number of concurrent sessions per org user. One settings row per
 * organisation (created lazily with defaults matching {@code app.jwt.*}); the
 * values are applied whenever org tokens are issued. Platform auth is
 * untouched: this is organisation-scoped only.
 */
@Service
public class OrganisationSessionSettingsService {

    private final OrganisationSessionSettingsRepository settingsRepository;
    private final EntityManager entityManager;

    public OrganisationSessionSettingsService(OrganisationSessionSettingsRepository settingsRepository,
                                              EntityManager entityManager) {
        this.settingsRepository = settingsRepository;
        this.entityManager = entityManager;
    }

    /** The organisation's settings, lazily created with defaults if missing. The
     * creation is double-checked under a lock on the organisation row so two
     * concurrent first accesses cannot race into a unique-constraint 409. */
    @Transactional
    public OrganisationSessionSettings settingsOf(Organisation organisation) {
        return settingsRepository.findByOrganisationId(organisation.getId())
                .orElseGet(() -> createSettings(organisation));
    }

    private OrganisationSessionSettings createSettings(Organisation organisation) {
        // Serialize concurrent lazy creation for this organisation on its own
        // (always present) row, then re-check: the other transaction has either
        // committed its insert or is waiting behind us.
        entityManager.lock(entityManager.merge(organisation), LockModeType.PESSIMISTIC_WRITE);
        return settingsRepository.findByOrganisationId(organisation.getId())
                .orElseGet(() -> {
                    OrganisationSessionSettings settings = new OrganisationSessionSettings();
                    settings.setOrganisation(organisation);
                    return settingsRepository.save(settings);
                });
    }

    /**
     * Not read-only: a fresh org has no settings row yet, so this lazily
     * creates the defaults (Postgres rejects inserts in read-only transactions).
     */
    @Transactional
    public OrganisationSessionSettingsResponse get(Organisation organisation) {
        return toResponse(settingsOf(organisation));
    }

    @Transactional
    public OrganisationSessionSettingsResponse update(Organisation organisation,
                                                      UpdateOrganisationSessionSettingsRequest request) {
        OrganisationSessionSettings settings = settingsOf(organisation);
        if (request.accessTokenTtlSeconds() != null) {
            settings.setAccessTokenTtlSeconds(request.accessTokenTtlSeconds());
        }
        if (request.refreshTokenTtlSeconds() != null) {
            settings.setRefreshTokenTtlSeconds(request.refreshTokenTtlSeconds());
        }
        if (request.maxSessionsPerUser() != null) {
            settings.setMaxSessionsPerUser(request.maxSessionsPerUser());
        }
        validate(settings);
        return toResponse(settingsRepository.save(settings));
    }

    /** Access-token lifetime applied when issuing org access tokens. */
    @Transactional
    public Duration accessTokenTtl(Organisation organisation) {
        return Duration.ofSeconds(settingsOf(organisation).getAccessTokenTtlSeconds());
    }

    /** Refresh-token lifetime applied when issuing/rotating org refresh tokens. */
    @Transactional
    public Duration refreshTokenTtl(Organisation organisation) {
        return Duration.ofSeconds(settingsOf(organisation).getRefreshTokenTtlSeconds());
    }

    /** Concurrent-session limit for an org user ({@code >= 1}). */
    @Transactional
    public int maxSessionsPerUser(Organisation organisation) {
        return settingsOf(organisation).getMaxSessionsPerUser();
    }

    private void validate(OrganisationSessionSettings settings) {
        if (settings.getRefreshTokenTtlSeconds() <= settings.getAccessTokenTtlSeconds()) {
            throw new BadRequestException("Refresh token TTL must be longer than the access token TTL");
        }
    }

    private OrganisationSessionSettingsResponse toResponse(OrganisationSessionSettings settings) {
        return new OrganisationSessionSettingsResponse(
                settings.getAccessTokenTtlSeconds(),
                settings.getRefreshTokenTtlSeconds(),
                settings.getMaxSessionsPerUser());
    }
}
