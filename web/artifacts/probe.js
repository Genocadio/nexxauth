const { chromium } = require("playwright");
const { readFileSync } = require("node:fs");

const session = JSON.parse(readFileSync("e2e/.auth/session.json", "utf8"));
const API = "http://localhost:8080/api/v1";

async function seedOrg() {
  const { request } = require("playwright");
  const ctx = await request.newContext();
  const slug = "probe-" + Date.now().toString(36);
  const res = await ctx.post(`${API}/platforms/${session.platformSlug}/organisations`, {
    headers: { Authorization: `Bearer ${session.session.accessToken}` },
    data: { name: `Probe ${slug}`, slug },
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
    const rect = (el) => {
      const r = el.getBoundingClientRect();
      return { w: Math.round(r.width), l: Math.round(r.left), r: Math.round(r.right), scrollW: el.scrollWidth };
    };
    if (content) {
      out.push({ which: "dialog-content", ...rect(content) });
      for (const el of content.querySelectorAll("*")) {
        const sw = el.scrollWidth;
        if (sw > 400) {
          const cls = String(el.className || "").slice(0, 90);
          out.push({ which: el.tagName, cls, ...rect(el), scrollW: sw });
        }
      }
      // walk ancestors of dialog-content
      let p = content.parentElement;
      while (p) {
        out.push({ which: "ancestor-" + p.tagName, cls: String(p.className || "").slice(0, 60), ...rect(p) });
        p = p.parentElement;
      }
    }
    return out;
  });

  for (const o of probe) console.log(JSON.stringify(o));
  await browser.close();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
