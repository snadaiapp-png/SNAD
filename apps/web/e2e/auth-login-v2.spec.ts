import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

const BASE_URL = process.env.WEB_BASE_URL ?? "http://localhost:3000";
const EXEC_EMAIL = process.env.EXECUTIVE_E2E_EMAIL;
const EXEC_PASSWORD = process.env.EXECUTIVE_E2E_PASSWORD;

test.describe("SNAD Login v2", () => {
  test("loads the approved wordmark and keeps primary controls reachable on a short mobile viewport", async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 640 });
    await page.goto(`${BASE_URL}/`, { waitUntil: "domcontentloaded" });

    const logo = page.locator('img[src="/assets/brand/snad-logo-official-wordmark.png"]');
    await expect(logo).toBeVisible();
    expect(await logo.evaluate((node: HTMLImageElement) => node.naturalWidth)).toBeGreaterThan(0);
    await expect(page.locator("#login-email")).toBeVisible();
    await expect(page.locator("#login-password")).toBeVisible();
    await expect(page.getByRole("button", { name: "تسجيل الدخول" })).toBeVisible();

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    );
    expect(overflow).toBe(false);
  });

  test("has no serious or critical axe violations on the anonymous login surface", async ({ page }) => {
    await page.goto(`${BASE_URL}/`, { waitUntil: "domcontentloaded" });
    const results = await new AxeBuilder({ page }).analyze();
    expect(
      results.violations.filter((violation) =>
        ["serious", "critical"].includes(violation.impact ?? ""),
      ),
    ).toEqual([]);
  });

  test("invalid credentials show a generic error without raw internals", async ({ page }) => {
    await page.goto(`${BASE_URL}/`, { waitUntil: "domcontentloaded" });
    await page.locator("#login-email").fill("not-a-user@example.invalid");
    await page.locator("#login-password").fill("DefinitelyWrong-1!");
    await page.getByRole("button", { name: "تسجيل الدخول" }).click();

    const alert = page.getByRole("alert").first();
    await expect(alert).toBeVisible();
    await expect(alert).not.toContainText(
      /stack|trace|http:\/\/|https:\/\/|SQLException|NullPointerException/i,
    );
  });

  test("keeps the Arabic shell RTL while technical credential inputs are LTR", async ({ page }) => {
    await page.goto(`${BASE_URL}/`, { waitUntil: "domcontentloaded" });
    await expect(page.locator("#login-email")).toHaveAttribute("dir", "ltr");
    await expect(page.locator("#login-password")).toHaveAttribute("dir", "ltr");
    await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
  });

  test("authorized executive principal returns to /executive/tenants", async ({ page }) => {
    test.skip(!EXEC_EMAIL || !EXEC_PASSWORD, "executive E2E credentials are required");
    await page.goto(`${BASE_URL}/?returnUrl=%2Fexecutive%2Ftenants`, {
      waitUntil: "domcontentloaded",
    });
    await page.locator("#login-email").fill(EXEC_EMAIL!);
    await page.locator("#login-password").fill(EXEC_PASSWORD!);
    await page.getByRole("button", { name: "تسجيل الدخول" }).click();
    await expect(page).toHaveURL(/\/executive\/tenants(?:[?#].*)?$/);

    const storage = await page.evaluate(() => ({
      local: Object.keys(localStorage),
      session: Object.keys(sessionStorage),
    }));
    expect(storage.local.filter((key) => /token|refresh/i.test(key))).toEqual([]);
    expect(storage.session.filter((key) => /token|refresh/i.test(key))).toEqual([]);
  });
});
