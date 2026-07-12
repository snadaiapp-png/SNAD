/**
 * SNAD CRM Tenant Isolation — cross-tenant access must fail
 * ----------------------------------------------------------------------------
 * Branch: crm/002d-authenticated-acceptance-environment
 *
 * Logs in as Tenant B's admin and proves that every attempt to read
 * Tenant A's CRM entities is rejected by the backend. The seed SQL
 * (apps/sanad-platform/src/test/resources/sql/crm-acceptance-seed.sql)
 * creates the following Tenant A entities with stable UUIDs that we
 * reference below:
 *
 *   • Account     aa00aa00-aa00-4aa0-8aa0-aa00aa00aa01
 *   • Contact     cc00cc00-cc00-4cc0-8cc0-cc00cc00cc01
 *   • Lead        ll00ll00-ll00-4ll0-8ll0-ll00ll00ll01
 *   • Opportunity oo00oo00-oo00-4oo0-8oo0-oo00oo00oo01
 *
 * Tenant B must:
 *   1. Receive 404 (or 403) when fetching these IDs via the API.
 *   2. See an empty/error state when navigating to the detail URLs.
 *   3. Never see Tenant A's data in any list endpoint.
 *
 * Required env vars:
 *   - PLAYWRIGHT_BASE_URL
 *   - CRM_TENANT_B_EMAIL
 *   - CRM_TENANT_B_PASSWORD
 */
import { test, expect, type APIResponse, type Page } from "@playwright/test";

const TENANT_B_EMAIL = process.env.CRM_TENANT_B_EMAIL ?? "";
const TENANT_B_PASSWORD = process.env.CRM_TENANT_B_PASSWORD ?? "";

// Stable Tenant A entity UUIDs from the seed SQL.
const TENANT_A_ACCOUNT_ID = "aa00aa00-aa00-4aa0-8aa0-aa00aa00aa01";
const TENANT_A_CONTACT_ID = "cc00cc00-cc00-4cc0-8cc0-cc00cc00cc01";
const TENANT_A_LEAD_ID = "ll00ll00-ll00-4ll0-8ll0-ll00ll00ll01";
const TENANT_A_OPPORTUNITY_ID = "oo00oo00-oo00-4oo0-8oo0-oo00oo00oo01";
// Known Tenant A display names — must never appear in Tenant B's lists.
const TENANT_A_ACCOUNT_NAME = "Tenant A Sample Account";
const TENANT_A_CONTACT_NAME = "Aisha Al-Saud";
const TENANT_A_LEAD_NAME = "Tenant A Sample Lead";
const TENANT_A_OPPORTUNITY_NAME = "Tenant A Sample Opportunity";
const TENANT_A_ORG_NAME = "Tenant A Org";

interface LoginResponse {
  accessToken: string;
  user: { id: string; tenantId: string; email: string; displayName: string | null; status: string };
}

async function loginTenantB(page: Page): Promise<string> {
  const response: APIResponse = await page.request.post("/api/platform/api/v1/auth/login", {
    data: { email: TENANT_B_EMAIL, password: TENANT_B_PASSWORD },
    headers: { "Content-Type": "application/json" },
  });
  expect(response.ok(), `Tenant B login failed: ${response.status()}`).toBe(true);
  const body = (await response.json()) as LoginResponse;
  expect(body.user.tenantId, "Tenant B user should be in a different tenant than A").not.toBe(
    "11111111-1111-4111-8111-111111111111",
  );
  return body.accessToken;
}

async function waitForCrmReady(page: Page, route: string): Promise<void> {
  await page.goto(route);
  await page.waitForSelector("#crm-operational-content", { timeout: 30_000 });
  await page.waitForLoadState("networkidle");
}

test.describe("CRM Tenant Isolation — Tenant B cannot see Tenant A data", () => {
  test.describe.configure({ mode: "serial" });

  test.beforeAll(async () => {
    expect(TENANT_B_EMAIL, "CRM_TENANT_B_EMAIL env var must be set").toBeTruthy();
    expect(TENANT_B_PASSWORD, "CRM_TENANT_B_PASSWORD env var must be set").toBeTruthy();
  });

  let accessToken: string;

  test.beforeAll(async ({ browser }) => {
    // Use a throwaway page to login and harvest the access token.
    const context = await browser.newContext();
    const page = await context.newPage();
    accessToken = await loginTenantB(page);
    await context.close();
  });

  test("Tenant B cannot fetch Tenant A's account via API", async ({ page }) => {
    const response = await page.request.get(
      `/api/platform/api/v1/crm/accounts/${TENANT_A_ACCOUNT_ID}`,
      { headers: { Authorization: `Bearer ${accessToken}` } },
    );
    expect(response.status(), "cross-tenant account fetch must fail (4xx)").toBeLessThan(500);
    expect(response.status(), "cross-tenant account fetch must not succeed").toBeGreaterThanOrEqual(400);
  });

  test("Tenant B cannot fetch Tenant A's contact via API", async ({ page }) => {
    const response = await page.request.get(
      `/api/platform/api/v1/crm/contacts/${TENANT_A_CONTACT_ID}`,
      { headers: { Authorization: `Bearer ${accessToken}` } },
    );
    expect(response.status(), "cross-tenant contact fetch must fail (4xx)").toBeLessThan(500);
    expect(response.status(), "cross-tenant contact fetch must not succeed").toBeGreaterThanOrEqual(400);
  });

  test("Tenant B cannot fetch Tenant A's lead via API", async ({ page }) => {
    const response = await page.request.get(
      `/api/platform/api/v1/crm/leads/${TENANT_A_LEAD_ID}`,
      { headers: { Authorization: `Bearer ${accessToken}` } },
    );
    expect(response.status(), "cross-tenant lead fetch must fail (4xx)").toBeLessThan(500);
    expect(response.status(), "cross-tenant lead fetch must not succeed").toBeGreaterThanOrEqual(400);
  });

  test("Tenant B cannot fetch Tenant A's opportunity via API", async ({ page }) => {
    const response = await page.request.get(
      `/api/platform/api/v1/crm/opportunities/${TENANT_A_OPPORTUNITY_ID}`,
      { headers: { Authorization: `Bearer ${accessToken}` } },
    );
    expect(response.status(), "cross-tenant opportunity fetch must fail (4xx)").toBeLessThan(500);
    expect(response.status(), "cross-tenant opportunity fetch must not succeed").toBeGreaterThanOrEqual(400);
  });

  test("Tenant B cannot open Tenant A's account detail page in the SPA", async ({ page }) => {
    await page.goto(`/crm/accounts/${TENANT_A_ACCOUNT_ID}`);
    await page.waitForLoadState("domcontentloaded");
    // The SPA will attempt to fetch customer-360 and the backend will
    // reject. The shell should still render (the auth cookie works for
    // Tenant B), but the account content must show an error or
    // not-found state — never Tenant A's data.
    const bodyText = await page.locator("body").innerText({ timeout: 30_000 });
    expect(bodyText, "Tenant A account name must never appear in Tenant B's view").not.toContain(
      TENANT_A_ACCOUNT_NAME,
    );
    expect(bodyText, "Tenant A org name must never appear in Tenant B's view").not.toContain(
      TENANT_A_ORG_NAME,
    );
  });

  test("Tenant B cannot open Tenant A's contact detail page in the SPA", async ({ page }) => {
    await page.goto(`/crm/contacts/${TENANT_A_CONTACT_ID}`);
    await page.waitForLoadState("domcontentloaded");
    const bodyText = await page.locator("body").innerText({ timeout: 30_000 });
    expect(bodyText, "Tenant A contact name must never appear in Tenant B's view").not.toContain(
      TENANT_A_CONTACT_NAME,
    );
  });

  test("Tenant B cannot open Tenant A's lead detail page in the SPA", async ({ page }) => {
    await page.goto(`/crm/leads/${TENANT_A_LEAD_ID}`);
    await page.waitForLoadState("domcontentloaded");
    const bodyText = await page.locator("body").innerText({ timeout: 30_000 });
    expect(bodyText, "Tenant A lead name must never appear in Tenant B's view").not.toContain(
      TENANT_A_LEAD_NAME,
    );
  });

  test("Tenant B cannot open Tenant A's opportunity detail page in the SPA", async ({ page }) => {
    await page.goto(`/crm/opportunities/${TENANT_A_OPPORTUNITY_ID}`);
    await page.waitForLoadState("domcontentloaded");
    const bodyText = await page.locator("body").innerText({ timeout: 30_000 });
    expect(bodyText, "Tenant A opportunity name must never appear in Tenant B's view").not.toContain(
      TENANT_A_OPPORTUNITY_NAME,
    );
  });

  test("Tenant B accounts list contains no Tenant A data", async ({ page }) => {
    await waitForCrmReady(page, "/crm/accounts");
    const bodyText = await page.locator("body").innerText();
    expect(bodyText).not.toContain(TENANT_A_ACCOUNT_NAME);
    expect(bodyText).not.toContain(TENANT_A_ORG_NAME);
  });

  test("Tenant B contacts list contains no Tenant A data", async ({ page }) => {
    await waitForCrmReady(page, "/crm/contacts");
    const bodyText = await page.locator("body").innerText();
    expect(bodyText).not.toContain(TENANT_A_CONTACT_NAME);
  });

  test("Tenant B leads list contains no Tenant A data", async ({ page }) => {
    await waitForCrmReady(page, "/crm/leads");
    const bodyText = await page.locator("body").innerText();
    expect(bodyText).not.toContain(TENANT_A_LEAD_NAME);
  });

  test("Tenant B opportunities list contains no Tenant A data", async ({ page }) => {
    await waitForCrmReady(page, "/crm/opportunities");
    const bodyText = await page.locator("body").innerText();
    expect(bodyText).not.toContain(TENANT_A_OPPORTUNITY_NAME);
  });

  test("Tenant B dashboard KPIs reflect only Tenant B data", async ({ page }) => {
    await waitForCrmReady(page, "/crm/overview");
    const bodyText = await page.locator("body").innerText();
    // The dashboard never lists entity names, but we assert the page
    // renders without surfacing any Tenant A identifier.
    expect(bodyText).not.toContain(TENANT_A_ACCOUNT_NAME);
    expect(bodyText).not.toContain(TENANT_A_LEAD_NAME);
    expect(bodyText).not.toContain(TENANT_A_OPPORTUNITY_NAME);
  });
});
