// @vitest-environment jsdom

/** WS5 Task 10 — Org Structure: as-of chart + revision workflow. */
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { hrmV2ApiMock, authMock } = vi.hoisted(() => ({
  hrmV2ApiMock: { listOrgUnits: vi.fn(), reviseOrgUnit: vi.fn() },
  authMock: { state: "AUTHENTICATED", capabilities: [] as string[] },
}));

vi.mock("@/lib/api/hr-v2-api", () => ({
  hrmV2Api: hrmV2ApiMock,
  newIdempotencyKey: vi.fn(() => "generated-key-1"),
  parseHrmV2Error: vi.fn(() => null),
  HrmV2ApiError: class HrmV2ApiError extends Error {},
}));

vi.mock("@/lib/auth/auth-provider", () => ({
  useAuth: () => ({ state: authMock.state, me: { capabilities: authMock.capabilities } }),
}));

vi.mock("next/navigation", () => ({
  useRouter: vi.fn(() => ({ replace: vi.fn(), push: vi.fn() })),
  usePathname: () => "/hr/org-structure",
}));

import OrgStructurePage from "./page";

const UNITS = [
  { orgUnitId: "ou-1", name: "الإدارة العامة", code: "GEN", unitType: "DIVISION", parentOrgUnitId: null, effectiveFrom: "2024-01-01", effectiveTo: null, status: "ACTIVE" },
  { orgUnitId: "ou-2", name: "الموارد البشرية", code: "HR", unitType: "DEPARTMENT", parentOrgUnitId: "ou-1", effectiveFrom: "2024-01-01", effectiveTo: null, status: "ACTIVE" },
  { orgUnitId: "ou-3", name: "وحدة مستقبلية", code: "FUT", unitType: "DEPARTMENT", parentOrgUnitId: "ou-1", effectiveFrom: "2030-01-01", effectiveTo: null, status: "ACTIVE" },
];

beforeEach(() => {
  hrmV2ApiMock.listOrgUnits.mockReset();
  hrmV2ApiMock.reviseOrgUnit.mockReset();
  hrmV2ApiMock.listOrgUnits.mockResolvedValue(UNITS);
  hrmV2ApiMock.reviseOrgUnit.mockResolvedValue({});
  authMock.state = "AUTHENTICATED";
  authMock.capabilities = ["HRM.ORG_STRUCTURE.VIEW", "HRM.ORG_STRUCTURE.MANAGE"];
});

afterEach(() => cleanup());

describe("Org Structure workspace", () => {
  it("renders the effective-dated hierarchy (parent → child) with Arabic labels", async () => {
    render(<OrgStructurePage />);
    const tree = await screen.findByRole("list", { name: "الهيكل التنظيمي" });
    expect(tree).toBeInTheDocument();
    expect(screen.getByText("الإدارة العامة")).toBeInTheDocument();
    expect(screen.getByText("الموارد البشرية")).toBeInTheDocument();
    // Future-dated unit (2030) is not visible at today's snapshot.
    expect(screen.queryByText("وحدة مستقبلية")).not.toBeInTheDocument();
  });

  it("shows future-dated units only when the asOf date covers them", async () => {
    const user = userEvent.setup();
    render(<OrgStructurePage />);
    await screen.findByText("الإدارة العامة");

    const asOfInput = screen.getByLabelText("تاريخ العرض (سريان)");
    // jsdom date inputs: set value via change with ISO text.
    await user.clear(asOfInput);
    await user.type(asOfInput, "2030-06-01");
    await waitFor(() => expect(screen.getByText("وحدة مستقبلية")).toBeInTheDocument());
  });

  it("revises an org unit effective-dated with a generated Idempotency-Key", async () => {
    const user = userEvent.setup();
    render(<OrgStructurePage />);
    await screen.findByText("الموارد البشرية");

    await user.click(screen.getAllByRole("button", { name: "تعديل" })[0]);
    const dialog = screen.getByRole("dialog");
    expect(dialog).toBeInTheDocument();
    // The effective date defaults to today (ISO shape verified below); the
    // operator may adjust it before confirming.

    await user.click(screen.getByRole("button", { name: "تأكيد" }));
    await waitFor(() => expect(hrmV2ApiMock.reviseOrgUnit).toHaveBeenCalledTimes(1));
    const [unitId, payload, key] = hrmV2ApiMock.reviseOrgUnit.mock.calls[0];
    expect(unitId).toBe("ou-1");
    expect(payload.effectiveDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(key).toBe("generated-key-1");
  });

  it("hides revise actions without ORG_STRUCTURE.MANAGE", async () => {
    authMock.capabilities = ["HRM.ORG_STRUCTURE.VIEW"];
    render(<OrgStructurePage />);
    await screen.findByText("الإدارة العامة");
    expect(screen.queryByRole("button", { name: "تعديل" })).not.toBeInTheDocument();
  });
});
