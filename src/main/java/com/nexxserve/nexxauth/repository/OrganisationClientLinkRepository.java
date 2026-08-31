package com.nexxserve.nexxauth.repository;

import com.nexxserve.nexxauth.entity.OrganisationClientLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganisationClientLinkRepository extends JpaRepository<OrganisationClientLink, Long> {

    List<OrganisationClientLink> findByClientIdOrderByIdAsc(Long clientId);

    Optional<OrganisationClientLink> findByIdAndClientId(Long id, Long clientId);

    boolean existsByClientIdAndOriginIgnoreCase(Long clientId, String origin);

    /** All distinct origins where allowCors is true, for the preflight shortcut. */
    List<OrganisationClientLink> findByAllowCorsTrue();
}
