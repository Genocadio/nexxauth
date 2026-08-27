package com.nexxserve.nexxauth.repository;

import com.nexxserve.nexxauth.entity.OrganisationHealthHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OrganisationHealthHistoryRepository extends JpaRepository<OrganisationHealthHistory, Long> {

    /** Latest snapshot for an organisation. */
    Optional<OrganisationHealthHistory> findTopByOrganisationIdOrderBySnapshotDateDesc(Long organisationId);

    /** Snapshot closest to a target date for an organisation. */
    Optional<OrganisationHealthHistory> findTopByOrganisationIdAndSnapshotDateLessThanEqualOrderBySnapshotDateDesc(
            Long organisationId, LocalDate targetDate);

    /** All snapshots for an organisation, newest first. */
    List<OrganisationHealthHistory> findByOrganisationIdOrderBySnapshotDateDesc(Long organisationId);

    /** Check if a snapshot already exists for this org on this date. */
    boolean existsByOrganisationIdAndSnapshotDate(Long organisationId, LocalDate snapshotDate);
}
