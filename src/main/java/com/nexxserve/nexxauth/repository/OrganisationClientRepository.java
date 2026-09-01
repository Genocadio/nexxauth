package com.nexxserve.nexxauth.repository;

import com.nexxserve.nexxauth.entity.OrganisationClient;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganisationClientRepository extends JpaRepository<OrganisationClient, Long> {

    List<OrganisationClient> findByOrganisationIdOrderByNameAsc(Long organisationId);

    /** Fetch the client with its organisation and platform eagerly — the
     *  organisation is always needed for URL rewriting, auth enforcement, and
     *  scope checks. Without this, the lazy proxy breaks when the entity is
     *  cached (detached from the persistence context). */
    @EntityGraph(attributePaths = {"organisation", "organisation.platform"})
    Optional<OrganisationClient> findByClientKey(String clientKey);

    Optional<OrganisationClient> findByClientKeyAndOrganisationId(String clientKey, Long organisationId);

    List<OrganisationClient> findByEnabledTrue();

    List<OrganisationClient> findByClientKeyIsNull();

    boolean existsByOrganisationIdAndName(Long organisationId, String name);

    long countByOrganisationId(Long organisationId);

    List<OrganisationClient> findByOrganisationIdAndClientKeyIn(Long organisationId, List<String> clientKeys);
}
