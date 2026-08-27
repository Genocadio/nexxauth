package com.nexxserve.nexxauth.repository;

import com.nexxserve.nexxauth.entity.OrganisationRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrganisationRefreshTokenRepository extends JpaRepository<OrganisationRefreshToken, Long> {

    Optional<OrganisationRefreshToken> findByTokenHash(String tokenHash);

    /** Live sessions of a user (not revoked, not evicted, not expired), oldest first. */
    @Query("""
            select rt from OrganisationRefreshToken rt
            where rt.organisationUser.id = :userId and rt.revokedAt is null and rt.evictedAt is null
              and rt.expiresAt > :now
            order by rt.expiresAt asc""")
    List<OrganisationRefreshToken> findActiveByUserIdOrderByExpiresAtAsc(@Param("userId") Long userId,
                                                                         @Param("now") Instant now);

    @Modifying
    @Query("""
            delete from OrganisationRefreshToken rt
            where rt.revokedAt is not null or rt.evictedAt is not null or rt.expiresAt < :now""")
    void deleteExpiredOrRevoked(@Param("now") Instant now);

    // No clearAutomatically: clearing the persistence context here would discard
    // pending entity changes in the same tx.
    @Modifying
    @Query("""
            update OrganisationRefreshToken rt set rt.revokedAt = :now
            where rt.organisationUser.id = :userId and rt.revokedAt is null""")
    void revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    // -------------------------------------------------------------------------
    // Session management queries
    // -------------------------------------------------------------------------

    /** All tokens (any state) for a given session id. */
    List<OrganisationRefreshToken> findBySessionId(String sessionId);

    /** Active tokens for a given session id (not revoked, not evicted, not expired). */
    @Query("""
            select rt from OrganisationRefreshToken rt
            where rt.sessionId = :sessionId and rt.revokedAt is null and rt.evictedAt is null
              and rt.expiresAt > :now""")
    List<OrganisationRefreshToken> findActiveBySessionId(@Param("sessionId") String sessionId,
                                                         @Param("now") Instant now);

    /** Find existing active session by user + IP + user-agent for dedup. */
    @Query("""
            select rt from OrganisationRefreshToken rt
            where rt.organisationUser.id = :userId
              and rt.ipAddress = :ipAddress
              and rt.userAgent = :userAgent
              and rt.sessionId is not null
              and rt.revokedAt is null and rt.evictedAt is null
              and rt.expiresAt > :now
            order by rt.createdAt desc""")
    List<OrganisationRefreshToken> findActiveByUserAndIpAndAgent(
            @Param("userId") Long userId,
            @Param("ipAddress") String ipAddress,
            @Param("userAgent") String userAgent,
            @Param("now") Instant now);

    /** All active tokens for a user within an organisation. */
    @Query("""
            select rt from OrganisationRefreshToken rt
            where rt.organisationUser.organisation.id = :organisationId
              and rt.revokedAt is null and rt.evictedAt is null
              and rt.expiresAt > :now
            order by rt.sessionId, rt.expiresAt desc""")
    List<OrganisationRefreshToken> findActiveByOrganisationId(@Param("organisationId") Long organisationId,
                                                              @Param("now") Instant now);

    /** All active tokens for a specific user within an organisation. */
    @Query("""
            select rt from OrganisationRefreshToken rt
            where rt.organisationUser.organisation.id = :organisationId
              and rt.organisationUser.id = :userId
              and rt.revokedAt is null and rt.evictedAt is null
              and rt.expiresAt > :now
            order by rt.sessionId, rt.expiresAt desc""")
    List<OrganisationRefreshToken> findActiveByOrganisationIdAndUserId(
            @Param("organisationId") Long organisationId,
            @Param("userId") Long userId,
            @Param("now") Instant now);

    /** Revoke all tokens for a session. */
    @Modifying
    @Query("""
            update OrganisationRefreshToken rt set rt.revokedAt = :now
            where rt.sessionId = :sessionId and rt.revokedAt is null""")
    void revokeBySessionId(@Param("sessionId") String sessionId, @Param("now") Instant now);

    /** Revoke all tokens for a session that belong to a specific user. */
    @Modifying
    @Query("""
            update OrganisationRefreshToken rt set rt.revokedAt = :now
            where rt.sessionId = :sessionId
              and rt.organisationUser.id = :userId
              and rt.revokedAt is null""")
    void revokeBySessionIdAndUserId(@Param("sessionId") String sessionId,
                                    @Param("userId") Long userId,
                                    @Param("now") Instant now);

    /** Count active sessions (distinct session_ids) for a user in an org. */
    @Query("""
            select count(distinct rt.sessionId) from OrganisationRefreshToken rt
            where rt.organisationUser.id = :userId
              and rt.sessionId is not null
              and rt.revokedAt is null and rt.evictedAt is null
              and rt.expiresAt > :now""")
    long countActiveSessionsByUserId(@Param("userId") Long userId, @Param("now") Instant now);

    /** All tokens for a session, including revoked/expired, ordered by creation. */
    List<OrganisationRefreshToken> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    /** Count active tokens for an organisation (not revoked, not evicted, not expired). */
    @Query("""
            select count(rt) from OrganisationRefreshToken rt
            where rt.organisationUser.organisation.id = :organisationId
              and rt.revokedAt is null and rt.evictedAt is null
              and rt.expiresAt > :now""")
    long countActiveByOrganisationId(@Param("organisationId") Long organisationId, @Param("now") Instant now);
}
