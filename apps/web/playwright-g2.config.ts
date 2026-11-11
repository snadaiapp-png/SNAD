import { defineConfig, devices } from "@playwright/test";

/**
 * G2 Authenticated Acceptance Playwright config.
 *
 * Single project (no locale/theme matrix) because the G2 spec is a stateful
 * cross-role journey — running it across 6 locale/theme projects would
 * cause mutable-state collisions (each project would create a leave request
 * with the same deterministic reason text, then the Manager/HR find logic
 * would match the wrong row).
 *
 * The dedicated .github/workflows/g2-authenticated-acceptance.yml uses this
 * config to run only g2-authenticated.spec.ts once, against the
 * provisioned G2 test tenant (Employee + Manager + HR users + WORKFLOW
 * module entitlement).
 *
 * Prerequisites (provisioned by the workflow):
 *   - E2E_EMPLOYEE_EMAIL / E2E_EMPLOYEE_PASSWORD
 *   - E2E_MANAGER_EMAIL  / E2E_MANAGER_PASSWORD
 *   - E2E_HR_EMAIL       / E2E_HR_PASSWORD
 *   - PLAYWRIGHT_BASE_URL (defaults to http://127.0.0.1:3001)
 *
 * Missing credentials FAIL the spec (no test.skip, no Assumptions soft-skip).
 */

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3001";

export default defineConfig({
  testDir: "./e2e",
  // Only run the G2 authenticated spec — no other specs in this config.
  testMatch: /g2-authenticated\.spec\.ts$/,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 0 : 0, // No retries — required regression, fail fast on real defects
  workers: 1, // Serial — stateful journey cannot run in parallel
  reporter: [
    ["html", { outputFolder: "playwright-g2-report" }],
    ["list"],
  ],
  timeout: 90_000, // Longer timeout for cross-role stateful flow
  expect: {
    timeout: 15_000,
  },
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
      name: "g2-authenticated-acceptance",
      use: {
        ...devices["Desktop Chrome"],
        locale: "en",
        colorScheme: "light",
      },
    },
  ],
});
