package com.nexxserve.nauth.repository;

import com.nexxserve.nauth.entity.OrganisationSigningKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganisationSigningKeyRepository extends JpaRepository<OrganisationSigningKey, Long> {

    Optional<OrganisationSigningKey> findByKid(String kid);

    Optional<OrganisationSigningKey> findByOrganisationIdAndActiveTrue(Long organisationId);

    List<OrganisationSigningKey> findByOrganisationIdOrderByCreatedAtAsc(Long organisationId);
}
