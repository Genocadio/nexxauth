import { chromium } from "playwright";

const BASE = "http://localhost:3000";
const SHOTS = "/tmp/nexx-shots";
const errors = [];
const steps = [];

const uniq = Date.now().toString(36);
const EMAIL = `ada-${uniq}@nexx.io`;
const PASSWORD = "sup3r-secret";
const PLATFORM_NAME = `E2E ${uniq}`;
const PLATFORM_SLUG = `e2e-${uniq}`;

function step(name) {
  steps.push(name);
  console.log("STEP:", name);
}

async function main() {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  page.on("console", (msg) => {
    if (msg.type() === "error") errors.push(`console.error: ${msg.text()}`);
  });
  page.on("pageerror", (err) => errors.push(`pageerror: ${err.message}`));
  page.on("requestfailed", (req) => {
    // net::ERR_ABORTED is expected when a successful mutation triggers a
    // redirect — the browser cancels response delivery during navigation.
    if (req.failure()?.errorText !== "net::ERR_ABORTED") {
      errors.push(`requestfailed: ${req.url()} (${req.failure()?.errorText ?? "?"})`);
    }
  });

  const shot = (name) => page.screenshot({ path: `${SHOTS}/${name}.png` }).catch(() => {});

  // ---------------------------------------------------------------- landing
  step("landing page");
  await page.goto(BASE + "/", { waitUntil: "domcontentloaded" });
  await page.getByRole("heading", { name: /Manage platforms/i }).waitFor({ timeout: 15000 });
  await page.getByRole("link", { name: "Create platform" }).click();
  await page.waitForURL("**/register");

  // ---------------------------------------------------------------- register
  step("register platform (creates super user)");
  await page.getByLabel("First name").fill("Ada");
  await page.getByLabel("Last name").fill("Lovelace");
  await page.getByLabel("Work email").fill(EMAIL);
  await page.getByLabel("Platform name").fill(PLATFORM_NAME);
  await page.getByLabel("Platform slug (optional)").fill(PLATFORM_SLUG);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "Create platform" }).click();
  await page.waitForURL("**/console/overview", { timeout: 20000 });
  await page.getByRole("heading", { name: PLATFORM_NAME }).waitFor({ timeout: 15000 });
  await shot("02-overview");

  // ---------------------------------------------------------------- overview
  step("overview: stats + recent orgs");
  await page.getByText("Organisations", { exact: true }).first().waitFor();
  await page.getByText("Platform users", { exact: true }).waitFor();

  // ------------------------------------------------------- platform users
  step("platform users: add a member");
  await page.goto(`${BASE}/console/users`);
  await page.getByRole("button", { name: "Add user" }).first().click();
  await page.getByLabel("First name").fill("Grace");
  await page.getByLabel("Last name").fill("Hopper");
  await page.getByLabel("Email", { exact: true }).fill(`grace-${uniq}@nexx.io`);
  await page.getByLabel("Password", { exact: true }).fill("another-secret");
  await page.locator('button[form="platform-user-form"]').click();
  await page.getByText(`grace-${uniq}@nexx.io`).waitFor({ timeout: 15000 });
  await shot("03-platform-users");

  // ------------------------------------------------------- create organisation
  step("organisations: create");
  await page.goto(`${BASE}/console/organisations`);
  await page.getByRole("button", { name: "New organisation" }).first().click();
  await page.getByLabel("Organisation name").fill("Acme Labs");
  await page.getByLabel("Slug (optional)").fill("acme-labs");
  await page.getByLabel("Description (optional)").fill("E2E organisation");
  await page.getByRole("button", { name: "Create organisation" }).click();
  const orgCard = page.getByText("Acme Labs").first();
  await orgCard.waitFor({ timeout: 15000 });
  await shot("04-organisations");

  // ------------------------------------------------------- org detail: overview
  step("org detail: overview + edit name");
  await page.getByRole("link", { name: "Acme Labs" }).click();
  await page.waitForURL("**/console/organisations/acme-labs**");
  await page.getByRole("button", { name: "Edit" }).click();
  await page.getByLabel("Name", { exact: true }).fill("Acme Labs HQ");
  await page.getByRole("button", { name: "Save changes" }).click();
  await page.getByText("Acme Labs HQ").first().waitFor({ timeout: 15000 });

  // ------------------------------------------------------- org roles
  step("org roles: create manager role");
  await page.goto(`${BASE}/console/organisations/acme-labs?tab=roles`);
  await page.getByRole("button", { name: "New role" }).click();
  await page.getByLabel("Role name").fill("manager");
  await page.getByLabel("Read users").check();
  await page.getByLabel("Create users").check();
  await page.getByRole("button", { name: "Create role" }).click();
  await page.getByText("manager", { exact: true }).first().waitFor({ timeout: 15000 });
  await shot("05-roles");

  // ------------------------------------------------------- org fields
  step("org fields: create employee-id field (login enabled)");
  await page.goto(`${BASE}/console/organisations/acme-labs?tab=fields`);
  await page.getByRole("button", { name: "New field" }).click();
  await page.getByLabel("Key", { exact: true }).fill("employee-id");
  await page.getByLabel("Label", { exact: true }).fill("Employee ID");
  await page.getByLabel("Login identifier").click();
  await page.getByRole("button", { name: "Create field" }).click();
  await page.getByText("employee-id").first().waitFor({ timeout: 15000 });
  await shot("06-fields");

  // ------------------------------------------------------- org users
  step("org users: create Bob with role + metadata");
  await page.goto(`${BASE}/console/organisations/acme-labs?tab=users`);
  await page.getByRole("button", { name: "Add user" }).first().click();
  await page.getByLabel("First name").fill("Bob");
  await page.getByLabel("Last name").fill("Builder");
  await page.getByLabel("Username").fill("bob");
  await page.getByLabel("Email", { exact: true }).fill("bob@acme.io");
  await page.locator("#org-user-form").getByLabel("manager").check();
  await page.getByLabel("Password (optional)").fill("pw-secret-1");
  await page.getByLabel("Employee ID").fill("EMP-1");
  await page.getByRole("button", { name: "Create user" }).click();
  await page.getByText("bob@acme.io").waitFor({ timeout: 15000 });
  await shot("07-org-users");

  // ------------------------------------------------------- org settings
  step("org settings: password policy + session settings");
  await page.goto(`${BASE}/console/organisations/acme-labs?tab=settings`);
  await page.getByLabel("Minimum length").fill("10");
  await page.getByRole("button", { name: "Save policy" }).click();
  await page.getByText("Authentication settings saved").waitFor({ timeout: 15000 });
  await page.getByLabel("Max sessions per user").fill("3");
  await page.getByRole("button", { name: "Save settings" }).click();
  await page.getByText("Session settings saved").waitFor({ timeout: 15000 });
  await shot("08-settings");

  // ------------------------------------------------------- org keys
  step("org keys: rotate signing key");
  await page.goto(`${BASE}/console/organisations/acme-labs?tab=keys`);
  await page.getByRole("button", { name: "Rotate key" }).first().click();
  await page.getByRole("button", { name: "Rotate key" }).last().click();
  await page.getByText("Signing key rotated").waitFor({ timeout: 15000 });
  await shot("09-keys");

  // ------------------------------------------------------- org portal (org user login)
  step("org portal: sign in as the org user");
  const orgId = await page.evaluate(async (slug) => {
    const state = JSON.parse(localStorage.getItem("nexxauth.platform.session") ?? "null");
    const res = await fetch(`/api/v1/platforms/${slug}/organisations`, {
      headers: { Authorization: `Bearer ${state.accessToken}` },
    });
    const orgs = await res.json();
    return orgs.find((o) => o.slug === "acme-labs")?.id;
  }, PLATFORM_SLUG);
  if (!orgId) throw new Error("could not resolve org id for portal URL");

  await page.goto(`${BASE}/org/${PLATFORM_SLUG}/${orgId}`);
  await page.getByLabel("Identifier").fill("bob");
  await page.getByLabel("Password", { exact: true }).fill("pw-secret-1");
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.getByText("Bob Builder").waitFor({ timeout: 15000 });
  await page.getByText("EMP-1").waitFor({ timeout: 15000 }); // metadata shown
  await shot("10-org-portal");

  step("org portal: sign out");
  await page.getByRole("button", { name: "Sign out" }).first().click();
  await page.getByRole("button", { name: "Sign out" }).last().click();
  await page.getByLabel("Identifier").waitFor({ timeout: 15000 }); // back to login

  // ------------------------------------------------------- profile + theme + password
  step("profile: update phone + theme toggle");
  await page.goto(`${BASE}/console/profile`);
  await page.getByLabel("Phone").fill("+1 555 0100");
  await page.getByRole("button", { name: "Save profile" }).click();
  await page.getByText("Profile updated").waitFor({ timeout: 15000 });

  const themeButton = page.getByRole("button", { name: "Toggle theme" });
  await themeButton.click();
  const darkClass = await page.evaluate(() => document.documentElement.classList.contains("dark"));
  console.log("dark mode after toggle:", darkClass);
  await themeButton.click();
  await shot("11-profile");

  step("change password (revokes sessions -> back to login)");
  await page.getByLabel("Current password").fill(PASSWORD);
  await page.getByLabel("New password").fill("brand-new-secret");
  await page.getByRole("button", { name: "Change password" }).click();
  await page.waitForURL("**/login", { timeout: 15000 });
  await page.getByRole("heading", { name: /Sign in to your platform/i }).waitFor({ timeout: 15000 });
  await shot("12-login-after-password-change");

  // ---------------------------------------------------------------- summary
  await browser.close();
  console.log("\n== steps ==");
  steps.forEach((s, i) => console.log(`  ${i + 1}. ${s}`));
  if (errors.length === 0) {
    console.log("\n✅ No console errors, page errors or failed requests.");
  } else {
    console.log(`\n❌ ${errors.length} browser error(s):`);
    errors.forEach((e) => console.log("  -", e));
    process.exit(1);
  }
}

main().catch((err) => {
  console.error("CLICKTHROUGH FAILED:", err.message);
  process.exit(1);
});
