import { expect, seedOrgUser, seedPreparedOrganisation, test } from "./fixtures";
import { uniqueSlug } from "./api";

test.describe("organisation user management", () => {
  test("creates a user with a role and user-field metadata", async ({ authedPage, platform }) => {
    const org = await seedPreparedOrganisation(platform);
    const username = uniqueSlug("bob");

    await authedPage.goto(`/console/organisations/${org.slug}?tab=users`);
    await authedPage.getByRole("button", { name: "Add user" }).first().click();
    await authedPage.getByLabel("First name").fill("Bob");
    await authedPage.getByLabel("Last name").fill("Builder");
    await authedPage.getByLabel("Username").fill(username);
    await authedPage.getByLabel("Email", { exact: true }).fill(`${username}@acme.test`);
    await authedPage.getByLabel("Password (optional)").fill("pw-secret-1");
    await authedPage.locator("#org-user-form").getByLabel("manager").check();
    // User-field inputs are labelled by their attribute key (the label concept
    // was removed from the field model).
    await authedPage.getByLabel("employee-id").fill("EMP-1");
    await authedPage.getByRole("button", { name: "Create user" }).click();

    const row = authedPage.locator("tr", { hasText: username });
    await expect(row).toHaveCount(1);
    await expect(row.getByText("manager")).toBeVisible();
    await expect(row.getByText("Active")).toBeVisible();
  });

  test("requires a first name when creating a user", async ({ authedPage, platform }) => {
    const org = await seedPreparedOrganisation(platform);

    await authedPage.goto(`/console/organisations/${org.slug}?tab=users`);
    await authedPage.getByRole("button", { name: "Add user" }).first().click();
    await authedPage.getByRole("button", { name: "Create user" }).click();

    await expect(authedPage.getByText("First name is required")).toBeVisible();
  });

  test("edits a user's profile and disables the account", async ({ authedPage, platform }) => {
    const org = await seedPreparedOrganisation(platform);
    const seeded = await seedOrgUser(platform, org.slug, org.roleId);

    await authedPage.goto(`/console/organisations/${org.slug}?tab=users`);
    const row = authedPage.locator("tr", { hasText: seeded.username });
    await expect(row).toHaveCount(1);
    await row.getByRole("button", { name: /Edit/i }).click();

    await authedPage.getByLabel("First name").fill("Renamed");
    await authedPage.getByLabel("Account enabled").click(); // toggle off
    await authedPage.getByRole("button", { name: "Save changes" }).click();

    const updated = authedPage.locator("tr", { hasText: "Renamed" });
    await expect(updated).toHaveCount(1);
    await expect(updated.getByText("Disabled")).toBeVisible();
  });

  test("deletes a user", async ({ authedPage, platform }) => {
    const org = await seedPreparedOrganisation(platform);
    const seeded = await seedOrgUser(platform, org.slug, org.roleId);

    await authedPage.goto(`/console/organisations/${org.slug}?tab=users`);
    const row = authedPage.locator("tr", { hasText: seeded.username });
    await expect(row).toHaveCount(1);
    await row.getByRole("button", { name: /Delete/i }).click();
    await authedPage.getByRole("button", { name: "Delete user" }).click();

    await expect(authedPage.locator("tr", { hasText: seeded.username })).toHaveCount(0);
  });
});
