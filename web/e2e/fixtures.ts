/* eslint-disable react-hooks/rules-of-hooks -- Playwright fixture parameters are named `use` */
import { expect, request, test as base, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import {
  createOrganisation,
  createOrgUser,
  createRole,
  createUserField,
  uniqueSlug,
  type PlatformSetup,
} from "./api";
import { AUTH_FILE } from "./global-setup";

export { expect };

function readSetup(): PlatformSetup {
  return JSON.parse(readFileSync(AUTH_FILE, "utf8")) as PlatformSetup;
}

export const test = base.extend<{ platform: PlatformSetup; authedPage: Page }>({
  /** The platform registered in global setup (session + credentials). */
  platform: async ({}, use) => use(readSetup()),

  /**
   * A page pre-authenticated as the platform's super user: the shared session
   * is seeded into localStorage before the app loads, then the console
   * overview is opened and confirmed rendered.
   */
  authedPage: async ({ page, platform }, use) => {
    await page.addInitScript((session) => {
      localStorage.setItem("nexxauth.platform.session", JSON.stringify(session));
    }, platform.session);
    await page.goto("/console/overview");
    await expect(page.getByRole("heading", { name: platform.platformName })).toBeVisible({
      timeout: 20_000,
    });
    await use(page);
  },
});

/** An organisation created via the API, ready for UI testing. */
export interface SeededOrganisation {
  id: number;
  slug: string;
  name: string;
}

export async function seedOrganisation(
  platform: PlatformSetup,
  options: { name?: string; slug?: string } = {},
): Promise<SeededOrganisation> {
  const api = await request.newContext();
  try {
    const slug = options.slug ?? uniqueSlug("org");
    const name = options.name ?? `Org ${slug}`;
    const created = await createOrganisation(api, platform.session.accessToken, platform.platformSlug, {
      name,
      slug,
      description: "created by e2e seeding",
    });
    return { id: created.id, slug, name };
  } finally {
    await api.dispose();
  }
}

/**
 * An organisation plus a `manager` role and an `employee-id` user field —
 * the setup the user-management tests exercise against.
 */
export interface PreparedOrganisation extends SeededOrganisation {
  roleId: number;
  fieldKey: string;
}

export async function seedPreparedOrganisation(platform: PlatformSetup): Promise<PreparedOrganisation> {
  const api = await request.newContext();
  try {
    const org = await seedOrganisation(platform);
    const role = await createRole(
      api,
      platform.session.accessToken,
      platform.platformSlug,
      org.slug,
      {
        name: "manager",
        permissions: [
          "ORGANISATION_USER_READ",
          "ORGANISATION_USER_CREATE",
          "ORGANISATION_USER_UPDATE",
          "ORGANISATION_USER_DELETE",
        ],
      },
    );
    const field = await createUserField(
      api,
      platform.session.accessToken,
      platform.platformSlug,
      org.slug,
      { key: "employee-id", label: "Employee ID", fieldType: "STRING", loginEnabled: true },
    );
    return { id: org.id, slug: org.slug, name: org.name, roleId: role.id, fieldKey: field.key };
  } finally {
    await api.dispose();
  }
}

/** A user seeded directly via the API (used by the edit/delete tests). */
export async function seedOrgUser(
  platform: PlatformSetup,
  organisationSlug: string,
  roleId: number,
  options: { username?: string; firstName?: string; lastName?: string } = {},
): Promise<{ id: number; username: string }> {
  const api = await request.newContext();
  try {
    const username = options.username ?? uniqueSlug("user");
    const user = await createOrgUser(
      api,
      platform.session.accessToken,
      platform.platformSlug,
      organisationSlug,
      {
        firstName: options.firstName ?? "Test",
        lastName: options.lastName ?? "User",
        username,
        password: "pw-secret-1",
        roleIds: [roleId],
        metadata: { "employee-id": "EMP-SEED" },
      },
    );
    return { id: user.id, username: user.username ?? username };
  } finally {
    await api.dispose();
  }
}
