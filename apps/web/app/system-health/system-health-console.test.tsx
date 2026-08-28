// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SystemHealthDashboard } from "./system-health-console";

const { snapshotMock, systemsMock, executeMock } = vi.hoisted(() => ({
  snapshotMock: vi.fn(),
  systemsMock: vi.fn(),
  executeMock: vi.fn(),
}));

vi.mock("@/lib/auth/auth-provider", () => ({
  useAuth: () => ({ state: "AUTHENTICATED" }),
}));

vi.mock("@/lib/api/system-health-api", () => ({
  systemHealthApi: {
    snapshot: snapshotMock,
    systems: systemsMock,
    execute: executeMock,
  },
}));

const healthFixture = {
  generatedAt: "2026-08-28T18:00:00Z",
  overallStatus: "DEGRADED",
  healthScore: 86,
  riskLevel: "MEDIUM",
  predictionSummary: "يوجد ضغط تشغيلي محدود يستلزم المراقبة.",
  partial: true,
  dataCompletenessScore: 75,
  degradedComponents: ["SERVICE_METRICS"],
  collectionErrors: [{
    component: "SERVICE_METRICS",
    code: "HEALTH-METRIC-003",
    message: "Service metrics unavailable",
    correlationId: "corr-123",
    timestamp: "2026-08-28T17:59:00Z",
  }],
  runtime: {
    cpuLoadPercent: 17,
    memoryUsagePercent: 42,
    memoryUsedMb: 840,
    memoryMaxMb: 2048,
    uptimeSeconds: 7200,
    availableProcessors: 4,
  },
  dataPressure: {
    pressureScore: 23,
    status: "NORMAL",
    trackedRows: 1532,
    auditEventsLastHour: 18,
    failedAuditEventsLastHour: 1,
    openInvoices: 4,
    activeUsers: 27,
    message: "ضغط البيانات والعمليات ضمن الحدود الطبيعية",
  },
  services: [{
    id: "00000000-0000-0000-0000-000000000001",
    code: "API",
    name: "SNAD Platform API",
    environment: "production",
    status: "OPERATIONAL",
    criticality: "CRITICAL",
    healthScore: 98,
    pressureScore: 21,
    riskLevel: "LOW",
    latencyMs: 42,
    lastMessage: "Healthy",
    lastCheckedAt: "2026-08-28T17:59:30Z",
    predictedStatus: "STABLE",
  }],
  tenants: [{
    tenantId: "00000000-0000-0000-0000-000000000010",
    tenantName: "SNAD Control Plane",
    tenantStatus: "HEALTHY",
    healthScore: 93,
    pressureScore: 18,
    riskLevel: "LOW",
    users: 12,
    organizations: 2,
    memberships: 14,
    invoices: 8,
    openInvoices: 1,
    seatCapacity: 50,
    seatUtilizationPercent: 24,
    trackedRecords: 300,
    prediction: "STABLE",
  }],
  forecast: [
    { horizonMinutes: 15, riskScore: 14, riskLevel: "LOW", label: "+15 دقيقة" },
    { horizonMinutes: 60, riskScore: 22, riskLevel: "LOW", label: "+60 دقيقة" },
  ],
  availableActions: [
    {
      code: "RUN_DIAGNOSTICS",
      scope: "PLATFORM",
      title: "تشغيل التشخيصات",
      description: "تشغيل فحص تشخيصي شامل.",
      requiresTarget: false,
    },
  ],
};

const systemsFixture = [{
  id: "00000000-0000-0000-0000-000000000001",
  code: "API",
  name: "SNAD Platform API",
  environment: "production",
  status: "OPERATIONAL",
  ownerName: "Platform",
  criticality: "CRITICAL",
  lastCheckedAt: "2026-08-28T17:59:30Z",
  lastLatencyMs: 42,
  lastMessage: "Healthy",
}];

describe("SystemHealthDashboard", () => {
  beforeEach(() => {
    snapshotMock.mockReset();
    systemsMock.mockReset();
    executeMock.mockReset();
    snapshotMock.mockResolvedValue(healthFixture);
    systemsMock.mockResolvedValue(systemsFixture);
  });

  afterEach(() => cleanup());

  it("renders health scores as percentages without multiplying backend percentages again", async () => {
    render(<SystemHealthDashboard />);
    await waitFor(() => expect(snapshotMock).toHaveBeenCalledTimes(1));
    expect(await screen.findByText("98%")).toBeInTheDocument();
    expect(screen.queryByText(/9800/)).not.toBeInTheDocument();
  });

  it("surfaces runtime telemetry, data completeness, forecast, and collection degradation", async () => {
    render(<SystemHealthDashboard />);
    expect((await screen.findAllByText("جاهزية البيانات")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("75%").length).toBeGreaterThan(0);
    expect(screen.getByText("استخدام المعالج")).toBeInTheDocument();
    expect(screen.getByText("17%")).toBeInTheDocument();
    expect(screen.getByText("استخدام الذاكرة")).toBeInTheDocument();
    expect(screen.getByText("42%")).toBeInTheDocument();
    expect(screen.getByText("HEALTH-METRIC-003")).toBeInTheDocument();
    expect(screen.getByText("+15 دقيقة")).toBeInTheDocument();
    expect(screen.getByText("SNAD Control Plane")).toBeInTheDocument();
  });
});
