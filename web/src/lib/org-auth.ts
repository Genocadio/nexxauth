/**
 * Server-only organisation auth. The org portal authenticates with signed
 * httpOnly cookies instead of Redux/localStorage, so all of its API traffic
 * happens server-side: server actions (login/logout), a session route handler
 * (verify/refresh) and the Next proxy gate.
 *
 * Never import this module from client code — it reads `process.env` and is
 * only safe in the Node runtime.
 */

import { decodeJwtPayload } from "@/lib/jwt";
import type { OrganisationUserResponse, OrgAuthResponse } from "@/types/api";
import type { LogoutRequest, OrgLoginRequest, RefreshTokenRequest } from "@/types/requests";

/** httpOnly cookies carrying the org session (access + rotating refresh). */
export const ORG_ACCESS_COOKIE = "org_access_token";
export const ORG_REFRESH_COOKIE = "org_refresh_token";

/** Upper bound for the session cookies; the backend enforces the real TTL. */
export const ORG_COOKIE_MAX_AGE = 60 * 60 * 24 * 30;

export const orgCookieOptions = {
  httpOnly: true,
  sameSite: "lax" as const,
  secure: process.env.NODE_ENV === "production",
  path: "/org",
  maxAge: ORG_COOKIE_MAX_AGE,
};

/**
 * Absolute base URL of the backend for server-side calls (browser requests go
 * through the same-origin /api/v1 proxy instead). Override API_SERVER_URL to
 * point at a remote backend in production.
 */
const API_SERVER_URL = process.env.API_SERVER_URL ?? "http://localhost:8080";

// Org endpoints live at the platform's clean root origin (/{slug}/...), so the
// server-side calls skip the /api/v1 prefix the browser proxy adds.
const apiPath = (path: string) => `${API_SERVER_URL}${path}`;

/** Claims carried by the org access token (see backend OrgJwtService). */
export interface OrgJwtClaims {
  orgId?: number;
  orgSlug?: string;
  roles?: string[];
}

/** The organisation ID from the access token claims, if any. */
export function orgIdFromToken(accessToken: string): number | undefined {
  return decodeJwtPayload<OrgJwtClaims>(accessToken)?.orgId;
}

/** The organisation slug from the access token claims, if any (display-only). */
export function orgSlugFromToken(accessToken: string): string | undefined {
  return decodeJwtPayload<OrgJwtClaims>(accessToken)?.orgSlug;
}

type ApiResult<T> = { ok: true; data: T } | { ok: false; error: string; status: number };

async function postJson<T>(
  path: string,
  body: unknown,
  token?: string,
  extraHeaders?: Record<string, string>,
): Promise<ApiResult<T>> {
  let res: Response;
  try {
    res = await fetch(apiPath(path), {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...extraHeaders,
      },
      body: JSON.stringify(body),
    });
  } catch {
    return { ok: false, error: "Could not reach the server. Please try again.", status: 0 };
  }
  if (!res.ok) {
    return { ok: false, error: await errorMessage(res), status: res.status };
  }
  if (res.status === 204) return { ok: true, data: undefined as T };
  return { ok: true, data: (await res.json()) as T };
}

async function errorMessage(res: Response): Promise<string> {
  try {
    const payload = (await res.json()) as { message?: string };
    if (payload.message) return payload.message;
  } catch {
    // non-JSON body — fall back to the status text
  }
  return res.statusText || "Request failed";
}

/** POST /{slug}/auth/login */
export function serverOrgLogin(
  platformSlug: string,
  body: OrgLoginRequest,
  userAgent?: string,
): Promise<ApiResult<OrgAuthResponse>> {
  const headers: Record<string, string> = {};
  if (userAgent) headers["User-Agent"] = userAgent;
  return postJson<OrgAuthResponse>(`/${platformSlug}/auth/login`, body, undefined, headers);
}

/** POST /{slug}/auth/refresh — rotates the refresh token. */
export function serverOrgRefresh(
  platformSlug: string,
  refreshToken: string,
  userAgent?: string,
): Promise<ApiResult<OrgAuthResponse>> {
  const body: RefreshTokenRequest = { refreshToken };
  const headers: Record<string, string> = {};
  if (userAgent) headers["User-Agent"] = userAgent;
  return postJson<OrgAuthResponse>(`/${platformSlug}/auth/refresh`, body, undefined, headers);
}

/** POST /{slug}/auth/logout — best effort (204 on success). */
export function serverOrgLogout(platformSlug: string, refreshToken: string): Promise<ApiResult<void>> {
  const body: LogoutRequest = { refreshToken };
  return postJson<void>(`/${platformSlug}/auth/logout`, body);
}

/** GET /{slug}/organisations/{orgId}/users/me */
export function serverOrgMe(
  platformSlug: string,
  organisationId: number,
  accessToken: string,
): Promise<ApiResult<OrganisationUserResponse>> {
  return getJson<OrganisationUserResponse>(
    `/${platformSlug}/organisations/${organisationId}/users/me`,
    accessToken,
  );
}

async function getJson<T>(path: string, token: string): Promise<ApiResult<T>> {
  let res: Response;
  try {
    res = await fetch(apiPath(path), {
      method: "GET",
      headers: { Accept: "application/json", Authorization: `Bearer ${token}` },
    });
  } catch {
    return { ok: false, error: "Could not reach the server. Please try again.", status: 0 };
  }
  if (!res.ok) return { ok: false, error: await errorMessage(res), status: res.status };
  return { ok: true, data: (await res.json()) as T };
}

async function deleteJson<T>(path: string, token: string): Promise<ApiResult<T>> {
  let res: Response;
  try {
    res = await fetch(apiPath(path), {
      method: "DELETE",
      headers: { Accept: "application/json", Authorization: `Bearer ${token}` },
    });
  } catch {
    return { ok: false, error: "Could not reach the server. Please try again.", status: 0 };
  }
  if (res.status === 204) return { ok: true, data: undefined as T };
  if (!res.ok) return { ok: false, error: await errorMessage(res), status: res.status };
  return { ok: true, data: (await res.json()) as T };
}

/** Session info returned by the backend. */
export interface OrgSessionInfo {
  sessionId: string;
  userId: number;
  userIdentifier: string;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: string;
  lastActivityAt: string;
  expiresAt: string;
  active: boolean;
  tokenCount: number;
}

/** GET /{slug}/organisations/{orgId}/sessions?userId={userId} */
export async function serverOrgSessions(
  platformSlug: string,
  organisationId: number,
  userId: number,
  accessToken: string,
): Promise<ApiResult<OrgSessionInfo[]>> {
  return getJson<OrgSessionInfo[]>(
    `/${platformSlug}/organisations/${organisationId}/sessions?userId=${userId}`,
    accessToken,
  );
}

/** DELETE /{slug}/organisations/{orgId}/sessions/{sessionId} */
export async function serverOrgRevokeSession(
  platformSlug: string,
  organisationId: number,
  sessionId: string,
  accessToken: string,
): Promise<ApiResult<void>> {
  return deleteJson<void>(
    `/${platformSlug}/organisations/${organisationId}/sessions/${sessionId}`,
    accessToken,
  );
}
