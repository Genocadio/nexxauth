import { request } from "@playwright/test";
import { expect, seedOrganisation, test } from "./fixtures";
import { createUserField } from "./api";

test.describe("organisation user fields", () => {
  test("creates and edits a login-enabled field", async ({ authedPage, platform }) => {
    const org = await seedOrganisation(platform);

    await authedPage.goto(`/console/organisations/${org.slug}?tab=fields`);
    await authedPage.getByRole("button", { name: "New field" }).first().click();
    await authedPage.getByLabel("Attribute name", { exact: true }).fill("employee-code");
    await authedPage.getByRole("combobox").click();
    await authedPage.getByRole("option", { name: /Number/ }).click();
    await authedPage.getByLabel("Login identifier").click();
    await authedPage.getByRole("button", { name: "Create field" }).click();

    const row = authedPage.locator("tr", { hasText: "employee-code" });
    await expect(row).toHaveCount(1);
    await expect(row.getByText("Can be used to log in")).toBeVisible();

    // the attribute name (key) is immutable on edit
    await row.getByRole("button", { name: /Edit/i }).click();
    await expect(authedPage.getByLabel("Attribute name", { exact: true })).toBeDisabled();
    await authedPage.getByRole("button", { name: "Save changes" }).click();
    await expect(authedPage.locator("tr", { hasText: "employee-code" })).toHaveCount(1);
  });

  test("deletes a field", async ({ authedPage, platform }) => {
    const org = await seedOrganisation(platform);
    const api = await request.newContext();
    try {
      await createUserField(
        api,
        platform.session.accessToken,
        platform.platformSlug,
        org.id,
        { key: "badge", fieldType: "STRING" },
      );
    } finally {
      await api.dispose();
    }

    await authedPage.goto(`/console/organisations/${org.slug}?tab=fields`);
    const row = authedPage.locator("tr", { hasText: "badge" });
    await expect(row).toHaveCount(1);
    await row.getByRole("button", { name: /Delete/i }).click();
    await authedPage.getByRole("button", { name: "Delete field" }).click();

    await expect(authedPage.locator("tr", { hasText: "badge" })).toHaveCount(0);
  });
});
