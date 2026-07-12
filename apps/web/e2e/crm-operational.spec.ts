/**
 * SNAD CRM Operational Routes — E2E smoke tests
 * ----------------------------------------------------------------------------
 * Branch: crm/002b-final-operational-acceptance
 *
 * These tests verify that every CRM operational route renders *something*
 * without crashing the server or the client. Because the test environment
 * does not have a live backend, we cannot assert authenticated flows; we
 * assert instead that the page either:
 *   - Renders the auth-loading state (INITIALIZING), or
 *   - Redirects to "/" (the login screen) once the auth provider gives up.
 *
 * Both outcomes are correct auth-protection behavior. The contract we test
 * is "no server error, no uncaught hydration error, and the route is
 * reachable".
 *
 * Coverage:
 *   - /crm                  → server-side redirect to /crm/overview
 *   - /crm/overview
 *   - /crm/accounts
 *   - /crm/contacts
 *   - /crm/leads
 *   - /crm/pipelines
 *   - /crm/opportunities
 *   - /crm/activities
 *   - /crm/imports
 *   - /crm/settings/custom-fields
 *   - /crm/command-center
 *   - Browser back / forward navigation
 *   - Refresh maintains the route
 */
import { test, expect } from "@playwright/test";

const CRM_ROUTES = [
  "/crm/overview",
  "/crm/accounts",
  "/crm/contacts",
  "/crm/leads",
  "/crm/pipelines",
  "/crm/opportunities",
  "/crm/activities",
  "/crm/imports",
  "/crm/settings/custom-fields",
  "/crm/command-center",
] as const;

test.describe("CRM Operational Routes — render smoke", () => {
  test("/crm redirects to /crm/overview", async ({ page }) => {
    const response = await page.goto("/crm");
    // /crm is a server-side redirect; the eventual URL must be /crm/overview.
    await page.waitForLoadState("networkidle");
    const finalUrl = page.url();
    expect(finalUrl).toMatch(/\/crm\/overview$/);
    // No HTTP 5xx on the originating request.
    if (response) {
      expect(response.status()).toBeLessThan(500);
    }
  });

  for (const route of CRM_ROUTES) {
    test(`${route} renders without server error`, async ({ page }) => {
      const errors: string[] = [];
      page.on("pageerror", (err) => {
        errors.push(`PAGE_ERROR: ${err.message}`);
      });

      const response = await page.goto(route);
      await page.waitForLoadState("networkidle");
      await page.waitForTimeout(800);

      // The page must not return an HTTP 5xx error.
      if (response) {
        expect(response.status(), `route ${route} returned a server error`).toBeLessThan(500);
      }

      // The body must have non-trivial content (either the auth-loading
      // surface or the redirect target's login screen).
      const bodyBox = await page.locator("body").boundingBox();
      expect(bodyBox).toBeTruthy();
      expect(bodyBox!.height, `route ${route} rendered an empty body`).toBeGreaterThan(80);

      // No hydration errors.
      const hydrationErrors = errors.filter(
        (e) =>
          e.includes("Hydration") ||
          e.includes("hydration") ||
          e.includes("did not match") ||
          e.includes("Text content does not match"),
      );
      expect(hydrationErrors, `Hydration errors on ${route}: ${hydrationErrors.join("; ")}`).toEqual([]);
    });
  }

  test("/crm/overview renders the expected <html lang> / <html dir> attributes", async ({ page }, testInfo) => {
    const expectedLang = testInfo.project.metadata?.expectedLang as string;
    const expectedDir = testInfo.project.metadata?.expectedDir as string;

    await page.goto("/crm/overview");
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(600);

    const htmlLang = await page.getAttribute("html", "lang");
    const htmlDir = await page.getAttribute("html", "dir");
    expect(htmlLang).toBe(expectedLang);
    expect(htmlDir).toBe(expectedDir);
  });
});

test.describe("CRM Operational Routes — navigation", () => {
  test("back/forward navigation keeps the user on CRM routes", async ({ page }) => {
    await page.goto("/crm/overview");
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(500);

    await page.goto("/crm/accounts");
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(500);

    await page.goBack();
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(500);
    expect(page.url()).toMatch(/\/crm\/overview$/);

    await page.goForward();
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(500);
    expect(page.url()).toMatch(/\/crm\/accounts$/);
  });

  test("refresh maintains the route", async ({ page }) => {
    await page.goto("/crm/contacts");
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(500);
    expect(page.url()).toMatch(/\/crm\/contacts$/);

    await page.reload();
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(500);
    expect(page.url()).toMatch(/\/crm\/contacts$/);
  });
});

test.describe("CRM Operational Routes — detail routes", () => {
  test("contact detail route renders without server error", async ({ page }) => {
    const response = await page.goto("/crm/contacts/nonexistent-contact-id");
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(800);
    if (response) {
      expect(response.status()).toBeLessThan(500);
    }
    const bodyBox = await page.locator("body").boundingBox();
    expect(bodyBox).toBeTruthy();
    expect(bodyBox!.height).toBeGreaterThan(80);
  });

  test("lead detail route renders without server error", async ({ page }) => {
    const response = await page.goto("/crm/leads/nonexistent-lead-id");
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(800);
    if (response) {
      expect(response.status()).toBeLessThan(500);
    }
    const bodyBox = await page.locator("body").boundingBox();
    expect(bodyBox).toBeTruthy();
    expect(bodyBox!.height).toBeGreaterThan(80);
  });

  test("opportunity detail route renders without server error", async ({ page }) => {
    const response = await page.goto("/crm/opportunities/nonexistent-opportunity-id");
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(800);
    if (response) {
      expect(response.status()).toBeLessThan(500);
    }
    const bodyBox = await page.locator("body").boundingBox();
    expect(bodyBox).toBeTruthy();
    expect(bodyBox!.height).toBeGreaterThan(80);
  });
});
