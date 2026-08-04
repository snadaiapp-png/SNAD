/**
 * CRM E2E Shared Helpers — reusable utilities for CRM Playwright specs.
 * ----------------------------------------------------------------------------
 * TD-004-1: Extracted from duplicated code across crm-lifecycle,
 * crm-authenticated-acceptance, crm-rbac-acceptance, and
 * crm-tenant-isolation specs.
 *
 * Provides:
 *   - waitForCrmReady() — navigate to CRM route and wait for auth shell
 *   - createTestLead() — create a lead via API for test setup
 *   - createTestAccount() — create an account via API for test setup
 *   - createTestPipeline() — create a pipeline via API for test setup
 *   - createTestContact() — create a contact via API for test setup
 */
import { expect, type Page } from "@playwright/test";

/* ============================================================================
 *  Environment helpers
 * ============================================================================ */

export const TENANT_A_EMAIL = process.env.CRM_TENANT_A_EMAIL ?? "";
export const TENANT_A_PASSWORD = process.env.CRM_TENANT_A_PASSWORD ?? "";
export const TENANT_B_EMAIL = process.env.CRM_TENANT_B_EMAIL ?? "";
export const TENANT_B_PASSWORD = process.env.CRM_TENANT_B_PASSWORD ?? "";

/* ============================================================================
 *  Navigation helpers
 * ============================================================================ */

/**
 * Wait for the SPA to finish bootstrapping and reach the AUTHENTICATED
 * state. The CRM shell renders `<main id="crm-operational-content">`
 * once auth is ready, so we wait for that element to appear.
 */
export async function waitForCrmReady(page: Page, route = "/crm/overview"): Promise<void> {
  await page.goto(route);
  await page.waitForSelector("#crm-operational-content", { timeout: 30_000 });
  await page.waitForLoadState("networkidle");
}

/* ============================================================================
 *  API test data helpers
 *
 *  These create entities via the V1 API using the Bearer token from login.
 *  They return the created entity for use in subsequent assertions.
 * ============================================================================ */

interface AuthHeaders {
  Authorization: string;
}

/**
 * Create a lead via the V1 CRM API.
 */
export async function createTestLead(
  page: Page,
  accessToken: string,
  overrides: Partial<{ displayName: string; companyName: string; email: string; source: string; status: string }> = {},
): Promise<{ id: string; displayName: string }> {
  const displayName = overrides.displayName ?? `E2E Lead ${Date.now()}`;
  const response = await page.request.post("/api/platform/api/v1/crm/leads", {
    data: {
      displayName,
      companyName: overrides.companyName ?? "E2E Test Corp",
      email: overrides.email ?? `e2e-lead+${Date.now()}@snad-crm-e2e.example`,
      source: overrides.source ?? "e2e-test",
      ...overrides,
    },
    headers: { Authorization: `Bearer ${accessToken}` } as AuthHeaders,
  });
  expect(response.ok(), `createTestLead failed: ${response.status()}`).toBe(true);
  const body = await response.json();
  expect(body.id, "Lead must have an ID").toBeTruthy();
  return { id: body.id, displayName };
}

/**
 * Create an account via the V1 CRM API.
 */
export async function createTestAccount(
  page: Page,
  accessToken: string,
  overrides: Partial<{ displayName: string; accountType: string; primaryCurrencyCode: string }> = {},
): Promise<{ id: string; displayName: string }> {
  const displayName = overrides.displayName ?? `E2E Account ${Date.now()}`;
  const response = await page.request.post("/api/platform/api/v1/crm/accounts", {
    data: {
      displayName,
      accountType: overrides.accountType ?? "CUSTOMER",
      primaryCurrencyCode: overrides.primaryCurrencyCode ?? "SAR",
      preferredLocale: "en",
      timeZone: "Asia/Riyadh",
      source: "e2e-test",
    },
    headers: { Authorization: `Bearer ${accessToken}` } as AuthHeaders,
  });
  expect(response.ok(), `createTestAccount failed: ${response.status()}`).toBe(true);
  const body = await response.json();
  expect(body.id, "Account must have an ID").toBeTruthy();
  return { id: body.id, displayName };
}

/**
 * Create a contact via the V1 CRM API.
 */
export async function createTestContact(
  page: Page,
  accessToken: string,
  accountId: string,
  overrides: Partial<{ givenName: string; familyName: string; primaryEmail: string }> = {},
): Promise<{ id: string; givenName: string }> {
  const givenName = overrides.givenName ?? `E2E Contact ${Date.now()}`;
  const response = await page.request.post("/api/platform/api/v1/crm/contacts", {
    data: {
      accountId,
      givenName,
      familyName: overrides.familyName ?? "Tester",
      primaryEmail: overrides.primaryEmail ?? `e2e-contact+${Date.now()}@snad-crm-e2e.example`,
      preferredLocale: "en",
      timeZone: "Asia/Riyadh",
      consentSummary: "E2E test consent",
    },
    headers: { Authorization: `Bearer ${accessToken}` } as AuthHeaders,
  });
  expect(response.ok(), `createTestContact failed: ${response.status()}`).toBe(true);
  const body = await response.json();
  expect(body.id, "Contact must have an ID").toBeTruthy();
  return { id: body.id, givenName };
}

/**
 * Create a pipeline with stages via the V1 CRM API.
 */
export async function createTestPipeline(
  page: Page,
  accessToken: string,
  overrides: Partial<{ name: string; stages: string[] }> = {},
): Promise<{ id: string; name: string; stageIds: string[] }> {
  const name = overrides.name ?? `E2E Pipeline ${Date.now()}`;
  const stages = overrides.stages ?? ["Qualification", "Proposal", "Negotiation", "Closed Won"];
  const response = await page.request.post("/api/platform/api/v1/crm/pipelines", {
    data: { name, currencyCode: "SAR", stages },
    headers: { Authorization: `Bearer ${accessToken}` } as AuthHeaders,
  });
  expect(response.ok(), `createTestPipeline failed: ${response.status()}`).toBe(true);
  const body = await response.json();
  expect(body.id, "Pipeline must have an ID").toBeTruthy();
  return { id: body.id, name, stageIds: body.stageIds ?? [] };
}

/* ============================================================================
 *  Assertion helpers
 * ============================================================================ */

/**
 * Assert that a status notice (toast / role="status") appeared after an action.
 */
export async function expectStatusNotice(page: Page, timeout = 10_000): Promise<void> {
  await expect(page.locator('[role="status"]').first()).toBeVisible({ timeout });
}

/**
 * Assert that the body contains text matching either English or Arabic.
 */
export async function expectBilingual(page: Page, enText: string, arText: string, timeout = 15_000): Promise<void> {
  await expect(page.locator("body")).toContainText(new RegExp(`${enText}|${arText}`, "i"), { timeout });
}
