package com.nexxserve.nexxauth.entity;

/**
 * How a client's {@code allowedRoles} set is interpreted:
 * <ul>
 *   <li>{@code NONE} — no restriction; every role may login/register.</li>
 *   <li>{@code ALLOWLIST} — only users holding at least one of the listed
 *       roles may authenticate.</li>
 *   <li>{@code BLOCKLIST} — users holding any of the listed roles are
 *       rejected; all other roles may authenticate.</li>
 * </ul>
 */
public enum RoleRestrictionMode {
    NONE,
    ALLOWLIST,
    BLOCKLIST
}
