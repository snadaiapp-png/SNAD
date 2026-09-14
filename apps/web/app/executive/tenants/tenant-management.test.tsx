// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/lib/api/errors";

const tenantsMock = vi.fn();
const hasMock = vi.fn();
const createTenantMock = vi.fn();
const tenantMock = vi.fn();
const updateTenantMock = vi.fn();
const changeTenantStatusMock = vi.fn();

vi.mock("@/lib/api/scp-api", () => ({
  scpApi: { tenants: (...args: unknown[]) => tenantsMock(...args) },
}));

vi.mock("@/lib/api/executive-api", () => ({
  executiveApi: {
    createTenant: (...args: unknown[]) => createTenantMock(...args),
    tenant: (...args: unknown[]) => tenantMock(...args),
    updateTenant: (...args: unknown[]) => updateTenantMock(...args),
    changeTenantStatus: (...args: unknown[]) => changeTenantStatusMock(...args),
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
  createTenantMock.mockReset();
  tenantMock.mockReset();
  updateTenantMock.mockReset();
  changeTenantStatusMock.mockReset();
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

  it("keeps focus on the input while typing multiple characters in the create dialog", async () => {
    const user = userEvent.setup();
    hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
    render(<TenantsPage />);
    await waitFor(() => expect(screen.getByText("Acme")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: "إنشاء حساب جديد" }));
    const subdomain = screen.getByLabelText("النطاق الفرعي");
    await user.click(subdomain);
    await user.type(subdomain, "acme01");

    expect((subdomain as HTMLInputElement).value).toBe("acme01");
    expect(screen.getByLabelText("النطاق الفرعي")).toHaveFocus();
  });

  it("shows visible labels for every create-dialog field", async () => {
    const user = userEvent.setup();
    hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
    render(<TenantsPage />);
    await waitFor(() => expect(screen.getByText("Acme")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: "إنشاء حساب جديد" }));

    for (const label of ["اسم الحساب", "النطاق الفرعي", "بريد المسؤول", "اسم المسؤول"]) {
      const labelEl = screen.getByText(label);
      expect(labelEl).toBeVisible();
      expect(labelEl.tagName).toBe("LABEL");
    }
  });

  it("validates tenant creation locally and keeps invalid payloads away from the API", async () => {
    const user = userEvent.setup();
    hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
    render(<TenantsPage />);
    await waitFor(() => expect(screen.getByText("Acme")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: "إنشاء حساب جديد" }));
    await user.type(screen.getByLabelText("اسم الحساب"), "شركة اختبار");
    await user.type(screen.getByLabelText("النطاق الفرعي"), "bad_domain");
    await user.type(screen.getByLabelText("بريد المسؤول"), "not-an-email");
    await user.type(screen.getByLabelText("اسم المسؤول"), "مدير النظام");

    await user.click(screen.getByRole("button", { name: "إنشاء" }));

    expect(createTenantMock).not.toHaveBeenCalled();
    const alert = screen.getByRole("alert");
    const alertText = alert.textContent ?? "";
    expect(alertText).toMatch(/النطاق الفرعي|البريد/i);
    // No raw regex as the primary user message.
    expect(alertText).not.toMatch(/[\^$\\]|(?:\(\?)/);
  });

  it("validates tenant edit country and currency structurally with human-readable messages", async () => {
    const user = userEvent.setup();
    hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
    tenantMock.mockResolvedValue({
      id: "11111111-1111-1111-1111-111111111111",
      name: "Acme Corp",
      legalName: null,
      subdomain: "acme",
      status: "ACTIVE",
      billingEmail: "billing@acme.example",
      countryCode: "SA",
      locale: "ar-SA",
      timezone: "Asia/Riyadh",
      currencyCode: "SAR",
      trialEndsAt: null,
      suspensionReason: null,
      createdAt: "2026-09-09T00:00:00Z",
      updatedAt: "2026-09-09T00:00:00Z",
    });
    render(<TenantsPage />);
    await waitFor(() => expect(screen.getByText("Acme")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: "تحديث" }));
    await waitFor(() => expect(screen.getByRole("dialog", { name: "تحديث بيانات الحساب" })).toBeInTheDocument());

    const country = screen.getByLabelText("الدولة");
    await user.clear(country);
    await user.type(country, "S1");
    await user.click(screen.getByRole("button", { name: "حفظ" }));

    expect(updateTenantMock).not.toHaveBeenCalled();
    const countryAlert = screen.getByRole("alert");
    expect(countryAlert.textContent).toMatch(/رمز الدولة/);
    expect(countryAlert.textContent).not.toMatch(/[\^$\\[\]{}]/);

    // Fix the country, then break the currency: the currency message is the one surfaced.
    const countryFixed = screen.getByLabelText("الدولة");
    await user.clear(countryFixed);
    await user.type(countryFixed, "SA");
    const currency = screen.getByLabelText("العملة");
    await user.clear(currency);
    await user.type(currency, "EU4");
    await user.click(screen.getByRole("button", { name: "حفظ" }));

    expect(updateTenantMock).not.toHaveBeenCalled();
    const currencyAlert = screen.getByRole("alert");
    expect(currencyAlert.textContent).toMatch(/رمز العملة/);
    expect(currencyAlert.textContent).not.toMatch(/[\^$\\[\]{}]/);
  });

  it("surfaces localized backend errors inside the create dialog without raw internals", async () => {
    const user = userEvent.setup();
    hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
    createTenantMock.mockRejectedValue(
      new ApiHttpError("Request failed", {
        status: 500,
        error: "Internal Server Error",
        message: "Duplicate key value violates unique constraint uk_tenants_subdomain",
        path: "/api/v1/executive/tenants",
        requestId: "req-g1a-1",
        body: null,
      }),
    );
    render(<TenantsPage />);
    await waitFor(() => expect(screen.getByText("Acme")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: "إنشاء حساب جديد" }));
    await user.type(screen.getByLabelText("اسم الحساب"), "شركة اختبار");
    await user.type(screen.getByLabelText("النطاق الفرعي"), "acme");
    await user.type(screen.getByLabelText("بريد المسؤول"), "admin@acme.example");
    await user.type(screen.getByLabelText("اسم المسؤول"), "مدير النظام");
    await user.click(screen.getByRole("button", { name: "إنشاء" }));

    await waitFor(() => expect(createTenantMock).toHaveBeenCalledTimes(1));

    // The dialog must stay open and carry the localized error inside it.
    expect(screen.getByRole("dialog", { name: "إنشاء حساب جديد" })).toBeInTheDocument();
    const dialog = screen.getByRole("dialog", { name: "إنشاء حساب جديد" });
    const alert = within(dialog).getByRole("alert");
    const alertText = alert.textContent ?? "";
    expect(alertText).toMatch(/خطأ|تعذر|حدث/); // Arabic localized backend failure
    expect(alertText).not.toMatch(/uk_tenants_subdomain|Duplicate key|constraint/i); // no raw internals
  });
});
