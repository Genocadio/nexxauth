package com.nexxserve.nauth.service;

import com.nexxserve.nauth.dto.response.DocumentationContextResponse;
import com.nexxserve.nauth.dto.response.DocumentationContextResponse.AuthConfig;
import com.nexxserve.nauth.dto.response.DocumentationContextResponse.ClientTypeSummary;
import com.nexxserve.nauth.dto.response.DocumentationContextResponse.FieldSummary;
import com.nexxserve.nauth.dto.response.DocumentationContextResponse.IdentifierConfig;
import com.nexxserve.nauth.dto.response.DocumentationContextResponse.OrganisationSummary;
import com.nexxserve.nauth.dto.response.DocumentationContextResponse.RoleSummary;
import com.nexxserve.nauth.dto.response.DocumentationContextResponse.SessionConfig;
import com.nexxserve.nauth.entity.AuthType;
import com.nexxserve.nauth.entity.ClientType;
import com.nexxserve.nauth.entity.Organisation;
import com.nexxserve.nauth.entity.OrganisationAuthConfig;
import com.nexxserve.nauth.entity.OrganisationClient;
import com.nexxserve.nauth.entity.OrganisationRole;
import com.nexxserve.nauth.entity.OrganisationSessionSettings;
import com.nexxserve.nauth.entity.OrganisationUserField;
import com.nexxserve.nauth.entity.Permission;
import com.nexxserve.nauth.repository.OrganisationAuthConfigRepository;
import com.nexxserve.nauth.repository.OrganisationClientRepository;
import com.nexxserve.nauth.repository.OrganisationRoleRepository;
import com.nexxserve.nauth.repository.OrganisationSessionSettingsRepository;
import com.nexxserve.nauth.repository.OrganisationUserFieldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the documentation context for an organisation: all configuration
 * needed to render context-aware API docs. Public endpoint — no authentication.
 * <p>
 * Reads run inside a read-only transaction so lazy associations (e.g. the
 * organisation's platform, used for {@code platformSlug}) can be initialized
 * while the session is still open.
 */
@Service
public class DocumentationContextService {

    private final OrganisationAccess organisationAccess;
    private final OrganisationAuthConfigRepository authConfigRepository;
    private final OrganisationSessionSettingsRepository sessionSettingsRepository;
    private final OrganisationUserFieldRepository userFieldRepository;
    private final OrganisationRoleRepository roleRepository;
    private final OrganisationClientRepository clientRepository;

    public DocumentationContextService(OrganisationAccess organisationAccess,
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

    @Transactional(readOnly = true)
    public DocumentationContextResponse context(Long organisationId) {
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
