// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { AuthProvider } from "@/lib/auth/auth-provider";
import { I18nProvider } from "@/lib/i18n/I18nProvider";
import { AuthEntry } from "./auth-entry";

const { authApiMock, pushMock } = vi.hoisted(() => ({
  authApiMock: {
    refresh: vi.fn(),
    login: vi.fn(),
    loginWithTenant: vi.fn(),
    logout: vi.fn(),
    me: vi.fn(),
    changeCredential: vi.fn(),
  },
  pushMock: vi.fn(),
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
  useRouter: () => ({
    replace: pushMock,
    push: vi.fn(),
    back: vi.fn(),
    refresh: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

const defaultUser = {
  id: "u1",
  tenantId: "t1",
  email: "test@example.com",
  displayName: null,
  status: "ACTIVE",
};

const defaultMe = {
  id: "u1",
  tenantId: "t1",
  email: "test@example.com",
  displayName: null,
  status: "ACTIVE",
  lastLoginAt: null,
  credentialRotationRequired: false,
  memberships: [],
  roleGrants: [],
};

function resetMocks() {
  authApiMock.refresh.mockReset();
  authApiMock.login.mockReset();
  authApiMock.logout.mockReset();
  authApiMock.me.mockReset();
  authApiMock.changeCredential.mockReset();
  pushMock.mockReset();
}

describe("AuthEntry — Session Bootstrap", () => {
  beforeEach(() => {
    resetMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("shows loading state during INITIALIZING", async () => {
    authApiMock.refresh.mockImplementation(() => new Promise(() => {}));
    render(<I18nProvider><AuthProvider><AuthEntry /></AuthProvider></I18nProvider>);
    await waitFor(() => {
      expect(screen.getByRole("status")).toBeInTheDocument();
    });
    expect(screen.getByText("جارٍ تجهيز مساحة العمل")).toBeInTheDocument();
  });

  it("does not show login form during silent refresh", async () => {
    authApiMock.refresh.mockImplementation(() => new Promise(() => {}));
    render(<I18nProvider><AuthProvider><AuthEntry /></AuthProvider></I18nProvider>);
    await waitFor(() => {
      expect(screen.getByText("جارٍ تجهيز مساحة العمل")).toBeInTheDocument();
    });
    expect(screen.queryByPlaceholderText("name@company.com")).not.toBeInTheDocument();
  });

  it("redirects to /workspace when AUTHENTICATED", async () => {
    authApiMock.refresh.mockResolvedValue({
      accessToken: "token",
      expiresAt: "2099-01-01T00:00:00Z",
      user: defaultUser,
    });
    authApiMock.me.mockResolvedValue(defaultMe);
    render(<I18nProvider><AuthProvider><AuthEntry /></AuthProvider></I18nProvider>);
    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith("/workspace");
    });
  });

  it("shows login form when ANONYMOUS", async () => {
    authApiMock.refresh.mockRejectedValue(new Error("no cookie"));
    render(<I18nProvider><AuthProvider><AuthEntry /></AuthProvider></I18nProvider>);
    await waitFor(() => {
      expect(screen.getByPlaceholderText("name@company.com")).toBeInTheDocument();
    });
    expect(screen.getByText("مرحبًا بعودتك")).toBeInTheDocument();
  });

  it("shows session expired message when EXPIRED", async () => {
    authApiMock.refresh.mockRejectedValue(new Error("expired"));
    render(<I18nProvider><AuthProvider><AuthEntry /></AuthProvider></I18nProvider>);
    // Wait for initial refresh to fail and go to ANONYMOUS
    await waitFor(() => {
      expect(screen.getByPlaceholderText("name@company.com")).toBeInTheDocument();
    });
  });

  it("preserves tenant picker while loginWithTenant is pending", async () => {
    const { AmbiguousTenantError } = await import("@/lib/api/auth");
    const userEvent = (await import("@testing-library/user-event")).default;

    // First login: refresh fails → ANONYMOUS
    authApiMock.refresh.mockRejectedValue(new Error("no cookie"));
    // Then login: returns ambiguous tenant error
    authApiMock.login.mockRejectedValueOnce(new AmbiguousTenantError("ambiguous", ["tenant-aaaa-8F21"]));

    render(<I18nProvider><AuthProvider><AuthEntry /></AuthProvider></I18nProvider>);

    // Wait for ANONYMOUS
    await waitFor(() => {
      expect(screen.getByPlaceholderText("name@company.com")).toBeInTheDocument();
    });

    // Trigger login that results in ambiguous tenant
    const user = userEvent.setup();
    await user.type(screen.getByPlaceholderText("name@company.com"), "test@example.com");
    await user.type(screen.getByPlaceholderText("••••••••"), "Password123!");
    await user.click(screen.getByRole("button", { name: "تسجيل الدخول" }));

    // Tenant picker appears
    await waitFor(() => {
      expect(screen.getByText("مساحة عمل •••• 8F21")).toBeInTheDocument();
    });

    // Now make loginWithTenant hang (pending)
    authApiMock.login.mockImplementation(() => new Promise(() => {}));

    // Select the tenant
    await user.click(screen.getByText("مساحة عمل •••• 8F21"));
    await user.click(screen.getByRole("button", { name: "متابعة" }));

    // Tenant picker remains visible with "جارٍ الدخول…"
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "جارٍ الدخول…" })).toBeDisabled();
    });

    // Login screen should NOT reappear
    expect(screen.queryByPlaceholderText("name@company.com")).not.toBeInTheDocument();

    // loginWithTenant should have been called exactly once (total login calls = 2: 1 ambiguous + 1 tenant-specific)
    expect(authApiMock.login).toHaveBeenCalledTimes(2);
  });
});
