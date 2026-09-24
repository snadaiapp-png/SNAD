import { defineConfig } from "@playwright/test";

/**
 * Playwright config for CRM Tags production UI verification.
 *
 * Runs against the live production environment to verify:
 * 1. Authentication works
 * 2. CRM.TAG.READ capability is present
 * 3. CRM navigation renders correctly
 * 4. Tags navigation item is visible
 * 5. Tags page loads successfully
 * 6. READ-only operations work
 */
export default defineConfig({
  testDir: "./e2e",
  testMatch: "crm-tags-production-verification.spec.ts",
  timeout: 120_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: [
    ["list"],
    ["html", { outputFolder: "crm-tags-production-report", open: "never" }],
  ],
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? "https://snad-app.vercel.app",
    channel: "chrome",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
    locale: "en-US",
    timezoneId: "Asia/Riyadh",
    navigationTimeout: 30_000,
    actionTimeout: 10_000,
  },
  projects: [
    {
      name: "crm-tags-production",
      use: { colorScheme: "light" },
    },
  ],
});
