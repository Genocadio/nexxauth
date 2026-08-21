package com.nexxserve.nauth.repository;

import com.nexxserve.nauth.entity.OrganisationUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganisationUserRepository extends JpaRepository<OrganisationUser, Long> {

    @EntityGraph(attributePaths = "roles")
    List<OrganisationUser> findByOrganisationIdOrderByCreatedAtAsc(Long organisationId);

    @EntityGraph(attributePaths = "roles")
    Optional<OrganisationUser> findByIdAndOrganisationId(Long id, Long organisationId);

    /** Auth lookup: roles + their permissions eagerly loaded so the org token
     * can carry fresh permissions computed outside a transaction. */
    @EntityGraph(attributePaths = {"roles.permissions", "organisation"})
    Optional<OrganisationUser> findWithRolesById(Long id);

    /** Auth lookup by the org's login identifier (username, or email when the
     * organisation uses email as username). */
    @EntityGraph(attributePaths = {"roles.permissions", "organisation"})
    Optional<OrganisationUser> findWithRolesByOrganisationIdAndUsername(Long organisationId, String username);

    @EntityGraph(attributePaths = {"roles.permissions", "organisation"})
    Optional<OrganisationUser> findWithRolesByOrganisationIdAndEmail(Long organisationId, String email);

    @EntityGraph(attributePaths = {"roles.permissions", "organisation"})
    Optional<OrganisationUser> findWithRolesByOrganisationIdAndPhone(Long organisationId, String phone);

    boolean existsByOrganisationIdAndUsername(Long organisationId, String username);

    boolean existsByOrganisationIdAndEmail(Long organisationId, String email);

    boolean existsByOrganisationIdAndPhone(Long organisationId, String phone);
}
