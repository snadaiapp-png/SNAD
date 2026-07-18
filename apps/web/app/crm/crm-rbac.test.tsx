// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { AuthResponse, MeResponse } from "@/lib/api/auth";
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
  authResponseToMe: (response: AuthResponse): MeResponse => ({
    ...response.user,
    lastLoginAt: response.lastLoginAt,
    credentialRotationRequired: response.credentialRotationRequired,
    memberships: response.memberships,
    roleGrants: response.effectiveRoleGrants,
  }),
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
  default: ({ href, children, ...props }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { href: string }) => (
    <a href={String(href)} {...props}>{children}</a>
  ),
}));

const defaultUser = {
  id: "u1",
  tenantId: "tenant-aaaa-bbbb-8F21",
  email: "admin@snad.app",
  displayName: "Admin User",
  status: "ACTIVE",
};

const adminRoles = [{
  id: "grant-admin",
  roleId: "role-admin",
  roleCode: "CRM_ADMIN",
  organizationId: null,
  status: "ACTIVE",
}];

const nonAdminRoles = [{
  id: "grant-sales",
  roleId: "role-sales",
  roleCode: "CRM_SALES_REP",
  organizationId: null,
  status: "ACTIVE",
}];

function bootstrap(effectiveRoleGrants = adminRoles): AuthResponse {
  return {
    accessToken: "token",
    expiresAt: "2099-01-01T00:00:00Z",
    user: defaultUser,
    lastLoginAt: null,
    credentialRotationRequired: false,
    memberships: [],
    effectiveRoleGrants,
    defaultOrganizationId: null,
    defaultDestination: "/crm",
    availableDestinations: ["/workspace", "/crm", "/crm/command-center"],
    tenantContext: { tenantId: defaultUser.tenantId, defaultOrganizationId: null },
  };
}

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

function setSessionHint(): void {
  document.cookie = "sanad_session_hint=1; Path=/";
}

function clearSessionHint(): void {
  document.cookie = "sanad_session_hint=; Max-Age=0; Path=/";
}

function renderShell() {
  return render(
    <ThemeProvider>
      <I18nProvider>
        <AuthProvider>
          <TenantContextProvider>
            <CrmShell><div data-testid="crm-content">page content</div></CrmShell>
          </TenantContextProvider>
        </AuthProvider>
      </I18nProvider>
    </ThemeProvider>,
  );
}

describe("CrmShell — RBAC + navigation", () => {
  beforeEach(() => {
    clearSessionHint();
    for (const mock of Object.values(authApiMock)) mock.mockReset();
    pushMock.mockReset();
    replaceMock.mockReset();
    pathnameMock.current = "/crm/overview";
  });

  afterEach(() => {
    cleanup();
    clearSessionHint();
  });

  it("renders all expected navigation items as links to the operational routes", async () => {
    setSessionHint();
    authApiMock.refresh.mockResolvedValue(bootstrap());
    renderShell();
    await waitFor(() => expect(screen.getByText("page content")).toBeInTheDocument());
    for (const href of EXPECTED_NAV_HREFS) {
      expect(document.querySelector(`a[href="${href}"]`), `expected sidebar link to ${href}`).not.toBeNull();
    }
    expect(authApiMock.me).not.toHaveBeenCalled();
  });

  it("marks the active sidebar link with aria-current=page based on usePathname", async () => {
    setSessionHint();
    authApiMock.refresh.mockResolvedValue(bootstrap());
    renderShell();
    await waitFor(() => expect(screen.getByText("page content")).toBeInTheDocument());
    const overviewLinks = document.querySelectorAll('a[href="/crm/overview"]');
    expect(Array.from(overviewLinks).some((link) => link.getAttribute("aria-current") === "page")).toBe(true);
  });

  it("preserves the active sidebar entry for detail routes (/crm/accounts/[id])", async () => {
    pathnameMock.current = "/crm/accounts/some-account-id";
    setSessionHint();
    authApiMock.refresh.mockResolvedValue(bootstrap());
    renderShell();
    await waitFor(() => expect(screen.getByText("page content")).toBeInTheDocument());
    const accountLinks = document.querySelectorAll('a[href="/crm/accounts"]');
    expect(Array.from(accountLinks).some((link) => link.getAttribute("aria-current") === "page")).toBe(true);
  });

  it("renders the auth-loading surface (not the sidebar) when the session is anonymous", async () => {
    renderShell();
    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/"));
    for (const href of EXPECTED_NAV_HREFS) {
      expect(document.querySelector(`a[href="${href}"]`)).toBeNull();
    }
    expect(screen.queryByText("page content")).not.toBeInTheDocument();
    expect(authApiMock.refresh).not.toHaveBeenCalled();
  });

  it("renders the shell for non-admin users (RBAC is enforced by the backend, not the shell)", async () => {
    setSessionHint();
    authApiMock.refresh.mockResolvedValue(bootstrap(nonAdminRoles));
    renderShell();
    await waitFor(() => expect(screen.getByText("page content")).toBeInTheDocument());
    expect(document.querySelector('a[href="/crm/settings/custom-fields"]')).not.toBeNull();
  });

  it("does not render the sidebar when session restoration enters ERROR", async () => {
    setSessionHint();
    authApiMock.refresh.mockRejectedValue(new Error("backend unavailable"));
    renderShell();
    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/"));
    for (const href of EXPECTED_NAV_HREFS) {
      expect(document.querySelector(`a[href="${href}"]`)).toBeNull();
    }
  });
});
