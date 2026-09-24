/**
 * Tests for workflow-api.ts — verifies the typed API client functions
 * correctly call the backend endpoints and handle responses.
 *
 * Pattern: same as lib/api/management-api.test.ts (mock fetch, verify URL + response).
 */
import { afterEach, describe, expect, it, vi } from "vitest";

// Mock the apiClient module before importing workflowApi
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

function mockResolve<T>(data: T) {
  return Promise.resolve(data);
}

describe("workflowApi", () => {
  describe("Definitions", () => {
    it("listDefinitions calls GET /api/v1/workflows/definitions with limit", async () => {
      vi.mocked(apiClient.get).mockReturnValue(mockResolve([]));
      await workflowApi.listDefinitions(25);
      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/workflows/definitions?limit=25");
    });

    it("listDefinitions uses default limit=50", async () => {
      vi.mocked(apiClient.get).mockReturnValue(mockResolve([]));
      await workflowApi.listDefinitions();
      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/workflows/definitions?limit=50");
    });

    it("getDefinition calls GET /definitions/:id", async () => {
      vi.mocked(apiClient.get).mockReturnValue(mockResolve({
        id: "wf-1", code: "WF-1", name: "Test", status: "DRAFT",
        triggerType: "MANUAL", module: "GENERAL", version: 1, versionLock: 0, createdBy: "user-1",
      }));
      const result = await workflowApi.getDefinition("wf-1");
      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/workflows/definitions/wf-1");
      expect(result.code).toBe("WF-1");
    });

    it("createDefinition calls POST with definition data", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({
        id: "wf-1", code: "WF-1", name: "Test", status: "DRAFT",
        triggerType: "MANUAL", module: "GENERAL", version: 1, versionLock: 0, createdBy: "user-1",
      }));
      const result = await workflowApi.createDefinition({
        code: "WF-1", name: "Test", module: "GENERAL", triggerType: "MANUAL",
      });
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/workflows/definitions", {
        code: "WF-1", name: "Test", module: "GENERAL", triggerType: "MANUAL",
      });
      expect(result.status).toBe("DRAFT");
    });

    it("activateDefinition calls POST /activate", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({ id: "wf-1", status: "ACTIVE" }));
      await workflowApi.activateDefinition("wf-1");
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/workflows/definitions/wf-1/activate");
    });

    it("deactivateDefinition calls POST /deactivate", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({ id: "wf-1", status: "INACTIVE" }));
      await workflowApi.deactivateDefinition("wf-1");
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/workflows/definitions/wf-1/deactivate");
    });

    it("archiveDefinition calls POST /archive", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({ id: "wf-1", status: "ARCHIVED" }));
      await workflowApi.archiveDefinition("wf-1");
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/workflows/definitions/wf-1/archive");
    });
  });

  describe("Instances", () => {
    it("listInstances calls GET /instances with limit", async () => {
      vi.mocked(apiClient.get).mockReturnValue(mockResolve([]));
      await workflowApi.listInstances(30);
      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/workflows/instances?limit=30");
    });

    it("getInstance calls GET /instances/:id", async () => {
      vi.mocked(apiClient.get).mockReturnValue(mockResolve({
        id: "inst-1", workflowDefinitionId: "wf-1", workflowVersion: 1,
        businessEntityType: "DECISION", businessEntityId: "dec-1",
        status: "RUNNING", currentStepKey: "REVIEW", startedBy: "user-1", version: 0,
      }));
      const result = await workflowApi.getInstance("inst-1");
      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/workflows/instances/inst-1");
      expect(result.businessEntityType).toBe("DECISION");
    });

    it("startWorkflow calls POST /instances with workflow data", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({
        id: "inst-1", workflowDefinitionId: "wf-1", workflowVersion: 1,
        businessEntityType: "DECISION", businessEntityId: "dec-1",
        status: "RUNNING", currentStepKey: "REVIEW", startedBy: "user-1", version: 0,
      }));
      const result = await workflowApi.startWorkflow({
        workflowDefinitionId: "wf-1", businessEntityType: "DECISION", businessEntityId: "dec-1",
      });
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/workflows/instances", {
        workflowDefinitionId: "wf-1", businessEntityType: "DECISION", businessEntityId: "dec-1",
      });
      expect(result.status).toBe("RUNNING");
    });

    it("pauseInstance calls POST /pause", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({ id: "inst-1", status: "PAUSED" }));
      await workflowApi.pauseInstance("inst-1");
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/workflows/instances/inst-1/pause");
    });

    it("resumeInstance calls POST /resume", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({ id: "inst-1", status: "RUNNING" }));
      await workflowApi.resumeInstance("inst-1");
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/workflows/instances/inst-1/resume");
    });

    it("cancelInstance calls POST /cancel with reason", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({ id: "inst-1", status: "CANCELLED" }));
      await workflowApi.cancelInstance("inst-1", "duplicate");
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/workflows/instances/inst-1/cancel", { reason: "duplicate" });
    });

    it("cancelInstance defaults reason to empty string", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({ id: "inst-1", status: "CANCELLED" }));
      await workflowApi.cancelInstance("inst-1");
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/workflows/instances/inst-1/cancel", { reason: "" });
    });
  });

  describe("Approvals", () => {
    it("listPendingApprovals calls GET /approvals with limit", async () => {
      vi.mocked(apiClient.get).mockReturnValue(mockResolve([]));
      await workflowApi.listPendingApprovals(20);
      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/workflows/approvals?limit=20");
    });

    it("listMyPendingApprovals calls GET /approvals/pending with limit", async () => {
      vi.mocked(apiClient.get).mockReturnValue(mockResolve([]));
      await workflowApi.listMyPendingApprovals(15);
      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/workflows/approvals/pending?limit=15");
    });

    it("approveRequest calls POST /approve with comments", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({
        id: "app-1", status: "APPROVED", decision: "APPROVED",
      }));
      const result = await workflowApi.approveRequest("app-1", "looks good");
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/workflows/approvals/app-1/approve", { comments: "looks good" });
      expect(result.status).toBe("APPROVED");
    });

    it("approveRequest defaults comments to empty string", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({ id: "app-1", status: "APPROVED" }));
      await workflowApi.approveRequest("app-1");
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/workflows/approvals/app-1/approve", { comments: "" });
    });

    it("rejectRequest calls POST /reject with comments", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({
        id: "app-1", status: "REJECTED", decision: "REJECTED",
      }));
      const result = await workflowApi.rejectRequest("app-1", "not compliant");
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/workflows/approvals/app-1/reject", { comments: "not compliant" });
      expect(result.status).toBe("REJECTED");
    });

    it("rejectRequest defaults comments to empty string", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({ id: "app-1", status: "REJECTED" }));
      await workflowApi.rejectRequest("app-1");
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/workflows/approvals/app-1/reject", { comments: "" });
    });
  });

  describe("Monitoring", () => {
    it("getMonitoringHealth calls GET /monitoring/health", async () => {
      vi.mocked(apiClient.get).mockReturnValue(mockResolve({
        status: "OK", tenantId: "t-1",
        overdueSteps: 0, overdueApprovals: 0, totalBreaches: 0,
      }));
      const result = await workflowApi.getMonitoringHealth();
      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/workflows/monitoring/health");
      expect(result.status).toBe("OK");
      expect(result.totalBreaches).toBe(0);
    });

    it("triggerSlaCheck calls POST /monitoring/check-sla", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({
        status: "OK", tenantId: "t-1",
        overdueSteps: 2, overdueApprovals: 1, totalBreaches: 3,
      }));
      const result = await workflowApi.triggerSlaCheck();
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/workflows/monitoring/check-sla");
      expect(result.totalBreaches).toBe(3);
    });
  });

  describe("Error Handling", () => {
    it("handles 401 errors (unauthorized)", async () => {
      const err = new Error("Unauthorized") as Error & { status?: number };
      err.status = 401;
      vi.mocked(apiClient.get).mockReturnValue(Promise.reject(err));
      await expect(workflowApi.listDefinitions()).rejects.toMatchObject({ status: 401 });
    });

    it("handles 403 errors (forbidden)", async () => {
      const err = new Error("Forbidden") as Error & { status?: number };
      err.status = 403;
      vi.mocked(apiClient.get).mockReturnValue(Promise.reject(err));
      await expect(workflowApi.listInstances()).rejects.toMatchObject({ status: 403 });
    });

    it("handles 500 errors (server)", async () => {
      const err = new Error("Server error") as Error & { status?: number };
      err.status = 500;
      vi.mocked(apiClient.post).mockReturnValue(Promise.reject(err));
      await expect(workflowApi.approveRequest("app-1")).rejects.toMatchObject({ status: 500 });
    });
  });

  describe("Type Safety", () => {
    it("WorkflowDefinitionResponse has all required fields", () => {
      const def: import("./workflow-api").WorkflowDefinitionResponse = {
        id: "wf-1", code: "WF-1", name: "Test", status: "DRAFT",
        triggerType: "MANUAL", module: "GENERAL", version: 1, versionLock: 0, createdBy: "u-1",
      };
      expect(def.code).toBe("WF-1");
      expect(def.status).toBe("DRAFT");
    });

    it("WorkflowInstanceResponse has all required fields", () => {
      const inst: import("./workflow-api").WorkflowInstanceResponse = {
        id: "inst-1", workflowDefinitionId: "wf-1", workflowVersion: 1,
        businessEntityType: "DECISION", businessEntityId: "dec-1",
        status: "RUNNING", currentStepKey: "REVIEW", startedBy: "u-1", version: 0,
      };
      expect(inst.businessEntityType).toBe("DECISION");
      expect(inst.currentStepKey).toBe("REVIEW");
    });

    it("WorkflowApprovalResponse has all required fields", () => {
      const app: import("./workflow-api").WorkflowApprovalResponse = {
        id: "app-1", workflowInstanceId: "inst-1", workflowStepInstanceId: "step-1",
        requestedFromUserId: "u-1", status: "PENDING", decision: "",
        comments: "", version: 0,
      };
      expect(app.status).toBe("PENDING");
    });

    it("WorkflowMonitoringHealthResponse has all required fields", () => {
      const h: import("./workflow-api").WorkflowMonitoringHealthResponse = {
        status: "OK", tenantId: "t-1",
        overdueSteps: 0, overdueApprovals: 0, totalBreaches: 0,
      };
      expect(h.totalBreaches).toBe(0);
    });
  });
});
