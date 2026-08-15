const { chromium } = require("playwright");
const { readFileSync } = require("node:fs");

const AUTH_FILE = "e2e/.auth/session.json";
const session = JSON.parse(readFileSync(AUTH_FILE, "utf8"));
const mkdir = require("node:fs").mkdirSync;
mkdir("artifacts", { recursive: true });

const API = "http://localhost:8080/api/v1";

async function seedOrg() {
  const { request } = require("playwright");
  const ctx = await request.newContext();
  const slug = "vis-" + Date.now().toString(36);
  const res = await ctx.post(
    `${API}/platforms/${session.platformSlug}/organisations`,
    {
      headers: { Authorization: `Bearer ${session.session.accessToken}` },
      data: { name: `Visual ${slug}`, slug },
    },
  );
  const org = await res.json();
  await ctx.dispose();
  return org;
}

async function checkOverflow(page, label) {
  const issues = await page.evaluate(() => {
    const bad = [];
    for (const el of document.querySelectorAll("*")) {
      if (!el.isConnected) continue;
      const cs = getComputedStyle(el);
      if (cs.visibility === "hidden" || cs.display === "none") continue;
      if (cs.position === "fixed") continue;
      if (el.scrollWidth > el.clientWidth + 1 || el.scrollHeight > el.clientHeight + 1) {
        if (cs.overflowX === "hidden" && cs.overflowY === "hidden") continue;
        const r = el.getBoundingClientRect();
        if (r.width === 0 || r.height === 0) continue;
        if (r.right > (window.innerWidth + 2) || r.left < -2) {
          bad.push({
            tag: el.tagName,
            cls: (el.className && String(el.className).slice(0, 80)) || "",
            txt: (el.textContent || "").trim().slice(0, 50),
            sw: el.scrollWidth,
            cw: el.clientWidth,
            right: Math.round(r.right),
            win: window.innerWidth,
          });
        }
      }
    }
    return bad.slice(0, 20);
  });
  if (issues.length) {
    console.log(`[${label}] OVERFLOW x${issues.length}:`);
    for (const i of issues) console.log("   ", JSON.stringify(i));
  } else {
    console.log(`[${label}] no horizontal overflow`);
  }
  return issues;
}

(async () => {
  const org = await seedOrg();
  const base = `http://localhost:3000/console/organisations/${org.slug}`;
  const browser = await chromium.launch();

  for (const vp of [
    { name: "desktop", width: 1280, height: 800 },
    { name: "laptop", width: 1024, height: 768 },
    { name: "tablet", width: 768, height: 1024 },
    { name: "mobile", width: 390, height: 844 },
  ]) {
    const page = await browser.newPage({ viewport: { width: vp.width, height: vp.height } });
    await page.addInitScript((s) => {
      localStorage.setItem("nexxauth.platform.session", JSON.stringify(s));
    }, session.session);

    // Clients tab
    await page.goto(`${base}?tab=clients`);
    await page.getByRole("heading", { name: org.name }).waitFor();
    await page.waitForTimeout(400);
    await page.screenshot({ path: `artifacts/clients-${vp.name}.png`, fullPage: false });

    // New client dialog
    await page.getByRole("button", { name: "New client" }).first().click();
    await page.waitForTimeout(300);
    await checkOverflow(page, `client-dialog-${vp.name}`);
    await page.screenshot({ path: `artifacts/client-dialog-${vp.name}.png` });

    // Type select dropdown
    const trigger = page.getByRole("combobox");
    if (await trigger.count()) {
      await trigger.first().click();
      await page.waitForTimeout(300);
      await checkOverflow(page, `type-select-${vp.name}`);
      await page.screenshot({ path: `artifacts/type-select-${vp.name}.png` });
      await page.keyboard.press("Escape");
    }

    await page.close();
  }

  // Token dialog: create a server client to get a token
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  await page.addInitScript((s) => {
    localStorage.setItem("nexxauth.platform.session", JSON.stringify(s));
  }, session.session);
  await page.goto(`${base}?tab=clients`);
  await page.getByRole("heading", { name: org.name }).waitFor();
  await page.getByRole("button", { name: "New client" }).first().click();
  await page.getByLabel("Name").fill("Token service");
  await page.getByRole("combobox").first().click();
  await page.getByRole("option", { name: /Server/ }).click();
  await page.getByRole("button", { name: "Create client" }).click();
  await page.waitForTimeout(600);
  await checkOverflow(page, "token-dialog-mobile");
  await page.screenshot({ path: "artifacts/token-dialog-mobile.png" });
  await page.close();

  await browser.close();
  console.log("done");
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
