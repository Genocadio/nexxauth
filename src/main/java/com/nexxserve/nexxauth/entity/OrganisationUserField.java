package com.nexxserve.nexxauth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * An organisation-defined custom user field: a named, org-scoped attribute
 * with a fixed value type that can be collected on each user. When
 * {@link #isLoginEnabled()}, the field's value also serves as an alternative
 * login identifier (values must then be unique per organisation). The flag is
 * config-level only and is not echoed on user objects.
 */
@Getter
@Setter
@Entity
@Table(name = "organisation_user_fields")
public class OrganisationUserField extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    /** Machine key, unique per organisation (e.g. {@code employee-id}). */
    @Column(name = "field_key", nullable = false, length = 100)
    private String key;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 20)
    private UserFieldType fieldType;

    @Column(name = "login_enabled", nullable = false)
    private boolean loginEnabled = false;

    /** When true, every user of the organisation must have a value for this
     * field. Users missing a required value get the UPDATE_PROFILE action at
     * login (non-gating: it does not restrict tokens, only informs the client
     * that the profile needs completing). */
    @Column(name = "required", nullable = false)
    private boolean required = false;
}
