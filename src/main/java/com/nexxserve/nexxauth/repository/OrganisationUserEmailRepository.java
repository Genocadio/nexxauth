package com.nexxserve.nexxauth.repository;

import com.nexxserve.nexxauth.entity.OrganisationUserEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganisationUserEmailRepository extends JpaRepository<OrganisationUserEmail, Long> {

    List<OrganisationUserEmail> findByUserId(Long userId);

    Optional<OrganisationUserEmail> findByOrganisationIdAndEmailIgnoreCase(Long organisationId, String email);

    boolean existsByOrganisationIdAndEmailIgnoreCase(Long organisationId, String email);
}
