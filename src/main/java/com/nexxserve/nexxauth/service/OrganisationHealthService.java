package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.dto.response.OrganisationHealthResponse;
import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.OrganisationHealthHistory;
import com.nexxserve.nexxauth.exception.ResourceNotFoundException;
import com.nexxserve.nexxauth.repository.OrganisationClientRepository;
import com.nexxserve.nexxauth.repository.OrganisationHealthHistoryRepository;
import com.nexxserve.nexxauth.repository.OrganisationRefreshTokenRepository;
import com.nexxserve.nexxauth.repository.OrganisationRepository;
import com.nexxserve.nexxauth.repository.OrganisationSigningKeyRepository;
import com.nexxserve.nexxauth.repository.OrganisationUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Computes a lightweight health score for each organisation. The score is
 * a 0-100 value based on:
 *   - Has users (25 pts)
 *   - Has active sessions (25 pts)
 *   - Has signing keys (25 pts)
 *   - Has API clients (25 pts)
 *
 * Snapshots are recorded daily so the UI can show a trend arrow comparing
 * today's score to ~7 days ago.
 */
@Service
public class OrganisationHealthService {

    private final OrganisationRepository organisationRepository;
    private final OrganisationUserRepository userRepository;
    private final OrganisationRefreshTokenRepository refreshTokenRepository;
    private final OrganisationSigningKeyRepository signingKeyRepository;
    private final OrganisationClientRepository clientRepository;
    private final OrganisationHealthHistoryRepository healthHistoryRepository;

    public OrganisationHealthService(OrganisationRepository organisationRepository,
                                      OrganisationUserRepository userRepository,
                                      OrganisationRefreshTokenRepository refreshTokenRepository,
                                      OrganisationSigningKeyRepository signingKeyRepository,
                                      OrganisationClientRepository clientRepository,
                                      OrganisationHealthHistoryRepository healthHistoryRepository) {
        this.organisationRepository = organisationRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.signingKeyRepository = signingKeyRepository;
        this.clientRepository = clientRepository;
        this.healthHistoryRepository = healthHistoryRepository;
    }

    @Transactional
    public List<OrganisationHealthResponse> healthAll(Long platformId) {
        List<Organisation> orgs = organisationRepository.findByPlatformId(platformId);
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();
        return orgs.stream()
                .map(org -> computeHealth(org, now, today))
                .toList();
    }

    @Transactional
    public OrganisationHealthResponse health(Long organisationId) {
        Organisation org = organisationRepository.findById(organisationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Organisation", organisationId));
        return computeHealth(org, Instant.now(), LocalDate.now());
    }

    private OrganisationHealthResponse computeHealth(Organisation org, Instant now, LocalDate today) {
        long users = userRepository.countByOrganisationId(org.getId());
        long keys = signingKeyRepository.countByOrganisationId(org.getId());
        long clients = clientRepository.countByOrganisationId(org.getId());
        long activeSessions = refreshTokenRepository.countActiveByOrganisationId(org.getId(), now);

        int score = 0;
        if (users > 0) score += 25;
        if (activeSessions > 0) score += 25;
        if (keys > 0) score += 25;
        if (clients > 0) score += 25;

        // Record today's snapshot (upsert by org + date)
        recordSnapshot(org, score, (int) users, (int) activeSessions, (int) keys, (int) clients, today);

        // Get previous score (~7 days ago) for trend
        LocalDate weekAgo = today.minusDays(7);
        Integer previousScore = healthHistoryRepository
                .findTopByOrganisationIdAndSnapshotDateLessThanEqualOrderBySnapshotDateDesc(org.getId(), weekAgo)
                .map(OrganisationHealthHistory::getScore)
                .orElse(null);

        String trend = null;
        if (previousScore != null) {
            trend = Integer.compare(score, previousScore) > 0 ? "UP"
                    : Integer.compare(score, previousScore) < 0 ? "DOWN"
                    : "SAME";
        }

        return new OrganisationHealthResponse(
                org.getId(),
                (int) users,
                (int) activeSessions,
                (int) keys,
                (int) clients,
                score,
                previousScore,
                trend
        );
    }

    private void recordSnapshot(Organisation org, int score, int users, int sessions,
                                 int keys, int clients, LocalDate today) {
        if (healthHistoryRepository.existsByOrganisationIdAndSnapshotDate(org.getId(), today)) {
            return; // already recorded today
        }
        OrganisationHealthHistory snapshot = new OrganisationHealthHistory();
        snapshot.setOrganisation(org);
        snapshot.setScore(score);
        snapshot.setUserCount(users);
        snapshot.setActiveSessions(sessions);
        snapshot.setSigningKeys(keys);
        snapshot.setApiClients(clients);
        snapshot.setSnapshotDate(today);
        healthHistoryRepository.save(snapshot);
    }
}
