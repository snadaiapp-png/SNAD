import { defineConfig, devices } from "@playwright/test";

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "https://snad-app.vercel.app";
const TRUSTED_OIDC_TOKEN = process.env.VERCEL_TRUSTED_OIDC_TOKEN?.trim();

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
    extraHTTPHeaders: TRUSTED_OIDC_TOKEN
      ? { "x-vercel-trusted-oidc-idp-token": TRUSTED_OIDC_TOKEN }
      : undefined,
  },
  projects: [{ name: "g3-production-readonly", use: { ...devices["Desktop Chrome"], locale: "en", colorScheme: "light" } }],
});
