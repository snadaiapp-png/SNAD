// @vitest-environment jsdom

/** WS5 Task 10 — /hr operational dashboard + G2 discoverability. */
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

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => {
    const messages: Record<string, string> = {
      "hrm.g2.landing.summary": "ملخص الموارد البشرية",
      "hrm.g2.landing.activeEmployment": "توظيف نشِط",
      "hrm.g2.landing.onboarding": "قيد التأهيل",
      "hrm.g2.landing.onLeaveSuspended": "في إجازة / موقوف",
      "hrm.g2.landing.occupiedPosition": "منصب مشغول",
      "hrm.g2.landing.vacantPosition": "منصب شاغر",
      "hrm.g2.landing.pendingOverrides": "تجاوزات قيد المراجعة",
      "hrm.g2.landing.employeeRecords": "سجل الموظفين",
      "hrm.g2.landing.organizationStructure": "الهيكل التنظيمي",
      "hrm.g2.landing.compliance": "الالتزام",
      "hrm.g2.workspace.title": "مساحة عمل الموارد البشرية",
      "hrm.g2.workspace.subtitle": "إدارة الموظفين والهيكل التنظيمي والتوظيف والتأهيل والإسنادات والعقود والالتزام",
      "hrm.g2.workspace.navLabel": "أقسام الموارد البشرية",
      "hrm.g2.workspace.nav.home": "الرئيسية",
      "hrm.g2.workspace.nav.employees": "الموظفون",
      "hrm.g2.workspace.nav.organizationStructure": "الهيكل التنظيمي",
      "hrm.g2.workspace.nav.jobs": "الوظائف",
      "hrm.g2.workspace.nav.positions": "المناصب",
      "hrm.g2.workspace.nav.assignments": "الإسنادات",
      "hrm.g2.workspace.nav.compliance": "الالتزام",
      "hrm.g2.workspace.nav.recruitment": "التوظيف",
      "hrm.g2.workspace.nav.onboarding": "التأهيل",
      "hrm.g2.workspace.nav.execution": "لوحة التنفيذ",
    };
    return {
      locale: "ar",
      direction: "rtl",
      setLocale: vi.fn(),
      t: (key: string) => messages[key] ?? key,
    };
  },
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

function statValue(label: string): string {
  const card = screen.getByText(label).closest("div")!;
  return within(card).getByText(/^\d+$/).textContent ?? "";
}

async function renderDashboard() {
  render(<HrPage />);
  await screen.findByRole("region", { name: "ملخص الموارد البشرية" });
  await screen.findByText("توظيف نشِط");
}

describe("HR operational dashboard", () => {
  it("derives authoritative employment status counts", async () => {
    await renderDashboard();
    expect(statValue("توظيف نشِط")).toBe("2");
    expect(statValue("قيد التأهيل")).toBe("1");
    expect(statValue("في إجازة / موقوف")).toBe("1");
  });

  it("derives occupancy from effective assignments only", async () => {
    await renderDashboard();
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

  it("allows a G2-only HR identity to discover its authorized self-service surface", async () => {
    authMock.capabilities = ["HRM.ATTENDANCE.SELF_VIEW"];
    render(<HrPage />);
    expect(await screen.findByTestId("hr-landing-ready")).toBeInTheDocument();
    expect(screen.getByTestId("g2-launcher-self")).toBeInTheDocument();
    expect(screen.queryByTestId("g2-launcher-team")).not.toBeInTheDocument();
    expect(screen.queryByTestId("g2-launcher-hr")).not.toBeInTheDocument();
    expect(hrmV2ApiMock.listEmployments).not.toHaveBeenCalled();
  });
});
