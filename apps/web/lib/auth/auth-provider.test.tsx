// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { useEffect } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { AuthProvider, useAuth } from "./auth-provider";

const { authApiMock } = vi.hoisted(() => ({
  authApiMock: {
    refresh: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
    me: vi.fn(),
    changeCredential: vi.fn(),
  },
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

const defaultUser = {
  id: "u1",
  tenantId: "t1",
  email: "test@example.com",
  displayName: null,
  status: "ACTIVE",
};

const defaultMe = {
  ...defaultUser,
  lastLoginAt: null,
  credentialRotationRequired: false,
  memberships: [],
  roleGrants: [],
};

/** Container that captures the latest auth context via useEffect. */
const authContainer: { current: ReturnType<typeof useAuth> | null } = { current: null };

function AuthProbe() {
  const auth = useAuth();
  useEffect(() => {
    authContainer.current = auth;
  });
  return <span data-testid="state">{auth.state}</span>;
}

function renderProvider() {
  return render(
    <AuthProvider>
      <AuthProbe />
    </AuthProvider>,
  );
}

describe("AuthProvider — Password Ref Security", () => {
  beforeEach(() => {
    authApiMock.refresh.mockReset();
    authApiMock.login.mockReset();
    authApiMock.logout.mockReset();
    authApiMock.me.mockReset();
    authApiMock.changeCredential.mockReset();
    authContainer.current = null;
    // Default: fail silent refresh so we start in ANONYMOUS
    authApiMock.refresh.mockRejectedValue(new Error("no cookie"));
  });

  afterEach(() => {
    cleanup();
  });

  it("clears password ref after successful direct login", async () => {
    authApiMock.login.mockResolvedValue({
      accessToken: "token",
      expiresAt: "2099-01-01T00:00:00Z",
      user: defaultUser,
    });
    authApiMock.me.mockResolvedValue(defaultMe);

    renderProvider();

    // Wait for INITIALIZING → ANONYMOUS
    await waitFor(() => {
      expect(screen.getByTestId("state")).toHaveTextContent("ANONYMOUS");
    });

    // Trigger login
    await act(async () => {
      await authContainer.current!.login({ email: "test@example.com", password: "secret123" });
    });

    // After success, state should be AUTHENTICATED
    expect(screen.getByTestId("state")).toHaveTextContent("AUTHENTICATED");

    // Verify password ref is cleared by attempting loginWithTenant
    // (should use empty password since ref was cleared)
    authApiMock.login.mockClear();
    authApiMock.login.mockResolvedValue({
      accessToken: "token2",
      expiresAt: "2099-01-01T00:00:00Z",
      user: defaultUser,
    });

    await act(async () => {
      try {
        await authContainer.current!.loginWithTenant("t1");
      } catch {
        // Expected — password ref is empty
      }
    });

    expect(authApiMock.login).toHaveBeenCalledWith(
      expect.objectContaining({ password: "" }),
    );
  });

  it("clears password ref after dismissAmbiguousTenant", async () => {
    const { AmbiguousTenantError } = await import("@/lib/api/auth");
    authApiMock.login.mockRejectedValue(new AmbiguousTenantError("ambiguous", ["t1", "t2"]));

    renderProvider();

    await waitFor(() => {
      expect(screen.getByTestId("state")).toHaveTextContent("ANONYMOUS");
    });

    // Trigger login that results in ambiguous tenant
    await act(async () => {
      await authContainer.current!.login({ email: "test@example.com", password: "secret123" });
    });

    expect(screen.getByTestId("state")).toHaveTextContent("AMBIGUOUS_TENANT");

    // Dismiss
    await act(async () => {
      authContainer.current!.dismissAmbiguousTenant();
    });

    expect(screen.getByTestId("state")).toHaveTextContent("ANONYMOUS");

    // Verify password ref is cleared — loginWithTenant should use empty password
    authApiMock.login.mockClear();
    authApiMock.login.mockResolvedValue({
      accessToken: "token",
      expiresAt: "2099-01-01T00:00:00Z",
      user: defaultUser,
    });

    await act(async () => {
      try {
        await authContainer.current!.loginWithTenant("t1");
      } catch {
        // Expected
      }
    });

    expect(authApiMock.login).toHaveBeenCalledWith(
      expect.objectContaining({ password: "" }),
    );
  });

  it("clears password ref after non-ambiguous error", async () => {
    authApiMock.login.mockRejectedValue(new Error("invalid credentials"));

    renderProvider();

    await waitFor(() => {
      expect(screen.getByTestId("state")).toHaveTextContent("ANONYMOUS");
    });

    await act(async () => {
      try {
        await authContainer.current!.login({ email: "test@example.com", password: "secret123" });
      } catch {
        // Expected
      }
    });

    expect(screen.getByTestId("state")).toHaveTextContent("ERROR");

    // Verify password ref is cleared by attempting loginWithTenant
    authApiMock.login.mockClear();
    authApiMock.me.mockResolvedValue(defaultMe);
    authApiMock.login.mockResolvedValue({
      accessToken: "token",
      expiresAt: "2099-01-01T00:00:00Z",
      user: defaultUser,
    });

    await act(async () => {
      try {
        await authContainer.current!.loginWithTenant("t1");
      } catch {
        // Expected
      }
    });

    expect(authApiMock.login).toHaveBeenCalledWith(
      expect.objectContaining({ password: "" }),
    );
  });

  it("retains password ref only during ambiguous tenant selection", async () => {
    const { AmbiguousTenantError } = await import("@/lib/api/auth");
    authApiMock.login.mockRejectedValueOnce(new AmbiguousTenantError("ambiguous", ["t1"]));

    renderProvider();

    await waitFor(() => {
      expect(screen.getByTestId("state")).toHaveTextContent("ANONYMOUS");
    });

    // Trigger login that results in ambiguous tenant
    await act(async () => {
      await authContainer.current!.login({ email: "test@example.com", password: "secret123" });
    });

    expect(screen.getByTestId("state")).toHaveTextContent("AMBIGUOUS_TENANT");

    // Now loginWithTenant should use the retained password (not empty)
    authApiMock.login.mockResolvedValueOnce({
      accessToken: "token",
      expiresAt: "2099-01-01T00:00:00Z",
      user: defaultUser,
    });
    authApiMock.me.mockResolvedValue(defaultMe);

    await act(async () => {
      await authContainer.current!.loginWithTenant("t1");
    });

    expect(screen.getByTestId("state")).toHaveTextContent("AUTHENTICATED");
    // Verify login was called with the retained password (not empty)
    expect(authApiMock.login).toHaveBeenCalledWith(
      expect.objectContaining({ password: "secret123", tenantId: "t1" }),
    );
  });

  it("runs silent refresh only once while profile loading triggers rerenders", async () => {
    let resolveMe:
      | ((value: typeof defaultMe) => void)
      | undefined;

    authApiMock.refresh.mockImplementation(async () => ({
      accessToken: "test-access-token",
      expiresAt: "2099-01-01T00:00:00Z",
      user: {
        ...defaultUser,
      },
    }));

    authApiMock.me.mockImplementation(
      () =>
        new Promise<typeof defaultMe>((resolve) => {
          resolveMe = resolve;
        }),
    );

    renderProvider();

    await waitFor(() => {
      expect(authApiMock.refresh).toHaveBeenCalledTimes(1);
    });

    await act(async () => {
      resolveMe?.({
        ...defaultMe,
      });
    });

    await waitFor(() => {
      expect(screen.getByTestId("state")).toHaveTextContent(
        "AUTHENTICATED",
      );
    });

    expect(authApiMock.refresh).toHaveBeenCalledTimes(1);
    expect(authApiMock.me).toHaveBeenCalledTimes(1);
  });

  it("does not re-run bootstrap on child rerender after authentication", async () => {
    authApiMock.refresh.mockResolvedValue({
      accessToken: "test-access-token",
      expiresAt: "2099-01-01T00:00:00Z",
      user: { ...defaultUser },
    });
    authApiMock.me.mockResolvedValue({ ...defaultMe });

    renderProvider();

    await waitFor(() => {
      expect(screen.getByTestId("state")).toHaveTextContent("AUTHENTICATED");
    });

    // After authenticated, refresh should have been called exactly once
    expect(authApiMock.refresh).toHaveBeenCalledTimes(1);
  });
});
