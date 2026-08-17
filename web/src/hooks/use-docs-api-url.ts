"use client";

import { usePlatform } from "@/hooks/queries";
import { docsApiUrl } from "@/lib/api-url";
import { useBackendOrigin } from "@/lib/backend-url";
import { selectPlatformSession, useAppSelector } from "@/store/store";

/**
 * Project base URL used in the docs code snippets. Logged-in viewers see the
 * same URL the dashboards show (backend-announced `apiBaseUrl`, falling back
 * to the web app's `BACKEND_PUBLIC_URL`); public visitors keep the
 * `your-api-domain.com` placeholder — the real origin is only exposed to the
 * console, like the dashboards.
 */
export function useDocsApiBaseUrl(platformSlug: string): string {
  const session = useAppSelector(selectPlatformSession);
  const platform = usePlatform();
  const backendOrigin = useBackendOrigin();

  if (!session) return `https://your-api-domain.com/${platformSlug}`;

  // Prefer the backend-announced apiBaseUrl when it belongs to this platform
  // (the exact value shown on the console overview / org dashboards).
  const apiBase = platform.data?.slug === platformSlug ? platform.data.apiBaseUrl : null;
  return docsApiUrl(apiBase, platformSlug, backendOrigin);
}
