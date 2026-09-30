package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.request.AddUserEmailRequest;
import com.nexxserve.nexxauth.dto.request.AddUserPhoneRequest;
import com.nexxserve.nexxauth.dto.request.ChangePasswordRequest;
import com.nexxserve.nexxauth.dto.request.CreateOrganisationUserRequest;
import com.nexxserve.nexxauth.dto.request.SendPasswordResetRequest;
import com.nexxserve.nexxauth.dto.request.SendUserVerificationRequest;
import com.nexxserve.nexxauth.dto.request.SetAddressVerifiedRequest;
import com.nexxserve.nexxauth.dto.request.UpdateOrganisationUserRequest;
import com.nexxserve.nexxauth.dto.request.UpdateOwnProfileRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationUserResponse;
import com.nexxserve.nexxauth.dto.response.VerificationRequestResponse;
import com.nexxserve.nexxauth.security.OrgActor;
import com.nexxserve.nexxauth.security.OrgUserPrincipal;
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
import org.springframework.web.bind.annotation.PutMapping;
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
@RequestMapping("/{slug}/organisations/{organisationId}/users")
public class OrganisationUserController {

    private final OrganisationUserService userService;

    public OrganisationUserController(OrganisationUserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_USER','READ_ONLY') or hasAuthority('PERM_ORGANISATION_USER_READ')")
    public List<OrganisationUserResponse> list(@PathVariable String slug,
                                               @PathVariable Long organisationId,
                                               @AuthenticationPrincipal OrgActor requester) {
        return userService.list(slug, organisationId, requester);
    }

    /** Self-service read: every org user can read their own profile. */
    @GetMapping("/me")
    public OrganisationUserResponse me(@PathVariable String slug,
                                       @PathVariable Long organisationId,
                                       @AuthenticationPrincipal OrgActor requester) {
        return userService.me(slug, organisationId, requester);
    }

    /** Self-service profile update: every org user can update their own profile
     * (first/last name and metadata). Used to complete the UPDATE_PROFILE action
     * (e.g. filling values for required org user fields). */
    @PatchMapping("/me")
    public OrganisationUserResponse updateOwnProfile(@PathVariable String slug,
                                                     @PathVariable Long organisationId,
                                                     @AuthenticationPrincipal OrgActor requester,
                                                     @Valid @RequestBody UpdateOwnProfileRequest request) {
        return userService.updateOwnProfile(slug, organisationId, requester, request);
    }

    /** Self-service password change: completes the CHANGE_PASSWORD action. Every
     * org user can change their own password; a temporary/forced password stays
     * in force until changed here. */
    @PostMapping("/me/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> changePassword(@PathVariable String slug,
                                               @PathVariable Long organisationId,
                                               @AuthenticationPrincipal OrgActor requester,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(slug, organisationId, requester, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_CREATE')")
    public OrganisationUserResponse create(@PathVariable String slug,
                                           @PathVariable Long organisationId,
                                           @AuthenticationPrincipal OrgActor requester,
                                           @Valid @RequestBody CreateOrganisationUserRequest request) {
        return userService.create(slug, organisationId, requester, request);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_USER','READ_ONLY') or hasAuthority('PERM_ORGANISATION_USER_READ')")
    public OrganisationUserResponse get(@PathVariable String slug,
                                        @PathVariable Long organisationId,
                                        @PathVariable Long userId,
                                        @AuthenticationPrincipal OrgActor requester) {
        return userService.get(slug, organisationId, userId, requester);
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_UPDATE')")
    public OrganisationUserResponse update(@PathVariable String slug,
                                           @PathVariable Long organisationId,
                                           @PathVariable Long userId,
                                           @AuthenticationPrincipal OrgActor requester,
                                           @Valid @RequestBody UpdateOrganisationUserRequest request) {
        return userService.update(slug, organisationId, userId, requester, request);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable String slug,
                                       @PathVariable Long organisationId,
                                       @PathVariable Long userId,
                                       @AuthenticationPrincipal OrgActor requester) {
        userService.delete(slug, organisationId, userId, requester);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // User Email Management (Self-Service & Admin)
    // -------------------------------------------------------------------------

    @PostMapping("/me/emails")
    public OrganisationUserResponse addOwnEmail(@PathVariable String slug,
                                                @PathVariable Long organisationId,
                                                @AuthenticationPrincipal OrgActor requester,
                                                @Valid @RequestBody AddUserEmailRequest request) {
        Long userId = ((OrgUserPrincipal) requester).id();
        return userService.addEmail(slug, organisationId, userId, requester, request);
    }

    @DeleteMapping("/me/emails/{emailId}")
    public OrganisationUserResponse deleteOwnEmail(@PathVariable String slug,
                                                   @PathVariable Long organisationId,
                                                   @PathVariable Long emailId,
                                                   @AuthenticationPrincipal OrgActor requester) {
        Long userId = ((OrgUserPrincipal) requester).id();
        return userService.deleteEmail(slug, organisationId, userId, emailId, requester);
    }

    @PutMapping("/me/emails/{emailId}/primary")
    public OrganisationUserResponse setOwnPrimaryEmail(@PathVariable String slug,
                                                       @PathVariable Long organisationId,
                                                       @PathVariable Long emailId,
                                                       @AuthenticationPrincipal OrgActor requester) {
        Long userId = ((OrgUserPrincipal) requester).id();
        return userService.setPrimaryEmail(slug, organisationId, userId, emailId, requester);
    }

    @PostMapping("/{userId}/emails")
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_UPDATE')")
    public OrganisationUserResponse addEmail(@PathVariable String slug,
                                             @PathVariable Long organisationId,
                                             @PathVariable Long userId,
                                             @AuthenticationPrincipal OrgActor requester,
                                             @Valid @RequestBody AddUserEmailRequest request) {
        return userService.addEmail(slug, organisationId, userId, requester, request);
    }

    @DeleteMapping("/{userId}/emails/{emailId}")
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_UPDATE')")
    public OrganisationUserResponse deleteEmail(@PathVariable String slug,
                                                @PathVariable Long organisationId,
                                                @PathVariable Long userId,
                                                @PathVariable Long emailId,
                                                @AuthenticationPrincipal OrgActor requester) {
        return userService.deleteEmail(slug, organisationId, userId, emailId, requester);
    }

    @PutMapping("/{userId}/emails/{emailId}/primary")
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_UPDATE')")
    public OrganisationUserResponse setPrimaryEmail(@PathVariable String slug,
                                                    @PathVariable Long organisationId,
                                                    @PathVariable Long userId,
                                                    @PathVariable Long emailId,
                                                    @AuthenticationPrincipal OrgActor requester) {
        return userService.setPrimaryEmail(slug, organisationId, userId, emailId, requester);
    }

    // -------------------------------------------------------------------------
    // User Phone Management (Self-Service & Admin)
    // -------------------------------------------------------------------------

    @PostMapping("/me/phones")
    public OrganisationUserResponse addOwnPhone(@PathVariable String slug,
                                                @PathVariable Long organisationId,
                                                @AuthenticationPrincipal OrgActor requester,
                                                @Valid @RequestBody AddUserPhoneRequest request) {
        Long userId = ((OrgUserPrincipal) requester).id();
        return userService.addPhone(slug, organisationId, userId, requester, request);
    }

    @DeleteMapping("/me/phones/{phoneId}")
    public OrganisationUserResponse deleteOwnPhone(@PathVariable String slug,
                                                   @PathVariable Long organisationId,
                                                   @PathVariable Long phoneId,
                                                   @AuthenticationPrincipal OrgActor requester) {
        Long userId = ((OrgUserPrincipal) requester).id();
        return userService.deletePhone(slug, organisationId, userId, phoneId, requester);
    }

    @PutMapping("/me/phones/{phoneId}/primary")
    public OrganisationUserResponse setOwnPrimaryPhone(@PathVariable String slug,
                                                       @PathVariable Long organisationId,
                                                       @PathVariable Long phoneId,
                                                       @AuthenticationPrincipal OrgActor requester) {
        Long userId = ((OrgUserPrincipal) requester).id();
        return userService.setPrimaryPhone(slug, organisationId, userId, phoneId, requester);
    }

    @PostMapping("/{userId}/phones")
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_UPDATE')")
    public OrganisationUserResponse addPhone(@PathVariable String slug,
                                             @PathVariable Long organisationId,
                                             @PathVariable Long userId,
                                             @AuthenticationPrincipal OrgActor requester,
                                             @Valid @RequestBody AddUserPhoneRequest request) {
        return userService.addPhone(slug, organisationId, userId, requester, request);
    }

    @DeleteMapping("/{userId}/phones/{phoneId}")
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_UPDATE')")
    public OrganisationUserResponse deletePhone(@PathVariable String slug,
                                                @PathVariable Long organisationId,
                                                @PathVariable Long userId,
                                                @PathVariable Long phoneId,
                                                @AuthenticationPrincipal OrgActor requester) {
        return userService.deletePhone(slug, organisationId, userId, phoneId, requester);
    }

    @PutMapping("/{userId}/phones/{phoneId}/primary")
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_UPDATE')")
    public OrganisationUserResponse setPrimaryPhone(@PathVariable String slug,
                                                    @PathVariable Long organisationId,
                                                    @PathVariable Long userId,
                                                    @PathVariable Long phoneId,
                                                    @AuthenticationPrincipal OrgActor requester) {
        return userService.setPrimaryPhone(slug, organisationId, userId, phoneId, requester);
    }

    /** Administrator-triggered verification send for one of the user's own
     * addresses, so an unverified email or phone can be proven now instead of
     * waiting for the user's next sign-in. */
    @PostMapping("/{userId}/verifications")
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_UPDATE')")
    public VerificationRequestResponse sendVerification(@PathVariable String slug,
                                                        @PathVariable Long organisationId,
                                                        @PathVariable Long userId,
                                                        @AuthenticationPrincipal OrgActor requester,
                                                        @Valid @RequestBody SendUserVerificationRequest request) {
        return userService.sendVerification(slug, organisationId, userId, requester, request);
    }

    /** Sends the user a password reset so they choose their own password. */
    @PostMapping("/{userId}/password-reset")
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_UPDATE')")
    public VerificationRequestResponse sendPasswordReset(@PathVariable String slug,
                                                        @PathVariable Long organisationId,
                                                        @PathVariable Long userId,
                                                        @AuthenticationPrincipal OrgActor requester,
                                                        @Valid @RequestBody(required = false) SendPasswordResetRequest request) {
        return userService.sendPasswordReset(slug, organisationId, userId, requester,
                request != null ? request : new SendPasswordResetRequest(null, null, null));
    }

    /** Manual override of an email's verified state (no proof of ownership). */
    @PatchMapping("/{userId}/emails/{emailId}/verified")
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_UPDATE')")
    public OrganisationUserResponse setEmailVerified(@PathVariable String slug,
                                                    @PathVariable Long organisationId,
                                                    @PathVariable Long userId,
                                                    @PathVariable Long emailId,
                                                    @AuthenticationPrincipal OrgActor requester,
                                                    @Valid @RequestBody SetAddressVerifiedRequest request) {
        return userService.setAddressVerified(slug, organisationId, userId, emailId, true,
                Boolean.TRUE.equals(request.verified()), requester);
    }

    /** Manual override of a phone's verified state (no proof of ownership). */
    @PatchMapping("/{userId}/phones/{phoneId}/verified")
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_UPDATE')")
    public OrganisationUserResponse setPhoneVerified(@PathVariable String slug,
                                                    @PathVariable Long organisationId,
                                                    @PathVariable Long userId,
                                                    @PathVariable Long phoneId,
                                                    @AuthenticationPrincipal OrgActor requester,
                                                    @Valid @RequestBody SetAddressVerifiedRequest request) {
        return userService.setAddressVerified(slug, organisationId, userId, phoneId, false,
                Boolean.TRUE.equals(request.verified()), requester);
    }
}
