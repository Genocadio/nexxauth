import { type APIRequestContext } from "@playwright/test";

/** Backend base URL used by the e2e setup (tests talk to it directly). */
export const API_BASE: string = process.env.BACKEND_URL ?? "http://localhost:8080";

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/** Unique lowercase slug (backend slug pattern: lowercase letters, digits, hyphens). */
export function uniqueSlug(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}${Math.random().toString(36).slice(2, 5)}`;
}

/** Unique email for registrations. */
export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}${Math.random().toString(36).slice(2, 5)}@nexx.test`;
}

/** Shape of the persisted platform session (matches src/store/authSlice). */
export interface PlatformSession {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: {
    id: number;
    firstName: string;
    lastName: string;
    email: string;
    phone: string | null;
    role: "SUPER_USER" | "READ_ONLY";
    enabled: boolean;
    platform: { id: number; name: string; slug: string };
    createdAt: string;
  };
  loginAt: number;
}

/** Everything a test needs about the platform created in global setup. */
export interface PlatformSetup {
  session: PlatformSession;
  email: string;
  password: string;
  platformSlug: string;
  platformName: string;
}

interface JsonResponse {
  status: number;
  json: Record<string, unknown> | null;
  text: string;
}

async function postJson(
  api: APIRequestContext,
  path: string,
  body: unknown,
  token?: string,
): Promise<JsonResponse> {
  const res = await api.post(`${API_BASE}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    data: body,
  });
  const text = await res.text();
  let json: Record<string, unknown> | null = null;
  try {
    json = text ? (JSON.parse(text) as Record<string, unknown>) : null;
  } catch {
    // non-JSON body — tests fail on the status + message below
  }
  return { status: res.status(), json, text };
}

function fail(what: string, res: JsonResponse): never {
  const message = res.json?.["message"];
  throw new Error(`${what} failed (${res.status}): ${typeof message === "string" ? message : res.text}`);
}

/** Register a fresh platform + super user. Retries on the register rate limit. */
export async function registerPlatform(api: APIRequestContext): Promise<PlatformSetup> {
  const email = uniqueEmail("ada");
  const password = "sup3r-secret";
  const platformSlug = uniqueSlug("e2e");
  const platformName = `E2E ${platformSlug}`;

  for (let attempt = 0; attempt < 5; attempt++) {
    const res = await postJson(api, "/auth/register", {
      firstName: "Ada",
      lastName: "Lovelace",
      email,
      password,
      platformName,
      platformSlug,
    });
    if (res.status === 201) {
      return {
        session: { ...(res.json as unknown as Omit<PlatformSession, "loginAt">), loginAt: Date.now() },
        email,
        password,
        platformSlug,
        platformName,
      };
    }
    if (res.status === 429) {
      // Register bucket refills 1/min — back off generously between attempts.
      await sleep(15_000 * (attempt + 1));
      continue;
    }
    fail("register", res);
  }
  throw new Error("register kept hitting the rate limit — wait a minute and re-run");
}

interface OrgBody {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  useEmailAsUsername: boolean;
  createdAt: string;
}

export async function createOrganisation(
  api: APIRequestContext,
  token: string,
  platformSlug: string,
  body: { name: string; slug: string; description?: string },
): Promise<OrgBody> {
  const res = await postJson(api, `/${platformSlug}/organisations`, body, token);
  if (res.status !== 201) fail("create organisation", res);
  return res.json as unknown as OrgBody;
}

/**
 * Partial update of an organisation. Used by the e2e seeding to mark seeded
 * organisations as fully onboarded (onboardingStep 8) — the console forces
 * incomplete organisations through their onboarding wizard, which would block
 * tests that exercise an already-configured organisation.
 */
export async function updateOrganisation(
  api: APIRequestContext,
  token: string,
  platformSlug: string,
  organisationId: number,
  body: { onboardingStep?: number },
): Promise<OrgBody> {
  const res = await api.patch(`${API_BASE}/${platformSlug}/organisations/${organisationId}`, {
    headers: { Authorization: `Bearer ${token}` },
    data: body,
  });
  const text = await res.text();
  let json: Record<string, unknown> | null = null;
  try {
    json = text ? (JSON.parse(text) as Record<string, unknown>) : null;
  } catch {
    // non-JSON body — the status check below reports it
  }
  if (res.status() !== 200) fail("update organisation", { status: res.status(), json, text });
  return json as unknown as OrgBody;
}

/**
 * Find an organisation by slug from the platform's org list. Used when an
 * organisation is created through the UI and only its slug is known.
 */
export async function findOrganisationBySlug(
  api: APIRequestContext,
  token: string,
  platformSlug: string,
  slug: string,
): Promise<OrgBody | undefined> {
  const res = await api.get(`${API_BASE}/${platformSlug}/organisations`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (res.status() !== 200) {
    throw new Error(`list organisations failed (${res.status()}): ${await res.text()}`);
  }
  const orgs = (await res.json()) as OrgBody[];
  return orgs.find((org) => org.slug === slug);
}

interface RoleBody {
  id: number;
  name: string;
  permissions: string[];
}

export async function createRole(
  api: APIRequestContext,
  token: string,
  platformSlug: string,
  organisationId: number,
  body: { name: string; permissions: string[] },
): Promise<RoleBody> {
  const res = await postJson(
    api,
    `/${platformSlug}/organisations/${organisationId}/roles`,
    body,
    token,
  );
  if (res.status !== 201) fail("create role", res);
  return res.json as unknown as RoleBody;
}

export async function createUserField(
  api: APIRequestContext,
  token: string,
  platformSlug: string,
  organisationId: number,
  body: { key: string; fieldType: string; loginEnabled?: boolean },
): Promise<{ id: number; key: string }> {
  const res = await postJson(
    api,
    `/${platformSlug}/organisations/${organisationId}/user-fields`,
    body,
    token,
  );
  if (res.status !== 201) fail("create user field", res);
  return res.json as unknown as { id: number; key: string };
}

interface OrgUserBody {
  id: number;
  firstName: string;
  lastName: string;
  username: string | null;
  email: string | null;
  enabled: boolean;
}

export async function createOrgUser(
  api: APIRequestContext,
  token: string,
  platformSlug: string,
  organisationId: number,
  body: {
    firstName: string;
    lastName: string;
    username?: string;
    email?: string;
    roleIds?: number[];
    password?: string;
    metadata?: Record<string, string>;
  },
): Promise<OrgUserBody> {
  const res = await postJson(
    api,
    `/${platformSlug}/organisations/${organisationId}/users`,
    body,
    token,
  );
  if (res.status !== 201) fail("create org user", res);
  return res.json as unknown as OrgUserBody;
}
