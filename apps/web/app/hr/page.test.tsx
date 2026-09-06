// @vitest-environment jsdom

/** WS5 Task 10 — /hr operational dashboard: authoritative summary counts. */
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { hrmV2ApiMock, authMock } = vi.hoisted(() => ({
  hrmV2ApiMock: {
    listEmployments: vi.fn(),
    listPositions: vi.fn(),
    listAssignments: vi.fn(),
    listComplianceOverrides: vi.fn(),
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

vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: React.AnchorHTMLAttributes<HTMLAnchorElement>) => (
    <a href={String(href)} {...props}>{children}</a>
  ),
}));

import HrPage from "./page";

const EMPLOYMENTS = [
  { employmentId: "e-1", personId: "p-1", legalEntityId: "le-1", employeeNumber: "E1", workerClassificationCode: "FULL_TIME", currentStatus: "ACTIVE", employmentStartDate: "2025-01-01", terminationDate: null, rehireOfEmployeeId: null, version: 1 },
  { employmentId: "e-2", personId: "p-2", legalEntityId: "le-1", employeeNumber: "E2", workerClassificationCode: "FULL_TIME", currentStatus: "ACTIVE", employmentStartDate: "2025-02-01", terminationDate: null, rehireOfEmployeeId: null, version: 1 },
  { employmentId: "e-3", personId: "p-3", legalEntityId: "le-1", employeeNumber: "E3", workerClassificationCode: "PART_TIME", currentStatus: "ONBOARDING", employmentStartDate: "2026-09-01", terminationDate: null, rehireOfEmployeeId: null, version: 1 },
  { employmentId: "e-4", personId: "p-4", legalEntityId: "le-1", employeeNumber: "E4", workerClassificationCode: "FULL_TIME", currentStatus: "ON_LEAVE", employmentStartDate: "2024-06-01", terminationDate: null, rehireOfEmployeeId: null, version: 1 },
];

const POSITIONS = [
  { positionId: "pos-1", staffability: "STAFFABLE", title: "أ", jobId: null, orgUnitId: null, effectiveFrom: "2020-01-01", effectiveTo: null, status: "ACTIVE" },
  { positionId: "pos-2", staffability: "STAFFABLE", title: "ب", jobId: null, orgUnitId: null, effectiveFrom: "2020-01-01", effectiveTo: null, status: "ACTIVE" },
  { positionId: "pos-3", staffability: "STAFFABLE", title: "ج", jobId: null, orgUnitId: null, effectiveFrom: "2020-01-01", effectiveTo: null, status: "ACTIVE" },
];

const ASSIGNMENTS = [
  { assignmentId: "a-1", employmentId: "e-1", organizationId: "o-1", orgUnitId: null, positionId: "pos-1", reportsToAssignmentId: null, assignmentType: "PRIMARY", occupancyMode: "DEDICATED", allocationPercent: 100, effectiveFrom: "2020-01-01", effectiveTo: null, status: "ACTIVE", version: 1 },
  // Ended assignment — must NOT count as occupying.
  { assignmentId: "a-2", employmentId: "e-2", organizationId: "o-1", orgUnitId: null, positionId: "pos-2", reportsToAssignmentId: null, assignmentType: "PRIMARY", occupancyMode: "DEDICATED", allocationPercent: 100, effectiveFrom: "2020-01-01", effectiveTo: "2021-01-01", status: "ENDED", version: 1 },
];

const OVERRIDES = [
  { requestId: "ov-1", complianceRuleId: "r", resourceType: "EMPLOYMENT", resourceId: "e-1", requesterUserId: "u", justification: "j", evidenceReference: null, approvedBy: null, approvalComment: null, validFrom: null, validUntil: null, status: "PENDING", executedAt: null },
  { requestId: "ov-2", complianceRuleId: "r", resourceType: "EMPLOYMENT", resourceId: "e-1", requesterUserId: "u", justification: "j", evidenceReference: null, approvedBy: "x", approvalComment: "c", validFrom: null, validUntil: null, status: "APPROVED", executedAt: null },
];

beforeEach(() => {
  for (const k of Object.keys(hrmV2ApiMock)) hrmV2ApiMock[k as keyof typeof hrmV2ApiMock].mockReset();
  hrmV2ApiMock.listEmployments.mockResolvedValue(EMPLOYMENTS);
  hrmV2ApiMock.listPositions.mockResolvedValue(POSITIONS);
  hrmV2ApiMock.listAssignments.mockResolvedValue(ASSIGNMENTS);
  hrmV2ApiMock.listComplianceOverrides.mockResolvedValue(OVERRIDES);
  authMock.state = "AUTHENTICATED";
  authMock.capabilities = [
    "HRM.EMPLOYEE.VIEW", "HRM.ORG_STRUCTURE.VIEW", "HRM.ASSIGNMENT.VIEW",
    "HRM.COMPLIANCE_OVERRIDE.REQUEST",
  ];
});

afterEach(() => cleanup());

/** Read the numeric value of the stat card bearing the given Arabic label. */
function statValue(label: string): string {
  const card = screen.getByText(label).closest("div")!;
  return within(card).getByText(/^\d+$/).textContent ?? "";
}

async function renderDashboard() {
  render(<HrPage />);
  await screen.findByRole("region", { name: "ملخص الموارد البشرية" });
  // Wait until loading finished (stat cards render).
  await screen.findByText("توظيف نشِط");
}

describe("HR operational dashboard", () => {
  it("derives authoritative employment status counts", async () => {
    await renderDashboard();
    expect(screen.getByText("توظيف نشِط")).toBeInTheDocument();
    expect(screen.getByText("قيد التأهيل")).toBeInTheDocument();
    expect(screen.getByText("في إجازة / موقوف")).toBeInTheDocument();
    expect(statValue("توظيف نشِط")).toBe("2");
    expect(statValue("قيد التأهيل")).toBe("1");
    expect(statValue("في إجازة / موقوف")).toBe("1");
  });

  it("derives occupancy from effective assignments only (ended ≠ occupying)", async () => {
    await renderDashboard();
    expect(screen.getByText("منصب مشغول")).toBeInTheDocument();
    expect(screen.getByText("منصب شاغر")).toBeInTheDocument();
    // pos-1 occupied; pos-2's assignment ENDED; pos-3 vacant → 1 / 2.
    expect(statValue("منصب مشغول")).toBe("1");
    expect(statValue("منصب شاغر")).toBe("2");
  });

  it("counts only PENDING compliance overrides", async () => {
    await renderDashboard();
    expect(screen.getByText("تجاوزات قيد المراجعة")).toBeInTheDocument();
  });

  it("renders the workspace home with navigation", async () => {
    await renderDashboard();
    expect(screen.getByRole("navigation", { name: "أقسام الموارد البشرية" })).toBeInTheDocument();
  });
});
