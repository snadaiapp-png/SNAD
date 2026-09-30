import { beforeEach, describe, expect, it, vi } from "vitest";

const getMock = vi.fn();
const postMock = vi.fn();
const patchMock = vi.fn();

vi.mock("./client", () => ({
  apiClient: {
    get: (...args: unknown[]) => getMock(...args),
    post: (...args: unknown[]) => postMock(...args),
    patch: (...args: unknown[]) => patchMock(...args),
  },
}));

import {
  createOverride,
  effectivePermissions,
  listOverrides,
  listRelationships,
  resync,
  revokeOverride,
} from "./access-api";

describe("access-api — canonical W1 routes", () => {
  beforeEach(() => {
    getMock.mockReset().mockResolvedValue([]);
    postMock.mockReset().mockResolvedValue({});
    patchMock.mockReset().mockResolvedValue(undefined);
  });

  it("uses the override administration routes", async () => {
    await listOverrides("user-1");
    await createOverride({
      targetUserId: "user-1",
      capabilityCode: "CRM.ACCOUNT.READ",
      effect: "ALLOW",
      scopeType: "TENANT_ALL",
      scopeReference: null,
      reason: "approved access",
      validFrom: null,
      validUntil: null,
    });
    await revokeOverride("override-1");

    expect(getMock).toHaveBeenCalledWith(
      "/api/v1/access/overrides",
      { query: { userId: "user-1" }, cache: "no-store" },
    );
    expect(postMock).toHaveBeenCalledWith(
      "/api/v1/access/overrides",
      expect.objectContaining({ targetUserId: "user-1", effect: "ALLOW" }),
    );
    expect(patchMock).toHaveBeenCalledWith(
      "/api/v1/access/overrides/override-1/revoke",
      undefined,
    );
  });

  it("uses relationship and effective-permission routes", async () => {
    await listRelationships("user-2");
    await effectivePermissions("user-2");
    await resync("user-2");

    expect(getMock).toHaveBeenNthCalledWith(
      1,
      "/api/v1/access/relationships",
      { query: { userId: "user-2" }, cache: "no-store" },
    );
    expect(getMock).toHaveBeenNthCalledWith(
      2,
      "/api/v1/access/effective-permissions",
      { query: { userId: "user-2" }, cache: "no-store" },
    );
    expect(postMock).toHaveBeenCalledWith(
      "/api/v1/access/effective-permissions/resync",
      undefined,
      { query: { userId: "user-2" } },
    );
  });
});
