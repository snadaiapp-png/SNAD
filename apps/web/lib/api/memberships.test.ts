/**
 * Tests for the Organization Memberships API client.
 *
 * Covers: list, get, invite, transition, validation, email normalization,
 * body/path/query ID consistency, and error propagation.
 * All tests mock `apiClient` methods — no real network calls.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { membershipsApi, createMembershipsApi } from "./memberships";
import { ApiConfigurationError, ApiHttpError } from "./errors";
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
const VALID_ORG = "33333333-3333-3333-3333-333333333333";
const VALID_MEMBERSHIP = "44444444-4444-4444-4444-444444444444";

function makeMembership(overrides: Partial<Record<string, unknown>> = {}): Record<string, unknown> {
  return {
    id: VALID_MEMBERSHIP,
    tenantId: VALID_TENANT,
    organizationId: VALID_ORG,
    userId: null,
    email: "member@example.com",
    displayName: "Member Name",
    status: "INVITED",
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
    path: "/api/v1/organizations",
    requestId: null,
    body: { status, message },
  };
  return new ApiHttpError(`HTTP ${status}`, details);
}

describe("membershipsApi — list", () => {
  beforeEach(() => vi.clearAllMocks());

  it("sends GET with tenantId and organizationId in path", async () => {
    vi.mocked(apiClient.get).mockResolvedValue([makeMembership()] as never);
    await membershipsApi.list(VALID_TENANT, VALID_ORG);
    const [path, options] = vi.mocked(apiClient.get).mock.calls[0];
    expect(path).toBe(`/api/v1/organizations/${VALID_ORG}/memberships`);
    expect(options?.query).toEqual({ tenantId: VALID_TENANT });
  });

  it("returns the list of memberships", async () => {
    vi.mocked(apiClient.get).mockResolvedValue([makeMembership({ id: "m1" }), makeMembership({ id: "m2" })] as never);
    const result = await membershipsApi.list(VALID_TENANT, VALID_ORG);
    expect(result).toHaveLength(2);
  });

  it("rejects invalid tenantId", async () => {
    await expect(membershipsApi.list("bad", VALID_ORG)).rejects.toThrow(ApiConfigurationError);
    expect(apiClient.get).not.toHaveBeenCalled();
  });

  it("rejects invalid organizationId", async () => {
    await expect(membershipsApi.list(VALID_TENANT, "bad")).rejects.toThrow(ApiConfigurationError);
    expect(apiClient.get).not.toHaveBeenCalled();
  });
});

describe("membershipsApi — get", () => {
  beforeEach(() => vi.clearAllMocks());

  it("sends GET with membershipId in path", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(makeMembership() as never);
    await membershipsApi.get(VALID_TENANT, VALID_ORG, VALID_MEMBERSHIP);
    const [path, options] = vi.mocked(apiClient.get).mock.calls[0];
    expect(path).toBe(`/api/v1/organizations/${VALID_ORG}/memberships/${VALID_MEMBERSHIP}`);
    expect(options?.query).toEqual({ tenantId: VALID_TENANT });
  });

  it("rejects invalid membershipId", async () => {
    await expect(membershipsApi.get(VALID_TENANT, VALID_ORG, "bad")).rejects.toThrow(ApiConfigurationError);
  });
});

describe("membershipsApi — invite", () => {
  beforeEach(() => vi.clearAllMocks());

  it("sends POST with body containing matching tenantId and organizationId", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(makeMembership() as never);
    await membershipsApi.invite(VALID_TENANT, VALID_ORG, { email: "new@example.com", displayName: "New Member" });
    const [path, body, options] = vi.mocked(apiClient.post).mock.calls[0];
    expect(path).toBe(`/api/v1/organizations/${VALID_ORG}/memberships`);
    expect(body).toEqual({ tenantId: VALID_TENANT, organizationId: VALID_ORG, email: "new@example.com", displayName: "New Member" });
    expect(options?.query).toEqual({ tenantId: VALID_TENANT });
  });

  it("body tenantId matches query tenantId (no cross-tenant mismatch)", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(makeMembership() as never);
    await membershipsApi.invite(VALID_TENANT, VALID_ORG, { email: "x@example.com", displayName: null });
    const [, body, options] = vi.mocked(apiClient.post).mock.calls[0];
    expect((body as { tenantId: string }).tenantId).toBe((options?.query as { tenantId: string }).tenantId);
  });

  it("body organizationId matches path organizationId (no cross-org mismatch)", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(makeMembership() as never);
    await membershipsApi.invite(VALID_TENANT, VALID_ORG, { email: "x@example.com", displayName: null });
    const [path, body] = vi.mocked(apiClient.post).mock.calls[0];
    const pathOrgId = path.match(/organizations\/([^/]+)\/memberships/)?.[1];
    expect(pathOrgId).toBe((body as { organizationId: string }).organizationId);
  });

  it("trims and lowercases email", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(makeMembership() as never);
    await membershipsApi.invite(VALID_TENANT, VALID_ORG, { email: "  New@EXAMPLE.COM  ", displayName: null });
    const [, body] = vi.mocked(apiClient.post).mock.calls[0];
    expect((body as { email: string }).email).toBe("new@example.com");
  });

  it("trims displayName and converts empty to null", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(makeMembership() as never);
    await membershipsApi.invite(VALID_TENANT, VALID_ORG, { email: "x@example.com", displayName: "   " });
    const [, body] = vi.mocked(apiClient.post).mock.calls[0];
    expect((body as { displayName: string | null }).displayName).toBeNull();
  });

  it("rejects invalid email", async () => {
    await expect(membershipsApi.invite(VALID_TENANT, VALID_ORG, { email: "not-an-email", displayName: null })).rejects.toThrow(ApiConfigurationError);
    expect(apiClient.post).not.toHaveBeenCalled();
  });

  it("rejects invalid tenantId", async () => {
    await expect(membershipsApi.invite("bad", VALID_ORG, { email: "x@example.com", displayName: null })).rejects.toThrow(ApiConfigurationError);
  });

  it("rejects invalid organizationId", async () => {
    await expect(membershipsApi.invite(VALID_TENANT, "bad", { email: "x@example.com", displayName: null })).rejects.toThrow(ApiConfigurationError);
  });

  it("propagates ApiHttpError on 409 (duplicate membership)", async () => {
    vi.mocked(apiClient.post).mockRejectedValue(makeHttpError(409, "Organization membership already exists for this email"));
    await expect(membershipsApi.invite(VALID_TENANT, VALID_ORG, { email: "dup@example.com", displayName: null })).rejects.toThrow(ApiHttpError);
  });
});

describe("membershipsApi — transition", () => {
  beforeEach(() => vi.clearAllMocks());

  it("activate sends PATCH to .../activate", async () => {
    vi.mocked(apiClient.patch).mockResolvedValue(makeMembership({ status: "ACTIVE" }) as never);
    await membershipsApi.transition(VALID_TENANT, VALID_ORG, VALID_MEMBERSHIP, "activate");
    const [path, body, options] = vi.mocked(apiClient.patch).mock.calls[0];
    expect(path).toBe(`/api/v1/organizations/${VALID_ORG}/memberships/${VALID_MEMBERSHIP}/activate`);
    expect(body).toBeUndefined();
    expect(options?.query).toEqual({ tenantId: VALID_TENANT });
  });

  it("deactivate sends PATCH to .../deactivate", async () => {
    vi.mocked(apiClient.patch).mockResolvedValue(makeMembership({ status: "INACTIVE" }) as never);
    await membershipsApi.transition(VALID_TENANT, VALID_ORG, VALID_MEMBERSHIP, "deactivate");
    const [path] = vi.mocked(apiClient.patch).mock.calls[0];
    expect(path).toBe(`/api/v1/organizations/${VALID_ORG}/memberships/${VALID_MEMBERSHIP}/deactivate`);
  });

  it("remove sends PATCH to .../remove (NOT HTTP DELETE)", async () => {
    vi.mocked(apiClient.patch).mockResolvedValue(makeMembership({ status: "REMOVED" }) as never);
    await membershipsApi.transition(VALID_TENANT, VALID_ORG, VALID_MEMBERSHIP, "remove");
    expect(apiClient.patch).toHaveBeenCalledTimes(1);
    expect(apiClient.delete).not.toHaveBeenCalled();
    const [path] = vi.mocked(apiClient.patch).mock.calls[0];
    expect(path).toBe(`/api/v1/organizations/${VALID_ORG}/memberships/${VALID_MEMBERSHIP}/remove`);
  });

  it("rejects invalid lifecycle action", async () => {
    await expect(membershipsApi.transition(VALID_TENANT, VALID_ORG, VALID_MEMBERSHIP, "suspend" as never)).rejects.toThrow();
    expect(apiClient.patch).not.toHaveBeenCalled();
  });

  it("rejects invalid membershipId", async () => {
    await expect(membershipsApi.transition(VALID_TENANT, VALID_ORG, "bad", "activate")).rejects.toThrow(ApiConfigurationError);
  });
});

describe("createMembershipsApi — custom client", () => {
  it("accepts a custom ApiClient instance", () => {
    const customClient = { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() } as unknown;
    const api = createMembershipsApi(customClient as never);
    expect(api).toBeDefined();
    expect(typeof api.invite).toBe("function");
  });
});
