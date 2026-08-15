package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.request.UpdateOrganisationAuthConfigRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationAuthConfigResponse;
import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.Platform;
import com.nexxserve.nexxauth.security.OrgActor;
import com.nexxserve.nexxauth.service.OrganisationAccess;
import com.nexxserve.nexxauth.service.OrganisationAuthConfigService;
import com.nexxserve.nexxauth.service.PlatformAccess;
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
@RequestMapping("/api/v1/platforms/{slug}/organisations/{organisationSlug}/auth-config")
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
                                              @PathVariable String organisationSlug,
                                              @AuthenticationPrincipal OrgActor requester) {
        Organisation organisation = resolve(slug, organisationSlug, requester, false);
        return authConfigService.get(organisation);
    }

    @PatchMapping
    @PreAuthorize("hasRole('SUPER_USER')")
    public OrganisationAuthConfigResponse update(@PathVariable String slug,
                                                 @PathVariable String organisationSlug,
                                                 @AuthenticationPrincipal OrgActor requester,
                                                 @Valid @RequestBody UpdateOrganisationAuthConfigRequest request) {
        Organisation organisation = resolve(slug, organisationSlug, requester, true);
        return authConfigService.update(organisation, request);
    }

    private Organisation resolve(String platformSlug, String organisationSlug, OrgActor requester,
                                 boolean write) {
        Platform platform = platformAccess.findPlatform(platformSlug);
        Organisation organisation = organisationAccess.findOrganisation(platform, organisationSlug);
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
