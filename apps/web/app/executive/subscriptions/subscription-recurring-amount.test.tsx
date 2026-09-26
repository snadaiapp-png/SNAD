// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const subscriptionsMock = vi.fn();

vi.mock("@/lib/api/scp-api", () => ({
  scpApi: { subscriptions: (...args: unknown[]) => subscriptionsMock(...args) },
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ prefetch: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("next/link", () => ({
  default: ({ href, children }: { href: string; children: unknown }) => <a href={href}>{children as never}</a>,
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ locale: "en", direction: "ltr", t: (key: string) => key }),
}));

vi.mock("../_components/format", () => ({
  useScpFormat: () => ({
    money: (value: number | null, currency: string | null) => value == null ? "—" : `${value}:${currency}`,
    day: (value: string) => value,
  }),
}));

import SubscriptionsPage from "./page";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("subscription recurring amount semantics", () => {
  it("renders the annual recurring charge, never its monthly equivalent as the billed amount", async () => {
    subscriptionsMock.mockResolvedValue({
      content: [{
        id: "11111111-1111-1111-1111-111111111111",
        tenantId: "22222222-2222-2222-2222-222222222222",
        tenantName: "Annual Tenant",
        tenantCountry: "SA",
        status: "ACTIVE",
        billingCycle: "ANNUAL",
        seatQuantity: 2,
        planId: "33333333-3333-3333-3333-333333333333",
        planName: "Growth",
        planCode: "GROWTH",
        planVersion: "v1",
        currencyCode: "SAR",
        recurringAmountMinor: 240000,
        monthlyEquivalentMinor: 20000,
        monthlyPriceMinor: 20000,
        itemCount: 1,
        trial: false,
        cancelAtPeriodEnd: false,
        currentPeriodEnd: "2027-09-23T00:00:00Z",
      }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });

    render(<SubscriptionsPage />);

    const amountCell = await screen.findByRole("cell", {
      name: /240000:SAR.*scp\.subscriptions\.annualRecurringAmount/,
    });
    expect(amountCell).toBeInTheDocument();
    expect(amountCell).not.toHaveTextContent("20000:SAR");
    expect(screen.queryByText("20000:SAR")).not.toBeInTheDocument();
  });
});
