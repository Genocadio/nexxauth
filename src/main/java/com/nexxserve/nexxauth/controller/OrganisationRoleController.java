package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.request.CreateOrganisationRoleRequest;
import com.nexxserve.nexxauth.dto.request.UpdateOrganisationRoleRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationRoleResponse;
import com.nexxserve.nexxauth.security.OrgActor;
import com.nexxserve.nexxauth.service.OrganisationRoleService;
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
 * Roles of an organisation. Permissions are fixed in the application; roles
 * group them per organisation. Reads require platform membership, writes the
 * super user role.
 */
@RestController
@RequestMapping("/{slug}/organisations/{organisationId}/roles")
public class OrganisationRoleController {

    private final OrganisationRoleService roleService;

    public OrganisationRoleController(OrganisationRoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_USER','READ_ONLY') or hasAuthority('PERM_ORGANISATION_USER_READ')")
    public List<OrganisationRoleResponse> list(@PathVariable String slug,
                                               @PathVariable Long organisationId,
                                               @AuthenticationPrincipal OrgActor requester) {
        return roleService.list(slug, organisationId, requester);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_USER')")
    public OrganisationRoleResponse create(@PathVariable String slug,
                                           @PathVariable Long organisationId,
                                           @AuthenticationPrincipal OrgActor requester,
                                           @Valid @RequestBody CreateOrganisationRoleRequest request) {
        return roleService.create(slug, organisationId, requester, request);
    }

    @GetMapping("/{roleId}")
    @PreAuthorize("hasAnyRole('SUPER_USER','READ_ONLY') or hasAuthority('PERM_ORGANISATION_USER_READ')")
    public OrganisationRoleResponse get(@PathVariable String slug,
                                        @PathVariable Long organisationId,
                                        @PathVariable Long roleId,
                                        @AuthenticationPrincipal OrgActor requester) {
        return roleService.get(slug, organisationId, roleId, requester);
    }

    @PatchMapping("/{roleId}")
    @PreAuthorize("hasRole('SUPER_USER')")
    public OrganisationRoleResponse update(@PathVariable String slug,
                                           @PathVariable Long organisationId,
                                           @PathVariable Long roleId,
                                           @AuthenticationPrincipal OrgActor requester,
                                           @Valid @RequestBody UpdateOrganisationRoleRequest request) {
        return roleService.update(slug, organisationId, roleId, requester, request);
    }

    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER_USER')")
    public ResponseEntity<Void> delete(@PathVariable String slug,
                                       @PathVariable Long organisationId,
                                       @PathVariable Long roleId,
                                       @AuthenticationPrincipal OrgActor requester) {
        roleService.delete(slug, organisationId, roleId, requester);
        return ResponseEntity.noContent().build();
    }
}
