package com.nexxserve.nauth.dto.response;

import com.nexxserve.nauth.entity.AuthType;
import com.nexxserve.nauth.entity.ClientType;
import com.nexxserve.nauth.entity.Permission;
import com.nexxserve.nauth.entity.UserFieldType;

import java.util.List;
import java.util.Set;

/**
 * Documentation context for an organisation: all configuration needed to render
 * context-aware API docs. Public endpoint — no authentication required.
 */
public record DocumentationContextResponse(
        OrganisationSummary organisation,
        IdentifierConfig identifiers,
        AuthConfig auth,
        SessionConfig sessions,
        List<FieldSummary> customFields,
        List<RoleSummary> roles,
        List<ClientTypeSummary> clientTypes,
        Set<Permission> availablePermissions
) {
    public record OrganisationSummary(
            Long id,
            String name,
            String slug,
            String platformSlug
    ) {
    }

    public record IdentifierConfig(
            boolean emailRequired,
            boolean usernameRequired,
            boolean phoneRequired,
            boolean emailCanLogin,
            boolean usernameCanLogin,
            boolean phoneCanLogin
    ) {
    }

    public record AuthConfig(
            AuthType authType,
            boolean passwordEnabled,
            int passwordMinLength,
            int passwordMaxLength
    ) {
    }

    public record SessionConfig(
            long accessTokenTtlSeconds,
            long refreshTokenTtlSeconds,
            int maxSessionsPerUser
    ) {
    }

    public record FieldSummary(
            String key,
            UserFieldType fieldType,
            boolean loginEnabled,
            boolean required
    ) {
    }

    public record RoleSummary(
            Long id,
            String name,
            Set<Permission> permissions,
            boolean isDefault
    ) {
    }

    public record ClientTypeSummary(
            ClientType type,
            int count,
            boolean requireAuthentication
    ) {
    }
}
