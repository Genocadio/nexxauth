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
 * An organisation belonging to a {@link Platform}. A platform can have many
 * organisations; the slug is unique within the owning platform.
 */
@Getter
@Setter
@Entity
@Table(name = "organisations")
public class Organisation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "platform_id", nullable = false)
    private Platform platform;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, length = 100)
    private String slug;

    @Column(name = "description", length = 1000)
    private String description;

    /**
     * When true, email is the required identifier of organisation users
     * (acting as their username).
     */
    @Column(name = "use_email_as_username", nullable = false)
    private boolean useEmailAsUsername = false;
}
