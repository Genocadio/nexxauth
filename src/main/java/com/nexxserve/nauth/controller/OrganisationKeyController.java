package com.nexxserve.nauth.controller;

import com.nexxserve.nauth.dto.response.OrganisationKeyResponse;
import com.nexxserve.nauth.entity.Organisation;
import com.nexxserve.nauth.entity.OrganisationSigningKey;
import com.nexxserve.nauth.entity.Platform;
import com.nexxserve.nauth.security.ClientPrincipal;
import com.nexxserve.nauth.service.OrgKeyService;
import com.nexxserve.nauth.service.OrganisationAccess;
import com.nexxserve.nauth.service.PlatformAccess;
import com.nexxserve.nauth.security.OrgActor;
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
 * <p>
 * For platform users the organisation is identified by its ID in the path.
 * For authenticated clients the organisation is resolved from the client key
 * ({@code X-Client-Id} header) and the path ID must match.
 */
@RestController
@RequestMapping("/{slug}/organisations/{organisationId}/keys")
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

    /** Verification keys (kid + public key). Platform users pass the org ID
     * in the path; authenticated clients resolve the org from their client
     * key and the path ID must match. */
    @GetMapping
    public List<OrganisationKeyResponse> keys(@PathVariable String slug,
                                              @PathVariable Long organisationId,
                                              @AuthenticationPrincipal OrgActor requester) {
        Organisation organisation = organisationAccess.findOrganisationById(organisationId);
        if (requester != null) {
            if (requester.isPlatformUser()) {
                Platform platform = platformAccess.findPlatform(slug);
                platformAccess.requireMember(platform, requester);
            } else if (requester instanceof ClientPrincipal client) {
                if (!organisationId.equals(client.organisationId())) {
                    throw new com.nexxserve.nauth.exception.ForbiddenException(
                            "Client is not authorised for this organisation");
                }
            }
        }
        return orgKeyService.keys(organisation).stream()
                .map(key -> new OrganisationKeyResponse(key.getKid(), key.getPublicKey(), key.isActive()))
                .toList();
    }

    /** Retires the current key and provisions a fresh one; old tokens keep
     * verifying against the retired key until they expire. */
    @PostMapping("/rotate")
    public OrganisationKeyResponse rotate(@PathVariable String slug,
                                          @PathVariable Long organisationId,
                                          @AuthenticationPrincipal OrgActor requester) {
        Platform platform = platformAccess.findPlatform(slug);
        platformAccess.requireSuperUser(platform, requester);
        Organisation organisation = organisationAccess.findOrganisationById(organisationId);
        OrganisationSigningKey key = orgKeyService.rotateKey(organisation);
        return new OrganisationKeyResponse(key.getKid(), key.getPublicKey(), key.isActive());
    }

}
