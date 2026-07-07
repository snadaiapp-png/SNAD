import { defineConfig, devices } from '@playwright/test';

/**
 * SNAD | سند — Playwright Configuration
 *
 * Per PM Directive §1.1: Real Visual Regression Tests
 * - Uses toHaveScreenshot() for visual comparison
 * - Fixed viewport, fonts, timezone, locale to reduce false diffs
 * - Uploads HTML report + diff images as artifacts
 * - No automatic baseline updates in CI
 */
export default defineConfig({
  testDir: './tests/visual',
  outputDir: './test-results/visual',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 0 : 0,
  workers: 1,
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['list'],
  ],
  use: {
    baseURL: 'http://localhost:3001',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    locale: 'ar-SA',
    timezoneId: 'Asia/Riyadh',
    viewport: { width: 1440, height: 900 },
    // Fix fonts to reduce false diffs
    extraHTTPHeaders: {
      'Accept-Language': 'ar,en;q=0.9',
    },
  },
  projects: [
    // Desktop Light RTL
    {
      name: 'desktop-light-rtl',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1440, height: 900 },
        locale: 'ar-SA',
        colorScheme: 'light',
      },
    },
    // Desktop Dark RTL
    {
      name: 'desktop-dark-rtl',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1440, height: 900 },
        locale: 'ar-SA',
        colorScheme: 'dark',
      },
    },
    // Desktop Light LTR
    {
      name: 'desktop-light-ltr',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1440, height: 900 },
        locale: 'en-US',
        colorScheme: 'light',
      },
    },
    // Desktop Dark LTR
    {
      name: 'desktop-dark-ltr',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1440, height: 900 },
        locale: 'en-US',
        colorScheme: 'dark',
      },
    },
    // Mobile RTL
    {
      name: 'mobile-rtl',
      use: {
        ...devices['Pixel 5'],
        locale: 'ar-SA',
        colorScheme: 'light',
      },
    },
    // Mobile LTR
    {
      name: 'mobile-ltr',
      use: {
        ...devices['Pixel 5'],
        locale: 'en-US',
        colorScheme: 'light',
      },
    },
  ],
  webServer: {
    command: 'npm run dev -- --port 3001',
    url: 'http://localhost:3001',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    cwd: 'apps/web',
  },
});
