package com.nexxserve.nexxauth.dto.response;

/**
 * Which slug space a suggestion request is about. Platform slugs are globally
 * unique and their suggestion endpoint is public (rate limited); organisation
 * slugs are unique within their platform and require an authenticated platform
 * user.
 */
public enum SlugType {
    PLATFORM,
    ORGANISATION
}
