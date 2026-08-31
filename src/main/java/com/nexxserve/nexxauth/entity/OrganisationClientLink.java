package com.nexxserve.nexxauth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A registered origin (link) belonging to an {@link OrganisationClient}.
 * Each link controls CORS behaviour and source restrictions:
 * <ul>
 *   <li>{@link #allowCors} — when true, the {@code Access-Control-Allow-Origin}
 *       header is echoed for requests carrying this origin.</li>
 *   <li>{@link #limitSource} — when true, only requests whose {@code Origin}
 *       matches this link are allowed through (all other origins are rejected).</li>
 * </ul>
 */
@Getter
@Setter
@Entity
@Table(name = "organisation_client_links")
public class OrganisationClientLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private OrganisationClient client;

    /** The trusted origin URL (e.g. {@code https://app.example.com}). */
    @Column(name = "origin", nullable = false, length = 2000)
    private String origin;

    /** When true, CORS headers are applied for requests from this origin. */
    @Column(name = "allow_cors", nullable = false)
    private boolean allowCors = true;

    /** When true, only requests from this origin are allowed (source restriction). */
    @Column(name = "limit_source", nullable = false)
    private boolean limitSource = false;
}
