package com.nexxserve.nexxauth.repository;

import com.nexxserve.nexxauth.entity.OrganisationRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface OrganisationRoleRepository extends JpaRepository<OrganisationRole, Long> {

    List<OrganisationRole> findByOrganisationIdOrderByCreatedAtAsc(Long organisationId);

    Optional<OrganisationRole> findByIdAndOrganisationId(Long id, Long organisationId);

    boolean existsByOrganisationIdAndName(Long organisationId, String name);

    /** Roles that belong to the given organisation (guards against cross-org assignment). */
    Set<OrganisationRole> findByIdInAndOrganisationId(Set<Long> ids, Long organisationId);

    /** Roles marked as default — new users of the organisation inherit them on
     * register. */
    List<OrganisationRole> findByOrganisationIdAndDefaultRoleTrue(Long organisationId);
}
