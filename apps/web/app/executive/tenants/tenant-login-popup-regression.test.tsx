// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";

const tenantsMock = vi.fn();
const hasMock = vi.fn();
const recordTenantLoginLinkEventMock = vi.fn();

vi.mock("@/lib/api/scp-api", () => ({
  scpApi: { tenants: (...args: unknown[]) => tenantsMock(...args) },
}));

vi.mock("@/lib/api/executive-api", () => ({
  executiveApi: {
    createTenant: vi.fn(),
    tenant: vi.fn(),
    updateTenant: vi.fn(),
    changeTenantStatus: vi.fn(),
    recordTenantLoginLinkEvent: (...args: unknown[]) => recordTenantLoginLinkEventMock(...args),
  },
}));

vi.mock("../_components/ScpAccess", () => ({
  useScpAccess: () => ({
    phase: "authorized",
    authenticated: true,
    loading: false,
    error: false,
    capabilities: {},
    has: (capability: string) => hasMock(capability),
    hasAll: () => false,
    hasAny: () => false,
    refresh: () => undefined,
  }),
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ locale: "ar", direction: "rtl", t: (key: string) => key }),
}));

vi.mock("next/link", () => ({
  default: ({ href, children }: { href: string; children: unknown }) => <a href={href}>{children as never}</a>,
}));

import TenantsPage from "./page";

beforeEach(() => {
  tenantsMock.mockResolvedValue({
    content: [{
      id: "11111111-1111-1111-1111-111111111111",
      name: "Acme",
      code: "acme",
      status: "ACTIVE",
      countryCode: "SA",
      currencyCode: "SAR",
      subscriptionCount: 1,
      subscriptionStatus: "ACTIVE",
      applicationHostname: null,
      effectiveSubscriptionId: "22222222-2222-2222-2222-222222222222",
      billingState: "CURRENT",
      accessDecision: "ACCESS_ALLOWED",
      commercialAction: "UPGRADE",
      anomalyCode: null,
      loginAllowed: true,
      createdAt: "2026-09-09T00:00:00Z",
    }],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  });
  hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
  recordTenantLoginLinkEventMock.mockReset();
  recordTenantLoginLinkEventMock.mockResolvedValue(undefined);
});

afterEach(() => {
  vi.restoreAllMocks();
  cleanup();
});

it("keeps a usable popup handle while severing opener before async audit", async () => {
  const user = userEvent.setup();
  let releaseAudit!: () => void;
  recordTenantLoginLinkEventMock.mockImplementation(
    () => new Promise<void>((resolve) => { releaseAudit = resolve; }),
  );

  const popup = {
    close: vi.fn(),
    location: { href: "about:blank" },
    opener: window,
  } as unknown as Window;
  const openMock = vi.spyOn(window, "open").mockReturnValue(popup);

  render(<TenantsPage />);
  await screen.findByText("Acme");
  await user.click(screen.getByRole("button", { name: "scp.tenants.openLogin" }));

  expect(openMock).toHaveBeenCalledWith("about:blank", "_blank");
  expect(popup.opener).toBeNull();
  expect(popup.location.href).toBe("about:blank");

  releaseAudit();
  await waitFor(() => {
    expect(popup.location.href).toBe(
      "http://localhost:3000/?tenantId=11111111-1111-1111-1111-111111111111&tenantLogin=1",
    );
  });
});
