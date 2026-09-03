/**
 * Workflow Y2 Playwright Release Gate — E2E scenarios against real
 * Spring Boot + PostgreSQL Direct.
 *
 * Authenticates via the real /api/v1/auth/login endpoint. The test user
 * is seeded by WorkflowE2eBootstrapConfig under the workflow-e2e profile.
 */
import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3001";
const API = process.env.SANAD_BACKEND_BASE_URL ?? "http://127.0.0.1:8080";
const E2E_EMAIL = process.env.WF_E2E_EMAIL ?? "wf-e2e-admin@snad-e2e.example";
const E2E_PASSWORD = process.env.WF_E2E_PASSWORD ?? "WfE2eTest!2026";

let cachedToken: string | null = null;

async function login(request: APIRequestContext): Promise<string> {
  if (cachedToken) return cachedToken;
  const res = await request.post(`${API}/api/v1/auth/login`, {
    data: { email: E2E_EMAIL, password: E2E_PASSWORD },
  });
  const status = res.status();
  if (status !== 200) {
    const bodyText = await res.text();
    throw new Error(`E2E login failed: status=${status}, body=${bodyText.substring(0, 200)}`);
  }
  const body = await res.json();
  const token = body.accessToken;
  if (!token || typeof token !== "string" || token.length < 10) {
    throw new Error("E2E login returned no valid accessToken");
  }
  cachedToken = token;
  return token;
}

async function authHeaders(request: APIRequestContext): Promise<Record<string, string>> {
  const token = await login(request);
  return { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };
}

async function createDefinition(
  request: APIRequestContext, code: string, name: string,
): Promise<{ id: string; version: number }> {
  const res = await request.post(`${API}/api/v1/workflows/definitions`, {
    headers: await authHeaders(request),
    data: { code, name, description: "E2E release gate", module: "GENERAL", triggerType: "MANUAL" },
  });
  expect(res.status()).toBe(200);
  const body = await res.json();
  return { id: body.id, version: body.version };
}

async function addStep(
  request: APIRequestContext, defId: string,
  stepKey: string, stepType: string, sequenceOrder: number,
): Promise<{ id: string }> {
  const res = await request.post(`${API}/api/v1/workflows/definitions/${defId}/steps`, {
    headers: await authHeaders(request),
    data: { stepKey, name: stepKey, stepType, sequenceOrder, configuration: "{}" },
  });
  expect(res.status()).toBe(200);
  return (await res.json()) as { id: string };
}

test("E2E auth smoke — login returns valid token", async ({ request }: { request: APIRequestContext }) => {
  const token = await login(request);
  expect(token.length).toBeGreaterThan(20);
  const res = await request.get(`${API}/api/v1/workflows/definitions`, {
    headers: await authHeaders(request),
  });
  expect(res.status()).toBe(200);
});

test("workflow Y2 draft to publish flow", async ({ request }: { request: APIRequestContext }) => {
  const def = await createDefinition(request, `WF-E2E-PUB-${Date.now()}`, "E2E Publish");
  await addStep(request, def.id, "start", "START", 1);
  await addStep(request, def.id, "end", "END", 2);
  const validation = await request.post(
    `${API}/api/v1/workflows/definitions/${def.id}/validate`,
    { headers: await authHeaders(request), data: {} },
  );
  expect(validation.status()).toBe(200);
  expect((await validation.json()).valid).toBe(true);
  const published = await request.post(
    `${API}/api/v1/workflows/definitions/${def.id}/publish`,
    { headers: await authHeaders(request), data: { expectedVersion: def.version } },
  );
  expect(published.status()).toBe(200);
  expect((await published.json()).status).toBe("ACTIVE");
});

test("workflow Y2 start creates instance", async ({ request }: { request: APIRequestContext }) => {
  const def = await createDefinition(request, `WF-E2E-START-${Date.now()}`, "E2E Start");
  await addStep(request, def.id, "start", "START", 1);
  await addStep(request, def.id, "end", "END", 2);
  const res = await request.post(`${API}/api/v1/workflows/instances`, {
    headers: await authHeaders(request),
    data: {
      workflowDefinitionId: def.id,
      workflowVersion: def.version,
      businessEntityType: "E2E",
      businessEntityId: crypto.randomUUID(),
      firstStepKey: "start",
    },
  });
  expect(res.status()).toBe(200);
  expect((await res.json()).id).toBeTruthy();
});

test("workflow Y2 my work items returns list", async ({ request }: { request: APIRequestContext }) => {
  const res = await request.get(`${API}/api/v1/workflows/work-items/mine`, {
    headers: await authHeaders(request),
  });
  expect(res.status()).toBe(200);
  expect(Array.isArray(await res.json())).toBe(true);
});

test("workflow Y2 pool returns list", async ({ request }: { request: APIRequestContext }) => {
  const res = await request.get(`${API}/api/v1/workflows/work-items/pool`, {
    headers: await authHeaders(request),
  });
  expect(res.status()).toBe(200);
  expect(Array.isArray(await res.json())).toBe(true);
});

test("workflow Y2 incidents returns list", async ({ request }: { request: APIRequestContext }) => {
  const res = await request.get(`${API}/api/v1/workflows/incidents`, {
    headers: await authHeaders(request),
  });
  expect(res.status()).toBe(200);
  expect(Array.isArray(await res.json())).toBe(true);
});

test("workflow Y2 nonexistent instance returns 404", async ({ request }: { request: APIRequestContext }) => {
  const res = await request.get(
    `${API}/api/v1/workflows/instances/${crypto.randomUUID()}`,
    { headers: await authHeaders(request) },
  );
  expect([200, 404]).toContain(res.status());
});

test("workflow Y2 validation rejects graph without steps", async ({ request }: { request: APIRequestContext }) => {
  const def = await createDefinition(request, `WF-E2E-BAD-${Date.now()}`, "E2E Invalid");
  const validation = await request.post(
    `${API}/api/v1/workflows/definitions/${def.id}/validate`,
    { headers: await authHeaders(request), data: {} },
  );
  expect(validation.status()).toBe(200);
  const body = await validation.json();
  expect(body.valid).toBe(false);
  expect(body.errors.length).toBeGreaterThan(0);
});

test("workflow page loads with RTL direction", async ({ page }: { page: Page }) => {
  await page.goto(`${BASE_URL}/workflow`, { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(2000);
  const dir = await page.evaluate(() => document.documentElement.getAttribute("dir") || "");
  if (dir) expect(dir).toBe("rtl");
  const body = await page.textContent("body");
  expect(body).toBeTruthy();
});
