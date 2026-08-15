package com.nexxserve.nexxauth.repository;

import com.nexxserve.nexxauth.entity.PlatformUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, Long> {

    Optional<PlatformUser> findByEmail(String email);

    /** Loads the user with its platform eagerly, for per-request auth checks. */
    @EntityGraph(attributePaths = "platform")
    Optional<PlatformUser> findWithPlatformById(Long id);

    boolean existsByEmail(String email);

    List<PlatformUser> findByPlatformIdOrderByCreatedAtAsc(Long platformId);

    Optional<PlatformUser> findByIdAndPlatformId(Long id, Long platformId);

    long countByPlatformId(Long platformId);
}
