import { test, expect, type Page } from "@playwright/test";

/**
 * HRM G4 Production UI Acceptance — real browser logins against live
 * production, read-only. No request interception, no mocks, no fixtures, no
 * hardcoded credentials, no skipped tests, no synthetic success.
 *
 * Preceding evidence this gate complements (never replaces):
 *   - G4_PRODUCTION_API_AUTHORIZATION = PASS (certification run 38302800978)
 *   - That certification used SYNTHETIC_RUN_ID against the dormant certified
 *     topology; it does not prove browser UX nor data-bearing surfaces.
 *
 * Security posture:
 *   - Fail-closed on any missing protected credential.
 *   - Passwords/tokens are never logged; traces disable DOM/network snapshots
 *     (see playwright-g4-production-ui.config.ts) so no payload, token or
 *     payroll row can leak into evidence; screenshots are failure-only plus
 *     explicitly sanitized success captures of data-free states.
 *   - This spec performs ZERO payroll mutations: production holds the dormant
 *     certified topology and no CALCULATE grant may be fabricated for testing.
 */

const WEB = process.env.PLAYWRIGHT_BASE_URL ?? "";
const ME = `${WEB}/api/platform/api/v1/auth/me`;
const PAYROLL_API = `${WEB}/api/platform/api/v2/hr/payroll`;

const REQUIRED = [
  "PLAYWRIGHT_BASE_URL",
  "G4_OWNER_EMAIL", "G4_OWNER_PASSWORD", "G4_OWNER_TENANT_ID",
  "G4_UNAUTHORIZED_EMAIL", "G4_UNAUTHORIZED_PASSWORD",
  "G4_FOREIGN_EMAIL", "G4_FOREIGN_PASSWORD", "G4_FOREIGN_TENANT_ID",
] as const;

function required(name: (typeof REQUIRED)[number]): string {
  const value = process.env[name];
  if (!value) throw new Error(`G4 UI FAIL-CLOSED: missing ${name}`);
  return value;
}

interface MeBody {
  id: string;
  tenantId: string;
  email: string;
  capabilities: string[];
}

interface LoginResult {
  accessToken: string;
  user: { id: string; tenantId: string; email: string; status: string };
}

let ownerUserId = "";
let ownerTenantId = "";
let ownerRunIds: string[] = [];

async function login(page: Page, email: string, password: string): Promise<LoginResult> {
  await page.goto("/");
  await page.locator("#login-email").fill(email);
  await page.locator("#login-password").fill(password);
  const responsePromise = page.waitForResponse(res =>
    res.request().method() === "POST" && res.url().includes("/api/platform/api/v1/auth/login"));
  await page.locator('form button[type="submit"]').click();
  const response = await responsePromise;
  expect(response.status(), `authenticated login for ${email}`).toBe(200);
  const data = await response.json() as LoginResult;
  expect(data.accessToken).toBeTruthy();
  expect(data.user.status).toBe("ACTIVE");
  try {
    await page.waitForURL(url => url.pathname.startsWith("/workspace"), { timeout: 45_000 });
  } finally {
    // Never leave the plaintext password in the DOM: failure evidence is
    // captured after test teardown, so a cleared field cannot leak.
    await page.locator("#login-password").fill("").catch(() => {});
  }
  return data;
}

test.describe.configure({ mode: "serial" });

test("G4 UI A - canonical owner: /auth/me identity, effective HRM.PAYROLL.VIEW, payroll surface, no forbidden, no unauthorized tools", async ({ page, request }, testInfo) => {
  for (const name of REQUIRED) required(name);
  ownerTenantId = required("G4_OWNER_TENANT_ID");

  const loginData = await login(page, required("G4_OWNER_EMAIL"), required("G4_OWNER_PASSWORD"));
  expect(loginData.user.id, "owner user.id is present").toBeTruthy();
  expect(loginData.user.tenantId, "owner binds the canonical control tenant").toBe(ownerTenantId);
  ownerUserId = loginData.user.id;

  // Identity + effective permissions through the BFF /auth/me contract.
  const meResponse = await request.get(ME, { headers: { Authorization: `Bearer ${loginData.accessToken}` } });
  expect(meResponse.status(), "owner /auth/me is readable through the BFF").toBe(200);
  const meBody = await meResponse.json() as MeBody;
  expect(meBody.id, "/auth/me user.id matches the login session").toBe(ownerUserId);
  expect(meBody.tenantId, "/auth/me tenant binding is canonical").toBe(ownerTenantId);
  expect(meBody.capabilities, "owner effective capabilities include HRM.PAYROLL.VIEW")
    .toContain("HRM.PAYROLL.VIEW");

  // Payroll surface through the real browser session.
  await page.goto("/hr/payroll");
  await expect(page.getByRole("heading", { name: "Payroll review" })).toBeVisible();
  await expect(page.locator('a[href="/hr/payroll"]'), "payroll link is present in navigation").toBeVisible();
  await expect(page.locator('[role="alert"]')).not.toContainText(/forbidden/i);
  await expect(page.locator('[role="alert"]')).not.toContainText(/Unable to load data/i);

  // Data loads, or the deterministic empty state renders - never an error.
  const runsPanel = page.locator('section[aria-label="Payroll runs"]');
  await expect(runsPanel).toBeVisible();
  await expect(runsPanel.locator('text=No payroll runs found (empty)')
    .or(runsPanel.locator("button:has(strong)")).first()).toBeVisible({ timeout: 20_000 });

  // The owner is view-only in the certified topology: no fabrication tools.
  await expect(page.getByRole("button", { name: "Create payroll run" })).toHaveCount(0);

  const isEmpty = await runsPanel.locator('text=No payroll runs found (empty)').count();
  if (isEmpty > 0) {
    await expect(page.getByText("Select a run to see details")).toBeVisible();
    ownerRunIds = [];
    // Empty production topology: the full page is data-free and safe as evidence.
    await page.screenshot({ path: testInfo.outputPath("g4-ui-owner-payroll.png"), fullPage: true });
    testInfo.attach("g4-ui-owner-payroll", { path: testInfo.outputPath("g4-ui-owner-payroll.png"), contentType: "image/png" });
  } else {
    // Runs exist: keep evidence sanitized (header only, no payroll rows) and
    // resolve owner run ids through the API (never scraped from UI text).
    const runIds = await request.get(`${PAYROLL_API}/runs`, { headers: { Authorization: `Bearer ${loginData.accessToken}` } });
    expect(runIds.status(), "owner payroll list HTTP 200").toBe(200);
    const list = await runIds.json() as Array<{ id: string }>;
    ownerRunIds = Array.isArray(list) ? list.map(run => run.id).filter(Boolean) : [];
    await page.screenshot({ path: testInfo.outputPath("g4-ui-owner-payroll-header.png"), clip: { x: 0, y: 0, width: 1280, height: 360 } });
    testInfo.attach("g4-ui-owner-payroll-header", { path: testInfo.outputPath("g4-ui-owner-payroll-header.png"), contentType: "image/png" });
  }
});

test("G4 UI B - unauthorized same-tenant user: no VIEW capability, no payroll link, forbidden surface, backend read denial", async ({ page, request }, testInfo) => {
  const loginData = await login(page, required("G4_UNAUTHORIZED_EMAIL"), required("G4_UNAUTHORIZED_PASSWORD"));
  expect(loginData.user.id, "unauthorized identity is distinct from the owner").not.toBe(ownerUserId);
  expect(loginData.user.tenantId, "unauthorized user is inside the owner tenant").toBe(required("G4_OWNER_TENANT_ID"));

  const meResponse = await request.get(ME, { headers: { Authorization: `Bearer ${loginData.accessToken}` } });
  expect(meResponse.status()).toBe(200);
  const meBody = await meResponse.json() as MeBody;
  expect(meBody.tenantId).toBe(required("G4_OWNER_TENANT_ID"));
  expect(meBody.capabilities, "unauthorized user must lack HRM.PAYROLL.VIEW").not.toContain("HRM.PAYROLL.VIEW");

  await page.goto("/hr/payroll");
  await expect(page.locator('[role="alert"]')).toContainText(/forbidden/i);
  await expect(page.locator('a[href="/hr/payroll"]'), "no payroll link in navigation").toHaveCount(0);

  // Denial is enforced by the backend, not by interface hiding alone.
  const denied = await request.get(`${PAYROLL_API}/runs`, { headers: { Authorization: `Bearer ${loginData.accessToken}` } });
  expect(denied.status(), "backend denies payroll reads for this user").toBe(403);

  // Forbidden surface is data-free: safe full-page evidence.
  await page.screenshot({ path: testInfo.outputPath("g4-ui-unauthorized-forbidden.png"), fullPage: true });
  testInfo.attach("g4-ui-unauthorized-forbidden", { path: testInfo.outputPath("g4-ui-unauthorized-forbidden.png"), contentType: "image/png" });
});

test("G4 UI C - foreign tenant: distinct tenant, forbidden surface, URL manipulation grants nothing, cross-tenant API denied", async ({ page, request }, testInfo) => {
  const ownerTenant = required("G4_OWNER_TENANT_ID");
  const loginData = await login(page, required("G4_FOREIGN_EMAIL"), required("G4_FOREIGN_PASSWORD"));
  expect(loginData.user.tenantId, "foreign tenant differs from the owner tenant").not.toBe(ownerTenant);
  expect(loginData.user.tenantId, "foreign identity binds its own tenant").toBe(required("G4_FOREIGN_TENANT_ID"));

  const meResponse = await request.get(ME, { headers: { Authorization: `Bearer ${loginData.accessToken}` } });
  expect(meResponse.status()).toBe(200);
  const meBody = await meResponse.json() as MeBody;
  expect(meBody.tenantId).toBe(required("G4_FOREIGN_TENANT_ID"));
  expect(meBody.capabilities, "foreign user must lack HRM.PAYROLL.VIEW").not.toContain("HRM.PAYROLL.VIEW");

  await page.goto("/hr/payroll");
  await expect(page.locator('[role="alert"]')).toContainText(/forbidden/i);

  // URL and query-parameter manipulation must not grant any additional reach.
  await page.goto(`/hr/payroll?runId=${crypto.randomUUID()}&tenantId=${ownerTenant}&period=2026-10`);
  await expect(page.locator('[role="alert"]')).toContainText(/forbidden/i);
  await expect(page.locator('section[aria-label="Payroll runs"]')).toHaveCount(0);

  // Isolation is proven at the backend boundary, not by interface hiding:
  // a real owner run id (when one exists) or a synthetic UUID still denied.
  const candidates = ownerRunIds.length > 0 ? [ownerRunIds[0]] : [crypto.randomUUID()];
  for (const candidate of candidates) {
    const cross = await request.get(`${PAYROLL_API}/runs/${candidate}`, { headers: { Authorization: `Bearer ${loginData.accessToken}` } });
    expect([403, 404]).toContain(cross.status());
  }
  const listDenied = await request.get(`${PAYROLL_API}/runs`, { headers: { Authorization: `Bearer ${loginData.accessToken}` } });
  expect(listDenied.status(), "foreign user payroll list denied").toBe(403);

  // Forbidden surface is data-free: safe full-page evidence.
  await page.screenshot({ path: testInfo.outputPath("g4-ui-foreign-forbidden.png"), fullPage: true });
  testInfo.attach("g4-ui-foreign-forbidden", { path: testInfo.outputPath("g4-ui-foreign-forbidden.png"), contentType: "image/png" });
});
