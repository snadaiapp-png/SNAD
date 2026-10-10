/**
 * Tests for the Users API client.
 *
 * Covers: list, get, create, update, transition, validation,
 * email normalization, default status, and error propagation.
 * All tests mock `apiClient` methods — no real network calls.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { usersApi, createUsersApi } from "./users";
import { ApiConfigurationError, ApiHttpError, ApiInputValidationError } from "./errors";
import type { ApiErrorDetails } from "./types";

// Mock the apiClient module
vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
  ApiClient: vi.fn(),
}));

const { apiClient } = await import("./client");

const VALID_TENANT = "11111111-1111-1111-1111-111111111111";
const VALID_USER = "22222222-2222-2222-2222-222222222222";
const TEST_BOOTSTRAP_CREDENTIAL = ["Temporary", "Credential", "For", "Tests"].join("-") + "123!";

function makeUser(overrides: Partial<Record<string, unknown>> = {}): Record<string, unknown> {
  return {
    id: VALID_USER,
    tenantId: VALID_TENANT,
    email: "user@example.com",
    displayName: "User Name",
    mobileNumber: "+966500000000",
    mobileRegion: "SA",
    status: "ACTIVE",
    lastLoginAt: null,
    credentialInitialized: false,
    credentialRotationRequired: false,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function makeHttpError(status: number, message: string): ApiHttpError {
  const details: ApiErrorDetails = {
    status,
    error: status === 409 ? "Conflict" : "Bad Request",
    message,
    path: "/api/v1/users",
    requestId: null,
    body: { status, message },
  };
  return new ApiHttpError(`HTTP ${status}`, details);
}

describe("usersApi — list", () => {
  beforeEach(() => vi.clearAllMocks());

  it("sends GET to /api/v1/users with tenantId as query param", async () => {
    vi.mocked(apiClient.get).mockResolvedValue([makeUser()] as never);
    await usersApi.list(VALID_TENANT);
    expect(apiClient.get).toHaveBeenCalledTimes(1);
    const [path, options] = vi.mocked(apiClient.get).mock.calls[0];
    expect(path).toBe("/api/v1/users");
    expect(options?.query).toEqual({ tenantId: VALID_TENANT });
  });

  it("returns the list of users", async () => {
    vi.mocked(apiClient.get).mockResolvedValue([makeUser({ id: "u1" }), makeUser({ id: "u2" })] as never);
    const result = await usersApi.list(VALID_TENANT);
    expect(result).toHaveLength(2);
  });

  it("rejects invalid tenantId UUID", async () => {
    await expect(usersApi.list("not-a-uuid")).rejects.toThrow(ApiConfigurationError);
    expect(apiClient.get).not.toHaveBeenCalled();
  });

  it("propagates ApiHttpError on 409", async () => {
    vi.mocked(apiClient.get).mockRejectedValue(makeHttpError(409, "conflict"));
    await expect(usersApi.list(VALID_TENANT)).rejects.toThrow(ApiHttpError);
  });
});

describe("usersApi — atomic module provisioning", () => {
  beforeEach(() => vi.clearAllMocks());

  it("normalizes Saudi-local contact data inside the atomic tenant-admin request", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(makeUser() as never);

    await usersApi.provisionModuleUser(
      VALID_TENANT,
      "/CRM/overview",
      ["CRM.ACCOUNT.READ"],
      {
        email: " New.User@Example.com ",
        username: "New.User",
        displayName: " New User ",
        mobileNumber: "0551234567",
        mobileRegion: "sa",
        initialCredential: TEST_BOOTSTRAP_CREDENTIAL,
      },
    );

    expect(apiClient.post).toHaveBeenCalledWith(
      "/api/v1/users/module-user-provisioning",
      {
        user: {
          email: "new.user@example.com",
          username: "new.user",
          displayName: "New User",
          mobileNumber: "+966551234567",
          mobileRegion: "SA",
          initialCredential: TEST_BOOTSTRAP_CREDENTIAL,
        },
        routeRoot: "crm",
        capabilityCodes: ["CRM.ACCOUNT.READ"],
      },
      { query: { tenantId: VALID_TENANT } },
    );
  });

  it("uses the override-authorized atomic endpoint for platform operators", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(makeUser() as never);

    await usersApi.provisionModuleUserWithOverrides(
      VALID_TENANT,
      "hr",
      ["HRM.EMPLOYEE.VIEW"],
      {
        email: "override@example.com",
        username: "override.user",
        displayName: "Override User",
      },
    );

    expect(apiClient.post).toHaveBeenCalledWith(
      "/api/v1/users/module-user-provisioning-overrides",
      {
        user: {
          email: "override@example.com",
          username: "override.user",
          displayName: "Override User",
        },
        routeRoot: "hr",
        capabilityCodes: ["HRM.EMPLOYEE.VIEW"],
      },
      { query: { tenantId: VALID_TENANT } },
    );
  });

  it("fails before transport when no module capability is selected", async () => {
    await expect(usersApi.provisionModuleUser(
      VALID_TENANT,
      "crm",
      [],
      { email: "user@example.com", username: "user.test" },
    )).rejects.toThrow(ApiConfigurationError);

    expect(apiClient.post).not.toHaveBeenCalled();
  });
});

describe("usersApi — get", () => {
  beforeEach(() => vi.clearAllMocks());

  it("sends GET to /api/v1/users/{userId} with tenantId as query param", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(makeUser() as never);
    await usersApi.get(VALID_TENANT, VALID_USER);
    expect(apiClient.get).toHaveBeenCalledTimes(1);
    const [path, options] = vi.mocked(apiClient.get).mock.calls[0];
    expect(path).toBe(`/api/v1/users/${VALID_USER}`);
    expect(options?.query).toEqual({ tenantId: VALID_TENANT });
  });

  it("rejects invalid userId UUID", async () => {
    await expect(usersApi.get(VALID_TENANT, "bad")).rejects.toThrow(ApiConfigurationError);
    expect(apiClient.get).not.toHaveBeenCalled();
  });
});

describe("usersApi — create", () => {
  beforeEach(() => vi.clearAllMocks());

  it("sends POST with correct body shape", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(makeUser() as never);
    await usersApi.create(VALID_TENANT, { email: "new@example.com", displayName: "New User", status: "ACTIVE" });
    const [path, body, options] = vi.mocked(apiClient.post).mock.calls[0];
    expect(path).toBe("/api/v1/users");
    expect(body).toEqual({ email: "new@example.com", displayName: "New User", status: "ACTIVE" });
    expect(options?.query).toEqual({ tenantId: VALID_TENANT });
  });

  it("trims and lowercases email", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(makeUser() as never);
    await usersApi.create(VALID_TENANT, { email: "  New@EXAMPLE.COM  ", displayName: null });
    const [, body] = vi.mocked(apiClient.post).mock.calls[0];
    expect((body as { email: string }).email).toBe("new@example.com");
  });

  it("sends normalized username only when explicitly provided", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(makeUser() as never);
    await usersApi.create(VALID_TENANT, {
      email: "new@example.com",
      username: "  Sales.User  ",
      displayName: "Sales User",
    });
    const [, body] = vi.mocked(apiClient.post).mock.calls[0];
    expect(body).toEqual({
      email: "new@example.com",
      username: "sales.user",
      displayName: "Sales User",
    });
  });

  it("sends one-time bootstrap credential when provided", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(makeUser({ status: "ACTIVE", credentialInitialized: true, credentialRotationRequired: true }) as never);
    await usersApi.create(VALID_TENANT, {
      email: "new@example.com",
      username: "new.user",
      displayName: "New User",
      initialCredential: TEST_BOOTSTRAP_CREDENTIAL,
    });
    const [, body] = vi.mocked(apiClient.post).mock.calls[0];
    expect(body).toMatchObject({
      email: "new@example.com",
      username: "new.user",
      initialCredential: TEST_BOOTSTRAP_CREDENTIAL,
    });
  });

  it("rejects a bootstrap credential shorter than eight characters as user input validation", async () => {
    await expect(usersApi.create(VALID_TENANT, {
      email: "new@example.com",
      displayName: "New User",
      initialCredential: "short",
    })).rejects.toThrow(ApiInputValidationError);
    expect(apiClient.post).not.toHaveBeenCalled();
  });

  it("normalizes mobile profile fields when provided", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(makeUser() as never);
    await usersApi.create(VALID_TENANT, { email: "new@example.com", displayName: "New", mobileNumber: "  +966500000000  ", mobileRegion: " sa " });
    const [, body] = vi.mocked(apiClient.post).mock.calls[0];
    expect(body).toMatchObject({ mobileNumber: "+966500000000", mobileRegion: "SA" });
  });

  it("normalizes Saudi local mobile input to E.164", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(makeUser() as never);
    await usersApi.create(VALID_TENANT, {
      email: "new@example.com",
      username: "new.user",
      displayName: "New",
      mobileNumber: "0551234567",
      mobileRegion: "SA",
    });
    const [, body] = vi.mocked(apiClient.post).mock.calls[0];
    expect(body).toMatchObject({ mobileNumber: "+966551234567", mobileRegion: "SA" });
  });

  it("rejects numeric region codes before transport", async () => {
    await expect(usersApi.create(VALID_TENANT, {
      email: "new@example.com",
      username: "new.user",
      displayName: "New",
      mobileNumber: "0551234567",
      mobileRegion: "01",
    })).rejects.toMatchObject({ field: "mobileRegion" });
    expect(apiClient.post).not.toHaveBeenCalled();
  });

  it("trims displayName and converts empty to null", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(makeUser() as never);
    await usersApi.create(VALID_TENANT, { email: "new@example.com", displayName: "   " });
    const [, body] = vi.mocked(apiClient.post).mock.calls[0];
    expect((body as { displayName: string | null }).displayName).toBeNull();
  });

  it("does not send status when omitted; backend derives INVITED or ACTIVE from credential presence", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(makeUser() as never);
    await usersApi.create(VALID_TENANT, { email: "new@example.com", displayName: null });
    const [, body] = vi.mocked(apiClient.post).mock.calls[0];
    expect(body).not.toHaveProperty("status");
  });

  it("rejects invalid email as user input validation", async () => {
    await expect(usersApi.create(VALID_TENANT, { email: "not-an-email", displayName: null })).rejects.toThrow(ApiInputValidationError);
    expect(apiClient.post).not.toHaveBeenCalled();
  });

  it("rejects empty email as user input validation", async () => {
    await expect(usersApi.create(VALID_TENANT, { email: "", displayName: null })).rejects.toThrow(ApiInputValidationError);
  });

  it("rejects displayName exceeding max length as user input validation", async () => {
    await expect(usersApi.create(VALID_TENANT, { email: "new@example.com", displayName: "a".repeat(201) })).rejects.toThrow(ApiInputValidationError);
  });

  it("propagates ApiHttpError on 409 (duplicate email)", async () => {
    vi.mocked(apiClient.post).mockRejectedValue(makeHttpError(409, "User email already exists for this tenant"));
    await expect(usersApi.create(VALID_TENANT, { email: "dup@example.com", displayName: null })).rejects.toThrow(ApiHttpError);
  });
});

describe("usersApi — update", () => {
  beforeEach(() => vi.clearAllMocks());

  it("sends PUT with email and displayName only (no status)", async () => {
    vi.mocked(apiClient.put).mockResolvedValue(makeUser() as never);
    await usersApi.update(VALID_TENANT, VALID_USER, { email: "updated@example.com", displayName: "Updated" });
    const [path, body, options] = vi.mocked(apiClient.put).mock.calls[0];
    expect(path).toBe(`/api/v1/users/${VALID_USER}`);
    expect(body).toEqual({ email: "updated@example.com", displayName: "Updated" });
    expect(body).not.toHaveProperty("status");
    expect(options?.query).toEqual({ tenantId: VALID_TENANT });
  });

  it("trims and lowercases email", async () => {
    vi.mocked(apiClient.put).mockResolvedValue(makeUser() as never);
    await usersApi.update(VALID_TENANT, VALID_USER, { email: "  Updated@Example.COM  ", displayName: null });
    const [, body] = vi.mocked(apiClient.put).mock.calls[0];
    expect((body as { email: string }).email).toBe("updated@example.com");
  });

  it("sends normalized username when explicitly provided", async () => {
    vi.mocked(apiClient.put).mockResolvedValue(makeUser() as never);
    await usersApi.update(VALID_TENANT, VALID_USER, {
      email: "updated@example.com",
      username: "  Updated.User  ",
      displayName: "Updated",
    });
    const [, body] = vi.mocked(apiClient.put).mock.calls[0];
    expect(body).toEqual({
      email: "updated@example.com",
      username: "updated.user",
      displayName: "Updated",
    });
  });

  it("sends mobile profile updates when explicitly provided", async () => {
    vi.mocked(apiClient.put).mockResolvedValue(makeUser() as never);
    await usersApi.update(VALID_TENANT, VALID_USER, { email: "updated@example.com", displayName: "Updated", mobileNumber: "+966500000001", mobileRegion: "sa" });
    const [, body] = vi.mocked(apiClient.put).mock.calls[0];
    expect(body).toMatchObject({ mobileNumber: "+966500000001", mobileRegion: "SA" });
  });

  it("rejects invalid userId", async () => {
    await expect(usersApi.update(VALID_TENANT, "bad", { email: "x@example.com", displayName: null })).rejects.toThrow(ApiConfigurationError);
  });
});

describe("usersApi — transition", () => {
  beforeEach(() => vi.clearAllMocks());

  it.each(["activate", "deactivate", "suspend", "archive"] as const)(
    "sends PATCH to /api/v1/users/{userId}/%s with tenantId query",
    async (action) => {
      vi.mocked(apiClient.patch).mockResolvedValue(makeUser() as never);
      await usersApi.transition(VALID_TENANT, VALID_USER, action);
      const [path, body, options] = vi.mocked(apiClient.patch).mock.calls[0];
      expect(path).toBe(`/api/v1/users/${VALID_USER}/${action}`);
      expect(body).toBeUndefined();
      expect(options?.query).toEqual({ tenantId: VALID_TENANT });
    }
  );

  it("rejects invalid lifecycle action", async () => {
    await expect(usersApi.transition(VALID_TENANT, VALID_USER, "delete" as never)).rejects.toThrow();
    expect(apiClient.patch).not.toHaveBeenCalled();
  });

  it("rejects invalid userId", async () => {
    await expect(usersApi.transition(VALID_TENANT, "bad", "activate")).rejects.toThrow(ApiConfigurationError);
  });
});

describe("createUsersApi — custom client", () => {
  it("accepts a custom ApiClient instance", () => {
    const customClient = { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() } as unknown;
    const api = createUsersApi(customClient as never);
    expect(api).toBeDefined();
    expect(typeof api.list).toBe("function");
  });
});