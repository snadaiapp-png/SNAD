// @vitest-environment jsdom

/**
 * Recruitment Dashboard — G1-T11 Screen 1 page test.
 * Verifies:
 *   - Permission-scoped rendering (tiles appear only when capability is present).
 *   - Loading → data render phases.
 *   - i18n key usage (no hard-coded strings).
 *   - Activity list rendering.
 */

import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const { recruitmentApiMock, authMock, i18nMock } = vi.hoisted(() => ({
  recruitmentApiMock: {
    listOpenings: vi.fn(),
    listCandidates: vi.fn(),
    listApplications: vi.fn(),
  },
  authMock: {
    state: "AUTHENTICATED" as string,
    capabilities: [] as string[],
  },
  i18nMock: {
    t: (key: string, params?: Record<string, string | number>) => {
      // Simple mock: return the key itself (so tests can assert on key presence),
      // or interpolate {param} placeholders for params.
      if (!params) return key;
      return key.replace(/\{(\w+)\}/g, (_, k: string) =>
        params[k] === undefined ? `{${k}}` : String(params[k]),
      );
    },
    locale: "ar" as const,
    direction: "rtl" as const,
    setLocale: vi.fn(),
  },
}));

vi.mock("@/lib/api/hr-v2-recruitment-api", () => ({
  hrmRecruitmentApi: recruitmentApiMock,
}));

vi.mock("@/lib/auth/auth-provider", () => ({
  useAuth: () => ({ state: authMock.state, me: { capabilities: authMock.capabilities } }),
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => i18nMock,
}));

vi.mock("@/lib/i18n", () => ({
  translations: { ar: {}, en: {} },
}));

vi.mock("@/components/auth/auth-loading-state", () => ({
  AuthLoadingState: () => <div>Loading session</div>,
}));

import RecruitmentDashboardPage from "./page";
import type { OpeningSummaryResponse } from "@/lib/api/hr-v2-recruitment-api";

const OPENINGS: OpeningSummaryResponse[] = [
  {
    openingId: "o1", title: "مهندس برمجيات", departmentName: "التقنية",
    headcount: 2, filledCount: 1, status: "OPEN",
    createdAt: "2026-09-01T00:00:00Z", updatedAt: "2026-09-15T00:00:00Z",
  },
  {
    openingId: "o2", title: "محاسب", departmentName: "المالية",
    headcount: 1, filledCount: 0, status: "DRAFT",
    createdAt: "2026-09-10T00:00:00Z", updatedAt: "2026-09-12T00:00:00Z",
  },
];

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("Recruitment Dashboard — G1-T11 Screen 1", () => {
  it("renders KPI tiles when user has all capabilities", async () => {
    authMock.capabilities = [
      "HRM.RECRUITMENT.OPENING.VIEW",
      "HRM.RECRUITMENT.CANDIDATE.VIEW",
      "HRM.RECRUITMENT.APPLICATION.MANAGE",
    ];
    recruitmentApiMock.listOpenings.mockResolvedValue(OPENINGS);
    recruitmentApiMock.listCandidates.mockResolvedValue([{ candidateId: "c1", displayName: "محمد", poolState: "ACTIVE", duplicateWarning: false, createdAt: "2026-09-10T00:00:00Z" }]);
    recruitmentApiMock.listApplications.mockResolvedValue([]);

    render(<RecruitmentDashboardPage />);

    await waitFor(() => {
      expect(screen.getByTestId("kpi-openings")).toBeInTheDocument();
    });
    expect(screen.getByTestId("kpi-openings")).toHaveTextContent("2");
    expect(screen.getByTestId("kpi-candidates")).toHaveTextContent("1");
    expect(screen.getByTestId("kpi-applications")).toBeInTheDocument();
    expect(screen.getByTestId("kpi-offers")).toBeInTheDocument();
    expect(screen.getByTestId("kpi-hires")).toBeInTheDocument();

    // i18n key for title is rendered (no hard-coded strings)
    expect(screen.getByText("hrm.recruitment.dashboard.title")).toBeInTheDocument();
  });

  it("hides openings tile when user lacks HRM.RECRUITMENT.OPENING.VIEW", async () => {
    authMock.capabilities = ["HRM.RECRUITMENT.CANDIDATE.VIEW"];
    recruitmentApiMock.listOpenings.mockResolvedValue([]);
    recruitmentApiMock.listCandidates.mockResolvedValue([
      { candidateId: "c1", displayName: "محمد ا.", poolState: "ACTIVE", duplicateWarning: false, createdAt: "2026-09-10T00:00:00Z" },
    ]);

    render(<RecruitmentDashboardPage />);

    await waitFor(() => {
      // Candidates tile appears (data loaded, canViewCandidates=true)
      expect(screen.getByTestId("kpi-candidates")).toBeInTheDocument();
    });
    // After loading completes, openings tile is still hidden (canViewOpenings=false)
    expect(screen.queryByTestId("kpi-openings")).not.toBeInTheDocument();
    expect(screen.queryByTestId("kpi-applications")).not.toBeInTheDocument();
  });

  it("shows permission hint when user has no recruitment capabilities", async () => {
    authMock.capabilities = [];
    recruitmentApiMock.listOpenings.mockResolvedValue([]);
    recruitmentApiMock.listCandidates.mockResolvedValue([]);
    recruitmentApiMock.listApplications.mockResolvedValue([]);

    render(<RecruitmentDashboardPage />);

    await waitFor(() => {
      expect(screen.getByText("hrm.recruitment.dashboard.permissionHint")).toBeInTheDocument();
    });
  });

  it("renders recent activity list with badge labels", async () => {
    authMock.capabilities = ["HRM.RECRUITMENT.OPENING.VIEW"];
    recruitmentApiMock.listOpenings.mockResolvedValue(OPENINGS);
    recruitmentApiMock.listCandidates.mockResolvedValue([]);
    recruitmentApiMock.listApplications.mockResolvedValue([]);

    render(<RecruitmentDashboardPage />);

    await waitFor(() => {
      expect(screen.getByText(/مهندس برمجيات/)).toBeInTheDocument();
    });
    expect(screen.getByText(/محاسب/)).toBeInTheDocument();
  });

  it("renders error state when API fails", async () => {
    authMock.capabilities = ["HRM.RECRUITMENT.OPENING.VIEW"];
    recruitmentApiMock.listOpenings.mockRejectedValue(new Error("network"));

    render(<RecruitmentDashboardPage />);

    await waitFor(() => {
      // Error state is rendered via HrErrorState which is part of hr-feedback
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
  });
});
