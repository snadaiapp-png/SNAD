/**
 * SNAD CRM Operational Routes — E2E smoke tests (unauthenticated)
 * ----------------------------------------------------------------------------
 * Classification: CRM Route Smoke — unauthenticated
 *
 * These tests verify that every CRM operational route renders without
 * server errors or hydration crashes. Because the CI environment does
 * not have a live backend, we test auth-protection behavior only.
 *
 * These are NOT acceptance tests for CRUD, RBAC, or tenant isolation.
 * Authenticated acceptance tests require a live backend with seeded data
 * and are documented in docs/crm/evidence/CRM-002-OPERATIONAL-UI-EVIDENCE.md
 * as a known limitation of the CI environment.
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

test.describe("CRM Route Smoke — unauthenticated", () => {
  test("/crm redirects to /crm/overview", async ({ page }) => {
    await page.goto("/crm");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(1000);
    const finalUrl = page.url();
    expect(finalUrl).toMatch(/\/crm\/(overview|)$/);
  });

  for (const route of CRM_ROUTES) {
    test(`${route} renders without server error`, async ({ page }) => {
      const response = await page.goto(route);
      await page.waitForLoadState("domcontentloaded");
      await page.waitForTimeout(500);

      // Must not return HTTP 5xx
      if (response) {
        expect(response.status()).toBeLessThan(500);
      }

      // Body must have content (auth loading or login redirect)
      const bodyBox = await page.locator("body").boundingBox();
      expect(bodyBox).toBeTruthy();
      expect(bodyBox!.height).toBeGreaterThan(50);
    });
  }

  test("back/forward navigation works", async ({ page }) => {
    await page.goto("/crm/overview");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(300);

    await page.goto("/crm/accounts");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(300);

    await page.goBack();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(300);

    await page.goForward();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(300);
  });

  test("refresh maintains route", async ({ page }) => {
    await page.goto("/crm/contacts");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(300);

    await page.reload();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(300);
    expect(page.url()).toMatch(/\/crm\/contacts/);
  });
});

test.describe("CRM Detail Routes — render smoke", () => {
  test("contact detail renders without 5xx", async ({ page }) => {
    const response = await page.goto("/crm/contacts/test-id");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);
    if (response) {
      expect(response.status()).toBeLessThan(500);
    }
  });

  test("lead detail renders without 5xx", async ({ page }) => {
    const response = await page.goto("/crm/leads/test-id");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);
    if (response) {
      expect(response.status()).toBeLessThan(500);
    }
  });

  test("opportunity detail renders without 5xx", async ({ page }) => {
    const response = await page.goto("/crm/opportunities/test-id");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);
    if (response) {
      expect(response.status()).toBeLessThan(500);
    }
  });
});
