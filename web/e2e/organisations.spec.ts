import { expect, seedOrganisation, test } from "./fixtures";
import { uniqueSlug } from "./api";

test.describe("organisation management", () => {
  test("creates an organisation and opens its detail page", async ({ authedPage }) => {
    const slug = uniqueSlug("org");
    await authedPage.goto("/console/organisations");
    await authedPage.getByRole("button", { name: "New organisation" }).first().click();
    await authedPage.getByLabel("Organisation name").fill(`Org ${slug}`);
    // The slug input is hidden until "+" is used; slugs are suggested as chips.
    await authedPage.getByRole("button", { name: "Create your own" }).click();
    await authedPage.getByLabel("Slug").fill(slug);
    await authedPage.getByRole("button", { name: "Create organisation" }).click();

    await expect(authedPage.getByText(`Org ${slug}`).first()).toBeVisible();
    await authedPage.getByRole("link", { name: `Org ${slug}` }).click();
    await expect(authedPage).toHaveURL(new RegExp(slug));
    await expect(authedPage.getByRole("heading", { name: `Org ${slug}` })).toBeVisible();
    await expect(authedPage.getByText("Username", { exact: true })).toBeVisible();
  });

  test("requires a name when creating an organisation", async ({ authedPage }) => {
    await authedPage.goto("/console/organisations");
    await authedPage.getByRole("button", { name: "New organisation" }).first().click();
    await authedPage.getByRole("button", { name: "Create organisation" }).click();

    await expect(authedPage.getByText("Organisation name is required")).toBeVisible();
  });

  test("edits an organisation's name", async ({ authedPage, platform }) => {
    const org = await seedOrganisation(platform);
    const newName = `Renamed ${org.slug}`;

    await authedPage.goto(`/console/organisations/${org.slug}`);
    await authedPage.getByRole("button", { name: "Edit" }).click();
    await authedPage.getByLabel("Name", { exact: true }).fill(newName);
    await authedPage.getByRole("button", { name: "Save changes" }).click();

    await expect(authedPage.getByRole("heading", { name: newName })).toBeVisible();
  });

  test("deletes an organisation from the danger zone with typed confirmation", async ({ authedPage, platform }) => {
    const org = await seedOrganisation(platform);

    await authedPage.goto(`/console/organisations/${org.slug}?tab=settings`);
    await authedPage.getByRole("tab", { name: "Miscellaneous" }).click();
    await authedPage.getByRole("button", { name: "Delete organisation" }).first().click();

    // The action stays disabled until the exact organisation name is typed.
    const confirmButton = authedPage.getByRole("button", { name: "Delete organisation" });
    const nameInput = authedPage.getByLabel("Type the organisation name to confirm");
    await expect(confirmButton).toBeDisabled();

    await nameInput.fill(org.name.toUpperCase());
    await expect(confirmButton).toBeDisabled();

    await nameInput.fill(org.name);
    await expect(confirmButton).toBeEnabled();
    await confirmButton.click();

    // Redirects back to the organisation list, where the org is gone.
    await expect(authedPage).toHaveURL(/\/console\/organisations$/);
    await expect(authedPage.getByText(org.name)).toHaveCount(0);
  });
});
