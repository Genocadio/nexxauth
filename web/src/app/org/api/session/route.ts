import { NextResponse, type NextRequest } from "next/server";
import {
  ORG_ACCESS_COOKIE,
  ORG_REFRESH_COOKIE,
  orgCookieOptions,
  orgSlugFromToken,
  serverOrgMe,
  serverOrgRefresh,
} from "@/lib/org-auth";

/**
 * GET /org/api/session?platform={slug}
 *
 * The single authoritative source of session state for the server-rendered
 * org portal. Reads the httpOnly session cookies, verifies the access token
 * against the backend, silently rotates tokens on 401, and clears the cookies
 * when the session is definitively dead. Both portal pages consult this
 * before rendering; the Next proxy only does the fast cookie-presence gate.
 */
export async function GET(request: NextRequest) {
  const platformSlug = request.nextUrl.searchParams.get("platform");
  const accessToken = request.cookies.get(ORG_ACCESS_COOKIE)?.value;
  const refreshToken = request.cookies.get(ORG_REFRESH_COOKIE)?.value;

  const unauthenticated = (clear: boolean) => {
    const res = NextResponse.json({ authenticated: false }, { status: 401 });
    if (clear) {
      res.cookies.set(ORG_ACCESS_COOKIE, "", { ...orgCookieOptions, maxAge: 0 });
      res.cookies.set(ORG_REFRESH_COOKIE, "", { ...orgCookieOptions, maxAge: 0 });
    }
    return res;
  };

  if (!platformSlug || !accessToken) return unauthenticated(true);

  const organisationSlug = orgSlugFromToken(accessToken);
  if (!organisationSlug) return unauthenticated(true);

  const me = await serverOrgMe(platformSlug, organisationSlug, accessToken);
  if (me.ok) return NextResponse.json({ authenticated: true, user: me.data });

  // The access token was rejected. If the backend answered 401 (session
  // expired or revoked) try rotating with the refresh token and retry once.
  // Any other failure (network/5xx) keeps the cookies so a transient backend
  // outage does not log the user out.
  let refreshFailed = false;
  if (me.status === 401 && refreshToken) {
    const rotated = await serverOrgRefresh(platformSlug, refreshToken);
    if (rotated.ok) {
      const retry = await serverOrgMe(platformSlug, organisationSlug, rotated.data.accessToken);
      if (retry.ok) {
        const res = NextResponse.json({ authenticated: true, user: retry.data });
        res.cookies.set(ORG_ACCESS_COOKIE, rotated.data.accessToken, orgCookieOptions);
        res.cookies.set(ORG_REFRESH_COOKIE, rotated.data.refreshToken, orgCookieOptions);
        return res;
      }
      // The rotated access token was also rejected — the session is dead.
      refreshFailed = retry.status === 401;
    } else {
      refreshFailed = true;
    }
  }

  // Clear the cookies only when the backend definitively rejected the session
  // (401). Transient failures leave them in place for the next attempt.
  return unauthenticated(me.status === 401 && (!refreshToken || refreshFailed));
}
