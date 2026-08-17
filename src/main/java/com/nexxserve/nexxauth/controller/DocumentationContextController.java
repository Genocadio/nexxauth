package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.response.DocumentationContextResponse;
import com.nexxserve.nexxauth.dto.response.DocumentationContextResponse.*;
import com.nexxserve.nexxauth.entity.*;
import com.nexxserve.nexxauth.repository.*;
import com.nexxserve.nexxauth.service.OrganisationAccess;
import com.nexxserve.nexxauth.service.PlatformAccess;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Public endpoint serving organisation configuration for context-aware API docs.
 * No authentication required — anyone with the URL can read docs context.
 */
@RestController
@RequestMapping("/{slug}/organisations/{organisationId}/docs")
public class DocumentationContextController {

    private final OrganisationAccess organisationAccess;
    private final OrganisationAuthConfigRepository authConfigRepository;
    private final OrganisationSessionSettingsRepository sessionSettingsRepository;
    private final OrganisationUserFieldRepository userFieldRepository;
    private final OrganisationRoleRepository roleRepository;
    private final OrganisationClientRepository clientRepository;

    public DocumentationContextController(
            OrganisationAccess organisationAccess,
            OrganisationAuthConfigRepository authConfigRepository,
            OrganisationSessionSettingsRepository sessionSettingsRepository,
            OrganisationUserFieldRepository userFieldRepository,
            OrganisationRoleRepository roleRepository,
            OrganisationClientRepository clientRepository) {
        this.organisationAccess = organisationAccess;
        this.authConfigRepository = authConfigRepository;
        this.sessionSettingsRepository = sessionSettingsRepository;
        this.userFieldRepository = userFieldRepository;
        this.roleRepository = roleRepository;
        this.clientRepository = clientRepository;
    }

    @GetMapping("/context")
    public DocumentationContextResponse context(@PathVariable String slug,
                                                @PathVariable Long organisationId) {
        Organisation org = organisationAccess.findOrganisationById(organisationId);

        OrganisationAuthConfig authConfig = authConfigRepository.findByOrganisationId(organisationId)
                .orElse(null);
        OrganisationSessionSettings sessionSettings = sessionSettingsRepository.findByOrganisationId(organisationId)
                .orElse(null);

        List<OrganisationUserField> fields = userFieldRepository.findByOrganisationIdOrderByKeyAsc(organisationId);
        List<OrganisationRole> roles = roleRepository.findByOrganisationIdOrderByCreatedAtAsc(organisationId);
        List<OrganisationClient> clients = clientRepository.findByOrganisationIdOrderByNameAsc(organisationId);

        Map<ClientType, List<OrganisationClient>> clientsByType = clients.stream()
                .collect(Collectors.groupingBy(OrganisationClient::getType));

        List<ClientTypeSummary> clientTypeSummaries = clientsByType.entrySet().stream()
                .map(e -> new ClientTypeSummary(
                        e.getKey(),
                        e.getValue().size(),
                        e.getValue().stream().anyMatch(OrganisationClient::isRequireAuthentication)))
                .sorted(Comparator.comparing(ClientTypeSummary::type))
                .toList();

        return new DocumentationContextResponse(
                new OrganisationSummary(org.getId(), org.getName(), org.getSlug(),
                        org.getPlatform().getSlug()),
                new IdentifierConfig(
                        org.isEmailRequired(), org.isUsernameRequired(), org.isPhoneRequired(),
                        org.isEmailCanLogin(), org.isUsernameCanLogin(), org.isPhoneCanLogin()),
                authConfig != null
                        ? new AuthConfig(authConfig.getAuthType(), authConfig.isPasswordEnabled(),
                        authConfig.getPasswordMinLength(), authConfig.getPasswordMaxLength())
                        : new AuthConfig(AuthType.PASSWORD, true, 8, 72),
                sessionSettings != null
                        ? new SessionConfig(sessionSettings.getAccessTokenTtlSeconds(),
                        sessionSettings.getRefreshTokenTtlSeconds(),
                        sessionSettings.getMaxSessionsPerUser())
                        : new SessionConfig(900, 604800, 5),
                fields.stream().map(f -> new FieldSummary(f.getKey(), f.getFieldType(),
                        f.isLoginEnabled(), f.isRequired())).toList(),
                roles.stream().map(r -> new RoleSummary(r.getId(), r.getName(),
                        r.getPermissions(), r.isDefaultRole())).toList(),
                clientTypeSummaries,
                EnumSet.allOf(Permission.class)
        );
    }
}
