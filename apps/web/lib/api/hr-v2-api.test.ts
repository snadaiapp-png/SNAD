/**
 * WS5 Task 8 — typed HRM v2 web client contract tests.
 *
 * These tests pin the client surface to the canonical /api/v2/hr contract:
 * - representative operations from every resource (People, Employments,
 *   Assignments, Org Units, Jobs, Positions, Contracts, Compensation,
 *   Compliance, Audit);
 * - Idempotency-Key header on every critical POST command;
 * - expectedVersion delivered in request bodies where the backend requires it;
 * - the safe directory/private PII split (private reads are explicit calls);
 * - canonical v2 error envelope parsing (HRM_* codes with field violations).
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { requestMock } = vi.hoisted(() => ({ requestMock: vi.fn() }));

vi.mock("@/lib/api/client", () => ({
  apiClient: {
    request: requestMock,
  },
}));

import {
  HrmV2ApiError,
  hrmV2Api,
  newIdempotencyKey,
  parseHrmV2Error,
} from "./hr-v2-api";

function lastCall(): { method: string; path: string; body?: unknown; headers?: Record<string, string>; query?: Record<string, string> } {
  const arg = requestMock.mock.calls[requestMock.mock.calls.length - 1][0] as {
    method: string; path: string; body?: unknown; query?: Record<string, string>;
    context?: { headers?: Record<string, string> };
  };
  // The ApiRequest contract carries caller headers under context.headers.
  return { ...arg, headers: arg.context?.headers };
}

beforeEach(() => {
  requestMock.mockReset();
  requestMock.mockResolvedValue({});
});

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("hrmV2Api — people", () => {
  it("lists people from the canonical directory endpoint", async () => {
    await hrmV2Api.listPeople();
    const call = lastCall();
    expect(call.method).toBe("GET");
    expect(call.path).toBe("/api/platform/api/v2/hr/people");
  });

  it("sends an Idempotency-Key when creating a person", async () => {
    await hrmV2Api.createPerson({ firstName: "سالم", lastName: "العتيبي" }, "idem-1");
    const call = lastCall();
    expect(call.method).toBe("POST");
    expect(call.path).toBe("/api/platform/api/v2/hr/people");
    expect(call.headers?.["Idempotency-Key"]).toBe("idem-1");
    expect(call.body).toEqual({ firstName: "سالم", lastName: "العتيبي" });
  });

  it("sends expectedVersion in the body when patching a person", async () => {
    await hrmV2Api.patchPerson("p-1", { firstName: "سالم", lastName: "العتيبي", expectedVersion: 4 });
    const call = lastCall();
    expect(call.method).toBe("PATCH");
    expect(call.path).toBe("/api/platform/api/v2/hr/people/p-1");
    expect((call.body as { expectedVersion: number }).expectedVersion).toBe(4);
  });

  it("keeps private PII reads on the explicit audited endpoint", async () => {
    await hrmV2Api.getPersonPrivate("p-1");
    const call = lastCall();
    expect(call.method).toBe("GET");
    expect(call.path).toBe("/api/platform/api/v2/hr/people/p-1/private");
  });

  it("requires an Idempotency-Key for identifier issuance", async () => {
    await hrmV2Api.addIdentifier("p-1", { identifierType: "NATIONAL_ID", value: "10...", issuingCountryCode: "SA" }, "idem-2");
    const call = lastCall();
    expect(call.headers?.["Idempotency-Key"]).toBe("idem-2");
    expect(call.path).toBe("/api/platform/api/v2/hr/people/p-1/identifiers");
  });

  it("sends Idempotency-Key for user-link and un-link operations", async () => {
    await hrmV2Api.linkUser("p-1", { userId: "u-1" }, "idem-3");
    expect(lastCall().headers?.["Idempotency-Key"]).toBe("idem-3");

    await hrmV2Api.unlinkUser("p-1");
    const call = lastCall();
    expect(call.method).toBe("DELETE");
    expect(call.path).toBe("/api/platform/api/v2/hr/people/p-1/user-link");
  });
});

describe("hrmV2Api — employments lifecycle", () => {
  it("creates employments with an Idempotency-Key", async () => {
    await hrmV2Api.createEmployment({ personId: "p-1" } as never, "idem-4");
    const call = lastCall();
    expect(call.path).toBe("/api/platform/api/v2/hr/employments");
    expect(call.headers?.["Idempotency-Key"]).toBe("idem-4");
  });

  it("sends effective date, expectedVersion and reason for lifecycle commands", async () => {
    await hrmV2Api.employmentLifecycle(
      "e-1", "activate",
      { effectiveDate: "2026-09-05", expectedVersion: 7, reasonCode: "ONBOARDING_COMPLETE" },
      "idem-5",
    );
    const call = lastCall();
    expect(call.method).toBe("POST");
    expect(call.path).toBe("/api/platform/api/v2/hr/employments/e-1/activate");
    expect(call.headers?.["Idempotency-Key"]).toBe("idem-5");
    expect(call.body).toEqual({
      effectiveDate: "2026-09-05",
      expectedVersion: 7,
      reasonCode: "ONBOARDING_COMPLETE",
    });
  });

  it("supports every canonical employment lifecycle command", async () => {
    const commands = [
      "submit-onboarding", "activate", "start-leave", "return-from-leave",
      "suspend", "reinstate", "terminate", "void",
    ] as const;
    for (const command of commands) {
      requestMock.mockClear();
      await hrmV2Api.employmentLifecycle("e-1", command, { effectiveDate: "2026-01-01", expectedVersion: 1 }, "k");
      expect(lastCall().path).toBe(`/api/platform/api/v2/hr/employments/e-1/${command}`);
    }
  });
});

describe("hrmV2Api — assignments", () => {
  it("creates assignments with an Idempotency-Key", async () => {
    await hrmV2Api.createAssignment({ employmentId: "e-1", organizationId: "o-1", assignmentType: "PRIMARY", occupancyMode: "DEDICATED", effectiveFrom: "2026-01-01" } as never, "idem-6");
    expect(lastCall().headers?.["Idempotency-Key"]).toBe("idem-6");
    expect(lastCall().path).toBe("/api/platform/api/v2/hr/assignments");
  });

  it("ends assignments with an Idempotency-Key and expectedVersion body", async () => {
    await hrmV2Api.endAssignment("a-1", { effectiveDate: "2026-02-01", expectedVersion: 3, reasonCode: "TRANSFER" }, "idem-7");
    const call = lastCall();
    expect(call.path).toBe("/api/platform/api/v2/hr/assignments/a-1/end");
    expect(call.headers?.["Idempotency-Key"]).toBe("idem-7");
    expect((call.body as { expectedVersion: number }).expectedVersion).toBe(3);
  });

  it("targets change-manager and transfer sub-resources", async () => {
    await hrmV2Api.changeAssignmentManager("a-1", { reportsToAssignmentId: "a-9", effectiveDate: "2026-02-01", expectedVersion: 3 }, "idem-8");
    expect(lastCall().path).toBe("/api/platform/api/v2/hr/assignments/a-1/change-manager");

    await hrmV2Api.transferAssignment("a-1", { orgUnitId: "ou-2", effectiveDate: "2026-02-01", expectedVersion: 4 }, "idem-9");
    expect(lastCall().path).toBe("/api/platform/api/v2/hr/assignments/a-1/transfer");
  });
});

describe("hrmV2Api — structure (org units, jobs, positions)", () => {
  it("revises org units effective-dated with an Idempotency-Key", async () => {
    await hrmV2Api.reviseOrgUnit("ou-1", { name: "الموارد البشرية", effectiveDate: "2026-03-01" }, "idem-10");
    const call = lastCall();
    expect(call.path).toBe("/api/platform/api/v2/hr/org-units/ou-1/revise");
    expect(call.headers?.["Idempotency-Key"]).toBe("idem-10");
  });

  it("creates jobs and positions with Idempotency-Keys", async () => {
    await hrmV2Api.createJob({ organizationId: "o-1", title: "مهندس", effectiveFrom: "2026-01-01" }, "idem-11");
    expect(lastCall().path).toBe("/api/platform/api/v2/hr/jobs");

    await hrmV2Api.createPosition({ title: "منصب مهندس", effectiveFrom: "2026-01-01" }, "idem-12");
    expect(lastCall().path).toBe("/api/platform/api/v2/hr/positions");
  });

  it("supports position revise, freeze and close commands", async () => {
    await hrmV2Api.revisePosition("pos-1", { title: "منصب", effectiveDate: "2026-03-01" }, "idem-13");
    expect(lastCall().path).toBe("/api/platform/api/v2/hr/positions/pos-1/revise");

    await hrmV2Api.freezePosition("pos-1", "idem-14");
    expect(lastCall().path).toBe("/api/platform/api/v2/hr/positions/pos-1/freeze");
    expect(lastCall().headers?.["Idempotency-Key"]).toBe("idem-14");

    await hrmV2Api.closePosition("pos-1", "idem-15");
    expect(lastCall().path).toBe("/api/platform/api/v2/hr/positions/pos-1/close");
  });
});

describe("hrmV2Api — contracts and compensation", () => {
  it("creates contracts with an Idempotency-Key", async () => {
    await hrmV2Api.createContract({ employmentId: "e-1", contractNumber: "C-1", effectiveDate: "2026-01-01" } as never, "idem-16");
    expect(lastCall().path).toBe("/api/platform/api/v2/hr/contracts");
    expect(lastCall().headers?.["Idempotency-Key"]).toBe("idem-16");
  });

  it("targets amend, activate and terminate sub-resources", async () => {
    await hrmV2Api.amendContract("c-1", { effectiveDate: "2026-04-01" } as never, "idem-17");
    expect(lastCall().path).toBe("/api/platform/api/v2/hr/contracts/c-1/amend");

    await hrmV2Api.activateContract("c-1", { versionNumber: 2, effectiveDate: "2026-04-01" }, "idem-18");
    expect(lastCall().path).toBe("/api/platform/api/v2/hr/contracts/c-1/activate");

    await hrmV2Api.terminateContract("c-1", { effectiveDate: "2026-05-01" }, "idem-19");
    expect(lastCall().path).toBe("/api/platform/api/v2/hr/contracts/c-1/terminate");
  });

  it("keeps compensation listing scoped to a specific employment", async () => {
    await hrmV2Api.listCompensationPackages("e-1");
    const call = lastCall();
    expect(call.method).toBe("GET");
    expect(call.path).toBe("/api/platform/api/v2/hr/compensation-packages");
    expect((call as unknown as { query?: Record<string, string> }).query).toEqual({ employmentId: "e-1" });
  });

  it("revises and ends compensation packages with Idempotency-Keys", async () => {
    await hrmV2Api.reviseCompensationPackage("cp-1", { effectiveFrom: "2026-06-01" } as never, "idem-20");
    expect(lastCall().path).toBe("/api/platform/api/v2/hr/compensation-packages/cp-1/revise");

    await hrmV2Api.endCompensationPackage("cp-1", { effectiveTo: "2026-12-31" }, "idem-21");
    expect(lastCall().path).toBe("/api/platform/api/v2/hr/compensation-packages/cp-1/end");
  });
});

describe("hrmV2Api — compliance and audit", () => {
  it("reads compliance context per employment", async () => {
    await hrmV2Api.getComplianceContext("e-1", "2026-09-05");
    const call = lastCall();
    expect(call.path).toBe("/api/platform/api/v2/hr/compliance/context");
    expect((call as unknown as { query?: Record<string, string> }).query).toEqual({ employmentId: "e-1", effectiveDate: "2026-09-05" });
  });

  it("requests compliance overrides with an Idempotency-Key", async () => {
    await hrmV2Api.requestComplianceOverride(
      { complianceRuleId: "r-1", resourceType: "EMPLOYMENT", resourceId: "e-1", justification: "سبب موثق" } as never,
      "idem-22",
    );
    expect(lastCall().path).toBe("/api/platform/api/v2/hr/compliance/overrides");
    expect(lastCall().headers?.["Idempotency-Key"]).toBe("idem-22");
  });

  it("targets approve, reject and revoke override decisions", async () => {
    await hrmV2Api.decideComplianceOverride("ov-1", "approve", { comment: "موافقة" }, "idem-23");
    expect(lastCall().path).toBe("/api/platform/api/v2/hr/compliance/overrides/ov-1/approve");

    await hrmV2Api.decideComplianceOverride("ov-1", "reject", { comment: "رفض" }, "idem-24");
    expect(lastCall().path).toBe("/api/platform/api/v2/hr/compliance/overrides/ov-1/reject");

    await hrmV2Api.decideComplianceOverride("ov-1", "revoke", { comment: "إلغاء" }, "idem-25");
    expect(lastCall().path).toBe("/api/platform/api/v2/hr/compliance/overrides/ov-1/revoke");
  });

  it("queries audit entries with resource type and limit", async () => {
    await hrmV2Api.listAudit({ resourceType: "EMPLOYMENT", limit: 25 });
    const call = lastCall();
    expect(call.path).toBe("/api/platform/api/v2/hr/audit");
    expect((call as unknown as { query?: Record<string, string> }).query).toEqual({ resourceType: "EMPLOYMENT", limit: 25 });
  });
});

describe("hrmV2Api — idempotency key generation", () => {
  it("generates unique UUID-shaped keys", () => {
    const a = newIdempotencyKey();
    const b = newIdempotencyKey();
    expect(a).not.toBe(b);
    expect(a).toMatch(/^[0-9a-f-]{36}$/);
  });
});

describe("hrmV2Api — canonical v2 error envelope", () => {
  const envelope = {
    code: "HRM_VALIDATION_FAILED",
    message: "Request validation failed",
    violations: [{ field: "firstName", message: "must not be blank" }],
  };

  function httpError(status: number, body: unknown): unknown {
    return {
      details: { status, body, message: null, error: null, path: "/x", requestId: "req-1" },
    };
  }

  it("parses the structured envelope into typed fields", () => {
    const parsed = parseHrmV2Error(httpError(400, envelope));
    expect(parsed).toBeInstanceOf(HrmV2ApiError);
    expect(parsed?.code).toBe("HRM_VALIDATION_FAILED");
    expect(parsed?.status).toBe(400);
    expect(parsed?.violations).toHaveLength(1);
    expect(parsed?.violations?.[0]).toEqual({ field: "firstName", message: "must not be blank" });
  });

  it("returns null for non-HRM error shapes", () => {
    expect(parseHrmV2Error(httpError(500, { message: "boom" }))).toBeNull();
    expect(parseHrmV2Error(new Error("network"))).toBeNull();
    expect(parseHrmV2Error(null)).toBeNull();
  });

  it("preserves conflict and compliance codes verbatim", () => {
    expect(parseHrmV2Error(httpError(409, { code: "HRM_CONCURRENCY_CONFLICT", message: "stale" }))?.code).toBe("HRM_CONCURRENCY_CONFLICT");
    expect(parseHrmV2Error(httpError(422, { code: "HRM_COMPLIANCE_BLOCKED", message: "blocked" }))?.code).toBe("HRM_COMPLIANCE_BLOCKED");
    expect(parseHrmV2Error(httpError(403, { code: "HRM_SCOPE_DENIED", message: "denied" }))?.status).toBe(403);
  });
});
