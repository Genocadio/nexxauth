package com.nexxserve.nauth.controller;

import com.nexxserve.nauth.dto.request.UpdateOrganisationAuthConfigRequest;
import com.nexxserve.nauth.dto.response.OrganisationAuthConfigResponse;
import com.nexxserve.nauth.entity.Organisation;
import com.nexxserve.nauth.entity.Platform;
import com.nexxserve.nauth.security.OrgActor;
import com.nexxserve.nauth.service.OrganisationAccess;
import com.nexxserve.nauth.service.OrganisationAuthConfigService;
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
 * Organisation-level authentication settings (auth type + password rules).
 * Reads: platform members and org users of the org. Writes: platform super
 * user. Never applies to the platform auth flow.
 */
@RestController
@RequestMapping("/{slug}/organisations/{organisationId}/auth-config")
public class OrganisationAuthConfigController {

    private final PlatformAccess platformAccess;
    private final OrganisationAccess organisationAccess;
    private final OrganisationAuthConfigService authConfigService;

    public OrganisationAuthConfigController(PlatformAccess platformAccess, OrganisationAccess organisationAccess,
                                            OrganisationAuthConfigService authConfigService) {
        this.platformAccess = platformAccess;
        this.organisationAccess = organisationAccess;
        this.authConfigService = authConfigService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_USER','READ_ONLY') or hasAuthority('ORG_USER')")
    public OrganisationAuthConfigResponse get(@PathVariable String slug,
                                              @PathVariable Long organisationId,
                                              @AuthenticationPrincipal OrgActor requester) {
        Organisation organisation = resolve(slug, organisationId, requester, false);
        return authConfigService.get(organisation);
    }

    @PatchMapping
    @PreAuthorize("hasRole('SUPER_USER')")
    public OrganisationAuthConfigResponse update(@PathVariable String slug,
                                                 @PathVariable Long organisationId,
                                                 @AuthenticationPrincipal OrgActor requester,
                                                 @Valid @RequestBody UpdateOrganisationAuthConfigRequest request) {
        Organisation organisation = resolve(slug, organisationId, requester, true);
        return authConfigService.update(organisation, request);
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
