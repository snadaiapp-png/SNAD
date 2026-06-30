// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { AuthProvider } from "@/lib/auth/auth-provider";
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
    render(<AuthProvider><AuthEntry /></AuthProvider>);
    await waitFor(() => {
      expect(screen.getByRole("status")).toBeInTheDocument();
    });
    expect(screen.getByText("جارٍ تجهيز مساحة العمل")).toBeInTheDocument();
  });

  it("does not show login form during silent refresh", async () => {
    authApiMock.refresh.mockImplementation(() => new Promise(() => {}));
    render(<AuthProvider><AuthEntry /></AuthProvider>);
    await waitFor(() => {
      expect(screen.getByText("جارٍ تجهيز مساحة العمل")).toBeInTheDocument();
    });
    expect(screen.queryByPlaceholderText("you@example.com")).not.toBeInTheDocument();
  });

  it("redirects to /workspace when AUTHENTICATED", async () => {
    authApiMock.refresh.mockResolvedValue({
      accessToken: "token",
      expiresAt: "2099-01-01T00:00:00Z",
      user: defaultUser,
    });
    authApiMock.me.mockResolvedValue(defaultMe);
    render(<AuthProvider><AuthEntry /></AuthProvider>);
    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith("/workspace");
    });
  });

  it("shows login form when ANONYMOUS", async () => {
    authApiMock.refresh.mockRejectedValue(new Error("no cookie"));
    render(<AuthProvider><AuthEntry /></AuthProvider>);
    await waitFor(() => {
      expect(screen.getByPlaceholderText("you@example.com")).toBeInTheDocument();
    });
    expect(screen.getByText("مرحبًا بعودتك")).toBeInTheDocument();
  });

  it("shows session expired message when EXPIRED", async () => {
    authApiMock.refresh.mockRejectedValue(new Error("expired"));
    render(<AuthProvider><AuthEntry /></AuthProvider>);
    // Wait for initial refresh to fail and go to ANONYMOUS
    await waitFor(() => {
      expect(screen.getByPlaceholderText("you@example.com")).toBeInTheDocument();
    });
  });
});
