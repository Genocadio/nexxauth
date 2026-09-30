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

  test("edits a user's profile from the Settings dialog", async ({ authedPage, platform }) => {
    const org = await seedPreparedOrganisation(platform);
    const seeded = await seedOrgUser(platform, org.id, org.roleId);

    await authedPage.goto(`/console/organisations/${org.slug}?tab=users`);
    const row = authedPage.locator("tr", { hasText: seeded.username });
    await expect(row).toHaveCount(1);
    await row.getByRole("button", { name: /Settings/i }).click();

    await authedPage.getByRole("tab", { name: "Profile" }).click();
    await authedPage.getByLabel("First name").fill("Renamed");
    await authedPage.getByRole("button", { name: "Save profile" }).click();
    await authedPage.getByRole("button", { name: "Close" }).last().click();

    const updated = authedPage.locator("tr", { hasText: "Renamed" });
    await expect(updated).toHaveCount(1);
  });

  test("disables the account from the Security tab", async ({ authedPage, platform }) => {
    const org = await seedPreparedOrganisation(platform);
    const seeded = await seedOrgUser(platform, org.id, org.roleId);

    await authedPage.goto(`/console/organisations/${org.slug}?tab=users`);
    const row = authedPage.locator("tr", { hasText: seeded.username });
    await expect(row).toHaveCount(1);
    await row.getByRole("button", { name: /Settings/i }).click();

    await authedPage.getByRole("tab", { name: "Security" }).click();
    await authedPage.getByLabel("Account enabled").click(); // toggle off
    await expect(authedPage.getByText("Disabling blocks sign-in immediately")).toBeVisible();

    await authedPage.getByRole("button", { name: "Close" }).last().click();
    await expect(authedPage.locator("tr", { hasText: seeded.username }).getByText("Disabled")).toBeVisible();
  });

  test("removing the password leaves the user able to sign in with a code", async ({
    authedPage,
    platform,
  }) => {
    const org = await seedPreparedOrganisation(platform);
    const email = `${uniqueSlug("otp")}@acme.test`;
    const seeded = await seedOrgUser(platform, org.id, org.roleId, { email });

    await authedPage.goto(`/console/organisations/${org.slug}?tab=users`);
    const row = authedPage.locator("tr", { hasText: seeded.username });
    await row.getByRole("button", { name: /Settings/i }).click();

    await authedPage.getByRole("tab", { name: "Security" }).click();
    // A user with a password reports both credentials.
    await expect(authedPage.getByText("Usable now:")).toBeVisible();
    await authedPage.getByRole("button", { name: "Remove password" }).click();

    // The dialog must say why this is not a lockout, because that is the whole
    // point of removing a password this way.
    await expect(
      authedPage.getByText("keeps access through one-time codes"),
    ).toBeVisible();
    await authedPage.getByRole("button", { name: "Remove password" }).last().click();

    // Only the code survives.
    await expect(authedPage.getByText("Password or one-time code")).toBeVisible();
    await expect(authedPage.getByText("One-time code").first()).toBeVisible();
  });

  test("marks an email verified by hand from the Main tab", async ({ authedPage, platform }) => {
    const org = await seedPreparedOrganisation(platform);
    const email = `${uniqueSlug("mv")}@acme.test`;
    const seeded = await seedOrgUser(platform, org.id, org.roleId, { email });

    await authedPage.goto(`/console/organisations/${org.slug}?tab=users`);
    const row = authedPage.locator("tr", { hasText: seeded.username });
    await row.getByRole("button", { name: /Settings/i }).click();

    await expect(authedPage.getByText("Email addresses")).toBeVisible();
    // The address only appears once the dialog has loaded the full user, and
    // "Mark verified" exists only for an unverified one.
    const markVerified = authedPage.getByRole("button", { name: "Mark verified" }).first();
    await expect(markVerified).toBeVisible();
    await markVerified.click();
    await expect(authedPage.getByRole("button", { name: "Mark unverified" }).first()).toBeVisible();
  });

  test("deletes a user", async ({ authedPage, platform }) => {
    const org = await seedPreparedOrganisation(platform);
    const seeded = await seedOrgUser(platform, org.id, org.roleId);

    await authedPage.goto(`/console/organisations/${org.slug}?tab=users`);
    const row = authedPage.locator("tr", { hasText: seeded.username });
    await expect(row).toHaveCount(1);
    await row.getByRole("button", { name: /Delete/i }).click();
    await authedPage.getByRole("button", { name: "Delete user" }).click();

    await expect(authedPage.locator("tr", { hasText: seeded.username })).toHaveCount(0);
  });
});
