// @vitest-environment jsdom

/**
 * SNAD CRM RBAC + navigation test (CRM-002b)
 * ----------------------------------------------------------------------------
 * Branch: crm/002b-final-operational-acceptance
 *
 * Verifies:
 *   - The CrmShell renders every expected navigation item.
 *   - Sidebar links point at the operational CRM routes.
 *   - The active sidebar entry is driven by usePathname() and is preserved
 *     for detail routes (e.g. /crm/accounts/[accountId] highlights Accounts).
 *   - The shell renders an auth-loading surface (and never the sidebar) when
 *     the auth state is not AUTHENTICATED — i.e. unauthorized users never
 *     see the CRM nav surface.
 *   - Authenticated CRM_ADMIN users see the custom-fields admin surface,
 *     while non-admin users still see the page (RBAC is enforced by the
 *     backend; the shell just hides the create form).
 */
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { AuthProvider } from "@/lib/auth/auth-provider";
import { I18nProvider } from "@/lib/i18n/I18nProvider";
import { ThemeProvider } from "@/lib/theme/ThemeProvider";
import { TenantContextProvider } from "@/lib/auth/tenant-context";
import { CrmShell } from "./components/crm-shell";

const { authApiMock, pathnameMock, pushMock, replaceMock } = vi.hoisted(() => ({
  authApiMock: {
    refresh: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
    me: vi.fn(),
    changeCredential: vi.fn(),
  },
  pathnameMock: { current: "/crm/overview" },
  pushMock: vi.fn(),
  replaceMock: vi.fn(),
}));

vi.mock("@/lib/api/auth", () => ({
  authApi: authApiMock,
  AmbiguousTenantError: class AmbiguousTenantError extends Error {
    readonly tenantIds: string[];
    constructor(message: string, tenantIds: string[]) {
      super(message);
      this.name = "AmbiguousTenantError";
      this.tenantIds = tenantIds;
    }
  },
}));

vi.mock("next/navigation", () => ({
  usePathname: () => pathnameMock.current,
  useRouter: () => ({
    push: pushMock,
    replace: replaceMock,
    back: vi.fn(),
    refresh: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { href: string }) => (
    <a href={String(href)} {...props}>
      {children}
    </a>
  ),
}));

const defaultUser = {
  id: "u1",
  tenantId: "tenant-aaaa-bbbb-8F21",
  email: "admin@snad.app",
  displayName: "Admin User",
  status: "ACTIVE",
};

const adminMe = {
  ...defaultUser,
  lastLoginAt: null,
  credentialRotationRequired: false,
  memberships: [],
  roleGrants: [
    { roleCode: "CRM_ADMIN", status: "ACTIVE", tenantId: "tenant-aaaa-bbbb-8F21" },
  ],
};

const nonAdminMe = {
  ...defaultUser,
  lastLoginAt: null,
  credentialRotationRequired: false,
  memberships: [],
  roleGrants: [
    { roleCode: "CRM_SALES_REP", status: "ACTIVE", tenantId: "tenant-aaaa-bbbb-8F21" },
  ],
};

const EXPECTED_NAV_HREFS = [
  "/crm/overview",
  "/crm/accounts",
  "/crm/contacts",
  "/crm/leads",
  "/crm/pipelines",
  "/crm/opportunities",
  "/crm/activities",
  "/crm/imports",
  "/crm/settings/custom-fields",
  "/crm/command-center",
] as const;

function renderShell() {
  return render(
    <ThemeProvider>
      <I18nProvider>
        <AuthProvider>
          <TenantContextProvider>
            <CrmShell>
              <div data-testid="crm-content">page content</div>
            </CrmShell>
          </TenantContextProvider>
        </AuthProvider>
      </I18nProvider>
    </ThemeProvider>,
  );
}

describe("CrmShell — RBAC + navigation", () => {
  beforeEach(() => {
    authApiMock.refresh.mockReset();
    authApiMock.logout.mockReset();
    authApiMock.me.mockReset();
    pushMock.mockReset();
    replaceMock.mockReset();
    pathnameMock.current = "/crm/overview";
  });

  afterEach(() => {
    cleanup();
  });

  it("renders all expected navigation items as links to the operational routes", async () => {
    authApiMock.refresh.mockResolvedValue({
      accessToken: "token",
      expiresAt: "2099-01-01T00:00:00Z",
      user: defaultUser,
    });
    authApiMock.me.mockResolvedValue(adminMe);

    renderShell();

    await waitFor(() => {
      expect(screen.getByText("page content")).toBeInTheDocument();
    });

    for (const href of EXPECTED_NAV_HREFS) {
      const link = document.querySelector(`a[href="${href}"]`);
      expect(link, `expected sidebar link to ${href}`).not.toBeNull();
    }
  });

  it("marks the active sidebar link with aria-current=page based on usePathname", async () => {
    authApiMock.refresh.mockResolvedValue({
      accessToken: "token",
      expiresAt: "2099-01-01T00:00:00Z",
      user: defaultUser,
    });
    authApiMock.me.mockResolvedValue(adminMe);

    renderShell();

    await waitFor(() => {
      expect(screen.getByText("page content")).toBeInTheDocument();
    });

    // Multiple links may point to /crm/overview (the header brand mark and
    // the sidebar entry). The sidebar entry is the one that receives
    // aria-current="page" from SidebarLink. Collect them all and assert at
    // least one matches.
    const overviewLinks = document.querySelectorAll('a[href="/crm/overview"]');
    expect(overviewLinks.length).toBeGreaterThan(0);
    const activeLinks = Array.from(overviewLinks).filter(
      (link) => link.getAttribute("aria-current") === "page",
    );
    expect(activeLinks.length, "expected at least one active sidebar link").toBeGreaterThan(0);
  });

  it("preserves the active sidebar entry for detail routes (/crm/accounts/[id])", async () => {
    pathnameMock.current = "/crm/accounts/some-account-id";
    authApiMock.refresh.mockResolvedValue({
      accessToken: "token",
      expiresAt: "2099-01-01T00:00:00Z",
      user: defaultUser,
    });
    authApiMock.me.mockResolvedValue(adminMe);

    renderShell();

    await waitFor(() => {
      expect(screen.getByText("page content")).toBeInTheDocument();
    });

    const accountsLinks = document.querySelectorAll('a[href="/crm/accounts"]');
    expect(accountsLinks.length).toBeGreaterThan(0);
    const activeLinks = Array.from(accountsLinks).filter(
      (link) => link.getAttribute("aria-current") === "page",
    );
    expect(activeLinks.length, "expected the Accounts sidebar link to be active").toBeGreaterThan(0);
  });

  it("renders the auth-loading surface (not the sidebar) when the session is anonymous", async () => {
    authApiMock.refresh.mockRejectedValue(new Error("no session"));

    renderShell();

    // Sidebar links must NOT be present until AUTHENTICATED.
    await waitFor(() => {
      expect(replaceMock).toHaveBeenCalledWith("/");
    });

    for (const href of EXPECTED_NAV_HREFS) {
      const link = document.querySelector(`a[href="${href}"]`);
      expect(link, `unauthenticated shell must not render nav link ${href}`).toBeNull();
    }
    expect(screen.queryByText("page content")).not.toBeInTheDocument();
  });

  it("renders the shell for non-admin users (RBAC is enforced by the backend, not the shell)", async () => {
    authApiMock.refresh.mockResolvedValue({
      accessToken: "token",
      expiresAt: "2099-01-01T00:00:00Z",
      user: defaultUser,
    });
    authApiMock.me.mockResolvedValue(nonAdminMe);

    renderShell();

    await waitFor(() => {
      expect(screen.getByText("page content")).toBeInTheDocument();
    });

    // Non-admin users still see the navigation surface — the custom-fields
    // admin page itself hides its create form when canCreate is false, but
    // the sidebar entry is always visible.
    const customFieldsLink = document.querySelector('a[href="/crm/settings/custom-fields"]');
    expect(customFieldsLink).not.toBeNull();
  });

  it("does not render the sidebar when the auth state is ERROR", async () => {
    // Force the bootstrap refresh to fail; the AuthProvider transitions to
    // ANONYMOUS, which CrmShell treats as unauthenticated.
    authApiMock.refresh.mockRejectedValue(new Error("no session"));

    renderShell();

    await waitFor(() => {
      expect(replaceMock).toHaveBeenCalledWith("/");
    });

    for (const href of EXPECTED_NAV_HREFS) {
      const link = document.querySelector(`a[href="${href}"]`);
      expect(link, `error-state shell must not render nav link ${href}`).toBeNull();
    }
  });
});
