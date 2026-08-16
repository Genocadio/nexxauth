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

  // 401 = definitively logged out (cookies cleared); 503 = transient backend
  // failure (cookies kept, caller may retry). The portal pages redirect to the
  // login on 401 and can retry on 503, so a hiccup never looks like a logout.
  const unauthenticated = () => {
    const res = NextResponse.json({ authenticated: false }, { status: 401 });
    res.cookies.set(ORG_ACCESS_COOKIE, "", { ...orgCookieOptions, maxAge: 0 });
    res.cookies.set(ORG_REFRESH_COOKIE, "", { ...orgCookieOptions, maxAge: 0 });
    return res;
  };
  const transient = () => NextResponse.json({ authenticated: false, transient: true }, { status: 503 });

  if (!platformSlug || !accessToken) return unauthenticated();

  const organisationSlug = orgSlugFromToken(accessToken);
  if (!organisationSlug) return unauthenticated();

  const me = await serverOrgMe(platformSlug, organisationSlug, accessToken);
  if (me.ok) return NextResponse.json({ authenticated: true, user: me.data });

  // The access token was rejected. If the backend answered 401 (session
  // expired or revoked) try rotating with the refresh token and retry once.
  // A network/5xx failure is transient: keep the cookies and report 503 so
  // the caller retries instead of logging the user out.
  if (me.status === 401) {
    if (!refreshToken) return unauthenticated();

    const rotated = await serverOrgRefresh(platformSlug, refreshToken);
    if (!rotated.ok) {
      // Refresh token itself rejected (401) — the session is dead, clear the
      // cookies. Any other failure is transient and keeps them.
      return rotated.status === 401 ? unauthenticated() : transient();
    }

    const retry = await serverOrgMe(platformSlug, organisationSlug, rotated.data.accessToken);
    if (retry.ok) {
      const res = NextResponse.json({ authenticated: true, user: retry.data });
      res.cookies.set(ORG_ACCESS_COOKIE, rotated.data.accessToken, orgCookieOptions);
      res.cookies.set(ORG_REFRESH_COOKIE, rotated.data.refreshToken, orgCookieOptions);
      return res;
    }
    // Rotated access token rejected too — the session is dead.
    return retry.status === 401 ? unauthenticated() : transient();
  }

  // Any non-401 failure (network error, 5xx) is transient.
  return transient();
}
