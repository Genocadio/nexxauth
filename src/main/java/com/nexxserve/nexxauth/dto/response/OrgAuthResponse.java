package com.nexxserve.nexxauth.dto.response;

import com.nexxserve.nexxauth.entity.OrgUserAction;

import java.util.List;

/**
 * Returned by org register / login / refresh / challenge verify. The access
 * token is signed with the organisation's own RSA key (kid in the JWT header)
 * and carries the user's roles and permissions in its claims. {@code actions}
 * lists the pending {@link OrgUserAction actions} the user must resolve
 * (empty when fully onboarded). While a gating action (CHANGE_PASSWORD, or a
 * register-required verification) is pending the refresh token is {@code null}
 * and the access token is fixed at 5 minutes.
 * <p>
 * When a login needs a second factor the tokens are all {@code null} and
 * {@code challenge} is set: the server already sent a one-time code and the
 * login resumes on the challenge endpoint, which may again return a challenge
 * or the completed session.
 */
public record OrgAuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        OrganisationUserResponse user,
        List<OrgUserAction> actions,
        LoginChallenge challenge
) {
    public static OrgAuthResponse of(String accessToken, String refreshToken, long expiresInSeconds,
                                     OrganisationUserResponse user, List<OrgUserAction> actions) {
        return new OrgAuthResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, user, actions, null);
    }

    /** A login that must continue on the challenge endpoint: no tokens yet. */
    public static OrgAuthResponse challenge(LoginChallenge challenge, OrganisationUserResponse user,
                                            List<OrgUserAction> actions) {
        return new OrgAuthResponse(null, null, null, 0, user, actions, challenge);
    }
}