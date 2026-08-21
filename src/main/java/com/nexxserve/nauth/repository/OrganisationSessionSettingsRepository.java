package com.nexxserve.nauth.repository;

import com.nexxserve.nauth.entity.OrganisationSessionSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganisationSessionSettingsRepository extends JpaRepository<OrganisationSessionSettings, Long> {

    Optional<OrganisationSessionSettings> findByOrganisationId(Long organisationId);
}
