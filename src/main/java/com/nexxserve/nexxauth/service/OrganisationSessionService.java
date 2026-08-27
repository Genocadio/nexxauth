package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.dto.response.OrganisationSessionResponse;
import com.nexxserve.nexxauth.dto.response.SessionTimelineEvent;
import com.nexxserve.nexxauth.entity.OrganisationRefreshToken;
import com.nexxserve.nexxauth.entity.OrganisationUser;
import com.nexxserve.nexxauth.exception.BadRequestException;
import com.nexxserve.nexxauth.exception.ResourceNotFoundException;
import com.nexxserve.nexxauth.repository.OrganisationRefreshTokenRepository;
import com.nexxserve.nexxauth.repository.OrganisationUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages sessions for organisation users. A "session" is a group of refresh
 * tokens sharing the same {@code sessionId} UUID — the original login's
 * IP + user-agent. On token rotation the session id is carried forward so the
 * session remains trackable. Same-IP re-login reuses the existing session
 * instead of creating a duplicate.
 */
@Service
public class OrganisationSessionService {

    private final OrganisationRefreshTokenRepository refreshTokenRepository;
    private final OrganisationUserRepository userRepository;

    public OrganisationSessionService(OrganisationRefreshTokenRepository refreshTokenRepository,
                                       OrganisationUserRepository userRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    /**
     * Generate a new session UUID for a fresh login.
     */
    public String newSessionId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Check if the user already has an active session with the same IP and
     * user-agent. If so, return the existing session id so the new token joins
     * that session (re-login behaviour). Otherwise return {@code null}.
     */
    @Transactional(readOnly = true)
    public String findExistingSessionId(Long userId, String ipAddress, String userAgent) {
        if (ipAddress == null || userAgent == null) return null;
        List<OrganisationRefreshToken> existing =
                refreshTokenRepository.findActiveByUserAndIpAndAgent(userId, ipAddress, userAgent, Instant.now());
        return existing.isEmpty() ? null : existing.getFirst().getSessionId();
    }

    /**
     * List all sessions for an organisation, optionally filtered by user id
     * and/or client key. Pass {@code null} for any filter to skip it.
     * Use {@code clientKey="__none__"} to match sessions with no client.
     * Returns sessions sorted by most recently active first.
     */
    @Transactional(readOnly = true)
    public List<OrganisationSessionResponse> listSessions(Long organisationId, Long userId, String clientKey) {
        Instant now = Instant.now();
        List<OrganisationRefreshToken> tokens;
        if (userId != null) {
            tokens = refreshTokenRepository.findActiveByOrganisationIdAndUserId(organisationId, userId, now);
        } else {
            tokens = refreshTokenRepository.findActiveByOrganisationId(organisationId, now);
        }
        List<OrganisationSessionResponse> sessions = aggregateSessions(tokens);

        // Filter by client key if requested
        if (clientKey != null) {
            if ("__none__".equals(clientKey)) {
                sessions = sessions.stream()
                        .filter(s -> s.clientKey() == null)
                        .toList();
            } else {
                sessions = sessions.stream()
                        .filter(s -> clientKey.equals(s.clientKey()))
                        .toList();
            }
        }

        return sessions;
    }

    /**
     * Revoke a single session (all its tokens) and return the session id
     * that was revoked, or throw if not found.
     */
    @Transactional
    public String revokeSession(Long organisationId, String sessionId, Long operatorUserId) {
        // Verify the session belongs to this organisation
        List<OrganisationRefreshToken> tokens = refreshTokenRepository.findBySessionId(sessionId);
        boolean belongsToOrg = tokens.stream()
                .anyMatch(t -> t.getOrganisationUser().getOrganisation().getId().equals(organisationId));
        if (!belongsToOrg || tokens.isEmpty()) {
            throw ResourceNotFoundException.of("Session", sessionId);
        }

        Instant now = Instant.now();
        refreshTokenRepository.revokeBySessionId(sessionId, now);
        return sessionId;
    }

    /**
     * Get the token-rotation timeline for a single session. Returns all tokens
     * (including revoked/expired) ordered by creation time, so the UI can draw
     * a chronological activity strip.
     */
    @Transactional(readOnly = true)
    public List<SessionTimelineEvent> sessionTimeline(Long organisationId, String sessionId) {
        List<OrganisationRefreshToken> tokens = refreshTokenRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        if (tokens.isEmpty()) {
            throw ResourceNotFoundException.of("Session", sessionId);
        }
        // Verify the session belongs to this organisation
        boolean belongsToOrg = tokens.stream()
                .anyMatch(t -> t.getOrganisationUser().getOrganisation().getId().equals(organisationId));
        if (!belongsToOrg) {
            throw ResourceNotFoundException.of("Session", sessionId);
        }
        return tokens.stream()
                .map(t -> new SessionTimelineEvent(
                        t.getCreatedAt(),
                        t.getExpiresAt(),
                        t.getRevokedAt(),
                        t.getEvictedAt(),
                        !t.isRevoked() && !t.isEvicted() && !t.isExpired(),
                        t.getClientKey()
                ))
                .toList();
    }

    /**
     * Revoke all sessions for a user within an organisation.
     */
    @Transactional
    public int revokeAllUserSessions(Long organisationId, Long userId) {
        // Verify user belongs to this org
        userRepository.findById(userId)
                .filter(u -> u.getOrganisation().getId().equals(organisationId))
                .orElseThrow(() -> ResourceNotFoundException.of("OrganisationUser", userId));

        Instant now = Instant.now();
        List<OrganisationRefreshToken> active =
                refreshTokenRepository.findActiveByUserIdOrderByExpiresAtAsc(userId, now);
        int count = 0;
        for (OrganisationRefreshToken token : active) {
            if (token.getSessionId() != null) {
                refreshTokenRepository.revokeBySessionId(token.getSessionId(), now);
                count++;
            }
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Aggregate a flat list of tokens into sessions grouped by sessionId.
     */
    private List<OrganisationSessionResponse> aggregateSessions(List<OrganisationRefreshToken> tokens) {
        Instant now = Instant.now();
        // Group by sessionId, preserving insertion order
        Map<String, List<OrganisationRefreshToken>> grouped = new LinkedHashMap<>();
        for (OrganisationRefreshToken token : tokens) {
            String sid = token.getSessionId();
            if (sid == null) continue; // legacy tokens without session id
            grouped.computeIfAbsent(sid, k -> new ArrayList<>()).add(token);
        }

        List<OrganisationSessionResponse> sessions = new ArrayList<>();
        for (Map.Entry<String, List<OrganisationRefreshToken>> entry : grouped.entrySet()) {
            List<OrganisationRefreshToken> sessionTokens = entry.getValue();
            // Use the first token (most recent) for metadata
            OrganisationRefreshToken primary = sessionTokens.getFirst();
            OrganisationUser user = primary.getOrganisationUser();

            Instant earliest = sessionTokens.stream()
                    .map(OrganisationRefreshToken::getCreatedAt)
                    .min(Instant::compareTo)
                    .orElse(primary.getCreatedAt());

            Instant latestActivity = sessionTokens.stream()
                    .map(t -> t.getRevokedAt() != null ? t.getRevokedAt() : t.getCreatedAt())
                    .max(Instant::compareTo)
                    .orElse(primary.getCreatedAt());

            Instant latestExpiry = sessionTokens.stream()
                    .map(OrganisationRefreshToken::getExpiresAt)
                    .max(Instant::compareTo)
                    .orElse(primary.getExpiresAt());

            boolean anyActive = sessionTokens.stream()
                    .anyMatch(t -> !t.isRevoked() && !t.isEvicted() && !t.isExpired());

            String identifier = user.getUsername() != null ? user.getUsername()
                    : user.getEmail() != null ? user.getEmail()
                    : user.getPhone() != null ? user.getPhone() : "unknown";

            // Use the client key from the most recent token in the session
            String clientKey = sessionTokens.stream()
                    .map(OrganisationRefreshToken::getClientKey)
                    .filter(k -> k != null && !k.isBlank())
                    .findFirst()
                    .orElse(null);

            sessions.add(new OrganisationSessionResponse(
                    entry.getKey(),
                    user.getId(),
                    identifier,
                    primary.getIpAddress(),
                    primary.getUserAgent(),
                    clientKey,
                    earliest,
                    latestActivity,
                    latestExpiry,
                    anyActive,
                    sessionTokens.size()
            ));
        }

        // Sort: active sessions first, then by last activity descending
        sessions.sort((a, b) -> {
            if (a.active() != b.active()) return a.active() ? -1 : 1;
            return b.lastActivityAt().compareTo(a.lastActivityAt());
        });

        return sessions;
    }
}
