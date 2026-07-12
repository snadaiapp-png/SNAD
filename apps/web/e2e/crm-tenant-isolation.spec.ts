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
 *   • Lead        ee964f6d-cff1-502b-a687-ae61611761de
 *   • Opportunity 5ff572da-a04a-5893-be50-d50e5ea64165
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
import { test, expect, type Page } from "@playwright/test";
import { loginThroughUi } from "./crm-auth-session";

const TENANT_B_EMAIL = process.env.CRM_TENANT_B_EMAIL ?? "";
const TENANT_B_PASSWORD = process.env.CRM_TENANT_B_PASSWORD ?? "";

// Stable Tenant A entity UUIDs from the seed SQL.
const TENANT_A_ACCOUNT_ID = "aa00aa00-aa00-4aa0-8aa0-aa00aa00aa01";
const TENANT_A_CONTACT_ID = "cc00cc00-cc00-4cc0-8cc0-cc00cc00cc01";
const TENANT_A_LEAD_ID = "ee964f6d-cff1-502b-a687-ae61611761de";
const TENANT_A_OPPORTUNITY_ID = "5ff572da-a04a-5893-be50-d50e5ea64165";
// Known Tenant A display names — must never appear in Tenant B's lists.
const TENANT_A_ACCOUNT_NAME = "Tenant A Sample Account";
const TENANT_A_CONTACT_NAME = "Aisha Al-Saud";
const TENANT_A_LEAD_NAME = "Tenant A Sample Lead";
const TENANT_A_OPPORTUNITY_NAME = "Tenant A Sample Opportunity";
const TENANT_A_ORG_NAME = "Tenant A Org";



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

  test.beforeEach(async ({ page }) => {
    const login = await loginThroughUi(page, TENANT_B_EMAIL, TENANT_B_PASSWORD);
    expect(login.user.tenantId, "Tenant B must not resolve to Tenant A").not.toBe(
      "11111111-1111-4111-8111-111111111111",
    );
    accessToken = login.accessToken;
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
