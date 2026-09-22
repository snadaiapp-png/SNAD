// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const subscriptionsMock = vi.fn();
const provisionMock = vi.fn();
const plansMock = vi.fn();
const createSubscriptionMock = vi.fn();
const pushMock = vi.fn();

vi.mock("@/lib/api/scp-api", () => ({
  scpApi: {
    subscriptions: (...args: unknown[]) => subscriptionsMock(...args),
    provision: (...args: unknown[]) => provisionMock(...args),
  },
}));

vi.mock("@/lib/api/executive-api", () => ({
  executiveApi: {
    plans: (...args: unknown[]) => plansMock(...args),
    createSubscription: (...args: unknown[]) => createSubscriptionMock(...args),
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
  scpErrorMessage: () => "SAFE_SUBSCRIPTIONS_ERROR",
}));

import SubscriptionsPage from "./page";

const EMPTY_PAGE = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
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

describe("Subscription tenant upgrade creation flow", () => {
  beforeEach(() => {
    subscriptionsMock.mockReset().mockResolvedValue(EMPTY_PAGE);
    provisionMock.mockReset().mockResolvedValue({
      jobId: "job-1",
      status: "SUCCEEDED",
      skippedSteps: [],
    });
    plansMock.mockReset().mockResolvedValue([ACTIVE_PLAN]);
    createSubscriptionMock.mockReset().mockResolvedValue({
      id: "sub-1",
      tenantId: "11111111-1111-1111-1111-111111111111",
      status: "TRIALING",
    });
    pushMock.mockReset();
  });

  afterEach(() => cleanup());

  it("creates only from an existing ACTIVE plan and immediately starts provisioning", async () => {
    const user = userEvent.setup();
    render(<SubscriptionsPage />);

    const createButton = await screen.findByRole("button", {
      name: "scp.subscriptions.createAndProvision",
    });
    await waitFor(() => expect(plansMock).toHaveBeenCalledTimes(1));
    expect(screen.getByRole("option", { name: "Starter (STARTER)" })).toBeInTheDocument();

    await user.click(createButton);

    await waitFor(() => expect(createSubscriptionMock).toHaveBeenCalledWith({
      tenantId: "11111111-1111-1111-1111-111111111111",
      planId: "plan-1",
      billingCycle: "MONTHLY",
      seatQuantity: 1,
      trialDays: 14,
    }));
    expect(provisionMock).toHaveBeenCalledWith("sub-1");
    expect(pushMock).toHaveBeenCalledWith(
      "/executive/subscriptions/sub-1?tenantId=11111111-1111-1111-1111-111111111111",
    );
  });

  it("keeps a provisioning failure visible instead of navigating away", async () => {
    const user = userEvent.setup();
    provisionMock.mockResolvedValue({ jobId: "job-2", status: "FAILED", skippedSteps: [] });
    render(<SubscriptionsPage />);

    await user.click(await screen.findByRole("button", {
      name: "scp.subscriptions.createAndProvision",
    }));

    await waitFor(() => expect(provisionMock).toHaveBeenCalledWith("sub-1"));
    expect(pushMock).not.toHaveBeenCalled();
    expect(await screen.findByText("scp.subscriptions.createdProvisionPending")).toBeInTheDocument();
  });
  it("does not expose retired plans as subscription creation choices", async () => {
    plansMock.mockResolvedValue([
      ACTIVE_PLAN,
      { ...ACTIVE_PLAN, id: "retired", code: "OLD", name: "Old", status: "RETIRED" },
    ]);
    render(<SubscriptionsPage />);

    await screen.findByRole("option", { name: "Starter (STARTER)" });
    expect(screen.queryByRole("option", { name: "Old (OLD)" })).not.toBeInTheDocument();
  });
});
