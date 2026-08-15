package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.request.CreateOrganisationUserFieldRequest;
import com.nexxserve.nexxauth.dto.request.UpdateOrganisationUserFieldRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationUserFieldResponse;
import com.nexxserve.nexxauth.security.OrgActor;
import com.nexxserve.nexxauth.service.OrganisationUserFieldService;
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
 * Organisation-defined user fields. Reads require platform membership (or the
 * org role permission), writes the super user role or the corresponding org
 * role permission, mirroring the user management endpoints.
 */
@RestController
@RequestMapping("/api/v1/platforms/{slug}/organisations/{organisationSlug}/user-fields")
public class OrganisationUserFieldController {

    private final OrganisationUserFieldService fieldService;

    public OrganisationUserFieldController(OrganisationUserFieldService fieldService) {
        this.fieldService = fieldService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_USER','READ_ONLY') or hasAuthority('PERM_ORGANISATION_USER_FIELD_READ')")
    public List<OrganisationUserFieldResponse> list(@PathVariable String slug,
                                                    @PathVariable String organisationSlug,
                                                    @AuthenticationPrincipal OrgActor requester) {
        return fieldService.listFields(slug, organisationSlug, requester);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_FIELD_CREATE')")
    public OrganisationUserFieldResponse create(@PathVariable String slug,
                                                @PathVariable String organisationSlug,
                                                @AuthenticationPrincipal OrgActor requester,
                                                @Valid @RequestBody CreateOrganisationUserFieldRequest request) {
        return fieldService.createField(slug, organisationSlug, requester, request);
    }

    @PatchMapping("/{fieldId}")
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_FIELD_UPDATE')")
    public OrganisationUserFieldResponse update(@PathVariable String slug,
                                                @PathVariable String organisationSlug,
                                                @PathVariable Long fieldId,
                                                @AuthenticationPrincipal OrgActor requester,
                                                @Valid @RequestBody UpdateOrganisationUserFieldRequest request) {
        return fieldService.updateField(slug, organisationSlug, fieldId, requester, request);
    }

    @DeleteMapping("/{fieldId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_FIELD_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable String slug,
                                       @PathVariable String organisationSlug,
                                       @PathVariable Long fieldId,
                                       @AuthenticationPrincipal OrgActor requester) {
        fieldService.deleteField(slug, organisationSlug, fieldId, requester);
        return ResponseEntity.noContent().build();
    }
}
