import { defineConfig, devices } from "@playwright/test";

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3001";

const storageState = (locale: "ar" | "en", theme: "light" | "dark" | "system") => ({
  cookies: [],
  origins: [{
    origin: BASE_URL,
    localStorage: [
      { name: "snad.locale", value: locale },
      { name: "snad.theme", value: theme },
    ],
  }],
});

export default defineConfig({
  testDir: "./e2e",
  // These tests mutate the real Production environment and require protected
  // credentials. They are executed only by playwright.crm007-production.config.ts.
  testIgnore: [
    "**/crm-007-production-closure.spec.ts",
    // CRM-EXEC acceptance requires CRM_TENANT_A_EMAIL/PASSWORD credentials
    // which are only available in the CRM Authenticated E2E workflow
    // (playwright.crm-acceptance.config.ts). Running it in the default
    // Playwright E2E & Visual Regression workflow would fail the
    // beforeAll env-var guard.
    "**/crm-execution-acceptance.spec.ts",
  ],
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [
    ["html", { outputFolder: "playwright-report" }],
    ["list"],
  ],
  timeout: 60_000,
  expect: {
    timeout: 10_000,
    toHaveScreenshot: {
      maxDiffPixelRatio: 0.05,
      maxDiffPixels: 5000,
      threshold: 0.3,
    },
  },
  snapshotPathTemplate: "{snapshotDir}/{testFileDir}/__screenshots__/{arg}{ext}",
  snapshotDir: "./e2e",
  use: {
    baseURL: BASE_URL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [
    {
      name: "ar-rtl-light",
      use: {
        ...devices["Desktop Chrome"],
        locale: "ar",
        colorScheme: "light",
        storageState: storageState("ar", "light"),
      },
      metadata: { expectedDir: "rtl", expectedLang: "ar" },
    },
    {
      name: "ar-rtl-dark",
      use: {
        ...devices["Desktop Chrome"],
        locale: "ar",
        colorScheme: "dark",
        storageState: storageState("ar", "dark"),
      },
      metadata: { expectedDir: "rtl", expectedLang: "ar" },
    },
    {
      name: "ar-rtl-system",
      use: {
        ...devices["Desktop Chrome"],
        locale: "ar",
        colorScheme: "light",
        storageState: storageState("ar", "system"),
      },
      metadata: { expectedDir: "rtl", expectedLang: "ar" },
    },
    {
      name: "en-ltr-light",
      use: {
        ...devices["Desktop Chrome"],
        locale: "en",
        colorScheme: "light",
        storageState: storageState("en", "light"),
      },
      metadata: { expectedDir: "ltr", expectedLang: "en" },
    },
    {
      name: "en-ltr-dark",
      use: {
        ...devices["Desktop Chrome"],
        locale: "en",
        colorScheme: "dark",
        storageState: storageState("en", "dark"),
      },
      metadata: { expectedDir: "ltr", expectedLang: "en" },
    },
    {
      name: "en-ltr-system",
      use: {
        ...devices["Desktop Chrome"],
        locale: "en",
        colorScheme: "dark",
        storageState: storageState("en", "system"),
      },
      metadata: { expectedDir: "ltr", expectedLang: "en" },
    },
  ],
});
