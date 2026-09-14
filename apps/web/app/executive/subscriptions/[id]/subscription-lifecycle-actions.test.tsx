// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const detailMock = vi.fn();
const itemsMock = vi.fn();
const usageMock = vi.fn();
const plansMock = vi.fn();
const lifecycleCommandMock = vi.fn();
const cancelSubscriptionMock = vi.fn();

vi.mock("next/navigation", () => ({
  useParams: () => ({ id: "sub-1" }),
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/lib/api/scp-api", () => ({
  scpApi: {
    subscriptionDetail: (...args: unknown[]) => detailMock(...args),
    subscriptionItems: (...args: unknown[]) => itemsMock(...args),
    usage: (...args: unknown[]) => usageMock(...args),
    lifecycleCommand: (...args: unknown[]) => lifecycleCommandMock(...args),
    planVersions: vi.fn().mockResolvedValue([]),
    previewChange: vi.fn(),
    executeChange: vi.fn(),
  },
}));
vi.mock("@/lib/api/executive-api", () => ({
  executiveApi: {
    plans: (...args: unknown[]) => plansMock(...args),
    cancelSubscription: (...args: unknown[]) => cancelSubscriptionMock(...args),
  },
}));
vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));
vi.mock("../../_components/format", () => ({
  useScpFormat: () => ({
    money: (value: number) => String(value),
    day: (value: string) => value || "—",
    number: (value: number) => String(value),
  }),
}));
vi.mock("../../_components/ScpAccess", () => ({
  useScpAccess: () => ({ has: () => true, hasAll: () => true, hasAny: () => true }),
}));

import SubscriptionDetailPage from "./page";

beforeEach(() => {
  detailMock.mockReset();
  itemsMock.mockReset();
  usageMock.mockReset();
  plansMock.mockReset();
  lifecycleCommandMock.mockReset();
  cancelSubscriptionMock.mockReset();
  itemsMock.mockResolvedValue([]);
  usageMock.mockResolvedValue([]);
  plansMock.mockResolvedValue([]);
});
afterEach(() => cleanup());

describe("Subscription detail lifecycle authority", () => {
  it("renders only backend-allowed direct lifecycle actions and never generic ACTIVATE/RENEW", async () => {
    detailMock.mockResolvedValue({
      id: "sub-1",
      overview: { tenantId: "tenant-1", status: "ACTIVE", currencyCode: "SAR" },
      items: [], entitlements: [], invoices: [], changes: [], provisioningJobs: [], audit: [],
      availableActions: ["PAUSE"], blockingReasons: [],
    });
    render(<SubscriptionDetailPage />);
    await waitFor(() => expect(screen.getByText("scp.detail.lifecycleCommands")).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "scp.detail.lifecycle.PAUSE" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "scp.detail.lifecycle.ACTIVATE" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "scp.detail.lifecycle.RENEW" })).not.toBeInTheDocument();
  });

  it("routes CANCEL through the governed cancellation endpoint, never the generic lifecycle command", async () => {
    const user = userEvent.setup();
    detailMock.mockResolvedValue({
      id: "sub-1",
      overview: { tenantId: "tenant-1", status: "ACTIVE", currencyCode: "SAR" },
      items: [], entitlements: [], invoices: [], changes: [], provisioningJobs: [], audit: [],
      availableActions: ["CANCEL"], blockingReasons: [],
    });
    cancelSubscriptionMock.mockResolvedValue({ id: "sub-1", status: "CANCELLED" });

    render(<SubscriptionDetailPage />);
    const cancelButton = await screen.findByRole("button", { name: "scp.detail.lifecycle.CANCEL" });
    await user.click(cancelButton);

    await waitFor(() => expect(cancelSubscriptionMock).toHaveBeenCalledWith(
      "sub-1",
      { immediate: true, reason: "CANCEL" },
    ));
    expect(lifecycleCommandMock).not.toHaveBeenCalled();
  });

  it("keeps the error surface renderable when subscription detail loading fails", async () => {
    detailMock.mockRejectedValue(new Error("load failed"));

    render(<SubscriptionDetailPage />);

    await waitFor(() => expect(detailMock).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByText("scp.detail.lifecycleCommands")).toBeInTheDocument());
    expect(screen.queryByRole("button", { name: "scp.detail.lifecycle.PAUSE" })).not.toBeInTheDocument();
  });
});
