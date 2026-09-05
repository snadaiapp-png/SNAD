// @vitest-environment jsdom

/** WS5 Task 10 — Compliance: controlled override request/approval + gating. */
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { hrmV2ApiMock, authMock } = vi.hoisted(() => ({
  hrmV2ApiMock: {
    listComplianceOverrides: vi.fn(),
    requestComplianceOverride: vi.fn(),
    decideComplianceOverride: vi.fn(),
  },
  authMock: { state: "AUTHENTICATED", capabilities: [] as string[] },
}));

vi.mock("@/lib/api/hr-v2-api", () => ({
  hrmV2Api: hrmV2ApiMock,
  newIdempotencyKey: vi.fn(() => "generated-key-1"),
  // Real-shape parser mock: HRM_* envelopes resolve to typed objects.
  parseHrmV2Error: vi.fn((err: unknown) => {
    const details = (err as { details?: { status?: number; body?: { code?: unknown; message?: unknown } } })?.details;
    const code = details?.body?.code;
    if (typeof code === "string" && code.startsWith("HRM_")) {
      return Object.assign(new Error(typeof details?.body?.message === "string" ? details.body.message : code), {
        code, status: details?.status ?? 0, violations: null, requestId: null,
      });
    }
    return null;
  }),
  HrmV2ApiError: class HrmV2ApiError extends Error {},
}));

vi.mock("@/lib/auth/auth-provider", () => ({
  useAuth: () => ({ state: authMock.state, me: { capabilities: authMock.capabilities } }),
}));

vi.mock("next/navigation", () => ({
  useRouter: vi.fn(() => ({ replace: vi.fn(), push: vi.fn() })),
  usePathname: () => "/hr/compliance",
}));

import CompliancePage from "./page";

const OVERRIDES = [
  {
    requestId: "ov-1",
    complianceRuleId: "11111111-2222-3333-4444-555555555555",
    resourceType: "EMPLOYMENT",
    resourceId: "e-1",
    requesterUserId: "u-9",
    justification: "سبب تشغيلي موثق",
    evidenceReference: "DOC-1",
    approvedBy: null,
    approvalComment: null,
    validFrom: "2026-09-01",
    validUntil: null,
    status: "PENDING_APPROVAL",
    executedAt: null,
  },
];

beforeEach(() => {
  for (const k of Object.keys(hrmV2ApiMock)) hrmV2ApiMock[k as keyof typeof hrmV2ApiMock].mockReset();
  hrmV2ApiMock.listComplianceOverrides.mockResolvedValue(OVERRIDES);
  hrmV2ApiMock.requestComplianceOverride.mockResolvedValue(OVERRIDES[0]);
  hrmV2ApiMock.decideComplianceOverride.mockResolvedValue(OVERRIDES[0]);
  authMock.state = "AUTHENTICATED";
  authMock.capabilities = ["HRM.EMPLOYEE.VIEW", "HRM.COMPLIANCE_OVERRIDE.REQUEST"];
});

afterEach(() => cleanup());

describe("Compliance workspace", () => {
  it("lists override requests with Arabic status labels", async () => {
    render(<CompliancePage />);
    const table = await screen.findByRole("table");
    expect(within(table).getByText("سبب تشغيلي موثق")).toBeInTheDocument();
    expect(within(table).getByText("قيد المراجعة")).toBeInTheDocument();
  });

  it("sends a controlled override request with a generated Idempotency-Key", async () => {
    const user = userEvent.setup();
    render(<CompliancePage />);
    await screen.findByRole("table");

    await user.click(screen.getByRole("button", { name: "طلب تجاوز مضبوط" }));
    const dialog = screen.getByRole("dialog");
    await user.type(within(dialog).getByLabelText("مرجع قاعدة الالتزام (المعرّف)"), "rule-1");
    await user.type(within(dialog).getByLabelText("معرّف السجل"), "e-1");
    await user.type(within(dialog).getByLabelText("المبرر"), "استثناء مضبوط بناءً على مستندات");
    await user.click(within(dialog).getByRole("button", { name: "تأكيد" }));

    await waitFor(() => expect(hrmV2ApiMock.requestComplianceOverride).toHaveBeenCalledTimes(1));
    const [payload, key] = hrmV2ApiMock.requestComplianceOverride.mock.calls[0];
    expect(payload.complianceRuleId).toBe("rule-1");
    expect(payload.resourceType).toBe("EMPLOYMENT");
    expect(payload.resourceId).toBe("e-1");
    expect(payload.justification).toContain("استثناء مضبوط");
    expect(key).toBe("generated-key-1");
  });

  it("reveals approve/reject decisions only with COMPLIANCE_OVERRIDE.APPROVE", async () => {
    authMock.capabilities = ["HRM.EMPLOYEE.VIEW", "HRM.COMPLIANCE_OVERRIDE.REQUEST", "HRM.COMPLIANCE_OVERRIDE.APPROVE"];
    const user = userEvent.setup();
    render(<CompliancePage />);
    await screen.findByRole("table");

    expect(screen.getByRole("button", { name: "اعتماد" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "رفض" })).toBeInTheDocument();

    // Approve sends the decision comment + key (four-eyes enforced server-side).
    await user.click(screen.getByRole("button", { name: "اعتماد" }));
    const dialog = screen.getByRole("dialog");
    await user.type(within(dialog).getByLabelText("تعليق القرار"), "مستندات كافية");
    await user.click(within(dialog).getByRole("button", { name: "تأكيد" }));

    await waitFor(() => expect(hrmV2ApiMock.decideComplianceOverride).toHaveBeenCalledTimes(1));
    const [overrideId, kind, payload, key] = hrmV2ApiMock.decideComplianceOverride.mock.calls[0];
    expect(overrideId).toBe("ov-1");
    expect(kind).toBe("approve");
    expect(payload.comment).toBe("مستندات كافية");
    expect(key).toBe("generated-key-1");
  });

  it("hides decision actions without COMPLIANCE_OVERRIDE.APPROVE", async () => {
    render(<CompliancePage />);
    await screen.findByRole("table");
    expect(screen.queryByRole("button", { name: "اعتماد" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "رفض" })).not.toBeInTheDocument();
  });

  it("renders a compliance-blocked 422 safely without weakening the rule", async () => {
    hrmV2ApiMock.requestComplianceOverride.mockRejectedValue({
      details: { status: 422, body: { code: "HRM_COMPLIANCE_BLOCKED", message: "hard rule" } },
    });
    const user = userEvent.setup();
    render(<CompliancePage />);
    await screen.findByRole("table");

    await user.click(screen.getByRole("button", { name: "طلب تجاوز مضبوط" }));
    const dialog = screen.getByRole("dialog");
    await user.type(within(dialog).getByLabelText("مرجع قاعدة الالتزام (المعرّف)"), "rule-hard");
    await user.type(within(dialog).getByLabelText("معرّف السجل"), "e-1");
    await user.type(within(dialog).getByLabelText("المبرر"), "محاولة تجاوز قاعدة صارمة");
    await user.click(within(dialog).getByRole("button", { name: "تأكيد" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("محظورة بموجب قاعدة التزام");
    // The request is never shown as submitted.
    expect(screen.queryByText("تم إرسال طلب التجاوز — بانتظار موافقة مستقل")).not.toBeInTheDocument();
  });
});