package com.nexxserve.nexxauth.entity;

/**
 * The kind of app a client is. The type drives the access rules enforced by
 * {@link com.nexxserve.nexxauth.security.ClientTokenFilter}:
 * <ul>
 *   <li>{@code WEB} — never authenticated; only the organisation auth endpoints
 *       (login, register, refresh, logout), unless the request carries a valid
 *       org-user JWT (then the user proceeds under their own roles); CORS
 *       origins apply.</li>
 *   <li>{@code SERVER} — always authenticated with a static client token; full
 *       access to its organisation's endpoints.</li>
 *   <li>{@code ANDROID}/{@code IOS} — {@code requireAuthentication} is
 *       configurable: without auth they are restricted to the organisation auth
 *       endpoints (like web clients, and likewise let a valid org-user JWT
 *       through); with auth they get full organisation access.</li>
 * </ul>
 * When no {@code X-Client-Id} is present at all, organisation access is default-deny
 * for an org user from a foreign origin — a client must be configured for external
 * browser access; the same-origin/server path (admin console, server-side portal)
 * is exempt.
 */
public enum ClientType {
    WEB,
    ANDROID,
    IOS,
    SERVER
}
