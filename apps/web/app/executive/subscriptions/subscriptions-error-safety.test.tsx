// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const subscriptionsMock = vi.fn();

vi.mock("@/lib/api/scp-api", () => ({
  scpApi: { subscriptions: (...args: unknown[]) => subscriptionsMock(...args) },
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ prefetch: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
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

beforeEach(() => subscriptionsMock.mockReset());
afterEach(() => cleanup());

describe("Subscription grid error safety", () => {
  it("routes backend failures through the SCP safe error mapper", async () => {
    subscriptionsMock.mockRejectedValueOnce(new Error("RAW_INTERNAL_SUBSCRIPTIONS_ERROR"));

    render(<SubscriptionsPage />);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("SAFE_SUBSCRIPTIONS_ERROR");
    expect(alert).not.toHaveTextContent("RAW_INTERNAL_SUBSCRIPTIONS_ERROR");
  });
});
