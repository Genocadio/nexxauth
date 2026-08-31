package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.dto.request.CreateOrganisationClientLinkRequest;
import com.nexxserve.nexxauth.dto.request.UpdateOrganisationClientLinkRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationClientLinkResponse;
import com.nexxserve.nexxauth.entity.LogCategory;
import com.nexxserve.nexxauth.entity.LogLevel;
import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.OrganisationClient;
import com.nexxserve.nexxauth.entity.OrganisationClientLink;
import com.nexxserve.nexxauth.entity.Platform;
import com.nexxserve.nexxauth.exception.BadRequestException;
import com.nexxserve.nexxauth.exception.ConflictException;
import com.nexxserve.nexxauth.exception.ResourceNotFoundException;
import com.nexxserve.nexxauth.repository.OrganisationClientLinkRepository;
import com.nexxserve.nexxauth.repository.OrganisationClientRepository;
import com.nexxserve.nexxauth.security.OrgActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Links (registered origins) for an organisation's clients. Each link
 * controls CORS behaviour and source restrictions for a specific origin.
 */
@Service
public class OrganisationClientLinkService {

    private final OrganisationClientLinkRepository linkRepository;
    private final OrganisationClientRepository clientRepository;
    private final PlatformAccess platformAccess;
    private final OrganisationAccess organisationAccess;
    private final AuthAuditService audit;

    public OrganisationClientLinkService(OrganisationClientLinkRepository linkRepository,
                                          OrganisationClientRepository clientRepository,
                                          PlatformAccess platformAccess,
                                          OrganisationAccess organisationAccess,
                                          AuthAuditService audit) {
        this.linkRepository = linkRepository;
        this.clientRepository = clientRepository;
        this.platformAccess = platformAccess;
        this.organisationAccess = organisationAccess;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<OrganisationClientLinkResponse> list(String platformSlug, Long organisationId,
                                                      String clientKey, OrgActor requester) {
        OrganisationClient client = resolveClient(platformSlug, organisationId, clientKey, requester);
        return linkRepository.findByClientIdOrderByIdAsc(client.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganisationClientLinkResponse get(String platformSlug, Long organisationId,
                                               String clientKey, Long linkId, OrgActor requester) {
        OrganisationClient client = resolveClient(platformSlug, organisationId, clientKey, requester);
        OrganisationClientLink link = findLinkEntity(client, linkId);
        return toResponse(link);
    }

    @Transactional
    public OrganisationClientLinkResponse create(String platformSlug, Long organisationId,
                                                  String clientKey, OrgActor requester,
                                                  CreateOrganisationClientLinkRequest request) {
        OrganisationClient client = resolveClient(platformSlug, organisationId, clientKey, requester);
        String origin = normaliseOrigin(request.origin());
        if (linkRepository.existsByClientIdAndOriginIgnoreCase(client.getId(), origin)) {
            throw new ConflictException("A link with origin " + origin + " already exists for this client");
        }

        OrganisationClientLink link = new OrganisationClientLink();
        link.setClient(client);
        link.setOrigin(origin);
        link.setAllowCors(request.allowCors() == null || request.allowCors());
        link.setLimitSource(request.limitSource() != null && request.limitSource());
        validateLinkSettings(link);

        OrganisationClientLink saved = linkRepository.save(link);
        audit.logPersisted(LogLevel.INFO, LogCategory.CONFIG, AuthAuditService.ORG_CLIENT_UPDATED, null,
                client.getOrganisation().getSlug(), client.getOrganisation().getId(),
                "link_created: " + origin);
        return toResponse(saved);
    }

    @Transactional
    public OrganisationClientLinkResponse update(String platformSlug, Long organisationId,
                                                  String clientKey, Long linkId, OrgActor requester,
                                                  UpdateOrganisationClientLinkRequest request) {
        OrganisationClient client = resolveClient(platformSlug, organisationId, clientKey, requester);
        OrganisationClientLink link = findLinkEntity(client, linkId);

        if (request.origin() != null) {
            String origin = normaliseOrigin(request.origin());
            if (!origin.equalsIgnoreCase(link.getOrigin())
                    && linkRepository.existsByClientIdAndOriginIgnoreCase(client.getId(), origin)) {
                throw new ConflictException("A link with origin " + origin + " already exists for this client");
            }
            link.setOrigin(origin);
        }
        if (request.allowCors() != null) {
            link.setAllowCors(request.allowCors());
        }
        if (request.limitSource() != null) {
            link.setLimitSource(request.limitSource());
        }
        // Auto-correct: turning CORS off forces limit source off
        if (!link.isAllowCors() && link.isLimitSource()) {
            link.setLimitSource(false);
        }
        validateLinkSettings(link);

        OrganisationClientLink saved = linkRepository.save(link);
        audit.logPersisted(LogLevel.INFO, LogCategory.CONFIG, AuthAuditService.ORG_CLIENT_UPDATED, null,
                client.getOrganisation().getSlug(), client.getOrganisation().getId(),
                "link_updated: " + saved.getOrigin());
        return toResponse(saved);
    }

    @Transactional
    public void delete(String platformSlug, Long organisationId,
                       String clientKey, Long linkId, OrgActor requester) {
        OrganisationClient client = resolveClient(platformSlug, organisationId, clientKey, requester);
        OrganisationClientLink link = findLinkEntity(client, linkId);
        audit.logPersisted(LogLevel.INFO, LogCategory.CONFIG, AuthAuditService.ORG_CLIENT_UPDATED, null,
                client.getOrganisation().getSlug(), client.getOrganisation().getId(),
                "link_deleted: " + link.getOrigin());
        linkRepository.delete(link);
    }

    // --- helpers -----------------------------------------------------------

    private OrganisationClient resolveClient(String platformSlug, Long organisationId,
                                              String clientKey, OrgActor requester) {
        Platform platform = platformAccess.findPlatform(platformSlug);
        Organisation organisation = organisationAccess.findOrganisationById(organisationId);
        organisationAccess.requireRead(platform, organisation, requester);
        return clientRepository.findByClientKeyAndOrganisationId(clientKey, organisation.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Organisation client", clientKey));
    }

    private OrganisationClientLink findLinkEntity(OrganisationClient client, Long linkId) {
        return linkRepository.findByIdAndClientId(linkId, client.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Client link", linkId));
    }

    /**
     * Prevent limitSource without allowCors — restricting source to an origin
     * while blocking CORS headers makes the setting useless for browser clients
     * (the browser would still reject the response).
     */
    private static void validateLinkSettings(OrganisationClientLink link) {
        if (link.isLimitSource() && !link.isAllowCors()) {
            throw new BadRequestException(
                    "Cannot enable limit source when CORS is disabled — "
                    + "browser requests from this origin would still be blocked by CORS");
        }
    }

    private static String normaliseOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            throw new BadRequestException("Origin must not be blank");
        }
        String trimmed = origin.trim();
        if (!trimmed.matches("^https?://[^/]+$")) {
            throw new BadRequestException("Invalid origin URL: " + trimmed + " (expected scheme://host)");
        }
        return trimmed;
    }

    private OrganisationClientLinkResponse toResponse(OrganisationClientLink link) {
        return new OrganisationClientLinkResponse(
                link.getId(), link.getOrigin(), link.isAllowCors(), link.isLimitSource(),
                link.getCreatedAt());
    }
}
