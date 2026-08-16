import { defineConfig } from "@playwright/test";

/**
 * UI test suite for the Nexxauth console. The backend and the Next.js dev
 * server are started automatically (or reused if already running), and a
 * global setup registers a throwaway platform used to seed authenticated
 * sessions.
 *
 * Run with: bun run test:e2e
 */
export default defineConfig({
  testDir: "./e2e",
  globalSetup: "./e2e/global-setup.ts",
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 3,
  retries: 1,
  reporter: [["list"]],
  use: {
    baseURL: "http://localhost:3000",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  webServer: [
    {
      // Backend (Spring Boot). Any response counts as ready — the probe is
      // unauthenticated and answers 401.
      command: "cd .. && ./gradlew bootRun -q",
      url: "http://localhost:8080/health",
      reuseExistingServer: true,
      timeout: 180_000,
    },
    {
      // Next.js dev server (proxies /api/v1/* to the backend).
      command: "bun run dev",
      url: "http://localhost:3000",
      reuseExistingServer: true,
      timeout: 120_000,
    },
  ],
});
