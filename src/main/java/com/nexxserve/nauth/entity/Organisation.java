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
 * An organisation belonging to a {@link Platform}. A platform can have many
 * organisations; the slug is unique within the owning platform.
 * <p>
 * Sign-in identifiers are configurable: email, username and phone all exist by
 * default, and each has two independent flags — {@code required} (every user
 * must have a value) and {@code canLogin} (the value works as a login
 * identifier). At least one identifier must be able to login.
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

    @Column(name = "email_required", nullable = false)
    private boolean emailRequired = false;

    @Column(name = "username_required", nullable = false)
    private boolean usernameRequired = true;

    @Column(name = "phone_required", nullable = false)
    private boolean phoneRequired = false;

    @Column(name = "email_can_login", nullable = false)
    private boolean emailCanLogin = false;

    @Column(name = "username_can_login", nullable = false)
    private boolean usernameCanLogin = true;

    @Column(name = "phone_can_login", nullable = false)
    private boolean phoneCanLogin = false;

    /** Onboarding wizard progress: 1..7 = current step, 8 = complete, null = not started. */
    @Column(name = "onboarding_step")
    private Integer onboardingStep;

    /** Backwards-compatible view: email is the primary login identifier. */
    public boolean isUseEmailAsUsername() {
        return emailCanLogin && emailRequired;
    }

    /** At least one identifier must be able to login for the org to be usable. */
    public boolean hasLoginIdentifier() {
        return emailCanLogin || usernameCanLogin || phoneCanLogin;
    }
}
