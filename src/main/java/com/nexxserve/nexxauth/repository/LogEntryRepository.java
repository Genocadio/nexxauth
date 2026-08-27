package com.nexxserve.nexxauth.repository;

import com.nexxserve.nexxauth.entity.LogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repository for log entries. Uses {@link JpaSpecificationExecutor} so
 * {@link com.nexxserve.nexxauth.service.LogService} can compose filters
 * dynamically without needing a query method for every combination.
 */
public interface LogEntryRepository extends JpaRepository<LogEntry, Long>, JpaSpecificationExecutor<LogEntry> {
}
