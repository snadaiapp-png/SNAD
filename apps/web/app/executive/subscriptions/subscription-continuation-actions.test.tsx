// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const subscriptionsMock = vi.fn();
const plansMock = vi.fn();
const createSubscriptionMock = vi.fn();
const resumeSubscriptionMock = vi.fn();
let params = new URLSearchParams();

vi.mock("@/lib/api/scp-api", () => ({
  scpApi: { subscriptions: (...args: unknown[]) => subscriptionsMock(...args) },
}));

vi.mock("@/lib/api/executive-api", () => ({
  executiveApi: {
    plans: (...args: unknown[]) => plansMock(...args),
    createSubscription: (...args: unknown[]) => createSubscriptionMock(...args),
    resumeSubscription: (...args: unknown[]) => resumeSubscriptionMock(...args),
  },
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ prefetch: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => params,
}));

vi.mock("next/link", () => ({
  default: ({ href, children }: { href: string; children: unknown }) => <a href={href}>{children as never}</a>,
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ locale: "en", direction: "ltr", t: (key: string) => key }),
}));

vi.mock("../_components/format", () => ({
  useScpFormat: () => ({ money: () => "—", day: (value: string) => value }),
}));

import SubscriptionsPage from "./page";

const TENANT = "22222222-2222-2222-2222-222222222222";
const PLAN = "33333333-3333-3333-3333-333333333333";
const CANCELLED_SUB = "44444444-4444-4444-4444-444444444444";

beforeEach(() => {
  params = new URLSearchParams();
  subscriptionsMock.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  plansMock.mockResolvedValue([
    { id: PLAN, code: "GROWTH", name: "Growth", status: "ACTIVE", currencyCode: "SAR", monthlyPriceMinor: 10000, annualPriceMinor: 100000, trialDays: 14 },
    { id: "55555555-5555-5555-5555-555555555555", code: "OLD", name: "Old", status: "ARCHIVED", currencyCode: "SAR", monthlyPriceMinor: 1, annualPriceMinor: 1, trialDays: 0 },
  ]);
  createSubscriptionMock.mockResolvedValue({ id: "66666666-6666-6666-6666-666666666666" });
  resumeSubscriptionMock.mockResolvedValue({ id: CANCELLED_SUB });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("deterministic subscription continuation actions", () => {
  it("CREATE_SUBSCRIPTION offers only active plans and sends canonical create payload", async () => {
    const user = userEvent.setup();
    params = new URLSearchParams(`tenantId=${TENANT}&intent=create`);
    render(<SubscriptionsPage />);

    const planSelect = await screen.findByLabelText("scp.subscriptions.create.plan");
    expect(plansMock).toHaveBeenCalled();
    expect(screen.getByRole("option", { name: "Growth" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Old" })).not.toBeInTheDocument();

    await user.selectOptions(planSelect, PLAN);
    await user.click(screen.getByRole("button", { name: "scp.subscriptions.create.submit" }));

    await waitFor(() => expect(createSubscriptionMock).toHaveBeenCalledWith({
      tenantId: TENANT,
      planId: PLAN,
      billingCycle: "MONTHLY",
      seatQuantity: 1,
      trialDays: 14,
    }));
  });

  it("CREATE_SUCCESSOR forces trialDays to zero and delegates legality to the backend", async () => {
    const user = userEvent.setup();
    params = new URLSearchParams(`tenantId=${TENANT}&intent=create-successor`);
    render(<SubscriptionsPage />);

    const planSelect = await screen.findByLabelText("scp.subscriptions.create.plan");
    await user.selectOptions(planSelect, PLAN);
    await user.click(screen.getByRole("button", { name: "scp.subscriptions.createSuccessor.submit" }));

    await waitFor(() => expect(createSubscriptionMock).toHaveBeenCalledWith(expect.objectContaining({
      tenantId: TENANT,
      planId: PLAN,
      trialDays: 0,
    })));
  });

  it("RESUME exposes only cancelled history and invokes canonical resume", async () => {
    const user = userEvent.setup();
    params = new URLSearchParams(`tenantId=${TENANT}&intent=resume`);
    subscriptionsMock.mockResolvedValue({
      content: [{
        id: CANCELLED_SUB, tenantId: TENANT, tenantName: "Acme", tenantCountry: "SA", status: "CANCELLED",
        billingCycle: "MONTHLY", seatQuantity: 1, planId: PLAN, planName: "Growth", planCode: "GROWTH",
        planVersion: "v1", currencyCode: "SAR", recurringAmountMinor: 10000, monthlyEquivalentMinor: 10000,
        monthlyPriceMinor: 10000, itemCount: 1, trial: false, cancelAtPeriodEnd: false, currentPeriodEnd: null,
      }], page: 0, size: 20, totalElements: 1, totalPages: 1,
    });

    render(<SubscriptionsPage />);
    await screen.findByText("Acme");
    await user.click(screen.getByRole("button", { name: "scp.subscriptions.resume.submit" }));

    await waitFor(() => expect(resumeSubscriptionMock).toHaveBeenCalledWith(CANCELLED_SUB));
  });

  it("unknown intent fails closed and exposes no commercial mutation", async () => {
    params = new URLSearchParams(`tenantId=${TENANT}&intent=future-command`);
    render(<SubscriptionsPage />);
    await screen.findByText("scp.state.empty");

    expect(screen.queryByRole("button", { name: /submit/ })).not.toBeInTheDocument();
    expect(createSubscriptionMock).not.toHaveBeenCalled();
    expect(resumeSubscriptionMock).not.toHaveBeenCalled();
  });
});
