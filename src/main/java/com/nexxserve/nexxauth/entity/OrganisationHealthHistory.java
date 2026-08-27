package com.nexxserve.nexxauth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * A daily snapshot of an organisation's health score. One row per org per day.
 * Used to compute trend arrows (up / down / same) in the dashboard.
 */
@Getter
@Setter
@Entity
@Table(name = "organisation_health_history", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"organisation_id", "snapshot_date"})
})
public class OrganisationHealthHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "user_count", nullable = false)
    private int userCount;

    @Column(name = "active_sessions", nullable = false)
    private int activeSessions;

    @Column(name = "signing_keys", nullable = false)
    private int signingKeys;

    @Column(name = "api_clients", nullable = false)
    private int apiClients;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;
}
