import { defineConfig, devices } from "@playwright/test";
/** G4-T10: isolated, serial and non-retried authenticated payroll acceptance. */
export default defineConfig({
  testDir: "./e2e",
  testMatch: /g4-payroll-authenticated\.spec\.ts$/,
  fullyParallel: false, workers: 1, retries: 0,
  forbidOnly: !!process.env.CI,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: [["list"], ["html", { outputFolder: "playwright-g4-payroll-report" }]],
  use: { baseURL: process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3001",
    ...devices["Desktop Chrome"], trace: "retain-on-failure", screenshot: "only-on-failure" },
});
