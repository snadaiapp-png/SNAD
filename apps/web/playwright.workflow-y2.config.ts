import { defineConfig } from "@playwright/test";

/**
 * Workflow Y2 Release Gate config.
 * Targets a local Spring Boot backend + PostgreSQL Direct + Next.js web.
 * Only this spec file runs under this config (not the CRM suite).
 */
export default defineConfig({
  testDir: "./e2e",
  testMatch: ["**/workflow-y2-release.spec.ts"],
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [["html", { outputFolder: "wf-playwright-report" }], ["list"]],
  timeout: 60_000,
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3001",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
});
