const { chromium } = require("playwright");
const { readFileSync } = require("node:fs");

const session = JSON.parse(readFileSync("e2e/.auth/session.json", "utf8"));
const API = "http://localhost:8080/api/v1";

async function seedOrg() {
  const { request } = require("playwright");
  const ctx = await request.newContext();
  const slug = "probe2-" + Date.now().toString(36);
  const res = await ctx.post(`${API}/platforms/${session.platformSlug}/organisations`, {
    headers: { Authorization: `Bearer ${session.session.accessToken}` },
    data: { name: `Probe2 ${slug}`, slug },
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
    const out = [];
    const content = document.querySelector('[data-slot="dialog-content"]');
    // largest right edge element
    let maxR = null;
    for (const el of document.querySelectorAll("*")) {
      if (!el.isConnected) continue;
      const r = el.getBoundingClientRect();
      if (r.width > 0 && (!maxR || r.right > maxR.r)) {
        maxR = { el, r };
      }
    }
    if (maxR) {
      const el = maxR.el;
      out.push({
        msg: "max-right",
        tag: el.tagName,
        cls: String(el.className || "").slice(0, 100),
        txt: (el.textContent || "").trim().slice(0, 40),
        right: Math.round(maxR.r),
        width: Math.round(maxR.r.width),
        sw: el.scrollWidth,
        cw: el.clientWidth,
      });
    }
    // Find elements whose scrollWidth forces the layout: look for a chain
    const chain = [];
    let node = content;
    while (node && node !== document.body) {
      const r = node.getBoundingClientRect();
      chain.push(`${node.tagName}.${(node.className||'').toString().split(' ')[0]||''} w=${Math.round(r.width)} sw=${node.scrollWidth}`);
      node = node.firstElementChild;
    }
    out.push({ msg: "chain", data: chain });
    return out;
  });

  for (const o of probe) console.log(JSON.stringify(o, null, 1));
  await browser.close();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
