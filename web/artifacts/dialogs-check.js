const { chromium } = require("playwright");
const { readFileSync } = require("node:fs");

const session = JSON.parse(readFileSync("e2e/.auth/session.json", "utf8"));
const API = "http://localhost:8080/api/v1";

async function seedOrg() {
  const { request } = require("playwright");
  const ctx = await request.newContext();
  const slug = "dial-" + Date.now().toString(36);
  const res = await ctx.post(`${API}/platforms/${session.platformSlug}/organisations`, {
    headers: { Authorization: `Bearer ${session.session.accessToken}` },
    data: { name: `Dial ${slug}`, slug },
  });
  const org = await res.json();
  await ctx.dispose();
  return org;
}

async function overflow(page, label) {
  const issues = await page.evaluate(() => {
    const bad = [];
    for (const el of document.querySelectorAll('[data-slot="dialog-content"] *')) {
      const cs = getComputedStyle(el);
      if (cs.visibility === "hidden" || cs.display === "none") continue;
      const r = el.getBoundingClientRect();
      if (r.width === 0 || r.height === 0) continue;
      if (r.right > window.innerWidth + 2 || r.left < -2) {
        bad.push({
          tag: el.tagName,
          txt: (el.textContent || "").trim().slice(0, 40),
          right: Math.round(r.right),
          win: window.innerWidth,
        });
      }
    }
    return bad.slice(0, 6);
  });
  console.log(`[${label}] ${issues.length ? "OVERFLOW " + JSON.stringify(issues) : "ok"}`);
}

(async () => {
  const org = await seedOrg();
  const base = `http://localhost:3000`;
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  await page.addInitScript((s) => {
    localStorage.setItem("nexxauth.platform.session", JSON.stringify(s));
  }, session.session);

  // organisation create dialog
  await page.goto(`${base}/console/organisations`);
  await page.getByRole("heading", { name: "Organisations" }).waitFor();
  await page.getByRole("button", { name: /New organisation|Create organisation/i }).first().click();
  await page.waitForTimeout(300);
  await overflow(page, "org-create-dialog");

  // org user dialog
  await page.goto(`${base}/console/organisations/${org.slug}?tab=users`);
  await page.getByRole("heading", { name: org.name }).waitFor();
  await page.getByRole("button", { name: "Add user" }).first().click();
  await page.waitForTimeout(500);
  await overflow(page, "org-user-dialog");

  // org role dialog
  await page.goto(`${base}/console/organisations/${org.slug}?tab=roles`);
  await page.getByRole("heading", { name: org.name }).waitFor();
  await page.getByRole("button", { name: /New role/ }).first().click();
  await page.waitForTimeout(400);
  await overflow(page, "org-role-dialog");

  // org field dialog
  await page.goto(`${base}/console/organisations/${org.slug}?tab=fields`);
  await page.getByRole("heading", { name: org.name }).waitFor();
  await page.getByRole("button", { name: /Add field|New field/ }).first().click();
  await page.waitForTimeout(400);
  await overflow(page, "org-field-dialog");

  // platform user dialog
  await page.goto(`${base}/console/users`);
  await page.getByRole("heading", { name: "Users" }).waitFor();
  await page.getByRole("button", { name: /Add user/ }).first().click();
  await page.waitForTimeout(400);
  await overflow(page, "platform-user-dialog");

  await browser.close();
  console.log("done");
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
