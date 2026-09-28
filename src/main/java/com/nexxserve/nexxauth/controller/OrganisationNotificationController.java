package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.request.UpdateOrganisationTemplatesRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationTemplatesResponse;
import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.Platform;
import com.nexxserve.nexxauth.security.OrgActor;
import com.nexxserve.nexxauth.service.NexxbotifyClient;
import com.nexxserve.nexxauth.service.OrganisationAccess;
import com.nexxserve.nexxauth.service.PlatformAccess;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manages email and SMS notification templates for an organisation.
 * Templates are stored and rendered by nexxnotify via the organisation's
 * dedicated notification flow.
 */
@RestController
@RequestMapping("/{slug}/organisations/{organisationId}/notification-templates")
public class OrganisationNotificationController {

    private final PlatformAccess platformAccess;
    private final OrganisationAccess organisationAccess;
    private final NexxbotifyClient nexxbotifyClient;

    public OrganisationNotificationController(PlatformAccess platformAccess,
                                              OrganisationAccess organisationAccess,
                                              NexxbotifyClient nexxbotifyClient) {
        this.platformAccess = platformAccess;
        this.organisationAccess = organisationAccess;
        this.nexxbotifyClient = nexxbotifyClient;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_USER','READ_ONLY') or hasAuthority('ORG_USER')")
    public OrganisationTemplatesResponse getTemplates(@PathVariable String slug,
                                                      @PathVariable Long organisationId,
                                                      @AuthenticationPrincipal OrgActor requester) {
        Organisation organisation = resolve(slug, organisationId, requester, false);
        return nexxbotifyClient.getOrganisationTemplates(organisation);
    }

    @PutMapping
    @PreAuthorize("hasRole('SUPER_USER')")
    public OrganisationTemplatesResponse updateTemplates(@PathVariable String slug,
                                                         @PathVariable Long organisationId,
                                                         @AuthenticationPrincipal OrgActor requester,
                                                         @Valid @RequestBody UpdateOrganisationTemplatesRequest request) {
        Organisation organisation = resolve(slug, organisationId, requester, true);
        return nexxbotifyClient.updateOrganisationTemplates(organisation, request);
    }

    private Organisation resolve(String platformSlug, Long organisationId, OrgActor requester,
                                 boolean write) {
        Platform platform = platformAccess.findPlatform(platformSlug);
        Organisation organisation = organisationAccess.findOrganisationById(organisationId);
        if (write) {
            platformAccess.requireSuperUser(platform, requester);
        } else if (requester.isPlatformUser()) {
            platformAccess.requireMember(platform, requester);
        } else {
            organisationAccess.requireOrgUserOf(organisation, requester);
        }
        return organisation;
    }
}
