package com.nexxserve.nauth.service;

import com.nexxserve.nauth.dto.response.SlugSuggestionResponse;
import com.nexxserve.nauth.dto.response.SlugType;
import com.nexxserve.nauth.entity.Platform;
import com.nexxserve.nauth.exception.BadRequestException;
import com.nexxserve.nauth.exception.ResourceNotFoundException;
import com.nexxserve.nauth.exception.UnauthorizedException;
import com.nexxserve.nauth.repository.OrganisationRepository;
import com.nexxserve.nauth.repository.PlatformRepository;
import com.nexxserve.nauth.security.AuthenticatedUser;
import com.nexxserve.nauth.util.Slugs;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Slug availability + suggestion lookups shared by the platform register form
 * and the organisation create dialog. Derives variants the same way
 * {@link Slugs} does on creation, and reports each one's availability so the
 * UI can offer clickable alternatives while the user types.
 */
@Service
public class SlugSuggestionService {

    /** Mirrors the {@code @Pattern} on CreateOrganisationRequest.slug. */
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final int MAX_SLUG_LENGTH = 100;
    private static final int MAX_SUGGESTIONS = 4;

    private final PlatformRepository platformRepository;
    private final OrganisationRepository organisationRepository;
    private final PlatformAccess platformAccess;

    public SlugSuggestionService(PlatformRepository platformRepository,
                                 OrganisationRepository organisationRepository,
                                 PlatformAccess platformAccess) {
        this.platformRepository = platformRepository;
        this.organisationRepository = organisationRepository;
        this.platformAccess = platformAccess;
    }

    /**
     * Reports the candidate (derived from {@code name} when {@code slug} is
     * absent, otherwise the typed slug) plus numbered variants, each with its
     * availability. Organisation lookups require an authenticated member of the
     * target platform; platform lookups are public (rate limited upstream).
     */
    public SlugSuggestionResponse suggest(SlugType type, String name, String slug, String platformSlug,
                                          AuthenticatedUser requester) {
        boolean hasSlug = slug != null && !slug.isBlank();
        boolean hasName = name != null && !name.isBlank();
        if (!hasSlug && !hasName) {
            throw new BadRequestException("Provide a name or a slug to check availability");
        }

        Predicate<String> taken = takenFor(type, platformSlug, requester);

        String base;
        if (hasSlug) {
            validate(slug);
            base = slug;
        } else {
            base = Slugs.slugify(name);
        }

        SlugSuggestionResponse.SlugCandidate candidate =
                new SlugSuggestionResponse.SlugCandidate(base, !taken.test(base));
        List<SlugSuggestionResponse.SlugCandidate> suggestions = new ArrayList<>(MAX_SUGGESTIONS);
        for (int i = 0; i < MAX_SUGGESTIONS; i++) {
            String variant = i == 0 ? base : base + "-" + (i + 1);
            if (variant.length() > MAX_SLUG_LENGTH) {
                break;
            }
            suggestions.add(new SlugSuggestionResponse.SlugCandidate(variant, !taken.test(variant)));
        }
        return new SlugSuggestionResponse(type, candidate, suggestions);
    }

    private Predicate<String> takenFor(SlugType type, String platformSlug, AuthenticatedUser requester) {
        if (type == SlugType.PLATFORM) {
            return platformRepository::existsBySlug;
        }
        if (requester == null) {
            throw new UnauthorizedException("Authentication required");
        }
        if (platformSlug == null || platformSlug.isBlank()) {
            throw new BadRequestException("platformSlug is required when checking organisation slugs");
        }
        Platform platform = platformRepository.findBySlug(platformSlug)
                .orElseThrow(() -> ResourceNotFoundException.of("Platform", platformSlug));
        platformAccess.requireMember(platform, requester);
        return candidate -> organisationRepository.existsByPlatformIdAndSlug(platform.getId(), candidate);
    }

    private void validate(String slug) {
        if (slug.length() > MAX_SLUG_LENGTH || !SLUG_PATTERN.matcher(slug).matches()) {
            throw new BadRequestException("Slug may only contain lowercase letters, digits and single hyphens");
        }
    }
}
