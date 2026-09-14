// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const tenantsMock = vi.fn();
const hasMock = vi.fn();

vi.mock("@/lib/api/scp-api", () => ({
  scpApi: { tenants: (...args: unknown[]) => tenantsMock(...args) },
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

const PAGE = {
  content: [{
    id: "11111111-1111-1111-1111-111111111111",
    name: "Acme",
    code: "acme",
    status: "ACTIVE",
    countryCode: "SA",
    currencyCode: "SAR",
    subscriptionCount: 1,
    subscriptionStatus: "ACTIVE",
    createdAt: "2026-09-09T00:00:00Z",
  }],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

beforeEach(() => {
  tenantsMock.mockResolvedValue(PAGE);
  hasMock.mockReset();
});

afterEach(() => cleanup());

describe("Executive tenant management controls", () => {
  it("manager sees create, update, freeze, archive and upgrade controls", async () => {
    hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
    render(<TenantsPage />);
    await waitFor(() => expect(screen.getByText("Acme")).toBeInTheDocument());

    expect(screen.getByRole("button", { name: "إنشاء حساب جديد" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "تحديث" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "تجميد" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "حذف الحساب" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "ترقية" })).toHaveAttribute(
      "href",
      "/executive/subscriptions?tenantId=11111111-1111-1111-1111-111111111111&intent=upgrade",
    );
    expect(hasMock).toHaveBeenCalledWith("EXECUTIVE_MANAGE");
  });

  it("granular-only/read-only user never receives mutation controls", async () => {
    hasMock.mockImplementation((capability: string) => capability !== "EXECUTIVE_MANAGE");
    render(<TenantsPage />);
    await waitFor(() => expect(screen.getByText("Acme")).toBeInTheDocument());

    expect(screen.queryByRole("button", { name: "إنشاء حساب جديد" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "تحديث" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "تجميد" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "حذف الحساب" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "ترقية" })).not.toBeInTheDocument();
  });
});
