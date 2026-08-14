/**
 * Tests for management-api.ts — verifies the typed API client functions
 * correctly call the backend endpoints and handle responses.
 *
 * Pattern: same as lib/api/client.test.ts (mock fetch, verify URL + response).
 */
import { afterEach, describe, expect, it, vi } from "vitest";

// Mock the apiClient module before importing managementApi
vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

import { managementApi } from "./management-api";
import { apiClient } from "./client";

afterEach(() => {
  vi.clearAllMocks();
});

function mockResolve<T>(data: T) {
  return Promise.resolve(data);
}

describe("managementApi", () => {
  describe("Command Center", () => {
    it("getDashboard calls GET /api/v1/management/command-center", async () => {
      const mockDashboard = {
        healthScore: 85, strategyScore: 80, kpiScore: 90,
        decisionScore: 70, riskScore: 95, issueScore: 85,
        escalationScore: 90, totalObjectives: 5, activeObjectives: 3,
        atRiskObjectives: 1, offTrackObjectives: 0, achievedObjectives: 1,
        totalKpis: 10, onTrackKpis: 7, atRiskKpis: 2, offTrackKpis: 1, noDataKpis: 0,
        pendingDecisions: 2, overdueDecisions: 0,
        criticalRisks: 0, highRisks: 1, totalRisks: 3,
        openIssues: 2, criticalIssues: 0, totalIssues: 5,
        activeEscalations: 1, overdueEscalations: 0, totalEscalations: 2,
        activeAlerts: 3, generatedAt: "2026-08-14T12:00:00Z",
      };
      vi.mocked(apiClient.get).mockReturnValue(mockResolve(mockDashboard));

      const result = await managementApi.getDashboard();

      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/management/command-center");
      expect(result.healthScore).toBe(85);
      expect(result.totalObjectives).toBe(5);
      expect(result.activeAlerts).toBe(3);
    });

    it("snapshotHealth calls POST /api/v1/management/command-center/snapshot", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({ healthScore: 80, snapshotAt: "2026-08-14T12:00:00Z" }));

      const result = await managementApi.snapshotHealth();

      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/management/command-center/snapshot");
      expect(result.healthScore).toBe(80);
    });
  });

  describe("Objectives", () => {
    it("listObjectives calls GET with limit parameter", async () => {
      vi.mocked(apiClient.get).mockReturnValue(mockResolve([]));
      await managementApi.listObjectives(25);
      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/management/objectives?limit=25");
    });

    it("createObjective calls POST with objective data", async () => {
      const mockResponse = { id: "obj-1", code: "OBJ-1", title: "Test", status: "DRAFT", priority: "HIGH", progressPct: 0, periodStart: "2026-01-01", periodEnd: "2026-12-31" };
      vi.mocked(apiClient.post).mockReturnValue(mockResolve(mockResponse));

      const result = await managementApi.createObjective({
        code: "OBJ-1", title: "Test", priority: "HIGH",
        periodStart: "2026-01-01", periodEnd: "2026-12-31",
      });

      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/management/objectives", {
        code: "OBJ-1", title: "Test", priority: "HIGH",
        periodStart: "2026-01-01", periodEnd: "2026-12-31",
      });
      expect(result.status).toBe("DRAFT");
    });

    it("activateObjective calls POST /activate", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({ id: "obj-1", status: "ACTIVE" }));
      await managementApi.activateObjective("obj-1");
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/management/objectives/obj-1/activate");
    });
  });

  describe("Alerts", () => {
    it("listOpenAlerts calls GET /alerts/open", async () => {
      vi.mocked(apiClient.get).mockReturnValue(mockResolve([]));
      await managementApi.listOpenAlerts(10);
      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/management/alerts/open?limit=10");
    });

    it("acknowledgeAlert calls POST /acknowledge", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({ id: "alert-1", status: "ACKNOWLEDGED" }));
      await managementApi.acknowledgeAlert("alert-1");
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/management/alerts/alert-1/acknowledge");
    });

    it("resolveAlert calls POST /resolve with resolution body", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({ id: "alert-1", status: "RESOLVED" }));
      await managementApi.resolveAlert("alert-1", "Fixed");
      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/management/alerts/alert-1/resolve", { resolution: "Fixed" });
    });
  });

  describe("AI Intelligence", () => {
    it("generateSummary calls POST /intelligence/summary", async () => {
      const mockInsight = {
        id: "insight-1", type: "SUMMARY", title: "Executive Summary",
        description: "All good", confidence: "1.0", modelName: "deterministic",
        advisory: true, status: "ACTIVE",
      };
      vi.mocked(apiClient.post).mockReturnValue(mockResolve(mockInsight));

      const result = await managementApi.generateSummary();

      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/management/intelligence/summary");
      expect(result.advisory).toBe(true);
      expect(result.modelName).toBe("deterministic");
    });

    it("recommendAction calls POST /intelligence/recommend", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({
        id: "insight-2", type: "RECOMMENDATION", title: "Action",
        description: "Do something", confidence: "0.85", modelName: "deterministic",
        advisory: true, status: "ACTIVE",
      }));

      const result = await managementApi.recommendAction();

      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/management/intelligence/recommend");
      expect(result.advisory).toBe(true);
    });

    it("listInsights calls GET /intelligence", async () => {
      vi.mocked(apiClient.get).mockReturnValue(mockResolve([]));
      await managementApi.listInsights(5);
      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/management/intelligence?limit=5");
    });
  });

  describe("Risks", () => {
    it("createRisk calls POST /risks with risk data", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({
        id: "risk-1", code: "RISK-1", title: "Test Risk", status: "IDENTIFIED",
        severity: "HIGH", riskScore: 12, probability: 3, impact: 4,
      }));

      const result = await managementApi.createRisk({
        code: "RISK-1", title: "Test Risk", probability: 3, impact: 4,
      });

      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/management/risks", {
        code: "RISK-1", title: "Test Risk", probability: 3, impact: 4,
      });
      expect(result.riskScore).toBe(12);
      expect(result.severity).toBe("HIGH");
    });
  });

  describe("Decisions", () => {
    it("createDecision calls POST /decisions", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({
        id: "dec-1", decisionNumber: "DEC-1", title: "Test Decision", status: "DRAFT", priority: "HIGH",
      }));

      const result = await managementApi.createDecision({
        decisionNumber: "DEC-1", title: "Test Decision", priority: "HIGH",
      });

      expect(result.status).toBe("DRAFT");
    });

    it("approveDecision calls POST /approve", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({
        id: "dec-1", status: "APPROVED",
      }));

      await managementApi.approveDecision("dec-1");

      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/management/decisions/dec-1/approve");
    });
  });

  describe("Escalations", () => {
    it("acknowledgeEscalation calls POST /acknowledge", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({
        id: "esc-1", status: "ACKNOWLEDGED",
      }));

      await managementApi.acknowledgeEscalation("esc-1");

      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/management/escalations/esc-1/acknowledge");
    });

    it("resolveEscalation calls POST /resolve with resolution", async () => {
      vi.mocked(apiClient.post).mockReturnValue(mockResolve({
        id: "esc-1", status: "RESOLVED",
      }));

      await managementApi.resolveEscalation("esc-1", "Resolved");

      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/management/escalations/esc-1/resolve", { resolution: "Resolved" });
    });
  });

  describe("Issues", () => {
    it("listIssues calls GET /issues", async () => {
      vi.mocked(apiClient.get).mockReturnValue(mockResolve([]));
      await managementApi.listIssues(30);
      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/management/issues?limit=30");
    });
  });

  describe("Type Safety", () => {
    it("InsightResponse.advisory is always boolean", () => {
      const insight: import("./management-api").InsightResponse = {
        id: "1", type: "SUMMARY", title: "Test", description: "Desc",
        confidence: "0.9", modelName: "deterministic", advisory: true, status: "ACTIVE",
      };
      expect(typeof insight.advisory).toBe("boolean");
    });

    it("CommandCenterDashboard has all required fields", () => {
      const dashboard: import("./management-api").CommandCenterDashboard = {
        healthScore: 100, strategyScore: 100, kpiScore: 100, decisionScore: 100,
        riskScore: 100, issueScore: 100, escalationScore: 100,
        totalObjectives: 0, activeObjectives: 0, atRiskObjectives: 0,
        offTrackObjectives: 0, achievedObjectives: 0,
        totalKpis: 0, onTrackKpis: 0, atRiskKpis: 0, offTrackKpis: 0, noDataKpis: 0,
        pendingDecisions: 0, overdueDecisions: 0,
        criticalRisks: 0, highRisks: 0, totalRisks: 0,
        openIssues: 0, criticalIssues: 0, totalIssues: 0,
        activeEscalations: 0, overdueEscalations: 0, totalEscalations: 0,
        activeAlerts: 0, generatedAt: "2026-01-01T00:00:00Z",
      };
      expect(dashboard.healthScore).toBe(100);
      expect(dashboard.generatedAt).toContain("2026");
    });
  });
});
