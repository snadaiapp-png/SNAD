// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { TenantRow } from "@/lib/api/scp-api";

const tenantsMock = vi.fn();
const hasMock = vi.fn();

vi.mock("@/lib/api/scp-api", () => ({
  scpApi: { tenants: (...args: unknown[]) => tenantsMock(...args) },
}));

vi.mock("@/lib/api/executive-api", () => ({
  executiveApi: {
    createTenant: vi.fn(), tenant: vi.fn(), updateTenant: vi.fn(), changeTenantStatus: vi.fn(),
    recordTenantLoginLinkEvent: vi.fn(), resumeSubscription: vi.fn(), createSubscription: vi.fn(),
  },
}));

vi.mock("../_components/ScpAccess", () => ({
  useScpAccess: () => ({
    phase: "authorized", authenticated: true, loading: false, error: false, capabilities: {},
    has: (capability: string) => hasMock(capability), hasAll: () => false, hasAny: () => false, refresh: () => undefined,
  }),
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ locale: "ar", direction: "rtl", t: (key: string) => key }),
}));

vi.mock("next/link", () => ({
  default: ({ href, children }: { href: string; children: unknown }) => <a href={href}>{children as never}</a>,
}));

import TenantsPage from "./page";

const baseTenant: TenantRow = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Acme",
  code: "acme",
  status: "ACTIVE",
  countryCode: "SA",
  currencyCode: "SAR",
  subscriptionCount: 0,
  subscriptionStatus: null,
  effectiveSubscriptionId: null,
  billingState: null,
  accessDecision: "NO_EFFECTIVE_SUBSCRIPTION",
  commercialAction: "CREATE_SUBSCRIPTION",
  anomalyCode: "ACTIVE_WITHOUT_EFFECTIVE_SUBSCRIPTION",
  loginAllowed: false,
  createdAt: "2026-09-09T00:00:00Z",
};

function page(content: TenantRow[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

beforeEach(() => {
  hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("tenant commercial actions are backend-derived", () => {
  it("fails closed for ACTIVE tenant with no effective subscription", async () => {
    tenantsMock.mockResolvedValue(page([baseTenant]));
    render(<TenantsPage />);
    await screen.findByText("Acme");

    expect(screen.queryByRole("button", { name: "scp.tenants.openLogin" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "scp.tenants.copyLogin" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "scp.tenants.upgrade" })).not.toBeInTheDocument();
    expect(screen.getByText("scp.tenants.createSubscription")).toBeInTheDocument();
  });

  it("shows upgrade only when backend action is UPGRADE and preserves effective subscription id", async () => {
    tenantsMock.mockResolvedValue(page([{
      ...baseTenant,
      subscriptionCount: 1,
      subscriptionStatus: "ACTIVE",
      effectiveSubscriptionId: "22222222-2222-2222-2222-222222222222",
      billingState: "CURRENT",
      accessDecision: "ACCESS_ALLOWED",
      commercialAction: "UPGRADE",
      anomalyCode: null,
      loginAllowed: true,
    }]));
    render(<TenantsPage />);
    await screen.findByText("Acme");

    expect(screen.getByRole("button", { name: "scp.tenants.openLogin" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "scp.tenants.upgrade" })).toHaveAttribute(
      "href",
      "/executive/subscriptions/22222222-2222-2222-2222-222222222222",
    );
  });

  it("renders blocked action without mutation fallback for terminal history", async () => {
    tenantsMock.mockResolvedValue(page([{
      ...baseTenant,
      subscriptionCount: 1,
      subscriptionStatus: null,
      accessDecision: "SUBSCRIPTION_TERMINAL",
      commercialAction: "BLOCKED",
      anomalyCode: "TERMINATED_HISTORY_WITHOUT_EFFECTIVE_SUBSCRIPTION",
    }]));
    render(<TenantsPage />);
    await screen.findByText("Acme");

    expect(screen.getByText("scp.tenants.commercialBlocked")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "scp.tenants.upgrade" })).not.toBeInTheDocument();
  });
});
