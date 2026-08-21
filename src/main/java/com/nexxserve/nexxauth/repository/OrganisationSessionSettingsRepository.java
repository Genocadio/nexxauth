package com.nexxserve.nexxauth.repository;

import com.nexxserve.nexxauth.entity.OrganisationSessionSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganisationSessionSettingsRepository extends JpaRepository<OrganisationSessionSettings, Long> {

    Optional<OrganisationSessionSettings> findByOrganisationId(Long organisationId);
}
