package com.nexxserve.nexxauth.dto.response;

import java.util.List;

/**
 * Slug suggestions for a display name and/or a user-typed candidate. The
 * candidate is what the form would submit (derived from the name when the user
 * did not type one); {@code suggestions} are the base and numbered variants,
 * each with its current availability so the UI can offer clickable options.
 */
public record SlugSuggestionResponse(
        SlugType type,
        SlugCandidate candidate,
        List<SlugCandidate> suggestions
) {

    /** A slug with its availability in the relevant slug space. */
    public record SlugCandidate(String slug, boolean available) {
    }
}
