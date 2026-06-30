// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { AuthProvider } from "@/lib/auth/auth-provider";
import WorkspacePage from "./page";

const { authApiMock, pushMock } = vi.hoisted(() => ({
  authApiMock: {
    refresh: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
    me: vi.fn(),
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
  tenantId: "tenant-aaaa-bbbb-8F21",
  email: "admin@snad.app",
  displayName: "Admin User",
  status: "ACTIVE",
};

const defaultMe = {
  ...defaultUser,
  lastLoginAt: null,
  credentialRotationRequired: false,
  memberships: [],
  roleGrants: [],
};

describe("WorkspacePage", () => {
  beforeEach(() => {
    authApiMock.refresh.mockReset();
    authApiMock.logout.mockReset();
    authApiMock.me.mockReset();
    pushMock.mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it("protects the route from anonymous users (redirects to /)", async () => {
    authApiMock.refresh.mockRejectedValue(new Error("no session"));
    render(<AuthProvider><WorkspacePage /></AuthProvider>);
    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith("/");
    });
  });

  it("displays the authenticated user info", async () => {
    authApiMock.refresh.mockResolvedValue({
      accessToken: "token",
      expiresAt: "2099-01-01T00:00:00Z",
      user: defaultUser,
    });
    authApiMock.me.mockResolvedValue(defaultMe);
    render(<AuthProvider><WorkspacePage /></AuthProvider>);
    await waitFor(() => {
      expect(screen.getByText("Admin User")).toBeInTheDocument();
    });
    expect(screen.getByText("•••• 8F21")).toBeInTheDocument();
    expect(screen.getByText("نشطة")).toBeInTheDocument();
  });

  it("performs logout and redirects to /", async () => {
    authApiMock.refresh.mockResolvedValue({
      accessToken: "token",
      expiresAt: "2099-01-01T00:00:00Z",
      user: defaultUser,
    });
    authApiMock.me.mockResolvedValue(defaultMe);
    authApiMock.logout.mockResolvedValue(undefined);
    render(<AuthProvider><WorkspacePage /></AuthProvider>);
    const logoutButton = await screen.findByRole("button", { name: "تسجيل الخروج" });
    logoutButton.click();
    await waitFor(() => {
      expect(authApiMock.logout).toHaveBeenCalledTimes(1);
    });
    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith("/");
    });
  });

  it("does not display the access token", async () => {
    authApiMock.refresh.mockResolvedValue({
      accessToken: "jwt-secret-token-xyz",
      expiresAt: "2099-01-01T00:00:00Z",
      user: defaultUser,
    });
    authApiMock.me.mockResolvedValue(defaultMe);
    const { container } = render(<AuthProvider><WorkspacePage /></AuthProvider>);
    await waitFor(() => {
      expect(screen.getByText("Admin User")).toBeInTheDocument();
    });
    expect(container.textContent).not.toContain("jwt-secret-token-xyz");
  });
});
