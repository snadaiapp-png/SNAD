import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

test.describe("SNAD Login v3 visual acceptance", () => {
  test("renders the v3 shell with the approved wordmark and no legacy panel copy", async ({ page }) => {
    await page.goto("/", { waitUntil: "domcontentloaded" });

    await expect(page.locator('[data-auth-version="v3"]')).toBeVisible();

    const logo = page.locator(
      'img[src="/assets/brand/snad-logo-official-wordmark.png"]',
    );
    await expect(logo).toHaveCount(1);
    await expect(logo).toBeVisible();
    expect(
      await logo.evaluate((node: HTMLImageElement) => node.naturalWidth),
    ).toBeGreaterThan(0);

    await expect(page.getByText("SNAD • سند")).toHaveCount(0);
    await expect(page.getByText("نظام تشغيل أعمال ذكي")).toHaveCount(0);
  });

  test("uses an asymmetric two-region composition on desktop", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/", { waitUntil: "domcontentloaded" });

    const layout = await page.locator('[data-auth-version="v3"] > div').evaluate((node) => {
      const style = getComputedStyle(node);
      return {
        display: style.display,
        columns: style.gridTemplateColumns,
      };
    });

    expect(layout.display).toBe("grid");
    expect(layout.columns).not.toBe("none");
  });

  test("keeps the full login flow reachable at 360x640 without horizontal overflow", async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 640 });
    await page.goto("/", { waitUntil: "domcontentloaded" });

    await expect(page.locator("#login-email")).toBeVisible();
    await expect(page.locator("#login-password")).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toBeVisible();

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    );
    expect(overflow).toBe(false);

    await page.locator('button[type="submit"]').scrollIntoViewIfNeeded();
    await expect(page.locator('button[type="submit"]')).toBeInViewport();

    const brandPanelDisplay = await page
      .locator('[data-auth-version="v3"] aside')
      .evaluate((node) => getComputedStyle(node).display);
    expect(brandPanelDisplay).toBe("none");
  });

  test("remains reachable on a short desktop viewport", async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 });
    await page.goto("/", { waitUntil: "domcontentloaded" });

    await page.locator('button[type="submit"]').scrollIntoViewIfNeeded();
    await expect(page.locator('button[type="submit"]')).toBeInViewport();

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    );
    expect(overflow).toBe(false);
  });

  test("reflows at the effective viewport width of 200 percent zoom", async ({ page }) => {
    // A 1440px desktop viewport at 200% browser zoom exposes roughly 720 CSS px.
    // Playwright cannot set browser UI zoom portably, so this verifies the
    // equivalent reflow width while manual 200% zoom remains a release gate.
    await page.setViewportSize({ width: 720, height: 1280 });
    await page.goto("/", { waitUntil: "domcontentloaded" });

    await page.locator('button[type="submit"]').scrollIntoViewIfNeeded();
    await expect(page.locator('button[type="submit"]')).toBeVisible();

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    );
    expect(overflow).toBe(false);
  });

  test("keeps technical inputs LTR while document direction follows locale", async ({ page }, testInfo) => {
    await page.goto("/", { waitUntil: "domcontentloaded" });
    await expect(page.locator("#login-email")).toHaveAttribute("dir", "ltr");
    await expect(page.locator("#login-password")).toHaveAttribute("dir", "ltr");
    await expect(page.locator("html")).toHaveAttribute(
      "dir",
      String(testInfo.project.metadata.expectedDir),
    );
  });

  test("has no serious or critical axe violations", async ({ page }) => {
    await page.goto("/", { waitUntil: "domcontentloaded" });
    const results = await new AxeBuilder({ page }).analyze();

    expect(
      results.violations.filter((violation) =>
        ["serious", "critical"].includes(violation.impact ?? ""),
      ),
    ).toEqual([]);
  });

  test("removes non-essential animation under reduced motion", async ({ page }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.goto("/", { waitUntil: "domcontentloaded" });

    const animation = await page
      .locator('[data-auth-version="v3"]')
      .evaluate((node) => getComputedStyle(node, "::before").animationName);
    expect(animation === "none" || animation === "").toBe(true);
  });
});
