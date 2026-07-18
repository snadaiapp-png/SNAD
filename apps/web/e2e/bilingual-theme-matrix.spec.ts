/**
 * SNAD E2E — Bilingual RTL/LTR and Theme Matrix
 * ----------------------------------------------------------------------------
 * Runs against the local Next.js production server (next start).
 *
 * The standard visual matrix has no authenticated backend. Authenticated-shell
 * controls are therefore asserted when they are present; otherwise the test
 * verifies the intentional protected-route redirect and preserved return URL.
 */
import { test, expect, type Page } from "@playwright/test";

async function expectProtectedWorkspaceRedirect(page: Page): Promise<void> {
  await page.waitForURL(
    (url) => url.pathname === "/" && url.searchParams.get("returnUrl") === "/workspace",
    { timeout: 10_000 },
  );
  await expect(page.locator("#login-email")).toBeVisible();
}

test.describe("SNAD — Bilingual Theme Matrix", () => {
  test("root route renders with correct locale and direction", async ({ page }, testInfo) => {
    const expectedDir = testInfo.project.metadata?.expectedDir as string;
    const expectedLang = testInfo.project.metadata?.expectedLang as string;

    await page.goto("/");
    await page.waitForLoadState("networkidle");

    expect(await page.getAttribute("html", "lang")).toBe(expectedLang);
    expect(await page.getAttribute("html", "dir")).toBe(expectedDir);

    const dataTheme = await page.getAttribute("html", "data-theme");
    expect(dataTheme).toBeTruthy();
    expect(["light", "dark"]).toContain(dataTheme);
  });

  test("root route shows SNAD brand identity", async ({ page }) => {
    await page.goto("/");
    await page.waitForLoadState("networkidle");

    const bodyText = await page.textContent("body");
    expect(bodyText).toBeTruthy();
    const hasBrand =
      bodyText!.includes("SNAD") ||
      bodyText!.includes("سند") ||
      bodyText!.toLowerCase().includes("snad");
    expect(hasBrand).toBeTruthy();
  });

  test("theme persists after page reload", async ({ page }) => {
    await page.goto("/");
    await page.waitForLoadState("networkidle");
    const dataThemeBefore = await page.getAttribute("html", "data-theme");

    await page.reload();
    await page.waitForLoadState("networkidle");

    expect(await page.getAttribute("html", "data-theme")).toBe(dataThemeBefore);
  });

  test("locale persists after page reload", async ({ page }, testInfo) => {
    const expectedLang = testInfo.project.metadata?.expectedLang as string;

    await page.goto("/");
    await page.waitForLoadState("networkidle");
    expect(await page.getAttribute("html", "lang")).toBe(expectedLang);

    await page.reload();
    await page.waitForLoadState("networkidle");

    expect(await page.getAttribute("html", "lang")).toBe(expectedLang);
  });

  test("no hydration errors on root route", async ({ page }) => {
    const errors: string[] = [];
    page.on("console", (message) => {
      if (message.type() === "error") errors.push(message.text());
    });
    page.on("pageerror", (error) => errors.push(`PAGE_ERROR: ${error.message}`));

    await page.goto("/");
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(1500);

    const hydrationErrors = errors.filter((error) =>
      error.includes("Hydration") ||
      error.includes("hydration") ||
      error.includes("did not match") ||
      error.includes("Text content does not match"),
    );
    expect(
      hydrationErrors,
      `Hydration errors detected: ${hydrationErrors.join("; ")}`,
    ).toEqual([]);
  });

  test("authenticated surface renders language switcher or protects the route", async ({ page }) => {
    await page.goto("/workspace");

    const switcher = page.locator('[role="group"][aria-label="Language"]');
    if (await switcher.count()) {
      await expect(switcher.first()).toBeVisible();
    } else {
      await expectProtectedWorkspaceRedirect(page);
    }
  });

  test("authenticated surface renders theme switcher or protects the route", async ({ page }) => {
    await page.goto("/workspace");

    const themeButton = page.locator(
      'button[aria-label*="Theme"], button[aria-label*="المظهر"]',
    );
    if (await themeButton.count()) {
      const box = await themeButton.first().boundingBox();
      expect(box).toBeTruthy();
      expect(box!.height).toBeGreaterThanOrEqual(32);
    } else {
      await expectProtectedWorkspaceRedirect(page);
    }
  });

  test("authenticated route renders shell or a complete protected login surface", async ({ page }) => {
    await page.goto("/workspace");

    const header = page.locator("header, [role='banner']").first();
    if (await header.isVisible().catch(() => false)) {
      const box = await header.boundingBox();
      expect(box).toBeTruthy();
      expect(box!.height).toBeGreaterThan(40);
    } else {
      await expectProtectedWorkspaceRedirect(page);
    }

    const bodyBox = await page.locator("body").boundingBox();
    expect(bodyBox).toBeTruthy();
    expect(bodyBox!.height).toBeGreaterThan(100);
  });
});
