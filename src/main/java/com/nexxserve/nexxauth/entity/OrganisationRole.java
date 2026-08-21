package com.nexxserve.nexxauth.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * A role within an organisation: a named group of {@link Permission}s. A role
 * may have zero permissions. Roles are organisation-scoped and mean nothing at
 * the platform level.
 */
@Getter
@Setter
@Entity
@Table(name = "organisation_roles")
public class OrganisationRole extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** When true, new users of the organisation inherit this role automatically
     * on register (default off; can be turned on per role). */
    @Column(name = "is_default", nullable = false)
    private boolean defaultRole;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "organisation_role_permissions",
            joinColumns = @JoinColumn(name = "organisation_role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 50)
    private Set<Permission> permissions = new HashSet<>();
}
