package com.nexxserve.nexxauth.repository;

import com.nexxserve.nexxauth.entity.OrganisationClient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganisationClientRepository extends JpaRepository<OrganisationClient, Long> {

    List<OrganisationClient> findByOrganisationIdOrderByNameAsc(Long organisationId);

    Optional<OrganisationClient> findByClientKey(String clientKey);

    Optional<OrganisationClient> findByClientKeyAndOrganisationId(String clientKey, Long organisationId);

    List<OrganisationClient> findByEnabledTrue();

    List<OrganisationClient> findByClientKeyIsNull();

    boolean existsByOrganisationIdAndName(Long organisationId, String name);

    long countByOrganisationId(Long organisationId);
}
