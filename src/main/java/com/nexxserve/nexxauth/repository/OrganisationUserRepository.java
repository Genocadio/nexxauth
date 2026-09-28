package com.nexxserve.nexxauth.repository;

import com.nexxserve.nexxauth.entity.OrganisationUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrganisationUserRepository extends JpaRepository<OrganisationUser, Long> {

    @EntityGraph(attributePaths = {"roles", "emails", "phones"})
    List<OrganisationUser> findByOrganisationIdOrderByCreatedAtAsc(Long organisationId);

    @EntityGraph(attributePaths = {"roles", "emails", "phones"})
    Optional<OrganisationUser> findByIdAndOrganisationId(Long id, Long organisationId);

    /** Auth lookup: roles + their permissions eagerly loaded so the org token
     * can carry fresh permissions computed outside a transaction. */
    @EntityGraph(attributePaths = {"roles.permissions", "organisation", "emails", "phones"})
    Optional<OrganisationUser> findWithRolesById(Long id);

    /** Auth lookup by the org's login identifier (username, or email when the
     * organisation uses email as username). */
    @EntityGraph(attributePaths = {"roles.permissions", "organisation", "emails", "phones"})
    Optional<OrganisationUser> findWithRolesByOrganisationIdAndUsername(Long organisationId, String username);

    @Query("SELECT u FROM OrganisationUser u JOIN u.emails e WHERE u.organisation.id = :organisationId AND LOWER(e.email) = LOWER(:email)")
    @EntityGraph(attributePaths = {"roles.permissions", "organisation", "emails", "phones"})
    Optional<OrganisationUser> findWithRolesByOrganisationIdAndEmail(@Param("organisationId") Long organisationId,
                                                                    @Param("email") String email);

    @Query("SELECT u FROM OrganisationUser u JOIN u.phones p WHERE u.organisation.id = :organisationId AND p.phone = :phone")
    @EntityGraph(attributePaths = {"roles.permissions", "organisation", "emails", "phones"})
    Optional<OrganisationUser> findWithRolesByOrganisationIdAndPhone(@Param("organisationId") Long organisationId,
                                                                    @Param("phone") String phone);

    @Query("SELECT u FROM OrganisationUser u JOIN u.emails e WHERE u.organisation.id = :organisationId AND LOWER(e.email) = LOWER(:email)")
    Optional<OrganisationUser> findByOrganisationIdAndEmail(@Param("organisationId") Long organisationId,
                                                            @Param("email") String email);

    @Query("SELECT u FROM OrganisationUser u JOIN u.phones p WHERE u.organisation.id = :organisationId AND p.phone = :phone")
    Optional<OrganisationUser> findByOrganisationIdAndPhone(@Param("organisationId") Long organisationId,
                                                            @Param("phone") String phone);

    Optional<OrganisationUser> findByOrganisationIdAndUsername(Long organisationId, String username);

    boolean existsByOrganisationIdAndUsername(Long organisationId, String username);

    @Query("SELECT COUNT(e) > 0 FROM OrganisationUserEmail e WHERE e.organisation.id = :organisationId AND LOWER(e.email) = LOWER(:email)")
    boolean existsByOrganisationIdAndEmail(@Param("organisationId") Long organisationId,
                                           @Param("email") String email);

    @Query("SELECT COUNT(p) > 0 FROM OrganisationUserPhone p WHERE p.organisation.id = :organisationId AND p.phone = :phone")
    boolean existsByOrganisationIdAndPhone(@Param("organisationId") Long organisationId,
                                           @Param("phone") String phone);

    long countByOrganisationId(Long organisationId);
}
