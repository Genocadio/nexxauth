import { expect, seedOrgUser, seedPreparedOrganisation, test } from "./fixtures";
import { uniqueSlug } from "./api";

test.describe("org portal (server-rendered, middleware auth)", () => {
  const PASSWORD = "pw-secret-1";

  test("signs in and views the server-rendered profile", async ({ page, platform }) => {
    const org = await seedPreparedOrganisation(platform);
    const user = await seedOrgUser(platform, org.slug, org.roleId, {
      username: uniqueSlug("portal"),
      firstName: "Portal",
      lastName: "User",
    });

    await page.goto(`/org/${platform.platformSlug}/${org.id}`);
    await page.getByLabel("Identifier").fill(user.username);
    await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
    await page.getByRole("button", { name: "Sign in" }).click();

    await expect(page).toHaveURL(new RegExp(`/org/${platform.platformSlug}/${org.id}/profile$`));
    await expect(page.getByText("Portal User")).toBeVisible();
    // User-field metadata is rendered server-side on the profile.
    await expect(page.getByText("EMP-SEED")).toBeVisible();
  });

  test("signs in with a login-enabled user field value (case-insensitive)", async ({ page, platform }) => {
    const org = await seedPreparedOrganisation(platform);
    await seedOrgUser(platform, org.slug, org.roleId, {
      username: uniqueSlug("portal"),
      firstName: "Portal",
      lastName: "User",
    });

    // The seeded employee-id value is "EMP-SEED"; the STRING field matches
    // case-insensitively, so the lowercase identifier still signs in.
    await page.goto(`/org/${platform.platformSlug}/${org.id}`);
    await page.getByLabel("Identifier").fill("emp-seed");
    await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
    await page.getByRole("button", { name: "Sign in" }).click();

    await expect(page).toHaveURL(new RegExp(`/org/${platform.platformSlug}/${org.id}/profile$`));
    await expect(page.getByText("Portal User")).toBeVisible();
  });

  test("shows an inline error for invalid credentials", async ({ page, platform }) => {
    const org = await seedPreparedOrganisation(platform);
    const user = await seedOrgUser(platform, org.slug, org.roleId, {
      username: uniqueSlug("portal"),
    });

    await page.goto(`/org/${platform.platformSlug}/${org.id}`);
    await page.getByLabel("Identifier").fill(user.username);
    await page.getByLabel("Password", { exact: true }).fill("wrong-password");
    await page.getByRole("button", { name: "Sign in" }).click();

    await expect(page.getByText("Invalid email or password")).toBeVisible();
    await expect(page).toHaveURL(new RegExp(`/org/${platform.platformSlug}/${org.id}$`));
  });

  test("middleware redirects unauthenticated profile visits to the login", async ({ page, platform }) => {
    const org = await seedPreparedOrganisation(platform);

    await page.goto(`/org/${platform.platformSlug}/${org.id}/profile`);

    await expect(page).toHaveURL(new RegExp(`/org/${platform.platformSlug}/${org.id}$`));
    await expect(page.getByLabel("Identifier")).toBeVisible();
  });

  test("a signed-in visitor on the login URL is sent to the profile, then can sign out", async ({ page, platform }) => {
    const org = await seedPreparedOrganisation(platform);
    const user = await seedOrgUser(platform, org.slug, org.roleId, {
      username: uniqueSlug("portal"),
      firstName: "Portal",
      lastName: "User",
    });

    // Sign in once…
    await page.goto(`/org/${platform.platformSlug}/${org.id}`);
    await page.getByLabel("Identifier").fill(user.username);
    await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page.getByText("Portal User")).toBeVisible();

    // …then visiting the login URL redirects to the profile.
    await page.goto(`/org/${platform.platformSlug}/${org.id}`);
    await expect(page).toHaveURL(new RegExp(`/org/${platform.platformSlug}/${org.id}/profile$`));
    await expect(page.getByText("Portal User")).toBeVisible();

    // Signing out returns to the login form.
    await page.getByRole("button", { name: "Sign out" }).first().click();
    await page.getByRole("button", { name: "Sign out" }).last().click();
    await expect(page).toHaveURL(new RegExp(`/org/${platform.platformSlug}/${org.id}$`));
    await expect(page.getByLabel("Identifier")).toBeVisible();
  });
});
