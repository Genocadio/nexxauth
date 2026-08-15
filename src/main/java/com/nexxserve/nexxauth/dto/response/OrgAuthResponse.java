package com.nexxserve.nexxauth.dto.response;

/**
 * Returned by org register / login / refresh. The access token is signed with
 * the organisation's own RSA key (kid in the JWT header) and carries the
 * user's roles and permissions in its claims.
 */
public record OrgAuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        OrganisationUserResponse user
) {
    public static OrgAuthResponse of(String accessToken, String refreshToken, long expiresInSeconds,
                                     OrganisationUserResponse user) {
        return new OrgAuthResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, user);
    }
}
