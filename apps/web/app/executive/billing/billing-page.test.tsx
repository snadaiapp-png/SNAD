// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/lib/api/errors";

const tenantSearchMock = vi.fn();
const billingMock = vi.fn();
vi.mock("@/lib/api/scp-api", () => ({
  scpApi: { tenants: (...args: unknown[]) => tenantSearchMock(...args) },
}));
vi.mock("@/lib/api/executive-api", () => ({
  executiveApi: { billingV2: (...args: unknown[]) => billingMock(...args) },
}));
vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));
vi.mock("../_components/format", () => ({
  useScpFormat: () => ({ money: (value: number) => String(value), day: (value: string) => value }),
}));

import BillingPage from "./page";

beforeEach(() => {
  tenantSearchMock.mockReset();
  billingMock.mockReset();
  tenantSearchMock.mockResolvedValue({
    content: [{ id: "tenant-1", name: "Acme", code: "acme" }],
    page: 0, size: 8, totalElements: 1, totalPages: 1,
  });
  billingMock.mockResolvedValue([]);
});
afterEach(() => { vi.useRealTimers(); cleanup(); });

describe("Billing tenant context", () => {
  it("does not issue an unscoped request and loads billing only for the selected tenant", async () => {
    const user = userEvent.setup();
    render(<BillingPage />);
    expect(billingMock).not.toHaveBeenCalled();

    const tenantSearch = screen.getByRole("searchbox", { name: "scp.entitlements.searchTenant" });
    await user.type(tenantSearch, "Acme");
    await waitFor(() => expect(tenantSearchMock).toHaveBeenCalled(), { timeout: 1000 });
    await user.click(await screen.findByRole("button", { name: /Acme · acme/ }));
    await user.click(screen.getByRole("button", { name: "scp.billing.load" }));
    await waitFor(() => expect(billingMock).toHaveBeenCalledWith("tenant-1"));
  });

  it("renders projection and Finance state without inferring a missing Finance link", async () => {
    const user = userEvent.setup();
    billingMock.mockResolvedValue([{
      id: "invoice-1",
      tenantId: "tenant-1",
      tenantName: "Acme",
      subscriptionId: "subscription-1",
      invoiceNumber: "INV-100",
      projectionStatus: "OPEN",
      currencyCode: "SAR",
      subtotalMinor: 10000,
      creditAppliedMinor: 1000,
      taxMinor: 1350,
      totalMinor: 10350,
      amountPaidMinor: 4000,
      outstandingMinor: 6350,
      description: null,
      periodStart: "2026-09-01T00:00:00Z",
      periodEnd: "2026-10-01T00:00:00Z",
      dueAt: "2026-09-10T00:00:00Z",
      paidAt: null,
      paymentReference: null,
      financeLinkId: null,
      financeInvoiceId: null,
      financeStatus: "UNLINKED",
      settlementState: "UNKNOWN",
      reconciliationClassification: null,
      reconciliationState: "UNRECONCILED",
      accountingSourceOfTruth: "FINANCE",
      projectionSource: "SCP_BILLING_PROJECTION",
      createdAt: "2026-09-01T00:00:00Z",
    }]);

    render(<BillingPage />);
    await user.type(screen.getByRole("searchbox", { name: "scp.entitlements.searchTenant" }), "Acme");
    await waitFor(() => expect(tenantSearchMock).toHaveBeenCalled(), { timeout: 1000 });
    await user.click(await screen.findByRole("button", { name: /Acme · acme/ }));
    await user.click(screen.getByRole("button", { name: "scp.billing.load" }));

    expect(await screen.findByText("INV-100")).toBeInTheDocument();
    expect(screen.getByText("scp.billing.notLinked")).toBeInTheDocument();
    expect(screen.getByText("FINANCE")).toBeInTheDocument();
    expect(screen.getByText("SCP_BILLING_PROJECTION")).toBeInTheDocument();
    expect(screen.getByText("UNRECONCILED")).toBeInTheDocument();
    expect(screen.getByText("6350")).toBeInTheDocument();
  });

  it("surfaces tenant-search failures instead of misrepresenting them as an empty result", async () => {
    const user = userEvent.setup();
    tenantSearchMock.mockRejectedValue(
      new ApiHttpError("Request failed", {
        status: 500,
        error: "Internal Server Error",
        message: "relation tenants does not exist",
        path: "/api/v1/executive/tenants/v2",
        requestId: "req-billing-search",
        body: null,
      }),
    );

    render(<BillingPage />);
    await user.type(
      screen.getByRole("searchbox", { name: "scp.entitlements.searchTenant" }),
      "Acme",
    );

    const alert = await screen.findByRole("alert");
    expect(alert.textContent).not.toMatch(/relation tenants|does not exist/i);
    expect(billingMock).not.toHaveBeenCalled();
  });

  it("ignores a stale tenant-search response that resolves after a newer query", async () => {
    vi.useFakeTimers();
    let resolveOld!: (value: unknown) => void;
    let resolveNew!: (value: unknown) => void;
    tenantSearchMock
      .mockReset()
      .mockReturnValueOnce(new Promise((resolve) => { resolveOld = resolve; }))
      .mockReturnValueOnce(new Promise((resolve) => { resolveNew = resolve; }));

    render(<BillingPage />);
    const search = screen.getByRole("searchbox", { name: "scp.entitlements.searchTenant" });

    fireEvent.change(search, { target: { value: "A" } });
    await act(async () => { vi.advanceTimersByTime(300); });
    fireEvent.change(search, { target: { value: "Ac" } });
    await act(async () => { vi.advanceTimersByTime(300); });

    await act(async () => {
      resolveNew({
        content: [{ id: "tenant-new", name: "Acme New", code: "new" }],
        page: 0, size: 8, totalElements: 1, totalPages: 1,
      });
      await Promise.resolve();
    });
    expect(screen.getByRole("button", { name: /Acme New · new/ })).toBeInTheDocument();

    await act(async () => {
      resolveOld({
        content: [{ id: "tenant-old", name: "Acme Old", code: "old" }],
        page: 0, size: 8, totalElements: 1, totalPages: 1,
      });
      await Promise.resolve();
    });

    expect(screen.queryByRole("button", { name: /Acme Old · old/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Acme New · new/ })).toBeInTheDocument();
    vi.useRealTimers();
  });

  it("does not render an invoice-retry control for a tenant-search failure", async () => {
    const user = userEvent.setup();
    tenantSearchMock.mockRejectedValueOnce(new Error("search failed"));

    render(<BillingPage />);
    await user.type(screen.getByRole("searchbox", { name: "scp.entitlements.searchTenant" }), "Acme");

    await screen.findByRole("alert");
    expect(screen.queryByRole("button", { name: "scp.state.retry" })).not.toBeInTheDocument();
  });
});
