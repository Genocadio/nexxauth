package com.nexxserve.nexxauth.entity;

/**
 * Fixed permissions defined by the application (never edited per organisation).
 * Organisation roles group these; organisation users get roles, never direct
 * permissions.
 */
public enum Permission {
    ORGANISATION_USER_READ,
    ORGANISATION_USER_CREATE,
    ORGANISATION_USER_UPDATE,
    ORGANISATION_USER_DELETE,
    ORGANISATION_USER_FIELD_READ,
    ORGANISATION_USER_FIELD_CREATE,
    ORGANISATION_USER_FIELD_UPDATE,
    ORGANISATION_USER_FIELD_DELETE
}
