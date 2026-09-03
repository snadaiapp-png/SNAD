/**
 * Workflow Y2 Playwright Release Gate — P01..P13 semantic matrix.
 *
 * Real Spring Boot + PostgreSQL Direct + Next.js. No mock backend.
 * Auth via /api/v1/auth/login using WorkflowE2eBootstrapConfig-seeded
 * multi-actor fixtures. Every scenario exercises the real product contract:
 * no random nonexistent resource IDs as semantic proof, no conditional
 * assertions, no acceptance of 200/500 where the contract says fail-closed.
 */
import { expect, test, type APIRequestContext, type APIResponse, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3001";
const API = process.env.SANAD_BACKEND_BASE_URL ?? "http://127.0.0.1:8080";
const E2E_PASSWORD = process.env.WF_E2E_PASSWORD ?? "WfE2eTest!2026";

/* ════════════════ Multi-actor fixture (task §9) ════════════════ */

const ACTORS = {
  ADMIN: "wf-e2e-admin@snad-e2e.example",
  DESIGNER: "wf-e2e-designer@snad-e2e.example",
  PUBLISHER: "wf-e2e-publisher@snad-e2e.example",
  EMPLOYEE_1: "wf-e2e-employee-1@snad-e2e.example",
  EMPLOYEE_2: "wf-e2e-employee-2@snad-e2e.example",
  APPROVER_1: "wf-e2e-approver-1@snad-e2e.example",
  APPROVER_2: "wf-e2e-approver-2@snad-e2e.example",
  REASSIGNER: "wf-e2e-reassigner@snad-e2e.example",
  INCIDENT_MANAGER: "wf-e2e-incident-manager@snad-e2e.example",
  TENANT_B_EMPLOYEE: "wf-e2e-tenant-b-employee@snad-e2e.example",
} as const;
type ActorEmail = (typeof ACTORS)[keyof typeof ACTORS];

/** Employee numbers are deterministic: local part upper-cased + "-E". */
function employeeNumberFor(email: string): string {
  return `${email.split("@")[0].toUpperCase()}-E`;
}

/* ════════════════ Identity-aware token cache ════════════════ */

const tokenCache = new Map<string, string>();

async function loginAs(request: APIRequestContext, email: string, password = E2E_PASSWORD): Promise<string> {
  const res = await request.post(`${API}/api/v1/auth/login`, {
    data: { email, password },
  });
  const status = res.status();
  if (status !== 200) {
    const body = await res.text();
    throw new Error(`Login failed for ${email}: status=${status} body=${body.substring(0, 200)}`);
  }
  const json = await res.json();
  const token = json.accessToken;
  if (!token || token.length < 10) throw new Error(`Login returned no valid accessToken for ${email}`);
  return token;
}

/** Cached login — one real token per actor identity, never shared. */
async function tokenFor(request: APIRequestContext, email: string): Promise<string> {
  const cached = tokenCache.get(email);
  if (cached) return cached;
  const token = await loginAs(request, email);
  tokenCache.set(email, token);
  return token;
}

async function headersFor(request: APIRequestContext, email: string): Promise<Record<string, string>> {
  return { Authorization: `Bearer ${await tokenFor(request, email)}`, "Content-Type": "application/json" };
}

async function postAs(
  request: APIRequestContext, email: string, path: string, data?: unknown,
): Promise<APIResponse> {
  return request.post(`${API}${path}`, { headers: await headersFor(request, email), data: data ?? {} });
}

async function getAs(request: APIRequestContext, email: string, path: string): Promise<APIResponse> {
  return request.get(`${API}${path}`, { headers: await headersFor(request, email) });
}

/** One-shot login used when the test must prove authentication itself. */
async function rawLogin(request: APIRequestContext, email: string, password = E2E_PASSWORD) {
  return request.post(`${API}/api/v1/auth/login`, { data: { email, password } });
}

/* ════════════════ Fixture registry: employee + tenant ids ════════════════ */

const employeeIdCache = new Map<string, string>();

async function employeeIdFor(request: APIRequestContext, email: string): Promise<string> {
  const cached = employeeIdCache.get(email);
  if (cached) return cached;
  const res = await getAs(request, ACTORS.ADMIN, "/api/v1/hr/employees?limit=200");
  expect(res.status(), "HR employee directory must be readable by the E2E admin").toBe(200);
  const employees = (await res.json()) as Array<{ employeeNumber: string; id: string; userId: string | null }>;
  const wanted = employeeNumberFor(email);
  const match = employees.find((e) => e.employeeNumber === wanted);
  if (!match) {
    throw new Error(`Fixture employee ${wanted} (${email}) not found in HR directory; ` +
      `seeded=[${employees.map((e) => e.employeeNumber).join(",")}]`);
  }
  employeeIdCache.set(email, match.id);
  return match.id;
}

async function employeeUserIdFor(request: APIRequestContext, email: string): Promise<string> {
  const res = await getAs(request, ACTORS.ADMIN, "/api/v1/hr/employees?limit=200");
  expect(res.status()).toBe(200);
  const employees = (await res.json()) as Array<{ employeeNumber: string; userId: string | null }>;
  const match = employees.find((e) => e.employeeNumber === employeeNumberFor(email));
  if (!match?.userId) throw new Error(`Fixture employee for ${email} has no linked user`);
  return match.userId;
}

let tenantACache: string | null = null;
async function tenantAId(request: APIRequestContext): Promise<string> {
  if (tenantACache) return tenantACache;
  const res = await rawLogin(request, ACTORS.ADMIN);
  expect(res.status()).toBe(200);
  const body = (await res.json()) as { user: { tenantId: string } };
  tenantACache = body.user.tenantId;
  return tenantACache;
}

/* ════════════════ Definition / graph builders ════════════════ */

interface StepSpec {
  key: string;
  type: string;
  config?: Record<string, unknown>;
  capability?: string;
  role?: string;
  outcome?: string;
}

interface BuiltDefinition {
  defId: string;
  version: number;
  versionLock: number;
  definitionFamilyId: string;
  engineGeneration: string;
  publicationState: string;
  steps: Record<string, { id: string }>;
}

let codeCounter = 0;

async function buildDraft(
  request: APIRequestContext, email: string, prefix: string, steps: StepSpec[],
): Promise<BuiltDefinition> {
  codeCounter += 1;
  const code = `${prefix}-${Date.now()}-${codeCounter}`;
  const createRes = await postAs(request, email, "/api/v1/workflows/definitions", {
    code, name: `E2E ${prefix}`, description: "Y2 release gate", module: "GENERAL", triggerType: "MANUAL",
  });
  expect(createRes.status(), "definition create must be 200").toBe(200);
  const def = (await createRes.json()) as BuiltDefinition & { id: string };
  const built: BuiltDefinition = {
    defId: def.id, version: def.version, versionLock: def.versionLock,
    definitionFamilyId: def.definitionFamilyId,
    engineGeneration: def.engineGeneration, publicationState: def.publicationState,
    steps: {},
  };
  for (let i = 0; i < steps.length; i++) {
    const s = steps[i];
    const stepRes = await postAs(request, email, `/api/v1/workflows/definitions/${built.defId}/steps`, {
      stepKey: s.key,
      name: s.key,
      stepType: s.type,
      sequenceOrder: i + 1,
      configuration: JSON.stringify(s.config ?? {}),
      ...(s.capability ? { requiredCapability: s.capability } : {}),
      ...(s.role ? { requiredRole: s.role } : {}),
    });
    expect(stepRes.status(), `step ${s.key} add must be 200 while DRAFT`).toBe(200);
    built.steps[s.key] = (await stepRes.json()) as { id: string };
  }
  for (let i = 0; i < steps.length - 1; i++) {
    const transitionRes = await postAs(request, email,
      `/api/v1/workflows/definitions/${built.defId}/transitions`, {
        fromStepId: built.steps[steps[i].key].id,
        toStepId: built.steps[steps[i + 1].key].id,
        transitionKey: steps[i + 1].key,
        outcome: steps[i + 1].outcome ?? "SUCCESS",
        priority: 10,
      });
    expect(transitionRes.status(), `transition into ${steps[i + 1].key} must be 200 while DRAFT`).toBe(200);
  }
  return built;
}

async function publishDefinition(
  request: APIRequestContext, def: BuiltDefinition, expectedVersion?: number,
): Promise<{ publicationState: string; engineGeneration: string; version: number }> {
  const res = await postAs(request, ACTORS.PUBLISHER,
    `/api/v1/workflows/definitions/${def.defId}/publish`, {
      expectedVersion: expectedVersion ?? def.versionLock,
    });
  expect(res.status(), "publish must be 200").toBe(200);
  return (await res.json()) as { publicationState: string; engineGeneration: string; version: number };
}

async function startY2Instance(request: APIRequestContext, email: string, defId: string) {
  const res = await postAs(request, email, "/api/v1/workflows/instances", {
    workflowDefinitionId: defId,
    businessEntityType: "E2E",
    businessEntityId: crypto.randomUUID(),
  });
  return res;
}

interface InstanceMap {
  id: string;
  workflowDefinitionId: string;
  workflowVersion: number;
  status: string;
  currentStepKey: string;
  engineGeneration: string;
  definitionFamilyId: string;
  definitionVersionId: string;
}

interface WorkItemMap {
  id: string;
  workflowInstanceId: string;
  workflowStepInstanceId: string;
  type: string;
  status: string;
  assigneeEmployeeId: string;
  claimedByEmployeeId: string;
  assignmentMode: string;
  title: string;
  version: number;
}

/* ════════════════ P01 — REAL AUTH ════════════════ */

test("P01 — real login returns JWT and every fixture actor accesses protected Workflow endpoints", async ({ request }) => {
  // Real login through the real authentication service for every actor.
  for (const email of Object.values(ACTORS)) {
    const token = await loginAs(request, email);
    expect(token.length, `token minted for ${email}`).toBeGreaterThan(20);
    const res = await getAs(request, email, "/api/v1/workflows/definitions?limit=1");
    expect(res.status(), `protected WORKFLOW.VIEW endpoint works for ${email}`).toBe(200);
  }
});

/* ════════════════ P02 — TRUE DESIGN/PUBLISH LIFECYCLE ════════════════ */

test("P02 — DRAFT → validate → simulate → PUBLISHED with engineGeneration Y2", async ({ request }) => {
  const wf = await buildDraft(request, ACTORS.DESIGNER, "P02", [
    { key: "start", type: "START" },
    { key: "task", type: "ACTION" },
    { key: "end", type: "END" },
  ]);
  expect(wf.publicationState).toBe("DRAFT");
  expect(wf.engineGeneration).toBe("LEGACY"); // create() starts LEGACY; publish flips to Y2

  const v = await postAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/definitions/${wf.defId}/validate`, {});
  expect(v.status()).toBe(200);
  expect(((await v.json()) as { valid: boolean }).valid).toBe(true);

  const sim = await postAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/definitions/${wf.defId}/simulate`, {});
  expect(sim.status()).toBe(200);
  const simBody = (await sim.json()) as { simulated: boolean; valid: boolean };
  expect(simBody.simulated).toBe(true);
  expect(simBody.valid).toBe(true);

  const pub = await publishDefinition(request, wf);
  expect(pub.publicationState).toBe("PUBLISHED");
  expect(pub.engineGeneration).toBe("Y2");
});

/* ════════════════ P03 — PUBLISHED IMMUTABILITY (fail-closed) ════════════════ */

test("P03 — published definition rejects graph mutations (409) and next-draft chains the family", async ({ request }) => {
  const wf = await buildDraft(request, ACTORS.DESIGNER, "P03", [
    { key: "start", type: "START" },
    { key: "task", type: "ACTION" },
    { key: "end", type: "END" },
  ]);
  await publishDefinition(request, wf);

  // addStep on a PUBLISHED version must fail closed — 409 exactly.
  // A 200 or 500 here is a product defect; never accepted by this gate.
  const stepRes = await postAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/definitions/${wf.defId}/steps`, {
      stepKey: "extra", name: "Extra", stepType: "ACTION", sequenceOrder: 99, configuration: "{}",
    });
  expect(stepRes.status(), "addStep on PUBLISHED definition must be rejected").toBe(409);

  // addTransition on a PUBLISHED version must fail closed — 409 exactly.
  const trRes = await postAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/definitions/${wf.defId}/transitions`, {
      fromStepId: wf.steps.start.id, toStepId: wf.steps.task.id,
      transitionKey: "extra-transition", outcome: "SUCCESS", priority: 10,
    });
  expect(trRes.status(), "addTransition on PUBLISHED definition must be rejected").toBe(409);

  // The published graph is provably unchanged.
  const stepsRes = await getAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/definitions/${wf.defId}/steps`);
  expect(stepsRes.status()).toBe(200);
  expect(((await stepsRes.json()) as unknown[]).length).toBe(3);

  // Next draft chains the family with an incremented version.
  const draftRes = await postAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/definitions/${wf.defId}/next-draft`, {});
  expect(draftRes.status()).toBe(200);
  const draft = (await draftRes.json()) as {
    id: string; version: number; publicationState: string; definitionFamilyId: string;
  };
  expect(draft.id).not.toBe(wf.defId);
  expect(draft.version).toBe(2);
  expect(draft.publicationState).toBe("DRAFT");
  expect(draft.definitionFamilyId).toBe(wf.definitionFamilyId);

  // Reload published version — still PUBLISHED.
  const reload = await getAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/definitions/${wf.defId}`);
  expect(reload.status()).toBe(200);
  expect(((await reload.json()) as { publicationState: string }).publicationState).toBe("PUBLISHED");
});

/* ════════════════ P04 — EXACT Y2 VERSION PINNING ════════════════ */

test("P04 — running instance pins its exact version; newer family publish never repins it", async ({ request }) => {
  const wf = await buildDraft(request, ACTORS.DESIGNER, "P04", [
    { key: "start", type: "START" },
    { key: "task", type: "ACTION" },
    { key: "end", type: "END" },
  ]);
  const published = await publishDefinition(request, wf);
  expect(published.engineGeneration).toBe("Y2");

  const startRes = await startY2Instance(request, ACTORS.DESIGNER, wf.defId);
  expect(startRes.status(), "Y2 start must be 200").toBe(200);
  const instance = (await startRes.json()) as InstanceMap;

  // Unconditional version pins on the runtime model — every exposed field.
  expect(instance.workflowDefinitionId).toBe(wf.defId);
  expect(instance.workflowVersion).toBe(1);
  expect(instance.engineGeneration).toBe("Y2");
  expect(instance.definitionFamilyId).toBe(wf.definitionFamilyId);
  expect(instance.definitionVersionId).toBe(wf.defId);

  // Publish a newer concrete version in the SAME family.
  const draftRes = await postAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/definitions/${wf.defId}/next-draft`, {});
  expect(draftRes.status()).toBe(200);
  const draftBody = (await draftRes.json()) as BuiltDefinition & { id: string };
  const v2: BuiltDefinition = {
    defId: draftBody.id, version: draftBody.version, versionLock: draftBody.versionLock,
    definitionFamilyId: draftBody.definitionFamilyId, engineGeneration: draftBody.engineGeneration,
    publicationState: draftBody.publicationState, steps: {},
  };
  for (let i = 0; i < 3; i++) {
    const keys = ["start", "task", "end"];
    const types = ["START", "ACTION", "END"];
    const stepRes = await postAs(request, ACTORS.DESIGNER,
      `/api/v1/workflows/definitions/${v2.defId}/steps`, {
        stepKey: keys[i], name: keys[i], stepType: types[i], sequenceOrder: i + 1, configuration: "{}",
      });
    expect(stepRes.status()).toBe(200);
    v2.steps[keys[i]] = (await stepRes.json()) as { id: string };
  }
  const t1 = await postAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/definitions/${v2.defId}/transitions`, {
      fromStepId: v2.steps.start.id, toStepId: v2.steps.task.id,
      transitionKey: "task", outcome: "SUCCESS", priority: 10,
    });
  expect(t1.status()).toBe(200);
  const t2 = await postAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/definitions/${v2.defId}/transitions`, {
      fromStepId: v2.steps.task.id, toStepId: v2.steps.end.id,
      transitionKey: "end", outcome: "SUCCESS", priority: 10,
    });
  expect(t2.status()).toBe(200);
  const v2pub = await publishDefinition(request, v2);
  expect(v2pub.publicationState).toBe("PUBLISHED");
  expect(v2pub.engineGeneration).toBe("Y2");

  // The original running instance keeps every pin unchanged.
  const reloadRes = await getAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/instances/${instance.id}`);
  expect(reloadRes.status()).toBe(200);
  const reloaded = (await reloadRes.json()) as InstanceMap;
  expect(reloaded.workflowDefinitionId).toBe(wf.defId);
  expect(reloaded.workflowVersion).toBe(1);
  expect(reloaded.engineGeneration).toBe("Y2");
  expect(reloaded.definitionFamilyId).toBe(wf.definitionFamilyId);
  expect(reloaded.definitionVersionId).toBe(wf.defId);

  // A NEW instance started against the newer published version uses it.
  const start2Res = await startY2Instance(request, ACTORS.DESIGNER, v2.defId);
  expect(start2Res.status()).toBe(200);
  const instance2 = (await start2Res.json()) as InstanceMap;
  expect(instance2.workflowDefinitionId).toBe(v2.defId);
  expect(instance2.workflowVersion).toBe(2);
  expect(instance2.definitionVersionId).toBe(v2.defId);
  expect(instance2.definitionFamilyId).toBe(wf.definitionFamilyId);
});

/* ════════════════ P05 — REAL DIRECT HUMAN_TASK ════════════════ */

test("P05 — DIRECT HUMAN_TASK is generated, assigned, completed, and advances the instance", async ({ request }) => {
  const emp1EmployeeId = await employeeIdFor(request, ACTORS.EMPLOYEE_1);
  const wf = await buildDraft(request, ACTORS.DESIGNER, "P05", [
    { key: "start", type: "START" },
    { key: "human-task", type: "HUMAN_TASK", config: { assigneeEmployeeId: emp1EmployeeId } },
    { key: "end", type: "END" },
  ]);
  await publishDefinition(request, wf);

  const startRes = await startY2Instance(request, ACTORS.DESIGNER, wf.defId);
  expect(startRes.status(), "start must activate the first real step").toBe(200);
  const instance = (await startRes.json()) as InstanceMap;

  // The assigned Employee account finds the exact WorkItem in My Tasks.
  const mineRes = await getAs(request, ACTORS.EMPLOYEE_1, "/api/v1/workflows/work-items/mine");
  expect(mineRes.status()).toBe(200);
  const mine = (await mineRes.json()) as WorkItemMap[];
  const item = mine.find((w) => w.workflowInstanceId === instance.id && w.type === "HUMAN_TASK");
  expect(item, "DIRECT work item must exist for the assignee").toBeTruthy();
  expect(item!.assignmentMode).toBe("DIRECT");
  expect(item!.assigneeEmployeeId).toBe(emp1EmployeeId);
  expect(item!.status).toBe("CLAIMED"); // DIRECT items are immediately assigned by contract

  // Perform the actual supported completion command with optimistic version.
  const completeRes = await postAs(request, ACTORS.EMPLOYEE_1,
    `/api/v1/workflows/work-items/${item!.id}/complete`, {
      expectedVersion: item!.version,
    });
  expect(completeRes.status()).toBe(200);
  const completed = (await completeRes.json()) as WorkItemMap;
  expect(completed.status).toBe("COMPLETED");

  // The workflow instance advanced to the expected terminal state.
  const detailRes = await getAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/instances/${instance.id}`);
  expect(detailRes.status()).toBe(200);
  const detail = (await detailRes.json()) as InstanceMap;
  expect(detail.status).toBe("COMPLETED");
});

/* ════════════════ P06 — REAL WORK_POOL CONCURRENCY ════════════════ */

test("P06 — concurrent WORK_POOL claims: exactly one wins, one loses with 409, no double ownership", async ({ request }) => {
  const emp1EmployeeId = await employeeIdFor(request, ACTORS.EMPLOYEE_1);
  const emp2EmployeeId = await employeeIdFor(request, ACTORS.EMPLOYEE_2);
  const wf = await buildDraft(request, ACTORS.DESIGNER, "P06", [
    { key: "start", type: "START" },
    { key: "pooled-task", type: "HUMAN_TASK", capability: "WORKFLOW.TASK_EXECUTE" },
    { key: "end", type: "END" },
  ]);
  await publishDefinition(request, wf);

  const startRes = await startY2Instance(request, ACTORS.DESIGNER, wf.defId);
  expect(startRes.status()).toBe(200);
  const instance = (await startRes.json()) as InstanceMap;

  // Both eligible employees see the item in the pool.
  const pool1Res = await getAs(request, ACTORS.EMPLOYEE_1, "/api/v1/workflows/work-items/pool");
  expect(pool1Res.status()).toBe(200);
  const item1 = ((await pool1Res.json()) as WorkItemMap[])
    .find((w) => w.workflowInstanceId === instance.id);
  expect(item1, "employee 1 must see the pool item").toBeTruthy();
  expect(item1!.assignmentMode).toBe("WORK_POOL");
  expect(item1!.status).toBe("AVAILABLE");
  const pool2Res = await getAs(request, ACTORS.EMPLOYEE_2, "/api/v1/workflows/work-items/pool");
  expect(pool2Res.status()).toBe(200);
  const item2 = ((await pool2Res.json()) as WorkItemMap[])
    .find((w) => w.id === item1!.id);
  expect(item2, "employee 2 must see the same pool item").toBeTruthy();

  // Two synchronized claim requests: SAME workItemId, SAME expectedVersion.
  const claimBody = { expectedVersion: item1!.version, reason: "P06 concurrency race" };
  const [r1, r2] = await Promise.all([
    postAs(request, ACTORS.EMPLOYEE_1, `/api/v1/workflows/work-items/${item1!.id}/claim`, claimBody),
    postAs(request, ACTORS.EMPLOYEE_2, `/api/v1/workflows/work-items/${item1!.id}/claim`, claimBody),
  ]);
  const statuses = [r1.status(), r2.status()].sort();
  expect(statuses, "exactly one 200 and exactly one 409").toEqual([200, 409]);

  const winnerEmail = r1.status() === 200 ? ACTORS.EMPLOYEE_1 : ACTORS.EMPLOYEE_2;
  const winnerEmployeeId = winnerEmail === ACTORS.EMPLOYEE_1 ? emp1EmployeeId : emp2EmployeeId;

  // No double ownership: the database ends with exactly one claimant.
  const mineRes = await getAs(request, winnerEmail, "/api/v1/workflows/work-items/mine");
  expect(mineRes.status()).toBe(200);
  const owned = ((await mineRes.json()) as WorkItemMap[]).find((w) => w.id === item1!.id);
  expect(owned, "winner owns the item").toBeTruthy();
  expect(owned!.claimedByEmployeeId).toBe(winnerEmployeeId);
  expect(owned!.status).toBe("CLAIMED");

  // Winner completes; the graph advances to END and completes the instance.
  const completeRes = await postAs(request, winnerEmail,
    `/api/v1/workflows/work-items/${item1!.id}/complete`, {
      expectedVersion: owned!.version,
    });
  expect(completeRes.status()).toBe(200);
  const detailRes = await getAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/instances/${instance.id}`);
  expect(detailRes.status()).toBe(200);
  expect(((await detailRes.json()) as InstanceMap).status).toBe("COMPLETED");
});

/* ════════════════ P07 — REAL ANY_ONE APPROVAL ════════════════ */

test("P07 — ANY_ONE approval: first approval closes the step, siblings cancelled, no second approval", async ({ request }) => {
  const wf = await buildDraft(request, ACTORS.DESIGNER, "P07", [
    { key: "start", type: "START" },
    { key: "signoff", type: "APPROVAL", role: "E2E_APPROVER", config: { approvalPolicy: "ANY_ONE" } },
    { key: "end", type: "END" },
  ]);
  await publishDefinition(request, wf);

  const startRes = await startY2Instance(request, ACTORS.DESIGNER, wf.defId);
  expect(startRes.status()).toBe(200);
  const instance = (await startRes.json()) as InstanceMap;

  // Both eligible approvers have a real approval opportunity addressed to them.
  const pending1Res = await getAs(request, ACTORS.APPROVER_1, "/api/v1/workflows/approvals/pending");
  expect(pending1Res.status()).toBe(200);
  const request1 = ((await pending1Res.json()) as ApprovalMap[])
    .find((a) => a.workflowInstanceId === instance.id);
  expect(request1, "approver 1 must hold a pending approval").toBeTruthy();
  expect(request1!.status).toBe("PENDING");
  const pending2Res = await getAs(request, ACTORS.APPROVER_2, "/api/v1/workflows/approvals/pending");
  expect(pending2Res.status()).toBe(200);
  const request2 = ((await pending2Res.json()) as ApprovalMap[])
    .find((a) => a.workflowInstanceId === instance.id);
  expect(request2, "approver 2 must hold a pending approval too").toBeTruthy();
  expect(request2!.id).not.toBe(request1!.id);

  // Approver 1 approves — ANY_ONE closes the step and the workflow advances.
  const approveRes = await postAs(request, ACTORS.APPROVER_1,
    `/api/v1/workflows/approvals/${request1!.id}/approve`, { comments: "P07 ANY_ONE approval" });
  expect(approveRes.status()).toBe(200);
  expect(((await approveRes.json()) as ApprovalMap).decision).toBe("APPROVED");

  const detailRes = await getAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/instances/${instance.id}`);
  expect(detailRes.status()).toBe(200);
  expect(((await detailRes.json()) as InstanceMap).status).toBe("COMPLETED");

  // Approver 2 cannot produce a second effective approval: the sibling
  // request was cancelled by the ANY_ONE policy — a second approve is 409.
  const lateRes = await postAs(request, ACTORS.APPROVER_2,
    `/api/v1/workflows/approvals/${request2!.id}/approve`, { comments: "too late" });
  expect(lateRes.status(), "second effective approval must not be possible").toBe(409);
  const latePendingRes = await getAs(request, ACTORS.APPROVER_2, "/api/v1/workflows/approvals/pending");
  expect(latePendingRes.status()).toBe(200);
  expect(((await latePendingRes.json()) as ApprovalMap[])
    .find((a) => a.id === request2!.id)).toBeUndefined();
});

/* ════════════════ P08 — REAL ALL APPROVAL (success + rejection) ════════════════ */

test("P08 — ALL approval: advance only after unanimity; reject-with-reason routes the REJECTED path", async ({ request }) => {
  // SUCCESS PATH: every approver must approve before the workflow advances.
  const successWf = await buildDraft(request, ACTORS.DESIGNER, "P08-ALL", [
    { key: "start", type: "START" },
    { key: "signoff", type: "APPROVAL", role: "E2E_APPROVER", config: { approvalPolicy: "ALL" } },
    { key: "end", type: "END" },
  ]);
  await publishDefinition(request, successWf);
  const startRes = await startY2Instance(request, ACTORS.DESIGNER, successWf.defId);
  expect(startRes.status()).toBe(200);
  const successInstance = (await startRes.json()) as InstanceMap;

  const approvals1 = ((await (await getAs(request, ACTORS.APPROVER_1,
    "/api/v1/workflows/approvals/pending")).json()) as ApprovalMap[]);
  const first = approvals1.find((a) => a.workflowInstanceId === successInstance.id);
  expect(first).toBeTruthy();
  const approvals2 = ((await (await getAs(request, ACTORS.APPROVER_2,
    "/api/v1/workflows/approvals/pending")).json()) as ApprovalMap[]);
  const second = approvals2.find((a) => a.workflowInstanceId === successInstance.id);
  expect(second).toBeTruthy();

  const approve1Res = await postAs(request, ACTORS.APPROVER_1,
    `/api/v1/workflows/approvals/${first!.id}/approve`, { comments: "P08 first approval" });
  expect(approve1Res.status()).toBe(200);

  // Unanimity not yet reached — the workflow must NOT advance yet.
  const midRes = await getAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/instances/${successInstance.id}`);
  expect(midRes.status()).toBe(200);
  const midState = (await midRes.json()) as InstanceMap;
  expect(midState.status).toBe("RUNNING");
  expect(midState.currentStepKey).toBe("signoff");

  const approve2Res = await postAs(request, ACTORS.APPROVER_2,
    `/api/v1/workflows/approvals/${second!.id}/approve`, { comments: "P08 second approval" });
  expect(approve2Res.status()).toBe(200);
  const doneRes = await getAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/instances/${successInstance.id}`);
  expect(doneRes.status()).toBe(200);
  expect(((await doneRes.json()) as InstanceMap).status).toBe("COMPLETED");

  // REJECTION PATH: reject without reason is rejected (400); a reasoned
  // reject follows the definition's REJECTED outcome transition to END.
  const rejectWf = await buildDraft(request, ACTORS.DESIGNER, "P08-REJ", [
    { key: "start", type: "START" },
    { key: "signoff", type: "APPROVAL", role: "E2E_APPROVER", config: { approvalPolicy: "ALL" } },
    { key: "rejected-end", type: "END", outcome: "REJECTED" },
  ]);
  await publishDefinition(request, rejectWf);
  const rejStartRes = await startY2Instance(request, ACTORS.DESIGNER, rejectWf.defId);
  expect(rejStartRes.status()).toBe(200);
  const rejectInstance = (await rejStartRes.json()) as InstanceMap;

  const rejApprovals = ((await (await getAs(request, ACTORS.APPROVER_1,
    "/api/v1/workflows/approvals/pending")).json()) as ApprovalMap[]);
  const rejFirst = rejApprovals.find((a) => a.workflowInstanceId === rejectInstance.id);
  expect(rejFirst).toBeTruthy();

  const blankRes = await postAs(request, ACTORS.APPROVER_1,
    `/api/v1/workflows/approvals/${rejFirst!.id}/reject`, { comments: "" });
  expect(blankRes.status(), "reject without reason must be rejected with 400").toBe(400);

  const rejectRes = await postAs(request, ACTORS.APPROVER_1,
    `/api/v1/workflows/approvals/${rejFirst!.id}/reject`,
    { comments: "Rejected: business case does not meet policy" });
  expect(rejectRes.status()).toBe(200);
  const rejDoneRes = await getAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/instances/${rejectInstance.id}`);
  expect(rejDoneRes.status()).toBe(200);
  expect(((await rejDoneRes.json()) as InstanceMap).status).toBe("COMPLETED");
});

/* ════════════════ P09 — DISABLED USER / B1 SEMANTICS ════════════════ */

test("P09 — disabled user cannot act; work preserved without auto-transfer; explicit reassign completes", async ({ request }) => {
  const emp1EmployeeId = await employeeIdFor(request, ACTORS.EMPLOYEE_1);
  const emp2EmployeeId = await employeeIdFor(request, ACTORS.EMPLOYEE_2);
  const emp1UserId = await employeeUserIdFor(request, ACTORS.EMPLOYEE_1);
  const tenantA = await tenantAId(request);

  const wf = await buildDraft(request, ACTORS.DESIGNER, "P09", [
    { key: "start", type: "START" },
    { key: "human-task", type: "HUMAN_TASK", config: { assigneeEmployeeId: emp1EmployeeId } },
    { key: "end", type: "END" },
  ]);
  await publishDefinition(request, wf);
  const startRes = await startY2Instance(request, ACTORS.DESIGNER, wf.defId);
  expect(startRes.status()).toBe(200);
  const instance = (await startRes.json()) as InstanceMap;

  // Employee 1 (ACTIVE user) can see and would act on the item.
  const preMineRes = await getAs(request, ACTORS.EMPLOYEE_1, "/api/v1/workflows/work-items/mine");
  expect(preMineRes.status()).toBe(200);
  const preItem = ((await preMineRes.json()) as WorkItemMap[])
    .find((w) => w.workflowInstanceId === instance.id);
  expect(preItem).toBeTruthy();
  expect(preItem!.status).toBe("CLAIMED");
  const preLoginToken = await tokenFor(request, ACTORS.EMPLOYEE_1);

  // Disable the USER while the Employee record remains ACTIVE.
  const deactivateRes = await request.patch(
    `${API}/api/v1/users/${emp1UserId}/deactivate?tenantId=${tenantA}`,
    { headers: await headersFor(request, ACTORS.ADMIN) });
  expect(deactivateRes.status(), "admin deactivates the user via the real users API").toBe(200);

  // The disabled user can no longer authenticate.
  const reloginRes = await rawLogin(request, ACTORS.EMPLOYEE_1);
  expect(reloginRes.status(), "disabled user login must be 401").toBe(401);

  // A token minted before the disable can no longer execute work —
  // actionability denies non-ACTIVE users on workflow work commands.
  const staleMineRes = await request.get(`${API}/api/v1/workflows/work-items/mine`, {
    headers: { Authorization: `Bearer ${preLoginToken}` },
  });
  expect(staleMineRes.status(), "disabled user work listing must be denied").toBe(403);

  // The work is preserved: no automatic manager transfer, no silent completion.
  const emp2BeforeRes = await getAs(request, ACTORS.EMPLOYEE_2, "/api/v1/workflows/work-items/mine");
  expect(emp2BeforeRes.status()).toBe(200);
  expect(((await emp2BeforeRes.json()) as WorkItemMap[])
    .find((w) => w.id === preItem!.id)).toBeUndefined();
  const preReassignDetailRes = await getAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/instances/${instance.id}`);
  expect(preReassignDetailRes.status()).toBe(200);
  expect(((await preReassignDetailRes.json()) as InstanceMap).status).toBe("RUNNING");

  // A dedicated REASSIGN actor (not admin bypass) explicitly reassigns.
  const reassignRes = await postAs(request, ACTORS.REASSIGNER,
    `/api/v1/workflows/work-items/${preItem!.id}/reassign`, {
      newAssigneeEmployeeId: emp2EmployeeId,
      expectedVersion: preItem!.version,
      reason: "B1: assignee user disabled; explicit reassignment to eligible employee",
    });
  expect(reassignRes.status()).toBe(200);

  // The new assignee authenticates and completes the work.
  const emp2MineRes = await getAs(request, ACTORS.EMPLOYEE_2, "/api/v1/workflows/work-items/mine");
  expect(emp2MineRes.status()).toBe(200);
  const reassigned = ((await emp2MineRes.json()) as WorkItemMap[])
    .find((w) => w.id === preItem!.id);
  expect(reassigned, "reassigned item must be in the new assignee's My Tasks").toBeTruthy();
  expect(reassigned!.assigneeEmployeeId).toBe(emp2EmployeeId);
  const completeRes = await postAs(request, ACTORS.EMPLOYEE_2,
    `/api/v1/workflows/work-items/${preItem!.id}/complete`, {
      expectedVersion: reassigned!.version,
    });
  expect(completeRes.status()).toBe(200);
  const finalRes = await getAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/instances/${instance.id}`);
  expect(finalRes.status()).toBe(200);
  expect(((await finalRes.json()) as InstanceMap).status).toBe("COMPLETED");
});

/* ════════════════ P10 — REAL INCIDENT LIFECYCLE ════════════════ */

test("P10 — failing SYSTEM_ACTION opens a real incident; lifecycle OPEN → acknowledged → resolved", async ({ request }) => {
  const wf = await buildDraft(request, ACTORS.DESIGNER, "P10", [
    { key: "start", type: "START" },
    { key: "risky-action", type: "SYSTEM_ACTION", config: { adapter: "E2E_ALWAYS_FAIL" } },
    { key: "end", type: "END" },
  ]);
  await publishDefinition(request, wf);

  // Starting the instance runs the deterministic failing adapter; the
  // platform opens a real incident and answers with a controlled 409.
  const startRes = await startY2Instance(request, ACTORS.DESIGNER, wf.defId);
  expect(startRes.status(), "deterministic system-action failure must be a controlled 409").toBe(409);
  const failure = (await startRes.json()) as {
    code: string; incidentId: string; workflowInstanceId: string; failureCategory: string;
  };
  expect(failure.code).toBe("WORKFLOW_SYSTEM_ACTION_FAILED");
  expect(failure.incidentId).toBeTruthy();
  expect(failure.failureCategory).toBe("RETRY_EXHAUSTED");

  // The instance is preserved, RUNNING at the failed step.
  const detailRes = await getAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/instances/${failure.workflowInstanceId}`);
  expect(detailRes.status()).toBe(200);
  expect(((await detailRes.json()) as InstanceMap).status).toBe("RUNNING");

  // The incident manager sees the real OPEN incident.
  const listRes = await getAs(request, ACTORS.INCIDENT_MANAGER, "/api/v1/workflows/incidents");
  expect(listRes.status()).toBe(200);
  const incident = ((await listRes.json()) as IncidentMap[])
    .find((i) => i.id === failure.incidentId);
  expect(incident, "the real incident must be listed").toBeTruthy();
  expect(incident!.status).toBe("OPEN");
  expect(incident!.severity).toBe("HIGH");

  // acknowledge → resolve without reason is rejected → resolve with reason.
  const ackRes = await postAs(request, ACTORS.INCIDENT_MANAGER,
    `/api/v1/workflows/incidents/${incident!.id}/acknowledge`, {});
  expect(ackRes.status()).toBe(200);
  expect(((await ackRes.json()) as IncidentMap).status).toBe("ACKNOWLEDGED");

  const blankResolveRes = await postAs(request, ACTORS.INCIDENT_MANAGER,
    `/api/v1/workflows/incidents/${incident!.id}/resolve`, { resolution: "" });
  expect(blankResolveRes.status(), "resolve without mandatory reason must be 400").toBe(400);

  const resolveRes = await postAs(request, ACTORS.INCIDENT_MANAGER,
    `/api/v1/workflows/incidents/${incident!.id}/resolve`, {
      resolution: "E2E deterministic failure accepted and remediated",
    });
  expect(resolveRes.status()).toBe(200);
  expect(((await resolveRes.json()) as IncidentMap).status).toBe("RESOLVED");

  // Resolving again is a state-machine conflict, not a 500.
  const repeatRes = await postAs(request, ACTORS.INCIDENT_MANAGER,
    `/api/v1/workflows/incidents/${incident!.id}/resolve`, { resolution: "again" });
  expect(repeatRes.status()).toBe(409);
});

/* ════════════════ P11 — REAL LEGACY STRANGLER ════════════════ */

test("P11 — LEGACY instance keeps its generation across Y2 cutover; new instances select Y2", async ({ request }) => {
  const wf = await buildDraft(request, ACTORS.DESIGNER, "P11-LEGACY", [
    { key: "start", type: "START" },
    { key: "task", type: "ACTION" },
    { key: "end", type: "END" },
  ]);
  expect(wf.engineGeneration).toBe("LEGACY");

  // Activate through the real LEGACY lifecycle and start BEFORE cutover.
  const activateRes = await postAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/definitions/${wf.defId}/activate`, {});
  expect(activateRes.status()).toBe(200);
  const legacyStartRes = await startY2Instance(request, ACTORS.DESIGNER, wf.defId);
  expect(legacyStartRes.status()).toBe(200);
  const legacyInstance = (await legacyStartRes.json()) as InstanceMap;
  // Unconditional — never a conditional engineGeneration assertion.
  expect(legacyInstance.engineGeneration).toBe("LEGACY");
  expect(legacyInstance.status).toBe("RUNNING");

  // Publish the Y2 version in the same family (cutover).
  const published = await publishDefinition(request, wf);
  expect(published.engineGeneration).toBe("Y2");

  // The pre-cutover instance remains LEGACY with its definition pin intact.
  const reloadRes = await getAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/instances/${legacyInstance.id}`);
  expect(reloadRes.status()).toBe(200);
  const reloaded = (await reloadRes.json()) as InstanceMap;
  expect(reloaded.engineGeneration).toBe("LEGACY");
  expect(reloaded.definitionVersionId).toBe(wf.defId);

  // The next supported LEGACY command still routes through the legacy runtime.
  const advanceRes = await postAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/instances/${legacyInstance.id}/advance`, { nextStepKey: "task" });
  expect(advanceRes.status()).toBe(200);
  const afterAdvanceRes = await getAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/instances/${legacyInstance.id}`);
  expect(afterAdvanceRes.status()).toBe(200);
  const afterAdvance = (await afterAdvanceRes.json()) as InstanceMap;
  expect(afterAdvance.engineGeneration).toBe("LEGACY");
  expect(afterAdvance.currentStepKey).toBe("task");

  // A post-cutover NEW instance selects Y2.
  const y2StartRes = await startY2Instance(request, ACTORS.DESIGNER, wf.defId);
  expect(y2StartRes.status()).toBe(200);
  expect(((await y2StartRes.json()) as InstanceMap).engineGeneration).toBe("Y2");
});

/* ════════════════ P12 — TRUE CROSS-TENANT ISOLATION ════════════════ */

test("P12 — tenant B is denied every real tenant A workflow resource; tenant A keeps access", async ({ request }) => {
  const emp1EmployeeId = await employeeIdFor(request, ACTORS.EMPLOYEE_1);
  const emp2EmployeeId = await employeeIdFor(request, ACTORS.EMPLOYEE_2);

  // Real tenant A resources: published definition, running instance, live work item.
  const wf = await buildDraft(request, ACTORS.DESIGNER, "P12", [
    { key: "start", type: "START" },
    { key: "pooled-task", type: "HUMAN_TASK", capability: "WORKFLOW.TASK_EXECUTE" },
    { key: "end", type: "END" },
  ]);
  await publishDefinition(request, wf);
  const startRes = await startY2Instance(request, ACTORS.DESIGNER, wf.defId);
  expect(startRes.status()).toBe(200);
  const instance = (await startRes.json()) as InstanceMap;
  const poolRes = await getAs(request, ACTORS.EMPLOYEE_1, "/api/v1/workflows/work-items/pool");
  expect(poolRes.status()).toBe(200);
  const item = ((await poolRes.json()) as WorkItemMap[])
    .find((w) => w.workflowInstanceId === instance.id);
  expect(item).toBeTruthy();

  // Tenant B employee (real authenticated actor in another tenant).
  const denyDefRes = await getAs(request, ACTORS.TENANT_B_EMPLOYEE,
    `/api/v1/workflows/definitions/${wf.defId}`);
  expect(denyDefRes.status(), "cross-tenant definition read must be denied").toBe(404);
  const denyInstanceRes = await getAs(request, ACTORS.TENANT_B_EMPLOYEE,
    `/api/v1/workflows/instances/${instance.id}`);
  expect(denyInstanceRes.status(), "cross-tenant instance read must be denied").toBe(404);
  const denyClaimRes = await postAs(request, ACTORS.TENANT_B_EMPLOYEE,
    `/api/v1/workflows/work-items/${item!.id}/claim`, { expectedVersion: item!.version });
  expect(denyClaimRes.status(), "cross-tenant claim must be denied").toBe(409);
  const denyCompleteRes = await postAs(request, ACTORS.TENANT_B_EMPLOYEE,
    `/api/v1/workflows/work-items/${item!.id}/complete`, { expectedVersion: item!.version });
  expect(denyCompleteRes.status(), "cross-tenant complete must be denied").toBe(409);

  // Tenant A still has full access to the same concrete IDs.
  const ownerDefRes = await getAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/definitions/${wf.defId}`);
  expect(ownerDefRes.status()).toBe(200);
  const ownerInstanceRes = await getAs(request, ACTORS.DESIGNER,
    `/api/v1/workflows/instances/${instance.id}`);
  expect(ownerInstanceRes.status()).toBe(200);
  expect(((await ownerInstanceRes.json()) as InstanceMap).id).toBe(instance.id);
  const emp2PoolRes = await getAs(request, ACTORS.EMPLOYEE_2, "/api/v1/workflows/work-items/pool");
  expect(emp2PoolRes.status()).toBe(200);
  expect(((await emp2PoolRes.json()) as WorkItemMap[])
    .find((w) => w.id === item!.id)).toBeTruthy();
});

/* ════════════════ P13 — REAL APPLICATION RTL / IA / ACCESSIBILITY ════════════════ */

const WORKFLOW_TABS = [
  "نظرة عامة", "التعريفات", "مهامي", "الموافقات",
  "المثيلات", "الحوادث", "المراقبة", "الإعدادات",
];

test("P13 — real application: authenticated Arabic RTL, full operational IA, accessibility", async ({ page }) => {
  // Authenticate through the actual frontend session mechanism (BFF login).
  await page.goto(`${BASE_URL}/`, { waitUntil: "domcontentloaded" });
  await page.locator("#login-email").waitFor({ state: "visible", timeout: 30_000 });

  // Visible error state on failed credentials (before the real login).
  await page.locator("#login-email").fill(ACTORS.DESIGNER);
  await page.locator("#login-password").fill("definitely-wrong-password");
  await page.getByRole("button", { name: /دخول|login|تسجيل/i }).first().click();
  await page.locator('[role="alert"]').first().waitFor({ state: "visible", timeout: 15_000 });

  // Keyboard activation for the primary interaction: Enter submits the form.
  await page.locator("#login-password").fill(E2E_PASSWORD);
  await page.locator("#login-password").press("Enter");
  // The form leaves the screen once authentication succeeds (redirect to the
  // authenticated destination), regardless of the default landing route.
  await page.locator("#login-email").waitFor({ state: "hidden", timeout: 30_000 });

  // Navigate to the real workflow application shell.
  await page.goto(`${BASE_URL}/workflow`, { waitUntil: "domcontentloaded" });
  await page.locator("h1", { hasText: "محرك سير العمل" })
    .waitFor({ state: "visible", timeout: 30_000 });

  // Canonical RTL root contract of the Arabic-first application.
  const dir = await page.evaluate(() => document.documentElement.getAttribute("dir"));
  expect(dir).toBe("rtl");
  const lang = await page.evaluate(() => document.documentElement.getAttribute("lang"));
  expect(lang).toBe("ar");

  // All operational IA destinations reachable through the real rendered UI.
  for (const tab of WORKFLOW_TABS) {
    const button = page.getByRole("button", { name: tab }).first();
    await expect(button, `workflow IA tab ${tab} must exist`).toBeVisible();
    await button.click();
  }

  // Navigation landmark + accessible names exist for the primary nav.
  const navCount = await page.locator("nav, [role='navigation']").count();
  expect(navCount, "navigation landmark must exist").toBeGreaterThan(0);

  // No critical accessibility violations (repo ships axe tooling).
  const axeResults = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa"])
    .analyze();
  const criticalViolations = axeResults.violations.filter((v) => v.impact === "critical");
  expect(
    criticalViolations,
    `critical axe violations: ${criticalViolations.map((v) => v.id).join(",")}`,
  ).toHaveLength(0);
});

interface ApprovalMap {
  id: string;
  workflowInstanceId: string;
  workflowStepInstanceId: string | null;
  requestedFromUserId: string;
  status: string;
  decision: string;
  comments: string;
  version: number;
}

interface IncidentMap {
  id: string;
  workflowInstanceId: string;
  source: string;
  severity: string;
  failureCategory: string;
  status: string;
  resolution: string;
  createdAt: string;
}
