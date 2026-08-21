package com.nexxserve.nexxauth.dto.response;

/**
 * An organisation's signing key as exposed to other services so they can verify
 * that organisation's access tokens. Only the public key is shared; the
 * private key never leaves the server.
 */
public record OrganisationKeyResponse(
        String kid,
        String publicKey,
        boolean active
) {
}
