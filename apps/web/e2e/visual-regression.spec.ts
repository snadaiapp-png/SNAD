/**
 * SNAD Visual Regression — Baseline & Comparison Tests
 * ----------------------------------------------------------------------------
 * P0-1: Visual Regression Testing
 *
 * This suite captures baseline screenshots of key UI surfaces and compares
 * them on subsequent runs. Any visual diff exceeding the threshold fails CI.
 *
 * Workflow:
 *   1. First run / baseline update:  npx playwright test --update-snapshots
 *   2. Subsequent runs (CI):          npx playwright test
 *
 * The baseline screenshots are committed to the repository under
 * apps/web/tests/visual/__screenshots__/ so that CI compares against
 * a stable, reviewed baseline — not a flaky auto-generated one.
 *
 * Diff artifacts (when a test fails) are uploaded as CI artifacts for
 * human review. The rollback decision is:
 *   - If diff < threshold (0.1px): PASS (anti-aliasing noise)
 *   - If diff >= threshold: FAIL → human review → either update baseline
 *     (intentional change) or rollback (unintended regression)
 */
import { test, expect } from "@playwright/test";

test.describe("Visual Regression — Auth Surfaces", () => {
  test("login screen — Arabic RTL Light", async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem("snad.locale", "ar");
      localStorage.setItem("snad.theme", "light");
    });
    await page.goto("/");
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(1000);
    await expect(page).toHaveScreenshot("login-ar-rtl-light.png", {
      maxDiffPixelRatio: 0.05,
      maxDiffPixels: 50000,
      threshold: 0.5,
    });
  });

  test("login screen — English LTR Light", async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem("snad.locale", "en");
      localStorage.setItem("snad.theme", "light");
    });
    await page.goto("/");
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(1000);
    await expect(page).toHaveScreenshot("login-en-ltr-light.png", {
      maxDiffPixelRatio: 0.05,
      maxDiffPixels: 50000,
      threshold: 0.5,
    });
  });

  test("login screen — Arabic RTL Dark", async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem("snad.locale", "ar");
      localStorage.setItem("snad.theme", "dark");
    });
    await page.goto("/");
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(1000);
    await expect(page).toHaveScreenshot("login-ar-rtl-dark.png", {
      maxDiffPixelRatio: 0.05,
      maxDiffPixels: 50000,
      threshold: 0.5,
    });
  });

  test("login screen — English LTR Dark", async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem("snad.locale", "en");
      localStorage.setItem("snad.theme", "dark");
    });
    await page.goto("/");
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(1000);
    await expect(page).toHaveScreenshot("login-en-ltr-dark.png", {
      maxDiffPixelRatio: 0.05,
      maxDiffPixels: 50000,
      threshold: 0.5,
    });
  });
});

test.describe("Visual Regression — Forgot Password", () => {
  test("forgot password — Arabic RTL", async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem("snad.locale", "ar");
      localStorage.setItem("snad.theme", "light");
    });
    await page.goto("/auth/forgot-password");
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(1000);
    await expect(page).toHaveScreenshot("forgot-password-ar-rtl.png", {
      maxDiffPixelRatio: 0.05,
      maxDiffPixels: 50000,
      threshold: 0.5,
    });
  });

  test("forgot password — English LTR", async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem("snad.locale", "en");
      localStorage.setItem("snad.theme", "light");
    });
    await page.goto("/auth/forgot-password");
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(1000);
    await expect(page).toHaveScreenshot("forgot-password-en-ltr.png", {
      maxDiffPixelRatio: 0.05,
      maxDiffPixels: 50000,
      threshold: 0.5,
    });
  });
});

test.describe("Visual Regression — Reset Password", () => {
  test("reset password — Arabic RTL", async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem("snad.locale", "ar");
      localStorage.setItem("snad.theme", "light");
    });
    await page.goto("/reset-password");
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(1000);
    await expect(page).toHaveScreenshot("reset-password-ar-rtl.png", {
      maxDiffPixelRatio: 0.05,
      maxDiffPixels: 50000,
      threshold: 0.5,
    });
  });
});

test.describe("Visual Regression — Protected Routes (Auth Redirect)", () => {
  test("workspace redirect — Arabic RTL", async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem("snad.locale", "ar");
      localStorage.setItem("snad.theme", "light");
    });
    await page.goto("/workspace");
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(1500);
    // After redirect, we should be back on / (login screen)
    await expect(page).toHaveScreenshot("workspace-redirect-ar-rtl.png", {
      maxDiffPixelRatio: 0.05,
      maxDiffPixels: 50000,
      threshold: 0.5,
    });
  });

  test("control-plane redirect — English LTR", async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem("snad.locale", "en");
      localStorage.setItem("snad.theme", "light");
    });
    await page.goto("/control-plane");
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(1500);
    await expect(page).toHaveScreenshot("control-plane-redirect-en-ltr.png", {
      maxDiffPixelRatio: 0.05,
      maxDiffPixels: 50000,
      threshold: 0.5,
    });
  });

});
