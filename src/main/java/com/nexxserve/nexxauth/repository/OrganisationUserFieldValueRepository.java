package com.nexxserve.nexxauth.repository;

import com.nexxserve.nexxauth.entity.OrganisationUserFieldValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrganisationUserFieldValueRepository extends JpaRepository<OrganisationUserFieldValue, Long> {

    List<OrganisationUserFieldValue> findByUserId(Long userId);

    /** Batch metadata read for lists of users (avoids N+1). */
    List<OrganisationUserFieldValue> findByUserIdIn(Collection<Long> userIds);

    Optional<OrganisationUserFieldValue> findByUserIdAndFieldKey(Long userId, String fieldKey);

    List<OrganisationUserFieldValue> findByOrganisationIdAndFieldKey(Long organisationId, String fieldKey);

    boolean existsByOrganisationIdAndFieldKey(Long organisationId, String fieldKey);

    /** Uniqueness of a value on a login-enabled field, excluding the owner. */
    boolean existsByOrganisationIdAndFieldKeyAndFieldValueAndUserIdNot(
            Long organisationId, String fieldKey, String fieldValue, Long userId);

    /** Case-insensitive variant for login-enabled STRING fields. */
    @Query("select case when count(v) > 0 then true else false end from OrganisationUserFieldValue v "
            + "where v.organisationId = :organisationId and v.fieldKey = :fieldKey "
            + "and lower(v.fieldValue) = lower(:fieldValue) and v.userId <> :userId")
    boolean existsByOrganisationIdAndFieldKeyAndFieldValueIgnoreCaseAndUserIdNot(
            @Param("organisationId") Long organisationId,
            @Param("fieldKey") String fieldKey,
            @Param("fieldValue") String fieldValue,
            @Param("userId") Long userId);

    @Query("select v.userId from OrganisationUserFieldValue v "
            + "where v.organisationId = :organisationId and v.fieldKey = :fieldKey and v.fieldValue = :fieldValue")
    List<Long> findUserIdsByOrganisationIdAndFieldKeyAndFieldValue(
            @Param("organisationId") Long organisationId,
            @Param("fieldKey") String fieldKey,
            @Param("fieldValue") String fieldValue);

    /** Case-insensitive variant for login-enabled STRING fields. */
    @Query("select v.userId from OrganisationUserFieldValue v "
            + "where v.organisationId = :organisationId and v.fieldKey = :fieldKey "
            + "and lower(v.fieldValue) = lower(:fieldValue)")
    List<Long> findUserIdsByOrganisationIdAndFieldKeyAndFieldValueIgnoreCase(
            @Param("organisationId") Long organisationId,
            @Param("fieldKey") String fieldKey,
            @Param("fieldValue") String fieldValue);

    void deleteByOrganisationIdAndFieldKey(Long organisationId, String fieldKey);
}
