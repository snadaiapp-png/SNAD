// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const subscriptionsMock = vi.fn();
const provisionMock = vi.fn();
const plansMock = vi.fn();
const tenantMock = vi.fn();
const createSubscriptionMock = vi.fn();
const changeTenantStatusMock = vi.fn();
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
  useSearchParams: () =>
    new URLSearchParams(
      "tenantId=b195f190-b9a1-48e4-867b-c617fce8a098&intent=upgrade",
    ),
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

const emptyPage = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
};

const starterPlan = {
  id: "11111111-1111-4111-8111-111111111111",
  code: "STARTER",
  name: "Starter",
  description: null,
  status: "ACTIVE",
  currencyCode: "SAR",
  monthlyPriceMinor: 10000,
  annualPriceMinor: 100000,
  trialDays: 14,
  maxUsers: 10,
  maxOrganizations: 3,
  storageMb: 1024,
  entitlements: [],
  createdAt: "2026-09-23T00:00:00Z",
  updatedAt: "2026-09-23T00:00:00Z",
};

const pendingTenant = {
  id: "b195f190-b9a1-48e4-867b-c617fce8a098",
  name: "Almarai Saudi 22",
  legalName: null,
  subdomain: "dfghjj",
  status: "PENDING",
  billingEmail: null,
  countryCode: "SA",
  locale: "ar-SA",
  timezone: "Asia/Riyadh",
  currencyCode: "SAR",
  trialEndsAt: null,
  suspensionReason: null,
  createdAt: "2026-09-23T00:00:00Z",
  updatedAt: "2026-09-23T00:00:00Z",
};

beforeEach(() => {
  subscriptionsMock.mockReset().mockResolvedValue(emptyPage);
  provisionMock.mockReset().mockResolvedValue({
    jobId: "22222222-2222-4222-8222-222222222222",
    status: "SUCCEEDED",
    skippedSteps: [],
  });
  plansMock.mockReset().mockResolvedValue([starterPlan]);
  tenantMock.mockReset().mockResolvedValue(pendingTenant);
  createSubscriptionMock.mockReset().mockResolvedValue({
    id: "33333333-3333-4333-8333-333333333333",
    tenantId: pendingTenant.id,
    status: "ACTIVE",
    planId: starterPlan.id,
  });
  changeTenantStatusMock.mockReset().mockResolvedValue({
    ...pendingTenant,
    status: "ACTIVE",
  });
  pushMock.mockReset();
});

afterEach(() => cleanup());

describe("Subscription tenant upgrade entry flow", () => {
  it("creates, provisions, activates the tenant, and opens detail when upgrade targets a tenant without a subscription", async () => {
    render(<SubscriptionsPage />);

    expect(
      await screen.findByText("scp.subscriptions.noSubscriptionUpgradeTitle"),
    ).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("scp.subscriptions.targetPlan"), {
      target: { value: starterPlan.id },
    });
    fireEvent.change(screen.getByLabelText("scp.subscriptions.seatQuantity"), {
      target: { value: "4" },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "scp.subscriptions.startUpgrade" }),
    );

    await waitFor(() => {
      expect(createSubscriptionMock).toHaveBeenCalledWith({
        tenantId: pendingTenant.id,
        planId: starterPlan.id,
        billingCycle: "MONTHLY",
        seatQuantity: 4,
        trialDays: 0,
      });
    });

    expect(provisionMock).toHaveBeenCalledWith(
      "33333333-3333-4333-8333-333333333333",
    );
    expect(changeTenantStatusMock).toHaveBeenCalledWith(
      pendingTenant.id,
      "ACTIVE",
      "Subscription upgrade activated",
    );
    expect(pushMock).toHaveBeenCalledWith(
      `/executive/subscriptions/33333333-3333-4333-8333-333333333333?tenantId=${pendingTenant.id}&intent=upgrade`,
    );
  });
});
