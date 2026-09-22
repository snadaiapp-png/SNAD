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
const recordTenantLoginLinkEventMock = vi.fn();

vi.mock("@/lib/api/scp-api", () => ({
  scpApi: { tenants: (...args: unknown[]) => tenantsMock(...args) },
}));

vi.mock("@/lib/api/executive-api", () => ({
  executiveApi: {
    createTenant: (...args: unknown[]) => createTenantMock(...args),
    tenant: (...args: unknown[]) => tenantMock(...args),
    updateTenant: (...args: unknown[]) => updateTenantMock(...args),
    changeTenantStatus: (...args: unknown[]) => changeTenantStatusMock(...args),
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
  recordTenantLoginLinkEventMock.mockReset();
  recordTenantLoginLinkEventMock.mockResolvedValue(undefined);
  hasMock.mockReset();
});

afterEach(() => cleanup());

describe("Executive tenant management controls", () => {
  it("manager sees create, update, freeze, archive and upgrade controls", async () => {
    hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
    render(<TenantsPage />);
    await waitFor(() => expect(screen.getByText("Acme")).toBeInTheDocument());

    expect(screen.getByRole("button", { name: "scp.tenants.create" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "scp.tenants.update" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "scp.tenants.freeze" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "scp.tenants.archive" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "scp.tenants.upgrade" })).toHaveAttribute(
      "href",
      "/executive/subscriptions?tenantId=11111111-1111-1111-1111-111111111111&intent=upgrade",
    );
    expect(hasMock).toHaveBeenCalledWith("EXECUTIVE_MANAGE");
  });

  it("opens an isolated synchronous placeholder and navigates it only after the audit event succeeds", async () => {
    const user = userEvent.setup();
    const popup = { close: vi.fn(), location: { href: "about:blank" }, opener: window } as unknown as Window;
    const openMock = vi.spyOn(window, "open").mockReturnValue(popup);
    hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
    render(<TenantsPage />);
    await screen.findByText("Acme");

    await user.click(screen.getByRole("button", { name: "scp.tenants.openLogin" }));

    expect(recordTenantLoginLinkEventMock).toHaveBeenCalledWith(
      "11111111-1111-1111-1111-111111111111",
      "OPEN",
    );
    expect(openMock).toHaveBeenCalledWith("about:blank", "_blank");
    expect(popup.opener).toBeNull();
    expect(popup.location.href).toBe(
      "http://localhost:3000/?tenantId=11111111-1111-1111-1111-111111111111",
    );
    openMock.mockRestore();
  });

  it("copies an active tenant login URL and records the copy event", async () => {
    const user = userEvent.setup();
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", { configurable: true, value: { writeText } });
    hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
    render(<TenantsPage />);
    await screen.findByText("Acme");

    await user.click(screen.getByRole("button", { name: "scp.tenants.copyLogin" }));

    expect(recordTenantLoginLinkEventMock).toHaveBeenCalledWith(
      "11111111-1111-1111-1111-111111111111",
      "COPY",
    );
    expect(writeText).toHaveBeenCalledWith(
      "http://localhost:3000/?tenantId=11111111-1111-1111-1111-111111111111",
    );
    expect(screen.getByText("scp.tenants.notice.loginLinkCopied")).toBeInTheDocument();
  });

  it("does not expose login-link controls for a non-active tenant", async () => {
    tenantsMock.mockResolvedValue({
      ...PAGE,
      content: [{ ...PAGE.content[0], status: "ARCHIVED", subscriptionStatus: "TERMINATED" }],
    });
    hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
    render(<TenantsPage />);
    await screen.findByText("Acme");

    expect(screen.queryByRole("button", { name: "scp.tenants.openLogin" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "scp.tenants.copyLogin" })).not.toBeInTheDocument();
  });

  it("does not expose login-link controls when the tenant subscription is terminated", async () => {
    tenantsMock.mockResolvedValue({
      ...PAGE,
      content: [{ ...PAGE.content[0], status: "ACTIVE", subscriptionStatus: "TERMINATED" }],
    });
    hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
    render(<TenantsPage />);
    await screen.findByText("Acme");

    expect(screen.queryByRole("button", { name: "scp.tenants.openLogin" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "scp.tenants.copyLogin" })).not.toBeInTheDocument();
  });

  it("fails closed and closes the placeholder tab when login-link auditing fails", async () => {
    const user = userEvent.setup();
    const popup = { close: vi.fn(), location: { href: "about:blank" }, opener: window } as unknown as Window;
    const openMock = vi.spyOn(window, "open").mockReturnValue(popup);
    recordTenantLoginLinkEventMock.mockRejectedValue(new Error("audit unavailable"));
    hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
    render(<TenantsPage />);
    await screen.findByText("Acme");

    await user.click(screen.getByRole("button", { name: "scp.tenants.openLogin" }));

    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
    expect(screen.getByRole("alert")).not.toHaveTextContent("audit unavailable");
    expect(openMock).toHaveBeenCalledWith("about:blank", "_blank");
    expect(popup.opener).toBeNull();
    expect(popup.close).toHaveBeenCalledOnce();
    expect(popup.location.href).toBe("about:blank");
    openMock.mockRestore();
  });

  it("granular-only/read-only user never receives mutation controls", async () => {
    hasMock.mockImplementation((capability: string) => capability !== "EXECUTIVE_MANAGE");
    render(<TenantsPage />);
    await waitFor(() => expect(screen.getByText("Acme")).toBeInTheDocument());

    expect(screen.queryByRole("button", { name: "scp.tenants.create" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "scp.tenants.update" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "scp.tenants.freeze" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "scp.tenants.archive" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "scp.tenants.upgrade" })).not.toBeInTheDocument();
  });

  it("keeps focus on the input while typing multiple characters in the create dialog", async () => {
    const user = userEvent.setup();
    hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
    render(<TenantsPage />);
    await waitFor(() => expect(screen.getByText("Acme")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: "scp.tenants.create" }));
    const subdomain = screen.getByLabelText("scp.tenants.form.subdomain");
    await user.click(subdomain);
    await user.type(subdomain, "acme01");

    expect((subdomain as HTMLInputElement).value).toBe("acme01");
    expect(screen.getByLabelText("scp.tenants.form.subdomain")).toHaveFocus();
  });

  it("shows visible labels for every create-dialog field", async () => {
    const user = userEvent.setup();
    hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
    render(<TenantsPage />);
    await waitFor(() => expect(screen.getByText("Acme")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: "scp.tenants.create" }));

    for (const label of ["scp.tenants.form.name", "scp.tenants.form.subdomain", "scp.tenants.form.adminEmail", "scp.tenants.form.adminDisplayName"]) {
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

    await user.click(screen.getByRole("button", { name: "scp.tenants.create" }));
    await user.type(screen.getByLabelText("scp.tenants.form.name"), "شركة اختبار");
    await user.type(screen.getByLabelText("scp.tenants.form.subdomain"), "bad_domain");
    await user.type(screen.getByLabelText("scp.tenants.form.adminEmail"), "not-an-email");
    await user.type(screen.getByLabelText("scp.tenants.form.adminDisplayName"), "مدير النظام");

    await user.click(screen.getByRole("button", { name: "form.action.create" }));

    expect(createTenantMock).not.toHaveBeenCalled();
    const alert = screen.getByRole("alert");
    const alertText = alert.textContent ?? "";
    expect(alertText).toMatch(/scp\.tenants\.validation\.(subdomainInvalid|adminEmailInvalid)/i);
    // No raw regex as the primary user message.
    expect(alertText).not.toMatch(/[\^$\\]|(?:\(\?)/);
  });

  it("uses governed selectors for country, locale, timezone and currency and saves canonical values", async () => {
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
    updateTenantMock.mockResolvedValue(undefined);
    render(<TenantsPage />);
    await waitFor(() => expect(screen.getByText("Acme")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: "scp.tenants.update" }));
    await waitFor(() => expect(screen.getByRole("dialog", { name: "scp.tenants.editDialogTitle" })).toBeInTheDocument());

    const country = screen.getByLabelText("scp.tenants.form.countryCode");
    const locale = screen.getByLabelText("scp.tenants.form.locale");
    const timezone = screen.getByLabelText("scp.tenants.form.timezone");
    const currency = screen.getByLabelText("scp.tenants.form.currencyCode");

    expect(country.tagName).toBe("SELECT");
    expect(locale.tagName).toBe("SELECT");
    expect(timezone.tagName).toBe("SELECT");
    expect(currency.tagName).toBe("SELECT");
    expect(within(country).getByRole("option", { name: "AE" })).toBeInTheDocument();
    expect(within(currency).getByRole("option", { name: "AED" })).toBeInTheDocument();

    await user.selectOptions(country, "AE");
    await user.selectOptions(locale, "en-GB");
    await user.selectOptions(timezone, "Asia/Dubai");
    await user.selectOptions(currency, "AED");
    await user.click(screen.getByRole("button", { name: "form.action.save" }));

    await waitFor(() => expect(updateTenantMock).toHaveBeenCalledWith(
      "11111111-1111-1111-1111-111111111111",
      expect.objectContaining({
        countryCode: "AE",
        locale: "en-GB",
        timezone: "Asia/Dubai",
        currencyCode: "AED",
      }),
    ));
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

    await user.click(screen.getByRole("button", { name: "scp.tenants.create" }));
    await user.type(screen.getByLabelText("scp.tenants.form.name"), "شركة اختبار");
    await user.type(screen.getByLabelText("scp.tenants.form.subdomain"), "acme");
    await user.type(screen.getByLabelText("scp.tenants.form.adminEmail"), "admin@acme.example");
    await user.type(screen.getByLabelText("scp.tenants.form.adminDisplayName"), "مدير النظام");
    await user.click(screen.getByRole("button", { name: "form.action.create" }));

    await waitFor(() => expect(createTenantMock).toHaveBeenCalledTimes(1));

    // The dialog must stay open and carry the localized error inside it.
    expect(screen.getByRole("dialog", { name: "scp.tenants.createDialogTitle" })).toBeInTheDocument();
    const dialog = screen.getByRole("dialog", { name: "scp.tenants.createDialogTitle" });
    const alert = within(dialog).getByRole("alert");
    const alertText = alert.textContent ?? "";
    expect(alertText).toMatch(/خطأ|تعذر|حدث/); // Arabic localized backend failure
    expect(alertText).not.toMatch(/uk_tenants_subdomain|Duplicate key|constraint/i); // no raw internals
  });

  it("uses archive terminology for the non-destructive tenant action", async () => {
    hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
    render(<TenantsPage />);
    await waitFor(() => expect(screen.getByText("Acme")).toBeInTheDocument());

    expect(screen.getByRole("button", { name: "scp.tenants.archive" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "form.action.delete" })).not.toBeInTheDocument();
  });

  it("routes tenant management controls and create dialog through i18n keys", async () => {
    const user = userEvent.setup();
    hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
    render(<TenantsPage />);
    await waitFor(() => expect(screen.getByText("Acme")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: "scp.tenants.create" }));
    expect(screen.getByRole("dialog", { name: "scp.tenants.createDialogTitle" })).toBeInTheDocument();
    expect(screen.getByLabelText("scp.tenants.form.name")).toBeInTheDocument();
    expect(screen.getByLabelText("scp.tenants.form.subdomain")).toBeInTheDocument();
    expect(screen.getByLabelText("scp.tenants.form.adminEmail")).toBeInTheDocument();
    expect(screen.getByLabelText("scp.tenants.form.adminDisplayName")).toBeInTheDocument();
  });

});
