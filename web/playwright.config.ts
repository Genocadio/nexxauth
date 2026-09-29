import { defineConfig } from "@playwright/test";

/**
 * UI test suite for the Nexxauth console. The backend and the Next.js dev
 * server are started automatically (or reused if already running), and a
 * global setup registers a throwaway platform used to seed authenticated
 * sessions.
 *
 * Run with: bun run test:e2e
 */
/* Ports are overridable so the suite can run beside services that already
   hold 8080/3000 on a developer machine; CI keeps the defaults. BACKEND_URL
   must be exported to match, since next.config.ts and e2e/api.ts read it. */
const BACKEND_PORT = process.env.BACKEND_PORT ?? "8080";
const WEB_PORT = process.env.WEB_PORT ?? "3000";

export default defineConfig({
  testDir: "./e2e",
  globalSetup: "./e2e/global-setup.ts",
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  // One worker: the suite shares a single platform (global setup) and the
  // console now forces any organisation that hasn't finished onboarding back
  // into its wizard. Parallel workers creating seeded orgs would redirect each
  // other's pages to onboarding, so the suite runs serially to stay deterministic.
  workers: 1,
  retries: 1,
  reporter: [["list"]],
  use: {
    baseURL: `http://localhost:${WEB_PORT}`,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  webServer: [
    {
      // Backend (Spring Boot). Any response counts as ready — the probe is
      // unauthenticated and answers 401.
      command: `cd .. && ./gradlew bootRun -q -Dorg.gradle.jvmargs=-Xmx1g`,
      url: `http://localhost:${BACKEND_PORT}/actuator/health`,
      env: { PORT: BACKEND_PORT },
      reuseExistingServer: true,
      timeout: 180_000,
    },
    {
      // Next.js dev server (proxies /api/v1/* to the backend).
      command: `bun run dev --port ${WEB_PORT}`,
      url: `http://localhost:${WEB_PORT}`,
      env: { PORT: WEB_PORT },
      reuseExistingServer: true,
      timeout: 120_000,
    },
  ],
});
