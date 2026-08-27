package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.response.OrganisationHealthResponse;
import com.nexxserve.nexxauth.security.OrgActor;
import com.nexxserve.nexxauth.service.OrganisationHealthService;
import com.nexxserve.nexxauth.service.PlatformAccess;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lightweight health scores for organisations. Returns user counts, active
 * sessions, key counts, client counts, and a 0-100 score -- enough for the
 * dashboard to render health bars without loading full org details.
 */
@RestController
@RequestMapping("/{slug}/organisations/health")
public class OrganisationHealthController {

    private final PlatformAccess platformAccess;
    private final OrganisationHealthService healthService;

    public OrganisationHealthController(PlatformAccess platformAccess,
                                         OrganisationHealthService healthService) {
        this.platformAccess = platformAccess;
        this.healthService = healthService;
    }

    /** Health scores for all organisations under this platform. */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_USER','READ_ONLY')")
    public List<OrganisationHealthResponse> healthAll(
            @PathVariable String slug,
            @AuthenticationPrincipal OrgActor requester) {
        var platform = platformAccess.findPlatform(slug);
        platformAccess.requireMember(platform, requester);
        return healthService.healthAll(platform.getId());
    }

    /** Health score for a single organisation. */
    @GetMapping("/{organisationId}")
    @PreAuthorize("hasAnyRole('SUPER_USER','READ_ONLY') or hasAuthority('ORG_USER')")
    public OrganisationHealthResponse health(
            @PathVariable String slug,
            @PathVariable Long organisationId,
            @AuthenticationPrincipal OrgActor requester) {
        return healthService.health(organisationId);
    }
}
