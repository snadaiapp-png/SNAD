// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { AuthResponse, MeResponse } from "@/lib/api/auth";
import { AuthProvider } from "@/lib/auth/auth-provider";
import { ThemeProvider } from "@/lib/theme/ThemeProvider";
import { I18nProvider } from "@/lib/i18n/I18nProvider";
import { TenantContextProvider } from "@/lib/auth/tenant-context";
import WorkspacePage from "./page";

const { authApiMock, replaceMock } = vi.hoisted(() => ({
  authApiMock: {
    refresh: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
    me: vi.fn(),
    changeCredential: vi.fn(),
  },
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
    capabilities: (response as { capabilities?: string[] }).capabilities ?? [],
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
  useRouter: () => ({
    replace: replaceMock,
    push: vi.fn(),
    back: vi.fn(),
    refresh: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

const defaultUser = {
  id: "u1",
  tenantId: "tenant-aaaa-bbbb-8F21",
  email: "admin@snad.app",
  displayName: "Admin User",
  status: "ACTIVE",
};

function bootstrap(accessToken = "token"): AuthResponse {
  return {
    accessToken,
    expiresAt: "2099-01-01T00:00:00Z",
    user: defaultUser,
    lastLoginAt: null,
    credentialRotationRequired: false,
    memberships: [],
    effectiveRoleGrants: [{
      id: "grant-admin",
      roleId: "role-admin",
      roleCode: "ADMIN",
      organizationId: null,
      status: "ACTIVE",
    }],
    capabilities: [],
    defaultOrganizationId: null,
    defaultDestination: "/control-plane",
    availableDestinations: ["/workspace", "/crm", "/crm/command-center", "/control-plane"],
    tenantContext: { tenantId: defaultUser.tenantId, defaultOrganizationId: null },
  };
}

function setSessionHint(): void {
  document.cookie = "sanad_session_hint=1; Path=/";
}

function clearSessionHint(): void {
  document.cookie = "sanad_session_hint=; Max-Age=0; Path=/";
}

function renderPage() {
  return render(
    <ThemeProvider>
      <I18nProvider>
        <AuthProvider>
          <TenantContextProvider><WorkspacePage /></TenantContextProvider>
        </AuthProvider>
      </I18nProvider>
    </ThemeProvider>,
  );
}

describe("WorkspacePage", () => {
  beforeEach(() => {
    clearSessionHint();
    for (const mock of Object.values(authApiMock)) mock.mockReset();
    replaceMock.mockReset();
  });

  afterEach(() => {
    cleanup();
    clearSessionHint();
  });

  it("protects the route from anonymous users and preserves the return destination", async () => {
    renderPage();
    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/?returnUrl=%2Fworkspace"));
    expect(authApiMock.refresh).not.toHaveBeenCalled();
  });

  it("renders one executive launcher instead of duplicating the legacy control-plane route", async () => {
    setSessionHint();
    authApiMock.refresh.mockResolvedValue(bootstrap());
    const { container } = renderPage();
    await waitFor(() => expect(screen.getByRole("heading", { name: /Admin User/ })).toBeInTheDocument());
    expect(screen.getByText(defaultUser.tenantId)).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: /CRM/ }).length).toBeGreaterThan(0);
    expect(container.querySelector('a[href="/executive"]')).toBeInTheDocument();
    expect(container.querySelector('a[href="/control-plane"]')).not.toBeInTheDocument();
    expect(authApiMock.me).not.toHaveBeenCalled();
  });

  it("renders the HR workspace launcher when the authenticated user has an HRM capability", async () => {
    setSessionHint();
    authApiMock.refresh.mockResolvedValue({
      ...bootstrap(),
      capabilities: ["HRM.EMPLOYEE.VIEW"],
    });
    const { container } = renderPage();
    await waitFor(() => expect(screen.getByRole("heading", { name: /Admin User/ })).toBeInTheDocument());
    expect(container.querySelector('a[href="/hr"]')).toBeInTheDocument();
  });

  it("performs logout and redirects to the login page", async () => {
    setSessionHint();
    authApiMock.refresh.mockResolvedValue(bootstrap());
    authApiMock.logout.mockResolvedValue(undefined);
    renderPage();
    const logoutButton = await screen.findByRole("button", { name: "تسجيل الخروج" });
    logoutButton.click();
    await waitFor(() => expect(authApiMock.logout).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/"));
  });

  it("does not render the access token", async () => {
    setSessionHint();
    authApiMock.refresh.mockResolvedValue(bootstrap("jwt-secret-token-xyz"));
    const { container } = renderPage();
    await waitFor(() => expect(screen.getByRole("heading", { name: /Admin User/ })).toBeInTheDocument());
    expect(container.textContent).not.toContain("jwt-secret-token-xyz");
  });
});
