package com.nexxserve.nexxauth.repository;

import com.nexxserve.nexxauth.entity.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganisationRepository extends JpaRepository<Organisation, Long> {

    List<Organisation> findByPlatformIdOrderByCreatedAtAsc(Long platformId);

    List<Organisation> findByPlatformId(Long platformId);

    Optional<Organisation> findByPlatformIdAndSlug(Long platformId, String slug);

    boolean existsByPlatformIdAndSlug(Long platformId, String slug);
}
