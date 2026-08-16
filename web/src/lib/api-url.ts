/**
 * Public API URL derivation shown on the dashboards (Supabase-style). The
 * platform's API base comes from the backend (BACKEND_PUBLIC_URL + slug);
 * organisation URLs extend it with the organisation slug.
 */

import { API_BASE_URL } from "@/lib/constants";

function trimTrailingSlash(url: string): string {
  return url.replace(/\/+$/, "");
}

/** Public API base of a platform: `{backendOrigin}/{slug}`. */
export function platformApiUrl(backendOrigin: string | null | undefined, platformSlug: string): string | null {
  if (!backendOrigin || !platformSlug) return null;
  return `${trimTrailingSlash(backendOrigin)}/${platformSlug}`;
}

/**
 * Absolute, copyable platform API base. Prefers the clean backend-announced
 * origin (`BACKEND_PUBLIC_URL` + slug); when the backend did not announce one,
 * falls back to the same-origin proxy base so a usable URL is always available
 * on the dashboards. Client-safe (guards the browser `location`).
 */
export function resolvePlatformApiUrl(
  backendApiBase: string | null | undefined,
  platformSlug: string,
): string {
  const clean = platformApiUrl(backendApiBase, platformSlug);
  if (clean) return clean;
  const origin = typeof window !== "undefined" ? window.location.origin : "";
  return `${origin}${API_BASE_URL}/${platformSlug}`;
}

/** Public API base of an organisation under a platform. */
export function organisationApiUrl(
  platformApiBase: string | null | undefined,
  organisationSlug: string,
): string | null {
  if (!platformApiBase || !organisationSlug) return null;
  return `${trimTrailingSlash(platformApiBase)}/organisations/${organisationSlug}`;
}