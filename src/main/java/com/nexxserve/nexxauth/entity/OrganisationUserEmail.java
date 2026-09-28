package com.nexxserve.nexxauth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "organisation_user_emails")
public class OrganisationUserEmail extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_user_id", nullable = false)
    private OrganisationUser user;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = false;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    public boolean isVerified() {
        return verifiedAt != null;
    }
}
