import { request } from "@playwright/test";
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { registerPlatform } from "./api";

/** Where the shared platform setup (session + credentials) is persisted. */
export const AUTH_DIR = join(process.cwd(), "e2e", ".auth");
export const AUTH_FILE = join(AUTH_DIR, "session.json");

/**
 * Registers one platform per test run. Every test seeds its browser session
 * from this file, so the suite performs exactly one register + one login and
 * never trips the backend's rate limits. The platform name/slug are unique
 * per run, so re-running is always safe.
 */
export default async function globalSetup(): Promise<void> {
  const api = await request.newContext();
  try {
    const setup = await registerPlatform(api);
    mkdirSync(AUTH_DIR, { recursive: true });
    writeFileSync(AUTH_FILE, JSON.stringify(setup, null, 2));
    console.log(`[global-setup] platform ready: ${setup.platformSlug} (${setup.email})`);
  } finally {
    await api.dispose();
  }
}
