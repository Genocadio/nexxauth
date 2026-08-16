package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.response.SlugSuggestionResponse;
import com.nexxserve.nexxauth.dto.response.SlugType;
import com.nexxserve.nexxauth.security.AuthenticatedUser;
import com.nexxserve.nexxauth.service.SlugSuggestionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Slug availability/suggestions for the register and organisation-create forms.
 * One endpoint serves both slug spaces: platform lookups are public (they are
 * rate limited per IP by {@link com.nexxserve.nexxauth.security.RateLimitFilter}),
 * organisation lookups require an authenticated member of the target platform
 * (enforced in {@link SlugSuggestionService}).
 */
@RestController
@RequestMapping
public class SlugSuggestionController {

    private final SlugSuggestionService slugSuggestionService;

    public SlugSuggestionController(SlugSuggestionService slugSuggestionService) {
        this.slugSuggestionService = slugSuggestionService;
    }

    @GetMapping("/slug-suggestions")
    public SlugSuggestionResponse suggest(@RequestParam SlugType type,
                                          @RequestParam(required = false) String name,
                                          @RequestParam(required = false) String slug,
                                          @RequestParam(required = false) String platformSlug,
                                          @AuthenticationPrincipal AuthenticatedUser requester) {
        return slugSuggestionService.suggest(type, name, slug, platformSlug, requester);
    }
}
