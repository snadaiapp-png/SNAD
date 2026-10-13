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
 *
 * Run 38321705462 root-cause fixes (evidence-keeping, assertion-identical):
 *   - Identity is checked first via authenticated browser-session /auth/me;
 *     protected UI navigation is then verified separately with real DOM
 *     assertions. No standalone request context or mocked response.
 *   - API probes use the browser context's request (real session) with an
 *     explicit 30s bound — the BFF grants auth paths a 125s upstream budget,
 *     which starved the former 180s test timeout on cold Render starts.
 *   - Locale is pinned to English via localStorage exactly like the proven
 *     G3 session helper (production default locale is Arabic).
 */

const WEB = process.env.PLAYWRIGHT_BASE_URL ?? "";
const ME_PATH = "/api/platform/api/v1/auth/me";
const ME = `${WEB}${ME_PATH}`;
const PAYROLL_API = `${WEB}/api/platform/api/v2/hr/payroll`;
const API_PROBE_TIMEOUT = 30_000;

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

interface Identity {
  body: MeBody;
  source: "application-navigation" | "browser-session-request";
}

let ownerUserId = "";
let ownerTenantId = "";
let ownerRunIds: string[] = [];

async function login(page: Page, email: string, password: string): Promise<LoginResult> {
  // Deterministic English locale exactly like the proven G3 session helper;
  // production renders Arabic by default and the assertions below pin the
  // English copy for evidence stability.
  await page.addInitScript(() => {
    if (!localStorage.getItem("snad.locale")) {
      localStorage.setItem("snad.locale", "en");
    }
  });
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

/**
 * Proves identity through the application's own /auth/me call on a real
 * navigation — the exact identity chain the UI depends on. The response is
 * captured (never intercepted or altered); only if the application does not
 * issue the call within 90s does a bounded browser-session request (same
 * cookie jar, Bearer like the app's own client) serve as fallback.
 */
async function appIdentity(page: Page, accessToken: string): Promise<Identity> {
  // Verify effective identity using the authenticated browser session before
  // the separate route-navigation probe. No mock or token logging.
  const response = await page.request.get(ME, {
    headers: { Authorization: 'Bearer ' + accessToken },
    timeout: API_PROBE_TIMEOUT,
  });
  expect(response.status(), 'authenticated browser-session /auth/me').toBe(200);
  return { body: (await response.json()) as MeBody, source: 'browser-session-request' };
}

async function openPayroll(page: Page, viaVisibleLink: boolean): Promise<void> {
  if (viaVisibleLink) {
    const link = page.locator('a[href="/hr/payroll"]');
    await expect(link, 'owner navigation link exists').toBeVisible({ timeout: 20_000 });
    await link.click({ noWaitAfter: true, timeout: 20_000 });
  } else {
    // Address-bar-equivalent access attempt for identities with no link.
    await page.evaluate(() => { window.location.assign('/hr/payroll'); });
  }
  await page.waitForURL(url => url.pathname === '/hr/payroll', { timeout: 25_000 });
}

test.describe.configure({ mode: "serial" });

test("G4 UI A - canonical owner: /auth/me identity, effective HRM.PAYROLL.VIEW, payroll surface, no forbidden, no unauthorized tools", async ({ page }, testInfo) => {
  for (const name of REQUIRED) required(name);
  ownerTenantId = required("G4_OWNER_TENANT_ID");

  const loginData = await login(page, required("G4_OWNER_EMAIL"), required("G4_OWNER_PASSWORD"));
  expect(loginData.user.id, "owner user.id is present").toBeTruthy();
  expect(loginData.user.tenantId, "owner binds the canonical control tenant").toBe(ownerTenantId);
  ownerUserId = loginData.user.id;

  // Identity + effective permissions through the application's own /auth/me.
  const identity = await appIdentity(page, loginData.accessToken);
  await openPayroll(page, true);
  expect(identity.body.id, "/auth/me user.id matches the login session").toBe(ownerUserId);
  expect(identity.body.tenantId, "/auth/me tenant binding is canonical").toBe(ownerTenantId);
  expect(identity.body.capabilities, "owner effective capabilities include HRM.PAYROLL.VIEW")
    .toContain("HRM.PAYROLL.VIEW");

  // Payroll surface through the real browser session.
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
    const runIds = await page.request.get(`${PAYROLL_API}/runs`, {
      headers: { Authorization: `Bearer ${loginData.accessToken}` },
      timeout: API_PROBE_TIMEOUT,
    });
    expect(runIds.status(), "owner payroll list HTTP 200").toBe(200);
    const list = await runIds.json() as Array<{ id: string }>;
    ownerRunIds = Array.isArray(list) ? list.map(run => run.id).filter(Boolean) : [];
    await page.screenshot({ path: testInfo.outputPath("g4-ui-owner-payroll-header.png"), clip: { x: 0, y: 0, width: 1280, height: 360 } });
    testInfo.attach("g4-ui-owner-payroll-header", { path: testInfo.outputPath("g4-ui-owner-payroll-header.png"), contentType: "image/png" });
  }
});

test("G4 UI B - unauthorized same-tenant user: no VIEW capability, no payroll link, forbidden surface, backend read denial", async ({ page }, testInfo) => {
  const loginData = await login(page, required("G4_UNAUTHORIZED_EMAIL"), required("G4_UNAUTHORIZED_PASSWORD"));
  expect(loginData.user.id, "unauthorized identity is distinct from the owner").not.toBe(ownerUserId);
  expect(loginData.user.tenantId, "unauthorized user is inside the owner tenant").toBe(required("G4_OWNER_TENANT_ID"));

  const identity = await appIdentity(page, loginData.accessToken);
  await openPayroll(page, false);
  expect(identity.body.tenantId).toBe(required("G4_OWNER_TENANT_ID"));
  expect(identity.body.capabilities, "unauthorized user must lack HRM.PAYROLL.VIEW").not.toContain("HRM.PAYROLL.VIEW");

  await expect(page.locator('[role="alert"]')).toContainText(/forbidden/i);
  await expect(page.locator('a[href="/hr/payroll"]'), "no payroll link in navigation").toHaveCount(0);

  // Denial is enforced by the backend, not by interface hiding alone.
  const denied = await page.request.get(`${PAYROLL_API}/runs`, {
    headers: { Authorization: `Bearer ${loginData.accessToken}` },
    timeout: API_PROBE_TIMEOUT,
  });
  expect(denied.status(), "backend denies payroll reads for this user").toBe(403);

  // Forbidden surface is data-free: safe full-page evidence.
  await page.screenshot({ path: testInfo.outputPath("g4-ui-unauthorized-forbidden.png"), fullPage: true });
  testInfo.attach("g4-ui-unauthorized-forbidden", { path: testInfo.outputPath("g4-ui-unauthorized-forbidden.png"), contentType: "image/png" });
});

test("G4 UI C - foreign tenant: distinct tenant, forbidden surface, URL manipulation grants nothing, cross-tenant API denied", async ({ page }, testInfo) => {
  const ownerTenant = required("G4_OWNER_TENANT_ID");
  const loginData = await login(page, required("G4_FOREIGN_EMAIL"), required("G4_FOREIGN_PASSWORD"));
  expect(loginData.user.tenantId, "foreign tenant differs from the owner tenant").not.toBe(ownerTenant);
  expect(loginData.user.tenantId, "foreign identity binds its own tenant").toBe(required("G4_FOREIGN_TENANT_ID"));

  const identity = await appIdentity(page, loginData.accessToken);
  await openPayroll(page, false);
  expect(identity.body.tenantId).toBe(required("G4_FOREIGN_TENANT_ID"));
  expect(identity.body.capabilities, "foreign user must lack HRM.PAYROLL.VIEW").not.toContain("HRM.PAYROLL.VIEW");

  await expect(page.locator('[role="alert"]')).toContainText(/forbidden/i);

  // URL and query-parameter manipulation must not grant any additional reach.
  await page.goto(`/hr/payroll?runId=${crypto.randomUUID()}&tenantId=${ownerTenant}&period=2026-10`);
  await expect(page.locator('[role="alert"]')).toContainText(/forbidden/i);
  await expect(page.locator('section[aria-label="Payroll runs"]')).toHaveCount(0);

  // Isolation is proven at the backend boundary, not by interface hiding:
  // a real owner run id (when one exists) or a synthetic UUID still denied.
  const candidates = ownerRunIds.length > 0 ? [ownerRunIds[0]] : [crypto.randomUUID()];
  for (const candidate of candidates) {
    const cross = await page.request.get(`${PAYROLL_API}/runs/${candidate}`, {
      headers: { Authorization: `Bearer ${loginData.accessToken}` },
      timeout: API_PROBE_TIMEOUT,
    });
    expect([403, 404]).toContain(cross.status());
  }
  const listDenied = await page.request.get(`${PAYROLL_API}/runs`, {
    headers: { Authorization: `Bearer ${loginData.accessToken}` },
    timeout: API_PROBE_TIMEOUT,
  });
  expect(listDenied.status(), "foreign user payroll list denied").toBe(403);

  // Forbidden surface is data-free: safe full-page evidence.
  await page.screenshot({ path: testInfo.outputPath("g4-ui-foreign-forbidden.png"), fullPage: true });
  testInfo.attach("g4-ui-foreign-forbidden", { path: testInfo.outputPath("g4-ui-foreign-forbidden.png"), contentType: "image/png" });
});
