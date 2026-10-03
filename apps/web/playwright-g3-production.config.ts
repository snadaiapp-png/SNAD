import { defineConfig, devices } from "@playwright/test";

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "https://snad-app.vercel.app";

export default defineConfig({
  testDir: "./e2e",
  testMatch: /g3-production-readonly\.spec\.ts$/,
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  workers: 1,
  reporter: [
    ["html", { outputFolder: "playwright-g3-production-report" }],
    ["list"],
  ],
  timeout: 90_000,
  expect: { timeout: 15_000 },
  use: {
    baseURL: BASE_URL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    ignoreHTTPSErrors: false,
    ...devices["Desktop Chrome"],
  },
  projects: [{ name: "g3-production-readonly", use: { ...devices["Desktop Chrome"], locale: "en", colorScheme: "light" } }],
});
