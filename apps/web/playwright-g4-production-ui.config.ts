import { defineConfig, devices } from "@playwright/test";

/**
 * HRM G4 Production UI Acceptance: isolated, serial, non-retried, read-only
 * production browser gate. Evidence hygiene: DOM/network snapshots and video
 * are disabled so no payload, token or payroll row can enter a trace; the
 * action log masks password input values; screenshots are failure-only plus
 * explicitly sanitized success captures taken by the spec itself.
 */
const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "";

export default defineConfig({
  testDir: "./e2e",
  testMatch: /g4-payroll-production-ui-acceptance\.spec\.ts$/,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: true,
  timeout: 180_000,
  expect: { timeout: 20_000 },
  reporter: [["list"], ["html", { outputFolder: "playwright-g4-production-ui-report" }]],
  use: {
    baseURL: BASE_URL,
    ...devices["Desktop Chrome"],
    locale: "en",
    colorScheme: "light",
    ignoreHTTPSErrors: false,
    trace: { mode: "retain-on-failure", snapshots: false, sources: false },
    screenshot: "only-on-failure",
    video: "off",
  },
  projects: [{ name: "g4-production-ui-acceptance", use: { ...devices["Desktop Chrome"] } }],
});
