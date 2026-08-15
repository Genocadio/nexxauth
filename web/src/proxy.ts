import { NextResponse, type NextRequest } from "next/server";
import { ORG_ACCESS_COOKIE } from "@/lib/org-auth";

/**
 * Next 16 proxy (the middleware convention, renamed in v16). Fast auth gate
 * for the org portal profile: without the org session cookie, requests to
 * `/org/{platformSlug}/{organisationId}/profile` are redirected to the portal
 * login before any page renders.
 *
 * Cookie *presence* is only a fast path — every render still verifies the
 * session against the backend (session route handler), and the login/logout
 * server actions authenticate and rotate the cookies themselves.
 */
export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  if (pathname.endsWith("/profile") && !request.cookies.has(ORG_ACCESS_COOKIE)) {
    const loginUrl = new URL(pathname.slice(0, -"/profile".length), request.url);
    return NextResponse.redirect(loginUrl);
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/org/:path*/profile"],
};
