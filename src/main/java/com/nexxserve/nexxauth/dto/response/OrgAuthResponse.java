package com.nexxserve.nexxauth.dto.response;

import com.nexxserve.nexxauth.entity.OrgUserAction;

import java.util.List;

/**
 * Returned by org register / login / refresh. The access token is signed with
 * the organisation's own RSA key (kid in the JWT header) and carries the user's
 * roles and permissions in its claims. {@code actions} lists the pending
 * {@link OrgUserAction actions} the user must resolve (empty when fully onboarded).
 */
public record OrgAuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        OrganisationUserResponse user,
        List<OrgUserAction> actions
) {
    public static OrgAuthResponse of(String accessToken, String refreshToken, long expiresInSeconds,
                                     OrganisationUserResponse user, List<OrgUserAction> actions) {
        return new OrgAuthResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, user, actions);
    }

    public static OrgAuthResponse challenge(OrganisationUserResponse user,
                                            List<OrgUserAction> actions) {
        return new OrgAuthResponse(null, null, null, 0, user, actions);
    }
}