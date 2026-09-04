// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/lib/api/errors";
import { WorkflowIncidents } from "../workflow-incidents";

const { workflowApiMock } = vi.hoisted(() => ({
  workflowApiMock: {
    listIncidents: vi.fn(),
    acknowledgeIncident: vi.fn(),
    resolveIncident: vi.fn(),
  },
}));

vi.mock("@/lib/api/workflow-api", () => ({
  workflowApi: workflowApiMock,
}));

function httpError(status: number): ApiHttpError {
  return new ApiHttpError(`HTTP ${status}: GET /api/v1/workflow/incidents`, {
    status,
    error: null,
    message: "relation \"workflow_incidents\" does not exist",
    path: "/api/v1/workflow/incidents",
    requestId: "req-test-2",
    body: null,
  });
}

const anIncident = {
  id: "33333333-3333-3333-3333-333333333333",
  source: "EXECUTION",
  severity: "HIGH",
  status: "OPEN",
  failureCategory: "SYSTEM_ACTION_FAILED",
  createdAt: "2026-09-04T00:00:00Z",
  resolution: null,
};

describe("<WorkflowIncidents /> error handling contract (Y2 hotfix regression)", () => {
  beforeEach(() => {
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("renders an explicit Arabic server error with retry and never the empty state when the API fails with 500", async () => {
    workflowApiMock.listIncidents.mockRejectedValueOnce(httpError(500));

    render(<WorkflowIncidents />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("حدث خطأ في الخادم");
    });

    expect(screen.queryByText("لا توجد حوادث مفتوحة.")).not.toBeInTheDocument();
    expect(screen.queryByText(/relation "workflow_incidents"/)).not.toBeInTheDocument();
    expect(screen.queryByText(/HTTP 500/)).not.toBeInTheDocument();

    workflowApiMock.listIncidents.mockResolvedValueOnce([anIncident]);
    await userEvent.click(screen.getByRole("button", { name: "إعادة المحاولة" }));

    await waitFor(() => {
      expect(screen.getByText("EXECUTION")).toBeInTheDocument();
    });
    expect(screen.queryByRole("button", { name: "إعادة المحاولة" })).not.toBeInTheDocument();
  });

  it("maps 401 to the Arabic session-expired guidance", async () => {
    workflowApiMock.listIncidents.mockRejectedValueOnce(httpError(401));

    render(<WorkflowIncidents />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("انتهت صلاحية الجلسة");
    });
    expect(screen.queryByText("لا توجد حوادث مفتوحة.")).not.toBeInTheDocument();
  });

  it("keeps the empty state only after a successful load with no incidents", async () => {
    workflowApiMock.listIncidents.mockResolvedValueOnce([]);

    render(<WorkflowIncidents />);

    await waitFor(() => {
      expect(screen.getByText("لا توجد حوادث مفتوحة.")).toBeInTheDocument();
    });
    expect(screen.queryByRole("button", { name: "إعادة المحاولة" })).not.toBeInTheDocument();
  });
});
