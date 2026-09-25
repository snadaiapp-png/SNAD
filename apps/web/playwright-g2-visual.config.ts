import { defineConfig, devices } from "@playwright/test";

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3001";

export default defineConfig({
  testDir: "./e2e",
  testMatch: /g2-visual\.spec\.ts$/,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [
    ["html", { outputFolder: "playwright-g2-visual-report" }],
    ["list"],
  ],
  timeout: 90_000,
  expect: { timeout: 20_000 },
  use: {
    baseURL: BASE_URL,
    channel: "chrome",
    locale: "ar",
    colorScheme: "light",
    trace: "retain-on-failure",
    screenshot: "on",
    video: "retain-on-failure",
    ignoreHTTPSErrors: true,
  },
  projects: [
    {
      name: "g2-visual-desktop",
      use: { ...devices["Desktop Chrome"], locale: "ar", colorScheme: "light" },
    },
    {
      name: "g2-visual-mobile",
      use: {
        ...devices["Pixel 5"],
        viewport: { width: 375, height: 667 },
        locale: "ar",
        colorScheme: "light",
      },
    },
  ],
});
