// @vitest-environment jsdom

/** WS5 Task 10 — Positions: derived occupancy + freeze/close workflows. */
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { hrmV2ApiMock, authMock } = vi.hoisted(() => ({
  hrmV2ApiMock: {
    listPositions: vi.fn(),
    listAssignments: vi.fn(),
    freezePosition: vi.fn(),
    closePosition: vi.fn(),
  },
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
  usePathname: () => "/hr/positions",
}));

import PositionsPage from "./page";

const past = "2020-01-01";

const POSITIONS = [
  { positionId: "pos-1", staffability: "STAFFABLE", title: "منصب مدير", jobId: null, orgUnitId: "ou-1", effectiveFrom: past, effectiveTo: null, status: "ACTIVE" },
  { positionId: "pos-2", staffability: "STAFFABLE", title: "منصب محاسب", jobId: null, orgUnitId: "ou-1", effectiveFrom: past, effectiveTo: null, status: "ACTIVE" },
];

const ASSIGNMENTS = [
  // Occupies pos-1 (ACTIVE, effective window covers today).
  { assignmentId: "a-1", employmentId: "e-1", organizationId: "o-1", orgUnitId: "ou-1", positionId: "pos-1", reportsToAssignmentId: null, assignmentType: "PRIMARY", occupancyMode: "DEDICATED", allocationPercent: 100, effectiveFrom: past, effectiveTo: null, status: "ACTIVE", version: 2 },
];

beforeEach(() => {
  for (const k of Object.keys(hrmV2ApiMock)) hrmV2ApiMock[k as keyof typeof hrmV2ApiMock].mockReset();
  hrmV2ApiMock.listPositions.mockResolvedValue(POSITIONS);
  hrmV2ApiMock.listAssignments.mockResolvedValue(ASSIGNMENTS);
  hrmV2ApiMock.freezePosition.mockResolvedValue({});
  hrmV2ApiMock.closePosition.mockResolvedValue({});
  authMock.state = "AUTHENTICATED";
  authMock.capabilities = ["HRM.ORG_STRUCTURE.VIEW", "HRM.ORG_STRUCTURE.MANAGE"];
});

afterEach(() => cleanup());

describe("Positions workspace", () => {
  it("derives occupancy from effective occupying assignments — no manual toggle", async () => {
    render(<PositionsPage />);
    const table = await screen.findByRole("table");
    const row1 = within(table).getByText("منصب مدير").closest("tr")!;
    expect(within(row1).getByText("مشغول")).toBeInTheDocument();
    const row2 = within(table).getByText("منصب محاسب").closest("tr")!;
    expect(within(row2).getByText("شاغر")).toBeInTheDocument();
    // No manual occupancy control anywhere on the page.
    expect(screen.queryByRole("button", { name: "تبديل الإشغال" })).not.toBeInTheDocument();
  });

  it("freezes a position with a generated Idempotency-Key", async () => {
    const user = userEvent.setup();
    render(<PositionsPage />);
    await screen.findByRole("table");

    await user.click(screen.getAllByRole("button", { name: "تجميد" })[0]);
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "تأكيد" }));

    await waitFor(() => expect(hrmV2ApiMock.freezePosition).toHaveBeenCalledWith("pos-1", "generated-key-1"));
    expect(hrmV2ApiMock.closePosition).not.toHaveBeenCalled();
  });

  it("closes a position with a generated Idempotency-Key", async () => {
    const user = userEvent.setup();
    render(<PositionsPage />);
    await screen.findByRole("table");

    await user.click(screen.getAllByRole("button", { name: "إغلاق" })[0]);
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "تأكيد" }));

    await waitFor(() => expect(hrmV2ApiMock.closePosition).toHaveBeenCalledWith("pos-1", "generated-key-1"));
  });

  it("hides freeze/close without ORG_STRUCTURE.MANAGE", async () => {
    authMock.capabilities = ["HRM.ORG_STRUCTURE.VIEW"];
    render(<PositionsPage />);
    await screen.findByRole("table");
    expect(screen.queryByRole("button", { name: "تجميد" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "إغلاق" })).not.toBeInTheDocument();
  });
});
