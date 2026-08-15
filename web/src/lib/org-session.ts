import { headers } from "next/headers";
import type { OrganisationUserResponse } from "@/types/api";

/**
 * Server-only. Portal pages consult the /org/api/session route handler (the
 * single source of truth for session state + cookie rotation/clearing) before
 * rendering. The request cookies are forwarded so the handler sees them.
 */
export interface OrgSessionResult {
  authenticated: boolean;
  user?: OrganisationUserResponse;
}

export async function fetchOrgSession(platformSlug: string): Promise<OrgSessionResult> {
  const h = await headers();
  const proto = h.get("x-forwarded-proto") ?? "http";
  const host = h.get("host") ?? "localhost:3000";
  const url = `${proto}://${host}/org/api/session?platform=${encodeURIComponent(platformSlug)}`;

  const res = await fetch(url, {
    headers: { cookie: h.get("cookie") ?? "" },
    cache: "no-store",
  });
  if (!res.ok) return { authenticated: false };
  return (await res.json()) as OrgSessionResult;
}
