import { test, expect, type Page, type APIRequestContext } from "@playwright/test";

/**
 * G4-T10 real authenticated acceptance. No request interception, mock,
 * fixture substitution, skipped tests, Docker, or synthetic success.
 * The dedicated CI environment MUST provision PostgreSQL Direct, backend,
 * frontend, two separate tenants and operator credentials.
 */
const BASE = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3001";
const API = `${BASE}/api/platform/api/v2/hr/payroll`;
const REQUIRED = ["G4_OPERATOR_EMAIL", "G4_OPERATOR_PASSWORD",
  "G4_FOREIGN_EMAIL", "G4_FOREIGN_PASSWORD", "G4_LEGAL_ENTITY_ID"];
function required(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`G4-T10 FAIL-CLOSED: missing ${name}`);
  return value;
}
function mutationHeaders(token: string) {
  return { Authorization: `Bearer ${token}`, "Idempotency-Key": crypto.randomUUID() };
}
async function login(page: Page, email: string, password: string) {
  await page.goto("/");
  await page.locator("#login-email").fill(email);
  await page.locator("#login-password").fill(password);
  const responsePromise = page.waitForResponse(res =>
    res.request().method() === "POST" && res.url().includes("/api/platform/api/v1/auth/login"));
  await page.locator('form button[type="submit"]').click();
  const response = await responsePromise;
  expect(response.status(), "authenticated operator login").toBe(200);
  const data = await response.json() as {
    accessToken: string; user: { tenantId: string; email: string; status: string };
  };
  expect(data.accessToken).toBeTruthy();
  expect(data.user.status).toBe("ACTIVE");
  await page.waitForURL(url => url.pathname.startsWith("/workspace"));
  return data;
}
async function getRun(request: APIRequestContext, token: string, id: string) {
  const response = await request.get(`${API}/runs/${id}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.status()).toBe(200);
  return response.json() as Promise<{ id: string; status: string; version: number }>;
}

test.describe.configure({ mode: "serial" });

test("authenticated payroll operator: create, calculate, review, approve, export boundary; foreign tenant denied", async ({ browser, request }) => {
  for (const name of REQUIRED) required(name);
  const operator = await browser.newContext();
  const page = await operator.newPage();
  const unexpected: string[] = [];
  page.on("response", response => {
    const path = new URL(response.url()).pathname;
    if (!path.includes("/api/v2/hr/payroll")) return;
    if ([403, 500].includes(response.status())) unexpected.push(`${path} => ${response.status()}`);
  });
  try {
    const loginData = await login(page, required("G4_OPERATOR_EMAIL"), required("G4_OPERATOR_PASSWORD"));
    await page.goto("/hr/payroll");
    await expect(page.getByRole("heading", { name: /Payroll review|مراجعة مسيرات الرواتب/ })).toBeVisible();
    const create = await request.post(`${API}/runs`, {
      headers: mutationHeaders(loginData.accessToken),
      data: {
        legalEntityId: required("G4_LEGAL_ENTITY_ID"),
        periodStart: "2026-09-01", periodEnd: "2026-09-30",
        currencyCode: "SAR", sourceCutoffAt: "2026-10-01T00:00:00Z",
      },
    });
    expect(create.status(), "authenticated payroll create").toBe(201);
    const created = await create.json() as { id: string; status: string; version: number };
    expect(created.status).toBe("DRAFT");
    expect(created.id).toBeTruthy();
    const id = created.id;
    let version = created.version;

    // Authenticated reads use server-resolved tenant identity.
    let state = await getRun(request, loginData.accessToken, id);
    expect(state.status).toBe("DRAFT");

    // A second authenticated tenant cannot read the first tenant's payroll.
    const foreignContext = await browser.newContext();
    try {
      const foreignPage = await foreignContext.newPage();
      const foreign = await login(foreignPage, required("G4_FOREIGN_EMAIL"), required("G4_FOREIGN_PASSWORD"));
      expect(foreign.user.tenantId).not.toBe(loginData.user.tenantId);
      const denied = await request.get(`${API}/runs/${id}`, {
        headers: { Authorization: `Bearer ${foreign.accessToken}` },
      });
      expect([403, 404]).toContain(denied.status()); // cross-tenant denial
      const foreignMutation = await request.post(`${API}/runs/${id}/review`, {
        headers: mutationHeaders(foreign.accessToken),
        data: { expectedVersion: version, reason: "foreign tenant probe" },
      });
      expect([403, 404]).toContain(foreignMutation.status());
    } finally {
      await foreignContext.close();
    }

    for (const [action, expected] of [
      ["calculate", "CALCULATED"], ["review", "REVIEWED"], ["approve", "APPROVED"],
    ] as const) {
      const response = await request.post(`${API}/runs/${id}/${action}`, {
        headers: mutationHeaders(loginData.accessToken),
        data: { expectedVersion: version, reason: `G4_T10_${action.toUpperCase()}_ACCEPTANCE` },
      });
      expect(response.status(), `${action} must be an authorized successful mutation`).toBe(200);
      state = await getRun(request, loginData.accessToken, id);
      expect(state.status).toBe(expected);
      expect(state.version).toBeGreaterThan(version);
      version = state.version;
    }

    const exportResponse = await request.post(`${API}/runs/${id}/export`, {
      headers: mutationHeaders(loginData.accessToken),
      data: { expectedVersion: version },
    });
    // Accounting owns journals; absent adapter must fail closed, not simulate export.
    expect([200, 503]).toContain(exportResponse.status());
    if (exportResponse.status() === 503) {
      state = await getRun(request, loginData.accessToken, id);
      expect(state.status, "no export state when adapter is unavailable").toBe("APPROVED");
    } else {
      state = await getRun(request, loginData.accessToken, id);
      expect(state.status).toBe("EXPORTED");
    }
    expect(unexpected, "no hidden 403/500 in authenticated HR payroll browser journey").toEqual([]);
  } finally {
    await operator.close();
  }
});
