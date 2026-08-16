package com.nexxserve.nexxauth.entity;

/**
 * Pending actions returned to an organisation user on login/refresh so the
 * client can guide the user. A {@code gating} action restricts the session:
 * the user gets a short-lived access token (fixed 5 minutes), no refresh token,
 * and only the action endpoints are reachable until the action is completed.
 * Non-gating actions (e.g. {@link #UPDATE_PROFILE}) are advisory only: they do
 * not limit the refresh or access token.
 */
public enum OrgUserAction {

    /** The password is temporary (or a forced change was triggered) and the
     * user must set a new one before anything else. Gating. */
    CHANGE_PASSWORD(true),

    /** One or more required organisation user fields have no value for this
     * user; the profile must be completed. Not gating. */
    UPDATE_PROFILE(false);

    private final boolean gating;

    OrgUserAction(boolean gating) {
        this.gating = gating;
    }

    public boolean isGating() {
        return gating;
    }
}
