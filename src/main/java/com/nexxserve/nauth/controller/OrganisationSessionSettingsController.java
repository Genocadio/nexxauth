package com.nexxserve.nauth.controller;

import com.nexxserve.nauth.dto.request.UpdateOrganisationSessionSettingsRequest;
import com.nexxserve.nauth.dto.response.OrganisationSessionSettingsResponse;
import com.nexxserve.nauth.entity.Organisation;
import com.nexxserve.nauth.entity.Platform;
import com.nexxserve.nauth.security.OrgActor;
import com.nexxserve.nauth.service.OrganisationAccess;
import com.nexxserve.nauth.service.OrganisationSessionSettingsService;
import com.nexxserve.nauth.service.PlatformAccess;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organisation-level session settings (access/refresh token lifetimes, max
 * concurrent sessions per user). Reads: platform members and org users of the
 * org. Writes: platform super user. Never applies to the platform auth flow.
 */
@RestController
@RequestMapping("/{slug}/organisations/{organisationId}/session-settings")
public class OrganisationSessionSettingsController {

    private final PlatformAccess platformAccess;
    private final OrganisationAccess organisationAccess;
    private final OrganisationSessionSettingsService settingsService;

    public OrganisationSessionSettingsController(PlatformAccess platformAccess, OrganisationAccess organisationAccess,
                                                 OrganisationSessionSettingsService settingsService) {
        this.platformAccess = platformAccess;
        this.organisationAccess = organisationAccess;
        this.settingsService = settingsService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_USER','READ_ONLY') or hasAuthority('ORG_USER')")
    public OrganisationSessionSettingsResponse get(@PathVariable String slug,
                                                   @PathVariable Long organisationId,
                                                   @AuthenticationPrincipal OrgActor requester) {
        Organisation organisation = resolve(slug, organisationId, requester, false);
        return settingsService.get(organisation);
    }

    @PatchMapping
    @PreAuthorize("hasRole('SUPER_USER')")
    public OrganisationSessionSettingsResponse update(@PathVariable String slug,
                                                      @PathVariable Long organisationId,
                                                      @AuthenticationPrincipal OrgActor requester,
                                                      @Valid @RequestBody UpdateOrganisationSessionSettingsRequest request) {
        Organisation organisation = resolve(slug, organisationId, requester, true);
        return settingsService.update(organisation, request);
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
