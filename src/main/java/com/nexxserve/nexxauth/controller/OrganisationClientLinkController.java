package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.request.CreateOrganisationClientLinkRequest;
import com.nexxserve.nexxauth.dto.request.UpdateOrganisationClientLinkRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationClientLinkResponse;
import com.nexxserve.nexxauth.security.OrgActor;
import com.nexxserve.nexxauth.service.OrganisationClientLinkService;
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
 * Links (registered origins) for an organisation's clients. Each link
 * controls CORS behaviour and source restrictions for a specific origin.
 */
@RestController
@RequestMapping("/{slug}/organisations/{organisationId}/clients/{clientKey}/links")
public class OrganisationClientLinkController {

    private final OrganisationClientLinkService linkService;

    public OrganisationClientLinkController(OrganisationClientLinkService linkService) {
        this.linkService = linkService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_USER','READ_ONLY') or hasAuthority('PERM_ORGANISATION_USER_READ')")
    public List<OrganisationClientLinkResponse> list(@PathVariable String slug,
                                                      @PathVariable Long organisationId,
                                                      @PathVariable String clientKey,
                                                      @AuthenticationPrincipal OrgActor requester) {
        return linkService.list(slug, organisationId, clientKey, requester);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_USER')")
    public OrganisationClientLinkResponse create(@PathVariable String slug,
                                                  @PathVariable Long organisationId,
                                                  @PathVariable String clientKey,
                                                  @AuthenticationPrincipal OrgActor requester,
                                                  @Valid @RequestBody CreateOrganisationClientLinkRequest request) {
        return linkService.create(slug, organisationId, clientKey, requester, request);
    }

    @GetMapping("/{linkId}")
    @PreAuthorize("hasAnyRole('SUPER_USER','READ_ONLY') or hasAuthority('PERM_ORGANISATION_USER_READ')")
    public OrganisationClientLinkResponse get(@PathVariable String slug,
                                              @PathVariable Long organisationId,
                                              @PathVariable String clientKey,
                                              @PathVariable Long linkId,
                                              @AuthenticationPrincipal OrgActor requester) {
        return linkService.get(slug, organisationId, clientKey, linkId, requester);
    }

    @PatchMapping("/{linkId}")
    @PreAuthorize("hasRole('SUPER_USER')")
    public OrganisationClientLinkResponse update(@PathVariable String slug,
                                                  @PathVariable Long organisationId,
                                                  @PathVariable String clientKey,
                                                  @PathVariable Long linkId,
                                                  @AuthenticationPrincipal OrgActor requester,
                                                  @Valid @RequestBody UpdateOrganisationClientLinkRequest request) {
        return linkService.update(slug, organisationId, clientKey, linkId, requester, request);
    }

    @DeleteMapping("/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER_USER')")
    public ResponseEntity<Void> delete(@PathVariable String slug,
                                        @PathVariable Long organisationId,
                                        @PathVariable String clientKey,
                                        @PathVariable Long linkId,
                                        @AuthenticationPrincipal OrgActor requester) {
        linkService.delete(slug, organisationId, clientKey, linkId, requester);
        return ResponseEntity.noContent().build();
    }
}
