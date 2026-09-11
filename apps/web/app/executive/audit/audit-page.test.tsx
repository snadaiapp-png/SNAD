// @vitest-environment jsdom

/**
 * R0C-12 HUMAN-UAT CORRECTIVE — AuditPage rendering contract (RED first).
 *
 * Mission Blocker B / R3: the page must render the audit trail without a
 * runtime crash for BOTH the canonical camelCase contract and defensive
 * null/absent optional fields (resource_id is NULLABLE in platform_audit_logs).
 * The historical failure: `entry.resourceId.slice(0, 8)` threw
 * "Cannot read properties of undefined (reading 'slice')" whenever the
 * backend row lacked the camelCase `resourceId` key — surfacing the global
 * "Something went wrong" boundary instead of the data.
 */
import "@testing-library/jest-dom/vitest";
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";

const auditMock = vi.fn();

vi.mock("@/lib/api/scp-api", () => ({
  scpApi: {
    audit: (...args: unknown[]) => auditMock(...args),
  },
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

vi.mock("../../_components/format", () => ({
  useScpFormat: () => ({
    day: (value: unknown) => (value ? String(value) : "—"),
    money: () => "0",
    number: (value: number) => String(value),
  }),
}));

import AuditPage from "./page";

const CANONICAL_ROW = {
  id: "11111111-1111-1111-1111-111111111111",
  actorTenantId: "22222222-2222-2222-2222-222222222222",
  actorUserId: "33333333-3333-3333-3333-333333333333",
  targetTenantId: "22222222-2222-2222-2222-222222222222",
  action: "ENTITLEMENTS_RECALCULATED",
  resourceType: "TENANT_ENTITLEMENTS",
  resourceId: "44444444-4444-4444-4444-444444444444",
  reason: "uat",
  result: "SUCCESS",
  correlationId: "55555555-5555-5555-5555-555555555555",
  createdAt: "2026-09-09T00:00:00Z",
};

function pageOf(rows: unknown[]) {
  return { content: rows, page: 0, size: 20, totalElements: rows.length, totalPages: 1 };
}

beforeEach(() => {
  auditMock.mockReset();
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

describe("AuditPage — render contract", () => {
  it("UAT-05/06: renders canonical camelCase rows (date, action, resource, reason, result)", async () => {
    auditMock.mockResolvedValueOnce(pageOf([CANONICAL_ROW]));
    render(<AuditPage />);
    await settle();
    expect(screen.getByText("ENTITLEMENTS_RECALCULATED")).toBeInTheDocument();
    expect(screen.getByText(/TENANT_ENTITLEMENTS/)).toBeInTheDocument();
    expect(screen.getByText("uat")).toBeInTheDocument();
    expect(screen.getByText("SUCCESS")).toBeInTheDocument();
  });

  it("R3/RED: absent resourceId (legacy raw backend shape) must NOT crash the page", async () => {
    auditMock.mockResolvedValueOnce(pageOf([{ ...CANONICAL_ROW, resourceId: undefined }]));
    render(<AuditPage />);
    await settle();
    // The row still renders; the resource cell shows a placeholder, not a crash.
    expect(screen.getByText("ENTITLEMENTS_RECALCULATED")).toBeInTheDocument();
  });

  it("R3/RED: null resourceId (NULLABLE resource_id column) renders without crashing", async () => {
    auditMock.mockResolvedValueOnce(pageOf([{ ...CANONICAL_ROW, resourceId: null }]));
    render(<AuditPage />);
    await settle();
    expect(screen.getByText("ENTITLEMENTS_RECALCULATED")).toBeInTheDocument();
  });

  it("Blocker G: empty dataset renders the explicit empty state — never an application error", async () => {
    auditMock.mockResolvedValueOnce(pageOf([]));
    render(<AuditPage />);
    await settle();
    expect(screen.getByText("scp.state.empty")).toBeInTheDocument();
  });

  it("Blocker G: degraded API renders a retryable error, never a blank page or crash", async () => {
    auditMock.mockRejectedValueOnce(new Error("HTTP 503"));
    render(<AuditPage />);
    await settle();
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });
});
