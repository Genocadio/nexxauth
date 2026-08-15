"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import {
  ORG_ACCESS_COOKIE,
  ORG_REFRESH_COOKIE,
  orgCookieOptions,
  serverOrgLogin,
  serverOrgLogout,
} from "@/lib/org-auth";
import { orgLoginSchema } from "@/lib/validation";

export interface OrgLoginInput {
  platformSlug: string;
  organisationId: number;
  identifier: string;
  password: string;
}

export type OrgLoginResult = { ok: true } | { ok: false; error: string };

/**
 * Sign an organisation user in: authenticates against the backend, stores the
 * session in httpOnly cookies (never visible to JS), then redirects to the
 * server-rendered profile. The Next proxy gate and the session route handler
 * both trust these cookies on subsequent requests.
 */
export async function loginOrg(input: OrgLoginInput): Promise<OrgLoginResult> {
  const parsed = orgLoginSchema.safeParse({
    identifier: input.identifier,
    password: input.password,
  });
  if (!parsed.success) {
    return { ok: false, error: "Please fix the highlighted fields." };
  }
  const { platformSlug, organisationId, identifier, password } = input;

  const result = await serverOrgLogin(platformSlug, { organisationId, identifier, password });
  if (!result.ok) return { ok: false, error: result.error };

  const cookieStore = await cookies();
  cookieStore.set(ORG_ACCESS_COOKIE, result.data.accessToken, orgCookieOptions);
  cookieStore.set(ORG_REFRESH_COOKIE, result.data.refreshToken, orgCookieOptions);

  redirect(`/org/${platformSlug}/${organisationId}/profile`);
}

/** Revoke the session (best effort) and drop the cookies, back to the login. */
export async function logoutOrg(platformSlug: string, organisationId: number): Promise<void> {
  const cookieStore = await cookies();
  const refreshToken = cookieStore.get(ORG_REFRESH_COOKIE)?.value;
  if (refreshToken) {
    await serverOrgLogout(platformSlug, refreshToken).catch(() => undefined);
  }
  // Delete with the exact same attributes the cookies were set with (notably
  // Path=/org) — a plain delete would send Path=/ and leave the session alive.
  cookieStore.set(ORG_ACCESS_COOKIE, "", { ...orgCookieOptions, maxAge: 0 });
  cookieStore.set(ORG_REFRESH_COOKIE, "", { ...orgCookieOptions, maxAge: 0 });
  redirect(`/org/${platformSlug}/${organisationId}`);
}
