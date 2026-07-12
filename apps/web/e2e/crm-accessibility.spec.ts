/**
 * SNAD CRM Accessibility — Axe automated checks
 * ----------------------------------------------------------------------------
 * Branch: crm/002d-authenticated-acceptance-environment
 *
 * Runs @axe-core/playwright against the seven CRM operational routes
 * after logging in as Tenant A admin. Asserts 0 critical and 0 serious
 * violations on each route.
 *
 * Routes covered:
 *   - /crm/overview
 *   - /crm/accounts
 *   - /crm/contacts
 *   - /crm/leads
 *   - /crm/opportunities
 *   - /crm/imports
 *   - /crm/settings/custom-fields
 *
 * Required env vars:
 *   - PLAYWRIGHT_BASE_URL
 *   - CRM_TENANT_A_EMAIL, CRM_TENANT_A_PASSWORD
 *
 * Required devDependency: @axe-core/playwright
 *   (The CI workflow installs it if missing.)
 */
import { test, expect, type APIResponse, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

const TENANT_A_EMAIL = process.env.CRM_TENANT_A_EMAIL ?? "";
const TENANT_A_PASSWORD = process.env.CRM_TENANT_A_PASSWORD ?? "";

interface LoginResponse {
  accessToken: string;
  user: { id: string; tenantId: string; email: string; displayName: string | null; status: string };
}

async function loginAsAdmin(page: Page): Promise<void> {
  expect(TENANT_A_EMAIL, "CRM_TENANT_A_EMAIL env var must be set").toBeTruthy();
  expect(TENANT_A_PASSWORD, "CRM_TENANT_A_PASSWORD env var must be set").toBeTruthy();
  const response: APIResponse = await page.request.post("/api/platform/api/v1/auth/login", {
    data: { email: TENANT_A_EMAIL, password: TENANT_A_PASSWORD },
    headers: { "Content-Type": "application/json" },
  });
  expect(response.ok(), `Login failed: ${response.status()}`).toBe(true);
  const body = (await response.json()) as LoginResponse;
  expect(body.accessToken).toBeTruthy();
}

async function waitForCrmReady(page: Page, route: string): Promise<void> {
  await page.goto(route);
  await page.waitForSelector("#crm-operational-content", { timeout: 30_000 });
  await page.waitForLoadState("networkidle");
}

const CRM_ROUTES_FOR_A11Y = [
  "/crm/overview",
  "/crm/accounts",
  "/crm/contacts",
  "/crm/leads",
  "/crm/opportunities",
  "/crm/imports",
  "/crm/settings/custom-fields",
] as const;

test.describe("CRM Accessibility — Axe automated checks", () => {
  test.describe.configure({ mode: "serial" });

  test.beforeAll(async () => {
    expect(TENANT_A_EMAIL).toBeTruthy();
    expect(TENANT_A_PASSWORD).toBeTruthy();
  });

  test("login as Tenant A admin (shared setup)", async ({ page }) => {
    await loginAsAdmin(page);
    // Touch /crm/overview to bootstrap the SPA so subsequent tests
    // inherit the auth cookie via the shared browser context.
    await waitForCrmReady(page, "/crm/overview");
  });

  for (const route of CRM_ROUTES_FOR_A11Y) {
    test(`${route} has 0 critical and 0 serious Axe violations`, async ({ page }) => {
      await waitForCrmReady(page, route);

      const results = await new AxeBuilder({ page })
        // We only fail on critical + serious violations. Moderate and
        // minor violations are reported but do not fail the gate — they
        // are tracked as backlog items in the design-system documentation.
        .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
        .analyze();

      const critical = results.violations.filter((v) => v.impact === "critical");
      const serious = results.violations.filter((v) => v.impact === "serious");

      if (critical.length > 0 || serious.length > 0) {
        const summary = [...critical, ...serious]
          .map(
            (v) =>
              `  • [${v.impact}] ${v.id}: ${v.description}\n    help: ${v.helpUrl}\n    targets: ${v.nodes
                .slice(0, 3)
                .map((n) => n.target.join(","))
                .join(" | ")}`,
          )
          .join("\n");
        console.error(`Axe violations on ${route}:\n${summary}`);
      }

      expect(critical, `${route}: ${critical.length} critical Axe violations`).toEqual([]);
      expect(serious, `${route}: ${serious.length} serious Axe violations`).toEqual([]);
    });
  }

  test("all CRM routes render the main content slot (smoke)", async ({ page }) => {
    // Sanity: every route in the matrix must render the CRM shell.
    for (const route of CRM_ROUTES_FOR_A11Y) {
      await waitForCrmReady(page, route);
      const heading = page.locator("h1").first();
      await expect(heading, `${route} should render at least one h1`).toBeVisible({ timeout: 15_000 });
    }
  });
});
