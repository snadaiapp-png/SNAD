/**
 * Workflow Y2 Playwright Release Gate — E2E scenarios against real
 * Spring Boot + PostgreSQL Direct.
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
  console.log(`Created def: id=${body.id} version=${body.version} versionLock=${body.versionLock}`);
  return { id: body.id, version: body.version, versionLock: body.versionLock };
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

async function createValidWorkflow(
  request: APIRequestContext, codePrefix: string,
): Promise<{ defId: string; version: number }> {
  const def = await createDefinition(request, codePrefix, `E2E ${codePrefix}`);
  const start = await addStep(request, def.id, "start", "START", 1);
  const task = await addStep(request, def.id, "task", "ACTION", 2);
  const end = await addStep(request, def.id, "end", "END", 3);
  const headers = await authHeaders(request);
  for (const t of [
    { from: start.id, to: task.id, key: "begin" },
    { from: task.id, to: end.id, key: "done" },
  ]) {
    const tr = await request.post(
      `${API}/api/v1/workflows/definitions/${def.id}/transitions`,
      { headers, data: { fromStepId: t.from, toStepId: t.to, transitionKey: t.key, outcome: "SUCCESS", priority: 10 } },
    );
    expect(tr.status()).toBe(200);
  }
  return { defId: def.id, versionLock: def.versionLock };
}

test("E2E auth smoke", async ({ request }: { request: APIRequestContext }) => {
  const token = await login(request);
  expect(token.length).toBeGreaterThan(20);
});

test("create validate publish", async ({ request }: { request: APIRequestContext }) => {
  const wf = await createValidWorkflow(request, `WF-E2E-VAL-${Date.now()}`);
  const v = await request.post(
    `${API}/api/v1/workflows/definitions/${wf.defId}/validate`,
    { headers: await authHeaders(request), data: {} },
  );
  expect(v.status()).toBe(200);
  expect((await v.json()).valid).toBe(true);
  const pub = await request.post(
    `${API}/api/v1/workflows/definitions/${wf.defId}/publish`,
    { headers: await authHeaders(request), data: { expectedVersion: wf.versionLock } },
  );
  expect(pub.status()).toBe(200);
  const pubBody = await pub.json(); expect(pubBody.publicationState).toBe("PUBLISHED"); expect(pubBody.engineGeneration).toBe("Y2");
});

test("start instance on published definition", async ({ request }: { request: APIRequestContext }) => {
  const wf = await createValidWorkflow(request, `WF-E2E-START-${Date.now()}`);
  const pub = await request.post(
    `${API}/api/v1/workflows/definitions/${wf.defId}/publish`,
    { headers: await authHeaders(request), data: { expectedVersion: wf.versionLock } },
  );
  expect(pub.status()).toBe(200);
  const res = await request.post(`${API}/api/v1/workflows/instances`, {
    headers: await authHeaders(request),
    data: {
      workflowDefinitionId: wf.defId, workflowVersion: wf.version,
      businessEntityType: "E2E", businessEntityId: crypto.randomUUID(), firstStepKey: "start",
    },
  });
  expect(res.status()).toBe(200);
  expect((await res.json()).id).toBeTruthy();
});

test("my work items returns list", async ({ request }: { request: APIRequestContext }) => {
  const res = await request.get(`${API}/api/v1/workflows/work-items/mine`, {
    headers: await authHeaders(request),
  });
  expect(res.status()).toBe(200);
  expect(Array.isArray(await res.json())).toBe(true);
});

test("pool returns list", async ({ request }: { request: APIRequestContext }) => {
  const res = await request.get(`${API}/api/v1/workflows/work-items/pool`, {
    headers: await authHeaders(request),
  });
  expect(res.status()).toBe(200);
  expect(Array.isArray(await res.json())).toBe(true);
});

test("incidents returns list", async ({ request }: { request: APIRequestContext }) => {
  const res = await request.get(`${API}/api/v1/workflows/incidents`, {
    headers: await authHeaders(request),
  });
  expect(res.status()).toBe(200);
  expect(Array.isArray(await res.json())).toBe(true);
});

test("validation rejects graph without start", async ({ request }: { request: APIRequestContext }) => {
  const def = await createDefinition(request, `WF-E2E-BAD-${Date.now()}`, "E2E Invalid");
  await addStep(request, def.id, "end", "END", 1);
  const v = await request.post(
    `${API}/api/v1/workflows/definitions/${def.id}/validate`,
    { headers: await authHeaders(request), data: {} },
  );
  expect(v.status()).toBe(200);
  const body = await v.json();
  expect(body.valid).toBe(false);
  expect(body.errors.length).toBeGreaterThan(0);
});

test("workflow page loads", async ({ page }: { page: Page }) => {
  await page.goto(`${BASE_URL}/workflow`, { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(2000);
  expect(await page.textContent("body")).toBeTruthy();
});
