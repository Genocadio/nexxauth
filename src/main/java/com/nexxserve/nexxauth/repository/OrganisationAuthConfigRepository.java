package com.nexxserve.nexxauth.repository;

import com.nexxserve.nexxauth.entity.OrganisationAuthConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganisationAuthConfigRepository extends JpaRepository<OrganisationAuthConfig, Long> {

    Optional<OrganisationAuthConfig> findByOrganisationId(Long organisationId);
}
