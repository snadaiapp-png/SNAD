import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

const EXEC_EMAIL = process.env.EXECUTIVE_E2E_EMAIL;
const EXEC_PASSWORD = process.env.EXECUTIVE_E2E_PASSWORD;

test.describe("SNAD Login v2", () => {
  test("loads the approved wordmark and keeps primary controls reachable on a short mobile viewport", async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 640 });
    await page.goto("/", { waitUntil: "domcontentloaded" });

    const logo = page.locator('img[src="/assets/brand/snad-logo-official-wordmark.png"]');
    await expect(logo).toBeVisible();
    expect(await logo.evaluate((node: HTMLImageElement) => node.naturalWidth)).toBeGreaterThan(0);
    await expect(page.locator("#login-email")).toBeVisible();
    await expect(page.locator("#login-password")).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toBeVisible();

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    );
    expect(overflow).toBe(false);
  });

  test("has no serious or critical axe violations on the anonymous login surface", async ({ page }) => {
    await page.goto("/", { waitUntil: "domcontentloaded" });
    const results = await new AxeBuilder({ page }).analyze();
    expect(
      results.violations.filter((violation) =>
        ["serious", "critical"].includes(violation.impact ?? ""),
      ),
    ).toEqual([]);
  });

  test("invalid credentials show a generic error without raw internals", async ({ page }) => {
    await page.goto("/", { waitUntil: "domcontentloaded" });
    await page.locator("#login-email").fill("not-a-user@example.invalid");
    await page.locator("#login-password").fill("DefinitelyWrong-1!");
    await page.locator('button[type="submit"]').click();

    const alert = page.getByRole("alert").first();
    await expect(alert).toBeVisible();
    await expect(alert).not.toContainText(
      /stack|trace|http:\/\/|https:\/\/|SQLException|NullPointerException/i,
    );
  });

  test("keeps technical credential inputs LTR while the shell follows the active locale", async ({ page }, testInfo) => {
    await page.goto("/", { waitUntil: "domcontentloaded" });
    await expect(page.locator("#login-email")).toHaveAttribute("dir", "ltr");
    await expect(page.locator("#login-password")).toHaveAttribute("dir", "ltr");
    const expectedDir = String(testInfo.project.metadata.expectedDir ?? "rtl");
    await expect(page.locator("html")).toHaveAttribute("dir", expectedDir);
  });

  test("authorized executive principal returns to /executive/tenants", async ({ page }) => {
    test.skip(!EXEC_EMAIL || !EXEC_PASSWORD, "executive E2E credentials are required");
    await page.goto("/?returnUrl=%2Fexecutive%2Ftenants", {
      waitUntil: "domcontentloaded",
    });
    await page.locator("#login-email").fill(EXEC_EMAIL!);
    await page.locator("#login-password").fill(EXEC_PASSWORD!);
    await page.locator('button[type="submit"]').click();
    await expect(page).toHaveURL(/\/executive\/tenants(?:[?#].*)?$/);

    const storage = await page.evaluate(() => ({
      local: Object.keys(localStorage),
      session: Object.keys(sessionStorage),
    }));
    expect(storage.local.filter((key) => /token|refresh/i.test(key))).toEqual([]);
    expect(storage.session.filter((key) => /token|refresh/i.test(key))).toEqual([]);
  });
});
