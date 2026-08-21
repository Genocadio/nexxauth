package com.nexxserve.nauth.repository;

import com.nexxserve.nauth.entity.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformRepository extends JpaRepository<Platform, Long> {

    Optional<Platform> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
