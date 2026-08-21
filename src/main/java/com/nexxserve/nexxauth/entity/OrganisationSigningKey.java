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
 * An RSA key pair owned by an organisation, used to sign and verify that
 * organisation's access tokens. The {@code kid} is carried in the JWT header;
 * retired keys are kept (active = false) so in-flight tokens verify after a
 * rotation until they expire.
 */
@Getter
@Setter
@Entity
@Table(name = "organisation_signing_keys")
public class OrganisationSigningKey extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @Column(name = "kid", nullable = false, unique = true, length = 64)
    private String kid;

    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    @Column(name = "private_key", nullable = false, columnDefinition = "TEXT")
    private String privateKey;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
