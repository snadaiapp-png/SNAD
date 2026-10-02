import { defineConfig, devices } from "@playwright/test";

/**
 * G3 Authenticated Acceptance Playwright config.
 *
 * Single project (no locale/theme matrix) because the G3 spec is a stateful
 * cross-role journey — running it across multiple locale/theme projects would
 * cause mutable-state collisions.
 *
 * The dedicated .github/workflows/g3-authenticated-acceptance.yml uses this
 * config to run only g3-authenticated.spec.ts once, against the provisioned
 * G3 test tenant (Employee + Manager + Unauthorized users + G3 capabilities).
 *
 * Prerequisites (provisioned by the workflow):
 *   - E2E_EMPLOYEE_EMAIL / E2E_EMPLOYEE_PASSWORD
 *   - E2E_MANAGER_EMAIL  / E2E_MANAGER_PASSWORD
 *   - E2E_UNAUTHORIZED_EMAIL / E2E_UNAUTHORIZED_PASSWORD
 *   - PLAYWRIGHT_BASE_URL (defaults to http://127.0.0.1:3001)
 *
 * Missing credentials FAIL the spec (no test.skip, no Assumptions soft-skip).
 */

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3001";

export default defineConfig({
  testDir: "./e2e",
  testMatch: /g3-authenticated\.spec\.ts$/,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [
    ["html", { outputFolder: "playwright-g3-report" }],
    ["list"],
  ],
  timeout: 90_000,
  expect: { timeout: 15_000 },
  use: {
    baseURL: BASE_URL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    ignoreHTTPSErrors: true,
    ...devices["Desktop Chrome"],
  },
  projects: [
    {
      name: "g3-authenticated-acceptance",
      use: {
        ...devices["Desktop Chrome"],
        locale: "en",
        colorScheme: "light",
      },
    },
  ],
});
