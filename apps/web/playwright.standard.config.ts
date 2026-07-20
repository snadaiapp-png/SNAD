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
  testMatch: /.*\.spec\.ts$/,
  testIgnore: [
    "**/crm-authenticated-acceptance.spec.ts",
    "**/crm-tenant-isolation.spec.ts",
    "**/crm-rbac-acceptance.spec.ts",
    "**/crm-accessibility.spec.ts",
    "**/crm-route-smoke.spec.ts",
    // Mutates the real Production environment and requires protected secrets.
    // It is mandatory in playwright.crm007-production.config.ts only.
    "**/crm-007-production-closure.spec.ts",
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
    channel: "chrome",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
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
