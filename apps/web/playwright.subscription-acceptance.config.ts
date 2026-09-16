import { defineConfig, devices } from "@playwright/test";

const configuredBaseUrl = process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3001";
const BASE_URL = configuredBaseUrl.replace("127.0.0.1", "localhost");
process.env.PLAYWRIGHT_BASE_URL = BASE_URL;

export default defineConfig({
  testDir: "./e2e",
  testMatch: ["**/subscription-executive-acceptance.spec.ts"],
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: [["html", { outputFolder: "subscription-playwright-report" }], ["list"]],
  use: {
    baseURL: BASE_URL,
    channel: "chrome",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
  },
  projects: [{
    name: "subscription-acceptance",
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
