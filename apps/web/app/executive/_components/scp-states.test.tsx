// @vitest-environment jsdom

/**
 * R0C-12 HUMAN-UAT CORRECTIVE — Blocker G state semantics (UAT-11 / UAT-12).
 *
 * Valid empty datasets are NOT errors: the usage page (real zero / no
 * metrics for a selected tenant) and the billing page (zero invoices) must
 * render their explicit empty states, a degraded API must render a retryable
 * error — and raw HTTP internals must never leak into the UI.
 */
import "@testing-library/jest-dom/vitest";
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const accessCheckMock = vi.fn();

vi.mock("@/lib/api/scp-api", () => ({
  scpApi: {
    accessCheckV2: (...args: unknown[]) => accessCheckMock(...args),
    usage: vi.fn().mockResolvedValue([]),
    tenants: vi.fn().mockResolvedValue({
      content: [{ id: "t-1", name: "UAT Tenant", code: "uat" }],
      page: 0,
      size: 8,
      totalElements: 1,
      totalPages: 1,
    }),
  },
}));

vi.mock("@/lib/api/executive-api", () => ({
  executiveApi: {
    invoices: vi.fn().mockResolvedValue([]),
  },
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

vi.mock("../_components/format", () => ({
  useScpFormat: () => ({
    day: (value: unknown) => (value ? String(value) : "—"),
    money: () => "0",
    number: (value: number) => String(value),
  }),
}));

import { ScpAccessProvider } from "../_components/ScpAccess";
import UsagePage from "../usage/page";
import BillingPage from "../billing/page";

const READ_MAP = {
  authenticated: true,
  capabilities: { "usage.read": true, "billing.read": true },
};

beforeEach(() => {
  accessCheckMock.mockReset();
  accessCheckMock.mockResolvedValue(READ_MAP);
  vi.spyOn(console, "error").mockImplementation(() => undefined);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

async function settle() {
  const { act } = await import("@testing-library/react");
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
}

async function selectTenantAndLoad(user: ReturnType<typeof userEvent.setup>) {
  // drive the real tenant-selection flow (debounced search → match → load)
  await user.type(screen.getByRole("searchbox"), "u");
  await new Promise((resolve) => setTimeout(resolve, 400)); // debounce 250ms
  await settle();
  await user.click(screen.getByRole("button", { name: /UAT Tenant/ }));
  await settle();
  // the dedicated Load control becomes enabled once a tenant is selected
  await user.click(screen.getByRole("button", { name: "scp.usage.load" }));
  await settle();
}

describe("Usage — state semantics (Blocker G / UAT-12)", () => {
  it("empty/real-zero usage renders the explicit empty state — never a 403 banner nor an error", async () => {
    const user = userEvent.setup();
    render(
      <ScpAccessProvider>
        <UsagePage />
      </ScpAccessProvider>,
    );
    await settle();
    await selectTenantAndLoad(user);
    expect(screen.getByText("scp.usage.noMetrics")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("degraded usage API renders a retryable, user-facing error (no raw internals)", async () => {
    const { scpApi } = await import("@/lib/api/scp-api");
    (scpApi.usage as ReturnType<typeof vi.fn>).mockRejectedValueOnce(
      new Error("HTTP 503 Service Unavailable: upstream-cluster-7"),
    );
    const user = userEvent.setup();
    render(
      <ScpAccessProvider>
        <UsagePage />
      </ScpAccessProvider>,
    );
    await settle();
    await selectTenantAndLoad(user);
    expect(screen.getByRole("alert")).toBeInTheDocument();
    // raw internals must not leak
    expect(screen.getByRole("alert").textContent).not.toContain("upstream-cluster-7");
  });
});

describe("Billing — state semantics (Blocker G / UAT-11)", () => {
  it("zero invoices renders the explicit empty state — never an application error", async () => {
    render(
      <ScpAccessProvider>
        <BillingPage />
      </ScpAccessProvider>,
    );
    await settle();
    expect(screen.getByText("scp.state.empty")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
