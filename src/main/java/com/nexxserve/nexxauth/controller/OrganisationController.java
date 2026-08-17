package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.request.CreateOrganisationRequest;
import com.nexxserve.nexxauth.dto.request.UpdateOrganisationRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationResponse;
import com.nexxserve.nexxauth.security.OrgActor;
import com.nexxserve.nexxauth.service.OrganisationService;
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
 * Organisations of a platform, addressed under the (immutable) platform slug.
 * Reads require platform membership; writes require the super user role.
 */
@RestController
@RequestMapping("/{slug}/organisations")
public class OrganisationController {

    private final OrganisationService organisationService;

    public OrganisationController(OrganisationService organisationService) {
        this.organisationService = organisationService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_USER','READ_ONLY')")
    public List<OrganisationResponse> list(@PathVariable String slug,
                                           @AuthenticationPrincipal OrgActor requester) {
        return organisationService.list(slug, requester);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_USER')")
    public OrganisationResponse create(@PathVariable String slug,
                                       @AuthenticationPrincipal OrgActor requester,
                                       @Valid @RequestBody CreateOrganisationRequest request) {
        return organisationService.create(slug, requester, request);
    }

    @GetMapping("/{organisationId}")
    @PreAuthorize("hasAnyRole('SUPER_USER','READ_ONLY') or hasAuthority('ORG_USER')")
    public OrganisationResponse get(@PathVariable String slug,
                                    @PathVariable Long organisationId,
                                    @AuthenticationPrincipal OrgActor requester) {
        return organisationService.get(slug, organisationId, requester);
    }

    @PatchMapping("/{organisationId}")
    @PreAuthorize("hasRole('SUPER_USER')")
    public OrganisationResponse update(@PathVariable String slug,
                                       @PathVariable Long organisationId,
                                       @AuthenticationPrincipal OrgActor requester,
                                       @Valid @RequestBody UpdateOrganisationRequest request) {
        return organisationService.update(slug, organisationId, requester, request);
    }

    @DeleteMapping("/{organisationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER_USER')")
    public ResponseEntity<Void> delete(@PathVariable String slug,
                                       @PathVariable Long organisationId,
                                       @AuthenticationPrincipal OrgActor requester) {
        organisationService.delete(slug, organisationId, requester);
        return ResponseEntity.noContent().build();
    }
}
