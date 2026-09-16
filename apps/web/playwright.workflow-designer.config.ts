import { defineConfig } from "@playwright/test";

/**
 * Dedicated Workflow Designer Browser Acceptance Gate.
 * Runs only the real /workflow/definitions/{id} journey against:
 * - host-native PostgreSQL Direct
 * - real Spring Boot backend
 * - production-built Next.js frontend
 */
export default defineConfig({
  testDir: "./e2e",
  testMatch: ["**/workflow-designer-browser.spec.ts"],
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [
    ["html", { outputFolder: "workflow-designer-playwright-report" }],
    ["list"],
  ],
  timeout: 90_000,
  expect: {
    timeout: 15_000,
  },
  outputDir: "workflow-designer-results",
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3001",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
});
