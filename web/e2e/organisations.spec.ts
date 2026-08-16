import { expect, seedOrganisation, test } from "./fixtures";
import { createOrganisation, uniqueSlug, updateOrganisation } from "./api";

test.describe("organisation management", () => {
  test("creates an organisation and launches its onboarding wizard", async ({
    authedPage,
    platform,
    request,
  }) => {
    const slug = uniqueSlug("org");
    await authedPage.goto("/console/organisations");
    await authedPage.getByRole("button", { name: "New organisation" }).first().click();
    await authedPage.getByLabel("Organisation name").fill(`Org ${slug}`);
    // The slug input is hidden until "+" is used; slugs are suggested as chips.
    await authedPage.getByRole("button", { name: "Create your own" }).click();
    await authedPage.getByLabel("Slug").fill(slug);
    await authedPage.getByRole("button", { name: "Create organisation" }).click();

    // A brand-new organisation hasn't been set up yet, so its onboarding
    // wizard launches automatically — no manual switch or banner click.
    await expect(authedPage).toHaveURL(new RegExp(`/console/onboarding/.*org=${slug}`));
    // The wizard resumes at step 2 (identifiers) since the org already exists.
    await expect(authedPage.getByText("How should your users sign in?")).toBeVisible();

    // Mark the org as fully set up via the API so the shared platform stays
    // onboarded for the rest of the suite, then verify the list + detail page.
    await updateOrganisation(request, platform.session.accessToken, platform.platformSlug, slug, {
      onboardingStep: 8,
    });

    await authedPage.goto("/console/organisations");
    await expect(authedPage.getByText(`Org ${slug}`).first()).toBeVisible();
    await authedPage.getByRole("link", { name: `Org ${slug}` }).click();
    await expect(authedPage).toHaveURL(new RegExp(slug));
    await expect(authedPage.getByRole("heading", { name: `Org ${slug}` })).toBeVisible();
    await expect(authedPage.getByText("Username", { exact: true })).toBeVisible();
  });

  test("onboarding is dedicated to the working organisation", async ({ authedPage, platform, request }) => {
    const slug = uniqueSlug("pend");
    const name = `Pend ${slug}`;
    await createOrganisation(request, platform.session.accessToken, platform.platformSlug, {
      name,
      slug,
      description: "left unfinished on purpose",
    });

    // The platform view is never gated, even with an unfinished org present.
    await authedPage.goto("/console/organisations");
    await expect(authedPage.getByRole("heading", { name: "Organisations", exact: true })).toBeVisible();

    // Entering the unfinished org forces its own onboarding wizard, which
    // names the dedicated organisation (heading + header switcher).
    await authedPage.goto(`/console/organisations/${slug}`);
    await expect(authedPage).toHaveURL(
      new RegExp(`/console/onboarding/${platform.platformSlug}\\?org=${slug}`),
    );
    await expect(authedPage.getByRole("heading", { name: `Set up ${name}` })).toBeVisible();
    await expect(authedPage.getByRole("button", { name })).toBeVisible();

    // Once the org is fully set up, the same page opens normally — no wizard,
    // and completing it never bounces into another org's onboarding.
    await updateOrganisation(request, platform.session.accessToken, platform.platformSlug, slug, {
      onboardingStep: 8,
    });
    await authedPage.goto(`/console/organisations/${slug}`);
    await expect(authedPage.getByRole("heading", { name })).toBeVisible();
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
