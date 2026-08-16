package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.response.OrganisationKeyResponse;
import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.OrganisationSigningKey;
import com.nexxserve.nexxauth.entity.Platform;
import com.nexxserve.nexxauth.service.OrgKeyService;
import com.nexxserve.nexxauth.service.OrganisationAccess;
import com.nexxserve.nexxauth.service.PlatformAccess;
import com.nexxserve.nexxauth.security.OrgActor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Organisation signing keys. The public keys are exposed (no auth) so other
 * services can verify this organisation's access tokens; rotation is a
 * platform super-user action. The private key never leaves the server.
 */
@RestController
@RequestMapping("/{slug}/organisations/{organisationSlug}/keys")
public class OrganisationKeyController {

    private final PlatformAccess platformAccess;
    private final OrganisationAccess organisationAccess;
    private final OrgKeyService orgKeyService;

    public OrganisationKeyController(PlatformAccess platformAccess, OrganisationAccess organisationAccess,
                                     OrgKeyService orgKeyService) {
        this.platformAccess = platformAccess;
        this.organisationAccess = organisationAccess;
        this.orgKeyService = orgKeyService;
    }

    /** Public verification keys for external services (kid + public key). */
    @GetMapping
    public List<OrganisationKeyResponse> keys(@PathVariable String slug,
                                              @PathVariable String organisationSlug) {
        Platform platform = platformAccess.findPlatform(slug);
        Organisation organisation = organisationAccess.findOrganisation(platform, organisationSlug);
        return orgKeyService.keys(organisation).stream()
                .map(key -> new OrganisationKeyResponse(key.getKid(), key.getPublicKey(), key.isActive()))
                .toList();
    }

    /** Retires the current key and provisions a fresh one; old tokens keep
     * verifying against the retired key until they expire. */
    @PostMapping("/rotate")
    public OrganisationKeyResponse rotate(@PathVariable String slug,
                                          @PathVariable String organisationSlug,
                                          @AuthenticationPrincipal OrgActor requester) {
        Platform platform = platformAccess.findPlatform(slug);
        platformAccess.requireSuperUser(platform, requester);
        Organisation organisation = organisationAccess.findOrganisation(platform, organisationSlug);
        OrganisationSigningKey key = orgKeyService.rotateKey(organisation);
        return new OrganisationKeyResponse(key.getKid(), key.getPublicKey(), key.isActive());
    }

}
