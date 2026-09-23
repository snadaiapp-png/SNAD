// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { AuthResponse } from "@/lib/api/auth";
import { AuthProvider } from "@/lib/auth/auth-provider";
import { I18nProvider } from "@/lib/i18n/I18nProvider";
import { AuthEntry } from "./auth-entry";

const { authApiMock, replaceMock, prefetchMock } = vi.hoisted(() => ({
  authApiMock: {
    refresh: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
    me: vi.fn(),
    changeCredential: vi.fn(),
  },
  replaceMock: vi.fn(),
  prefetchMock: vi.fn(),
}));

vi.mock("@/lib/api/auth", () => ({
  authApi: authApiMock,
  authResponseToMe: (response: AuthResponse) => ({
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
  useRouter: () => ({
    replace: replaceMock,
    push: vi.fn(),
    back: vi.fn(),
    refresh: vi.fn(),
    prefetch: prefetchMock,
  }),
}));

const bootstrap = {
  accessToken: "token",
  expiresAt: "2099-01-01T00:00:00Z",
  user: { id: "u1", tenantId: "t1", email: "test@example.com", displayName: null, status: "ACTIVE" },
  lastLoginAt: null,
  credentialRotationRequired: false,
  memberships: [],
  effectiveRoleGrants: [],
  defaultOrganizationId: null,
  defaultDestination: "/crm",
  availableDestinations: ["/workspace", "/crm"],
  tenantContext: { tenantId: "t1", defaultOrganizationId: null },
};

const executiveBootstrap = {
  ...bootstrap,
  defaultDestination: "/executive",
  availableDestinations: ["/workspace", "/executive"],
};

function renderEntry() {
  return render(<I18nProvider><AuthProvider><AuthEntry /></AuthProvider></I18nProvider>);
}

function clearHint() {
  document.cookie = "sanad_session_hint=; Max-Age=0; Path=/";
}

describe("AuthEntry session bootstrap", () => {
  beforeEach(() => {
    clearHint();
    for (const mock of Object.values(authApiMock)) mock.mockReset();
    replaceMock.mockReset();
    prefetchMock.mockReset();
    window.history.replaceState({}, "", "/");
  });

  afterEach(() => {
    cleanup();
    clearHint();
  });

  it("renders the login form immediately for a new visitor and skips refresh", async () => {
    renderEntry();
    await waitFor(() => expect(screen.getByPlaceholderText("name@company.com")).toBeInTheDocument());
    expect(screen.queryByText("جارٍ تجهيز مساحة العمل")).not.toBeInTheDocument();
    expect(authApiMock.refresh).not.toHaveBeenCalled();
  });

  it("shows session restoration only when the browser carries a session hint", async () => {
    document.cookie = "sanad_session_hint=1; Path=/";
    authApiMock.refresh.mockImplementation(() => new Promise(() => {}));
    renderEntry();
    await waitFor(() => expect(screen.getByRole("status")).toBeInTheDocument());
    expect(screen.getByText("جارٍ استعادة الجلسة…")).toBeInTheDocument();
    expect(screen.queryByPlaceholderText("name@company.com")).not.toBeInTheDocument();
  });

  it("redirects to workspace after session bootstrap without /me", async () => {
    document.cookie = "sanad_session_hint=1; Path=/";
    authApiMock.refresh.mockResolvedValue(bootstrap);
    renderEntry();
    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/workspace"));
    expect(prefetchMock).toHaveBeenCalledWith("/workspace");
    expect(authApiMock.me).not.toHaveBeenCalled();
  });

  it("honors an authorized returnUrl after authentication", async () => {
    document.cookie = "sanad_session_hint=1; Path=/";
    window.history.replaceState({}, "", "/?returnUrl=%2Fcrm%2Fleads");
    authApiMock.refresh.mockResolvedValue(bootstrap);
    renderEntry();
    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/crm/leads"));
  });

  it("honors an authorized nested executive returnUrl after session bootstrap", async () => {
    document.cookie = "sanad_session_hint=1; Path=/";
    window.history.replaceState({}, "", "/?returnUrl=%2Fexecutive%2Ftenants");
    authApiMock.refresh.mockResolvedValue(executiveBootstrap);
    renderEntry();
    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/executive/tenants"));
    expect(prefetchMock).toHaveBeenCalledWith("/executive/tenants");
  });

  it("falls back to workspace when nested executive returnUrl is not authorized", async () => {
    document.cookie = "sanad_session_hint=1; Path=/";
    window.history.replaceState({}, "", "/?returnUrl=%2Fexecutive%2Ftenants");
    authApiMock.refresh.mockResolvedValue(bootstrap);
    renderEntry();
    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/workspace"));
    expect(prefetchMock).toHaveBeenCalledWith("/workspace");
  });

  it("submits the tenant selected by a tenant login link with the credentials", async () => {
    const tenantId = "11111111-1111-1111-1111-111111111111";
    window.history.replaceState({}, "", `/?tenantId=${tenantId}`);
    authApiMock.login.mockImplementation(() => new Promise(() => {}));
    renderEntry();

    const email = await screen.findByPlaceholderText("name@company.com");
    fireEvent.change(email, { target: { value: "owner@acme.example" } });
    fireEvent.change(screen.getByLabelText("كلمة المرور"), { target: { value: "secret" } });
    fireEvent.submit(email.closest("form")!);

    await waitFor(() => expect(authApiMock.login).toHaveBeenCalledWith({
      email: "owner@acme.example",
      password: "secret",
      tenantId,
    }));
  });
});
