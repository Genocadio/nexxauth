/**
 * Public API URL derivation shown on the dashboards (Supabase-style). The
 * platform URL is the project base — `{BACKEND_PUBLIC_URL}/{slug}` — and all
 * other management endpoints live under it. No `/api/v1` is ever shown.
 */

function trimTrailingSlash(url: string): string {
  return url.replace(/\/+$/, "");
}

/** Public API base of a platform: `{backendOrigin}/{slug}`. */
export function platformApiUrl(backendOrigin: string | null | undefined, platformSlug: string): string | null {
  if (!backendOrigin || !platformSlug) return null;
  return `${trimTrailingSlash(backendOrigin)}/${platformSlug}`;
}

/**
 * Copyable platform/project base URL for the dashboards. Prefers the
 * backend-announced `apiBaseUrl` (already `{origin}/{slug}`); otherwise derives
 * it from the web app's own `BACKEND_PUBLIC_URL` env. Returns null when neither
 * is known, in which case the dashboards omit the row.
 */
export function resolvePlatformApiUrl(
  backendApiBase: string | null | undefined,
  platformSlug: string,
  envOrigin: string,
): string | null {
  if (backendApiBase) return trimTrailingSlash(backendApiBase);
  return platformApiUrl(envOrigin, platformSlug);
}

/**
 * Base URL used in the docs code snippets. Logged-in viewers get the same
 * project base the dashboards show; otherwise the docs fall back to the
 * `your-api-domain.com` placeholder (docs are public).
 */
export function docsApiUrl(
  backendApiBase: string | null | undefined,
  platformSlug: string,
  envOrigin: string,
): string {
  return resolvePlatformApiUrl(backendApiBase, platformSlug, envOrigin) ?? `https://your-api-domain.com/${platformSlug}`;
}
