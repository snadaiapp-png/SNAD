/**
 * Workflow Y2 Playwright Release Gate — P01..P13 semantic matrix.
 *
 * Real Spring Boot + PostgreSQL Direct + Next.js. No mock backend.
 * Setup-only fixture endpoints are profile-gated by `workflow-e2e`; every
 * business action is exercised through the normal production API surface.
 */
import { expect, test, type APIRequestContext, type Page } from "@playwright/test";
import { loginThroughUi } from "./crm-auth-session";

const API = process.env.SANAD_BACKEND_BASE_URL ?? "http://127.0.0.1:8080";
const PASSWORD = process.env.WF_E2E_PASSWORD ?? "WfE2eTest!2026";
const DIRECT_ASSIGNMENT = JSON.stringify({ assignment: { type: "DIRECT" } });

const ACTORS = {
  admin: process.env.WF_E2E_EMAIL ?? "wf-e2e-admin@snad-e2e.example",
  execA: "wf-e2e-exec-a@snad-e2e.example",
  execB: "wf-e2e-exec-b@snad-e2e.example",
  approverA: "wf-e2e-approver-a@snad-e2e.example",
  approverB: "wf-e2e-approver-b@snad-e2e.example",
  reassigner: "wf-e2e-reassigner@snad-e2e.example",
  incident: "wf-e2e-incident@snad-e2e.example",
  tenantB: "wf-e2e-tenant-b@snad-e2e.example",
} as const;

const IDS = {
  tenantA: "aaaaaaa1-0000-4000-8000-000000000001",
  execAUser: "aaaaaaa2-0000-4000-8000-000000000011",
  execAEmployee: "aaaaaaa4-0000-4000-8000-000000000011",
  execBEmployee: "aaaaaaa4-0000-4000-8000-000000000012",
  approverAUser: "aaaaaaa2-0000-4000-8000-000000000021",
  approverBUser: "aaaaaaa2-0000-4000-8000-000000000022",
} as const;

const tokenCache = new Map<string, string>();

test.describe.configure({ mode: "serial" });

async function login(request: APIRequestContext, email = ACTORS.admin): Promise<string> {
  const cached = tokenCache.get(email);
  if (cached) return cached;
  const res = await request.post(`${API}/api/v1/auth/login`, {
    data: { email, password: PASSWORD },
  });
  expect(res.status(), `login failed for ${email}: ${await res.text()}`).toBe(200);
  const body = await res.json();
  expect(body.accessToken).toBeTruthy();
  tokenCache.set(email, body.accessToken);
  return body.accessToken as string;
}

async function auth(request: APIRequestContext, email = ACTORS.admin): Promise<Record<string, string>> {
  return {
    Authorization: `Bearer ${await login(request, email)}`,
    "Content-Type": "application/json",
  };
}

async function createDefinition(
  req: APIRequestContext,
  code: string,
  name: string,
  email = ACTORS.admin,
): Promise<{ id: string; version: number; versionLock: number; definitionFamilyId: string }> {
  const res = await req.post(`${API}/api/v1/workflows/definitions`, {
    headers: await auth(req, email),
    data: { code, name, description: "E2E release gate", module: "GENERAL", triggerType: "MANUAL" },
  });
  expect(res.status()).toBe(200);
  return await res.json();
}

async function addStep(
  req: APIRequestContext,
  defId: string,
  stepKey: string,
  stepType: string,
  sequenceOrder: number,
  options: { requiredCapability?: string; requiredRole?: string; configuration?: string } = {},
  email = ACTORS.admin,
): Promise<{ id: string }> {
  const res = await req.post(`${API}/api/v1/workflows/definitions/${defId}/steps`, {
    headers: await auth(req, email),
    data: {
      stepKey,
      name: stepKey,
      stepType,
      sequenceOrder,
      configuration: options.configuration ?? "{}",
      requiredCapability: options.requiredCapability ?? "",
      requiredRole: options.requiredRole ?? "",
    },
  });
  expect(res.status()).toBe(200);
  return await res.json();
}

async function addTransition(
  req: APIRequestContext,
  defId: string,
  fromStepId: string,
  toStepId: string,
  key: string,
  outcome = "SUCCESS",
  email = ACTORS.admin,
): Promise<void> {
  const res = await req.post(`${API}/api/v1/workflows/definitions/${defId}/transitions`, {
    headers: await auth(req, email),
    data: { fromStepId, toStepId, transitionKey: key, outcome, priority: 10 },
  });
  expect(res.status()).toBe(200);
}

async function createLinearWorkflow(
  req: APIRequestContext,
  prefix: string,
  taskType = "ACTION",
  taskOptions: { requiredCapability?: string; requiredRole?: string; configuration?: string } = {},
  email = ACTORS.admin,
): Promise<{ defId: string; versionLock: number; familyId: string }> {
  const def = await createDefinition(req, prefix, `E2E ${prefix}`, email);
  const start = await addStep(req, def.id, "start", "START", 1, {}, email);
  const task = await addStep(req, def.id, "task", taskType, 2, taskOptions, email);
  const end = await addStep(req, def.id, "end", "END", 3, {}, email);
  await addTransition(req, def.id, start.id, task.id, "begin", "SUCCESS", email);
  await addTransition(req, def.id, task.id, end.id, "done", "SUCCESS", email);
  return { defId: def.id, versionLock: def.versionLock, familyId: def.definitionFamilyId };
}

async function publish(req: APIRequestContext, defId: string, versionLock: number, email = ACTORS.admin) {
  const res = await req.post(`${API}/api/v1/workflows/definitions/${defId}/publish`, {
    headers: await auth(req, email),
    data: { expectedVersion: versionLock },
  });
  expect(res.status()).toBe(200);
  return await res.json();
}

async function activateDefinition(req: APIRequestContext, defId: string, email = ACTORS.admin): Promise<void> {
  const res = await req.post(`${API}/api/v1/workflows/definitions/${defId}/activate`, {
    headers: await auth(req, email), data: {},
  });
  expect(res.status()).toBe(200);
}

async function startInstance(req: APIRequestContext, defId: string, email = ACTORS.admin) {
  const res = await req.post(`${API}/api/v1/workflows/instances`, {
    headers: await auth(req, email),
    data: {
      workflowDefinitionId: defId,
      businessEntityType: "E2E",
      businessEntityId: crypto.randomUUID(),
    },
  });
  expect(res.status()).toBe(200);
  return await res.json();
}

async function advance(req: APIRequestContext, instanceId: string, outcome: string, email = ACTORS.admin) {
  return req.post(`${API}/api/v1/workflows/y2/instances/${instanceId}/advance`, {
    headers: await auth(req, email), data: { outcome },
  });
}

async function legacyAdvance(req: APIRequestContext, instanceId: string, nextStepKey: string, email = ACTORS.admin) {
  return req.post(`${API}/api/v1/workflows/instances/${instanceId}/advance`, {
    headers: await auth(req, email), data: { nextStepKey },
  });
}

async function instanceSteps(req: APIRequestContext, instanceId: string, email = ACTORS.admin) {
  const res = await req.get(`${API}/api/v1/workflows/instances/${instanceId}/steps`, {
    headers: await auth(req, email),
  });
  expect(res.status()).toBe(200);
  return await res.json() as Array<{ id: string; stepKey: string; status: string }>;
}

async function myWork(req: APIRequestContext, email: string) {
  const res = await req.get(`${API}/api/v1/workflows/work-items/mine`, { headers: await auth(req, email) });
  expect(res.status()).toBe(200);
  return await res.json() as Array<Record<string, unknown>>;
}

async function poolWork(req: APIRequestContext, email: string) {
  const res = await req.get(`${API}/api/v1/workflows/work-items/pool`, { headers: await auth(req, email) });
  expect(res.status()).toBe(200);
  return await res.json() as Array<Record<string, unknown>>;
}

async function createApproval(
  req: APIRequestContext,
  instanceId: string,
  stepInstanceId: string,
  requestedFromUserId: string,
  aggregation: "ANY_ONE" | "ALL",
) {
  const res = await req.post(`${API}/api/v1/workflows/instances/${instanceId}/approvals`, {
    headers: await auth(req),
    data: {
      workflowStepInstanceId: stepInstanceId,
      requestedFromUserId,
      requestedFromRole: null,
      approvalPolicy: aggregation,
    },
  });
  expect(res.status()).toBe(200);
  return await res.json();
}

/* P01 — AUTH + JWT */
test("P01 — real login returns JWT and accesses protected Workflow endpoint", async ({ request }) => {
  const token = await login(request);
  expect(token.length).toBeGreaterThan(20);
  const res = await request.get(`${API}/api/v1/workflows/definitions`, { headers: await auth(request) });
  expect(res.status()).toBe(200);
});

/* P02 — DESIGN / VALIDATE / SIMULATE / PUBLISH */
test("P02 — draft validates, simulates and publishes as Y2", async ({ request }) => {
  const wf = await createLinearWorkflow(request, `P02-${Date.now()}`);
  const validation = await request.post(`${API}/api/v1/workflows/definitions/${wf.defId}/validate`, {
    headers: await auth(request), data: {},
  });
  expect(validation.status()).toBe(200);
  expect((await validation.json()).valid).toBe(true);

  const simulation = await request.post(`${API}/api/v1/workflows/definitions/${wf.defId}/simulate`, {
    headers: await auth(request), data: {},
  });
  expect(simulation.status()).toBe(200);
  const simBody = await simulation.json();
  expect(simBody.valid).toBe(true);
  expect(simBody.simulated).toBe(true);

  const published = await publish(request, wf.defId, wf.versionLock);
  expect(published.publicationState).toBe("PUBLISHED");
  expect(published.engineGeneration).toBe("Y2");
});

/* P03 — IMMUTABILITY / NEXT DRAFT */
test("P03 — published definition rejects graph mutation and next draft preserves family", async ({ request }) => {
  const wf = await createLinearWorkflow(request, `P03-${Date.now()}`);
  const published = await publish(request, wf.defId, wf.versionLock);

  const stepRes = await request.post(`${API}/api/v1/workflows/definitions/${wf.defId}/steps`, {
    headers: await auth(request),
    data: { stepKey: "forbidden", name: "Forbidden", stepType: "ACTION", sequenceOrder: 99, configuration: "{}" },
  });
  expect(stepRes.status()).toBe(409);

  const transitions = await request.get(`${API}/api/v1/workflows/definitions/${wf.defId}/transitions`, {
    headers: await auth(request),
  });
  expect(transitions.status()).toBe(200);
  const transitionRows = await transitions.json();
  const trRes = await request.post(`${API}/api/v1/workflows/definitions/${wf.defId}/transitions`, {
    headers: await auth(request),
    data: {
      fromStepId: transitionRows[0].fromStepId,
      toStepId: transitionRows[0].toStepId,
      transitionKey: "forbidden",
      outcome: "SUCCESS",
      priority: 0,
    },
  });
  expect(trRes.status()).toBe(409);

  const draft = await request.post(`${API}/api/v1/workflows/definitions/${wf.defId}/next-draft`, {
    headers: await auth(request), data: {},
  });
  expect(draft.status()).toBe(200);
  const next = await draft.json();
  expect(next.id).not.toBe(wf.defId);
  expect(next.version).toBe(published.version + 1);
  expect(next.definitionFamilyId).toBe(published.definitionFamilyId);
  expect(next.publicationState).toBe("DRAFT");

  const old = await request.get(`${API}/api/v1/workflows/definitions/${wf.defId}`, { headers: await auth(request) });
  expect(old.status()).toBe(200);
  expect((await old.json()).publicationState).toBe("PUBLISHED");
});

/* P04 — EXACT Y2 PINNING */
test("P04 — Y2 instance remains pinned after newer family version publishes", async ({ request }) => {
  const wf = await createLinearWorkflow(request, `P04-${Date.now()}`);
  const firstPublished = await publish(request, wf.defId, wf.versionLock);
  const instance = await startInstance(request, wf.defId);

  const pins = {
    workflowDefinitionId: instance.workflowDefinitionId,
    definitionVersionId: instance.definitionVersionId,
    definitionFamilyId: instance.definitionFamilyId,
    engineGeneration: instance.engineGeneration,
    workflowVersion: instance.workflowVersion,
  };
  expect(pins.workflowDefinitionId).toBe(wf.defId);
  expect(pins.definitionVersionId).toBe(wf.defId);
  expect(pins.definitionFamilyId).toBe(firstPublished.definitionFamilyId);
  expect(pins.engineGeneration).toBe("Y2");
  expect(pins.workflowVersion).toBe(1);

  const draftRes = await request.post(`${API}/api/v1/workflows/definitions/${wf.defId}/next-draft`, {
    headers: await auth(request), data: {},
  });
  expect(draftRes.status()).toBe(200);
  const draft = await draftRes.json();
  const s = await addStep(request, draft.id, "start", "START", 1);
  const t = await addStep(request, draft.id, "task", "ACTION", 2);
  const e = await addStep(request, draft.id, "end", "END", 3);
  await addTransition(request, draft.id, s.id, t.id, "begin");
  await addTransition(request, draft.id, t.id, e.id, "done");
  await publish(request, draft.id, draft.versionLock);

  const detailRes = await request.get(`${API}/api/v1/workflows/instances/${instance.id}`, { headers: await auth(request) });
  expect(detailRes.status()).toBe(200);
  const detail = await detailRes.json();
  expect(detail.workflowDefinitionId).toBe(pins.workflowDefinitionId);
  expect(detail.definitionVersionId).toBe(pins.definitionVersionId);
  expect(detail.definitionFamilyId).toBe(pins.definitionFamilyId);
  expect(detail.engineGeneration).toBe(pins.engineGeneration);
  expect(detail.workflowVersion).toBe(pins.workflowVersion);
});

/* P05 — DIRECT HUMAN TASK */
test("P05 — DIRECT human task is generated, completed by assignee and instance progresses", async ({ request }) => {
  const wf = await createLinearWorkflow(
    request,
    `P05-${Date.now()}`,
    "HUMAN_TASK",
    { configuration: DIRECT_ASSIGNMENT },
    ACTORS.execA,
  );
  await publish(request, wf.defId, wf.versionLock, ACTORS.execA);
  const instance = await startInstance(request, wf.defId, ACTORS.execA);
  const toTask = await advance(request, instance.id, "begin", ACTORS.execA);
  expect(toTask.status()).toBe(200);

  const items = await myWork(request, ACTORS.execA);
  const item = items.find((row) => row.workflowInstanceId === instance.id);
  expect(item).toBeTruthy();
  expect(item?.assignmentMode).toBe("DIRECT");
  expect(item?.status).toBe("CLAIMED");
  expect(item?.assigneeEmployeeId).toBe(IDS.execAEmployee);
  expect(item?.claimedByEmployeeId).toBe(IDS.execAEmployee);

  const completed = await request.post(`${API}/api/v1/workflows/work-items/${item?.id}/complete`, {
    headers: await auth(request, ACTORS.execA), data: { expectedVersion: item?.version, reason: "done" },
  });
  expect(completed.status()).toBe(200);
  expect((await completed.json()).status).toBe("COMPLETED");

  const toEnd = await advance(request, instance.id, "done", ACTORS.execA);
  expect(toEnd.status()).toBe(200);
  expect((await toEnd.json()).status).toBe("COMPLETED");
});

/* P06 — WORK_POOL ATOMIC CLAIM */
test("P06 — two eligible actors race for one pool item and exactly one wins", async ({ request }) => {
  const wf = await createLinearWorkflow(
    request,
    `P06-${Date.now()}`,
    "HUMAN_TASK",
    { requiredCapability: "WORKFLOW.TASK_EXECUTE" },
  );
  await publish(request, wf.defId, wf.versionLock);
  const instance = await startInstance(request, wf.defId);
  const toTask = await advance(request, instance.id, "begin");
  expect(toTask.status()).toBe(200);

  const [poolA, poolB] = await Promise.all([
    poolWork(request, ACTORS.execA),
    poolWork(request, ACTORS.execB),
  ]);
  const itemA = poolA.find((row) => row.workflowInstanceId === instance.id);
  const itemB = poolB.find((row) => row.workflowInstanceId === instance.id);
  expect(itemA?.id).toBeTruthy();
  expect(itemA?.id).toBe(itemB?.id);

  const [claimA, claimB] = await Promise.all([
    request.post(`${API}/api/v1/workflows/work-items/${itemA?.id}/claim`, {
      headers: await auth(request, ACTORS.execA), data: { expectedVersion: itemA?.version, reason: "race A" },
    }),
    request.post(`${API}/api/v1/workflows/work-items/${itemB?.id}/claim`, {
      headers: await auth(request, ACTORS.execB), data: { expectedVersion: itemB?.version, reason: "race B" },
    }),
  ]);
  expect([claimA.status(), claimB.status()].sort()).toEqual([200, 409]);

  const winner = claimA.status() === 200 ? await claimA.json() : await claimB.json();
  expect([IDS.execAEmployee, IDS.execBEmployee]).toContain(winner.claimedByEmployeeId);
});

/* P07 — ANY_ONE APPROVAL */
test("P07 — ANY_ONE first approval wins, closes sibling and routes approve", async ({ request }) => {
  const def = await createDefinition(request, `P07-${Date.now()}`, "ANY_ONE approval");
  const start = await addStep(request, def.id, "start", "START", 1);
  const approval = await addStep(request, def.id, "approval", "APPROVAL", 2, { requiredCapability: "WORKFLOW.APPROVE" });
  const end = await addStep(request, def.id, "end", "END", 3);
  await addTransition(request, def.id, start.id, approval.id, "begin");
  await addTransition(request, def.id, approval.id, end.id, "approve", "APPROVE");
  await addTransition(request, def.id, approval.id, end.id, "reject", "REJECT");
  await publish(request, def.id, def.versionLock);
  const instance = await startInstance(request, def.id);
  expect((await advance(request, instance.id, "begin")).status()).toBe(200);
  const steps = await instanceSteps(request, instance.id);
  const approvalStep = steps.find((step) => step.stepKey === "approval");
  expect(approvalStep?.id).toBeTruthy();

  const a = await createApproval(request, instance.id, approvalStep!.id, IDS.approverAUser, "ANY_ONE");
  const b = await createApproval(request, instance.id, approvalStep!.id, IDS.approverBUser, "ANY_ONE");
  const approved = await request.post(`${API}/api/v1/workflows/approvals/${a.id}/approve`, {
    headers: await auth(request, ACTORS.approverA), data: { comments: "approved" },
  });
  expect(approved.status()).toBe(200);
  expect((await approved.json()).status).toBe("APPROVED");

  const sibling = await request.post(`${API}/api/v1/workflows/approvals/${b.id}/approve`, {
    headers: await auth(request, ACTORS.approverB), data: { comments: "too late" },
  });
  expect(sibling.status()).toBe(409);

  const routed = await advance(request, instance.id, "APPROVE");
  expect(routed.status()).toBe(200);
  expect((await routed.json()).status).toBe("COMPLETED");
});

/* P08 — ALL APPROVAL */
test("P08 — ALL requires everyone for success and first valid rejection closes a fresh aggregate", async ({ request }) => {
  const createAllScenario = async (prefix: string) => {
    const def = await createDefinition(request, `${prefix}-${Date.now()}`, "ALL approval");
    const start = await addStep(request, def.id, "start", "START", 1);
    const approval = await addStep(request, def.id, "approval", "APPROVAL", 2, { requiredCapability: "WORKFLOW.APPROVE" });
    const end = await addStep(request, def.id, "end", "END", 3);
    await addTransition(request, def.id, start.id, approval.id, "begin");
    await addTransition(request, def.id, approval.id, end.id, "approve", "APPROVE");
    await addTransition(request, def.id, approval.id, end.id, "reject", "REJECT");
    await publish(request, def.id, def.versionLock);
    const instance = await startInstance(request, def.id);
    expect((await advance(request, instance.id, "begin")).status()).toBe(200);
    const step = (await instanceSteps(request, instance.id)).find((row) => row.stepKey === "approval")!;
    const a = await createApproval(request, instance.id, step.id, IDS.approverAUser, "ALL");
    const b = await createApproval(request, instance.id, step.id, IDS.approverBUser, "ALL");
    return { instance, a, b };
  };

  const success = await createAllScenario("P08-S");
  expect((await request.post(`${API}/api/v1/workflows/approvals/${success.a.id}/approve`, {
    headers: await auth(request, ACTORS.approverA), data: { comments: "A approves" },
  })).status()).toBe(200);
  expect((await request.post(`${API}/api/v1/workflows/approvals/${success.b.id}/approve`, {
    headers: await auth(request, ACTORS.approverB), data: { comments: "B approves" },
  })).status()).toBe(200);
  const approveRoute = await advance(request, success.instance.id, "APPROVE");
  expect(approveRoute.status()).toBe(200);
  expect((await approveRoute.json()).status).toBe("COMPLETED");

  const rejected = await createAllScenario("P08-R");
  const blank = await request.post(`${API}/api/v1/workflows/approvals/${rejected.a.id}/reject`, {
    headers: await auth(request, ACTORS.approverA), data: { comments: "" },
  });
  expect(blank.status()).toBe(400);
  const validReject = await request.post(`${API}/api/v1/workflows/approvals/${rejected.a.id}/reject`, {
    headers: await auth(request, ACTORS.approverA), data: { comments: "budget rejected" },
  });
  expect(validReject.status()).toBe(200);
  const closedSibling = await request.post(`${API}/api/v1/workflows/approvals/${rejected.b.id}/approve`, {
    headers: await auth(request, ACTORS.approverB), data: { comments: "too late" },
  });
  expect(closedSibling.status()).toBe(409);
  const rejectRoute = await advance(request, rejected.instance.id, "REJECT");
  expect(rejectRoute.status()).toBe(200);
  expect((await rejectRoute.json()).status).toBe("COMPLETED");
});

/* P09 — DISABLED USER / ACTIVE EMPLOYEE */
test("P09 — disabled User cannot act, work is preserved, explicit reassignment restores progress", async ({ request }) => {
  const wf = await createLinearWorkflow(
    request,
    `P09-${Date.now()}`,
    "HUMAN_TASK",
    { configuration: DIRECT_ASSIGNMENT },
    ACTORS.execA,
  );
  await publish(request, wf.defId, wf.versionLock, ACTORS.execA);
  const instance = await startInstance(request, wf.defId, ACTORS.execA);
  expect((await advance(request, instance.id, "begin", ACTORS.execA)).status()).toBe(200);
  const item = (await myWork(request, ACTORS.execA)).find((row) => row.workflowInstanceId === instance.id)!;
  expect(item.id).toBeTruthy();

  const disable = await request.post(`${API}/api/v1/workflows/e2e-fixtures/user-status`, {
    headers: await auth(request), data: { userId: IDS.execAUser, status: "INACTIVE" },
  });
  expect(disable.status()).toBe(200);
  expect((await disable.json()).employeeStatus).toBe("ACTIVE");

  const denied = await request.post(`${API}/api/v1/workflows/work-items/${item.id}/complete`, {
    headers: await auth(request, ACTORS.execA), data: { expectedVersion: item.version, reason: "must fail" },
  });
  expect(denied.status()).toBe(403);

  const reassign = await request.post(`${API}/api/v1/workflows/work-items/${item.id}/reassign`, {
    headers: await auth(request, ACTORS.reassigner),
    data: { newAssigneeEmployeeId: IDS.execBEmployee, expectedVersion: item.version, reason: "disabled assignee" },
  });
  expect(reassign.status()).toBe(200);
  const reassigned = await reassign.json();
  expect(reassigned.assigneeEmployeeId).toBe(IDS.execBEmployee);

  const completed = await request.post(`${API}/api/v1/workflows/work-items/${item.id}/complete`, {
    headers: await auth(request, ACTORS.execB), data: { expectedVersion: reassigned.version, reason: "replacement completed" },
  });
  expect(completed.status()).toBe(200);

  const restore = await request.post(`${API}/api/v1/workflows/e2e-fixtures/user-status`, {
    headers: await auth(request), data: { userId: IDS.execAUser, status: "ACTIVE" },
  });
  expect(restore.status()).toBe(200);
});

/* P10 — INCIDENT LIFECYCLE */
test("P10 — real incident follows OPEN → ACKNOWLEDGED → RESOLVED with mandatory resolution", async ({ request }) => {
  const wf = await createLinearWorkflow(request, `P10-${Date.now()}`);
  await publish(request, wf.defId, wf.versionLock);
  const instance = await startInstance(request, wf.defId);
  const startStep = (await instanceSteps(request, instance.id)).find((row) => row.stepKey === "start")!;

  const fixture = await request.post(`${API}/api/v1/workflows/e2e-fixtures/incidents`, {
    headers: await auth(request),
    data: { workflowInstanceId: instance.id, workflowStepInstanceId: startStep.id },
  });
  expect(fixture.status()).toBe(200);
  const incident = await fixture.json();
  expect(incident.status).toBe("OPEN");

  const ack = await request.post(`${API}/api/v1/workflows/incidents/${incident.id}/acknowledge`, {
    headers: await auth(request, ACTORS.incident), data: {},
  });
  expect(ack.status()).toBe(200);
  expect((await ack.json()).status).toBe("ACKNOWLEDGED");

  const blank = await request.post(`${API}/api/v1/workflows/incidents/${incident.id}/resolve`, {
    headers: await auth(request, ACTORS.incident), data: { resolution: "" },
  });
  expect(blank.status()).toBe(400);

  const resolved = await request.post(`${API}/api/v1/workflows/incidents/${incident.id}/resolve`, {
    headers: await auth(request, ACTORS.incident), data: { resolution: "operator verified recovery" },
  });
  expect(resolved.status()).toBe(200);
  const body = await resolved.json();
  expect(body.status).toBe("RESOLVED");
  expect(body.resolution).toBe("operator verified recovery");
});

/* P11 — LEGACY STRANGLER */
test("P11 — in-flight LEGACY instance remains LEGACY after definition publishes Y2", async ({ request }) => {
  const wf = await createLinearWorkflow(request, `P11-${Date.now()}`);
  await activateDefinition(request, wf.defId);
  const instance = await startInstance(request, wf.defId);
  expect(instance.engineGeneration).toBe("LEGACY");
  expect(instance.workflowDefinitionId).toBe(wf.defId);
  expect(instance.definitionVersionId).toBe(wf.defId);

  await publish(request, wf.defId, 1);
  const next = await legacyAdvance(request, instance.id, "task");
  expect(next.status()).toBe(200);
  const nextBody = await next.json();
  expect(nextBody.engineGeneration).toBe("LEGACY");
  expect(nextBody.workflowDefinitionId).toBe(wf.defId);
  expect(nextBody.definitionVersionId).toBe(wf.defId);
});

/* P12 — REAL CROSS-TENANT DENIAL */
test("P12 — Tenant B cannot access or mutate real Tenant A resources", async ({ request }) => {
  const wf = await createLinearWorkflow(
    request,
    `P12-${Date.now()}`,
    "HUMAN_TASK",
    { requiredCapability: "WORKFLOW.TASK_EXECUTE" },
  );
  await publish(request, wf.defId, wf.versionLock);
  const instance = await startInstance(request, wf.defId);
  expect((await advance(request, instance.id, "begin")).status()).toBe(200);
  const poolItem = (await poolWork(request, ACTORS.execA)).find((row) => row.workflowInstanceId === instance.id)!;
  expect(poolItem.id).toBeTruthy();
  const startStep = (await instanceSteps(request, instance.id)).find((row) => row.stepKey === "start")!;
  const incidentFixture = await request.post(`${API}/api/v1/workflows/e2e-fixtures/incidents`, {
    headers: await auth(request), data: { workflowInstanceId: instance.id, workflowStepInstanceId: startStep.id },
  });
  expect(incidentFixture.status()).toBe(200);
  const incident = await incidentFixture.json();

  const tenantBHeaders = await auth(request, ACTORS.tenantB);
  const defDenied = await request.get(`${API}/api/v1/workflows/definitions/${wf.defId}`, { headers: tenantBHeaders });
  const instanceDenied = await request.get(`${API}/api/v1/workflows/instances/${instance.id}`, { headers: tenantBHeaders });
  expect([403, 404]).toContain(defDenied.status());
  expect([403, 404]).toContain(instanceDenied.status());

  const workDenied = await request.post(`${API}/api/v1/workflows/work-items/${poolItem.id}/claim`, {
    headers: tenantBHeaders, data: { expectedVersion: poolItem.version, reason: "cross tenant" },
  });
  expect([403, 404]).toContain(workDenied.status());

  const incidentDenied = await request.post(`${API}/api/v1/workflows/incidents/${incident.id}/acknowledge`, {
    headers: tenantBHeaders, data: {},
  });
  expect([403, 404]).toContain(incidentDenied.status());
});

/* P13 — REAL UI / RTL / ACCESSIBILITY */
test("P13 — real Workflow UI exposes Arabic RTL IA, keyboard access, validation and conflict errors", async ({ page }: { page: Page }) => {
  await loginThroughUi(page, ACTORS.admin, PASSWORD);
  const workflowLink = page.locator('a[href="/workflow"]').first();
  await expect(workflowLink).toBeVisible();
  await workflowLink.click();
  await page.waitForURL(/\/workflow$/);
  await page.waitForLoadState("networkidle");

  await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
  await expect(page.locator("html")).toHaveAttribute("lang", "ar");

  const labels = ["نظرة عامة", "التعريفات", "مهامي", "الموافقات", "المثيلات", "الحوادث", "المراقبة", "الإعدادات"];
  for (const label of labels) {
    await expect(page.getByRole("button", { name: label, exact: true })).toBeVisible();
  }

  await page.getByRole("button", { name: "التعريفات", exact: true }).focus();
  await expect(page.getByRole("button", { name: "التعريفات", exact: true })).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("heading", { name: "تعريفات سير العمل" })).toBeVisible();

  await page.getByRole("button", { name: "+ تعريف جديد" }).click();
  await page.getByRole("button", { name: "حفظ", exact: true }).click();
  await expect(page.getByText("الرمز والاسم مطلوبان")).toBeVisible();

  const duplicateCode = `P13-${Date.now()}`;
  await page.getByPlaceholder("الرمز (مثال: WF-001)").fill(duplicateCode);
  await page.getByPlaceholder("الاسم").fill("P13 first definition");
  await page.getByRole("button", { name: "حفظ", exact: true }).click();
  await expect(page.getByText(duplicateCode)).toBeVisible();

  await page.getByRole("button", { name: "+ تعريف جديد" }).click();
  await page.getByPlaceholder("الرمز (مثال: WF-001)").fill(duplicateCode);
  await page.getByPlaceholder("الاسم").fill("P13 duplicate definition");
  await page.getByRole("button", { name: "حفظ", exact: true }).click();
  await expect(page.getByText(/فشل|موجود|تعارض|Conflict/i).first()).toBeVisible();
});
