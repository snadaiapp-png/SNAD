// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { useEffect } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/lib/api/errors";
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
  authResponseToMe: (response: any) => ({
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

const bootstrap = {
  accessToken: "access-token",
  expiresAt: "2099-01-01T00:00:00Z",
  user: {
    id: "u1",
    tenantId: "t1",
    email: "test@example.com",
    displayName: "Test User",
    status: "ACTIVE",
  },
  lastLoginAt: "2026-07-18T10:00:00Z",
  credentialRotationRequired: false,
  memberships: [],
  effectiveRoleGrants: [],
  defaultOrganizationId: null,
  defaultDestination: "/crm",
  availableDestinations: ["/workspace", "/crm"],
  tenantContext: { tenantId: "t1", defaultOrganizationId: null },
};

const authContainer: { current: ReturnType<typeof useAuth> | null } = { current: null };

function Probe() {
  const auth = useAuth();
  useEffect(() => { authContainer.current = auth; });
  return (
    <div>
      <span data-testid="state">{auth.state}</span>
      <span data-testid="retry">{String(auth.canRetrySessionRestore)}</span>
      <span data-testid="destination">{auth.defaultDestination}</span>
    </div>
  );
}

function renderProvider() {
  return render(<AuthProvider><Probe /></AuthProvider>);
}

function clearCookies() {
  document.cookie = "sanad_session_hint=; Max-Age=0; Path=/";
}

describe("AuthProvider bootstrap", () => {
  beforeEach(() => {
    clearCookies();
    authContainer.current = null;
    for (const mock of Object.values(authApiMock)) mock.mockReset();
  });

  afterEach(() => {
    cleanup();
    clearCookies();
  });

  it("shows anonymous state without making a speculative refresh for a new visitor", async () => {
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("ANONYMOUS"));
    expect(authApiMock.refresh).not.toHaveBeenCalled();
  });

  it("restores a hinted session from one refresh bootstrap and never calls /me", async () => {
    document.cookie = "sanad_session_hint=1; Path=/";
    authApiMock.refresh.mockResolvedValue(bootstrap);
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("AUTHENTICATED"));
    expect(authApiMock.refresh).toHaveBeenCalledTimes(1);
    expect(authApiMock.me).not.toHaveBeenCalled();
    expect(screen.getByTestId("destination")).toHaveTextContent("/crm");
  });

  it("logs in from a single bootstrap response without a sequential /me request", async () => {
    authApiMock.login.mockResolvedValue(bootstrap);
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("ANONYMOUS"));
    await act(async () => {
      await authContainer.current!.login({ email: "test@example.com", password: "secret" });
    });
    expect(screen.getByTestId("state")).toHaveTextContent("AUTHENTICATED");
    expect(authApiMock.login).toHaveBeenCalledTimes(1);
    expect(authApiMock.me).not.toHaveBeenCalled();
  });

  it("treats rejected refresh credentials as an anonymous session, not an outage", async () => {
    document.cookie = "sanad_session_hint=1; Path=/";
    authApiMock.refresh.mockRejectedValue(new ApiHttpError("unauthorized", {
      status: 401,
      error: "Unauthorized",
      message: null,
      path: null,
      requestId: null,
      body: null,
    }));
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("ANONYMOUS"));
    expect(screen.getByTestId("retry")).toHaveTextContent("false");
  });

  it("surfaces transient restore failures with a retry path", async () => {
    document.cookie = "sanad_session_hint=1; Path=/";
    authApiMock.refresh
      .mockRejectedValueOnce(new Error("temporary network failure"))
      .mockResolvedValueOnce(bootstrap);
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("ERROR"));
    expect(screen.getByTestId("retry")).toHaveTextContent("true");
    await act(async () => { await authContainer.current!.retrySessionRestore(); });
    expect(screen.getByTestId("state")).toHaveTextContent("AUTHENTICATED");
  });

  it("re-establishes a fresh bootstrap after mandatory credential rotation without /me", async () => {
    const rotationBootstrap = { ...bootstrap, credentialRotationRequired: true };
    authApiMock.login.mockResolvedValueOnce(rotationBootstrap).mockResolvedValueOnce(bootstrap);
    authApiMock.changeCredential.mockResolvedValue(undefined);
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("ANONYMOUS"));
    await act(async () => {
      await authContainer.current!.login({ email: "test@example.com", password: "old-secret" });
    });
    expect(screen.getByTestId("state")).toHaveTextContent("CREDENTIAL_ROTATION_REQUIRED");
    await act(async () => {
      await authContainer.current!.changeCredential("old-secret", "new-secret");
    });
    expect(authApiMock.changeCredential).toHaveBeenCalledTimes(1);
    expect(authApiMock.login).toHaveBeenLastCalledWith({
      email: "test@example.com",
      password: "new-secret",
      tenantId: "t1",
    });
    expect(authApiMock.me).not.toHaveBeenCalled();
    expect(screen.getByTestId("state")).toHaveTextContent("AUTHENTICATED");
  });
});
