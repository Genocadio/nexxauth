package com.nexxserve.nexxauth.dto.response;

/**
 * A role as it appears on a user object: id + name only. Permissions are never
 * exposed on user responses — they are an internal concept, resolved
 * server-side from the database on every request (see the roles endpoints for
 * a role's full definition).
 */
public record OrganisationUserRoleResponse(
        Long id,
        String name
) {
}
