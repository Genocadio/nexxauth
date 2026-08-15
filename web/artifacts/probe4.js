const { chromium } = require("playwright");
const { readFileSync } = require("node:fs");

const session = JSON.parse(readFileSync("e2e/.auth/session.json", "utf8"));
const API = "http://localhost:8080/api/v1";

async function seedOrg() {
  const { request } = require("playwright");
  const ctx = await request.newContext();
  const slug = "probe4-" + Date.now().toString(36);
  const res = await ctx.post(`${API}/platforms/${session.platformSlug}/organisations`, {
    headers: { Authorization: `Bearer ${session.session.accessToken}` },
    data: { name: `Probe4 ${slug}`, slug },
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

  // 1. find the intrinsic offender: descendants with minW auto and big natural width
  const offender = await page.evaluate(() => {
    const content = document.querySelector('[data-slot="dialog-content"]');
    const hits = [];
    for (const el of content.querySelectorAll("*")) {
      const cs = getComputedStyle(el);
      const r = el.getBoundingClientRect();
      if (r.width < 350) continue;
      // an element that STRETCHES follows its parent; an element whose min-content
      // is big will keep its width when the track is forced narrow. Find candidates.
      if (cs.minWidth === "auto") {
        hits.push({ tag: el.tagName, cls: String(el.className||"").slice(0,80), txt:(el.textContent||"").trim().slice(0,40), w: Math.round(r.width) });
      }
    }
    return hits.slice(0, 15);
  });
  console.log("=== minW:auto descendants >=350px ===");
  for (const o of offender) console.log(JSON.stringify(o));

  // 2. apply the candidate fix and re-measure
  const after = await page.evaluate(() => {
    const content = document.querySelector('[data-slot="dialog-content"]');
    content.style.gridTemplateColumns = "minmax(0, 1fr)";
    const r = content.getBoundingClientRect();
    return { w: Math.round(r.width), scrollW: content.scrollWidth };
  });
  console.log("=== after gridTemplateColumns=minmax(0,1fr) ===", JSON.stringify(after));

  await browser.close();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
