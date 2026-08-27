package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.dto.request.CreateOrganisationClientRequest;
import com.nexxserve.nexxauth.dto.request.UpdateOrganisationClientRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationClientResponse;
import com.nexxserve.nexxauth.entity.ClientType;
import com.nexxserve.nexxauth.entity.LogCategory;
import com.nexxserve.nexxauth.entity.LogLevel;
import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.OrganisationClient;
import com.nexxserve.nexxauth.entity.Platform;
import com.nexxserve.nexxauth.exception.BadRequestException;
import com.nexxserve.nexxauth.exception.ConflictException;
import com.nexxserve.nexxauth.exception.ResourceNotFoundException;
import com.nexxserve.nexxauth.mapper.OrganisationClientMapper;
import com.nexxserve.nexxauth.repository.OrganisationClientRepository;
import com.nexxserve.nexxauth.security.ClientTokens;
import com.nexxserve.nexxauth.security.OrgActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Clients of an organisation (the external apps that talk to its API).
 * Management is a platform super-user action (reads: any platform member or
 * org user with read access). The client type drives {@code requireAuthentication}
 * (WEB never, SERVER always, apps configurable) and whether a static token is
 * generated — the token is shown once on create/rotate and only its hash is stored.
 */
@Service
public class OrganisationClientService {

    private static final TypeReference<Map<String, String>> SETTINGS_TYPE = new TypeReference<>() {
    };

    private final OrganisationClientRepository clientRepository;
    private final OrganisationClientMapper clientMapper;
    private final PlatformAccess platformAccess;
    private final OrganisationAccess organisationAccess;
    private final ObjectMapper objectMapper;
    private final AuthAuditService audit;

    public OrganisationClientService(OrganisationClientRepository clientRepository,
                                     OrganisationClientMapper clientMapper,
                                     PlatformAccess platformAccess,
                                     OrganisationAccess organisationAccess,
                                     ObjectMapper objectMapper,
                                     AuthAuditService audit) {
        this.clientRepository = clientRepository;
        this.clientMapper = clientMapper;
        this.platformAccess = platformAccess;
        this.organisationAccess = organisationAccess;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<OrganisationClientResponse> list(String platformSlug, Long organisationId, OrgActor requester) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, false);
        return clientRepository.findByOrganisationIdOrderByNameAsc(organisation.getId()).stream()
                .map(client -> toResponse(client, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganisationClientResponse get(String platformSlug, Long organisationId, String clientKey,
                                          OrgActor requester) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, false);
        return toResponse(findClientEntity(organisation, clientKey), null);
    }

    @Transactional
    public OrganisationClientResponse create(String platformSlug, Long organisationId, OrgActor requester,
                                             CreateOrganisationClientRequest request) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, true);
        if (clientRepository.existsByOrganisationIdAndName(organisation.getId(), request.name())) {
            throw new ConflictException("A client named " + request.name()
                    + " already exists in this organisation");
        }

        boolean requireAuth = effectiveRequireAuthentication(request.type(), request.requireAuthentication());
        rejectForcedTypeConflict(request.type(), request.requireAuthentication());

        OrganisationClient client = clientMapper.toEntity(request);
        client.setOrganisation(organisation);
        client.setClientKey(ClientTokens.generateKey());
        client.setRequireAuthentication(requireAuth);
        client.setEnabled(request.enabled() == null || request.enabled());
        client.setAllowedOrigins(clientMapper.joinOrigins(parseOrigins(request.allowedOrigins())));
        client.setSettings(serializeSettings(request.settings()));
        client.setAccessTokenTtlSeconds(request.accessTokenTtlSeconds());
        client.setRefreshTokenTtlSeconds(request.refreshTokenTtlSeconds());
        client.setMaxSessionsPerUser(request.maxSessionsPerUser());

        String token = null;
        if (requireAuth) {
            token = ClientTokens.generate();
            client.setTokenHash(ClientTokens.hash(token));
        }
        OrganisationClient saved = clientRepository.save(client);
        audit.logPersisted(LogLevel.INFO, LogCategory.CONFIG, AuthAuditService.ORG_CLIENT_CREATED, null,
                organisation.getSlug(), organisation.getId(), saved.getName());
        return toResponse(saved, token);
    }

    @Transactional
    public OrganisationClientResponse update(String platformSlug, Long organisationId, String clientKey,
                                             OrgActor requester, UpdateOrganisationClientRequest request) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, true);
        OrganisationClient client = findClientEntity(organisation, clientKey);

        if (request.name() != null && !request.name().equals(client.getName())) {
            if (clientRepository.existsByOrganisationIdAndName(organisation.getId(), request.name())) {
                throw new ConflictException("A client named " + request.name()
                        + " already exists in this organisation");
            }
            client.setName(request.name());
        }

        String newToken = null;
        if (request.requireAuthentication() != null) {
            rejectForcedTypeConflict(client.getType(), request.requireAuthentication());
            boolean forced = effectiveRequireAuthentication(client.getType(), request.requireAuthentication());
            if (client.isRequireAuthentication() != forced) {
                client.setRequireAuthentication(forced);
                if (forced) {
                    newToken = ClientTokens.generate();
                    client.setTokenHash(ClientTokens.hash(newToken));
                } else {
                    client.setTokenHash(null);
                }
            }
        }

        if (request.allowedOrigins() != null) {
            client.setAllowedOrigins(clientMapper.joinOrigins(parseOrigins(request.allowedOrigins())));
        }
        if (request.enabled() != null) {
            client.setEnabled(request.enabled());
        }
        if (request.settings() != null) {
            client.setSettings(serializeSettings(request.settings()));
        }
        // Session overrides: negative value clears back to org default.
        if (request.accessTokenTtlSeconds() != null) {
            client.setAccessTokenTtlSeconds(request.accessTokenTtlSeconds() > 0 ? request.accessTokenTtlSeconds() : null);
        }
        if (request.refreshTokenTtlSeconds() != null) {
            client.setRefreshTokenTtlSeconds(request.refreshTokenTtlSeconds() > 0 ? request.refreshTokenTtlSeconds() : null);
        }
        if (request.maxSessionsPerUser() != null) {
            client.setMaxSessionsPerUser(request.maxSessionsPerUser() > 0 ? request.maxSessionsPerUser() : null);
        }
        OrganisationClient saved = clientRepository.save(client);
        audit.logPersisted(LogLevel.INFO, LogCategory.CONFIG, AuthAuditService.ORG_CLIENT_UPDATED, null,
                organisation.getSlug(), organisation.getId(), saved.getName());
        return toResponse(saved, newToken);
    }

    @Transactional
    public void delete(String platformSlug, Long organisationId, String clientKey, OrgActor requester) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, true);
        OrganisationClient client = findClientEntity(organisation, clientKey);
        audit.logPersisted(LogLevel.INFO, LogCategory.CONFIG, AuthAuditService.ORG_CLIENT_DELETED, null,
                organisation.getSlug(), organisation.getId(), client.getName());
        clientRepository.delete(client);
    }

    @Transactional
    public OrganisationClientResponse rotateToken(String platformSlug, Long organisationId, String clientKey,
                                                  OrgActor requester) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, true);
        OrganisationClient client = findClientEntity(organisation, clientKey);
        if (!client.isRequireAuthentication()) {
            throw new BadRequestException("This client does not require authentication, so it has no token");
        }
        String token = ClientTokens.generate();
        client.setTokenHash(ClientTokens.hash(token));
        OrganisationClient saved = clientRepository.save(client);
        audit.logPersisted(LogLevel.INFO, LogCategory.SECURITY, AuthAuditService.ORG_CLIENT_TOKEN_ROTATED, null,
                organisation.getSlug(), organisation.getId(), client.getName());
        return toResponse(saved, token);
    }

    // --- type rules --------------------------------------------------------

    /** The effective auth requirement for a type + requested value. */
    private static boolean effectiveRequireAuthentication(ClientType type, Boolean requested) {
        return switch (type) {
            case WEB -> false;
            case SERVER -> true;
            case ANDROID, IOS -> requested != null && requested;
        };
    }

    /** Reject an explicit value that contradicts the type's forced rule. */
    private static void rejectForcedTypeConflict(ClientType type, Boolean requested) {
        if (requested == null) {
            return;
        }
        if (type == ClientType.WEB && requested) {
            throw new BadRequestException("Web clients never require authentication");
        }
        if (type == ClientType.SERVER && !requested) {
            throw new BadRequestException("Server clients always require authentication");
        }
    }

    // --- helpers -----------------------------------------------------------

    private OrganisationClient findClientEntity(Organisation organisation, String clientKey) {
        return clientRepository.findByClientKeyAndOrganisationId(clientKey, organisation.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Organisation client", clientKey));
    }

    private Organisation resolve(String platformSlug, Long organisationId, OrgActor requester,
                                 boolean write) {
        Platform platform = platformAccess.findPlatform(platformSlug);
        Organisation organisation = organisationAccess.findOrganisationById(organisationId);
        if (write) {
            platformAccess.requireSuperUser(platform, requester);
        } else {
            organisationAccess.requireRead(platform, organisation, requester);
        }
        return organisation;
    }

    /** Trim, deduplicate and validate each origin (scheme://host, no path). */
    private List<String> parseOrigins(List<String> origins) {
        if (origins == null) {
            return null;
        }
        Set<String> cleaned = new LinkedHashSet<>();
        for (String origin : origins) {
            if (origin == null || origin.isBlank()) {
                continue;
            }
            String trimmed = origin.trim();
            if (!trimmed.matches("^https?://[^/]+$")) {
                throw new BadRequestException("Invalid origin URL: " + trimmed);
            }
            cleaned.add(trimmed);
        }
        return List.copyOf(cleaned);
    }

    private String serializeSettings(Map<String, String> settings) {
        if (settings == null || settings.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (JacksonException e) {
            throw new BadRequestException("Invalid client settings");
        }
    }

    private Map<String, String> deserializeSettings(String settings) {
        if (settings == null || settings.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(settings, SETTINGS_TYPE);
        } catch (JacksonException e) {
            return Map.of();
        }
    }

    private OrganisationClientResponse toResponse(OrganisationClient client, String token) {
        OrganisationClientResponse base = clientMapper.toResponse(client);
        return new OrganisationClientResponse(
                base.clientKey(), base.name(), base.type(), base.requireAuthentication(),
                base.allowedOrigins(), base.enabled(), deserializeSettings(client.getSettings()),
                base.createdAt(), token,
                client.getAccessTokenTtlSeconds(), client.getRefreshTokenTtlSeconds(),
                client.getMaxSessionsPerUser());
    }
}
