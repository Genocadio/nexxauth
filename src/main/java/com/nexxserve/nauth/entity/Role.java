package com.nexxserve.nauth.entity;

/**
 * Access level of a platform user.
 * <ul>
 *   <li>{@link #SUPER_USER} - full management rights within their platform
 *       (create/update users, change roles).</li>
 *   <li>{@link #READ_ONLY} - can authenticate and read platform data only.</li>
 * </ul>
 */
public enum Role {
    SUPER_USER,
    READ_ONLY
}
