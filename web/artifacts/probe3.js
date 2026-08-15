const { chromium } = require("playwright");
const { readFileSync } = require("node:fs");

const session = JSON.parse(readFileSync("e2e/.auth/session.json", "utf8"));
const API = "http://localhost:8080/api/v1";

async function seedOrg() {
  const { request } = require("playwright");
  const ctx = await request.newContext();
  const slug = "probe3-" + Date.now().toString(36);
  const res = await ctx.post(`${API}/platforms/${session.platformSlug}/organisations`, {
    headers: { Authorization: `Bearer ${session.session.accessToken}` },
    data: { name: `Probe3 ${slug}`, slug },
  });
  const org = await res.json();
  await ctx.dispose();
  return org;
}

(async () => {
  const org = await seedOrg();
  const base = `http://localhost:3000/console/organisations/${org.slug}`;
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  await page.addInitScript((s) => {
    localStorage.setItem("nexxauth.platform.session", JSON.stringify(s));
  }, session.session);

  await page.goto(`${base}?tab=clients`);
  await page.getByRole("heading", { name: org.name }).waitFor();
  await page.getByRole("button", { name: "New client" }).first().click();
  await page.waitForTimeout(400);

  const probe = await page.evaluate(() => {
    const content = document.querySelector('[data-slot="dialog-content"]');
    const cs = getComputedStyle(content);
    const info = {
      w: content.getBoundingClientRect().width,
      scrollW: content.scrollWidth,
      gridCols: cs.gridTemplateColumns,
      minWidth: cs.minWidth,
      maxWidth: cs.maxWidth,
      overflow: cs.overflow,
    };
    const rows = [];
    for (const el of content.querySelectorAll("*")) {
      const r = el.getBoundingClientRect();
      if (r.width === 0 || r.height === 0) continue;
      rows.push({
        tag: el.tagName,
        cls: String(el.className || "").slice(0, 70),
        sw: el.scrollWidth,
        w: Math.round(r.width),
        left: Math.round(r.left),
        right: Math.round(r.right),
        minW: getComputedStyle(el).minWidth,
        ws: getComputedStyle(el).whiteSpace,
      });
    }
    // sort by right edge descending, show top 8
    rows.sort((a, b) => b.right - a.right);
    return { info, rows: rows.slice(0, 10) };
  });

  console.log("INFO:", JSON.stringify(probe.info));
  for (const r of probe.rows) console.log(JSON.stringify(r));
  await browser.close();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
