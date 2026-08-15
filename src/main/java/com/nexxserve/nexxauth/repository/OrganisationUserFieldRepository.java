package com.nexxserve.nexxauth.repository;

import com.nexxserve.nexxauth.entity.OrganisationUserField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganisationUserFieldRepository extends JpaRepository<OrganisationUserField, Long> {

    List<OrganisationUserField> findByOrganisationIdOrderByKeyAsc(Long organisationId);

    /** All configured fields, for metadata validation. */
    List<OrganisationUserField> findByOrganisationId(Long organisationId);

    /** Login resolution order: fields enabled for login, by key. */
    List<OrganisationUserField> findByOrganisationIdAndLoginEnabledTrueOrderByKeyAsc(Long organisationId);

    Optional<OrganisationUserField> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<OrganisationUserField> findByOrganisationIdAndKey(Long organisationId, String key);

    boolean existsByOrganisationIdAndKey(Long organisationId, String key);
}
