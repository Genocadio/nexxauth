package com.nexxserve.nexxauth.entity;

/**
 * The kind of app a client is. The type drives the access rules enforced by
 * {@link com.nexxserve.nexxauth.security.ClientTokenFilter}:
 * <ul>
 *   <li>{@code WEB} — never authenticated; only the organisation login/register
 *       endpoints; CORS origins apply.</li>
 *   <li>{@code SERVER} — always authenticated with a static client token; full
 *       access to its organisation's endpoints.</li>
 *   <li>{@code ANDROID}/{@code IOS} — {@code requireAuthentication} is
 *       configurable: without auth they are restricted to login/register (like
 *       web clients); with auth they get full organisation access.</li>
 * </ul>
 */
public enum ClientType {
    WEB,
    ANDROID,
    IOS,
    SERVER
}
