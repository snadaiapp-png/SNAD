// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/lib/api/errors";

const tenantSearchMock = vi.fn();
const invoicesMock = vi.fn();
vi.mock("@/lib/api/scp-api", () => ({
  scpApi: { tenants: (...args: unknown[]) => tenantSearchMock(...args) },
}));
vi.mock("@/lib/api/executive-api", () => ({
  executiveApi: { invoices: (...args: unknown[]) => invoicesMock(...args) },
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
  invoicesMock.mockReset();
  tenantSearchMock.mockResolvedValue({
    content: [{ id: "tenant-1", name: "Acme", code: "acme" }],
    page: 0, size: 8, totalElements: 1, totalPages: 1,
  });
  invoicesMock.mockResolvedValue([]);
});
afterEach(() => cleanup());

describe("Billing tenant context", () => {
  it("does not issue an unscoped request and loads invoices only for the selected tenant", async () => {
    const user = userEvent.setup();
    render(<BillingPage />);
    expect(invoicesMock).not.toHaveBeenCalled();

    const tenantSearch = screen.getByRole("searchbox", { name: "scp.entitlements.searchTenant" });
    await user.type(tenantSearch, "Acme");
    await waitFor(() => expect(tenantSearchMock).toHaveBeenCalled(), { timeout: 1000 });
    await user.click(await screen.findByRole("button", { name: /Acme · acme/ }));
    await user.click(screen.getByRole("button", { name: "scp.billing.load" }));
    await waitFor(() => expect(invoicesMock).toHaveBeenCalledWith("tenant-1"));
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
    expect(invoicesMock).not.toHaveBeenCalled();
  });

});
