package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.request.ChangePasswordRequest;
import com.nexxserve.nexxauth.dto.request.CreateOrganisationUserRequest;
import com.nexxserve.nexxauth.dto.request.UpdateOrganisationUserRequest;
import com.nexxserve.nexxauth.dto.request.UpdateOwnProfileRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationUserResponse;
import com.nexxserve.nexxauth.security.OrgActor;
import com.nexxserve.nexxauth.service.OrganisationUserService;
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
 * Users of an organisation. Tokens may come from a platform user (current
 * behaviour: member reads, super user writes) or an organisation user, who is
 * gated by the permissions of their org roles. Every org user can read their
 * own profile via {@code /me} regardless of permissions.
 */
@RestController
@RequestMapping("/{slug}/organisations/{organisationSlug}/users")
public class OrganisationUserController {

    private final OrganisationUserService userService;

    public OrganisationUserController(OrganisationUserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_USER','READ_ONLY') or hasAuthority('PERM_ORGANISATION_USER_READ')")
    public List<OrganisationUserResponse> list(@PathVariable String slug,
                                               @PathVariable String organisationSlug,
                                               @AuthenticationPrincipal OrgActor requester) {
        return userService.list(slug, organisationSlug, requester);
    }

    /** Self-service read: every org user can read their own profile. */
    @GetMapping("/me")
    public OrganisationUserResponse me(@PathVariable String slug,
                                       @PathVariable String organisationSlug,
                                       @AuthenticationPrincipal OrgActor requester) {
        return userService.me(slug, organisationSlug, requester);
    }

    /** Self-service profile update: every org user can update their own profile
     * (first/last name and metadata). Used to complete the UPDATE_PROFILE action
     * (e.g. filling values for required org user fields). */
    @PatchMapping("/me")
    public OrganisationUserResponse updateOwnProfile(@PathVariable String slug,
                                                     @PathVariable String organisationSlug,
                                                     @AuthenticationPrincipal OrgActor requester,
                                                     @Valid @RequestBody UpdateOwnProfileRequest request) {
        return userService.updateOwnProfile(slug, organisationSlug, requester, request);
    }

    /** Self-service password change: completes the CHANGE_PASSWORD action. Every
     * org user can change their own password; a temporary/forced password stays
     * in force until changed here. */
    @PostMapping("/me/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> changePassword(@PathVariable String slug,
                                               @PathVariable String organisationSlug,
                                               @AuthenticationPrincipal OrgActor requester,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(slug, organisationSlug, requester, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_CREATE')")
    public OrganisationUserResponse create(@PathVariable String slug,
                                           @PathVariable String organisationSlug,
                                           @AuthenticationPrincipal OrgActor requester,
                                           @Valid @RequestBody CreateOrganisationUserRequest request) {
        return userService.create(slug, organisationSlug, requester, request);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_USER','READ_ONLY') or hasAuthority('PERM_ORGANISATION_USER_READ')")
    public OrganisationUserResponse get(@PathVariable String slug,
                                        @PathVariable String organisationSlug,
                                        @PathVariable Long userId,
                                        @AuthenticationPrincipal OrgActor requester) {
        return userService.get(slug, organisationSlug, userId, requester);
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_UPDATE')")
    public OrganisationUserResponse update(@PathVariable String slug,
                                           @PathVariable String organisationSlug,
                                           @PathVariable Long userId,
                                           @AuthenticationPrincipal OrgActor requester,
                                           @Valid @RequestBody UpdateOrganisationUserRequest request) {
        return userService.update(slug, organisationSlug, userId, requester, request);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable String slug,
                                       @PathVariable String organisationSlug,
                                       @PathVariable Long userId,
                                       @AuthenticationPrincipal OrgActor requester) {
        userService.delete(slug, organisationSlug, userId, requester);
        return ResponseEntity.noContent().build();
    }
}
