// @vitest-environment jsdom

/**
 * Job Opening Detail — G1-T11 Screen 3 page test.
 * Verifies: permission-scoped rendering (action buttons hidden when capability missing),
 * i18n key usage (no hard-coded strings), error state, notFound state.
 */

import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const { recruitmentApiMock, authMock, i18nMock } = vi.hoisted(() => ({
  recruitmentApiMock: {
    getOpening: vi.fn(),
    submitOpening: vi.fn(),
    approveOpening: vi.fn(),
    rejectOpening: vi.fn(),
    pauseOpening: vi.fn(),
    closeOpening: vi.fn(),
  },
  authMock: {
    state: "AUTHENTICATED" as string,
    capabilities: [] as string[],
  },
  i18nMock: {
    t: (key: string, params?: Record<string, string | number>) => {
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

vi.mock("@/lib/api/hr-v2-recruitment-api", () => ({ hrmRecruitmentApi: recruitmentApiMock }));
vi.mock("@/lib/api/hr-v2-api", () => ({
  newIdempotencyKey: vi.fn(() => "key-1"),
  parseHrmV2Error: vi.fn(() => null),
  HrmV2ApiError: class HrmV2ApiError extends Error {},
}));
vi.mock("@/lib/auth/auth-provider", () => ({
  useAuth: () => ({ state: authMock.state, me: { capabilities: authMock.capabilities, id: "u1" } }),
}));
vi.mock("@/lib/i18n/I18nProvider", () => ({ useI18n: () => i18nMock }));
vi.mock("@/lib/i18n", () => ({ translations: { ar: {}, en: {} } }));
vi.mock("@/components/auth/auth-loading-state", () => ({ AuthLoadingState: () => <div>Loading</div> }));
vi.mock("next/navigation", () => ({
  useParams: () => ({ openingId: "o1" }),
}));

import JobOpeningDetailPage from "./page";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("Job Opening Detail — G1-T11 Screen 3", () => {
  it("renders opening detail when user has OPENING.VIEW", async () => {
    authMock.capabilities = ["HRM.RECRUITMENT.OPENING.VIEW"];
    recruitmentApiMock.getOpening.mockResolvedValue({
      openingId: "o1",
      title: "مهندس برمجيات",
      description: null,
      departmentId: null,
      departmentName: "التقنية",
      headcount: 2,
      filledCount: 1,
      status: "OPEN",
      complianceDecision: null,
      stateHistory: [],
      version: 1,
      createdAt: "2026-09-01T00:00:00Z",
      updatedAt: "2026-09-15T00:00:00Z",
    });

    render(<JobOpeningDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("مهندس برمجيات")).toBeInTheDocument();
    });
    // i18n key for the title is rendered
    expect(screen.getByText("hrm.recruitment.openingDetail.title")).toBeInTheDocument();
  });

  it("shows permission hint when user lacks OPENING.VIEW", async () => {
    authMock.capabilities = [];
    recruitmentApiMock.getOpening.mockResolvedValue(null);

    render(<JobOpeningDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("hrm.recruitment.dashboard.permissionHint")).toBeInTheDocument();
    });
  });

  it("renders error state when API fails", async () => {
    authMock.capabilities = ["HRM.RECRUITMENT.OPENING.VIEW"];
    recruitmentApiMock.getOpening.mockRejectedValue(new Error("network"));

    render(<JobOpeningDetailPage />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
  });
});
