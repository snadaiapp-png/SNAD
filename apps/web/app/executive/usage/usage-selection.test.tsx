// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const tenantsMock = vi.fn();
const usageMock = vi.fn();

vi.mock("@/lib/api/scp-api", () => ({
  scpApi: {
    tenants: (...args: unknown[]) => tenantsMock(...args),
    usage: (...args: unknown[]) => usageMock(...args),
  },
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

vi.mock("../_components/format", () => ({
  useScpFormat: () => ({ number: (value: number) => String(value) }),
}));

import UsagePage from "./page";

describe("UsagePage — tenant selection correctness", () => {
  beforeEach(() => {
    tenantsMock.mockReset();
    usageMock.mockReset();
    tenantsMock.mockResolvedValue({
      content: [{
        id: "tenant-1",
        name: "Acme",
        code: "acme",
        status: "ACTIVE",
        countryCode: "SA",
        currencyCode: "SAR",
        subscriptionCount: 1,
        subscriptionStatus: "ACTIVE",
        createdAt: "2026-09-01T00:00:00Z",
      }],
      page: 0,
      size: 8,
      totalElements: 1,
      totalPages: 1,
    });
    usageMock.mockResolvedValue([]);
  });

  afterEach(() => cleanup());

  it("loads usage for the tenant clicked, not the stale previous state", async () => {
    render(<UsagePage />);

    fireEvent.change(screen.getByLabelText("scp.entitlements.searchTenant"), {
      target: { value: "Acme" },
    });

    const tenantButton = await screen.findByRole("button", { name: "Acme · acme" });
    fireEvent.click(tenantButton);
    expect(usageMock).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "scp.usage.load" }));

    await waitFor(() => expect(usageMock).toHaveBeenCalledWith("tenant-1"));
  });
});
