/**
 * Tests for ai-api.ts — verifies the typed API client functions.
 */
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("./client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

import { aiApi } from "./ai-api";
import { apiClient } from "./client";

afterEach(() => { vi.clearAllMocks(); });

function mockResolve<T>(data: T) { return Promise.resolve(data); }

describe("aiApi", () => {
  describe("Agents", () => {
    it("listAgents calls GET /agents with limit", async () => {
      vi.mocked(apiClient.get).mockReturnValue(mockResolve([]));
      await aiApi.listAgents(25);
      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/ai/agents?limit=25");
    });

    it("listAgents uses default limit=50", async () => {
      vi.mocked(apiClient.get).mockReturnValue(mockResolve([]));
      await aiApi.listAgents();
      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/ai/agents?limit=50");
    });

    it("getAgent calls GET /agents/:id", async () => {
      vi.mocked(apiClient.get).mockReturnValue(mockResolve({
        id: "a-1", code: "A1", name: "Test", status: "DRAFT",
        provider: "DETERMINISTIC", modelName: "", version: 0, versionLock: 0,
        createdBy: "u-1", advisoryOnly: true,
      }));
      const result = await aiApi.getAgent("a-1");
      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/ai/agents/a-1");
      expect(result.code).toBe("A1");
    });

    it("createAgent calls POST /agents with data", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({
        id: "a-1", code: "A1", name: "Test", status: "DRAFT",
        provider: "DETERMINISTIC", modelName: "", version: 0, versionLock: 0,
        createdBy: "u-1", advisoryOnly: true,
      }));
      await aiApi.createAgent({ code: "A1", name: "Test" });
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/ai/agents", { code: "A1", name: "Test" });
    });

    it("activateAgent calls POST /activate", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({ id: "a-1", status: "ACTIVE" }));
      await aiApi.activateAgent("a-1");
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/ai/agents/a-1/activate");
    });

    it("deactivateAgent calls POST /deactivate", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({ id: "a-1", status: "INACTIVE" }));
      await aiApi.deactivateAgent("a-1");
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/ai/agents/a-1/deactivate");
    });

    it("archiveAgent calls POST /archive", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({ id: "a-1", status: "ARCHIVED" }));
      await aiApi.archiveAgent("a-1");
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/ai/agents/a-1/archive");
    });
  });

  describe("Inferences", () => {
    it("listInferences calls GET /inferences with limit", async () => {
      vi.mocked(apiClient.get).mockReturnValue(mockResolve([]));
      await aiApi.listInferences(30);
      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/ai/inferences?limit=30");
    });

    it("getInference calls GET /inferences/:id", async () => {
      vi.mocked(apiClient.get).mockReturnValue(mockResolve({
        id: "inf-1", agentId: "a-1", invokedBy: "u-1", status: "COMPLETED",
        advisory: true, tokensInput: 10, tokensOutput: 20, latencyMs: 5,
        costCents: 0, createdAt: "2026-08-15T00:00:00Z",
      }));
      const result = await aiApi.getInference("inf-1");
      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/ai/inferences/inf-1");
      expect(result.status).toBe("COMPLETED");
    });

    it("listAgentInferences calls GET /agents/:id/inferences", async () => {
      vi.mocked(apiClient.get).mockReturnValue(mockResolve([]));
      await aiApi.listAgentInferences("a-1", 20);
      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/ai/agents/a-1/inferences?limit=20");
    });
  });

  describe("Execution", () => {
    it("execute calls POST /execute", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({
        id: "inf-1", agentId: "a-1", invokedBy: "u-1", status: "COMPLETED",
        advisory: true, tokensInput: 10, tokensOutput: 20, latencyMs: 5,
        costCents: 0, createdAt: "2026-08-15T00:00:00Z",
      }));
      await aiApi.execute({ agentId: "a-1", input: "test" });
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/ai/execute", { agentId: "a-1", input: "test" });
    });
  });

  describe("Quota", () => {
    it("getQuota calls GET /quota", async () => {
      vi.mocked(apiClient.get).mockReturnValue(mockResolve({
        tenantId: "t-1", usedThisMonth: 42, advisoryOnly: true,
      }));
      const result = await aiApi.getQuota();
      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/ai/quota");
      expect(result.usedThisMonth).toBe(42);
      expect(result.advisoryOnly).toBe(true);
    });
  });

  describe("Error Handling", () => {
    it("handles 401 errors", async () => {
      const err = new Error("Unauthorized") as Error & { status?: number };
      err.status = 401;
      vi.mocked(apiClient.get).mockReturnValue(Promise.reject(err));
      await expect(aiApi.listAgents()).rejects.toMatchObject({ status: 401 });
    });

    it("handles 403 errors", async () => {
      const err = new Error("Forbidden") as Error & { status?: number };
      err.status = 403;
      vi.mocked(apiClient.get).mockReturnValue(Promise.reject(err));
      await expect(aiApi.listInferences()).rejects.toMatchObject({ status: 403 });
    });
  });

  describe("Type Safety", () => {
    it("AiAgentResponse has advisoryOnly field", () => {
      const a: import("./ai-api").AiAgentResponse = {
        id: "a-1", code: "A1", name: "Test", status: "DRAFT",
        provider: "DETERMINISTIC", modelName: "", version: 0, versionLock: 0,
        createdBy: "u-1", advisoryOnly: true,
      };
      expect(a.advisoryOnly).toBe(true);
    });

    it("AiInferenceResponse has advisory field", () => {
      const i: import("./ai-api").AiInferenceResponse = {
        id: "inf-1", agentId: "a-1", invokedBy: "u-1", status: "COMPLETED",
        advisory: true, tokensInput: 0, tokensOutput: 0, latencyMs: 0,
        costCents: 0, createdAt: "2026-08-15T00:00:00Z",
      };
      expect(i.advisory).toBe(true);
    });
  });
});
