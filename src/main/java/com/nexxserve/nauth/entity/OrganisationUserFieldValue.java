package com.nexxserve.nauth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One value of an organisation-defined user field for one user. Values are
 * stored as canonical strings (see {@link UserFieldType}); {@code userId} and
 * {@code organisationId} are kept denormalized (no JPA relationship) so reads
 * and the login-by-field lookup never touch the user graph lazily.
 */
@Getter
@Setter
@Entity
@Table(name = "organisation_user_field_values")
public class OrganisationUserFieldValue extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    @Column(name = "field_value", nullable = false, length = 255)
    private String fieldValue;
}
