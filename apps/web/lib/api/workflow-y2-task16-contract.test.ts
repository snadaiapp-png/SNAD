import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

import { workflowApi } from "./workflow-api";
import { apiClient } from "./client";

afterEach(() => {
  vi.clearAllMocks();
});

describe("Workflow Y2 Task 16 mutation contracts", () => {
  it("sends expectedVersion when approving", async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ id: "approval-1", status: "APPROVED" } as never);

    await workflowApi.approveRequest("approval-1", 7, "approved");

    expect(apiClient.post).toHaveBeenCalledWith(
      "/api/v1/workflows/approvals/approval-1/approve",
      { expectedVersion: 7, comments: "approved" },
    );
  });

  it("sends expectedVersion when rejecting", async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ id: "approval-1", status: "REJECTED" } as never);

    await workflowApi.rejectRequest("approval-1", 8, "rejected");

    expect(apiClient.post).toHaveBeenCalledWith(
      "/api/v1/workflows/approvals/approval-1/reject",
      { expectedVersion: 8, comments: "rejected" },
    );
  });

  it("sends expectedVersion when resolving an incident", async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ id: "incident-1", status: "RESOLVED", version: 4 } as never);

    await workflowApi.resolveIncident("incident-1", 3, "restarted dependency");

    expect(apiClient.post).toHaveBeenCalledWith(
      "/api/v1/workflows/incidents/incident-1/resolve",
      { expectedVersion: 3, resolution: "restarted dependency" },
    );
  });
});
