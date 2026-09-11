// @vitest-environment jsdom

/**
 * R0C-12 HUMAN-UAT CORRECTIVE — Entitlements + Provisioning mutation gating (RED first).
 *
 * Mission Blocker C / F / R4 / R5:
 *   - Entitlements "Recalculate" (backend authority EXECUTIVE_MANAGE ⇒
 *     entitlement.manage) must be absent for read-only viewers;
 *   - Provisioning "Retry" (backend authority EXECUTIVE_MANAGE ⇒
 *     provisioning.retry) must be absent for read-only viewers while job
 *     status remains fully visible;
 *   - empty job list renders the explicit empty state (UAT-07) and the page
 *     must never fabricate jobs or mutate state machine semantics.
 */
import "@testing-library/jest-dom/vitest";
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";

const accessCheckMock = vi.fn();

vi.mock("@/lib/api/scp-api", () => ({
  scpApi: {
    accessCheckV2: (...args: unknown[]) => accessCheckMock(...args),
    provisioningJobs: vi.fn().mockResolvedValue([]),
    retryProvisioningJob: vi.fn(),
    tenants: vi.fn().mockResolvedValue({ content: [], page: 0, size: 8, totalElements: 0, totalPages: 0 }),
  },
}));

vi.mock("@/lib/api/executive-api", () => ({
  executiveApi: {
    modules: vi.fn().mockResolvedValue([]),
    tenantEntitlements: vi.fn().mockResolvedValue([]),
    recalculateEntitlements: vi.fn(),
  },
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

vi.mock("../_components/format", () => ({
  useScpFormat: () => ({
    day: (value: unknown) => (value ? String(value) : "—"),
    money: () => "0",
    number: (value: number) => String(value),
  }),
}));

import { ScpAccessProvider } from "./ScpAccess";
import ProvisioningPage from "../provisioning/page";
import EntitlementsPage from "../entitlements/page";

const ADMIN_MAP = {
  authenticated: true,
  capabilities: {
    "subscription.read": true,
    "entitlement.read": true,
    "entitlement.manage": true,
    "provisioning.read": true,
    "provisioning.retry": true,
    "audit.read": true,
  },
};

const READ_ONLY_MAP = {
  authenticated: true,
  capabilities: {
    "entitlement.read": true,
    "entitlement.manage": false,
    "provisioning.read": true,
    "provisioning.retry": false,
  },
};

const FAILED_JOB = {
  id: "job-1",
  tenantId: "t-1",
  subscriptionId: "s-1",
  action: "PROVISION_SUBSCRIPTION",
  status: "FAILED",
  attempts: 2,
  startedAt: "2026-09-09T00:00:00Z",
  completedAt: null,
  errorCode: "E_TEST",
  createdAt: "2026-09-09T00:00:00Z",
};

beforeEach(async () => {
  accessCheckMock.mockReset();
  // Fresh per-test api mocks (mockResolvedValue is sticky across tests).
  const { scpApi } = await import("@/lib/api/scp-api");
  (scpApi.provisioningJobs as ReturnType<typeof vi.fn>).mockReset();
  (scpApi.provisioningJobs as ReturnType<typeof vi.fn>).mockResolvedValue([]);
  vi.spyOn(console, "error").mockImplementation(() => undefined);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

async function settle() {
  const { act } = await import("@testing-library/react");
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
}

describe("Provisioning — retry gating (Blocker F)", () => {
  it("UAT-08: admin sees the retry control for a FAILED job", async () => {
    const { scpApi } = await import("@/lib/api/scp-api");
    (scpApi.provisioningJobs as ReturnType<typeof vi.fn>).mockResolvedValue([FAILED_JOB]);
    accessCheckMock.mockResolvedValueOnce(ADMIN_MAP);
    render(
      <ScpAccessProvider>
        <ProvisioningPage />
      </ScpAccessProvider>,
    );
    await settle();
    const retry = screen.getByRole("button", { name: "scp.provisioning.retry" });
    expect(retry).toBeEnabled();
  });

  it("R4 RED: read-only viewer sees the job status but NO retry control", async () => {
    const { scpApi } = await import("@/lib/api/scp-api");
    (scpApi.provisioningJobs as ReturnType<typeof vi.fn>).mockResolvedValue([FAILED_JOB]);
    accessCheckMock.mockResolvedValueOnce(READ_ONLY_MAP);
    render(
      <ScpAccessProvider>
        <ProvisioningPage />
      </ScpAccessProvider>,
    );
    await settle();
    // status remains fully visible (read surface intact)
    expect(screen.getByText("PROVISION_SUBSCRIPTION")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "scp.provisioning.retry" })).not.toBeInTheDocument();
  });

  it("UAT-07: no jobs renders the explicit empty state (never fabricated data, never an error)", async () => {
    accessCheckMock.mockResolvedValueOnce(READ_ONLY_MAP);
    render(
      <ScpAccessProvider>
        <ProvisioningPage />
      </ScpAccessProvider>,
    );
    await settle();
    expect(screen.getByText("scp.state.empty")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "scp.provisioning.retry" })).not.toBeInTheDocument();
  });
});

describe("Entitlements — recalculate gating (Blocker C)", () => {
  it("R4 RED: read-only viewer has NO Recalculate control", async () => {
    accessCheckMock.mockResolvedValueOnce(READ_ONLY_MAP);
    render(
      <ScpAccessProvider>
        <EntitlementsPage />
      </ScpAccessProvider>,
    );
    await settle();
    expect(screen.queryByRole("button", { name: "scp.entitlements.recalculate" })).not.toBeInTheDocument();
    // view (read) control still available
    expect(screen.getByRole("button", { name: "scp.entitlements.view" })).toBeInTheDocument();
  });

  it("R5: admin has the Recalculate control", async () => {
    accessCheckMock.mockResolvedValueOnce(ADMIN_MAP);
    render(
      <ScpAccessProvider>
        <EntitlementsPage />
      </ScpAccessProvider>,
    );
    await settle();
    expect(screen.getByRole("button", { name: "scp.entitlements.recalculate" })).toBeInTheDocument();
  });
});
