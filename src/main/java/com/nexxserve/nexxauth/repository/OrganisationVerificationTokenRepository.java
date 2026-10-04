package com.nexxserve.nexxauth.repository;

import com.nexxserve.nexxauth.entity.OrganisationVerificationToken;
import com.nexxserve.nexxauth.entity.VerificationChannel;
import com.nexxserve.nexxauth.entity.VerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface OrganisationVerificationTokenRepository extends JpaRepository<OrganisationVerificationToken, Long> {

    Optional<OrganisationVerificationToken>
    findFirstByOrganisationIdAndPurposeAndChannelAndIdentifierAndConsumedAtIsNullOrderByCreatedAtDesc(
            Long organisationId, VerificationPurpose purpose, VerificationChannel channel,
            String identifier);

    Optional<OrganisationVerificationToken> findFirstByTokenHashAndConsumedAtIsNull(String tokenHash);

    /** The active (unconsumed, unexpired) token for an identifier - the one a
     * submitted OTP or magic link is matched against (old tokens are discarded
     * when a new one is issued, so there is at most one anyway). */
    @Query("""
            select t from OrganisationVerificationToken t
            where t.organisation.id = :orgId
              and t.purpose = :purpose
              and t.channel = :channel
              and t.identifier = :identifier
              and t.consumedAt is null
              and t.expiresAt > :now
            order by t.createdAt desc
            """)
    Optional<OrganisationVerificationToken> findActive(
            @Param("orgId") Long orgId,
            @Param("purpose") VerificationPurpose purpose,
            @Param("channel") VerificationChannel channel,
            @Param("identifier") String identifier,
            @Param("now") Instant now);

    /* Declared @Modifying queries get no transaction from the repository proxy
     * (only inherited SimpleJpaRepository methods do), so without this they throw
     * TransactionRequiredException whenever a caller has no ambient transaction
     * - e.g. the nightly scheduled purge. */
    @Transactional
    @Modifying
    @Query("""
            delete from OrganisationVerificationToken t
            where t.organisation.id = :orgId
              and t.purpose = :purpose
              and t.channel = :channel
              and t.identifier = :identifier
              and t.consumedAt is null
            """)
    void deleteActiveForIdentifier(@Param("orgId") Long orgId,
                                   @Param("purpose") VerificationPurpose purpose,
                                   @Param("channel") VerificationChannel channel,
                                   @Param("identifier") String identifier);

    /* Transaction supplied here so the purge works regardless of caller. */
    @Transactional
    @Modifying
    @Query("delete from OrganisationVerificationToken t where t.expiresAt < :before")
    void deleteExpired(@Param("before") Instant before);
}