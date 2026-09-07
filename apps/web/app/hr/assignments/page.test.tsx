// @vitest-environment jsdom

/** WS5 Task 10 — Assignments: transfer + change-manager workflows. */
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { hrmV2ApiMock, authMock } = vi.hoisted(() => ({
  hrmV2ApiMock: {
    listAssignments: vi.fn(),
    transferAssignment: vi.fn(),
    changeAssignmentManager: vi.fn(),
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
  usePathname: () => "/hr/assignments",
}));

import AssignmentsPage from "./page";

const ASSIGNMENTS = [
  { assignmentId: "a-1", employmentId: "e-1", organizationId: "o-1", orgUnitId: "ou-1", positionId: "pos-1", reportsToAssignmentId: null, assignmentType: "PRIMARY", occupancyMode: "DEDICATED", allocationPercent: 100, effectiveFrom: "2020-01-01", effectiveTo: null, status: "ACTIVE", version: 6 },
];

beforeEach(() => {
  for (const k of Object.keys(hrmV2ApiMock)) hrmV2ApiMock[k as keyof typeof hrmV2ApiMock].mockReset();
  hrmV2ApiMock.listAssignments.mockResolvedValue(ASSIGNMENTS);
  hrmV2ApiMock.transferAssignment.mockResolvedValue({});
  hrmV2ApiMock.changeAssignmentManager.mockResolvedValue({});
  authMock.state = "AUTHENTICATED";
  authMock.capabilities = ["HRM.ASSIGNMENT.VIEW", "HRM.ASSIGNMENT.MANAGE"];
});

afterEach(() => cleanup());

describe("Assignments workspace", () => {
  it("transfers an assignment with expectedVersion + generated Idempotency-Key", async () => {
    const user = userEvent.setup();
    render(<AssignmentsPage />);
    await screen.findByRole("table");

    await user.click(screen.getByRole("button", { name: "نقل" }));
    const dialog = screen.getByRole("dialog");
    await user.type(within(dialog).getByLabelText("الوحدة التنظيمية الجديدة (المعرّف)"), "ou-9");
    await user.click(within(dialog).getByRole("button", { name: "تأكيد" }));

    await waitFor(() => expect(hrmV2ApiMock.transferAssignment).toHaveBeenCalledTimes(1));
    const [assignmentId, payload, key] = hrmV2ApiMock.transferAssignment.mock.calls[0];
    expect(assignmentId).toBe("a-1");
    expect(payload.orgUnitId).toBe("ou-9");
    expect(payload.expectedVersion).toBe(6);
    expect(payload.effectiveDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(key).toBe("generated-key-1");
  });

  it("changes manager with expectedVersion + generated Idempotency-Key", async () => {
    const user = userEvent.setup();
    render(<AssignmentsPage />);
    await screen.findByRole("table");

    await user.click(screen.getByRole("button", { name: "تغيير المدير" }));
    const dialog = screen.getByRole("dialog");
    await user.type(within(dialog).getByLabelText("إسناد المدير الجديد (المعرّف)"), "a-77");
    await user.click(within(dialog).getByRole("button", { name: "تأكيد" }));

    await waitFor(() => expect(hrmV2ApiMock.changeAssignmentManager).toHaveBeenCalledTimes(1));
    const [assignmentId, payload, key] = hrmV2ApiMock.changeAssignmentManager.mock.calls[0];
    expect(assignmentId).toBe("a-1");
    expect(payload.reportsToAssignmentId).toBe("a-77");
    expect(payload.expectedVersion).toBe(6);
    expect(key).toBe("generated-key-1");
  });

  it("hides command actions without ASSIGNMENT.MANAGE", async () => {
    authMock.capabilities = ["HRM.ASSIGNMENT.VIEW"];
    render(<AssignmentsPage />);
    await screen.findByRole("table");
    expect(screen.queryByRole("button", { name: "نقل" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "تغيير المدير" })).not.toBeInTheDocument();
  });

  it("renders the 409 conflict safely in the dialog without claiming success", async () => {
    hrmV2ApiMock.transferAssignment.mockRejectedValue({
      details: { status: 409, body: { code: "HRM_CONCURRENCY_CONFLICT", message: "stale" } },
    });
    const user = userEvent.setup();
    render(<AssignmentsPage />);
    await screen.findByRole("table");

    await user.click(screen.getByRole("button", { name: "نقل" }));
    await user.type(within(screen.getByRole("dialog")).getByLabelText("الوحدة التنظيمية الجديدة (المعرّف)"), "ou-9");
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "تأكيد" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("مستخدم آخر");
    expect(screen.queryByText("تم تسجيل نقل الإسناد بتاريخ السريان المحدد")).not.toBeInTheDocument();
  });
});
