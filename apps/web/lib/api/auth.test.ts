import { describe, expect, it, vi } from "vitest";
import { createAuthApi, AmbiguousTenantError } from "./auth";
import { ApiClient } from "./client";
import { ApiHttpError } from "./errors";

describe("createAuthApi", () => {
  function mockClient(status: number, body: unknown): ApiClient {
    const client = new ApiClient({ baseUrl: "https://api.example.com", timeoutMs: 1000 });
    if (status === 204) {
      vi.stubGlobal("fetch", vi.fn().mockImplementation(() =>
        Promise.resolve(new Response(null, { status }))
      ));
    } else {
      vi.stubGlobal("fetch", vi.fn().mockImplementation(() =>
        Promise.resolve(new Response(JSON.stringify(body), {
          status,
          headers: { "content-type": "application/json" },
        }))
      ));
    }
    return client;
  }

  it("login sends email and password to /api/v1/auth/login", async () => {
    const client = mockClient(200, {
      accessToken: "test-access-token",
      expiresAt: "2099-01-01T00:00:00Z",
      user: { id: "u1", tenantId: "t1", email: "test@example.com", displayName: null, status: "ACTIVE" },
    });
    const api = createAuthApi(client);

    const result = await api.login({ email: "test@example.com", password: "pass123" });
    expect(result.accessToken).toBe("test-access-token");
    expect(result.user.email).toBe("test@example.com");
  });

  it("login throws AmbiguousTenantError on 409 with tenantIds", async () => {
    const client = mockClient(409, {
      timestamp: "2024-01-01T00:00:00Z",
      status: 409,
      error: "Conflict",
      message: "البريد الإلكتروني موجود في عدة مستأجرين",
      path: "/api/v1/auth/login",
      tenantIds: ["tenant-1-uuid", "tenant-2-uuid"],
    });
    const api = createAuthApi(client);

    await expect(api.login({ email: "test@example.com", password: "pass123" }))
      .rejects.toThrow(AmbiguousTenantError);

    try {
      await api.login({ email: "test@example.com", password: "pass123" });
    } catch (err) {
      expect(err).toBeInstanceOf(AmbiguousTenantError);
      const ate = err as AmbiguousTenantError;
      expect(ate.tenantIds).toEqual(["tenant-1-uuid", "tenant-2-uuid"]);
    }
  });

  it("login throws ApiHttpError on other HTTP errors", async () => {
    const client = mockClient(401, {
      timestamp: "2024-01-01T00:00:00Z",
      status: 401,
      error: "Unauthorized",
      message: "بيانات الدخول غير صحيحة",
      path: "/api/v1/auth/login",
    });
    const api = createAuthApi(client);

    await expect(api.login({ email: "test@example.com", password: "wrong" }))
      .rejects.toThrow(ApiHttpError);
  });

  it("me returns user identity with tenantId", async () => {
    const client = mockClient(200, {
      id: "u1",
      tenantId: "t1",
      email: "test@example.com",
      displayName: null,
      status: "ACTIVE",
      lastLoginAt: null,
      credentialRotationRequired: false,
      memberships: [],
      roleGrants: [],
    });
    const api = createAuthApi(client);

    const result = await api.me();
    expect(result.id).toBe("u1");
    expect(result.tenantId).toBe("t1");
    expect(result.email).toBe("test@example.com");
    expect(result.credentialRotationRequired).toBe(false);
    expect(result.memberships).toEqual([]);
    expect(result.roleGrants).toEqual([]);
  });

  it("refresh sends request to /api/v1/auth/refresh", async () => {
    const client = mockClient(200, {
      accessToken: "new-access-token",
      expiresAt: "2099-01-01T00:00:00Z",
      user: { id: "u1", tenantId: "t1", email: "test@example.com", displayName: null, status: "ACTIVE" },
    });
    const api = createAuthApi(client);

    const result = await api.refresh("old-refresh-token");
    expect(result.accessToken).toBe("new-access-token");
  });

  it("logout sends request to /api/v1/auth/logout", async () => {
    const client = mockClient(204, null);
    const api = createAuthApi(client);

    await expect(api.logout()).resolves.toBeUndefined();
  });
});

describe("AmbiguousTenantError", () => {
  it("stores tenant IDs from the 409 response", () => {
    const err = new AmbiguousTenantError("ambiguous", ["id1", "id2"]);
    expect(err.name).toBe("AmbiguousTenantError");
    expect(err.message).toBe("ambiguous");
    expect(err.tenantIds).toEqual(["id1", "id2"]);
  });

  it("works with empty tenant IDs array", () => {
    const err = new AmbiguousTenantError("no tenants", []);
    expect(err.tenantIds).toEqual([]);
  });
});
