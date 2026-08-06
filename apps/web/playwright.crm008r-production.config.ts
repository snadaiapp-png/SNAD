import { defineConfig, devices } from "@playwright/test";

const configuredBaseUrl = process.env.PLAYWRIGHT_BASE_URL ?? "https://snad-app.vercel.app";
const baseURL = configuredBaseUrl.replace("127.0.0.1", "localhost");

export default defineConfig({
  testDir: "./e2e",
  testMatch: ["**/crm-008r-production-closure.spec.ts"],
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  workers: 1,
  timeout: 180_000,
  expect: { timeout: 30_000 },
  reporter: [["html", { outputFolder: "crm008r-production-report" }], ["list"]],
  use: {
    baseURL,
    channel: "chrome",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
    ...devices["Desktop Chrome"],
    locale: "en",
    colorScheme: "light",
    storageState: {
      cookies: [],
      origins: [{
        origin: baseURL,
        localStorage: [
          { name: "snad.locale", value: "en" },
          { name: "snad.theme", value: "light" },
        ],
      }],
    },
  },
});
