/**
 * SNAD — Playwright configuration
 * ----------------------------------------------------------------------------
 * Runs E2E tests against the local Next.js production server.
 *
 * Test matrix (per PM Final Closure Order §10):
 *   - Arabic + RTL + Light
 *   - Arabic + RTL + Dark
 *   - Arabic + RTL + System
 *   - English + LTR + Light
 *   - English + LTR + Dark
 *   - English + LTR + System
 *
 * For each combination, the tests verify:
 *   - SNAD logo is visible
 *   - Page direction (dir attribute) is correct
 *   - Language switcher is visible
 *   - Theme switcher is visible
 *   - No header collapse
 *   - No text overlap (basic layout sanity)
 *   - Theme persists after reload
 *   - Language persists after reload
 *   - No console errors
 *   - No hydration errors
 *
 * Usage:
 *   npx playwright test              # run all tests
 *   npx playwright test --project=ar-rtl-light   # run one project
 *   npx playwright test --reporter=html          # HTML report
 */
import { defineConfig, devices } from "@playwright/test";

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3001";

export default defineConfig({
  testDir: "./e2e",
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
    // Visual regression: screenshots stored under e2e/__screenshots__/
    // Cross-environment tolerance: CI runners render slightly differently
    // than local dev machines (font rendering, anti-aliasing, GPU). The
    // thresholds below allow for these minor differences while still
    // catching significant visual regressions (>5% pixel diff).
    toHaveScreenshot: {
      maxDiffPixelRatio: 0.05,
      maxDiffPixels: 5000,
      threshold: 0.3,
    },
  },
  // Snapshot path template — keeps baselines in-repo for reviewable diffs
  snapshotPathTemplate: "{snapshotDir}/{testFileDir}/__screenshots__/{arg}{ext}",
  snapshotDir: "./e2e",
  use: {
    baseURL: BASE_URL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },

  projects: [
    // === Arabic + RTL ===
    {
      name: "ar-rtl-light",
      use: {
        ...devices["Desktop Chrome"],
        locale: "ar",
        colorScheme: "light",
        // Pre-set localStorage before first navigation to avoid FOUC
        storageState: {
          cookies: [],
          origins: [
            {
              origin: BASE_URL,
              localStorage: [
                { name: "snad.locale", value: "ar" },
                { name: "snad.theme", value: "light" },
              ],
            },
          ],
        },
      },
      metadata: { expectedDir: "rtl", expectedLang: "ar" },
    },
    {
      name: "ar-rtl-dark",
      use: {
        ...devices["Desktop Chrome"],
        locale: "ar",
        colorScheme: "dark",
        storageState: {
          cookies: [],
          origins: [
            {
              origin: BASE_URL,
              localStorage: [
                { name: "snad.locale", value: "ar" },
                { name: "snad.theme", value: "dark" },
              ],
            },
          ],
        },
      },
      metadata: { expectedDir: "rtl", expectedLang: "ar" },
    },
    {
      name: "ar-rtl-system",
      use: {
        ...devices["Desktop Chrome"],
        locale: "ar",
        colorScheme: "light", // system mode resolves to light
        storageState: {
          cookies: [],
          origins: [
            {
              origin: BASE_URL,
              localStorage: [
                { name: "snad.locale", value: "ar" },
                { name: "snad.theme", value: "system" },
              ],
            },
          ],
        },
      },
      metadata: { expectedDir: "rtl", expectedLang: "ar" },
    },

    // === English + LTR ===
    {
      name: "en-ltr-light",
      use: {
        ...devices["Desktop Chrome"],
        locale: "en",
        colorScheme: "light",
        storageState: {
          cookies: [],
          origins: [
            {
              origin: BASE_URL,
              localStorage: [
                { name: "snad.locale", value: "en" },
                { name: "snad.theme", value: "light" },
              ],
            },
          ],
        },
      },
      metadata: { expectedDir: "ltr", expectedLang: "en" },
    },
    {
      name: "en-ltr-dark",
      use: {
        ...devices["Desktop Chrome"],
        locale: "en",
        colorScheme: "dark",
        storageState: {
          cookies: [],
          origins: [
            {
              origin: BASE_URL,
              localStorage: [
                { name: "snad.locale", value: "en" },
                { name: "snad.theme", value: "dark" },
              ],
            },
          ],
        },
      },
      metadata: { expectedDir: "ltr", expectedLang: "en" },
    },
    {
      name: "en-ltr-system",
      use: {
        ...devices["Desktop Chrome"],
        locale: "en",
        colorScheme: "dark", // system mode resolves to dark
        storageState: {
          cookies: [],
          origins: [
            {
              origin: BASE_URL,
              localStorage: [
                { name: "snad.locale", value: "en" },
                { name: "snad.theme", value: "system" },
              ],
            },
          ],
        },
      },
      metadata: { expectedDir: "ltr", expectedLang: "en" },
    },
  ],
});
