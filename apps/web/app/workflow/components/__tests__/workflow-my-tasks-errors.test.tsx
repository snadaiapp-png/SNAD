// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError, ApiNetworkError } from "@/lib/api/errors";
import { WorkflowMyTasks } from "../workflow-my-tasks";

const { workflowApiMock } = vi.hoisted(() => ({
  workflowApiMock: {
    listMyWorkItems: vi.fn(),
    listPoolWorkItems: vi.fn(),
    claimWorkItem: vi.fn(),
    releaseWorkItem: vi.fn(),
    completeWorkItem: vi.fn(),
  },
}));

vi.mock("@/lib/api/workflow-api", () => ({
  workflowApi: workflowApiMock,
}));

function httpError(status: number): ApiHttpError {
  return new ApiHttpError(`HTTP ${status}: GET /api/v1/workflow/work-items`, {
    status,
    error: status === 500 ? "Internal Server Error" : null,
    message: status === 500 ? "A database failure occurred (relation missing)" : null,
    path: "/api/v1/workflow/work-items",
    requestId: "req-test-1",
    body: null,
  });
}

const anItem = {
  id: "11111111-1111-1111-1111-111111111111",
  type: "HUMAN_TASK",
  status: "CLAIMED",
  title: "مراجعة الطلب",
  version: 3,
  assignmentMode: "DIRECT",
  dueAt: null,
  slaDueAt: null,
  sourceModule: "crm",
  sourceEntityType: "lead",
  sourceEntityId: "22222222-2222-2222-2222-222222222222",
};

describe("<WorkflowMyTasks /> error handling contract (Y2 hotfix regression)", () => {
  beforeEach(() => {
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("renders an explicit Arabic server error, a retry action, and never the empty state when the API fails with 500", async () => {
    workflowApiMock.listMyWorkItems.mockRejectedValueOnce(httpError(500));
    workflowApiMock.listPoolWorkItems.mockRejectedValueOnce(httpError(500));

    render(<WorkflowMyTasks />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("حدث خطأ في الخادم");
    });

    // A failed request must NOT be presented as an empty state.
    expect(screen.queryByText("لا توجد مهام مباشرة.")).not.toBeInTheDocument();
    expect(screen.queryByText("لا توجد مهام متاحة في التجمع.")).not.toBeInTheDocument();

    // Raw transport details must never leak to the user.
    expect(screen.queryByText(/HTTP 500/)).not.toBeInTheDocument();
    expect(screen.queryByText(/\/api\/v1\//)).not.toBeInTheDocument();
    expect(screen.queryByText(/A database failure occurred/)).not.toBeInTheDocument();

    // Clear retry affordance.
    const retry = screen.getByRole("button", { name: "إعادة المحاولة" });
    workflowApiMock.listMyWorkItems.mockResolvedValueOnce([anItem]);
    workflowApiMock.listPoolWorkItems.mockResolvedValueOnce([]);
    await userEvent.click(retry);

    await waitFor(() => {
      expect(screen.getByText("مراجعة الطلب")).toBeInTheDocument();
    });
    expect(screen.queryByRole("button", { name: "إعادة المحاولة" })).not.toBeInTheDocument();
  });

  it("maps 401 to the Arabic session-expired guidance", async () => {
    workflowApiMock.listMyWorkItems.mockRejectedValueOnce(httpError(401));
    workflowApiMock.listPoolWorkItems.mockRejectedValueOnce(httpError(401));

    render(<WorkflowMyTasks />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("انتهت صلاحية الجلسة");
    });
    expect(screen.queryByText("لا توجد مهام مباشرة.")).not.toBeInTheDocument();
  });

  it("maps 403 to the Arabic permission guidance", async () => {
    workflowApiMock.listMyWorkItems.mockRejectedValueOnce(httpError(403));
    workflowApiMock.listPoolWorkItems.mockRejectedValueOnce(httpError(403));

    render(<WorkflowMyTasks />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("لا تملك صلاحية الوصول");
    });
    expect(screen.queryByText("لا توجد مهام مباشرة.")).not.toBeInTheDocument();
  });

  it("maps network failures to the Arabic connectivity guidance with retry", async () => {
    workflowApiMock.listMyWorkItems.mockRejectedValueOnce(
      new ApiNetworkError("fetch failed", null),
    );
    workflowApiMock.listPoolWorkItems.mockRejectedValueOnce(
      new ApiNetworkError("fetch failed", null),
    );

    render(<WorkflowMyTasks />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("تعذر الاتصال بالخادم");
    });
    expect(screen.getByRole("button", { name: "إعادة المحاولة" })).toBeInTheDocument();
    expect(screen.queryByText("لا توجد مهام متاحة في التجمع.")).not.toBeInTheDocument();
  });

  it("keeps showing the empty states only after a successful load with no rows", async () => {
    workflowApiMock.listMyWorkItems.mockResolvedValueOnce([]);
    workflowApiMock.listPoolWorkItems.mockResolvedValueOnce([]);

    render(<WorkflowMyTasks />);

    await waitFor(() => {
      expect(screen.getByText("لا توجد مهام مباشرة.")).toBeInTheDocument();
    });
    expect(screen.getByText("لا توجد مهام متاحة في التجمع.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "إعادة المحاولة" })).not.toBeInTheDocument();
  });
});
