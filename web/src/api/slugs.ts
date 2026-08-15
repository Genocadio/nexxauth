import { get } from "@/lib/api-client";
import { endpoints } from "@/lib/endpoints";
import type { SlugSuggestionResponse } from "@/types/api";
import type { SlugType } from "@/types/enums";

export interface SlugSuggestParams {
  type: SlugType;
  /** Display name — the backend derives suggested slugs from it. */
  name?: string;
  /** A user-typed candidate to validate availability for. */
  slug?: string;
  /** Parent platform, required for ORGANISATION lookups. */
  platformSlug?: string;
}

/**
 * Slug suggestions for the register and organisation-create forms. Platform
 * lookups are public (rate limited); organisation lookups send the platform
 * session so the backend can scope availability + membership.
 */
export const slugApi = {
  suggest: (params: SlugSuggestParams): Promise<SlugSuggestionResponse> => {
    const query = new URLSearchParams({ type: params.type });
    if (params.name) query.set("name", params.name);
    if (params.slug) query.set("slug", params.slug);
    if (params.platformSlug) query.set("platformSlug", params.platformSlug);
    return get<SlugSuggestionResponse>(
      `${endpoints.slugSuggestions}?${query.toString()}`,
      params.type === "ORGANISATION" ? "platform" : "none",
    );
  },
};
