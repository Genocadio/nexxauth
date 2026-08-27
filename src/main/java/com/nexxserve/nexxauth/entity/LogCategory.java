package com.nexxserve.nexxauth.entity;

/**
 * High-level grouping for log entries so the UI can filter by category
 * (e.g. "show me all auth events" or "show me user management").
 */
public enum LogCategory {
    /** Authentication: login, register, refresh, logout, password changes. */
    AUTH,
    /** User management: create, update, delete platform/org users. */
    USER_MANAGEMENT,
    /** Organisation management: create, update, delete orgs. */
    ORG_MANAGEMENT,
    /** Configuration: roles, fields, keys, clients, session settings. */
    CONFIG,
    /** Security: token reuse, account disabled, suspicious activity. */
    SECURITY
}
