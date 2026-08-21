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
}
