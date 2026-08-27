package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.response.OrganisationSessionResponse;
import com.nexxserve.nexxauth.dto.response.SessionTimelineEvent;
import com.nexxserve.nexxauth.entity.LogCategory;
import com.nexxserve.nexxauth.entity.LogLevel;
import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.Platform;
import com.nexxserve.nexxauth.security.OrgActor;
import com.nexxserve.nexxauth.service.AuthAuditService;
import com.nexxserve.nexxauth.service.OrganisationAccess;
import com.nexxserve.nexxauth.service.OrganisationSessionService;
import com.nexxserve.nexxauth.service.PlatformAccess;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Organisation session management: list active sessions, see per-user session
 * counts, and revoke individual or all sessions. Platform super users and
 * org users with the appropriate permissions can manage sessions.
 */
@RestController
@RequestMapping("/{slug}/organisations/{organisationId}/sessions")
public class OrganisationSessionController {

    private final PlatformAccess platformAccess;
    private final OrganisationAccess organisationAccess;
    private final OrganisationSessionService sessionService;
    private final AuthAuditService audit;

    public OrganisationSessionController(PlatformAccess platformAccess, OrganisationAccess organisationAccess,
                                          OrganisationSessionService sessionService, AuthAuditService audit) {
        this.platformAccess = platformAccess;
        this.organisationAccess = organisationAccess;
        this.sessionService = sessionService;
        this.audit = audit;
    }

    /**
     * List sessions for an organisation. Optionally filter by user id.
     * Sessions show: IP, user-agent, creation time, last activity, expected
     * expiry, whether active, and how many tokens in the chain.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_USER','READ_ONLY') or hasAuthority('ORG_USER')")
    public List<OrganisationSessionResponse> list(
            @PathVariable String slug,
            @PathVariable Long organisationId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String clientKey,
            @AuthenticationPrincipal OrgActor requester) {
        Organisation organisation = resolve(slug, organisationId, requester);
        return sessionService.listSessions(organisation.getId(), userId, clientKey);
    }

    /**
     * Token-rotation timeline for a single session. Returns all tokens
     * (including revoked/expired) in chronological order so the UI can draw
     * a visual activity strip.
     */
    @GetMapping("/{sessionId}/timeline")
    @PreAuthorize("hasAnyRole('SUPER_USER','READ_ONLY') or hasAuthority('ORG_USER')")
    public List<SessionTimelineEvent> timeline(
            @PathVariable String slug,
            @PathVariable Long organisationId,
            @PathVariable String sessionId,
            @AuthenticationPrincipal OrgActor requester) {
        Organisation organisation = resolve(slug, organisationId, requester);
        return sessionService.sessionTimeline(organisation.getId(), sessionId);
    }

    /**
     * Revoke a single session: all refresh tokens in that session are revoked
     * and the session becomes inactive immediately.
     */
    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_UPDATE')")
    public ResponseEntity<Void> revoke(
            @PathVariable String slug,
            @PathVariable Long organisationId,
            @PathVariable String sessionId,
            @AuthenticationPrincipal OrgActor requester) {
        Organisation organisation = resolve(slug, organisationId, requester);
        String revoked = sessionService.revokeSession(organisation.getId(), sessionId, null);
        audit.logPersisted(LogLevel.INFO, LogCategory.SECURITY, AuthAuditService.ORG_SESSION_REVOKED,
                requester.isPlatformUser() ? "platform" : "org_user",
                organisation.getSlug(), organisation.getId(), "sessionId=" + revoked);
        return ResponseEntity.noContent().build();
    }

    /**
     * Revoke all sessions for a specific user within the organisation.
     */
    @DeleteMapping(params = "userId")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER_USER') or hasAuthority('PERM_ORGANISATION_USER_UPDATE')")
    public ResponseEntity<Void> revokeAllForUser(
            @PathVariable String slug,
            @PathVariable Long organisationId,
            @RequestParam Long userId,
            @AuthenticationPrincipal OrgActor requester) {
        Organisation organisation = resolve(slug, organisationId, requester);
        int count = sessionService.revokeAllUserSessions(organisation.getId(), userId);
        audit.logPersisted(LogLevel.INFO, LogCategory.SECURITY, AuthAuditService.ORG_SESSIONS_REVOKED_ALL,
                requester.isPlatformUser() ? "platform" : "org_user",
                organisation.getSlug(), organisation.getId(), "userId=" + userId + " count=" + count);
        return ResponseEntity.noContent().build();
    }

    private Organisation resolve(String platformSlug, Long organisationId, OrgActor requester) {
        Platform platform = platformAccess.findPlatform(platformSlug);
        Organisation organisation = organisationAccess.findOrganisationById(organisationId);
        if (requester.isPlatformUser()) {
            platformAccess.requireMember(platform, requester);
        } else {
            organisationAccess.requireOrgUserOf(organisation, requester);
        }
        return organisation;
    }
}
