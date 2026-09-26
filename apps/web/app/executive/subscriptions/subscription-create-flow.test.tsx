// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// Contract tests for the canonical operator upgrade flow on the subscriptions
// list page. Main's upgrade semantics are authoritative: one upgrade path,
// fail-closed provisioning, explicit commercial activation (never an implicit
// trial), and fail-closed plan-version currency eligibility.
const subscriptionsMock = vi.fn();
const provisionMock = vi.fn();
const plansMock = vi.fn();
const planVersionsMock = vi.fn();
const tenantMock = vi.fn();
const createSubscriptionMock = vi.fn();
const changeTenantStatusMock = vi.fn();
const pushMock = vi.fn();

vi.mock("@/lib/api/scp-api", () => ({
  scpApi: {
    subscriptions: (...args: unknown[]) => subscriptionsMock(...args),
    planVersions: (...args: unknown[]) => planVersionsMock(...args),
    provision: (...args: unknown[]) => provisionMock(...args),
  },
}));

vi.mock("@/lib/api/executive-api", () => ({
  executiveApi: {
    plans: (...args: unknown[]) => plansMock(...args),
    tenant: (...args: unknown[]) => tenantMock(...args),
    createSubscription: (...args: unknown[]) => createSubscriptionMock(...args),
    changeTenantStatus: (...args: unknown[]) => changeTenantStatusMock(...args),
  },
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ prefetch: vi.fn(), push: pushMock }),
  useSearchParams: () => new URLSearchParams(
    "tenantId=11111111-1111-1111-1111-111111111111&intent=upgrade",
  ),
}));

vi.mock("../_components/ScpAccess", () => ({
  useScpAccess: () => ({
    has: (capability: string) => capability === "EXECUTIVE_MANAGE",
  }),
}));

vi.mock("../_components/format", () => ({
  useScpFormat: () => ({
    money: (value: number, currency: string) => `${value} ${currency}`,
    day: (value: string) => value,
  }),
}));

vi.mock("../_components/scp-errors", () => ({
  scpErrorMessage: (reason: unknown) =>
    reason instanceof Error ? reason.message : "SAFE_SUBSCRIPTIONS_ERROR",
}));

import SubscriptionsPage from "./page";

const EMPTY_PAGE = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
};

const TENANT_ID = "11111111-1111-1111-1111-111111111111";

const PENDING_TENANT = {
  id: TENANT_ID,
  name: "Upgrade Target Tenant",
  legalName: null,
  subdomain: "upgrade-target",
  status: "PENDING",
  billingEmail: null,
  countryCode: "SA",
  locale: "ar-SA",
  timezone: "Asia/Riyadh",
  currencyCode: "SAR",
  trialEndsAt: null,
  suspensionReason: null,
  createdAt: "2026-09-01T00:00:00Z",
  updatedAt: "2026-09-01T00:00:00Z",
};

const ACTIVE_PLAN = {
  id: "plan-1",
  code: "STARTER",
  name: "Starter",
  description: null,
  status: "ACTIVE",
  currencyCode: "SAR",
  monthlyPriceMinor: 1000,
  annualPriceMinor: 10000,
  trialDays: 14,
  maxUsers: 10,
  maxOrganizations: 5,
  storageMb: 1024,
  entitlements: [],
  createdAt: "2026-09-01T00:00:00Z",
  updatedAt: "2026-09-01T00:00:00Z",
};

const ACTIVE_VERSION = {
  id: "version-1",
  planId: "plan-1",
  versionNumber: 1,
  status: "ACTIVE",
};

beforeEach(() => {
  subscriptionsMock.mockReset().mockResolvedValue(EMPTY_PAGE);
  provisionMock.mockReset().mockResolvedValue({
    jobId: "job-1",
    status: "SUCCEEDED",
    skippedSteps: [],
  });
  plansMock.mockReset().mockResolvedValue([ACTIVE_PLAN]);
  planVersionsMock.mockReset().mockResolvedValue([ACTIVE_VERSION]);
  tenantMock.mockReset().mockResolvedValue(PENDING_TENANT);
  createSubscriptionMock.mockReset().mockResolvedValue({
    id: "sub-1",
    tenantId: TENANT_ID,
    status: "TRIALING",
  });
  changeTenantStatusMock.mockReset().mockResolvedValue({
    ...PENDING_TENANT,
    status: "ACTIVE",
  });
  pushMock.mockReset();
});

afterEach(() => cleanup());

describe("Canonical operator upgrade eligibility and fail-closed provisioning", () => {
  it("offers only plans whose canonical version is ACTIVE, never retired plans", async () => {
    plansMock.mockResolvedValue([
      ACTIVE_PLAN,
      { ...ACTIVE_PLAN, id: "retired", code: "OLD", name: "Old", status: "RETIRED" },
    ]);
    render(<SubscriptionsPage />);

    await screen.findByRole("heading", { name: "scp.tenants.upgrade" });
    await waitFor(() => expect(planVersionsMock).toHaveBeenCalledWith("plan-1"));
    expect(screen.getByRole("option", { name: "Starter (STARTER)" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Old (OLD)" })).not.toBeInTheDocument();
    expect(planVersionsMock).not.toHaveBeenCalledWith("retired");
  });

  it("fails closed on plans without an active version: nothing selectable", async () => {
    planVersionsMock.mockResolvedValue([
      { ...ACTIVE_VERSION, status: "RETIRED" },
    ]);
    render(<SubscriptionsPage />);

    // No plan has a live version → no upgrade panel is rendered at all;
    // the governed empty state takes its place (fail-closed).
    await waitFor(() => expect(planVersionsMock).toHaveBeenCalledWith("plan-1"));
    expect(screen.queryByRole("option", { name: "Starter (STARTER)" })).not.toBeInTheDocument();
    expect(await screen.findByText("scp.state.empty")).toBeInTheDocument();
  });

  it("never sends an implicit trial: operator upgrade is explicit commercial activation", async () => {
    const user = userEvent.setup();
    render(<SubscriptionsPage />);

    await screen.findByRole("heading", { name: "scp.tenants.upgrade" });
    await user.click(await screen.findByRole("button", { name: "scp.tenants.upgrade" }));

    await waitFor(() => expect(createSubscriptionMock).toHaveBeenCalledWith({
      tenantId: TENANT_ID,
      planId: "plan-1",
      billingCycle: "MONTHLY",
      seatQuantity: 1,
      trialDays: 0,
    }));
  });

  it("fails closed when provisioning does not succeed: error surfaced, no navigation, no pending notice", async () => {
    const user = userEvent.setup();
    provisionMock.mockResolvedValue({ jobId: "job-2", status: "FAILED", skippedSteps: [] });
    render(<SubscriptionsPage />);

    await screen.findByRole("heading", { name: "scp.tenants.upgrade" });
    await user.click(await screen.findByRole("button", { name: "scp.tenants.upgrade" }));

    await waitFor(() => expect(provisionMock).toHaveBeenCalledWith("sub-1"));
    expect(pushMock).not.toHaveBeenCalled();
    expect(changeTenantStatusMock).not.toHaveBeenCalled();
    expect(await screen.findByText("Subscription provisioning did not succeed")).toBeInTheDocument();
    expect(screen.queryByText("scp.subscriptions.createdProvisionPending")).not.toBeInTheDocument();
  });
});
