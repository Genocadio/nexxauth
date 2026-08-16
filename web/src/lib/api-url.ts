/**
 * Public API URL derivation shown on the dashboards (Supabase-style). The
 * platform's API base comes from the backend (BACKEND_PUBLIC_URL + slug);
 * organisation URLs extend it with the organisation slug.
 */

function trimTrailingSlash(url: string): string {
  return url.replace(/\/+$/, "");
}

/** Public API base of a platform: `{backendOrigin}/{slug}`. */
export function platformApiUrl(backendOrigin: string | null | undefined, platformSlug: string): string | null {
  if (!backendOrigin || !platformSlug) return null;
  return `${trimTrailingSlash(backendOrigin)}/${platformSlug}`;
}

/** Public API base of an organisation under a platform. */
export function organisationApiUrl(
  platformApiBase: string | null | undefined,
  organisationSlug: string,
): string | null {
  if (!platformApiBase || !organisationSlug) return null;
  return `${trimTrailingSlash(platformApiBase)}/organisations/${organisationSlug}`;
}