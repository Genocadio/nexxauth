package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.response.LogEntryResponse;
import com.nexxserve.nexxauth.entity.LogCategory;
import com.nexxserve.nexxauth.entity.LogLevel;
import com.nexxserve.nexxauth.security.AuthenticatedUser;
import com.nexxserve.nexxauth.service.LogService;
import com.nexxserve.nexxauth.service.PlatformAccess;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;

@RestController
@RequestMapping
public class LogController {

    private final LogService logService;
    private final PlatformAccess platformAccess;

    public LogController(LogService logService, PlatformAccess platformAccess) {
        this.logService = logService;
        this.platformAccess = platformAccess;
    }

    /**
     * GET /{slug}/logs — paginated, filterable log query (platform-level).
     * Organisation scope is optional: pass organisationId to narrow results.
     */
    @GetMapping("/{slug}/logs")
    @PreAuthorize("hasRole('SUPER_USER') or hasRole('READ_ONLY')")
    public Page<LogEntryResponse> getLogs(
            @PathVariable String slug,
            @AuthenticationPrincipal AuthenticatedUser requester,
            @RequestParam(required = false) Long organisationId,
            @RequestParam(required = false) LogLevel level,
            @RequestParam(required = false) LogCategory category,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String clientKey,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        var platform = platformAccess.findPlatform(slug);
        platformAccess.requireMember(platform, requester);

        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        return logService.query(platform.getId(), organisationId, level, category, eventType, clientKey, domain, from, to, pageable);
    }

    /**
     * GET /{slug}/logs/stream — SSE endpoint for real-time log streaming.
     * Organisation scope is optional.
     */
    @GetMapping(value = "/{slug}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('SUPER_USER') or hasRole('READ_ONLY')")
    public SseEmitter streamLogs(
            @PathVariable String slug,
            @AuthenticationPrincipal AuthenticatedUser requester,
            @RequestParam(required = false) Long organisationId) {

        var platform = platformAccess.findPlatform(slug);
        platformAccess.requireMember(platform, requester);

        return logService.subscribe(platform.getId(), organisationId);
    }
}
