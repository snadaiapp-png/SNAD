import { defineConfig, devices } from "@playwright/test";
const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3001";
export default defineConfig({
  testDir: "./e2e",
  testMatch: [
    "**/crm-authenticated-acceptance.spec.ts",
    "**/crm-tenant-isolation.spec.ts",
    "**/crm-rbac-acceptance.spec.ts",
    "**/crm-accessibility.spec.ts",
    "**/crm-route-smoke.spec.ts",
  ],
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: [["html", { outputFolder: "crm-playwright-report" }], ["list"]],
  use: {
    baseURL: BASE_URL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [{
    name: "crm-acceptance",
    metadata: { expectedDir: "ltr", expectedLang: "en", expectedTheme: "light" },
    use: {
      ...devices["Desktop Chrome"],
      locale: "en",
      colorScheme: "light",
      storageState: {
        cookies: [],
        origins: [{
          origin: BASE_URL,
          localStorage: [
            { name: "snad.locale", value: "en" },
            { name: "snad.theme", value: "light" },
          ],
        }],
      },
    },
  }],
});
