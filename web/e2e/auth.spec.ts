import { expect, test } from "./fixtures";
import { uniqueEmail, uniqueSlug } from "./api";

test.describe("authentication", () => {
  test("registers a platform and lands on the console", async ({ page }) => {
    const slug = uniqueSlug("reg");
    await page.goto("/register");
    await page.getByLabel("First name").fill("Ada");
    await page.getByLabel("Last name").fill("Lovelace");
    await page.getByLabel("Work email").fill(uniqueEmail("reg"));
    await page.getByLabel("Platform name").fill(`Reg ${slug}`);
    await page.getByLabel("Password", { exact: true }).fill("sup3r-secret");
    await page.getByRole("button", { name: "Create platform" }).click();

    await expect(page).toHaveURL(/\/console\/organisations/);
    await expect(page.getByRole("heading", { name: "Organisations", exact: true })).toBeVisible();
  });

  test("rejects invalid credentials with a clear message", async ({ page }) => {
    await page.goto("/login");
    await page.getByLabel("Email").fill("nobody@nexx.test");
    await page.getByLabel("Password", { exact: true }).fill("wrong-password");
    await page.getByRole("button", { name: "Sign in" }).click();

    await expect(page.getByText("Invalid email or password")).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test("signs an existing platform user in and out", async ({ page, platform }) => {
    await page.goto("/login");
    await page.getByLabel("Email").fill(platform.email);
    await page.getByLabel("Password", { exact: true }).fill(platform.password);
    await page.getByRole("button", { name: "Sign in" }).click();

    await expect(page).toHaveURL(/\/console\/organisations/);
    await expect(page.getByRole("heading", { name: "Organisations", exact: true })).toBeVisible();

    // Sign out via the user menu — the console sends users back to /login.
    await page.getByRole("button", { name: platform.session.user.firstName }).click();
    await page.getByRole("menuitem", { name: "Sign out" }).click();

    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole("heading", { name: /Sign in to your platform/i })).toBeVisible();
  });
});
