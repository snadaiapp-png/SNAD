// @vitest-environment jsdom

/**
 * R0C-12 HUMAN-UAT CORRECTIVE — Subscription detail mutation gating (RED first).
 *
 * Mission Blocker C / R4 / R5 / UAT-09 / UAT-10:
 *   - a read-only executive viewer must NEVER receive an enabled lifecycle
 *     mutation control (ACTIVATE/RENEW/PAUSE/RESUME/SUSPEND/CANCEL/TERMINATE)
 *     or the plan-change confirm control;
 *   - an admin must receive them (R5 / UAT-10);
 *   - gating comes from the unified useScpAccess() source (access-check/v2),
 *     mapping to the granular subscription.* write codes that are co-granted
 *     with the backend's EXECUTIVE_MANAGE authority (V20260830_2 construction).
 */
import "@testing-library/jest-dom/vitest";
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";

const accessCheckMock = vi.fn();
const plansMock = vi.fn();

vi.mock("@/lib/api/scp-api", () => ({
  scpApi: {
    accessCheckV2: (...args: unknown[]) => accessCheckMock(...args),
    subscriptionDetail: vi.fn().mockResolvedValue({
      id: "s-1",
      overview: { tenantId: "t-1", status: "ACTIVE", currencyCode: "SAR" },
      items: [],
      entitlements: [],
      invoices: [],
      changes: [],
      provisioningJobs: [],
      audit: [],
    }),
    subscriptionItems: vi.fn().mockResolvedValue([]),
    usage: vi.fn().mockResolvedValue([]),
    planVersions: vi.fn().mockResolvedValue([]),
    previewChange: vi.fn(),
    executeChange: vi.fn(),
    lifecycleCommand: vi.fn(),
  },
}));

vi.mock("@/lib/api/executive-api", () => ({
  executiveApi: {
    plans: (...args: unknown[]) => plansMock(...args),
  },
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

vi.mock("next/navigation", () => ({
  useParams: () => ({ id: "s-1" }),
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
}));

vi.mock("next/link", () => ({
  default: ({ children }: { children: React.ReactNode }) => <a href="#">{children}</a>,
}));

vi.mock("../../_components/format", () => ({
  useScpFormat: () => ({
    day: (value: unknown) => (value ? String(value) : "—"),
    money: () => "0",
    number: (value: number) => String(value),
  }),
}));

import { ScpAccessProvider } from "../../_components/ScpAccess";
import SubscriptionDetailPage from "./page";

const ADMIN_MAP = {
  authenticated: true,
  capabilities: {
    "subscription.read": true,
    "subscription.create": true,
    "subscription.change_plan": true,
    "subscription.cancel": true,
    "subscription.suspend": true,
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
    "subscription.read": true,
    "entitlement.read": true,
    "provisioning.read": true,
    "audit.read": true,
    "subscription.create": false,
    "subscription.change_plan": false,
    "subscription.cancel": false,
    "subscription.suspend": false,
    "entitlement.manage": false,
    "provisioning.retry": false,
  },
};

beforeEach(() => {
  accessCheckMock.mockReset();
  plansMock.mockReset();
  plansMock.mockResolvedValue([]);
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
    await Promise.resolve();
  });
}

describe("Subscription detail — mutation capability gating (Blocker C)", () => {
  it("R4/UAT-09 RED: read-only viewer receives NO lifecycle mutation controls", async () => {
    accessCheckMock.mockResolvedValueOnce(READ_ONLY_MAP);
    render(
      <ScpAccessProvider>
        <SubscriptionDetailPage />
      </ScpAccessProvider>,
    );
    await settle();
    for (const command of ["ACTIVATE", "RENEW", "PAUSE", "RESUME", "SUSPEND", "CANCEL", "TERMINATE"]) {
      expect(screen.queryByRole("button", { name: command })).not.toBeInTheDocument();
    }
    // plan-change confirm control must not exist either
    expect(screen.queryByRole("button", { name: "scp.detail.confirmChange" })).not.toBeInTheDocument();
  });

  it("R5/UAT-10: admin receives the lifecycle mutation controls", async () => {
    accessCheckMock.mockResolvedValueOnce(ADMIN_MAP);
    render(
      <ScpAccessProvider>
        <SubscriptionDetailPage />
      </ScpAccessProvider>,
    );
    await settle();
    for (const command of ["ACTIVATE", "RENEW", "PAUSE", "RESUME", "SUSPEND", "CANCEL", "TERMINATE"]) {
      const button = screen.getByRole("button", { name: command });
      expect(button).toBeEnabled();
    }
  });

  it("loads selectable plans on entry so plan change is reachable before preview", async () => {
    accessCheckMock.mockResolvedValueOnce(ADMIN_MAP);
    plansMock.mockResolvedValueOnce([
      {
        id: "plan-growth",
        code: "GROWTH",
        name: "Growth",
        status: "ACTIVE",
        currencyCode: "SAR",
        monthlyPriceMinor: 29900,
        annualPriceMinor: 299000,
        trialDays: 14,
        maxUsers: 25,
        maxOrganizations: 5,
        storageMb: 10240,
        entitlements: [],
        description: null,
        createdAt: "2026-09-01T00:00:00Z",
        updatedAt: "2026-09-01T00:00:00Z",
      },
    ]);

    render(
      <ScpAccessProvider>
        <SubscriptionDetailPage />
      </ScpAccessProvider>,
    );
    await settle();

    expect(plansMock).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("option", { name: "Growth (GROWTH)" })).toBeInTheDocument();
  });

  it("FAIL-CLOSED: while the access check is in flight no mutation control is enabled", async () => {
    accessCheckMock.mockReturnValueOnce(new Promise(() => undefined)); // never resolves
    render(
      <ScpAccessProvider>
        <SubscriptionDetailPage />
      </ScpAccessProvider>,
    );
    await settle();
    for (const command of ["ACTIVATE", "RENEW", "PAUSE", "RESUME", "SUSPEND", "CANCEL", "TERMINATE"]) {
      expect(screen.queryByRole("button", { name: command })).not.toBeInTheDocument();
    }
  });
});
