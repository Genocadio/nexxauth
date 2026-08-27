import { request } from "@playwright/test";
import { expect, test, seedOrganisation } from "./fixtures";
import { API_BASE, createOrgUser, uniqueSlug } from "./api";

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/** Create a role for an org. */
async function ensureRole(
  platform: { session: { accessToken: string }; platformSlug: string },
  orgId: number,
): Promise<number> {
  const api = await request.newContext();
  try {
    const res = await api.post(`${API_BASE}/${platform.platformSlug}/organisations/${orgId}/roles`, {
      headers: { Authorization: `Bearer ${platform.session.accessToken}` },
      data: { name: "member", permissions: ["ORGANISATION_USER_READ"] },
    });
    if (res.status() !== 201) throw new Error(`create role failed (${res.status()}): ${await res.text()}`);
    return ((await res.json()) as { id: number }).id;
  } finally {
    await api.dispose();
  }
}

/** Create an org user and log them in to create a session. Retries on 429. */
async function createSession(
  platform: { session: { accessToken: string }; platformSlug: string },
  organisationId: number,
  roleId: number,
  opts: { userAgent?: string } = {},
): Promise<{ userId: number; username: string; refreshToken: string }> {
  const api = await request.newContext();
  try {
    const username = uniqueSlug("sess");
    const user = await createOrgUser(api, platform.session.accessToken, platform.platformSlug, organisationId, {
      firstName: "Session",
      lastName: "User",
      username,
      password: "pw-test-123",
      roleIds: [roleId],
    });

    // Login with retry on rate limit
    for (let attempt = 0; attempt < 5; attempt++) {
      const loginRes = await api.post(`${API_BASE}/${platform.platformSlug}/auth/login`, {
        headers: { "User-Agent": opts.userAgent ?? "Mozilla/5.0 E2E-Test" },
        data: { organisationId, identifier: username, identifierType: "USERNAME", password: "pw-test-123" },
      });
      if (loginRes.status() === 200) {
        const loginData = (await loginRes.json()) as { refreshToken: string };
        return { userId: user.id, username, refreshToken: loginData.refreshToken };
      }
      if (loginRes.status() === 429) {
        await sleep(3_000 * (attempt + 1));
        continue;
      }
      throw new Error(`org login failed (${loginRes.status()}): ${await loginRes.text()}`);
    }
    throw new Error("org login kept hitting rate limit");
  } finally {
    await api.dispose();
  }
}

/** List sessions via API. */
async function listSessions(
  platform: { session: { accessToken: string }; platformSlug: string },
  orgId: number,
) {
  const api = await request.newContext();
  try {
    const res = await api.get(`${API_BASE}/${platform.platformSlug}/organisations/${orgId}/sessions`, {
      headers: { Authorization: `Bearer ${platform.session.accessToken}` },
    });
    if (res.status() !== 200) throw new Error(`list sessions failed (${res.status()}): ${await res.text()}`);
    return (await res.json()) as Array<{
      sessionId: string; userId: number; active: boolean;
      ipAddress: string | null; userAgent: string | null;
    }>;
  } finally {
    await api.dispose();
  }
}

test.describe("sessions tab", () => {
  test("renders stats cards and sessions list", async ({ authedPage, platform }) => {
    const org = await seedOrganisation(platform, { slug: uniqueSlug("sess1") });
    const roleId = await ensureRole(platform, org.id);

    // Create one session
    const sess = await createSession(platform, org.id, roleId, {
      userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Chrome/124.0.0.0 Safari/537.36",
    });

    await authedPage.goto(`/console/organisations/${org.slug}?tab=sessions`);

    // Stats cards render
    await expect(authedPage.getByText("Active sessions")).toBeVisible({ timeout: 10_000 });
    await expect(authedPage.getByText("Total sessions")).toBeVisible();
    await expect(authedPage.getByText("Users with sessions")).toBeVisible();

    // The user's username should appear
    await expect(authedPage.getByText(sess.username)).toBeVisible({ timeout: 10_000 });

    // Active badge visible (use exact match to avoid matching "Active sessions" etc.)
    await expect(authedPage.locator("[data-slot='badge']").filter({ hasText: "Active" })).toBeVisible();
  });

  test("displays parsed device info from user-agent", async ({ authedPage, platform }) => {
    const org = await seedOrganisation(platform, { slug: uniqueSlug("sess2") });
    const roleId = await ensureRole(platform, org.id);

    await createSession(platform, org.id, roleId, {
      userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    });

    await authedPage.goto(`/console/organisations/${org.slug}?tab=sessions`);
    await expect(authedPage.getByText("Active sessions")).toBeVisible({ timeout: 10_000 });

    // Parsed UA shows Chrome + macOS
    await expect(authedPage.getByText("Chrome")).toBeVisible({ timeout: 10_000 });
    await expect(authedPage.getByText("macOS")).toBeVisible();
  });

  test("revoke a session and verify via API", async ({ authedPage, platform }) => {
    const org = await seedOrganisation(platform, { slug: uniqueSlug("sess3") });
    const roleId = await ensureRole(platform, org.id);

    const sess = await createSession(platform, org.id, roleId);

    // Verify active via API
    const sessionsBefore = await listSessions(platform, org.id);
    const target = sessionsBefore.find((s) => s.userId === sess.userId && s.active);
    expect(target).toBeDefined();

    // Revoke via API
    const api = await request.newContext();
    try {
      const res = await api.delete(
        `${API_BASE}/${platform.platformSlug}/organisations/${org.id}/sessions/${target!.sessionId}`,
        { headers: { Authorization: `Bearer ${platform.session.accessToken}` } },
      );
      expect(res.status()).toBe(204);
    } finally {
      await api.dispose();
    }

    // Verify via API
    const sessionsAfter = await listSessions(platform, org.id);
    const stillActive = sessionsAfter.filter((s) => s.sessionId === target!.sessionId && s.active);
    expect(stillActive).toHaveLength(0);

    // UI renders correctly
    await authedPage.goto(`/console/organisations/${org.slug}?tab=sessions`);
    await expect(authedPage.getByText("Active sessions")).toBeVisible({ timeout: 10_000 });
  });

  test("empty state shows when no sessions exist", async ({ authedPage, platform }) => {
    const org = await seedOrganisation(platform, { slug: uniqueSlug("sess4") });

    await authedPage.goto(`/console/organisations/${org.slug}?tab=sessions`);
    await expect(authedPage.getByText("Active sessions")).toBeVisible({ timeout: 10_000 });
    await expect(authedPage.getByText("No sessions found.")).toBeVisible({ timeout: 10_000 });
  });

  test("sessions tab accessible from sidebar navigation", async ({ authedPage, platform }) => {
    const org = await seedOrganisation(platform, { slug: uniqueSlug("sess5") });

    // Go to org overview
    await authedPage.goto(`/console/organisations/${org.slug}`);
    await expect(authedPage.getByRole("heading", { name: org.name })).toBeVisible({ timeout: 10_000 });

    // Click Sessions in sidebar (use exact to avoid matching "Active sessions" quick link)
    await authedPage.getByRole("link", { name: "Sessions", exact: true }).click();
    await expect(authedPage).toHaveURL(/tab=sessions/);
    await expect(authedPage.getByText("Active sessions")).toBeVisible({ timeout: 10_000 });
  });

  test("user filter dropdown lists org users", async ({ authedPage, platform }) => {
    const org = await seedOrganisation(platform, { slug: uniqueSlug("sess6") });
    const roleId = await ensureRole(platform, org.id);

    await createSession(platform, org.id, roleId);

    await authedPage.goto(`/console/organisations/${org.slug}?tab=sessions`);
    await expect(authedPage.getByText("Active sessions")).toBeVisible({ timeout: 10_000 });

    // User filter dropdown should be visible
    const userFilter = authedPage.locator("button").filter({ hasText: "All users" });
    await expect(userFilter).toBeVisible({ timeout: 10_000 });

    // Open it — should list at least one user
    await userFilter.click();
    const options = authedPage.getByRole("option");
    const count = await options.count();
    expect(count).toBeGreaterThanOrEqual(1);
  });
});
