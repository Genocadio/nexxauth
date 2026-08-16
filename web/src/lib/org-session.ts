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

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Fetches the org session from the /org/api/session route handler, retrying
 * on a transient backend failure (503). The route handler already keeps the
 * cookies on such failures, so a hiccup must not look like a logout — it is
 * retried here so the rendered page sees the authenticated session.
 */
export async function fetchOrgSession(platformSlug: string): Promise<OrgSessionResult> {
  const h = await headers();
  const proto = h.get("x-forwarded-proto") ?? "http";
  const host = h.get("host") ?? "localhost:3000";
  const url = `${proto}://${host}/org/api/session?platform=${encodeURIComponent(platformSlug)}`;

  for (let attempt = 0; attempt < 3; attempt++) {
    const res = await fetch(url, {
      headers: { cookie: h.get("cookie") ?? "" },
      cache: "no-store",
    });
    if (res.ok) return (await res.json()) as OrgSessionResult;
    if (res.status !== 503 || attempt === 2) return { authenticated: false };
    // Transient backend failure — back off briefly and retry. The session
    // route keeps the cookies on 503, so a hiccup must not look like a
    // logout; a cold CI backend can take a couple of seconds to answer.
    await sleep(400 * (attempt + 1));
  }
  return { authenticated: false };
}
