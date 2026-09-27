import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiConfigurationError } from "./errors";
import { createTenantAccessApi, tenantAccessApi } from "./tenant-access";

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

const TENANT_ID = "11111111-1111-1111-1111-111111111111";
const USER_ID = "22222222-2222-2222-2222-222222222222";
const ROLE_ID = "33333333-3333-3333-3333-333333333333";
const CAPABILITY_ID = "44444444-4444-4444-4444-444444444444";
const GRANT_ID = "55555555-5555-5555-5555-555555555555";

describe("tenantAccessApi — authenticated membership scope", () => {
  beforeEach(() => vi.clearAllMocks());

  it("lists a user's memberships without sending tenantId because JWT scope is authoritative", async () => {
    vi.mocked(apiClient.get).mockResolvedValue([] as never);

    await tenantAccessApi.listUserMemberships(USER_ID);

    expect(apiClient.get).toHaveBeenCalledWith(
      `/api/v1/users/${USER_ID}/memberships`,
      undefined,
    );
  });

  it("rejects an invalid user id before transport", async () => {
    await expect(tenantAccessApi.listUserMemberships("not-a-uuid")).rejects.toThrow(ApiConfigurationError);
    expect(apiClient.get).not.toHaveBeenCalled();
  });
});

describe("tenantAccessApi — roles and capabilities", () => {
  beforeEach(() => vi.clearAllMocks());

  it("lists tenant roles using the authenticated tenant id supplied by session code", async () => {
    vi.mocked(apiClient.get).mockResolvedValue([] as never);

    await tenantAccessApi.listRoles(TENANT_ID);

    expect(apiClient.get).toHaveBeenCalledWith("/api/v1/access/roles", {
      query: { tenantId: TENANT_ID },
    });
  });

  it("loads one role and validates both ids", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({} as never);

    await tenantAccessApi.getRole(TENANT_ID, ROLE_ID);

    expect(apiClient.get).toHaveBeenCalledWith(`/api/v1/access/roles/${ROLE_ID}`, {
      query: { tenantId: TENANT_ID },
    });
    await expect(tenantAccessApi.getRole(TENANT_ID, "bad")).rejects.toThrow(ApiConfigurationError);
  });

  it("lists the global capability registry without a tenant query parameter", async () => {
    vi.mocked(apiClient.get).mockResolvedValue([] as never);

    await tenantAccessApi.listCapabilities();

    expect(apiClient.get).toHaveBeenCalledWith("/api/v1/access/capabilities", undefined);
  });

  it("lists and mutates role capability links through the canonical access API", async () => {
    vi.mocked(apiClient.get).mockResolvedValue([] as never);
    vi.mocked(apiClient.post).mockResolvedValue({} as never);
    vi.mocked(apiClient.delete).mockResolvedValue(undefined as never);

    await tenantAccessApi.listRoleCapabilities(TENANT_ID, ROLE_ID);
    expect(apiClient.get).toHaveBeenCalledWith(
      `/api/v1/access/roles/${ROLE_ID}/access-items`,
      { query: { tenantId: TENANT_ID } },
    );

    await tenantAccessApi.attachRoleCapability(TENANT_ID, ROLE_ID, CAPABILITY_ID);
    expect(apiClient.post).toHaveBeenCalledWith(
      `/api/v1/access/roles/${ROLE_ID}/access-items/${CAPABILITY_ID}`,
      undefined,
      { query: { tenantId: TENANT_ID } },
    );

    await tenantAccessApi.detachRoleCapability(TENANT_ID, ROLE_ID, CAPABILITY_ID);
    expect(apiClient.delete).toHaveBeenCalledWith(
      `/api/v1/access/roles/${ROLE_ID}/access-items/${CAPABILITY_ID}`,
      { query: { tenantId: TENANT_ID } },
    );
  });
});

describe("tenantAccessApi — user role links", () => {
  beforeEach(() => vi.clearAllMocks());

  it("lists role links and grants a tenant-wide role", async () => {
    vi.mocked(apiClient.get).mockResolvedValue([] as never);
    vi.mocked(apiClient.post).mockResolvedValue({} as never);

    await tenantAccessApi.listUserRoleLinks(TENANT_ID, USER_ID);
    expect(apiClient.get).toHaveBeenCalledWith(
      `/api/v1/access/users/${USER_ID}/role-links`,
      { query: { tenantId: TENANT_ID } },
    );

    await tenantAccessApi.grantUserRole(TENANT_ID, USER_ID, ROLE_ID);
    expect(apiClient.post).toHaveBeenCalledWith(
      `/api/v1/access/users/${USER_ID}/role-links/${ROLE_ID}`,
      undefined,
      { query: { tenantId: TENANT_ID } },
    );
  });

  it("includes organizationId only when an organization-scoped role is requested", async () => {
    const organizationId = "66666666-6666-6666-6666-666666666666";
    vi.mocked(apiClient.post).mockResolvedValue({} as never);

    await tenantAccessApi.grantUserRole(TENANT_ID, USER_ID, ROLE_ID, organizationId);

    expect(apiClient.post).toHaveBeenCalledWith(
      `/api/v1/access/users/${USER_ID}/role-links/${ROLE_ID}`,
      undefined,
      { query: { tenantId: TENANT_ID, organizationId } },
    );
  });

  it("revokes by canonical grantId", async () => {
    vi.mocked(apiClient.patch).mockResolvedValue({} as never);

    await tenantAccessApi.revokeUserRole(TENANT_ID, GRANT_ID);

    expect(apiClient.patch).toHaveBeenCalledWith(
      `/api/v1/access/users/role-links/${GRANT_ID}/revoke`,
      undefined,
      { query: { tenantId: TENANT_ID } },
    );
  });
});

describe("createTenantAccessApi", () => {
  it("supports dependency injection of the established ApiClient", () => {
    const customClient = {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      patch: vi.fn(),
      delete: vi.fn(),
    } as never;

    const api = createTenantAccessApi(customClient);
    expect(typeof api.listRoles).toBe("function");
    expect(typeof api.listUserMemberships).toBe("function");
  });
});
