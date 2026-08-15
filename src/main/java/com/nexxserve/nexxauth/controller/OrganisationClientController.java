package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.request.CreateOrganisationClientRequest;
import com.nexxserve.nexxauth.dto.request.UpdateOrganisationClientRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationClientResponse;
import com.nexxserve.nexxauth.security.OrgActor;
import com.nexxserve.nexxauth.service.OrganisationClientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Clients of an organisation (the external apps talking to its API). Reads
 * require platform membership or org read access; writes the super user role.
 * The create and rotate-token responses carry the client's static token —
 * the only time it is ever shown.
 */
@RestController
@RequestMapping("/api/v1/platforms/{slug}/organisations/{organisationSlug}/clients")
public class OrganisationClientController {

    private final OrganisationClientService clientService;

    public OrganisationClientController(OrganisationClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_USER','READ_ONLY') or hasAuthority('PERM_ORGANISATION_USER_READ')")
    public List<OrganisationClientResponse> list(@PathVariable String slug,
                                                 @PathVariable String organisationSlug,
                                                 @AuthenticationPrincipal OrgActor requester) {
        return clientService.list(slug, organisationSlug, requester);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_USER')")
    public OrganisationClientResponse create(@PathVariable String slug,
                                             @PathVariable String organisationSlug,
                                             @AuthenticationPrincipal OrgActor requester,
                                             @Valid @RequestBody CreateOrganisationClientRequest request) {
        return clientService.create(slug, organisationSlug, requester, request);
    }

    @GetMapping("/{clientId}")
    @PreAuthorize("hasAnyRole('SUPER_USER','READ_ONLY') or hasAuthority('PERM_ORGANISATION_USER_READ')")
    public OrganisationClientResponse get(@PathVariable String slug,
                                          @PathVariable String organisationSlug,
                                          @PathVariable Long clientId,
                                          @AuthenticationPrincipal OrgActor requester) {
        return clientService.get(slug, organisationSlug, clientId, requester);
    }

    @PatchMapping("/{clientId}")
    @PreAuthorize("hasRole('SUPER_USER')")
    public OrganisationClientResponse update(@PathVariable String slug,
                                             @PathVariable String organisationSlug,
                                             @PathVariable Long clientId,
                                             @AuthenticationPrincipal OrgActor requester,
                                             @Valid @RequestBody UpdateOrganisationClientRequest request) {
        return clientService.update(slug, organisationSlug, clientId, requester, request);
    }

    @DeleteMapping("/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER_USER')")
    public ResponseEntity<Void> delete(@PathVariable String slug,
                                       @PathVariable String organisationSlug,
                                       @PathVariable Long clientId,
                                       @AuthenticationPrincipal OrgActor requester) {
        clientService.delete(slug, organisationSlug, clientId, requester);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{clientId}/rotate-token")
    @PreAuthorize("hasRole('SUPER_USER')")
    public OrganisationClientResponse rotateToken(@PathVariable String slug,
                                                  @PathVariable String organisationSlug,
                                                  @PathVariable Long clientId,
                                                  @AuthenticationPrincipal OrgActor requester) {
        return clientService.rotateToken(slug, organisationSlug, clientId, requester);
    }
}
