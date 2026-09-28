import { defineConfig, devices } from "@playwright/test";

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3001";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [
    ["html", { outputFolder: "playwright-report-users-module" }],
    ["list"],
  ],
  timeout: 60_000,
  expect: { timeout: 15_000 },
  use: {
    baseURL: BASE_URL,
    channel: "chrome",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
  },
  projects: [
    {
      name: "users-behavior-desktop",
      testMatch: /users-module-authenticated\.spec\.ts$/,
      use: { ...devices["Desktop Chrome"] },
    },
    {
      name: "users-visual-desktop",
      testMatch: /users-module-visual\.spec\.ts$/,
      use: { ...devices["Desktop Chrome"] },
    },
    {
      name: "users-visual-mobile",
      testMatch: /users-module-visual\.spec\.ts$/,
      use: { ...devices["Pixel 7"] },
    },
  ],
});
