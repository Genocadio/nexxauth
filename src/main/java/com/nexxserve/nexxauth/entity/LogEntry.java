package com.nexxserve.nexxauth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A persisted, queryable log entry. Security-relevant events (auth success/
 * failure, key rotation, etc.) are written here by {@link
 * com.nexxserve.nexxauth.service.LogService} and broadcast to connected SSE
 * clients in real time.
 */
@Getter
@Setter
@Entity
@Table(name = "log_entries", indexes = {
        @Index(name = "idx_log_entries_platform", columnList = "platform_id"),
        @Index(name = "idx_log_entries_organisation", columnList = "organisation_id"),
        @Index(name = "idx_log_entries_level", columnList = "level"),
        @Index(name = "idx_log_entries_event_type", columnList = "event_type"),
        @Index(name = "idx_log_entries_created_at", columnList = "created_at"),
    @Index(name = "idx_log_entries_category", columnList = "category"),
})
public class LogEntry extends BaseEntity {

    /** Platform ID (null for platform-level auth events outside a scoped URL). */
    @Column(name = "platform_id")
    private Long platformId;

    /** Organisation ID (null for platform-level events). */
    @Column(name = "organisation_id")
    private Long organisationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 10)
    private LogLevel level;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private LogCategory category;

    /** Machine-readable event type, e.g. PLATFORM_LOGIN_SUCCESS, ORG_REGISTER. */
    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    /** Human-readable message. */
    @Column(name = "message", nullable = false, length = 2000)
    private String message;

    /** Actor identifier (email, username or slug). */
    @Column(name = "actor", length = 255)
    private String actor;

    /** Client IP address. */
    @Column(name = "ip", length = 45)
    private String ip;

    /** Request ID from the MDC for correlation. */
    @Column(name = "request_id", length = 36)
    private String requestId;

    /** Optional JSON detail blob. */
    @Column(name = "detail", length = 2000)
    private String detail;

    /** API client key that initiated the request (null for direct console access). */
    @Column(name = "client_key", length = 64)
    private String clientKey;

    /** Domain/hostname from the Host header of the request. */
    @Column(name = "domain", length = 255)
    private String domain;
}
