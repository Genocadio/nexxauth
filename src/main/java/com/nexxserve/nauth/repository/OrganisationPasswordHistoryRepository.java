package com.nexxserve.nauth.repository;

import com.nexxserve.nauth.entity.OrganisationPasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrganisationPasswordHistoryRepository extends JpaRepository<OrganisationPasswordHistory, Long> {

    List<OrganisationPasswordHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByUserIdAndIdNotIn(Long userId, java.util.Collection<Long> keepIds);
}
