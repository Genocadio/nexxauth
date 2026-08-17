import { expect, seedOrganisation, test } from "./fixtures";

test.describe("docs (context-aware API documentation)", () => {
  // The docs are public — the pages render from GET /{platformSlug}/organisations/{id}/docs/context.
  const docsUrl = (platform: { session: { user: { platform: { slug: string } } } }, orgId: number, path = "") =>
    `/docs/${platform.session.user.platform.slug}/${orgId}${path}`;

  test("renders the docs overview from /docs/context", async ({ page, platform }) => {
    const org = await seedOrganisation(platform);

    await page.goto(docsUrl(platform, org.id));
    await expect(page.getByRole("heading", { name: `${org.name} API Documentation` })).toBeVisible();
    // Base URL snippet uses the platform slug served by the context response.
    await expect(
      page.getByText(`https://your-api-domain.com/${platform.session.user.platform.slug}`)
    ).toBeVisible();
  });

  test("navigates to a docs sub-page (client-side)", async ({ page, platform }) => {
    const org = await seedOrganisation(platform);

    await page.goto(docsUrl(platform, org.id));
    await page.getByRole("link", { name: "Quick Start" }).click();

    await expect(page).toHaveURL(new RegExp(`/docs/${platform.session.user.platform.slug}/${org.id}/quickstart$`));
    await expect(page.getByRole("heading", { name: "Quick Start" })).toBeVisible();
    await expect(page.getByText("1. Get Your Client Credentials")).toBeVisible();
  });

  test("loads a docs sub-page directly", async ({ page, platform }) => {
    const org = await seedOrganisation(platform);

    // Fresh page load (no overview first) — the layout provides the context.
    await page.goto(docsUrl(platform, org.id, "/quickstart"));
    await expect(page.getByRole("heading", { name: "Quick Start" })).toBeVisible();
    await expect(page.getByText("1. Get Your Client Credentials")).toBeVisible();
    // The login page renders org-specific identifier config from the context.
    await page.goto(docsUrl(platform, org.id, "/auth/login"));
    await expect(page.getByRole("heading", { name: "Login" })).toBeVisible();
    await expect(page.getByText("Your Configuration")).toBeVisible();
  });
});
