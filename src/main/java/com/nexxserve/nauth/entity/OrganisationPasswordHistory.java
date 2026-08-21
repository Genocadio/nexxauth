package com.nexxserve.nauth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A previously used password hash of an organisation user, kept so the org's
 * password policy can reject reusing the last N passwords.
 */
@Getter
@Setter
@Entity
@Table(name = "organisation_password_history")
public class OrganisationPasswordHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_user_id", nullable = false)
    private OrganisationUser user;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
}
