package com.nexxserve.nauth.repository;

import com.nexxserve.nauth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from RefreshToken rt where rt.revokedAt is not null or rt.expiresAt < :now")
    void deleteExpiredOrRevoked(@Param("now") Instant now);

    // No clearAutomatically: clearing the persistence context here would discard
    // pending entity changes (e.g. an unflushed password update) in the same tx.
    @Modifying
    @Query("""
            update RefreshToken rt set rt.revokedAt = :now
            where rt.platformUser.id = :userId and rt.revokedAt is null""")
    void revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
